# API Design Excellence P0 — Backlog
## Safety, Idempotency & Abuse Protection

**Priority:** P0  
**All stories:** Must  
**Companions:** `api-design-excellence-p0-spec.md` · `api-design-excellence-p0-implementation-sequence.md`

**Execution status:** started 2026-08-23 from baseline `04f42f4`; Phase A (Step 0) complete —
see `p0-action-plan.md` and `p0-baseline-inventory.md`. Story checkboxes below are updated per phase.

---

## Epic outcome

The current `/api/v1` API converges safely under retries, enforces User ↔ Artist authorization, protects public/cost-sensitive routes with distributed rate limits, validates upload integrity and returns protocol-correct errors without exposing operational details.

---

## Story map

```text
FOUNDATION
S0   Baseline, locked ADRs and dependency/schema plan
S1   Input normalization, limits and protocol-error primitives

OWNERSHIP
S2   ArtistAccount domain + persistence + provisioning
S3   Artist owner assignment and migration/backfill
S4   Application-level album/upload access policy

NATURALLY IDEMPOTENT STATE
S5   Replace like toggle with PUT/DELETE
S6   Make playlist membership PUT/DELETE idempotent

DURABLE IDEMPOTENCY
S7   Idempotency record model/table/TTL
S8   Claim, lease, replay and crash-recovery coordinator
S9   Idempotent registration without stored JWT
S10  Idempotent artist creation
S11  Idempotent album creation
S12  Idempotent playlist creation

UPLOAD LIFECYCLE
S13  SongUpload model/table/state transitions
S14  Idempotent upload initiation + URL regeneration
S15  Replay-safe confirmation + integrity + staging promotion
S16  Upload cleanup/reconciliation + S3 lifecycle defence

ABUSE PROTECTION
S17  Redis Lua token-bucket core + safe keying
S18  Register/auth rate-limit policies
S19  Upload/search rate-limit policies + headers

HARDENING
S20  Exact 400/405/413/415/429/503 mapping
S21  Production Swagger/Actuator lockdown
S22  Metrics, audit logs and sensitive-data redaction

VERIFICATION & DELIVERY
S23  Full retry/concurrency/failure integration matrix
S24  OpenAPI, documentation, provisioning and release sync
```

---

## S0 — Baseline, ADRs and implementation preconditions

**Goal:** Do not build safety mechanisms on an unknown/red baseline.

### Work

- verify current `main` and public CI are green;
- run fast tests and relevant current ITs;
- record locked decisions from the technical specification in `docs/data-model-decisions.md` or focused ADR sections;
- confirm existing Spring Data Redis/Lettuce classes support `StringRedisTemplate`, Lua script execution and `GenericContainer` tests;
- obtain explicit approval before any Maven coordinate change;
- inventory current tables, routes, security matchers and upload flow;
- define schema rollout/rollback order for three new DynamoDB tables;
- confirm no tracked analysis/source document is missing.

### Acceptance

- [ ] Baseline commands pass or blockers are reported before feature edits.
- [ ] Idempotency, ownership, SongUpload and Redis limiter decisions are checked in.
- [ ] No unresolved “or equivalent/or Redis + DynamoDB/or route” choice remains.
- [ ] Dependency approach is approved and recorded.

---

## S1 — Input normalization, limits and protocol primitives

### Work

- implement the exact bounds from the specification at DTO and domain/application boundaries;
- normalize names/e-mail/country/content type/query deterministically;
- add `Idempotency-Key` value validation support;
- remove ignored `songTitles` and empty `PlaylistDetailsResponse`;
- add typed exceptions needed for payload-too-large, active idempotency, limiter unavailable and integrity failure;
- add request body maximum enforcement without logging bodies.

### Acceptance

- [ ] Every specified boundary has a unit test at min/max/outside values.
- [ ] Normalization is deterministic and used by idempotency fingerprinting.
- [ ] Oversized declared audio maps to the selected 413 contract.
- [ ] No password is silently trimmed.

---

## S2 — ArtistAccount domain, port and DynamoDB persistence

### Work

- create `ArtistAccount` and `ArtistPermission` pure domain models;
- add `ArtistAccountRepository` or focused management port;
- create `ArtistAccounts` DynamoDB table mapping and adapter;
- implement conditional create/find by `(artistId,userId)`;
- add LocalStack and integration-test provisioning.

### Acceptance

