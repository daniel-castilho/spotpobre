# Coding Standards — Java / Spring Boot / Maven (Spotpobre API)

Practical reference for solo and AI-assisted development. Goal: **consistency over time**, not ceremony. Living document — edit as the project evolves.

**Relationship to other docs:**

| Doc               | Wins when                                            |
| ----------------- | ---------------------------------------------------- |
| `AGENTS.md`       | Project conventions, release flow, hard agent rules  |
| **This file**     | Day-to-day coding detail that does not fit in AGENTS |
| `docs/lessons.md` | Durable rules learned the hard way (pending)         |

Where this file conflicts with `AGENTS.md`, **`AGENTS.md` wins**.

---

## 1. Naming

| Element                        | Convention                                       | Example                                                        |
| ------------------------------ | ------------------------------------------------ | -------------------------------------------------------------- |
| Packages                       | lowercase, feature-first per layer               | `com.spotpobre.backend.application.playlist.service`           |
| Use-case interfaces            | `*UseCase`                                       | `CreatePlaylistUseCase`                                         |
| Use-case implementations       | `*Service`                                       | `CreatePlaylistService`                                         |
| Domain models / value objects  | `PascalCase`; ids as `*Id`                       | `Album`, `SongMetadata`, `PlaylistId`, `UserId`                 |
| Domain outbound ports          | `*Repository` / `*StoragePort`                   | `LikeRepository`, `SongStoragePort`                             |
| Persistence adapters           | `DynamoDb*RepositoryAdapter`                     | `DynamoDbPlaylistRepositoryAdapter`                             |
| Persistence repositories       | `DynamoDb*Repository` + `*Impl`                  | `DynamoDbPlaylistRepository`, `DynamoDbPlaylistRepositoryImpl`  |
| Persistence entities           | `*Document`                                      | `PlaylistDocument`, `UserDocument`                              |
| Persistence mappers            | `*PersistenceMapper`                             | `UserPersistenceMapper`                                         |
| Web controllers                | `*Controller`                                    | `PlaylistController`                                            |
| Web DTOs                       | `*Request` / `*Response`                        | `RegisterRequest`, `PlaylistResponse`                           |
| Web API mappers                | `*ApiMapper` (MapStruct)                         | `PlaylistApiMapper`                                             |
| Enums                          | `PascalCase`                                     | `Role`, `EntityType`                                            |
| Constants                      | `UPPER_SNAKE_CASE` (usually in the owning class) | `USER_CACHE` (in `CacheConfig`)                                 |
| Test classes                   | `*Test` (unit), `*IT` (integration/E2E)         | `CreatePlaylistServiceTest`, `PlaylistFlowIT`                   |

Name for **what it is or does**, not the implementation: `SongStoragePort`, not `S3StoragePortV2` (storage is swappable, the port is the contract). Use-case names speak **business language** (`CreatePlaylist`, `ToggleLike`), not HTTP verbs or paths.

---

## 2. Package / folder structure (layered Clean Architecture)

```
com.spotpobre.backend/
├── domain/                     # Entities, value objects & outbound port interfaces
│   └── <feature>/              # album, artist, like, playlist, song, user
│       ├── model/              # entities + value objects — no framework imports
│       └── port/               # outbound ports (*Repository, *StoragePort)
├── application/                # Use-case orchestration (depends on domain ports only)
│   └── <feature>/
│       ├── port/in/            # *UseCase interfaces consumed by the web layer
│       └── service/            # *Service implementations of those use cases
└── infrastructure/             # Adapters — Spring Web, DynamoDB, S3, JWT, config
    ├── config/                 # beans, security, cache, AWS & properties configuration
    ├── persistence/            # DynamoDB adapters, repositories, *Document entities, mappers
    ├── security/               # JWT filter, UserDetailsService, SecurityConfig
    ├── storage/                # S3 adapter + CDN adapter (SongStoragePort)
    └── web/                    # controllers, dto/{request,response}, mappers, exception
```

**Framework boundary (enforce with the grep in `AGENTS.md` rule 1):**

