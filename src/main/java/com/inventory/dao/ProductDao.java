package com.inventory.dao;

import com.inventory.model.Product;
import com.inventory.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDao {
    private static final String SELECT_COLUMNS = """
            SELECT id, name, code, category_id, purchase_price, selling_price,
                   quantity, minimum_stock
            FROM products
            """;

    public List<Product> findAll() throws SQLException {
        String sql = SELECT_COLUMNS + " ORDER BY id";
        List<Product> products = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                products.add(mapProduct(resultSet));
            }
        }

        return products;
    }

    public Optional<Product> findById(long id) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapProduct(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Product> findByCode(String code) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE code = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapProduct(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    public List<Product> findByCategoryId(long categoryId) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE category_id = ? ORDER BY id";
        List<Product> products = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, categoryId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    products.add(mapProduct(resultSet));
                }
            }
        }

        return products;
    }

    public Product save(Product product) throws SQLException {
        String sql = """
                INSERT INTO products (
                    name, code, category_id, purchase_price, selling_price, quantity, minimum_stock
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, product.getName());
            statement.setString(2, product.getCode());
            statement.setLong(3, product.getCategoryId());
            statement.setBigDecimal(4, product.getPurchasePrice());
            statement.setBigDecimal(5, product.getSellingPrice());
            statement.setInt(6, product.getQuantity());
            statement.setInt(7, product.getMinimumStock());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    product.setId(generatedKeys.getLong(1));
                    return product;
                }
            }
        }

        throw new SQLException("Inserting the product did not return a generated ID.");
    }

    public boolean update(Product product) throws SQLException {
        String sql = """
                UPDATE products
                SET name = ?, code = ?, category_id = ?, purchase_price = ?,
                    selling_price = ?, quantity = ?, minimum_stock = ?
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.getName());
            statement.setString(2, product.getCode());
            statement.setLong(3, product.getCategoryId());
            statement.setBigDecimal(4, product.getPurchasePrice());
            statement.setBigDecimal(5, product.getSellingPrice());
            statement.setInt(6, product.getQuantity());
            statement.setInt(7, product.getMinimumStock());
            statement.setLong(8, product.getId());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteById(long id) throws SQLException {
        String sql = "DELETE FROM products WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    private Product mapProduct(ResultSet resultSet) throws SQLException {
        return new Product(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("code"),
                resultSet.getLong("category_id"),
                resultSet.getBigDecimal("purchase_price"),
                resultSet.getBigDecimal("selling_price"),
                resultSet.getInt("quantity"),
                resultSet.getInt("minimum_stock"));
    }
}
