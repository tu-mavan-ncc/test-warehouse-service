package com.warehouse.inventory.service;

import com.warehouse.inventory.model.Inventory;
import com.warehouse.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Inventory> getInventory(String sku) {
        return inventoryRepository.findById(sku);
    }
}
