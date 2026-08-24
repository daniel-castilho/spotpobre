# Execution Authorization — Spotpobre API Design Excellence P0
## Safety, Idempotency, Abuse Protection and Production Hardening

You are authorized to execute the complete **API Design Excellence P0** epic in the current Spotpobre repository.

This is an implementation authorization, not a request for another generic approval. Execute the work in small, reviewable local commits and stop only at the explicit stop conditions below.

All identifiers, code, comments, Javadocs, logs, commit messages, documentation and test names must remain in **English**.

---

## 1. Repository baseline and source of truth

The latest public `main` reviewed for this authorization is:

```text
04f42f4466f55ee35810152940b412726b6f80f1
docs: finalize platform decision - on-premises forever, LocalStack is production
```

The repository has already released:

```text
v0.10.0 — durable idempotent endpoint wave
v0.11.0 — catalog cursor pagination
v0.12.0 — password recovery and informational e-mail verification
v0.13.0 — 60% line / 60% branch coverage floor
```

The latest public CI run reviewed is CI #77 on `04f42f4`:

```text
build: success
image: success
runtime-smoke: success
performance: success, but currently consultative/non-blocking
```

CodeQL and automatic dependency submission also pass.

Use the current repository as the source of truth. If your local `HEAD` is newer, inspect it and preserve the intent below. Do not reset to `04f42f4` merely because this prompt names it.

### Worktree safety

Before editing:

```bash
git status --short
git log -1 --format='%H%n%s'
```

Do not run any command that discards another agent's or the human's work:

```text
git reset --hard
git clean -fd
git checkout -- .
git restore .
```

Do not silently stash, delete, overwrite or mix unrelated worktree changes. If unrelated changes are present, isolate the P0 work in a separate worktree or stop and report the paths and ownership risk.

At the time of this authorization, a separate local checkout had uncommitted changes in deployment/seed scripts and a deletion of `IdempotencyMetrics.java`. If those changes exist in your worktree, preserve them; do not assume they are disposable.

You are not authorized to push, create a remote release, or alter GitHub repository settings. Local commits are allowed. Do not create a release tag unless the human explicitly asks.

---

## 2. Objective and non-negotiable outcome

Complete every P0 story S0–S24 in:

```text
tasks/api-design-excellence-p0-spec.md
tasks/api-design-excellence-p0-backlog.md
tasks/api-design-excellence-p0-implementation-sequence.md
```

The objective is not merely a green Maven command. The completed system must:

1. converge safely under sequential retries, concurrent duplicates and specified crash points;
2. enforce artist-resource authorization in the application layer and at the HTTP boundary;
3. prevent pending or failed uploads from becoming playable catalog songs;
4. protect public and cost-sensitive operations with a real distributed limiter;
5. return exact protocol-level error contracts;
6. keep production operational exposure private and fail-safe;
7. avoid secrets, PII, raw tokens and expiring URLs in logs, metrics and durable protocol records;
8. prove the above with bounded unit, adapter, integration, concurrency and fault-injection tests;
9. keep schemas, provisioning, API documentation, runbooks and status files synchronized with reality.

Do not mark a story complete because a class with the expected name exists. Every story requires implementation evidence and a named test or verification command.

---

## 3. Current implementation inventory — do not duplicate or assume completion

The repository already contains real implementations for some P0 areas:

### Existing and to be audited, preserved and repaired where necessary

- `ArtistAccount` ownership model and `ArtistAccounts` persistence;
- artist ownership/backfill scripts;
- desired-state like `PUT`/`DELETE` operations;
- desired-state playlist membership operations;
- `IdempotencyRecords` and the claim/lease coordinator;
- idempotent registration, artist, album, playlist and upload-initiation endpoints;
- account-token storage for password recovery and e-mail verification;
- password recovery and informational e-mail verification endpoints;
- artist membership authorization;
- basic request-size filtering;
- basic in-memory fixed-window rate limiting;
- error envelope and global exception handling;
- CI quality/security jobs and runtime smoke.

