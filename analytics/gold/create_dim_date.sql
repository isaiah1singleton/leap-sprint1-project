-- LEAP Analytics
-- Gold Layer: Date Dimension

CREATE OR REPLACE TABLE leap.gold.dim_date AS
SELECT
    CAST(DATE_FORMAT(trade_date, 'yyyyMMdd') AS INT) AS date_key,
    trade_date AS full_date,
    YEAR(trade_date) AS year,
    QUARTER(trade_date) AS quarter,
    MONTH(trade_date) AS month,
    DATE_FORMAT(trade_date, 'MMMM') AS month_name,
    DAY(trade_date) AS day,
    DATE_FORMAT(trade_date, 'EEEE') AS day_name
FROM (
    SELECT DISTINCT TO_DATE(execution_time) AS trade_date
    FROM leap.silver.executed_trades
);
