CREATE TABLE clients (
    client_id SERIAL PRIMARY KEY,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    client_status TEXT NOT NULL CHECK (client_status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_segment TEXT NOT NULL DEFAULT 'RETAIL'
);

CREATE UNIQUE INDEX clients_email_lower_uk ON clients (LOWER(email));