### Known incomplete or incorrect areas that are in P0 scope

Do not assume the following are complete merely because the README or CHANGELOG claims related functionality:

1. There is no `SongUploads` table/model/state machine in the production source. Upload initiation still writes visible `Song` metadata before confirmation.
2. There is no production Redis Lua token-bucket limiter. `FixedWindowRateLimiter` is still in-memory and per-instance.
3. `RateLimitFilter` trusts `X-Forwarded-For` without a trusted-proxy CIDR policy.
4. The configured rate-limit paths still cover only registration and authentication; upload/search/recovery/verification policies are not fully wired.
5. Rate-limit success/rejection headers are not implemented consistently.
6. `management.server.port=9090` is not implemented; health and operational endpoints remain on business listeners.
7. Swagger/API docs are not disabled by the production profile.
8. The production compose file enables LocalStack DynamoDB/S3 but does not enable SES, despite the account-lifecycle e-mail feature.
9. Production e-mail/app configuration is not fully represented in `application-prod.yaml`, `deploy/.env.example` or `ProdConfigValidator`.
10. Application services directly reference infrastructure `EmailProperties` through fully-qualified names, bypassing the current import-only boundary check.
11. `AuthenticationController` directly depends on infrastructure `JwtService`, contrary to the controller/inbound-port boundary rule.
12. Idempotent services ignore the boolean result of `IdempotencyCoordinator.completeClaim(...)` and may return success after losing a lease.
13. Password reset does not evict the authentication cache, revoke existing JWTs or invalidate older password-reset tokens.
14. Current account-token and user updates are separate DynamoDB writes; do not describe them as atomic without implementing a DynamoDB transaction or an explicitly safe state protocol.
15. PII/raw identifiers appear in several logs, including e-mail addresses, cache keys and storage keys.
16. The P0 task documents remain largely written as pre-implementation checklists and contain stale management-port/legacy statements. Update them to an honest as-built/deviation status.

Treat these as real work items, not as documentation-only observations.

---

## 4. Platform constraint and production-durability gate

The current business/platform decision is:

```text
on-premises bare metal
Docker Compose
NGINX blue/green
LocalStack for DynamoDB/S3/SES
Redis on the same host
no migration to real AWS
```

Do not silently migrate the project to AWS or reopen this business decision.

However, this decision does not waive production durability requirements. The official LocalStack documentation describes the emulator as ephemeral by default and documents persistence separately. The current compose files use `localstack/localstack:3.2`, mount a volume, but do not configure a verified durable persistence/backup/restore strategy.

Therefore:

- do not claim production excellence merely because a LocalStack container restarts;
- do not claim a bind mount is a backup;
- do not mark this P0 complete without a documented durability decision;
- if the selected LocalStack edition cannot provide an approved durable production mode, stop and report a P0 GO/NO-GO blocker instead of pretending the emulator is a durable database;
- within the on-premises constraint, implement the strongest feasible solution: supported persistence, external durable volumes, encryption, automated backups, restore verification, version compatibility checks, RPO/RTO and host-failure runbook;
- if a production-grade replacement for the data plane is required, present it as a human decision with exact migration impact. Do not introduce it unilaterally.

LocalStack remains acceptable for CI and development. Production use requires explicit operational evidence.

---

## 5. Locked runtime topology

Do not use the stale P0 document value `8081` for management. The current topology is:

```text
NGINX/load balancer public business port: 8080
blue application business port:          8081
green application business port:         8082
internal management port:                9090
```

Required health endpoints:

```text
http://localhost:9090/actuator/health/liveness
http://localhost:9090/actuator/health/readiness
```

Port 9090 must be internal only. It must not be published to the host or internet by the production compose topology.

Update every stale reference in P0/runtime documents to 9090 while preserving 8080/8081/8082 business-port semantics.

---

## 6. P0 story requirements

## S0 — Baseline, decisions and preconditions

Before feature edits:

