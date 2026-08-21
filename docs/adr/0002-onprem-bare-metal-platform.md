# ADR-0002: Production platform pivot — on-premises bare metal with LocalStack

**Status:** Accepted
**Date:** 2026-08-20
**Deciders:** Spotpobre API maintainers
**Context:** Runtime & Deployment epic (S1 review); supersedes [ADR-0001](0001-production-platform.md)
**Companion:** `tasks/runtime-deployment-spec.md`

---

## Context

ADR-0001 selected AWS ECS Fargate + ECR + ALB + Secrets Manager + CodeDeploy blue/green as the
production platform, and the corresponding versioned manifests were produced (`deploy/stack.yaml`,
`deploy/task-definition.json`, `deploy/codedeploy.yaml`, `deploy/appspec.yaml`).

The deployment target has since changed by business decision:

- Production runs on a **single on-premises bare-metal server**, not in the AWS cloud.
- There is **no AWS account** for this workload. The AWS APIs the application depends on
  (DynamoDB, S3) are provided by **LocalStack running in Docker on the same server**.
- The operations profile is a single host, small traffic, operator-run deployments.

The AWS-native manifests from ADR-0001 cannot be executed against this target and are kept in the
repository **as a documented legacy backup** (they remain valid if the project ever moves to a real
AWS account).

## Decision

| Concern        | Decision                                                                                     |
| -------------- | -------------------------------------------------------------------------------------------- |
| Compute        | **Docker Compose on the bare-metal host** — hardened non-root application containers (S2/S3) |
| AWS APIs       | **LocalStack in Docker on the same host** (DynamoDB + S3), reached over the compose network  |
| Cache          | **Redis in Docker on the same host** (unchanged)                                             |
| Load balancing | **NGINX** with a weighted `upstream` (blue/green fleets), health-gated traffic shifting       |
| Registry       | **Local image store on the host** (`docker load` / `docker compose build`); images pinned by digest |
| Secrets        | **Environment injection from an untracked, root-owned `.env` file** on the host (e.g. `JWT_SECRET`); no secrets in Git, images or manifests |
| Identity       | **Host-perimeter security.** No real cloud credentials exist; LocalStack accepts dummy keys (`test`/`test`) reachable only from the compose network |
| Rollout        | **NGINX blue/green**: canary weight shift (10% → 100%) with readiness health gates, observation window and scripted rollback (`scripts/bluegreen-deploy.sh`, `scripts/bluegreen-rollback.sh`) |

### Why these choices

1. **Matches the actual infrastructure.** One server, no cloud account, no Kubernetes/ECS control
   plane to operate. Docker Compose is already the project's local/CI runtime shape.
2. **LocalStack keeps the application cloud-agnostic.** The domain/application layers still speak
   only to DynamoDB/S3 through ports; swapping the emulator for real AWS later requires no code
   change — only endpoints and credentials.
3. **NGINX weighted upstreams reproduce the ALB/CodeDeploy blue/green semantics** (two fleets,
   weighted traffic shift, drain, instant rollback) without any managed service.
4. **Env-file secrets are the honest on-prem equivalent** of a secret store: untracked, root-owned,
   referenced by the compose file, rotatable by editing the file and restarting the fleet. LocalStack
   also emulates Secrets Manager; adopting it later would be an incremental change behind the same
   `JWT_SECRET` contract.

## Consequences

### Positive

- Zero cloud cost and zero vendor dependency for production.
- Deploy/rollback is fully scriptable and reproducible on the single host; exercises run locally
  against the exact production shape (no "pending AWS credentials" gap).
- The ADR-0001 artifact set remains a ready-made migration path if a real AWS account appears.

### Negative / accepted trade-offs

- **Single-host deployment** — no multi-AZ redundancy; availability is bounded by the host.
  Mitigation: fast scripted rollback and the runbook's incident procedures.
- **LocalStack is an emulator**, not DynamoDB/S3 proper: edge behaviours may differ. Mitigation:
  the data model sticks to core APIs (tables, GSIs, presigned URLs) that LocalStack implements
  faithfully; the IT suite runs against Testcontainers LocalStack, so CI parity is high.
- **No IAM workload identity** — replaced by host-perimeter isolation plus non-root containers.
  The "no long-lived keys" goal is met trivially: no real keys exist anywhere.
- Blue/green doubles app-container memory during a rollout (accepted; sized in the compose file).

### Follow-ups required by this ADR

- S9: keep `deploy/stack.yaml`, `task-definition.json`, `codedeploy.yaml`, `appspec.yaml` untouched
  as legacy backup; make `deploy/docker-compose.bluegreen.yml` + NGINX config the canonical
  production manifests; update `deploy/README.md` accordingly.
- S10/S11: exercise deploy + rollback end-to-end against LocalStack on the host (now executable).
- S12/S14: runbook and documentation must describe the on-premises shape, not ECS.

---

## Alternatives considered

- **Keep ADR-0001 and acquire a real AWS account** — rejected: the business requires on-premises
  operation; cloud egress/control is out of scope for this workload.
- **JAR + systemd on the host** — rejected: loses the hardened non-root image, the uniform
  container boundary for app + infra, and the blue/green swap mechanics already built.
- **Kubernetes (on-prem k3s/kubeadm)** — rejected: a control plane is unjustified for one service
  on one host; NGINX + Compose delivers the same rollout safety with far less operational surface.
- **LocalStack Secrets Manager for `JWT_SECRET` now** — deferred: adds an SDK integration and a
  bootstrap ordering problem for marginal gain over a root-owned env file; revisit if the number of
  secrets grows.
