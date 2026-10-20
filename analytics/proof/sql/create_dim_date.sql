-- LEAP Analytics
-- Gold Layer: Date Dimension

CREATE OR REPLACE TABLE gold.dim_date AS
SELECT
    CAST(strftime(trade_date, '%Y%m%d') AS INT) AS date_key,
    trade_date AS full_date,
    YEAR(trade_date) AS year,
    QUARTER(trade_date) AS quarter,
    MONTH(trade_date) AS month,
    strftime(trade_date, '%B') AS month_name,
    DAY(trade_date) AS day,
    strftime(trade_date, '%A') AS day_name
FROM (
    SELECT DISTINCT CAST(execution_time AS DATE) AS trade_date
    FROM silver.executed_trades
);
