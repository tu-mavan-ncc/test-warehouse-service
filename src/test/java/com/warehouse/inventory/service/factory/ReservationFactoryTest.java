package com.warehouse.inventory.service.factory;

import com.warehouse.inventory.dto.ReservationItemDto;
import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.model.Reservation;
import com.warehouse.inventory.model.ReservationItem;
import com.warehouse.inventory.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationFactoryTest {

    private final ReservationFactory reservationFactory = new ReservationFactoryImpl();

    @Test
    void testCreatePendingReservation() {
        // Arrange
        String orderId = "ORD-TEST-123";
        ReservationRequest request = new ReservationRequest(orderId, List.of(
                new ReservationItemDto("SKU-1", 5),
                new ReservationItemDto("SKU-2", 10)
        ));

        // Act
        Reservation reservation = reservationFactory.createPendingReservation(request);

        // Assert
        assertNotNull(reservation);
        assertNotNull(reservation.getId(), "Reservation ID should be generated");
        assertEquals(orderId, reservation.getOrderId());
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        assertNotNull(reservation.getCreatedAt(), "CreatedAt timestamp should be generated");

        List<ReservationItem> items = reservation.getItems();
        assertNotNull(items);
        assertEquals(2, items.size());

        // Assert item 1
        assertEquals("SKU-1", items.get(0).getSku());
        assertEquals(5, items.get(0).getQuantity());
        assertEquals(reservation, items.get(0).getReservation());

        // Assert item 2
        assertEquals("SKU-2", items.get(1).getSku());
        assertEquals(10, items.get(1).getQuantity());
        assertEquals(reservation, items.get(1).getReservation());
    }
}
