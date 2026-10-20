-- Adds 1000 synthetic executed trades, separate from the 12-trade baseline.
-- If both seeds are loaded, analytics includes 1012 fills plus other database trades.
-- Dates span January through September 2026 (UTC); no randomness is used.
-- In pgAdmin select jayson-trading-dev, open this file, and execute it in full.
-- Re-running the script does not add another 1000 trades. Refresh analytics
-- afterward; this script does not update DuckDB/Superset directly.
-- ANALYTICS FIXTURES ONLY: run against an isolated development/test database.
-- Tables must already exist from the current transaction_schema.sql.
-- Edit expected_database below. This does not create/drop tables or reset data.
-- No application login, balances, holdings or FIFO inventory are provisioned.
BEGIN;
SET LOCAL TIME ZONE 'UTC';

DO $$
DECLARE
    expected_database TEXT := 'jayson-trading-dev';
    fixture RECORD;
    client_key INTEGER;
    account_key INTEGER;
    instrument_key INTEGER;
    order_key INTEGER;
    fill_key INTEGER;
    total_fills INTEGER;
BEGIN
    IF current_database() <> expected_database THEN
        RAISE EXCEPTION 'Wrong database: expected %, connected to %. Edit expected_database only for your isolated test database.',
            expected_database, current_database();
    END IF;
    -- Serialize executions of this seed without locking unrelated application work.
    PERFORM pg_advisory_xact_lock(160026, 1000);

    FOR fixture IN
        SELECT * FROM (VALUES
            ('olap1000.retail1@example.test','Olivia Retail','RETAIL'),
            ('olap1000.retail2@example.test','Noah Retail','RETAIL'),
            ('olap1000.premium1@example.test','Priya Premium','PREMIUM'),
            ('olap1000.professional1@example.test','David Professional','PROFESSIONAL')
        ) AS v(email, client_name, segment)
    LOOP
        INSERT INTO clients (client_name,email,password_hash,client_status,client_segment)
        VALUES (fixture.client_name,fixture.email,'ANALYTICS_FIXTURE_NOT_FOR_LOGIN','INACTIVE',fixture.segment)
        ON CONFLICT (email) DO NOTHING;

        SELECT client_id INTO STRICT client_key FROM clients WHERE email=fixture.email;
        IF EXISTS (SELECT 1 FROM clients WHERE client_id=client_key AND client_segment<>fixture.segment) THEN
            RAISE EXCEPTION 'Fixture client % has a different segment; seed will not overwrite it.',fixture.email;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM accounts WHERE client_id=client_key AND account_name='OLAP 1000 trade fixtures') THEN
            INSERT INTO accounts (client_id,account_status,account_name)
            VALUES (client_key,'ACTIVE','OLAP 1000 trade fixtures');
        END IF;
    END LOOP;

    INSERT INTO instruments (market,symbol,asset_class,instrument_name,is_tradable,quote_currency)
    VALUES
        ('NASDAQ','AAPL','EQUITY','Apple Inc.',FALSE,'USD'),
        ('NASDAQ','MSFT','EQUITY','Microsoft Corporation',FALSE,'USD'),
        ('LSE','BARC','EQUITY','Barclays PLC',FALSE,'GBP'),
        ('FX','GBPUSD','FX','British Pound / US Dollar',FALSE,'USD'),
        ('CRYPTO','BTCUSD','CRYPTO','Bitcoin / US Dollar',FALSE,'USD')
    ON CONFLICT (market,symbol) DO NOTHING;

    FOR fixture IN
        SELECT v.email,v.market,v.symbol,v.side,
               v.quantity * (1 + n.trade_number % 4) AS quantity,
               round(v.price * (1 + ((n.trade_number % 21) - 10)::numeric / 1000),8) AS price,
               TIMESTAMPTZ '2026-01-01 09:00:00+00'
                   + n.trade_number * INTERVAL '6 hours' AS submitted_at
        FROM generate_series(0,999) AS n(trade_number)
        JOIN (VALUES
            (0,'olap1000.retail1@example.test','NASDAQ','AAPL','BUY',10::numeric,225.10::numeric),
            (1,'olap1000.retail1@example.test','NASDAQ','MSFT','BUY',5::numeric,509.80::numeric),
            (2,'olap1000.retail1@example.test','NASDAQ','AAPL','SELL',2::numeric,230.40::numeric),
            (3,'olap1000.retail2@example.test','LSE','BARC','BUY',50::numeric,3.18::numeric),
            (4,'olap1000.retail2@example.test','NASDAQ','AAPL','BUY',20::numeric,228.25::numeric),
            (5,'olap1000.premium1@example.test','CRYPTO','BTCUSD','BUY',0.10::numeric,64100::numeric),
            (6,'olap1000.premium1@example.test','FX','GBPUSD','BUY',1000::numeric,1.3420::numeric),
            (7,'olap1000.premium1@example.test','NASDAQ','MSFT','BUY',8::numeric,514.75::numeric),
            (8,'olap1000.professional1@example.test','NASDAQ','AAPL','BUY',100::numeric,226.15::numeric),
            (9,'olap1000.professional1@example.test','LSE','BARC','BUY',500::numeric,3.27::numeric),
            (10,'olap1000.professional1@example.test','CRYPTO','BTCUSD','BUY',0.25::numeric,65200::numeric),
            (11,'olap1000.professional1@example.test','NASDAQ','AAPL','SELL',25::numeric,232.20::numeric)
        ) AS v(template_number,email,market,symbol,side,quantity,price)
          ON v.template_number = n.trade_number % 12
        ORDER BY n.trade_number
    LOOP
        SELECT a.account_id INTO STRICT account_key
        FROM accounts a JOIN clients c ON c.client_id=a.client_id
        WHERE c.email=fixture.email AND a.account_name='OLAP 1000 trade fixtures';
        SELECT instrument_id INTO STRICT instrument_key FROM instruments
        WHERE market=fixture.market AND symbol=fixture.symbol;

        -- Current orders has no idempotency_key. Match only the dedicated
        -- fixture account, instrument, side and fixed submission timestamp.
        SELECT o.order_id INTO order_key FROM orders o
        WHERE o.account_id=account_key AND o.instrument_id=instrument_key
          AND o.side=fixture.side AND o.submitted_at=fixture.submitted_at;
        IF order_key IS NULL THEN
            INSERT INTO orders (account_id,instrument_id,side,requested_quantity,
                                submitted_quote_price,submitted_quote_at,submitted_at)
            VALUES (account_key,instrument_key,fixture.side,fixture.quantity,
                    fixture.price,fixture.submitted_at-INTERVAL '1 second',fixture.submitted_at)
            RETURNING order_id INTO order_key;
        ELSIF EXISTS (SELECT 1 FROM orders WHERE order_id=order_key
                      AND (requested_quantity<>fixture.quantity OR submitted_quote_price<>fixture.price)) THEN
            RAISE EXCEPTION 'Fixture order % differs from baseline; seed will not overwrite it.',order_key;
        END IF;

        INSERT INTO order_events (order_id,status,occurred_at)
        VALUES (order_key,'SUBMITTED',fixture.submitted_at)
        ON CONFLICT (order_id,status) DO NOTHING;
        INSERT INTO order_events (order_id,status,decision_quote_price,decision_quote_at,occurred_at)
        VALUES
            (order_key,'ACCEPTED',fixture.price,fixture.submitted_at,fixture.submitted_at+INTERVAL '1 second'),
            (order_key,'FILLED',fixture.price,fixture.submitted_at+INTERVAL '4 seconds',fixture.submitted_at+INTERVAL '5 seconds')
        ON CONFLICT (order_id,status) DO NOTHING;

        INSERT INTO fills (order_id,execution_price,execution_time)
        VALUES (order_key,fixture.price,fixture.submitted_at+INTERVAL '5 seconds')
        ON CONFLICT (order_id) DO NOTHING;
        SELECT fill_id INTO STRICT fill_key FROM fills WHERE order_id=order_key;
        IF EXISTS (SELECT 1 FROM fills WHERE fill_id=fill_key
                   AND (execution_price<>fixture.price OR execution_time<>fixture.submitted_at+INTERVAL '5 seconds')) THEN
            RAISE EXCEPTION 'Fixture fill % differs from baseline; seed will not overwrite it.',fill_key;
        END IF;

        INSERT INTO cash_movements (account_id,fill_id,amount,movement_type,currency,occurred_at)
        SELECT account_key,fill_key,
               CASE fixture.side WHEN 'BUY' THEN -1 ELSE 1 END * fixture.quantity * fixture.price,
               'TRADE',quote_currency,fixture.submitted_at+INTERVAL '5 seconds'
        FROM instruments WHERE instrument_id=instrument_key
        ON CONFLICT (fill_id) DO NOTHING;
    END LOOP;

    SELECT count(*) INTO total_fills
    FROM fills f JOIN orders o ON o.order_id=f.order_id
    JOIN accounts a ON a.account_id=o.account_id JOIN clients c ON c.client_id=a.client_id
    WHERE a.account_name='OLAP 1000 trade fixtures'
      AND c.email IN ('olap1000.retail1@example.test','olap1000.retail2@example.test',
                      'olap1000.premium1@example.test','olap1000.professional1@example.test');
    IF total_fills<>1000 THEN
        RAISE EXCEPTION 'Expected 1000 dedicated fixture fills, found %; transaction rolled back.',total_fills;
    END IF;
END $$;
COMMIT;

-- Expected bulk fill counts: RETAIL=419, PREMIUM=249, PROFESSIONAL=332.
SELECT c.client_segment,count(*) AS seeded_fills
FROM fills f JOIN orders o ON o.order_id=f.order_id
JOIN accounts a ON a.account_id=o.account_id JOIN clients c ON c.client_id=a.client_id
WHERE a.account_name='OLAP 1000 trade fixtures'
  AND c.email IN ('olap1000.retail1@example.test','olap1000.retail2@example.test',
                  'olap1000.premium1@example.test','olap1000.professional1@example.test')
GROUP BY c.client_segment ORDER BY c.client_segment;
