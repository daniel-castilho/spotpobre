#!/bin/sh
# Provision the LocalStack DynamoDB tables + S3 bucket the application expects.
# Mirrors `README.md → Configure LocalStack`. Idempotent: existing tables/buckets are
# detected with describe-table / list-buckets and skipped; any other error fails loudly
# (no blind `|| true`).
#
# Execution modes (chosen automatically):
#   1. Host AWS CLI against the LocalStack edge at http://localhost:4566
#      (or $LOCALSTACK_ENDPOINT). Fast path; used in CI.
#   2. If the host CLI cannot talk to S3 (older releases have parsing bugs against
#      emulated endpoints) and a running LocalStack container exists, every call is
#      executed inside that container with the bundled `awslocal` shim instead.
#
# POSIX sh compatible (no arrays, no `local`). Tables use PAY_PER_REQUEST billing,
# matching the Testcontainers fixtures in AbstractIntegrationTest (LocalStack ignores
# billing mode; kept for shape parity).

set -eu

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
BUCKET="${SPOTBOBRE_BUCKET:-spotpobre-songs}"

# --- Resolve the AWS command wrapper -------------------------------------------
# run_aws <service-api-call...> — single-level wrapper, no indirection, so multi-GSI
# argument lists survive POSIX sh word splitting through one "$@" hop.
USE_CONTAINER=""
if ! aws s3api list-buckets --endpoint-url="$ENDPOINT" --region us-east-1 \
        >/dev/null 2>&1; then
    USE_CONTAINER="$(docker ps --format '{{.Names}}' 2>/dev/null \
        | grep -i localstack | head -1 || true)"
    if [ -z "$USE_CONTAINER" ]; then
        echo "[seed] FATAL: host AWS CLI cannot reach LocalStack S3 at $ENDPOINT" >&2
        echo "[seed]        and no running LocalStack container found for the fallback." >&2
        exit 2
    fi
    echo "[seed] host CLI unusable for S3 — falling back to 'awslocal' inside container '$USE_CONTAINER'"
fi

if [ -n "$USE_CONTAINER" ]; then
  run_aws() { docker exec "$USE_CONTAINER" awslocal "$@"; }
else
  run_aws() {
    AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
        aws --endpoint-url="$ENDPOINT" --region us-east-1 --no-cli-pager "$@"
  }
fi

echo "[seed] s3://${BUCKET}"
if run_aws s3api list-buckets --query "Buckets[].Name" --output text 2>/dev/null \
        | tr '\t' '\n' | grep -qx "$BUCKET"; then
  echo "[seed] bucket already exists — ok"
else
  run_aws s3api create-bucket --bucket "$BUCKET" >/dev/null
fi

table_exists() {
  run_aws dynamodb describe-table --table-name "$1" >/dev/null 2>&1
}

create_table() {
  name="$1"
  shift
  echo "[seed] table ${name}"
  if table_exists "$name"; then
    echo "[seed] table ${name} already exists — ok"
    return 0
  fi
  run_aws dynamodb create-table --table-name "$name" "$@" >/dev/null
}

# NOTE: the email-index GSI is keyed on the literal nested attribute "profile.email"
# (how the Enhanced Client schema in DynamoDbConfig flattens the user profile email),
# NOT on a top-level "email" attribute. A wrong key here makes registration succeed
# while every login silently 401s (items are stored but never indexed).
create_table Users \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=profile.email,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=email-index,KeySchema=[{AttributeName=profile.email,KeyType=HASH}],Projection={ProjectionType=ALL}" \
  --billing-mode PAY_PER_REQUEST

create_table UserEmails \
  --attribute-definitions AttributeName=email,AttributeType=S \
  --key-schema AttributeName=email,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

create_table Playlists \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=ownerId,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=ownerId-index,KeySchema=[{AttributeName=ownerId,KeyType=HASH}],Projection={ProjectionType=ALL}" \
  --billing-mode PAY_PER_REQUEST

create_table Songs \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=searchPartition,AttributeType=S AttributeName=searchTitle,AttributeType=S AttributeName=albumId,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=title-search-index,KeySchema=[{AttributeName=searchPartition,KeyType=HASH},{AttributeName=searchTitle,KeyType=RANGE}],Projection={ProjectionType=ALL}" "IndexName=albumId-index,KeySchema=[{AttributeName=albumId,KeyType=HASH}],Projection={ProjectionType=ALL}" \
  --billing-mode PAY_PER_REQUEST

create_table Artists \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=searchPartition,AttributeType=S AttributeName=searchName,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=name-search-index,KeySchema=[{AttributeName=searchPartition,KeyType=HASH},{AttributeName=searchName,KeyType=RANGE}],Projection={ProjectionType=ALL}" \
  --billing-mode PAY_PER_REQUEST

create_table ArtistAccounts \
  --attribute-definitions AttributeName=artistId,AttributeType=S AttributeName=userId,AttributeType=S \
  --key-schema AttributeName=artistId,KeyType=HASH AttributeName=userId,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

create_table Albums \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=artistId,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=artistId-index,KeySchema=[{AttributeName=artistId,KeyType=HASH}],Projection={ProjectionType=ALL}" \
  --billing-mode PAY_PER_REQUEST

create_table Likes \
  --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=entityCompositeKey,AttributeType=S \
  --key-schema AttributeName=userId,KeyType=HASH AttributeName=entityCompositeKey,KeyType=RANGE \
  --global-secondary-indexes "IndexName=entityId-index,KeySchema=[{AttributeName=entityCompositeKey,KeyType=HASH},{AttributeName=userId,KeyType=RANGE}],Projection={ProjectionType=ALL}" \
  --billing-mode PAY_PER_REQUEST

create_table IdempotencyRecords \
  --attribute-definitions AttributeName=scopeKey,AttributeType=S \
  --key-schema AttributeName=scopeKey,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

# Durable idempotency relies on DynamoDB TTL for physical cleanup of expired records.
run_aws dynamodb update-time-to-live \
  --table-name IdempotencyRecords \
  --time-to-live-specification "Enabled=true, AttributeName=expiresAtEpochSeconds" >/dev/null

create_table AccountTokens \
  --attribute-definitions AttributeName=tokenHash,AttributeType=S \
  --key-schema AttributeName=tokenHash,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

# Account-lifecycle tokens (password recovery) rely on DynamoDB TTL for cleanup.
run_aws dynamodb update-time-to-live \
  --table-name AccountTokens \
  --time-to-live-specification "Enabled=true, AttributeName=expiresAtEpochSeconds" >/dev/null

echo "[seed] LocalStack environment configured successfully!"