- [ ] OWNER/MANAGER membership is persisted and loaded against LocalStack.
- [ ] Duplicate assignment is idempotent or returns a defined conflict.
- [ ] No framework/cloud type enters domain/application.

---

## S3 — Artist owner assignment and existing-data backfill

### Work

- add required `ownerUserId` to current admin artist-creation contract;
- validate target user exists and has `ROLE_ARTIST`;
- atomically create Artist + OWNER membership;
- make artist creation compatible with the future idempotency reserved ID;
- create dry-run-capable idempotent backfill script/runbook;
- define unowned-artist fail-closed behaviour.

### Acceptance

- [ ] New artist always has one explicit OWNER.
- [ ] Invalid/non-artist owner is rejected.
- [ ] Artist and owner assignment cannot partially persist.
- [ ] Existing unowned artist is non-admin inaccessible until explicit backfill.
- [ ] Backfill repeated execution does not create inconsistent records.

---

## S4 — Album/upload access policy

### Work

- implement application-level `ArtistAccessPolicy` using domain repositories/models;
- include immutable actor ID in album/upload commands;
- allow ADMIN override explicitly;
- require ARTIST role plus OWNER/MANAGER association otherwise;
- enforce on album create, upload initiate and upload confirm;
- update `SecurityConfig` as defence in depth;
- add safe audit logs/metrics.

### Acceptance

- [ ] OWNER and MANAGER artist accounts can act.
- [ ] Unrelated `ROLE_ARTIST` receives 403.
- [ ] ADMIN override succeeds and is auditable.
- [ ] Role alone and association alone follow the exact policy.

---

## S5 — Idempotent like PUT/DELETE

### Work

- remove `POST /api/v1/likes/toggle`;
- add specified PUT/DELETE routes;
- parse lowercase entity type and UUID;
- replace toggle repository API with create-if-absent/delete-if-present;
- preserve original `likedAt` on repeated PUT;
- return 204, no mutation count;
- update security matchers, mapper/DTO cleanup and docs.

### Acceptance

- [ ] Sequential/concurrent PUT creates one record.
- [ ] Repeated DELETE is successful and leaves no record.
- [ ] Missing target returns 404.
- [ ] Old toggle route cannot mutate state.
- [ ] Unit + LocalStack integration + HTTP tests pass.

---

## S6 — Idempotent playlist membership

### Work

- change add-song mapping from POST to PUT;
- implement unique SongId domain membership;
- no write/version bump when desired state already exists;
- reconcile same-song optimistic conflicts by reload;
- make DELETE return 204 and no-op when absent;
- retain 409 for genuine different-change stale conflicts.

### Acceptance

- [ ] Same-song sequential retry produces one entry.
- [ ] N concurrent same-song PUTs converge to one entry and successful desired-state responses.
- [ ] Repeated DELETE returns 204 without a write.
- [ ] Owner/non-owner and max-100-unique-song rules remain green.

---

## S7 — Idempotency record/table/TTL

### Work

- create pure idempotency models and repository port;
- create `IdempotencyRecords` document/table/adapter;
- implement conditional create/update and logical expiry;
- enable/document DynamoDB TTL;
- persist only digested scope/lease and safe metadata;
- update all provisioning sources.

### Acceptance

- [ ] Table contract matches the specification exactly.
- [ ] Expired record can be replaced before physical TTL deletion.
- [ ] Raw key/body/JWT/signed URL/e-mail never persists.
- [ ] Adapter IT covers claim/conflict/conditional transition/expiry.

---

## S8 — Idempotency coordinator and crash recovery

### Work

- implement canonical request fingerprint versioning;
- authenticated/anonymous scope generation;
- claim, active lease, takeover, completion and final-failure handling;
- stable resource ID reservation;
- operation result reference/safe snapshot support;
- metrics and redacted diagnostics;
- fault injection seam for before/after resource-write recovery tests.

### Acceptance

- [ ] Same request replays; different request conflicts.
- [ ] N concurrent claimers execute one logical operation.
- [ ] Active lease returns 409 + Retry-After.
- [ ] Expired lease takeover preserves resource ID.
- [ ] Crash after resource write completes on retry without duplicate resource.
- [ ] Idempotency replay header is correct.

---

## S9 — Idempotent registration

### Work

- require header on current `/api/v1/auth/register` route;
- use anonymous registration scope;
- reserve stable UserId;
- recover conditional user/e-mail transaction after crash;
- never persist JWT in idempotency record;
- mint fresh JWT from same user result on replay;
- apply register rate limit before idempotency/business work.

