CREATE TABLE orders (
	id UUID PRIMARY KEY,
	partner_id UUID NOT NULL REFERENCES partners (id),
	status VARCHAR(32) NOT NULL,
	total NUMERIC(19, 2) NOT NULL CHECK (total >= 0),
	reserved_amount NUMERIC(19, 2) NOT NULL CHECK (reserved_amount >= 0),
	idempotency_key VARCHAR(128) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT uk_orders_partner_idempotency UNIQUE (partner_id, idempotency_key)
);

CREATE INDEX idx_orders_partner_id ON orders (partner_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);
CREATE INDEX idx_orders_partner_status_created ON orders (partner_id, status, created_at);
