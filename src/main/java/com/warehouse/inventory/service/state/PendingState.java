package com.warehouse.inventory.service.state;

import com.warehouse.inventory.model.Inventory;
import com.warehouse.inventory.model.Reservation;
import com.warehouse.inventory.model.ReservationItem;
import com.warehouse.inventory.model.ReservationStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PendingState implements ReservationState {

    @Override
    public void confirm(Reservation reservation, List<Inventory> inventories) {
        reservation.setStatus(ReservationStatus.CONFIRMED);

        Map<String, Inventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(Inventory::getSku, i -> i));

        for (ReservationItem item : reservation.getItems()) {
            Inventory inventory = inventoryMap.get(item.getSku());
            if (inventory != null) {
                inventory.setTotalStock(inventory.getTotalStock() - item.getQuantity());
                inventory.setReservedStock(inventory.getReservedStock() - item.getQuantity());
            }
        }
    }

    @Override
    public void cancel(Reservation reservation, List<Inventory> inventories) {
        reservation.setStatus(ReservationStatus.CANCELLED);

        Map<String, Inventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(Inventory::getSku, i -> i));

        for (ReservationItem item : reservation.getItems()) {
            Inventory inventory = inventoryMap.get(item.getSku());
            if (inventory != null) {
                inventory.setAvailableStock(inventory.getAvailableStock() + item.getQuantity());
                inventory.setReservedStock(inventory.getReservedStock() - item.getQuantity());
            }
        }
    }
}
