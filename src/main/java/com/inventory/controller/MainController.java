package com.inventory.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.BooleanSupplier;

public class MainController {
    @FXML
    private VBox contentArea;

    @FXML
    private Label usernameLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Button dashboardButton;

    @FXML
    private Button productsButton;

    @FXML
    private Button categoriesButton;

    @FXML
    private Button salesButton;

    @FXML
    private Button stockMovementsButton;

    @FXML
    private Button usersButton;

    @FXML
    private Button reportsButton;

    @FXML
    private Button logoutButton;

    private List<Button> navigationButtons;
    private long authenticatedUserId;
    private String authenticatedUsername;
    private String authenticatedRole;
    private BooleanSupplier logoutAction;

    @FXML
    private void initialize() {
        navigationButtons = List.of(
                dashboardButton, productsButton, categoriesButton, salesButton,
                stockMovementsButton, usersButton, reportsButton);
        showDashboard();
        selectNavigationButton(dashboardButton);
    }

    public void initializeAuthenticatedUser(long userId, String username, String role) {
        if (userId <= 0 || username == null || username.isBlank()
                || role == null || role.isBlank()) {
            throw new IllegalArgumentException("Authenticated user information is invalid.");
        }

        authenticatedUserId = userId;
        authenticatedUsername = username;
        authenticatedRole = role;
        usernameLabel.setText(authenticatedUsername);
        roleLabel.setText(authenticatedRole);
    }

    public void setLogoutAction(BooleanSupplier logoutAction) {
        this.logoutAction = logoutAction;
    }

    @FXML
    private void handleDashboardNavigation() {
        showDashboard();
        selectNavigationButton(dashboardButton);
    }

    @FXML
    private void handleProductsNavigation() {
        showComingSoon("Products");
        selectNavigationButton(productsButton);
    }

    @FXML
    private void handleCategoriesNavigation() {
        showComingSoon("Categories");
        selectNavigationButton(categoriesButton);
    }

    @FXML
    private void handleSalesNavigation() {
        showComingSoon("Sales & Invoices");
        selectNavigationButton(salesButton);
    }

    @FXML
    private void handleStockMovementsNavigation() {
        showComingSoon("Stock Movements");
        selectNavigationButton(stockMovementsButton);
    }

    @FXML
    private void handleUsersNavigation() {
        showComingSoon("Users");
        selectNavigationButton(usersButton);
    }

    @FXML
    private void handleReportsNavigation() {
        showComingSoon("Reports");
        selectNavigationButton(reportsButton);
    }

    @FXML
    private void handleLogout() {
        if (logoutAction == null) {
            throw new IllegalStateException("Logout action has not been configured.");
        }
        if (logoutAction.getAsBoolean()) {
            clearAuthenticatedUser();
        }
    }

    private void showDashboard() {
        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");

        Label description = new Label("Your inventory workspace at a glance.");
        description.getStyleClass().add("page-description");

        HBox cards = new HBox(18,
                createSummaryCard("Products", "—", "No live statistics are available yet."),
                createSummaryCard("Categories", "—", "No live statistics are available yet."),
                createSummaryCard("Stock movements", "—", "No live statistics are available yet."));
        cards.getStyleClass().add("summary-cards");

        contentArea.getChildren().setAll(title, description, cards);
    }

    private VBox createSummaryCard(String titleText, String valueText, String detailText) {
        Label title = new Label(titleText);
        title.getStyleClass().add("summary-card-title");

        Label value = new Label(valueText);
        value.getStyleClass().add("summary-card-value");

        Label detail = new Label(detailText);
        detail.getStyleClass().add("summary-card-detail");
        detail.setWrapText(true);

        VBox card = new VBox(12, title, value, detail);
        card.getStyleClass().add("summary-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void showComingSoon(String sectionName) {
        Label title = new Label(sectionName);
        title.getStyleClass().add("page-title");

        Label comingSoon = new Label("This module is coming soon.");
        comingSoon.getStyleClass().add("coming-soon-title");

        Label description = new Label(
                "This section is a placeholder. No module data is displayed here yet.");
        description.getStyleClass().add("coming-soon-description");
        description.setWrapText(true);

        VBox placeholder = new VBox(12, comingSoon, description);
        placeholder.getStyleClass().add("coming-soon-card");
        contentArea.getChildren().setAll(title, placeholder);
    }

    private void selectNavigationButton(Button selectedButton) {
        for (Button button : navigationButtons) {
            button.getStyleClass().remove("selected");
        }
        selectedButton.getStyleClass().add("selected");
    }

    private void clearAuthenticatedUser() {
        authenticatedUserId = 0;
        authenticatedUsername = null;
        authenticatedRole = null;
        usernameLabel.setText("");
        roleLabel.setText("");
        logoutButton.setDisable(true);
    }
}
