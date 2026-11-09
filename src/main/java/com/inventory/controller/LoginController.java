package com.inventory.controller;

import com.inventory.model.User;
import com.inventory.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

public class LoginController {
    private final UserService userService;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label feedbackLabel;

    private boolean loginInProgress;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    @FXML
    private void handleLogin() {
        if (loginInProgress) {
            return;
        }

        String username = usernameField.getText();
        String passwordText = passwordField.getText();
        if (username == null || username.isBlank()) {
            feedbackLabel.setText("Enter your username.");
            passwordField.clear();
            return;
        }
        if (passwordText == null || passwordText.isBlank()) {
            feedbackLabel.setText("Enter your password.");
            passwordField.clear();
            return;
        }

        char[] password = passwordText.toCharArray();
        loginInProgress = true;
        loginButton.setDisable(true);
        feedbackLabel.setText("");

        try {
            Optional<User> authenticatedUser = userService.authenticate(username, password);
            if (authenticatedUser.isEmpty()) {
                feedbackLabel.setText("Invalid username or password.");
                return;
            }

            showMainView(authenticatedUser.get());
        } catch (SQLException | GeneralSecurityException | IOException exception) {
            feedbackLabel.setText("Unable to sign in right now. Please try again later.");
        } finally {
            Arrays.fill(password, '\0');
            passwordField.clear();
            loginInProgress = false;
            loginButton.setDisable(false);
        }
    }

    private void showMainView(User user) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/inventory/view/main-view.fxml"));
        Parent root = loader.load();

        Stage stage = (Stage) loginButton.getScene().getWindow();
        MainController mainController = loader.getController();
        mainController.initializeAuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
        mainController.setLogoutAction(() -> returnToLogin(stage));

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(
                getClass().getResource("/com/inventory/css/main.css").toExternalForm());

        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setWidth(1100);
        stage.setHeight(720);
        stage.setScene(scene);
    }

    private boolean returnToLogin(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/inventory/view/login-view.fxml"));
            loader.setControllerFactory(controllerType -> {
                if (controllerType == LoginController.class) {
                    return new LoginController(userService);
                }
                throw new IllegalStateException(
                        "No controller factory is configured for " + controllerType.getName());
            });
            Parent root = loader.load();

            Scene scene = new Scene(root, 480, 560);
            scene.getStylesheets().add(
                    getClass().getResource("/com/inventory/css/login.css").toExternalForm());

            stage.setMinWidth(420);
            stage.setMinHeight(500);
            stage.setWidth(480);
            stage.setHeight(560);
            stage.setScene(scene);
            return true;
        } catch (IOException exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Logout failed");
            alert.setHeaderText("Unable to return to the sign-in screen.");
            alert.setContentText("No account data was changed. Please try logging out again.");
            alert.showAndWait();
            return false;
        }
    }

}
