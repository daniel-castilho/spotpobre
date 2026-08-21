# API Design Excellence P0 — Implementation Sequence
## Safety, Idempotency & Abuse Protection

**Companions:** `api-design-excellence-p0-spec.md` · `api-design-excellence-p0-backlog.md`  
**Rule:** Complete each step’s acceptance and verification before starting the next. Do not invent P1/P2 scope.

---

## Global execution rules

1. Work in small, reviewable vertical commits; never deliver the epic as one unreviewable change.
2. Read the referenced story acceptance before coding.
3. Add tests with the production change, not at the end.
4. A red baseline or ambiguous locked decision stops work.
5. Every DynamoDB schema change updates README/local setup and integration-test provisioning in the same step.
6. Every route/status/header change updates security matchers, tests and minimal OpenAPI in the same step.
7. Never add a dependency without explicit approval.
8. After each step, update task status and note deviations; do not silently alter the specification.

### Fast verification used throughout

```bash
./mvnw test
```

### Infrastructure verification when touched

```bash
./mvnw test -Dtest='*IT' -DfailIfNoTests=false
```

### Boundary verification when core packages change

```bash
grep -rEn "^import (com\.spotpobre\.backend\.infrastructure|software\.amazon|io\.awspring|org\.mapstruct|org\.springdoc|org\.springframework\.web)" \
  src/main/java/com/spotpobre/backend/domain \
  src/main/java/com/spotpobre/backend/application
```

Expected: no matches.

---

## Step 0 — Baseline, design lock and dependency gate
### Stories: S0

### Actions

1. Confirm HEAD, working tree and current CI status.
2. Run current fast suite and representative LocalStack ITs.
3. Compare current routes, DTOs, tables, security rules and upload code to the specification.
4. Record the locked decisions in `docs/data-model-decisions.md` or focused ADR sections:
   - DynamoDB durable idempotency record + lease recovery;
   - registration fresh-token replay;
   - SongUpload lifecycle/staging promotion;
   - ArtistAccount ownership;
   - Redis Lua token bucket and failure modes.
5. Verify the existing dependency graph exposes:
   - `StringRedisTemplate` / Spring Data Redis script support;
   - Testcontainers `GenericContainer`;
   - required AWS SDK S3 checksum/copy/list APIs.
6. If any capability requires a new Maven coordinate, stop and request approval with exact coordinate/reason/alternatives.
7. Plan new tables, TTL/GSI and rollout order.
8. Confirm all mandatory source files exist; remove references to untracked analysis files.

### Done when

- baseline is green or a blocker report is accepted;
- decisions are checked in and match the technical specification;
- dependency approach is approved;
- no unresolved “or/if available/equivalent” choice remains;
- implementation plan names exact files/packages/tests to change.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='AuthenticationFlowIT,PlaylistFlowIT,ArtistSongFlowIT' -DfailIfNoTests=false
```

---

## Step 1 — Boundary normalization and protocol primitives
### Stories: S1

This step happens before idempotency hashing so the canonical command is stable.

### Actions

1. Implement exact length/pattern/normalization rules from the spec.
2. Add pure `IdempotencyKey` validation model without HTTP annotations.
3. Add typed exceptions for payload too large, idempotency conflict/in-progress, limiter unavailable and upload integrity.
4. Add web-layer support for custom headers on canonical error responses.
5. Enforce 64 KiB API JSON-body limit without reading/logging unrestricted bodies.
6. Remove ignored `CreateAlbumRequest.songTitles` and empty `PlaylistDetailsResponse`.
7. Add min/max/invalid tests.

Do not yet require Idempotency-Key on production routes; foundation first, application in later steps.

### Done when

- normalization is deterministic and tested;
- password is never silently trimmed;
- exact max bounds are enforced at DTO and core boundary;
- declared audio over 500 MiB has a typed 413 path;
- no known protocol error has accidentally changed before Step 8.

### Verify

```bash
./mvnw test
```

---

## Step 2 — ArtistAccount schema, owner assignment and migration
### Stories: S2, S3

Ownership must be final before album/upload idempotency scope is implemented.

### Actions

1. Add pure `ArtistAccount` and `ArtistPermission`.
2. Add repository/management ports.
3. Implement `ArtistAccounts` DynamoDB table/document/adapter.
4. Update LocalStack README setup and `AbstractIntegrationTest`.
5. Add required `ownerUserId` to current admin artist-creation request/command.
6. Verify owner user exists and has `ROLE_ARTIST`.
7. Atomically create Artist + OWNER membership through a DynamoDB transaction adapter.
8. Keep `POST /api/v1/artists` ADMIN-only.
9. Implement dry-run/idempotent explicit backfill script/runbook.
10. Fail closed for unowned existing artists except ADMIN.

Idempotency-Key is not applied to artist creation until Step 5; write creation APIs so they can accept a reserved ArtistId later.

### Done when

- artist creation cannot leave Artist without owner;
- membership adapter IT is green;
- invalid owner fails without partial persistence;
- backfill process is repeatable and documented;
- table provisioning is committed everywhere.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='*ArtistAccount*IT,*Artist*IT' -DfailIfNoTests=false
```

