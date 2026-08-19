### 2. `tasks/ci-cd-spec.md`

```markdown
# CI/CD Green & Reliable — Technical Specification (P0)

**Status:** Complete — implemented in commit `aeb162e` ("ci: make GitHub Actions pipeline green and deterministic")
**Focus:** Make the continuous integration pipeline deterministic and green
**Companions:** `ci-cd-backlog.md` · `ci-cd-implementation-sequence.md`

---

## 1. Purpose & scope

Restore a trustworthy CI pipeline.

**In scope (P0):**

- Fix AWS credential provider mismatch between test properties and manually created clients (`DynamoDbConfig`, `S3Config`)
- Separate pure unit tests from tests that require Docker/LocalStack
- Ensure all Spring contexts started during tests are properly closed
- Update deprecated GitHub Actions (`actions/setup-java@v4` → current recommended version)
- Make the workflow execute unit/slice tests reliably and, when green, continue to E2E and package
- Align README claims with actual test behaviour

**Out of scope:**

- Adding new quality gates (JaCoCo, SpotBugs, etc.) — later epic
- Changing application production configuration
- Implementing new features
- Full rewrite of the test suite

---

## 2. Root causes (from analysis)

1. `AbstractIntegrationTest` sets `aws.credentials.access-key` / `aws.credentials.secret-key` / `aws.region` / `aws.*.endpoint` via `@DynamicPropertySource`, but `DynamoDbConfig` and `S3Config` build clients with `DefaultCredentialsProvider` (in the original version), which ignores those properties on a clean runner.
2. Some tests annotated or named in a way that forces LocalStack even in the default `./mvnw test` run.
3. `SpotpobreApplicationTests` calls `SpringApplication.run()` and never closes the context.
4. The GitHub Actions workflow originally used `actions/setup-java@v4`, which was deprecated.
5. Because the first test step fails, later steps (E2E, build) are skipped.

---

## 3. Target behaviour

- Pure unit tests (domain + application) run without Docker and without AWS credentials.
- Slice / integration tests that need LocalStack run only when explicitly requested or in a dedicated CI job.
- All AWS clients used in tests are configured with a credentials provider that works with LocalStack (static test credentials or the properties already set by the test base class).
- GitHub Actions workflow is green end-to-end on a clean ubuntu runner.

---

## 4. Key files expected to change

- `DynamoDbConfig.java` / `S3Config.java` (credentials provider)
- `AbstractIntegrationTest` (or equivalent base class)
- `SpotpobreApplicationTests`
- Test categories / Surefire configuration (if needed)
- `.github/workflows/ci.yml`
- README.md (test commands section)

---

## 5. Testing & verification requirements

- Local reproduction of the previous failure mode
- Confirmation that unit tests pass without Docker
- Confirmation that integration tests pass with LocalStack
- Successful GitHub Actions run after the changes

---

## 6. Definition of Done

- [x] Pipeline green on GitHub Actions (confirmed: last 3 runs on main all green)
- [x] Credential handling works on clean runners (`StaticCredentialsProvider` from `AwsProperties`)
- [x] Contexts are closed (`SpotpobreApplicationTests.mainApplicationStarts()` uses try-with-resources)
- [x] Deprecated actions updated (`actions/setup-java@v4` → `@v5`)
- [x] Documentation matches reality (README, AGENS.md updated 2026-08-19)
```