| Layer            | Framework / external imports                                                                                          |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `domain/`        | Pure Java — no Lombok, no annotation processors, no Spring, no AWS SDK, no MapStruct, no springdoc, no `org.springframework.web`. Entities, value objects, factories, getters, `equals`/`hashCode`/`toString` and small fluent builders are written by hand. Pagination uses pure domain types `PageRequest`/`PageResult` (`domain/common/pagination`). |
| `application/`   | **Minimal**: Lombok + Spring annotations (`@Component`, `@RequiredArgsConstructor`, `@Transactional`) and the Java stdlib. No `infrastructure.*`, no AWS SDK, no MapStruct/springdoc, no `org.springframework.web`. Password hashing goes through the domain `PasswordHasher` port; authentication goes through the domain `AuthenticationPort` — the Spring Security `AuthenticationManager` is owned by `SpringSecurityAuthenticationAdapter`. |
| `infrastructure/`| Full stack allowed: Spring Web, Spring Security, AWS SDK (DynamoDB Enhanced, S3), MapStruct, springdoc, jjwt, Redis.     |

Web controllers are **thin**: they bind `port/in` use cases and DTOs only. No business rules, no repository calls from controllers.

---

## 3. Clean Architecture & SOLID

- **Dependency rule (DIP).** Inject **ports, never concretions**. Services depend on `domain`
  outbound ports and `application/<feature>/port/in` interfaces; `infrastructure` adapters depend
  on those ports. A service never imports a concrete adapter or a framework bean.
- **Interface segregation (ISP).** Prefer narrow interfaces over wide ones. When a use case needs
  only a subset of a repository's surface, split the port into `*Reader` / `*Writer` pairs (or
  narrow `*Reader`/`*Writer` interfaces) — the adapter satisfies the union. Do not force a use
  case to depend on methods it never calls. The wide `*Repository` ports are known debt; keep the
  pattern in mind for new ports.
- **Open/closed (OCP).** Prefer strategy / interface dispatch for behaviour that varies by type.
  The `LikeStrategy` family (`SongLikeStrategy`, `ArtistLikeStrategy`, `PlaylistLikeStrategy`)
  behind `LikeStrategyFactory` is the model. Do **not** add `if/else` chains on `EntityType` or
  enum values to introduce new variants.
- **Single responsibility (SRP) & clean code.** One responsibility per service; small methods and
  classes; extract private helpers. Methods named as **verbs** (`createPlaylist`, `isOwner`),
  booleans as predicates. No abbreviations beyond established conventions.
- **Rich domain model.** Business invariants live in the **aggregate root** (`Album` aggregate owns
  its consistency), not in services and never in `*Document` entities. Avoid anemic models
  (getters/setters only). Domain entities expose behaviour, not just data.
- **Tell, don't ask.** Avoid chains of getters in services; move the decision into the domain
  model (`playlist.isOwnedBy(ownerId)`, `song.canBeAddedTo(album)`).
- **No magic numbers / strings.** Use named constants or enums; never scatter literals.
- **Exceptions over error codes.** Business-rule failures throw; fail fast with a clear message.
  No silent error flags or `null`-as-failure.

---

## 4. Design patterns (house style)

House patterns are the ones already established in the codebase — **reuse them instead of
inventing variants**. Prefer the pattern that matches existing code; if none fits, ask the human.

| Pattern                          | Where it lives                                  | Use it when                                                                          | Avoid / instead                                                            |
| -------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| **Strategy**                     | `LikeStrategy` family                           | Behaviour varies by type and new variants will be added                              | `if/else` or `switch` on `EntityType` / enums                               |
| **Factory**                      | `LikeStrategyFactory`                           | Resolving a strategy/implementation from a type or condition; centralize the mapping | Stringly-typed dispatch, lookups scattered across services                  |
| **Adapter (Ports & Adapters)**   | `DynamoDb*RepositoryAdapter`, `S3SongStorageAdapter`, `CdnSongStorageAdapter` | Any technical boundary (DynamoDB, S3, JWT, CDN) — implement the domain port | AWS SDK / framework calls outside `infrastructure/`                         |
| **Facade (use-case service)**    | `*Service` in `application/<feature>/service`   | Orchestrating a use case across domain ports; keep services thin                     | Putting business rules in the service                                        |
| **Repository**                   | `*Repository` domain ports                      | Data-access abstraction; lookups driven by GSIs                                      | Raw DynamoDB/S3 calls in services or controllers                             |
| **Builder**                      | Hand-written static `X.builder()` returning a private nested `Builder` (the domain layer is Lombok-free) | Entities with many/optional fields; test fixtures                                    | Telescoping constructors                                                      |
| **Translation (anti-corruption)**| `*PersistenceMapper`, `*ApiMapper`              | Translating between domain ↔ persistence ↔ web models at the boundaries             | Silently sharing one model across layers                                      |

