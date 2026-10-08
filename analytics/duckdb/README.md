# LEAP DuckDB local analytics (BR-16)

An optional **local** alternative to the Databricks analytical warehouse. PostgreSQL remains the live trading system of record. DuckDB stores a **materialized snapshot** for reporting; dashboard queries must never run directly against the OLTP tables.

## Requirements

- Python 3.10+ and PostgreSQL running in Docker with a port published to your host
- The PostgreSQL credentials used by the backend
- Internet access on first run to install DuckDB's `postgres` extension

## Setup (from repository root)

```bash
python -m venv .venv
# macOS/Linux:
source .venv/bin/activate
# Windows PowerShell:
# .venv\\Scripts\\Activate.ps1
pip install -r analytics/duckdb/requirements.txt
```

Set the connection settings in your shell (use values from your existing backend .env; **do not commit passwords**):

```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=your_database
export POSTGRES_USER=your_username
export POSTGRES_PASSWORD=your_password
python analytics/duckdb/refresh.py
```

On Windows PowerShell, use `$env:POSTGRES_DB="..."` (and similarly for the other variables) instead of `export`.

If you run Python inside a Docker container on the same Compose network, set `POSTGRES_HOST=postgres` instead of `localhost`.

The script creates `analytics/duckdb/leap_analytics.duckdb` and prints totals by segment, instrument and month. It copies only the columns needed for BR-16, omitting emails, passwords and sessions. Run it again to refresh the data. The file should remain local and **must not be committed**.

## Query examples

```sql
SELECT COUNT(*) AS total_trades FROM gold.fact_trade;
SELECT client_segment, COUNT(*) AS trades
FROM gold.fact_trade GROUP BY client_segment ORDER BY client_segment;
SELECT symbol, COUNT(*) AS trades
FROM gold.fact_trade GROUP BY symbol ORDER BY symbol;
SELECT trade_month, COUNT(*) AS trades
FROM gold.fact_trade GROUP BY trade_month ORDER BY trade_month;
```

You can run queries with Python's DuckDB API:

```python
import duckdb
con = duckdb.connect("analytics/duckdb/leap_analytics.duckdb", read_only=True)
print(con.execute("SELECT count(*) FROM gold.fact_trade").fetchall())
con.close()
```

## Important limitations

- This is **manual full-snapshot ingestion**, not CDC or automatic scheduling. Run the script after new trades are committed to PostgreSQL. Snapshots are not guaranteed to reflect one PostgreSQL transaction boundary across all five source tables; schedule refreshes outside active trading for this prototype.
- The current OLTP `fills` table has no executed-quantity field. As in the Databricks prototype, this script uses `orders.requested_quantity` per fill. It is valid for the seed data's single full fill per order, but **incorrect for partial or multiple fills**. Add actual filled quantity before production use.
- Notional values use each instrument's quote currency. Never add amounts from different currencies without conversion.
- Do not open the same DuckDB file for concurrent writes from multiple processes. An eventual Spring Boot API should use a read-only connection or a separate service, coordinated with refresh.
- Keep the Databricks SQL files as a separate implementation; this DuckDB model uses equivalent reporting columns, not the same SQL dialect or star-schema dimension keys.
- This is not yet integrated into Spring Boot or Angular. Authentication/authorization for analytics endpoints is required before exposing cross-client statistics.
