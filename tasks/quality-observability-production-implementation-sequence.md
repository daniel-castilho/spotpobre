### 4. `tasks/quality-observability-production-implementation-sequence.md`

```markdown
# Quality, Observability & Production Readiness — Implementation Sequence (P2)

**Companions:** `quality-observability-production-spec.md` · `quality-observability-production-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Analysis

1. Inspect current `pom.xml` plugins and CI workflow
2. Check Actuator configuration and exposed endpoints
3. Verify whether `.localstack` files are still tracked
4. Confirm absence of LICENSE and Dockerfile

**Done when:** Clear gap list exists.

---

## Step 1 — JaCoCo

- Add JaCoCo plugin
- Generate coverage reports
- Set a reasonable minimum coverage threshold
- Make CI fail when the threshold is not met

**Done when:** Coverage report is produced and the gate works.

---

## Step 2 — Static analysis

- Add SpotBugs and/or PMD
- Configure sensible rules (start strict on high/critical)
- Integrate into the Maven build and CI

**Done when:** Static analysis runs and fails the build on real issues.

---

## Step 3 — Dependency scanning

- Add OWASP Dependency Check (or equivalent)
- Run it in CI
- Decide how to handle known vulnerabilities (fail or report)

**Done when:** Dependency check is part of the pipeline.

---

## Step 4 — GitHub security features

- Enable Dependabot (or confirm it is active)
- Enable CodeQL if straightforward
- Document the decision

**Done when:** Automated dependency and code scanning are active.

---

## Step 5 — Actuator & observability

- Review which Actuator endpoints are exposed
- Ensure health, info and metrics are useful and not overly verbose in production
- Confirm structured logging works as expected

**Done when:** Operators can get clear health and basic metrics.

---

## Step 6 — Dockerfile

- Create a multi-stage Dockerfile
- Run the application with a non-root user if possible
- Support configuration via environment variables
- Document how to build and run

**Done when:** `docker build` produces a working image.

---

## Step 7 — Production configuration & probes

- Confirm `prod` profile fails fast on missing required env vars
- Expose clear liveness and readiness semantics via Actuator
- Document required environment variables

**Done when:** Production configuration is explicit and safe.

---

## Step 8 — Repository hygiene

- Add an appropriate `LICENSE` file
- Ensure `.localstack` (and similar runtime artefacts) are fully ignored and removed from tracking if still present

**Done when:** Repo is clean and licensed.

---

## Step 9 — Basic rate limiting

- Add a simple rate-limiting mechanism on authentication and other sensitive endpoints
- Prefer a lightweight, well-known solution
- Keep configuration externalised

**Done when:** Excessive requests are throttled.

---

## Step 10 — Final verification

- Full CI pipeline green with the new gates
- Docker image builds and starts
- Documentation (README, CHANGELOG, AGENTS.md) updated
- Short note on how to interpret the quality reports

**Done when:** Definition of Done is fully met.

---

## Smoke path

1. `./mvnw clean verify` → quality gates run and pass
2. CI pipeline on GitHub is green
3. `docker build -t spotpobre .` succeeds
4. Container starts and `/actuator/health` returns UP
5. LICENSE file is present at the root
6. Rapid repeated calls to a protected endpoint are rate-limited

---

_Pre-implementation sequence. After delivery, replace with an as-built status note._
```
