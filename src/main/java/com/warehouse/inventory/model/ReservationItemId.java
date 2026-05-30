package com.warehouse.inventory.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ReservationItemId implements Serializable {
    private UUID reservation;
    private String sku;

    public ReservationItemId() {}

    public ReservationItemId(UUID reservation, String sku) {
        this.reservation = reservation;
        this.sku = sku;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReservationItemId that = (ReservationItemId) o;
        return Objects.equals(reservation, that.reservation) && Objects.equals(sku, that.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservation, sku);
    }
}
