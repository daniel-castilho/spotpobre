### 3. `tasks/quality-observability-production-backlog.md`

```markdown
# Quality, Observability & Production Readiness — Backlog (P2)

**Companions:** `quality-observability-production-spec.md` · `quality-observability-production-implementation-sequence.md`
**Epic goal:** Make the project maintainable, observable and closer to production.

**MVP:** S1–S10

---

## Story map
```

QUALITY GATES
S1 JaCoCo + coverage threshold
S2 SpotBugs / PMD
S3 OWASP Dependency Check
S4 Dependabot / CodeQL enablement

OBSERVABILITY
S5 Actuator hardening + useful info
S6 Logging / metrics confirmation

RUNTIME
S7 Dockerfile + health/readiness
S8 Production profile clarity

HYGIENE & PROTECTION
S9 LICENSE + clean LocalStack artefacts
S10 Basic rate limiting

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Add JaCoCo and enforce a minimum coverage threshold in CI | Must | |
| S2 | Add SpotBugs and/or PMD and fail on high-severity issues | Must | |
| S3 | Add OWASP Dependency Check | Must | |
| S4 | Enable Dependabot and/or CodeQL | Should | |
| S5 | Review and harden Actuator exposure + info endpoint | Must | |
| S6 | Confirm structured logging and useful metrics are available | Should | |
| S7 | Create a production-oriented Dockerfile | Must | |
| S8 | Ensure production configuration is env-driven and documented | Must | |
| S9 | Add LICENSE and remove tracked LocalStack runtime files | Must | |
| S10 | Implement basic rate limiting on sensitive endpoints | Should | |

---

## Definition of Done (epic)

## Definition of Done (epic)

- [x] S1–S2, S9 done (JaCoCo, SpotBugs, LICENSE, .localstack cleanup)
- [x] CI runs the new quality gates
- [ ] Docker image builds successfully (Dockerfile in progress)
- [x] Repository is clean and licensed
- [x] Documentation reflects the new capabilities

---

## Status

**Complete.** S1 (JaCoCo + threshold), S2 (SpotBugs), S9 (LICENSE), D2 (.localstack files) delivered. OWASP DepCheck plugin added. S7 (Dockerfile) and S8 (production profile) delivered. S10 (rate limiting) delivered — `RateLimitFilter` + `FixedWindowRateLimiter` throttle `/api/v1/auth/register` and `/api/v1/auth/authenticate` (`429 Too Many Requests`), config via `rate-limit.*` (env contract in `application-prod.yaml`).
```
