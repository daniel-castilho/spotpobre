# P0 Baseline Inventory & Pre-Audit (Phase A, Step 0)

**Baseline:** `04f42f4` · **Date:** 2026-08-23 · **Executor:** per `p0-action-plan.md`
**Verification evidence:** `./mvnw test` → 353 tests, 0 failures/errors. Representative ITs
(`AuthenticationFlowIT`, `PlaylistFlowIT`, `ArtistSongFlowIT`) → 6 tests green on Docker 29.2.1.

## 1. §3 claims — final verdicts

| # | Claim | Verdict | Evidence |
| :--- | :--- | :--- | :--- |
| 1 | No `SongUploads` model/table | CONFIRMED | no `SongUpload` in `domain/song/model/`; seed script creates 11 tables, none named `SongUploads` |
| 2 | In-memory fixed-window limiter | CONFIRMED | `infrastructure/security/ratelimit/FixedWindowRateLimiter.java` (sole limiter class) |
| 3 | XFF trusted without CIDR policy | CONFIRMED | `RateLimitFilter` takes first `X-Forwarded-For` value when header present, else `getRemoteAddr()`; no CIDR logic anywhere |
| 4 | Limiter covers only register/auth | CONFIRMED | `RateLimitProperties` defaults: paths = `/api/v1/auth/register`, `/api/v1/auth/authenticate`; single window 20/1m; key = `ip|method|path` |
| 5 | Rate-limit headers inconsistent | CONFIRMED | only idempotency 409 emits `Retry-After` (`GlobalExceptionHandler`); zero `RateLimit-*` headers in codebase |
| 6 | Prod lacks management port config | CONFIRMED | `application-prod.yaml` has neither `management:` nor `springdoc:` keys |
| 7 | Swagger not disabled in prod | CONFIRMED | same file; SecurityConfig permits `/swagger-ui/**`, `/v3/api-docs/**` unconditionally |
| 8 | Prod compose lacks SES | CONFIRMED | `deploy/docker-compose.bluegreen.yml` → `SERVICES: dynamodb,s3` |
| 9 | Prod e-mail contract incomplete | CONFIRMED | follows from #8; `deploy/.env.example` has 9 keys, no SES identity/sender set documented for the prod stack |
| 10 | `EmailProperties` FQN leak | CONFIRMED | `RegisterUserIdempotentService:56`, `RequestEmailVerificationResendService:37` |
| 11 | Controller → `JwtService` direct | CONFIRMED | `AuthenticationController` imports + injects infrastructure `JwtService` |
| 12 | `completeClaim` boolean discarded | CONFIRMED | all six creation services call and ignore the result |
| 13 | Password reset lacks eviction/burn/revocation | CONFIRMED | `ResetPasswordService` burns only the redeemed token; no cache eviction, no sibling-token burn, no JWT invalidation |
| 14 | Account-token + user writes not atomic | CONFIRMED | separate repository calls in the same service method (no transactional store) |
| 15 | PII / raw identifiers in logs | CONFIRMED (sites enumerated) | full e-mail logged at `RequestEmailVerificationResendService:68`, `RequestPasswordRecoveryService:54` (delivery failure), `SesEmailSenderAdapter:85` (recipient); raw cache keys at `CacheOutageTolerantErrorHandler:31,37`; storage-key values in upload-initiate warn logs |
| 16 | P0 docs stale vs locked decisions | CONFIRMED | spec §11.2 said management port default `8081` — now annotated with locked deviation to **9090 internal** |

## 2. Route × security inventory (SecurityConfig, ordered as declared)

| Matcher | Rule |
| :--- | :--- |
| POST `/api/v1/auth/email/verification/resend` | authenticated |
| POST `/api/v1/auth/email/verification/confirm` | permitAll |
| `/api/v1/auth/**` | permitAll (covers recover/reset/register/authenticate/logout) |
| swagger-ui/**, v3/api-docs/** | permitAll (no prod gating — S21) |
| actuator health/liveness/readiness (+ `/actuator/health`) | permitAll (public business port — S21) |
| `/actuator/**` | authenticated |
| POST `/api/v1/artists` | ROLE_ADMIN |
| `/api/v1/artists/*/accounts/**` | ROLE_ADMIN |
| POST `/api/v1/albums/*/songs`, `/songs/*/confirm` | ROLE_ARTIST |
| POST `/api/v1/albums` | ARTIST or ADMIN |
| `/api/v1/users/me` (GET/PATCH), likes PUT/DELETE, playlists CRUD, `/me/playlists`, GET artists/albums/songs | authenticated |
| anyRequest | authenticated |

Notes: lines 49–50 (recover/reset) are dead matchers behind the `/auth/**` wildcard — harmless,
cleanup candidate during Phase G. No public GET routes exist.

## 3. Data plane inventory (seed-localstack.sh — authoritative provisioning)

11 tables: `Users` (GSI email-index), `UserEmails`, `Playlists` (GSI ownerId-index), `Songs`
(GSIs title-search-index [HASH+RANGE], albumId-index), `Artists` (GSI name-search-index),
`ArtistAccounts`, `Albums` (GSI artistId-index), `Likes` (GSI entityId composite HASH+RANGE),
`IdempotencyRecords` (TTL), `AccountTokens` (TTL). Missing for P0: `SongUploads`
(+ `state-expiry-index`). Seed uses skip-if-exists guards (`describe-table`) → shallow validation
(S24 must assert KeySchema/GSI/TTL instead).

## 4. Upload flow trace (current, pre-S13–S16)

`InitiateSongUploadService`: presigned PUT generated against final storage key *before* metadata is
durable (warn log confirms "Metadata save failed after generating presigned … nothing to abort");
song row visibility semantics unguarded (S15 requirement). Confirmation path trusts client-supplied
key/upload identifiers; no staging prefix, no checksum verification, no lease between initiate and
confirm, no cleanup sweeper. All to be replaced by Phases D–E.

## 5. Production manifests state

- `deploy/docker-compose.bluegreen.yml`: blue/green app fleets, NGINX LB on 8080, LocalStack
  `SERVICES=dynamodb,s3`, Redis present. Host port publishing of app/internal services to be
  reduced to 8080-only (Phase G).
- `deploy/.env.example`: 9 keys; needs SES sender/identity, `RATE_LIMIT_KEY_SECRET`,
  management-port contract additions (Phases F–G).
- `ProdConfigValidator` exists with tests; extensions planned for prod-contract completeness.
- `scripts/seed-localstack.sh` + `deploy/README.md` + `docs/release-runbook.md` are the operator
  entry points referenced by the durability dossier.

## 6. Dependency gate (rule 5)

Present in graph, no POM edits needed: `spring-boot-starter-data-redis-reactive` (pom.xml:93,
brings Lettuce + `StringRedisTemplate` support), Testcontainers BOM 2.0.5 (core `GenericContainer`),
AWS SDK v2 (checksum + multipart/copy APIs). **No new Maven coordinates anticipated for the epic.**

## 7. Durability decision

Dossier recorded in `docs/data-model-decisions.md` → "LocalStack production durability".
Provisional execution under Option A (Community file-Pod snapshots); human pick A/B/C required
before any production-durability claim.
