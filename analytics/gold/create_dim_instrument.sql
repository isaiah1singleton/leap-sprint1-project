-- LEAP Analytics
-- Gold Layer: Instrument Dimension

CREATE OR REPLACE TABLE leap.gold.dim_instrument AS
SELECT
    ROW_NUMBER() OVER (ORDER BY instrument_id) AS instrument_key,
    instrument_id AS source_instrument_id,
    symbol,
    instrument_name,
    asset_class,
    market,
    quote_currency
FROM (
    SELECT DISTINCT
        instrument_id, symbol, instrument_name, asset_class, market, quote_currency
    FROM leap.silver.executed_trades
);
