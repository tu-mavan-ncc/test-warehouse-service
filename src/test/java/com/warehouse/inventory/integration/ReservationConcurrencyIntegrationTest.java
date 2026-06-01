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
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<ResponseEntity<ApiResponse<ReservationResponse>>>> futures = new java.util.ArrayList<>();

        // Act: 10 threads try to reserve the same 7 items
        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            futures.add(CompletableFuture.supplyAsync(
                () -> restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(new ReservationRequest("ORD-CONC-" + index, List.of(new ReservationItemDto("C300", 7)))),
                    new ParameterizedTypeReference<ApiResponse<ReservationResponse>>() {}
                ), executor
            ));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        executor.shutdown();

        // Check response status codes and bodies
        long successCount = futures.stream()
                .map(CompletableFuture::join)
                .filter(res -> res.getStatusCode() == HttpStatus.CREATED)
                .count();

        long failureCount = futures.stream()
                .map(CompletableFuture::join)
                .filter(res -> res.getStatusCode() == HttpStatus.BAD_REQUEST || res.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                .count();

        assertEquals(1, successCount, "Exactly one request must succeed");
        assertEquals(numberOfThreads - 1, failureCount, "Other requests must be rejected");

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
        assertEquals("items[0].quantity: Quantity must be greater than 0", resZero.getBody().error().message());

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
        assertEquals("items[0].sku: SKU must not be blank", resBlank.getBody().error().message());
        
        // Blank OrderId should return BAD_REQUEST
        ReservationRequest blankOrderIdReq = new ReservationRequest(" ", List.of(new ReservationItemDto("C300", 5)));
        ResponseEntity<ApiResponse<ReservationResponse>> resOrderId = restTemplate.exchange(
            url,
            HttpMethod.POST,
            new HttpEntity<>(blankOrderIdReq),
            new ParameterizedTypeReference<ApiResponse<ReservationResponse>>() {}
        );
        assertEquals(HttpStatus.BAD_REQUEST, resOrderId.getStatusCode());
        assertEquals("BAD_REQUEST", resOrderId.getBody().error().code());
        assertTrue(resOrderId.getBody().error().message().contains("Order ID must not be blank"));
    }

    @Test
    void testDuplicateOrderIdReject() {
        String url = "http://localhost:" + port + "/api/v1/reservations";
        ReservationRequest req = new ReservationRequest("ORD-DUP-1", List.of(new ReservationItemDto("C300", 2)));

        // First request should succeed
        ResponseEntity<ApiResponse<ReservationResponse>> res1 = restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>(req), new ParameterizedTypeReference<ApiResponse<ReservationResponse>>() {}
        );
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Second identical request should fail with BAD_REQUEST due to existsByOrderId check
        ResponseEntity<ApiResponse<ReservationResponse>> res2 = restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>(req), new ParameterizedTypeReference<ApiResponse<ReservationResponse>>() {}
        );
        assertEquals(HttpStatus.BAD_REQUEST, res2.getStatusCode());
        assertEquals("BAD_REQUEST", res2.getBody().error().code());
        assertEquals("Order ID already exists: ORD-DUP-1", res2.getBody().error().message());
    }
}