**Cross-cutting (declarative).** Prefer framework annotations over hand-rolled wrappers:

- `@Cacheable` (TTL centralized in `CacheConfig`) instead of manual cache code in services/adapters.
- `@Transactional` on application services for multi-write use cases — never on adapters.
- JWT auth as a single filter + explicit `SecurityConfig` rules — never per-controller auth code.

**Not now, but planned (do not build yet):**

- **Domain events** for cross-aggregate consistency / side effects (audit, notifications) once the
  workflows grow.

**Anti-patterns to avoid:**

- **Service Locator** — no `ApplicationContext.getBean()` in services/controllers; inject at the
  composition root (`ApplicationBeanConfig`).
- **God classes / anemic domain models.**
- **Stringly-typed dispatch** (`switch` on strings/ids) where a Strategy/Factory fits.
- **Direct instantiation of concrete adapters in services.**

---

## 5. Java 21 language features

- **Records** for immutable value objects and DTOs (`*Request` / `*Response`, value objects).
  Prefer records when the shape is fixed; use explicit classes only where behaviour matters.
- **Sealed interfaces** for closed hierarchies (`EntityType`, like strategies) — prevents
  accidental extension of a deliberately closed set.
- **Pattern matching for `switch`** on sealed/enum types instead of `instanceof` chains.
- **Text blocks** for multi-line literals (SQL, JSON samples, large strings).
- **Immutability.** `final` fields; no public setters on value objects; defensive copies where a
  mutable object crosses a boundary.
- **Null discipline.** Never return `null` from `domain`/`application`. Use `Optional` for
  possibly-absent results, `Objects.requireNonNull` for preconditions, and validate at the input
  boundary. **Never** use `Optional` as a method parameter or field.
- **Streams.** Prefer `Stream`/`collect` over manual loops where clearer. No stateful lambdas, no
  side effects inside stream pipelines.

---

## 6. Spring Boot conventions

- **Java 21 / Spring Boot 3.5.** Annotate services with `@Component` or register them via `@Bean`
  in `infrastructure/config/ApplicationBeanConfig` (explicit wiring is preferred for use cases).
- **Constructor injection only** (`@RequiredArgsConstructor` for Lombok-managed fields, or an
  explicit constructor). Never field injection.
- **Typed configuration.** Use `@ConfigurationProperties` (`AwsProperties`, `JwtProperties` are the
  pattern). Do **not** scatter new `@Value` fields for config.
- **Profiles.** Environment-specific values live in `application-{profile}.yaml` overridden by env
  vars (12-factor). `application-dev.yaml` / `application-prod.yaml` are currently empty — keep
  them that way until there is a real reason, and keep prod values as env vars, never committed.
- **`@Transactional`** only in `application/` services when a use case spans multiple writes. Keep
  the transaction short; never put it on adapters.
- **Caching.** Prefer `@Cacheable` on application services that call ports; TTLs are centralized
  in `CacheConfig` (e.g. `userCache` 5 min). Do not place cache logic inside adapters. Exception:
  the Spring Security adapter `UserDetailsServiceImpl` may cache its lookup — the application layer
  must not import the infrastructure `CacheConfig` constant (boundary, rule 1), so the annotation
  stays on the adapter and the cache name constant remains in `CacheConfig`.
- **Bean scoping.** Default singleton; services are stateless — no per-request mutable fields.
- **DTOs for every external input/output.** Never expose domain entities through the API. Keep
  `web` mappers thin (MapStruct, `componentModel = spring`).
- **Validation**: `spring-boot-starter-validation` + `jakarta.validation.constraints.*` on request
  DTOs, `@Valid` in controllers. Centralized errors (see § 8).
