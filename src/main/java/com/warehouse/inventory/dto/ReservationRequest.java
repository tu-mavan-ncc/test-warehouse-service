package com.warehouse.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReservationRequest(
        @NotBlank(message = "Order ID must not be blank")
        String orderId,
        @NotEmpty(message = "Reservation request must contain at least one item") 
        @Valid 
        List<ReservationItemDto> items
) {}
