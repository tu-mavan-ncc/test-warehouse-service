package com.warehouse.inventory.exception;

public class InsufficientStockException extends RuntimeException {
    
    private final String sku;
    private final int available;
    private final int requested;

    public InsufficientStockException(String sku, int available, int requested) {
        super(String.format("SKU %s has only %d units available, %d were requested", sku, available, requested));
        this.sku = sku;
        this.available = available;
        this.requested = requested;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}
