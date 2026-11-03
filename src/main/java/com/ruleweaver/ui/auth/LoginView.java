package com.ruleweaver.ui.auth;

import com.ruleweaver.config.MongoConfig;
import com.ruleweaver.repository.UserRepository;
import com.ruleweaver.service.AuthService;
import com.ruleweaver.service.AuthService.AuthException;
import com.ruleweaver.service.AuthService.AuthUser;
import com.ruleweaver.service.CredentialUtil;
import com.ruleweaver.ui.AppContext;
import com.ruleweaver.ui.components.MongoConnectionDialog;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Sign-in / create-account screen. Styled by /css/login.css.
 * Accounts are saved in MongoDB through AuthService.
 */
public final class LoginView {

    private static final String STYLESHEET = "/css/login.css";
    private static final String COLLEGE_LOGO = "/images/rajalakshmi-engineering-college.png";
    private static final double TAB_WIDTH = 184;
    private static final double TAB_HEIGHT = 38;
    private static final double NAME_FIELD_HEIGHT = 76;

    private final Stage owner;
    private final Consumer<AuthUser> onSuccess;

    private final StackPane root = new StackPane();
    private final WeaveCanvas weave = new WeaveCanvas();
    private final VBox card = new VBox();

    private final Button signInTab = new Button("Sign in");
    private final Button createTab = new Button("Create account");
    private final Region tabIndicator = new Region();
    private final Label title = new Label("Welcome back");
    private final Label subtitle = new Label("Sign in to continue to your dashboard.");

    private final VBox nameBox = new VBox();
    private final TextField nameField = new TextField();
    private final TextField emailField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField passwordPlain = new TextField();
    private final Button showButton = new Button("Show");

    private final Label message = new Label();
    private final Hyperlink databaseLink = new Hyperlink("Set up the database connection");
    private final Button submit = new Button("Sign in");

    private boolean registerMode;
    private boolean busy;

    public LoginView(Stage owner, Consumer<AuthUser> onSuccess) {
        this.owner = owner;
        this.onSuccess = onSuccess;
        build();
        playEntrance();
    }

    public Parent getRoot() {
        return root;
    }

    /** Stops the background animation. Called once the person is signed in. */
    public void dispose() {
        weave.stop();
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        root.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        root.getStyleClass().add("login-root");

        Image logoImage = new Image(getClass().getResource(COLLEGE_LOGO).toExternalForm());
        weave.widthProperty().bind(root.widthProperty());
        weave.heightProperty().bind(root.heightProperty());
        weave.setMouseTransparent(true);
        root.setOnMouseMoved(e -> weave.setPointer(
                e.getSceneX() / Math.max(1, root.getWidth()),
                e.getSceneY() / Math.max(1, root.getHeight())));

        HBox content = new HBox(48, buildBrand(), buildCard());
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(48, 96, 48, 96));

        ImageView collegeLogo = new ImageView(logoImage);
        collegeLogo.setFitWidth(300);
        collegeLogo.setPreserveRatio(true);
        collegeLogo.setSmooth(true);
        collegeLogo.setMouseTransparent(true);
        StackPane.setAlignment(collegeLogo, Pos.TOP_LEFT);
        StackPane.setMargin(collegeLogo, new Insets(24, 0, 0, 28));

