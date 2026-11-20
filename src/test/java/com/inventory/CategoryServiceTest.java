package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.model.Category;
import com.inventory.service.CategoryService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CategoryServiceTest {
    private static final String ADMIN_ROLE = "ADMIN";

    private CategoryServiceTest() {
    }

    public static void main(String[] args) throws SQLException {
        CategoryService service = new CategoryService(new CategoryDao());
        String uniqueName = "Service test category " + UUID.randomUUID();
        Category category = null;
        Category duplicateCategory = null;
        Throwable testFailure = null;

        try {
            List<Category> existingCategories = service.getAllCategories();
            require(!existingCategories.isEmpty(), "Expected existing categories.");
            System.out.println("PASS: retrieved existing categories.");

            category = service.createCategory(
                    ADMIN_ROLE, new Category("  " + uniqueName + "  "));
            require(category.getId() > 0, "Created category ID must be positive.");
            System.out.println("PASS: created a category with a generated ID.");
            require(uniqueName.equals(category.getName()), "Category name was not trimmed.");
            System.out.println("PASS: category name was trimmed.");

            duplicateCategory = service.createCategory(
                    ADMIN_ROLE, new Category(uniqueName + " other"));

            Optional<Category> byId = service.getCategoryById(category.getId());
            require(byId.isPresent(), "Created category was not found by ID.");
            require(uniqueName.equals(byId.get().getName()), "Retrieved category name did not match.");
            System.out.println("PASS: retrieved the created category by ID.");

            category.setName("\u2003 " + uniqueName + " \u2003");
            require(
                    service.updateCategory(ADMIN_ROLE, category)
                            == CategoryService.UpdateResult.UNCHANGED,
                    "Updating with the category's existing name should be unchanged.");
            require(uniqueName.equals(category.getName()), "Updated category name was not trimmed.");
            System.out.println("PASS: recognized an unchanged category name.");

            String updatedName = uniqueName + " updated";
            category.setName("  " + updatedName + "  ");
            require(
                    service.updateCategory(ADMIN_ROLE, category)
                            == CategoryService.UpdateResult.UPDATED,
                    "Updating the category name failed.");
            require(updatedName.equals(category.getName()), "New category name was not trimmed.");
            Optional<Category> updated = service.getCategoryById(category.getId());
            require(updated.isPresent(), "Updated category was not found.");
            require(updatedName.equals(updated.get().getName()), "Updated name was not persisted.");
            System.out.println("PASS: updated and retrieved the category's new name.");

            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createCategory(ADMIN_ROLE, new Category(null)),
                    "reject null category name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createCategory(ADMIN_ROLE, new Category("   ")),
                    "reject blank category name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createCategory(
                            ADMIN_ROLE, new Category(" " + updatedName + " ")),
                    "reject duplicate category name");
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.createCategory(
                            ADMIN_ROLE, new Category("x".repeat(101))),
                    "reject a category name longer than 100 characters");
            long categoryId = category.getId();
            String duplicateName = duplicateCategory.getName();
            expectExact(
                    IllegalArgumentException.class,
                    () -> service.updateCategory(
                            ADMIN_ROLE,
                            new Category(categoryId, duplicateName)),
                    "reject updating to another category's name");

            expectExact(
                    IllegalStateException.class,
                    () -> service.updateCategory(
                            ADMIN_ROLE, new Category(Long.MAX_VALUE, "Missing category")),
                    "reject updating a nonexistent category");
            expectExact(
                    IllegalStateException.class,
                    () -> service.deleteCategory(ADMIN_ROLE, Long.MAX_VALUE),
                    "reject deleting a nonexistent category");

            require(
                    service.deleteCategory(ADMIN_ROLE, category.getId()),
                    "Deleting the temporary category failed.");
            System.out.println("PASS: deleted the temporary category.");
            require(service.getCategoryById(category.getId()).isEmpty(),
                    "Deleted category is still present.");
            System.out.println("PASS: deleted category is no longer retrievable.");
            require(
                    service.deleteCategory(ADMIN_ROLE, duplicateCategory.getId()),
                    "Deleting the duplicate-check category failed.");
            duplicateCategory = null;
        } catch (SQLException | RuntimeException | Error exception) {
            testFailure = exception;
            throw exception;
        } finally {
            cleanupCategory(service, category, testFailure);
            cleanupCategory(service, duplicateCategory, testFailure);
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

    private static void cleanupCategory(
            CategoryService service, Category category, Throwable testFailure)
            throws SQLException {
        if (category == null || category.getId() <= 0) {
            return;
        }

        try {
            if (service.getCategoryById(category.getId()).isPresent()) {
                service.deleteCategory(ADMIN_ROLE, category.getId());
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

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
