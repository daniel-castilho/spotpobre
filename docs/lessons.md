# Lessons learned

Practical findings from running and hardening the project's test/infra setup. Add to this file
whenever a non-obvious failure or design decision cost real debugging time.

## Testcontainers / Docker

- **docker-java in Testcontainers 1.19.x cannot talk to Docker 29+** (daemon API 1.53, MinAPIVersion
  1.44). LocalStack containers fail to start with HTTP 400 / empty container info. Fix: upgrade
  `testcontainers.version` to >= 1.21.x (we use 1.21.4). A version bump is not a new Maven
  coordinate, so it does not need AGENTS.md rule-5 approval.
- **Debug Testcontainers startup** by pointing logback at a temp file with DEBUG for
  `org.testcontainers` and `com.github.dockerjava`:
  `./mvnw test -Dlogback.configurationFile=/tmp/logback-testcontainers.xml -Dtest='<MyIT>'`.
- **LocalStack ships empty.** No tables, no bucket. ITs that assume a schema will 500 with
  `ResourceNotFoundException`. `AbstractIntegrationTest` provisions the bucket and all tables (with
  GSIs) in a static `@BeforeAll`, mirroring the README setup block.

## Spring context caching vs. Testcontainers lifecycle

- A **static `@Container` field in a shared base class** is stopped by Testcontainers after the
  first test class that uses it, while Spring's `@SpringBootTest` context cache keeps the
  application pointed at that container's (now dead) port → `Connection refused` (SDK Attempt
  Count: N) on every subsequent *IT class.
  Symptom: the *first* IT passes, all *following* ones 500 on the first request.
  Fix: start the container manually (no `@Container` annotation) as a `static final` field so it
  lives for the whole JVM and the cached context always targets a live endpoint.

## DynamoDB Enhanced Client

- **Empty query results crash on `.get(0)` / `iterator().next()`.** `index.query(...)` returns a
  lazy `Iterable<Page<T>>`; a fresh table yields zero pages. `page.items().get(0)` throws
  `IndexOutOfBoundsException` and `iterator().next()` throws `NoSuchElementException`. Prefer
  `...stream().flatMap(page -> page.items().stream()).findFirst()` and
  `...stream().findFirst().map(Page::count).orElse(0)`. `Page.count()` returns `Integer`, not `long`.
- **README schema drift bit us twice**: the Users GSI partition key is the nested `profile.email`
  (not `email`), and the Artists/Songs search indexes are `searchPartition` (HASH) + `name`/`title`
  (RANGE), with the search partition being a constant. The `title-search-index` was missing from the
  `DynamoDbConfig` schema entirely (querying it would 500). Keep the `awslocal` block in README in
  sync with `DynamoDbConfig` — the IT provisioning is the enforcement.

## Password hashing (BCrypt → Argon2id)

- **`Argon2PasswordEncoder` needs BouncyCastle on the classpath**; it is NOT bundled with
  spring-security-crypto. Without it you get `NoClassDefFoundError:
  org/bouncycastle/crypto/params/Argon2Parameters$Builder` at hash time (not at startup). Added
  `org.bouncycastle:bcprov-jdk18on` (approved, rule 5).
- **Swappable hashing design**: domain `PasswordHasher` port + `SpringSecurityPasswordHasher`
  adapter. The application layer depends only on the port; the algorithm lives in one
  `PasswordEncoder` bean. Swapping libraries = changing one bean + the adapter, nothing else.

## Security / authorization

- **Authority prefix mismatch breaks `hasAuthority` silently** (403, no logs). `GetUserDetailsService`
  maps roles to `ROLE_<NAME>`, so `SecurityConfig` must use `hasRole(...)` (or `ROLE_`-prefixed
  authorities). Using bare `Role.ADMIN.name()` never matches. The JWT filter re-loads
  `UserDetails` (authorities from DB) and ignores the token claims, so keep claims consistent with
  the `ROLE_` convention too.

## E2E test authoring

- **Registering the same email in two ITs** in one suite → second register returns 400
  ("already exists"). Use distinct emails per test method.
- **There is no role-granting endpoint.** The register endpoint only creates default `USER`
  accounts. For role-based flows (admin/artist), seed `UserDocument`s directly in DynamoDB with a
  BCrypt/Argon2id-hashed password (`PasswordHasher`), then authenticate to obtain the token.
- **Test against the real route.** `POST /api/v1/songs` does not exist; song upload is a two-step
  flow at `POST /api/v1/albums/{albumId}/songs` (presigned URL) then
  `POST /api/v1/albums/{albumId}/songs/{songId}/confirm`. Songs belong to albums, not artists.
  An IT asserting a route/contract that doesn't exist hides missing/renamed endpoints.

## Operations

- **The 500 log line now carries method + URI + exception class**
  (`Unexpected error on POST /api/v1/auth/register: SdkClientException - ...`), which made every
  infra failure above a 30-second diagnosis instead of a dump-scrape. When touching
  `GlobalExceptionHandler`, keep the request context in the error log.