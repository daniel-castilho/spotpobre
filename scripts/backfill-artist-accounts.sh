#!/bin/sh
# Backfill OWNER/MANAGER memberships for artists that predate the ArtistAccounts table.
#
# The ownership model (docs/data-model-decisions.md → "User ↔ Artist ownership")
# requires every artist to have at least one OWNER account. Artists created before
# that table existed are unowned and fail closed for non-admin actors.
#
# Usage (spec §6.4 — explicit mapping, repeatable):
#   ./scripts/backfill-artist-accounts.sh --map ARTIST_ID,USER_ID,PERMISSION \
#       [--map ...] [--apply]
#
# Legacy usage (assign one owner to every unowned artist):
#   ./scripts/backfill-artist-accounts.sh OWNER_USER_ID [--apply]
#
# Behaviour:
#   - DRY-RUN by default: prints what would change without writing.
#   - --apply performs the writes.
#   - Validates that each mapped artist and user actually exist before writing
#     (fail fast, no partial application of a broken mapping).
#   - PERMISSION is OWNER or MANAGER.
#   - Idempotent: artists that already own at least one ArtistAccounts entry are
#     skipped in legacy mode; explicit mappings write the same PK/SK row, so
#     re-running converges to the same state.
#
# Uses the same host-CLI / awslocal-in-container fallback as seed-localstack.sh.

set -eu

if [ "$#" -lt 1 ]; then
    echo "usage: $0 (--map ARTIST_ID,USER_ID,PERMISSION | OWNER_USER_ID) [--apply]" >&2
    exit 1
fi

APPLY=0
MAPS=""
LEGACY_OWNER=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        --map)
            [ -n "${2:-}" ] || { echo "[backfill] FATAL: --map requires ARTIST_ID,USER_ID,PERMISSION" >&2; exit 1; }
            MAPS="$MAPS $2"
            shift 2 ;;
        --apply)
            APPLY=1; shift ;;
        *)
            LEGACY_OWNER="$1"; shift ;;
    esac
done

if [ -z "$MAPS" ] && [ -z "$LEGACY_OWNER" ]; then
    echo "[backfill] FATAL: provide at least one --map or a legacy owner-user-id" >&2
    exit 1
fi

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

# get-item prints the literal string "None" when the key is absent — compare, don't grep.
user_exists() {
    [ "$(run_aws dynamodb get-item --table-name Users \
        --key '{"id":{"S":"'"$1"'"}}' \
        --projection-expression "id" --output text \
        --query 'Item.id.S' 2>/dev/null)" != "None" ]
}

artist_exists() {
    [ "$(run_aws dynamodb get-item --table-name Artists \
        --key '{"id":{"S":"'"$1"'"}}' \
        --projection-expression "id" --output text \
        --query 'Item.id.S' 2>/dev/null)" != "None" ]
}

grant() {
    ARTIST_ID="$1"; USER_ID="$2"; PERMISSION="$3"
    if ! artist_exists "$ARTIST_ID"; then
        echo "[backfill] FATAL: artist $ARTIST_ID does not exist" >&2
        exit 3
    fi
    if ! user_exists "$USER_ID"; then
        echo "[backfill] FATAL: user $USER_ID does not exist" >&2
        exit 3
    fi
    if [ "$APPLY" -eq 1 ]; then
        run_aws dynamodb put-item --table-name ArtistAccounts \
            --item '{'\
'"artistId":{"S":"'"$ARTIST_ID"'"},'\
'"userId":{"S":"'"$USER_ID"'"},'\
'"permission":{"S":"'"$PERMISSION"'"},'\
'"createdAt":{"S":"'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'"}'} >/dev/null
        echo "[backfill] artist ${ARTIST_ID}: ${PERMISSION} granted to ${USER_ID}"
    else
        echo "[backfill] artist ${ARTIST_ID}: WOULD grant ${PERMISSION} to ${USER_ID}"
    fi
}

BACKFILLED=0
SKIPPED=0

if [ -n "$MAPS" ]; then
    echo "[backfill] explicit mapping mode${APPLY:+ [APPLY]}"
    for m in $MAPS; do
        ARTIST_ID="$(echo "$m" | cut -d, -f1)"
        USER_ID="$(echo "$m" | cut -d, -f2)"
        PERMISSION="$(echo "$m" | cut -d, -f3)"
        case "$PERMISSION" in
            OWNER|MANAGER) ;;
            *) echo "[backfill] FATAL: permission must be OWNER or MANAGER (got: $PERMISSION)" >&2; exit 1 ;;
        esac
        grant "$ARTIST_ID" "$USER_ID" "$PERMISSION"
        BACKFILLED=$((BACKFILLED + 1))
    done
else
    echo "[backfill] legacy mode: assign OWNER=${LEGACY_OWNER} to unowned artists${APPLY:+ [APPLY]}"
    if ! user_exists "$LEGACY_OWNER"; then
        echo "[backfill] FATAL: owner user $LEGACY_OWNER does not exist" >&2
        exit 3
    fi

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

        grant "$artist_id" "$LEGACY_OWNER" "OWNER"
        BACKFILLED=$((BACKFILLED + 1))
    done
fi

MODE="would write"
[ "$APPLY" -eq 1 ] && MODE="written"
echo "[backfill] done: ${BACKFILLED} mapping(s) ${MODE}, ${SKIPPED} skipped — ok"
[ "$APPLY" -eq 1 ] || echo "[backfill] dry-run only; re-run with --apply to write"
