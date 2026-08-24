# API Design Excellence P0 — Technical Specification
## Safety, Idempotency & Abuse Protection

**Status:** Execution started 2026-08-23 from baseline `04f42f4` — live status is tracked in
`p0-action-plan.md` and the as-built report; story checkboxes in the backlog are updated per phase.
**Priority:** P0  
**Companions:** `api-design-excellence-p0-backlog.md` · `api-design-excellence-p0-implementation-sequence.md`

---

## 1. Purpose

Make the current Spotpobre `/api/v1` API safe under duplicate delivery, concurrent requests, process crashes, partial S3/DynamoDB failure and common public-endpoint abuse.

This specification intentionally solves a small number of high-risk behaviours deeply. It does not attempt a complete API redesign.

---

## 2. Scope

### In scope

- replace like toggle with explicit idempotent state operations;
- make playlist membership naturally idempotent;
- require durable `Idempotency-Key` handling for selected POST commands;
- recover idempotent operations after process crashes;
- implement a persistent, replay-safe song-upload lifecycle;
- verify upload integrity and promote staging objects to immutable playable keys;
- model and enforce User ↔ Artist management;
- implement atomic distributed Redis rate limiting;
- map protocol errors to exact 4xx statuses/headers;
- add explicit input bounds;
- lock down Swagger and Actuator in production;
- add low-cardinality metrics, safe audit logs and complete retry/concurrency tests.

### Out of scope

- broad route renaming (`/sessions`, collection search, all `/users/me/...` routes);
- API v2 and deprecation lifecycle;
- ETag / If-Match;
- full RFC 9457 error-envelope migration;
- HTTP response caching;
- advanced filtering/sorting;
- refresh-token/session product design;
- deployment manifests/network policies;
- arbitrary authorization administration UI;
- a complete OpenAPI-first program.

Changed P0 routes and headers still require minimal accurate OpenAPI documentation.

---

## 3. Architectural constraints

### 3.1 Dependency direction

- Domain models and ports contain no Spring, servlet, Jackson, Redis or AWS SDK types.
- Application services contain no servlet, Spring Web, Redis or AWS SDK types.
- Web adapters resolve headers, `Principal`, trusted client address and HTTP response headers.
- DynamoDB/S3/Redis implementation details stay under `infrastructure/`.
- Authorization and idempotency decisions are application behaviours expressed through pure ports/models.

### 3.2 Suggested package shape

```text
com.spotpobre.backend/
├── domain/
│   ├── artist/model/ArtistAccount.java
│   ├── artist/port/ArtistAccountRepository.java
│   ├── idempotency/model/...
│   ├── idempotency/port/IdempotencyRepository.java
│   ├── ratelimit/model/...
│   ├── ratelimit/port/RateLimitPort.java
│   └── song/model/SongUpload.java
├── application/
│   ├── artist/service/ArtistAccessPolicy.java
│   ├── idempotency/service/IdempotencyCoordinator.java
│   ├── ratelimit/port/in/CheckRateLimitUseCase.java
│   └── song/service/...
└── infrastructure/
    ├── idempotency/dynamodb/...
    ├── persistence/kv/... ArtistAccount/SongUpload ...
    ├── ratelimit/redis/...
    ├── web/filter/RequestSizeLimitFilter.java
    └── web/... controllers/error mapping/client address ...
```

Names may follow existing repository conventions, but layer boundaries and behaviours are mandatory.

### 3.3 Cross-system consistency

`@Transactional` does not make S3 and DynamoDB atomic. Cross-system flows must use:

- persistent state;
- conditional transitions;
- stable resource IDs;
- leases for recoverable work;
- compensating cleanup;
- reconciliation/lifecycle defence.

---

## 4. Exact HTTP contract changes

### 4.1 Likes

Remove:

```http
POST /api/v1/likes/toggle
```

Add:

```http
PUT /api/v1/users/me/likes/{entityType}/{entityId}
Authorization: Bearer <token>
```