        root.getChildren().addAll(weave, content, collegeLogo);
        weave.play(!AppContext.getInstance().reducedMotionProperty().get());
    }

    private VBox buildBrand() {
        Label logo = new Label("RuleWeaver ");
        logo.getStyleClass().add("login-logo");
        Label logoX = new Label("X");
        logoX.getStyleClass().addAll("login-logo", "login-logo-x");
        HBox logoRow = new HBox(logo, logoX);

        Label tagline = new Label("Explainable decision engine");
        tagline.getStyleClass().add("login-tagline");

        Label headline = new Label("Every decision,\nexplained.");
        headline.getStyleClass().add("login-headline");
        headline.setMinHeight(Region.USE_PREF_SIZE);
        headline.setMinWidth(Region.USE_PREF_SIZE);

        Label blurb = new Label(
                "Sign in to see your programs, applicants and policy decisions, and the reasons behind each one.");
        blurb.getStyleClass().add("login-blurb");
        blurb.setWrapText(true);
        blurb.setPrefWidth(420);
        blurb.setMaxWidth(420);
        blurb.setMinHeight(Region.USE_PREF_SIZE);

        VBox brand = new VBox(0, logoRow, tagline, headline, blurb);
        VBox.setMargin(headline, new Insets(56, 0, 18, 0));
        brand.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(brand, Priority.ALWAYS);
        return brand;
    }

    private VBox buildCard() {
        card.getStyleClass().add("login-card");
        card.setMinWidth(440);
        card.setPrefWidth(440);
        card.setMaxWidth(440);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        // Blue bar along the top edge (sweeps in when the screen opens)
        Region bar = new Region();
        bar.getStyleClass().add("login-card-bar");
        bar.setMinHeight(3);
        bar.setPrefHeight(3);
        bar.setMaxHeight(3);
        Scale sweep = new Scale(0, 1, 0, 0);
        bar.getTransforms().add(sweep);
        new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(sweep.xProperty(), 0)),
                new KeyFrame(Duration.millis(900), new KeyValue(sweep.xProperty(), 1, Interpolator.EASE_OUT)))
                .playFrom(Duration.millis(0));
        VBox.setMargin(bar, new Insets(0, 32, 0, 32));

        VBox body = new VBox(0);
        body.setPadding(new Insets(26, 32, 30, 32));

        body.getChildren().addAll(buildTabs(), headingBlock(), buildForm());
        card.getChildren().addAll(bar, body);
        return card;
    }

    private StackPane buildTabs() {
        for (Button tab : new Button[] { signInTab, createTab }) {
            tab.getStyleClass().add("login-tab");
            tab.setPrefSize(TAB_WIDTH, TAB_HEIGHT);
            tab.setMinSize(TAB_WIDTH, TAB_HEIGHT);
            tab.setFocusTraversable(false);
        }
        signInTab.getStyleClass().add("active");
        signInTab.setOnAction(e -> setMode(false));
        createTab.setOnAction(e -> setMode(true));

        tabIndicator.getStyleClass().add("login-tab-indicator");
        tabIndicator.setMinSize(TAB_WIDTH, TAB_HEIGHT);
        tabIndicator.setPrefSize(TAB_WIDTH, TAB_HEIGHT);
        tabIndicator.setMaxSize(TAB_WIDTH, TAB_HEIGHT);

        HBox buttons = new HBox(signInTab, createTab);
        buttons.setMaxWidth(TAB_WIDTH * 2);
        StackPane tabs = new StackPane(tabIndicator, buttons);
        tabs.getStyleClass().add("login-tabs");
        tabs.setAlignment(Pos.CENTER_LEFT);
        tabs.setMaxWidth(TAB_WIDTH * 2 + 8);
        return tabs;
    }

    private VBox headingBlock() {
        title.getStyleClass().add("login-title");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setWrapText(true);
        VBox box = new VBox(6, title, subtitle);
        box.setPadding(new Insets(26, 0, 22, 0));
        return box;
    }

    private VBox buildForm() {
        // Name (only for Create account) - collapses to zero height in Sign in mode
        nameField.setPromptText("Your full name");
        nameBox.getChildren().add(fieldBox("Full name", nameField));
        nameBox.setMinHeight(0);
        nameBox.setMaxHeight(0);
        nameBox.setOpacity(0);
        nameField.setFocusTraversable(false);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(nameBox.widthProperty());
        clip.heightProperty().bind(nameBox.heightProperty());
        nameBox.setClip(clip);

        emailField.setPromptText("you@example.com");
        VBox emailBox = fieldBox("Email", emailField);

        passwordField.setPromptText("At least 8 characters");
        passwordPlain.setPromptText("At least 8 characters");
        passwordPlain.textProperty().bindBidirectional(passwordField.textProperty());
        passwordPlain.setVisible(false);
        passwordPlain.setManaged(false);
        passwordField.getStyleClass().add("login-input-password");
        passwordPlain.getStyleClass().add("login-input-password");
        showButton.getStyleClass().add("login-eye");
        showButton.setFocusTraversable(false);
        showButton.setOnAction(e -> togglePassword());
        StackPane passwordStack = new StackPane(passwordField, passwordPlain, showButton);
        StackPane.setAlignment(showButton, Pos.CENTER_RIGHT);
        VBox passwordBox = fieldBox("Password", passwordStack);

        for (TextField input : new TextField[] { nameField, emailField, passwordField, passwordPlain }) {
            input.getStyleClass().add("login-input");
        }

        message.getStyleClass().add("login-message");
        message.setWrapText(true);
        message.setMinHeight(20);
        databaseLink.getStyleClass().add("login-link");
        databaseLink.setVisible(false);
        databaseLink.setManaged(false);
        databaseLink.setOnAction(e -> openDatabaseDialog());
        VBox messageBox = new VBox(2, message, databaseLink);
        messageBox.setPadding(new Insets(2, 0, 10, 0));

        submit.getStyleClass().add("login-submit");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.setOnAction(e -> submit());
        submit.hoverProperty().addListener((o, was, hovering) -> {
            if (busy) {
                return;
            }
            TranslateTransition lift = new TranslateTransition(Duration.millis(120), submit);
            lift.setToY(hovering ? -2 : 0);
            lift.play();
        });

        Label footnote = new Label("Your account is stored securely. Passwords are hashed.");
        footnote.getStyleClass().add("login-footnote");
        footnote.setMaxWidth(Double.MAX_VALUE);
        footnote.setAlignment(Pos.CENTER);
        VBox.setMargin(footnote, new Insets(18, 0, 0, 0));

        return new VBox(0, nameBox, emailBox, passwordBox, messageBox, submit, footnote);
    }

    private VBox fieldBox(String labelText, javafx.scene.Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("login-field-label");
        VBox box = new VBox(6, label, input);
        box.setPadding(new Insets(0, 0, 14, 0));
        return box;
    }

    // --------------------------------------------------------------- animation

    private void playEntrance() {
        card.setOpacity(0);
        card.setTranslateY(28);
        FadeTransition fade = new FadeTransition(Duration.millis(800), card);
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(800), card);
        rise.setToY(0);
        rise.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, rise).play();
    }

    private void shake() {
        new Timeline(
                new KeyFrame(Duration.millis(0), new KeyValue(card.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(80), new KeyValue(card.translateXProperty(), -9)),
                new KeyFrame(Duration.millis(160), new KeyValue(card.translateXProperty(), 9)),
                new KeyFrame(Duration.millis(240), new KeyValue(card.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(320), new KeyValue(card.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(400), new KeyValue(card.translateXProperty(), 0)))
                .play();
    }

    // ------------------------------------------------------------------- logic

    private void setMode(boolean register) {
        if (busy || register == registerMode) {
            return;
        }
        registerMode = register;
        clearMessage();

        title.setText(register ? "Create your account" : "Welcome back");
        subtitle.setText(register ? "It takes less than a minute." : "Sign in to continue to your dashboard.");
        submit.setText(register ? "Create account" : "Sign in");
        passwordField.setPromptText("At least 8 characters");
        signInTab.getStyleClass().remove("active");
        createTab.getStyleClass().remove("active");
        (register ? createTab : signInTab).getStyleClass().add("active");

        TranslateTransition slide = new TranslateTransition(Duration.millis(300), tabIndicator);
        slide.setToX(register ? TAB_WIDTH : 0);
        slide.setInterpolator(Interpolator.EASE_BOTH);
        slide.play();

        double open = nameBox.getChildren().isEmpty() ? NAME_FIELD_HEIGHT
                : Math.max(NAME_FIELD_HEIGHT, nameBox.getChildren().get(0).prefHeight(-1));
        new Timeline(new KeyFrame(Duration.millis(320),
                new KeyValue(nameBox.maxHeightProperty(), register ? open : 0, Interpolator.EASE_BOTH),
                new KeyValue(nameBox.opacityProperty(), register ? 1 : 0, Interpolator.EASE_BOTH))).play();
        nameField.setFocusTraversable(register);
        (register ? nameField : emailField).requestFocus();
    }

    private void togglePassword() {
        boolean show = !passwordPlain.isVisible();
        passwordPlain.setVisible(show);
        passwordPlain.setManaged(show);
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        showButton.setText(show ? "Hide" : "Show");
    }

    private void submit() {
        if (busy) {
            return;
        }
        boolean register = registerMode;
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        String problem = CredentialUtil.validate(register, name, email, password);
        if (problem != null) {
            fail(problem, false);
            return;
        }

        setBusy(true);
        clearMessage();
        Task<Object[]> task = new Task<>() {
            @Override
            protected Object[] call() {
                AuthService service = new AuthService(new UserRepository(MongoConfig.getDatabase()));
                AuthUser user = register ? service.register(name, email, password) : service.login(email, password);
                return new Object[] { user, service };
            }
        };
        task.setOnSucceeded(e -> {
            Object[] result = task.getValue();
            succeed((AuthUser) result[0], (AuthService) result[1]);
        });
        task.setOnFailed(e -> {
            setBusy(false);
            handleFailure(task.getException());
        });
        AppContext.getInstance().getExecutor().execute(task);
    }

    private void succeed(AuthUser user, AuthService service) {
        Session.start(user, service);
        message.getStyleClass().setAll("login-message", "login-message-ok");
        message.setText("Welcome, " + user.name() + ".");
        submit.setGraphic(null);
        submit.setText("Signed in \u2713");
        submit.getStyleClass().add("done");
        PauseTransition pause = new PauseTransition(Duration.millis(800));
        pause.setOnFinished(e -> {
            dispose();
            onSuccess.accept(user);
        });
        pause.play();
    }

    private void handleFailure(Throwable failure) {
        if (failure instanceof AuthException) {
            fail(failure.getMessage(), false);
            return;
        }
        if (failure != null) {
            AppContext.logFailure("Sign-in failed", failure);
        }
        if (failure instanceof IllegalStateException && MongoConfig.getConfiguredUri().isEmpty()) {
            fail("No database is connected yet. Connect MongoDB to sign in.", true);
        } else {
            fail("Can\u2019t reach the database. Check your connection and the MongoDB settings.", true);
        }
    }

    private void fail(String text, boolean offerDatabaseSetup) {
        message.getStyleClass().setAll("login-message", "login-message-error");
        message.setText(text);
        databaseLink.setVisible(offerDatabaseSetup);
        databaseLink.setManaged(offerDatabaseSetup);
        shake();
    }

    private void clearMessage() {
        message.getStyleClass().setAll("login-message");
        message.setText("");
        databaseLink.setVisible(false);
        databaseLink.setManaged(false);
    }

    private void setBusy(boolean value) {
        busy = value;
        submit.setDisable(value);
        if (value) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setPrefSize(18, 18);
            spinner.setMaxSize(18, 18);
            spinner.getStyleClass().add("login-spinner");
            submit.setGraphic(spinner);
            submit.setText(registerMode ? "Creating account" : "Signing in");
        } else {
            submit.setGraphic(null);
            submit.setText(registerMode ? "Create account" : "Sign in");
        }
    }

    private void openDatabaseDialog() {
        MongoConnectionDialog.show(owner, () -> {
            message.getStyleClass().setAll("login-message", "login-message-ok");
            message.setText("Database connected. You can sign in now.");
            databaseLink.setVisible(false);
            databaseLink.setManaged(false);
        }, () -> {
        });
    }
}
