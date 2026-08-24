#!/bin/sh
# LocalStack durability - Option A restore + verification gate.
#
# Rebuilds the schema with seed-localstack.sh (single validated provisioning path),
# reloads every table from a snapshot directory produced by localstack-snapshot.sh,
# restores S3 objects, and VERIFIES item counts before the deployment may serve traffic.
#
# Usage:
#   ./scripts/localstack-restore.sh [--dir <snapshot-dir>] [--yes]
#
# POSIX sh compatible. Exit codes: 0 ok, 2 environment problem, 3 verification failure.

set -eu

CONTAINER="${LOCALSTACK_CONTAINER:-}"
SNAPSHOT_ROOT="${LOCALSTACK_SNAPSHOT_DIR:-$PWD/.localstack-snapshots}"
BUCKET="${SPOTBOBRE_BUCKET:-spotpobre-songs}"

CONFIRM=0
DIR_ARG=""
for arg in "$@"; do
    case "$arg" in
        --yes) CONFIRM=1 ;;
        --dir) DIR_ARG="next" ;;
        *) if [ "$DIR_ARG" = "next" ]; then SNAPSHOT_DIR="$arg"; DIR_ARG="done"; fi ;;
    esac
done

if [ -z "${SNAPSHOT_DIR:-}" ]; then
    SNAPSHOT_DIR="$(ls -1dt "$SNAPSHOT_ROOT"/snapshot-* 2>/dev/null | head -1 || true)"
fi
if [ -z "${SNAPSHOT_DIR:-}" ] || [ ! -d "$SNAPSHOT_DIR" ]; then
    echo "[restore] FATAL: no snapshot directory under $SNAPSHOT_ROOT" >&2
    exit 2
fi
NAME="$(basename "$SNAPSHOT_DIR")"

if [ "$CONFIRM" -ne 1 ]; then
    printf "[restore] This REBUILDS the live LocalStack state from '%s'. Continue? [y/N] " "$NAME"
    read -r answer
    case "$answer" in
        y|Y|yes|Yes) : ;;
        *) echo "[restore] aborted by operator"; exit 0 ;;
    esac
fi

echo "[restore] 0/4 dropping every existing table (the snapshot defines the truth) ..."
if [ -z "$CONTAINER" ]; then
    CONTAINER="$(docker ps --format '{{.Names}}' | grep -i localstack | head -1 || true)"
fi
docker exec "$CONTAINER" python3 -c "
import boto3
client = boto3.client('dynamodb', region_name='us-east-1', endpoint_url='http://localhost:4566', aws_access_key_id='test', aws_secret_access_key='test')
existing = []
for page in client.get_paginator('list_tables').paginate():
    existing.extend(page.get('TableNames', []))
for name in existing:
    client.delete_table(TableName=name)
"
# LocalStack deletes are async - wait until the table list is empty.
i=0
while [ $i -lt 30 ]; do
    LEFT=$(docker exec "$CONTAINER" awslocal dynamodb list-tables --output text 2>/dev/null | wc -w || echo 0)
    [ "$LEFT" = "0" ] && break
    i=$((i + 1))
    sleep 1
done

echo "[restore] 1/4 rebuilding empty schema via seed-localstack.sh ..."
sh scripts/seed-localstack.sh >/dev/null

if [ -z "$CONTAINER" ]; then
    CONTAINER="$(docker ps --format '{{.Names}}' | grep -i localstack | head -1 || true)"
fi
if [ -z "$CONTAINER" ]; then
    echo "[restore] FATAL: no running LocalStack container found." >&2
    exit 2
fi

echo "[restore] 2/4 loading table data ..."
docker cp "$SNAPSHOT_DIR/data" "$CONTAINER:/tmp/spotpobre-restore" >/dev/null
LOAD_SUMMARY=$(docker exec -i "$CONTAINER" python3 - <<'PYEOF'
import json, os, sys
import boto3
from boto3.dynamodb.types import TypeSerializer

client = boto3.client("dynamodb", region_name="us-east-1",
                      endpoint_url="http://localhost:4566",
                      aws_access_key_id="test", aws_secret_access_key="test")
in_dir = "/tmp/spotpobre-restore"
serializer = TypeSerializer()

summary = {}
for file_name in sorted(os.listdir(in_dir)):
    if not file_name.endswith(".json"):
        continue
    table = file_name[:-5]
    print(f"[load] {table}", file=sys.stderr, flush=True)
    with open(f"{in_dir}/{file_name}") as fh:
        items = json.load(fh)
    # TypeSerializer.serialize(plain_dict) wraps the attributes in an outer {M: ...};
    # PutItem wants the bare attribute map, so strip that single wrapper.
    import time
    written = 0
    for item in items:
        payload = serializer.serialize(item)["M"]
        for attempt in range(20):
            try:
                client.put_item(TableName=table, Item=payload)
                break
            except client.exceptions.ClientError as e:
                if "ValidationException" not in str(e) or attempt == 19:
                    raise
                time.sleep(0.5)
        written += 1
    summary[table] = written
print(json.dumps(summary, sort_keys=True))
PYEOF
)
docker exec "$CONTAINER" rm -rf /tmp/spotpobre-restore
echo "[restore] loaded: $LOAD_SUMMARY"

echo "[restore] 3/4 restoring S3 objects ..."
S3_DIR="$SNAPSHOT_DIR/s3"
if [ -d "$S3_DIR" ] && [ "$(ls -A "$S3_DIR" 2>/dev/null)" ]; then
    docker cp "$S3_DIR" "$CONTAINER:/tmp/spotpobre-s3" >/dev/null
    docker exec "$CONTAINER" sh -lc \
        "awslocal s3 sync /tmp/spotpobre-s3 s3://$BUCKET >/dev/null && rm -rf /tmp/spotpobre-s3"
else
    echo "[restore] no S3 objects in this snapshot - skipped"
fi

echo "[restore] 4/4 verifying item counts against the snapshot manifest ..."
docker cp "$SNAPSHOT_DIR/manifest.txt" "$CONTAINER:/tmp/spotpobre-manifest.json" >/dev/null
VERIFY=$(docker exec -i "$CONTAINER" python3 - <<'PYEOF'
import json
expected = json.load(open("/tmp/spotpobre-manifest.json"))

import boto3
client = boto3.client("dynamodb", region_name="us-east-1",
                      endpoint_url="http://localhost:4566",
                      aws_access_key_id="test", aws_secret_access_key="test")
failures = []
for table, meta in sorted(expected.items()):
    want = meta.get("items")
    if want is None:
        continue  # table was absent at snapshot time
    got = sum(p.get("Count", 0) for p in client.get_paginator("scan").paginate(TableName=table))
    if got != want:
        failures.append(f"{table}: restored {got}, snapshot had {want}")
existing = set()
for page in client.get_paginator("list_tables").paginate():
    existing.update(page.get("TableNames", []))
missing = [t for t in expected if t not in existing]
if missing:
    failures.append("missing tables: " + ", ".join(missing))
print("OK" if not failures else "FAIL " + "; ".join(failures))
PYEOF
)
echo "[restore] verify result: $VERIFY"
case "$VERIFY" in
    OK*) echo "[restore] OK - state rebuilt and verified from '$NAME'." ;;
    *) echo "[restore] VERIFICATION FAILED - do NOT serve traffic on this state." >&2; exit 3 ;;
esac
