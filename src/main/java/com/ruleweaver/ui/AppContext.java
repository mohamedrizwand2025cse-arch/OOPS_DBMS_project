
package com.ruleweaver.ui;

import com.ruleweaver.sync.MongoToMySqlChangeStreamListener;
import com.mongodb.client.MongoDatabase;
import com.ruleweaver.config.MongoConfig;
import com.ruleweaver.engine.DecisionEngine;
import com.ruleweaver.model.Applicant;
import com.ruleweaver.model.EvaluationResult;
import com.ruleweaver.model.Policy;
import com.ruleweaver.model.PolicyVersion;
import com.ruleweaver.repository.*;
import com.ruleweaver.service.ComparisonService;
import com.ruleweaver.service.PolicyService;
import com.ruleweaver.service.ProgramService;
import com.ruleweaver.sync.SyncWorkerRunner;
import com.ruleweaver.ui.adapter.ApplicantDocumentMapper;
import com.ruleweaver.ui.components.Toast;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import org.bson.Document;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Composition Root and shared State Context for RuleWeaver X.
 *
 * Manages background workers, MongoDB repositories, domain services,
 * synchronization lifecycle, and observable application state.
 */
public class AppContext {

    private static AppContext instance;

    // Concurrency
    private final ExecutorService executor;
    private MongoToMySqlChangeStreamListener mongoChangeStreamListener;
    // Synchronization lifecycle
    private SyncWorkerRunner syncWorkerRunner;

    // Repositories & Services
    private ApplicantRepository applicantRepository;
    private PolicyRepository policyRepository;
    private PolicyVersionRepository policyVersionRepository;
    private EvaluationRepository evaluationRepository;
    private InMemoryEvaluationResultRepository inMemoryEvaluationResultRepository;
    private EvaluationResultRepository evaluationResultRepository;
    private DecisionEngine decisionEngine;
    private ComparisonService comparisonService;
    private PolicyService policyService;
    private ProgramRepository programRepository;
    private ProgramService programService;

    // Observable application state
    private final ObservableList<Applicant> applicants = FXCollections.observableArrayList();

    private final ObservableList<Policy> policies = FXCollections.observableArrayList();

    private final ObservableList<EvaluationResult> evaluationResults = FXCollections.observableArrayList();

    private final ObjectProperty<Policy> selectedPolicy = new SimpleObjectProperty<>();

    private final ObjectProperty<PolicyVersion> selectedVersion = new SimpleObjectProperty<>();

    private final ObjectProperty<PolicyVersion> activeVersion = new SimpleObjectProperty<>();

    private final ObjectProperty<Applicant> selectedApplicant = new SimpleObjectProperty<>();

    private final ObjectProperty<com.ruleweaver.model.ProgramConfiguration> selectedProgram = new SimpleObjectProperty<>();

    private final BooleanProperty mongoOnline = new SimpleBooleanProperty(false);

    private final BooleanProperty reducedMotion = new SimpleBooleanProperty(false);

    private final LongProperty resultsChanged = new SimpleLongProperty();

    // Global toast hook
    private Consumer<Toast> toastConsumer;

