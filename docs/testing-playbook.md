# Testing Playbook

**Role:** Define how to design, run, diagnose and maintain tests for this Java 21 / Spring Boot 4.1 Clean Architecture service (DynamoDB + S3 through LocalStack, Redis and JWT).
**Audience:** Human contributors and AI software-engineering agents.
**Stack constraints:** JUnit 5 + Mockito + Testcontainers (LocalStack) + RestAssured. Do not add a new test dependency without explicit human approval (`AGENTS.md`, rule 5).

**Sources of truth:**

1. `AGENTS.md`
2. `pom.xml`
3. `.github/workflows/ci.yml`
4. `docs/coding-standards.md`
5. Colocated `*Test` / `*IT` classes
6. `README.md`

When this document disagrees with executable configuration, `pom.xml` and `.github/workflows/ci.yml` win. Fix the documentation in the same change set.

---

## 1. Testing principles

1. Test behaviour and observable contracts, not implementation details.
2. Keep the fastest useful feedback loop at the lowest appropriate layer.
3. Put business-rule assertions in domain/application tests; use HTTP tests for routing, validation, authentication, authorization, serialization and status/body contracts.
4. Mock outbound boundaries, not domain state.
5. Every important rejection path is as valuable as its happy path.
6. A test must be deterministic, isolated and repeatable locally and in CI.
7. Never weaken, skip or delete a valid test merely to make the build green.
8. A green test suite is necessary but not sufficient: coverage, static analysis and dependency reports must also be interpreted.

---

## 2. Test taxonomy

| Level                    | Naming                 | Runtime                                                   | Purpose                                                                           |
| ------------------------ | ---------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------- |
| **Domain unit**          | `*Test`                | Plain JUnit; no Spring, mocks or I/O                      | Entity/value-object invariants and state transitions                              |
| **Application unit**     | `*Test`                | JUnit + Mockito; no Spring context                        | Use-case orchestration, port interactions, ownership and rejection paths          |
| **Infrastructure unit**  | `*Test`                | JUnit + Mockito, or small Spring-independent fixture      | Mapping/adaptation behaviour that does not require real external services         |
| **Spring context smoke** | `*Tests` / `*Test`     | `@SpringBootTest`; no Docker or external I/O              | Application wiring and context startup                                            |
| **Adapter integration**  | `*IT`                  | Testcontainers + LocalStack                               | Real DynamoDB/S3 adapter behaviour, consistency, cursor and concurrency semantics |
| **HTTP end-to-end**      | `*IT`                  | `@SpringBootTest(RANDOM_PORT)` + RestAssured + LocalStack | Full request flow, authentication/authorization and public API contracts          |
| **Release smoke**        | Manual until automated | Compose/local production-like runtime                     | Small, high-value path before release/deployment                                  |

### Test placement

Mirror production packages under `src/test/java`:

```text
.../domain/<feature>/model/*Test.java
.../application/<feature>/service/*ServiceTest.java
.../infrastructure/<area>/*Test.java
.../infrastructure/<area>/*IT.java
.../<BusinessFlow>IT.java
```

Use one of these naming styles consistently inside a class:

```text
method_condition_expectedResult
shouldDescribeExpectedBehaviour
```

---

## 3. Commands and Maven lifecycle

### 3.1 Fast loop — no Docker

```bash
./mvnw test
```

This runs `*Test` / `*Tests` classes. It includes domain/application unit tests, infrastructure unit tests and the Spring context smoke. It does **not** run `*IT` classes and does not require Docker.

### 3.2 Adapter integration + HTTP E2E — Docker required

```bash
./mvnw test -Dtest='*IT' -DfailIfNoTests=false
```

This explicitly runs all `*IT` classes, including both adapter integration tests and HTTP end-to-end flows. Docker must be available because the shared integration base starts LocalStack through Testcontainers.

### 3.3 Quality checks

```bash
./mvnw jacoco:check
./mvnw spotbugs:check
./mvnw dependency-check:check -DfailBuildOnAnyVulnerability=false
```

Current interpretation:

