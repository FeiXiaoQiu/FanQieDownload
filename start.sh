#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export PORT="${PORT:-8787}"
export HOST="${HOST:-0.0.0.0}"
export MAX_WORKERS="${MAX_WORKERS:-6}"
mkdir -p downloads

if command -v node >/dev/null 2>&1; then
  exec node server.js
fi

if command -v python3 >/dev/null 2>&1 && [[ -f server.py ]]; then
  echo "node 不可用，回退到 python3 server.py" >&2
  exec python3 server.py
fi

echo "需要 Node.js >= 18，或提供 server.py" >&2
exit 1
