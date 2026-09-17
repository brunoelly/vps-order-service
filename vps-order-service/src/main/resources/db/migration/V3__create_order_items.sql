CREATE TABLE order_items (
	id UUID PRIMARY KEY,
	order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
	sku VARCHAR(64) NOT NULL,
	product_name VARCHAR(255) NOT NULL,
	quantity INTEGER NOT NULL CHECK (quantity > 0),
	unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
