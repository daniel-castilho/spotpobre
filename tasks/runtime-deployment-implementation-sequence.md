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
>
> **AS-BUILT UPDATE (2026-08-21):** Platform pivoted to on-premises bare metal (human decision:
> production runs Docker Compose + NGINX blue/green + LocalStack). New
> `docs/adr/0002-onprem-bare-metal-platform.md` supersedes ADR-0001, whose content is preserved
> untouched as the real-AWS migration path (status line updated only).

---

## Step 2 — Dockerfile & image hygiene

> **AS-BUILT (2026-08-19):** DONE. Multi-stage `Dockerfile` (Maven build → Temurin 21 JRE), non-root
> UID/GID 10001 (verified via `docker run --entrypoint id`), exec-form entrypoint, `.dockerignore`.

---

## Step 3 — Supply chain

> **AS-BUILT (2026-08-19):** DONE. Base images pinned by digest; `.github/workflows/image-security.yml`
> fails on UID 0, scans with Trivy (SARIF to GitHub Security), uploads CycloneDX SBOM artifact.
>
> **AS-BUILT UPDATE (2026-08-21):** `.dockerignore` hardened (deploy/, scripts/, mp3/ excluded;
> `!deploy/nginx-bluegreen.conf` re-included for the LB image build). The standalone
> `image-security.yml` workflow was removed as duplication — its checks live in the consolidated
> `ci.yml` `image` job (see Step 11).

---

## Step 4 — Production configuration contract

> **AS-BUILT (2026-08-19):** DONE. `ProdConfigValidator` fails fast when any required value is missing
> and rejects static AWS credentials in prod; verified end-to-end. `application-prod.yaml` uses empty
> env defaults so dev secrets never leak into prod config.
>
> **AS-BUILT UPDATE (2026-08-21):** Contract redesigned around an explicit credential source:
> `aws.credentials.source` (`AWS_CREDENTIALS_SOURCE`) ∈ {`static`, `workload-identity`} is now
> REQUIRED in prod — `static` demands access/secret keys (LocalStack target), `workload-identity`
> forbids them (real-AWS task-role target). `AwsCredentialsProviderResolver` switches providers on
> the flag; 14 unit tests cover the matrix (`ProdConfigValidatorTest`,
> `AwsCredentialsProviderResolverTest`).

---

## Step 5 — Secrets & workload identity

> **AS-BUILT (2026-08-19):** DONE (application side). `AwsCredentialsProviderResolver` resolves from the
> ECS task role (`DefaultCredentialsProvider`) when no static keys are set. Secrets Manager wiring
> (`valueFrom`) is deferred to Step 8 (manifests).
>
> **AS-BUILT UPDATE (2026-08-21):** Superseded by the explicit `AWS_CREDENTIALS_SOURCE` switch
> (see Step 4 update): inference removed in favour of an explicit, validated contract.

---

## Step 6 — Health model

> **AS-BUILT (2026-08-19):** DONE. Probes enabled (`management.endpoint.health.probes.enabled`),
> readiness gates on DynamoDB + S3 via `DynamoDbHealthIndicator` / `S3HealthIndicator` (Redis is a
> cache and excluded from the gate — S6 decision), `show-details: when-authorized`, probe paths
> unauthenticated while the rest of `/actuator/**` requires auth. Verified end-to-end: readiness DOWN
> without the S3 bucket, UP after it exists. Bonus fix: `CachedUserDetails` DTO resolves the Redis
> auth-cache serialization bug (see CHANGELOG `Unreleased`).
>
> **AS-BUILT UPDATE (2026-08-21):** Automation gap closed: `DynamoDbHealthIndicatorTest`,
> `S3HealthIndicatorTest` (unit) and `HealthProbeFlowIT` (full failure → DOWN → recovery cycle on
> Testcontainers LocalStack) added.

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

