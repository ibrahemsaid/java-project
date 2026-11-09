package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.model.Category;
import com.inventory.model.Product;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ProductDaoTest {
    private ProductDaoTest() {
    }

    public static void main(String[] args) throws SQLException {
        CategoryDao categoryDao = new CategoryDao();
        ProductDao productDao = new ProductDao();
        List<Category> categories = categoryDao.findAll();
        require(!categories.isEmpty(), "No existing category is available for the test product.");
        Category category = categories.get(0);
        System.out.println("PASS: retrieved an existing category for the test product.");

        String uniqueSuffix = System.currentTimeMillis() + "-" + UUID.randomUUID();
        String originalName = "DAO integration test product " + uniqueSuffix;
        String code = "DAO-TEST-" + uniqueSuffix;
        String updatedName = originalName + " updated";
        Product product = new Product(
                originalName,
                code,
                category.getId(),
                new BigDecimal("10.25"),
                new BigDecimal("15.75"),
                25,
                5);

        try {
            productDao.save(product);
            require(product.getId() > 0, "Generated product ID must be greater than zero.");
            System.out.println("PASS: saved the temporary product and received a generated ID.");

            Optional<Product> byId = productDao.findById(product.getId());
            require(byId.isPresent(), "findById did not return the inserted product.");
            requireMatches(byId.get(), product, originalName, code);
            System.out.println("PASS: findById returned the inserted product.");

            Optional<Product> byCode = productDao.findByCode(code);
            require(byCode.isPresent(), "findByCode did not return the inserted product.");
            require(byCode.get().getId() == product.getId(), "findByCode returned the wrong product.");
            System.out.println("PASS: findByCode returned the inserted product.");

            List<Product> byCategory = productDao.findByCategoryId(category.getId());
            require(
                    byCategory.stream().anyMatch(found -> found.getId() == product.getId()),
                    "findByCategoryId did not include the inserted product.");
            System.out.println("PASS: findByCategoryId includes the inserted product.");

            List<Product> allProducts = productDao.findAll();
            require(
                    allProducts.stream().anyMatch(found -> found.getId() == product.getId()),
                    "findAll did not include the inserted product.");
            System.out.println("PASS: findAll includes the inserted product.");

            product.setName(updatedName);
            product.setSellingPrice(new BigDecimal("18.40"));
            product.setQuantity(19);
            product.setMinimumStock(7);
            require(productDao.update(product), "update did not update the temporary product.");
            System.out.println("PASS: updated the temporary product.");

            Optional<Product> updated = productDao.findById(product.getId());
            require(updated.isPresent(), "Updated product was not found by ID.");
            requireUpdatedValues(updated.get(), product);
            System.out.println("PASS: findById returned all updated values.");

            require(productDao.deleteById(product.getId()), "deleteById did not delete the temporary product.");
            System.out.println("PASS: deleted the temporary product.");

            Optional<Product> afterDelete = productDao.findById(product.getId());
            require(afterDelete.isEmpty(), "Deleted product is still present.");
            System.out.println("PASS: findById returns empty after deletion.");
        } finally {
            if (product.getId() > 0 && productDao.findById(product.getId()).isPresent()) {
                productDao.deleteById(product.getId());
                System.out.println("CLEANUP: deleted the temporary product.");
            }
        }

        System.out.println("ProductDao integration test passed.");
    }

    private static void requireMatches(
            Product actual, Product expected, String expectedName, String expectedCode) {
        require(actual.getId() == expected.getId(), "Product ID does not match.");
        require(expectedName.equals(actual.getName()), "Product name does not match.");
        require(expectedCode.equals(actual.getCode()), "Product code does not match.");
        require(actual.getCategoryId() == expected.getCategoryId(), "Category ID does not match.");
        require(
                actual.getPurchasePrice().compareTo(expected.getPurchasePrice()) == 0,
                "Purchase price does not match.");
        require(
                actual.getSellingPrice().compareTo(expected.getSellingPrice()) == 0,
                "Selling price does not match.");
        require(actual.getQuantity() == expected.getQuantity(), "Quantity does not match.");
        require(actual.getMinimumStock() == expected.getMinimumStock(), "Minimum stock does not match.");
    }

    private static void requireUpdatedValues(Product actual, Product expected) {
        require(actual.getId() == expected.getId(), "Updated product ID does not match.");
        require(expected.getName().equals(actual.getName()), "Updated product name does not match.");
        require(expected.getCode().equals(actual.getCode()), "Product code changed unexpectedly.");
        require(actual.getCategoryId() == expected.getCategoryId(), "Category ID changed unexpectedly.");
        require(
                actual.getPurchasePrice().compareTo(expected.getPurchasePrice()) == 0,
                "Purchase price changed unexpectedly.");
        require(
                actual.getSellingPrice().compareTo(expected.getSellingPrice()) == 0,
                "Updated selling price does not match.");
        require(actual.getQuantity() == expected.getQuantity(), "Updated quantity does not match.");
        require(
                actual.getMinimumStock() == expected.getMinimumStock(),
                "Updated minimum stock does not match.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
