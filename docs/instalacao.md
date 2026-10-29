# Instalação

!!! tip "Só quer experimentar?"
    O [playground](playground/index.html) roda HeroScript direto no navegador, sem instalar nada.

## Requisitos

- **JDK 17 ou superior** (precisa ser JDK: o comando `run` usa o compilador Java).
- Bash (Linux, macOS, WSL ou Git Bash).
- Opcional, para a documentação: [uv](https://docs.astral.sh/uv/).

| Sistema | Instalar o JDK |
|---|---|
| Arch Linux | `sudo pacman -S jdk-openjdk` |
| Ubuntu/Debian | `sudo apt install openjdk-21-jdk` |
| Fedora | `sudo dnf install java-21-openjdk-devel` |
| macOS (Homebrew) | `brew install openjdk@21` |
| Windows | [Temurin JDK](https://adoptium.net) + Git Bash ou WSL |

```bash
java -version    # 17 ou mais
javac -version
```

## Compilar

```bash
git clone https://github.com/YukidaK/heroscript.git
cd heroscript
./build.sh       # gera build/classes
```

O script `./heroscript` compila sozinho na primeira execução, então este passo é opcional.

## Linha de comando

| Comando | O que faz |
|---|---|
| `./heroscript run arquivo.hero` | transpila, compila e executa |
| `./heroscript build arquivo.hero [-o pasta]` | grava o Java gerado (padrão: `build/gen/`) |
| `./heroscript check arquivo.hero` | só valida léxico, sintaxe e semântica |
| `./heroscript tokens arquivo.hero` | mostra os tokens produzidos pelo lexer |

O código de saída é `0` em caso de sucesso e `1` em erro de compilação ou `HeroError` não tratado.

## Testes

```bash
tests/run_tests.sh            # roda cada exemplo e compara com tests/expected/
tests/run_tests.sh --update   # regrava as saídas esperadas (revise o diff!)
```

## Documentação e playground locais

```bash
uv sync                                   # instala o MkDocs Material em .venv
scripts/build_playground.sh docs/playground   # opcional: inclui o playground no site
uv run mkdocs serve                       # http://127.0.0.1:8000
```

Para testar o playground use o servidor com suporte a *Range requests* (exigido pelo CheerpJ):

```bash
scripts/build_site.sh                     # gera site/ completo
python3 scripts/serve.py site 8000        # http://localhost:8000/playground/
```
