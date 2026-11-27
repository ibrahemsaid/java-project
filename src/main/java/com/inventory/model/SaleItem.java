package com.inventory.model;

import java.math.BigDecimal;

public class SaleItem {
    private long id;
    private long saleId;
    private long productId;
    private String productName;
    private String productCode;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    public SaleItem() {
    }

    public SaleItem(
            long saleId,
            long productId,
            String productName,
            String productCode,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {
        this.saleId = saleId;
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public SaleItem(
            long id,
            long saleId,
            long productId,
            String productName,
            String productCode,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {
        this.id = id;
        this.saleId = saleId;
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSaleId() {
        return saleId;
    }

    public void setSaleId(long saleId) {
        this.saleId = saleId;
    }

    public long getProductId() {
        return productId;
    }

    public void setProductId(long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    @Override
    public String toString() {
        return "SaleItem{id=" + id
                + ", saleId=" + saleId
                + ", productId=" + productId
                + ", productName='" + productName + '\''
                + ", productCode='" + productCode + '\''
                + ", quantity=" + quantity
                + ", unitPrice=" + unitPrice
                + ", subtotal=" + subtotal
                + '}';
    }
}