- **JaCoCo:** blocking gate; minimum **35% line** and **15% branch** coverage at bundle level.
- **SpotBugs:** blocking gate for findings at the configured threshold (`High`, effort `Max`).
- **OWASP Dependency Check:** currently **advisory** for vulnerabilities because `failBuildOnAnyVulnerability=false`; tool execution errors can still fail the command. Review the generated report instead of treating a successful step as “no vulnerabilities”.

The current JaCoCo check is based on the fast-test execution. Integration/E2E coverage is not merged into the blocking report.

### 3.4 Current CI local mirror

Run in this order:

```bash
./mvnw test
./mvnw jacoco:check
./mvnw spotbugs:check
./mvnw dependency-check:check -DfailBuildOnAnyVulnerability=false
./mvnw test -Dtest='*IT' -DfailIfNoTests=false
./mvnw clean package
```

The canonical executable sequence lives in `.github/workflows/ci.yml` and must be kept synchronized with this section.

### 3.5 Runtime shutdown smoke (Docker + built jar)

```bash
scripts/shutdown-under-load-test.sh [JAR] [PORT] [CONCURRENCY]
```

Post-build runtime check: boots the jar, generates continuous concurrent traffic (default 40
parallel authenticated requests), sends SIGTERM mid-flight and asserts readiness goes DOWN while
the process is still alive, in-flight requests complete with 200, new requests are rejected, and
the process exits within the 30s grace period. Requires LocalStack + Redis running with the schema
provisioned (README) and a built jar.

### 3.6 Full gate

`./mvnw verify` is the complete local gate: Surefire runs `*Test` during `test`, the
maven-failsafe-plugin runs `*IT` during `integration-test`, and the bound quality executions
(SpotBugs, JaCoCo check, OWASP Dependency Check) plus the packaged jar complete at `verify`.
Requires Docker. `./mvnw clean package` alone remains **only** a production artifact build —
do not claim it validates integration/E2E behaviour.

---

## 4. Mandatory patterns

| Area                  | Rule                                                                                                                                                                                                                                                                                   |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Domain tests**      | Instantiate real domain objects. No mocks, Spring context or infrastructure classes.                                                                                                                                                                                                   |
| **Application tests** | Mock outbound domain ports (`UserRepository`, `PlaylistRepository`, `SongMetadataRepository`, `SongStoragePort`, etc.). Keep application collaborators real when practical. Mock an application strategy/factory only when the test is intentionally isolating dispatch/orchestration. |
| **Typed failures**    | Prefer `NotFoundException`, `ConflictException`, `ForbiddenException` and feature-specific typed exceptions. Reserve `IllegalArgumentException` for invalid arguments/preconditions. Assert exact messages only when they are part of the public contract.                             |
| **Ownership**         | Every owner-scoped mutation must test owner success and non-owner rejection. The actor identity must come from the authenticated context, never from client-controlled ownership data.                                                                                                 |
| **Passwords**         | Hash only through `PasswordHasher`; never assert/log plaintext, hashes as fixtures, JWT secrets or complete tokens.                                                                                                                                                                    |
| **HTTP errors**       | Verify status and the standard error body for 400, 401, 403, 404, 409 and unexpected 500 paths where applicable.                                                                                                                                                                       |
| **Pagination**        | Test case normalization, first page, subsequent cursor, end-of-results and malformed cursor. Never equate a DynamoDB page size with a total count.                                                                                                                                     |
| **Storage**           | Mock `SongStoragePort` in application tests. Use S3 integration tests for presigned upload, confirmation, streaming URL, content and content type.                                                                                                                                     |
| **Concurrency**       | Test conditional writes/optimistic locking against real DynamoDB. Use bounded waits/timeouts and always close executors.                                                                                                                                                               |
| **Security**          | Every new endpoint needs an explicit `SecurityConfig` rule plus at least one allowed and one rejected HTTP scenario.                                                                                                                                                                   |
| **Isolation**         | IT data must use unique IDs/emails/names and must not depend on test order or leftovers from another test.                                                                                                                                                                             |
| **Boundaries**        | Run the rule-1 import check. Domain/application tests must not introduce infrastructure, AWS SDK, MapStruct, springdoc or Spring Web dependencies into the core.                                                                                                                       |

