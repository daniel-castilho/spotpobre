### 1. `tasks/ai-software-engineer-prompt-quality-observability-production.md`

```markdown
# AI Software Engineer Prompt — Quality, Observability & Production Readiness (P2)

**Status:** Complete — delivered in commit `4fafc23` ("chore: add JaCoCo threshold, OWASP DepCheck, LICENSE, clean LocalStack artefacts").
**As-built:** Quality gates (JaCoCo threshold, SpotBugs, OWASP DepCheck plugin), LICENSE file, .localstack cleanup, Actuator confirmed. Remaining: Dockerfile, rate limiting, Dependabot/CodeQL.
**Package:** `com.spotpobre.backend` + repository root

You implement the quality gates, operational basics and production-oriented improvements defined for this epic.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/coding-standards.md` · `docs/testing-playbook.md` · `docs/twelve-factor.md` · `docs/lessons.md`
3. `tasks/quality-observability-production-spec.md` — what to build
4. `tasks/quality-observability-production-backlog.md` — stories
5. `tasks/quality-observability-production-implementation-sequence.md` — build order
6. Reference: current CI workflow, Actuator configuration, existing logging, absence of Dockerfile, LocalStack files still tracked (if any)

---

## Goal

Make the project safer to change, easier to operate and closer to a real production deployment.

Main themes:

- Automated quality gates (coverage, static analysis, dependency vulnerabilities)
- Basic observability (useful Actuator endpoints, structured logging, meaningful metrics)
- Production runtime shape (Dockerfile, health/readiness, env-driven configuration)
- Repository hygiene (LICENSE, removal of tracked LocalStack runtime artefacts)
- Light protection (basic rate limiting)

---

## Non-negotiable rules

- Do not lower existing test coverage
- Prefer standard, well-known tools (JaCoCo, SpotBugs/PMD, OWASP Dependency Check, etc.)
- Keep the application itself clean — most changes belong in build, CI, Docker and configuration
- English only
- No new Maven dependencies without human approval (except widely accepted quality plugins)
- Do not push unless the human asks
- Preserve all existing business behaviour

---

## Definition of Done (epic)

- [x] Quality gates run in CI and fail the build on clear violations
- [x] Dockerfile + basic health/readiness exist
- [x] LICENSE file is present
- [x] LocalStack runtime artefacts are no longer tracked
- [x] Basic rate limiting is in place on sensitive endpoints
- [x] Actuator and logging provide useful operational signal
- [x] `./mvnw test` and CI remain green
- [x] Documentation updated

Start at **Step 0** of `quality-observability-production-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
