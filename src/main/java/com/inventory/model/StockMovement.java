package com.inventory.model;

import java.time.LocalDateTime;

public class StockMovement {
    private long id;
    private long productId;
    private long userId;
    private StockMovementType movementType;
    private int quantityChange;
    private int quantityBefore;
    private int quantityAfter;
    private String reason;
    private Long referenceSaleId;
    private LocalDateTime createdAt;

    public StockMovement() {
    }

    public StockMovement(
            long productId,
            long userId,
            StockMovementType movementType,
            int quantityChange,
            int quantityBefore,
            int quantityAfter,
            String reason,
            Long referenceSaleId) {
        this.productId = productId;
        this.userId = userId;
        this.movementType = movementType;
        this.quantityChange = quantityChange;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.reason = reason;
        this.referenceSaleId = referenceSaleId;
    }

    public StockMovement(
            long id,
            long productId,
            long userId,
            StockMovementType movementType,
            int quantityChange,
            int quantityBefore,
            int quantityAfter,
            String reason,
            Long referenceSaleId,
            LocalDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.userId = userId;
        this.movementType = movementType;
        this.quantityChange = quantityChange;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.reason = reason;
        this.referenceSaleId = referenceSaleId;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getProductId() {
        return productId;
    }

    public void setProductId(long productId) {
        this.productId = productId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(StockMovementType movementType) {
        this.movementType = movementType;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public int getQuantityBefore() {
        return quantityBefore;
    }

    public void setQuantityBefore(int quantityBefore) {
        this.quantityBefore = quantityBefore;
    }

    public int getQuantityAfter() {
        return quantityAfter;
    }

    public void setQuantityAfter(int quantityAfter) {
        this.quantityAfter = quantityAfter;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getReferenceSaleId() {
        return referenceSaleId;
    }

    public void setReferenceSaleId(Long referenceSaleId) {
        this.referenceSaleId = referenceSaleId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "StockMovement{id=" + id
                + ", productId=" + productId
                + ", userId=" + userId
                + ", movementType=" + movementType
                + ", quantityChange=" + quantityChange
                + ", quantityBefore=" + quantityBefore
                + ", quantityAfter=" + quantityAfter
                + ", reason='" + reason + '\''
                + ", referenceSaleId=" + referenceSaleId
                + ", createdAt=" + createdAt
                + '}';
    }
}
