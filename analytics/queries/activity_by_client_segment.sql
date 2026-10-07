-- BR-16: Analyse trading activity by client segment.

SELECT
    c.client_segment,
    COUNT(*) AS trade_count
FROM leap.gold.fact_trade f
INNER JOIN leap.gold.dim_client c ON f.client_key = c.client_key
GROUP BY c.client_segment
ORDER BY trade_count DESC;
