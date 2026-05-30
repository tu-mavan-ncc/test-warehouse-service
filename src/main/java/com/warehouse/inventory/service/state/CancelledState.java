package com.warehouse.inventory.service.state;

import com.warehouse.inventory.exception.InvalidStateTransitionException;
import com.warehouse.inventory.model.Inventory;
import com.warehouse.inventory.model.Reservation;

import java.util.List;

public class CancelledState implements ReservationState {

    @Override
    public void confirm(Reservation reservation, List<Inventory> inventories) {
        throw new InvalidStateTransitionException("A CANCELLED reservation cannot be confirmed");
    }

    @Override
    public void cancel(Reservation reservation, List<Inventory> inventories) {
        throw new InvalidStateTransitionException("Reservation is already CANCELLED");
    }
}
