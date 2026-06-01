-- liquibase formatted sql

-- changeset developer:1
CREATE TABLE products (
    sku VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT
);

CREATE TABLE inventory (
    sku VARCHAR(50) PRIMARY KEY REFERENCES products(sku),
    total_stock INT NOT NULL DEFAULT 0,
    available_stock INT NOT NULL DEFAULT 0,
    reserved_stock INT NOT NULL DEFAULT 0,
    CONSTRAINT check_total_stock CHECK (total_stock >= 0),
    CONSTRAINT check_available_stock CHECK (available_stock >= 0),
    CONSTRAINT check_reserved_stock CHECK (reserved_stock >= 0)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE reservation_items (
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    sku VARCHAR(50) NOT NULL REFERENCES products(sku),
    quantity INT NOT NULL,
    PRIMARY KEY (reservation_id, sku),
    CONSTRAINT check_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_reservations_order_id ON reservations(order_id);
CREATE INDEX idx_reservations_status ON reservations(status);

