package com.inventory.service;

import com.inventory.dao.UserDao;
import com.inventory.model.User;
import com.inventory.util.PasswordHasher;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Optional;

public class UserService {
    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public User registerUser(String username, char[] password, String role)
            throws SQLException, GeneralSecurityException {
        String normalizedUsername = normalizeRequired(username, "Username");
        requirePassword(password);
        String normalizedRole = normalizeRequired(role, "Role");

        if (userDao.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("A user with that username already exists.");
        }

        String passwordHash = PasswordHasher.hash(password);
        User user = new User(normalizedUsername, passwordHash, normalizedRole);
        return userDao.save(user);
    }

    public boolean usersExist() throws SQLException {
        return userDao.hasUsers();
    }

    public User createInitialAdmin(String username, char[] password)
            throws SQLException, GeneralSecurityException {
        String normalizedUsername = normalizeRequired(username, "Username");
        requirePassword(password);

        if (usersExist()) {
            throw new IllegalStateException(
                    "Initial administrator setup is only allowed when the users table is empty.");
        }

        String passwordHash = PasswordHasher.hash(password);
        User user = new User(normalizedUsername, passwordHash, "ADMIN");
        return userDao.saveInitialAdminIfNoUsers(user).orElseThrow(
                () -> new IllegalStateException(
                        "Initial administrator setup is only allowed when the users table is empty."));
    }

    public Optional<User> authenticate(String username, char[] password)
            throws SQLException, GeneralSecurityException {
        String normalizedUsername = normalizeRequired(username, "Username");
        requirePassword(password);

        Optional<User> user = userDao.findByUsername(normalizedUsername);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        if (!PasswordHasher.verify(password, user.get().getPasswordHash())) {
            return Optional.empty();
        }

        return user;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value.trim();
    }

    private void requirePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be blank.");
        }
        for (char character : password) {
            if (!Character.isWhitespace(character)) {
                return;
            }
        }
        throw new IllegalArgumentException("Password must not be blank.");
    }
}