- **Pagination**: pure domain types `PageRequest`/`PageResult` in `domain/common/pagination` used
  by ports, use cases and services. Adapters translate to the storage-native mechanism; the web
  layer adapts results back to the REST shape (Spring `Page` for search, `PageResponse` + cursor
  for playlists). Controllers may use Spring Data `Pageable`/`Page` at the boundary.

---

## 7. Formatting & tooling

- 4-space indent, no tabs.
- Follow the layout of the layer you are editing; Maven/Spring Boot convention (files compiled by
  `./mvnw`). No formatter config is committed — keep style consistent manually.
- Imports: keep them clean and ordered (IDE auto-organize); **no wildcard imports** in new code
  (existing wildcard imports in `ApplicationBeanConfig` are legacy).
- Run `./mvnw test` (fast loop) before commit; run `./mvnw clean package` after significant
  changes.

---

## 8. Errors & logging

- **Centralized error handling.** `GlobalExceptionHandler` (`infrastructure/web/exception`) maps
  exceptions to `ErrorResponse` / `ValidationErrorResponse`:
  - `MethodArgumentNotValidException` / `ConstraintViolationException` → `400` Validation Error
  - `IllegalStateException` → `400` Business Rule Error (this is the current pattern for
    business-rule violations from services)
  - `AccessDeniedException` → `403` Forbidden
  - anything else → `500` Internal Server Error (logged as `error` with stack trace)
- Never empty `catch`. Log with context (ids, operation, resource), not only `"error occurred"`.
- Never log passwords, JWT secrets or full token payloads.
- Logging: SLF4J (`LoggerFactory.getLogger(...)`) + `logstash-logback-encoder` for structured JSON.
  Levels: `error` — needs attention; `warn` — handled anomaly; `info` — significant lifecycle;
  `debug` — diagnostic detail.
- English only in log messages.

---

## 9. Persistence (DynamoDB) & cache (Redis)

- **All DynamoDB access lives in `infrastructure/persistence`.** Adapters implement the domain
  port; repositories are thin DynamoDB Enhanced Client queries; entities (`*Document`) are
  persistence records; `*PersistenceMapper` translates explicitly between domain and document —
  no silent casts.
- Business invariants stay in the `domain` model, never in `*Document` entities.
- **GSIs drive lookups**: `email-index` (Users), `ownerId-index` (Playlists),
  `albumId-index` (Songs), `title-search-index` (Songs), `name-search-index` (Artists),
  `artistId-index` (Albums), `entityId-index` (Likes reverse). The `UserEmails` table is a
  uniqueness sentinel used by registration (`TransactWriteItems`).
  When adding a new query shape, add the GSI in `docker-compose`/LocalStack setup and in the
  README setup block, not by scanning.
- **Concurrency-safe writes**: registration uses `attribute_not_exists` inside a transaction;
  playlist mutations carry a `version` attribute and are persisted with a conditional write
  (`version = :expected`) that fails with `PlaylistConcurrentModificationException` on a stale
  snapshot. See `docs/data-model-decisions.md`.
- Pagination on DynamoDB uses `DynamoDbCursorHelper` for the playlist cursor; results are exposed
  to the core as `PageResult` (domain type). Search index queries use `PageResult` too — never
  import Spring Data types into `domain/`/`application/`.
- **Redis** (reactive starter): cache only. TTLs are set per cache (`CacheConfig`, e.g. `userCache`
  5 min). Never store secrets or large blobs. `disableCachingNullValues()`.
- Schema/tables: created via `awslocal dynamodb create-table ...` in the README setup block
  (dev). No migration tooling yet — keep the README block as the source of truth.

---

## 10. Testing

| Kind                | Tooling                       | Notes                                                        |
| ------------------- | ----------------------------- | ------------------------------------------------------------ |
| Domain unit         | JUnit 5                       | Pure entities/value objects, **no mocks**                    |
| Application unit    | JUnit 5 + Mockito             | Mock the **domain ports only**; happy path + rejection       |
| Slice integration   | Testcontainers + LocalStack   | Real DynamoDB/S3 adapters (`DynamoDbPlaylistRepositoryAdapterIT`, `S3SongStorageAdapterIT`) |
| End-to-end          | RestAssured + Testcontainers  | `*IT` on `RANDOM_PORT`; run explicitly `-Dtest='*IT'`        |

