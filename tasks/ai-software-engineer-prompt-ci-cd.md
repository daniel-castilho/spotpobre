### 1. `tasks/ai-software-engineer-prompt-ci-cd.md`

```markdown
# AI Software Engineer Prompt — CI/CD Green & Reliable (P0)

**Status:** Complete — delivered in commit `aeb162e` (`aeb162e`). CI is green on main.
**Target:** Make the GitHub Actions pipeline consistently green and trustworthy
**Package:** `com.spotpobre.backend` + `.github/workflows`

You implement the complete fix so that the CI pipeline becomes reliable, deterministic and green on every push/PR.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/testing-playbook.md` · `docs/lessons.md` · `docs/coding-standards.md`
3. `tasks/ci-cd-spec.md` — what to build
4. `tasks/ci-cd-backlog.md` — stories
5. `tasks/ci-cd-implementation-sequence.md` — build order
6. Reference: current `.github/workflows/ci.yml`, `AbstractIntegrationTest`, `DynamoDbConfig`, `S3Config`, `SpotpobreApplicationTests`

---

## Goal

The GitHub Actions pipeline must pass reliably on a clean runner.

**Status:** This epic is **complete**. Delivered in commit `aeb162e` ("ci: make GitHub Actions pipeline
green and deterministic"). The pipeline is green end-to-end on a clean ubuntu runner — last verified
with 3 consecutive green runs on `main` as of 2026-08-19.

No further work needed unless regressions appear.

---

## Non-negotiable rules

- Tests that require Docker/LocalStack must be clearly separated from pure unit tests
- AWS clients used in tests must receive credentials that work both locally and on GitHub Actions (LocalStack)
- Spring contexts started in tests must be closed
- Do not lower test coverage or delete tests just to make CI green
- English only
- No new Maven dependencies without human approval
- Prefer fixing root causes over adding `@Disabled`
- Do not push unless the human asks

---

## Definition of Done (epic)

- [x] `./mvnw test` (unit + slice) passes on a clean environment (137 tests, no Docker)
- [x] Integration/E2E tests can be executed selectively and pass (`./mvnw test -Dtest='*IT'`)
- [x] GitHub Actions workflow is green on main and on PRs
- [x] Deprecated actions updated (`setup-java@v4` → `@v5`)
- [x] README statements about test commands are accurate
- [x] No flaky tests introduced

This epic is **complete** (commit `aeb162e`). No further action required.
```
