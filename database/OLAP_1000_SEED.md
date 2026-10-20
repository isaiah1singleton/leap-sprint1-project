# 1,000-trade analytics dataset

Open `olap_1000_trade_seed.sql` in pgAdmin Query Tool connected to the isolated
development database `jayson-trading-dev`, confirm the database name, and execute
the entire file. Change its expected_database safety guard only if deliberately
using another isolated test database. Tables must match transaction_schema.sql;
do not recreate the existing schema to load data.

The script adds 1,000 executed fills/orders using separate non-login clients and
dedicated accounts. It spreads synthetic activity from January to September 2026
in UTC, with varied quantities and prices across five instruments, BUY/SELL,
three client segments and two quote currencies. Lifecycle events and signed
trade cash movements are included. Holdings, balances and FIFO state are not;
these are analytics fixtures, not executable trading accounts.

It is deterministic and repeatable: a second run creates no duplicates. It never
deletes the existing 12-fill baseline or other records. If that baseline is also
loaded, the dashboard will contain **1,012 fills**, plus any other source trades.
The default ETL includes all fills, not only bulk fixture accounts.

| Bulk segment | Fills |
|---|---:|
| RETAIL | 419 |
| PREMIUM | 249 |
| PROFESSIONAL | 332 |

| Bulk instrument | Fills |
|---|---:|
| AAPL | 417 |
| MSFT | 167 |
| BARC | 167 |
| BTCUSD | 166 |
| GBPUSD | 83 |

After SQL succeeds, run your configured analytics refresh on the Docker host:

```bash
python3 analytics/scheduler/refresh.py
```

Wait for snapshot activation to complete, then refresh Superset. The analytics
source must point to the seeded database. Do not change a production delta chain
to another source database; keep separate source deployments and volumes.

Validation used an isolated embedded PostgreSQL engine against the current
branch schema: 1,000 fills, 3,000 lifecycle events, 1,000 trade cash movements,
duplicate prevention, expected segment/instrument counts, coexistence with the
12-fill seed and wrong-database rejection. This is correctness validation, not
a load/performance benchmark or a run against your deployed PostgreSQL server.
