# LEAP stocks MCP

A local MCP adapter for the existing Java API on the `dev` branch. It reads the
authenticated user's stocks and submits buy/sell orders through `POST /api/orders`
after explicit user confirmation. The app's database, ownership checks, trading
rules, and execution service remain authoritative.

## Setup

Use Node.js 22.9 or newer and start the existing LEAP backend and its dependencies
as described in [the project README](../README.md). The current Compose backend
port is **8082**; set the base URL to match your own deployment.

```sh
cd mcp
npm ci
cp .env.example .env
```

In PowerShell, `Copy-Item .env.example .env` also works. Edit the ignored `.env`:

```dotenv
LEAP_API_BASE_URL=http://127.0.0.1:8082
LEAP_ACCESS_TOKEN=your-current-user-access-token
# Optional when a user has multiple accounts:
# LEAP_ACCOUNT_ID=7
```

Obtain a current `accessToken` through the app's existing sign-in flow
(`POST /api/auth/sign-in`). Keep it in the MCP host's environment or the ignored
`.env`, never in a prompt, tool argument, or committed client configuration.
When the app session expires or is revoked, sign in again, replace the token, and
restart the MCP process.

This is a **local stdio server with one signed-in user per process**. It is not a
shared HTTP service. Launch a separate process with each user's own token.
The backend still verifies account and order ownership on every request. A
configured default account does not bypass that verification.

For a manual start:

```sh
npm start
```

The process waits for an MCP client on stdin. Do not type ordinary chat into its
terminal. Diagnostic messages use stderr; stdout is reserved for MCP messages.

## Connect an MCP client

Use [mcp-client.example.json](./mcp-client.example.json) as a generic stdio client
configuration, adapting its wrapper to your host. Replace both absolute paths.
Windows paths can use forward slashes, such as `C:/projects/leap/mcp/.env`.

Launch `node` directly from the client so npm's startup banner cannot interfere
with the protocol. The example loads the ignored `.env`; alternatively pass
`LEAP_API_BASE_URL`, `LEAP_ACCESS_TOKEN`, and optional `LEAP_ACCOUNT_ID` through
the client's environment.

The MCP host must implement **form elicitation** (`elicitation.form`) and render
the confirmation to the user. Read operations work without this capability;
trades are refused. Never implement the host's confirmation handler as automatic
acceptance. Allow up to two minutes for confirmation and a little longer for the
overall tool call (for example, a three-minute call timeout).