1. confirm the actual `HEAD`, status and remote CI state;
2. run the fast unit suite and relevant integration tests when Docker is available;
3. inventory current routes, security matchers, tables, GSIs, TTLs, rate limiter, upload path and production manifests;
4. compare code against the P0 spec instead of trusting current status prose;
5. record every intentional deviation;
6. confirm no Maven dependency change is needed before editing `pom.xml`.

Acceptance:

- baseline evidence is recorded;
- no unrelated worktree changes are discarded;
- locked decisions are explicit;
- the P0 backlog is updated honestly.

## S1 — Input normalization, limits and protocol primitives

Implement and test deterministic boundaries:

- e-mail: trim and lowercase before lookup, uniqueness and fingerprinting;
- country: trim and uppercase;
- names/titles: defined trim/control-character/length policy;
- content type: trim and lowercase;
- search query: trim and bounded length;
- password: validate as received; never silently trim or normalize;
- `Idempotency-Key`: validate length, ASCII and allowed characters;
- cursor: size limit and malformed-input handling;
- request body: enforce the 64 KiB JSON limit without logging body content;
- audio size/type/checksum boundaries.

The normalized command, not raw HTTP text, must feed canonical idempotency hashing.

Do not rely only on annotations. Direct application-port calls must retain the relevant invariants.

## S2–S4 — Artist ownership and authorization

Audit and prove:

- `ArtistAccount` has `OWNER`/`MANAGER` semantics;
- `ArtistAccounts` uses `artistId` partition key and `userId` sort key;
- new artist creation atomically creates Artist + OWNER membership;
- owner user exists and has the required artist role;
- unrelated `ROLE_ARTIST` users are rejected;
- ADMIN override is explicit and auditable;
- album creation, upload initiation and upload confirmation all enforce the application policy;
- existing unowned artists fail closed for non-admins;
- backfill is dry-run capable, idempotent and documented.

Use defense-in-depth HTTP rules, but keep the actual authorization decision in the application layer.

## S5–S6 — Naturally idempotent state operations

Verify and preserve:

- old like toggle cannot mutate state;
- `PUT` like is a desired-state operation;
- `DELETE` like is a desired-state operation;
- repeated/concurrent operations converge;
- original `likedAt` is preserved;
- playlist membership uses `PUT`/`DELETE`;
- repeated same-song `PUT` produces one membership without a version bump;
- concurrent same-song operations converge to success when the desired state already exists;
- genuine stale different-change conflicts still return 409;
- owner authorization remains enforced.

## S7–S8 — Durable idempotency foundation

The only durable idempotency store is DynamoDB:

```text
Table: IdempotencyRecords
PK: scopeKey
TTL: expiresAtEpochSeconds
No GSI
```

Required semantics:

- canonical request fingerprint with explicit version;
- actor-scoped and anonymous scopes;
- raw key/body/JWT/e-mail/IP/signed URL/password never persisted;
- conditional `IN_PROGRESS` claim;
- stable preassigned resource ID;
- live lease;
- active foreign lease → 409 + positive capped `Retry-After`;
- expired lease takeover preserving resource ID;
- `COMPLETED` replay;
- deterministic `FAILED_FINAL` replay;
- logical expiry handled before eventual DynamoDB TTL deletion;
- conditional completion/failure/release tied to the current lease;
- every caller checks the boolean result of `completeClaim`, `failClaim` and `releaseClaim`;
- a caller that lost its lease must not send side effects or return a stale success response.

Add fault-injection seams around business write and idempotency completion.

## S9–S12 — Idempotent creation endpoints

Audit each endpoint separately:

```text
POST /api/v1/auth/register
POST /api/v1/artists
POST /api/v1/albums
POST /api/v1/playlists
```

For each:

- validation and authorization occur before claim when deterministic;
- same key + same normalized request replays the same logical resource;
- same key + different request returns 409;
- concurrent callers create one logical resource;
- crash after business write recovers the same resource;
- replay does not repeat quota consumption;
- registration replay mints a fresh JWT and never stores a JWT in the idempotency record;
- all response headers/statuses remain stable.

