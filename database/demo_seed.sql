-- Run transaction_schema.sql first. Register/sign in through the app, then
-- replace the email below with that registered client's email and run in pgAdmin.
-- This creates one funded demo account; ordinary new accounts still start at $0.
BEGIN;

INSERT INTO instruments (market, symbol, asset_class, instrument_name, is_tradable, quote_currency)
VALUES
  ('US', 'AAPL', 'EQUITY', 'Apple Inc.', TRUE, 'USD'),
  ('US', 'MSFT', 'EQUITY', 'Microsoft Corporation', TRUE, 'USD'),
  ('US', 'NVDA', 'EQUITY', 'NVIDIA Corporation', TRUE, 'USD'),
  ('US', 'AMZN', 'EQUITY', 'Amazon.com Inc.', TRUE, 'USD'),
  ('US', 'GOOGL', 'EQUITY', 'Alphabet Inc.', TRUE, 'USD'),
  ('US', 'META', 'EQUITY', 'Meta Platforms Inc.', TRUE, 'USD'),
  ('US', 'TSLA', 'EQUITY', 'Tesla Inc.', TRUE, 'USD'),
  ('NYSE ARCA', 'SPY', 'ETF', 'SPDR S&P 500 ETF Trust', TRUE, 'USD'),
  ('NYSE ARCA', 'QQQ', 'ETF', 'Invesco QQQ Trust', TRUE, 'USD')
ON CONFLICT (market, symbol) DO UPDATE SET is_tradable = TRUE;

DO $$
DECLARE
  demo_email TEXT := 'your.registered.email@example.com'; -- EDIT THIS
  demo_client_id INTEGER;
  demo_account_id INTEGER;
BEGIN
  SELECT client_id INTO demo_client_id FROM clients WHERE lower(email) = lower(demo_email) AND client_status = 'ACTIVE';
  IF demo_client_id IS NULL THEN
    RAISE EXCEPTION 'Register an active client and replace demo_email before running this seed.';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM accounts WHERE client_id = demo_client_id AND account_name = 'Demo funded account') THEN
    INSERT INTO accounts (client_id, account_status, account_name)
    VALUES (demo_client_id, 'ACTIVE', 'Demo funded account') RETURNING account_id INTO demo_account_id;

    INSERT INTO account_balances (account_id, currency, total_balance, updated_at)
    VALUES (demo_account_id, 'USD', 10000.00, CURRENT_TIMESTAMP);
    INSERT INTO cash_movements (account_id, amount, movement_type, currency, reason)
    VALUES (demo_account_id, 10000.00, 'DEPOSIT', 'USD', 'Initial demo funding');

    INSERT INTO account_holdings (account_id, instrument_id, total_quantity, reserved_quantity)
    SELECT demo_account_id, instrument_id,
           CASE symbol WHEN 'AAPL' THEN 5.00000000 WHEN 'MSFT' THEN 2.00000000 END,
           0
    FROM instruments WHERE market = 'US' AND symbol IN ('AAPL', 'MSFT');
  END IF;
END $$;

COMMIT;
