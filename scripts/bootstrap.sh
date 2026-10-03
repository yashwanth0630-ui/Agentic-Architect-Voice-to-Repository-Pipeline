#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
#  bootstrap.sh — "Green Light" Desktop Handoff & Auto-Boot
#
#  Fetches/receives bundle.zip from an iQOO smartphone via
#  iQOO Office Kit shared folders or local HTTP export,
#  unpacks it into ./scaffolded-workspace, installs dependencies,
#  and auto-boots the AI-primed IDE (Cursor or VS Code).
#
#  Usage:
#    chmod +x scripts/bootstrap.sh
#    ./scripts/bootstrap.sh                       # auto-detects bridge / server
#    API_URL=http://192.168.1.100:3000 ./scripts/bootstrap.sh
# ──────────────────────────────────────────────────────────────
set -euo pipefail

API_URL="${API_URL:-http://localhost:3000}"
ENDPOINT="${API_URL}/api/export"
BUNDLE="bundle.zip"
WORKSPACE="./scaffolded-workspace"
IQOO_SHARE_DIR="${IQOO_SHARE_DIR:-$HOME/iQOO_Share}"

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║   🚀  Agentic Architect — Mobile-to-Desktop Handoff & Auto-Boot  ║"
echo "║       Powered by iQOO 13 / Snapdragon NPU & Qualcomm GenieX      ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

# ── 1. Bridge Transfer: Local iQOO Office Kit Folder or HTTP ────
TRANSFER_SOURCE=""

if [ -f "${IQOO_SHARE_DIR}/${BUNDLE}" ]; then
  echo "📡  Detected bundle in iQOO Office Kit shared folder: ${IQOO_SHARE_DIR}/${BUNDLE}"
  cp "${IQOO_SHARE_DIR}/${BUNDLE}" "./${BUNDLE}"
  TRANSFER_SOURCE="iQOO Office Kit Direct Share"
elif [ -f "./${BUNDLE}" ]; then
  echo "📁  Found existing local ${BUNDLE}"
  TRANSFER_SOURCE="Local Bundle"
else
  echo "⏳  Requesting workspace bundle via HTTP from ${ENDPOINT} ..."
  HTTP_CODE=$(curl -s -o "${BUNDLE}" -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{}' \
    "${ENDPOINT}" || echo "000")

  if [ "${HTTP_CODE}" -ne 200 ]; then
    echo "❌  Server request failed (HTTP ${HTTP_CODE})."
    if [ -f "${BUNDLE}" ]; then
      cat "${BUNDLE}"
      rm -f "${BUNDLE}"
    fi
    exit 1
  fi
  TRANSFER_SOURCE="HTTP Bridge (${ENDPOINT})"
fi

echo "✅  Workspace bundle acquired via ${TRANSFER_SOURCE} ($(wc -c < "${BUNDLE}" | tr -d ' ') bytes)"

# ── 2. Workspace Unpacking ──────────────────────────────────────
if [ -d "${WORKSPACE}" ]; then
  echo "🗑   Refreshing target directory: ${WORKSPACE} ..."
  rm -rf "${WORKSPACE}"
fi

mkdir -p "${WORKSPACE}"

if command -v unzip >/dev/null 2>&1; then
  unzip -q -o "${BUNDLE}" -d "${WORKSPACE}"
else
  tar -xf "${BUNDLE}" -C "${WORKSPACE}"
fi

# Clean temporary zip if transferred via HTTP
if [ "${TRANSFER_SOURCE}" = "HTTP Bridge (${ENDPOINT})" ]; then
  rm -f "${BUNDLE}"
fi

# ── 3. Dependency Hydration (npm install) ────────────────────────
echo ""
echo "📦  Hydrating workspace dependencies (npm install) ..."
if command -v npm >/dev/null 2>&1; then
  (
    cd "${WORKSPACE}"
    if [ -f "package.json" ]; then
      npm install --silent || npm install
      echo "✅  Dependencies installed successfully."
    else
      echo "⚠️   No package.json found; skipping npm install."
    fi
  )
else
  echo "⚠️   npm not found in PATH; skipping npm install."
fi

# ── 4. Verify AI Agent Context Files ────────────────────────────
echo ""
echo "🔍  Verifying AI Agent Manifest Pyramid:"
for file in "AGENTS.md" ".cursorrules" "CLAUDE.md" "GEMINI.md" "skills/api-contracts.md"; do
  if [ -f "${WORKSPACE}/${file}" ]; then
    echo "    ✅  ${file}"
  else
    echo "    ⚠️   ${file} (missing)"
  fi
done

# ── 5. Agent Priming & Auto-Boot IDE ────────────────────────────
echo ""
echo "🤖  Priming Multi-Agent System (Cursor, VS Code, Cline, Roo Code, Aider, Claude Code) ..."

BOOTED_IDE=false
if command -v cursor >/dev/null 2>&1; then
  echo "🎯  Booting Cursor IDE..."
  cursor "${WORKSPACE}" || true
  BOOTED_IDE=true
elif command -v code >/dev/null 2>&1; then
  echo "🎯  Booting VS Code..."
  code "${WORKSPACE}" || true
  BOOTED_IDE=true
fi

# ── 6. Summary ──────────────────────────────────────────────────
echo ""
echo "┌──────────────────────────────────────────────────────────────────┐"
echo "│  🎉  Workspace scaffolded & AI-primed successfully!               │"
echo "│                                                                  │"
echo "│  Target Directory:  ${WORKSPACE}/"
echo "│  Handoff Method:    ${TRANSFER_SOURCE}"
if [ "${BOOTED_IDE}" = true ]; then
echo "│  IDE Boot:          Active (Assistant auto-primed with manifests) │"
else
echo "│  Manual Boot:       cd ${WORKSPACE} && cursor .                   │"
fi
echo "└──────────────────────────────────────────────────────────────────┘"
echo ""
