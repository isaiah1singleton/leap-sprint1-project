-- LEAP Analytics
-- Gold Layer: Client Dimension

CREATE OR REPLACE TABLE leap.gold.dim_client AS
SELECT
    ROW_NUMBER() OVER (ORDER BY client_id) AS client_key,
    client_id AS source_client_id,
    client_segment
FROM (
    SELECT DISTINCT client_id, client_segment
    FROM leap.silver.executed_trades
);
