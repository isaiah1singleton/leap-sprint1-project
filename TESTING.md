# Testing

Work is based on `origin/dev`, not the older `main` prototype. Tests use generated identities and disposable databases. They do not require developer `.env` files, production credentials, or a live market-data provider.

## Run everything

Install Node.js 22.19+ (or 24.13+), npm, Java 17+, Maven 3.9+, Python 3.11+ with venv support, and Docker. On a machine with those tools:

```sh
node tests/run-all.mjs
```

The local runner installs locked npm dependencies and Chromium, creates a uniquely named PostgreSQL 18 container on a random loopback port, runs the Java/auth/frontend suites, both production builds and existing analytics tests, and removes its own container in `finally`. It fails on any unsuccessful suite. On Linux, installing Chromium system dependencies requires permission to install OS packages. Tests are run locally; the Jenkinsfile retains the existing build and smoke-test pipeline. Analytics dependencies are installed in `.venv`; set `LEAP_PYTHON` if the Python executable is not named `python3`.

To use an existing **disposable test database** without Docker:

```powershell
$env:LEAP_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:55432/leap_test'
$env:LEAP_TEST_DB_USER = 'leap_test'
$env:LEAP_TEST_DB_PASSWORD = 'test-only-password'
node tests/run-all.mjs
```

The Java suite requires a local database name ending in `_test`, creates a unique schema, and removes only that schema. The database must be reserved for tests. `LEAP_SKIP_BROWSER_INSTALL=1` reuses installed Playwright browsers; `LEAP_NPM_CLI` can supply an explicit `npm-cli.js` path when the system npm shim is broken.

## Individual suites

| Command (run in directory) | Coverage and boundaries |
| --- | --- |
| `mvn test` (`backend`) | Existing JUnit/Mockito and H2 integration coverage, current JWT HTTP authorization, order validation, ownership, portfolio, tax, legacy session service, audit publisher success/failure/retry, consumer persistence failures |
| `mvn verify` (`backend`) | All the above plus `TradingPostgresIT` and `AuditKafkaIT`; Docker is needed for PostgreSQL unless `LEAP_TEST_JDBC_URL` is supplied |
| `npm test` (`auth`) | Auth service unit tests with mocked hashing/repositories; real NestJS HTTP routing and validation with a mocked service |
| `npm run test:ci` (`auth`) | Unit/HTTP tests with coverage |
| `npm test -- --watch=false` (`frontend`) | Angular/Vitest services and components; `HttpTestingController` controls HTTP responses and verifies outstanding requests |
| `npm run test:ci` (`frontend`) | Angular tests plus HTML/LCOV coverage and JUnit reports |
| `npx playwright install chromium`, then `npm run test:e2e` (`frontend`) | Real Chromium and Angular server, with explicit stateful API contract mocks |

Maven Surefire runs `*Test`; Failsafe runs `*IT` during `verify`. Integration failures are not silently skipped when infrastructure is missing. Run Maven from `backend`, since the tests load the canonical `../database/transaction_schema.sql`.

For a restricted Windows installation, `-Dmaven.repo.local=../.m2/repository` keeps Maven's cache inside the checkout. Some Windows JDK installations require `-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp` to run NIO HTTP/Kafka tests; use an existing writable short temporary path. [Oracle documents the socket-path property](https://docs.oracle.com/en/java/javase/17/core/java-networking.html).

## Transaction guarantees exercised

| Property | Evidence |
| --- | --- |
| Atomicity | A failed transfer ledger write restores the balance; a failure after fill creation restores cash, holdings, fill, lifecycle, and outbox together. |
| Consistency | PostgreSQL rejects negative balances; order validation rejects nonpositive/oversized quantities, invalid currency and future quotes. Decimal cash transfers remain exact. Accepted/filled events and audit outbox entries agree with the HTTP order lifecycle. |
| Isolation | Competing withdrawals cannot overdraw; simultaneous attempts to settle one order produce one fill; another connection cannot see an uncommitted balance. Frontend tests reject stale responses after an account switch. |
| Durability boundary | A committed transfer is visible through a fresh independent PostgreSQL connection. The transactional outbox persists events until broker acknowledgement; failed sends remain retryable. The real Kafka broker test checks JSON serialization, listener consumption, persistence, and duplicate redelivery. |

These checks exercise application transaction behavior. They do not prove recovery from a host power failure, multi-node failover, cross-service distributed ACID, or exactly-once Kafka delivery. The outbox uses at-least-once delivery and audit event IDs provide deduplication. Browser tests deliberately mock API behavior; they are frontend end-to-end tests, not evidence that the deployed full stack is wired correctly. `TradingPostgresIT` separately exercises real HTTP → JWT security → order submission → PostgreSQL settlement → HTTP account/order reads, with deterministic quote decisions.

The active auth boundary is the NestJS `/auth` API and HS256 access JWTs. Old backend `/api/auth` registration/login endpoints no longer exist. Historical opaque-session tests retain service-level expiration/revocation coverage; API tests now use signed JWTs. Frontend sign-out clears local state; this suite does not claim server-side revocation of an already-issued JWT.

## Retained source fix

The only retained behavior change is Spring error dispatch preserving HTTP error statuses for previously failing HTTP tests. The added auth transaction/validation, Kafka interruption, bearer-origin, and Angular rendering changes and their regression tests have been removed.

## Reports

The disposable-container mode of the local runner requires verification on a Docker-enabled machine. PostgreSQL/Kafka tests were verified locally using an isolated PostgreSQL 18 cluster and an embedded Kafka broker.

Java XML/text reports are under `backend/target/surefire-reports` and `backend/target/failsafe-reports`. Auth coverage is under `auth/coverage`. Frontend coverage is under `frontend/coverage`, JUnit output under `frontend/test-results`, and browser reports under `frontend/playwright-report`. Playwright retains traces and screenshots on failure. Test data and browser contexts are independent; no test depends on execution order.

The analytics Python suite also runs independently with `.venv/bin/python -m unittest discover -s analytics/proof/tests` (Windows: `.venv/Scripts/python.exe`). Its fixtures and operating instructions are documented in `analytics/README.md` and `analytics/OPERATIONS.md`. Some pre-existing analytics tests skip on Windows because they require Bash or platform-specific process handling.