> **AS-BUILT (2026-08-19):** DONE. `deploy/` holds the versioned manifests for the ADR-0001
> platform:
> - `deploy/stack.yaml` — one CloudFormation stack: VPC (public/private subnets, NAT), security
>   groups (ALB 443, ECS 8080 from ALB), IAM roles (task execution + task role for DynamoDB/S3),
>   ALB + target group + HTTPS listener, ECS cluster (Container Insights), task definition, service
>   (`CODE_DEPLOY` controller), CPU autoscaling (target-tracking 70%, 1–3). Parameterised
>   (`ImageDigest` pinned by digest, `JwtSecretArn`, `SslCertificateArn`, `RedisHost`, `BucketName`,
>   env, capacity). Honours the full runtime contract: non-root `10001:10001`, read-only root FS,
>   cpu/memory limits, `JWT_SECRET` from Secrets Manager via `valueFrom`, container + ALB health
>   checks on `/actuator/health/readiness`, `prod,json` logging to CloudWatch, 90s health-check
>   grace aligned with the S7 drain.
> - `deploy/task-definition.json` — reference task definition in native ECS JSON (used by the
>   CodeDeploy AppSpec in S9).
> - `deploy/README.md` — pre-requisites, apply/update commands, runtime-contract matrix.
> Validated locally (YAML structure + required properties); `aws cloudformation deploy` needs real
> AWS credentials (staging, S10).
>
> **AS-BUILT UPDATE (2026-08-21):** Production target changed to on-premises (ADR-0002). The
> CloudFormation manifests above are kept UNTOUCHED as the documented real-AWS backup. The
> production path is now `deploy/docker-compose.bluegreen.yml` (hardened blue/green fleets + NGINX
> LB + LocalStack + Redis: non-root, read-only root FS + tmpfs, resource limits, health checks,
> `depends_on: service_healthy`, `restart: unless-stopped`) with `deploy/.env.example` as the
> operator contract and `deploy/nginx-lb/Dockerfile` (nginx pinned 1.27.4-alpine for OSS upstream
> `resolve`).

---

## Step 9 — Rollout strategy

- Implement or document blue/green (or canary) using the platform's native mechanism
- Define health gates and automatic rollback criteria
- Document the observation window and manual rollback procedure

**AS-BUILT (2026-08-19):** DONE. `deploy/codedeploy.yaml` defines the CodeDeploy application +
blue/green deployment group (`DeploymentType: BLUE_GREEN`, `DeploymentOption: WITH_TRAFFIC_CONTROL`,
deployment config `CodeDeployDefault.ECSCanary10Percent5Minutes`) and the service role
(`AWSCodeDeployRoleForECS`). `deploy/appspec.yaml` is the reference ECS AppSpec (submitted inline
via `RevisionType: AppSpecContent`) with the task-definition ARN as a placeholder. `deploy/stack.yaml`
updated to a blue/green listener (blue 100 / green 0) on the blue/green target group pair, and
three rollback alarms (5xx, target response time, zero healthy hosts) plus a deployment-failure
trigger. `deploy/README.md` documents the rollout procedure, the 10%/5min observation window, and
the manual rollback procedure. Validated locally (YAML/JSON structural parse); full blue/green
exercise is staged in Step 10 (real AWS).

**AS-BUILT UPDATE (2026-08-21):** The operative rollout mechanism for the on-premises target is
script-based: `scripts/bluegreen-deploy.sh` (green readiness gate → canary 10% with 30 s
observation + automatic abort-to-blue → cutover; optional image-tag argument) and
`scripts/bluegreen-rollback.sh` (instant revert). NGINX config corrected per official docs: `down`
instead of the nonexistent `weight=0` (which crash-looped the LB), `keepalive 32`,
`proxy_next_upstream error timeout`, passive checks, and runtime DNS re-resolution
(`resolver 127.0.0.11 valid=10s` + `zone` + `resolve`) eliminating stale-IP routing after fleet
recreations. The CodeDeploy path remains documented for the future real-AWS migration.

---

## Step 10 — Staging exercise

- Deploy to staging using the new manifests and image
- Exercise a normal deploy
- Exercise a rollback
- Verify probes, secrets and shutdown behaviour in the real environment

