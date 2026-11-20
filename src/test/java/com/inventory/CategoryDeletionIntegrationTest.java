package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.model.Category;
import com.inventory.model.Product;
import com.inventory.service.CategoryService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

public final class CategoryDeletionIntegrationTest {
    private static final String ADMIN_ROLE = "ADMIN";

    private CategoryDeletionIntegrationTest() {
    }

    public static void main(String[] args) throws SQLException {
        CategoryDao categoryDao = new CategoryDao();
        ProductDao productDao = new ProductDao();
        CategoryService categoryService = new CategoryService(categoryDao);
        Category category = null;
        Product product = null;
        Throwable testFailure = null;

        try {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            category = categoryService.createCategory(
                    ADMIN_ROLE, new Category("FK test category " + suffix));
            product = new Product(
                    "FK test product " + suffix,
                    "FK-TEST-" + suffix,
                    category.getId(),
                    new BigDecimal("1.00"),
                    new BigDecimal("2.00"),
                    1,
                    0);
            productDao.save(product);

            long categoryId = category.getId();
            expectExact(
                    CategoryService.CategoryInUseException.class,
                    () -> categoryService.deleteCategory(ADMIN_ROLE, categoryId),
                    "reject deletion of a category referenced by a product");

            System.out.println("Category deletion integration test passed.");
        } catch (SQLException | RuntimeException | Error exception) {
            testFailure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (product != null && product.getId() > 0) {
                try {
                    productDao.deleteById(product.getId());
                } catch (SQLException | RuntimeException | Error exception) {
                    cleanupFailure = exception;
                }
            }
            if (category != null && category.getId() > 0) {
                try {
                    if (categoryDao.findById(category.getId()).isPresent()) {
                        categoryService.deleteCategory(ADMIN_ROLE, category.getId());
                    }
                } catch (SQLException | RuntimeException | Error exception) {
                    if (cleanupFailure == null) {
                        cleanupFailure = exception;
                    } else {
                        cleanupFailure.addSuppressed(exception);
                    }
                }
            }
            if (cleanupFailure != null) {
                if (testFailure != null) {
                    testFailure.addSuppressed(cleanupFailure);
                } else if (cleanupFailure instanceof SQLException sqlException) {
                    throw sqlException;
                } else if (cleanupFailure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                } else {
                    throw (Error) cleanupFailure;
                }
            }
        }
    }

    private static void expectExact(
            Class<? extends RuntimeException> expectedType,
            CheckedOperation operation,
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
            System.out.println("PASS: " + checkName);
            return;
        }
        throw new AssertionError(checkName + " did not throw " + expectedType.getName());
    }

    @FunctionalInterface
    private interface CheckedOperation {
        Object run() throws SQLException;
    }
}
