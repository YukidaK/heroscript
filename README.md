# HeroScript

Linguagem de programação temática de super-heróis, **transpilada para Java**. Ela reaproveita do Java tipos,
expressões, `if`, `for`, `while` e `try/catch`, e adiciona construções próprias — heróis, poderes, gadgets,
transformações, identidades secretas, missões e equipes — cada uma com **regras verificadas** pela linguagem
(energia, recarga, usos, duração, controle de acesso, fogo amigo...).

```hero
hero IronGuardian {
    String name = "Iron Guardian";
    int energy = 100;

    gadget Armor { defense = 40; uses = 2; }
    power RepulsorBlast { cost = 25; damage = 60; }

    mission SaveCity(Fighter target) {
        unleash RepulsorBlast on target;
        if (energy < 80) {
            recharge(50);
        }
        shield(Armor);
    }
}

villain Crusher { int health = 400; }

battle {
    summon IronGuardian as tony;
    summon Crusher as crusher;
    tony.SaveCity(crusher);
}
```

- 📚 **Documentação:** https://yukidak.github.io/heroscript/
- ▶️ **Playground (roda no navegador):** https://yukidak.github.io/heroscript/playground/

> O passo a passo de **como a linguagem foi construída** (gramática, lexer, parser, runtime, geração de código,
> testes e como estendê-la) está em **[PROMPT.md](PROMPT.md)**, que também serve de prompt para uma IA recriar o projeto.

---

## Configuração

### 1. Requisitos

- **JDK 17 ou superior** (precisa ser JDK, não só JRE: o `run` usa o compilador Java embutido).
- Bash (Linux, macOS ou WSL/Git Bash no Windows).

Instalação do JDK:

