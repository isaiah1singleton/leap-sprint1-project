# Failure and recovery emails via Gmail

The monitor checks every 15 minutes. It alerts on source snapshot age over 90
minutes, a failed refresh service, an inactive hourly timer, Superset health
failure, stuck pending state, and disk free space below 10% or 1 GiB. It suppresses
Superset downtime during the first five minutes of known snapshot activation.
It sends one email when issue categories change and a recovery email after all
issues clear. It does not send routine successful-refresh emails. Failed SMTP
delivery is retried on the next check; partial delivery/restarts can cause duplicates.

## Configure on EC2

After pulling this code, from the repository root:

```bash
cp -n analytics/scheduler/monitor.example.json analytics/monitor.json
chmod 600 analytics/monitor.json
nano analytics/monitor.json
```

Replace sender and username with ONE Gmail sending account. Keep both configured
recipient addresses. Replace password with that account's Google app password,
not its normal password. Google requires 2-Step Verification for app passwords:
https://support.google.com/mail/answer/185833. App passwords are unavailable for
some account/security configurations; if so, use another permitted SMTP service
or implement OAuth rather than weakening account security. Gmail SMTP uses
smtp.gmail.com with STARTTLS on port 587:
https://developers.google.com/workspace/gmail/imap/imap-smtp.

The local monitor.json is ignored by Git and excluded from Docker build contexts.
Do not paste its contents or password into chat. No email is sent until you
configure credentials and invoke the monitor/test command. EC2 needs outbound
network access to Gmail port 587. Neither PostgreSQL nor Superset needs these
credentials; this host-only script uses the Python standard library.

Send one test email to BOTH recipients:

```bash
python3 analytics/scheduler/monitor.py --test-email
```

Confirm both inboxes (and spam folders). SMTP acceptance does not guarantee inbox
delivery. Then run a health check:

```bash
python3 analytics/scheduler/monitor.py
```

It prints a JSON report and saves analytics/runtime/monitor-status.json. Exit 1
means an issue was found; this is intentional, so systemd marks unhealthy checks
failed. Initial healthy state is quiet. If configuration is absent, monitoring
still logs issues but reports email_not_configured. Notification state is recorded
only after successful delivery, so retry does not silently lose an alert.

## Install the monitoring timer

Confirm the supplied service paths match /home/ec2-user/leap-sprint1-project and
/usr/bin/python3. The host user must have Docker access. Then:

```bash
sudo cp analytics/scheduler/leap-analytics-monitor.service /etc/systemd/system/
sudo cp analytics/scheduler/leap-analytics-monitor.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now leap-analytics-monitor.timer
systemctl list-timers leap-analytics-monitor.timer
journalctl -u leap-analytics-monitor.service -n 60 --no-pager
```

The hourly refresh timer remains separate. Refresh, retention, and monitoring
auto-detect docker compose or docker-compose; no manual source edits are needed.
Use --disk-path to monitor the filesystem holding Docker volumes if it differs
from root, and --max-age-minutes to adjust freshness. Put those flags in the
installed monitoring service ExecStart and reload systemd.

## Limits

Email is sent by the same EC2 machine being monitored. Complete host/network
outage cannot send an alert; external uptime monitoring is needed for that.
Availability checks cover the Superset health endpoint, not chart rendering or
analytical correctness. Snapshot freshness uses scheduler generation time so
retries of an old source batch do not masquerade as fresh data. Disk checks default
to root; ensure that covers Docker's actual data filesystem.
Tests mock SMTP and runtime probes; live Gmail delivery and systemd behavior must
be verified on EC2. No credentials or deployment changes were made by local tests.
