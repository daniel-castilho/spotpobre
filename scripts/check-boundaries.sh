#!/usr/bin/env bash
# Architecture boundary verification (AGENTS.md rule 1, spec S24).
#
# Fails when domain/ or application/ reference infrastructure or framework adapters —
# via imports OR fully-qualified inline references (the strengthened check required by
# the P0 authorization section 8).
set -euo pipefail

TARGETS=(
  src/main/java/com/spotpobre/backend/domain
  src/main/java/com/spotpobre/backend/application
)

PATTERN='com\.spotpobre\.backend\.infrastructure|software\.amazon|io\.awspring|org\.mapstruct|org\.springdoc|org\.springframework\.web'

echo "[boundaries] scanning imports and fully-qualified references..."
violations=0
for target in "${TARGETS[@]}"; do
  # Match references anywhere in the line (import, field type, cast, generic argument),
  # but ignore comment lines so documented examples never trip the gate.
  hits=$(grep -RInE "$PATTERN" "$target" --include='*.java' \
    | grep -v '^\s*[^:]*:\s*[0-9]*:\s*\*' \
    | grep -vE ':[0-9]+:\s*(//|\*|/\*)' || true)
  if [ -n "$hits" ]; then
    echo "[boundaries] VIOLATIONS in $target:"
    echo "$hits"
    violations=1
  fi
done

if [ "$violations" -ne 0 ]; then
  echo "[boundaries] FAILED - clean-architecture boundary violated."
  exit 1
fi
echo "[boundaries] OK - domain and application are infrastructure-free."
