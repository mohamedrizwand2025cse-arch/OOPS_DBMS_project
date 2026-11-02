package com.ruleweaver.ui.shell;

import atlantafx.base.theme.Styles;
import com.ruleweaver.ui.AppContext;
import com.ruleweaver.ui.components.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Modern 240px Sidebar with glowing active navigation indicators and MongoDB
 * status chip.
 */
public class Sidebar extends VBox {

    public enum Destination {
        DASHBOARD("Dashboard", Icons.DASHBOARD),
        APPLICANTS("Applicants", Icons.APPLICANTS),
        PROGRAMS("Programs", Icons.POLICIES),
        POLICIES("Policies", Icons.POLICIES),
        EVALUATION("Evaluation", Icons.EVALUATION),
        COMPARISON("Time Machine", Icons.COMPARISON);

        private final String title;
        private final String iconName;

        Destination(String title, String iconName) {
            this.title = title;
            this.iconName = iconName;
        }

        public String getTitle() {
            return title;
        }

        public String getIconName() {
            return iconName;
        }
    }

    private final Map<Destination, HBox> navItemMap = new HashMap<>();
    private Destination currentDestination = Destination.DASHBOARD;
    private final Consumer<Destination> onNavigate;

    public Sidebar(Consumer<Destination> onNavigate, Runnable onSignOut) {
        this.onNavigate = onNavigate;
        getStyleClass().add("sidebar");

        // 1. Logo Block
        VBox logoBlock = createLogoBlock();

        // 2. Navigation items
        VBox navItemsBox = new VBox(6);
        for (Destination dest : Destination.values()) {
            HBox item = createNavItem(dest);
            navItemMap.put(dest, item);
            navItemsBox.getChildren().add(item);
        }
        VBox.setVgrow(navItemsBox, Priority.ALWAYS);

        // 3. Footer with sign-out and Mongo status
        VBox footerBox = createFooter(onSignOut);

        getChildren().addAll(logoBlock, navItemsBox, footerBox);
        setActive(Destination.DASHBOARD);
    }

    private VBox createLogoBlock() {
        VBox box = new VBox(4);
        box.getStyleClass().add("sidebar-logo-block");

        HBox titleRow = new HBox(4);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("RuleWeaver ");
        title.getStyleClass().add("sidebar-logo-title");

        Label accentX = new Label("X");
        accentX.getStyleClass().add("sidebar-logo-accent");

        titleRow.getChildren().addAll(title, accentX);

        Label subtitle = new Label("EXPLAINABLE DECISION ENGINE");
        subtitle.getStyleClass().add("sidebar-logo-subtitle");

        box.getChildren().addAll(titleRow, subtitle);
        return box;
    }

    private HBox createNavItem(Destination dest) {
        HBox item = new HBox(12);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("nav-item");

        FontIcon icon = Icons.create(dest.getIconName(), 0.8, "#9aa0c3");
        icon.getStyleClass().add("nav-icon");

        Label label = new Label(dest.getTitle());
        label.getStyleClass().add("nav-label");

        item.getChildren().addAll(icon, label);

        item.setOnMouseClicked(e -> {
            setActive(dest);
            if (onNavigate != null) {
                onNavigate.accept(dest);
            }
        });

        return item;
    }

    public void setActive(Destination dest) {
        this.currentDestination = dest;
        for (Map.Entry<Destination, HBox> entry : navItemMap.entrySet()) {
            HBox item = entry.getValue();
            boolean isActive = entry.getKey() == dest;

            item.getStyleClass().removeAll("nav-item-active");
            if (isActive) {
                item.getStyleClass().add("nav-item-active");
            }

            // Update label and icon styles
            FontIcon icon = (FontIcon) item.getChildren().get(0);
            Label label = (Label) item.getChildren().get(1);

            icon.getStyleClass().removeAll("nav-icon-active");
            label.getStyleClass().removeAll("nav-label-active");

            if (isActive) {
                icon.getStyleClass().add("nav-icon-active");
                label.getStyleClass().add("nav-label-active");
            }
        }
    }

    private VBox createFooter(Runnable onSignOut) {
        VBox footer = new VBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("sidebar-footer");

        Button signOutBtn = new Button("Sign Out");
        signOutBtn.getStyleClass().add(Styles.DANGER);
        signOutBtn.setMaxWidth(Double.MAX_VALUE);
        signOutBtn.setOnAction(e -> onSignOut.run());
        footer.getChildren().add(signOutBtn);

        // MongoDB Atlas Status Chip
        HBox mongoChip = new HBox(8);
        mongoChip.setAlignment(Pos.CENTER_LEFT);
        mongoChip.getStyleClass().add("mongo-status-chip");

        Circle dot = new Circle(4);
        Label statusLabel = new Label("MongoDB Atlas: Checking...");
        statusLabel.getStyleClass().add("mongo-status-text");

        AppContext.getInstance().mongoOnlineProperty().addListener((obs, oldVal, online) -> {
            javafx.application.Platform.runLater(() -> {
                if (online) {
                    dot.getStyleClass().removeAll("status-dot-offline");
                    dot.getStyleClass().add("status-dot-online");
                    statusLabel.setText("Atlas: Connected");
                    statusLabel.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
                    statusLabel.getStyleClass().add(Styles.SUCCESS);
                } else {
                    dot.getStyleClass().removeAll("status-dot-online");
                    dot.getStyleClass().add("status-dot-offline");
                    statusLabel.setText("Atlas: Offline");
                    statusLabel.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
                    statusLabel.getStyleClass().add(Styles.DANGER);
                }
            });
        });

        mongoChip.getChildren().addAll(dot, statusLabel);
        footer.getChildren().add(mongoChip);

        return footer;
    }
}
