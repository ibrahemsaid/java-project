package com.inventory.dao;

import com.inventory.model.User;
import com.inventory.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class UserDao {
    private static final String USER_WRITE_LOCK = "inventory_user_creation";
    private static final int USER_WRITE_LOCK_TIMEOUT_SECONDS = 10;
    private static final String SELECT_COLUMNS =
            "SELECT id, username, password_hash, role, created_at FROM users";

    public boolean hasUsers() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return hasUsers(connection);
        }
    }

    public Optional<User> findById(long id) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapUser(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE username = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapUser(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    public User save(User user) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            acquireUserWriteLock(connection);
            return insertUser(connection, user);
        }
    }

    public Optional<User> saveInitialAdminIfNoUsers(User user) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            acquireUserWriteLock(connection);
            if (hasUsers(connection)) {
                return Optional.empty();
            }

            return Optional.of(insertUser(connection, user));
        }
    }

    private boolean hasUsers(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT 1 FROM users LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    private void acquireUserWriteLock(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, USER_WRITE_LOCK);
            statement.setInt(2, USER_WRITE_LOCK_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1 || resultSet.wasNull()) {
                    throw new SQLException(
                            "Could not acquire the database lock for user creation.");
                }
            }
        }
    }

    private User insertUser(Connection connection, User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getRole());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getLong(1));
                } else {
                    throw new SQLException("Inserting the user did not return a generated ID.");
                }
            }
        }

        try (PreparedStatement createdAtStatement =
                     connection.prepareStatement("SELECT created_at FROM users WHERE id = ?")) {
            createdAtStatement.setLong(1, user.getId());
            try (ResultSet resultSet = createdAtStatement.executeQuery()) {
                if (resultSet.next()) {
                    user.setCreatedAt(resultSet.getTimestamp("created_at"));
                }
            }
        }

        return user;
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        return new User(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("role"),
                resultSet.getTimestamp("created_at"));
    }
}
