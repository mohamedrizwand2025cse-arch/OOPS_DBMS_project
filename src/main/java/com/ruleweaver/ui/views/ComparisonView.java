package com.ruleweaver.ui.views;

import com.ruleweaver.model.*;
import com.ruleweaver.config.MongoConfig;
import com.ruleweaver.service.PolicyVersionDiff;
import com.ruleweaver.ui.AppContext;
import com.ruleweaver.ui.components.*;
import com.ruleweaver.ui.util.Theme;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Screen 5: 'The Decision Time Machine' Policy Comparison Inspector.
 * Computes decision shifts across version transitions and pinpoints root-cause rule changes.
 */
public class ComparisonView implements BaseView {

    private final StackPane rootPane = new StackPane();
    private final ComboBox<Policy> policyCombo = new ComboBox<>();
    private final ComboBox<PolicyVersion> versionACombo = new ComboBox<>();
    private final ComboBox<PolicyVersion> versionBCombo = new ComboBox<>();

    private final TableView<ApplicantDiff> diffTable = new TableView<>();
    private final ObservableList<ApplicantDiff> diffList = FXCollections.observableArrayList();

    private final Label changedCountLabel = new Label("—");
    private final Label lostCountLabel = new Label("—");
    private final Label gainedCountLabel = new Label("—");

    private final ComboBox<Policy> historyPolicyCombo = new ComboBox<>();
    private final TextField historySearchField = new TextField();
    private final TableView<PolicyVersion> historyTable = new TableView<>();
    private final ObservableList<PolicyVersion> historyVersions = FXCollections.observableArrayList();
    private final FilteredList<PolicyVersion> filteredHistoryVersions = new FilteredList<>(historyVersions);
    private final VBox versionDetail = new VBox(12);
    private final TableView<PolicyVersionDiff.Change> configurationDiffTable = new TableView<>();
    private final ObservableList<PolicyVersionDiff.Change> configurationChanges = FXCollections.observableArrayList();
    private final Label configurationDiffSummary = new Label("Select two saved versions to compare their configurations.");
    private final CheckBox showUnchangedRules = new CheckBox("Show unchanged rules");

    private final ComboBox<Policy> auditPolicyCombo = new ComboBox<>();
    private final ComboBox<PolicyVersion> auditVersionCombo = new ComboBox<>();
    private final ComboBox<String> auditOutcomeCombo = new ComboBox<>();
    private final ComboBox<String> auditDateRangeCombo = new ComboBox<>();
    private final TextField auditSearchField = new TextField();
    private final TableView<EvaluationResult> auditTable = new TableView<>();
    private final ObservableList<EvaluationResult> auditResults = FXCollections.observableArrayList();
    private final VBox evaluationDetail = new VBox(12);
    private final Label auditStateLabel = new Label("Select a policy version to inspect its stored decisions.");
    private final Button auditRetryButton = new Button("Retry");
    private int auditRequestId;

    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Comparing policy versions in the Time Machine...");

    public ComparisonView() {
        buildUI();
    }

    private void buildUI() {
        VBox contentBox = new VBox(18);
        contentBox.setPadding(new Insets(24, 28, 24, 28));

        HBox toolbar = createToolbar();
        TabPane workflows = new TabPane();
        workflows.getStyleClass().add("time-machine-tabs");
        workflows.getTabs().addAll(
                createHistoryTab(),
                createComparisonTab(),
                createAuditTab());
        VBox.setVgrow(workflows, Priority.ALWAYS);

        contentBox.getChildren().addAll(toolbar, workflows);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        rootPane.getChildren().addAll(contentBox, loadingOverlay);

        setupListeners();
    }

    private Tab createHistoryTab() {
        historyPolicyCombo.setItems(AppContext.getInstance().getPolicies());
        configurePolicyCombo(historyPolicyCombo, "Select policy");

        historySearchField.setPromptText("Search versions or rules");
        historySearchField.getStyleClass().add("time-machine-search");
        historySearchField.setPrefWidth(250);
        HBox filters = new HBox(12, fieldLabel("POLICY"), historyPolicyCombo,
                fieldLabel("SEARCH"), historySearchField);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("time-machine-toolbar");

        historyTable.setItems(filteredHistoryVersions);
        historyTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> showVersionDetails(selected));

        ScrollPane detailsScroll = new ScrollPane(versionDetail);
        detailsScroll.setFitToWidth(true);
        detailsScroll.getStyleClass().add("time-machine-detail-scroll");
        detailsScroll.setMinWidth(280);
        detailsScroll.setPrefWidth(370);
        versionDetail.getStyleClass().add("time-machine-detail");

        SplitPane columns = new SplitPane(createHistoryTableCard(), createDetailCard(
                "SELECTED VERSION", detailsScroll));
        columns.setDividerPositions(0.62);
        VBox.setVgrow(columns, Priority.ALWAYS);

