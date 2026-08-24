#!/usr/bin/env bash
# Performance baseline — k6 scenarios against a locally running instance.
#
# Runs the three foundation read-path scenarios (users-me, song-search,
# artists-list) via the pinned grafana/k6 container, enforcing the thresholds
# declared inside each scenario file. Results (JSON summaries) land in
# perf/results/. Threshold failures exit non-zero.
#
# Usage:
#   scripts/performance-baseline.sh [JAR] [PORT]
#     JAR   path to the application jar (default target/spotpobre-api-0.0.1-SNAPSHOT.jar)
#     PORT  app port (default 8080)
#
# Prerequisites: LocalStack + Redis running (docker-compose up -d) with the schema
# provisioned (scripts/seed-localstack.sh), Docker available for the k6 image, and
# the jar already built (./mvnw clean package -DskipTests). Uses --network host,
# so it requires Linux (GitHub runners and typical dev hosts qualify; on macOS
# publish the port or run k6 with host.docker.internal instead).
# Exits 0 when every scenario passes its thresholds, 1 otherwise.

set -euo pipefail

JAR="${1:-target/spotpobre-api-0.0.1-SNAPSHOT.jar}"
PORT="${2:-8080}"
BASE_URL="http://localhost:${PORT}"
K6_IMAGE="grafana/k6:2.2.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(dirname "$SCRIPT_DIR")/perf"
RESULTS_DIR="$PERF_DIR/results"

log() { printf '[perf] %s\n' "$*"; }

fail() {
    printf '[perf] FAIL: %s\n' "$*" >&2
    if [ -f /tmp/spotpobre-perf-app.log ]; then
        printf '[perf] --- application log (last 40 lines) ---\n' >&2
        tail -n 40 /tmp/spotpobre-perf-app.log >&2 || true
    fi
    exit 1
}

[ -f "$JAR" ] || fail "jar not found at $JAR (build with ./mvnw clean package -DskipTests)"
command -v curl >/dev/null || fail "curl is required"
command -v docker >/dev/null || fail "docker is required (k6 runs as a container)"

mkdir -p "$RESULTS_DIR"
rm -f "$RESULTS_DIR"/*-summary.json

APP_PID=""
cleanup() {
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        kill -TERM "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

log "starting application ($JAR on :$PORT)..."
java -jar "$JAR" > /tmp/spotpobre-perf-app.log 2>&1 &
APP_PID=$!

ready=""
for _ in $(seq 1 120); do
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$BASE_URL/actuator/health/readiness" 2>/dev/null || true)
    if [ "$code" = "200" ]; then ready=1; break; fi
    sleep 0.5
done
[ -n "$ready" ] || fail "application did not become ready within 60s"
log "readiness UP"

# Warm JVM/JIT and caches so measured iterations are representative.
TOKEN_WARMUP=$(curl -s --max-time 10 -X POST "$BASE_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: perf-warmup-$(date +%s)-a1b2c3d4e5f6g7h8" \
    -d '{"name":"Perf Warmup","email":"perf-warmup-'"$(date +%s)"'@example.com","password":"password123","country":"US"}' \
    | python3 -c "import sys,json;print(json.load(sys.stdin).get('token',''))" 2>/dev/null || true)
[ -n "$TOKEN_WARMUP" ] || fail "warm-up registration failed"
for _ in 1 2 3 4 5; do
    curl -s -o /dev/null --max-time 5 \
        -H "Authorization: Bearer $TOKEN_WARMUP" \
        "$BASE_URL/api/v1/users/me"
done
log "warmed up"

FAILED=0
# Docker Desktop's host-gateway alias does not forward to the host loopback
# (and --network host is a no-op inside its VM), so reach the app through the
# host's own IP; Spring Boot listens on all interfaces by default.
HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
[ -n "$HOST_IP" ] || HOST_IP=host.docker.internal
for SCENARIO in users-me song-search artists-list; do
    log "running scenario '$SCENARIO'..."
    # The container runs as the invoking user so it can write summaries into the
    # mounted results directory.
    docker run --rm \
        --user "$(id -u):$(id -g)" \
        -e BASE_URL="http://${HOST_IP}:${PORT}" \
        -v "$PERF_DIR:/perf" \
        "$K6_IMAGE" run --summary-export="/perf/results/$SCENARIO-summary.json" \
        "/perf/scenarios/$SCENARIO.js" > "/tmp/spotpobre-perf-$SCENARIO.out" 2>&1 \
        || { grep -E '✗|thresholds|levels' "/tmp/spotpobre-perf-$SCENARIO.out" | head -20 >&2 || true; FAILED=1; }

    if [ -f "$RESULTS_DIR/$SCENARIO-summary.json" ]; then
        python3 - "$RESULTS_DIR/$SCENARIO-summary.json" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    m = json.load(f)["metrics"]
dur = m["http_req_duration"]
reqs = m["http_reqs"]["count"]
fails = m["http_req_failed"]["value"]
name = sys.argv[1].split("/")[-1].replace("-summary.json", "")
print(f"[perf] {name}: {reqs} reqs | med={dur['med']:.1f}ms "
      f"p90={dur['p(90)']:.1f}ms p95={dur['p(95)']:.1f}ms avg={dur['avg']:.1f}ms "
      f"fail_rate={fails:.2%}")
PY
    else
        printf '[perf] %s: no summary produced\n' "$SCENARIO" >&2
        FAILED=1
    fi
done

[ "$FAILED" -eq 0 ] || fail "one or more scenarios breached their thresholds (see output above)"
log "PASS — all scenarios within budget; summaries in perf/results/"
