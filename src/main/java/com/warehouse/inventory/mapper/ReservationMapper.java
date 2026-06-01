package com.warehouse.inventory.mapper;

import com.warehouse.inventory.dto.ReservationItemResponse;
import com.warehouse.inventory.dto.ReservationResponse;
import com.warehouse.inventory.model.Reservation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReservationMapper {

    public ReservationResponse toResponse(Reservation reservation) {
        if (reservation == null) {
            return null;
        }

        List<ReservationItemResponse> itemResponses = reservation.getItems().stream()
                .map(item -> new ReservationItemResponse(item.getSku(), item.getQuantity()))
                .collect(Collectors.toList());

        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                itemResponses
        );
    }
}
