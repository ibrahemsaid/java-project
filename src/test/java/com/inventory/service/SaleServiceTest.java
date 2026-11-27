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

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class SaleServiceTest {
    private static final long USER_ID = 501;

    private SaleServiceTest() {
    }

    public static void main(String[] args) throws SQLException {
        testSuccessfulSaleWithMultipleProducts();
        testConfirmedPriceChangeRollsBackBeforeWrites();
        testMissingProductRollsBack();
        testInsufficientStockRollsBack();
        testInvalidInputIsRejectedBeforeOpeningConnection();
        testNullProductSellingPriceIsRejectedAndRolledBack();
        testNegativeProductSellingPriceIsRejectedAndRolledBack();
        testInvalidProductSnapshotFieldsAreRejectedAndRolledBack();
        testSaleItemFailureRollsBack();
        testStockMovementFailureRollsBack();
        testUnexpectedStockUpdateCountRollsBack();
        testRollbackFailureIsSuppressedOnOriginalFailure();
        testAutoCommitRestorationFailureIsSuppressedOnOriginalFailure();
        testCommittedSaleReturnedWhenAutoCommitRestorationFails();
        testCommittedSaleReturnedWhenConnectionCloseFails();
        System.out.println("SaleService unit tests passed.");
    }

    private static void testSuccessfulSaleWithMultipleProducts() throws SQLException {
        Fixture fixture = new Fixture(
                product(20, 8),
                product(10, 5));

        Sale sale = fixture.service.createSale(
                USER_ID,
                List.of(
                        requested(20, 2),
                        requested(10, 3)));

        require(sale.getId() == 9001, "Sale ID was not returned.");
        require(sale.getUserId() == USER_ID, "Sale has the wrong user ID.");
        require(
                sale.getTotalAmount().compareTo(new BigDecimal("25.00")) == 0,
                "Sale total did not use the stored product selling prices.");
        require(fixture.connection.openCount == 1, "Sale did not use exactly one connection.");
        require(fixture.connection.commitCount == 1, "Successful sale was not committed once.");
        require(fixture.connection.rollbackCount == 0, "Successful sale was rolled back.");
        require(fixture.connection.closeCount == 1, "Sale connection was not closed.");
        require(
                fixture.connection.autoCommitChanges.equals(List.of(false, true)),
                "Auto-commit was not disabled and restored.");
        require(
                fixture.productDao.lockOrder.equals(List.of(10L, 20L)),
                "Products were not locked in ascending ID order.");
        require(
                fixture.productDao.connectionsMatch,
                "Product DAO methods did not receive the transaction connection.");
        require(fixture.saleDao.connectionsMatch, "Sale DAO used a different connection.");
        require(fixture.saleItemDao.connectionsMatch, "SaleItem DAO used a different connection.");
        require(fixture.stockMovementDao.connectionsMatch,
                "StockMovement DAO used a different connection.");

        require(fixture.products.get(10L).getQuantity() == 2, "Product 10 stock is incorrect.");
        require(fixture.products.get(20L).getQuantity() == 6, "Product 20 stock is incorrect.");
        require(fixture.saleItemDao.savedItems.size() == 2, "Two sale items were not saved.");

        SaleItem item10 = findItem(fixture.saleItemDao.savedItems, 10);
        SaleItem item20 = findItem(fixture.saleItemDao.savedItems, 20);
        require(item10.getSaleId() == sale.getId(), "Sale item has the wrong sale reference.");
        require(item10.getQuantity() == 3, "Product 10 sale quantity is incorrect.");
        require("Product 10".equals(item10.getProductName()),
                "Product 10 name snapshot is incorrect.");
        require("CODE-10".equals(item10.getProductCode()),
                "Product 10 code snapshot is incorrect.");
        require(
                item10.getUnitPrice().compareTo(new BigDecimal("5.00")) == 0,
                "Product 10 unit price did not use its stored selling price.");
        require(
                item10.getSubtotal().compareTo(new BigDecimal("15.00")) == 0,
                "Product 10 subtotal is incorrect.");
        require(
                item20.getUnitPrice().compareTo(new BigDecimal("5.00")) == 0,
                "Product 20 unit price did not use its stored selling price.");
        require("Product 20".equals(item20.getProductName()),
                "Product 20 name snapshot is incorrect.");
        require("CODE-20".equals(item20.getProductCode()),
                "Product 20 code snapshot is incorrect.");
        require(
                item20.getSubtotal().compareTo(new BigDecimal("10.00")) == 0,
                "Product 20 subtotal is incorrect.");
        require(fixture.stockMovementDao.savedMovements.size() == 2,
                "A SALE movement was not saved for each product.");

        StockMovement movement10 = findMovement(fixture.stockMovementDao.savedMovements, 10);
        StockMovement movement20 = findMovement(fixture.stockMovementDao.savedMovements, 20);
        requireMovement(movement10, sale.getId(), 5, 2, -3);
        requireMovement(movement20, sale.getId(), 8, 6, -2);
        System.out.println("PASS: successful multi-product sale and movement records.");
    }

    private static void testInsufficientStockRollsBack() throws SQLException {
        Fixture fixture = new Fixture(product(10, 1));

        expectIllegalArgument(
                () -> fixture.service.createSale(USER_ID, List.of(requested(10, 2))),
                "reject insufficient stock");

        require(fixture.connection.openCount == 1, "Insufficient-stock check opened wrong connections.");
        require(fixture.connection.rollbackCount == 1, "Insufficient stock did not roll back.");
        require(fixture.connection.commitCount == 0, "Insufficient stock was committed.");
        require(fixture.saleDao.savedSales.isEmpty(), "Sale was saved despite insufficient stock.");
        require(fixture.products.get(10L).getQuantity() == 1, "Insufficient stock was changed.");
        System.out.println("PASS: insufficient stock rejected and transaction rolled back.");
    }

    private static void testConfirmedPriceChangeRollsBackBeforeWrites() throws SQLException {
        Product unchangedProduct = product(10, 5);
        Product changedProduct = product(20, 8);
        changedProduct.setSellingPrice(new BigDecimal("6.25"));
        Fixture fixture = new Fixture(unchangedProduct, changedProduct);

        try {
            fixture.service.createSale(
                    USER_ID,
                    List.of(requested(10, 1), requested(20, 1)));
            throw new AssertionError("A changed confirmed selling price was accepted.");
        } catch (SaleService.PriceChangedException exception) {
            require(exception.getProductId() == 20,
                    "Price-change rejection identified the wrong product.");
            require(exception.getExpectedUnitPrice().compareTo(new BigDecimal("5.00")) == 0,
                    "Price-change rejection did not retain the confirmed price.");
            require(exception.getCurrentUnitPrice().compareTo(new BigDecimal("6.25")) == 0,
                    "Price-change rejection did not report the current price.");
        }

        require(fixture.connection.rollbackCount == 1,
                "Changed confirmed price did not roll back.");
        require(fixture.connection.commitCount == 0,
                "Sale with a changed confirmed price was committed.");
        require(fixture.saleDao.savedSales.isEmpty(),
                "Sale was saved despite a changed confirmed price.");
        require(fixture.saleItemDao.savedItems.isEmpty(),
                "Sale items were saved despite a changed confirmed price.");
        require(fixture.stockMovementDao.savedMovements.isEmpty(),
                "Stock movements were saved despite a changed confirmed price.");
        require(unchangedProduct.getQuantity() == 5 && changedProduct.getQuantity() == 8,
                "Product stock changed despite a changed confirmed price.");
        require(fixture.productDao.lockOrder.equals(List.of(10L, 20L)),
                "Price comparison changed product lock ordering.");
        System.out.println("PASS: changed confirmed price rejected before any sale writes.");
    }

    private static void testMissingProductRollsBack() throws SQLException {
        Fixture fixture = new Fixture(product(10, 5));

        expectIllegalArgument(
                () -> fixture.service.createSale(
                        USER_ID, List.of(requested(99, 1))),
                "reject a nonexistent product");

        require(fixture.connection.rollbackCount == 1,
                "Missing product did not roll back the transaction.");
        require(fixture.connection.commitCount == 0,
                "Sale with a missing product was committed.");
        require(fixture.saleDao.savedSales.isEmpty(),
                "Sale was saved despite a missing product.");
        System.out.println("PASS: missing product rejected before sale persistence.");
    }

    private static void testInvalidInputIsRejectedBeforeOpeningConnection() throws SQLException {
        Fixture fixture = new Fixture(product(10, 5));
        List<SaleService.RequestedSaleItem> valid = List.of(requested(10, 1));

        expectIllegalArgument(
                () -> fixture.service.createSale(0, valid),
                "reject invalid authenticated user ID");
        expectIllegalArgument(
                () -> fixture.service.createSale(USER_ID, List.of()),
                "reject an empty sale");
        expectIllegalArgument(
                () -> fixture.service.createSale(
                        USER_ID, List.of(requested(0, 1))),
                "reject an invalid product ID");
        expectIllegalArgument(
                () -> fixture.service.createSale(
                        USER_ID, List.of(requested(10, 0))),
                "reject a nonpositive quantity");
        expectIllegalArgument(
                () -> fixture.service.createSale(
                        USER_ID,
                        List.of(new SaleService.RequestedSaleItem(10, 1, null))),
                "reject a missing confirmed price");
        expectIllegalArgument(
                () -> fixture.service.createSale(
                        USER_ID,
                        List.of(requested(10, 1), requested(10, 2))),
                "reject duplicate product IDs");
        expectIllegalArgument(
                () -> fixture.service.createSale(USER_ID, null),
                "reject a null item collection");

        require(fixture.connection.openCount == 0,
                "Invalid input opened a database connection.");
        System.out.println("PASS: invalid sale inputs were rejected before connection creation.");
    }

    private static void testNullProductSellingPriceIsRejectedAndRolledBack()
            throws SQLException {
        Product product = product(10, 5);
        product.setSellingPrice(null);
        assertInvalidProductSellingPriceIsRejectedAndRolledBack(product);
        System.out.println("PASS: null product selling price rejected and rolled back.");
    }

    private static void testNegativeProductSellingPriceIsRejectedAndRolledBack()
            throws SQLException {
        Product product = product(10, 5);
        product.setSellingPrice(new BigDecimal("-0.01"));
        assertInvalidProductSellingPriceIsRejectedAndRolledBack(product);
        System.out.println("PASS: negative product selling price rejected and rolled back.");
    }

    private static void testInvalidProductSnapshotFieldsAreRejectedAndRolledBack()
            throws SQLException {
        Product nullName = product(10, 5);
        nullName.setName(null);
        assertInvalidProductSnapshotFieldIsRejectedAndRolledBack(nullName, "Product name");

        Product blankName = product(10, 5);
        blankName.setName(" \t");
        assertInvalidProductSnapshotFieldIsRejectedAndRolledBack(blankName, "Product name");

        Product nullCode = product(10, 5);
        nullCode.setCode(null);
        assertInvalidProductSnapshotFieldIsRejectedAndRolledBack(nullCode, "Product code");

        Product blankCode = product(10, 5);
        blankCode.setCode(" \t");
        assertInvalidProductSnapshotFieldIsRejectedAndRolledBack(blankCode, "Product code");
        System.out.println("PASS: null and blank product snapshot fields rejected and rolled back.");
    }

    private static void assertInvalidProductSnapshotFieldIsRejectedAndRolledBack(
            Product product,
            String expectedMessage) throws SQLException {
        Fixture fixture = new Fixture(product);
        expectIllegalArgumentContaining(
                () -> fixture.service.createSale(USER_ID, List.of(requested(10, 1))),
                expectedMessage);

        require(fixture.connection.rollbackCount == 1,
                "Invalid product snapshot field did not roll back.");
        require(fixture.connection.commitCount == 0,
                "Sale with an invalid product snapshot field was committed.");
        require(fixture.saleDao.savedSales.isEmpty(),
                "Sale was saved with an invalid product snapshot field.");
        require(fixture.saleItemDao.savedItems.isEmpty(),
                "Sale item was saved with an invalid product snapshot field.");
        require(product.getQuantity() == 5,
                "Stock changed after rejecting an invalid product snapshot field.");
    }

    private static void assertInvalidProductSellingPriceIsRejectedAndRolledBack(
            Product product) throws SQLException {
        Fixture fixture = new Fixture(product);
        expectIllegalArgument(
                () -> fixture.service.createSale(USER_ID, List.of(requested(10, 1))),
                "reject an invalid product selling price");

        require(fixture.connection.rollbackCount == 1,
                "Invalid product selling price did not roll back.");
        require(fixture.connection.commitCount == 0,
                "Sale with an invalid product selling price was committed.");
        require(fixture.saleDao.savedSales.isEmpty(),
                "Sale was saved with an invalid product selling price.");
        require(fixture.saleItemDao.savedItems.isEmpty(),
                "Sale item was saved with an invalid product selling price.");
        require(product.getQuantity() == 5,
                "Stock changed after rejecting an invalid product selling price.");
    }

    private static void testSaleItemFailureRollsBack() throws SQLException {
        Fixture fixture = new Fixture(product(10, 5), product(20, 5));
        fixture.saleItemDao.failOnSaveNumber = 2;

        expectSqlFailure(
                () -> fixture.service.createSale(
                        USER_ID,
                        List.of(requested(10, 1), requested(20, 1))),
                "sale item insert failed");

        require(fixture.connection.rollbackCount == 1, "Sale item failure did not roll back.");
        require(fixture.connection.commitCount == 0, "Sale item failure was committed.");
        require(fixture.saleDao.savedSales.isEmpty(), "Sale row survived sale item rollback.");
        require(fixture.saleItemDao.savedItems.isEmpty(), "Sale items survived rollback.");
        require(fixture.stockMovementDao.savedMovements.isEmpty(),
                "Stock movements survived sale item rollback.");
        require(fixture.products.get(10L).getQuantity() == 5,
                "Earlier stock update survived sale item rollback.");
        require(fixture.products.get(20L).getQuantity() == 5,
                "Later stock was unexpectedly changed.");
        System.out.println("PASS: sale item failure rolled back the sale and prior writes.");
    }

    private static void testStockMovementFailureRollsBack() throws SQLException {
        Fixture fixture = new Fixture(product(10, 5));
        fixture.stockMovementDao.failure = new SQLException("movement insert failed");

        expectSqlFailure(
                () -> fixture.service.createSale(
                        USER_ID, List.of(requested(10, 2))),
                "movement insert failed");

        require(fixture.connection.rollbackCount == 1, "Movement failure did not roll back.");
        require(fixture.connection.commitCount == 0, "Movement failure was committed.");
        require(fixture.saleDao.savedSales.isEmpty(), "Sale row survived movement rollback.");
        require(fixture.saleItemDao.savedItems.isEmpty(), "Sale item survived movement rollback.");
        require(fixture.products.get(10L).getQuantity() == 5,
                "Stock update survived movement rollback.");
        System.out.println("PASS: movement failure rolled back sale, item, and stock.");
    }

    private static void testUnexpectedStockUpdateCountRollsBack() throws SQLException {
        Fixture fixture = new Fixture(product(10, 5));
        fixture.productDao.forceZeroUpdateCount = true;

        try {
            fixture.service.createSale(USER_ID, List.of(requested(10, 1)));
            throw new AssertionError("A stock update affecting zero rows was accepted.");
        } catch (IllegalStateException exception) {
            require(
                    exception.getMessage().contains("could not be updated"),
                    "Unexpected stock update failure was not reported.");
        }

        require(fixture.connection.rollbackCount == 1,
                "Unexpected stock update count did not roll back.");
        require(fixture.connection.commitCount == 0,
                "Sale with an unexpected stock update count was committed.");
        require(fixture.saleDao.savedSales.isEmpty(),
                "Sale survived an unexpected stock update count.");
        require(fixture.saleItemDao.savedItems.isEmpty(),
                "Sale item survived an unexpected stock update count.");
        require(fixture.products.get(10L).getQuantity() == 5,
                "Stock changed after an unexpected stock update count.");
        System.out.println("PASS: unexpected stock update count rolled back the sale.");
    }

    private static void testRollbackFailureIsSuppressedOnOriginalFailure() throws SQLException {
        Fixture fixture = new Fixture(product(10, 5));
        fixture.saleItemDao.failure = new SQLException("sale item insert failed");
        fixture.connection.rollbackFailure = new SQLException("rollback failed");

        try {
            fixture.service.createSale(USER_ID, List.of(requested(10, 1)));
            throw new AssertionError("Expected sale item insert failure.");
        } catch (SQLException exception) {
            require(
                    "sale item insert failed".equals(exception.getMessage()),
                    "Rollback failure replaced the original failure.");
            require(exception.getSuppressed().length == 1,
                    "Rollback failure was not preserved as a suppressed exception.");
            require(
                    "rollback failed".equals(exception.getSuppressed()[0].getMessage()),
                    "Unexpected rollback failure was suppressed.");
            require(fixture.connection.autoCommitChanges.equals(List.of(false)),
                    "Auto-commit restoration was attempted after rollback failed.");
        }
        System.out.println("PASS: rollback failure was suppressed without masking the original.");
    }

    private static void testAutoCommitRestorationFailureIsSuppressedOnOriginalFailure()
            throws SQLException {
        Fixture fixture = new Fixture(product(10, 5));
        fixture.saleItemDao.failure = new SQLException("sale item insert failed");
        fixture.connection.autoCommitRestoreFailure =
                new SQLException("auto-commit restoration failed");

        try {
            fixture.service.createSale(USER_ID, List.of(requested(10, 1)));
            throw new AssertionError("Expected sale item insert failure.");
        } catch (SQLException exception) {
            require(
                    "sale item insert failed".equals(exception.getMessage()),
                    "Auto-commit restoration failure replaced the original failure.");
            require(exception.getSuppressed().length == 1,
                    "Auto-commit restoration failure was not suppressed.");
            require(
                    "auto-commit restoration failed".equals(
                            exception.getSuppressed()[0].getMessage()),
                    "Unexpected auto-commit restoration failure was suppressed.");
        }
        System.out.println(
                "PASS: auto-commit restoration failure did not mask the original failure.");
    }

    private static void testCommittedSaleReturnedWhenAutoCommitRestorationFails()
            throws SQLException {
        Fixture fixture = new Fixture(product(10, 5));
        fixture.connection.autoCommitRestoreFailure =
                new SQLException("auto-commit restoration failed");
        Logger logger = Logger.getLogger(SaleService.class.getName());
        CapturingLogHandler logHandler = new CapturingLogHandler();
        logger.addHandler(logHandler);

        Sale sale;
        try {
            sale = fixture.service.createSale(USER_ID, List.of(requested(10, 1)));
        } finally {
            logger.removeHandler(logHandler);
        }

        require(sale.getId() == 9001, "Committed sale was not returned after restoration failed.");
        require(fixture.connection.commitCount == 1, "Sale was not committed.");
        require(fixture.connection.rollbackCount == 0,
                "Rollback was attempted after the sale committed.");
        require(fixture.connection.closeCount == 1, "Connection was not closed.");
        requireCleanupFailureLogged(
                logHandler, sale.getId(), "auto-commit restoration failed");
        System.out.println("PASS: committed sale returned and restoration failure was logged.");
    }

    private static void testCommittedSaleReturnedWhenConnectionCloseFails()
            throws SQLException {
        Fixture fixture = new Fixture(product(10, 5));
        fixture.connection.closeFailure = new SQLException("connection close failed");
        Logger logger = Logger.getLogger(SaleService.class.getName());
        CapturingLogHandler logHandler = new CapturingLogHandler();
        logger.addHandler(logHandler);

        Sale sale;
        try {
            sale = fixture.service.createSale(USER_ID, List.of(requested(10, 1)));
        } finally {
            logger.removeHandler(logHandler);
        }

        require(sale.getId() == 9001, "Committed sale was not returned after close failed.");
        require(fixture.connection.commitCount == 1, "Sale was not committed.");
        require(fixture.connection.rollbackCount == 0,
                "Rollback was attempted after the sale committed.");
        require(fixture.connection.closeCount == 1, "Connection close was not attempted.");
        requireCleanupFailureLogged(logHandler, sale.getId(), "connection close failed");
        System.out.println("PASS: committed sale returned and close failure was logged.");
    }

    private static void requireCleanupFailureLogged(
            CapturingLogHandler logHandler,
            long saleId,
            String expectedFailure) {
        require(logHandler.records.size() == 1,
                "Expected one logged post-commit cleanup failure.");
        LogRecord record = logHandler.records.get(0);
        require(record.getLevel().equals(Level.SEVERE),
                "Post-commit cleanup failure was not logged at SEVERE.");
        require(record.getMessage().contains("Sale " + saleId),
                "Cleanup log did not identify the committed sale.");
        require(record.getThrown() instanceof SQLException,
                "Cleanup log did not include the thrown exception.");
        require(expectedFailure.equals(record.getThrown().getMessage()),
                "Cleanup log contained a different exception.");
    }

    private static void requireMovement(
            StockMovement movement,
            long saleId,
            int quantityBefore,
            int quantityAfter,
            int quantityChange) {
        require(movement.getMovementType() == StockMovementType.SALE,
                "Movement type was not SALE.");
        require(movement.getUserId() == USER_ID, "Movement has the wrong user ID.");
        require(movement.getReferenceSaleId() == saleId, "Movement has the wrong sale reference.");
        require(
                ("Sale " + saleId).equals(movement.getReason()),
                "Movement has the wrong reason.");
        require(movement.getQuantityBefore() == quantityBefore,
                "Movement has the wrong before quantity.");
        require(movement.getQuantityAfter() == quantityAfter,
                "Movement has the wrong after quantity.");
        require(movement.getQuantityChange() == quantityChange,
                "Movement has the wrong quantity change.");
    }

    private static SaleItem findItem(List<SaleItem> items, long productId) {
        return items.stream()
                .filter(item -> item.getProductId() == productId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sale item for product " + productId + " missing."));
    }

    private static StockMovement findMovement(List<StockMovement> movements, long productId) {
        return movements.stream()
                .filter(movement -> movement.getProductId() == productId)
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Movement for product " + productId + " missing."));
    }

    private static Product product(long id, int quantity) {
        return new Product(
                id,
                "Product " + id,
                "CODE-" + id,
                1,
                new BigDecimal("1.00"),
                new BigDecimal("5.00"),
                quantity,
                0);
    }

    private static SaleService.RequestedSaleItem requested(long productId, int quantity) {
        return new SaleService.RequestedSaleItem(
                productId, quantity, new BigDecimal("5.00"));
    }

    private static void expectIllegalArgument(
            CheckedOperation operation,
            String checkName) throws SQLException {
        try {
            operation.run();
        } catch (IllegalArgumentException exception) {
            System.out.println("PASS: " + checkName + ".");
            return;
        }
        throw new AssertionError(checkName + " was not rejected.");
    }

    private static void expectIllegalArgumentContaining(
            CheckedOperation operation,
            String expectedMessage) throws SQLException {
        try {
            operation.run();
        } catch (IllegalArgumentException exception) {
            require(exception.getMessage().contains(expectedMessage),
                    "Validation exception did not identify " + expectedMessage + ".");
            return;
        }
        throw new AssertionError("Expected validation failure for " + expectedMessage + ".");
    }

    private static void expectSqlFailure(
            CheckedOperation operation,
            String expectedMessage) throws SQLException {
        try {
            operation.run();
        } catch (SQLException exception) {
            require(expectedMessage.equals(exception.getMessage()),
                    "Unexpected SQL failure message.");
            return;
        }
        throw new AssertionError("Expected SQL failure: " + expectedMessage);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface CheckedOperation {
        void run() throws SQLException;
    }

    private static final class Fixture {
        private final TestConnection connection = new TestConnection();
        private final Map<Long, Product> products = new HashMap<>();
        private final FakeProductDao productDao = new FakeProductDao(products, connection);
        private final FakeSaleDao saleDao = new FakeSaleDao(connection);
        private final FakeSaleItemDao saleItemDao = new FakeSaleItemDao(connection);
        private final FakeStockMovementDao stockMovementDao =
                new FakeStockMovementDao(connection);
        private final SaleService service = new SaleService(
                productDao,
                saleDao,
                saleItemDao,
                stockMovementDao,
                connection::open);

        private Fixture(Product... initialProducts) {
            for (Product product : initialProducts) {
                products.put(product.getId(), product);
            }
            productDao.captureInitialQuantities();
        }
    }

    private static final class FakeProductDao extends ProductDao {
        private final Map<Long, Product> products;
        private final TestConnection connection;
        private final List<Long> lockOrder = new ArrayList<>();
        private boolean connectionsMatch = true;
        private boolean forceZeroUpdateCount;
        private Map<Long, Integer> initialQuantities;

        private FakeProductDao(Map<Long, Product> products, TestConnection connection) {
            this.products = products;
            this.connection = connection;
        }

        private void captureInitialQuantities() {
            initialQuantities = new HashMap<>();
            products.forEach((id, product) -> initialQuantities.put(id, product.getQuantity()));
            connection.addRollbackAction(() ->
                    initialQuantities.forEach((id, quantity) ->
                            products.get(id).setQuantity(quantity)));
        }

        @Override
        public Optional<Product> findByIdForUpdate(long id, Connection suppliedConnection) {
            connectionsMatch &= suppliedConnection == connection.connection;
            lockOrder.add(id);
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public int changeStockQuantity(
                long productId,
                int quantityChange,
                Connection suppliedConnection) {
            connectionsMatch &= suppliedConnection == connection.connection;
            if (forceZeroUpdateCount) {
                return 0;
            }
            Product product = products.get(productId);
            if (product == null || product.getQuantity() + (long) quantityChange < 0) {
                return 0;
            }
            product.setQuantity(product.getQuantity() + quantityChange);
            return 1;
        }
    }

    private static final class FakeSaleDao extends SaleDao {
        private final TestConnection connection;
        private final List<Sale> savedSales = new ArrayList<>();
        private boolean connectionsMatch = true;

        private FakeSaleDao(TestConnection connection) {
            this.connection = connection;
            connection.addRollbackAction(savedSales::clear);
        }

        @Override
        public Sale save(Sale sale, Connection suppliedConnection) {
            connectionsMatch &= suppliedConnection == connection.connection;
            sale.setId(9001);
            savedSales.add(sale);
            return sale;
        }
    }

    private static final class FakeSaleItemDao extends SaleItemDao {
        private final TestConnection connection;
        private final List<SaleItem> savedItems = new ArrayList<>();
        private boolean connectionsMatch = true;
        private int failOnSaveNumber;
        private int saveCount;
        private SQLException failure;

        private FakeSaleItemDao(TestConnection connection) {
            this.connection = connection;
            connection.addRollbackAction(savedItems::clear);
        }

        @Override
        public SaleItem save(SaleItem item, Connection suppliedConnection) throws SQLException {
            connectionsMatch &= suppliedConnection == connection.connection;
            saveCount++;
            if (failure != null) {
                throw failure;
            }
            if (failOnSaveNumber == saveCount) {
                throw new SQLException("sale item insert failed");
            }
            item.setId(1000 + saveCount);
            savedItems.add(item);
            return item;
        }
    }

    private static final class FakeStockMovementDao extends StockMovementDao {
        private final TestConnection connection;
        private final List<StockMovement> savedMovements = new ArrayList<>();
        private boolean connectionsMatch = true;
        private SQLException failure;

        private FakeStockMovementDao(TestConnection connection) {
            this.connection = connection;
            connection.addRollbackAction(savedMovements::clear);
        }

        @Override
        public StockMovement save(StockMovement movement, Connection suppliedConnection)
                throws SQLException {
            connectionsMatch &= suppliedConnection == connection.connection;
            if (failure != null) {
                throw failure;
            }
            movement.setId(savedMovements.size() + 1);
            savedMovements.add(movement);
            return movement;
        }
    }

    private static final class TestConnection {
        private final List<Boolean> autoCommitChanges = new ArrayList<>();
        private final List<Runnable> rollbackActions = new ArrayList<>();
        private final Connection connection;
        private boolean autoCommit = true;
        private int openCount;
        private int commitCount;
        private int rollbackCount;
        private int closeCount;
        private SQLException rollbackFailure;
        private SQLException autoCommitRestoreFailure;
        private SQLException closeFailure;

        private TestConnection() {
            connection = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> {
                            boolean requestedAutoCommit = (Boolean) args[0];
                            if (requestedAutoCommit && autoCommitRestoreFailure != null) {
                                throw autoCommitRestoreFailure;
                            }
                            autoCommit = requestedAutoCommit;
                            autoCommitChanges.add(autoCommit);
                            yield null;
                        }
                        case "commit" -> {
                            commitCount++;
                            yield null;
                        }
                        case "rollback" -> {
                            rollbackCount++;
                            if (rollbackFailure != null) {
                                throw rollbackFailure;
                            }
                            rollbackActions.forEach(Runnable::run);
                            yield null;
                        }
                        case "close" -> {
                            closeCount++;
                            if (closeFailure != null) {
                                throw closeFailure;
                            }
                            yield null;
                        }
                        case "isClosed" -> closeCount > 0;
                        case "toString" -> "TestConnection";
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private Connection open() {
            openCount++;
            return connection;
        }

        private void addRollbackAction(Runnable action) {
            rollbackActions.add(action);
        }
    }

    private static final class CapturingLogHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        private CapturingLogHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return null;
    }
}
