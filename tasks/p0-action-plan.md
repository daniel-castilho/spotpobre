# P0 Action Plan — API Design Excellence Execution

**Purpose:** Concrete execution plan for the authorized P0 epic (`prompt-executar-todos-p0-spotpobre.md`),
mapping its Steps 0–7 onto the companion documents' Steps 0–11 and the audited reality of this repository.
**Baseline:** `04f42f4` (clean tree, origin/main, CI green incl. consultative `performance` job).
**Companions:** `api-design-excellence-p0-spec.md` · `api-design-excellence-p0-backlog.md` · `api-design-excellence-p0-implementation-sequence.md`
**Standing constraints:** no push, no tag, no new Maven coordinate without approval, English-only artifacts,
on-premises bare metal + LocalStack locked, never weaken an existing gate.

---

## 0. Pre-audit results (claims verified against code, not prose)

| §3 claim | Verdict | Evidence |
| :--- | :--- | :--- |
| No `SongUploads` model/state machine | CONFIRMED | `domain/song/model/` has no `SongUpload`; only command/result records |
| In-memory `FixedWindowRateLimiter` | CONFIRMED | `infrastructure/security/ratelimit/FixedWindowRateLimiter.java` |
| `RateLimitFilter` trusts XFF without CIDR | TO RE-VERIFY | filter located; policy inspection in Phase A |
| Rate-limit paths cover only register/auth | TO RE-VERIFY | Phase A inventory |
| Rate-limit headers inconsistent | TO RE-VERIFY | Phase A |
| No `management.server.port` | CONFIRMED | `application-prod.yaml` has neither `management:` nor `springdoc:` |
| Swagger not disabled in prod | CONFIRMED | same file |
| Prod compose lacks SES | CONFIRMED | `deploy/docker-compose.bluegreen.yml` → `SERVICES: dynamodb,s3` |
| Prod e-mail/app config incomplete | CONFIRMED | follows from previous two |
| `EmailProperties` FQN leak into application | CONFIRMED | `RegisterUserIdempotentService:56`, `RequestEmailVerificationResendService:37` |
| `AuthenticationController` → `JwtService` direct | CONFIRMED | import + field at lines 12/46 |
| `completeClaim` boolean ignored | CONFIRMED | all 6 call sites discard the result (register/artist/album/playlist/upload-initiate) |
| Password reset lacks eviction/revocation/token burn | TO RE-VERIFY | Phase A read of recovery flow |
| Account-token + user writes not atomic | CONFIRMED (design fact) | separate repository calls |
| PII/raw identifiers in logs | PARTIALLY CONFIRMED | known sites: verification e-mail failure log (full e-mail), cache-error handler (key); full sweep in Phase A |
| P0 docs stale | CONFIRMED | spec §11.2 still says management port "default 8081" — superseded by authorization §5 (**9090**) |

Existing foundations confirmed present: `IdempotencyRecords` stack (model/document/adapter/coordinator/metrics),
`ArtistAccount` ownership model, desired-state like PUT/DELETE, playlist membership PUT/DELETE,
`IdempotencyKey`/conflict exceptions, `MicrometerIdempotencyMetrics`.

**Spec deviation to record (locked):** management port = **9090 internal**, overriding spec §11.2's
"default 8081". Authorization §5 wins everywhere.

---

## 1. Durability GO/NO-GO dossier (researched; human decision required)

Official LocalStack position: ephemeral by default. Snapshot persistence (`PERSISTENCE=1`,
`SNAPSHOT_SAVE_STRATEGY=SCHEDULED|ON_SHUTDOWN|ON_REQUEST`, state under `/var/lib/localstack/state`,
version compatibility rules) is documented, but the snapshot engine targets the **Pro** image;
the Community edition lost its old persistence in 0.13.x. What Community *does* ship (≥ 1.3):
**manual Cloud Pod save/load to plain files** (`localstack pod save file://…` / `pod load file://…`).

Options for the production substrate (single host, small traffic):

| Option | Mechanism | RPO | Cost / risk |
| :--- | :--- | :--- | :--- |
| **A (recommended)** | Scheduled file-Pod snapshots (systemd timer/cron) to an external durable volume + tested restore-on-boot + host backup of snapshot dir | snapshot interval (e.g. 5–15 min) | Zero license cost; official Community CLI feature; RPO is bounded-not-zero; must pin image versions (compatibility rules) |
| B | LocalStack Pro license + `PERSISTENCE=1` | seconds (SCHEDULED flush) | Licensing decision; strongest emulator durability |
| C | Replace data plane (real DynamoDB-compatible store, e.g. self-hosted alternatives) | continuous | Out of P0 scope; migration impact assessment required; **cannot be introduced unilaterally** |