### Boundary check

```bash
grep -rEn "^import (com\.spotpobre\.backend\.infrastructure|software\.amazon|io\.awspring|org\.mapstruct|org\.springdoc|org\.springframework\.web)" \
  src/main/java/com/spotpobre/backend/domain \
  src/main/java/com/spotpobre/backend/application
```

Expected result: no matches. Do not weaken the expression to hide a violation. If the architecture policy intentionally changes, update `AGENTS.md`, coding standards and architecture documentation together.

---

## 5. Current automated suite map

| Area                            | Main test files                                                                                                                        | What is pinned                                                                                                    |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| **Registration**                | `RegisterUserServiceTest`, `AuthenticationFlowIT`, `EmailUniquenessIT`                                                                 | Hashing through port, default role, duplicate rejection, concurrent uniqueness, JWT returned by HTTP registration |
| **Authentication**              | `AuthenticationServiceTest`, `SpringSecurityAuthenticationAdapterTest`, `UserDetailsServiceImplTest`, `CachedUserDetailsTest`, `AuthenticationFlowIT` | Domain authentication result, Spring adapter mapping, wrong-password 401, protected access with token, Redis round-trip of cached user details |
| **User profile**                | `GetUserProfileServiceTest`, `GetUserDetailsServiceTest`                                                                               | Profile lookup and not-found/empty behaviour                                                                      |
| **Artists**                     | `CreateArtistServiceTest`, `SearchArtistsServiceTest`, `ArtistSearchPaginationIT`, `ArtistSongFlowIT`                                  | Creation, admin-protected flow, case-insensitive search, cursor forwarding and page-size guard                    |
| **Albums**                      | `CreateAlbumServiceTest`, `AlbumSongConsistencyIT`, `ArtistSongFlowIT`                                                                 | Artist existence, album persistence and album–song query consistency                                              |
| **Song upload**                 | `SongUploadCommandTest`, `InitiateSongUploadServiceTest`, `ConfirmSongUploadServiceTest`, `S3SongStorageAdapterIT`, `ArtistSongFlowIT` | MIME/size validation, presigned upload, compensation, confirm guards, S3 round-trip and downloadable stream       |
| **Song read/search**            | `GetSongMetadataServiceTest`, `GetSongStreamUrlServiceTest`, `SearchSongsServiceTest`, `SongSearchPaginationIT`                        | Metadata/not-found, storage-key streaming, case-insensitive search, cursor walk and malformed cursor              |
| **Playlist ownership**          | `PlaylistOwnershipGuardTest`, playlist service tests, `PlaylistFlowIT`, `ErrorHandlingFlowIT`                                          | Owner success, non-owner 403 for rename/delete/add/remove and standard error body                                 |
| **Playlist limits/concurrency** | `CreatePlaylistServiceTest`, `PlaylistLimitAndConcurrencyIT`, `DynamoDbPlaylistRepositoryAdapterIT`                                    | Ten-playlist limit, stale-snapshot rejection and basic adapter save/find                                          |
| **Playlist membership**         | `AddSongToPlaylistServiceTest`, `RemoveSongFromPlaylistServiceTest`, `PlaylistFlowIT`                                                  | Missing resource guards, owner enforcement and full add-song HTTP flow                                            |
| **Likes**                       | `ToggleLikeServiceTest`, `LikeStrategyFactoryTest`, `SongLikeStrategyTest`, `ArtistLikeStrategyTest`, `PlaylistLikeStrategyTest`       | Strategy dispatch, entity-existence checks, toggle add/remove and returned count contract through mocked port     |
| **HTTP error contract**         | `GlobalExceptionHandlerTest`, `ErrorHandlingFlowIT`                                                                                    | Standard bodies for 400/401/403/404/409/500 handlers; missing, malformed and expired JWT cases                    |
| **Rate limiting**               | `FixedWindowRateLimiterTest`, `RateLimitFilterTest`, `RateLimitFlowIT`                                                                 | Fixed-window windowing, per-client identity (X-Forwarded-For), 429 on exceed, disabled-path behaviour             |
| **Production config (fail-fast)** | `ProdConfigValidatorTest`                                                                                                             | Missing required property and static AWS credentials in `prod` abort startup; non-prod passes                        |
| **Actuator probes**             | `DynamoDbHealthIndicator`, `S3HealthIndicator` (main classes); no dedicated unit/IT yet (see gap 6)                                       | Readiness gates on DynamoDB/S3; liveness/readiness reachable without auth; failure/recovery behaviour             |
| **Application startup**         | `SpotpobreApplicationTests`                                                                                                            | Spring context and main application context start/close                                                           |

