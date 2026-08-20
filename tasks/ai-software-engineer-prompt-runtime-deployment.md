### 1. `tasks/ai-software-engineer-prompt-runtime-deployment.md`

```markdown
# AI Software Engineer Prompt — Runtime & Deployment (Critical – Full Production Excellence)

**Status:** Not implemented — critical production epic (complete scope).
**Target:** Deliver a real, secure, operable and production-grade runtime & deployment foundation for Spotpobre
**Package / scope:** Repository root, configuration, Docker, CI, deployment manifests, secrets, runbooks

You implement the **complete** Runtime & Deployment epic to production excellence standard.
This is not a “Phase 1 container only” effort. All items below are mandatory.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/twelve-factor.md` · `docs/coding-standards.md` · `docs/testing-playbook.md` · `docs/lessons.md`
3. `tasks/runtime-deployment-spec.md` — full specification
4. `tasks/runtime-deployment-backlog.md` — stories
5. `tasks/runtime-deployment-implementation-sequence.md` — strict build order
6. Current `application-prod.yaml`, Actuator config, CI workflow, `docker-compose.yaml`

---

## Goal

Spotpobre must be deployable to a real production platform with:

- Immutable, non-root, scanned container images
- Workload identity (no long-lived AWS access keys)
- Secrets coming exclusively from a real secret store
- Distinct startup / liveness / readiness probes that are tested under failure
- Graceful shutdown validated under concurrent load
- Versioned deployment manifests
- Blue/green or canary rollout with automated rollback criteria
- Complete operational runbook
- CI that is green **before** and **after** the new steps

---

## Non-negotiable rules

- First make the existing CI green (unit + slice + IT). Do not add image work on top of a red pipeline.
- Choose **one** production platform via a short ADR (recommended default for this project: **AWS ECS Fargate + ECR + ALB + Secrets Manager + IAM Task Roles**).
- Domain and application layers remain untouched.
- No secrets in Git, Dockerfile layers, manifests, CI logs or plain environment variables that are committed.
- Prefer workload identity / task roles over static AWS keys.
- English only.
- No new Maven dependencies without human approval.
- Do not push unless the human explicitly asks.
- Do not invent scope beyond this epic, but **do implement everything listed here**.

---

## Definition of Done (epic) — Full Excellence

- [ ] Existing CI (unit + slice + IT) is green
- [ ] ADR choosing the production platform exists
- [ ] Multi-stage Dockerfile produces a non-root, reproducible image
- [ ] Image is scanned, has SBOM, uses fixed base digests and is published with immutable tag/digest
- [ ] Container runs as non-root (`id -u` ≠ 0)
- [ ] `prod` profile fails fast on every required configuration value
- [ ] Secrets come from AWS Secrets Manager (or chosen store) via workload identity
- [ ] No long-lived AWS access keys required in production
- [ ] Startup, liveness and readiness probes are distinct, secured and tested (including dependency failure + recovery)
- [ ] Graceful shutdown under load is validated (SIGTERM + in-flight requests + draining)
- [ ] Versioned deployment manifests for the chosen platform exist
- [ ] Blue/green or canary strategy with health gates and rollback criteria is defined and testable in staging
- [ ] Staging deployment exercise succeeded
- [ ] Operational runbook exists (deploy, rollback, secret rotation, basic incidents)
- [ ] README, CHANGELOG, AGENTS.md and operational docs reflect reality

Start at **Step 0** of the implementation sequence. If any decision is blocked, stop and ask.
```
