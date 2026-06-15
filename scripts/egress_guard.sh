#!/usr/bin/env bash
# Fails if any content-transmitting path is reintroduced into the SDK source.
# CI backstop behind the hardened egress allowlist (egress-allowlist.json).
# Keep this script identical in spirit across modelcost-python / -java / -node.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/src/main"
status=0

fail() { echo "::error::$1"; status=1; }

# 1. The raw-content scan endpoint and client method must never come back.
if grep -rniE --include='*.java' -e 'governance/scan' -e 'scanText' -e 'GovernanceScanRequest' -e 'GovernanceScanResponse' "$SRC"; then
  fail "Reintroduced server-side content scan path. Content must never be transmitted."
fi

# 2. The content-capture flag must not return as a real config gate.
#    (A deprecated, throwing builder shim keeps the bare word, so we match the
#     config getter / env var, which only exist if the flag is truly back.)
if grep -rniE --include='*.java' -e 'isContentPrivacy' -e 'MODELCOST_CONTENT_PRIVACY' "$SRC"; then
  fail "Reintroduced contentPrivacy gate. Content suppression must be architectural, not a toggle."
fi

# 3. No raw-content / free-form-metadata field on any egress DTO (serialized keys).
if grep -rniE --include='*.java' '@JsonProperty\("(text|prompt|messages|completion|content|metadata)"\)' "$SRC"; then
  fail "An egress DTO declares a raw-content / metadata field."
fi

if [ "$status" -eq 0 ]; then
  echo "egress guard: OK — no content-transmitting paths found."
fi
exit "$status"