```http
DELETE /api/v1/users/me/likes/{entityType}/{entityId}
Authorization: Bearer <token>
```

Contract:

- `entityType`: lowercase `song`, `artist` or `playlist`;
- `entityId`: UUID;
- success: 204 with no body for both methods;
- repeated PUT leaves one like and preserves the original `likedAt`;
- repeated DELETE leaves no like and returns 204;
- missing target entity: 404;
- unauthenticated: 401;
- no `newLikeCount` in mutation responses because the current GSI count is eventually consistent and not transactionally coupled to the mutation;
- concurrent opposite operations have last-successful-storage-operation semantics; clients must not infer ordering between simultaneous PUT and DELETE.

Repository operations become state-setting operations such as `createIfAbsent` and `deleteIfPresent`, backed by the existing deterministic `(userId, entityType#entityId)` key.

### 4.2 Playlist song membership

Replace add route method:

```http
PUT /api/v1/playlists/{playlistId}/songs/{songId}
```

Keep delete path, with revised response:

```http
DELETE /api/v1/playlists/{playlistId}/songs/{songId}
```

Contract:

- PUT success: 200 with `PlaylistResponse`;
- same-song repeated PUT: 200, one membership, no version increment when already present;
- concurrent same-song PUT: if one write loses optimistic locking, reload; if desired membership exists, return 200 rather than exposing a conflict;
- concurrent different-song writes retain current optimistic conflict semantics; caller may retry;
- DELETE success: 204 whether membership was present or absent;
- absent membership DELETE performs no persistence update/version increment;
- non-owner: 403;
- missing playlist/song on PUT: 404;
- playlist maximum remains 100 unique songs.

Domain API must expose intent explicitly, for example:

```java
boolean ensureSongPresent(Song song);
boolean ensureSongAbsent(SongId songId);
boolean containsSong(SongId songId);
```

The song collection must never contain duplicate `SongId` values.

### 4.3 Idempotency-protected POST routes

`Idempotency-Key` is required on:

```text
POST /api/v1/auth/register
POST /api/v1/artists
POST /api/v1/albums
POST /api/v1/playlists
POST /api/v1/albums/{albumId}/songs
```

Header rules:

- length: 16–128 ASCII characters;
- allowed: `[A-Za-z0-9._:-]`;
- recommended client format: UUID or ULID;
- missing/blank/invalid/oversized: 400;
- raw key is never logged, used as a metric tag or persisted; persist only a SHA-256 scope digest;
- responses include `Idempotency-Replayed: false` for the first completed execution and `true` for replay;
- active operation returns 409 and `Retry-After` derived from its lease, capped to a small positive integer;
- same key with another canonical request returns 409;
- completed resource creation replay returns the original stable resource outcome/status where safe.

Special response rules:

- registration resolves the same user but generates a fresh JWT; no JWT is persisted in idempotency storage;
- upload initiation resolves the same `SongUpload` and regenerates valid URLs; presigned URLs are never persisted as replay bodies.

---

## 5. Idempotency design

### 5.1 Durable store

Create DynamoDB table `IdempotencyRecords`:

```text
PK: scopeKey (String, SHA-256 hex/base64url digest)
TTL attribute: expiresAtEpochSeconds
```

Record fields:

```text
scopeKey
operationName
routeTemplate
actorScopeHash
requestHash
hashVersion
state                 IN_PROGRESS | COMPLETED | FAILED_FINAL
resourceType
resourceId
leaseTokenHash
leaseUntil
resultSnapshot        optional safe JSON/string; no JWT/signed URL/secret
responseStatus        optional
responseContentType   optional allowlisted value
location              optional relative URI
failureStatus         optional deterministic 4xx
failureType           optional safe canonical error title
failureMessage        optional safe message
createdAt
updatedAt
completedAt
expiresAtEpochSeconds
```

TTL:

- creation and registration records: 24 hours;
- upload initiation records: 24 hours;
- application checks logical expiry; it never waits for eventual DynamoDB TTL deletion;
- expired records may be replaced conditionally by a new claim.

Update README/local setup and `AbstractIntegrationTest` to create the table and enable/document TTL. Production schema changes must be versioned as an admin/deployment step.

### 5.2 Scope

Authenticated scope input:

```text
apiVersion | immutableUserId | HTTP method | canonical route template | path identity | Idempotency-Key
```

Anonymous registration scope input:

```text
apiVersion | anonymous-registration | HTTP method | canonical route template | Idempotency-Key
```

Hash this scope before persistence. Do not include e-mail or client IP in the persisted scope.

`path identity` includes resource IDs that materially change the operation, such as `albumId`, in canonical UUID form. Query parameters participate only when the operation contract declares them relevant.

Authorization is re-evaluated before returning an authenticated replay. A revoked actor must not gain access merely because an idempotency record exists.

### 5.3 Canonical request hash

Hash a versioned canonical representation of:

- validated and normalized command fields;
- canonical path parameters;
- relevant content type;
- owner/target IDs that affect the result.

Requirements:

- SHA-256;
- canonical field order;
- UTF-8;
- normalized e-mail/case/whitespace according to domain policy;
- hash version stored in the record;
- no framework type in the domain/application model;
- validation/input-size limits happen before canonicalization;
- the raw body and raw hash inputs are not persisted/logged.

### 5.4 Claim and lease protocol

Default lease:

- normal creations: 30 seconds;
- upload initialization/confirmation: 120 seconds.

Protocol:

1. validate header, request and authorization;
2. apply rate-limit policy;
3. compute scope and request hash;
4. conditional create `IN_PROGRESS` with a stable preassigned resource ID and lease token;
5. if record exists:
   - hash differs → 409 key reused;
   - `COMPLETED` → replay operation-specific result;
   - `FAILED_FINAL` → replay deterministic failure;
   - active `IN_PROGRESS` → 409 + `Retry-After`;
   - expired lease → conditional lease takeover preserving resource ID;
6. execute/recover operation using the record’s stable resource ID;
7. conditionally mark `COMPLETED` only with the current lease token;
8. never overwrite a completed record.

Persist a one-way hash of the lease token if it is stored. Do not expose lease tokens to clients.

### 5.5 Crash recovery

A generic response cache is prohibited as the only safety mechanism.

For user/artist/album/playlist creation:

- the claim allocates and persists the resource UUID before the business write;
- creation factories/services accept the reserved ID;
- repositories use conditional create, never blind overwrite;
- after lease takeover, retry first loads the reserved resource ID;
- if the resource exists and matches the idempotency result, complete/replay it;
- if absent, safely continue creation with the same ID;
- operation-specific uniqueness constraints still apply across different idempotency keys.

This guarantees one logical resource even when the process crashes after resource creation but before marking the idempotency record complete.

### 5.6 Failure policy

- validation, authentication, authorization and rate limiting occur before the idempotency claim when possible;
- deterministic post-claim 4xx failures become `FAILED_FINAL` and can be replayed safely;
- infrastructure/unknown 5xx failures do not become `COMPLETED`; retain/release via lease so a retry can recover;
- no stack trace, signed URL, JWT, password, raw e-mail or raw key in the record;
- same key cannot be repurposed after a final failure until logical expiry.

### 5.7 Operation-specific replay

- User: load reserved user, generate a fresh JWT, return normal authentication response.
- Artist/Album/Playlist: return stored safe creation snapshot or reconstruct the stable creation result; preserve original success status. Mutable resources must use the safe snapshot if current state could differ.
- Upload: load `SongUpload`; regenerate presigned URLs if pending, return completed state if completed, or return the defined terminal conflict for failed/expired upload.

---

## 6. User ↔ Artist ownership

### 6.1 Model

Create pure domain model:

```text
ArtistAccount
- ArtistId artistId
- UserId userId
- ArtistPermission permission: OWNER | MANAGER
- Instant createdAt
```

