# Stable Gold keys and batch replay

The loader now maintains `pipeline.client_keys`, `account_keys`,
`instrument_keys`, and `trade_keys`. These map source IDs to warehouse keys and
are retained even when a full reconciliation removes an entity from Gold.
New keys are allocated above the registry maximum; existing keys never change.
Date/time keys retain their original deterministic format.

On first use of an existing Gold database, its keys are imported before any
tables are rebuilt. A conflicting/invalid registry or legacy schema fails and
rolls back instead of silently changing references. Back up the working Gold
volume before deploying. The original proof is still a full-refresh baseline
and does not use these registries.

ETL manifests now include `batch_id`. For older manifests the directory name
is used, so legacy batches remain loadable. Keep this ID globally unique within
the warehouse. `pipeline.applied_batches` records the ID, a content/contract
fingerprint, and application time in the same transaction as Gold updates.

- A previously applied ID with the same fingerprint returns current reports
  without reapplying the old snapshot. In particular, replaying an older batch
  does not undo newer warehouse state.
- Reusing an applied ID with different Silver content or contract is rejected.
- A failed load rolls back both key allocations and batch registration. Retrying
  it can apply normally.
- A new ID represents a new full snapshot, even if its content matches a prior
  batch. Operators must submit newly extracted snapshots in chronological order;
  this step does not implement a source offset or reject out-of-order new IDs.

## Verify/deploy

```bash
.venv/bin/python -m unittest discover -s analytics/proof/tests -v
docker compose --env-file analytics/.env -f analytics/compose.yml build etl warehouse
```

After backup, choose a new BATCH_ID and SNAPSHOT_ID in analytics/.env, then run
ETL followed by warehouse as documented in analytics/DOCKER.md. Existing
publication and Superset switching procedures are unchanged. Published snapshots
contain only Gold, not private key registries or application-state tables.
Do not run multiple warehouse writers concurrently.

## Scope of this step

This prepares for incremental loading but **still accepts only full batches**:
Gold tables are rebuilt within a transaction, then remapped to stable keys.
Dimensions retain current-attribute semantics; a new full batch propagates
corrections/deletions. No source extraction checkpoints, incremental upserts,
CDC, scheduler, or deletion tombstones are introduced yet. PostgreSQL is unchanged.
The next step is a versioned delta contract and persistent Bronze/Silver ETL state,
followed by fact upserts keyed by fill_id and explicit deletion handling.