---

## Step 3 — Application artist access policy
### Stories: S4

### Actions

1. Add immutable actor UserId to create-album/initiate/confirm commands.
2. Implement pure application `ArtistAccessPolicy` using domain ports.
3. Policy:
   - ADMIN allowed;
   - otherwise requires ARTIST role and OWNER/MANAGER membership.
4. Resolve album → artist before upload decisions.
5. Store initiating actor in future upload command/model seams.
6. Apply policy to album creation and existing upload services.
7. Configure explicit HTTP defence in `SecurityConfig`: `POST /api/v1/albums` and both song-upload POST routes require `ROLE_ARTIST` or `ROLE_ADMIN`; keep the application ownership policy authoritative.
8. Add safe access-decision metrics/audit hooks.

### Done when

- unrelated `ROLE_ARTIST` cannot act on another artist/album;
- owner/manager and ADMIN paths are tested;
- application tests use no Spring Security/web type;
- current E2E flow seeds explicit membership.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='ArtistSongFlowIT,*Ownership*IT' -DfailIfNoTests=false
```

---

## Step 4 — Naturally idempotent likes and playlist membership
### Stories: S5, S6

### Part A — Likes

1. Add PUT/DELETE mappings under `/api/v1/users/me/likes/{entityType}/{entityId}`.
2. Use lowercase singular entity type and UUID parsing.
3. Replace toggle service/repository methods with desired-state methods.
4. PUT uses conditional create-if-absent and preserves original likedAt.
5. DELETE is delete-if-present.
6. Return 204 and remove mutation count response.
7. Remove old toggle mapping and update security/docs/tests.

### Part B — Playlist membership

1. Change add-song POST to PUT.
2. Implement unique SongId membership and `ensure...` domain operations.
3. Skip writes/version bumps for already-satisfied state.
4. On same-song stale conflict, reload and return success if membership now exists.
5. DELETE returns 204 and skips write when absent.
6. Retain conflict semantics for genuinely different concurrent modifications.

### Done when

- all sequential/concurrent same-state scenarios from the spec pass;
- old toggle cannot mutate;
- playlist never contains duplicate SongId;
- ownership and limits remain green;
- minimal OpenAPI and CHANGELOG note breaking method/route changes immediately.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='*Like*IT,PlaylistFlowIT,*Playlist*ConcurrencyIT' -DfailIfNoTests=false
```

---

## Step 5 — Durable idempotency foundation
### Stories: S7, S8

### Actions

1. Add pure idempotency models/port/coordinator.
2. Add `IdempotencyRecords` DynamoDB document/table/adapter and TTL docs.
3. Implement scope digest and versioned canonical request hash.
4. Implement conditional claim, active lease, takeover, completion and final failure.
5. Reserve stable operation resource ID in the claim.
6. Implement logical expiry independent of physical TTL deletion.
7. Add result reference/safe snapshot support; prohibit secrets/expiring URLs.
8. Add fault-injection seam around business write/completion for tests.
9. Add low-cardinality metrics and redacted log helpers.
10. Update LocalStack/test/prod provisioning.

Do not attach the coordinator to endpoints until adapter/core tests prove crash-recovery primitives.

### Done when

