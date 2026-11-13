#!/usr/bin/env bash
# Monta o playground web em build/playground (ou na pasta passada como argumento):
# heroscript.jar (compilador + runtime), ecj.jar (compilador Java para o navegador), jdk17-base.jar
# (API do Java 17 para o ECJ) e examples.json.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/build/playground}"

ECJ_VERSION="3.38.0"   # última linha que roda em Java 17 (CheerpJ ainda não suporta 21)
ECJ_SHA256="97c566b120009c203a2fc8b291f4a9adbc171cf1ccb70f06f6b4e1828c00ce8e"
ECJ_JAR="$ROOT/build/vendor/ecj-$ECJ_VERSION.jar"

"$ROOT/build.sh"

if [ ! -f "$ECJ_JAR" ]; then
    mkdir -p "$(dirname "$ECJ_JAR")"
    curl -sSfL -o "$ECJ_JAR" \
        "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/$ECJ_VERSION/ecj-$ECJ_VERSION.jar"
fi
echo "$ECJ_SHA256  $ECJ_JAR" | sha256sum --check --quiet

rm -rf "$OUT"
mkdir -p "$OUT"
cp "$ROOT"/playground/* "$OUT"/
# Tokens visuais e logo são os mesmos da documentação.
cp "$ROOT/docs/assets/comic-tokens.css" "$ROOT/docs/assets/logo.svg" "$OUT"/
jar --create --file "$OUT/heroscript.jar" -C "$ROOT/build/classes" .
cp "$ECJ_JAR" "$OUT/ecj.jar"

# API pública do java.base (Java 17), extraída do ct.sym do JDK: o ECJ a usa no lugar do jrt:/, que o CheerpJ não tem.
JDK_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
python3 "$ROOT/scripts/make_jdk_stub.py" "$JDK_HOME/lib/ct.sym" "$OUT/jdk17-base.jar" 17

# examples.json: {"nome": {"title": "<primeiro comentário, ou titles[nome]>", "code": "..."}}, na ordem de order.
python3 - "$ROOT/examples" "$OUT/examples.json" <<'PY'
import json, pathlib, sys
order = ["hello", "iron", "cooldown", "transformations", "evolve_loop", "secret", "squad",
         "rescue", "tournament", "boss_fight", "catch_errors", "errors",
         "batcave", "secret_members", "vision", "supervision", "flashtravel", "deadpool_action",
         "batplan", "hulksmash"]
# Exemplos sem comentário na primeira linha (em flashtravel.hero os números de linha fazem parte do programa).
titles = {
    "batcave": "Batcave com membros privados e sem observação interna",
    "secret_members": "secret em campos, métodos e variáveis locais",
    "vision": "supervision.getValue e variáveis kryptonite",
    "supervision": "supervision: getValue e getMethod",
    "flashtravel": "flashPoint e saltos com flashtravel",
    "deadpool_action": "Blocos sorteados com deadpoolAction",
    "batplan": "Desativando objetos com batplan",
    "hulksmash": "hulksmash, blackwidow e hulkbuster",
}
src = pathlib.Path(sys.argv[1])
names = order + sorted(p.stem for p in src.glob("*.hero") if p.stem not in order)
data = {}
for name in names:
    code = (src / f"{name}.hero").read_text(encoding="utf-8")
    first = code.splitlines()[0]
    title = first.removeprefix("//").strip() if first.startswith("//") else titles.get(name, name)
    data[name] = {"title": title, "code": code}
pathlib.Path(sys.argv[2]).write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
PY
echo "Playground montado em $OUT"
