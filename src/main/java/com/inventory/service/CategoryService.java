package com.inventory.service;

import com.inventory.dao.CategoryDao;
import com.inventory.model.Category;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CategoryService {
    private final CategoryDao categoryDao;

    public CategoryService(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    public List<Category> getAllCategories() throws SQLException {
        return categoryDao.findAll();
    }

    public Optional<Category> getCategoryById(long id) throws SQLException {
        return categoryDao.findById(id);
    }

    public Category createCategory(Category category) throws SQLException {
        if (category == null) {
            throw new IllegalArgumentException("Category must not be null.");
        }
        String name = normalizeName(category.getName());
        ensureNameIsAvailable(name, 0);
        category.setName(name);
        return categoryDao.save(category);
    }

    public boolean updateCategory(Category category) throws SQLException {
        if (category == null) {
            throw new IllegalArgumentException("Category must not be null.");
        }
        if (category.getId() <= 0) {
            throw new IllegalArgumentException("Category ID must be positive.");
        }
        if (categoryDao.findById(category.getId()).isEmpty()) {
            throw new IllegalStateException(
                    "Category with ID " + category.getId() + " does not exist.");
        }

        String name = normalizeName(category.getName());
        ensureNameIsAvailable(name, category.getId());
        category.setName(name);
        return categoryDao.update(category);
    }

    public boolean deleteCategory(long id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("Category ID must be positive.");
        }
        if (categoryDao.findById(id).isEmpty()) {
            throw new IllegalStateException("Category with ID " + id + " does not exist.");
        }
        return categoryDao.deleteById(id);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank.");
        }
        return name.trim();
    }

    private void ensureNameIsAvailable(String name, long currentCategoryId)
            throws SQLException {
        Optional<Category> existingCategory = categoryDao.findByName(name);
        if (existingCategory.isPresent()
                && existingCategory.get().getId() != currentCategoryId) {
            throw new IllegalArgumentException(
                    "A category with name '" + name + "' already exists.");
        }
    }
}
