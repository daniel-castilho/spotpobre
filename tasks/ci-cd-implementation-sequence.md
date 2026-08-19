### 4. `tasks/ci-cd-implementation-sequence.md`

```markdown
# CI/CD Green & Reliable — Implementation Sequence (P0)

**Companions:** `ci-cd-spec.md` · `ci-cd-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Diagnosis

1. Reproduce the failure locally or inspect the latest GitHub Actions logs
2. Confirm the exact credential provider used in `DynamoDbConfig` and `S3Config`
3. List all tests that start LocalStack or full Spring Boot context
4. Identify deprecated actions in `.github/workflows/ci.yml`

**Done when:** Root causes are confirmed and a short written list of failing points exists.

---

## Step 1 — Credential provider fix

- Change the way `S3Client` and `DynamoDbClient` (or Enhanced Client) are built so they honour the credentials supplied by the test environment / LocalStack.
- Preferred approaches (choose the cleanest that matches existing style):
  - Use `StaticCredentialsProvider` with test values when a specific profile/property is present
  - Or make the beans pick up `spring.cloud.aws.credentials.*` correctly
- Keep production behaviour unchanged.

**Done when:** Integration tests can obtain a working client on a clean runner with LocalStack.

---

## Step 2 — Test isolation

- Ensure pure domain and application unit tests have zero Docker / LocalStack dependency.
- Move or tag tests that require Testcontainers/LocalStack so they are not executed by the default `./mvnw test` goal (or document the exact command).
- Update Surefire configuration if necessary.

**Done when:** `./mvnw test` runs successfully without Docker.

---

## Step 3 — Context lifecycle

- Fix `SpotpobreApplicationTests` (and any similar test) so the Spring context is closed after the test.
- Prefer `@SpringBootTest` + proper cleanup over manual `SpringApplication.run()`.

**Done when:** No leftover contexts or port conflicts after the test suite.

---

## Step 4 — Maven configuration polish

- Confirm Surefire and Failsafe plugins behave as intended.
- Make the distinction between unit and IT clear (naming `*IT`, failsafe, or profiles).

**Done when:** Default test goal is fast and safe; IT goal is explicit.

---

## Step 5 — GitHub Actions update

- Replace deprecated actions (`actions/setup-java@v4` → current major version recommended by GitHub).
- Ensure the workflow:
  1. Runs unit + slice tests
  2. Only proceeds to E2E / package if the previous step succeeds
  3. Uses consistent Java 21 and Maven wrapper

**Done when:** Workflow file is updated and validates.

---

## Step 6 — Documentation alignment

- Update README sections that claim `./mvnw test` needs no Docker (or change the command).
- Document the exact commands for unit vs integration vs E2E.

**Done when:** README matches reality.

---

## Step 7 — End-to-end verification

1. Push the changes (or open a PR)
2. Confirm the GitHub Actions run is fully green
3. Optionally re-run the previous failing commit's scenario to prove the fix

**Done when:** Pipeline is green and the Definition of Done is satisfied.

---

## Smoke path

1. On a clean machine (or CI runner) with no AWS credentials:
   - `./mvnw test` → passes
2. With Docker available:
   - Integration / E2E tests → pass
3. GitHub Actions → full workflow green

---

## As-built status (delivered in commit `aeb162e`)

All steps completed. The CI pipeline is green end-to-end on a clean ubuntu runner:

1. **Credential provider fix** — `DynamoDbConfig` and `S3Config` now build clients with
   `StaticCredentialsProvider` from `AwsProperties.credentials()` (bound from
   `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` env vars with LocalStack-compatible `test`
   defaults), instead of `DefaultCredentialsProvider`. `ProdConfigValidator` requires the two
   credential vars in the prod profile.
2. **Test isolation** — Docker-dependent adapter/E2E tests renamed from `*Test` to `*IT`
   (e.g. `DynamoDbPlaylistRepositoryAdapterTest` → `IT`, `S3SongStorageAdapterTest` → `IT`).
   `./mvnw test` runs only pure unit tests (137 tests, no Docker needed). `*IT` tests run via
   `./mvnw test -Dtest='*IT'` or in CI.
3. **Context lifecycle** — `SpotpobreApplicationTests.mainApplicationStarts()` starts the
   context in a try-with-resources so it is closed instead of leaking.
4. **Maven configuration** — Surefire runs `*Test` only; `*IT` tests are excluded from the
   default `test` goal and run explicitly or in CI.
5. **GitHub Actions** — `actions/setup-java@v4` → `@v5`; `actions/checkout@v4` retained (current).
   Workflow structure: Unit tests → SpotBugs → Slice+E2E → Production build, each gated on
   `if: success()`.
6. **README accuracy** — Commands table and Testing sections now state `./mvnw test` needs
   no Docker, and `*IT` tests require it.
7. **End-to-end verification** — Latest 3 CI runs on `main` are green; `./mvnw test` = 137
   green (no Docker); `./mvnw test -Dtest='*IT'` = 8 green; `./mvnw clean package` = SUCCESS.
```