### Acceptance

- [ ] Same key/request creates one user and each successful replay returns a valid fresh JWT.
- [ ] Same key/different request returns 409.
- [ ] Different keys cannot bypass e-mail uniqueness.
- [ ] Crash-recovery IT proves one user/e-mail reservation.

---

## S10 — Idempotent artist creation

### Work

- require header;
- fingerprint name + ownerUserId;
- reserve ArtistId;
- recover atomic Artist + OWNER creation;
- persist safe original response snapshot;
- re-evaluate ADMIN authorization before replay.

### Acceptance

- [ ] Same key produces one artist/owner membership and stable response.
- [ ] Different body conflicts.
- [ ] Crash between transaction and idempotency completion recovers.

---

## S11 — Idempotent album creation

### Work

- require header;
- include actor, normalized album fields and artistId in scope/fingerprint;
- authorize owner/manager/admin before claim/replay;
- reserve AlbumId and use conditional create;
- remove ignored songTitles input.

### Acceptance

- [ ] One album per idempotent operation.
- [ ] Unauthorized actor cannot create or replay.
- [ ] Crash after album write recovers same album.

---

## S12 — Idempotent playlist creation

### Work

- require header;
- reserve PlaylistId;
- include immutable owner and normalized name in fingerprint;
- preserve per-user limit and uniqueness semantics;
- recover after conditional create;
- store safe creation snapshot.

### Acceptance

- [ ] Same key creates one playlist.
- [ ] Different key still counts as another playlist.
- [ ] Replay does not consume playlist limit again.
- [ ] Crash recovery and limit interaction are integration-tested.

---

## S13 — SongUpload model, table and state transitions

### Work

- create pure `SongUpload`/state models;
- create `SongUploads` table and `state-expiry-index`;
- store expected size/type/checksums, actor, album/artist and keys;
- implement conditional create/version/lease transitions;
- stop writing Song metadata at initiation;
- ensure only completed Song table rows are visible.

### Acceptance

- [ ] Legal/illegal transitions are unit-tested.
- [ ] Adapter IT proves conditional acquisition and stale-owner rejection.
- [ ] Pending upload cannot be fetched/searched/streamed/liked/added to playlist.

---

## S14 — Idempotent upload initiation and URL regeneration

### Work

- require header and authorized actor;
- validate exact checksum list;
- reserve same song/upload/staging/final keys through idempotency;
- recover/create multipart ID once logically;
- regenerate presigned URLs on replay;
- include required signed headers in response;
- do not store signed URLs in DynamoDB/logs.

### Acceptance

- [ ] Replay before/after URL expiry returns usable URLs for same upload ID/song ID.
- [ ] Concurrent initiation creates one logical upload.
- [ ] Multipart crash recovery reuses/cleans duplicate S3 upload attempts.
- [ ] Request hash includes all integrity metadata.

---

## S15 — Replay-safe confirmation, integrity and final promotion

### Work

- make confirmation server-authoritative for keys/upload ID;
- acquire `COMPLETING` lease;
- complete/recover S3 multipart;
- verify expected size/type/checksums/parts;
- promote `pending/{songId}` to `songs/{songId}`;
- transactionally create Song + mark upload completed;
- recover after S3 effect/Dynamo crash;
- completed replay returns same Song.

### Acceptance

- [ ] Concurrent confirmations make one logical completion.
- [ ] Repeated completed confirmation returns 200.
- [ ] Integrity mismatch creates no Song and cleans object.
- [ ] Final downloadable bytes/content type match.
- [ ] Still-valid staging URL cannot overwrite final playable key.

---

## S16 — Upload cleanup and reconciliation

### Work

- implement bounded `state-expiry-index` cleanup;
- conditionally claim expired uploads;
- abort multipart/delete staging/mark terminal;
- document S3 lifecycle rules;
- make multiple cleaner instances safe;
- add metrics/runbook.

### Acceptance

- [ ] Cleanup never scans full table.
- [ ] Repeated/concurrent cleanup is idempotent.
- [ ] Incomplete multipart/staging defence is documented and smoke-tested where LocalStack supports it.

---

## S17 — Redis token-bucket core

### Work

- add manual synchronous Redis template/config using existing dependency graph;
- implement atomic Lua token bucket using Redis time;
- HMAC subject keys with separate secret;
- implement trusted client-address resolver;
- add policy configuration validation;
- add dedicated Redis Testcontainer support;
- add low-cardinality metrics.

### Acceptance