**AS-BUILT (2026-08-19):** Procedure defined and committed (`deploy/README.md` "Staging exercise
(S10)" with exact `aws cloudformation deploy` + `aws deploy create-deployment` commands and an
acceptance checklist covering services-stable, liveness/readiness/metrics auth, canary 10%/5min
traffic shift, alarm observation and forced rollback). **Execution is pending AWS credentials** —
`aws sts get-caller-identity` returns no configured profile in this environment, so the deploy and
rollback were not run against a real account. This item is recorded as an open debt item in
`AGENTS.md` ("Blue/green rollout not exercised against real AWS").

**AS-BUILT UPDATE (2026-08-21): EXECUTED AND PASSED** against the on-premises production stack
(ADR-0002). Full transcript and acceptance table in `deploy/README.md` §1.6: smoke through the LB
(register returns JWT on the prod profile), canary deploy of v2 (gate → 10%/30 s → cutover) PASS,
cutover proven by stopping blue while green served PASS, rollback PASS, LB resilience to fleet IP
change (auto-heal via `resolve`, squatter on the old IP) PASS. Defects found during the exercise
were fixed and are listed in CHANGELOG `Unreleased` → Fixed. The AWS-native exercise remains a
roadmap item for a future real account (AGENTS.md debt updated accordingly).

---

## Step 11 — CI pipeline completion

- Extend CI to:
  - Build the image
  - Scan it
  - Generate SBOM
  - Run health smoke
  - (Optionally) run the shutdown test
- Gate the pipeline on these steps

**AS-BUILT (2026-08-19):** DONE. `.github/workflows/ci.yml` extended: added an `image` job (build,
non-root UID check, Trivy HIGH/CRITICAL scan with SARIF upload to GitHub Security, CycloneDX SBOM
artifact + image digest `image-digest.txt` artifact) and a non-blocking `runtime-smoke` job that
starts LocalStack + Redis via `docker compose`, seeds the schema with the new reusable
`scripts/seed-localstack.sh`, runs `scripts/shutdown-under-load-test.sh`, then tears down. Workflow
permissions widened to `security-events: write` and `packages: write`. Verified live: the shutdown
smoke passes end-to-end (140 in-flight OK, 2s exit) against the running LocalStack/Redis. Image
push to ECR (OIDC) is the only remaining CI gate, intentionally left to production-only IAM.
The shutdown smoke is non-blocking per the testing-playbook gap 7 (shutdown test has no CI gate
yet). `secrets/seed-localstack.sh` was extracted from the README so the same commands run locally
and in CI.

**AS-BUILT UPDATE (2026-08-21):** Two defects fixed in `ci.yml`: image digest capture used
`{{index .RepoDigests 0}}` (empty/exception for locally built images) → now `{{.Id}}`; the
`runtime-smoke` job no longer runs when the build job failed (`if: always()` removed from job
level — it previously died confusingly at the artifact-download step). Workflow YAML validated.
The seed script itself was rewritten (idempotent pre-checks, awslocal-in-container fallback for
broken host S3 CLI, correct `profile.email` GSI shape).

## Step 12 — Runbook & documentation

- Write the operational runbook
- Update README, CHANGELOG, AGENTS.md and any deployment docs
- Clear the previous "no documented production runtime shape" debt

**AS-BUILT (2026-08-19):** DONE. A runnable-by-a-stranger operational runbook was added to
`README.md` (deploy, rollback, secret rotation, readiness-DOWN triage, container incident response),
cross-referencing `deploy/README.md`. `README.md`/`CHANGELOG.md`/`AGENTS.md` Current State,
Documentation table and Known technical debt were all updated during Steps 0–10; the
"production runtime shape" debt is cleared from the open-debt list.

**AS-BUILT UPDATE (2026-08-21):** Runbook promoted to its own mandatory deliverable,
`docs/release-runbook.md` (spec §9), rewritten for the on-premises target: deploy, rollback,
secret rotation (one-fleet-at-a-time), readiness-DOWN triage, incident response (crash loops, LB
5xx, Redis outage, LocalStack outage), routine operations, legacy-AWS appendix. The README section
is now a pointer to it. README Current State/Roadmap, CHANGELOG `Unreleased`, AGENTS.md debt list
and this file's as-built notes all synced to the ADR-0002 reality.

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
