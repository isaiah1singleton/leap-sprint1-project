# Hourly refresh on EC2 (Linux systemd)

The host runner orchestrates existing containers; it does not add a data-processing
container or change OLTP. Each successful cycle extracts a stateful batch, loads
Gold, publishes a new snapshot, stops Superset briefly, updates its connection,
recreates web workers, and checks web health. It refreshes managed dashboard
assets as the existing bootstrap does. Existing snapshot readers remain isolated
from warehouse writes.

## Install on your EC2 machine

Requires Python 3.9+, Docker Compose v2, and ec2-user permission to run Docker.
Superset initialization and the first working dashboard must already exist.
Use the production analytics/.env, not the isolated test configuration. Clear
stale exported POSTGRES_* / SUPERSET_* values if running manually. The service
reads configuration through Compose's --env-file and does not print credentials.
Keep SUPERSET_SECRET_KEY stable.

After pulling the code, rebuild ETL/warehouse and test one complete cycle:

```bash
docker compose --env-file analytics/.env -f analytics/compose.yml build etl warehouse
python3 analytics/scheduler/refresh.py
```

The runner uses unique IDs automatically. Confirm the dashboard still renders
your baseline at localhost:8088 through your SSH tunnel. Success writes
`analytics/runtime/last-success.json`; runtime state is ignored by Git.

Review the supplied service file before installing. It assumes user ec2-user,
repository /home/ec2-user/leap-sprint1-project, and /usr/bin/python3. Adjust those
paths if your checkout differs (`pwd` and `command -v python3`). Then:

```bash
sudo cp analytics/scheduler/leap-analytics.service /etc/systemd/system/
sudo cp analytics/scheduler/leap-analytics.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now leap-analytics.timer
systemctl list-timers leap-analytics.timer
```

Runs occur at the top of each hour in the EC2 host's timezone. Persistent=true
triggers one catch-up run after a missed scheduled activation, not one per missed
hour. The timer does not launch another instance of an active service; a host
file lock also prevents overlapping manual invocations. Long cycles delay fresh
data beyond an hour; check logs rather than assuming every cycle completed.

## Observe and retry

```bash
systemctl status leap-analytics.timer
systemctl status leap-analytics.service
journalctl -u leap-analytics.service -n 100 --no-pager
cat analytics/runtime/last-success.json
```

Failures leave pending.json with the batch ID and stage. The next hourly run
retries that stage with the SAME IDs, not a new delta. After fixing the cause:

```bash
sudo systemctl start leap-analytics.service
```

ETL recovers published-but-uncheckpointed batches. Warehouse replay is idempotent;
`--reuse-publication` accepts an existing snapshot only if all six Gold tables
match current Gold exactly. This covers a crash after publication but before
the runner records stage completion. A failed bootstrap still attempts to restart
Superset; a health failure keeps activation pending for retry.

Do not manually extract/load additional production batches while a scheduled
cycle is pending; it would change the delta chain or current Gold. Do not delete
pending.json or checkpoint volumes to bypass an error. Inspect Docker jobs before
retrying after forced host/process termination. The schedule uses the fixed
Compose project leap-analytics; do not point it at isolated test volumes.

To pause before manual maintenance:

```bash
sudo systemctl stop leap-analytics.timer
systemctl status leap-analytics.service
```

Wait for any active cycle to finish. Resume with `sudo systemctl start
leap-analytics.timer`. The runner overrides BATCH_ID/SNAPSHOT_ID for its child
commands; analytics/.env is not rewritten. For manual dashboard provisioning,
select the generation recorded in last-success.json explicitly.

## Operational limits

This does not install automatic notifications or pruning. Review failed service
status/journal and monitor disk space: retained snapshots and full source batches
grow hourly. Keep checkpoint-referenced batches and snapshots needed for rollback;
design retention before unattended long-term operation. A web health check proves
HTTP availability, not browser chart rendering. Docker/EC2 installation must be
verified on your machine; local tests simulate orchestration failures/retries.
