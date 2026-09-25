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

Authentication, portfolio data, trades, transfers, and market data are held in
memory in the browser. Refreshing the page resets that state. The security
buttons on the account page are placeholders; no backend API is connected yet.

## Run the backend

From the repository root:

```bash
cd backend
mvn spring-boot:run
```

The backend starts at <http://localhost:8080>.

### Run the backend in Docker

From the repository root:

```bash
docker build -t leap-backend ./backend
docker run --rm -p 8080:8080 leap-backend
```

The default configuration uses an in-memory H2 database, so its data is reset
when the container stops.
