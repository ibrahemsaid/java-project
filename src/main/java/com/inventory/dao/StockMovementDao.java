package com.inventory.dao;

import com.inventory.model.StockMovement;
import com.inventory.model.StockMovementType;
import com.inventory.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class StockMovementDao {
    private static final String SELECT_COLUMNS = """
            SELECT id, product_id, user_id, movement_type, quantity_change,
                   quantity_before, quantity_after, reason, reference_sale_id, created_at
            FROM stock_movements
            """;

    public StockMovement save(StockMovement movement, Connection connection) throws SQLException {
        String sql = """
                INSERT INTO stock_movements (
                    product_id, user_id, movement_type, quantity_change,
                    quantity_before, quantity_after, reason, reference_sale_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, movement.getProductId());
            statement.setLong(2, movement.getUserId());
            statement.setString(3, movement.getMovementType().name());
            statement.setInt(4, movement.getQuantityChange());
            statement.setInt(5, movement.getQuantityBefore());
            statement.setInt(6, movement.getQuantityAfter());
            statement.setString(7, movement.getReason());

            if (movement.getReferenceSaleId() == null) {
                statement.setNull(8, Types.BIGINT);
            } else {
                statement.setLong(8, movement.getReferenceSaleId());
            }

            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    movement.setId(generatedKeys.getLong(1));
                    return movement;
                }
            }
        }

        throw new SQLException("Inserting the stock movement did not return a generated ID.");
    }

    public List<StockMovement> findAll() throws SQLException {
        String sql = SELECT_COLUMNS
                + " ORDER BY created_at DESC, id DESC";
        List<StockMovement> movements = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                movements.add(mapStockMovement(resultSet));
            }
        }

        return movements;
    }

    public List<StockMovement> findByProductId(long productId) throws SQLException {
        String sql = SELECT_COLUMNS
                + " WHERE product_id = ?"
                + " ORDER BY created_at DESC, id DESC";
        List<StockMovement> movements = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    movements.add(mapStockMovement(resultSet));
                }
            }
        }

        return movements;
    }

    public List<StockMovement> findBySaleId(long saleId) throws SQLException {
        String sql = SELECT_COLUMNS
                + " WHERE reference_sale_id = ?"
                + " ORDER BY created_at DESC, id DESC";
        List<StockMovement> movements = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, saleId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    movements.add(mapStockMovement(resultSet));
                }
            }
        }

        return movements;
    }

    private StockMovement mapStockMovement(ResultSet resultSet) throws SQLException {
        Long referenceSaleId = resultSet.getObject("reference_sale_id", Long.class);
        Timestamp createdAt = resultSet.getTimestamp("created_at");

        return new StockMovement(
                resultSet.getLong("id"),
                resultSet.getLong("product_id"),
                resultSet.getLong("user_id"),
                StockMovementType.valueOf(resultSet.getString("movement_type")),
                resultSet.getInt("quantity_change"),
                resultSet.getInt("quantity_before"),
                resultSet.getInt("quantity_after"),
                resultSet.getString("reason"),
                referenceSaleId,
                createdAt == null ? null : createdAt.toLocalDateTime());
    }
}