- [ ] Atomic N/N+1 and refill tests pass against Redis.
- [ ] Two limiter clients share state.
- [ ] No PII/raw IP in Redis keys or metrics.
- [ ] Untrusted X-Forwarded-For cannot bypass limiter.

---

## S18 — Register/auth policies

### Work

- apply IP-wide and IP+normalized-e-mail buckets;
- ensure limit check occurs before Argon2/authentication work;
- implement fail-closed 503 on Redis outage;
- return success and 429 headers;
- exempt only documented internal/preflight paths.

### Acceptance

- [ ] Configured thresholds/reset work without long sleeps.
- [ ] Generic auth error behaviour remains non-enumerating.
- [ ] Redis outage does not silently disable auth protection.

---

## S19 — Upload/search policies

### Work

- apply user and user+album limits to initiate/confirm;
- apply authenticated user/fallback trusted-IP search limit;
- upload fails closed, search fails open with metric/warn;
- ensure idempotent replays consume capacity;
- propagate headers consistently.

### Acceptance

- [ ] Per-policy isolation and headers pass E2E.
- [ ] Search remains available during Redis outage and emits backend-error metric.
- [ ] Upload does not proceed during fail-closed limiter outage.

---

## S20 — Exact protocol error mapping

### Work

- handle malformed JSON, invalid enum/UUID, missing parameter/header, 405, 413, 415, idempotency conflicts, 429 and limiter 503;
- preserve `Allow`, Retry-After and rate-limit headers;
- ensure filter and MVC errors use canonical envelope;
- keep generic 500 only for unexpected failures.

### Acceptance

- [ ] Every row in specification protocol table has an E2E test.
- [ ] No listed client/protocol error returns 500.

---

## S21 — Production Swagger/Actuator lockdown

### Work

- disable Swagger/API docs in prod;
- configure management port, health-only exposure, probes and no details;
- prevent business port exposure of metrics/info;
- document network-isolation dependency;
- add prod-profile tests.

### Acceptance

- [ ] Swagger unavailable in prod.
- [ ] Normal business user cannot access metrics/details.
- [ ] Management liveness/readiness remain usable without sensitive details.

---

## S22 — Observability and sensitive-data controls

### Work

- implement required metrics;
- add sampled safe audit/security logs;
- centralize redaction rules;
- verify no high-cardinality/sensitive tags;
- add log-capture tests for critical paths where practical.

### Acceptance

- [ ] Required metric names/outcomes exist.
- [ ] No raw key/e-mail/IP/JWT/signed URL/password in records/logs/metrics.

---

## S23 — Full acceptance and fault-injection suite

### Work

- implement every scenario in the specification acceptance matrix;
- include injected crash points around resource write/idempotency completion and S3/Dynamo boundaries;
- include LocalStack and Redis real-adapter tests;
- keep tests bounded/order-independent/no blind retry;
- run complete CI mirror and boundary check.

### Acceptance

- [ ] Matrix is traceable requirement → test → CI step.
- [ ] All full-gate commands pass.
- [ ] JaCoCo/SpotBugs are not bypassed and OWASP findings are reviewed.

---

## S24 — Contract, provisioning, migration and documentation sync

### Work

Update:

- README routes, headers, schemas, setup and current state;
- CHANGELOG breaking method/route changes;
- AGENTS rules, commands and debt;
- testing playbook suite/gaps;
- coding standards;
- data-model decisions;
- LocalStack/test/prod provisioning;
- minimal OpenAPI operations, headers, responses and upload examples;
- artist-account backfill runbook;
- upload cleanup/reconciliation runbook;
- production env contract.

### Acceptance

- [ ] No document mentions toggle, POST membership or placeholder-song initiation as current.
- [ ] A new operator can provision all required backing resources from committed instructions.
- [ ] API consumers can implement retries, upload checksums and rate-limit handling from docs alone.

---

## Epic Definition of Done

- [ ] S0–S24 complete.
- [ ] Every protected operation is safe under sequential retry, concurrent duplicate delivery and specified crash points.
- [ ] Artist-resource authorization is enforced in application and HTTP layers.
- [ ] Public/cost-sensitive routes are protected by real Redis state.
- [ ] Pending/failed uploads are never playable catalog songs.
- [ ] All specified protocol errors return exact 4xx/503 contracts.
- [ ] Production operational exposure is fail-safe.
- [ ] Full CI mirror and boundary check pass.
- [ ] Schema, API, tests, runbooks and status documentation agree.