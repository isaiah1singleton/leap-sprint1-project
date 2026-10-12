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

Open <http://localhost:4200>. Use the seeded credentials
`jmoore` / `trade2026`, or create an account from the registration page. A new
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

Set `POSTGRES_PASSWORD` to a nonempty value in `.env`, then start Compose from
`backend`:

```bash
docker compose -f compose.yml up -d --build
```

The backend is available at <http://127.0.0.1:18080>. The Compose file waits for
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

## Current prototype scope

Portfolio data, trades, transfers, and market data are held in memory in the
browser, so refreshing resets that state. Authentication credentials and
sessions are stored by the backend. The frontend account page still uses
placeholder security controls and does not yet call the account API.
