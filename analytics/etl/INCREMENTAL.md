# Snapshot-diff batches

Compose now runs ETL with `--state /data/state/checkpoint.duckdb`, stored in a
new ETL-owned named volume. The first run creates a version-2 full baseline;
subsequent runs produce ordered version-2 deltas. Old version-1 full batches
and commands without `--state` remain supported.

## What is incremental

Each extraction still reads a consistent read-only PostgreSQL snapshot and
recomputes Silver. SQL EXCEPT compares complete typed Silver rows with the last
persisted snapshot. This catches new, late-committed and backdated fills,
corrections and current dimension-attribute changes. Missing fill IDs become
explicit deletion tombstones. No MAX(id) or source timestamps are used as a
lossless watermark. Failed joins fail validation instead of silently deleting
unjoinable fills.

Each immutable batch retains projected Bronze Parquet, a complete Silver state
snapshot, changed Silver rows, deletion IDs, and a checksummed manifest. The
checkpoint references the previous batch on the same mounted batches volume.
Do not remove that batch or move the volume paths independently of ETL state.
This prototype deliberately retains full snapshots for exact verification;
source reads, storage, and dimension reconciliation are still full-sized.

Warehouse applies changed Silver rows and deletion IDs, verifies the merged
result exactly against the state snapshot, reconciles dimensions, and upserts
only affected fact rows. All data, key registries, batch registration, and head
movement commit together. The Gold star schema and BR-16 metrics are unchanged.

## Deploy on your Docker machine

Back up analytics volumes first. Keep existing published snapshots and the
Superset metadata volume. After pulling the code:

```bash
docker compose --env-file analytics/.env -f analytics/compose.yml build etl warehouse
```

Set NEW BATCH_ID and SNAPSHOT_ID values in analytics/.env. Run sequentially:

```bash
docker compose --env-file analytics/.env -f analytics/compose.yml run --rm etl
docker compose --env-file analytics/.env -f analytics/compose.yml run --rm warehouse
```

The first stateful manifest reports version=2 and mode=full. It reconciles the
existing Gold while retaining its keys. Compare reports with your baseline.
Set another pair of new IDs and repeat without changing PostgreSQL. The next
manifest should report mode=delta, upsert_count=0, delete_count=0, and unchanged
fill_count. Gold reports should match. To test changes, use an isolated test
database and verify against a fresh full proof; never insert test trades into
the live database merely to exercise ETL.

Activate published snapshots using the existing Superset stop/bootstrap/recreate
procedure. Dashboard readers stay on the previous generation until activation.

## Ordering and recovery

- Only one ETL state writer and one warehouse writer may run at a time. DuckDB
  file locks enforce state ownership, not a distributed scheduler.
- Apply batches in their manifest parent order. ETL checkpoints publication,
  not warehouse acknowledgment. You may queue batches, but never skip a parent.
- Publication precedes checkpoint advancement. If ETL fails after publishing,
  retry the SAME batch ID/path; it checks files and completes the checkpoint
  without re-extracting. Failure before publication leaves the checkpoint intact.
- Retrying a successfully checkpointed batch returns its manifest. Warehouse
  replay stays idempotent; changed content under an applied ID is rejected.
- If warehouse fails, retry the same batch before loading later batches. If
  Gold committed but snapshot publication failed, choose a new SNAPSHOT_ID and
  retry warehouse; it skips reapplication and publishes current Gold.
- If a parent is missing, apply the retained batches in order. Do not edit head
  or checkpoint records to bypass the check. Restoring ETL state, batches and
  warehouse together is the preferred recovery. A deliberately reset ETL state
  creates a new full reconciliation and establishes a new chain; use unique IDs
  and coordinate this explicitly rather than deleting volumes casually.
- Do not mix independent ETL checkpoint databases into one warehouse or interleave
  ad hoc legacy full loads with an active delta chain. A full load establishes a
  new warehouse head and older-chain deltas will be rejected.

## Verification

```bash
.venv/bin/python -m unittest discover -s analytics/proof/tests -v
```

Tests cover corrections, new/backdated fills, segment changes, deletions, no-op
deltas, source-snapshot parity, stable trade keys, replay, missing-parent rejection,
transaction rollback and publication/checkpoint recovery. Docker builds and live
database runs still require your Docker machine. The existing requested-quantity,
UTC and decimal-precision limitations remain; this is snapshot differencing,
not CDC or efficient incremental PostgreSQL extraction.