        VBox panel = new VBox(14, filters, columns);
        panel.setPadding(new Insets(18, 0, 0, 0));
        VBox.setVgrow(panel, Priority.ALWAYS);
        Tab tab = new Tab("Version History", panel);
        tab.setClosable(false);
        return tab;
    }

    private Tab createComparisonTab() {
        VBox comparisonContent = new VBox(14);
        comparisonContent.getStyleClass().add("time-machine-comparison-content");
        comparisonContent.setFillWidth(true);
        HBox controlBar = createControlBar();
        VBox configurationCard = createConfigurationComparison();
        HBox summaryRow = createSummaryCards();
        VBox tableCard = createTableCard();
        VBox recordedComparison = new VBox(8, summaryRow, tableCard);
        recordedComparison.getStyleClass().add("time-machine-recorded-comparison");
        VBox.setVgrow(tableCard, Priority.ALWAYS);
        comparisonContent.getChildren().addAll(controlBar, configurationCard, recordedComparison);
        ScrollPane scroll = new ScrollPane(comparisonContent);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("time-machine-content-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Tab tab = new Tab("Compare Versions", scroll);
        tab.setClosable(false);
        return tab;
    }

    private Tab createAuditTab() {
        auditPolicyCombo.setItems(AppContext.getInstance().getPolicies());
        configurePolicyCombo(auditPolicyCombo, "Select policy");
        auditPolicyCombo.setPrefWidth(165);
        auditVersionCombo.setPrefWidth(100);
        setupVersionCellFactory(auditVersionCombo);
        auditOutcomeCombo.getItems().setAll("All outcomes", "Approved", "Rejected", "Unknown");
        auditOutcomeCombo.setValue("All outcomes");
        auditOutcomeCombo.setPrefWidth(110);
        auditDateRangeCombo.getItems().setAll("All time", "Last 7 days", "Last 30 days");
        auditDateRangeCombo.setValue("All time");
        auditDateRangeCombo.setPrefWidth(105);
        auditSearchField.setPromptText("Search applicant");
        auditSearchField.getStyleClass().add("time-machine-search");
        auditSearchField.setPrefWidth(145);

        HBox filters = new HBox(12,
                fieldLabel("POLICY"), auditPolicyCombo,
                fieldLabel("VERSION"), auditVersionCombo,
                fieldLabel("OUTCOME"), auditOutcomeCombo);
        filters.getChildren().addAll(fieldLabel("DATE"), auditDateRangeCombo,
                fieldLabel("SEARCH"), auditSearchField);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("time-machine-toolbar");

        configureAuditTable();
        auditTable.setItems(auditResults);
        auditTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> showEvaluationDetails(selected));

        auditRetryButton.getStyleClass().add("btn-secondary");
        auditRetryButton.setVisible(false);
        auditRetryButton.setManaged(false);
        auditRetryButton.setOnAction(event -> loadAuditRecords());
        HBox auditStateRow = new HBox(10, auditStateLabel, auditRetryButton);
        auditStateRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(auditStateLabel, Priority.ALWAYS);
        VBox tableContent = new VBox(8, auditStateRow, auditTable);
        tableContent.getStyleClass().add("time-machine-panel");
        VBox.setVgrow(auditTable, Priority.ALWAYS);

        ScrollPane detailScroll = new ScrollPane(evaluationDetail);
        detailScroll.setFitToWidth(true);
        detailScroll.getStyleClass().add("time-machine-detail-scroll");
        detailScroll.setMinWidth(280);
        detailScroll.setPrefWidth(370);
        evaluationDetail.getStyleClass().add("time-machine-detail");

        SplitPane columns = new SplitPane(tableContent, createDetailCard("RECORDED DECISION", detailScroll));
        columns.setDividerPositions(0.62);
        VBox.setVgrow(columns, Priority.ALWAYS);
        VBox panel = new VBox(14, filters, columns);
        panel.setPadding(new Insets(18, 0, 0, 0));
        VBox.setVgrow(panel, Priority.ALWAYS);
        Tab tab = new Tab("Decision Log", panel);
        tab.setClosable(false);
        return tab;
    }

    private void configurePolicyCombo(ComboBox<Policy> combo, String prompt) {
        combo.setPromptText(prompt);
        combo.setPrefWidth(260);
        combo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Policy policy, boolean empty) {
                super.updateItem(policy, empty);
                setText(empty || policy == null ? null : policy.getName());
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Policy policy, boolean empty) {
                super.updateItem(policy, empty);
                setText(empty || policy == null ? prompt : policy.getName());
            }
        });
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("time-machine-filter-label");
        return label;
    }

    private VBox createHistoryTableCard() {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("card", "time-machine-panel");
        Label heading = new Label("Saved policy versions");
        heading.getStyleClass().add("section-header");
        Label description = new Label("Immutable snapshots stored for this policy.");
        description.getStyleClass().add("section-subtitle");
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPlaceholder(new Label("No saved policy versions are available."));
        historyTable.setRowFactory(table -> {
            TableRow<PolicyVersion> row = new TableRow<>();
            row.setPrefHeight(48);
            return row;
        });

        TableColumn<PolicyVersion, Number> versionColumn = new TableColumn<>("VERSION");
        versionColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleIntegerProperty(
                cell.getValue().getVersionNumber()));
        versionColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "v" + value.intValue());
            }
        });
        versionColumn.setPrefWidth(80);

        TableColumn<PolicyVersion, LocalDateTime> dateColumn = new TableColumn<>("SAVED");
        dateColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(
                cell.getValue().getCreatedAt()));
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : formatDateTime(value));
            }
        });
        dateColumn.setPrefWidth(170);

        TableColumn<PolicyVersion, Number> rulesColumn = new TableColumn<>("RULES");
        rulesColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleIntegerProperty(
                cell.getValue().getConditions().size()));
        rulesColumn.setPrefWidth(75);

        historyTable.getColumns().setAll(versionColumn, dateColumn, rulesColumn);
        historyTable.getSortOrder().setAll(versionColumn);
        versionColumn.setSortType(TableColumn.SortType.DESCENDING);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        card.getChildren().addAll(heading, description, historyTable);
        return card;
    }

    private VBox createDetailCard(String headingText, Node details) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("card", "time-machine-panel");
        Label heading = new Label(headingText);
        heading.getStyleClass().add("caption-label");
        VBox.setVgrow(details, Priority.ALWAYS);
        card.getChildren().addAll(heading, details);
        return card;
    }

    private void showVersionDetails(PolicyVersion version) {
        versionDetail.getChildren().clear();
        if (version == null) {
            versionDetail.getChildren().add(stateText("Select a saved version to inspect its stored configuration."));
            return;
        }
        Label title = new Label("Version " + version.getVersionNumber());
        title.getStyleClass().add("time-machine-detail-title");
        versionDetail.getChildren().add(title);

        Policy policy = historyPolicyCombo.getValue();
        if (policy != null && policy.getActiveVersionNumber() == version.getVersionNumber()) {
            versionDetail.getChildren().add(new StatusBadge("ACTIVE VERSION", StatusBadge.Type.INFO));
        }
        addDetailSection(versionDetail, "SAVED", formatDateTime(version.getCreatedAt()));
        addDetailSection(versionDetail, "DESCRIPTION",
                blankAsUnavailable(version.getDescription(), "No description was stored."));
        addDetailSection(versionDetail, "STORED CONFIGURATION",
                version.getConditions().isEmpty()
                        ? "No conditions are stored in this version."
                        : version.getConditions().size() + " condition(s)");
        for (var condition : version.getConditions()) {
            Label rule = new Label(blankAsUnavailable(condition.getMessage(), "Condition details unavailable."));
            rule.getStyleClass().add("time-machine-rule");
            rule.setWrapText(true);
            versionDetail.getChildren().add(rule);
        }
        versionDetail.getChildren().add(stateText(
                "Publisher change metadata is not stored; this view shows only the saved version snapshot."));
    }

    private VBox createConfigurationComparison() {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("card", "time-machine-panel", "time-machine-config-diff");
        HBox headingRow = new HBox(12);
        headingRow.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label("Configuration changes");
        heading.getStyleClass().add("section-header");
        HBox.setHgrow(heading, Priority.ALWAYS);
        showUnchangedRules.setSelected(true);
        showUnchangedRules.getStyleClass().add("time-machine-checkbox");
        headingRow.getChildren().addAll(heading, showUnchangedRules);

        configurationDiffSummary.getStyleClass().add("section-subtitle");
        configurationDiffTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        configurationDiffTable.setMinHeight(130);
        configurationDiffTable.setPlaceholder(
                new Label("Select two versions to compare their saved conditions."));
        configurationDiffTable.setRowFactory(table -> {
            TableRow<PolicyVersionDiff.Change> row = new TableRow<>();
            row.setMinHeight(48);
            row.setPrefHeight(-1);
            return row;
        });

        TableColumn<PolicyVersionDiff.Change, String> changeColumn = new TableColumn<>("CHANGE");
        changeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                titleCase(cell.getValue().type().name())));
        changeColumn.setMinWidth(118);
        changeColumn.setPrefWidth(138);
        changeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().removeAll("time-machine-diff-added",
                        "time-machine-diff-removed", "time-machine-diff-modified",
                        "time-machine-diff-unchanged");
                if (!empty && value != null) {
                    getStyleClass().add("time-machine-diff-" + value.toLowerCase(java.util.Locale.ROOT));
                }
            }
        });

        TableColumn<PolicyVersionDiff.Change, String> baselineColumn = new TableColumn<>("BASELINE");
        baselineColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().baseline()));
        baselineColumn.setCellFactory(column -> wrappedTextCell());
        baselineColumn.setMinWidth(260);
        baselineColumn.setPrefWidth(360);

        TableColumn<PolicyVersionDiff.Change, String> targetColumn = new TableColumn<>("TARGET");
        targetColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().target()));
        targetColumn.setCellFactory(column -> wrappedTextCell());
        targetColumn.setMinWidth(260);
        targetColumn.setPrefWidth(360);
        configurationDiffTable.getColumns().setAll(changeColumn, baselineColumn, targetColumn);
        configurationDiffTable.setItems(configurationChanges);
        VBox.setVgrow(configurationDiffTable, Priority.ALWAYS);
        card.getChildren().addAll(headingRow, configurationDiffSummary, configurationDiffTable);
        return card;
    }

    private TableCell<PolicyVersionDiff.Change, String> wrappedTextCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setWrapText(true);
                setAlignment(Pos.CENTER_LEFT);
                setText(empty ? null : blankAsUnavailable(value, "—"));
                setTooltip(empty || value == null || value.isBlank() ? null : new Tooltip(value));
            }
        };
    }

    private String titleCase(String value) {
        String lowercase = value.toLowerCase(java.util.Locale.ROOT);
        return lowercase.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + lowercase.substring(1);
    }

    private void updateConfigurationDiff() {
        PolicyVersion baseline = versionACombo.getValue();
        PolicyVersion target = versionBCombo.getValue();
        configurationChanges.clear();
        if (baseline == null || target == null) {
            configurationDiffSummary.setText("Select two saved versions to compare their configurations.");
            return;
        }
        if (baseline.getVersionNumber() == target.getVersionNumber()) {
            configurationDiffSummary.setText("Both selectors reference the same saved version.");
        } else {
            configurationDiffSummary.setText("Policy " + baseline.getPolicyId() + " - v"
                    + baseline.getVersionNumber() + " compared with v" + target.getVersionNumber());
        }
        List<PolicyVersionDiff.Change> changes = PolicyVersionDiff.compare(baseline, target);
        long changedRules = changes.stream()
                .filter(change -> change.type() != PolicyVersionDiff.ChangeType.UNCHANGED)
                .count();
        configurationDiffSummary.setText(configurationDiffSummary.getText()
                + " - " + changedRules + " added, removed or modified rule(s).");
        configurationChanges.setAll(showUnchangedRules.isSelected() ? changes
                : changes.stream().filter(change ->
                        change.type() != PolicyVersionDiff.ChangeType.UNCHANGED).toList());
    }

    private void addDetailSection(VBox parent, String heading, String value) {
        Label title = fieldLabel(heading);
        Label content = new Label(value);
        content.getStyleClass().add("time-machine-detail-value");
        content.setWrapText(true);
        parent.getChildren().addAll(title, content);
    }

    private Label stateText(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("time-machine-state-text");
        label.setWrapText(true);
        return label;
    }

    private String blankAsUnavailable(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "Timestamp unavailable"
                : DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").format(value);
    }

    private void configureAuditTable() {
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auditTable.setPlaceholder(new Label("No stored decision records for this version."));
        auditTable.setRowFactory(table -> {
            TableRow<EvaluationResult> row = new TableRow<>();
            row.setPrefHeight(48);
            return row;
        });

        TableColumn<EvaluationResult, String> applicantColumn = new TableColumn<>("APPLICANT");
        applicantColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                blankAsUnavailable(cell.getValue().getApplicantName(), "Unnamed applicant")));
        applicantColumn.setPrefWidth(190);

        TableColumn<EvaluationResult, String> outcomeColumn = new TableColumn<>("RECORDED OUTCOME");
        outcomeColumn.setCellValueFactory(cell -> new SimpleStringProperty(outcomeText(cell.getValue())));
        outcomeColumn.setPrefWidth(150);
        outcomeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().removeAll("time-machine-outcome-pass",
                        "time-machine-outcome-fail", "time-machine-outcome-unknown");
                if (!empty && value != null) {
                    getStyleClass().add(value.equals("Approved") ? "time-machine-outcome-pass"
                            : value.equals("Rejected") ? "time-machine-outcome-fail"
                            : "time-machine-outcome-unknown");
                }
            }
        });

        TableColumn<EvaluationResult, String> evaluatedColumn = new TableColumn<>("RECORDED AT");
        evaluatedColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                formatDateTime(cell.getValue().getEvaluatedOn())));
        evaluatedColumn.setPrefWidth(170);

        auditTable.getColumns().setAll(applicantColumn, outcomeColumn, evaluatedColumn);
        auditTable.getSortOrder().setAll(evaluatedColumn);
        evaluatedColumn.setSortType(TableColumn.SortType.DESCENDING);
    }

    private String outcomeText(EvaluationResult result) {
        if (!result.hasDecisionOutcome()) {
            return "Unknown";
        }
        return result.isOverallDecision() ? "Approved" : "Rejected";
    }

    private void showEvaluationDetails(EvaluationResult result) {
        evaluationDetail.getChildren().clear();
        if (result == null) {
            evaluationDetail.getChildren().add(stateText("Select a stored decision to inspect its details."));
            return;
        }
        Label title = new Label(blankAsUnavailable(result.getApplicantName(), "Unnamed applicant"));
        title.getStyleClass().add("time-machine-detail-title");
        evaluationDetail.getChildren().add(title);
        evaluationDetail.getChildren().add(new StatusBadge(outcomeText(result), result.hasDecisionOutcome()
                ? result.isOverallDecision() ? StatusBadge.Type.PASS : StatusBadge.Type.FAIL
                : StatusBadge.Type.WARNING));
        addDetailSection(evaluationDetail, "APPLICANT ID",
                blankAsUnavailable(result.getApplicantId(), "Not stored"));
        addDetailSection(evaluationDetail, "POLICY",
                blankAsUnavailable(result.getPolicyName(), blankAsUnavailable(result.getPolicyId(), "Not stored")));
        addDetailSection(evaluationDetail, "SAVED VERSION",
                result.getPolicyVersionNumber() > 0 ? "Version " + result.getPolicyVersionNumber() : "Not stored");
        addDetailSection(evaluationDetail, "EVALUATED",
                formatDateTime(result.getEvaluatedOn()));
        addDetailSection(evaluationDetail, "RECORD TYPE", "Stored historical evaluation; no replay was run.");

        if (result.getApplicantInputSnapshot().isEmpty()) {
            addDetailSection(evaluationDetail, "APPLICANT SNAPSHOT", "No input snapshot was stored.");
        } else {
            evaluationDetail.getChildren().add(fieldLabel("APPLICANT SNAPSHOT"));
            result.getApplicantInputSnapshot().forEach((key, value) ->
                    addDetailSection(evaluationDetail, key, String.valueOf(value)));
        }

        evaluationDetail.getChildren().add(fieldLabel("CONDITION RESULTS"));
        if (result.getConditionResults().isEmpty()) {
            evaluationDetail.getChildren().add(stateText("No condition-level results were stored."));
        } else {
            for (ConditionResult condition : result.getConditionResults()) {
                VBox conditionCard = new VBox(3);
                conditionCard.getStyleClass().add("time-machine-condition-card");
                Label name = new Label(blankAsUnavailable(condition.getConditionName(), "Condition"));
                name.getStyleClass().add("time-machine-detail-value");
                Label value = new Label(blankAsUnavailable(condition.getExplanation(),
                        "Stored outcome: " + (condition.isPassed() ? "passed" : "failed")));
                value.getStyleClass().add("time-machine-state-text");
                value.setWrapText(true);
                conditionCard.getChildren().addAll(name, value);
                if (condition.getActualValue() != null) {
                    addDetailSection(conditionCard, "ACTUAL", String.valueOf(condition.getActualValue()));
                }
                if (condition.getRequiredValue() != null) {
                    addDetailSection(conditionCard, "REQUIRED", String.valueOf(condition.getRequiredValue()));
                }
                evaluationDetail.getChildren().add(conditionCard);
            }
        }
    }

    private void loadAuditRecords() {
        Policy policy = auditPolicyCombo.getValue();
        PolicyVersion version = auditVersionCombo.getValue();
        int requestId = ++auditRequestId;
        auditResults.clear();
        evaluationDetail.getChildren().setAll(stateText("Select a stored decision to inspect its details."));
        if (policy == null || version == null) {
            auditStateLabel.setText("Select a policy and version to load stored decision records.");
            setAuditRetryVisible(false);
            return;
        }
        if (!AppContext.getInstance().mongoOnlineProperty().get()) {
            auditStateLabel.setText("Database offline. Stored Atlas decision history is unavailable.");
            auditTable.setPlaceholder(new Label("Connect to MongoDB to inspect historical decisions."));
            setAuditRetryVisible(false);
            return;
        }
        setAuditRetryVisible(false);
        auditStateLabel.setText("Loading stored decisions for " + policy.getName()
                + " - Version " + version.getVersionNumber() + "...");
        AppContext.getInstance().loadEvaluationResultsAsync(
                policy.getId(), version.getVersionNumber(),
                results -> {
                    if (requestId != auditRequestId) {
                        return;
                    }
                    auditResults.setAll(results);
                    auditStateLabel.setText(auditResults.isEmpty()
                            ? "No stored decision records for this policy version."
                            : auditResults.size() + " stored decision record(s) - unknown outcomes remain unclassified.");
                    setAuditRetryVisible(false);
                    applyAuditOutcomeFilter();
                },
                failure -> {
                    if (requestId != auditRequestId) {
                        return;
                    }
                    AppContext.logFailure("Could not load Time Machine decision records", failure);
                    auditStateLabel.setText("Could not load decision records. Use Retry to try again.");
                    auditTable.setPlaceholder(new Label("Decision history unavailable."));
                    setAuditRetryVisible(true);
                    AppContext.getInstance().showToast(Toast.errorWithAction(
                            "Decision History Unavailable",
                            failure.getMessage() == null ? "Could not load stored decisions."
                                    : MongoConfig.sanitize(failure.getMessage()),
                            "Retry", this::loadAuditRecords));
                });
    }

    private void applyAuditOutcomeFilter() {
        String selectedOutcome = auditOutcomeCombo.getValue();
        String query = auditSearchField.getText() == null
                ? "" : auditSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        int days = switch (auditDateRangeCombo.getValue() == null
                ? "All time" : auditDateRangeCombo.getValue()) {
            case "Last 7 days" -> 7;
            case "Last 30 days" -> 30;
            default -> 0;
        };
        LocalDate earliestDate = days == 0 ? null : LocalDate.now().minusDays(days - 1L);
        List<EvaluationResult> filtered = auditResults.stream()
                .filter(result -> selectedOutcome == null || selectedOutcome.equals("All outcomes")
                        || selectedOutcome.equals(outcomeText(result)))
                .filter(result -> query.isEmpty()
                        || (result.getApplicantName() != null
                                && result.getApplicantName().toLowerCase(java.util.Locale.ROOT).contains(query))
                        || (result.getApplicantId() != null
                                && result.getApplicantId().toLowerCase(java.util.Locale.ROOT).contains(query)))
                .filter(result -> earliestDate == null || result.getEvaluatedOn() != null
                        && !result.getEvaluatedOn().toLocalDate().isBefore(earliestDate))
                .toList();
        auditTable.setItems(FXCollections.observableArrayList(filtered));
        if (filtered.isEmpty() && !auditResults.isEmpty()) {
            auditTable.setPlaceholder(new Label("No records match the selected filters."));
        } else {
            auditTable.setPlaceholder(new Label("No stored decision records for this version."));
        }
        auditStateLabel.setText(auditResults.isEmpty()
                ? "No stored decision records for this policy version."
                : filtered.size() + " of " + auditResults.size() + " stored decision record(s)");
    }

    private void setAuditRetryVisible(boolean visible) {
        auditRetryButton.setVisible(visible);
        auditRetryButton.setManaged(visible);
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(16);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("time-machine-page-header");

        VBox titleBox = new VBox(2);
        Label title = new Label("The Decision Time Machine");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Inspect saved policy snapshots, compare versions and review recorded decisions");
        subtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(title, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label context = new Label("HISTORY  /  VERSIONS  /  DECISIONS");
        context.getStyleClass().add("time-machine-context");
        toolbar.getChildren().addAll(titleBox, context);
        return toolbar;
    }

    private HBox createControlBar() {
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.BOTTOM_LEFT);
        bar.getStyleClass().addAll("time-machine-toolbar", "time-machine-comparison-controls");

        policyCombo.setMinWidth(155);
        policyCombo.setPrefWidth(220);
        policyCombo.setMaxWidth(Double.MAX_VALUE);
        policyCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Policy p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getName());
            }
        });
        policyCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Policy p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? "Select Policy" : p.getName());
            }
        });

        versionACombo.setMinWidth(112);
        versionACombo.setPrefWidth(135);
        versionACombo.setMaxWidth(Double.MAX_VALUE);
        setupVersionCellFactory(versionACombo);

        // Swap Button
        Button swapBtn = new Button();
        swapBtn.setGraphic(Icons.create(Icons.SWAP, 0.75, "#89b4fa"));
        swapBtn.getStyleClass().addAll("btn-icon", "btn-secondary", "time-machine-swap-button");
        swapBtn.setAccessibleText("Swap baseline and target versions");
        Tooltip swapTooltip = new Tooltip("Swap Baseline & Target Versions");
        Theme.apply(swapTooltip);
        swapBtn.setTooltip(swapTooltip);
        swapBtn.setOnAction(e -> {
            PolicyVersion temp = versionACombo.getValue();
            versionACombo.setValue(versionBCombo.getValue());
            versionBCombo.setValue(temp);
            runComparisonAsync();
        });

        versionBCombo.setMinWidth(112);
        versionBCombo.setPrefWidth(135);
        versionBCombo.setMaxWidth(Double.MAX_VALUE);
        setupVersionCellFactory(versionBCombo);

        Button compareBtn = new Button("Compare Decisions");
        compareBtn.getStyleClass().add("btn-primary");
        compareBtn.setGraphic(Icons.create(Icons.COMPARISON, 0.7, "#11111b"));
        compareBtn.setMinWidth(165);
        compareBtn.setOnAction(e -> runComparisonAsync());
        VBox policyField = controlField("POLICY", policyCombo);
        HBox.setHgrow(policyField, Priority.ALWAYS);
        VBox baselineField = controlField("BASELINE VERSION", versionACombo);
        VBox targetField = controlField("TARGET VERSION", versionBCombo);
        bar.getChildren().addAll(policyField, baselineField, swapBtn, targetField, compareBtn);
        return bar;
    }

    private VBox controlField(String labelText, Node control) {
        VBox field = new VBox(6);
        field.getStyleClass().add("time-machine-control-field");
        field.getChildren().addAll(fieldLabel(labelText), control);
        return field;
    }

    private void setupVersionCellFactory(ComboBox<PolicyVersion> combo) {
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PolicyVersion pv, boolean empty) {
                super.updateItem(pv, empty);
                setText(empty || pv == null ? null : "Version " + pv.getVersionNumber());
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PolicyVersion pv, boolean empty) {
                super.updateItem(pv, empty);
                setText(empty || pv == null ? "Version" : "Version " + pv.getVersionNumber());
            }
        });
    }

    private HBox createSummaryCards() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("time-machine-summary-row");
        row.getChildren().addAll(
                createSummaryMetric("DECISIONS CHANGED", changedCountLabel, "time-machine-summary-blue"),
                createSummaryMetric("LOST ELIGIBILITY", lostCountLabel, "time-machine-summary-red"),
                createSummaryMetric("GAINED ELIGIBILITY", gainedCountLabel, "time-machine-summary-teal"));
        return row;
    }

    private VBox createSummaryMetric(String title, Label countLabel, String valueStyleClass) {
        VBox metric = new VBox(8);
        metric.getStyleClass().add("time-machine-summary-card");
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("time-machine-summary-label");
        countLabel.getStyleClass().addAll("time-machine-summary-value", valueStyleClass);
        metric.getChildren().addAll(titleLbl, countLabel);
        HBox.setHgrow(metric, Priority.ALWAYS);
        metric.setMaxWidth(Double.MAX_VALUE);
        return metric;
    }

    private VBox createTableCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        diffTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        diffTable.setMinHeight(260);
        diffTable.setPlaceholder(new EmptyState(
                Icons.COMPARISON,
                "No Version Comparison Executed",
                "Select Version A and Version B and click 'Run Comparison' to calculate differences.",
                "Compare Baseline vs Target",
                this::runComparisonAsync
        ));

        // Row factory for highlight tints
        diffTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ApplicantDiff item, boolean empty) {
                super.updateItem(item, empty);
                setMinHeight(58);
                setPrefHeight(-1);
                getStyleClass().removeAll("row-changed-lost", "row-changed-gained");
                if (!empty && item != null && item.hasDecisionChanged()) {
                    if (item.isV1Decision() && !item.isV2Decision()) {
                        getStyleClass().add("row-changed-lost");
                    } else {
                        getStyleClass().add("row-changed-gained");
                    }
                }
            }
        });

        // Column 1: Candidate
        TableColumn<ApplicantDiff, ApplicantDiff> candCol = new TableColumn<>("CANDIDATE");
        candCol.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));
        candCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ApplicantDiff diff, boolean empty) {
                super.updateItem(diff, empty);
                if (empty || diff == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(12);
                    box.setAlignment(Pos.CENTER_LEFT);
                    Avatar avatar = new Avatar(diff.getApplicantName());
                    VBox textBlock = new VBox(2);
                    Label nameLbl = new Label(diff.getApplicantName());
                    nameLbl.getStyleClass().add("time-machine-applicant-name");
                    nameLbl.setWrapText(true);
                    Label idLbl = new Label("ID: " + diff.getApplicantId());
                    idLbl.getStyleClass().add("time-machine-applicant-id");
                    textBlock.getChildren().addAll(nameLbl, idLbl);
                    HBox.setHgrow(textBlock, Priority.ALWAYS);
                    textBlock.setMaxWidth(Double.MAX_VALUE);
                    box.getChildren().addAll(avatar, textBlock);
                    setGraphic(box);
                }
            }
        });
        candCol.setMinWidth(195);
        candCol.setPrefWidth(215);

        // Column 2: Transition Badges (v1 -> v2)
        TableColumn<ApplicantDiff, ApplicantDiff> shiftCol = new TableColumn<>("DECISION TRANSITION");
        shiftCol.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));
        shiftCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ApplicantDiff diff, boolean empty) {
                super.updateItem(diff, empty);
                if (empty || diff == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);

                    StatusBadge v1Badge = diff.isV1Decision()
                            ? StatusBadge.pass("BASELINE: PASS") : StatusBadge.fail("BASELINE: FAIL");
                    FontIcon arrow = Icons.create(Icons.ARROW_RIGHT, 0.6, "#9aa0c3");
                    StatusBadge v2Badge = diff.isV2Decision()
                            ? StatusBadge.pass("TARGET: PASS") : StatusBadge.fail("TARGET: FAIL");

                    box.getChildren().addAll(v1Badge, arrow, v2Badge);
                    setGraphic(box);
                }
            }
        });
        shiftCol.setMinWidth(260);
        shiftCol.setPrefWidth(280);

        // Column 3: Transition State Badge
        TableColumn<ApplicantDiff, String> stateCol = new TableColumn<>("CHANGE IMPACT");
        stateCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getChangeType()));
        stateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setGraphic(null);
                } else {
                    if (val.contains("LOST")) {
                        setGraphic(StatusBadge.fail("LOST ELIGIBILITY"));
                    } else if (val.contains("GAINED")) {
                        setGraphic(StatusBadge.pass("GAINED ELIGIBILITY"));
                    } else {
                        Label lbl = new Label("Unchanged");
                        lbl.getStyleClass().add("time-machine-state-text");
                        setGraphic(lbl);
                    }
                }
            }
        });
        stateCol.setMinWidth(185);
        stateCol.setPrefWidth(200);

        // Column 4: Differentiating Reason
        TableColumn<ApplicantDiff, ApplicantDiff> reasonCol = new TableColumn<>("ROOT CAUSE DIFFERENTIATING FACTOR");
        reasonCol.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));
        reasonCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ApplicantDiff diff, boolean empty) {
                super.updateItem(diff, empty);
                if (empty || diff == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    getStyleClass().remove("time-machine-state-text");
                } else {
                    setGraphic(null);
                    setWrapText(true);
                    setAlignment(Pos.CENTER_LEFT);
                    if (!diff.hasDecisionChanged()) {
                        setText("Consistent across criteria");
                        if (!getStyleClass().contains("time-machine-state-text")) {
                            getStyleClass().add("time-machine-state-text");
                        }
                        setTooltip(null);
                    } else {
                        getStyleClass().remove("time-machine-state-text");
                        setText(diff.getDifferentiatingReasons().stream()
                                .map(reason -> "• " + reason)
                                .collect(java.util.stream.Collectors.joining("\n")));
                        setTooltip(new Tooltip(getText()));
                    }
                }
            }
        });
        reasonCol.setMinWidth(360);
        reasonCol.setPrefWidth(400);

        diffTable.getColumns().addAll(List.of(candCol, shiftCol, stateCol, reasonCol));
        diffTable.setItems(diffList);
        VBox.setVgrow(diffTable, Priority.ALWAYS);

        card.getChildren().add(diffTable);
        return card;
    }

    private void setupListeners() {
        AppContext ctx = AppContext.getInstance();
        policyCombo.setItems(ctx.getPolicies());

        policyCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            clearComparisonResults();
            if (newVal != null) {
                List<PolicyVersion> versions = newVal.getVersions();
                versionACombo.getItems().setAll(versions);
                versionBCombo.getItems().setAll(versions);

                if (versions.size() >= 2) {
                    versionACombo.setValue(versions.get(0));
                    versionBCombo.setValue(versions.get(1));
                } else if (!versions.isEmpty()) {
                    versionACombo.setValue(versions.get(0));
                    versionBCombo.setValue(versions.get(0));
                }
                updateConfigurationDiff();
            }
        });
        versionACombo.valueProperty().addListener((observable, previous, selected) ->
                selectionChanged());
        versionBCombo.valueProperty().addListener((observable, previous, selected) ->
                selectionChanged());
        historyPolicyCombo.valueProperty().addListener((observable, previous, selected) ->
                showHistoryVersions(selected));
        historySearchField.textProperty().addListener((observable, previous, selected) ->
                filterHistoryVersions());
        auditPolicyCombo.valueProperty().addListener((observable, previous, selected) -> {
            List<PolicyVersion> versions = selected == null ? List.of() : selected.getVersions();
            auditVersionCombo.getItems().setAll(versions);
            auditVersionCombo.setValue(versions.stream()
                    .max(Comparator.comparingInt(PolicyVersion::getVersionNumber))
                    .orElse(null));
            if (auditVersionCombo.getValue() == null) {
                loadAuditRecords();
            }
        });
        auditVersionCombo.valueProperty().addListener((observable, previous, selected) ->
                loadAuditRecords());
        auditOutcomeCombo.valueProperty().addListener((observable, previous, selected) ->
                applyAuditOutcomeFilter());
        auditDateRangeCombo.valueProperty().addListener((observable, previous, selected) ->
                applyAuditOutcomeFilter());
        auditSearchField.textProperty().addListener((observable, previous, selected) ->
                applyAuditOutcomeFilter());
        showUnchangedRules.selectedProperty().addListener((observable, previous, selected) ->
                updateConfigurationDiff());
        ctx.resultsChangedProperty().addListener((observable, oldValue, newValue) -> {
            if (policyCombo.getValue() != null && versionACombo.getValue() != null
                    && versionBCombo.getValue() != null) {
                runComparisonAsync();
            }
            loadAuditRecords();
        });
    }

    private void showHistoryVersions(Policy policy) {
        List<PolicyVersion> versions = policy == null ? List.of() : policy.getVersions().stream()
                .sorted(Comparator.comparingInt(PolicyVersion::getVersionNumber).reversed())
                .toList();
        historyVersions.setAll(versions);
        historyTable.getSelectionModel().selectFirst();
        if (versions.isEmpty()) {
            showVersionDetails(null);
        }
        filterHistoryVersions();
    }

    private void selectionChanged() {
        updateConfigurationDiff();
        clearComparisonResults();
    }

    private void clearComparisonResults() {
        diffList.clear();
        changedCountLabel.setText("—");
        lostCountLabel.setText("—");
        gainedCountLabel.setText("—");
    }

    private void filterHistoryVersions() {
        String query = historySearchField.getText() == null
                ? "" : historySearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        Integer selectedVersion = historyTable.getSelectionModel().getSelectedItem() == null
                ? null : historyTable.getSelectionModel().getSelectedItem().getVersionNumber();
        filteredHistoryVersions.setPredicate(version -> query.isEmpty()
                || Integer.toString(version.getVersionNumber()).contains(query)
                || (version.getDescription() != null
                        && version.getDescription().toLowerCase(java.util.Locale.ROOT).contains(query))
                || version.getConditions().stream().anyMatch(condition ->
                        condition.getMessage() != null
                                && condition.getMessage().toLowerCase(java.util.Locale.ROOT).contains(query)));
        if (selectedVersion != null) {
            for (PolicyVersion visible : filteredHistoryVersions) {
                if (visible.getVersionNumber() == selectedVersion) {
                    historyTable.getSelectionModel().select(visible);
                    return;
                }
            }
        }
        historyTable.getSelectionModel().selectFirst();
    }

    private void runComparisonAsync() {
        Policy policy = policyCombo.getValue();
        PolicyVersion vA = versionACombo.getValue();
        PolicyVersion vB = versionBCombo.getValue();

        if (policy == null || vA == null || vB == null) {
            AppContext.getInstance().showToast(Toast.error("Selection Required", "Please select a policy and two versions to compare.", null));
            return;
        }

        AppContext ctx = AppContext.getInstance();
        clearComparisonResults();
        loadingOverlay.show("Comparing stored decision records between v"
                + vA.getVersionNumber() + " and v" + vB.getVersionNumber() + "...");
        ctx.loadEvaluationResultsAsync(policy.getId(), vA.getVersionNumber(), firstResults ->
                ctx.loadEvaluationResultsAsync(policy.getId(), vB.getVersionNumber(), secondResults -> {
                    loadingOverlay.hide();
                    if (firstResults.isEmpty() || secondResults.isEmpty()) {
                        clearComparisonResults();
                        ctx.showToast(Toast.errorWithAction(
                                "Stored Results Required",
                                "Evaluate both selected versions before comparing their saved results.",
                                "Retry", this::runComparisonAsync));
                        return;
                    }
                    ComparisonReport report = ctx.getComparisonService()
                            .compareEvaluations(firstResults, secondResults);
                    ObservableList<ApplicantDiff> allDiffs = FXCollections.observableArrayList();
                    allDiffs.addAll(report.getChangedApplicants());
                    allDiffs.addAll(report.getUnchangedApplicants());
                    diffList.setAll(allDiffs);

                    int changed = report.getChangedApplicants().size();
                    changedCountLabel.setText(String.valueOf(changed));
                    lostCountLabel.setText(String.valueOf(report.getLostEligibilityCount()));
                    gainedCountLabel.setText(String.valueOf(report.getGainedEligibilityCount()));
                    ctx.showToast(Toast.success(
                            "Comparison Complete",
                            "Compared " + report.getTotalApplicants() + " applicants with known stored outcomes for v"
                                    + vA.getVersionNumber() + " vs v" + vB.getVersionNumber()
                                    + "; ambiguous timestamp histories were omitted; "
                                    + changed + " decision shifts detected.",
                            null));
                }, failure -> {
                    loadingOverlay.hide();
                    AppContext.logFailure("Could not load target comparison results", failure);
                    ctx.showToast(Toast.errorWithAction("Comparison Error",
                            failure.getMessage() == null ? "Could not load stored results."
                                    : MongoConfig.sanitize(failure.getMessage()),
                            "Retry", this::runComparisonAsync));
                }), failure -> {
            loadingOverlay.hide();
            AppContext.logFailure("Could not load baseline comparison results", failure);
            ctx.showToast(Toast.errorWithAction("Comparison Error",
                    failure.getMessage() == null ? "Could not load stored results."
                            : MongoConfig.sanitize(failure.getMessage()),
                    "Retry", this::runComparisonAsync));
        });
    }

    @Override
    public Node getRoot() {
        return rootPane;
    }

    @Override
    public void onShow() {
        AppContext ctx = AppContext.getInstance();
        Policy sel = ctx.selectedPolicyProperty().get();
        if (sel == null && !ctx.getPolicies().isEmpty()) {
            sel = ctx.getPolicies().get(0);
        }
        if (sel != null && policyCombo.getValue() != sel) {
            policyCombo.setValue(sel);
        }
        if (sel != null) {
            if (historyPolicyCombo.getValue() == null) {
                historyPolicyCombo.setValue(sel);
            }
            if (auditPolicyCombo.getValue() == null) {
                auditPolicyCombo.setValue(sel);
            }
        } else {
            showHistoryVersions(null);
            loadAuditRecords();
        }
    }
}
