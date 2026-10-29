#!/usr/bin/env bash
# Gera o site completo em site/: documentação MkDocs + playground em site/playground/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

tests/run_tests.sh
scripts/build_playground.sh docs/playground
uv run mkdocs build --strict
echo "Site gerado em $ROOT/site (teste com: python3 scripts/serve.py site 8000)"
