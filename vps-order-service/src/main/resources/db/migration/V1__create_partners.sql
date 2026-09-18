CREATE TABLE partners (
	id UUID PRIMARY KEY,
	name VARCHAR(160) NOT NULL,
	credit_limit NUMERIC(19, 2) NOT NULL CHECK (credit_limit >= 0),
	available_credit NUMERIC(19, 2) NOT NULL CHECK (available_credit >= 0),
	version BIGINT NOT NULL DEFAULT 0,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);
