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

    account_id INTEGER NOT NULL
        REFERENCES accounts(account_id),

    instrument_id INTEGER NOT NULL
        REFERENCES instruments(instrument_id),

    side TEXT NOT NULL
        CHECK (side IN ('BUY', 'SELL')),

    requested_quantity NUMERIC(24, 8) NOT NULL
        CHECK (requested_quantity > 0),

    submitted_quote_price NUMERIC(24, 8) NOT NULL
        CHECK (submitted_quote_price > 0),

    submitted_quote_at TIMESTAMP WITH TIME ZONE NOT NULL,

    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_orders_quote_time
        CHECK (submitted_quote_at <= submitted_at)
);

CREATE TABLE order_events
(
    event_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders(order_id),
    status TEXT NOT NULL CHECK( status IN ('SUBMITTED', 'ACCEPTED', 'FILLED', 'REJECTED')),
	reason TEXT,

	decision_quote_price NUMERIC CHECK(decision_quote_price > 0),

	occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	decision_quote_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_order_events_order_status UNIQUE (order_id, status),
    CONSTRAINT ck_order_events_quote_pair CHECK (
        (decision_quote_price IS NULL AND decision_quote_at IS NULL)
        OR
        (decision_quote_price IS NOT NULL AND decision_quote_at IS NOT NULL)
    ),
    CONSTRAINT ck_order_events_rejection_reason
        CHECK (
            status <> 'REJECTED'
            OR (reason IS NOT NULL AND length(trim(reason)) > 0)
        ),

    CONSTRAINT ck_order_events_required_quote
        CHECK (
            status NOT IN ('ACCEPTED', 'FILLED')
            OR (
                decision_quote_price IS NOT NULL
                AND decision_quote_at IS NOT NULL
            )
        ),

    CONSTRAINT ck_order_events_quote_time
        CHECK (
            decision_quote_at IS NULL
            OR decision_quote_at <= occurred_at
        )

);

CREATE TABLE account_balances
(
    account_id INTEGER NOT NULL REFERENCES accounts(account_id),
    currency TEXT NOT NULL,
    PRIMARY KEY (account_id, currency),

    total_balance NUMERIC(24, 8) NOT NULL CHECK(total_balance >= 0),
    reserved_balance NUMERIC(24, 8) NOT NULL CHECK(reserved_balance >= 0 AND reserved_balance <= total_balance),

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE account_holdings
(
    account_id INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),
    PRIMARY KEY (account_id, instrument_id),
    total_quantity NUMERIC(24, 8) NOT NULL DEFAULT 0 CHECK(total_quantity >= 0),
    reserved_quantity NUMERIC(24, 8) NOT NULL DEFAULT 0
        CHECK(reserved_quantity >= 0 AND reserved_quantity <= total_quantity),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fills
(
    fill_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders(order_id),
    execution_price NUMERIC NOT NULL CHECK(execution_price > 0),
    execution_time TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fills_order UNIQUE (order_id)
);

CREATE TABLE cash_movements
(
    cash_movement_id SERIAL PRIMARY KEY,

    account_id INTEGER NOT NULL
        REFERENCES accounts(account_id),

    fill_id INTEGER
        REFERENCES fills(fill_id),

    amount NUMERIC NOT NULL
        CHECK (amount <> 0),

    movement_type TEXT NOT NULL
        CHECK (
            movement_type IN (
                'DEPOSIT',
                'WITHDRAW',
                'FEE',
                'TRADE',
                'ADJUSTMENT'
            )
        ),

    currency TEXT NOT NULL
        CHECK (currency IN ('EUR', 'USD', 'GBP', 'INR')),

    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    reason TEXT,

    CONSTRAINT uk_cash_movements_fill
        UNIQUE (fill_id),

    CONSTRAINT ck_cash_movements_fill_type
        CHECK (
            (movement_type = 'TRADE' AND fill_id IS NOT NULL)
            OR
            (movement_type <> 'TRADE' AND fill_id IS NULL)
        ),

    CONSTRAINT ck_cash_movements_direction
        CHECK (
            (movement_type = 'DEPOSIT' AND amount > 0)
            OR
            (movement_type IN ('WITHDRAW', 'FEE') AND amount < 0)
            OR
            movement_type IN ('TRADE', 'ADJUSTMENT')
        ),

    CONSTRAINT ck_cash_movements_adjustment_reason
        CHECK (
            movement_type <> 'ADJUSTMENT'
            OR (
                reason IS NOT NULL
                AND length(trim(reason)) > 0
            )
        )
);

CREATE TABLE fifo_allocations
(
    sell_fill_id INTEGER NOT NULL REFERENCES fills(fill_id),
    buy_fill_id INTEGER NOT NULL REFERENCES fills(fill_id),
    PRIMARY KEY(sell_fill_id, buy_fill_id),

    allocated_quantity INTEGER NOT NULL CHECK(allocated_quantity > 0)
);
