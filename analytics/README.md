# LEAP Trading Analytics — BR-16

DuckDB is the analytical warehouse and Apache Superset serves reporting for the
LEAP trading platform. PostgreSQL remains the system of record for trading.
BR-16 is implemented as activity reporting over time, by instrument, and by client
segment, using the original implemented Gold star schema.

## Architecture

```text
PostgreSQL OLTP (existing trading stack)
    | SELECT-only, repeatable-read snapshot
    v
ETL container: Bronze projections -> Silver executed trades
    | immutable Parquet batches + manifests + deletion IDs
    v
DuckDB owner container: retained Silver + Gold star + pipeline state
    | checkpointed, closed, immutable Gold-only generations
    v
Superset: embedded DuckDB read-only queries -> BR-16 dashboard
    |
    +-- dedicated PostgreSQL metadata database (separate from OLTP)

EC2 systemd: hourly refresh orchestration; health/email checks every 15 minutes
```

The ETL and warehouse containers run as sequential jobs, not database servers.
DuckDB has no PostgreSQL-compatible listener in this deployment. Superset uses
`duckdb-engine` to execute queries locally against a published file. Only the
warehouse writes the working Gold database. Superset never opens that working
file or queries PostgreSQL OLTP. Source extraction still consumes OLTP resources;
no production trading-load impact benchmark has been captured.

## Data contract

Bronze contains analytical projections of clients, accounts, instruments, orders,
and fills, excluding credentials, sessions and unrelated personal data. Silver
joins these sources at one row per executed fill. Missing joins and duplicate
fill IDs fail validation.

| Gold table | Key / relationship | Purpose |
|---|---|---|
| fact_trade | trade_key; lineage fill_id/order_id | One row per fill; quantity, price, notional and side |
| dim_client | client_key; source_client_id | Current client segment |
| dim_account | account_key; source_account_id | Source account lineage |
| dim_instrument | instrument_key; source_instrument_id | Symbol, name, asset class, market, quote currency |
| dim_date | date_key = YYYYMMDD | Execution date attributes |
| dim_time | time_key = HHMMSS | Execution hour, minute and second |

Fact foreign-key columns are client_key, account_key, instrument_key,
execution_date_key and execution_time_key. Relationships are validated explicitly;
CTAS-generated tables do not declare physical foreign-key constraints. Persistent
pipeline key registries retain existing surrogate keys across reconciliations.
Dimensions use current attributes, not SCD Type 2 history.

Execution dates/times use UTC. Quantity and price are DECIMAL(18,8); notional is
DECIMAL(24,8), with wider multiplication intermediates. Trade quantity currently
comes from orders.requested_quantity because fills has no executed quantity.
This preserves the existing prototype calculation, but does not correctly model
partial/multiple fills. Monetary totals remain separated by quote currency;
there is no FX conversion or portfolio valuation feature here.

## Incremental behavior

Each ETL run reads a consistent full source snapshot, recomputes Silver and
compares it to retained Silver. The first stateful batch is full; subsequent
batches contain new/changed fills and explicit deletion IDs. This is snapshot
differencing, not CDC or reduced-volume PostgreSQL extraction. It handles late
commits without relying on MAX(fill_id).

The warehouse requires parent batches in order, merges Silver, reconciles
dimensions, and updates only changed/deleted Gold facts. It checks merged Silver
against the complete source snapshot. Key updates, data, batch registration and
warehouse head commit together. Duplicate delivery is a no-op; changing content
under an applied batch ID is rejected. Snapshots activate through a controlled
Superset restart; this creates a brief dashboard interruption.

## Documentation and entry points

| Need | Reference |
|---|---|
| BR-16 acceptance, expected results and evidence gaps | [Acceptance record](BR16_ACCEPTANCE.md) |
| Standalone proof and offline test setup | [Proof](proof/README.md) |
| Initial ETL/warehouse Docker deployment | [Docker](DOCKER.md) |
| Stateful extraction, delta ordering and recovery | [Incremental pipeline](etl/INCREMENTAL.md) |
| Stable keys and batch replay | [Warehouse](warehouse/README.md) |
| Superset setup, SSH access and rollback | [Superset](superset/README.md) |
| Hourly orchestration and systemd installation | [Scheduler](scheduler/README.md) |
| Dry-run/apply retention | [Retention](scheduler/RETENTION.md) |
| Gmail alerts and monitoring installation | [Monitoring](scheduler/MONITORING.md) |
| Incident response and evidence collection | [Operations](OPERATIONS.md) |

DuckDB SQL in proof/sql/ is the analytical transformation source of truth;
queries/ contains native DuckDB BR-16 reporting SQL. The proof directory also
supplies shared extraction/validation helpers and offline regression tests used
by the deployed containers; it is an active dependency, not a legacy pipeline.
OLAP_diagram.mmd describes additional attributes/history not present in the
implemented SQL; do not treat those as delivered features.
