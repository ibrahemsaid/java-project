package com.inventory;

import com.inventory.dao.UserDao;
import com.inventory.model.User;
import com.inventory.service.UserService;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public final class UserServiceTest {
    private UserServiceTest() {
    }

    public static void main(String[] args) throws SQLException, GeneralSecurityException {
        UserService userService = new UserService(new UserDao());
        String username = "ims_test_" + UUID.randomUUID().toString().replace("-", "");
        String missingUsername = username + "_missing";
        String role = "TEST";
        char[] password = "Temporary-Test-Password-21!".toCharArray();
        char[] originalPassword = password.clone();
        char[] incorrectPassword = "Incorrect-Test-Password-21!".toCharArray();
        char[] emptyPassword = new char[0];
        char[] whitespacePassword = {' ', '\t'};

        try {
            System.out.println("Test username: " + username);
            User registeredUser = userService.registerUser(
                    " " + username + " ", password, role);

            check(registeredUser.getId() > 0, "Registration returns a positive generated ID.");
            check(username.equals(registeredUser.getUsername()),
                    "Registration trims the username.");

            String storedHash = registeredUser.getPasswordHash();
            check(storedHash != null, "Registration returns a stored password hash.");
            check(!storedHash.contentEquals(java.nio.CharBuffer.wrap(password)),
                    "Stored password hash differs from the plaintext password.");
            check(isExpectedPbkdf2Format(storedHash),
                    "Stored password hash uses the versioned PBKDF2 format.");
            check(Arrays.equals(originalPassword, password),
                    "Password hashing does not modify the caller-owned password array.");

            Optional<User> correctLogin = userService.authenticate(username, password);
            check(correctLogin.isPresent()
                            && correctLogin.get().getId() == registeredUser.getId(),
                    "Correct password authenticates the expected user.");

            check(userService.authenticate(username, incorrectPassword).isEmpty(),
                    "Incorrect password is rejected with an empty result.");
            check(userService.authenticate(missingUsername, password).isEmpty(),
                    "Nonexistent username is rejected with an empty result.");

            expectIllegalArgument(
                    () -> userService.registerUser(username, password, role),
                    "Duplicate username registration is rejected.");

            expectIllegalArgument(
                    () -> userService.registerUser(null, password, role),
                    "Null username is rejected.");
            expectIllegalArgument(
                    () -> userService.registerUser("", password, role),
                    "Blank username is rejected.");
            expectIllegalArgument(
                    () -> userService.registerUser(" \t ", password, role),
                    "Whitespace-only username is rejected.");

            expectIllegalArgument(
                    () -> userService.registerUser(username + "_null_password", null, role),
                    "Null password is rejected.");
            expectIllegalArgument(
                    () -> userService.registerUser(
                            username + "_empty_password", emptyPassword, role),
                    "Empty password is rejected.");
            expectIllegalArgument(
                    () -> userService.registerUser(
                            username + "_blank_password", whitespacePassword, role),
                    "Whitespace-only password is rejected.");

            expectIllegalArgument(
                    () -> userService.registerUser(username + "_null_role", password, null),
                    "Null role is rejected.");
            expectIllegalArgument(
                    () -> userService.registerUser(username + "_blank_role", password, " \t "),
                    "Blank role is rejected.");

            System.out.println("UserService integration checks completed.");
        } catch (AssertionError failure) {
            System.err.println("FAIL: " + failure.getMessage());
            throw failure;
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(originalPassword, '\0');
            Arrays.fill(incorrectPassword, '\0');
            Arrays.fill(emptyPassword, '\0');
            Arrays.fill(whitespacePassword, '\0');
        }
    }

    private static boolean isExpectedPbkdf2Format(String hash) {
        String[] parts = hash.split("\\$", -1);
        if (parts.length != 6
                || !"pbkdf2-sha256".equals(parts[0])
                || !"v1".equals(parts[1])
                || !"600000".equals(parts[2])
                || !"256".equals(parts[3])) {
            return false;
        }

        try {
            return java.util.Base64.getDecoder().decode(parts[4]).length == 16
                    && java.util.Base64.getDecoder().decode(parts[5]).length == 32;
        } catch (IllegalArgumentException malformedEncoding) {
            return false;
        }
    }

    private static void expectIllegalArgument(
            CheckedOperation operation, String message)
            throws SQLException, GeneralSecurityException {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            System.out.println("PASS: " + message);
            return;
        }

        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("PASS: " + message);
    }

    @FunctionalInterface
    private interface CheckedOperation {
        Object run() throws SQLException, GeneralSecurityException;
    }
}