The server uses the maintained MCP SDK v1 protocol (2025-11-25), including
`elicitation/create`. See the
[official SDK documentation](https://ts.sdk.modelcontextprotocol.io/).

## Tools

| Tool | Inputs | Behavior |
| --- | --- | --- |
| `list_accounts` | None | List accounts belonging to the signed-in user. |
| `view_stocks` | Optional `accountId`, `filter`, `startDate`, `endDate`, `symbol` | Current holdings, dated portfolio observations, and dated order activity. |
| `place_trade` | `symbol`, `side` (`BUY` or `SELL`), positive whole-share `quantity`; optional `accountId`, `market` | Obtain a quote, ask the user to confirm, then submit the exact shown order. Supports tradable equities and ETFs. |
| `get_order` | `orderId` | Read the exact current status of an owned order. |

If there is one active account, no account argument is needed. If several exist,
choose an account explicitly or configure an owned `LEAP_ACCOUNT_ID`. Symbols
are normalized to uppercase. A symbol listed on several markets requires
`market`.

### Dates

These are all valid `view_stocks` arguments:

```json
{}
```

```json
{ "filter": "past week" }
```

```json
{ "filter": "filter by past week" }
```

```json
{ "accountId": 7, "startDate": "2026-10-01", "endDate": "2026-10-15" }
```

The default is the preceding **30 days**, matching the backend's `1M` range.
`past week` is seven days; `past day` is 24 hours. Canonical `1D`, `1W`,
`1M`, `ALL`, plus `last week`, `last month`, and `all time`, are supported.
Explicit `YYYY-MM-DD` dates include both whole days in UTC. Supply both dates
and omit `filter`. Invalid dates, reversed ranges, and future dates are rejected.

Dates filter **portfolio observations and order activity**. Holdings are always
current and labeled as such. A symbol filter applies to holdings and orders;
portfolio value history remains account-wide. History includes only observations
already recorded by the backend: this adapter does not invent historical prices
or reconstruct historical holdings. Empty history is valid.

## Resources

All resource reads use the same user session, ownership checks, and filters:

| URI or URI template | Content |
| --- | --- |
| `leap://accounts` | User's accounts. |
| `leap://stocks` | Current holdings and past-month history/activity for the default account. |
| `leap://accounts/{accountId}/stocks` | Default past-month view for an owned account. |
| `leap://accounts/{accountId}/stocks/{filter}` | Filtered view, e.g. `leap://accounts/7/stocks/1W`. |
| `leap://accounts/{accountId}/stocks/from/{startDate}/to/{endDate}` | Explicit inclusive dates, e.g. `leap://accounts/7/stocks/from/2026-10-01/to/2026-10-15`. |

Natural-language URI filters must be URL encoded, such as
`leap://accounts/7/stocks/past%20week`. Resources return JSON and never submit
orders.

## Prompts and instructions

- `review_stocks`: optional `accountId` and `filter`; defaults to a past-month
  review and separates current holdings from dated activity.
- `trade_stock`: `symbol`, `side`, `quantity`, and optional `accountId`;
  prepares a buy or sale and requires the client's confirmation UI.

Prompt arguments are strings, as required by MCP prompts. Prompt retrieval does
not execute a trade. Server-wide instructions also describe default dates,
account selection, confirmation, unavailable history, and order-status handling.

## Confirmation and order outcomes

`place_trade` resolves an owned account and supported instrument, then fetches
the quote from the existing market API. It shows the account, side, quantity,
symbol, market, price, currency, estimated total, quote timestamp, and source.
Only an accepted form with `confirm: true` submits the frozen request body.
The model cannot supply a confirmation flag or price through tool arguments.

Decline, cancel, an unchecked box, timeout, cancellation of the tool call,
invalid quotes, and missing client support cause no submission. Confirmation is
required for every new order. Only one trade can await confirmation or submission
at a time per MCP process. The app handles cash, holdings, trading hours, and
execution validation.

The returned backend status is preserved: **ACCEPTED is not FILLED**. Rejections
include the backend's reason. An interrupted response, malformed submission
response, or server error after sending the order returns
`status: "submission_unknown"`. The order may exist, so inspect activity in
the app or `view_stocks` before requesting another order. The adapter never
automatically retries a submission; the existing API has no idempotency key.

## Existing API bindings

| Request | Purpose |
| --- | --- |
| `GET /api/accounts` | Account selection and ownership. |
| `GET /api/accounts/{accountId}/holdings` | Current stocks. |
| `GET /api/accounts/{accountId}/portfolio-history?range=1M` | Portfolio observations; also uses `1D`, `1W`, or `ALL`. |
| `GET /api/orders?accountId={accountId}` | Order activity, filtered by `submittedAt` in the adapter. |
| `GET /api/orders/{orderId}` | Order status. |
| `GET /api/instruments` | Resolve supported instrument IDs. |
| `GET /api/market/quotes/{symbol}` | Obtain the displayed indicative quote. |
| `POST /api/orders` | Submit only after user confirmation. |

No backend endpoints or database schema changes are required.

## Verification

```sh
cd mcp
npm test
```

Tests use the official MCP client with an HTTP test double matching the app's
controllers and DTOs, plus a spawned stdio MCP process. They cover date defaults
and boundaries, resource/prompt discovery, ownership, buy and sell payloads,
explicit confirmation, declines, timeout, unsupported clients, quote failures,
concurrent confirmations, rejected orders, and uncertain submissions.

These tests do not place orders against a live LEAP account. For a live read
check, connect a configured client and call `list_accounts`, followed by
`view_stocks`. Trading uses the app's existing execution path; it is not a
separate simulator.
