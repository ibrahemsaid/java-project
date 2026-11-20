package com.inventory.controller;

import com.inventory.model.Category;
import com.inventory.service.CategoryService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CategoryController {
    private static final Logger LOGGER =
            Logger.getLogger(CategoryController.class.getName());
    private static final String ADMIN_ROLE = "ADMIN";

    private final CategoryService categoryService;
    private final String authenticatedRole;

    @FXML
    private TableView<Category> categoryTable;

    @FXML
    private TableColumn<Category, Number> idColumn;

    @FXML
    private TableColumn<Category, String> nameColumn;

    @FXML
    private Button addButton;

    @FXML
    private Button editButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Label statusLabel;

    private boolean operationInProgress;

    public CategoryController(CategoryService categoryService, String authenticatedRole) {
        this.categoryService = Objects.requireNonNull(categoryService);
        if (!ADMIN_ROLE.equals(authenticatedRole)) {
            throw new SecurityException("Only Admin users can manage categories.");
        }
        this.authenticatedRole = authenticatedRole;
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(
                cell -> new ReadOnlyObjectWrapper<Number>(cell.getValue().getId()));
        nameColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        categoryTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousCategory, selectedCategory) -> updateActionButtons());
        updateActionButtons();
        refreshCategories();
    }

    @FXML
    private void handleAddCategory() {
        if (operationInProgress) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add category");
        dialog.setHeaderText("Create a category");
        dialog.setContentText("Category name:");
        dialog.showAndWait().ifPresent(name -> {
            Category category = new Category(name);
            Task<Category> task = new Task<>() {
                @Override
                protected Category call() throws SQLException {
                    return categoryService.createCategory(authenticatedRole, category);
                }
            };
            runTask(task, createdCategory -> {
                showMessage(
                        Alert.AlertType.INFORMATION,
                        "Category created",
                        "Category '" + createdCategory.getName() + "' was created.");
                refreshCategories();
            });
        });
    }

    @FXML
    private void handleEditCategory() {
        if (operationInProgress) {
            return;
        }

        Category selectedCategory = categoryTable.getSelectionModel().getSelectedItem();
        if (selectedCategory == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selectedCategory.getName());
        dialog.setTitle("Edit category");
        dialog.setHeaderText("Update category");
        dialog.setContentText("Category name:");
        dialog.showAndWait().ifPresent(name -> {
            Category updatedCategory = new Category(selectedCategory.getId(), name);
            Task<CategoryService.UpdateResult> task = new Task<>() {
                @Override
                protected CategoryService.UpdateResult call() throws SQLException {
                    return categoryService.updateCategory(
                            authenticatedRole, updatedCategory);
                }
            };
            runTask(task, result -> {
                switch (result) {
                    case UPDATED -> {
                        refreshCategories();
                        showMessage(
                                Alert.AlertType.INFORMATION,
                                "Category updated",
                                "Category was updated successfully.");
                    }
                    case UNCHANGED -> showMessage(
                            Alert.AlertType.INFORMATION,
                            "No changes",
                            "No changes were made.");
                }
            });
        });
    }

    @FXML
    private void handleDeleteCategory() {
        if (operationInProgress) {
            return;
        }

        Category selectedCategory = categoryTable.getSelectionModel().getSelectedItem();
        if (selectedCategory == null) {
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete category");
        confirmation.setHeaderText("Delete '" + selectedCategory.getName() + "'?");
        confirmation.setContentText(
                "A category assigned to products cannot be deleted.");
        confirmation.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> answer = confirmation.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws SQLException {
                return categoryService.deleteCategory(
                        authenticatedRole, selectedCategory.getId());
            }
        };
        runTask(task, deleted -> {
            if (!deleted) {
                throw new IllegalStateException(
                        "Category with ID " + selectedCategory.getId() + " does not exist.");
            }
            showMessage(
                    Alert.AlertType.INFORMATION,
                    "Category deleted",
                    "Category '" + selectedCategory.getName() + "' was deleted.");
            refreshCategories();
        });
    }

    @FXML
    private void handleRefreshCategories() {
        refreshCategories();
    }

    private void refreshCategories() {
        if (operationInProgress) {
            return;
        }

        statusLabel.setText("Loading categories...");
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() throws SQLException {
                return categoryService.getAllCategories();
            }
        };
        runTask(task, categories -> {
            categoryTable.getItems().setAll(categories);
            statusLabel.setText(
                    categories.size() == 1
                            ? "1 category"
                            : categories.size() + " categories");
        });
    }

    private <T> void runTask(Task<T> task, Consumer<T> onSuccess) {
        if (operationInProgress) {
            return;
        }

        setOperationInProgress(true);
        task.setOnSucceeded(event -> {
            setOperationInProgress(false);
            try {
                onSuccess.accept(task.getValue());
            } catch (RuntimeException exception) {
                LOGGER.log(
                        Level.SEVERE,
                        "Category operation completed, but the UI could not be updated.",
                        exception);
                String message =
                        "The operation completed, but the screen could not be updated.";
                statusLabel.setText(message);
                showMessage(Alert.AlertType.ERROR, "Display update failed", message);
            }
        });
        task.setOnFailed(event -> {
            setOperationInProgress(false);
            Throwable failure = task.getException();
            if (failure == null) {
                failure = new IllegalStateException("The category operation failed.");
            }
            showFailure(failure);
        });
        task.setOnCancelled(event -> {
            setOperationInProgress(false);
            statusLabel.setText("Category operation cancelled.");
        });

        try {
            Thread worker = new Thread(task, "category-management");
            worker.setDaemon(true);
            worker.start();
        } catch (RuntimeException exception) {
            setOperationInProgress(false);
            LOGGER.log(Level.SEVERE, "Unable to start a category operation.", exception);
            String message = "Unable to start the category operation. Please try again.";
            statusLabel.setText(message);
            showMessage(Alert.AlertType.ERROR, "Category operation unavailable", message);
        }
    }

    private void setOperationInProgress(boolean inProgress) {
        operationInProgress = inProgress;
        updateActionButtons();
    }

    private void updateActionButtons() {
        boolean hasSelection = categoryTable.getSelectionModel().getSelectedItem() != null;
        addButton.setDisable(operationInProgress);
        editButton.setDisable(operationInProgress || !hasSelection);
        deleteButton.setDisable(operationInProgress || !hasSelection);
        refreshButton.setDisable(operationInProgress);
    }

    private void showFailure(Throwable failure) {
        String message;
        if (failure instanceof IllegalArgumentException
                || failure instanceof IllegalStateException
                || failure instanceof SecurityException) {
            message = failure.getMessage();
        } else {
            LOGGER.log(Level.SEVERE, "Category management operation failed.", failure);
            message = failure instanceof SQLException
                    ? "The database could not complete this operation. Please try again."
                    : "An unexpected error occurred. Please try again.";
        }

        statusLabel.setText(message);
        showMessage(Alert.AlertType.ERROR, "Category operation failed", message);
    }

    private void showMessage(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
