#!/usr/bin/env bash
# Compila o compilador e o runtime da HeroScript para build/classes.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo "Erro: javac não encontrado. Instale um JDK 17+ (ex.: sudo pacman -S jdk-openjdk)." >&2
    exit 1
fi

rm -rf build/classes
mkdir -p build/classes
javac --release 17 -encoding UTF-8 -d build/classes $(find src -name '*.java')
echo "HeroScript compilado em build/classes"
