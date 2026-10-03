#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
#  bootstrap.sh — "Green Light" Desktop Handoff
#
#  Downloads the Agentic Architect workspace bundle from the
#  running IQOO server and unpacks it into ./scaffolded-workspace
#
#  Usage:
#    chmod +x scripts/bootstrap.sh
#    ./scripts/bootstrap.sh                       # defaults to localhost:3000
#    API_URL=https://my-server.dev ./scripts/bootstrap.sh
# ──────────────────────────────────────────────────────────────
set -euo pipefail

API_URL="${API_URL:-http://localhost:3000}"
ENDPOINT="${API_URL}/api/export"
BUNDLE="bundle.zip"
WORKSPACE="./scaffolded-workspace"

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║   🚀  Agentic Architect — Green Light Bootstrap     ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# ── 1. Hit the export endpoint ────────────────────────────
echo "⏳  Requesting workspace bundle from ${ENDPOINT} ..."
HTTP_CODE=$(curl -s -o "${BUNDLE}" -w "%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{}' \
  "${ENDPOINT}")

if [ "${HTTP_CODE}" -ne 200 ]; then
  echo "❌  Server returned HTTP ${HTTP_CODE}. Response body:"
  cat "${BUNDLE}"
  rm -f "${BUNDLE}"
  exit 1
fi

echo "✅  Received ${BUNDLE} ($(wc -c < "${BUNDLE}" | tr -d ' ') bytes)"

# ── 2. Extract into workspace directory ───────────────────
if [ -d "${WORKSPACE}" ]; then
  echo "🗑   Removing existing ${WORKSPACE} directory ..."
  rm -rf "${WORKSPACE}"
fi

mkdir -p "${WORKSPACE}"
unzip -o "${BUNDLE}" -d "${WORKSPACE}"

# ── 3. Clean up the zip ──────────────────────────────────
rm -f "${BUNDLE}"

# ── 4. Print success summary ─────────────────────────────
echo ""
echo "┌──────────────────────────────────────────────────────┐"
echo "│  ✅  Workspace scaffolded successfully!              │"
echo "│                                                      │"
echo "│  Location:  ${WORKSPACE}/"
echo "│                                                      │"
echo "│  Contents:                                           │"
ls -1 "${WORKSPACE}" | sed 's/^/│    📄  /'
if [ -d "${WORKSPACE}/skills" ]; then
  ls -1 "${WORKSPACE}/skills" | sed 's/^/│    📂  skills\//'
fi
echo "│                                                      │"
echo "│  Next steps:                                         │"
echo "│    cd ${WORKSPACE}                                   │"
echo "│    Open in Cursor / VS Code / your preferred editor  │"
echo "└──────────────────────────────────────────────────────┘"
echo ""
