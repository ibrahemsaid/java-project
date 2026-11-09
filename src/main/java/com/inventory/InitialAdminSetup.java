package com.inventory;

import com.inventory.dao.UserDao;
import com.inventory.service.UserService;

import java.io.Console;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Arrays;

public final class InitialAdminSetup {
    private InitialAdminSetup() {
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println(
                    "A terminal console is required. Run this utility directly from a terminal.");
            return;
        }

        char[] password = null;
        try {
            String username = console.readLine("Initial administrator username: ");
            if (username == null) {
                console.printf("Setup cancelled.%n");
                return;
            }

            password = console.readPassword("Initial administrator password: ");
            if (password == null) {
                console.printf("Password input was cancelled.%n");
                return;
            }

            UserService userService = new UserService(new UserDao());
            userService.createInitialAdmin(username, password);
            console.printf("Initial administrator account created for '%s'.%n", username.trim());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println("Setup could not be completed: " + exception.getMessage());
        } catch (SQLException exception) {
            if (isDuplicateKey(exception)) {
                System.err.println("A user with that username already exists.");
            } else {
                System.err.println(
                        "Database error while creating the initial administrator: "
                                + exception.getMessage());
            }
        } catch (GeneralSecurityException exception) {
            System.err.println(
                    "Password protection failed; the administrator account was not created.");
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static boolean isDuplicateKey(SQLException exception) {
        for (SQLException current = exception;
             current != null;
             current = current.getNextException()) {
            if (current.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }
}