| Sistema | Comando |
|---|---|
| Arch Linux | `sudo pacman -S jdk-openjdk` |
| Ubuntu/Debian | `sudo apt install openjdk-21-jdk` |
| Fedora | `sudo dnf install java-21-openjdk-devel` |
| macOS (Homebrew) | `brew install openjdk@21` |
| Windows | instale o [Temurin JDK](https://adoptium.net) e use Git Bash/WSL |

Confira:

```bash
java -version   # 17 ou mais
javac -version
```

### 2. Compilar a HeroScript

```bash
git clone <url-do-repositorio> heroscript
cd heroscript
./build.sh            # gera build/classes
```

(O script `./heroscript` também compila automaticamente na primeira execução.)

### 3. Executar programas

```bash
./heroscript run examples/iron.hero      # transpila, compila e executa
./heroscript build examples/iron.hero    # grava o Java gerado em build/gen/IronProgram.java
./heroscript check examples/iron.hero    # só valida
./heroscript tokens examples/iron.hero   # mostra os tokens do analisador léxico
```

### 4. Rodar os testes

```bash
tests/run_tests.sh            # compara a saída de cada exemplo com tests/expected/
tests/run_tests.sh --update   # regrava as saídas esperadas (revise o diff!)
```

### 5. Documentação (MkDocs + uv)

A documentação fica em `docs/` e usa [MkDocs Material](https://squidfunk.github.io/mkdocs-material/), com as
dependências Python gerenciadas pelo [uv](https://docs.astral.sh/uv/):

```bash
uv sync                    # cria .venv com o MkDocs Material (versões travadas em uv.lock)
uv run mkdocs serve        # http://127.0.0.1:8000, recarrega ao editar
```

As páginas de exemplo incluem o código de `examples/` e a saída real de `tests/expected/`, então documentação e
testes nunca divergem.

### 6. Playground web (WebAssembly)

O playground roda **o mesmo compilador** no navegador: o [CheerpJ](https://cheerpj.com) executa a JVM em
WebAssembly, o [ECJ](https://eclipse.dev/jdt/core/) substitui o `javac` e a API do Java 17 vem de um jar extraído
do `ct.sym` do JDK. Para montar e testar localmente:

```bash
scripts/build_site.sh                 # testes + playground + mkdocs build --strict → site/
python3 scripts/serve.py site 8000    # http://localhost:8000/playground/
```

(`scripts/serve.py` suporta *Range requests*, exigidas pelo CheerpJ; `python -m http.server` não serve.)

### 7. Publicação no GitHub Pages

O workflow `.github/workflows/pages.yml` roda os testes, monta o playground e publica o site a cada push na `main`.
Uma única configuração é necessária no GitHub: **Settings → Pages → Source: GitHub Actions**.

---

## A linguagem

### Declarações

| Construção | Exemplo | Regra |
|---|---|---|
| `hero` / `villain` | `hero SkyTitan { ... }` | Personagem com `name`, `energy` (padrão 100) e `health` (padrão 100); os valores iniciais são os máximos |
| `power` | `power HeatVision { cost = 20; damage = 55; cooldown = 1; }` | Custo de energia, dano e recarga (em ações do personagem) |
| `gadget` | `gadget Armor { defense = 40; uses = 2; }` | Defesa para um golpe; sem `uses` = ilimitado |
| `form` | `form Overdrive { duration = 2; uses = 1; damage *= 2; defense += 20; }` | Altera `damage`, `defense`, `energy`, `health`, `cost` com `= += -= *=`; `duration` em ações (0 = até `revert`) |
| `secret` | `secret String identity = "..." allow Unmask;` | Só pode ser lido **dentro das missões listadas** (não é `private`: nem os outros métodos do herói leem) |
| `mission` | `mission SaveCity(Fighter target) { ... }` | Bloco de ação do personagem; chamado como método: `tony.SaveCity(x);` |
| `squad` / `combo` | `squad League { members A, B; combo Storm { cost = 60; damage = 180; requires = 3; } }` | Só aceita os tipos listados; combo exige N membros disponíveis e divide o custo |
| `battle` | `battle { ... }` | Ponto de entrada do programa |

### Comandos

| Comando | Efeito |
|---|---|
| `summon Tipo as var;` | Cria um herói, vilão ou squad |
| `unleash [p.]Poder [on alvo];` | Usa um poder (sem `p.` = o próprio personagem, só dentro de `mission`) |
| `shield(Gadget);` | Ativa um gadget de defesa |
| `transform [p.]Forma;` / `revert [p];` | Entra / sai de uma forma |
| `recharge(n);` | Recupera energia até o máximo |
| `evolve [p.]Poder by n;` | Aumenta o dano de um poder permanentemente |
| `recruit heroi into squad;` | Adiciona um herói ao squad |
| `assemble squad.Combo [on alvo];` | Executa um combo cooperativo |
| `announce expr;` | Imprime uma mensagem |

Todo o resto (variáveis, `if`, `for`, `while`, `try/catch`, chamadas de método) é Java normal.
Os personagens são do tipo `Fighter` (base), `Hero` ou `Villain`.

### Erros

Em tempo de compilação, com posição exata:

```
HeroSyntaxError em syntax_error.hero:6:5 — ';' faltando no valor
HeroSyntaxError em bad_property.hero:4:9 — propriedade desconhecida 'dmg' em power Kick (esperado: cooldown, cost, damage)
HeroCompileError em java_error.hero:8 — incompatible types: java.lang.String cannot be converted to int
    Java gerado (linha 18): int x = "texto";
```

Em tempo de execução (`HeroError`, podem ser tratados com `catch (HeroError e)`):

| Erro | Quando |
|---|---|
| `InsufficientEnergy` | energia menor que o custo do poder |
| `PowerNotFound` | poder não existe no personagem |
| `PowerOnCooldown` | poder usado antes de recarregar |
| `FriendlyFire` | herói tentando atacar outro herói |
| `GadgetNotFound` / `GadgetUnavailable` | gadget inexistente / sem usos |
| `FormNotFound` / `TransformationExpired` | forma inexistente / sem usos restantes |
| `SecretAccessDenied` | leitura de `secret` fora das missões autorizadas |
| `NotASquadMember` | tipo de herói não aceito pelo squad |
| `SquadUnavailable` | membros disponíveis insuficientes para o combo |
| `ComboNotFound` | combo inexistente |
| `CharacterDefeated` | personagem derrotado tentando agir |

---

## Exemplos

| Arquivo | Mostra |
|---|---|
| `examples/hello.hero` | o menor programa completo |
| `examples/iron.hero` | herói com gadget, poderes, forma temporária, missão, `evolve` e laço `for` |
| `examples/cooldown.hero` | recarga de poderes tratada com `try/catch` |
| `examples/transformations.hero` | formas com duração, usos, modificadores e `revert` |
| `examples/evolve_loop.hero` | laço `while` com `evolve` e `recharge` |
| `examples/rescue.hero` | missões com parâmetros chamando outras missões |
| `examples/tournament.hero` | arrays e for-each do Java contra vários vilões |
| `examples/boss_fight.hero` | vilão com missão, gadget e forma de fúria |
| `examples/catch_errors.hero` | `switch` sobre `e.kind()` |
| `examples/squad.hero` | três heróis diferentes, squad, combos e `SquadUnavailable` |
| `examples/secret.hero` | controle de acesso de `secret` por missão |
| `examples/errors.hero` | cada `HeroError` sendo disparado e tratado |

## Estrutura do projeto

```
src/heroscript/compiler/   Lexer, Parser, AST, JavaGenerator (validação + geração), JavaRunner (compila e executa), Main (CLI)
src/heroscript/runtime/    Fighter, Hero, Villain, Power, Gadget, Form, Squad, Arena, HeroError
src/heroscript/playground/ Playground: ponte chamada pelo JavaScript no navegador
examples/                  programas .hero de exemplo
tests/                     casos de erro, saídas esperadas e run_tests.sh
docs/, mkdocs.yml          documentação MkDocs
pyproject.toml, uv.lock    dependências da documentação (uv)
playground/                página do playground web (HTML/CSS/JS)
scripts/                   build_playground.sh, build_site.sh, serve.py
.github/workflows/         publicação no GitHub Pages
build.sh                   compila o projeto para build/classes
heroscript                 linha de comando
PROMPT.md                  tutorial completo de como construir a linguagem
```

## Como funciona (resumo)

```
arquivo.hero → Lexer → tokens → Parser → AST → JavaGenerator → Programa.java → javac → execução
```

Detalhes de cada etapa em [PROMPT.md](PROMPT.md).
