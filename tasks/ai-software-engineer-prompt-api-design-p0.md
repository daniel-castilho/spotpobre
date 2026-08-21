# AI Software Engineer Prompt — API Design Excellence P0
## Safety, Idempotency & Abuse Protection

**Status:** Ready for implementation after Step 0 baseline verification  
**Priority:** P0 — production safety  
**Target:** Eliminate retry hazards, close artist-resource authorization gaps and protect the public API against abuse  
**Package:** `com.spotpobre.backend` plus repository-level configuration, tests, scripts and documentation

You implement the complete **API Design Excellence P0** epic for the Spotpobre API. Correctness under retries, concurrency and partial failure takes priority over new features, cosmetic refactors and premature abstraction.

---

## Sources of truth — read in this order

1. `AGENTS.md`
2. `pom.xml` and `.github/workflows/ci.yml`
3. `docs/coding-standards.md`
4. `docs/testing-playbook.md`
5. `docs/data-model-decisions.md` and `docs/lessons.md`
6. `tasks/api-design-excellence-p0-spec.md`
7. `tasks/api-design-excellence-p0-backlog.md`
8. `tasks/api-design-excellence-p0-implementation-sequence.md`
9. Current production code and colocated `*Test` / `*IT` classes

If documentation disagrees with executable configuration, stop, report the mismatch and resolve it in the same change set. Do not rely on an analysis file that is not tracked in the repository.

---

## Goal

Make the current `/api/v1` contract safe under real network retries, concurrent delivery, process crashes and common abuse patterns.

The epic closes these current P0 risks:

- `POST /api/v1/likes/toggle` reverses user intent when a request is delivered twice;
- playlist song membership can contain duplicate song IDs;
- high-value POST commands have no `Idempotency-Key` contract;
- song-upload initiation can create duplicate metadata/multipart uploads;
- song-upload confirmation has no persistent lifecycle and is not replay-safe;
- pending uploads can be visible as songs before storage integrity is confirmed;
- `ROLE_ARTIST` is not tied to ownership/management of the target artist or album;
- public authentication, upload and search routes have no distributed abuse control;
- malformed/protocol-level requests can fall through to a generic 500;
- inputs are insufficiently bounded;
- production Swagger and operational endpoints are overexposed.

---

## Locked technical decisions

These decisions are not left for the implementation agent to invent:

1. **Keep current P0 route families.** Full route redesign remains P1. The registration endpoint stays `POST /api/v1/auth/register` for this epic.
2. **Replace like toggle** with idempotent PUT/DELETE routes and return 204. Do not return an eventually consistent like count from the mutation.
3. **Playlist membership uses PUT/DELETE.** Same-song PUT/DELETE retries must converge successfully without duplicate membership or unnecessary version changes.
4. **DynamoDB is the durable idempotency store.** Redis is used only for rate limiting.
5. **Idempotency is operation-aware, not a naïve response-cache filter.** Every protected creation gets a stable preassigned resource ID and can recover after a crash between the business write and idempotency completion.
6. **Registration never stores JWTs in idempotency records.** Replay resolves the same user creation and mints a fresh token.
7. **Upload initiation never replays expired signed URLs.** Replay resolves the same upload resource and regenerates URLs for the existing pending upload.
8. **Uploads use a persistent `SongUpload` resource** keyed by the reserved `songId`, with explicit lifecycle, leases, expected metadata and cleanup.
9. **Only completed uploads create visible `Song` metadata.** Pending/failed uploads cannot be searched, streamed or added to playlists.
10. **User-to-artist management uses `ArtistAccount`.** `ROLE_ARTIST` alone is insufficient. ADMIN override is explicit and audited.
11. **Rate limiting uses an atomic Redis Lua token bucket** through the existing Spring Data Redis/Lettuce dependency graph. Do not add Bucket4j, Redisson or another Maven coordinate without explicit approval.
12. **Production defaults are fail-safe:** Swagger disabled, detailed health disabled and only health probes exposed on a separate management port. Deployment/network isolation remains coordinated with the Runtime & Deployment epic.
13. **No new error-envelope redesign.** Preserve the canonical envelope in P0, but add exact protocol mappings and required headers. A broader RFC 9457 migration remains P1.

If the existing dependency graph cannot provide `StringRedisTemplate`, `RedisScript` and Testcontainers `GenericContainer`, stop before editing `pom.xml` and ask for approval.

---

## Non-negotiable engineering rules

- Keep `domain/` free of Spring, servlet, Redis, AWS SDK, Jackson and web types.
- Keep `application/` free of servlet, Spring Web, Redis and AWS SDK types.
- Controllers remain thin: resolve trusted boundary data, map requests, invoke inbound use cases and map results.
- Never claim exactly-once behaviour without a tested crash-recovery path.
- Never store/log raw idempotency keys, passwords, JWTs, signed URLs, rate-limit e-mail subjects or secrets.
- Idempotency validation, authorization and rate limiting happen before expensive or irreversible side effects.
- Use immutable user IDs for authenticated idempotency/ownership scope; never use mutable e-mail as the actor ID.
- Prefer naturally idempotent PUT/DELETE operations over POST action commands.
- Use DynamoDB conditional writes/transactions for uniqueness and state transitions.
- S3 and DynamoDB are not one transaction; every cross-system flow needs explicit compensation/recovery.
- Do not trust client-supplied storage keys/upload IDs when server state already owns those values.
- Do not make a pending upload visible as a catalog song.
- Every schema change must update local provisioning, integration-test provisioning and operator documentation.
- Every step adds its own tests; do not postpone all tests to the final step.
- English only in code, tests, logs and project documentation.
- Do not add a Maven dependency without explicit human approval.
- Do not push unless the human explicitly asks.
- Do not expand into ETag/If-Match, full route redesign, HTTP caching, advanced filtering, full OpenAPI redesign or API deprecation policy.

