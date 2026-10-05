-- Initial schema for an empty database. PostgreSQL's Docker initializer runs
-- this file only when creating a new data volume.
CREATE TABLE clients
(
	client_id SERIAL PRIMARY KEY,
    client_name TEXT NOT NULL,
	email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    client_status TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_segment TEXT NOT NULL
);

CREATE TABLE sessions
(
    session_id SERIAL PRIMARY KEY,
    session_token_hash TEXT NOT NULL UNIQUE,
    client_id INTEGER NOT NULL REFERENCES clients(client_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK(expires_at > created_at),
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE accounts
(
	account_id SERIAL PRIMARY KEY,
	client_id INTEGER NOT NULL REFERENCES clients(client_id),
	account_status TEXT NOT NULL,

	account_name TEXT NOT NULL,

	opened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE instruments
(
	instrument_id SERIAL PRIMARY KEY,
    market TEXT NOT NULL,
    symbol TEXT NOT NULL,
    asset_class TEXT NOT NULL,
    instrument_name TEXT NOT NULL,
    is_tradable BOOLEAN NOT NULL DEFAULT FALSE,
    quote_currency TEXT NOT NULL,
    CONSTRAINT uk_instruments_market_symbol UNIQUE (market, symbol)
);

CREATE TABLE orders
(
	order_id SERIAL PRIMARY KEY,
	account_id INTEGER REFERENCES accounts(account_id),
	instrument_id INTEGER REFERENCES instruments(instrument_id),

	idempotency_key UUID UNIQUE NOT NULL,

	side TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
	submitted_quote_price NUMERIC CHECK(submitted_quote_price > 0),
	requested_quantity NUMERIC CHECK(requested_quantity > 0),
	
	submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
		
	submitted_quote_at TIMESTAMP WITH TIME ZONE NOT NULL
		CHECK(submitted_at <= submitted_quote_at)
);

CREATE TABLE order_events
(
    event_id SERIAL PRIMARY KEY,
    order_id INTEGER REFERENCES orders(order_id),
    status TEXT NOT NULL CHECK( status IN ('SUBMITTED', 'ACCEPTED', 'FILLED', 'REJECTED')),
	reason TEXT,

	decision_quote_price NUMERIC CHECK(decision_quote_price > 0),

	occured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	decision_quote_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE account_balances
(
    account_id INTEGER NOT NULL REFERENCES accounts(account_id),
    currency TEXT NOT NULL,
    PRIMARY KEY (account_id, currency),

    total_balance DECIMAL NOT NULL CHECK(total_balance > 0),
    reserved_balance DECIMAL NOT NULL CHECK(reserved_balance > 0),

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE account_holdings
(
    account_id INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),
    PRIMARY KEY (account_id, instrument_id)
);

CREATE TABLE fills
(
    fill_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders(order_id),

    execution_price NUMERIC NOT NULL CHECK(execution_price > 0),
    execution_time TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE cash_movements
(
    cash_movement_id SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts(account_id),
    fill_id INTEGER REFERENCES fills(fill_id),

    amount NUMERIC NOT NULL CHECK(amount != 0),
    movement_type TEXT NOT NULL CHECK(movement_type IN ('TRADE', 'ADJUSTMENT', 'DEPOSIT', 'WITHDRAWAL')),
    currency TEXT NOT NULL,

    occured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fifo_allocations
(
    sell_fill_id INTEGER NOT NULL REFERENCES fills(fill_id),
    buy_fill_id INTEGER NOT NULL REFERENCES fills(fill_id),
    PRIMARY KEY(sell_fill_id, buy_fill_id),

    allocated_quantity INTEGER NOT NULL CHECK(allocated_quantity > 0)
);
