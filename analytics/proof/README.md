# Standalone DuckDB BR-16 proof

This full-load proof preserves the **implemented** Databricks Gold star: one
`gold.fact_trade` row per fill, joined to `dim_client`, `dim_account`,
`dim_instrument`, `dim_date`, and `dim_time`. Original Databricks SQL and the
older flattened DuckDB prototype are unchanged. It is not incremental ETL or
a deployed Superset integration.

## Run offline first

From the repository root:

```sh
python3 -m venv .venv
.venv/bin/pip install -r analytics/proof/requirements.txt
.venv/bin/python -m unittest discover -s analytics/proof/tests -v
.venv/bin/python analytics/proof/proof.py --fixture --db /tmp/leap-proof.duckdb
```

The offline fixture is an analytical projection of the existing 12 seed trades.
It never connects to PostgreSQL. The command prints all three existing BR-16
query results as JSON; decimals are serialized as strings without losing digits.

## Read a PostgreSQL snapshot

Set `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and
`POSTGRES_PASSWORD` in your environment, then run:

```sh
.venv/bin/python analytics/proof/proof.py --db /tmp/leap-postgres-proof.duckdb
```

Prefer a dedicated SELECT-only PostgreSQL role provisioned separately. This
program enforces a read-only, repeatable-read source transaction, uses bounded
fetch batches and query/lock timeouts, and does not execute source DDL, seed
inserts, or application migrations. Extraction still uses source resources;
run against an isolated seeded database first. No Compose changes are required.

Use a dedicated output file: each successful run replaces its Bronze, Silver,
and Gold tables. Do not run simultaneous readers/writers on that file. Local
updates and validation are transactional; a validation error rolls back the
refresh. On success the file is checkpointed and closed. It can then be opened
read-only using DuckDB. Keep output files outside the repository.

## Contract and limitations

- Bronze copies only explicitly listed analytical columns; no emails,
  passwords, or sessions. Silver and Gold are local to this standalone proof.
- UTC is explicit for source and target. Confirm the existing Databricks
  session timezone before claiming production parity.
- Source quantities/prices are staged as DECIMAL(18,8); larger or higher-scale
  source values are outside this proof's supported precision contract. Silver
  notional remains DECIMAL(24,8), with wider multiplication intermediates.
- Quantity remains requested order quantity per fill, matching existing SQL.
  Partial/multiple fills require a separate business-model correction.
- Surrogate keys retain full-refresh ROW_NUMBER semantics. They are not stable
  across changing snapshots; incremental deployment needs persistent mappings.
- Dimensions include only entities represented in executed trades and use
  current attributes, matching the implemented SQL, not the richer ER diagram.
- Original reporting SQL is reused with only `leap.gold` changed to `gold`.
  Currency totals stay grouped by instrument and quote currency.
- Validation rejects missing/duplicated fills and orphan dimension references.
  The offline tests verify counts, exact quantities/notionals, date/time keys,
  repeatable refreshes, and rollback. They do not prove live PostgreSQL or actual
  Databricks parity; compare the JSON reports against the same source snapshot.

Expected fixture checks: 12 facts; segments RETAIL=5, PREMIUM=3,
PROFESSIONAL=4; months August=2, September=5, October=5. Instrument notionals:
AAPL USD 35696.80, MSFT USD 6667.00, BARC GBP 1794.00,
GBPUSD USD 1342.00, BTCUSD USD 22710.00.