- adapter IT passes all claim/lease/hash/expiry transitions;
- N concurrent claims have one owner;
- active lease yields defined retry metadata;
- expired lease takeover preserves resource ID;
- raw keys/PII are absent from Dynamo/logs/metrics;
- full core remains framework-free.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='*Idempotency*IT' -DfailIfNoTests=false
```

---

## Step 6 — Apply idempotency to stable creation operations
### Stories: S9, S10, S11, S12

Implement one endpoint at a time in this order. Complete tests before the next.

### 6A Registration

- require header on current `/auth/register`;
- anonymous scope;
- stable UserId and unique-email transaction recovery;
- fresh JWT on replay, never stored;
- rate limit hook interface may be stubbed until Step 9, but do not bypass final ordering.

### 6B Artist

- ADMIN + ownerUserId contract from Step 2;
- stable ArtistId;
- recover atomic Artist + OWNER transaction;
- safe response snapshot.

### 6C Album

- owner/manager/ADMIN check before claim/replay;
- stable AlbumId conditional create;
- actor and artist included in scope/fingerprint.

### 6D Playlist

- stable PlaylistId;
- owner/name fingerprint;
- replay does not consume ten-playlist limit again;
- safe creation snapshot.

### Done when

For every endpoint:

- missing/invalid key → 400;
- same key/request → one resource + replay header;
- same key/different request → 409;
- N concurrent duplicates → one resource;
- injected crash after resource write recovers same resource;
- different idempotency keys retain normal business uniqueness/limit rules;
- authorization is rechecked on authenticated replay.

### Verify after each substep

```bash
./mvnw test
./mvnw test -Dtest='*Idempotent*IT,AuthenticationFlowIT,ArtistSongFlowIT,PlaylistFlowIT' -DfailIfNoTests=false
```

---

## Step 7 — SongUpload source of truth and visibility
### Stories: S13

### Actions

1. Add `SongUpload` state model with all specified fields/transitions.
2. Add `SongUploads` table/document/adapter and `state-expiry-index`.
3. Implement conditional create/version/lease transitions.
4. Refactor initiation so it does not write Song metadata.
5. Ensure reads/search/album queries/playlist validation operate only on completed `Song` table rows.
6. Store actor/artist/expected integrity metadata.
7. Update all provisioning.

### Done when

- state transition unit tests are exhaustive;
- adapter lease/version IT is green;
- pending upload cannot be fetched/searched/streamed/liked/added;
- existing completed-song flow remains green.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='*SongUpload*IT,AlbumSongConsistencyIT,SongSearchPaginationIT' -DfailIfNoTests=false
```

---

## Step 8 — Idempotent initiation, confirmation, integrity and cleanup
### Stories: S14, S15, S16

### 8A Initiation

1. Add required checksum list validation.
2. Require Idempotency-Key and authorized actor.
3. Reserve same song/staging/final keys through idempotency.
4. Single-part presign requires content type/checksum headers.
5. Multipart creation stores/reuses upload ID; recover by exact staging key after crash.
6. Response parts include required headers.
7. Replay regenerates URLs; never stores signed URLs.

### 8B Confirmation

1. Make server record authoritative for keys/upload ID.
2. Acquire `PENDING → COMPLETING` lease.
3. Validate ordered unique part evidence/checksums.
4. Complete/recover S3 effect.
5. Verify staging size/type/checksum policy.
6. Copy staging to immutable final key, verify, delete staging.
7. Transactionally create Song + mark completed.
8. Recover after S3/Dynamo crash.
9. Repeated completed confirm returns same Song.

### 8C Cleanup

1. Implement bounded state-expiry GSI query.
2. Conditional cleanup claim.
3. Abort multipart/delete staging/mark terminal.
4. Add S3 lifecycle policy docs and metrics.
5. Prove multiple cleaners are safe.

### Done when

