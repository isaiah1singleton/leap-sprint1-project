-- OLAP seed data for LEAP analytics development.
-- Run this AFTER transaction_schema.sql.
-- Safe to re-run: rows are keyed by unique test emails, market/symbol pairs,
-- and deterministic UUID idempotency keys.

BEGIN;

-- ---------------------------------------------------------------------------
-- Clients: deliberately span multiple client segments for BR-16 reporting.
-- Password hashes are placeholders because these users are analytics fixtures,
-- not authentication test users.
-- ---------------------------------------------------------------------------
INSERT INTO clients
    (client_name, email, password_hash, client_status, created_at, client_segment)
VALUES
    ('Olivia Retail', 'olap.retail1@example.test', 'ANALYTICS_FIXTURE_NOT_FOR_LOGIN', 'ACTIVE', '2026-08-15 10:00:00+00', 'RETAIL'),
    ('Noah Retail', 'olap.retail2@example.test', 'ANALYTICS_FIXTURE_NOT_FOR_LOGIN', 'ACTIVE', '2026-08-20 10:00:00+00', 'RETAIL'),
    ('Priya Premium', 'olap.premium1@example.test', 'ANALYTICS_FIXTURE_NOT_FOR_LOGIN', 'ACTIVE', '2026-08-22 10:00:00+00', 'PREMIUM'),
    ('David Professional', 'olap.professional1@example.test', 'ANALYTICS_FIXTURE_NOT_FOR_LOGIN', 'ACTIVE', '2026-08-25 10:00:00+00', 'PROFESSIONAL')
ON CONFLICT (email) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------
INSERT INTO accounts (client_id, account_status, account_name, opened_at)
SELECT c.client_id, 'ACTIVE', 'Analytics Trading Account', c.created_at + INTERVAL '1 day'
FROM clients c
WHERE c.email LIKE 'olap.%@example.test'
  AND NOT EXISTS (
      SELECT 1
      FROM accounts a
      WHERE a.client_id = c.client_id
        AND a.account_name = 'Analytics Trading Account'
  );

-- ---------------------------------------------------------------------------
-- Instruments: cover multiple markets / asset classes.
-- ---------------------------------------------------------------------------
INSERT INTO instruments
    (market, symbol, asset_class, instrument_name, is_tradable, quote_currency)
VALUES
    ('NASDAQ', 'AAPL', 'EQUITY', 'Apple Inc.', TRUE, 'USD'),
    ('NASDAQ', 'MSFT', 'EQUITY', 'Microsoft Corporation', TRUE, 'USD'),
    ('LSE', 'BARC', 'EQUITY', 'Barclays PLC', TRUE, 'GBP'),
    ('FX', 'GBPUSD', 'FX', 'British Pound / US Dollar', TRUE, 'USD'),
    ('CRYPTO', 'BTCUSD', 'CRYPTO', 'Bitcoin / US Dollar', TRUE, 'USD')
