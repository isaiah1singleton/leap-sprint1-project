# Analytics containers

Run all commands from the repository root. Requires Docker Desktop with Compose
v2 and a running PostgreSQL container. These are separate one-shot analytics jobs;
they do not manage, restart, or mount the existing OLTP database volume.

## Configure (PowerShell)

```powershell
Copy-Item analytics/.env.example analytics/.env
docker network ls
```

Edit `analytics/.env` locally. Set OLTP_NETWORK to the existing network containing
PostgreSQL (commonly `backend_default`). To inspect its exact networks:

```powershell
docker ps
docker inspect --format '{{json .NetworkSettings.Networks}}' YOUR_POSTGRES_CONTAINER
```

Set POSTGRES_HOST to its network alias (usually `postgres`), POSTGRES_PORT to
the internal port `5432`, and the database credentials to your existing values.
Use a SELECT-only role if available. Do not use `localhost` to refer to another
container. Published host ports are not used on this shared network.
Do not copy backend/.env blindly: its POSTGRES_PORT can be the published host
port. The analytics .env is ignored by Git and excluded from build contexts.
Compose uses it only when explicitly passed with --env-file as below.
Shell variables override .env values: clear stale POSTGRES_* variables from
earlier local runs before using these commands.

## Build and verify offline

```powershell
docker compose --env-file analytics/.env -f analytics/compose.yml build
docker compose --env-file analytics/.env -f analytics/compose.yml run --rm etl --fixture --output /data/batches/fixture-001
docker compose --env-file analytics/.env -f analytics/compose.yml run --rm warehouse --batch /data/batches/fixture-001 --db /data/warehouse/fixture.duckdb
```

The existing OLTP network must exist even for fixture ETL. Fixture mode never
connects to PostgreSQL. Expect 12 trades, segment counts 5/3/4 and month counts
2/5/5; see proof/README.md for exact monetary expectations. Re-running extraction
with the same batch name fails intentionally; choose a new name.

## Extract your seeded database and load Gold

Set BATCH_ID in analytics/.env to a new value such as seeded-001, then run:

```powershell
docker compose --env-file analytics/.env -f analytics/compose.yml run --rm etl
docker compose --env-file analytics/.env -f analytics/compose.yml run --rm warehouse
```

Run the second command only after extraction succeeds. It prints all BR-16
reports; compare against your verified standalone proof on the same source data.
Use these same commands on macOS/Linux. Do not use `up` to launch both jobs in
parallel; do not start simultaneous warehouse jobs.

## Ownership and limits

- ETL owns Bronze/Silver batch production. Its volume retains Parquet and manifests.
- Warehouse alone writes Gold. It mounts batches read-only, receives no database
  credentials, and has networking disabled.
- Both images run as UID 10001. Fresh named volumes inherit the image directory
  permissions. Pre-existing volumes with other ownership may require an explicit
  administrator-managed permission adjustment; do not delete them to fix this.
- Images use pinned Python and Python-package versions. Build context excludes
  credentials, DuckDB files, batch data, and unrelated application files.
- Memory and CPU caps bound container resources, but extraction still consumes
  PostgreSQL resources. Large datasets may need tuning; ETL currently uses an
  in-memory transformation database.
- Gold persists in the analytics gold volume. This is a working database, not
  a concurrently readable Superset snapshot. The warehouse now also publishes a
  separate immutable generation selected by SNAPSHOT_ID. See
  `analytics/superset/README.md` for dashboard deployment and generation switching.
- This is full refresh with the proof's UTC, decimal precision, requested-fill
  quantity semantics for source extraction. Default ETL now retains state and
  produces full/delta batches; see `analytics/etl/INCREMENTAL.md`. Gold keys are
  stable. Source-side CDC and automatic scheduling are not implemented.

Do not run `down -v`: it would delete analytics data volumes. Nothing here
requires taking down the trading stack. Do not print resolved Compose config
into shared logs, since it contains the PostgreSQL password.
