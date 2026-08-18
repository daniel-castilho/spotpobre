# Twelve-Factor App — Reference & Compliance

The project follows the [Twelve-Factor App](https://12factor.net/) methodology (originating from
Heroku). Goal: a codebase that deploys identically to any environment (dev, staging, prod) with no
code changes, reproducible builds, and easy horizontal scaling.

> **This is a commitment, not a suggestion.** When writing or reviewing code, check the factor
> affected by the change and keep the table below green.

## The 12 factors and how Spotpobre API complies

| # | Factor           | Spotpobre status | Notes |
| - | ---------------- | ---------------- | ----- |
| 1 | Codebase         | ✅ One repo, one app | Git repo `spotpobre-api`, `main` branch. No per-environment branches. No tags yet (`0.0.1-SNAPSHOT`). |
| 2 | Dependencies     | ✅ Declared & pinned | `pom.xml` with `dependencyManagement` (spring-cloud-aws / testcontainers / rest-assured BOMs) and the Maven wrapper pinned to Maven 3.9.11 (`.mvn/wrapper/maven-wrapper.properties`). Reproducible build: `./mvnw clean package`. No lockfile equivalent in Maven — keep versions in `<properties>`. |
| 3 | Config           | ✅ Env contract     | Dev-only defaults live in `application.yaml` (`jwt.secret` example, AWS endpoints → `localhost:4566`, Redis → `localhost:6379`). **`application-prod.yaml` documents the production contract**: every env-specific value binds from env vars (`JWT_SECRET`, `AWS_REGION`, `AWS_DYNAMODB_ENDPOINT`, `AWS_S3_ENDPOINT`, `AWS_S3_BUCKET_NAME`, `REDIS_HOST`) and **`ProdConfigValidator` (prod profile only, ordered before the AWS clients) aborts startup** with a message naming the first missing variable. Activate with `SPRING_PROFILES_ACTIVE=prod`. `application-dev.yaml` intentionally empty — dev uses the baked defaults. |
| 4 | Backing services | ✅ Attached resources | DynamoDB, S3 (both via LocalStack) and Redis are attached external resources addressed by endpoint/config (`docker-compose.yaml` + `application.yaml`). No backing service is embedded in the app. |
| 5 | Build, release, run | ⚠️ Partial       | Build = `./mvnw clean package` → executable Spring Boot jar; run = `java -jar ...` or `./mvnw spring-boot:run`. **CI workflow added** (`.github/workflows/ci.yml`) running unit+slice tests, the `*IT` E2E suite (Docker/Testcontainers) and the production build on every push/PR — first green run pending until the repo is pushed to GitHub. DynamoDB tables/bucket are created by the `awslocal` setup block in `README.md` (dev) — no versioned schema migration tooling yet. |
| 6 | Processes        | ✅ Stateless       | Auth is stateless JWT; session-free. Cache (Redis) is a shared attached resource. No in-memory state assumed across requests. |
| 7 | Port binding     | ✅ Self-contained   | Spring Boot embedded web server binds `:8080`; no external web server injected. |
| 8 | Concurrency      | ✅ Process-based    | Stateless service scales by spawning processes; each is a copy of the same app. |
| 9 | Disposability    | ✅ Graceful         | Spring Boot boots quickly; `server.shutdown: graceful` with a 30s per-phase timeout drains in-flight requests before stopping on `SIGTERM`. |
| 10 | Dev/prod parity  | ✅ Containers       | `docker-compose up -d` (LocalStack DynamoDB + S3, Redis) keeps local close to prod. |
| 11 | Logs             | ✅ Structured       | `logback-spring.xml` wires the declared `logstash-logback-encoder`: default profile uses Spring Boot's console pattern; the `json` profile emits structured JSON lines (LogstashEncoder) to stdout for aggregation. `GlobalExceptionHandler` logs unexpected errors with request context. Never log passwords, JWT secrets or token payloads. |
| 12 | Admin processes  | ⚠️ Partial          | One-off tasks run as separate commands outside the app: `awslocal dynamodb create-table ...` / `awslocal s3 mb ...` (README setup block); the IT base class (`AbstractIntegrationTest`) provisions the same schema for tests. **TBD:** versioned schema changes (tables/GSIs) as a repeatable, committed step. |

Legend: ✅ compliant · ⚠️ partially compliant / has an open TODO.

## Hard rules to keep the list green

- Never hardcode environment-specific values (URLs, secrets, credentials) in production code paths.
  Read them from env vars, with dev-only defaults kept in `application.yaml`.
- Secrets live only in env vars / the deployed environment — never in git, code, or logs. The
  `jwt.secret` in `application.yaml` is a dev-only example.
- Every build must be reproducible: `./mvnw clean package` from a clean checkout. Do not rely on
  artifacts left in `target/`.
- Schema changes (DynamoDB tables/GSIs) are shipped as part of the change set — update the README
  setup block and the `docker-compose` LocalStack environment together; never apply them by hand to
  a shared environment only.
- Local dev must match production dependencies as closely as possible (use `docker-compose`).

## Open TODOs (tracked)

1. Introduce a repeatable, versioned schema step for DynamoDB tables/GSIs (no migration tooling
   today; tables are created via the README `awslocal` block and the IT `@BeforeAll` provisioner).
2. Confirm the first CI green run once the repo is pushed to GitHub (workflow
   `.github/workflows/ci.yml` runs `./mvnw test`, `./mvnw test -Dtest='*IT'` and
   `./mvnw clean package`).
3. Document the production deployment runtime shape (container image / platform) — the env-var
   contract is defined in `application-prod.yaml`; how the artifact is shipped and run is not.