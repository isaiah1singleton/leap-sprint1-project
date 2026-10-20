# Portfolio performance history

History records real observations from enablement onward. It does not reconstruct past prices.
Portfolio value is the same cash-plus-holdings valuation used by the portfolio snapshot;
lifetime profit/loss subtracts cumulative net funding and remains unavailable when account
history does not reconcile. Synthetic provider quotes are excluded. Missing prices and
unavailable profit/loss are stored as nulls, not zero, and displayed as gaps.

## Existing databases

Before starting the updated backend, apply only the `CREATE TABLE IF NOT EXISTS
portfolio_observations` block at the end of `database/transaction_schema.sql` to the
existing PostgreSQL database. Do not rerun the entire schema or reset the database.
Fresh Compose databases initialize the table automatically. The app deliberately does
not manage database migrations. Back up existing data before applying schema changes.

## Recording

Compose enables `PORTFOLIO_HISTORY_ENABLED=true`; outside Compose set that environment
variable to enable background observations. Active accounts are sampled every five minutes.
The scheduler operates while the backend runs, including when the UI is closed. Provider
quota and availability can limit usable observations. No historical values are invented
for downtime. The history endpoint also records an observation when viewed/refreshed;
an account lock deduplicates observations to at most one per UTC minute and rejects stale
concurrent results. The observation timestamp is the account snapshot time; the oldest
quote timestamp is stored separately, since prices may be delayed.

`GET /api/accounts/{accountId}/portfolio-history?range=ALL` requires the owning client's
bearer session. Supported ranges are `1D`, `1W`, `1M` (30 days), and `ALL`. Range selection
does not rebase lifetime profit/loss. The Portfolio page refreshes history once a minute
while visible and after account activity. The charts support pointer/touch inspection and
Left/Right/Home/End/Escape keys. A lone observation renders as a point.

Observations persist in PostgreSQL across browser refreshes and backend restarts. Include
the table in existing database backups. No automatic history deletion is enabled.