    private AppContext() {
        this.executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "RuleWeaver-Worker");
            thread.setDaemon(true);
            return thread;
        });

        initBackend();
    }

    public static synchronized AppContext getInstance() {
        if (instance == null) {
            instance = new AppContext();
        }
        return instance;
    }

    private void initBackend() {
        this.policyRepository = new InMemoryPolicyRepository();

        this.inMemoryEvaluationResultRepository = new InMemoryEvaluationResultRepository();

        this.evaluationResultRepository = inMemoryEvaluationResultRepository;

        this.decisionEngine = new DecisionEngine();

        this.comparisonService = new ComparisonService(decisionEngine);

        this.policyService = new PolicyService(
                policyRepository,
                inMemoryEvaluationResultRepository,
                decisionEngine,
                comparisonService);

        this.programRepository = new InMemoryProgramRepository();
        this.programService = new ProgramService(programRepository);
        this.programService.ensureTemplates();

        this.policyService.setProgramService(programService);
    }

    private void runAsync(Runnable action) {
        executor.execute(action);
    }

    /**
     * Connects to MongoDB and initializes repositories on a worker thread.
     */

    public void connectToDatabase(
            Runnable onConnected,
            Consumer<Throwable> onError) {

        Task<MongoRepositories> task = new Task<>() {

            @Override
            protected MongoRepositories call() {
                MongoDatabase db = MongoConfig.getDatabase();

                db.runCommand(new Document("ping", 1));

                ProgramRepository programs = new MongoProgramRepository(db);

                new ProgramService(programs).ensureTemplates();

                return new MongoRepositories(
                        new ApplicantRepository(db),
                        new MongoPolicyRepository(db),
                        new PolicyVersionRepository(db),
                        new EvaluationRepository(db),
                        programs);
            }
        };

        task.setOnSucceeded(event -> {
            MongoRepositories repositories = task.getValue();

            applicantRepository = repositories.applicants();
            policyRepository = repositories.policies();
            policyVersionRepository = repositories.versions();
            evaluationRepository = repositories.evaluations();
            programRepository = repositories.programs();

            programService = new ProgramService(programRepository);

            evaluationResultRepository = evaluationRepository;

            policyService = new PolicyService(
                    policyRepository,
                    evaluationResultRepository,
                    decisionEngine,
                    comparisonService);

            policyService.setProgramService(programService);

            MongoDatabase db = MongoConfig.getDatabase();

            // Prepare the MySQL -> MongoDB worker.
            // Do not start it until bidirectional sync safeguards are complete.
            if (syncWorkerRunner == null) {
                syncWorkerRunner = new SyncWorkerRunner(db);
                syncWorkerRunner.start();
            }

            // Start MongoDB -> MySQL change-stream synchronization.
            if (mongoChangeStreamListener == null) {
                mongoChangeStreamListener = new MongoToMySqlChangeStreamListener(db);

                mongoChangeStreamListener.start();
            }

            mongoOnline.set(true);

            if (onConnected != null) {
                onConnected.run();
            }
        });

        task.setOnFailed(event -> {
            Throwable failure = task.getException();

            mongoOnline.set(false);

            if (failure != null) {
                logFailure("MongoDB connection failed", failure);

                showToast(Toast.error(
                        "Database Connection Failed",
                        MongoConfig.sanitize(failure.getMessage()),
                        null));

                if (onError != null) {
                    onError.accept(failure);
                }
            }
        });

        executor.execute(task);
    }

    /**
     * Refreshes applicants asynchronously from MongoDB.
     */
    public void loadApplicantsAsync(
            Runnable onComplete,
            Consumer<Throwable> onError) {

        runAsync(() -> {
            try {
                if (applicantRepository != null) {
                    List<Document> docs = applicantRepository.findAll();

                    List<Applicant> loaded = docs.stream()
                            .map(ApplicantDocumentMapper::toApplicant)
                            .toList();

                    Platform.runLater(() -> {
                        applicants.setAll(loaded);
                        mongoOnline.set(true);

                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                }
            } catch (Exception e) {
                logFailure("Could not load applicants", e);

                Platform.runLater(() -> {
                    mongoOnline.set(false);

                    if (onError != null) {
                        onError.accept(e);
                    } else {
                        showToast(Toast.error(
                                "Applicant Load Failed",
                                e.getMessage() == null
                                        ? "Could not load applicants."
                                        : MongoConfig.sanitize(
                                                e.getMessage()),
                                null));
                    }
                });
            }
        });
    }

    /**
     * Refreshes policies asynchronously from the repository.
     */
    public void loadPoliciesAsync(Runnable onComplete) {
        runAsync(() -> {
            List<Policy> all = policyRepository.findAll();

            Platform.runLater(() -> {
                policies.setAll(all);

                if (selectedPolicy.get() == null && !policies.isEmpty()) {
                    selectedPolicy.set(policies.get(0));

                    selectedVersion.set(
                            policies.get(0)
                                    .getLatestVersion()
                                    .orElse(null));
                }

                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    public void loadEvaluationResultsAsync(
            String policyId,
            int versionNumber,
            Consumer<List<EvaluationResult>> onComplete,
            Consumer<Throwable> onError) {

        Task<List<EvaluationResult>> task = new Task<>() {

            @Override
            protected List<EvaluationResult> call() {
                return evaluationResultRepository
                        .findByPolicyAndVersion(policyId, versionNumber);
            }
        };

        task.setOnSucceeded(event -> {
            List<EvaluationResult> loaded = task.getValue();

            evaluationResults.setAll(loaded);

            if (onComplete != null) {
                onComplete.accept(loaded);
            }
        });

        task.setOnFailed(event -> {
            Throwable failure = task.getException();

            if (failure != null) {
                logFailure("Could not load evaluation results", failure);

                if (onError != null) {
                    onError.accept(failure);
                } else {
                    showToast(Toast.error(
                            "Evaluation History Failed",
                            MongoConfig.sanitize(failure.getMessage()),
                            null));
                }
            }
        });

        executor.execute(task);
    }

    public void loadEvaluationResultsAsync(
            String policyId,
            int versionNumber,
            Consumer<List<EvaluationResult>> onComplete) {

        loadEvaluationResultsAsync(
                policyId,
                versionNumber,
                onComplete,
                null);
    }

    public void markResultsChanged() {
        resultsChanged.set(resultsChanged.get() + 1);
    }

    public void showToast(Toast toast) {
        if (toastConsumer != null) {
            Platform.runLater(() -> toastConsumer.accept(toast));
        }
    }

    public void setToastConsumer(Consumer<Toast> consumer) {
        this.toastConsumer = consumer;
    }

    /**
     * Releases synchronization and database resources.
     */
    public void shutdown() {
        try {
            if (syncWorkerRunner != null) {
                syncWorkerRunner.close();
                syncWorkerRunner = null;
            }
            if (mongoChangeStreamListener != null) {
                mongoChangeStreamListener.close();
                mongoChangeStreamListener = null;
            }

            executor.shutdownNow();
            MongoConfig.close();

        } catch (Exception e) {
            System.err.println(
                    "Error during AppContext shutdown: "
                            + MongoConfig.sanitize(e.getMessage()));
        }
    }

    public static void logFailure(String message, Throwable failure) {
        System.err.println(
                message + ": "
                        + MongoConfig.sanitize(failure.getMessage()));

        for (StackTraceElement element : failure.getStackTrace()) {
            System.err.println("\tat " + element);
        }

        for (Throwable suppressed : failure.getSuppressed()) {
            logFailure("Suppressed", suppressed);
        }

        if (failure.getCause() != null) {
            logFailure("Caused by", failure.getCause());
        }
    }

    private record MongoRepositories(
            ApplicantRepository applicants,
            PolicyRepository policies,
            PolicyVersionRepository versions,
            EvaluationRepository evaluations,
            ProgramRepository programs) {
    }

    // Getters

    public ExecutorService getExecutor() {
        return executor;
    }

    public ApplicantRepository getApplicantRepository() {
        return applicantRepository;
    }

    public PolicyRepository getPolicyRepository() {
        return policyRepository;
    }

    public PolicyVersionRepository getPolicyVersionRepository() {
        return policyVersionRepository;
    }

    public EvaluationRepository getEvaluationRepository() {
        return evaluationRepository;
    }

    public EvaluationResultRepository getEvaluationResultRepository() {
        return evaluationResultRepository;
    }

    public DecisionEngine getDecisionEngine() {
        return decisionEngine;
    }

    public ComparisonService getComparisonService() {
        return comparisonService;
    }

    public PolicyService getPolicyService() {
        return policyService;
    }

    public ProgramRepository getProgramRepository() {
        return programRepository;
    }

    public ProgramService getProgramService() {
        return programService;
    }

    public ObservableList<Applicant> getApplicants() {
        return applicants;
    }

    public ObservableList<Policy> getPolicies() {
        return policies;
    }

    public ObservableList<EvaluationResult> getEvaluationResults() {
        return evaluationResults;
    }

    public ObjectProperty<Policy> selectedPolicyProperty() {
        return selectedPolicy;
    }

    public ObjectProperty<PolicyVersion> selectedVersionProperty() {
        return selectedVersion;
    }

    public ObjectProperty<PolicyVersion> activeVersionProperty() {
        return activeVersion;
    }

    public ObjectProperty<Applicant> selectedApplicantProperty() {
        return selectedApplicant;
    }

    public ObjectProperty<com.ruleweaver.model.ProgramConfiguration> selectedProgramProperty() {
        return selectedProgram;
    }

    public BooleanProperty mongoOnlineProperty() {
        return mongoOnline;
    }

    public BooleanProperty reducedMotionProperty() {
        return reducedMotion;
    }

    public LongProperty resultsChangedProperty() {
        return resultsChanged;
    }
}