## S13 — SongUpload model and lifecycle

Implement the missing upload resource:

```text
Table: SongUploads
PK: songId
GSI: state-expiry-index
  PK: state
  SK: expiresAtEpochSeconds
```

Required states:

```text
INITIALIZING
PENDING
COMPLETING
COMPLETED
FAILED
ABORTED
EXPIRED
```

Persist at least:

```text
songId
albumId
artistId
actorUserId
expectedContentType
expectedContentLength
expected checksums
stagingStorageKey
finalStorageKey
multipartUploadId
state
version
lease information
createdAt
updatedAt
expiresAtEpochSeconds
```

Do not write visible `Song` metadata during initiation. Only a completed upload may create the playable `Song` row.

Pending/failed/expired uploads must not be:

- searchable;
- streamable;
- fetchable as catalog songs;
- likeable;
- addable to playlists.

Every state transition must be conditional and version/lease safe.

## S14 — Idempotent upload initiation

For:

```text
POST /api/v1/albums/{albumId}/songs
```

Require:

- `Idempotency-Key`;
- explicit actor and artist authorization;
- normalized title/content type;
- expected size and checksum metadata;
- stable reserved song/upload identity;
- storage staging key;
- one logical multipart upload;
- regenerated presigned URLs on replay/recovery;
- no persisted signed URLs;
- no raw signed URLs in logs or metrics;
- required signed headers returned to the client;
- cleanup if metadata/storage initialization fails.

A replay must target the same logical upload and storage key, never create an unrelated upload.

## S15 — Replay-safe upload confirmation and integrity

For:

```text
POST /api/v1/albums/{albumId}/songs/{songId}/confirm
```

Confirmation must:

- have its own idempotency/replay contract;
- be server-authoritative for song, album, storage key and multipart ID;
- acquire a `PENDING → COMPLETING` conditional lease;
- handle concurrent confirmations with one logical completion;
- complete or recover multipart effects safely;
- verify expected content length;
- verify content type;
- verify expected SHA-256/checksum evidence;
- validate part numbers, uniqueness, ordering and ETags;
- promote staging object to immutable final object key;
- delete staging object after successful promotion;
- transactionally or safely conditionally create the final Song and mark upload `COMPLETED`;
- recover after S3 completion/promotion but before DynamoDB completion;
- return the same completed resource on replay;
- never allow a still-valid staging URL to overwrite the playable final object.

Do not claim cross-service atomicity unless the implementation actually provides it or documents a tested compensation protocol.

## S16 — Upload cleanup and storage lifecycle

Implement bounded cleanup using `state-expiry-index`, not a full table scan.

Cleanup must:

- claim expired uploads conditionally;
- abort incomplete multipart uploads;
- delete staging objects;
- transition to a terminal state;
- be safe with multiple cleaner instances;
- be idempotent under retry;
- emit low-cardinality metrics;
- have an operator runbook;
- configure/document S3 lifecycle rules:
  - abort incomplete multipart uploads after one day;
  - expire `pending/` objects after two days;
  - never expire playable `songs/` objects through this rule.

## S17 — Redis Lua token bucket

Replace the existing in-memory `FixedWindowRateLimiter`. Do not run two limiter implementations.

Use only the existing dependency graph if possible:

```text
StringRedisTemplate
DefaultRedisScript or RedisScript
Lettuce
```

Required behavior:

- atomic Lua token bucket;
- Redis server `TIME`, not application wall-clock time;
- HMAC-derived keys;
- key secret separate from JWT secret:
  ```text
  RATE_LIMIT_KEY_SECRET
  ```
- no raw e-mail, IP or user ID in Redis keys;
- trusted-proxy CIDR resolver;
- arbitrary client-supplied `X-Forwarded-For` cannot bypass limits;
- positive configuration validation;
- dedicated Redis Testcontainers support;
- tests for N/N+1 and refill behavior;
- tests proving two limiter clients share state;
- tests proving outage behavior.

