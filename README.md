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

The backend is available at <http://localhost:8080>. The Compose file waits for
PostgreSQL to become healthy before starting Spring Boot. On the first database
startup, PostgreSQL runs `database/transaction_schema.sql` to create the tables.
The `postgres_data` volume keeps the data between restarts; initialization scripts
run only when that volume is empty. Set `BACKEND_PORT` and `POSTGRES_PORT` in
`backend/.env` if the default host ports are occupied.

### Mock authentication endpoints

```text
POST /api/auth/register  { "email": "trader@example.com", "password": "password" }
POST /api/auth/sign-in  { "email": "trader@example.com", "password": "password" }
```

Passwords are stored as BCrypt hashes. Emails are normalized to lowercase and
must be unique regardless of case.

## Current prototype scope

Authentication, portfolio data, trades, transfers, and market data are held in
memory in the browser. Refreshing the page resets that state. The security
buttons on the account page are placeholders; no backend API is connected yet.
