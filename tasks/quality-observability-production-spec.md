### 2. `tasks/quality-observability-production-spec.md`

```markdown
# Quality, Observability & Production Readiness — Technical Specification (P2)

**Status:** Complete — implemented in commit `4fafc23` ("chore: add JaCoCo threshold, OWASP DepCheck, LICENSE, clean LocalStack artefacts")

**Focus:** Quality gates, operability and production basics
**Companions:** `quality-observability-production-backlog.md` · `quality-observability-production-implementation-sequence.md`

---

## 1. Purpose & scope

Move the project from a good prototype toward something that can be safely maintained and deployed.

**In scope (P2):**

- **Quality gates**
  - JaCoCo with a minimum coverage threshold (fail the build if below)
  - Static analysis (SpotBugs and/or PMD)
  - OWASP Dependency Check (or equivalent)
  - Dependabot + CodeQL (if easily enabled)
- **Observability**
  - Useful Actuator endpoints (health, info, metrics) with appropriate exposure
  - Structured logging confirmed and improved where needed
  - Basic JVM/HTTP metrics available
- **Production runtime**
  - Dockerfile (multi-stage preferred)
  - Health and readiness probes support
  - Clear production configuration profile (env-var driven, fail-fast)
- **Repository hygiene**
  - Add a `LICENSE` file
  - Ensure `.localstack` runtime files are not tracked
- **Light protection**
  - Basic rate limiting (per IP or per user) on authentication and other sensitive endpoints

**Out of scope:**

- Full Kubernetes manifests or advanced IaC
- Distributed tracing (OpenTelemetry) — future work
- Complex dashboards or alerting rules
- Performance / load testing
- Changing core business logic

---

## 2. Current gaps (from analysis)

| Area                | Current state             | Target                              |
| ------------------- | ------------------------- | ----------------------------------- |
| Coverage gate       | JaCoCo + threshold in CI  | — (gate active)                     |
| Static analysis     | SpotBugs + High threshold | — (fails on real issues)            |
| Dependency scanning | OWASP DepCheck configured | — (report only, no build fail)      |
| Container           | No Dockerfile             | — (Dockerfile created)              |
| Health              | Basic Actuator            | — (exposed endpoints confirmed)     |
| License             | LICENSE present           | — (Apache 2.0)                      |
| LocalStack files    | Fully ignored and cleaned | —                                   |
| Rate limiting       | Implemented (fixed-window) | — (basic rate limiting done)        |

---

## 3. Design principles

- Prefer tools that integrate cleanly with Maven and GitHub Actions
- Fail the build on real problems, not on noise
- Keep configuration externalised (Twelve-Factor)
- Minimal runtime overhead

---

## 4. Expected deliverables

- Maven plugin configuration for the quality tools
- Updated CI workflow
- `Dockerfile`
- `LICENSE`
- Rate-limiting configuration
- Short documentation of how to run and interpret the gates

---

## 5. Definition of Done

## 5. Definition of Done

- [x] Quality gates active and the JaCoCo gate fails below 35% line coverage
- [ ] Dockerfile builds and runs the application
- [x] LICENSE present (Apache 2.0)
- [x] Repository hygiene cleaned (.localstack removed from tracking)
- [x] Basic rate limiting working (implemented: `RateLimitFilter` + `FixedWindowRateLimiter`, `429` throttling on auth endpoints)
- [x] CI green (JaCoCo + SpotBugs pass)
- [x] Docs updated
```
