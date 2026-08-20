#!/usr/bin/env bash
# Provision the LocalStack DynamoDB tables + S3 bucket the application expects.
# Mirrors `README.md → Configure LocalStack` so the same commands are used locally
# and in CI for the runtime shutdown smoke. Idempotent: tables/bucket that already
# exist (ResourceInUse) are tolerated.
set -euo pipefail

AWSLocal=(aws --endpoint-url=http://localhost:4566 --region us-east-1 \
          --access-key-id test --secret-access-key test --no-cli-pager)
BUCKET="${SPOTBOBRE_BUCKET:-spotpobre-songs}"

echo "[seed] s3://${BUCKET}"
"${AWSLocal[@]}" s3api create-bucket --bucket "$BUCKET" 2>/dev/null || \
  echo "[seed] bucket already exists — ok"

create_table() {
  local name="$1"; shift
  echo "[seed] table ${name}"
  "${AWSLocal[@]}" dynamodb create-table "$@" 2>/dev/null || \
    echo "[seed] table ${name} already exists — ok"
}

create_table Users \
  --table-name Users \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=email,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=email-index,KeySchema=[{AttributeName=email,KeyType=HASH}],Projection={ProjectionType=ALL}"

create_table UserEmails \
  --table-name UserEmails \
  --attribute-definitions AttributeName=email,AttributeType=S \
  --key-schema AttributeName=email,KeyType=HASH

create_table Playlists \
  --table-name Playlists \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=ownerId,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=ownerId-index,KeySchema=[{AttributeName=ownerId,KeyType=HASH}],Projection={ProjectionType=ALL}"

create_table Songs \
  --table-name Songs \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=searchPartition,AttributeType=S AttributeName=searchTitle,AttributeType=S AttributeName=albumId,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes \
    "IndexName=title-search-index,KeySchema=[{AttributeName=searchPartition,KeyType=HASH},{AttributeName=searchTitle,KeyType=RANGE}],Projection={ProjectionType=ALL}" \
    "IndexName=albumId-index,KeySchema=[{AttributeName=albumId,KeyType=HASH}],Projection={ProjectionType=ALL}"

create_table Artists \
  --table-name Artists \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=searchPartition,AttributeType=S AttributeName=searchName,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=name-search-index,KeySchema=[{AttributeName=searchPartition,KeyType=HASH},{AttributeName=searchName,KeyType=RANGE}],Projection={ProjectionType=ALL}"

create_table Albums \
  --table-name Albums \
  --attribute-definitions AttributeName=id,AttributeType=S AttributeName=artistId,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes "IndexName=artistId-index,KeySchema=[{AttributeName=artistId,KeyType=HASH}],Projection={ProjectionType=ALL}"

create_table Likes \
  --table-name Likes \
  --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=entityCompositeKey,AttributeType=S \
  --key-schema AttributeName=userId,KeyType=HASH AttributeName=entityCompositeKey,KeyType=RANGE \
  --global-secondary-indexes "IndexName=entityId-index,KeySchema=[{AttributeName=entityCompositeKey,KeyType=HASH},{AttributeName=userId,KeyType=RANGE}],Projection={ProjectionType=ALL}"

echo "[seed] LocalStack environment configured successfully!"
