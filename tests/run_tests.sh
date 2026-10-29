#!/usr/bin/env bash
# Executa cada programa de examples/ e tests/cases/ e compara a saída com tests/expected/<nome>.out.
# Use --update para regravar as saídas esperadas (revise o diff antes de commitar!).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/build.sh" >/dev/null || exit 1

update=0
[ "${1:-}" = "--update" ] && update=1
pass=0
fail=0

for src in "$ROOT"/examples/*.hero "$ROOT"/tests/cases/*.hero; do
    name="$(basename "$src" .hero)"
    expected="$ROOT/tests/expected/$name.out"
    actual="$(cd "$(dirname "$src")" && "$ROOT/heroscript" run "$(basename "$src")" 2>&1; echo "[exit $?]")"
    if [ "$update" = 1 ]; then
        printf '%s\n' "$actual" > "$expected"
        echo "atualizado  $name"
    elif [ -f "$expected" ] && diff -u "$expected" <(printf '%s\n' "$actual") >/dev/null; then
        echo "ok          $name"
        pass=$((pass + 1))
    else
        echo "FALHOU      $name"
        diff -u "$expected" <(printf '%s\n' "$actual") 2>&1 | sed 's/^/    /'
        fail=$((fail + 1))
    fi
done

[ "$update" = 1 ] && exit 0
echo
echo "$pass ok, $fail falha(s)"
[ "$fail" = 0 ]
