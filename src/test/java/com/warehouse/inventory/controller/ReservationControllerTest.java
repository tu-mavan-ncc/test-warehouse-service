package com.warehouse.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.inventory.dto.ReservationItemDto;
import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.dto.ReservationResponse;
import com.warehouse.inventory.mapper.ReservationMapper;
import com.warehouse.inventory.model.Reservation;
import com.warehouse.inventory.model.ReservationStatus;
import com.warehouse.inventory.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private ReservationMapper reservationMapper;

    @Test
    void testCreateReservationSuccess() throws Exception {
        // Arrange
        ReservationRequest request = new ReservationRequest("ORD-123", List.of(new ReservationItemDto("SKU-1", 5)));
        Reservation mockReservation = new Reservation();
        ReservationResponse mockResponse = new ReservationResponse(UUID.randomUUID(), "ORD-123", ReservationStatus.PENDING, LocalDateTime.now(), List.of());

        when(reservationService.reserve(any(ReservationRequest.class))).thenReturn(mockReservation);
        when(reservationMapper.toResponse(any(Reservation.class))).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value("ORD-123"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void testCreateReservationBlankOrderId() throws Exception {
        // Arrange
        ReservationRequest request = new ReservationRequest("  ", List.of(new ReservationItemDto("SKU-1", 5)));

        // Act & Assert
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("orderId: Order ID must not be blank"));
    }

    @Test
    void testCreateReservationEmptyItems() throws Exception {
        // Arrange
        ReservationRequest request = new ReservationRequest("ORD-123", List.of());

        // Act & Assert
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
