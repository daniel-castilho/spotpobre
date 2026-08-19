### 3. `tasks/ci-cd-backlog.md`

```markdown
# CI/CD Green & Reliable — Backlog (P0)

**Companions:** `ci-cd-spec.md` · `ci-cd-implementation-sequence.md`
**Epic goal:** Make the CI pipeline consistently green and trustworthy.

**MVP:** S1–S8

---

## Story map
```

FOUNDATION
S1 Credential provider fix
S2 Test isolation (unit vs IT)

STABILITY
S3 Close Spring contexts
S4 Surefire / failsafe configuration

PIPELINE
S5 Update GitHub Actions
S6 Workflow job structure

DOCS & VERIFICATION
S7 README accuracy
S8 Final green pipeline confirmation

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Make DynamoDB and S3 clients use credentials compatible with LocalStack/test properties | Must | Root cause of most failures |
| S2 | Clearly separate pure unit tests from Docker-dependent tests | Must | |
| S3 | Ensure every test that starts a Spring context closes it | Must | Memory / port leaks |
| S4 | Adjust Maven Surefire/Failsafe so default `test` goal is safe | Must | |
| S5 | Update deprecated actions (setup-java, etc.) | Must | |
| S6 | Make workflow continue to E2E and package only after unit/slice succeed | Must | |
| S7 | Correct README statements about which commands need Docker | Must | |
| S8 | Verify full pipeline green on GitHub Actions | Must | Final gate |

---

## Definition of Done (epic)

- [ ] S1–S8 done
- [ ] Latest workflow run on main/PR is green
- [ ] `./mvnw test` passes on a clean machine without AWS credentials
- [ ] Integration tests pass when Docker is available

---

## Status

**Not started.** Critical P0 — no reliable feedback loop exists today.
```
