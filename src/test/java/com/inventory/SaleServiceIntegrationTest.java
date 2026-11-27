package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.dao.SaleDao;
import com.inventory.dao.SaleItemDao;
import com.inventory.dao.StockMovementDao;
import com.inventory.model.Category;
import com.inventory.model.Product;
import com.inventory.model.Sale;
import com.inventory.model.SaleItem;
import com.inventory.model.StockMovement;
import com.inventory.model.StockMovementType;
import com.inventory.service.SaleService;
import com.inventory.util.DatabaseConnection;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class SaleServiceIntegrationTest {
    private static final String EXPECTED_DATABASE =
            System.getProperty("sale.integration.database");
    private static final String ROLLBACK_FAILURE_MESSAGE =
            "Intentional failure after inserting the second stock movement.";

    private SaleServiceIntegrationTest() {
    }

    public static void main(String[] args) throws SQLException {
        requireExplicitTestDatabase();
        requireConfiguredDatabase();
        long userId = findExistingUserId();
        long categoryId = findExistingCategoryId();

        testSuccessfulSale(userId, categoryId);
        testInsufficientStockDoesNotWrite(userId, categoryId);
        testLaterMovementFailureRollsBack(userId, categoryId);

        System.out.println("SaleService MySQL integration tests passed.");
    }

    private static void testSuccessfulSale(long userId, long categoryId) throws SQLException {
        TestRecords records = new TestRecords();
        withCleanup(records, () -> {
            List<Product> products = createFixtureProducts(records, categoryId, 10, 8);
            Product first = products.get(0);
            Product second = products.get(1);

            SaleDao saleDao = new TrackingSaleDao(records);
            SaleItemDao saleItemDao = new TrackingSaleItemDao(records);
            StockMovementDao stockMovementDao = new TrackingStockMovementDao(records, 0);
            ProductDao productDao = new ProductDao();
            SaleService service =
                    new SaleService(productDao, saleDao, saleItemDao, stockMovementDao);

            Sale sale = service.createSale(
                    userId,
                    List.of(
                            requested(first.getId(), 3),
                            requested(second.getId(), 2)));

            require(sale.getId() > 0, "Sale did not receive a generated ID.");
            require(records.saleIds.contains(sale.getId()), "Sale ID was not tracked.");
            require(sale.getUserId() == userId, "Returned sale has the wrong user ID.");
            requireAmount(sale.getTotalAmount(), "25.00", "Returned sale total");

            Sale persistedSale = saleDao.findById(sale.getId())
                    .orElseThrow(() -> new AssertionError("Persisted sale was not found."));
            require(persistedSale.getUserId() == userId, "Persisted sale has the wrong user ID.");
            requireAmount(persistedSale.getTotalAmount(), "25.00", "Persisted sale total");

            List<SaleItem> items = saleItemDao.findBySaleId(sale.getId());
            require(items.size() == 2, "Expected two persisted sale items.");
            requireSaleItem(
                    findSaleItem(items, first.getId()), sale.getId(), first.getId(),
                    3, first.getName(), first.getCode(), "5.00", "15.00");
            requireSaleItem(
                    findSaleItem(items, second.getId()), sale.getId(), second.getId(),
                    2, second.getName(), second.getCode(), "5.00", "10.00");
            requireAmount(
                    items.stream()
                            .map(SaleItem::getSubtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add),
                    "25.00",
                    "Sum of persisted sale item subtotals");

            Product updatedFirst = productDao.findById(first.getId())
                    .orElseThrow(() -> new AssertionError("First fixture product was not found."));
            Product updatedSecond = productDao.findById(second.getId())
                    .orElseThrow(() -> new AssertionError("Second fixture product was not found."));
            require(updatedFirst.getQuantity() == 7, "First product stock was not decremented.");
            require(updatedSecond.getQuantity() == 6, "Second product stock was not decremented.");

            updatedFirst.setName(first.getName() + " updated");
            updatedFirst.setCode(first.getCode() + "-UPDATED");
            updatedFirst.setSellingPrice(new BigDecimal("6.75"));
            require(productDao.update(updatedFirst),
                    "First product details were not updated for the snapshot regression test.");
            Product renamedFirst = productDao.findById(first.getId())
                    .orElseThrow(() -> new AssertionError("Updated first product was not found."));
            require(renamedFirst.getName().equals(updatedFirst.getName()),
                    "First product name update was not persisted.");
            require(renamedFirst.getCode().equals(updatedFirst.getCode()),
                    "First product code update was not persisted.");
            requireAmount(renamedFirst.getSellingPrice(), "6.75",
                    "Updated first product selling price");

            List<SaleItem> reloadedItems = saleItemDao.findBySaleId(sale.getId());
            require(reloadedItems.size() == 2,
                    "Expected two sale items after updating the product.");
            requireSaleItem(
                    findSaleItem(reloadedItems, first.getId()), sale.getId(), first.getId(),
                    3, first.getName(), first.getCode(), "5.00", "15.00");
            requireSaleItem(
                    findSaleItem(reloadedItems, second.getId()), sale.getId(), second.getId(),
                    2, second.getName(), second.getCode(), "5.00", "10.00");

            List<StockMovement> movements = stockMovementDao.findBySaleId(sale.getId());
            require(movements.size() == 2, "Expected two persisted SALE movements.");
            requireMovement(
                    findMovement(movements, first.getId()), sale.getId(), userId,
                    first.getId(), 10, 7, -3);
            requireMovement(
                    findMovement(movements, second.getId()), sale.getId(), userId,
                    second.getId(), 8, 6, -2);

            System.out.println("PASS: persisted sale, items, stock decrements, and movements.");
        });
    }

    private static void testInsufficientStockDoesNotWrite(long userId, long categoryId)
            throws SQLException {
        TestRecords records = new TestRecords();
        withCleanup(records, () -> {
            List<Product> products = createFixtureProducts(records, categoryId, 5, 1);
            Product first = products.get(0);
            Product second = products.get(1);

            TrackingSaleDao saleDao = new TrackingSaleDao(records);
            SaleItemDao saleItemDao = new TrackingSaleItemDao(records);
            StockMovementDao stockMovementDao = new TrackingStockMovementDao(records, 0);
            ProductDao productDao = new ProductDao();
            SaleService service =
                    new SaleService(productDao, saleDao, saleItemDao, stockMovementDao);

            try {
                service.createSale(
                        userId,
                        List.of(
                                requested(first.getId(), 2),
                                requested(second.getId(), 2)));
                throw new AssertionError("Insufficient stock was accepted.");
            } catch (IllegalArgumentException expected) {
                require(
                        expected.getMessage().contains("Insufficient"),
                        "Unexpected rejection for the insufficient-stock sale.");
            }

            require(saleDao.saveCalls == 0, "A sale was saved despite insufficient stock.");
            require(records.saleIds.isEmpty(), "A sale ID was tracked despite insufficient stock.");
            require(
                    productDao.findById(first.getId()).orElseThrow().getQuantity() == 5,
                    "First product stock changed after insufficient-stock rejection.");
            require(
                    productDao.findById(second.getId()).orElseThrow().getQuantity() == 1,
                    "Second product stock changed after insufficient-stock rejection.");
            require(
                    stockMovementDao.findByProductId(first.getId()).isEmpty()
                            && stockMovementDao.findByProductId(second.getId()).isEmpty(),
                    "A stock movement was created despite insufficient stock.");

            System.out.println("PASS: insufficient stock caused no sale, stock, or movement writes.");
        });
    }

    private static void testLaterMovementFailureRollsBack(long userId, long categoryId)
            throws SQLException {
        TestRecords records = new TestRecords();
        withCleanup(records, () -> {
            List<Product> products = createFixtureProducts(records, categoryId, 10, 8);
            Product first = products.get(0);
            Product second = products.get(1);

            TrackingSaleDao saleDao = new TrackingSaleDao(records);
            SaleItemDao saleItemDao = new TrackingSaleItemDao(records);
            TrackingStockMovementDao stockMovementDao =
                    new TrackingStockMovementDao(records, 2);
            ProductDao productDao = new ProductDao();
            SaleService service =
                    new SaleService(productDao, saleDao, saleItemDao, stockMovementDao);

            try {
                service.createSale(
                        userId,
                        List.of(
                                requested(first.getId(), 3),
                                requested(second.getId(), 2)));
                throw new AssertionError("The injected later movement failure was not propagated.");
            } catch (SQLException expected) {
                require(
                        ROLLBACK_FAILURE_MESSAGE.equals(expected.getMessage()),
                        "The injected movement failure was not preserved.");
            }

            require(saleDao.saveCalls == 1, "The rollback case did not save exactly one sale.");
            require(records.saleIds.size() == 1, "The generated sale ID was not tracked.");
            require(records.saleItemIds.size() == 2, "Both inserted sale item IDs were not tracked.");
            require(
                    records.stockMovementIds.size() == 2,
                    "Both inserted movement IDs were not tracked before the injected failure.");

            long saleId = records.saleIds.iterator().next();
            require(saleDao.findById(saleId).isEmpty(), "Sale remained after rollback.");
            require(saleItemDao.findBySaleId(saleId).isEmpty(),
                    "Sale items remained after rollback.");
            require(stockMovementDao.findBySaleId(saleId).isEmpty(),
                    "Sale movements remained after rollback.");
            require(
                    !recordExists("sale_items", records.saleItemIds),
                    "A tracked sale item remained after rollback.");
            require(
                    !recordExists("stock_movements", records.stockMovementIds),
                    "A tracked stock movement remained after rollback.");

            require(
                    productDao.findById(first.getId()).orElseThrow().getQuantity() == 10,
                    "First product stock was not restored by rollback.");
            require(
                    productDao.findById(second.getId()).orElseThrow().getQuantity() == 8,
                    "Second product stock was not restored by rollback.");

            System.out.println("PASS: failure after a later movement insert rolled back the sale.");
        });
    }

    private static List<Product> createFixtureProducts(
            TestRecords records,
            long categoryId,
            int firstQuantity,
            int secondQuantity) throws SQLException {
        ProductDao productDao = new ProductDao();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        List<Product> products = List.of(
                new Product(
                        "Sale integration product A " + suffix,
                        "SALE-IT-" + suffix + "-A",
                        categoryId,
                        new BigDecimal("1.00"),
                        new BigDecimal("5.00"),
                        firstQuantity,
                        0),
                new Product(
                        "Sale integration product B " + suffix,
                        "SALE-IT-" + suffix + "-B",
                        categoryId,
                        new BigDecimal("1.00"),
                        new BigDecimal("5.00"),
                        secondQuantity,
                        0));

        for (Product product : products) {
            if (productDao.findByCode(product.getCode()).isPresent()) {
                throw new SQLException("A generated fixture product code already exists.");
            }
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Product product : products) {
                    productDao.save(product, connection);
                    records.recordProductId(product.getId());
                    if (product.getId() <= 0) {
                        throw new SQLException("Fixture product insert returned an invalid ID.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException | Error exception) {
                try {
                    connection.rollback();
                } catch (SQLException | RuntimeException | Error rollbackException) {
                    if (rollbackException != exception) {
                        exception.addSuppressed(rollbackException);
                    }
                }
                throw exception;
            }
        }

        return products;
    }

    private static void requireConfiguredDatabase() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            if (!resultSet.next()
                    || !EXPECTED_DATABASE.equals(resultSet.getString(1))) {
                throw new SQLException(
                        "Integration test must connect to the configured database '"
                                + EXPECTED_DATABASE + "'.");
            }
        }
    }

    private static void requireExplicitTestDatabase() throws SQLException {
        if (EXPECTED_DATABASE == null
                || !EXPECTED_DATABASE.matches("(?i)[a-z0-9][a-z0-9_]*_test")) {
            throw new SQLException(
                    "Set -Dsale.integration.database to an isolated database name ending in "
                            + "'_test'.");
        }

        Path configPath = Path.of("config", "db.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new SQLException(
                    "Could not read config/db.properties to verify the integration-test target.",
                    exception);
        }

        String configuredDatabase = databaseNameFromUrl(properties.getProperty("db.url"));
        if (!EXPECTED_DATABASE.equals(configuredDatabase)) {
            throw new SQLException(
                    "The database in config/db.properties must exactly match the explicitly "
                            + "selected '_test' database.");
        }
    }

    private static String databaseNameFromUrl(String jdbcUrl) throws SQLException {
        String prefix = "jdbc:mysql://";
        if (jdbcUrl == null || !jdbcUrl.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new SQLException(
                    "config/db.properties must contain a MySQL JDBC URL with an explicit "
                            + "database name.");
        }

        int pathStart = jdbcUrl.indexOf('/', prefix.length());
        if (pathStart < 0 || pathStart == jdbcUrl.length() - 1) {
            throw new SQLException(
                    "config/db.properties must contain a MySQL JDBC URL with an explicit "
                            + "database name.");
        }

        int pathEnd = jdbcUrl.length();
        for (char terminator : new char[] {'?', ';', '#'}) {
            int terminatorIndex = jdbcUrl.indexOf(terminator, pathStart + 1);
            if (terminatorIndex >= 0) {
                pathEnd = Math.min(pathEnd, terminatorIndex);
            }
        }

        String databaseName = jdbcUrl.substring(pathStart + 1, pathEnd);
        if (!databaseName.matches("(?i)[a-z0-9][a-z0-9_]*_test")) {
            throw new SQLException(
                    "config/db.properties must target a database name ending in '_test'.");
        }
        return databaseName;
    }

    private static long findExistingUserId() throws SQLException {
        String sql = "SELECT id FROM users ORDER BY id LIMIT 1";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getLong("id");
            }
        }

        throw new SQLException("No existing user is available for the integration test.");
    }

    private static long findExistingCategoryId() throws SQLException {
        List<Category> categories = new CategoryDao().findAll();
        if (categories.isEmpty()) {
            throw new SQLException("No existing category is available for fixture products.");
        }
        return categories.get(0).getId();
    }

    private static SaleService.RequestedSaleItem requested(long productId, int quantity) {
        return new SaleService.RequestedSaleItem(
                productId, quantity, new BigDecimal("5.00"));
    }

    private static SaleItem findSaleItem(List<SaleItem> items, long productId) {
        return items.stream()
                .filter(item -> item.getProductId() == productId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Sale item for product " + productId + " was not found."));
    }

    private static StockMovement findMovement(List<StockMovement> movements, long productId) {
        return movements.stream()
                .filter(movement -> movement.getProductId() == productId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Stock movement for product " + productId + " was not found."));
    }

    private static void requireSaleItem(
            SaleItem item,
            long saleId,
            long productId,
            int quantity,
            String productName,
            String productCode,
            String unitPrice,
            String subtotal) {
        require(item.getSaleId() == saleId, "Sale item has the wrong sale ID.");
        require(item.getProductId() == productId, "Sale item has the wrong product ID.");
        require(item.getQuantity() == quantity, "Sale item has the wrong quantity.");
        require(productName.equals(item.getProductName()), "Sale item product name was incorrect.");
        require(productCode.equals(item.getProductCode()), "Sale item product code was incorrect.");
        requireAmount(item.getUnitPrice(), unitPrice, "Sale item unit price");
        requireAmount(item.getSubtotal(), subtotal, "Sale item subtotal");
    }

    private static void requireMovement(
            StockMovement movement,
            long saleId,
            long userId,
            long productId,
            int quantityBefore,
            int quantityAfter,
            int quantityChange) {
        require(movement.getMovementType() == StockMovementType.SALE,
                "Movement type was not SALE.");
        require(movement.getProductId() == productId, "Movement has the wrong product ID.");
        require(movement.getUserId() == userId, "Movement has the wrong user ID.");
        require(movement.getReferenceSaleId() != null
                        && movement.getReferenceSaleId() == saleId,
                "Movement has the wrong sale reference.");
        require(movement.getQuantityBefore() == quantityBefore,
                "Movement has the wrong before quantity.");
        require(movement.getQuantityAfter() == quantityAfter,
                "Movement has the wrong after quantity.");
        require(movement.getQuantityChange() == quantityChange,
                "Movement has the wrong quantity change.");
    }

    private static void requireAmount(
            BigDecimal actual, String expected, String description) {
        require(
                actual != null && actual.compareTo(new BigDecimal(expected)) == 0,
                description + " was incorrect.");
    }

    private static boolean recordExists(String tableName, Set<Long> ids) throws SQLException {
        String sql = "SELECT 1 FROM " + tableName + " WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Long id : ids) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void withCleanup(TestRecords records, SqlOperation operation)
            throws SQLException {
        Throwable testFailure = null;
        try {
            operation.run();
        } catch (SQLException | RuntimeException | Error exception) {
            testFailure = exception;
            throw exception;
        } finally {
            try {
                records.cleanup();
            } catch (SQLException | RuntimeException | Error cleanupException) {
                if (testFailure == null) {
                    throw cleanupException;
                }
                if (cleanupException != testFailure) {
                    testFailure.addSuppressed(cleanupException);
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }

    private static final class TestRecords {
        private final Set<Long> productIds = new LinkedHashSet<>();
        private final Set<Long> saleIds = new LinkedHashSet<>();
        private final Set<Long> saleItemIds = new LinkedHashSet<>();
        private final Set<Long> stockMovementIds = new LinkedHashSet<>();

        private void recordProductId(long id) {
            record(productIds, id);
        }

        private void recordSaleId(long id) {
            record(saleIds, id);
        }

        private void recordSaleItemId(long id) {
            record(saleItemIds, id);
        }

        private void recordStockMovementId(long id) {
            record(stockMovementIds, id);
        }

        private void record(Set<Long> ids, long id) {
            if (id > 0) {
                ids.add(id);
            }
        }

        private void cleanup() throws SQLException {
            if (productIds.isEmpty()
                    && saleIds.isEmpty()
                    && saleItemIds.isEmpty()
                    && stockMovementIds.isEmpty()) {
                return;
            }

            SQLException failure = null;
            try (Connection connection = DatabaseConnection.getConnection()) {
                failure = deleteExactIds(
                        connection, "stock_movements", stockMovementIds, failure);
                failure = deleteExactIds(connection, "sale_items", saleItemIds, failure);
                failure = deleteExactIds(connection, "sales", saleIds, failure);
                failure = deleteExactIds(connection, "products", productIds, failure);
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private SQLException deleteExactIds(
                Connection connection,
                String tableName,
                Set<Long> ids,
                SQLException failure) {
            String sql = "DELETE FROM " + tableName + " WHERE id = ?";
            for (Long id : ids) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, id);
                    int affectedRows = statement.executeUpdate();
                    if (affectedRows > 1) {
                        throw new SQLException(
                                "Exact-ID cleanup unexpectedly affected multiple "
                                        + tableName + " rows.");
                    }
                } catch (SQLException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else if (failure != exception) {
                        failure.addSuppressed(exception);
                    }
                }
            }
            return failure;
        }
    }

    private static final class TrackingSaleDao extends SaleDao {
        private final TestRecords records;
        private int saveCalls;

        private TrackingSaleDao(TestRecords records) {
            this.records = records;
        }

        @Override
        public Sale save(Sale sale, Connection connection) throws SQLException {
            saveCalls++;
            try {
                return super.save(sale, connection);
            } finally {
                records.recordSaleId(sale.getId());
            }
        }
    }

    private static final class TrackingSaleItemDao extends SaleItemDao {
        private final TestRecords records;

        private TrackingSaleItemDao(TestRecords records) {
            this.records = records;
        }

        @Override
        public SaleItem save(SaleItem saleItem, Connection connection) throws SQLException {
            try {
                return super.save(saleItem, connection);
            } finally {
                records.recordSaleItemId(saleItem.getId());
            }
        }
    }

    private static final class TrackingStockMovementDao extends StockMovementDao {
        private final TestRecords records;
        private final int failOnSaveCall;
        private int saveCalls;

        private TrackingStockMovementDao(TestRecords records, int failOnSaveCall) {
            this.records = records;
            this.failOnSaveCall = failOnSaveCall;
        }

        @Override
        public StockMovement save(StockMovement movement, Connection connection)
                throws SQLException {
            int currentSaveCall = ++saveCalls;
            try {
                StockMovement saved = super.save(movement, connection);
                if (currentSaveCall == failOnSaveCall) {
                    throw new SQLException(ROLLBACK_FAILURE_MESSAGE);
                }
                return saved;
            } finally {
                records.recordStockMovementId(movement.getId());
            }
        }
    }
}
