-- LEAP Analytics
-- Gold Layer: Time Dimension

CREATE OR REPLACE TABLE gold.dim_time AS
SELECT
    CAST(strftime(execution_time, '%H%M%S') AS INT) AS time_key,
    HOUR(execution_time) AS hour,
    MINUTE(execution_time) AS minute,
    SECOND(execution_time) AS second
FROM silver.executed_trades
GROUP BY
    strftime(execution_time, '%H%M%S'),
    HOUR(execution_time),
    MINUTE(execution_time),
    SECOND(execution_time);
