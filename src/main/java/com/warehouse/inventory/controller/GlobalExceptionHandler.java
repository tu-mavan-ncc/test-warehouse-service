package com.warehouse.inventory.controller;

import com.warehouse.inventory.dto.ApiResponse;
import com.warehouse.inventory.exception.InsufficientStockException;
import com.warehouse.inventory.exception.InventoryNotFoundException;
import com.warehouse.inventory.exception.InvalidStateTransitionException;
import com.warehouse.inventory.exception.ReservationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientStock(InsufficientStockException ex) {
        log.warn("Business error (INSUFFICIENT_STOCK): {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("INSUFFICIENT_STOCK", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReservationNotFound(ReservationNotFoundException ex) {
        log.warn("Business error (RESERVATION_NOT_FOUND): {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("RESERVATION_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInventoryNotFound(InventoryNotFoundException ex) {
        log.warn("Business error (INVENTORY_NOT_FOUND): {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("INVENTORY_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStateTransition(InvalidStateTransitionException ex) {
        log.warn("Business error (INVALID_STATE_TRANSITION): {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("INVALID_STATE_TRANSITION", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Business error (BAD_REQUEST): {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("BAD_REQUEST", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", errorMsg);
        ApiResponse<Void> response = ApiResponse.error("BAD_REQUEST", errorMsg);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Database constraint violation: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("CONFLICT", "The request conflicts with existing data (e.g. duplicate Order ID).");
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("BAD_REQUEST", "Malformed JSON request");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("An unexpected error occurred", ex);
        ApiResponse<Void> response = ApiResponse.error("INTERNAL_SERVER_ERROR", "An unexpected internal error occurred.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