Create DynamoDB table `ArtistAccounts`:

```text
PK: artistId
SK: userId
```

P0 access pattern is `find membership by artistId + userId`. Do not add a GSI without a current use case.

### 6.2 Artist creation

`POST /api/v1/artists` remains ADMIN-only and `CreateArtistRequest` gains required `ownerUserId`.

Rules:

- owner user must exist;
- owner user must have `ROLE_ARTIST`;
- create `Artist` and `ArtistAccount(OWNER)` atomically through an infrastructure port using DynamoDB transaction;
- endpoint requires `Idempotency-Key`;
- request hash includes `name` and `ownerUserId`;
- response can remain `ArtistResponse`; ownership administration UI is out of scope.

### 6.3 Access policy

An actor can manage an artist when:

```text
actor has ADMIN role
OR
actor has ARTIST role AND an OWNER/MANAGER ArtistAccount for the artist
```

Application-level checks are mandatory even when `SecurityConfig` also restricts routes.

Apply to:

- album creation for `command.artistId`;
- song-upload initiation after resolving album → artist;
- song-upload confirmation after resolving stored upload → artist.

Upload records store `initiatedByUserId` for audit. Any active OWNER/MANAGER for the same artist or ADMIN may confirm; confirmation is not limited to the initiating user.

### 6.4 Existing data

Fail closed:

- unowned existing artists are manageable only by ADMIN;
- no automatic assignment to the first `ROLE_ARTIST` user;
- provide a versioned, idempotent backfill script/runbook that accepts an explicit `artistId,userId,permission` mapping;
- script validates user/artist existence, supports dry-run and uses conditional writes;
- document rollback and audit output;
- tests seed explicit memberships.

Add table provisioning to README and integration-test setup.

---

## 7. Song upload lifecycle

### 7.1 Source of truth

Create separate `SongUpload` resource. Do not write `Song` metadata at initiation.

`SongUpload` fields:

```text
SongId songId                         # primary identity and route identity
AlbumId albumId
ArtistId artistId
UserId initiatedByUserId
String title
String stagingStorageKey              # pending/{songId}
String finalStorageKey                # songs/{songId}
String expectedContentType
long expectedContentLengthBytes
List<String> expectedPartChecksumsSha256
int expectedPartCount
String multipartUploadId              # nullable for single-part
UploadState state
String leaseTokenHash                  # nullable
Instant leaseUntil                     # nullable
Instant createdAt
Instant expiresAt
Instant completedAt                    # nullable
long version
```

States:

```text
INITIALIZING | PENDING | COMPLETING | COMPLETED | FAILED | ABORTED | EXPIRED
```

Create DynamoDB table `SongUploads`:

```text
PK: songId
GSI state-expiry-index:
  PK: state
  SK: expiresAtEpochSeconds
```

The GSI supports bounded cleanup/reconciliation. Provision it in dev/test/prod documentation.

### 7.2 Initiation request

Extend `InitiateSongUploadRequest` with expected checksums:

```text
title
contentType
contentLengthBytes
partChecksumsSha256
```

Rules:

- existing max size remains 500 MiB;
- existing multipart threshold/part size determine expected part count;
- exactly one SHA-256 checksum for single-part;
- one SHA-256 checksum per expected multipart part;
- each checksum is Base64 for exactly 32 decoded bytes;
- checksums are included in the idempotency fingerprint;
- title/content type/size/checksums persist in `SongUpload`;
- endpoint requires an authorized artist manager and `Idempotency-Key`.

### 7.3 Initiation algorithm