ON CONFLICT (market, symbol) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Orders
-- The fixed UUIDs make this seed script idempotent.
-- Data spans Aug, Sep and Oct 2026 so period reporting can be tested.
-- ---------------------------------------------------------------------------
WITH fixture_orders (
    email, market, symbol, idem, side, qty, submitted_price, submitted_at
) AS (
    VALUES
      ('olap.retail1@example.test','NASDAQ','AAPL','10000000-0000-0000-0000-000000000001'::uuid,'BUY',  10::numeric, 225.00::numeric,'2026-08-28 14:00:00+00'::timestamptz),
      ('olap.retail1@example.test','NASDAQ','MSFT','10000000-0000-0000-0000-000000000002'::uuid,'BUY',   5::numeric, 510.00::numeric,'2026-09-03 15:00:00+00'::timestamptz),
      ('olap.retail1@example.test','NASDAQ','AAPL','10000000-0000-0000-0000-000000000003'::uuid,'SELL',  2::numeric, 230.00::numeric,'2026-10-01 14:30:00+00'::timestamptz),

      ('olap.retail2@example.test','LSE','BARC','10000000-0000-0000-0000-000000000004'::uuid,'BUY',    50::numeric,   3.20::numeric,'2026-09-10 09:00:00+00'::timestamptz),
      ('olap.retail2@example.test','NASDAQ','AAPL','10000000-0000-0000-0000-000000000005'::uuid,'BUY', 20::numeric, 228.00::numeric,'2026-10-02 15:00:00+00'::timestamptz),

      ('olap.premium1@example.test','CRYPTO','BTCUSD','10000000-0000-0000-0000-000000000006'::uuid,'BUY', 0.10::numeric, 64000.00::numeric,'2026-08-30 18:00:00+00'::timestamptz),
      ('olap.premium1@example.test','FX','GBPUSD','10000000-0000-0000-0000-000000000007'::uuid,'BUY', 1000::numeric, 1.34::numeric,'2026-09-15 12:00:00+00'::timestamptz),
      ('olap.premium1@example.test','NASDAQ','MSFT','10000000-0000-0000-0000-000000000008'::uuid,'BUY', 8::numeric, 515.00::numeric,'2026-10-03 16:00:00+00'::timestamptz),

      ('olap.professional1@example.test','NASDAQ','AAPL','10000000-0000-0000-0000-000000000009'::uuid,'BUY',100::numeric, 226.00::numeric,'2026-09-20 14:00:00+00'::timestamptz),
      ('olap.professional1@example.test','LSE','BARC','10000000-0000-0000-0000-000000000010'::uuid,'BUY',500::numeric, 3.25::numeric,'2026-09-21 10:00:00+00'::timestamptz),
      ('olap.professional1@example.test','CRYPTO','BTCUSD','10000000-0000-0000-0000-000000000011'::uuid,'BUY',0.25::numeric, 65000.00::numeric,'2026-10-04 17:00:00+00'::timestamptz),
      ('olap.professional1@example.test','NASDAQ','AAPL','10000000-0000-0000-0000-000000000012'::uuid,'SELL',25::numeric, 232.00::numeric,'2026-10-05 14:00:00+00'::timestamptz)
)
INSERT INTO orders
    (account_id, instrument_id, idempotency_key, side, submitted_quote_price,
     requested_quantity, submitted_at, submitted_quote_at)
SELECT
    a.account_id,
    i.instrument_id,
    f.idem,
    f.side,
    f.submitted_price,
    f.qty,
    f.submitted_at,
    f.submitted_at + INTERVAL '2 seconds'
FROM fixture_orders f
JOIN clients c ON c.email = f.email
JOIN accounts a
  ON a.client_id = c.client_id
 AND a.account_name = 'Analytics Trading Account'
JOIN instruments i
  ON i.market = f.market
 AND i.symbol = f.symbol
