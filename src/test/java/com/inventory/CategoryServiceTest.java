package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.model.Category;
import com.inventory.service.CategoryService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CategoryServiceTest {
    private CategoryServiceTest() {
    }

    public static void main(String[] args) throws SQLException {
        CategoryService service = new CategoryService(new CategoryDao());
        String uniqueName = "Service test category " + UUID.randomUUID();
        Category category = null;
        Throwable testFailure = null;

        try {
            List<Category> existingCategories = service.getAllCategories();
            require(!existingCategories.isEmpty(), "Expected existing categories.");
            System.out.println("PASS: retrieved existing categories.");

            category = service.createCategory(new Category("  " + uniqueName + "  "));
            require(category.getId() > 0, "Created category ID must be positive.");
            System.out.println("PASS: created a category with a generated ID.");
            require(uniqueName.equals(category.getName()), "Category name was not trimmed.");
            System.out.println("PASS: category name was trimmed.");

            Optional<Category> byId = service.getCategoryById(category.getId());
            require(byId.isPresent(), "Created category was not found by ID.");
            require(uniqueName.equals(byId.get().getName()), "Retrieved category name did not match.");
            System.out.println("PASS: retrieved the created category by ID.");

            category.setName("  " + uniqueName + "  ");
            require(service.updateCategory(category), "Updating the category's existing name failed.");
            require(uniqueName.equals(category.getName()), "Updated category name was not trimmed.");
            System.out.println("PASS: updated a category while retaining its own name.");

            String updatedName = uniqueName + " updated";
            category.setName("  " + updatedName + "  ");
            require(service.updateCategory(category), "Updating the category name failed.");
            require(updatedName.equals(category.getName()), "New category name was not trimmed.");
            Optional<Category> updated = service.getCategoryById(category.getId());
            require(updated.isPresent(), "Updated category was not found.");
            require(updatedName.equals(updated.get().getName()), "Updated name was not persisted.");
            System.out.println("PASS: updated and retrieved the category's new name.");

            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createCategory(new Category(null)),
                    "reject null category name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createCategory(new Category("   ")),
                    "reject blank category name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createCategory(new Category(" " + updatedName + " ")),
                    "reject duplicate category name");

            expectExact(
                    IllegalStateException.class,
                    () -> service.updateCategory(new Category(Long.MAX_VALUE, "Missing category")),
                    "reject updating a nonexistent category");
            expectExact(
                    IllegalStateException.class,
                    () -> service.deleteCategory(Long.MAX_VALUE),
                    "reject deleting a nonexistent category");

            require(service.deleteCategory(category.getId()), "Deleting the temporary category failed.");
            System.out.println("PASS: deleted the temporary category.");
            require(service.getCategoryById(category.getId()).isEmpty(),
                    "Deleted category is still present.");
            System.out.println("PASS: deleted category is no longer retrievable.");
        } catch (SQLException | RuntimeException | Error exception) {
            testFailure = exception;
            throw exception;
        } finally {
            if (category != null && category.getId() > 0) {
                try {
                    if (service.getCategoryById(category.getId()).isPresent()) {
                        service.deleteCategory(category.getId());
                        System.out.println("CLEANUP: deleted the temporary category.");
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

        System.out.println("CategoryService integration test passed.");
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
