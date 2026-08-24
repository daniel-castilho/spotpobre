#!/bin/sh
# LocalStack durability - Option A snapshot (docs/data-model-decisions.md).
#
# Dumps every expected DynamoDB table (full scan -> JSON) plus the songs bucket to a
# timestamped snapshot directory on the host, using python3+boto3 that ship INSIDE the
# LocalStack container (no extra host dependencies beyond docker itself).
# Rotation keeps the newest $KEEP_SNAPSHOTS directories.
#
# Schedule: deploy/systemd/spotpobre-localstack-snapshot.{service,timer} (every 15 min).
# Also run once right after scripts/seed-localstack.sh so a fresh environment has a
# baseline snapshot. Point LOCALSTACK_SNAPSHOT_DIR at a durable external volume in
# production (default lives beside the checkout for convenience).
#
# POSIX sh compatible. Exit codes: 0 ok, 2 environment problem, 1 partial failure.

set -eu

CONTAINER="${LOCALSTACK_CONTAINER:-}"
SNAPSHOT_ROOT="${LOCALSTACK_SNAPSHOT_DIR:-$PWD/.localstack-snapshots}"
KEEP_SNAPSHOTS="${KEEP_SNAPSHOTS:-8}"
BUCKET="${SPOTBOBRE_BUCKET:-spotpobre-songs}"

if [ -z "$CONTAINER" ]; then
    CONTAINER="$(docker ps --format '{{.Names}}' | grep -i localstack | head -1 || true)"
fi
if [ -z "$CONTAINER" ]; then
    echo "[snapshot] FATAL: no running LocalStack container found." >&2
    exit 2
fi

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
NAME="snapshot-$STAMP"
HOST_DIR="$SNAPSHOT_ROOT/$NAME"
mkdir -p "$HOST_DIR"

echo "[snapshot] container=$CONTAINER target=$HOST_DIR"

# --- Dump every table through boto3 inside the container ------------------------
docker exec -i "$CONTAINER" python3 - "$@" <<'PYEOF' > "$HOST_DIR/manifest.txt"
import json, os
import boto3

client = boto3.client("dynamodb", region_name="us-east-1",
                      endpoint_url="http://localhost:4566",
                      aws_access_key_id="test", aws_secret_access_key="test")
out_dir = "/tmp/spotpobre-snapshot"
os.makedirs(out_dir, exist_ok=True)

expected = ["Users", "UserEmails", "Playlists", "Songs", "Artists", "ArtistAccounts",
            "Albums", "Likes", "IdempotencyRecords", "AccountTokens", "SongUploads"]

manifest = {}
for table in sorted(expected):
    try:
        paginator = client.get_paginator("scan")
        items = []
        for page in paginator.paginate(TableName=table):
            items.extend(page.get("Items", []))
    except client.exceptions.ResourceNotFoundException:
        manifest[table] = {"status": "missing"}
        continue
    # boto3 Decimal values are not JSON-serialisable by default; the TypeDeserializer
    # route gives plain AttributeValue dicts which ARE serialisable and lossless.
    from boto3.dynamodb.types import TypeDeserializer
    deser = TypeDeserializer()
    plain = [deser.deserialize({"M": i}) for i in items]
    with open(f"{out_dir}/{table}.json", "w") as fh:
        json.dump(plain, fh)
    manifest[table] = {"status": "ok", "items": len(plain)}

print(json.dumps(manifest, indent=2, sort_keys=True))
PYEOF

docker cp "$CONTAINER:/tmp/spotpobre-snapshot" "$HOST_DIR/data" >/dev/null
docker exec "$CONTAINER" rm -rf /tmp/spotpobre-snapshot

# --- S3 objects -------------------------------------------------------------
mkdir -p "$HOST_DIR/s3"
if docker exec "$CONTAINER" awslocal s3 ls "s3://$BUCKET/" >/dev/null 2>&1; then
    docker exec "$CONTAINER" awslocal s3 sync "s3://$BUCKET" "/tmp/spotpobre-s3" --delete >/dev/null
    docker cp "$CONTAINER:/tmp/spotpobre-s3/." "$HOST_DIR/s3/" >/dev/null
    docker exec "$CONTAINER" rm -rf /tmp/spotpobre-s3
else
    echo "[snapshot] WARN: bucket $BUCKET absent - S3 part skipped"
fi

# --- Verification -----------------------------------------------------------
MISSING=$(grep -c '"status": "missing"' "$HOST_DIR/manifest.txt" || true)
if [ "$MISSING" != "0" ]; then
    echo "[snapshot] WARNING: $MISSING expected table(s) absent from this environment"
fi
echo "[snapshot] contents:"
ls -la "$HOST_DIR/data"

# --- Rotation ----------------------------------------------------------------
COUNT=1
for old in $(ls -1dt "$SNAPSHOT_ROOT"/snapshot-* 2>/dev/null); do
    if [ "$COUNT" -gt "$KEEP_SNAPSHOTS" ]; then
        rm -rf "$old"
        echo "[snapshot] pruned $(basename "$old")"
    fi
    COUNT=$((COUNT + 1))
done

echo "[snapshot] OK -> $HOST_DIR"
