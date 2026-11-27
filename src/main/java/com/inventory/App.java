package com.inventory;

import com.inventory.controller.LoginController;
import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.dao.SaleDao;
import com.inventory.dao.SaleItemDao;
import com.inventory.dao.StockMovementDao;
import com.inventory.dao.UserDao;
import com.inventory.service.CategoryService;
import com.inventory.service.ProductService;
import com.inventory.service.SaleService;
import com.inventory.service.UserService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        CategoryDao categoryDao = new CategoryDao();
        ProductDao productDao = new ProductDao();
        SaleDao saleDao = new SaleDao();
        SaleItemDao saleItemDao = new SaleItemDao();
        StockMovementDao stockMovementDao = new StockMovementDao();

        CategoryService categoryService = new CategoryService(categoryDao);
        ProductService productService =
                new ProductService(productDao, categoryDao, stockMovementDao);
        SaleService saleService =
                new SaleService(productDao, saleDao, saleItemDao, stockMovementDao);
        UserService userService = new UserService(new UserDao());
        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/com/inventory/view/login-view.fxml"));
        loader.setControllerFactory(controllerType -> {
            if (controllerType == LoginController.class) {
                return new LoginController(
                        userService,
                        categoryService,
                        productService,
                        saleService,
                        saleDao,
                        saleItemDao);
            }
            throw new IllegalStateException(
                    "No controller factory is configured for " + controllerType.getName());
        });
        Parent root = loader.load();

        Scene scene = new Scene(root, 480, 560);
        scene.getStylesheets().add(
                App.class.getResource("/com/inventory/css/login.css").toExternalForm());

        stage.setTitle("Inventory Management System");
        stage.setMinWidth(420);
        stage.setMinHeight(500);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