Do not use `.block()` on a reactive Redis API inside the servlet request path. Use deliberate synchronous access.

If an additional Maven coordinate is required, stop before editing the POM and request human approval with exact coordinate, version, rationale and alternatives.

## S18 — Register/auth rate-limit policies

Implement separate policies for:

- IP-wide registration;
- IP + normalized e-mail registration;
- IP-wide authentication;
- IP + normalized e-mail authentication.

Requirements:

- rate-limit check before Argon2 and before idempotency claim;
- register/auth limiter outage → canonical 503;
- unknown-user and wrong-password behavior remains non-enumerating;
- replays still consume configured capacity;
- success and rejection headers are consistent;
- tests do not rely on long sleeps.

## S19 — Upload/search policies

Implement:

- user + album limits for upload initiation/confirmation;
- authenticated user/fallback trusted-IP limits for search;
- upload limiter outage → fail closed with 503;
- search limiter outage → fail open with warning and metric;
- verification/recovery endpoints receive an explicit abuse policy;
- resend cooldown is not an unbounded per-instance memory map;
- concurrent resend requests cannot both pass a check-then-put race;
- limiter state has bounded memory and/or Redis authority.

## S20 — Exact protocol errors

Canonicalize and test:

```text
400 malformed/invalid request
401 missing/invalid authentication
403 authorization failure
404 unknown resource/token
405 unsupported method, preserving Allow
409 conflict/idempotency/concurrency
413 body or payload too large
415 unsupported media type
429 rate limit, with Retry-After where applicable
503 fail-closed dependency/limiter unavailable
500 unexpected server failure only
```

Requirements:

- filters and MVC errors use the same envelope;
- no broad `IllegalStateException` mapping hides unexpected server errors as 400;
- no internal storage key, token, stack trace or infrastructure detail is returned;
- `Allow`, `Retry-After` and `RateLimit-*` headers are preserved consistently;
- each protocol row has an E2E test.

## S21 — Production exposure lockdown

For `prod`:

