package com.warehouse.inventory.service;

import com.warehouse.inventory.dto.ReservationItemDto;
import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.exception.InsufficientStockException;
import com.warehouse.inventory.exception.InvalidStateTransitionException;
import com.warehouse.inventory.exception.ReservationNotFoundException;
import com.warehouse.inventory.model.Inventory;
import com.warehouse.inventory.model.Reservation;
import com.warehouse.inventory.model.ReservationItem;
import com.warehouse.inventory.model.ReservationStatus;
import com.warehouse.inventory.repository.InventoryRepository;
import com.warehouse.inventory.repository.ReservationRepository;
import com.warehouse.inventory.service.factory.ReservationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReservationFactory reservationFactory;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private Inventory inventoryA;
    private Inventory inventoryB;

    @BeforeEach
    void setUp() {
        inventoryA = Inventory.builder()
                .sku("A100")
                .totalStock(100)
                .availableStock(100)
                .reservedStock(0)
                .build();

        inventoryB = Inventory.builder()
                .sku("B200")
                .totalStock(50)
                .availableStock(50)
                .reservedStock(0)
                .build();
    }

    @Test
    void testReserveSuccess() {
        // Arrange
        ReservationRequest request = new ReservationRequest("ORD-1001", List.of(
            new ReservationItemDto("A100", 5),
            new ReservationItemDto("B200", 10)
        ));

        when(inventoryRepository.findBySkuInForUpdate(List.of("A100", "B200")))
            .thenReturn(List.of(inventoryA, inventoryB));

        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(reservationFactory.createPendingReservation(any(ReservationRequest.class)))
                .thenAnswer(invocation -> {
                    ReservationRequest req = invocation.getArgument(0);
                    Reservation r = new Reservation();
                    r.setId(UUID.randomUUID());
                    r.setOrderId(req.orderId());
                    r.setStatus(ReservationStatus.PENDING);
                    List<ReservationItem> items = req.items().stream()
                        .map(dto -> ReservationItem.builder().reservation(r).sku(dto.sku()).quantity(dto.quantity()).build())
                        .toList();
                    r.setItems(items);
                    return r;
                });

        // Act
        Reservation reservation = reservationService.reserve(request);

        // Assert
        assertNotNull(reservation);
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        assertEquals("ORD-1001", reservation.getOrderId());
        assertEquals(2, reservation.getItems().size());

        // Check stock changes
        assertEquals(95, inventoryA.getAvailableStock());
        assertEquals(5, inventoryA.getReservedStock());
        assertEquals(40, inventoryB.getAvailableStock());
        assertEquals(10, inventoryB.getReservedStock());

        verify(inventoryRepository, times(1)).save(inventoryA);
        verify(inventoryRepository, times(1)).save(inventoryB);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    @Test
    void testReserveDuplicateOrderIdReject() {
        // Arrange
        ReservationRequest request = new ReservationRequest("ORD-1001", List.of(
            new ReservationItemDto("A100", 5)
        ));

        when(reservationRepository.existsByOrderId("ORD-1001")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            reservationService.reserve(request);
        });

        assertEquals("Order ID already exists: ORD-1001", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void testReserveInsufficientStockReject() {
        // Arrange
        ReservationRequest request = new ReservationRequest("ORD-1001", List.of(
            new ReservationItemDto("A100", 150) // more than 100 available
        ));

        when(inventoryRepository.findBySkuInForUpdate(List.of("A100")))
            .thenReturn(List.of(inventoryA));

        // Act & Assert
        InsufficientStockException exception = assertThrows(InsufficientStockException.class, () -> {
            reservationService.reserve(request);
        });

        assertTrue(exception.getMessage().contains("SKU A100 has only 100 units available, 150 were requested"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }


    @Test
    void testConfirmPendingReservationSuccess() {
        // Arrange
        UUID resId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(resId)
                .orderId("ORD-1001")
                .status(ReservationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        
        ReservationItem item = ReservationItem.builder()
                .reservation(reservation)
                .sku("A100")
                .quantity(10)
                .build();
        reservation.setItems(List.of(item));

        // Set state to match reserving
        inventoryA.setAvailableStock(90);
        inventoryA.setReservedStock(10);

        when(reservationRepository.findByIdForUpdate(resId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findBySkuInForUpdate(List.of("A100"))).thenReturn(List.of(inventoryA));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Reservation result = reservationService.confirm(resId);

        // Assert
        assertEquals(ReservationStatus.CONFIRMED, result.getStatus());
        assertEquals(90, inventoryA.getTotalStock()); // reduced
        assertEquals(90, inventoryA.getAvailableStock()); // unchanged during confirmation (already reduced)
        assertEquals(0, inventoryA.getReservedStock()); // cleared

        verify(inventoryRepository, times(1)).saveAll(any());
        verify(reservationRepository, times(1)).save(reservation);
    }

    @Test
    void testCancelPendingReservationSuccess() {
        // Arrange
        UUID resId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(resId)
                .orderId("ORD-1001")
                .status(ReservationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        ReservationItem item = ReservationItem.builder()
                .reservation(reservation)
                .sku("A100")
                .quantity(10)
                .build();
        reservation.setItems(List.of(item));

        // Set state to match reserving
        inventoryA.setAvailableStock(90);
        inventoryA.setReservedStock(10);

        when(reservationRepository.findByIdForUpdate(resId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findBySkuInForUpdate(List.of("A100"))).thenReturn(List.of(inventoryA));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Reservation result = reservationService.cancel(resId);

        // Assert
        assertEquals(ReservationStatus.CANCELLED, result.getStatus());
        assertEquals(100, inventoryA.getTotalStock()); // unchanged
        assertEquals(100, inventoryA.getAvailableStock()); // returned
        assertEquals(0, inventoryA.getReservedStock()); // cleared

        verify(inventoryRepository, times(1)).saveAll(any());
        verify(reservationRepository, times(1)).save(reservation);
    }

    @Test
    void testConfirmAlreadyConfirmedThrowsException() {
        // Arrange
        UUID resId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(resId)
                .orderId("ORD-1001")
                .status(ReservationStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();

        when(reservationRepository.findByIdForUpdate(resId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findBySkuInForUpdate(anyList())).thenReturn(Collections.emptyList());

        // Act & Assert
        InvalidStateTransitionException exception = assertThrows(InvalidStateTransitionException.class, () -> {
            reservationService.confirm(resId);
        });

        assertEquals("Reservation is already CONFIRMED", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void testCancelAlreadyConfirmedThrowsException() {
        // Arrange
        UUID resId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(resId)
                .orderId("ORD-1001")
                .status(ReservationStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();

        when(reservationRepository.findByIdForUpdate(resId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findBySkuInForUpdate(anyList())).thenReturn(Collections.emptyList());

        // Act & Assert
        InvalidStateTransitionException exception = assertThrows(InvalidStateTransitionException.class, () -> {
            reservationService.cancel(resId);
        });

        assertEquals("A CONFIRMED reservation cannot be cancelled", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void testConfirmAlreadyCancelledThrowsException() {
        // Arrange
        UUID resId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(resId)
                .orderId("ORD-1001")
                .status(ReservationStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .build();

        when(reservationRepository.findByIdForUpdate(resId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findBySkuInForUpdate(anyList())).thenReturn(Collections.emptyList());

        // Act & Assert
        InvalidStateTransitionException exception = assertThrows(InvalidStateTransitionException.class, () -> {
            reservationService.confirm(resId);
        });

        assertEquals("A CANCELLED reservation cannot be confirmed", exception.getMessage());
    }

    @Test
    void testCancelAlreadyCancelledThrowsException() {
        // Arrange
        UUID resId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(resId)
                .orderId("ORD-1001")
                .status(ReservationStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .build();

        when(reservationRepository.findByIdForUpdate(resId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findBySkuInForUpdate(anyList())).thenReturn(Collections.emptyList());

        // Act & Assert
        InvalidStateTransitionException exception = assertThrows(InvalidStateTransitionException.class, () -> {
            reservationService.cancel(resId);
        });

        assertEquals("Reservation is already CANCELLED", exception.getMessage());
    }
}
