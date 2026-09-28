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
PostgreSQL to become healthy before starting Spring Boot. On the first database
startup, PostgreSQL runs `database/transaction_schema.sql` to create the tables.
The `postgres_data` volume keeps the data between restarts; initialization scripts
run only when that volume is empty. Set `POSTGRES_PORT` in `backend/.env` if the
default PostgreSQL host port is occupied.

### Authentication endpoints

Register and sign-in both return a 24-hour bearer token in `accessToken`:

```bash
curl -i 'http://127.0.0.1:18080/hello?myName=Test'
curl -i -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"testpass123"}' \
  'http://127.0.0.1:18080/api/auth/register'
curl -i -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"testpass123"}' \
  'http://127.0.0.1:18080/api/auth/sign-in'
```

Passwords are stored as BCrypt hashes. Emails are normalized to lowercase and
must be unique regardless of case. The random token is returned once and only
its SHA-256 hash is stored in the `sessions` table. Send it as
`Authorization: Bearer <accessToken>` to authenticate account API requests.

## Current prototype scope

Portfolio data, trades, transfers, and market data are held in memory in the
browser, so refreshing resets that state. Authentication credentials and
sessions are stored by the backend. The frontend account page still uses
placeholder security controls and does not yet call the account API.
