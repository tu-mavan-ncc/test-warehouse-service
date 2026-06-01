package com.warehouse.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReservationItemDto(
        @NotBlank(message = "SKU must not be blank") String sku,
        @Min(value = 1, message = "Quantity must be greater than 0") int quantity
) {}
