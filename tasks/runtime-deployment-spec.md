### 2. `tasks/runtime-deployment-spec.md`

```markdown
# Runtime & Deployment — Technical Specification (Full Production Excellence)

**Status:** Draft for complete implementation
**Focus:** Container, platform, secrets, probes, shutdown, rollout and operability
**Companions:** `runtime-deployment-backlog.md` · `runtime-deployment-implementation-sequence.md`

---

## 1. Purpose & scope

Deliver a production-ready runtime and deployment capability that closes the original “Runtime & Deployment (Critical)” deficiency at excellence level.

**In scope (mandatory):**

- Green CI baseline as prerequisite
- Short ADR choosing the production platform (default recommendation: AWS ECS Fargate + ECR + ALB + Secrets Manager + IAM Task Roles)
- Multi-stage, non-root, hardened Dockerfile + supply-chain controls (fixed digests, SBOM, vulnerability scan, immutable tags)
- Complete production configuration contract with fail-fast validation
- Real secret management via secret store + workload identity (no long-lived keys)
- Distinct **startup**, **liveness** and **readiness** probes with explicit dependency behaviour and security
- Automated test of graceful shutdown under concurrent load (SIGTERM, draining, in-flight requests)
- Versioned deployment manifests for the chosen platform
- Blue/green or canary deployment strategy with health gates and rollback criteria
- Staging deployment exercise
- Operational runbook
- Full documentation sync

**Explicitly out of scope for this epic (can be future work):**

- Multi-region active-active
- Advanced service mesh
- Full GitOps tool (Argo CD / Flux) unless the chosen platform makes it trivial
- Cost-optimisation automation beyond basic resource limits

---

## 2. Platform decision (ADR required)

Before writing manifests, produce a short ADR that selects **one** platform.

**Recommended default (coherent with existing DynamoDB + S3 usage):**

- Compute: AWS ECS Fargate
- Image registry: Amazon ECR
- Load balancing: Application Load Balancer
- Secrets: AWS Secrets Manager (or SSM Parameter Store for non-sensitive config)
- Identity: ECS Task Role (IAM) — no static `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` in production
- Rollout: ECS + CodeDeploy blue/green or equivalent ALB target-group shifting

Alternative (Kubernetes) is acceptable only if the ADR justifies it and supplies the corresponding manifests, workload identity and secret mechanism.

---

## 3. Image & supply chain requirements

- Multi-stage build (Maven build stage → minimal JRE runtime stage)
- Non-root user with stable UID/GID
- Fixed base image digests (not floating tags)
- `.dockerignore` that excludes secrets, `.git`, `.localstack`, `target/`, etc.
- Exec-form entrypoint
- Read-only root filesystem where feasible + explicit `/tmp`
- Resource limits defined in the deployment manifest
- Vulnerability scan in CI
- SBOM generation
- Immutable image reference (digest preferred)
- Automated test that fails if the container runs as UID 0

---

## 4. Configuration & secrets

- `application-prod.yaml` + validator must fail fast on every required value
- Required configuration documented as the single operator contract
- Secrets (JWT secret, any other sensitive values) retrieved from the secret store at runtime
- AWS clients must use the task / instance role (workload identity)
- Static access keys are forbidden in production
- No secret values in Git, image layers, manifests or CI logs
- Secret rotation behaviour must be documented

---

## 5. Health model (must be explicit)

| Probe     | Purpose                         | Behaviour required                                                                                                  |
| --------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Startup   | Protect slow-starting instances | Fails while the application is still initialising                                                                   |
| Liveness  | Process is alive                | Lightweight; does not check external dependencies                                                                   |
| Readiness | Ready to receive traffic        | May check critical dependencies; must go DOWN when a critical dependency is unavailable and recover when it returns |

**Decisions that must be recorded:**

- Which dependencies are critical for readiness (DynamoDB? S3? Redis?)
- How checks are performed without excessive cost/load
- How Actuator endpoints used by probes are secured (management port, permit only probe paths, or platform-native auth)
- Behaviour during shutdown (readiness must go DOWN before the process exits)

Tests must cover: startup, dependency failure → readiness DOWN, recovery, liveness still UP, and security of non-probe Actuator endpoints.

---

## 6. Graceful shutdown under load

A reproducible test (or automated CI job) must demonstrate:

1. Generate concurrent traffic with in-flight requests
2. Send SIGTERM to the container
3. Readiness becomes DOWN (instance removed from load balancer)
4. In-flight requests complete successfully
5. New requests are no longer routed to the instance
6. Process exits within the configured grace period
7. Interaction with the load balancer draining settings is correct

Manifests must set appropriate `terminationGracePeriodSeconds` / preStop / deregistration delay.

---

## 7. Deployment & rollout

- Versioned manifests (task definition, service, ALB target groups, etc. or Kubernetes equivalents)
- Immutable artefact promotion (by digest)
- Blue/green or canary with:
  - Health gate before full traffic
  - Automatic rollback criteria (readiness, 5xx rate, latency)
  - Observation window
  - Documented rollback procedure
- Staging environment exercise that proves deploy + rollback

---

## 8. Definition of Done (full)

Matches the prompt’s excellence checklist. The epic is not done until every item is satisfied and evidenced (tests, manifests, ADR, runbook, CI logs).

---

## 9. Runbook (mandatory deliverable)

Must cover at minimum:

- How to deploy a new version
- How to roll back
- How to rotate secrets
- What to do when readiness is DOWN
- Basic incident response for container / dependency failures
```