Plan proceeds under **Option A provisionally** (runbook + restore drill written against it);
the epic's DoD requires the human to explicitly pick A/B/C before "production durability" is claimed.
This does **not** block code phases — only the final production-durability claim.

---

## 2. Phase plan

### Phase A — Truth pass (auth Step 0 / S0) — commit `docs(p0)`
1. Run `./mvnw test` + representative ITs (`AuthenticationFlowIT,PlaylistFlowIT,ArtistSongFlowIT`) with Docker.
2. Inventory artifact: routes×methods×security matchers; tables/GSIs/TTLs; limiter config; upload flow;
   prod manifests (`docker-compose.bluegreen.yml`, `.env.example`, `ProdConfigValidator`).
3. Close the four TO-RE-VERIFY audit rows (XFF/CIDR, policy coverage, headers, reset hardening, PII sweep list).
4. Write durability dossier (section 1 above) into `docs/data-model-decisions.md`; flag decision request.
5. Update P0 docs status language; record the 9090 deviation in the spec's margin.
6. Dependency gate: confirm `spring-boot-starter-data-redis`, Testcontainers `GenericContainer`,
   AWS SDK checksum/copy APIs already in graph (expected yes via Redis cache usage) — no POM edit anticipated.
**Exit:** baseline green; decisions checked in; no unresolved "or/equivalent".

### Phase B — Boundary, normalization, protocol primitives (auth Step 1 / S1 + leaks #10, #11)
- Exact bounds table (spec §10) at DTO **and** application boundaries; deterministic normalizers
  (e-mail lower/trim, country upper, name/title trim+control-char policy, content-type lowercase, query trim≤100).
- `IdempotencyKey` hardening check: 16–128 `[A-Za-z0-9._:-]`.
- Typed exceptions: payload-too-large (413), limiter-unavailable (503), upload-integrity, active-idempotency
  (Retry-After carrier); handlers preserve custom headers on the canonical envelope; 64 KiB JSON body cap
  in `RequestSizeLimitFilter` without body logging.
- Remove `CreateAlbumRequest.songTitles` and empty `PlaylistDetailsResponse` if present as described.
- **Leak repairs:** application-owned e-mail settings type replacing FQN `EmailProperties`;
  `AuthenticationController` consumes an inbound token port instead of `JwtService`.
**Exit:** min/max/outside unit tests per field; password never trimmed; audio >500 MiB → typed 413.
Commits: `fix(p0): enforce normalized request boundaries` · `fix(p0): remove infrastructure leaks from application and web layers`

### Phase C — Ownership and desired-state proof (auth Step 2 / S2–S6)
- Audit `ArtistAccounts` (PK/SK), atomic Artist+OWNER transactional creation, `ownerUserId` contract,
  owner-must-exist-and-have-ROLE_ARTIST, fail-closed unowned artists, backfill dry-run/idempotence.
- Audit/complete `ArtistAccessPolicy` enforcement on album-create, upload-initiate, upload-confirm
  (ADMIN override explicit + audited).
- Prove like PUT/DELETE and membership PUT/DELETE convergence semantics incl. same-song conflict reload,
  no version bump on satisfied state, repeated DELETE no-op, max-100 unique songs.
**Exit:** LocalStack ITs for membership; concurrency tests converge; unrelated ARTIST → 403.
Commits: `fix(p0): close ownership policy gaps` · `test(p0): prove desired-state convergence`

### Phase D — Durable idempotency repair (auth Step 3 / S7–S12)
- Honor lease booleans at all 6 call sites: lost lease ⇒ no side effects, no stale success.
- Versioned canonical request hash (normalized command feeds fingerprint; `hashVersion` persisted).
- Logical expiry before physical TTL; expired-lease takeover preserving resource ID; capped positive `Retry-After`.
- Anonymous registration scope (no e-mail/IP in scope); fresh-JWT replay; no JWT/signed-URL in records.
- Endpoint passes: register → artist (+ownerUserId) → album → playlist, one commit each, tests first-class
  (concurrent N-claim, crash-after-business-write recovery, different-key uniqueness intact).
