"""Refresh a local DuckDB analytical copy from PostgreSQL.

Run from any directory: python analytics/duckdb/refresh.py
Requires the DuckDB postgres extension to be installable on first use.
"""
import argparse
import os
from pathlib import Path
import duckdb

DEFAULT_DB = Path(__file__).resolve().parent / "leap_analytics.duckdb"

def connection_string():
    host = os.getenv("POSTGRES_HOST", "localhost")
    port = os.getenv("POSTGRES_PORT", "5432")
    database = os.getenv("POSTGRES_DB")
    user = os.getenv("POSTGRES_USER")
    password = os.getenv("POSTGRES_PASSWORD")
    if not all((database, user, password)):
        raise SystemExit("Set POSTGRES_DB, POSTGRES_USER and POSTGRES_PASSWORD in your shell.")
    # libpq keyword values: single quotes and backslashes are escaped.
    def quote(value):
        return "'" + str(value).replace("\\", "\\\\").replace("'", "\\'") + "'"
    return " ".join(f"{key}={quote(value)}" for key, value in (
        ("host", host), ("port", port), ("dbname", database),
        ("user", user), ("password", password),
    ))

def refresh(db_path):
    db_path.parent.mkdir(parents=True, exist_ok=True)
    con = duckdb.connect(str(db_path))
    try:
        con.execute("INSTALL postgres")
        con.execute("LOAD postgres")
        con.execute("ATTACH ? AS oltp (TYPE postgres, READ_ONLY)", [connection_string()])
        # Keep the reporting snapshot consistent: all materialized tables are
        # replaced within one DuckDB transaction, so failures roll back.
        con.execute("BEGIN TRANSACTION")
        try:
            con.execute("CREATE SCHEMA IF NOT EXISTS bronze")
            con.execute("CREATE SCHEMA IF NOT EXISTS silver")
            con.execute("CREATE SCHEMA IF NOT EXISTS gold")
            # Do not ingest passwords, emails, sessions or other unrelated PII.
            sources = {
                "clients": "SELECT client_id, client_segment FROM oltp.public.clients",
                "accounts": "SELECT account_id, client_id FROM oltp.public.accounts",
                "instruments": "SELECT instrument_id, symbol, instrument_name, asset_class, market, quote_currency FROM oltp.public.instruments",
                "orders": "SELECT order_id, account_id, instrument_id, side, requested_quantity FROM oltp.public.orders",
                "fills": "SELECT fill_id, order_id, execution_price, execution_time FROM oltp.public.fills",
            }
            for table, query in sources.items():
                con.execute(f"CREATE OR REPLACE TABLE bronze.{table} AS {query}")
            con.execute("""
                CREATE OR REPLACE TABLE silver.executed_trades AS
                SELECT f.fill_id, o.order_id, a.account_id, c.client_id,
                       i.instrument_id, c.client_segment, i.symbol,
                       i.instrument_name, i.asset_class, i.market,
                       i.quote_currency, o.side,
                       CAST(o.requested_quantity AS DECIMAL(18,8)) AS executed_quantity,
                       CAST(f.execution_price AS DECIMAL(18,8)) AS execution_price,
                       CAST(f.execution_time AS TIMESTAMP) AS execution_time,
                       CAST(o.requested_quantity * f.execution_price AS DECIMAL(24,8)) AS notional_amount
                FROM bronze.fills f
                JOIN bronze.orders o ON f.order_id = o.order_id
                JOIN bronze.accounts a ON o.account_id = a.account_id
                JOIN bronze.clients c ON a.client_id = c.client_id
                JOIN bronze.instruments i ON o.instrument_id = i.instrument_id
            """)
            con.execute("""
                CREATE OR REPLACE TABLE gold.fact_trade AS
                SELECT fill_id, order_id, account_id, client_id, instrument_id,
                       client_segment, symbol, instrument_name, asset_class,
                       market, quote_currency, side, executed_quantity,
                       execution_price, execution_time, notional_amount,
                       CAST(date_trunc('month', execution_time) AS DATE) AS trade_month
                FROM silver.executed_trades
            """)
            con.execute("COMMIT")
        except Exception:
            con.execute("ROLLBACK")
            raise
        finally:
            con.execute("DETACH oltp")
        count = con.execute("SELECT count(*) FROM gold.fact_trade").fetchone()[0]
        print(f"Refresh complete: {count} executed fills in {db_path}")
        print("By client segment:")
        for row in con.execute("SELECT client_segment, count(*) FROM gold.fact_trade GROUP BY 1 ORDER BY 1").fetchall():
            print(f"  {row[0]}: {row[1]}")
        print("By instrument:")
        for row in con.execute("SELECT symbol, count(*) FROM gold.fact_trade GROUP BY 1 ORDER BY 1").fetchall():
            print(f"  {row[0]}: {row[1]}")
        print("By month:")
        for row in con.execute("SELECT trade_month, count(*) FROM gold.fact_trade GROUP BY 1 ORDER BY 1").fetchall():
            print(f"  {row[0]}: {row[1]}")
    finally:
        con.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Refresh LEAP DuckDB analytical snapshot")
    parser.add_argument("--db", type=Path, default=DEFAULT_DB, help="DuckDB output path")
    args = parser.parse_args()
    refresh(args.db)
