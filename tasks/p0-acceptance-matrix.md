# P0 Acceptance Matrix — as-built traceability (S23)

Requirement → implementation → test → evidence. Companion to `p0-action-plan.md`.
Evidence = commit + suite status at the time of the final P0 run (full `./mvnw clean verify`).

Legend: ✅ done · ⚠️ partial (declared deviation) · ⏸ deferred with owner.

## S0–S1 Truth pass, boundaries, leaks

| Requirement | Implementation | Test / evidence |
| :--- | :--- | :--- |
| §3 audit claims verified | `tasks/p0-baseline-inventory.md` (16 rows closed) | Phase A commits 047bf54/f8e7022 |
| Field bounds + normalization at both boundaries | DTO validation + `Normalization` in services | `RegisterUserIdempotentServiceTest`, `CreateArtist/Album/PlaylistIdempotentServiceTest` |
| Typed protocol exceptions (413/429/503/integrity) | `PayloadTooLargeException`, `TooManyRequestsException`, `RateLimiterUnavailableException`, `UploadIntegrityException` + handler map | `GlobalExceptionHandlerTest`; ErrorHandlingFlowIT |
| No FQN infra leakage into application/web | `EmailVerificationSettings` app-owned type; controllers consume `AuthTokenIssuer` port | boundary grep script; unit suites |

## S2–S6 Ownership & desired state

| Requirement | Implementation | Test / evidence |
| :--- | :--- | :--- |
| Membership grants fail closed; clock-injected | `GrantArtistAccountService` (+404 unknown user) | `GrantArtistAccountServiceTest` |
| Policy honoured E2E incl. ADMIN override | `RequireArtistAccess` chain | `ArtistAccountAccessFlowIT` (LocalStack) |
| Backfill idempotent/dry-run | `scripts/backfill-artist-accounts.sh --map` | smoke E2E (dry-run/apply/re-run) |
| Convergence: likes/membership/max-100 | domain rules + conditional writes | `LikeFlowIT`, `PlaylistFlowIT`, `PlaylistTest.MAX_SONGS` |

## S7–S12 Durable idempotency

