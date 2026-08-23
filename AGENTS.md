# AGENTS.md

Spotpobre API — a music streaming backend built with **Java 21 + Spring Boot 3.5** as a strict
**Clean Architecture** (layered) service. The business core (`domain` + `application`) is kept
independent of `infrastructure` details: persistence (DynamoDB), storage (S3), security (JWT) and
the web layer (Spring Web controllers) are all behind ports.

Sources of truth: `README.md`, `pom.xml`, `docker-compose.yaml`, `docs/coding-standards.md`,
`docs/testing-playbook.md`, `docs/twelve-factor.md`. Re-read the relevant parts before starting any
task.

## Critical rules (never violate)

1. `domain/` and `application/` must **never import `infrastructure` code or cloud/framework
   adapters** — no `com.spotpobre.backend.infrastructure.*`, `software.amazon.*`, `io.awspring.*`,
   `org.mapstruct.*`, `org.springdoc.*`, or `org.springframework.web.*`.
   Verify before finishing:
   `grep -rEn "^import (com\.spotpobre\.backend\.infrastructure|software\.amazon|io\.awspring|org\.mapstruct|org\.springdoc|org\.springframework\.web)" src/main/java/com/spotpobre/backend/domain src/main/java/com/spotpobre/backend/application`
   must return nothing (matches real imports only, not comments). Known exceptions/leaks are
   tracked in "Known technical debt" — do **not** silently extend them. Today there are **no
   known leaks** in `domain/`/`application/`; any new one must be flagged below.
2. Business logic lives in `domain` (entities, value objects, rich rules) and `application`
   (use cases). `infrastructure/web` controllers and DTOs are **thin** — no business rules, no
   repository calls from controllers. Controllers depend only on `application/<feature>/port/in`
   interfaces (e.g. `CreateArtistUseCase`).
3. **Zero direct DynamoDB / S3 / Redis / JWT usage outside `infrastructure/`.** Every operation
   goes through a domain port (`UserRepository`, `AlbumRepository`, `SongMetadataRepository`,
   `SongStoragePort`, `LikeRepository`, ...) implemented by an adapter
   (`DynamoDb*RepositoryAdapter`, `S3SongStorageAdapter`, `CdnSongStorageAdapter`).
4. Passwords are hashed with **Argon2id** via Spring Security's `PasswordEncoder`, exposed to the
   application layer only through the domain `PasswordHasher` port (adapter:
   `SpringSecurityPasswordHasher`). Never store plaintext, and never commit secrets, `.env`-style
   files or real credentials. The `jwt.secret` in `application.yaml` is a **dev-only example** —
   production must override it via environment variables.
5. Do **not** add a new dependency (Maven coordinate) to `pom.xml` without explicit human
   approval.
6. **English only.** All identifiers, comments, Javadoc, commit messages, documentation and log
   messages must be in English. (Existing Portuguese comments in config files are legacy — do not
   add new ones.)
7. Every new endpoint must have an explicit authorization rule in
   `infrastructure/security/config/SecurityConfig.java`. New admin-only routes require
   `ROLE_ADMIN`; new artist-owned routes require `ROLE_ARTIST`; never default a mutating route to
   permit-all.
8. **Doc sync is part of Done.** After milestone-sized work (a new feature area, a behaviour
   change, a debt resolution) the same change set or the immediate follow-up commit MUST update:
   1. `README.md` → "Current State" / "Roadmap"
   2. `CHANGELOG.md` → an entry under the next version or `Unreleased` (Keep a Changelog format)
   3. `AGENTS.md` → "Known technical debt" (add or clear)
   Do **not** claim work DONE while `README.md` or `CHANGELOG.md` still describes the previous
   state as current.
9. Keep the default test suite green: `./mvnw test` must pass before finishing. Domain tests never mock;
   application tests mock the domain ports only. Do not weaken an existing test to make a change
   pass.

## Commands

| Purpose | Command |
| :--- | :--- |
| Run the dev server | `./mvnw spring-boot:run` → http://localhost:8080 |
| Run pure unit tests (no Docker needed) | `./mvnw test` |
| Run slice + E2E tests explicitly (needs Docker + LocalStack) | `./mvnw test -Dtest='*IT'` |
| Full gate: unit + slice/E2E tests, SpotBugs, JaCoCo, OWASP and jar | `./mvnw verify` |
| Production build | `./mvnw clean package` |
| Start external services (LocalStack + Redis) | `docker-compose up -d` |
| Stop external services | `docker-compose down` |
| Interactive API docs | http://localhost:8080/swagger-ui.html |

