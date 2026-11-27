package com.inventory.service;

import com.inventory.dao.ProductDao;
import com.inventory.dao.StockMovementDao;
import com.inventory.model.Product;
import com.inventory.model.StockMovement;
import com.inventory.model.StockMovementType;
import com.inventory.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class StockMovementService {
    private final ProductDao productDao;
    private final StockMovementDao stockMovementDao;

    public StockMovementService(ProductDao productDao, StockMovementDao stockMovementDao) {
        this.productDao = productDao;
        this.stockMovementDao = stockMovementDao;
    }

    public List<StockMovement> getAllMovements() throws SQLException {
        return stockMovementDao.findAll();
    }

    public List<StockMovement> getMovementsByProductId(long productId) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive.");
        }
        return stockMovementDao.findByProductId(productId);
    }

    public StockMovement adjustStock(
            long productId,
            long authenticatedUserId,
            int quantityChange,
            String reason) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive.");
        }
        if (authenticatedUserId <= 0) {
            throw new IllegalArgumentException("Authenticated user ID must be positive.");
        }
        if (quantityChange == 0) {
            throw new IllegalArgumentException("Quantity change must not be zero.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank.");
        }

        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 255) {
            throw new IllegalArgumentException("Reason must not exceed 255 characters.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean restoreAutoCommit = true;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);

                Optional<Product> lockedProduct =
                        productDao.findByIdForUpdate(productId, connection);
                if (lockedProduct.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Product with ID " + productId + " does not exist.");
                }

                int quantityBefore = lockedProduct.get().getQuantity();
                long quantityAfterLong = (long) quantityBefore + quantityChange;
                if (quantityAfterLong < 0 || quantityAfterLong > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Stock quantity after adjustment is outside the supported range.");
                }
                int quantityAfter = (int) quantityAfterLong;

                int updatedRows =
                        productDao.changeStockQuantity(productId, quantityChange, connection);
                if (updatedRows != 1) {
                    throw new IllegalStateException(
                            "The product stock quantity could not be updated.");
                }

                StockMovement movement = new StockMovement(
                        productId,
                        authenticatedUserId,
                        StockMovementType.ADJUSTMENT,
                        quantityChange,
                        quantityBefore,
                        quantityAfter,
                        normalizedReason,
                        null);
                StockMovement savedMovement = stockMovementDao.save(movement, connection);
                connection.commit();
                return savedMovement;
            } catch (SQLException | RuntimeException exception) {
                transactionFailure = exception;
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    restoreAutoCommit = false;
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                if (restoreAutoCommit) {
                    try {
                        connection.setAutoCommit(originalAutoCommit);
                    } catch (SQLException restoreException) {
                        if (transactionFailure != null) {
                            transactionFailure.addSuppressed(restoreException);
                        } else {
                            throw restoreException;
                        }
                    }
                }
            }
        }
    }
}
