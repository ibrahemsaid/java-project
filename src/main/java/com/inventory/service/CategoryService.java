package com.inventory.service;

import com.inventory.dao.CategoryDao;
import com.inventory.model.Category;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CategoryService {
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int DUPLICATE_KEY_ERROR_CODE = 1062;
    private static final int REFERENCED_ROW_ERROR_CODE = 1451;
    private static final int MAX_NAME_LENGTH = 100;

    private final CategoryDao categoryDao;

    public CategoryService(CategoryDao categoryDao) {
        this.categoryDao = Objects.requireNonNull(categoryDao);
    }

    public List<Category> getAllCategories() throws SQLException {
        return categoryDao.findAll();
    }

    public Optional<Category> getCategoryById(long id) throws SQLException {
        return categoryDao.findById(id);
    }

    public Category createCategory(String authenticatedRole, Category category)
            throws SQLException {
        requireAdmin(authenticatedRole);
        if (category == null) {
            throw new IllegalArgumentException("Category must not be null.");
        }
        String name = normalizeName(category.getName());
        ensureNameIsAvailable(name, 0);
        category.setName(name);
        try {
            return categoryDao.save(category);
        } catch (SQLException exception) {
            if (hasErrorCode(exception, DUPLICATE_KEY_ERROR_CODE)) {
                throw duplicateNameException(name, exception);
            }
            throw exception;
        }
    }

    public UpdateResult updateCategory(String authenticatedRole, Category category)
            throws SQLException {
        requireAdmin(authenticatedRole);
        if (category == null) {
            throw new IllegalArgumentException("Category must not be null.");
        }
        if (category.getId() <= 0) {
            throw new IllegalArgumentException("Category ID must be positive.");
        }

        String name = normalizeName(category.getName());
        Optional<Category> existingCategory = categoryDao.findById(category.getId());
        if (existingCategory.isEmpty()) {
            throw new IllegalStateException(
                    "Category with ID " + category.getId() + " does not exist.");
        }
        Optional<Category> matchingCategory = categoryDao.findByName(name);
        if (matchingCategory.isPresent()
                && matchingCategory.get().getId() == category.getId()) {
            category.setName(existingCategory.get().getName());
            return UpdateResult.UNCHANGED;
        }
        if (matchingCategory.isPresent()) {
            throw duplicateNameException(name, null);
        }

        category.setName(name);
        try {
            if (!categoryDao.update(category)) {
                throw new IllegalStateException(
                        "Category with ID " + category.getId() + " does not exist.");
            }
            return UpdateResult.UPDATED;
        } catch (SQLException exception) {
            if (hasErrorCode(exception, DUPLICATE_KEY_ERROR_CODE)) {
                throw duplicateNameException(name, exception);
            }
            throw exception;
        }
    }

    public boolean deleteCategory(String authenticatedRole, long id) throws SQLException {
        requireAdmin(authenticatedRole);
        if (id <= 0) {
            throw new IllegalArgumentException("Category ID must be positive.");
        }
        if (categoryDao.findById(id).isEmpty()) {
            throw new IllegalStateException("Category with ID " + id + " does not exist.");
        }
        try {
            if (!categoryDao.deleteById(id)) {
                throw new IllegalStateException("Category with ID " + id + " does not exist.");
            }
            return true;
        } catch (SQLException exception) {
            if (hasErrorCode(exception, REFERENCED_ROW_ERROR_CODE)) {
                throw new CategoryInUseException(
                        "This category is assigned to one or more products and cannot be deleted.",
                        exception);
            }
            throw exception;
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank.");
        }
        String normalizedName = name.strip();
        if (normalizedName.codePointCount(0, normalizedName.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Category name must not exceed " + MAX_NAME_LENGTH + " characters.");
        }
        return normalizedName;
    }

    private void ensureNameIsAvailable(String name, long currentCategoryId)
            throws SQLException {
        Optional<Category> existingCategory = categoryDao.findByName(name);
        if (existingCategory.isPresent()
                && existingCategory.get().getId() != currentCategoryId) {
            throw duplicateNameException(name, null);
        }
    }

    private void requireAdmin(String authenticatedRole) {
        if (!ADMIN_ROLE.equals(authenticatedRole)) {
            throw new SecurityException("Only Admin users can manage categories.");
        }
    }

    private IllegalArgumentException duplicateNameException(String name, SQLException cause) {
        return new IllegalArgumentException(
                "A category with name '" + name + "' already exists.", cause);
    }

    private boolean hasErrorCode(SQLException exception, int errorCode) {
        for (SQLException current = exception;
             current != null;
             current = current.getNextException()) {
            if (current.getErrorCode() == errorCode) {
                return true;
            }
        }
        return false;
    }

    public enum UpdateResult {
        UPDATED,
        UNCHANGED
    }

    public static final class CategoryInUseException extends IllegalStateException {
        public CategoryInUseException(String message, SQLException cause) {
            super(message, cause);
        }
    }
}
