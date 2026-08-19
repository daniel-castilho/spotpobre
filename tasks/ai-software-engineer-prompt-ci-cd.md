### 1. `tasks/ai-software-engineer-prompt-ci-cd.md`

```markdown
# AI Software Engineer Prompt — CI/CD Green & Reliable (P0)

**Status:** Not implemented — critical infrastructure epic.
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
Currently every public run fails at the “Unit + slice tests” step, so E2E and production build never execute. The root causes are credential handling, test isolation, and deprecated actions.

No new features. Focus only on making the existing test suite and pipeline trustworthy.

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

- [ ] `./mvnw test` (unit + slice) passes on a clean environment
- [ ] Integration/E2E tests can be executed selectively and pass
- [ ] GitHub Actions workflow is green on main and on PRs
- [ ] Deprecated actions updated
- [ ] README statements about test commands are accurate
- [ ] No flaky tests introduced

Start at **Step 0** of `ci-cd-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