- every upload scenario in the spec matrix passes;
- URL-expiry replay is usable;
- S3 completion/promotion is logically once under concurrency/crash;
- integrity mismatch creates no playable Song;
- final object cannot be overwritten by pending URL;
- expired uploads have automated/reconciled cleanup.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='S3SongStorageAdapterIT,ArtistSongFlowIT,*SongUpload*IT,*Upload*IT' -DfailIfNoTests=false
```

---

## Step 9 — Redis rate limiting and policies
### Stories: S17, S18, S19

### 9A Core

1. Configure synchronous Spring Data Redis access from existing dependency graph.
2. Add atomic Lua token bucket using Redis TIME.
3. HMAC all subjects with dedicated secret.
4. Add trusted client-address resolver with explicit CIDRs.
5. Add externalized policy records/properties and startup validation.
6. Add dedicated Redis `GenericContainer` IT base.
7. Add metrics.

### 9B Public auth

1. IP-wide register/auth protection.
2. IP+normalized-email register/auth protection.
3. Check before expensive auth/password work.
4. Fail closed with 503 on Redis outage.

### 9C Upload/search

1. User and user+album upload policies, fail closed.
2. User/fallback trusted-IP search policy, fail open with metric/warn.
3. Idempotent replays consume capacity.
4. Add success/429 headers and canonical envelope.

### Done when

- real Redis atomic/concurrent tests pass;
- N/N+1, refill, key isolation and two-client sharing pass;
- forwarded-header spoof cannot bypass;
- no PII in Redis keys;
- outage modes match spec;
- 429 and allowed response headers match contract.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='*RateLimit*IT,AuthenticationFlowIT,ArtistSongFlowIT,*SearchPaginationIT' -DfailIfNoTests=false
```

---

## Step 10 — Complete protocol mapping and production exposure
### Stories: S20, S21, S22

### Part A — Protocol errors

- implement every status/header row in the specification;
- preserve `Allow`, Retry-After and RateLimit headers;
- canonical envelope from filters and MVC;
- keep generic 500 only for unexpected failures.

### Part B — Production exposure

- prod Swagger/API docs disabled;
- management port default 8081;
- expose health only, probes enabled, no details;
- no metrics/info on public business port;
- document required deployment network isolation.

### Part C — Observability/redaction

- all required metrics;
- safe ownership/admin audit;
- sampled limiter logs;
- automated checks for sensitive values where practical.

### Done when

- exact protocol E2E matrix is green;
- prod-profile exposure tests are green;
- no normal business user sees operational details;
- no high-cardinality/sensitive metric/log tags exist.

### Verify

```bash
./mvnw test
./mvnw test -Dtest='ErrorHandlingFlowIT,*ProductionExposure*IT,*RateLimit*IT' -DfailIfNoTests=false
```

---

## Step 11 — Full acceptance, fault injection and documentation sync
### Stories: S23, S24

### Actions

1. Map every specification acceptance row to a named test and CI command.
2. Run fault injection for:
   - resource write/idempotency completion gap;
   - multipart create/persistence gap;
   - S3 complete/promotion/Dynamo completion gaps;
   - Redis unavailable modes.
3. Run complete suite and quality gates.
4. Review OWASP report; do not equate advisory success with no findings.
5. Verify no forbidden core imports.
6. Verify no sensitive values in logs, Dynamo idempotency rows or Redis keys.
7. Update all documentation/provisioning/runbooks listed in the spec.
8. Replace this file’s pre-implementation language with an as-built status/deviation note if project convention requires it.
9. Produce final implementation report:
   - changed contracts;
   - schemas/config;
   - tests/evidence;
   - migration/rollback;
   - remaining risks explicitly deferred to P1/P2.

### Full verification

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

### Done when

- all S0–S24 acceptance is evidenced;
- full gate is green;
- schema and rollback/backfill/cleanup procedures are documented;
- README, CHANGELOG, AGENTS, testing playbook, coding standards, data-model decisions and OpenAPI notes match reality;
- no P0 item is silently deferred;
- human reviewer accepts the as-built report.

---

## Final smoke / acceptance path

1. Register with key K; retry K → one user, both tokens currently valid.
2. Reuse K with another body → 409.
3. Create artist with explicit owner and key; retry → one artist/account.
4. Unrelated ARTIST attempts album/upload → 403; owner and ADMIN succeed.
5. PUT like twice → liked; DELETE twice → unliked.
6. PUT same playlist song sequentially/concurrently → one membership.
7. Initiate upload with key; let URLs expire/replay → same upload, fresh URLs.
8. Upload with required headers/checksums and confirm twice/concurrently → one completed Song.
9. Corrupt size/type/checksum → no Song, staging cleaned/terminal state.
10. Exceed each rate policy → 429 with exact headers; Redis outage follows selected mode.
11. Send malformed JSON/wrong method/media type/oversized body → exact 4xx, never 500.
12. Start prod profile → Swagger unavailable; public metrics unavailable; safe management probes available.
13. Inspect logs/metrics/Redis/Dynamo samples → no raw keys, e-mail/IP, JWT, signed URL or secret.

---

_Pre-implementation sequence. Preserve deviations and final evidence as an as-built record after delivery._