When behaviour covered by this map changes, extend the existing test where it remains cohesive. Create a new class when the new concern has a distinct fixture/lifecycle or would make the existing class hard to understand.

---

## 6. Traceability matrix

For milestone-sized work, record or verify this chain in the change description:

```text
requirement / risk
    → lowest useful test level
    → test class and scenario
    → command that executes it
    → CI step that gates it
```

Examples:

| Requirement/risk                             | Lowest useful test    | Representative class                                 | Command / CI step                    |
| -------------------------------------------- | --------------------- | ---------------------------------------------------- | ------------------------------------ |
| Non-owner cannot mutate playlist             | Application + E2E     | `DeletePlaylistServiceTest`, `PlaylistFlowIT`        | `test`; then `*IT`                   |
| Duplicate e-mail under race                  | Real adapter          | `EmailUniquenessIT`                                  | Slice + E2E CI step                  |
| Presigned URL points to uploaded object      | S3 integration        | `S3SongStorageAdapterIT`                             | Slice + E2E CI step                  |
| Malformed JWT returns standard 401           | E2E                   | `ErrorHandlingFlowIT`                                | Slice + E2E CI step                  |
| Cursor does not repeat first page            | Real adapter          | `SongSearchPaginationIT`, `ArtistSearchPaginationIT` | Slice + E2E CI step                  |
| Domain remains independent of infrastructure | Static boundary check | Rule-1 grep                                          | Local check; add to CI when approved |

A feature is not fully covered if its only test mocks the behaviour that carries the main risk.

---

## 7. Known coverage and process gaps

Keep this section honest. Move an item out only when an automated test/gate exists.

1. **Failsafe lifecycle integration — RESOLVED.** The `maven-failsafe-plugin` runs every `*IT`
   class during `mvn verify` (`./mvnw verify` is the single full gate); explicit Surefire
   selection (`-Dtest='*IT'`) still works for targeted runs.
2. **No complete endpoint × HTTP method × role matrix.** Existing E2E tests cover critical authentication and playlist ownership paths, not every matcher in `SecurityConfig`.
3. **Like persistence LocalStack integration — RESOLVED.** `DynamoDbLikeRepositoryAdapterIT`
   pins reverse-GSI count/pagination behaviour against real DynamoDB and `LikeFlowIT` covers the
   desired-state PUT/DELETE flow end to end.
4. **Not every DynamoDB adapter operation has direct integration coverage.** Several paths are covered indirectly by E2E, but an indirect flow may not exercise edge cases such as empty pages, large result sets or conditional failures.
5. **Production fail-fast startup is not automated end to end.** `ProdConfigValidator` has unit
   coverage (`ProdConfigValidatorTest`), but booting the app with the `prod` profile against a real
   missing/forbidden variable set is only exercised manually.
6. **Actuator probe failure/recovery automation — RESOLVED.** `HealthProbeFlowIT` drives
   dependency failure → readiness DOWN → recovery → UP with liveness staying UP against real
   LocalStack containers.
7. **Graceful shutdown CI gate — RESOLVED.** `scripts/shutdown-under-load-test.sh` now GATES the
   `runtime-smoke` job on every push/PR (previously warn-only). Two latent defects were fixed when
   promoting it: the CI job ran `java -jar` with the runner's default JDK (17), which cannot load
   the Java 21 jar (explicit `setup-java` added), and registration predated the now-mandatory
   `Idempotency-Key` header; failures dump the application-log tail for diagnosability.
