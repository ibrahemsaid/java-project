package com.inventory.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class Sale {
    private long id;
    private long userId;
    private LocalDateTime saleDate;
    private BigDecimal totalAmount;

    public Sale() {
    }

    public Sale(long userId, BigDecimal totalAmount) {
        this.userId = userId;
        this.totalAmount = totalAmount;
    }

    public Sale(
            long userId,
            LocalDateTime saleDate,
            BigDecimal totalAmount) {
        this.userId = userId;
        this.saleDate = saleDate;
        this.totalAmount = totalAmount;
    }

    public Sale(
            long id,
            long userId,
            LocalDateTime saleDate,
            BigDecimal totalAmount) {
        this.id = id;
        this.userId = userId;
        this.saleDate = saleDate;
        this.totalAmount = totalAmount;
    }

    public Sale(
            long id,
            long userId,
            Timestamp saleDate,
            BigDecimal totalAmount) {
        this(id, userId, saleDate == null ? null : saleDate.toLocalDateTime(), totalAmount);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public LocalDateTime getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(LocalDateTime saleDate) {
        this.saleDate = saleDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public String toString() {
        return "Sale{id=" + id
                + ", userId=" + userId
                + ", saleDate=" + saleDate
                + ", totalAmount=" + totalAmount
                + '}';
    }
}
