-- liquibase formatted sql

-- changeset developer:2
INSERT INTO products (sku, name, description) VALUES
('A100', 'SKU A100 Product', 'Description of A100'),
('B200', 'SKU B200 Product', 'Description of B200'),
('C300', 'SKU C300 Product', 'Description of C300');

INSERT INTO inventory (sku, total_stock, available_stock, reserved_stock) VALUES
('A100', 100, 100, 0),
('B200', 100, 100, 0),
('C300', 10, 10, 0);
