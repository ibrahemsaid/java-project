package com.inventory.service;

import com.inventory.dao.ProductDao;
import com.inventory.dao.SaleDao;
import com.inventory.dao.SaleItemDao;
import com.inventory.dao.StockMovementDao;
import com.inventory.model.Product;
import com.inventory.model.Sale;
import com.inventory.model.SaleItem;
import com.inventory.model.StockMovement;
import com.inventory.model.StockMovementType;
import com.inventory.util.DatabaseConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SaleService {
    private static final Logger LOGGER = Logger.getLogger(SaleService.class.getName());
    private static final int CURRENCY_SCALE = 2;
    private static final int MAX_DECIMAL_PRECISION = 12;
    private static final RoundingMode CURRENCY_ROUNDING = RoundingMode.HALF_UP;

    private final ProductDao productDao;
    private final SaleDao saleDao;
    private final SaleItemDao saleItemDao;
    private final StockMovementDao stockMovementDao;
    private final ConnectionProvider connectionProvider;

    public SaleService(
            ProductDao productDao,
            SaleDao saleDao,
            SaleItemDao saleItemDao,
            StockMovementDao stockMovementDao) {
        this(
                productDao,
                saleDao,
                saleItemDao,
                stockMovementDao,
                DatabaseConnection::getConnection);
    }

    SaleService(
            ProductDao productDao,
            SaleDao saleDao,
            SaleItemDao saleItemDao,
            StockMovementDao stockMovementDao,
            ConnectionProvider connectionProvider) {
        this.productDao = Objects.requireNonNull(productDao, "ProductDao must not be null.");
        this.saleDao = Objects.requireNonNull(saleDao, "SaleDao must not be null.");
        this.saleItemDao = Objects.requireNonNull(saleItemDao, "SaleItemDao must not be null.");
        this.stockMovementDao =
                Objects.requireNonNull(stockMovementDao, "StockMovementDao must not be null.");
        this.connectionProvider =
                Objects.requireNonNull(connectionProvider, "Connection provider must not be null.");
    }

    public Sale createSale(
            long authenticatedUserId,
            Collection<RequestedSaleItem> requestedItems) throws SQLException {
        if (authenticatedUserId <= 0) {
            throw new IllegalArgumentException("Authenticated user ID must be positive.");
        }

        TreeMap<Long, RequestedSaleItem> itemsByProductId =
                validateAndSortItems(requestedItems);

        boolean committed = false;
        Sale committedSale = null;
        try {
            try (Connection connection = connectionProvider.open()) {
                boolean originalAutoCommit = connection.getAutoCommit();
                boolean restoreAutoCommit = true;
                Throwable transactionFailure = null;

                try {
                    connection.setAutoCommit(false);

                    List<SaleLine> saleLines = lockAndBuildSaleLines(itemsByProductId, connection);
                    BigDecimal totalAmount = saleLines.stream()
                            .map(SaleLine::subtotal)
                            .reduce(BigDecimal.ZERO.setScale(CURRENCY_SCALE), BigDecimal::add)
                            .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
                    requireDatabaseMoneyRange(totalAmount, "Sale total");

                    Sale sale = saleDao.save(
                            new Sale(authenticatedUserId, totalAmount), connection);
                    if (sale == null || sale.getId() <= 0) {
                        throw new IllegalStateException(
                                "Saving the sale did not produce a valid sale ID.");
                    }

                    for (SaleLine line : saleLines) {
                        SaleItem saleItem = new SaleItem(
                                sale.getId(),
                                line.requestedItem().productId(),
                                line.productName(),
                                line.productCode(),
                                line.requestedItem().quantity(),
                                line.unitPrice(),
                                line.subtotal());
                        saleItemDao.save(saleItem, connection);

                        int quantityChange = -line.requestedItem().quantity();
                        int updatedRows = productDao.changeStockQuantity(
                                line.product().getId(), quantityChange, connection);
                        if (updatedRows != 1) {
                            throw new IllegalStateException(
                                    "Stock for product " + line.product().getId()
                                            + " could not be updated.");
                        }

                        StockMovement movement = new StockMovement(
                                line.product().getId(),
                                authenticatedUserId,
                                StockMovementType.SALE,
                                quantityChange,
                                line.quantityBefore(),
                                line.quantityAfter(),
                                "Sale " + sale.getId(),
                                sale.getId());
                        stockMovementDao.save(movement, connection);
                    }

                    connection.commit();
                    committedSale = sale;
                    committed = true;
                    return sale;
                } catch (SQLException | RuntimeException | Error exception) {
                    transactionFailure = exception;
                    try {
                        connection.rollback();
                    } catch (SQLException | RuntimeException | Error rollbackException) {
                        restoreAutoCommit = false;
                        if (rollbackException != exception) {
                            exception.addSuppressed(rollbackException);
                        }
                    }
                    throw exception;
                } finally {
                    if (restoreAutoCommit) {
                        try {
                            connection.setAutoCommit(originalAutoCommit);
                        } catch (SQLException | RuntimeException | Error restoreException) {
                            if (transactionFailure != null) {
                                if (restoreException != transactionFailure) {
                                    transactionFailure.addSuppressed(restoreException);
                                }
                            } else {
                                throw restoreException;
                            }
                        }
                    }
                }
            }
        } catch (SQLException | RuntimeException | Error exception) {
            if (committed) {
                LOGGER.log(
                        Level.SEVERE,
                        "Sale " + committedSale.getId()
                                + " was committed, but connection cleanup failed.",
                        exception);
                return committedSale;
            }
            throw exception;
        }
    }

    private TreeMap<Long, RequestedSaleItem> validateAndSortItems(
            Collection<RequestedSaleItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("A sale must contain at least one item.");
        }

        TreeMap<Long, RequestedSaleItem> itemsByProductId = new TreeMap<>();
        for (RequestedSaleItem item : requestedItems) {
            if (item == null) {
                throw new IllegalArgumentException("Sale items must not be null.");
            }
            if (item.productId() <= 0) {
                throw new IllegalArgumentException("Product IDs must be positive.");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Sale item quantities must be positive.");
            }
            if (item.expectedUnitPrice() == null
                    || item.expectedUnitPrice().signum() < 0) {
                throw new IllegalArgumentException(
                        "Expected confirmed selling prices must be nonnegative.");
            }
            if (itemsByProductId.putIfAbsent(item.productId(), item) != null) {
                throw new IllegalArgumentException(
                        "A product may appear only once in a sale: " + item.productId());
            }
        }

        if (itemsByProductId.isEmpty()) {
            throw new IllegalArgumentException("A sale must contain at least one item.");
        }
        return itemsByProductId;
    }

    private List<SaleLine> lockAndBuildSaleLines(
            Map<Long, RequestedSaleItem> itemsByProductId,
            Connection connection) throws SQLException {
        TreeMap<Long, Product> lockedProducts = new TreeMap<>();
        for (Long productId : itemsByProductId.keySet()) {
            Optional<Product> product = productDao.findByIdForUpdate(productId, connection);
            if (product.isEmpty()) {
                throw new IllegalArgumentException(
                        "Product with ID " + productId + " does not exist.");
            }
            lockedProducts.put(productId, product.get());
        }

        List<SaleLine> saleLines = new ArrayList<>(itemsByProductId.size());
        for (Map.Entry<Long, RequestedSaleItem> entry : itemsByProductId.entrySet()) {
            RequestedSaleItem requestedItem = entry.getValue();
            Product product = lockedProducts.get(entry.getKey());
            String productName = product.getName();
            if (productName == null || productName.isBlank()) {
                throw new IllegalArgumentException(
                        "Product name for product " + product.getId()
                                + " must not be null or blank.");
            }
            String productCode = product.getCode();
            if (productCode == null || productCode.isBlank()) {
                throw new IllegalArgumentException(
                        "Product code for product " + product.getId()
                                + " must not be null or blank.");
            }
            BigDecimal productSellingPrice = product.getSellingPrice();
            if (productSellingPrice == null || productSellingPrice.signum() < 0) {
                throw new IllegalArgumentException(
                        "Selling price for product " + product.getId()
                                + " must be non-null and nonnegative.");
            }

            BigDecimal unitPrice =
                    productSellingPrice.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
            requireDatabaseMoneyRange(unitPrice, "Sale item unit price");
            if (unitPrice.compareTo(requestedItem.expectedUnitPrice()) != 0) {
                throw new PriceChangedException(
                        product.getId(), requestedItem.expectedUnitPrice(), unitPrice);
            }

            int quantityBefore = product.getQuantity();
            long quantityAfterLong =
                    (long) quantityBefore - (long) requestedItem.quantity();
            if (quantityBefore < 0
                    || quantityAfterLong < 0
                    || quantityAfterLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Insufficient or invalid stock for product " + product.getId() + ".");
            }

            BigDecimal subtotal = unitPrice
                    .multiply(BigDecimal.valueOf(requestedItem.quantity()))
                    .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
            requireDatabaseMoneyRange(subtotal, "Sale item subtotal");

            saleLines.add(new SaleLine(
                    requestedItem,
                    product,
                    productName,
                    productCode,
                    quantityBefore,
                    (int) quantityAfterLong,
                    unitPrice,
                    subtotal));
        }
        return saleLines;
    }

    private void requireDatabaseMoneyRange(BigDecimal amount, String description) {
        if (amount.precision() > MAX_DECIMAL_PRECISION) {
            throw new IllegalArgumentException(
                    description + " exceeds the database DECIMAL(12, 2) range.");
        }
    }

    public record RequestedSaleItem(
            long productId,
            int quantity,
            BigDecimal expectedUnitPrice) {
    }

    public static final class PriceChangedException extends IllegalArgumentException {
        private final long productId;
        private final BigDecimal expectedUnitPrice;
        private final BigDecimal currentUnitPrice;

        public PriceChangedException(
                long productId,
                BigDecimal expectedUnitPrice,
                BigDecimal currentUnitPrice) {
            super("Selling price changed for product " + productId
                    + " from " + expectedUnitPrice.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING)
                    + " to " + currentUnitPrice.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING)
                    + ".");
            this.productId = productId;
            this.expectedUnitPrice = expectedUnitPrice;
            this.currentUnitPrice = currentUnitPrice;
        }

        public long getProductId() {
            return productId;
        }

        public BigDecimal getExpectedUnitPrice() {
            return expectedUnitPrice;
        }

        public BigDecimal getCurrentUnitPrice() {
            return currentUnitPrice;
        }
    }

    private record SaleLine(
            RequestedSaleItem requestedItem,
            Product product,
            String productName,
            String productCode,
            int quantityBefore,
            int quantityAfter,
            BigDecimal unitPrice,
            BigDecimal subtotal) {
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }
}