8. **No performance/load baseline — RESOLVED (foundation, consultative).** `perf/` holds three
   k6 read-path scenarios (users-me, song-search, artists-list) with budgets-as-code
   (thresholds inside each scenario), run by `scripts/performance-baseline.sh` via the pinned
   `grafana/k6:2.2.0` container against the compose stack. CI runs it as a separate,
   `continue-on-error` job (`performance`) that uploads JSON summaries as artifacts; promoting
   it to a hard gate is deliberate follow-up once 2-3 runs have calibrated realistic floors.
   Known scope limit: the foundation catalog is minimal (no ADMIN seeding over HTTP), so the
   numbers are regression tripwires for infrastructure overhead, not capacity results.
   Rate-limit budgets remain unexercised (scenario backlog).
9. **JaCoCo thresholds raised to a real floor (60% line / 60% branch) — RESOLVED as a gate, ongoing as a target.** Both floors now sit at 0.60 (previously 0.35/0.15) and are enforced by `jacoco:check` locally and in CI. Coverage does not prove assertion quality; keep closing meaningful gaps when touched.
10. **Dependency vulnerability policy — CHANGED.** OWASP Dependency Check stays report-only by
    design (`failBuildOnCVSS=11`, `failOnError=false`) so an unstable NVD can never break CI;
    enforcement lives in the Trivy HIGH/CRITICAL image gate, which scans the production jar
    inside the image and blocks the pipeline on fixable findings. Jar-level findings are tracked
    via the Security-tab SARIF trail and pinned as they appear (netty, BouncyCastle, httpcore5).
11. **No explicit mutation-testing or API schema compatibility gate.** Add only with human approval because both may require new tooling/dependencies.

---

## 8. Regression checklist

| Area                    | Must verify                                                                                                                                              |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Register**            | Password goes through `PasswordHasher`; no plaintext persistence/logging; `ROLE_USER`; duplicate email → 409; concurrent duplicate creates only one user |
| **Authenticate**        | Valid credentials return JWT at HTTP boundary; wrong password and unknown user do not leak existence; malformed/expired token → standard 401             |
| **Artists**             | Create requires `ROLE_ADMIN`; search is case-insensitive; cursor advances; invalid cursor and excessive limit are rejected                               |
| **Albums**              | Create validates artist; album query reflects uploaded/confirmed songs                                                                                   |
| **Song initiate**       | Album exists; MIME and size validated; metadata/storage compensation on failure; no audio bytes pass through the API                                     |
| **Song confirm/stream** | Album/song/storage key match; object exists; returned stream URL downloads the exact content and content type                                            |
| **Playlists**           | Create/list/rename/add/remove/delete; every mutation owner-scoped; non-owner → 403; missing → 404; stale write → 409                                     |
| **Likes**               | Toggle song/artist/playlist; invalid target rejected; `isLiked` and `newLikeCount` are coherent                                                          |
| **HTTP errors**         | Standard body and correct status for validation 400, auth 401, authorization 403, missing 404, conflict 409 and generic 500 handler                      |
| **Security**            | Every endpoint has an explicit matcher; mutating routes never default to permit-all; allowed and denied role scenarios exist                             |
| **Pagination**          | No repeated page; no skipped/duplicated result in the tested dataset; end cursor is null; malformed cursor rejected; max page size enforced              |
| **Boundaries**          | Rule-1 grep has no matches; controllers call inbound use cases, not repositories                                                                         |
| **Quality**             | Fast tests, coverage, SpotBugs, dependency report, `*IT`, package and documentation all synchronized                                                     |

---

## 9. Release regression smoke

Run against disposable local services before a release tag. Prefer the automated `*IT` suite; use this smoke to validate the assembled local runtime and operator instructions.

```bash
docker-compose up -d
# Create LocalStack tables/bucket using the current README instructions.
./mvnw spring-boot:run
```

Prerequisites:

- disposable local environment only;
- seeded local `ADMIN` and `ARTIST` accounts for role-protected flows;
- never use or document production credentials in smoke scripts;
- retain the IDs/tokens returned by previous steps rather than hardcoding real values.