1. validate/normalize request and checksums;
2. resolve album and artist;
3. enforce owner/manager/ADMIN policy;
4. pass rate limits;
5. claim idempotency and reserve `songId`, staging key and final key;
6. conditional create/recover `SongUpload(INITIALIZING)`;
7. single part: transition to `PENDING`, generate a PUT URL for staging key with signed `Content-Type` and `x-amz-checksum-sha256` requirements;
8. multipart:
   - if no stored upload ID, recover any existing in-progress multipart upload for the exact staging key;
   - abort duplicates if multiple recoverable uploads exist;
   - otherwise create one multipart upload with checksum support;
   - conditionally persist upload ID and transition `PENDING`;
   - generate part URLs for the existing upload ID with required per-part checksum headers;
9. mark idempotency complete by stable upload reference, not by persisting signed URLs;
10. return URLs plus `requiredHeaders` and expiry.

Crash after S3 multipart creation but before persistence is mitigated by recovery/listing and bucket lifecycle abort of incomplete multipart uploads.

Replay:

- `PENDING`: generate fresh URLs against same staging key/upload ID;
- `INITIALIZING` with active lease: 409 + Retry-After;
- expired initialization lease: recover/take over;
- `COMPLETED`: return completed stable result or defined 200 completed response;
- `FAILED/ABORTED/EXPIRED`: 409 with stable terminal message; a new attempt needs a new key.

### 7.4 Response contract

`PresignedUploadPartResponse` must include:

```text
partNumber
url
requiredHeaders     # content type/checksum headers the client must send
```

Never log URLs or return them from metrics/traces.

### 7.5 Confirmation request

The route remains for P0:

```http
POST /api/v1/albums/{albumId}/songs/{songId}/confirm
```

The server obtains staging key, final key and multipart upload ID from `SongUpload`. Remove those values from `ConfirmSongUploadRequest` after compatibility review. The request contains only completed part evidence needed from the client:

```text
parts: partNumber, eTag, checksumSha256
```

Single-part confirmation may use an empty parts list.

### 7.6 Confirmation algorithm

1. load `SongUpload` by `songId`;
2. verify route album matches stored album;
3. enforce owner/manager/ADMIN policy;
4. apply rate limit;
5. if `COMPLETED`, load and return the existing Song with 200;
6. if terminal failed/expired, return 409;
7. conditionally acquire `PENDING → COMPLETING` lease;
8. if another active confirmer owns the lease, return 409 + Retry-After;
9. for multipart, complete using ordered, unique expected parts and checksums;
10. if retry finds S3 multipart already completed, detect staging object with `HeadObject` and continue rather than failing;
11. verify staging object:
    - exact content length;
    - normalized content type;
    - signed single-part checksum or required multipart per-part checksum evidence;
12. promote staging object to immutable `songs/{songId}` final key using S3 copy;
13. verify final object and delete staging object;
14. DynamoDB transaction:
    - conditional put final `Song` if absent;
    - conditional transition upload lease to `COMPLETED`;
15. return stable `SongResponse` 200.

If integrity fails:

- delete/quarantine staging/final object as appropriate;
- conditionally mark upload `FAILED`;
- do not create Song metadata;
- return deterministic 400/409 according to the failure class.

Crash recovery:

- if S3 completion/promotion happened but Dynamo transaction did not, lease takeover verifies the final object and completes Dynamo state;
- no second logical Song is created;
- final object key is never exposed to an unconfirmed client PUT, preventing post-confirm overwrite through a still-valid staging URL.

### 7.7 Visibility

Only `Song` table records represent playable/catalog songs. Therefore pending uploads are naturally excluded from:

- song detail/stream URL;
- search;
- album-song query;
- playlist membership validation;
- likes.

### 7.8 Cleanup

Implement a bounded scheduled/admin cleanup adapter:

- query non-terminal upload states through `state-expiry-index`;
- conditionally claim expired upload;
- abort multipart upload when possible;
- delete staging object;
- mark `EXPIRED`/`ABORTED`;
- increment cleanup metrics;
- never scan the entire table.

Also document S3 lifecycle rules:

- abort incomplete multipart uploads after one day;
- expire `pending/` staging objects after two days;
- never expire `songs/` playable objects through this rule.

Multiple application instances may run cleanup; conditional claim makes it safe.

