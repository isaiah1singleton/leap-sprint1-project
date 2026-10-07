-- BR-16: Analyse trading activity by instrument.
-- Monetary totals remain separated by quote currency.

SELECT
    i.symbol,
    i.asset_class,
    i.quote_currency,
    COUNT(*) AS trade_count,
    SUM(f.executed_quantity) AS total_quantity,
    SUM(f.notional_amount) AS total_notional
FROM leap.gold.fact_trade f
INNER JOIN leap.gold.dim_instrument i ON f.instrument_key = i.instrument_key
GROUP BY i.symbol, i.asset_class, i.quote_currency
ORDER BY trade_count DESC, i.symbol;