**Stop on first failure.**

|   # | Step                                                             | Expected                                                                                                                        |
| --: | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
|   1 | Register and authenticate a normal user                          | 200 with JWT; profile endpoint succeeds; no secret appears in logs                                                              |
|   2 | Authenticate with a wrong password                               | Standard 401 without user-existence leak                                                                                        |
|   3 | Create artist as normal user, then as admin                      | 403, then 201                                                                                                                   |
|   4 | Search artist using different case                               | Created artist returned                                                                                                         |
|   5 | Create album                                                     | 201; artist relationship correct                                                                                                |
|   6 | Initiate song upload as artist                                   | 201 with storage key and presigned URL(s); no file body through API                                                             |
|   7 | PUT audio directly to presigned URL and confirm                  | Upload succeeds; confirm returns 200                                                                                            |
|   8 | Fetch song details and GET streaming URL                         | Metadata correct; downloaded bytes/content type match upload                                                                    |
|   9 | Owner playlist CRUD and add/remove song                          | All operations succeed and list reflects changes                                                                                |
|  10 | Repeat playlist mutations as a different user                    | Standard 403; owner data unchanged                                                                                              |
|  11 | Toggle like for song/artist/playlist                             | `isLiked` toggles and count response remains coherent                                                                           |
|  12 | Call protected endpoint without/with malformed token             | Standard 401 in both cases                                                                                                      |
|  13 | Call `/actuator/health/liveness` and `/actuator/health/readiness` | Both 200 `UP` without authentication (probes are reachable for the ALB); readiness must go DOWN when a critical dependency (DynamoDB/S3) is unavailable and recover to UP |
|  14 | Call a non-probe actuator endpoint (e.g. `/actuator/metrics`) without a token | Standard 401 (only probe paths are unauthenticated)                                                          |

Optional, non-blocking until promoted to a gate:

- Swagger UI renders the current endpoints;
- `/actuator/info` and `/actuator/metrics` are reachable only under the intended security policy;
- inspect structured logs for correlation and accidental secrets.

---

## 10. Flakiness, isolation and test-data policy

### Determinism

- Unit tests use deterministic values unless uniqueness itself is under test.
- ITs may use random UUIDs/e-mails to avoid collision in the shared LocalStack JVM, but assertions must never depend on random ordering.
- Inject or wrap time/randomness when exact timestamps/ordering become business-relevant.
- Never depend on test execution order.

### Shared LocalStack state

`AbstractIntegrationTest` shares one manually started LocalStack container across `*IT` classes so Spring can reuse its cached context. Therefore:

- use unique partition keys and e-mails;
- do not assume an empty table unless the test explicitly provisions/cleans its own data;
- filter/query using data owned by the test;
- do not enable parallel IT execution until table/data isolation is proven.

### Concurrency and network tests

- Use bounded timeouts for `Future.get`, HTTP calls and polling.
- Always close `ExecutorService`, HTTP resources and application contexts.
- Avoid unbounded `Thread.sleep`. Poll a condition with a deadline using existing JDK/test tools.
- A rerun may be used once to classify reproducibility, never as the fix. A flaky test must be corrected or tracked with an owner and reason; do not add blind retries.

### Failure diagnostics

Failure output should identify the resource/operation without printing passwords, raw JWTs, secret keys or full signed URLs.

---

## 11. Reading failures

