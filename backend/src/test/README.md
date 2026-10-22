# Backend tests

Run commands from `backend/`. All new entity and service tests live under
`src/test/java`; the instrument entity tests have been moved out of production
sources. No production entity/service or Maven configuration changes are needed.

## Fast entity and service unit suite

```bash
mvn -Dgroups=unit test
```

The unit suite uses JUnit, Mockito, real domain objects, fixed clocks and mocked
repositories/providers. It does not require NestJS, PostgreSQL, Kafka, Docker,
provider credentials, or a running application. Expected error logs from worker
failure-isolation tests do not indicate failing tests.

Coverage without editing pom.xml:

```bash
mvn clean org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.14:report -Dgroups=unit -Djacoco.append=false
```

Reports: `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.xml`.
The latter can be imported by SonarQube. The report includes all production code;
controllers/configuration are intentionally not covered by this unit-only run.
Do not interpret its overall percentage as entity/service coverage alone.

## Covered behavior

| Area | Tests |
|---|---|
| Domain | Money arithmetic, precision, rounding, cash overdraft/overflow, holdings reservation/release/settlement, account ownership, order construction, complete lifecycle transition matrix, fill/cash movement signs and audit payloads |
| Trading | Trading hours/DST, configurable restrictions, validation rejection codes, submission acceptance/rejection, execution retries/expiry, bid/ask settlement, missing balance/holding creation, invariant failures and numeric limits |
| Service queries | Account creation/duplicates, owner-scoped accounts, holdings, orders, events, fills and cash movement lookups |
| Workers | Empty batches, batching, pagination, quote deduplication, pre-fetch expiry and failure isolation |
| Portfolio | Cash/fill reconciliation, funding, currency limitations, valuation rounding, incomplete prices, synthetic quotes, history ranges, gap preservation and recording decisions |
| Market | Catalogue filtering/ranking, cache expiry boundary, in-flight sharing, partial errors, fallback timestamp preservation and quota resets |
| Audit/admin | Outbox success/failure/retry state, sequential event deduplication, lifecycle completeness, suspicious flag account freezing/restoration |
| Current identity | Active-client mapping and current admin checks, independent of token format |

## JWT migration

`LegacyTokenServiceTest` and the current registration/login methods in
`ClientServiceTest` are tagged `legacy-auth`. These are temporary regression tests
for the existing opaque-session implementation, not requirements for JWTs.
To run business unit tests without them:

```bash
mvn -Dgroups=unit -DexcludedGroups=legacy-auth test
```

Existing session-based HTTP/integration tests remain regression coverage until
the NestJS/JWT cutover. Their JWT replacements must exercise the actual Spring
security filter chain and real tokens issued by NestJS. Unit tests supplying
client IDs/emails do not establish JWT validity or HTTP authorization.

## Deliberate limits and next tests

- Full suite: `mvn test`. It also runs existing H2/Spring/HTTP tests. Those tests
  disable unrelated Kafka listener startup; they do not prove Kafka delivery.
- Controller/web tests: routes, JSON, validation, status/error mapping, and
  authenticated/forbidden requests with the actual security configuration.
- PostgreSQL integration: real SQL constraints, JPA hydration/lifecycle callbacks,
  isolation, lock ordering, simultaneous transfers/submissions, duplicate
  execution/flags/audit delivery, and atomic rollback of cash/holdings/fills/events.
- Kafka integration: committed outbox to broker to audit ledger, retries and
  duplicate deliveries across crashes.
- JWT security: signature/algorithm, expiry, issuer/audience, subject-to-client
  mapping, roles, cross-account access and the agreed revocation policy.
- Cross-service and browser journeys: NestJS login, Spring account/trading APIs,
  frontend session handling, portfolio updates, and admin review.

Unit mocks cannot prove database rollback, actual SQL execution or concurrent
uniqueness. The coverage report is measured, not a claim of 100% correctness.
Remaining uncovered lines include ORM constructors and environmental failure
paths such as classpath I/O failure and the JVM lacking SHA-256. Do not weaken
assertions, mock Java internals, or exclude production code just to reach 100%.

The current `ClientService.register(null)` and `signIn(null)` dereference the
request before their intended null guards. Fix this in the production-code work
if those endpoints remain; it is not an intended contract to preserve in tests.

Two existing fixtures were corrected: registration supplies the required name,
and the real-HTTP market test seeds its own database-backed AAPL instrument.
The obsolete delayed-quote warning expectation was updated to the current policy.

## Validation record (22 October 2026)

- Clean unit-only run: **259 tests passed**, zero failures/errors/skips.
- JaCoCo unit-only coverage, with no coverage exclusions:
  - Entities: **463/497 lines (93.16%)**, **215/223 branches (96.41%)**.
  - Services (including order validation and nested classes):
    **1123/1128 lines (99.56%)**, **660/675 branches (97.78%)**.
- Full suite excluding only `MarketHttpIntegrationTest`: **282 tests passed**,
  zero failures/errors/skips. Command: `mvn test -Dtest=!MarketHttpIntegrationTest`.
- The unrestricted full suite was attempted, but all four real-HTTP tests fail
  before requests execute on this Windows/JDK 25 host: `HttpClient` construction
  reports `Unable to establish loopback connection` / `Invalid argument: connect`.
  Retrying outside the sandbox and with IPv4 preference did not resolve it.
  Those tests remain enabled and their expected HTTP status assertions remain
  unchanged. Re-run the complete suite on the Linux development/CI host; previous
  error-dispatch 400/404-versus-401 regressions must still be checked there.

These measurements cover this checkout, not the upcoming JWT implementation.
