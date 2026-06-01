package com.warehouse.inventory.service.factory;

import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.model.Reservation;

public interface ReservationFactory {
    Reservation createPendingReservation(ReservationRequest request);
}
