package com.inventory.dao;

import com.inventory.model.Sale;
import com.inventory.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SaleDao {
    private static final String SELECT_COLUMNS =
            "SELECT id, user_id, sale_date, total_amount FROM sales";

    public Sale save(Sale sale, Connection connection) throws SQLException {
        String sql;
        boolean useSuppliedSaleDate = sale.getSaleDate() != null;
        if (useSuppliedSaleDate) {
            sql = """
                    INSERT INTO sales (user_id, sale_date, total_amount)
                    VALUES (?, ?, ?)
                    """;
        } else {
            sql = """
                    INSERT INTO sales (user_id, total_amount)
                    VALUES (?, ?)
                    """;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, sale.getUserId());
            if (useSuppliedSaleDate) {
                statement.setTimestamp(2, Timestamp.valueOf(sale.getSaleDate()));
                statement.setBigDecimal(3, sale.getTotalAmount());
            } else {
                statement.setBigDecimal(2, sale.getTotalAmount());
            }
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    sale.setId(generatedKeys.getLong(1));
                } else {
                    throw new SQLException("Inserting the sale did not return a generated ID.");
                }
            }
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT sale_date FROM sales WHERE id = ?")) {
            statement.setLong(1, sale.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Timestamp saleDate = resultSet.getTimestamp("sale_date");
                    sale.setSaleDate(
                            saleDate == null ? null : saleDate.toLocalDateTime());
                }
            }
        }

        return sale;
    }

    public List<Sale> findAll() throws SQLException {
        String sql = SELECT_COLUMNS + " ORDER BY sale_date DESC, id DESC";
        List<Sale> sales = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                sales.add(mapSale(resultSet));
            }
        }

        return sales;
    }

    public Optional<Sale> findById(long id) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapSale(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    private Sale mapSale(ResultSet resultSet) throws SQLException {
        Timestamp saleDate = resultSet.getTimestamp("sale_date");
        return new Sale(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                saleDate == null ? null : saleDate.toLocalDateTime(),
                resultSet.getBigDecimal("total_amount"));
    }
}
