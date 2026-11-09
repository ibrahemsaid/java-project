package com.inventory.dao;

import com.inventory.model.Category;
import com.inventory.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoryDao {
    private static final String SELECT_COLUMNS = "SELECT id, name FROM categories";

    public List<Category> findAll() throws SQLException {
        String sql = SELECT_COLUMNS + " ORDER BY id";
        List<Category> categories = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                categories.add(mapCategory(resultSet));
            }
        }

        return categories;
    }

    public Optional<Category> findById(long id) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapCategory(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Category> findByName(String name) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE name = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapCategory(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    public Category save(Category category) throws SQLException {
        String sql = "INSERT INTO categories (name) VALUES (?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getName());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    category.setId(generatedKeys.getLong(1));
                    return category;
                }
            }
        }

        throw new SQLException("Inserting the category did not return a generated ID.");
    }

    public boolean update(Category category) throws SQLException {
        String sql = "UPDATE categories SET name = ? WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.getName());
            statement.setLong(2, category.getId());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteById(long id) throws SQLException {
        String sql = "DELETE FROM categories WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    private Category mapCategory(ResultSet resultSet) throws SQLException {
        return new Category(resultSet.getLong("id"), resultSet.getString("name"));
    }
}
