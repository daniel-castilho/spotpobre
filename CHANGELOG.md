# Changelog

All notable changes to Spotpobre API will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project
intends to follow [Semantic Versioning](https://semver.org/) starting from its first tag.

## [Unreleased]

### Added

- **Password recovery via AWS SES** (roadmap: email verification and password recovery —
  recovery ships now; email verification reuses the same foundation next).
  `POST /api/v1/auth/password/recover` always answers 202 without revealing whether the address
  exists (no account enumeration; provider failures are logged, never surfaced). Known addresses
  receive a single-use token (32 random bytes; only its SHA-256 hash is stored in the new
  TTL-backed `AccountTokens` table) inside a reset link built from `APP_BASE_URL`.
  `POST /api/v1/auth/password/reset` redeems it atomically (conditional write burns the token,
  so replays of a used link answer 404 exactly like unknown ones), encodes the new password with
  the Argon2id `PasswordHasher` and persists. Delivery goes through the new outbound port
  `EmailSenderPort`; the first adapter is `SesEmailSenderAdapter` (SES v1 API — emulated by
  LocalStack Community, so dev/CI exercise real sends offline). Swapping providers later means
  adding an adapter, nothing else. Proven by `PasswordRecoveryFlowIT` (full journey incl.
  old-password 401 / new-password 200 and token replay 404), `SesEmailSenderAdapterIT` against
  the LocalStack SES emulation, and service unit tests.

## [0.11.0] - 2026-08-23

### Added

- **Cursor-paginated catalog listings** (roadmap: pagination on artists/albums).
  `GET /api/v1/artists` lists the artist catalog through storage-native scan pages
  (`limit` capped at 50, opaque `nextPageToken` cursor); `GET /api/v1/artists/{artistId}/albums`
  lists one artist's albums via the `artistId-index` GSI with the same contract, answering 404 for
  an unknown artist and an empty page for a known artist without albums. Both require
  authentication like every other read route. Proven by `CatalogPaginationIT` (no lost rows,
  no repeated rows across cursored pages) plus service unit tests.

## [0.10.0] - 2026-08-23

### Added

- **Idempotent endpoints: registration, artist, album, playlist creation and song upload
  initiation (spec §4.3, step 6A–6E).**
  `POST /api/v1/auth/register`, admin-only `POST /api/v1/artists`,
  `POST /api/v1/albums`, `POST /api/v1/playlists` and
  `POST /api/v1/albums/{albumId}/songs` now run behind the durable
  claim-and-lease protocol via new port-in use cases (`RegisterUserIdempotentlyUseCase`,
  `CreateArtistIdempotentlyUseCase`, `CreateAlbumIdempotentlyUseCase`). `Idempotency-Key` is
  required (400 when missing/invalid, validated before any persistence); successful responses
  carry `Idempotency-Replayed: true|false`
  and replays preserve the original status code. Same key + same canonical request returns the
  stored outcome without re-executing; same key + different request → 409 key-reuse conflict;
  concurrent duplicate requests → exactly one resource, losers get 409 + capped positive
  `Retry-After`.   Registration replays return a **freshly minted JWT** (tokens are never stored);
  artist creation reserves a stable `ArtistId` before the atomic Artist + OWNER write; album
  creation reserves a stable `AlbumId` and re-checks artist-membership authorization before the
  claim on every call — deterministic failures (unknown artist, non-member) never consume the key
  and replays cannot bypass the policy. Playlist creation reserves a stable `PlaylistId`; the
  per-user playlist limit is enforced at execution time and exceeding it records a replayable
  FAILED_FINAL 409 (the key stays bound to that outcome). Song upload initiation uses the long
  120 s upload lease, scopes claims by album (`pathIdentity`), and — on both replay and crash
  recovery — re-presigns a fresh URL for the storage key already bound to the reserved song
  (`SongStoragePort.regenerateUploadUrl`), so clients resume against the exact object; a metadata
  save failure after multipart creation still aborts the orphan upload. A crash between claim and
  completion
  recovers the same resource on retry (lease takeover). The authenticated principal scopes claims
  on protected routes.
- **Durable idempotency foundation (spec §5, steps S7–S8).** New pure domain core under
  `domain/idempotency` (`IdempotencyScope`, `CanonicalRequestHash` v1, `LeaseToken`,
  `ResultSnapshot`, `FailureDescriptor`, `IdempotencyRecord`) and a claim-and-lease protocol
  coordinator in `application/idempotency` (`IdempotencyCoordinator` with `Claim` /
  `ClaimOutcome`), backed by the conditional repository port `IdempotencyRecordRepository`.
  The DynamoDB adapter persists only digests and validated safe snapshots to the new
  `IdempotencyRecords` table (PK `scopeKey`, TTL attribute `expiresAtEpochSeconds`, 24 h):
  raw keys, e-mails, IPs, JWTs, signed URLs and credentials are structurally excluded
  (`ResultSnapshot` rejects absolute URLs / `eyJ` / passwords; `FailureDescriptor` accepts
  deterministic 4xx only). Protocol semantics: single-winner conditional claims with a stable
  preassigned resource ID, replay of COMPLETED results and FAILED_FINAL 4xx, 409 on key reuse
  with a different canonical request, lease takeover after expiry preserving the resource ID,
  and replacement of logically expired records ahead of DynamoDB's eventual physical deletion.
  Low-cardinality metrics via the outbound port `IdempotencyMetrics`
  (Micrometer: `spotpobre_idempotency_claims_total`, `spotpobre_idempotency_transitions_total`).
  Provisioning (seed script, README setup, Testcontainers base class) creates the table and
  enables TTL. No endpoint consumes the coordinator yet by design — endpoint wiring follows in
  the next step once crash-recovery primitives are proven.
- **Tests** — 38 unit tests (value-object guards, coordinator claim/replay/takeover/expiry/
  lost-lease flows against an in-memory fake) plus
  `DynamoDbIdempotencyRecordRepositoryAdapterIT` covering round-trip, duplicate-claim,
  foreign-lease, takeover-preserving-resource-ID, logical-expiry replacement, release and a
  concurrent single-winner race against Testcontainers LocalStack.
- **Artist accounts (memberships).** Management rights on an artist are no longer implied by
  `ROLE_ARTIST`; they now come from an explicit `ArtistAccount` aggregate
  (PK `artistId`, SK `userId`, permission `OWNER` | `MANAGER`, pure domain type in
  `domain/artist/model`). Every new artist is created together with an `OWNER` account in a single
  DynamoDB transactional write (`ArtistRepository.createWithOwner`); the designated owner must
  exist and hold `ROLE_ARTIST`, otherwise creation is rejected without partial persistence.
  Admins grant additional `MANAGER` memberships via `POST /api/v1/artists/{artistId}/accounts`
  and revoke them via `DELETE /api/v1/artists/{artistId}/accounts/{userId}` (both admin-only,
  rule 7). Access checks are centralised in `RequireArtistAccessUseCase` / `ArtistAccessService`:
  creating an album or uploading/confirming a song on an album now requires a membership on the
  owning artist (admins bypass, non-members get 403 fail-closed). Access denials and admin
  overrides emit structured audit logs with safe UUIDs only.
- **LocalStack support for the new table** — `scripts/seed-localstack.sh` and the README setup
  create the `ArtistAccounts` table; `scripts/backfill-artist-accounts.sh` assigns a designated
  user as `OWNER` of every pre-existing artist (dry-run by default, idempotent, `--apply` to
  write). Existing environments must run both before deploying this change.
- **Tests** — unit tests for the domain model, grant/revoke/access services and updated
  use-case tests; `DynamoDbArtistAccountRepositoryAdapterIT` covers atomic owner creation,
  round-trip and isolation against Testcontainers LocalStack.

### Fixed

- **Auth lookups survive a Redis outage** (AGENTS debt: auth cache had no fallback). A new
  `CacheOutageTolerantErrorHandler` wired through `CachingConfigurer` treats cache
  infrastructure failures as misses and swallows write/evict/clear failures with loud WARNs, so
  `UserDetailsServiceImpl.loadUserByUsername` degrades to the direct DynamoDB lookup instead of
  failing every authenticated request. Readiness remains deliberately ungated on Redis (S6);
  the policy covers every Redis-backed cache, current and future. Proven by
  `AuthCacheOutageResilienceIT`: a real Redis container serving `userCache` is killed
  mid-flight and authentication keeps working from source.
- **Closed the playlist-limit creation race** (AGENTS debt: count-then-insert). Creating a
  playlist now commits the row and the owner's counter advance in one DynamoDB transaction whose
  condition rejects anything that would exceed `MAX_PLAYLISTS_PER_USER = 10` — two strictly
  concurrent creations can no longer both observe room and overshoot. Deletion decrements the
  counter inside the same transaction as the removal. Owners with pre-counter playlists get a
  lazy, safe-side undercount until `scripts/backfill-playlist-counters.sh` recomputes counters
  from real rows. Proven by a new Testcontainers race test (14 simultaneous creates → exactly 10
  accepted) and covered end-to-end by the playlist flow ITs.
- **The runtime shutdown smoke never passed in CI — and nothing noticed because it was
  non-blocking.** The `runtime-smoke` job launched the production jar with the runner's default
  JDK (17), which cannot load the Java 21 jar, so readiness never came UP and every run failed
  into a swallowed warning; the script also predated the mandatory register `Idempotency-Key`
  and discarded the application log on failure. The job now pins Temurin 21, sends a fresh
  idempotency key per run, dumps the application-log tail on any failure, and the step is
  promoted to a hard gate: shutdown-drain regressions fail the pipeline (testing-playbook
  regression item 7 closed).
- **Silenced the MapStruct unmapped-property warning on `ArtistApiMapper`.** The mapper omitted
  `ArtistResponse.songs` intentionally (artists are never returned with their song list), but only
  a stale comment documented it, so every compile logged an "Unmapped target property" warning.
  It now carries an explicit `@Mapping(target = "songs", ignore = true)` with the rationale
  inline; the item is cleared from AGENTS.md Known Technical Debt.

- **CI red on two supply-chain gates** (fixed per the portable recipe in
  `flowtxt-parent/docs/ci-vulnerability-gates.md`):
  - The Trivy image scan failed on fixable MEDIUM CVEs although the policy is HIGH/CRITICAL —
    with `format: sarif`, trivy-action ignores the `severity` filter for both the report and
    the exit code
    ([aquasecurity/trivy-action#309](https://github.com/aquasecurity/trivy-action/issues/309)).
    The pipeline now runs a non-blocking full-SARIF pass (Security-tab advisory trail,
    unchanged) plus a separate table-format gate whose exit code only fires on fixable
    HIGH/CRITICAL findings.
  - The OWASP Dependency Check build gate broke whenever the NVD API answered 429/503
    (ongoing instability since its June 2026 schema migration), aborting mid-update. The POM
    now sets `failOnError=false` so an unreachable NVD degrades to scanning against the cached
    mirror instead of failing CI; vulnerability reporting stays fail-soft by design
    (`failBuildOnCVSS=11`) and the image remains gated hard by Trivy.
- **Test infra: rate-limit test override was silently shadowed.** The `rate-limit.limit=100000`
  escape hatch added to the shared `AbstractIntegrationTest` `@DynamicPropertySource` in step 6A
  outranks subclass `@TestPropertySource` values in Spring's property precedence, so
  `RateLimitFlowIT`'s tight `limit=3` never applied and its 429 assertions failed. Flow ITs now
  extend a dedicated `AbstractFlowIT` base that neutralises rate limiting, while
  `AbstractIntegrationTest` no longer touches `rate-limit.*` — rate-limit-specific ITs control
  their own properties again. `RateLimitFlowIT` also uses a 1 h window: the limiter's windows are
  wall-clock aligned, so a 1 m window can roll over mid-test and reset the counter.

### Changed

- **Dependency maintenance wave merged from Dependabot** (all CI-green on rebased branches):
  Maven wrapper 3.9.11 → 3.9.16, jjwt 0.12.5 → 0.13.0 (single additive change per the upstream
  release notes), JaCoCo 0.8.9 → 0.8.15, spotbugs-maven-plugin 4.9.3.0 → 4.10.3.0 (SpotBugs core
  4.10.3 — mostly false-negative/false-positive fixes; gate stays watchful for new findings),
  MapStruct 1.5.5.Final → 1.6.3 and spring-cloud-aws 4.0.2 → 4.1.0 (both verified against our
  usage: none of the documented 1.6 breaking changes apply; SC-AWS 4.1.x is the Boot-4-era line).
- **Adopted Testcontainers 2.0.5** with its breaking changes handled in one place
  (`AbstractIntegrationTest`): renamed modules (`localstack` → `testcontainers-localstack`,
  `junit-jupiter` → `testcontainers-junit-jupiter`), relocated `LocalStackContainer`
  (`org.testcontainers.localstack`), service enum replaced by name strings in `withServices`, and
  the unified no-arg `getEndpoint()` replacing `getEndpointOverride(service)`. The LocalStack
  image deliberately stays pinned at `localstack/localstack:3.2` — project policy: bumping the
  image is the last-resort option, and 3.2 predates the March 2026 `LOCALSTACK_AUTH_TOKEN`
  requirement of newer images.
- **Upgraded to Spring Boot 4.1** (from 3.5.7): Spring Framework 7 / Security 7 / Tomcat 11 with
  modularized starters — `spring-boot-starter-web` renamed to `spring-boot-starter-webmvc`, cache
  autoconfiguration now requires the explicit `spring-boot-starter-cache`, health indicators move
  from `spring-boot.actuate.health` to `org.springframework.boot.health.contributor`,
  `RedisCacheManagerBuilderCustomizer` moved to the new cache module package, and
  `DaoAuthenticationProvider` takes its `UserDetailsService` via constructor. Jackson 3
  (`tools.jackson.*`) replaces Jackson 2 in application code (`DynamoDbCursorHelper`,
  `RestErrorResponseWriter`); the legacy `com.fasterxml.jackson.core:jackson-databind` 2.x line
  stays pinned at 2.22.2 because jjwt-jackson still requires it at runtime. Companion bumps:
  spring-cloud-aws 4.0.2, springdoc 3.1.0, rest-assured 6.0.1.
- **CI actions moved to supported Node 24 runtimes.** `actions/cache` v4 → v6,
  `actions/upload-artifact` v4 → v7, `actions/download-artifact` v4 → v7,
  `actions/checkout` v5 → v7 and `actions/dependency-review-action` v4 → v5 (the former all
  declared `node20`, which runners force-upgrade to Node 24 while flagging deprecation
  warnings). Trivy DBs are now cached by our own daily-keyed `actions/cache@v6` step over
  `.cache/trivy` with `cache: false` on each trivy invocation — trivy-action's built-in caching
  pins an old node20-pinned `actions/cache` internally. `actions/setup-java@v5`,
  `github/codeql-action@v4` and the SHA-pinned trivy-action were verified to already declare
  `node24`.
- **NVD API key wired into OWASP Dependency Check.** The plugin now reads `NVD_API_KEY` from the
  environment (GitHub Actions repository secret; local dev via shell env) and persists its mirror
  in the shared `~/.m2/dependency-check-data` directory, so runs apply NVD deltas instead of
  hours-long throttled full downloads. CI caches the mirror between runs.
- **`./mvnw verify` is now the single full gate.** The `maven-failsafe-plugin` runs the slice
  integration and E2E `*IT` classes during `integration-test`, so one command covers unit tests,
  integration/E2E tests, SpotBugs, JaCoCo check, OWASP Dependency Check and the production jar
  (Docker required). `./mvnw test` remains the Docker-free unit loop; `-Dtest='*IT'` still works
  for running integration tests alone.
- **BREAKING: likes are now desired-state PUT/DELETE.** `POST /api/v1/likes/toggle` was removed.
  Like mutations move to `PUT /api/v1/users/me/likes/{entityType}/{entityId}` (like) and
  `DELETE /api/v1/users/me/likes/{entityType}/{entityId}` (unlike), where `entityType` is lowercase
  `song`, `artist` or `playlist` and `entityId` is a UUID. Both are idempotent, return 204 with no
  body, and mutation responses no longer carry `newLikeCount` (the GSI count is eventually
  consistent and not transactionally coupled to the mutation). Missing target entity returns 404;
  concurrent opposite operations follow last-successful-storage-operation semantics.
- **BREAKING: playlist song membership is idempotent PUT/DELETE.** Adding a song is now
  `PUT /api/v1/playlists/{playlistId}/songs/{songId}` (was POST): repeated PUT keeps one membership
  with no version increment when already present; a concurrent same-song PUT that loses optimistic
  locking reloads and succeeds when the desired membership already exists instead of surfacing a
  409; genuinely different concurrent modifications still return 409 for retry. Removing a song
  keeps the same path but now always returns 204 — removing an absent song is a successful no-op
  with no persistence write or version bump. The 100-unique-songs maximum and owner-only access
  rules are unchanged.
- **Security rules tightened** — `POST /api/v1/albums` now accepts `ROLE_ARTIST` **or**
  `ROLE_ADMIN` at the edge (real authorisation happens in the use case via membership);
  `/api/v1/artists/*/accounts/**` is admin-only. Any `ROLE_ARTIST` user can no longer manage
  artists they have no membership on.

### Security

- **Pinned the httpcore5 family to 5.4.3** via Boot's `httpcore5.version` override property:
  spring-cloud-aws 4.1.0 pulls `org.apache.httpcomponents.core5:httpcore5`/-h2 into the jar at
  Boot's managed 5.4.2, which carries CVE-2026-54399 / CVE-2026-54428 (HIGH, fixed in 5.4.3) —
  caught by the new Trivy HIGH/CRITICAL gate on PR #6's preview run before it ever reached main.
- **Closed the six fixable HIGH/CRITICAL findings flagged by the new Trivy gate** (CI run
  32652460147), all in the application jar:
  - Netty pinned to **4.2.16.Final** by overriding Boot's managed `netty.version` property
    (Boot 4.1.0 manages 4.2.15.Final) — closes CVE-2026-59901 (bzip2 infinite loop,
    codec-compression), CVE-2026-55831 / CVE-2026-55833 / CVE-2026-56745 (SPDY/codec-http
    denial of service) and CVE-2026-56819 (HTTP/2 decompression ByteBuf leak, codec-http2),
    all confirmed fixed upstream in 4.2.16.Final (Netty 4.2.16 release notes / NVD).
  - BouncyCastle bumped 1.80 → **1.84**, closing CRITICAL CVE-2025-14813 (GOSTCTR counter
    wraps after 255 blocks) plus CVE-2026-0636, CVE-2026-3505, CVE-2026-5588 and
    CVE-2026-5598 fixed in the same upstream release.

## [0.9.0] - 2026-08-20

### Added

- **Runtime & Deployment epic (S0–S14) — completed.** The production runtime shape is defined,
  shipped and exercised end-to-end:
  - **Platform pivot (ADR-0002)** — production target is now **on-premises bare metal**: Docker
    Compose fleets behind an NGINX weighted blue/green load balancer, with LocalStack emulating
    DynamoDB/S3 and Redis alongside. `docs/adr/0002-onprem-bare-metal-platform.md` records the
    decision; ADR-0001 (ECS Fargate + CodeDeploy) is superseded but its versioned manifests are
    kept untouched as a ready-made migration path to real AWS.
  - **Container** — multi-stage `Dockerfile` (Maven build stage → Temurin 21 JRE runtime),
    non-root user (UID/GID 10001), exec-form entrypoint, hardened `.dockerignore` (secrets and
    deploy/scripts material never enter the build context).
  - **Supply chain (CI `image` job)** — builds the image, fails on UID 0, scans with Trivy
    (HIGH/CRITICAL, SARIF → GitHub Security), uploads a CycloneDX SBOM + immutable image-ID
    artifact. The separate `image-security.yml` workflow was removed as duplication.
  - **Prod config contract (fail-fast, explicit credential source)** — `ProdConfigValidator`
    requires `aws.credentials.source` ∈ {`static`, `workload-identity`} in prod: `static`
    demands access/secret keys (LocalStack target), `workload-identity` forbids them (real-AWS
    task-role target). `AwsCredentialsProviderResolver` switches providers on the flag.
    Startup still aborts on any missing required value (`jwt.secret`, endpoints, bucket, Redis).
  - **Health model + automated proof** — liveness/readiness probes gate on DynamoDB + S3;
    probe paths unauthenticated, all other `/actuator/**` authenticated. New unit tests
    (`DynamoDbHealthIndicatorTest`, `S3HealthIndicatorTest`) and `HealthProbeFlowIT` prove the
    failure → readiness DOWN → recovery cycle against Testcontainers LocalStack.
  - **Graceful shutdown under load** — `scripts/shutdown-under-load-test.sh` verifies the six
    drain criteria under concurrent traffic (ran twice consecutively: 130/129 in-flight OK).
  - **Production stack (`deploy/docker-compose.bluegreen.yml`)** — blue (8081) / green (8082)
    fleets + NGINX LB (8080) + LocalStack + Redis; non-root, read-only root FS + tmpfs, CPU/RAM
    limits, health checks with start periods, `depends_on: service_healthy` ordering,
    `restart: unless-stopped`. Operator contract documented in `deploy/.env.example`.
  - **Blue/green rollout scripts** — `scripts/bluegreen-deploy.sh` (green readiness gate →
    canary 10% with 30 s observation + automatic abort-to-blue → cutover, optional image-tag
    argument) and `scripts/bluegreen-rollback.sh` (instant traffic revert). NGINX upstream uses
    `down` (not the nonexistent `weight=0`), explicit `keepalive 32`, `proxy_next_upstream error
    timeout`, passive health checks, and runtime DNS re-resolution (`resolver 127.0.0.11` +
    `zone` + `resolve`, pinned `nginx:1.27.4-alpine`) so recreated containers are picked up
    without reloads.
  - **Deploy + rollback exercise executed successfully** (previously pending): full local
    production exercise against the compose stack — smoke through the LB, canary deploy of v2,
    cutover proven by stopping blue while green served, rollback PASS, and LB resilience proven
    by recreating blue with a different IP (auto-healed without reload). Results recorded in
    `deploy/README.md` §1.6.
  - **Operational runbook** — new `docs/release-runbook.md`: deploy, rollback, secret rotation,
    readiness-DOWN triage, incident response (crash loops, LB 5xx, Redis outage, LocalStack
    outage), routine operations; legacy AWS deltas summarised in an appendix.
- **Reusable LocalStack seed script.** `scripts/seed-localstack.sh` (extracted from the README's
  "Configure LocalStack" block) is now idempotent with existence pre-checks, POSIX sh, and a
  fallback that runs `awslocal` inside the LocalStack container when the host AWS CLI cannot talk
  to emulated S3.
- **Basic rate limiting (S10 of quality-observability).** New `RateLimitFilter` +
  `FixedWindowRateLimiter` apply a per-client fixed-window throttle to `/api/v1/auth/register`
  and `/api/v1/auth/authenticate`. Configurable via the `rate-limit.*` properties
  (`enabled`, `limit`, `window`, `paths`, `client-ip-header`) — dev defaults in
  `application.yaml`, production contract (env overrides) in `application-prod.yaml`. Excess
  requests receive `429 Too Many Requests` in the canonical error envelope. In-memory,
  dependency-free (no Redis required). Covered by unit tests and the `RateLimitFlowIT`
  end-to-end test.
- **Docs sync for the runtime epic and test suite.** `docs/testing-playbook.md` restructured to a
  test taxonomy (level / naming / runtime / purpose) with explicit principles, a suite coverage
  matrix, a reading-failures matrix and a regression checklist that now includes the shutdown
  script; the "pyramid" references in `README.md`/`AGENTS.md` were dropped in favour of the
  taxonomy.

### Fixed

- **LocalStack seeding GSI shape (`scripts/seed-localstack.sh`).** The `Users.email-index` was
  provisioned keyed on `email` instead of the literal dotted attribute `profile.email` used by
  `DynamoDbConfig`. On freshly seeded volumes, registration succeeded but every login silently
  returned 401 (items stored, never indexed). The seed now matches the adapter's GSI mapping.
- **NGINX blue/green config.** `weight=0` does not exist in nginx and crashed the LB at startup;
  a fleet out of rotation now uses `down`. Added upstream keepalive (off by default before
  nginx 1.29.7), explicit `proxy_next_upstream error timeout` (never retries non-idempotent
  POSTs across fleets), and runtime DNS re-resolution — without it, nginx cached a fleet's IP at
  config load and routed to a dead address after any container recreation (observed as 504s
  during the exercise).
- **Rollback script.** `docker compose stop --no-deps` is not a valid flag combination (stop has
  no `--no-deps`); the script also now recreates only the target fleet on deploy.
- **CI image digest capture.** `{{index .RepoDigests 0}}` fails with "index out of range" for
  locally built, un-pushed images; the image job now records `{{.Id}}`, and the `runtime-smoke`
  job no longer runs when the build job failed (`if: always()` removed from job level).
- **Auth cache serialization.** `UserDetailsServiceImpl` cached Spring Security's `User`, which
  cannot be round-tripped by `GenericJackson2JsonRedisSerializer` (no default constructor) — the
  first request after a cache write worked, but the next cache hit failed with a
  `SerializationException`, surfacing as 401 on authenticated routes. The adapter now caches a
  Jackson-friendly DTO (`CachedUserDetails`) that implements `UserDetails`; authorities are
  rebuilt in memory and the Argon2id hash is preserved for login. Covered by
  `CachedUserDetailsTest` (round-trip through the real serializer) and verified end-to-end with
  Redis connected.

## [0.8.1] - 2026-08-19

### Added

- **Dockerfile.** Multi-stage Dockerfile created for production deployment: build stage with Maven, runtime stage with Eclipse Temurin 21 JRE. Supports `docker build` and container startup.
- **Quality observability (P2 epic).** Completed steps: JaCoCo coverage gate (35% line / 15% branch), SpotBugs static analysis (0 bugs), OWASP Dependency Check configured (report only). Dockerfile added for production builds. Rate limiting was added in a later entry (see `0.9.0`).

### Changed

- **Repository hygiene.** `.localstack/` runtime files removed from git tracking.

### Security

- **GitHub security scanning enabled.** CodeQL analysis workflow (`.github/workflows/codeql-analysis.yml`),
  Dependabot version updates (`.github/dependabot.yml`) and a Dependency Review workflow
  (`.github/workflows/dependency-review.yml`) added.

---

## [0.8.0] - 2026-08-18

### Added

- **Typed HTTP contracts completed (http-contracts epic).** Domain-level `ConflictException` and
  `NotFoundException`; REST authentication error handlers (`RestAuthenticationEntryPoint`,
  `RestAccessDeniedHandler`) returning the standard error envelope instead of default Spring
  responses; shared `RestErrorResponseWriter`. New `ErrorHandlingFlowIT` exercises validation
  errors, 401/403, 404/409 business conflicts and unknown routes end-to-end.
- **JaCoCo coverage reporting.** `jacoco-maven-plugin` with prepare-agent/report goals wired into
  surefire (`@{argLine}`); reports generated under `target/site/jacoco/`.
- **quality-observability-production epic specification** and implementation sequence added under
  `tasks/`.

---

## [0.7.0] - 2026-08-18

### Changed

- **Security and error-handling refinements.** JWT authentication filter restructured;
  `SecurityConfig` updates; `GlobalExceptionHandler` normalized; controller refinements for
  playlist and like flows.
- **Service-layer consistency pass.** Constructor-injection and port-alignment adjustments across
  album, like, playlist, song and user services; corresponding test updates.

---

## [0.6.0] - 2026-08-18

### Changed

- **Architectural purity (S2–S7 of the architectural-purity epic).** `PlaylistController` and
  `LikeController` now depend only on application inbound ports (`*UseCase`); direct
  `UserRepository` calls replaced by the new `GetCurrentUserUseCase` application port + service
  (registered in `ApplicationBeanConfig`). Public API behaviour unchanged.

### Removed

- Stray LocalStack TLS cache files (`server.test.pem*`) dropped from git tracking.

---

## [0.5.0] - 2026-08-18

### Changed

- **Typed exceptions for upload confirmation.** `ConfirmSongUploadService` throws
  `NotFoundException` (HTTP 404 with the standard error envelope) instead of
  `IllegalArgumentException`/400 for business rule violations (song not belonging to album,
  storage key mismatch).

---

## [0.4.0] - 2026-08-18

### Added

- **Case-insensitive search (songs + artists).** Search GSIs now sort on write-time normalized
  keys (`searchTitle` for `title-search-index`, `searchName` for `name-search-index`) instead of
  the raw display value; the query lowercases the input before `sortBeginsWith`, so `test`,
  `TEST` and `Test` return the same results.
- **Real cursor pagination for search.** `/api/v1/songs/search` and `/api/v1/artists/search`
  moved from fake offset pagination to DynamoDB cursor pagination: they accept `limit` + optional
  `cursor` and return `content` + `nextPageToken` + `hasNext` (`PageResponse`). The old
  `page`/`size`/`sort` params were removed — an intentional API change.
- **Max page size.** `PageRequest.MAX_PAGE_SIZE = 50`; search use cases reject larger sizes with
  a 400 (`pageSize must not exceed 50`). Malformed cursor tokens are rejected with a 400 instead
  of a 500.
- **Honest pagination metadata.** `PageResponse` now carries `hasNext` (playlist listing and
  search); playlist listing already reported a real `nextPageToken` from `LastEvaluatedKey`.
- **New integration tests (LocalStack):** `SongSearchPaginationIT`, `ArtistSearchPaginationIT`
  (mixed-case search returns the same results, pages advance via the cursor, invalid cursor
  rejected).
- **Fixed latent `DynamoDbCursorHelper` bug**: cursor encode/decode serialized the SDK
  `AttributeValue` map directly (Jackson cannot round-trip it); it now stores the scalar key map
  with a type prefix. The encode path had never been exercised before (no test produced a non-empty
  `LastEvaluatedKey`).

### Changed

- **SpotBugs static analysis (S2).** Added `spotbugs-maven-plugin` 4.9.3.0 with `threshold=High`, `effort=Max`; CI runs `spotbugs:check` after unit tests and fails the build on High-severity findings. Fixed initial finding: `DM_DEFAULT_ENCODING` in `JwtService.getSigningKey()`.
- **JaCoCo coverage reporting (S1).** Added `jacoco-maven-plugin` 0.8.9 with `prepare-agent` and `report` goals; `./mvnw test` now generates coverage reports in `target/site/jacoco/`. Surefire `argLine` updated to `@{argLine}` for agent integration.
- **Exception type hardening.** `ConfirmSongUploadService` now throws `NotFoundException` instead of
  `IllegalArgumentException` for business rule violations (song not belonging to album, storage key mismatch),
  mapping to HTTP 404 with the standard error envelope instead of 400.
- **Architectural purity.** `PlaylistController` and `LikeController` refactored to depend only on
  application inbound ports (`*UseCase`); direct `UserRepository` calls removed and replaced with
  `GetCurrentUserUseCase` application service; controllers become thin translation layers; public API
  behaviour unchanged.
- **Data model:** Songs/Artists tables carry `searchTitle`/`searchName` (lowercased at save time);
  the search GSIs' sort keys moved from `title`/`name` to `searchTitle`/`searchName`. README
  LocalStack setup block and `AbstractIntegrationTest` provisioning updated. Existing rows written
  before this change are not searchable until the tables are re-provisioned (dev only).
- **Ports:** `SongMetadataRepository.searchByTitle` and `ArtistRepository.searchByName` take an
  `exclusiveStartKey` cursor; `SearchSongsCommand`/`SearchArtistsCommand` carry it as `cursor`.
- **Web:** search endpoints return `PageResponse` instead of Spring `Page`.

### Deferred

- Production deployment runtime shape (how the artifact is shipped/run — container image, platform).
- E2E tests not yet wired into the build via the failsafe plugin (they run via
  `./mvnw test -Dtest='*IT'` and in the CI workflow).

## [0.3.0] - 2026-08-18

### Added

- **Data consistency & modelling guarantees** (`docs/data-model-decisions.md` records the
  single-source-of-truth decisions):
  - Playlists live only in the `Playlists` table (`ownerId-index`); the embedded `playlists`
    collection was removed from the `User` aggregate and the `Users` table.
  - Album songs live only in the `Songs` table; new `albumId-index` GSI + `findByAlbumId` on
    `SongMetadataRepository` replace the removed embedded `Album.songs`.
  - `MAX_PLAYLISTS_PER_USER = 10` is enforced persistently (`PlaylistRepository.countByOwnerId`)
    before creating a playlist.
  - User registration is atomic against duplicate emails: `UserRepository.createIfEmailNotExists`
    writes the user + an email marker in a single `TransactWriteItems` against the new `UserEmails`
    table (`attribute_not_exists`), with retry on transient transaction conflicts.
  - Playlist mutations use optimistic locking: `Playlist` carries a `version`; `create`/`update`
    use conditional writes (`attribute_not_exists(id)` / `version = :expected`) and a stale write
    throws `PlaylistConcurrentModificationException`.
  - `SongStoragePort.abortUpload` removes an orphan S3 multipart upload when metadata persistence
    fails after `generateUploadUrl`; `@Transactional` removed from the song upload services (it
    gave a false sense of atomicity across S3 and DynamoDB).
- **New integration tests (LocalStack):** `PlaylistLimitAndConcurrencyIT` (10th succeeds / 11th
  rejected, stale concurrent update rejected), `EmailUniquenessIT` (duplicate email rejected,
  only one concurrent registration with the same email succeeds), `AlbumSongConsistencyIT`
  (album song query reflects the uploaded song).

### Changed

- **Data model:** `Users` and `Albums` tables no longer store nested collections; `Songs` gained
  the `albumId-index` GSI; new `UserEmails` table. README LocalStack setup block and
  `AbstractIntegrationTest` provisioning updated to match.
- **`PlaylistRepository`** now exposes `create` / `update` / `countByOwnerId` instead of `save`.
- **`RegisterUserService`** no longer pre-checks the email with a non-atomic read; it relies on
  the atomic `createIfEmailNotExists` write.

### Deferred

- Production deployment runtime shape (how the artifact is shipped/run — container image, platform).
- E2E tests not yet wired into the build via the failsafe plugin (they run via
  `./mvnw test -Dtest='*IT'` and in the CI workflow).

## [0.2.0] - 2026-08-19

### Added

- **`S3SongStorageAdapterIT`** — LocalStack slice test for the song storage adapter:
  `generateUploadUrl` → direct PUT to presigned URL → `confirmUpload` → `getStreamingUrl` →
  HTTP GET on signed URL returns 200 with correct `Content-Type` and byte-identical body.
- **CI/CD pipeline made green and deterministic.** The GitHub Actions workflow
  (`.github/workflows/ci.yml`) now runs pure unit tests, then the `*IT` slice + E2E suite, then
  `./mvnw clean package`, gating each step on the previous one. Deprecated
  `actions/setup-java@v4` updated to `@v5`. `DynamoDbConfig` and `S3Config` build AWS clients with
  `StaticCredentialsProvider` from `AwsProperties.credentials()` (bound from `AWS_ACCESS_KEY_ID` /
  `AWS_SECRET_ACCESS_KEY` env vars with LocalStack-compatible defaults), so tests authenticate
  against LocalStack on clean runners instead of relying on `DefaultCredentialsProvider` (which
  ignored the test properties). `ProdConfigValidator` now also requires the two credential
  variables in prod. `SpotpobreApplicationTests` closes the Spring context it starts (try-with-
  resources instead of a leaked `main()`).

### Fixed

- **Song streaming URL now points to the correct S3 object.** `S3SongStorageAdapter.getStreamingUrl`
  uses the storage key persisted during upload (the UUID stored on the `Song` aggregate as `storageId`),
  not the `SongId`. `GetSongStreamUrlService` loads the `Song`, extracts `getStorageId()`, and passes
  it to `SongStoragePort.getStreamingUrl(storageKey)`. Previously the signed URL referenced a non-existent
  object key. Covered by `S3SongStorageAdapterIT` (LocalStack round-trip: upload → confirm → stream → download)
  and `ArtistSongFlowIT.shouldDownloadSongContentViaSignedStreamingUrl()` (full E2E: presigned PUT + signed GET
  returns HTTP 200 with correct `Content-Type: audio/mpeg` and byte-identical body).
- **RestAssured query-parameter double-encoding bypassed in presigned-URL tests.** The presigned PUT and
  GET URLs produced by LocalStack 3.x contain query parameters (e.g. `X-Amz-Content-Sha256`) that
  RestAssured re-encodes (`%` → `%25`), causing signature mismatches. Tests that hit signed URLs now use
  the JDK `HttpClient` directly, preserving the original encoding.

### Deferred

- Production deployment runtime shape (how the artifact is shipped/run — container image, platform;
  the env-var contract itself is defined in `application-prod.yaml`).
- Pagination on more list endpoints; rate limiting; email verification and password recovery.
- Additional quality gates (JaCoCo coverage, SpotBugs, dependency/security scanning).

## [0.1.0] - 2026-08-18

### Added

- **Direct-to-S3 song upload via presigned URLs.** The API no longer accepts audio as `byte[]` or
  `MultipartFile`. `POST /api/v1/albums/{albumId}/songs` (`ROLE_ARTIST`) validates content type
  and size (max 500 MB), persists song metadata, and returns short-lived (10 min) presigned PUT
  URL(s). Files larger than 100 MB receive S3 multipart part URLs. The client uploads directly to
  S3; `POST /api/v1/albums/{albumId}/songs/{songId}/confirm` verifies the object (or completes
  multipart with part ETags). Domain `SongStoragePort` exposes `generateUploadUrl` /
  `confirmUpload` with pure value objects (`SongUploadCommand`, `PresignedUploadResult`,
  `ConfirmUploadCommand`). AWS SDK v2 stays in `S3SongStorageAdapter`.
- **Twelve-Factor compliance hardening.**
  - Factor 3 — production config contract: `application-prod.yaml` binds every env-specific value
    from env vars and `ProdConfigValidator` (prod profile only) aborts startup with a clear message
    when a required variable is missing (fail fast; no silent fallback to dev defaults).
  - Factor 9 — graceful shutdown: `server.shutdown: graceful` with a 30s per-phase timeout.
  - Factor 11 — structured logging: `logback-spring.xml` wires `logstash-logback-encoder`
    (JSON lines via `LogstashEncoder` on the `json` profile; default profile keeps the console
    pattern).
  - Factor 5 — CI workflow added (`.github/workflows/ci.yml`): unit+slice tests, the `*IT` E2E
    suite and `./mvnw clean package` on every push/PR.
- **Password hashing behind a domain port.** New `PasswordHasher` port in
  `domain/user/port/` implemented by the `SpringSecurityPasswordHasher` adapter
  (`infrastructure/security/adapter`). The application layer depends only on the port, so the
  hashing library can be swapped by changing one `PasswordEncoder` bean without touching business
  code.
- **Authentication behind a domain port.** New `AuthenticationPort` (`domain/user/port/`) returns a
  pure domain `AuthenticatedUser` (wrapping the `User` aggregate, no Spring Security types). The
  application layer's `AuthenticationService` now depends only on that port; the
  `SpringSecurityAuthenticationAdapter` (`infrastructure/security/adapter/`) owns the
  `AuthenticationManager` injection and maps to/from Spring Security types. Item cleared from the
  AGENTS technical debt list; `docs/coding-standards.md` updated to drop the
  "`AuthenticationManager` injection is tracked debt" note.
- **Domain layer is now truly pure Java.** Every Lombok annotation (`@Getter`, `@Setter`,
  `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`) has been removed from the six domain
  entities (`User`, `Artist`, `Album`, `Song`, `Playlist`) and `UserProfile` was already a record.
  The domain now has zero annotation processors and zero external dependencies on the model side:
  fields, constructors, getters, `equals`/`hashCode`/`toString` and small fluent builders are
  written by hand. The public API of each entity is preserved (factory methods, getters, the
  fluent `X.builder()...build()`, and the four setters the persistence layer actually needs —
  `User.setId`/`User.setProfile`/etc. are gone; `Song.setId`, `Song.setAlbumId`,
  `Album.setArtistId`, `Playlist.setOwnerId` remain because the DynamoDB Enhanced mapper or
  existing tests call them). `UserPersistenceMapper`, `CreateAlbumService`,
  `*LikeStrategyTest`, `UploadSongServiceTest` and `RemoveSongFromPlaylistServiceTest` keep
  working unchanged. README "100% framework-free" wording now matches reality. Item cleared
  from the AGENTS technical debt list; `docs/coding-standards.md` updated.
- **Argon2id password hashing.** The `SecurityConfig` `PasswordEncoder` bean switched from BCrypt
  to `Argon2PasswordEncoder` (Spring Security defaults for 5.8+); BouncyCastle
  (`bcprov-jdk18on`) added to satisfy Argon2. (Dependency approved by human — AGENTS rule 5.)
- **`POST /api/v1/albums/{albumId}/songs` secured with `ROLE_ARTIST`.** The song upload route
  (previously only `authenticated()`) now enforces the artist role explicitly.
- **Unit tests for album, likes and error handling.** `CreateAlbumServiceTest`,
  `ToggleLikeServiceTest`, `LikeStrategyFactoryTest`, `SongLikeStrategyTest`,
  `ArtistLikeStrategyTest`, `PlaylistLikeStrategyTest`, `GlobalExceptionHandlerTest`.
- **Testcontainers upgraded to 1.21.4** (docker-java in 1.19.x cannot talk to Docker 29+).
- **`docs/lessons.md`** — durable lessons from the infra/test hardening (Testcontainers, DynamoDB
  empty-page crashes, GSI drift, context-cache vs. container lifecycle, Argon2/BouncyCastle,
  authority prefix mismatch, IT authoring).
- **`CHANGELOG.md`** — this file; update policy copied from the `tycoma` project (AGENTS rule 8,
  `docs/coding-standards.md` § Doc sync and § 12).

### Changed

- **Authorities standardized on the `ROLE_` prefix.** `SecurityConfig` now uses `hasRole(...)`
  (matching `GetUserDetailsService`, which builds `ROLE_<NAME>`); JWT claims in
  `AuthenticationController` carry the same prefix. Previously `hasAuthority(Role.ADMIN.name())`
  never matched, causing silent 403s.
- **`GlobalExceptionHandler` 500 log is diagnostic.** It now logs `method`, `URI` and exception
  class + message alongside the stack trace (`Unexpected error on POST /api/v1/auth/register:
  ...`).
- **README LocalStack setup block corrected** to the real schema (Users GSI on `profile.email`,
  Artists/Songs search indexes on `searchPartition` + `name`/`title`).
- **README Current State / Roadmap synced** (Argon2id; upload flow exercised end-to-end).
- **Pagination decoupled from Spring Data.** New pure domain types `PageRequest` / `PageResult`
  (`domain/common/pagination`) replace `org.springframework.data.domain.Page` / `Pageable` in the
  domain ports (`ArtistRepository`, `SongMetadataRepository`, `PlaylistRepository`) and the
  application use cases/services (artist/song search, playlists by owner). Adapters translate
  between the domain types and the storage-native mechanism; the web layer rebuilds the Spring
  `Page` for the search endpoints (identical REST shape) and maps `PageResult` to `PageResponse`
  for playlists. The `DynamoDbPage` infrastructure model was removed — both items cleared from the
  AGENTS technical debt list.

### Fixed

- **Playlist IDOR (Insecure Direct Object Reference) eliminated.** All four playlist mutation
  use cases (`UpdatePlaylistDetailsService`, `DeletePlaylistService`, `AddSongToPlaylistService`,
  `RemoveSongFromPlaylistService`) now enforce ownership via `PlaylistOwnershipGuard`, which throws
  `ForbiddenException` (mapped to HTTP 403) when the authenticated user is not the playlist owner.
  The `currentUserId` is injected from the security context in every controller mutation path — never
  trusted from the request body. Covered by unit tests (per-service forbidden/not-found cases) and
  E2E A-versus-B tests in `PlaylistFlowIT`.
- **DynamoDB empty-page crashes.** `findByProfileEmail` used `page.items().get(0)` and the
  playlist/like queries used `iterator().next()`; all now stream empty pages safely
  (`IndexOutOfBoundsException` / `NoSuchElementException` on fresh tables).
- **Missing `title-search-index` in the Songs schema.** `searchByTitle` queried an index that was
  neither in `DynamoDbConfig` nor the README setup block; schema, provisioning and docs aligned.
- **Wrong-password authentication returned 500** (leaking behaviour); now a generic 401 via
  `BadCredentialsException` handling, pinned by tests.
- **E2E tests were unrunnable** against the local stack and had drifted from the real contract:
  reworked `AuthenticationFlowIT` / `ArtistSongFlowIT` / `PlaylistFlowIT` to provision schema in
  `AbstractIntegrationTest`, share one LocalStack container per JVM, seed role-bearing users
  directly, and exercise the real upload route.

### Deferred

- Production environment shape (`application-prod.yaml` empty; JWT secret, AWS endpoints and
  Redis host from env vars in real deployments).
- Pagination on more list endpoints; rate limiting; email verification and password recovery.
- CI pipeline and dependency/security gates.