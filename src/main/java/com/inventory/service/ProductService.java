package com.inventory.service;

import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.model.Product;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ProductService {
    private final ProductDao productDao;
    private final CategoryDao categoryDao;

    public ProductService(ProductDao productDao, CategoryDao categoryDao) {
        this.productDao = productDao;
        this.categoryDao = categoryDao;
    }

    public List<Product> getAllProducts() throws SQLException {
        return productDao.findAll();
    }

    public Optional<Product> getProductById(long id) throws SQLException {
        return productDao.findById(id);
    }

    public Optional<Product> getProductByCode(String code) throws SQLException {
        return productDao.findByCode(code);
    }

    public List<Product> getProductsByCategoryId(long categoryId) throws SQLException {
        return productDao.findByCategoryId(categoryId);
    }

    public Product createProduct(Product product) throws SQLException {
        if (product == null) {
            throw new IllegalArgumentException("Product must not be null.");
        }
        validateProductFields(product);
        ensureCategoryExists(product.getCategoryId());
        ensureCodeIsAvailable(product.getCode(), 0);
        normalizeProductFields(product);
        return productDao.save(product);
    }

    public boolean updateProduct(Product product) throws SQLException {
        if (product == null) {
            throw new IllegalArgumentException("Product must not be null.");
        }
        if (product.getId() <= 0) {
            throw new IllegalArgumentException("Product ID must be positive.");
        }
        if (productDao.findById(product.getId()).isEmpty()) {
            throw new IllegalStateException(
                    "Product with ID " + product.getId() + " does not exist.");
        }

        validateProductFields(product);
        ensureCategoryExists(product.getCategoryId());
        String code = product.getCode().trim();
        ensureCodeIsAvailable(code, product.getId());
        normalizeProductFields(product);
        return productDao.update(product);
    }

    public boolean deleteProduct(long id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("Product ID must be positive.");
        }
        if (productDao.findById(id).isEmpty()) {
            throw new IllegalStateException("Product with ID " + id + " does not exist.");
        }
        return productDao.deleteById(id);
    }

    private void validateProductFields(Product product) throws SQLException {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank.");
        }
        if (product.getCode() == null || product.getCode().isBlank()) {
            throw new IllegalArgumentException("Product code must not be blank.");
        }
        if (product.getCategoryId() <= 0) {
            throw new IllegalArgumentException("Category ID must be positive.");
        }
        validatePrice(product.getPurchasePrice(), "Purchase price");
        validatePrice(product.getSellingPrice(), "Selling price");
        if (product.getQuantity() < 0) {
            throw new IllegalArgumentException("Product quantity must not be negative.");
        }
        if (product.getMinimumStock() < 0) {
            throw new IllegalArgumentException("Minimum stock must not be negative.");
        }
    }

    private void validatePrice(BigDecimal price, String fieldName) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be null or negative.");
        }
    }

    private void ensureCategoryExists(long categoryId) throws SQLException {
        if (categoryDao.findById(categoryId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Category with ID " + categoryId + " does not exist.");
        }
    }

    private void ensureCodeIsAvailable(String code, long currentProductId)
            throws SQLException {
        Optional<Product> existingProduct = productDao.findByCode(code);
        if (existingProduct.isPresent()
                && existingProduct.get().getId() != currentProductId) {
            throw new IllegalArgumentException(
                    "A product with code '" + code + "' already exists.");
        }
    }

    private void normalizeProductFields(Product product) {
        product.setName(product.getName().trim());
        product.setCode(product.getCode().trim());
    }
}
