#!/bin/sh
# Backfill per-owner playlist counters (OWNER_COUNT#<ownerId> items in the Playlists table)
# after adopting the transactional playlist limit. Owners whose playlists predate the counter
# have none, which makes the limit bind LATE for them (safe-side undercount). This script
# recomputes each counter from the real playlist rows so the limit is exact again.
#
# Idempotent: counters are overwritten with authoritative scanned values. Dry-run by default;
# pass --apply to write.
#
# Uses the host AWS CLI against LocalStack when it can parse emulated S3/DynamoDB responses,
# falling back to the bundled `awslocal` inside the running LocalStack container otherwise
# (same strategy as scripts/seed-localstack.sh).

set -eu

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
TABLE="Playlists"
TMP_SCAN="$(mktemp)"
trap 'rm -f "$TMP_SCAN"' EXIT

APPLY=0
if [ "${1:-}" = "--apply" ]; then
    APPLY=1
fi

command -v python3 >/dev/null || { echo "[backfill] python3 is required" >&2; exit 2; }

USE_CONTAINER=""
if ! AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
        aws dynamodb scan --table-name "$TABLE" --endpoint-url="$ENDPOINT" --region us-east-1 \
        >/dev/null 2>&1; then
    USE_CONTAINER="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -i localstack | head -1 || true)"
    if [ -z "$USE_CONTAINER" ]; then
        echo "[backfill] FATAL: host AWS CLI cannot reach DynamoDB at $ENDPOINT" >&2
        echo "[backfill]        and no running LocalStack container found for the fallback." >&2
        exit 2
    fi
    echo "[backfill] host CLI unusable — falling back to 'awslocal' inside container '$USE_CONTAINER'"
fi

if [ -n "$USE_CONTAINER" ]; then
    run_aws() { docker exec "$USE_CONTAINER" awslocal "$@"; }
else
    run_aws() {
        AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
            aws --endpoint-url="$ENDPOINT" --region us-east-1 --no-cli-pager "$@"
    }
fi

echo "[backfill] scanning real playlist rows..."
run_aws dynamodb scan --table-name "$TABLE" \
    --filter-expression "NOT begins_with(id, :prefix)" \
    --expression-attribute-values '{":prefix":{"S":"OWNER_COUNT#"}}' \
    --projection-expression "ownerId" \
    > "$TMP_SCAN"

# Emit "<ownerId> <count>" lines sorted by owner.
PLAN=$(python3 - "$TMP_SCAN" <<'PY'
import json, sys
from collections import Counter

with open(sys.argv[1]) as fh:
    data = json.load(fh)

counts = Counter(
    item["ownerId"]["S"]
    for item in data.get("Items", [])
    if "ownerId" in item
)
for owner_id, total in sorted(counts.items()):
    print(f"{owner_id} {total}")
PY
)

if [ -z "$PLAN" ]; then
    echo "[backfill] no playlist rows found — nothing to do."
    exit 0
fi

echo "$PLAN" | while read -r owner_id total; do
    if [ "$APPLY" = "1" ]; then
        run_aws dynamodb update-item --table-name "$TABLE" \
            --key "{\"id\":{\"S\":\"OWNER_COUNT#${owner_id}\"}}" \
            --update-expression "SET playlistCount = :n" \
            --expression-attribute-values "{\":n\":{\"N\":\"${total}\"}}" >/dev/null
        echo "[backfill] OWNER_COUNT#${owner_id} = ${total}"
    else
        echo "[backfill] would set OWNER_COUNT#${owner_id} = ${total} (dry-run; use --apply)"
    fi
done

if [ "$APPLY" != "1" ]; then
    echo "[backfill] dry-run complete. Re-run with --apply to write the counters."
else
    echo "[backfill] counters reconciled successfully!"
fi
