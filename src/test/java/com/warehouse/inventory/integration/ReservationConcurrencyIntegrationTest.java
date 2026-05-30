package com.warehouse.inventory.integration;

import com.warehouse.inventory.dto.ApiResponse;
import com.warehouse.inventory.dto.ReservationItemDto;
import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.dto.ReservationResponse;
import com.warehouse.inventory.model.Inventory;
import com.warehouse.inventory.repository.InventoryRepository;
import com.warehouse.inventory.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReservationConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void cleanDb() {
        reservationRepository.deleteAll();
        // Reset inventory for testing (SKU C300 starts with total=10, available=10, reserved=0)
        Inventory inv = inventoryRepository.findById("C300").orElseThrow();
        inv.setTotalStock(10);
        inv.setAvailableStock(10);
        inv.setReservedStock(0);
        inventoryRepository.save(inv);
    }

    @Test
    void testConcurrentReservations() throws Exception {
        // Initial stock for C300 is 10
        // We submit two requests concurrently, each trying to reserve 7 items of SKU C300 (total = 14 > 10).
        // Exactly one should succeed, and one should fail with INSUFFICIENT_STOCK.

        String url = "http://localhost:" + port + "/api/v1/reservations";
        ReservationRequest req1 = new ReservationRequest("ORD-CONC-1", List.of(new ReservationItemDto("C300", 7)));
        ReservationRequest req2 = new ReservationRequest("ORD-CONC-2", List.of(new ReservationItemDto("C300", 7)));

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CompletableFuture<ResponseEntity<ApiResponse<ReservationResponse>>> future1 = CompletableFuture.supplyAsync(
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<>(req1),
                        new ParameterizedTypeReference<ApiResponse<ReservationResponse>>() {}
                ), executor
        );

        CompletableFuture<ResponseEntity<ApiResponse<ReservationResponse>>> future2 = CompletableFuture.supplyAsync(
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<>(req2),
                        new ParameterizedTypeReference<ApiResponse<ReservationResponse>>() {}
                ), executor
        );

        CompletableFuture.allOf(future1, future2).join();

        ResponseEntity<ApiResponse<ReservationResponse>> res1 = future1.get();
        ResponseEntity<ApiResponse<ReservationResponse>> res2 = future2.get();

        executor.shutdown();

        // Check response status codes and bodies
        int successCount = 0;
        int failureCount = 0;

        if (res1.getStatusCode() == HttpStatus.CREATED) {
            successCount++;
            assertNotNull(res1.getBody());
            assertNotNull(res1.getBody().data());
            assertNull(res1.getBody().error());
        } else if (res1.getStatusCode() == HttpStatus.BAD_REQUEST) {
            failureCount++;
            assertNotNull(res1.getBody());
            assertNull(res1.getBody().data());
            assertEquals("INSUFFICIENT_STOCK", res1.getBody().error().code());
        }

        if (res2.getStatusCode() == HttpStatus.CREATED) {
            successCount++;
            assertNotNull(res2.getBody());
            assertNotNull(res2.getBody().data());
            assertNull(res2.getBody().error());
        } else if (res2.getStatusCode() == HttpStatus.BAD_REQUEST) {
            failureCount++;
            assertNotNull(res2.getBody());
            assertNull(res2.getBody().data());
            assertEquals("INSUFFICIENT_STOCK", res2.getBody().error().code());
        }

        assertEquals(1, successCount, "Exactly one request must succeed");
        assertEquals(1, failureCount, "Exactly one request must be rejected");

        // Verify database stock
        Inventory finalInv = inventoryRepository.findById("C300").orElseThrow();
        assertEquals(10, finalInv.getTotalStock());
        assertEquals(3, finalInv.getAvailableStock()); // 10 - 7
        assertEquals(7, finalInv.getReservedStock()); // 7 reserved
    }

    @Test
    void testReserveValidationIntegration() {
        String url = "http://localhost:" + port + "/api/v1/reservations";
        
        // Zero quantity should return BAD_REQUEST
        ReservationRequest zeroQtyReq = new ReservationRequest("ORD-VAL-1", List.of(new ReservationItemDto("C300", 0)));
        ResponseEntity<ApiResponse<ReservationResponse>> resZero = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(zeroQtyReq),
                new ParameterizedTypeReference<ApiResponse<ReservationResponse>>() {}
        );
        assertEquals(HttpStatus.BAD_REQUEST, resZero.getStatusCode());
        assertNotNull(resZero.getBody());
        assertNotNull(resZero.getBody().error());
        assertEquals("BAD_REQUEST", resZero.getBody().error().code());
        assertEquals("Quantity must be greater than 0", resZero.getBody().error().message());

        // Blank SKU should return BAD_REQUEST
        ReservationRequest blankSkuReq = new ReservationRequest("ORD-VAL-2", List.of(new ReservationItemDto("  ", 5)));
        ResponseEntity<ApiResponse<ReservationResponse>> resBlank = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(blankSkuReq),
                new ParameterizedTypeReference<ApiResponse<ReservationResponse>>() {}
        );
        assertEquals(HttpStatus.BAD_REQUEST, resBlank.getStatusCode());
        assertNotNull(resBlank.getBody());
        assertNotNull(resBlank.getBody().error());
        assertEquals("BAD_REQUEST", resBlank.getBody().error().code());
        assertEquals("SKU must not be blank", resBlank.getBody().error().message());
    }
}
