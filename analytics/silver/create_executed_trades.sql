-- LEAP Analytics
-- Silver Layer: Executed Trades
-- Grain: one row per executed fill.

CREATE OR REPLACE TABLE leap.silver.executed_trades AS
SELECT
    f.fill_id,
    o.order_id,
    a.account_id,
    c.client_id,
    i.instrument_id,
    c.client_segment,
    i.symbol,
    i.instrument_name,
    i.asset_class,
    i.market,
    i.quote_currency,
    o.side,
    CAST(o.requested_quantity AS DECIMAL(18, 8)) AS executed_quantity,
    CAST(f.execution_price AS DECIMAL(18, 8)) AS execution_price,
    CAST(f.execution_time AS TIMESTAMP) AS execution_time,
    CAST(o.requested_quantity * f.execution_price AS DECIMAL(24, 8)) AS notional_amount
FROM leap.bronze.fills f
INNER JOIN leap.bronze.orders o ON f.order_id = o.order_id
INNER JOIN leap.bronze.accounts a ON o.account_id = a.account_id
INNER JOIN leap.bronze.clients c ON a.client_id = c.client_id
INNER JOIN leap.bronze.instruments i ON o.instrument_id = i.instrument_id;
