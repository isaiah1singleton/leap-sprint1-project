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

## Current prototype scope

Authentication, portfolio data, trades, transfers, and market data are held in
memory in the browser. Refreshing the page resets that state. The security
buttons on the account page are placeholders; no backend API is connected yet.

## Run the backend

```bash
cd backend
mvn compile exec:java -Dexec.mainClass=com.neueda.leap.Main
```

The backend is currently independent of the frontend prototype.
