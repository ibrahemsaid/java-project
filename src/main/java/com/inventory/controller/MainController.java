package com.inventory.controller;

import com.inventory.service.CategoryService;
import com.inventory.dao.SaleDao;
import com.inventory.dao.SaleItemDao;
import com.inventory.service.ProductService;
import com.inventory.service.SaleService;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainController {
    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final String ADMIN_ROLE = "ADMIN";

    private final CategoryService categoryService;
    private final ProductService productService;
    private final SaleService saleService;
    private final SaleDao saleDao;
    private final SaleItemDao saleItemDao;

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
    private boolean salesCheckoutInProgress;

    public MainController(
            CategoryService categoryService,
            ProductService productService,
            SaleService saleService,
            SaleDao saleDao,
            SaleItemDao saleItemDao) {
        this.categoryService = Objects.requireNonNull(categoryService);
        this.productService = Objects.requireNonNull(productService);
        this.saleService = Objects.requireNonNull(saleService);
        this.saleDao = Objects.requireNonNull(saleDao);
        this.saleItemDao = Objects.requireNonNull(saleItemDao);
    }

    @FXML
    private void initialize() {
        navigationButtons = List.of(
                dashboardButton, productsButton, categoriesButton, salesButton,
                stockMovementsButton, usersButton, reportsButton);
        categoriesButton.setVisible(false);
        categoriesButton.setManaged(false);
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
        boolean admin = isAdmin();
        categoriesButton.setVisible(admin);
        categoriesButton.setManaged(admin);
    }

    public void setLogoutAction(BooleanSupplier logoutAction) {
        this.logoutAction = logoutAction;
    }

    @FXML
    private void handleDashboardNavigation() {
        if (salesCheckoutInProgress) {
            return;
        }
        showDashboard();
        selectNavigationButton(dashboardButton);
    }

    @FXML
    private void handleProductsNavigation() {
        if (salesCheckoutInProgress) {
            return;
        }
        showComingSoon("Products");
        selectNavigationButton(productsButton);
    }

    @FXML
    private void handleCategoriesNavigation() {
        if (salesCheckoutInProgress) {
            return;
        }
        if (!isAdmin()) {
            showAccessDenied();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/inventory/view/categories-view.fxml"));
            loader.setControllerFactory(controllerType -> {
                if (controllerType == CategoryController.class) {
                    return new CategoryController(categoryService, authenticatedRole);
                }
                throw new IllegalStateException(
                        "No controller factory is configured for "
                                + controllerType.getName());
            });
            Parent categoriesView = loader.load();
            contentArea.getChildren().setAll(categoriesView);
            selectNavigationButton(categoriesButton);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Unable to open the Categories screen.", exception);
            showNavigationError();
        }
    }

    @FXML
    private void handleSalesNavigation() {
        if (salesCheckoutInProgress) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/inventory/view/sales-view.fxml"));
            loader.setControllerFactory(controllerType -> {
                if (controllerType == SalesController.class) {
                    return new SalesController(
                            productService,
                            saleService,
                            saleDao,
                            saleItemDao,
                            authenticatedUserId,
                            this::setSalesCheckoutInProgress);
                }
                throw new IllegalStateException(
                        "No controller factory is configured for "
                                + controllerType.getName());
            });
            Parent salesView = loader.load();
            contentArea.getChildren().setAll(salesView);
            selectNavigationButton(salesButton);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Unable to open the Sales & Invoices screen.", exception);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Sales & Invoices unavailable");
            alert.setHeaderText("Unable to open Sales & Invoices.");
            alert.setContentText("Please try again later.");
            alert.showAndWait();
        }
    }

    @FXML
    private void handleStockMovementsNavigation() {
        if (salesCheckoutInProgress) {
            return;
        }
        showComingSoon("Stock Movements");
        selectNavigationButton(stockMovementsButton);
    }

    @FXML
    private void handleUsersNavigation() {
        if (salesCheckoutInProgress) {
            return;
        }
        showComingSoon("Users");
        selectNavigationButton(usersButton);
    }

    @FXML
    private void handleReportsNavigation() {
        if (salesCheckoutInProgress) {
            return;
        }
        showComingSoon("Reports");
        selectNavigationButton(reportsButton);
    }

    @FXML
    private void handleLogout() {
        if (salesCheckoutInProgress) {
            return;
        }
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

    private boolean isAdmin() {
        return ADMIN_ROLE.equals(authenticatedRole);
    }

    private void showAccessDenied() {
        Label title = new Label("Categories");
        title.getStyleClass().add("page-title");

        Label message = new Label("Only Admin users can manage categories.");
        message.setId("accessDeniedMessage");
        message.getStyleClass().add("coming-soon-description");

        VBox accessDenied = new VBox(12, message);
        accessDenied.getStyleClass().add("coming-soon-card");
        contentArea.getChildren().setAll(title, accessDenied);
    }

    private void showNavigationError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Categories unavailable");
        alert.setHeaderText("Unable to open Categories.");
        alert.setContentText("Please try again later.");
        alert.showAndWait();
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

    private void setSalesCheckoutInProgress(boolean inProgress) {
        salesCheckoutInProgress = inProgress;
        navigationButtons.forEach(button -> button.setDisable(inProgress));
        logoutButton.setDisable(inProgress);
    }
}
