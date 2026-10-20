# Gold snapshots and Superset BR-16 dashboard

The warehouse publishes immutable Gold-only DuckDB generations. Superset uses
duckdb-engine with read_only=true and a read-only volume. Its PostgreSQL metadata
database is separate from OLTP. The pinned Superset 5.0 image is extended with
DuckDB dependencies; upgrade the image and bootstrap together and rerun checks.
The initial dashboard contains three table charts with exact existing BR-16
aggregates, including currencies. Chart tables have a 10,000-row display limit;
the underlying views aggregate all facts. This is synchronous local reporting,
without Redis, Celery, scheduled emails, or incremental ingestion.

## First deployment (Bash, repository root)

Keep your existing analytics/.env. Add these new keys from .env.example:

```dotenv
SNAPSHOT_ID=snapshot-001
SUPERSET_SECRET_KEY=replace-with-at-least-32-random-characters
SUPERSET_DB_PASSWORD=replace-with-a-strong-independent-password
SUPERSET_ADMIN_USERNAME=admin
SUPERSET_ADMIN_PASSWORD=replace-with-a-strong-independent-password
SUPERSET_ADMIN_EMAIL=you@example.com
SUPERSET_PORT=8088
```

Generate secrets locally using `openssl rand -hex 32`. Keep the secret key stable
across restarts; do not commit your .env. Changing the metadata password in .env
does not change the password of an already initialized metadata database.

Build and publish your previously extracted seeded batch. BATCH_ID must still
identify that existing batch; extraction need not be repeated:

```bash
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi build
docker compose --env-file analytics/.env -f analytics/compose.yml run --rm warehouse
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi up -d superset-db
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi run --rm superset-init
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi run --rm superset-bootstrap
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi up -d superset
```

Run sequentially and stop on any error. A successful warehouse run publishes
`/data/published/snapshot-001.duckdb`; an existing generation is never overwritten.
Bootstrap smoke-tests SQLAlchemy read-only connectivity, schema discovery, and
all three reporting views before updating metadata. It provisions the LEAP Gold
connection, three datasets/table charts, and LEAP BR-16 Trading Analytics dashboard.
Bootstrap uses the pinned version's internal models; Docker verification of
asset provisioning and actual chart rendering is required on your machine.

Open http://localhost:8088 and sign in with your configured admin. Open dashboard
`/superset/dashboard/leap-br16/`. Verify all three charts render the previously
verified counts, quantities, and currency-separated notionals.

For your EC2 machine, the port is bound to loopback. From your laptop establish:

```bash
ssh -i /path/to/key.pem -L 8088:127.0.0.1:8088 ec2-user@YOUR_EC2_HOST
```

Then use http://localhost:8088 on your laptop. Do not expose this development
configuration publicly; it does not configure HTTPS or production identity.

## Refresh and activate a new generation

Set new BATCH_ID and SNAPSHOT_ID values in analytics/.env. Run ETL then warehouse
as before. Superset continues reading the OLD snapshot during both jobs.
To activate the new generation, use a short controlled dashboard interruption:

```bash
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi stop superset
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi run --rm superset-bootstrap
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi up -d --force-recreate superset
```

Recreation removes old worker connections; query caching is disabled. Bootstrap
updates the same assets rather than duplicating them, so it overwrites edits to
these managed charts. Preserve custom charts under different names.
Keep old snapshots for rollback: select an existing SNAPSHOT_ID and run the same
stop/bootstrap/recreate sequence without rerunning the warehouse. Do not remove
snapshots while readers use them. No automatic retention policy is installed.

Publication failure leaves any previous published generation intact; Gold may
already have committed. Choose a new ID and rerun warehouse against the same
immutable batch. Only one warehouse writer job may run at a time.

## Checks and troubleshooting

```bash
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi ps
docker compose --env-file analytics/.env -f analytics/compose.yml --profile bi logs --tail 100 superset
```

Check the three charts rather than only the web server health page. If snapshot
access fails, confirm warehouse publication finished and SNAPSHOT_ID matches.
Never run `down -v`; it deletes analytics/metadata volumes. The OLTP Compose file
and volume are not changed. Docker image builds and browser rendering cannot be
verified in environments without Docker; local tests cover snapshot parity,
read-only access, refresh isolation, and overwrite rejection.
