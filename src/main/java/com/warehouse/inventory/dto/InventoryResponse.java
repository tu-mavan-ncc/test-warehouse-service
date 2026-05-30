package com.warehouse.inventory.dto;

public record InventoryResponse(String sku, int totalStock, int availableStock, int reservedStock) {}