ON CONFLICT (idempotency_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Order lifecycle events. Every analytics fixture order is accepted and filled.
-- ---------------------------------------------------------------------------
INSERT INTO order_events (order_id, status, occured_at)
SELECT o.order_id, 'SUBMITTED', o.submitted_at
FROM orders o
WHERE o.idempotency_key::text LIKE '10000000-0000-0000-0000-%'
  AND NOT EXISTS (
      SELECT 1 FROM order_events e
      WHERE e.order_id = o.order_id AND e.status = 'SUBMITTED'
  );

INSERT INTO order_events
    (order_id, status, decision_quote_price, occured_at, decision_quote_at)
SELECT
    o.order_id,
    'ACCEPTED',
    o.submitted_quote_price,
    o.submitted_at + INTERVAL '3 seconds',
    o.submitted_at + INTERVAL '2 seconds'
FROM orders o
WHERE o.idempotency_key::text LIKE '10000000-0000-0000-0000-%'
  AND NOT EXISTS (
      SELECT 1 FROM order_events e
      WHERE e.order_id = o.order_id AND e.status = 'ACCEPTED'
  );

-- ---------------------------------------------------------------------------
-- Fills. Prices intentionally differ slightly from submitted quotes.
-- ---------------------------------------------------------------------------
WITH fixture_fills (idem, execution_price) AS (
    VALUES
      ('10000000-0000-0000-0000-000000000001'::uuid, 225.10::numeric),
      ('10000000-0000-0000-0000-000000000002'::uuid, 509.80::numeric),
      ('10000000-0000-0000-0000-000000000003'::uuid, 230.40::numeric),
      ('10000000-0000-0000-0000-000000000004'::uuid,   3.18::numeric),
      ('10000000-0000-0000-0000-000000000005'::uuid, 228.25::numeric),
      ('10000000-0000-0000-0000-000000000006'::uuid, 64100.00::numeric),
      ('10000000-0000-0000-0000-000000000007'::uuid,   1.3420::numeric),
      ('10000000-0000-0000-0000-000000000008'::uuid, 514.75::numeric),
      ('10000000-0000-0000-0000-000000000009'::uuid, 226.15::numeric),
      ('10000000-0000-0000-0000-000000000010'::uuid,   3.27::numeric),
      ('10000000-0000-0000-0000-000000000011'::uuid, 65200.00::numeric),
      ('10000000-0000-0000-0000-000000000012'::uuid, 232.20::numeric)
)
INSERT INTO fills (order_id, execution_price, execution_time)
SELECT o.order_id, f.execution_price, o.submitted_at + INTERVAL '5 seconds'
FROM fixture_fills f
JOIN orders o ON o.idempotency_key = f.idem
WHERE NOT EXISTS (
    SELECT 1 FROM fills existing WHERE existing.order_id = o.order_id
);

INSERT INTO order_events
    (order_id, status, decision_quote_price, occured_at, decision_quote_at)
SELECT
    o.order_id,
    'FILLED',
    f.execution_price,
    f.execution_time,
    f.execution_time
FROM orders o
JOIN fills f ON f.order_id = o.order_id
WHERE o.idempotency_key::text LIKE '10000000-0000-0000-0000-%'
  AND NOT EXISTS (
      SELECT 1 FROM order_events e
      WHERE e.order_id = o.order_id AND e.status = 'FILLED'
  );

-- ---------------------------------------------------------------------------
-- Trade cash movements. BUY = cash outflow, SELL = cash inflow.
-- ---------------------------------------------------------------------------
INSERT INTO cash_movements
    (account_id, fill_id, amount, movement_type, currency, occured_at)
SELECT
    o.account_id,
    f.fill_id,
    CASE
        WHEN o.side = 'BUY' THEN -(o.requested_quantity * f.execution_price)
        ELSE (o.requested_quantity * f.execution_price)
    END,
    'TRADE',
    i.quote_currency,
    f.execution_time
FROM fills f
JOIN orders o ON o.order_id = f.order_id
JOIN instruments i ON i.instrument_id = o.instrument_id
WHERE o.idempotency_key::text LIKE '10000000-0000-0000-0000-%'
  AND NOT EXISTS (
      SELECT 1
      FROM cash_movements cm
      WHERE cm.fill_id = f.fill_id
        AND cm.movement_type = 'TRADE'
  );

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification query: this is the OLTP lineage that will feed FACT_TRADE.
-- Expected result after a clean seed: 12 rows.
-- ---------------------------------------------------------------------------
SELECT
    f.fill_id,
    o.order_id,
    c.client_id,
    c.client_segment,
    a.account_id,
    i.instrument_id,
    i.symbol,
    i.asset_class,
    i.market,
    o.side,
    o.requested_quantity,
    f.execution_price,
    f.execution_time,
    (o.requested_quantity * f.execution_price) AS notional_amount,
    i.quote_currency
FROM fills f
JOIN orders o ON o.order_id = f.order_id
JOIN accounts a ON a.account_id = o.account_id
JOIN clients c ON c.client_id = a.client_id
JOIN instruments i ON i.instrument_id = o.instrument_id
WHERE o.idempotency_key::text LIKE '10000000-0000-0000-0000-%'
ORDER BY f.execution_time, f.fill_id;
