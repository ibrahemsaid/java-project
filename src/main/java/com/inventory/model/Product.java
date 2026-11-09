package com.inventory.model;

import java.math.BigDecimal;

public class Product {
    private long id;
    private String name;
    private String code;
    private long categoryId;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private int quantity;
    private int minimumStock;

    public Product() {
    }

    public Product(
            String name,
            String code,
            long categoryId,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            int quantity,
            int minimumStock) {
        this.name = name;
        this.code = code;
        this.categoryId = categoryId;
        this.purchasePrice = purchasePrice;
        this.sellingPrice = sellingPrice;
        this.quantity = quantity;
        this.minimumStock = minimumStock;
    }

    public Product(
            long id,
            String name,
            String code,
            long categoryId,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            int quantity,
            int minimumStock) {
        this(name, code, categoryId, purchasePrice, sellingPrice, quantity, minimumStock);
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(long categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(int minimumStock) {
        this.minimumStock = minimumStock;
    }

    @Override
    public String toString() {
        return "Product{id=" + id
                + ", name='" + name + '\''
                + ", code='" + code + '\''
                + ", categoryId=" + categoryId
                + ", purchasePrice=" + purchasePrice
                + ", sellingPrice=" + sellingPrice
                + ", quantity=" + quantity
                + ", minimumStock=" + minimumStock
                + '}';
    }
}
