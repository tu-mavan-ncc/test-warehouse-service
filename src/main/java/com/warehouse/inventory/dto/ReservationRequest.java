package com.warehouse.inventory.dto;

import java.util.List;

public record ReservationRequest(String orderId, List<ReservationItemDto> items) {}
