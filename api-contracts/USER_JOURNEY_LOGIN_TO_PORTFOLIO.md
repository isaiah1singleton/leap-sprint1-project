# User Journey: Login to Portfolio View

This document walks through a complete user journey using the LEAP API: **from sign-in to viewing the portfolio**.

---

## Overview

A user named **Jane Moore** signs in and immediately views her account portfolio. The journey involves **4 API calls**:

1. Sign in → receive auth token
2. Retrieve account list → get default account ID
3. Fetch portfolio data → get holdings and balances
4. (Optional) Retrieve individual holdings or cash balance details

---

## Step 1: User Signs In

**Endpoint:** `POST /auth/signin`

**User enters credentials on the login page:**
- Email: `jane@example.com`
- Password: `trade2026`

**Request:**
```json
POST /v1/auth/signin
Content-Type: application/json

{
  "email": "jane@example.com",
  "password": "trade2026"
}
```

**Response (200 OK):**
```json
{
  "client": {
    "clientId": 501,
    "name": "Jane Moore",
    "email": "jane@example.com",
    "clientStatus": "ACTIVE",
    "clientSegment": "RETAIL"
  },
  "defaultAccountId": 1001,
  "session": {
    "sessionId": 9001,
    "clientId": 501,
    "tokenHash": "hash_of_session_token_...",
    "createdAt": "2026-09-24T10:30:00Z",
    "expiresAt": "2026-09-24T14:30:00Z",
    "revoked": false
  },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
}
```

**What the frontend does:**
- Stores the `token` (typically in secure storage or session storage)
- Saves the `defaultAccountId` (1001) for future requests
- Shows Jane's name on the dashboard
- Redirects to the dashboard/portfolio page

**Key point:** All future authenticated requests include:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## Step 2: Frontend Retrieves Account Context (Optional)

**Endpoint:** `GET /auth/me`

The frontend can call this to confirm who is logged in and fetch the defaultAccountId again.

**Request:**
```http
GET /v1/auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK):**
```json
{
  "client": {
    "clientId": 501,
    "name": "Jane Moore",
    "email": "jane@example.com",
    "clientStatus": "ACTIVE",
    "clientSegment": "RETAIL"
  },
  "defaultAccountId": 1001
}
```

This is useful for:
- Confirming the user is still authenticated
- Refreshing session state if the frontend was closed and reopened

---

## Step 3: Frontend Loads the Portfolio

**Endpoint:** `GET /accounts/{accountId}/portfolio`

Now Jane views her dashboard. The frontend calls this endpoint using the `defaultAccountId` from step 1.

**Request:**
```http
GET /v1/accounts/1001/portfolio
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK):**
```json
{
  "accountId": 1001,
  "holdings": [
    {
      "accountId": 1001,
      "instrumentId": 3001,
      "totalQuantity": 320,
      "reservedQuantity": 0,
      "updatedAt": "2026-09-24T10:25:00Z"
    },
    {
      "accountId": 1001,
      "instrumentId": 3002,
      "totalQuantity": 150,
      "reservedQuantity": 50,
      "updatedAt": "2026-09-24T10:15:00Z"
    }
  ],
  "cashBalances": {
    "USD": {
      "accountId": 1001,
      "currency": "USD",
      "totalBalance": {
        "amount": 48250.00,
        "currency": "USD"
      },
      "reservedBalance": {
        "amount": 2500.00,
        "currency": "USD"
      },
      "updatedAt": "2026-09-24T10:20:00Z"
    },
    "EUR": {
      "accountId": 1001,
      "currency": "EUR",
      "totalBalance": {
        "amount": 5000.00,
        "currency": "EUR"
      },
      "reservedBalance": {
        "amount": 0.00,
        "currency": "EUR"
      },
      "updatedAt": "2026-09-24T09:50:00Z"
    }
  }
}
```

**What the frontend displays:**
- **Total portfolio value**: calculated from holdings + cash (this might be computed on the frontend or returned by a separate endpoint)
- **Holdings table:**
  - AAPL: 320 shares reserved=0
  - MSFT: 150 shares reserved=50 (50 shares are reserved for a pending order)
- **Cash balances:**
  - USD: $48,250.00 total, $2,500.00 reserved
  - EUR: €5,000.00 total, €0 reserved

