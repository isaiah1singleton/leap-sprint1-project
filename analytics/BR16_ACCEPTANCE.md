# BR-16 acceptance record

Documentation date: 15 October 2026. This record covers the implemented reporting
scope described in this repository, not formal business-owner sign-off.

## Requirement traceability

| Capability | Implementation | Acceptance evidence |
|---|---|---|
| Trading activity over time | activity_by_period.sql joins fact_trade to dim_date; year/month, fill count, summed quantity | Exact fixture assertions; user reported matching Docker/database results |
| Activity by instrument | activity_by_instrument.sql joins dim_instrument; count, quantity and notional grouped by symbol/asset class/currency | Exact decimal fixture assertions; user reported dashboard reports present |
| Activity by client segment | activity_by_client_segment.sql joins dim_client; fill count by current segment | Fixture assertions and user-reported seed baseline parity |
| Preserve fact/dimension relationships | Six Gold tables; stable source-to-surrogate mappings; orphan/duplicate checks | Migration, backdated-fill and key-restoration tests |
| Isolate reporting from live trading | Read-only extraction; dashboard queries on immutable DuckDB snapshots; separate metadata DB | Configuration inspection and deployment reports; production OLTP impact benchmark outstanding |
| Refresh analytics automatically | Ordered delta batches, systemd hourly job, immutable publication and activation | Local orchestration/recovery tests; user reported successful manual scheduler refresh |
| Operational safeguards | Dry-run retention and SMTP failure/recovery checks | Local tests; user supplied retention dry-run and reported enabling monitoring checks |

## Baseline results

The existing OLAP seed defines 12 executed fills, assuming a clean seeded database
without additional trades. Counts are fills, not distinct orders. Both BUY and
SELL quantities/notionals are positive activity measures, not net cash flow.

| Segment | Fill count |
|---|---:|
| RETAIL | 5 |
| PREMIUM | 3 |
| PROFESSIONAL | 4 |

| Period (UTC) | Fill count | Total quantity |
|---|---:|---:|
| August 2026 | 2 | 10.10 |
| September 2026 | 5 | 1655.00 |
| October 2026 | 5 | 55.25 |

Quantity sums reproduce existing queries; adding quantities of unlike instruments
is an activity statistic, not an economically comparable exposure measure.

| Instrument | Quote currency | Fill count | Quantity | Notional |
|---|---|---:|---:|---:|
| AAPL | USD | 5 | 157 | 35696.80 |
| MSFT | USD | 2 | 13 | 6667.00 |
| BARC | GBP | 2 | 550 | 1794.00 |
| GBPUSD | USD | 1 | 1000 | 1342.00 |
| BTCUSD | USD | 2 | 0.35 | 22710.00 |

Do not combine currencies into one monetary total. Tied segment-count rows may
appear in different orders; compare result sets and decimal values, not JSON
row ordering.

## Automated validation

On 15 October 2026, the current local suite completed with **22 tests passing**:

```bash
python -m unittest discover -s analytics/proof/tests -v
```

Use a virtual environment with proof/requirements.txt installed. Scheduler tests
are skipped on Windows because scheduling targets Linux; SMTP tests use mocks.
Coverage includes baseline reports, full/delta parity, insert/correction/delete
handling, backdated fills, empty deltas, stable keys, replay, conflicting IDs,
missing-parent rejection, transaction rollback, checkpoint recovery, legacy batch
migration, snapshot refresh isolation, read-only access, retention protection,
orchestrator retries/locking, email deduplication/recovery/TLS and Compose detection.

## Deployment evidence and limits

The user reported successful Docker builds/runs, seeded PostgreSQL parity,
Superset dashboard rendering, insert/correction/delete tests on an isolated
copy, and a successful manual refresh after legacy image/batch issues were fixed.
The user also reported enabling monitoring checks. These are conversation-based
observations; no EC2 logs, screenshots or SMTP receipts were copied into this repo.

A retention dry-run supplied by the user showed no eligible deletions, no unknown
artifacts, and protected fixture, seeded and hourly generations. This verifies
that run's plan, not a destructive cleanup or daily retention schedule.

## Evidence still to capture for formal acceptance

- Timestamped baseline reports and screenshots of all three dashboard tables.
- First successful unattended hourly run: timer status, service journal and
  last-success.json with advancing timestamps.
- Live test email received by BOTH recipients; observed failure and recovery
  delivery. Local mocks do not prove Gmail/EC2 connectivity or inbox delivery.
- Reviewed retention plan and, if desired, a verified manual apply on backed-up
  analytics volumes. Automatic pruning is not enabled by this implementation.
- Backup/restore rehearsal of coordinated ETL state, batches, working Gold and
  Superset metadata; measured restore time.
- Extraction/runtime/resource measurements during representative trading load.
- Business-owner confirmation of timezone and current-attribute semantics, and
  acknowledgment of the requested-quantity/partial-fill limitation.

Store sanitized evidence under your team's chosen project documentation location.
Never include .env, monitor.json, passwords, session tokens or SSH private keys.

## Scope boundaries

The implemented star and metric semantics are validated against the seed
baseline and full-refresh proof; external warehouse parity is not claimed.
There is no source CDC, SCD Type 2 history, FX conversion, execution-fill quantity
model correction, zero-downtime dashboard activation, external host-outage alert,
or production availability/performance guarantee. These are follow-up work, not
implied acceptance of this prototype.
