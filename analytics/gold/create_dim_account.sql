-- LEAP Analytics
-- Gold Layer: Account Dimension

CREATE OR REPLACE TABLE leap.gold.dim_account AS
SELECT
    ROW_NUMBER() OVER (ORDER BY account_id) AS account_key,
    account_id AS source_account_id
FROM (
    SELECT DISTINCT account_id
    FROM leap.silver.executed_trades
);
