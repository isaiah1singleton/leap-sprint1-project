# Safe batch and snapshot retention

Cleanup defaults to a dry-run. The default policy keeps seven days of artifacts
and at least 24 newest batches and 24 newest snapshots, regardless of age.
It additionally protects the ETL checkpoint batch, warehouse head, pending and
last-success generations, every unapplied batch, and every DuckDB snapshot
referenced by Superset metadata. Unknown batch directories, hidden staging files,
symlinks and files that are not valid Gold-only snapshots are skipped.
Registry/application-history records and source data are never deleted.

## Verify on EC2

After pulling changes, rebuild images with the scheduler's project name:

```bash
docker compose -p leap-analytics --env-file analytics/.env -f analytics/compose.yml --profile bi build warehouse retention superset superset-bootstrap
python3 analytics/scheduler/cleanup.py
```

Inspect delete_batches, delete_snapshots, protected lists and skipped_unknown.
No data is removed. A recent installation should usually report no candidates.
To use another policy, add `--days 3 --keep 48`, for example.

When the dry-run plan is satisfactory, take a backup and apply the same policy:

```bash
python3 analytics/scheduler/cleanup.py --apply
```

The apply run recalculates its plan under the shared scheduler lock. It briefly
stops Superset, queries active snapshot paths using a separate metadata reader,
cleans eligible artifacts, then recreates Superset even if cleanup fails. This
closes pooled old readers before removing files. Dry-run does not stop Superset.
Cleanup fails closed if state, warehouse head, active metadata or active files
cannot be read. No active scheduled refresh can overlap it. Manual ETL,
warehouse jobs and ad hoc readers MUST also be stopped; the host lock cannot
coordinate commands launched outside the scheduler. Do not invoke the container's
retention entry point directly; use the host coordinator.

The new retention job has no network or PostgreSQL credentials. Only batch and
published volumes are writable; ETL state and working Gold are mounted read-only.
It does not touch trading PostgreSQL or Superset metadata. Directly opened
DuckDB readers outside Superset are not tracked; close them before apply.

If an active snapshot is older than seven days it remains protected. Its source
batch can expire if already applied and no longer checkpoint/head/pending; the
snapshot alone is sufficient for dashboard rollback. Parent manifests may refer
to removed, already-applied historical batches; the current checkpoint's complete
snapshot and all unapplied queued batches remain available for forward progress.
Restore coordinated backups for rollback of ETL/warehouse state.

## Scheduling

This change deliberately does not enable automatic deletion. Verify the dry-run
and one manual apply on your EC2 machine first. It is independent of the hourly
refresh timer. A separate daily cleanup schedule can be installed after that
verification. Disk consumption can still grow if queues are stuck, unknown files
accumulate, or metadata references many snapshots; inspect the protected/skipped
lists rather than assuming retention guarantees a fixed disk cap.

Tests exercise protected generations, dry-run behavior, actual temporary-file
cleanup, unknown artifacts, and missing-state failures. Docker/metadata discovery
and container-volume permissions must be verified on EC2.