```yaml
management:
  server:
    port: 9090
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

Required topology:

- 8080 is the only public LB/business entry point;
- 8081/8082 remain internal business fleet ports;
- 9090 is internal management only;
- Redis and LocalStack are not publicly host-published;
- NGINX must not expose actuator details through the public business listener;
- healthchecks use the internal 9090 endpoint;
- blue/green scripts use internal container health or `docker exec`, not a public health proxy;
- TLS termination, certificate rotation, HSTS and forwarded-header trust are explicitly configured or documented at the perimeter;
- production boot fails if the `prod` profile is not active;
- production profile tests prove Swagger is unavailable and non-probe operational endpoints are private.

## S22 — Metrics, audit logs and redaction

Implement low-cardinality observability for:

- idempotency claims/transitions;
- rate-limit decisions/outages;
- upload lifecycle transitions/cleanup;
- verification/recovery requests, sends, failures, expiry and replay;
- S3/DynamoDB/Redis/SES dependency failures;
- authorization denials and ADMIN overrides;
- deployment/readiness incidents.

Add correlation/request IDs through logs.

Never log or emit as metric labels:

```text
passwords
JWTs
JWT secrets
raw account tokens
full e-mail addresses
raw IP addresses
signed URLs
full storage keys when avoidable
request bodies
```

Use masking, short one-way correlation digests and bounded enum tags. Add log-capture tests for critical paths where practical.

Fix the current full e-mail logging in the account and SES services and the full cache-key logging in cache error handling.

## S23 — Full acceptance and fault injection

Create a traceability matrix:

```text
requirement → implementation → test → CI step → evidence
```

The matrix must include:

- sequential retry;
- concurrent duplicate requests;
- key reuse with a different payload;
- active and expired leases;
- crash after each business write;
- crash before/after idempotency completion;
- S3 multipart initialization failure;
- metadata write failure after S3 initialization;
- S3 completion/promotion failure;
- DynamoDB completion failure;
- duplicate upload confirmation;
- pending visibility restrictions;
- Redis outage under each policy;
- untrusted forwarded-IP spoofing;
- malformed JSON, 405, 413 and 415;
- production profile exposure;
- SES unavailable and token persistence failure;
- password reset cache/session behavior;
- data backup/restore behavior if the selected LocalStack platform remains.

Tests must be bounded, deterministic, order-independent and free of blind retries.

## S24 — Contract, provisioning, documentation, release and delivery sync

Synchronize:

```text
README.md
CHANGELOG.md
AGENTS.md
docs/testing-playbook.md
docs/coding-standards.md
docs/data-model-decisions.md
docs/lessons.md
docs/release-runbook.md
deploy/README.md
application-prod.yaml
application.yaml
scripts/seed-localstack.sh
AbstractIntegrationTest
production compose files
OpenAPI annotations/specification
P0 backlog/spec/implementation sequence
```

Correct all stale claims, including:

- `ToggleLikeService` references after the toggle was removed;
- old management port values;
- old Spring Boot versions in living documents;
- the obsolete statement that Redis outage breaks authentication;
- claims that SongUpload lifecycle is implemented when it is not;
- claims that production LocalStack is durable without evidence;
- release history ending at `v0.9.0` when newer releases exist.

Provisioning must be a single, repeatable, validated path. A seed script that merely skips an existing table without checking keys, GSIs or TTL is not sufficient for production schema safety.

### Account-lifecycle compatibility repair

Preserve the released `v0.12.0` API behavior while repairing its P0 defects:

- remove infrastructure-property types from application services;
- keep `emailVerifiedAt` legacy semantics (`missing` means unverified);
- keep login ungated for this release;
- keep verification confirmation as POST JSON;
- ensure production compose actually enables/configures SES;
- make resend throttling race-safe and bounded;
- add cache/session/token invalidation considerations to password recovery;
- do not add an unimplemented frontend link to e-mails.

### Supply-chain delivery integrity

The image scanned by CI must be the image deployed by operators.

Implement or document an immutable delivery path:

- build once;
- publish or transport the exact OCI image;
- deploy by immutable digest;
- verify digest before rollout;
- generate SBOM even when the vulnerability gate fails;
- pin all runtime base/service images by digest where feasible;
- correct the Docker source label to the real repository;
- align Dockerfile Maven execution with the project wrapper or explicitly justify the difference;
- add image provenance/signing/attestation when compatible with the approved on-prem registry path.

Do not use a second uncontrolled `docker build` on the production host as the only delivery mechanism.

---

## 7. Required implementation order

Execute in this order unless a documented dependency requires otherwise:

1. **Step 0 — audit and status truth**
   - preserve worktree;
   - confirm current CI;
   - update stale P0 status language;
   - record deviations and production-platform GO/NO-GO.
2. **Step 1 — boundary, normalization and protocol primitives**
   - fix application/infrastructure leaks;
   - normalize input;
   - complete typed errors and body limits.
3. **Step 2 — verify ownership/desired-state operations**
   - repair only actual gaps;
   - prove current behavior against LocalStack.
4. **Step 3 — durable idempotency audit and repair**
   - fix lost-lease handling and canonical hashing;
   - finish endpoint-by-endpoint acceptance.
5. **Step 4 — SongUpload lifecycle S13–S16**
   - implement the table, state machine, staging/final storage, integrity, cleanup and confirmation.
6. **Step 5 — Redis limiter S17–S19**
   - replace the in-memory authority;
   - implement policies, trusted proxy handling, HMAC keys and headers.
7. **Step 6 — protocol and production hardening S20–S22**
   - exact errors;
   - management port 9090;
   - Swagger lockdown;
   - network/TLS policy;
   - metrics/audit/redaction.
8. **Step 7 — fault injection, delivery and documentation S23–S24**
   - complete matrix;
   - exact artifact delivery;
   - production-shaped compose verification;
   - docs and release status.

Each step must end with:

- focused unit tests;
- relevant LocalStack/Redis adapter tests;
- relevant E2E/fault tests;
- boundary verification;
- schema/provisioning/doc updates;
- a short evidence report;
- a small local commit in English.

Do not mix unrelated refactors, dependency waves, frontend work or new product features into this epic.

---

## 8. Verification commands

Use the Maven Wrapper and Java 21:

```bash
./mvnw clean test
./mvnw test -Dtest='*IT' -DfailIfNoTests=false
./mvnw jacoco:check
./mvnw spotbugs:check
./mvnw dependency-check:check
./mvnw clean package
```

For the full lifecycle:

```bash
./mvnw clean verify
```

Run the exact boundary check, but strengthen it so fully-qualified references cannot bypass it:

```bash
grep -RInE 'com\.spotpobre\.backend\.infrastructure|software\.amazon|io\.awspring|org\.mapstruct|org\.springdoc|org\.springframework\.web' \
  src/main/java/com/spotpobre/backend/domain \
  src/main/java/com/spotpobre/backend/application
