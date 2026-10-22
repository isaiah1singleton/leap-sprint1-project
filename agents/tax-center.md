# Tax Center

Open **Tax Center** in the sidebar (`/app/tax-center`) after signing in.
Choose a tax year and view stock/ETF or cryptocurrency sales across all accounts
owned by the signed-in client. The two download buttons produce separate
`YYYY-1099-B.pdf` and `YYYY-1099-DA.pdf` files.
Both use portrait recipient-copy layouts with numbered form boxes, a tax-year
header, payer/filer and recipient panels, and holding-period checkboxes. One
page represents one allocated purchase lot; a sale lacking reliable basis has
one page with unavailable values. Empty statements contain no invented trades.

Identity placeholders are presentation-only: TINs are masked, addresses use
Example City / Demo City, state ZZ, and postal code 00000. No real SSNs or
addresses are collected or persisted. The requested simulation banner is removed
from PDFs and the UI. These remain simplified demo documents; they do not assert
that any information was filed with the IRS. Unsupported financial/reporting
fields are marked unavailable rather than filled with invented dollar amounts.

## Data and scope

- One reporting service reads completed sell fills and their persisted FIFO
  allocations. It uses execution prices, never current quotes or submitted prices.
- Stock/ETF and crypto documents are separate. Stock/ETF sales retain their recorded
  currencies. Crypto covers USD buys and sells only; other asset classes are excluded.
  Combined totals are unavailable for mixed currencies rather than assuming exchange rates.
- UI rows combine each sale's FIFO lots. PDF pages show the individual allocations,
  including acquisition date, quantity, proceeds, basis, gain/loss, and holding period.
  The repeated fill reference identifies pages belonging to the same sale.
- Gains and losses are totals of positive and negative **combined sale** results.
  All calculations use `BigDecimal`, with the existing eight-decimal, half-even
  settlement convention. Decimal strings are retained in the UI without converting
  financial amounts to JavaScript numbers.
- Years and acquisition/disposal dates use UTC. A holding is long-term when the
  sale date is later than the acquisition date plus one year.
- Missing, incomplete, or inconsistent allocations produce an unavailable basis,
  acquisition date, and gain/loss. Those sales remain visible, but are excluded
  from gain/loss totals with an explicit partial-totals notice.
- PDF names use the existing non-sensitive client ID. Standard PDF fonts replace
  unsupported characters with `?`; the UI preserves the full asset description.
- No document storage/versioning, wash-sale rules, filing, or taxpayer onboarding.
  These are simplified educational reports, not tax advice or compliant returns.

Crypto uses the same persisted FIFO allocations as securities, including
fractional quantities, so no new crypto accounting infrastructure was necessary.

## Setup and demonstration

No database changes or migrations are required. Maven downloads Apache PDFBox
3.0.8 during the normal backend build. Start the backend and frontend as usual.

Use an account with actual completed buys followed by sells. To demonstrate
multiple lots, buy the same stock twice, then sell more than the first purchase's
quantity. Wait until orders are filled; accepted or rejected orders do not appear.
The existing execution worker must be enabled for trades to complete
(`ORDER_EXECUTION_ENABLED=true`) and its usual market/provider configuration must
be available. Choose the year containing those sales in Tax Center, check the
transaction row, and download 1099-B. Repeat with a supported USD crypto
pair to demonstrate 1099-DA. A user with no sales gets an empty statement.

Do not use test fixtures as user financial records. Automated test examples are
isolated in H2 and the generated visual previews live only under `backend/target`.

## API and checks

- `GET /api/tax/report?year=2026&document=1099-B` (or `1099-DA`)
- `GET /api/tax/documents/1099-B?year=2026` (or `1099-DA`)

Year defaults to the current UTC year. Both routes resolve the client from the
authenticated principal, accept no user/account selection, and return `no-store`.

From `backend`: `mvn "-Dtest=TaxReportingIntegrationTest,FifoAllocationIntegrationTest" "-Dspring.kafka.listener.auto-startup=false" test`.
From `frontend`: `npm test -- --watch=false --include=src/app/pages/tax-center.component.spec.ts`.
Build the UI with `npm run build`.
