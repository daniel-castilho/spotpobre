# Changelog

All notable changes to Spotpobre API will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project
intends to follow [Semantic Versioning](https://semver.org/) starting from its first tag.

## [Unreleased]

### Added

- **Runtime & Deployment (Steps 0–6 of the runtime-deployment epic).** The production runtime shape
  is now defined and partially shipped:
  - **ADR** (`docs/adr/0001-production-platform.md`) — ECS Fargate + ECR + ALB + Secrets Manager +
    ECS task-role identity + CodeDeploy blue/green.
  - **Container** — multi-stage `Dockerfile` (Maven build stage → Temurin 21 JRE runtime), non-root
    user (UID/GID 10001), exec-form entrypoint, `.dockerignore`.
  - **Supply chain** — base images pinned by digest; `.github/workflows/image-security.yml` runs
    on push/PR: fails the build if the image runs as UID 0, scans with Trivy (HIGH/CRITICAL,
    SARIF uploaded to GitHub Security), and uploads a CycloneDX SBOM artifact.
  - **Prod config contract (fail-fast)** — `ProdConfigValidator` (prod profile only) now rejects
    static AWS credentials in prod and aborts startup when any required value is missing
    (`jwt.secret`, `aws.region`, DynamoDB/S3 endpoints, bucket name, Redis host). `AwsProperties`
    credentials are optional so the AWS clients resolve them from the ECS task role via
    `AwsCredentialsProviderResolver` (falling back to static values only for dev/tests).
  - **Health model** — `management.endpoint.health.probes.enabled: true` exposes liveness and
    readiness probes; the readiness group gates on the critical dependencies (DynamoDB + S3) via
    new `DynamoDbHealthIndicator` / `S3HealthIndicator`; `show-details: when-authorized`. The probe
    paths are reachable without auth (for the ALB), every other `/actuator/**` route requires
    authentication (`SecurityConfig`). Verified end-to-end against LocalStack: readiness goes
    DOWN while the S3 bucket is missing and recovers to UP once it exists.
- **Basic rate limiting (S10 of quality-observability).** New `RateLimitFilter` +
  `FixedWindowRateLimiter` apply a per-client fixed-window throttle to `/api/v1/auth/register`
  and `/api/v1/auth/authenticate`. Configurable via the `rate-limit.*` properties
  (`enabled`, `limit`, `window`, `paths`, `client-ip-header`) — dev defaults in
  `application.yaml`, production contract (env overrides) in `application-prod.yaml`. Excess
  requests receive `429 Too Many Requests` in the canonical error envelope. In-memory,
  dependency-free (no Redis required). Covered by unit tests and the `RateLimitFlowIT`
  end-to-end test.

### Fixed

- **Auth cache serialization.** `UserDetailsServiceImpl` cached Spring Security's `User`, which
  cannot be round-tripped by `GenericJackson2JsonRedisSerializer` (no default constructor) — the
  first request after a cache write worked, but the next cache hit failed with a
  `SerializationException`, surfacing as 401 on authenticated routes. The adapter now caches a
  Jackson-friendly DTO (`CachedUserDetails`) that implements `UserDetails`; authorities are
  rebuilt in memory and the Argon2id hash is preserved for login. Covered by
  `CachedUserDetailsTest` (round-trip through the real serializer) and verified end-to-end with
  Redis connected.

## [0.4.1] - 2026-08-19

### Added

- **Dockerfile.** Multi-stage Dockerfile created for production deployment: build stage with Maven, runtime stage with Eclipse Temurin 21 JRE. Supports `docker build` and container startup.
- **Quality observability (P2 epic).** Completed steps: JaCoCo coverage gate (35% line / 15% branch), SpotBugs static analysis (0 bugs), OWASP Dependency Check configured (report only). Dockerfile added for production builds. Rate limiting was added in a later entry (see `Unreleased`).

### Changed

- **Repository hygiene.** `.localstack/` runtime files removed from git tracking.

### Security

- **GitHub security scanning enabled.** CodeQL analysis workflow (`.github/workflows/codeql-analysis.yml`),
  Dependabot version updates (`.github/dependabot.yml`) and a Dependency Review workflow
  (`.github/workflows/dependency-review.yml`) added.

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