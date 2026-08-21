# Data Model Decisions

Record of the single-source-of-truth decisions taken during the Data Consistency & Modelling
epic (P1). Keep this file in sync whenever the data model changes.

## User ↔ Playlists

- **Source of truth:** the `Playlists` table, queried via the `ownerId-index` GSI.
- The `Users` table no longer embeds a `playlists` collection. The `User` aggregate does not hold
  playlists (the collection was removed from the domain model and the DynamoDB document).
- `MAX_PLAYLISTS_PER_USER = 10` is enforced by `CreatePlaylistService` against a persistent count
  (`PlaylistRepository.countByOwnerId`) before persisting.
- Concurrency note: the limit uses a count-then-insert pattern. For a user, two truly concurrent
  create requests could in principle both observe 9 and insert — a residual race we accept for P1.
  A strictly serializable counter (conditional increment on the User item) is the follow-up if the
  race becomes a concern.

## Album ↔ Songs

- **Source of truth:** the `Songs` table, each row carrying its `albumId`. Songs are queried via
  the `albumId-index` GSI on `Songs`.
- The `Albums` table and the `Album` aggregate no longer embed a `songs` collection, so there is no
  divergent list to keep in sync when a song is uploaded.

## Playlist updates (concurrency)

- Playlists carry a `version` attribute. Mutations (`addSong`, `removeSong`, `updateDetails`) are
  persisted with a conditional write `version = :expected` and the stored version is bumped.
- A write with a stale version fails with `PlaylistConcurrentModificationException` instead of
  silently overwriting another client's change.

## User registration (email uniqueness)

- **Source of truth:** the `UserEmails` table, keyed by the (normalized) email address.
- Registration writes the user and an email marker in a single `TransactWriteItems`; the email
  marker uses `attribute_not_exists(email)` so a concurrent second registration with the same
  email is rejected atomically.

## Song upload (S3 + DynamoDB partial failure)

- Order: generate S3 upload URL → persist song metadata.
- If metadata persistence fails after a multipart upload was created, the adapter attempts
  `abortUpload` to remove the orphan multipart upload and logs the outcome. Single-part presigned
  URLs do not create an object until the client PUTs it, so there is nothing to clean up.
- `@Transactional` was removed from the song upload services: it provided a false sense of
  atomicity across S3 and DynamoDB, which are separate systems.

> Superseded by "Song upload lifecycle (SongUpload)" below in the API Design Excellence P0 epic.

## API Design Excellence P0 — locked decisions

Recorded at Step 0 of the epic (source: `tasks/api-design-excellence-p0-spec.md`; binding per the
execution authorization). These are not open for reinterpretation during implementation.

### Durable idempotency (DynamoDB, lease-based)

- DynamoDB table `IdempotencyRecords` (`PK: scopeKey`, TTL attribute `expiresAtEpochSeconds`,
  no GSI) is the durable idempotency store. Redis/in-memory response caching is prohibited as a
  safety mechanism.
- Scope = SHA-256 digest of `apiVersion | actor scope | method | route template | path identity |
  Idempotency-Key`. Raw keys, e-mails, IPs, JWTs, presigned URLs and request bodies are never
  persisted or logged; only digests and safe snapshots.
- Canonical request hash is versioned (`hashVersion`), SHA-256 over normalized command fields.
- Claim protocol: conditional create `IN_PROGRESS` with a **preassigned stable resource ID** and
  a lease (30 s creations / 120 s uploads). Active lease → 409 + `Retry-After`; expired lease →
  conditional takeover preserving the resource ID. Completion is conditional on the current lease
  token; completed records are never overwritten.
- Crash recovery: creation services accept the reserved ID; after takeover they first check
  whether the resource already exists (conditional create, never blind overwrite) — one logical
  resource even if the process dies between business write and idempotency completion.
- Deterministic post-claim 4xx failures become `FAILED_FINAL` (replayable); unknown/5xx failures
  release via lease so a retry can recover.

### Registration replay mints fresh tokens

- Registration never stores JWTs in idempotency records. A replay resolves the same reserved
  user and generates a fresh JWT. Route stays `POST /api/v1/auth/register` for P0.

### User ↔ Artist ownership (ArtistAccount)

- New pure aggregate `ArtistAccount` (`artistId`, `userId`, permission `OWNER | MANAGER`,
  createdAt) persisted in table `ArtistAccounts` (`PK: artistId`, `SK: userId`, no GSI in P0).
