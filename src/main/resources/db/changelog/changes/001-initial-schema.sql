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
    order_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservation_items (
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    sku VARCHAR(50) NOT NULL REFERENCES products(sku),
    quantity INT NOT NULL,
    PRIMARY KEY (reservation_id, sku),
    CONSTRAINT check_quantity CHECK (quantity > 0)
);

-- changeset developer:2
INSERT INTO products (sku, name, description) VALUES
('A100', 'SKU A100 Product', 'Description of A100'),
('B200', 'SKU B200 Product', 'Description of B200'),
('C300', 'SKU C300 Product', 'Description of C300');

INSERT INTO inventory (sku, total_stock, available_stock, reserved_stock) VALUES
('A100', 100, 100, 0),
('B200', 100, 100, 0),
('C300', 10, 10, 0);
