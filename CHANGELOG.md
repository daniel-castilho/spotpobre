# Changelog

All notable changes to Spotpobre API will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project
intends to follow [Semantic Versioning](https://semver.org/) starting from its first tag.

## [Unreleased]

### Added

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
- Streaming / chunked song upload (today multipart upload is buffered as a `byte[]`).
- Pagination on more list endpoints; rate limiting; email verification and password recovery.
- CI pipeline and dependency/security gates.