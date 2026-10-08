# LEAP Sprint 1

This repository contains a Java backend and an Angular frontend. The frontend is
the Inside Tr8ders sign-in and investor dashboard prototype.

## Prerequisites

- Node.js 22 or newer
- npm 11 or newer
- Java 17 or newer and Maven 3.9+ (only required for the backend)

## Run the Angular frontend

From the repository root:

```bash
cd frontend
npm install
npm start
```

Open <http://localhost:4200> and create an account from the registration page. A new
password must contain at least eight characters and one number.

For a production build:

```bash
cd frontend
npm run build
```

The compiled files are written under `frontend/dist/`.

## Run the backend and PostgreSQL

From the repository root, create `backend/.env` from `backend/.env.example`:

```bash
cd backend
cp .env.example .env
```

Set `POSTGRES_PASSWORD` to a nonempty value and `FAUXNANCE_API_KEY` to your
instructor-issued key in `.env`, then start Compose from
`backend`:

```bash
docker compose -f compose.yml up -d --build
```

The backend is available at <http://localhost:8082> by default (or `BACKEND_PORT`
in `.env`). The frontend's API URL is configured in
`frontend/src/environments/environment.ts`. The Compose file waits for
PostgreSQL before starting. A new database is set up automatically, and its data
is kept between restarts. If you reuse an older database, update it separately
before starting the backend.

### Authentication

You can create an account or sign in to start a session. Sessions last up to 24
hours and end after 30 minutes without activity. Signing out revokes the session.
Expired and revoked sessions remain recorded.

Run `mvn test` from the backend directory to check the session behavior.

### Order validation

Before an order proceeds, the backend checks that its details are valid, the
instrument is supported and available for trading, the requested quantity
follows the firm's rules, and the client has enough cash or units. The check
uses the price shown to the client and follows the platform's configurable
trading rules.

For now, the order and account information used for this check is sample data.
This step checks whether an order meets the rules; accepting orders and
reserving cash or units are not included yet.

## Stock and crypto market data

The existing Markets and Transact pages search symbols and names from
`backend/src/main/resources/us-v1.json` and `crypto-us.json`. Selecting a result
loads its price, daily change, previous close, bid, ask, market state, timestamp,
and provider status through the Spring backend. Pin up to 25 US stocks from
Markets; the Dashboard displays their prices and green/red daily changes.
Pins are saved per login in this browser. Crypto symbols can be searched and
traded in the simulator, but cannot be pinned to the stock watchlist.

All market routes require a valid backend session:

- `GET /api/market/symbols?q=apple&limit=15` searches the local catalogues.
- `GET /api/market/quotes/AAPL` returns a ticker quote.
- `GET /api/market/quotes?symbols=AAPL,MSFT` returns up to 25 quotes, including
  per-symbol errors when the provider cannot return a price.

The backend alone sends `X-Api-Key` to
[Fauxnance](https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1/docs).
It reads `FAUXNANCE_API_KEY` from the environment or the optional backend `.env`
file; never place this key in Angular configuration. `FAUXNANCE_BASE_URL` can
override the provider URL. Quotes are cached for 30 seconds. Dashboard batches
and selected ticker details refresh every 60 seconds while the page is visible.
Fauxnance quotes are delayed/best-effort; stale and synthetic data are labelled.
Provider failures show a retry option instead of fabricated prices.

Buy/sell use the quoted ask/bid respectively. Their buttons are disabled for
insufficient simulated cash/holdings, invalid quantities, an absent account,
or unavailable/stale quotes. Simulation state is separate per login and trading
account and survives reloads within the same browser session. Each new simulation
starts with the existing demo cash and holdings; these are not bank balances.

Run `mvn test` from `backend`, and `npm test -- --watch=false` plus
`npm run build` from `frontend` to check the integration.

## Current prototype scope

Trades, transfers, and portfolio balances are simulated in the browser session;
there is no broker execution or server-side order ledger yet. Market prices,
authentication credentials, trading accounts, and sessions come from the backend.
Signing out also revokes the backend session. The frontend account page still uses
placeholder security controls and does not yet call the account API.
