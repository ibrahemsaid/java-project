package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.model.Category;
import com.inventory.model.Product;
import com.inventory.service.ProductService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ProductServiceTest {
    private ProductServiceTest() {
    }

    public static void main(String[] args) throws SQLException {
        CategoryDao categoryDao = new CategoryDao();
        ProductDao productDao = new ProductDao();
        ProductService service = new ProductService(productDao, categoryDao);
        List<Category> categories = categoryDao.findAll();
        require(!categories.isEmpty(), "Expected an existing category for the product.");
        long categoryId = categories.get(0).getId();
        System.out.println("PASS: retrieved an existing category for the product.");

        List<Product> existingProducts = service.getAllProducts();
        System.out.println("PASS: retrieved existing products.");

        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "");
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
        Throwable testFailure = null;

        try {
            service.createProduct(product);
            require(product.getId() > 0, "Created product ID must be positive.");
            System.out.println("PASS: created a product with a generated ID.");
            require(productName.equals(product.getName()), "Product name was not trimmed.");
            require(productCode.equals(product.getCode()), "Product code was not trimmed.");
            System.out.println("PASS: product name and code were trimmed.");

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
            product.setQuantity(19);
            product.setMinimumStock(7);
            require(service.updateProduct(product), "Updating the product with its own code failed.");
            require(productCode.equals(product.getCode()), "Updated product code was not trimmed.");
            System.out.println("PASS: updated the product while retaining its own code.");

            Optional<Product> updated = service.getProductById(product.getId());
            require(updated.isPresent(), "Updated product was not found by ID.");
            requireProductEquals(updated.get(), product, "Updated product");
            System.out.println("PASS: retrieved all updated product fields.");

            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, null, "NULL-NAME-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0)),
                    "reject null product name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "   ", "BLANK-NAME-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0)),
                    "reject blank product name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Valid name", null,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0)),
                    "reject null product code");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Valid name", "   ",
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0)),
                    "reject blank product code");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Duplicate code", productCode,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0)),
                    "reject duplicate product code");

            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Null purchase price", "NULL-PURCHASE-" + uniqueSuffix,
                            null, new BigDecimal("2.00"), 1, 0)),
                    "reject null purchase price");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Negative purchase price", "NEG-PURCHASE-" + uniqueSuffix,
                            new BigDecimal("-0.01"), new BigDecimal("2.00"), 1, 0)),
                    "reject negative purchase price");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Null selling price", "NULL-SELLING-" + uniqueSuffix,
                            new BigDecimal("1.00"), null, 1, 0)),
                    "reject null selling price");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Negative selling price", "NEG-SELLING-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("-0.01"), 1, 0)),
                    "reject negative selling price");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Negative quantity", "NEG-QUANTITY-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), -1, 0)),
                    "reject negative quantity");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            categoryId, "Negative minimum stock", "NEG-MINIMUM-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, -1)),
                    "reject negative minimum stock");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createProduct(product(
                            Long.MAX_VALUE, "Missing category", "MISSING-CATEGORY-" + uniqueSuffix,
                            new BigDecimal("1.00"), new BigDecimal("2.00"), 1, 0)),
                    "reject nonexistent category ID");

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

            require(service.deleteProduct(product.getId()), "Deleting the temporary product failed.");
            System.out.println("PASS: deleted the temporary product.");
            require(service.getProductById(product.getId()).isEmpty(),
                    "Deleted product is still present.");
            System.out.println("PASS: deleted product is no longer retrievable.");
        } catch (SQLException | RuntimeException | Error exception) {
            testFailure = exception;
            throw exception;
        } finally {
            if (product.getId() > 0) {
                try {
                    if (service.getProductById(product.getId()).isPresent()) {
                        service.deleteProduct(product.getId());
                        System.out.println("CLEANUP: deleted the temporary product.");
                    }
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
