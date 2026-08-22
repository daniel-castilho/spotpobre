#!/bin/sh
# Backfill OWNER memberships for artists that predate the ArtistAccounts table.
#
# The ownership model (docs/data-model-decisions.md → "User ↔ Artist ownership")
# requires every artist to have at least one OWNER account. Artists created before
# that table existed are unowned and fail closed for non-admin actors. This script
# assigns a designated user as OWNER of each unowned artist.
#
# Usage:
#   ./scripts/backfill-artist-accounts.sh <owner-user-id> [--apply]
#
# Behaviour:
#   - DRY-RUN by default: prints what would change without writing.
#   - --apply performs the writes.
#   - Idempotent: artists that already own at least one ArtistAccounts entry are
#     skipped, so the script can be re-run safely.
#
# Uses the same host-CLI / awslocal-in-container fallback as seed-localstack.sh.

set -eu

if [ "$#" -lt 1 ]; then
    echo "usage: $0 <owner-user-id> [--apply]" >&2
    exit 1
fi

OWNER_USER_ID="$1"
APPLY=0
[ "${2:-}" = "--apply" ] && APPLY=1

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"

USE_CONTAINER=""
if ! aws s3api list-buckets --endpoint-url="$ENDPOINT" --region us-east-1 \
        >/dev/null 2>&1; then
    USE_CONTAINER="$(docker ps --format '{{.Names}}' 2>/dev/null \
        | grep -i localstack | head -1 || true)"
    if [ -z "$USE_CONTAINER" ]; then
        echo "[backfill] FATAL: host AWS CLI cannot reach LocalStack at $ENDPOINT" >&2
        echo "[backfill]        and no running LocalStack container found for the fallback." >&2
        exit 2
    fi
    echo "[backfill] falling back to 'awslocal' inside container '$USE_CONTAINER'"
fi

if [ -n "$USE_CONTAINER" ]; then
  run_aws() { docker exec "$USE_CONTAINER" awslocal "$@"; }
else
  run_aws() {
    AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
        aws --endpoint-url="$ENDPOINT" --region us-east-1 --no-cli-pager "$@"
  }
fi

echo "[backfill] scanning Artists (owner will be ${OWNER_USER_ID})${APPLY:+ [APPLY]}"
BACKFILLED=0
SKIPPED=0

ARTIST_IDS=$(run_aws dynamodb scan --table-name Artists \
    --projection-expression "id" \
    --query 'Items[].id.S' --output text)

for artist_id in $ARTIST_IDS; do
    existing=$(run_aws dynamodb query --table-name ArtistAccounts \
        --key-condition-expression 'artistId = :a' \
        --expression-attribute-values '{":a":{"S":"'"$artist_id"'"}}' \
        --select COUNT --query 'Count' --output text 2>/dev/null || echo 0)

    if [ "${existing:-0}" -gt 0 ]; then
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    if [ "$APPLY" -eq 1 ]; then
        run_aws dynamodb put-item --table-name ArtistAccounts \
            --item '{'\
'"artistId":{"S":"'"$artist_id"'"},'\
'"userId":{"S":"'"$OWNER_USER_ID"'"},'\
'"permission":{"S":"OWNER"},'\
'"createdAt":{"S":"'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'"}'} >/dev/null
        echo "[backfill] artist ${artist_id}: OWNER granted to ${OWNER_USER_ID}"
    else
        echo "[backfill] artist ${artist_id}: WOULD grant OWNER to ${OWNER_USER_ID}"
    fi
    BACKFILLED=$((BACKFILLED + 1))
done

echo "[backfill] done: ${BACKFILLED} artist(s) ${APPLY:+written}${APPLY:-would be written}, ${SKIPPED} already owned — ok"
[ "$APPLY" -eq 1 ] || echo "[backfill] dry-run only; re-run with --apply to write"