- `ROLE_ARTIST` alone is insufficient to manage an artist resource: the policy is ADMIN **or**
  (ARTIST role + OWNER/MANAGER membership), enforced at application level with `SecurityConfig`
  as defence in depth. Admin override is explicit and audited.
- Artist creation (ADMIN-only) requires `ownerUserId`; Artist + OWNER membership are created in
  one DynamoDB transaction. Existing unowned artists fail closed for non-admins; backfill is an
  explicit, idempotent, dry-run-capable script.

### Song upload lifecycle (SongUpload)

- Upload initiation creates a persistent `SongUpload` resource keyed by the reserved `songId`
  (table `SongUploads`, `PK: songId`, GSI `state-expiry-index` on `state`/`expiresAtEpochSeconds`)
  — it no longer writes visible `Song` metadata. Only `COMPLETED` uploads produce a `Song` row,
  so pending/failed uploads are never searchable/streamable/likeable/addable to playlists.
- States: `INITIALIZING | PENDING | COMPLETING | COMPLETED | FAILED | ABORTED | EXPIRED`;
  transitions are conditional writes with version + lease.
- Integrity: expected content type/length and per-part SHA-256 checksums (Base64, 32 decoded
  bytes) are required at initiation and verified at confirmation.
- Staging/final key separation: client PUTs go to `pending/{songId}`; after verification the
  object is promoted (S3 copy) to the immutable final key `songs/{songId}` and staging is
  deleted. A still-valid staging URL can never overwrite the playable object.
- Confirmation is server-authoritative (keys/upload ID come from `SongUpload`), acquires a
  `PENDING → COMPLETING` lease, and recovers when S3 effects happened but the DynamoDB
  transaction did not.
- Replayed initiation returns the same logical upload with freshly generated signed URLs;
  presigned URLs are never persisted.
- Cleanup uses the state-expiry GSI with conditional claims (bounded queries, safe for multiple
  instances) plus documented S3 lifecycle rules (abort incomplete multipart after 1 day, expire
  `pending/` after 2 days, never expire `songs/`).

### Distributed rate limiting (Redis Lua token bucket)

- The previous in-memory fixed-window limiter (`FixedWindowRateLimiter` + flat
  `rate-limit.*` contract) is **replaced**, not paralleled: one canonical limiter path remains.
- Implementation: atomic Lua token bucket executed through `StringRedisTemplate` +
  `DefaultRedisScript` (existing Spring Data Redis/Lettuce graph — no new Maven coordinate),
  refilled using Redis server `TIME`.
- Redis keys contain only HMAC digests (secret `RATE_LIMIT_KEY_SECRET`, separate from the JWT
  secret); raw e-mail/IP/user identifiers never reach Redis or metrics.
- Client address defaults to `request.getRemoteAddr()`; forwarded headers are honoured only from
  configured trusted-proxy CIDRs (production wildcard trust rejected).
- Failure modes: register/authenticate/upload policies fail closed (canonical 503); search fails
  open with warning + metric. Rate limiting runs before Argon2 work and before idempotency
  claims; replays still consume capacity.
- Related consistency fix required by this decision: the auth cache must degrade to direct
  DynamoDB lookup when Redis is unavailable so Redis stays non-gating for readiness.

### Production exposure

- Swagger UI and `/v3/api-docs` disabled in `prod`.
- Management interface moves to dedicated port **9090** (`MANAGEMENT_SERVER_PORT`) — 8081/8082
  are the blue/green business ports in the on-premises topology. Health-only exposure, probes
  enabled, details never; `info`/`metrics` stay off the public business listener; 9090 must not
  be host-published/internet-accessible (network isolation enforced by the deployment platform).

### Schema rollout / rollback order (P0 tables)

All three tables are additive — no existing table changes shape — so rollout is low-risk:

1. `ArtistAccounts` (needed first: ownership precedes album/upload idempotency scope).
2. `IdempotencyRecords` (+ TTL enablement; adapter proven before any endpoint attaches).
3. `SongUploads` (+ `state-expiry-index` GSI) together with the initiation/confirmation refactor.

Each table ships in the same change set as its mapping/config, `scripts/seed-localstack.sh`
provisioning, README schema block and `AbstractIntegrationTest` provisioning. Rollback order is
reverse adoption order (stop writing → verify no in-flight records → drop table); because all
writes are conditional creates keyed by new PKs, dropping a table never corrupts existing
domain data.