---

## Step 4: (Optional) Frontend Fetches Additional Instrument Details

If the frontend needs to show instrument names (e.g., "AAPL" → "Apple Inc."), it can call:

**Endpoint:** `GET /instruments/{instrumentId}`

**Request:**
```http
GET /v1/instruments/3001
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK):**
```json
{
  "instrumentId": 3001,
  "market": "NASDAQ",
  "symbol": "AAPL",
  "assetClass": "EQUITY",
  "isTradable": true,
  "quoteCurrency": "USD"
}
```

Now the portfolio UI shows:
- **AAPL** (Apple Inc.) | Qty: 320 | Avg Cost: $188.40 | Last: $214.62 | Value: $68,678.40

---

## Summary: Request Flow

```
Browser/Frontend                          Backend (LEAP API)
│
├─> POST /auth/signin
│   - email, password
│   <────────────────── 200 OK + token + defaultAccountId (1001)
│   (stores token in secure storage)
│
├─> [Optional] GET /auth/me
│   <────────────────── 200 OK + client info + defaultAccountId
│   (confirm session)
│
├─> GET /accounts/1001/portfolio
│   Authorization: Bearer <token>
│   <────────────────── 200 OK + holdings + cashBalances
│   (Jane sees her portfolio with:
│    - 320 AAPL, 150 MSFT
│    - $48,250 USD cash, €5,000 EUR cash)
│
├─> [Optional] GET /instruments/3001
│   Authorization: Bearer <token>
│   <────────────────── 200 OK + AAPL details
│   (frontend enriches the display with names)
│
└─> Display Portfolio Dashboard
    - Total Value: $167,532.99
    - Holdings: AAPL (320), MSFT (150)
    - Cash: USD $48,250 / EUR €5,000
```

---

## Data Types Used in This Journey

| Field | Type | Example | Notes |
|-------|------|---------|-------|
| `clientId` | int64 | 501 | Unique client identifier |
| `accountId` | int64 | 1001 | Unique account identifier |
| `token` | string (JWT) | `eyJhbG...` | Sent in Authorization header |
| `totalQuantity` | double | 320.0 | Decimal for fractional shares |
| `amount` | double | 48250.00 | Decimal for money values |
| `createdAt` | date-time | `2026-09-24T10:30:00Z` | ISO-8601 format |
| `currency` | enum | USD, EUR | Limited set |

---

## Error Scenarios

### Invalid credentials
**Request:**
```http
POST /v1/auth/signin
Content-Type: application/json

{
  "email": "jane@example.com",
  "password": "wrong-password"
}
```

**Response (401 Unauthorized):**
```json
{
  "message": "Invalid credentials"
}
```

### Missing or expired token
**Request:**
```http
GET /v1/accounts/1001/portfolio
Authorization: Bearer expired-or-invalid-token
```

**Response (401 Unauthorized):**
```json
{
  "message": "Missing or invalid JWT"
}
```

### Account not found
**Request:**
```http
GET /v1/accounts/9999/portfolio
Authorization: Bearer eyJhbG...
```

**Response (404 Not Found):**
```json
{
  "message": "Account not found"
}
```

---

## Key Takeaways

1. **Authentication:** User signs in once, receives a token, uses it for all subsequent requests.
2. **Portfolio view is a single endpoint:** Instead of calling separate endpoints for holdings, cash, and other data, the frontend can get everything in one call to `/accounts/{accountId}/portfolio`.
3. **Idempotent reads:** Calling `GET /auth/me` or `GET /accounts/{accountId}/portfolio` multiple times is safe—no data changes.
4. **Rich response structure:** The portfolio response includes nested objects (`Money`, `Holdings`, `CashBalances`) so the frontend has all the information needed.
5. **Reserved quantities:** The API distinguishes between total and reserved balances, which is useful for showing committed/pending activity.

---

## Next Steps in a Real User Session

After viewing the portfolio, Jane might:
- **View order history:** `GET /accounts/1001/activity` or `GET /accounts/1001/orders`
- **Place a new order:** `POST /accounts/1001/orders` with instrument, side, quantity
- **Deposit cash:** `POST /accounts/1001/cash-balances/USD/deposit`
- **View session details:** `GET /clients/501/sessions`
- **Sign out:** `POST /auth/signout`