- Method/test names: `method_condition_expectedResult` or descriptive `should ...`.
- Fast loop: `./mvnw test` (only the adapter test needs Docker).
- After significant changes: `./mvnw clean package` + smoke against `docker-compose up -d`.
- Full guidance: `docs/testing-playbook.md`.

---

## 11. Documentation

- Javadoc where the purpose is not obvious from the name; skip trivial getters.
- Comment **why**, not what.
- English only for code, comments, commits, and docs.

### Doc sync

After milestone-sized work (new feature area, public behaviour change, debt resolution), the same
change set — or an immediate follow-up commit — MUST update:

- `README.md` → "Current State" / "Roadmap"
- `CHANGELOG.md` (entry under the next version or `Unreleased`)
- `AGENTS.md` → "Known technical debt" (add or clear)

Do **not** claim work DONE while `README.md` or `CHANGELOG.md` still describes a previous
milestone as current. The hard rule lives in `AGENTS.md` § *Critical rules* (rule 8).

---

## 12. Version control

- Imperative commit subject: `feat(playlist): add add-song-to-playlist flow`.
- Existing style uses conventional prefixes (`feat`, `fix`, `refactor`, `docs`, `test`).
- Small, focused commits.
- Do **not** push unless the human asks.
- CHANGELOG: every milestone-sized change set adds an entry under the next version or
  `Unreleased` (Keep a Changelog format; sections `Added` / `Changed` / `Fixed` / `Deferred` /
  `Documentation`). Promote the `Unreleased` block when tagging.
- Annotated tags only at milestones with DoD met (`v0.X.0` — see `AGENTS.md`; none yet).

---

## 13. Security

- **Authorization is centralized in `SecurityConfig`** (`infrastructure/security/config`). Every
  new endpoint gets an explicit rule: admin routes `hasRole(Role.ADMIN.name())`, artist-owned
  routes `hasRole(Role.ARTIST.name())`, authenticated routes `authenticated()`. Never default a
  mutating route to permit-all. Authorities always carry the `ROLE_` prefix (see
  `GetUserDetailsService`).
- Passwords: **Argon2id** via the domain `PasswordHasher` port (adapter
  `SpringSecurityPasswordHasher`). The concrete algorithm is configured by one `PasswordEncoder`
  bean in `SecurityConfig` — swap hashing libraries by changing that bean and the adapter, never
  the application layer. Never store plaintext.
- JWT: `jjwt`; `jwt.secret` in `application.yaml` is a **dev-only example** — production must
  override it via environment variables. Never commit real secrets.
- Server-side validation always (`@Valid`); never trust the client alone.
- Extend the existing auth model — do not invent a second one.
- Rate limiting / lockout on sensitive endpoints is a roadmap item (not yet implemented).

---

## Quick pre-commit checklist

- [ ] No wildcard imports added; 4-space indent; layout follows the layer
- [ ] `domain/` free of `infrastructure.*`, AWS SDK, MapStruct, springdoc, `springframework.web`
- [ ] `application/` free of `infrastructure.*`; pagination uses `PageRequest`/`PageResult` only
- [ ] Injects ports only (DIP); no concrete adapter imported by a service
- [ ] No `if/else` on `EntityType`; strategy used when behaviour varies by type
- [ ] Used a house pattern (Strategy/Factory/Adapter/Facade/Repository) instead of raw SDK calls or stringly-typed dispatch
- [ ] No Service Locator (`ApplicationContext.getBean()`); wiring stays in the composition root
- [ ] Invariant placed in the aggregate/domain, not in the service or `*Document`
- [ ] Records/sealed/immutable where it fits; no `null` returned from the core; no `Optional` params
- [ ] No magic numbers/strings; named constants or enums
- [ ] DTOs for external I/O; controller is thin; MapStruct mapper used
- [ ] New endpoint has an explicit `SecurityConfig` rule
- [ ] Unit test for new domain/application behaviour; existing suite not weakened
- [ ] No secrets in the diff; log messages in English with context
- [ ] `./mvnw test` green
- [ ] Commit message says what and why