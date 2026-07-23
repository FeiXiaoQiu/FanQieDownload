#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export PORT="${PORT:-8787}"
export HOST="${HOST:-0.0.0.0}"
mkdir -p downloads

if command -v node >/dev/null 2>&1; then
  exec node server.js
fi

echo "需要 Node.js >= 18" >&2
exit 1