> Prefer the fast unit-test loop (`./mvnw test`); it needs **no Docker**. The slice integration and
> E2E tests (`*IT` classes — e.g. `DynamoDbPlaylistRepositoryAdapterIT`, `S3SongStorageAdapterIT`,
> `AuthenticationFlowIT`, `ArtistSongFlowIT`, `PlaylistFlowIT`) are **not** picked up by the default
> surefire run — `./mvnw verify` runs them via the failsafe plugin together with the quality checks
> and the jar (needs Docker), or run them alone with `./mvnw test -Dtest='*IT'` after
> infrastructure changes.

## Architecture

The application follows Clean Architecture, split into three layers with a strict dependency rule
that always points inward:

```
src/main/java/com/spotpobre/backend/
├── domain/           Entities, value objects & outbound port interfaces
│   ├── album/  artist/  like/  playlist/  song/  user/
├── application/      Use-case orchestration (pure Java, depends only on domain ports)
│   └── <feature>/
│       ├── port/in/  *UseCase interfaces consumed by the web layer
│       └── service/  *Service implementations of those use cases
└── infrastructure/   Adapters — Spring Web, DynamoDB, S3, JWT, config
    ├── config/       Beans, security, cache, AWS & properties configuration
    ├── persistence/  DynamoDB repository adapters, entities (*Document) & mappers
    ├── security/     JWT filter, UserDetailsService, SecurityConfig
    ├── storage/      S3 adapter + CDN storage adapter (SongStoragePort)
    └── web/          Controllers, DTOs and MapStruct API mappers
```

- **`domain`** — `User`, `Artist`, `Album`, `Song`, `SongMetadata`, `Playlist`,
  `Like`, value objects (`*Id`), `Role`/`EntityType` enums, and the outbound port interfaces
  (`*Repository`, `SongStoragePort`). Rich business rules live here. Pure Java — no Lombok, no
  annotation processors; getters, `equals`/`hashCode`/`toString` and small fluent builders are
  hand-written.
- **`application`** — use-case implementations such as `CreateArtistService`,
  `ToggleLikeService` (a `LikeStrategy` family: `SongLikeStrategy`, `ArtistLikeStrategy`,
  `PlaylistLikeStrategy` behind `LikeStrategyFactory`). They depend on domain ports only and stay
  ignorant of `infrastructure/`.
- **`infrastructure`** — implements the domain ports (`DynamoDbUserRepositoryAdapter`,
  `DynamoDbAlbumRepositoryAdapter`, ...), exposes REST via thin controllers, and holds all
  framework configuration (`DynamoDbConfig`, `S3Config`, `CacheConfig`, `JwtProperties`,
  `SecurityConfig`).
- `application` services may use Lombok and Spring annotations (`@Component`,
  `@RequiredArgsConstructor`, `@Transactional`) — those are acceptable. They must **not** use the
  `infrastructure` layer or AWS/MapStruct/springdoc/web imports (rule 1).

## Conventions

- Java 21; 4-space indent, no tabs. Follow the layout already used in the three layers.
- Naming: use-case interfaces `*UseCase` in `application/<feature>/port/in/`; implementations
  `*Service` in `application/<feature>/service/`. Persistence: `DynamoDb*RepositoryAdapter`
  (adapters), `DynamoDb*Repository` + `*Impl` (repositories), `*Document` (entities),
  `*PersistenceMapper`. Web: `*Controller`, DTOs under `web/dto/request` and `web/dto/response`,
  MapStruct mappers `*ApiMapper` (component model `spring`).
- DTOs for every external input/output — never expose domain entities through the API. Keep
  `web` mappers thin; validate inputs (`spring-boot-starter-validation`) and centralize errors in
  `GlobalExceptionHandler` (`ErrorResponse` / `ValidationErrorResponse`).
- Adapters translate explicitly between domain and persistence/web models — no silent casts.
- New endpoints: add the `*UseCase` port in `application`, implement it in `application/service`,
  expose it via a thin controller and a MapStruct mapper, and add an explicit `SecurityConfig`
  rule (rule 7).
- Pagination: pure domain types `PageRequest`/`PageResult` in `domain/common/pagination` are used
  by ports, use cases and services. Adapters translate to/from the storage-native mechanism and the
  web layer adapts them back to the REST response (`PageResponse` + cursor token for playlists and
  for song/artist search).

## Testing

- **JUnit 5 + Mockito** unit tests live in `src/test/java/.../domain` and `.../application`.
  Domain tests instantiate pure entities (no mocks, no Spring); application tests mock the domain
  ports only. Name tests `*Test` (e.g. `CreatePlaylistServiceTest`). `./mvnw test` runs **only**
  these — no Docker, no AWS credentials.
