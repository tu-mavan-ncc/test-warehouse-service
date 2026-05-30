package com.warehouse.inventory.service.state;

import com.warehouse.inventory.model.Inventory;
import com.warehouse.inventory.model.Reservation;

import java.util.List;

public interface ReservationState {
    void confirm(Reservation reservation, List<Inventory> inventories);
    void cancel(Reservation reservation, List<Inventory> inventories);
}
