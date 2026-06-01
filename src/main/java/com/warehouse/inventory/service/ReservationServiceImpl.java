package com.warehouse.inventory.service;

import com.warehouse.inventory.dto.ReservationItemDto;
import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.exception.InsufficientStockException;
import com.warehouse.inventory.exception.ReservationNotFoundException;
import com.warehouse.inventory.model.Inventory;
import com.warehouse.inventory.model.Reservation;
import com.warehouse.inventory.model.ReservationItem;
import com.warehouse.inventory.repository.InventoryRepository;
import com.warehouse.inventory.repository.ReservationRepository;
import com.warehouse.inventory.service.factory.ReservationFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final ReservationFactory reservationFactory;

    public ReservationServiceImpl(ReservationRepository reservationRepository, InventoryRepository inventoryRepository, ReservationFactory reservationFactory) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationFactory = reservationFactory;
    }

    @Transactional
    public Reservation reserve(ReservationRequest request) {
        if (reservationRepository.existsByOrderId(request.orderId())) {
            throw new IllegalArgumentException("Order ID already exists: " + request.orderId());
        }

        java.util.Set<String> seenSkus = new java.util.HashSet<>();
        for (ReservationItemDto item : request.items()) {
            if (!seenSkus.add(item.sku())) {
                throw new IllegalArgumentException("Duplicate SKU found in request: " + item.sku());
            }
        }

        // Get unique SKUs and sort them to prevent deadlocks
        List<String> sortedSkus = request.items().stream()
                .map(ReservationItemDto::sku)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Load and lock inventory records in sorted SKU order
        List<Inventory> lockedInventories = inventoryRepository.findBySkuInForUpdate(sortedSkus);
        Map<String, Inventory> inventoryMap = lockedInventories.stream()
                .collect(Collectors.toMap(Inventory::getSku, i -> i));

        // Validate stock availability
        for (ReservationItemDto item : request.items()) {
            String sku = item.sku();
            int qty = item.quantity();

            Inventory inventory = inventoryMap.get(sku);
            if (inventory == null) {
                throw new IllegalArgumentException("SKU " + sku + " does not exist in inventory");
            }

            if (inventory.getAvailableStock() < qty) {
                throw new InsufficientStockException(sku, inventory.getAvailableStock(), qty);
            }
        }

        // Deduct available stock and add to reserved stock
        for (ReservationItemDto item : request.items()) {
            String sku = item.sku();
            int qty = item.quantity();
            Inventory inventory = inventoryMap.get(sku);

            inventory.setAvailableStock(inventory.getAvailableStock() - qty);
            inventory.setReservedStock(inventory.getReservedStock() + qty);
            inventoryRepository.save(inventory);
        }

        // Create reservation using the injected Factory
        Reservation reservation = reservationFactory.createPendingReservation(request);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation confirm(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId));

        // Get unique SKUs in reservation and sort them to lock in order
        List<String> sortedSkus = reservation.getItems().stream()
                .map(ReservationItem::getSku)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<Inventory> lockedInventories = inventoryRepository.findBySkuInForUpdate(sortedSkus);

        // State Pattern: Delegate state transition logic to the current state object
        reservation.getReservationState().confirm(reservation, lockedInventories);

        // Persist updates
        inventoryRepository.saveAll(lockedInventories);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation cancel(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId));

        // Get unique SKUs in reservation and sort them to lock in order
        List<String> sortedSkus = reservation.getItems().stream()
                .map(ReservationItem::getSku)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<Inventory> lockedInventories = inventoryRepository.findBySkuInForUpdate(sortedSkus);

        // State Pattern: Delegate state transition logic to the current state object
        reservation.getReservationState().cancel(reservation, lockedInventories);

        // Persist updates
        inventoryRepository.saveAll(lockedInventories);
        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + id));
    }
}