---

## 8. Distributed rate limiting

### 8.1 Implementation

Use Redis atomic token buckets implemented as a Lua script through Spring Data Redis/Lettuce already present in the dependency graph.

- no in-memory limiter as production authority;
- no new Maven coordinate without approval;
- Redis server time drives refill calculations;
- one Lua execution atomically refills, consumes, updates TTL and returns decision;
- Redis keys contain only HMAC digests, never raw e-mail/IP/user identifiers;
- HMAC key comes from required prod secret `RATE_LIMIT_KEY_SECRET`, separate from JWT secret;
- dev receives an explicit non-production default.

A pure application port accepts policy name and primitive subjects. Redis/Lua stays in infrastructure.

### 8.2 Client address

Default source is `request.getRemoteAddr()`.

Forwarded headers are used only when the immediate peer matches configured trusted proxy CIDRs. Reject a production wildcard trust configuration. Parse the standardized `Forwarded` header first, then `X-Forwarded-For` if policy allows. Normalize IPv4/IPv6 before hashing.

### 8.3 Policies

All values are externally configurable. Safe defaults:

| Policy | Buckets | Capacity/refill | Backend failure |
|---|---|---|---|
| Register | IP-wide; IP + normalized e-mail | 20/hour; 5/hour | fail closed with 503 |
| Authenticate | IP-wide; IP + normalized e-mail | 100/15 min; 10/15 min | fail closed with 503 |
| Upload initiate | user; user + album | 20/min; 40/hour | fail closed with 503 |
| Upload confirm | user; user + album | 60/min; 120/hour | fail closed with 503 |
| Search | user, fallback trusted IP | 120/min | fail open with warn + metric |

All applicable buckets must allow a request. Header values represent the most restrictive evaluated bucket.

Rate limiting occurs before Argon2 authentication work and before idempotency claims/business side effects. Idempotent replays still consume rate-limit capacity because they consume API resources and must not bypass abuse controls.

OPTIONS/CORS preflight, internal health probes and static Swagger assets in non-prod are excluded from business limits.

### 8.4 Headers and errors

On allowed and blocked requests:

```text
RateLimit-Limit
RateLimit-Remaining
RateLimit-Reset      # seconds until useful reset/refill
```

On blocked requests also:

```text
Retry-After
```

429 uses canonical `ErrorResponse`. Redis fail-closed 503 also uses the canonical envelope and must not claim the caller exceeded a limit.

### 8.5 Test infrastructure

Create dedicated Redis integration-test support with Testcontainers `GenericContainer` using a pinned `redis:7-alpine` image. Do not force every LocalStack IT to start Redis when it does not need it.

Tests use bounded polling/controllable policy windows, not long `sleep` calls.

---

## 9. Protocol error mapping

Preserve the current canonical envelope. Add explicit mapping/tests:

| Scenario | Response |
|---|---|
| Malformed JSON | 400 |
| Invalid enum/path UUID | 400 |
| Missing required query/header parameter | 400 |
| Bean/domain length/pattern violation | 400 |
| Missing/invalid Idempotency-Key | 400 |
| Same key/different request | 409 |
| Active idempotency lease | 409 + `Retry-After` |
| Unsupported method | 405 + framework-provided `Allow` preserved |
| Unsupported media type | 415 |
| API JSON request body over 64 KiB | 413 |
| Declared audio size over 500 MiB | 413 |
| Rate limit exceeded | 429 + rate-limit headers |
| Rate limiter required but unavailable | 503 |

Implement through specific exception handlers / `ResponseEntityExceptionHandler` overrides and filter error writer support. The generic `Exception` handler remains the last resort and must not absorb known client errors into 500.

---

## 10. Input bounds and normalization

Apply at DTO and domain/application boundaries. Exact limits:

