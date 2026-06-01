package com.warehouse.inventory.controller;

import com.warehouse.inventory.dto.ApiResponse;
import com.warehouse.inventory.dto.ReservationRequest;
import com.warehouse.inventory.dto.ReservationResponse;
import com.warehouse.inventory.mapper.ReservationMapper;
import com.warehouse.inventory.model.Reservation;
import com.warehouse.inventory.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationMapper reservationMapper;

    public ReservationController(ReservationService reservationService, ReservationMapper reservationMapper) {
        this.reservationService = reservationService;
        this.reservationMapper = reservationMapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(@jakarta.validation.Valid @RequestBody ReservationRequest request) {
        Reservation reservation = reservationService.reserve(request);
        ReservationResponse response = reservationMapper.toResponse(reservation);
        return new ResponseEntity<>(ApiResponse.success(response), HttpStatus.CREATED);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<ReservationResponse>> confirmReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.confirm(id);
        ReservationResponse response = reservationMapper.toResponse(reservation);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.cancel(id);
        ReservationResponse response = reservationMapper.toResponse(reservation);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.getReservation(id);
        ReservationResponse response = reservationMapper.toResponse(reservation);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