```

When Docker is available:

```bash
docker compose up -d
./scripts/seed-localstack.sh
# Run the relevant adapter/E2E/fault-injection tests.
docker compose down
```

The final report must identify which commands were actually run, which were unavailable because of the environment, and which public CI run proves the result. Never label a check green merely because it was skipped.

---

## 9. Commit policy

Use focused local commits such as:

```text
fix(p0): enforce normalized request boundaries
fix(p0): complete song upload lifecycle
fix(p0): replace in-memory limiter with redis token bucket
fix(p0): lock down production operational exposure
fix(p0): harden account lifecycle and delivery configuration
test(p0): add crash and concurrency acceptance matrix
docs(p0): synchronize as-built status and runbooks
```

Do not amend or rewrite commits belonging to another agent unless explicitly instructed.

Do not add a Maven coordinate without explicit human approval.

Do not push.

---

## 10. Stop conditions

Stop and report instead of guessing if:

1. an additional Maven dependency is required;
2. a new DynamoDB table or GSI is required outside the approved P0 schema;
3. existing data requires destructive migration;
4. the on-premises LocalStack platform cannot meet a required durability property;
5. another agent's uncommitted work would be touched;
6. a locked API or platform decision must change;
7. a security behavior cannot be made fail-safe with the current infrastructure;
8. a test cannot run because a required environment is absent — report it honestly;
9. the current public repository contains a newer conflicting decision;
10. implementation would require silently weakening an existing gate, hiding an error, excluding code from coverage or suppressing a vulnerability.

When stopping, provide:

```text
symptom
root cause
evidence
impact
smallest safe options
recommended option
exact human decision required
```

---

## 11. Final Definition of Done

Do not claim the P0 epic is complete until all are true:

- S0–S24 have explicit as-built status;
- no P0 acceptance item is silently unchecked or deferred;
- upload initiation/confirmation is safe and pending uploads are invisible to the catalog;
- Redis limiter is the single production authority;
- trusted forwarded-IP handling cannot be spoofed;
- critical limiter outages fail closed;
- all required protocol statuses and headers are tested;
- management/Swagger/metrics exposure matches the production contract;
- e-mail/recovery configuration works in the production-shaped compose stack;
- application/infrastructure boundaries pass a check that catches fully-qualified leaks;
- password reset/session/cache semantics are documented and tested;
- no raw sensitive values occur in durable records, Redis keys, logs or metrics;
- schema provisioning and rollback/backup procedures are repeatable;
- the exact artifact scanned by CI is the artifact deployed;
- CI build, image, runtime-smoke, integration, JaCoCo and SpotBugs gates pass;
- performance is either a measured, explicitly consultative baseline or a calibrated hard gate;
- fault-injection evidence exists for every specified cross-system crash point;
- README, CHANGELOG, AGENTS, ADRs, P0 documents, runbooks, OpenAPI and code agree;
- remaining non-P0 risks are explicitly listed with owner/priority and are not misrepresented as resolved.

**Start with Step 0: inspect, inventory and report the real P0 gap set before changing production code.**