| Requirement | Implementation | Test / evidence |
| :--- | :--- | :--- |
| Lease-loss booleans honoured (#12) | publish gate in all 5 creation services; `IdempotencyLeaseLostException`→503 Retry-After | per-service fault-injection tests (`failNextConditionalTransition`) |
| Register e-mail exactly-once publisher | send only after successful publish | `RegisterUserIdempotentServiceTest` |
| Versioned request hash persisted | `CanonicalRequestHash.CURRENT_VERSION` + `hashVersion` column | adapter read-back assertions |
| Logical expiry before TTL; takeover keeps resourceId; capped Retry-After | `IdempotencyCoordinator` | `IdempotencyCoordinatorTest` (incl. 16-race winner election) |
| Anonymous registration scope; fresh-JWT replay; no JWT/URL stored | scope factory + snapshot body only `{userId}` | register endpoint pass tests |
| Crash-after-write recovery | recovery branch re-executes under same reserved ID | crash tests per service |

## S13–S16 SongUpload lifecycle

| Requirement | Implementation | Test / evidence |
| :--- | :--- | :--- |
| Pure model + legal transitions | `SongUpload`/`SongUploadState` | `SongUploadTest` (7) |
| Conditional store + GSI cleanup scan | `SongUploadRepository` + DynamoDB adapter (CAS on lease tokens) | `DynamoDbSongUploadRepositoryAdapterIT` (6) |
| Staging-only initiation; no visible Song row | initiate rewrite; server-derived keys; insert-race convergence | initiate suite (9); `ArtistSongFlowIT` invisibility block |
| Authoritative confirmation | lease COMPLETING; size/type integrity gate; promote copy→verify→delete; transactional Song+COMPLETED | confirm suite (9); `S3SongStorageAdapterIT` |
| Bounded cleanup, multi-instance safe | `ReconcileExpiredUploadsService` via state-expiry-index; scheduler props | `ReconcileExpiredUploadsServiceTest` (4) |
| S3 lifecycle defence | seed script rules (MPU abort 1d, pending/ expire 2d); accepted by LocalStack 3.2 | seed output; runbook note |
| Checksum verification end-to-end | ⏸ deferred — emulator parity risk declared in plan §5; size+type verified today. Owner: human decision for P1 | risk watchlist |

## S17–S19 Rate limiting

| Requirement | Implementation | Test / evidence |
| :--- | :--- | :--- |
| Single authority, atomic buckets | `FixedWindowRateLimiter` deleted; `RedisTokenBucketLimiter` Lua w/ Redis TIME | `RedisTokenBucketLimiterIT` (4, pinned redis:7-alpine) |
| HMAC subject keys, separate secret | `RateLimitKeyEncoder` + RATE_LIMIT_KEY_SECRET required in prod | `ProdConfigValidatorTest`; .env.example |
| Trusted proxy CIDR resolution | `ClientAddressResolver` (Forwarded→XFF; v4/v6; spoof ignored) | `ClientAddressResolverTest` (7) |
| Policies per §8.3 table | two filters: anonymous pre-JWT, authenticated post-JWT; fail-closed/fail-open split | `RateLimitServiceTest` (6); `AnonymousRateLimitFilterTest` |
| Headers + canonical envelopes | RateLimit-Limit/Remaining/Reset + Retry-After; 503 never claims limit | `RateLimitFlowIT`, `RateLimitOutageFlowIT` |
| Race-safe resend cooldown | `VerificationResendCooldownPort` + Redis adapter replacing per-instance map | resend service suite |
| Isolation of unrelated ITs | limiter disabled in flow-IT base per §8.5 | full verify green after fix b5957ce |

## S20–S22 Protocol errors, exposure, redaction

| Requirement | Implementation | Test / evidence |
| :--- | :--- | :--- |
| Error matrix rows on canonical envelope | 400/405(Allow)/413/415/429/503 handlers; `ErrorHandlingFlowIT` 12 tests | `ErrorHandlingFlowIT` |
| Management plane internal-only 9090; health-only; no swagger | `application-prod.yaml` lockdown | config review + validator |
| Only 8080 published; deps unpublished; SES enabled | compose topology edits; seed verifies SES identity | deploy docs §1.7 |
| Password reset invalidation (#13) | burn siblings + cache evict + issuedAt-vs-passwordChangedAt gate | `ResetPasswordServiceTest`; `PasswordRecoveryFlowIT`; filter gate |
| PII redaction (#15) | `Redaction` helper; delivery/cache/storage log sites fixed | `RedactionTest` |
| Metric families ratelimit/song_upload/artist_access | ⚠️ partial — idempotency family existed; new families not yet added. Owner: P1 follow-up | — |

## S23–S24 Delivery integrity & sync

| Requirement | Status |
| :--- | :--- |
| Full `./mvnw clean verify` green at phase ends D/F/H | ✅ (unit 392+, ITs 90+, JaCoCo 60/60, SpotBugs 0) |
| Durability option A/B/C human decision | ⏸ OPEN — dossier in `docs/data-model-decisions.md`; Option A drafted; **blocks the production-durability claim only** |
| README/CHANGELOG/AGENTS as-built sync | ✅ this change set |
| SBOM-on-failure CI tweak; digest-pinned images; provenance signing | ⏸ deferred honestly — CI settings are outside local authority (no push/settings changes allowed by authorization); documented for the human |

## Known residual deviations (declared, none silent)

1. Upload checksum verification deferred (emulator parity risk was a planned fallback).
2. Metric families beyond idempotency not yet added (cleanup/ratelimit decisions log only).
3. Spec §11.2 "management default 8081" superseded by locked 9090 (recorded since Phase A).
4. Delivery-integrity CI items (SBOM-on-failure, digest pinning, provenance) remain pending
   human-side workflow edits; pushes have resumed so they are now actionable.
5. SongUpload state machine implements 4 states (PENDING_UPLOAD/COMPLETING/COMPLETED/ABORTED)
   instead of the spec's 7 names: INITIALIZING folds into the atomic insert of PENDING_UPLOAD,
   FAILED folds into ABORTED (quarantine path), EXPIRED is logical-only (expiry scan never
   needs a state write before cleanup). Declared mapping, not an omission of behavior.
6. SongUpload carries no numeric `version` attribute; lease-token compare-and-set serves the
   same optimistic-concurrency role on every transition.
7. Confirm response does not return a `requiredHeaders` list; presigned URLs embed all signed
   headers (Content-Type/Content-Length for single PUT), so clients need no extra contract.
8. RESOLVED: multipart part validation now rejects duplicate/out-of-order numbers and blank
   ETags before any lease or storage work (service guard + value-object boundary).
9. RESOLVED: RequestCorrelationFilter puts a per-request requestId into the MDC, rendered by
   the console pattern and emitted as a JSON field; incoming X-Request-Id is validated and
   echoed.
10. RESOLVED: ProductionExposureFlowIT boots the prod profile against Testcontainers and proves
    Swagger/api-docs unavailable, actuator absent from the business listener, health-only
    management port with show-details never, and metrics/env private (401/404).
11. "Boot fails if prod profile not active" remains enforced by the deployment contract
    (.env sets SPRING_PROFILES_ACTIVE=prod), not by application code - declared control split.

## Stop-condition disclosures

- A `userId-index` GSI was added to the existing `AccountTokens` table (seed + IT base +
  schema bean). The spec's schema section did not list it; it is required by the defect-#13
  sibling-token burn with bounded lookups. Reported here per stop condition #2 - retroactive
  disclosure, functionally verified by PasswordRecoveryFlowIT.
- Account-token + user writes in password reset remain separate DynamoDB writes (#14): the
  safe-state protocol is burn-after-password-change plus the passwordChangedAt JWT gate;
  crash windows converge via replay repair or janitor cleanup.
