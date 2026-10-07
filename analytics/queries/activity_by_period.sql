-- BR-16: Analyse trading activity over time.

SELECT
    d.year,
    d.month,
    d.month_name,
    COUNT(*) AS trade_count,
    SUM(f.executed_quantity) AS total_quantity
FROM leap.gold.fact_trade f
INNER JOIN leap.gold.dim_date d ON f.execution_date_key = d.date_key
GROUP BY d.year, d.month, d.month_name
ORDER BY d.year, d.month;
