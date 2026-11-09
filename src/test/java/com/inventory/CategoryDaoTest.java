package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.model.Category;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class CategoryDaoTest {
    private CategoryDaoTest() {
    }

    public static void main(String[] args) throws SQLException {
        CategoryDao categoryDao = new CategoryDao();
        String originalName = "DAO integration test " + System.currentTimeMillis();
        String updatedName = originalName + " updated";
        Category category = new Category(originalName);

        try {
            categoryDao.save(category);
            require(category.getId() > 0, "Generated category ID must be greater than zero.");
            System.out.println("PASS: save generated a category ID.");

            Optional<Category> byId = categoryDao.findById(category.getId());
            require(byId.isPresent(), "findById did not return the inserted category.");
            require(originalName.equals(byId.get().getName()), "findById returned the wrong category.");
            System.out.println("PASS: findById returned the inserted category.");

            Optional<Category> byName = categoryDao.findByName(originalName);
            require(byName.isPresent(), "findByName did not return the inserted category.");
            require(category.getId() == byName.get().getId(), "findByName returned the wrong category.");
            System.out.println("PASS: findByName returned the inserted category.");

            List<Category> categories = categoryDao.findAll();
            require(
                    categories.stream().anyMatch(found -> found.getId() == category.getId()),
                    "findAll did not contain the inserted category.");
            System.out.println("PASS: findAll contains the inserted category.");

            category.setName(updatedName);
            require(categoryDao.update(category), "update did not update the inserted category.");
            System.out.println("PASS: update changed the category.");

            Optional<Category> updated = categoryDao.findById(category.getId());
            require(updated.isPresent(), "Updated category was not found by ID.");
            require(updatedName.equals(updated.get().getName()), "Updated category name did not persist.");
            System.out.println("PASS: findById returned the updated name.");

            require(categoryDao.deleteById(category.getId()), "deleteById did not delete the test category.");
            System.out.println("PASS: deleteById deleted the test category.");

            Optional<Category> afterDelete = categoryDao.findById(category.getId());
            require(afterDelete.isEmpty(), "Deleted category is still present.");
            System.out.println("PASS: findById returns empty after deletion.");
        } finally {
            Optional<Category> remaining = categoryDao.findById(category.getId());
            if (remaining.isPresent()) {
                categoryDao.deleteById(category.getId());
                System.out.println("CLEANUP: deleted the temporary test category.");
            }
        }

        System.out.println("CategoryDao integration test passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
