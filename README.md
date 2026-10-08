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

Signed-in users can search US stocks and crypto, view quote details, and pin up
to 25 US stocks to their dashboard. Quotes come from
[Fauxnance](https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1/docs)
through the backend, keeping the API key private.

Prices are requested every 60 seconds while the page is visible, but may be
delayed, stale, or synthetic. Buy and sell controls check simulated cash and
holdings.

## Current prototype scope

Trades, transfers, and portfolio balances are simulated in the browser session;
there is no broker execution or server-side order ledger yet. Market prices,
authentication credentials, trading accounts, and sessions come from the backend.
Signing out also revokes the backend session. The frontend account page still uses
placeholder security controls and does not yet call the account API.