- **Slice integration:** `DynamoDbPlaylistRepositoryAdapterIT`, the data-consistency ITs
  (`PlaylistLimitAndConcurrencyIT`, `EmailUniquenessIT`, `AlbumSongConsistencyIT`) and the
  search-pagination ITs (`SongSearchPaginationIT`, `ArtistSearchPaginationIT`) boot
  **Testcontainers** with **LocalStack** to exercise the DynamoDB adapters for real;
  `S3SongStorageAdapterIT` exercises the full storage round-trip (upload → confirm → stream →
  download). All are `*IT` classes (requires Docker).
- **End-to-end:** `AuthenticationFlowIT`, `ArtistSongFlowIT`, `PlaylistFlowIT` use **RestAssured**
  against the full application on a random port (`@SpringBootTest(webEnvironment =
  RANDOM_PORT)`) with Testcontainers. `./mvnw verify` runs them via the failsafe plugin, or run
  all `*IT` classes alone with `./mvnw test -Dtest='*IT'`.
- Test method names: `method_condition_expectedResult` or descriptive `should ...`.
- After significant changes run `./mvnw clean package` and smoke-test against
  `docker-compose up -d` (plus the LocalStack setup commands from `README.md`).
- Full guidance: `docs/testing-playbook.md` (taxonomy, principles, patterns, regression checklist, smoke).

## Releases

- The first annotated tag (`v0.1.0`) was created on commit `f0716a7` covering playlist ownership
  (IDOR fix), presigned S3 upload, and test hardening. Tag only when a milestone meets its
  Definition of Done and the human asks for it.
- Before tagging:
  1. Add a high-level entry to `CHANGELOG.md` (or promote the `Unreleased` block to a version).
  2. Update `README.md` → "Current State".
  3. Update `AGENTS.md` → "Known technical debt".
  4. Create the annotated tag (`git tag -a v0.X.0 -m "v0.X.0 — <short title>"`).

## Known technical debt (resolve later)

Items that currently violate the rules above. Do **not** silently "fix" them, and do **not** add
new violations — flag them to the human instead.

- **MapStruct unmapped-property warning.** `ArtistApiMapper` produces a compiler warning for the
  unmapped `songs` target property on `ArtistResponse` (the `songs` field is intentionally omitted —
  artists are not returned with their song list at this endpoint). This is a pre-existing benign
  warning, not a SpotBugs finding; a `@Mapping(target = "songs", ignore = true)` or a deliberate
  comment would silence it.
- **Playlist-limit check is count-then-insert.** `CreatePlaylistService` enforces
  `MAX_PLAYLISTS_PER_USER` with `countByOwnerId` before insert; two strictly simultaneous creates
  could both pass the count and exceed 10. Documented as accepted for P1 in
  `docs/data-model-decisions.md` — closing it needs a conditional/transactional insert or a
  dedicated counter.
- **Auth cache has no Redis-outage fallback.** `UserDetailsServiceImpl.loadUserByUsername` is
  `@Cacheable(USER_CACHE)` with no `unless`/fallback, so a Redis outage makes every authenticated
  request fail (JWT filter → cache miss → `RedisConnectionFailureException`) even though the
  readiness probe reports UP (Redis is deliberately not part of the readiness gate — it is a cache,
  S6 decision in `application.yaml`). Closing it needs either a cache-outage tolerant `CacheManager`
  (degrade to direct DynamoDB lookup), or adding Redis to the readiness gate. The serialization
  side is already fixed (`CachedUserDetails` DTO — see CHANGELOG `Unreleased`).
- **Blue/green exercise proven locally; AWS-native path unexercised.** The on-premises
  production target (ADR-0002) was exercised end-to-end against the compose stack: canary deploy,
  cutover, rollback and LB IP-change resilience all PASS (`deploy/README.md` §1.6). What remains
  unproven is only the **legacy AWS-native path** (ADR-0001: CodeDeploy canary 10%/5min shift,
  rollback alarms, task-role identity) — it activates when the project migrates to a real AWS
  account (see README Roadmap); its first real deployment must prove those gates end to end.
- **Shutdown smoke is non-blocking in CI.** The `runtime-smoke` job warns but does not fail the
  pipeline (testing-playbook gap 7). Promote it to a hard gate so the pipeline fails on
  shutdown-drain regressions.

## Notes

- Do **not** push to the remote unless the human explicitly asks.
- For current project status and pending work, see `README.md` ("Current State" / "Roadmap").
- Secrets live in environment variables only — never committed. The `jwt.secret` in
  `application.yaml` is a dev-only example.