- Fault-injection seam around business write / completion.
**Exit:** `*Idempotency*IT` green; per-endpoint acceptance checklist ticked with named tests.
Commits: `fix(p0): honor lease loss in idempotent services` · `feat(p0): versioned canonical idempotency hashing` · `feat(p0): idempotent creation endpoint passes (register, artist, album, playlist)` (split as needed)

### Phase E — SongUpload lifecycle (auth Step 4 / S13–S16) — largest phase
- Pure `SongUpload` + `UploadState` (7 states) with exhaustive legal/illegal transition unit tests.
- `SongUploads` table + `state-expiry-index`; document/adapter with conditional create/version/lease transitions.
- Provisioning upgraded everywhere: `seed-localstack.sh` validates keys/GSI/TTL (not skip-if-exists),
  `AbstractIntegrationTest`, README, prod rollout order documented.
- Initiation rewrite: no visible `Song` row; reserved IDs; checksum list validation (Base64→32 B);
  multipart recover-by-exact-staging-key; `requiredHeaders` in response; URL regeneration on replay;
  compensation cleanup on metadata failure.
- Confirmation rewrite: server-authoritative keys/uploadId; `PENDING→COMPLETING` conditional lease;
  complete/recover multipart (HeadObject continuation); length/type/checksum/part verification;
  copy→verify→delete staging; transactional Song-put-if-absent + COMPLETED; integrity failure ⇒ FAILED + quarantine, no Song.
- Bounded cleanup via GSI (never full scan), multi-instance-safe conditional claims, metrics, operator runbook;
  S3 lifecycle rules (abort incomplete MPU 1 d, expire `pending/` 2 d, never `songs/`) — implemented if the
  emulator supports it, otherwise documented + scripted fallback (declared honestly).
- Early spike: LocalStack 3.2 multipart-checksum API parity (may reshape 8A/8B details).
- E2E visibility proofs: pending upload invisible to detail/search/stream/like/playlist.
**Exit:** `*SongUpload*IT,S3SongStorageAdapterIT,ArtistSongFlowIT,AlbumSongConsistencyIT` green; crash/concurrency matrix for upload.
Commits: `feat(p0): song upload lifecycle model and store` · `feat(p0): staging-only upload initiation` · `feat(p0): authoritative upload confirmation with integrity` · `feat(p0): expired upload cleanup and storage defence`

### Phase F — Redis token-bucket authority (auth Step 5 / S17–S19)
- Delete `FixedWindowRateLimiter` (single authority). Lua token bucket on `StringRedisTemplate`
  (synchronous), Redis `TIME`-driven refill, HMAC-SHA256 subject keys with new `RATE_LIMIT_KEY_SECRET`
  (separate from JWT secret; dev default documented non-production).
- Trusted-proxy CIDR resolver (`Forwarded` then `XFF`, IPv4/IPv6 normalization, prod rejects wildcard trust).
- Policies per spec §8.3 table; register/auth checks before Argon2 and before idempotency claim;
  upload fail-closed 503; search fail-open warn+metric; replays consume capacity.
- Consistent `RateLimit-Limit/Remaining/Reset` + `Retry-After`; canonical envelopes for 429/503.
- Resend cooldown rebuilt on the limiter (race-safe, bounded) replacing any per-instance map.
- Dedicated Redis `GenericContainer` test base (`redis:7-alpine` pinned); N/N+1, refill, two-client sharing,
  outage-per-policy, spoof-attempt tests without long sleeps.
- Prod wiring: `.env.example` + `ProdConfigValidator` require the secret; compose already runs Redis.
**Exit:** `*RateLimit*IT` green; untrusted forwarded header cannot bypass; no PII in keys.
Commits: `feat(p0): redis lua token bucket authority` · `feat(p0): trusted proxy resolution and rate-limit policies` · `fix(p0): race-safe bounded resend cooldown`

### Phase G — Protocol errors + production exposure (auth Step 6 / S20–S22 + #6,#7,#8,#9,#13)
- Protocol matrix rows as E2E tests (400 malformed/enum/UUID/missing/validation; 405 preserving `Allow`;
  413 JSON+audio; 415; 429 headers; 503 fail-closed; 500 strictly last resort; filter and MVC share the envelope).
- `application-prod.yaml`: `management.server.port: 9090`, health-only exposure, `show-details: never`,
  springdoc disabled; boot fails fast on incomplete prod contract (validator extension).
