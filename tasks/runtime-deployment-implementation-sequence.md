### 4. `tasks/runtime-deployment-implementation-sequence.md`

```markdown
# Runtime & Deployment — Implementation Sequence (Full Production Excellence)

**Companions:** `runtime-deployment-spec.md` · `runtime-deployment-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not skip ahead.

---

## Step 0 — Baseline CI green

> **AS-BUILT (2026-08-19):** DONE. All CI steps validated locally — unit tests (152), `jacoco:check`,
> `spotbugs:check`, `*IT` suite (29) and `./mvnw clean package` all green.

---

## Step 1 — ADR

> **AS-BUILT (2026-08-19):** DONE. `docs/adr/0001-production-platform.md` — ECS Fargate + ECR + ALB +
> Secrets Manager + ECS Task Role + CodeDeploy blue/green.

---

## Step 2 — Dockerfile & image hygiene

> **AS-BUILT (2026-08-19):** DONE. Multi-stage `Dockerfile` (Maven build → Temurin 21 JRE), non-root
> UID/GID 10001 (verified via `docker run --entrypoint id`), exec-form entrypoint, `.dockerignore`.

---

## Step 3 — Supply chain

> **AS-BUILT (2026-08-19):** DONE. Base images pinned by digest; `.github/workflows/image-security.yml`
> fails on UID 0, scans with Trivy (SARIF to GitHub Security), uploads CycloneDX SBOM artifact.

---

## Step 4 — Production configuration contract

> **AS-BUILT (2026-08-19):** DONE. `ProdConfigValidator` fails fast when any required value is missing
> and rejects static AWS credentials in prod; verified end-to-end. `application-prod.yaml` uses empty
> env defaults so dev secrets never leak into prod config.

---

## Step 5 — Secrets & workload identity

> **AS-BUILT (2026-08-19):** DONE (application side). `AwsCredentialsProviderResolver` resolves from the
> ECS task role (`DefaultCredentialsProvider`) when no static keys are set. Secrets Manager wiring
> (`valueFrom`) is deferred to Step 8 (manifests).

---

## Step 6 — Health model

> **AS-BUILT (2026-08-19):** DONE. Probes enabled (`management.endpoint.health.probes.enabled`),
> readiness gates on DynamoDB + S3 via `DynamoDbHealthIndicator` / `S3HealthIndicator` (Redis is a
> cache and excluded from the gate — S6 decision), `show-details: when-authorized`, probe paths
> unauthenticated while the rest of `/actuator/**` requires auth. Verified end-to-end: readiness DOWN
> without the S3 bucket, UP after it exists. Bonus fix: `CachedUserDetails` DTO resolves the Redis
> auth-cache serialization bug (see CHANGELOG `Unreleased`).

---

## Step 7 — Graceful shutdown under load

> **AS-BUILT (2026-08-19):** DONE. `scripts/shutdown-under-load-test.sh` is a reproducible,
> self-contained test that starts the app, runs a continuous concurrent traffic generator
> (default 40 parallel authenticated requests), sends SIGTERM mid-traffic, and asserts the six
> criteria: readiness goes DOWN (503) while the process is still alive, in-flight requests complete
> with 200, requests arriving after drain begins are rejected (503/000), and the process exits
> within the configured grace period (30s). Runs in ~10s; executed twice to confirm reproducibility
> (137+134 in-flight OK, 0 unexpected statuses, 2s exit each run). Spring Boot 3.5 already publishes
> `REFUSING_TRAFFIC` on graceful shutdown (`server.shutdown: graceful`,
> `spring.lifecycle.timeout-per-shutdown-phase: 30s` in `application.yaml`), so no code change was
> needed — only the verification artifact. Manifest termination settings (preStop /
> deregistration delay) are aligned in Step 8.

---

## Step 8 — Deployment manifests

- Write versioned manifests for the chosen platform (task definition, service, ALB, etc.)
- Include resource limits, security context, probe configuration and secret references

**Done when:** Manifests are complete and can be applied to a staging account/cluster.

---

## Step 9 — Rollout strategy

- Implement or document blue/green (or canary) using the platform’s native mechanism
- Define health gates and automatic rollback criteria
- Document the observation window and manual rollback procedure

**Done when:** Rollout and rollback paths are defined and testable.

---

## Step 10 — Staging exercise

- Deploy to staging using the new manifests and image
- Exercise a normal deploy
- Exercise a rollback
- Verify probes, secrets and shutdown behaviour in the real environment

**Done when:** Staging exercise succeeds and is recorded.

---

## Step 11 — CI pipeline completion

- Extend CI to:
  - Build the image
  - Scan it
  - Generate SBOM
  - Run health smoke
  - (Optionally) run the shutdown test
- Gate the pipeline on these steps

**Done when:** CI fully verifies the runtime artefacts.

---

## Step 12 — Runbook & documentation

- Write the operational runbook
- Update README, CHANGELOG, AGENTS.md and any deployment docs
- Clear the previous “no documented production runtime shape” debt

**Done when:** All documentation is accurate and the full Definition of Done is met.

---

## Final smoke / acceptance path

1. CI green (including new image steps)
2. Image builds, is non-root, scanned and published by digest
3. Application starts with secrets from the secret store + task role
4. Startup / liveness / readiness behave correctly under failure
5. SIGTERM under load drains cleanly
6. Staging deploy + rollback succeeds
7. Runbook is usable by an operator who has never seen the code

---

_After delivery, replace this file with an as-built status note if desired._
```
