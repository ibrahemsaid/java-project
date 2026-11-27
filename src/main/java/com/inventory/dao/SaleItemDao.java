package com.inventory.dao;

import com.inventory.model.SaleItem;
import com.inventory.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SaleItemDao {
    private static final String SELECT_COLUMNS =
            "SELECT id, sale_id, product_id, product_name, product_code, "
                    + "quantity, unit_price, subtotal FROM sale_items";

    public SaleItem save(SaleItem saleItem, Connection connection) throws SQLException {
        String sql = """
                INSERT INTO sale_items (
                    sale_id, product_id, product_name, product_code, quantity, unit_price, subtotal
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, saleItem.getSaleId());
            statement.setLong(2, saleItem.getProductId());
            statement.setString(3, saleItem.getProductName());
            statement.setString(4, saleItem.getProductCode());
            statement.setInt(5, saleItem.getQuantity());
            statement.setBigDecimal(6, saleItem.getUnitPrice());
            statement.setBigDecimal(7, saleItem.getSubtotal());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    saleItem.setId(generatedKeys.getLong(1));
                    return saleItem;
                }
            }
        }

        throw new SQLException(
                "Inserting the sale item did not return a generated ID.");
    }

    public List<SaleItem> findBySaleId(long saleId) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE sale_id = ? ORDER BY id";
        List<SaleItem> saleItems = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, saleId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    saleItems.add(mapSaleItem(resultSet));
                }
            }
        }

        return saleItems;
    }

    private SaleItem mapSaleItem(ResultSet resultSet) throws SQLException {
        return new SaleItem(
                resultSet.getLong("id"),
                resultSet.getLong("sale_id"),
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getString("product_code"),
                resultSet.getInt("quantity"),
                resultSet.getBigDecimal("unit_price"),
                resultSet.getBigDecimal("subtotal"));
    }
}