| Field | Rule |
|---|---|
| Idempotency-Key | 16–128 ASCII, `[A-Za-z0-9._:-]+` |
| User name | trim; 1–100 chars; reject control characters |
| E-mail | trim + lowercase `Locale.ROOT`; valid; max 320 chars |
| Password | 8–128 chars; never trim silently |
| Country | uppercase ISO-style two-letter `[A-Z]{2}` |
| Artist name | trim; 1–200 chars |
| Album name | trim; 1–200 chars |
| Playlist name | trim; 1–100 chars |
| Song title | trim; 1–200 chars |
| Search query | trim; 1–100 chars |
| Cover-art URL | optional; max 2048; absolute `https` in prod policy |
| Audio content type | existing allowlist; normalized lowercase |
| Audio length | 1–500 MiB |
| Part count | exactly calculated count; current bounds produce max 10 |
| Part number | unique, contiguous, 1..expectedPartCount |
| ETag | 1–256 chars; safe character validation |
| SHA-256 checksum | valid Base64 decoding to 32 bytes |
| Generic JSON body | max 64 KiB at application boundary |

Remove ignored `CreateAlbumRequest.songTitles` and empty unused `PlaylistDetailsResponse` in this epic because they create misleading input/contract surface.

Do not log rejected passwords, JWTs, idempotency keys, signed URLs or full request bodies.

---

## 11. Production exposure

### 11.1 Swagger

In `application-prod.yaml`:

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

Dev/test retain Swagger. Add a prod-profile test that verifies public Swagger/API-doc paths are unavailable.

### 11.2 Actuator

Production safe default:

- separate management port, default `8081`, configurable by `MANAGEMENT_SERVER_PORT`;
  > **Locked deviation (2026-08-23):** the authorization document fixes the production management
  > port at **`9090` (internal-only)**. Where this spec says `8081`, read `9090`.
- expose `health` only;
- enable liveness/readiness probes;
- `show-details: never`;
- no `info` or `metrics` through the public business port;
- liveness/readiness are usable on the management port;
- deployment documentation must state that the management port is internal and must not be internet-published.

Network isolation is enforced by the deployment platform, not claimed by application configuration alone.

---

## 12. Observability and security logging

Use existing Micrometer/Actuator support. Required low-cardinality metrics:

```text
api.idempotency.claims{operation,outcome}
api.idempotency.replays{operation}
api.idempotency.conflicts{operation,reason}
api.rate_limit.requests{policy,outcome}
api.rate_limit.backend_errors{policy,mode}
api.artist_access.decisions{operation,outcome}
api.song_upload.transitions{from,to}
api.song_upload.cleanup{outcome}
```

Allowed tags are fixed enums/operation names. Never tag user ID, IP, e-mail, key, artist ID, album ID, song ID or storage key.

Structured logs:

- audit ownership denial/admin override with safe actor/resource UUIDs and operation;
- log idempotency outcome using operation and a short non-reversible correlation digest, never raw key;
- never log JWTs, passwords, request bodies, checksums as secrets, signed URLs or Redis keys;
- warn/error on cleanup/reconciliation failures with safe IDs;
- rate-limit blocks log only aggregated/sampled events to avoid log-amplification abuse.

---

## 13. Data/schema deliverables

Add and document:

1. `IdempotencyRecords` table with TTL;
2. `ArtistAccounts` table with composite key;
3. `SongUploads` table with `state-expiry-index`;
4. S3 lifecycle rules for pending objects/incomplete multipart uploads;
5. any conditional-create changes for Users/Artists/Albums/Playlists/Songs;
6. Redis rate-limit configuration and required secret;
7. LocalStack/README provisioning;
8. `AbstractIntegrationTest` provisioning;
9. dedicated Redis Testcontainer support;
10. explicit artist-account backfill runbook/script.

No test may depend on a manually created local table that is absent from committed setup.

---

## 14. Acceptance test matrix

### 14.1 Likes

- PUT absent/present/repeated/concurrent → one stable record;
- DELETE absent/present/repeated → no record, 204;
- missing entity, invalid type and invalid UUID;
- unauthorized request;
- old toggle route no longer mutates state.