- Topology: only 8080 published; 8081/8082 internal fleets; 9090 internal management; healthchecks via
  container-internal/exec calls, never a public proxy; Redis/LocalStack unpublished from host;
  TLS/HSTS/perimeter trust documented in deploy docs.
- Production compose enables SES (`SERVICES=dynamodb,s3,ses`) + sender-identity bootstrap + e-mail env contract
  in `.env.example` and validator.
- Password reset: evict auth-cache entry, burn sibling unused reset tokens, revoke JWTs issued before
  password change (issuedAt vs passwordChangedAt check on the authenticated path) — documented + tested.
- Redaction sweep: mask e-mails (account/SES services), cache-key logs, storage-key shortening; correlation
  digest helper; metric families from spec §12 (extend existing idempotency metrics; add ratelimit,
  artist_access, song_upload families) with enum-only tags.
**Exit:** `ErrorHandlingFlowIT,*ProductionExposure*IT` green; prod-profile Swagger unavailable; no sensitive raw values in logs/metrics/records.
Commits: `fix(p0): exact protocol error contracts` · `feat(p0): lock down production operational exposure` · `feat(p0): enable ses in production-shaped stack` · `fix(p0): password reset invalidates sessions and tokens` · `fix(p0): redact pii from logs and metrics`

### Phase H — Acceptance matrix, delivery integrity, full sync (auth Step 7 / S23–S24)
- Traceability matrix requirement → implementation → test → CI step → evidence (committed under `tasks/`).
- Fault-injection ITs executed for every specified gap (business-write/completion, MPU create/persist,
  S3 complete/promote/Dynamo complete, Redis outage modes).
- Delivery integrity: build-once/deploy-by-digest procedure in `deploy/README.md`; digest-pinned runtime
  images where feasible; SBOM generated even when the Trivy gate fails (CI tweak); Dockerfile Maven↔wrapper
  alignment or explicit justification; corrected OCI source label; provenance/signing assessed against the
  on-prem registry reality (honestly deferred if tooling absent — flagged, not hidden).
- Seed/provisioning single validated path (schema asserts); rollback/backfill/cleanup procedures documented.
- Full living-doc sync (README, CHANGELOG, AGENTS, playbooks, standards, data-model decisions, lessons,
  runbooks, OpenAPI minimal notes) + P0 docs rewritten as-built; final report lists skipped-vs-run commands
  and remaining P1/P2 risks with owners.
**Exit:** full `./mvnw clean verify` green; strengthened boundary check (import **and** fully-qualified
reference regex) wired as a committed script and CI step; human-reviewable as-built report.
Commits: `test(p0): fault injection acceptance matrix` · `build(p0): immutable delivery path` · `docs(p0): as-built synchronization and final report`

---

## 3. Verification cadence (every phase)

```bash
./mvnw test                                  # after each vertical slice
./mvnw test -Dtest='<phase-relevant *IT>'    # when Docker applies
./mvnw jacoco:check && ./mvnw spotbugs:check # before each commit batch
scripts/check-boundaries.sh                  # new: import + FQN regex
```

Full mirror at the end of Phases D, F, H: `./mvnw clean verify` (+ `dependency-check:check` advisory review).
CI stays the public witness once the human resumes pushes; until then local evidence rules.

## 4. Human decision points

1. **Durability option A/B/C** (section 1) — required before the final DoD claim; plan drafts Option A.
2. Spec §11.2 8081→9090 recorded as locked deviation (authorization already decides; no action needed unless contested).
3. Any new Maven coordinate → stop-and-report (none anticipated: Redis driver, Testcontainers core, AWS SDK checksum APIs all present).

## 5. Risk watchlist

| Risk | Mitigation |
| :--- | :--- |
| LocalStack 3.2 multipart/checksum parity gaps | Phase E opening spike; fall back to client-computed checksum verification with declared deviation |
| S3 lifecycle API unsupported by emulator | Document + operator script fallback, marked honestly in runbook |
| JaCoCo 60/60 floor vs large new surface | Tests land with production code each slice; floor re-checked per commit batch |
| GSI eventual consistency affecting cleanup tests | Bounded polling assertions, never blind retries/sleeps |
| Scope creep (renaming routes, API v2, caching…) | Out-of-scope list in spec §2 is binding |

## 6. Explicitly out of scope

Push/tag/release actions · GitHub settings changes · AWS migration of any kind · frontend work ·
route renaming beyond spec · refresh-token product design · unilateral data-plane replacement.
