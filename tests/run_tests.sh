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
[ "$fail" = 0 ] || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/FeatureTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/InvestigatorTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/SupervisionTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/FlashTravelTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/DeadpoolActionTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/BatplanTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/HulkSmashTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/SpiderSenseTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/ValidationTests.java" || exit 1
java -Dfile.encoding=UTF-8 -cp "$ROOT/build/classes" "$ROOT/tests/RevisionFixTests.java" || exit 1
if command -v node >/dev/null 2>&1; then
    (cd "$ROOT" && node tests/spiderSense-editor.mjs) || exit 1
fi