### 14.2 Playlist membership

- sequential and concurrent same-song PUT → one membership;
- same-song optimistic conflict reconciles to 200;
- different-song stale update retains explicit conflict semantics;
- repeated DELETE → 204 without version bump;
- owner/non-owner; missing playlist/song; max unique-song limit.

### 14.3 Idempotency

- missing/blank/malformed/oversized key;
- same key/request replay;
- different body/path parameter conflict;
- key isolation across user and endpoint;
- anonymous registration scope;
- concurrent N-way claim;
- active lease and `Retry-After`;
- expired lease takeover;
- crash before resource write;
- crash after resource write/before idempotency completion;
- logical expiry before Dynamo TTL deletion;
- deterministic final failure replay;
- sensitive response data absent from records/logs.

### 14.4 Upload

- same initiation key before/after URL expiry;
- concurrent initiation creates one upload/song ID;
- multipart initialization recovery;
- pending song is not readable/searchable/playable;
- valid single/multipart confirm;
- repeated/concurrent confirm;
- crash after S3 complete/promote but before Dynamo completion;
- wrong album/non-owner/admin override;
- size/content-type/checksum/parts mismatch;
- staging promotion and final-content download;
- expired cleanup and idempotent cleanup claim.

### 14.5 Ownership

- owner and manager ARTIST allowed;
- unrelated ARTIST denied;
- ADMIN override allowed;
- target owner must exist and have ARTIST role;
- unowned existing artist fails closed for non-admin;
- backfill is idempotent.

### 14.6 Rate limiting

- N allowed, N+1 429;
- allowed and blocked headers;
- reset/refill;
- policy/key isolation;
- concurrent atomic consumption;
- trusted/untrusted forwarded header;
- e-mail normalization without PII Redis keys;
- Redis fail-closed/fail-open policy;
- two limiter instances share state;
- metrics outcomes.

### 14.7 Protocol/exposure

- 400 malformed JSON/enum/UUID/missing parameter/validation;
- 405 with `Allow`;
- 413 JSON/audio declared size;
- 415 wrong content type;
- 429 canonical body and headers;
- prod Swagger disabled;
- public port has no metrics/detailed health;
- management health has no details and probes remain available.

---

## 15. Verification commands

At each relevant step, run the smallest useful subset and finish with the full mirror:

```bash
./mvnw test
./mvnw jacoco:check
./mvnw spotbugs:check
./mvnw dependency-check:check -DfailBuildOnAnyVulnerability=false
./mvnw test -Dtest='*IT' -DfailIfNoTests=false
./mvnw clean package

grep -rEn "^import (com\.spotpobre\.backend\.infrastructure|software\.amazon|io\.awspring|org\.mapstruct|org\.springdoc|org\.springframework\.web)" \
  src/main/java/com/spotpobre/backend/domain \
  src/main/java/com/spotpobre/backend/application
```

OWASP remains advisory under current configuration; review findings rather than treating command success as proof of no vulnerabilities.

---

## 16. Documentation deliverables

Update in the same epic:

- `README.md` current state, routes, headers, LocalStack tables and Redis/runtime settings;
- `CHANGELOG.md` breaking route/method changes and security improvements;
- `AGENTS.md` commands, architecture and debt;
- `docs/testing-playbook.md` suite map, Redis/LocalStack tests and retry matrix;
- `docs/coding-standards.md` idempotency/rate-limit/ownership rules;
- `docs/data-model-decisions.md` ArtistAccount, IdempotencyRecord and SongUpload decisions;
- `docs/lessons.md` only durable implementation lessons;
- minimal OpenAPI annotations/examples for new methods, headers, statuses and upload fields;
- artist-account backfill and upload cleanup runbooks;
- production configuration contract for rate-limit secret, Redis and management port.

The epic is not Done while documentation describes the previous toggle, POST membership, placeholder-song upload or public operational exposure.