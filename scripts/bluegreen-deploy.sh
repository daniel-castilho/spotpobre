#!/usr/bin/env bash
# S10/S11 — Blue/green deploy for the on-premises production target (ADR-0002).
#
# Traffic is controlled by the `weight=` values of the NGINX upstream. Weights are never
# edited in the tracked template (deploy/nginx-bluegreen.conf); the script writes a runtime
# copy (deploy/nginx-bluegreen.runtime.conf, gitignored) and pushes it into the loadbalancer
# container (`docker compose cp` + `nginx -s reload`) — the stand-in for an ALB weighted
# target-group shift / CodeDeploy canary.
#
# Sequence (health-gated):
#   [optional] recreate green with the given image tag
#   green readiness must be UP (gate)
#   -> canary: green 10% (observation window; any readiness loss aborts to blue 100%)
#   -> cutover: green 100%, blue drained to 0%
#
# Usage:
#   scripts/bluegreen-deploy.sh [IMAGE_TAG]
#     IMAGE_TAG  optional new image for green (e.g. spotpobre-api:v2). Without it, the
#                currently configured green fleet is rolled out.
set -euo pipefail
cd "$(dirname "$0")/.."

CONF_TEMPLATE="deploy/nginx-bluegreen.conf"
RUNTIME_CONF="deploy/nginx-bluegreen.runtime.conf"
COMPOSE="docker compose -f deploy/docker-compose.bluegreen.yml"
CANARY_PERCENT=10
OBSERVATION_SECONDS=30
[ -f "$CONF_TEMPLATE" ] || { echo "FATAL: $CONF_TEMPLATE not found"; exit 2; }

log() { printf '[deploy] %s\n' "$*"; }
fail() { printf '[deploy] FAIL: %s\n' "$*" >&2; exit 1; }

# Emits an upstream server line: weight=N for active fleets, `down` for 0%
# (NGINX has no weight=0).
upstream_line() {
  local host="$1" port="$2" weight="$3"
  if [ "$weight" -gt 0 ]; then
    printf '    server %s:%s max_fails=3 fail_timeout=10s weight=%s;' "$host" "$port" "$weight"
  else
    printf '    server %s:%s max_fails=3 fail_timeout=10s down;' "$host" "$port"
  fi
}

set_weight() {
  local blue="$1" green="$2"
  cp "$CONF_TEMPLATE" "$RUNTIME_CONF"
  sed -i \
    -e "s|^    server blue:8081.*|$(upstream_line blue 8081 "$blue")|" \
    -e "s|^    server green:8082.*|$(upstream_line green 8082 "$green")|" "$RUNTIME_CONF"
  $COMPOSE cp "$RUNTIME_CONF" loadbalancer:/etc/nginx/conf.d/default.conf
  $COMPOSE exec -T loadbalancer nginx -s reload 2>/dev/null || true
}

health_ok() {
  local port="$1"
  curl -sf "http://localhost:${port}/actuator/health/readiness" >/dev/null 2>&1
}

# --- Optional: roll the requested image out to green before shifting traffic ----------
if [ $# -ge 1 ]; then
  log "recreating green fleet with image '$1'..."
  IMAGE_GREEN="$1" $COMPOSE up -d --no-deps green
fi

# --- Health gate: green must be ready before receiving any traffic --------------------
log "health gate: green readiness (must be 200)..."
for i in $(seq 1 30); do
  if health_ok 8082; then log "green readiness OK"; break; fi
  [ "$i" = "30" ] && fail "green never became ready"
  sleep 2
done

# --- Canary shift ---------------------------------------------------------------------
log "canary: blue 100 -> green ${CANARY_PERCENT}"
set_weight 100 "$CANARY_PERCENT"

log "observation window (${CANARY_PERCENT}% green for ${OBSERVATION_SECONDS}s)..."
for i in $(seq 1 "$OBSERVATION_SECONDS"); do
  if ! health_ok 8082; then
    log "green readiness lost during canary -> rolling back to blue 100%"
    set_weight 100 0
    exit 1
  fi
  sleep 1
done

# --- Cutover --------------------------------------------------------------------------
log "full cutover: green 100, blue drain 0"
set_weight 0 100
sleep 3

code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health/readiness)"
[ "$code" = "200" ] || { log "post-cutover readiness=$code -> rolling back"; set_weight 100 0; exit 1; }

log "PASS: green serving 100% (readiness 200). Blue stays up at 0% for instant rollback:"
log "      scripts/bluegreen-rollback.sh"
