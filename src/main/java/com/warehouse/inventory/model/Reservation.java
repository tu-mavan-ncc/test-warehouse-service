package com.warehouse.inventory.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.warehouse.inventory.service.state.CancelledState;
import com.warehouse.inventory.service.state.ConfirmedState;
import com.warehouse.inventory.service.state.PendingState;
import com.warehouse.inventory.service.state.ReservationState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<ReservationItem> items = new ArrayList<>();

    private static final ReservationState PENDING_STATE = new PendingState();
    private static final ReservationState CONFIRMED_STATE = new ConfirmedState();
    private static final ReservationState CANCELLED_STATE = new CancelledState();

    public ReservationState getReservationState() {
        if (status == null) return null;
        switch (status) {
            case PENDING: return PENDING_STATE;
            case CONFIRMED: return CONFIRMED_STATE;
            case CANCELLED: return CANCELLED_STATE;
            default: throw new IllegalArgumentException("Unknown status: " + status);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reservation that = (Reservation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
