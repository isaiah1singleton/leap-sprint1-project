# Analytics operations and handover

Run commands from the repository root on EC2. Python scheduler scripts detect
both docker compose and docker-compose. For direct commands below, use the CLI
installed on your host; examples use docker-compose.

## Daily checks

```bash
systemctl list-timers leap-analytics.timer leap-analytics-monitor.timer
journalctl -u leap-analytics.service -n 60 --no-pager
cat analytics/runtime/last-success.json
cat analytics/runtime/monitor-status.json
```

Open the dashboard through a laptop SSH tunnel to the EC2 host, forwarding local
8088 to 127.0.0.1:8088. Superset is bound to EC2 loopback, not a public listener.
Check all three charts, not only HTTP health. Query freshness is hourly plus run
and activation time; failed jobs can extend that lag. Alerts check every 15 minutes.

## Pause and resume

```bash
sudo systemctl stop leap-analytics.timer
systemctl status leap-analytics.service
```

Wait for any active refresh to complete before manual loads, cleanup or backup.
Stopping a timer does not stop its active job. The scheduler lock coordinates
host scripts, not manually launched Docker jobs. During planned maintenance,
optionally pause the monitor timer to avoid expected downtime alerts; resume it
with the refresh timer afterward. No procedure requires stopping trading OLTP.

## Refresh failure

Read the Docker error before the final CalledProcessError:

```bash
journalctl -u leap-analytics.service -n 100 --no-pager
cat analytics/runtime/pending.json
```

Fix the cause and retry `python3 analytics/scheduler/refresh.py`. Keep the pending
state and batch unchanged. Outdated images require rebuilding under the SAME
Compose project name leap-analytics. The runner resumes the recorded stage; do
not create another production batch while recovery is pending. See the incremental
and scheduler guides for parent ordering and publication recovery.

## Dashboard rollback

Pause refreshes and wait for the active job. Select an existing immutable snapshot,
then use the Superset stop/bootstrap/recreate procedure with that SNAPSHOT_ID.
This changes dashboard serving only, not warehouse/ETL history. The next successful
scheduled refresh will activate its new generation unless the timer remains paused.
Retention reads active metadata and keeps the currently configured snapshot.

## Storage management

```bash
python3 analytics/scheduler/cleanup.py
```

Review its dry-run, take a backup, then use --apply only when satisfied. Apply
briefly restarts Superset. The default keeps seven days and at least 24 recent
generations, with additional protections for current state and unapplied batches.
Unknown artifacts and stuck queues can still grow disk usage. Do not use down -v
or generic volume-prune commands as analytics cleanup.

## Backup and restore

Coordinate backups rather than copying an actively written DuckDB file. Pause
refreshes, wait for jobs, and keep checkpointed working Gold, ETL checkpoint and
referenced batch directories together. Back up the separate Superset metadata
PostgreSQL database using its normal database backup procedure. Retain required
published snapshots and keep environment/SMTP configuration in secure separate
storage. Do not put credentials into Git or evidence bundles.

Restore the matching set to an isolated environment first, confirm permissions,
read-only snapshot queries, batch/head consistency and dashboard metadata, then
re-enable scheduled work. A restoration rehearsal and measured recovery objectives
remain acceptance evidence to collect; this document does not claim one occurred.

## Email troubleshooting

```bash
python3 analytics/scheduler/monitor.py --test-email
journalctl -u leap-analytics-monitor.service -n 60 --no-pager
```

Confirm both recipient inboxes. Configure one sender account's app password in
ignored analytics/monitor.json; never use the normal Gmail login password. Email
failures are retried; partial SMTP delivery can cause duplicates. No email is sent
for an unchanged issue set or ordinary healthy refresh. Host/network failure can
prevent email entirely, so external uptime monitoring is a separate improvement.

## Evidence checklist

Use BR16_ACCEPTANCE.md to collect sanitized reports, screenshots, automatic-run
logs, delivery evidence, retention review and backup/restore results. Mark evidence
as observed only after the corresponding action succeeds. Current automated tests
can be rerun with `python -m unittest discover -s analytics/proof/tests -v`.
