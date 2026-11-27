package com.inventory;

import com.inventory.controller.MainController;
import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.dao.SaleDao;
import com.inventory.dao.SaleItemDao;
import com.inventory.dao.StockMovementDao;
import com.inventory.model.Category;
import com.inventory.service.CategoryService;
import com.inventory.service.ProductService;
import com.inventory.service.SaleService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class CategoryUiTest {
    private CategoryUiTest() {
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.startup(() -> {
            try {
                CategoryService categoryService = createInMemoryService();
                Parent adminRoot = loadMainView(categoryService);
                MainController adminController = getController(adminRoot);
                adminController.initializeAuthenticatedUser(1, "admin", "ADMIN");

                Button adminCategoriesButton =
                        (Button) adminRoot.lookup("#categoriesButton");
                require(
                        adminCategoriesButton.isVisible() && adminCategoriesButton.isManaged(),
                        "Admin can see the Categories navigation button.");
                adminCategoriesButton.fire();

                VBox adminContent = getContentArea(adminController);
                Parent categoriesView = (Parent) adminContent.getChildren().get(0);
                require(
                        categoriesView.lookup("#categoryTable") instanceof TableView<?>,
                        "Categories FXML loads and provides its TableView.");
                require(
                        categoriesView.lookup("#addButton") instanceof Button
                                && categoriesView.lookup("#editButton") instanceof Button
                                && categoriesView.lookup("#deleteButton") instanceof Button
                                && categoriesView.lookup("#refreshButton") instanceof Button,
                        "Categories FXML provides all required action buttons.");

                Parent regularRoot = loadMainView(categoryService);
                MainController regularController = getController(regularRoot);
                regularController.initializeAuthenticatedUser(2, "regular", "USER");
                Button regularCategoriesButton =
                        (Button) regularRoot.lookup("#categoriesButton");
                require(
                        !regularCategoriesButton.isVisible()
                                && !regularCategoriesButton.isManaged(),
                        "Regular users cannot see the Categories navigation button.");
                invokeCategoriesNavigation(regularController);

                VBox regularContent = getContentArea(regularController);
                Parent accessDeniedView = (Parent) regularContent.getChildren().get(1);
                require(
                        accessDeniedView.lookup("#accessDeniedMessage") != null,
                        "The navigation handler blocks regular users.");
                System.out.println("Category UI checks passed.");
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                completed.countDown();
            }
        });

        if (!completed.await(15, TimeUnit.SECONDS)) {
            Platform.exit();
            throw new AssertionError("JavaFX UI checks timed out.");
        }
        Platform.exit();

        Throwable testFailure = failure.get();
        if (testFailure != null) {
            throw new AssertionError("Category UI checks failed.", testFailure);
        }
    }

    private static Parent loadMainView(CategoryService categoryService) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                CategoryUiTest.class.getResource("/com/inventory/view/main-view.fxml"));
        loader.setControllerFactory(controllerType -> {
            if (controllerType == MainController.class) {
                ProductDao productDao = new ProductDao();
                SaleDao saleDao = new SaleDao();
                SaleItemDao saleItemDao = new SaleItemDao();
                StockMovementDao stockMovementDao = new StockMovementDao();
                return new MainController(
                        categoryService,
                        new ProductService(
                                productDao, new CategoryDao(), stockMovementDao),
                        new SaleService(
                                productDao, saleDao, saleItemDao, stockMovementDao),
                        saleDao,
                        saleItemDao);
            }
            throw new IllegalStateException(
                    "No controller factory is configured for " + controllerType.getName());
        });
        Parent root = loader.load();
        root.setUserData(loader.getController());
        return root;
    }

    private static MainController getController(Parent root) {
        if (!(root.getUserData() instanceof MainController)) {
            throw new AssertionError("Main controller was not initialized.");
        }
        return (MainController) root.getUserData();
    }

    private static VBox getContentArea(MainController controller) throws ReflectiveOperationException {
        Field field = MainController.class.getDeclaredField("contentArea");
        field.setAccessible(true);
        return (VBox) field.get(controller);
    }

    private static void invokeCategoriesNavigation(MainController controller)
            throws ReflectiveOperationException {
        Method method = MainController.class.getDeclaredMethod("handleCategoriesNavigation");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static CategoryService createInMemoryService() {
        return new CategoryService(new CategoryDao()) {
            @Override
            public List<Category> getAllCategories() {
                return List.of(new Category(1, "Office"));
            }

            @Override
            public Category createCategory(String authenticatedRole, Category category)
                    throws SQLException {
                category.setId(2);
                return category;
            }

            @Override
            public CategoryService.UpdateResult updateCategory(
                    String authenticatedRole, Category category)
                    throws SQLException {
                return CategoryService.UpdateResult.UPDATED;
            }

            @Override
            public boolean deleteCategory(String authenticatedRole, long id)
                    throws SQLException {
                return true;
            }
        };
    }

    private static void require(boolean condition, String checkName) {
        if (!condition) {
            throw new AssertionError(checkName);
        }
    }
}
