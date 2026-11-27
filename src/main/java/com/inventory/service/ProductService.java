package com.inventory.service;

import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.dao.StockMovementDao;
import com.inventory.model.Product;
import com.inventory.model.StockMovement;
import com.inventory.model.StockMovementType;
import com.inventory.util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ProductService {
    private static final String OPENING_STOCK_REASON = "Opening stock on product creation";

    private final ProductDao productDao;
    private final CategoryDao categoryDao;
    private final StockMovementDao stockMovementDao;

    public ProductService(ProductDao productDao, CategoryDao categoryDao) {
        this(productDao, categoryDao, new StockMovementDao());
    }

    public ProductService(
            ProductDao productDao,
            CategoryDao categoryDao,
            StockMovementDao stockMovementDao) {
        this.productDao = productDao;
        this.categoryDao = categoryDao;
        this.stockMovementDao = stockMovementDao;
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

    public Product createProduct(Product product, long authenticatedUserId) throws SQLException {
        if (product == null) {
            throw new IllegalArgumentException("Product must not be null.");
        }
        if (authenticatedUserId <= 0) {
            throw new IllegalArgumentException("Authenticated user ID must be positive.");
        }
        validateProductFields(product);
        normalizeProductFields(product);
        ensureCategoryExists(product.getCategoryId());
        ensureCodeIsAvailable(product.getCode(), 0);

        long originalProductId = product.getId();
        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean restoreAutoCommit = true;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                productDao.save(product, connection);

                if (product.getQuantity() > 0) {
                    StockMovement movement = new StockMovement(
                            product.getId(),
                            authenticatedUserId,
                            StockMovementType.RECEIPT,
                            product.getQuantity(),
                            0,
                            product.getQuantity(),
                            OPENING_STOCK_REASON,
                            null);
                    stockMovementDao.save(movement, connection);
                }

                connection.commit();
                return product;
            } catch (SQLException | RuntimeException | Error exception) {
                transactionFailure = exception;
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    restoreAutoCommit = false;
                    exception.addSuppressed(rollbackException);
                }
                product.setId(originalProductId);
                throw exception;
            } finally {
                if (restoreAutoCommit) {
                    try {
                        connection.setAutoCommit(originalAutoCommit);
                    } catch (SQLException restoreException) {
                        if (transactionFailure != null) {
                            transactionFailure.addSuppressed(restoreException);
                        } else {
                            throw restoreException;
                        }
                    }
                }
            }
        }
    }

    public boolean updateProduct(Product product) throws SQLException {
        if (product == null) {
            throw new IllegalArgumentException("Product must not be null.");
        }
        if (product.getId() <= 0) {
            throw new IllegalArgumentException("Product ID must be positive.");
        }
        Optional<Product> existingProduct = productDao.findById(product.getId());
        if (existingProduct.isEmpty()) {
            throw new IllegalStateException(
                    "Product with ID " + product.getId() + " does not exist.");
        }
        if (product.getQuantity() != existingProduct.get().getQuantity()) {
            throw new IllegalArgumentException(
                    "Product quantity cannot be changed through product updates. "
                            + "Use the stock movement workflow "
                            + "(StockMovementService.adjustStock) instead.");
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
