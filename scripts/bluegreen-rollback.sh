#!/usr/bin/env bash
# S10/S11 — Rollback for the on-premises blue/green target (ADR-0002).
#
# Shifts 100% of the traffic back to blue (green weight 0) and stops the green fleet —
# the stand-in for a CodeDeploy rollback (traffic revert + green task-set termination).
# Uses the same runtime-conf mechanism as the deploy script; the tracked template is
# never modified.
#
# Usage: scripts/bluegreen-rollback.sh
set -euo pipefail
cd "$(dirname "$0")/.."

CONF_TEMPLATE="deploy/nginx-bluegreen.conf"
RUNTIME_CONF="deploy/nginx-bluegreen.runtime.conf"
COMPOSE="docker compose -f deploy/docker-compose.bluegreen.yml"
[ -f "$CONF_TEMPLATE" ] || { echo "FATAL: $CONF_TEMPLATE not found"; exit 2; }

log() { printf '[rollback] %s\n' "$*"; }

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

cp "$CONF_TEMPLATE" "$RUNTIME_CONF"
sed -i \
  -e "s|^    server blue:8081.*|$(upstream_line blue 8081 100)|" \
  -e "s|^    server green:8082.*|$(upstream_line green 8082 0)|" "$RUNTIME_CONF"
$COMPOSE cp "$RUNTIME_CONF" loadbalancer:/etc/nginx/conf.d/default.conf
$COMPOSE exec -T loadbalancer nginx -s reload 2>/dev/null || true
log "traffic reverted to blue (100/0)"

$COMPOSE stop green || true
log "green fleet stopped (kept for inspection; it stays out of traffic rotation)"

sleep 3
code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health/readiness)"
if [ "$code" = "200" ]; then
  log "PASS: blue serving 100% (readiness 200)"
else
  log "WARNING: blue readiness=$code — inspect fleet health"
  exit 1
fi
