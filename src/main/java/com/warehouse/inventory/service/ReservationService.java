package com.warehouse.inventory.service;

import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.model.Reservation;

import java.util.UUID;

public interface ReservationService {
    Reservation reserve(ReservationRequest request);
    Reservation confirm(UUID reservationId);
    Reservation cancel(UUID reservationId);
    Reservation getReservation(UUID id);
}
