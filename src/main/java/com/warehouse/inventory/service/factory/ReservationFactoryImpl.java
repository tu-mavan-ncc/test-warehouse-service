package com.warehouse.inventory.service.factory;

import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.model.Reservation;
import com.warehouse.inventory.model.ReservationItem;
import com.warehouse.inventory.model.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ReservationFactoryImpl implements ReservationFactory {

    @Override
    public Reservation createPendingReservation(ReservationRequest request) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setOrderId(request.orderId());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCreatedAt(LocalDateTime.now());

        List<ReservationItem> items = request.items().stream()
                .map(itemDto -> ReservationItem.builder()
                        .reservation(reservation)
                        .sku(itemDto.sku())
                        .quantity(itemDto.quantity())
                        .build())
                .collect(Collectors.toList());

        reservation.setItems(items);
        return reservation;
    }
}
