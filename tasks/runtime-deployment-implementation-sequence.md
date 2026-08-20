### 4. `tasks/runtime-deployment-implementation-sequence.md`

```markdown
# Runtime & Deployment — Implementation Sequence (Full Production Excellence)

**Companions:** `runtime-deployment-spec.md` · `runtime-deployment-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not skip ahead.

---

## Step 0 — Baseline CI green

1. Fix any failing unit, slice or IT tests
2. Ensure pure unit tests run without Docker
3. Ensure IT tests are deterministic on clean runners
4. Confirm the existing GitHub Actions workflow is green

**Done when:** CI is green on main with no new failures.

---

## Step 1 — ADR

Write a short Architecture Decision Record that chooses:

- Compute platform (recommended: ECS Fargate)
- Registry (ECR)
- Load balancer
- Secret store
- Identity model (task role)
- Rollout strategy (blue/green or canary)

**Done when:** ADR is committed and accepted.

---

## Step 2 — Dockerfile & image hygiene

- Multi-stage Dockerfile
- Non-root user
- `.dockerignore`
- Exec-form entrypoint
- Minimal runtime image

**Done when:** `docker build` succeeds and the container starts as non-root.

---

## Step 3 — Supply chain

- Pin base images by digest
- Add vulnerability scan
- Generate SBOM
- Publish with immutable tag/digest
- CI test that fails if UID == 0

**Done when:** Image is scanned, attested and non-root verified.

---

## Step 4 — Production configuration contract

- Complete the list of required environment / secret values
- Strengthen `ProdConfigValidator` (or equivalent) to fail fast
- Document the full contract

**Done when:** Starting with profile `prod` and any missing required value aborts clearly.

---

## Step 5 — Secrets & workload identity

- Integrate the chosen secret store
- Configure the application and AWS clients to use task / workload identity
- Remove any requirement for long-lived access keys in production
- Prove that no secret appears in logs or image layers

**Done when:** Application starts in a production-like environment using only the secret store + role.

---

## Step 6 — Health model

- Implement distinct startup, liveness and readiness endpoints
- Decide and document critical dependencies for readiness
- Secure the probe endpoints appropriately
- Add tests for failure and recovery of readiness

**Done when:** Probes behave correctly under normal, failure and recovery conditions.

---

## Step 7 — Graceful shutdown under load

- Create a reproducible test that:
  - Generates concurrent traffic
  - Sends SIGTERM
  - Verifies readiness goes DOWN
  - Verifies in-flight requests complete
  - Verifies clean exit within grace period
- Align manifest termination settings with the test

**Done when:** The shutdown test passes reliably.

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
