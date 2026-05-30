package com.warehouse.inventory.controller;

import com.warehouse.inventory.dto.ApiResponse;
import com.warehouse.inventory.dto.InventoryResponse;
import com.warehouse.inventory.exception.InventoryNotFoundException;
import com.warehouse.inventory.model.Inventory;
import com.warehouse.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getStock(@PathVariable String sku) {
        Inventory inventory = inventoryService.getInventory(sku)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for SKU: " + sku));

        InventoryResponse response = new InventoryResponse(
                inventory.getSku(),
                inventory.getTotalStock(),
                inventory.getAvailableStock(),
                inventory.getReservedStock()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