| Class               | Signal                                                 | First move                                                                                  |
| ------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| **Logic**           | Domain/application assertion fails                     | Verify the invariant/use case before changing the expectation                               |
| **Contract**        | Wrong HTTP status/body                                 | Check typed exception, handler/entry point and API contract                                 |
| **Authorization**   | Unexpected 401/403                                     | Separate authentication failure, role matcher and owner guard                               |
| **Boundary**        | Forbidden core import                                  | Restore the port boundary; do not weaken the check                                          |
| **Compile**         | Mock/signature mismatch                                | Verify the port surface and update all callers/tests coherently                             |
| **DynamoDB**        | Conditional failure, cursor error, missing table/index | Compare config, test provisioning and README schema; reproduce with the relevant adapter IT |
| **S3**              | Presigned PUT/confirm/download fails                   | Check endpoint, credentials, content type, storage key and LocalStack logs                  |
| **Environment**     | Docker/port/Redis/LocalStack problem                   | Verify prerequisites; distinguish infrastructure failure from product failure               |
| **Coverage**        | JaCoCo threshold fails                                 | Add meaningful behaviour tests; do not exclude code merely to raise the percentage          |
| **Static analysis** | SpotBugs fails                                         | Fix the finding or document a narrowly justified suppression with human review              |
| **Dependency scan** | CVE reported                                           | Assess reachability/severity, upgrade or add a justified, expiring suppression              |
| **Discovery**       | `*IT` not picked up                                    | Failsafe only runs them in `./mvnw verify`; use that, or the explicit `-Dtest='*IT'` command |

When many tests fail, triage in this order:

1. compile and application context;
2. environment/Testcontainers;
3. domain invariants;
4. touched application services;
5. adapter integration;
6. HTTP E2E;
7. quality reports.

---

## 12. Analyzer reply format

```text
## Summary
Failing class / scenario
Category: Logic | Contract | Authorization | Boundary | Compile | DynamoDB | S3 | Environment | Coverage | Static analysis | Dependency
Root cause: one concise sentence

## Fix plan
1. Smallest production-code fix
2. Regression test at the lowest useful level
3. Wider verification, if required

## Verify
./mvnw test
# when persistence/storage/security/HTTP changed:
./mvnw test -Dtest='*IT' -DfailIfNoTests=false
# or, for the complete gate (tests + ITs + quality checks + jar):
./mvnw verify
```

---

## 13. Do not

- Skip, delete or add `@Disabled` merely to green the build.
- Weaken an assertion without proving the previous contract was wrong.
- Catch and ignore failures in tests.
- Add blind retries for flaky tests.
- Use real cloud accounts, production secrets or production data.
- Log plaintext passwords, JWT secrets, full bearer tokens or presigned URLs.
- Mock domain entities/value objects.
- Mock persistence/storage in an adapter integration test.
- Assert business rules only through controller/E2E tests when a lower-level test is possible.
- Treat code coverage percentage as proof of correctness.
- Treat a successful advisory OWASP step as proof that dependencies have no vulnerabilities.
- Widen `Page`/`Pageable`/DynamoDB-specific cursor types into the core; use domain pagination abstractions.
- Add a dependency or change the test lifecycle without explicit human approval and documentation sync.

---

## 14. Done when

- [ ] The change has a happy path and at least one relevant rejection path.
- [ ] New domain/application behaviour has a colocated `*Test`.
- [ ] Ownership/authorization is tested for every affected mutation.
- [ ] HTTP changes pin status and standard response body.
- [ ] Persistence/storage changes have an appropriate `*IT` against LocalStack.
- [ ] Concurrency-sensitive changes are tested with real conditional/optimistic behaviour.
- [ ] Test data is isolated and execution-order independent.
- [ ] `./mvnw test` is green.
- [ ] `*IT` is green when persistence, storage, security or HTTP behaviour changed.
- [ ] JaCoCo and SpotBugs gates are green; dependency findings were reviewed when relevant.
- [ ] The boundary grep has no new matches.
- [ ] Smoke instructions are updated when the assembled HTTP/runtime flow changed.
- [ ] `README.md`, `CHANGELOG.md`, `AGENTS.md` and this suite map/gap list are synchronized for milestone-sized work.

---

## 15. Document maintenance

Treat these sections differently:

- **Stable policy:** principles, taxonomy, mandatory patterns and “Do not”. Change only when engineering policy changes.
- **Executable commands:** keep synchronized with `pom.xml` and `.github/workflows/ci.yml`.
- **Current suite map:** update whenever test classes are added, renamed or removed.
- **Known gaps:** remove an item only when the corresponding automated test/gate exists; add newly discovered risks immediately.
- **Release smoke:** update whenever endpoint paths, authentication, upload protocol or runtime security changes.

A milestone is not done if the tests changed but this document still describes the previous suite.
