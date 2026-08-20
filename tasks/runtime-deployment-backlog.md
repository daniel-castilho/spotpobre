### 3. `tasks/runtime-deployment-backlog.md`

```markdown
# Runtime & Deployment — Backlog (Full Production Excellence)

**Companions:** `runtime-deployment-spec.md` · `runtime-deployment-implementation-sequence.md`
**Epic goal:** Complete, production-grade runtime and deployment capability.

**MVP = all stories below (S0–S14)**

---

## Story map
```

BASELINE
S0 Make existing CI green (unit + slice + IT)

DECISION
S1 ADR – choose production platform & secret mechanism

IMAGE
S2 Multi-stage non-root Dockerfile + .dockerignore
S3 Image supply chain (digests, scan, SBOM, immutable tags, non-root test)

CONFIGURATION & SECRETS
S4 Production config contract + fail-fast validator
S5 Secret store integration + workload identity (no long-lived keys)

HEALTH
S6 Startup / liveness / readiness model + Actuator security
S7 Probe failure & recovery tests

SHUTDOWN
S8 Graceful shutdown under load (SIGTERM + draining test)

DEPLOYMENT
S9 Versioned manifests for the chosen platform
S10 Blue/green or canary + rollback criteria
S11 Staging deployment + rollback exercise

OPERATIONS
S12 Operational runbook
S13 CI image build + full verification pipeline
S14 Documentation sync (README, CHANGELOG, AGENTS, deployment docs)

```

---

## Stories

| ID  | Story                                                                 | Priority |
|-----|-----------------------------------------------------------------------|----------|
| S0  | Make current CI (unit + slice + IT) reliably green                    | Must     |
| S1  | Write ADR selecting platform + secret store + identity model           | Must     |
| S2  | Multi-stage Dockerfile, non-root user, .dockerignore                  | Must     |
| S3  | Fixed digests, vulnerability scan, SBOM, immutable publish, UID test | Must     |
| S4  | Complete prod config contract + fail-fast validator                   | Must     |
| S5  | Secrets Manager (or chosen store) + task role / workload identity     | Must     |
| S6  | Distinct startup, liveness, readiness + secure probe endpoints        | Must     |
| S7  | Automated tests for dependency failure → readiness DOWN + recovery    | Must     |
| S8  | Automated or reproducible graceful-shutdown-under-load test           | Must     |
| S9  | Versioned deployment manifests                                        | Must     |
| S10 | Blue/green or canary strategy with health gates and rollback          | Must     |
| S11 | Successful staging deploy + rollback exercise                         | Must     |
| S12 | Operational runbook                                                   | Must     |
| S13 | CI builds image, scans it, runs health + shutdown checks              | Must     |
| S14 | Full documentation sync                                               | Must     |

---

## Definition of Done (epic)

All items in the prompt’s excellence checklist are satisfied and evidenced.
```
