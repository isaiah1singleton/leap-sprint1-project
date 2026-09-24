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

Open <http://localhost:4200>. Frontend demo authentication is enabled by default.
Sign in with `demo@insidetr8ders.com` / `trade2026`; the same credentials appear
on the sign-in page. No backend is needed for this demo. The signed-in email is
kept in the browser session so protected routes remain available after a refresh.
Signing out clears it. The registration page creates temporary accounts held in
memory until the page is refreshed; use the demo account for repeatable sign-in.
Change `mockAuth` to `false` in `frontend/src/app/core/auth.service.ts` to use
the Spring authentication API instead.

For a production build:

```bash
cd frontend
npm run build
```

The compiled files are written under `frontend/dist/`.

## Run the backend

From the project root, run:

```powershell
docker compose up -d postgres
cd backend
mvn spring-boot:run
```

The API starts at `http://localhost:8080`.

The first backend startup creates the `clients` table automatically through the
database migration. PostgreSQL defaults to `localhost:5432`, database `leap`,
username `postgres`, and password `postgres`. Override these with
`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` if needed.

### Mock authentication endpoints

```text
POST /api/auth/register  { "email": "trader@example.com", "password": "password" }
POST /api/auth/sign-in  { "email": "trader@example.com", "password": "password" }
```

Passwords are stored as BCrypt hashes. Emails are normalized to lowercase and
must be unique regardless of case.

## Current prototype scope

In demo mode, authentication and registration run in the browser. Portfolio
data, trades, transfers, and market data are held in memory; refreshing the page
resets that trading state. The security buttons on the account page are
placeholders.

## Run the backend

From the repository root:

```bash
cd backend
mvn spring-boot:run
```

The backend starts at <http://localhost:8080>.
