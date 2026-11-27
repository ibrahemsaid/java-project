package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.dao.StockMovementDao;
import com.inventory.model.Category;
import com.inventory.model.Product;
import com.inventory.model.StockMovement;
import com.inventory.model.StockMovementType;
import com.inventory.service.ProductService;
import com.inventory.util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ProductServiceTest {
    private ProductServiceTest() {
    }

    public static void main(String[] args) throws SQLException {
        CategoryDao categoryDao = new CategoryDao();
        ProductDao productDao = new ProductDao();
        StockMovementDao stockMovementDao = new StockMovementDao();
        ProductService service = new ProductService(productDao, categoryDao, stockMovementDao);
        long authenticatedUserId = findExistingUserId();
        List<Category> categories = categoryDao.findAll();
        require(!categories.isEmpty(), "Expected an existing category for the product.");
        long categoryId = categories.get(0).getId();
        System.out.println("PASS: retrieved an existing category for the product.");

        List<Product> existingProducts = service.getAllProducts();
        System.out.println("PASS: retrieved existing products.");

        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "");
        List<Product> testProducts = new ArrayList<>();
        String productName = "Service test product " + uniqueSuffix;
        String productCode = "SERVICE-TEST-" + uniqueSuffix;
        Product product = new Product(
                "  " + productName + "  ",
                "  " + productCode + "  ",
                categoryId,
                new BigDecimal("10.25"),
                new BigDecimal("15.75"),
                25,
                5);
        testProducts.add(product);
        int originalQuantity = product.getQuantity();
        Throwable testFailure = null;

        try {
            service.createProduct(product, authenticatedUserId);
            require(product.getId() > 0, "Created product ID must be positive.");
            System.out.println("PASS: created a product with a generated ID.");
            require(productName.equals(product.getName()), "Product name was not trimmed.");
            require(productCode.equals(product.getCode()), "Product code was not trimmed.");
            System.out.println("PASS: product name and code were trimmed.");
            requireOpeningReceipt(stockMovementDao, product, authenticatedUserId);

            Product zeroQuantityProduct = product(
                    categoryId, "Zero stock test product " + uniqueSuffix,
                    "ZERO-TEST-" + uniqueSuffix,
                    new BigDecimal("1.00"), new BigDecimal("2.00"), 0, 0);
            testProducts.add(zeroQuantityProduct);
            service.createProduct(zeroQuantityProduct, authenticatedUserId);
            require(
                    productDao.findById(zeroQuantityProduct.getId()).orElseThrow().getQuantity() == 0,
                    "Zero-quantity product was not created with zero stock.");
            require(
                    stockMovementDao.findByProductId(zeroQuantityProduct.getId()).isEmpty(),
                    "Creating a zero-quantity product created a stock movement.");
            System.out.println("PASS: zero-quantity product was created without a stock movement.");

            Product failedProduct = product(
                    categoryId, "Failed receipt test product " + uniqueSuffix,
                    "FAILED-RECEIPT-TEST-" + uniqueSuffix,
                    new BigDecimal("1.00"), new BigDecimal("2.00"), 3, 0);
            testProducts.add(failedProduct);
            StockMovementDao failingStockMovementDao = new StockMovementDao() {
                @Override
                public StockMovement save(StockMovement movement, Connection connection)
                        throws SQLException {
                    throw new SQLException("Intentional opening movement insert failure.");
                }
            };
            ProductService failingService =
                    new ProductService(productDao, categoryDao, failingStockMovementDao);
            long originalFailedProductId = failedProduct.getId();
            try {
                failingService.createProduct(failedProduct, authenticatedUserId);
                throw new AssertionError("A failed opening movement insert did not fail creation.");
            } catch (SQLException exception) {
                require(
                        "Intentional opening movement insert failure.".equals(exception.getMessage()),
                        "The opening movement insert failure was not propagated.");
            }
            require(
                    failedProduct.getId() == originalFailedProductId,
                    "Product ID was not restored after rolling back product creation.");
            require(
                    productDao.findByCode("FAILED-RECEIPT-TEST-" + uniqueSuffix).isEmpty(),
                    "Product remained in the database after its opening movement insert failed.");
            System.out.println("PASS: failed opening movement insert rolled back product creation.");

            Optional<Product> byId = service.getProductById(product.getId());
            require(byId.isPresent(), "Created product was not found by ID.");
            requireProductEquals(byId.get(), product, "Created product");
            System.out.println("PASS: retrieved the created product by ID.");

            Optional<Product> byCode = service.getProductByCode(productCode);
            require(byCode.isPresent(), "Created product was not found by code.");
            require(byCode.get().getId() == product.getId(), "Code lookup returned a different product.");
            System.out.println("PASS: retrieved the created product by code.");

            List<Product> byCategory = service.getProductsByCategoryId(categoryId);
            require(byCategory.stream().anyMatch(found -> found.getId() == product.getId()),
                    "Category lookup did not include the created product.");
            System.out.println("PASS: retrieved the created product by category ID.");

            List<Product> allProducts = service.getAllProducts();
            require(allProducts.stream().anyMatch(found -> found.getId() == product.getId()),
                    "Product list did not include the created product.");
            System.out.println("PASS: all products include the created product.");

            product.setName("Updated service test product " + uniqueSuffix);
            product.setCode("  " + productCode + "  ");
            product.setPurchasePrice(new BigDecimal("11.50"));
            product.setSellingPrice(new BigDecimal("18.40"));
            product.setMinimumStock(7);
            require(service.updateProduct(product), "Updating the product with its own code failed.");
            require(productCode.equals(product.getCode()), "Updated product code was not trimmed.");
            System.out.println("PASS: updated the product while retaining its own code.");

            Optional<Product> updated = service.getProductById(product.getId());
            require(updated.isPresent(), "Updated product was not found by ID.");
            requireProductEquals(updated.get(), product, "Updated product");
            System.out.println("PASS: retrieved all updated product fields.");

            product.setQuantity(19);
            try {
                service.updateProduct(product);
                throw new AssertionError(
                        "Changing stock quantity through product update was not rejected.");
            } catch (IllegalArgumentException exception) {
                require(
                        exception.getMessage().contains("StockMovementService.adjustStock"),
                        "Quantity-change error did not direct callers to the stock movement workflow.");
            }

            Optional<Product> afterRejectedQuantityChange =
                    service.getProductById(product.getId());
            require(afterRejectedQuantityChange.isPresent(),
                    "Product was not found after rejecting a quantity change.");
            require(
                    afterRejectedQuantityChange.get().getQuantity() == originalQuantity,
                    "Rejected product update changed the stock quantity.");
            require(
                    updated.get().getName().equals(afterRejectedQuantityChange.get().getName()),
                    "Rejected quantity update changed the product name.");
            require(
                    updated.get().getSellingPrice().compareTo(
                            afterRejectedQuantityChange.get().getSellingPrice()) == 0,
                    "Rejected quantity update changed the selling price.");
            product.setQuantity(originalQuantity);

            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, null, "NULL-NAME-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject null product name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "   ", "BLANK-NAME-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject blank product name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Valid name", null,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject null product code");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Valid name", "   ",
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject blank product code");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Duplicate code", productCode,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject duplicate product code");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Padded duplicate code", "  " + productCode + "  ",
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject whitespace-padded duplicate product code");

            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Null purchase price", "NULL-PURCHASE-" + uniqueSuffix,
                            null, new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject null purchase price");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Negative purchase price", "NEG-PURCHASE-" + uniqueSuffix,
                            new BigDecimal("-0.01"), new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject negative purchase price");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Null selling price", "NULL-SELLING-" + uniqueSuffix,
                            new BigDecimal("1.00"), null, 1, 0),
                            authenticatedUserId),
                    "reject null selling price");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Negative selling price", "NEG-SELLING-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("-0.01"), 1, 0),
                            authenticatedUserId),
                    "reject negative selling price");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Negative quantity", "NEG-QUANTITY-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), -1, 0),
                            authenticatedUserId),
                    "reject negative quantity");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Negative minimum stock", "NEG-MINIMUM-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, -1),
                            authenticatedUserId),
                    "reject negative minimum stock");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            Long.MAX_VALUE, "Missing category", "MISSING-CATEGORY-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0),
                            authenticatedUserId),
                    "reject nonexistent category ID");

            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(
                            product(
                                    categoryId, "Invalid user", "INVALID-USER-" + uniqueSuffix,
                                    new BigDecimal("1.00"), new BigDecimal("2.00"), 0, 0),
                            0),
                    "reject invalid authenticated user ID");

            Product missingProduct = product(
                    categoryId, "Missing product", "MISSING-PRODUCT-" + uniqueSuffix,
                    new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0);
            missingProduct.setId(Long.MAX_VALUE);
            expectExact(
                    IllegalStateException.class,
                    () -> service.updateProduct(missingProduct),
                    "reject updating a nonexistent product");
            expectExact(
                    IllegalStateException.class,
                    () -> service.deleteProduct(Long.MAX_VALUE),
                    "reject deleting a nonexistent product");

            deleteTestMovements(product.getId());
            require(service.deleteProduct(product.getId()), "Deleting the temporary product failed.");
            System.out.println("PASS: deleted the temporary product.");
            require(service.getProductById(product.getId()).isEmpty(),
                    "Deleted product is still present.");
            System.out.println("PASS: deleted product is no longer retrievable.");
        } catch (SQLException | RuntimeException | Error exception) {
            testFailure = exception;
            throw exception;
        } finally {
            for (Product testProduct : testProducts) {
                try {
                    cleanupTestProduct(testProduct, service);
                } catch (SQLException | RuntimeException | Error cleanupException) {
                    if (testFailure != null) {
                        testFailure.addSuppressed(cleanupException);
                    } else if (cleanupException instanceof SQLException sqlException) {
                        throw sqlException;
                    } else if (cleanupException instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    } else {
                        throw (Error) cleanupException;
                    }
                }
            }
        }

        System.out.println("ProductService integration test passed.");
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

        throw new SQLException("No existing user is available for product creation tests.");
    }

    private static void requireOpeningReceipt(
            StockMovementDao stockMovementDao,
            Product product,
            long authenticatedUserId) throws SQLException {
        List<StockMovement> movements =
                stockMovementDao.findByProductId(product.getId());
        require(movements.size() == 1, "Opening stock did not create exactly one movement.");

        StockMovement movement = movements.get(0);
        require(
                movement.getMovementType() == StockMovementType.RECEIPT,
                "Opening stock movement type was not RECEIPT.");
        require(movement.getProductId() == product.getId(),
                "Opening stock movement has the wrong product ID.");
        require(movement.getUserId() == authenticatedUserId,
                "Opening stock movement has the wrong authenticated user ID.");
        require(movement.getQuantityChange() == product.getQuantity(),
                "Opening stock movement has the wrong quantity change.");
        require(movement.getQuantityBefore() == 0,
                "Opening stock movement has a nonzero starting quantity.");
        require(movement.getQuantityAfter() == product.getQuantity(),
                "Opening stock movement has the wrong resulting quantity.");
        require(
                "Opening stock on product creation".equals(movement.getReason()),
                "Opening stock movement has the wrong reason.");
        System.out.println("PASS: positive opening stock created the expected RECEIPT movement.");
    }

    private static void deleteTestMovements(long productId) throws SQLException {
        String sql = "DELETE FROM stock_movements WHERE product_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            statement.executeUpdate();
        }
    }

    private static void cleanupTestProduct(Product product, ProductService service)
            throws SQLException {
        if (product.getId() <= 0) {
            return;
        }

        deleteTestMovements(product.getId());
        if (service.getProductById(product.getId()).isPresent()) {
            service.deleteProduct(product.getId());
            System.out.println("CLEANUP: deleted temporary product and its stock movements.");
        }
    }

    private static Product product(
            long categoryId,
            String name,
            String code,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            int quantity,
            int minimumStock) {
        return new Product(
                name, code, categoryId, purchasePrice, sellingPrice, quantity, minimumStock);
    }

    private static void requireProductEquals(Product actual, Product expected, String description) {
        require(actual.getId() == expected.getId(), description + " ID does not match.");
        require(expected.getName().equals(actual.getName()), description + " name does not match.");
        require(expected.getCode().equals(actual.getCode()), description + " code does not match.");
        require(
                actual.getCategoryId() == expected.getCategoryId(),
                description + " category ID does not match.");
        require(
                actual.getPurchasePrice().compareTo(expected.getPurchasePrice()) == 0,
                description + " purchase price does not match.");
        require(
                actual.getSellingPrice().compareTo(expected.getSellingPrice()) == 0,
                description + " selling price does not match.");
        require(actual.getQuantity() == expected.getQuantity(), description + " quantity does not match.");
        require(
                actual.getMinimumStock() == expected.getMinimumStock(),
                description + " minimum stock does not match.");
    }

    private static void expectExact(
            Class<? extends RuntimeException> expectedType,
            SqlOperation operation,
            String checkName) throws SQLException {
        try {
            operation.run();
        } catch (SQLException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (exception.getClass() != expectedType) {
                throw new AssertionError(
                        checkName + " threw " + exception.getClass().getName()
                                + " instead of " + expectedType.getName(),
                        exception);
            }
            System.out.println("PASS: " + checkName + ".");
            return;
        }

        throw new AssertionError(checkName + " did not throw " + expectedType.getName() + ".");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("PASS: " + message);
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
