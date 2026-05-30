package com.warehouse.inventory.dto;

import com.warehouse.inventory.model.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String orderId,
        ReservationStatus status,
        LocalDateTime createdAt,
        List<ReservationItemResponse> items
) {}
