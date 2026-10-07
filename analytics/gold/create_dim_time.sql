-- LEAP Analytics
-- Gold Layer: Time Dimension

CREATE OR REPLACE TABLE leap.gold.dim_time AS
SELECT
    CAST(DATE_FORMAT(execution_time, 'HHmmss') AS INT) AS time_key,
    HOUR(execution_time) AS hour,
    MINUTE(execution_time) AS minute,
    SECOND(execution_time) AS second
FROM leap.silver.executed_trades
GROUP BY
    DATE_FORMAT(execution_time, 'HHmmss'),
    HOUR(execution_time),
    MINUTE(execution_time),
    SECOND(execution_time);
