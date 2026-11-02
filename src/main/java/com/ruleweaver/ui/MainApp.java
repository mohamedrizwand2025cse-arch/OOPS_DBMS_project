package com.ruleweaver.ui;

import com.ruleweaver.ui.auth.LoginView;
import com.ruleweaver.ui.auth.Session;
import com.ruleweaver.ui.components.Toast;
import com.ruleweaver.ui.components.MongoConnectionDialog;
import com.ruleweaver.ui.shell.AppHeader;
import com.ruleweaver.ui.shell.Sidebar;
import com.ruleweaver.ui.shell.ViewRouter;
import com.ruleweaver.ui.util.SampleDataSeeder;
import com.ruleweaver.ui.util.Theme;
import com.ruleweaver.ui.views.*;
import com.ruleweaver.config.MongoConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main JavaFX Application Shell & Composition Root for RuleWeaver X.
 */
public class MainApp extends Application {

    private AppContext appContext;
    private ViewRouter viewRouter;
    private AppHeader header;
    private final VBox toastContainer = new VBox(10);

    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }

    @Override
    public void start(Stage primaryStage) {
        appContext = AppContext.getInstance();

        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.setOnCloseRequest(e -> appContext.shutdown());
        showLogin(primaryStage);
    }

    private void showLogin(Stage primaryStage) {
        LoginView loginView = new LoginView(primaryStage, user -> showApp(primaryStage));
        Scene loginScene = new Scene(loginView.getRoot(), 1220, 780);
        Theme.apply(loginScene);
        primaryStage.setTitle("RuleWeaver X — Sign in");
        primaryStage.setScene(loginScene);
        primaryStage.show();
    }

    private void showApp(Stage primaryStage) {
        appContext = AppContext.getInstance();

        // 1. Root Shell Layout
        StackPane rootLayer = new StackPane();
        rootLayer.getStyleClass().add("app-shell");

        BorderPane shellPane = new BorderPane();

        // Center Content Area
        StackPane centerPane = new StackPane();
        viewRouter = new ViewRouter(centerPane);

        // Views Instantiation
        PoliciesView policiesView = new PoliciesView(() -> navigateTo(Sidebar.Destination.DASHBOARD));
        ApplicantsView applicantsView = new ApplicantsView();
        DashboardView dashboardView = new DashboardView((policy, simulatedVersion) -> {
            navigateTo(Sidebar.Destination.POLICIES);
            policiesView.showNewVersionDraft(policy, simulatedVersion);
        }, this::navigateTo, () -> {
            navigateTo(Sidebar.Destination.APPLICANTS);
            Platform.runLater(applicantsView::openAddApplicantDialog);
        });
        ProgramManagerView programManagerView = new ProgramManagerView(this::navigateTo);
        EvaluationView evaluationView = new EvaluationView();
        ComparisonView comparisonView = new ComparisonView();

        viewRouter.registerView(Sidebar.Destination.DASHBOARD, dashboardView);
        viewRouter.registerView(Sidebar.Destination.APPLICANTS, applicantsView);
        viewRouter.registerView(Sidebar.Destination.PROGRAMS, programManagerView);
        viewRouter.registerView(Sidebar.Destination.POLICIES, policiesView);
        viewRouter.registerView(Sidebar.Destination.EVALUATION, evaluationView);
        viewRouter.registerView(Sidebar.Destination.COMPARISON, comparisonView);

        // Sidebar Navigation
        Sidebar sidebar = new Sidebar(
                this::navigateTo,
                () -> {
                    Session.end();
                    showLogin(primaryStage);
                });

        // Header
        header = new AppHeader(() -> {
            if (!appContext.mongoOnlineProperty().get()) {
                showMongoConnectionDialog(primaryStage);
                return;
            }
            BaseView cur = viewRouter.getCurrentView();
            if (cur != null)
                cur.onShow();
            appContext.showToast(Toast.info("Data Refreshed", "Screen state reloaded successfully.", null));
        });

        shellPane.setLeft(sidebar);
        shellPane.setTop(header);
        shellPane.setCenter(centerPane);

        // Toast Notification Overlay
        toastContainer.setAlignment(Pos.BOTTOM_RIGHT);
        toastContainer.setMouseTransparent(false);
        toastContainer.setPickOnBounds(false);
        toastContainer.setStyle("-fx-padding: 24px;");

        rootLayer.getChildren().addAll(shellPane, toastContainer);

        // Register Global Toast Listener
        appContext.setToastConsumer(toast -> {
            toastContainer.getChildren().add(toast);
        });

        // 2. Scene & Stylesheet
        Scene scene = new Scene(rootLayer, 1220, 780);
        Theme.apply(scene);

        // 3. Stage Setup
        primaryStage.setTitle("RuleWeaver X — Explainable Decision Engine & Time Machine");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(e -> {
            appContext.shutdown();
        });

        if (Boolean.getBoolean("ruleweaver.styledebug")) {
            StyleDebug.install();
        }

        // Initial Navigation
        viewRouter.navigateTo(Sidebar.Destination.DASHBOARD);
        primaryStage.show();

        if (Boolean.getBoolean("ruleweaver.styledebug")) {
            Platform.runLater(() -> {
                StyleDebug.dump("Dashboard", primaryStage);
                viewRouter.navigateTo(Sidebar.Destination.POLICIES);
                Platform.runLater(() -> {
                    StyleDebug.dump("Policies", primaryStage);
                    viewRouter.navigateTo(Sidebar.Destination.DASHBOARD);
                });
            });
        }

        if (MongoConfig.getConfiguredUri().isPresent()) {
            connectAndLoadData(primaryStage);
        } else {
            showMongoConnectionDialog(primaryStage);
        }
    }

    private void bootstrapInitialData() {
        appContext.loadApplicantsAsync(() -> {
            appContext.loadPoliciesAsync(() -> {
                // If DB is empty, auto-seed sample demo data
                if (appContext.getApplicants().isEmpty() || appContext.getPolicies().isEmpty()) {
                    handleSeedDemoData();
                } else {
                    BaseView cur = viewRouter.getCurrentView();
                    if (cur != null)
                        cur.onShow();
                }
            });
        }, error -> {
            appContext.showToast(Toast.error(
                    "MongoDB Notice",
                    "Could not load applicants from MongoDB Atlas: "
                            + com.ruleweaver.config.MongoConfig.sanitize(error.getMessage()),
                    null));
        });
    }

    private void navigateTo(Sidebar.Destination destination) {
        System.out.println("CLICKED DESTINATION: " + destination);

        System.out.println("SQL CONSOLE REGISTERED: "
                + (viewRouter.getCurrentView() != null));

        try {
            viewRouter.navigateTo(destination);

            System.out.println("CURRENT VIEW: "
                    + (viewRouter.getCurrentView() == null
                            ? "null"
                            : viewRouter.getCurrentView().getClass().getName()));

            if (header != null) {
                header.setTitle(destination.getTitle(), getSubtitleFor(destination));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectAndLoadData(Stage owner) {
        appContext.connectToDatabase(this::bootstrapInitialData,
                error -> showMongoConnectionDialog(owner));
    }

    private void showMongoConnectionDialog(Stage owner) {
        MongoConnectionDialog.show(owner, () -> bootstrapInitialData(), () -> {
            appContext.showToast(Toast.info(
                    "Offline Mode",
                    "MongoDB was not connected. Use Refresh to connect and load applicant data.",
                    null));
        });
    }

    private void handleSeedDemoData() {
        if (!appContext.mongoOnlineProperty().get()) {
            showMongoConnectionDialog((Stage) header.getScene().getWindow());
            return;
        }
        appContext.showToast(Toast.info("Seeding Data", "Populating demo candidates and policies...", null));
        SampleDataSeeder.seedDemoData(() -> {
            appContext.showToast(Toast.success(
                    "Demo Data Loaded",
                    "Seeded candidates (Alice, Bob, Carol, Dave, Eve) and Policies (v1 & v2).",
                    null));
            BaseView cur = viewRouter.getCurrentView();
            if (cur != null)
                cur.onShow();
        });
    }

    private void confirmClearDemoData() {
        if (!appContext.mongoOnlineProperty().get()) {
            showMongoConnectionDialog((Stage) header.getScene().getWindow());
            return;
        }
        javafx.scene.control.Alert confirmation = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Remove the demo applicants, their saved evaluations, and the POL-INTERN policy and versions?",
                javafx.scene.control.ButtonType.CANCEL,
                javafx.scene.control.ButtonType.OK);
        confirmation.setTitle("Clear Demo Data");
        confirmation.setHeaderText("Clear seeded records");
        confirmation.initOwner((Stage) header.getScene().getWindow());
        confirmation.initModality(javafx.stage.Modality.WINDOW_MODAL);
        Theme.apply(confirmation);
        confirmation.showAndWait().filter(javafx.scene.control.ButtonType.OK::equals).ifPresent(button -> {
            SampleDataSeeder.clearDemoData(() -> {
                appContext.showToast(Toast.success(
                        "Demo Data Cleared", "Seeded applicants, evaluations, and policy versions were removed.",
                        null));
                BaseView current = viewRouter.getCurrentView();
                if (current != null)
                    current.onShow();
            });
        });
    }

    private String getSubtitleFor(Sidebar.Destination dest) {
        return switch (dest) {
            case DASHBOARD ->
                "Real-time decision distribution, candidate velocity, and version metrics";
            case APPLICANTS ->
                "Manage applicant pool and evaluate eligibility credentials";
            case PROGRAMS ->
                "Configure program templates and applicant field schemas";
            case POLICIES ->
                "Configure governance criteria, version snapshots, and threshold rules";
            case EVALUATION ->
                "Evaluate candidate pool against governance policies with complete explainability";
            case COMPARISON ->
                "Simulate policy shifts and inspect candidate decision flips between version epochs";

        };
    }

    @Override
    public void stop() {
        if (appContext != null) {
            appContext.shutdown();
        }
    }
}
