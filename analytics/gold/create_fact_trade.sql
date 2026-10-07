-- LEAP Analytics
-- Gold Layer: Trade Fact
-- Grain: one row per executed fill.

CREATE OR REPLACE TABLE leap.gold.fact_trade AS
SELECT
    ROW_NUMBER() OVER (ORDER BY s.execution_time, s.fill_id) AS trade_key,
    s.fill_id,
    s.order_id,
    dc.client_key,
    da.account_key,
    di.instrument_key,
    CAST(DATE_FORMAT(s.execution_time, 'yyyyMMdd') AS INT) AS execution_date_key,
    CAST(DATE_FORMAT(s.execution_time, 'HHmmss') AS INT) AS execution_time_key,
    s.side,
    s.executed_quantity,
    s.execution_price,
    s.notional_amount
FROM leap.silver.executed_trades s
INNER JOIN leap.gold.dim_client dc ON s.client_id = dc.source_client_id
INNER JOIN leap.gold.dim_account da ON s.account_id = da.source_account_id
INNER JOIN leap.gold.dim_instrument di ON s.instrument_id = di.source_instrument_id;