---

## Required HTTP contracts

### Likes

```http
PUT    /api/v1/users/me/likes/{entityType}/{entityId}  -> 204
DELETE /api/v1/users/me/likes/{entityType}/{entityId}  -> 204
```

`entityType` is lowercase singular: `song`, `artist` or `playlist`. `entityId` is a UUID. Repeated same-state operations return 204 and do not change an existing `likedAt` timestamp.

### Playlist membership

```http
PUT    /api/v1/playlists/{playlistId}/songs/{songId}  -> 200 PlaylistResponse
DELETE /api/v1/playlists/{playlistId}/songs/{songId}  -> 204
```

A repeated same-song PUT reloads/reconciles optimistic conflicts and returns success if membership already exists. Repeated DELETE succeeds without a write when membership is absent.

### Idempotency-Key

Required on:

```text
POST /api/v1/auth/register
POST /api/v1/artists
POST /api/v1/albums
POST /api/v1/playlists
POST /api/v1/albums/{albumId}/songs
```

Required behaviour:

- missing/invalid key → 400;
- same key + same canonical request → one logical effect;
- same key + different canonical request → 409;
- active lease → 409 + `Retry-After`;
- replay → original stable resource outcome and `Idempotency-Replayed: true`;
- first successful execution → `Idempotency-Replayed: false`;
- registration replay creates no second user and returns a fresh JWT;
- upload replay creates no second song/upload and regenerates usable signed URLs.

### Rate limiting

Use the policies and keys locked in the technical specification. A blocked request returns:

```text
429 Too Many Requests
Retry-After
RateLimit-Limit
RateLimit-Remaining
RateLimit-Reset
```

with the canonical JSON error envelope.

---

## Scope exclusions

Do not implement in this epic:

- wholesale `/users`, `/sessions`, collection-search or `/users/me/...` route redesign;
- API v2 or deprecation headers;
- ETag / If-Match;
- full Problem Details/RFC 9457 migration;
- HTTP response caching;
- arbitrary filters/sorts;
- refresh-token product design;
- Kubernetes/ECS manifests;
- full OpenAPI-first governance.

Minimal OpenAPI updates for changed routes, headers, statuses and upload fields are required.

---

## Definition of Done

The epic is complete only when all are true:

### State-setting operations

- [ ] Toggle endpoint is removed and explicit like PUT/DELETE is tested sequentially and concurrently.
- [ ] Playlist membership uses PUT/DELETE and cannot contain duplicate song IDs.
- [ ] Same-song optimistic conflicts reconcile to successful desired state.

### Durable idempotency

- [ ] Key format, required-header policy, authenticated/anonymous scope and canonical request hashing are implemented.
- [ ] `IdempotencyRecords` table, TTL, leases and conditional transitions are provisioned.
- [ ] Registration, artist, album, playlist and upload initiation are protected.
- [ ] Same-key/same-request, different-body, active lease, lease takeover, expiry and concurrent delivery are tested.
- [ ] Crash after business write but before idempotency completion does not duplicate a resource.
- [ ] JWTs and presigned URLs are not persisted/replayed as stale response secrets.

### Upload lifecycle and integrity

- [ ] `SongUpload` lifecycle and table are implemented with `PENDING`, `COMPLETING`, `COMPLETED`, `FAILED`, `ABORTED` and `EXPIRED` semantics.
- [ ] Pending uploads are not visible as songs.
- [ ] Replayed initiation regenerates URLs for the same upload.
- [ ] Concurrent/repeated confirmation is safe and logically completes S3 at most once.
- [ ] Expected size/content type and the specified checksum policy are enforced.
- [ ] Staging-to-final object promotion prevents post-confirm overwrite of the playable object.
- [ ] Expired/orphan upload cleanup and S3 lifecycle defence are implemented and tested where feasible.

### Ownership

- [ ] `ArtistAccount` is persisted and provisioned.
- [ ] New artists receive an explicit owner.
- [ ] Only an associated ARTIST or ADMIN can create albums/initiate/confirm for that artist.
- [ ] Existing unowned artists fail closed for non-admins and have a documented backfill path.
- [ ] Positive, negative and admin-override E2E tests pass.

### Abuse and protocol safety

- [ ] Atomic Redis token buckets protect register, authenticate, upload and search.
- [ ] Public-IP and e-mail/user policies, trusted-proxy handling and Redis outage modes are tested.
- [ ] 429 and success rate-limit headers are correct.
- [ ] Malformed JSON, invalid enum/UUID, missing parameter, 405/Allow, 413 and 415 return canonical 4xx responses.
- [ ] All request/header/path/body limits in the specification are enforced.

### Production exposure, observability and delivery

- [ ] Swagger/OpenAPI UI is disabled in prod.
- [ ] Prod management port exposes only safe health probes with no details.
- [ ] Idempotency/rate-limit/upload/ownership metrics exist without high-cardinality or sensitive tags.
- [ ] Audit/security logs contain actor/resource IDs where safe, but no secrets/PII-bearing keys.
- [ ] Unit, integration, E2E, JaCoCo, SpotBugs, boundary check and package build pass.
- [ ] README, CHANGELOG, AGENTS, testing playbook, coding standards, data-model decisions, API notes and provisioning instructions are synchronized.

Start at **Step 0** of `api-design-excellence-p0-implementation-sequence.md`. Stop immediately if the current baseline is red, a locked decision cannot be implemented with the approved dependency graph, or repository state contradicts the specification.