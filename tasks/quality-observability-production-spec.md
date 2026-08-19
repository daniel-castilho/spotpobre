### 2. `tasks/quality-observability-production-spec.md`

```markdown
# Quality, Observability & Production Readiness — Technical Specification (P2)

**Status:** Draft for implementation
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
| Coverage gate       | None                      | JaCoCo + threshold in CI            |
| Static analysis     | None                      | SpotBugs / PMD                      |
| Dependency scanning | None                      | OWASP Dependency Check + Dependabot |
| Container           | No Dockerfile             | Production-ready Dockerfile         |
| Health              | Basic Actuator            | Clear liveness/readiness            |
| License             | Missing                   | LICENSE file present                |
| LocalStack files    | Some may still be tracked | Fully ignored and cleaned           |
| Rate limiting       | Absent                    | Basic protection                    |

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

- [ ] Quality gates active and failing the build when appropriate
- [ ] Dockerfile builds and runs the application
- [ ] LICENSE present
- [ ] Repository hygiene cleaned
- [ ] Basic rate limiting working
- [ ] CI green
- [ ] Docs updated
```
