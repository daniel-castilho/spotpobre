# Performance baseline

Budgets-as-code for the read paths, executed with [k6](https://k6.io) via the pinned
`grafana/k6:2.2.0` container (no Maven dependency involved).

## Scenarios

| File | Path | Budget (consultative) |
| :--- | :--- | :--- |
| `scenarios/users-me.js` | `GET /api/v1/users/me` | p95 < 150 ms · p99 < 300 ms |
| `scenarios/song-search.js` | `GET /api/v1/songs/search` | p95 < 350 ms · p99 < 600 ms |
| `scenarios/artists-list.js` | `GET /api/v1/artists` | p95 < 250 ms · p99 < 500 ms |

All scenarios also require `http_req_failed rate < 1%`. Each registers a fresh user in
`setup()` (durable-idempotent registration, unique key) and reuses the bearer token.

## Philosophy

- **Trend over absolute numbers.** Shared CI runners are noisy; the value is detecting
  regressions between runs, not capacity planning.
- **Thresholds are deliberately loose** until 2-3 runs of artifact data calibrate realistic
  floors — then tighten the numbers and consider promoting the CI `performance` job
  (currently `continue-on-error`) to a hard gate.
- **Minimal catalog scope.** ADMIN/ARTIST seeding requires direct DynamoDB writes (no public
  API), so search/list run against a near-empty catalog. The numbers therefore measure the
  infrastructure overhead floor (JWT filter chain → use case → DynamoDB → serialization),
  not behavior at scale. Capacity testing is a separate future effort.

## Running locally

Prerequisites: compose stack up (`docker-compose up -d`), schema provisioned
(`scripts/seed-localstack.sh`), production jar built (`./mvnw clean package -DskipTests`),
Docker available (Linux host or `--network host` support).

```bash
scripts/performance-baseline.sh            # default jar/port
scripts/performance-baseline.sh target/spotpobre-api-0.0.1-SNAPSHOT.jar 8080
```

JSON summaries land in `perf/results/` (gitignored). Threshold breaches exit non-zero.
