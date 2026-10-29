# Tutorial: como construir a HeroScript do zero

Este documento explica, passo a passo, como a HeroScript foi construída: uma linguagem de programação temática de
super-heróis que é **transpilada para Java**. Serve para duas coisas:

1. **Tutorial** para quem quer entender ou refazer o projeto (ex.: apresentação para o professor).
2. **Prompt** para uma IA: o bloco final ("Prompt para IA") resume tudo que é preciso para recriar o projeto.

---

## 0. Decisões de design

| Decisão | Escolha | Por quê |
|---|---|---|
| Tipo de implementação | Transpiler HeroScript → Java | Mostra lexer, parser e gerador de código sem precisar de uma máquina virtual própria |
| Linguagem do compilador | Java 17+, sem Maven/Gradle | Recomendação do professor; só `javac` é necessário |
| O que vem do Java | tipos (`int`, `String`), `if`, `for`, `while`, `try/catch`, expressões | Já funcionam bem; reinventar não traria vantagem |
| O que é novo | `hero`, `villain`, `power`, `gadget`, `form`, `secret`, `mission`, `squad`, `combo`, `battle`, `unleash`, `transform`, `revert`, `evolve`, `summon`, `recruit`, `assemble`, `announce`, `shield(...)` | Conceitos com **regras próprias**, não simples renomeações |
| Universo | Misto e independente de franquias | A linguagem conhece conceitos (poder, gadget, forma), não personagens |

A regra de ouro: **não basta trocar `class` por `hero`**. Cada construção nova faz validações que o Java não faz
(energia, recarga, usos de gadget, duração de forma, acesso a segredos, fogo amigo, disponibilidade do squad).

### Pipeline

```
arquivo.hero ──Lexer──▶ tokens ──Parser──▶ AST ──JavaGenerator──▶ Programa.java ──javac──▶ .class ──▶ execução
                                                 (validação semântica)                    (runtime heroscript.runtime)
```

### Estrutura de pastas

```
src/heroscript/compiler/   TokenType, Token, CompileError, Lexer, Ast, Parser, JavaGenerator, JavaRunner, Main
src/heroscript/playground/ Playground (ponte para o navegador)
src/heroscript/runtime/    Fighter, Hero, Villain, Power, Gadget, Form, Squad, Arena, HeroError
examples/                  iron.hero, squad.hero, secret.hero, errors.hero
tests/                     cases/*.hero (erros de compilação), expected/*.out, run_tests.sh
build.sh, heroscript       scripts de compilação e execução
```

---

## 1. A gramática (EBNF)

Escrever a gramática primeiro guia todo o resto: cada regra vira um método do parser.

```ebnf
program     = { character | squad | battle } EOF ;
character   = ("hero" | "villain") IDENT "{" { member } "}" ;
member      = "power"  propBlock
            | "gadget" propBlock
            | "form"   propBlock
            | secret
            | mission
            | javaField ;                      (* ex.: int energy = 100; *)
propBlock   = IDENT "{" { IDENT ("=" | "+=" | "-=" | "*=") expr ";" } "}" ;
secret      = "secret" type IDENT [ "=" expr ] [ "allow" IDENT { "," IDENT } ] ";" ;
mission     = "mission" IDENT [ "(" params ")" ] block ;
squad       = "squad" IDENT "{" { "members" IDENT { "," IDENT } ";" | "combo" propBlock } "}" ;
battle      = "battle" block ;

block       = "{" { statement } "}" ;
statement   = block
            | "unleash"   target [ "on" expr ] ";"
            | "transform" target ";"
            | "revert"    [ IDENT ] ";"
            | "evolve"    target "by" expr ";"
            | "summon"    IDENT "as" IDENT ";"
            | "recruit"   IDENT "into" IDENT ";"
            | "assemble"  IDENT "." IDENT [ "on" expr ] ";"
            | "announce"  expr ";"
            | javaStatement ;                  (* copiado para o Java; pode abrir blocos *)
target      = [ IDENT "." ] IDENT ;            (* sujeito.Nome  ou  Nome (= this, só em mission) *)
```

`expr`, `type`, `params` e `javaStatement` **não são analisados** pela HeroScript: viram listas de tokens copiadas
para o Java. É isso que permite reaproveitar todo o Java sem reescrevê-lo.

---

## 2. Passo 1 — Tokens e Lexer

O lexer lê caractere por caractere e produz tokens com **linha e coluna** (essencial para boas mensagens de erro).

`TokenType.java` — só cinco categorias. Palavras-chave são `IDENT`; o parser as reconhece pelo texto. Assim
`on`, `by`, `as`, `allow` são *palavras-chave contextuais* e continuam livres para nomes de variáveis.

```java
public enum TokenType { IDENT, NUMBER, STRING, CHAR, SYMBOL, EOF }
public record Token(TokenType type, String text, int line, int col) { ... }
```

`Lexer.java`, ideias principais:

- **Espaços e comentários** (`//` e `/* */`) são descartados em `skipSpacesAndComments()`.
- **Identificadores**: `Character.isJavaIdentifierStart/Part`.
- **Strings e chars**: copiados com aspas e escapes, exatamente como no fonte (o Java vai recebê-los iguais).
- **Símbolos**: uma tabela ordenada do maior para o menor (`">>>="` antes de `">>"` antes de `">"`), garantindo
  que o operador mais longo seja escolhido.

```java
for (String s : SYMBOLS) {
    if (src.startsWith(s, pos)) { ...; return new Token(TokenType.SYMBOL, s, startLine, startCol); }
}
throw new CompileError(file, startLine, startCol, "caractere inesperado '" + c + "'");
```

Teste: `./heroscript tokens examples/iron.hero` imprime a lista de tokens.

---

## 3. Passo 2 — AST e Parser

### AST (`Ast.java`)

Records Java imutáveis, um para cada construção da gramática:

```java
public record CharacterDecl(Token name, boolean villain, List<FieldDecl> fields, List<PropertyBlock> powers,
        List<PropertyBlock> gadgets, List<PropertyBlock> forms, List<SecretDecl> secrets, List<MissionDecl> missions) {}
public record PropertyBlock(Token name, List<Property> props) {}      // power, gadget, form, combo
public sealed interface Stmt permits Block, Raw, Unleash, Transform, Revert, Evolve, Summon, Recruit, Assemble, Announce {}
public record Raw(List<Token> tokens, Block body) implements Stmt {}  // código Java repassado
public record Unleash(Token kw, Token subject, Token power, List<Token> target) implements Stmt {}
```

Guardamos `Token`s (e não só `String`s) para sempre saber a linha/coluna de cada elemento.

### Parser (`Parser.java`)

Descendente recursivo: um método por regra (`parseCharacter`, `parsePropertyBlock`, `parseSecret`, `parseMission`,
`parseSquad`, `parseBlock`, `parseStatement`).

O truque central é **`parseRaw()`**: para qualquer comando que não comece com palavra-chave HeroScript, junta tokens
até achar `;` **em profundidade zero** — contando `(`/`)` e `[`/`]`, para o `;` de dentro de um `for (...;...;...)`
não terminar o comando. Se encontrar `{` em profundidade zero, o que veio antes é um cabeçalho (`if (...)`,
`for (...)`, `try`, `else`, `catch (...)`) e o bloco é analisado **recursivamente** — por isso comandos HeroScript
funcionam dentro de `if`, `for`, `try`, etc.

```java
private Raw parseRaw() {
    List<Token> toks = new ArrayList<>();
    int depth = 0;
    while (true) {
        Token t = peek();
        if (depth == 0 && t.is("{")) return new Raw(toks, parseBlock());   // if (...) { ... }
        if (depth == 0 && t.is("}")) return new Raw(toks, null);
        advance(); toks.add(t);
        if (t.is("(") || t.is("[") || t.is("{")) depth++;
        if (t.is(")") || t.is("]") || t.is("}")) depth--;
        if (depth == 0 && t.is(";")) return new Raw(toks, null);
    }
}
```

Erros de sintaxe saem no formato `HeroSyntaxError em arquivo.hero:linha:coluna — mensagem`.

---

## 4. Passo 3 — O runtime (as regras da linguagem)

O código gerado é fino; **as regras ficam no runtime** (`src/heroscript/runtime`), em Java comum e testável.

| Classe | Responsabilidade |
|---|---|
| `Fighter` | Base de herói/vilão: `name`, `energy`, `health`, poderes, gadgets, formas, regras de `unleash`, `shield`, `transform`, `revert`, `recharge`, `evolve`, `checkSecret` |
| `Hero` / `Villain` | Lados da batalha (heróis não atacam heróis; vilões não entram em squads) |
| `Power`, `Gadget`, `Form` | Dados de cada habilidade (custo, dano, recarga, defesa, usos, duração, modificadores) |
| `Squad` | Recrutamento com tipos permitidos e combos cooperativos |
| `Arena` | Pilha de missões em execução (usada por `secret`), log da batalha, `announce` |
| `HeroError` | Erros de execução nomeados |

Exemplo — a regra do `unleash` (`Fighter.unleash`), na ordem em que é validada:

1. personagem fora de combate → `CharacterDefeated`
2. poder inexistente → `PowerNotFound`
3. herói atacando herói → `FriendlyFire`
4. poder em recarga → `PowerOnCooldown`
5. energia menor que o custo → `InsufficientEnergy`
6. desconta energia, calcula o dano (aplicando a forma ativa), o alvo recebe o dano descontando escudo/defesa
7. fim da ação: avança recargas e o tempo da forma (que pode expirar e reverter sozinha)

Regra do `secret` — **não é `private`**: o valor só pode ser lido enquanto uma das missões listadas em `allow`,
do próprio personagem, estiver em execução. A `Arena` mantém uma pilha `(personagem, missão)`:

```java
protected void checkSecret(String secret, String... allowedMissions) {
    if (!Arena.inMissionOf(this, allowedMissions)) throw new HeroError("SecretAccessDenied", ...);
}
```

---

## 5. Passo 4 — Geração de Java (`JavaGenerator.java`)

O gerador percorre a AST, **valida a semântica** e escreve um único arquivo `<Nome>Program.java`:

- cada `hero`/`villain` → `public static class X extends Hero/Villain`
- atributos `name`/`energy`/`health` → atribuições no construtor; outros atributos → campos públicos
- `power`/`gadget`/`form` → `definePower(...)`, `defineGadget(...)`, `defineForm(...).set(...).modify(...)`
- `secret` → campo privado + método `__reveal_nome()` que chama `checkSecret`
- `mission` → método que registra a missão na `Arena` (com `try/finally`)
- `squad` → `class X extends Squad` com `allowMember` e `defineCombo`
- `battle` → `public static void main`, com `catch (HeroError e)` que mostra o erro e encerra

Exemplo de tradução:

```text
HeroScript                               Java gerado
---------------------------------------  -----------------------------------------------
power RepulsorBlast { cost = 25;         definePower("RepulsorBlast", (25), (60), 0);
                      damage = 60; }
unleash RepulsorBlast on target;         this.unleash("RepulsorBlast", target);
unleash tony.RepulsorBlast on crusher;   tony.unleash("RepulsorBlast", crusher);
shield(Armor);                           shield("Armor");
summon IronGuardian as tony;             IronGuardian tony = new IronGuardian();
evolve tony.RepulsorBlast by 15;         tony.evolve("RepulsorBlast", 15);
announce identity;  (secret)             Arena.announce(__reveal_identity());
```

Validações feitas aqui (erros em tempo de compilação, com linha/coluna):
nomes duplicados, propriedade desconhecida (`dmg` em vez de `damage`), operador inválido, `allow` de missão que não
existe, `summon` de tipo desconhecido, vilão como membro de squad, `recruit` de vilão, `unleash` sem personagem
fora de uma `mission`.

**Mapa de linhas**: cada linha gerada guarda a linha `.hero` de origem (`emit(srcLine, texto)`). Se o `javac`
reclamar de algo no código Java embutido, `Main` traduz o erro para `HeroCompileError em arquivo.hero:linha`.

Veja o resultado: `./heroscript build examples/iron.hero` grava `build/gen/IronProgram.java`.

---

## 6. Passo 5 — CLI e execução (`Main.java`)

```
./heroscript run    arquivo.hero   # transpila, compila com javax.tools e executa
./heroscript build  arquivo.hero   # só grava o .java gerado (opção -o pasta)
./heroscript check  arquivo.hero   # valida sem executar
./heroscript tokens arquivo.hero   # mostra os tokens
```

`run` entrega o Java gerado ao `JavaRunner`, que compila numa pasta temporária, carrega os `.class` com um
`ClassLoader` que faz `defineClass` e chama `main` por reflexão. O compilador é o `javac` do JDK
(`ToolProvider.getSystemJavaCompiler()`) ou, se não houver, o **ECJ** (Eclipse Compiler for Java). O
`Arena.fail` não chama `System.exit` direto: usa um *exit handler* que o `JavaRunner` troca por um sinal interno,
para encerrar só o programa e não a JVM.

---

## 7. Passo 6 — Testes

`tests/run_tests.sh` executa cada `examples/*.hero` e `tests/cases/*.hero` e compara saída + código de saída com
`tests/expected/<nome>.out`. Após mudar o comportamento de propósito: `tests/run_tests.sh --update` e revise o diff.

Casos cobertos: batalha completa, squads e combos, `secret`, todos os `HeroError`, erro de sintaxe, propriedade
inválida, vilão em squad e erro no Java embutido (mapeado para a linha do `.hero`).

---

## 8. Passo 7 — Documentação e playground web

**Documentação**: MkDocs Material gerenciado com uv (`pyproject.toml` + `uv.lock`, `uv run mkdocs serve`). As
páginas de exemplo incluem `examples/*.hero` e `tests/expected/*.out` com `pymdownx.snippets`, então a saída
mostrada é sempre a real.

**Playground**: roda o mesmo compilador no navegador.

1. `src/heroscript/playground/Playground.java` expõe `run(src)`, `java(src)` e `tokens(src)`, que devolvem texto
   (redirecionando `System.out` para um buffer).
2. `scripts/build_playground.sh` empacota `heroscript.jar` (compilado com `--release 17`), baixa o `ecj.jar`
   (versão fixa + sha256) e gera `examples.json`.
3. `playground/playground.js` carrega o CheerpJ (JVM em WebAssembly), abre os jars em modo biblioteca e chama
   `await Playground.run(codigo)`.
4. O ECJ só compila fontes em disco: o `JavaRunner` grava o `.java` numa pasta temporária (`/files` no CheerpJ).
5. O CheerpJ não tem o `jrt:/` de onde o ECJ lê as classes do JDK. `scripts/make_jdk_stub.py` extrai do `ct.sym`
   a API do `java.base` do Java 17 (`jdk17-base.jar`); o `JavaRunner` a passa como `PLATFORM_CLASS_PATH` e
   esvazia `SYSTEM_MODULES`, usando o gerenciador de arquivos **padrão** do ECJ (um `ForwardingJavaFileManager`
   faria o ECJ ignorar essas configurações).
6. O CheerpJ só executa bytecode Java 17: o ECJ recebe `-source 17 -target 17` (o `javac`, `--release 17`).
7. `Playground.run` captura também `Error`, porque uma exceção que escapasse deixaria o JavaScript esperando para
   sempre. Como a primeira compilação leva cerca de 40 s, a página aquece o compilador ao abrir.

**Publicação**: `scripts/build_site.sh` (testes → playground → `mkdocs build --strict`) e o workflow
`.github/workflows/pages.yml` publicam tudo no GitHub Pages.

---

## 9. Como estender a linguagem (ex.: novo comando `heal`)

1. **Gramática**: `statement += "heal" target "by" expr ";"`.
2. **AST**: `record Heal(Token kw, Token subject, Token amount...) implements Stmt` e adicione ao `permits`.
3. **Parser**: inclua `"heal"` em `STATEMENT_KEYWORDS` e um `case "heal"` em `parseHeroStatement`.
4. **Runtime**: método `heal(int)` em `Fighter` com a regra (ex.: não cura acima do máximo; `HeroError` se derrotado).
5. **Gerador**: em `genStmt`, traduza para `sujeito.heal(qtd);`.
6. **Teste**: um exemplo `.hero` + `tests/run_tests.sh --update`.

---

## Prompt para IA

> Crie em Java 17+ (sem Maven/Gradle, só `javac`) a linguagem **HeroScript**, um transpiler de arquivos `.hero`
> para Java. Estrutura: `src/heroscript/compiler` (TokenType, Token com linha/coluna, CompileError, Lexer, Ast com
> records, Parser descendente recursivo, JavaGenerator com validação semântica e mapa de linhas, Main com comandos
> `run`/`build`/`check`/`tokens`, e JavaRunner que compila com `javax.tools` usando javac ou ECJ) e
> `src/heroscript/runtime` (Fighter, Hero, Villain, Power, Gadget, Form, Squad, Arena, HeroError).
> Tipos, expressões e controle de fluxo são Java copiado como tokens; comandos Java que abrem `{` têm o bloco
> analisado recursivamente para aceitar comandos HeroScript dentro de `if`/`for`/`try`.
> Construções: `hero`/`villain` (classes com `name`, `energy`, `health`), `power {cost, damage, cooldown}`,
> `gadget {defense, uses}`, `form {duration, uses, damage|defense|energy|health|cost com = += -= *=}`,
> `secret Tipo nome = valor allow Missao1, Missao2;` (leitura só dentro das missões listadas → `SecretAccessDenied`),
> `mission Nome(params) {}`, `squad {members A, B; combo X {cost, damage, requires}}`, `battle {}` (vira `main`).
> Comandos: `unleash [p.]Poder [on alvo];`, `transform [p.]Forma;`, `revert [p];`, `evolve [p.]Poder by n;`,
> `summon Tipo as var;`, `recruit heroi into squad;`, `assemble squad.Combo [on alvo];`, `announce expr;`,
> `shield(Gadget)` e `recharge(n)`. Erros de execução: InsufficientEnergy, PowerNotFound, PowerOnCooldown,
> FriendlyFire, GadgetNotFound, GadgetUnavailable, FormNotFound, TransformationExpired, SecretAccessDenied,
> NotASquadMember, SquadUnavailable, ComboNotFound, CharacterDefeated. Erros de compilação no formato
> `HeroSyntaxError em arquivo:linha:coluna — mensagem`. Inclua exemplos (`iron`, `squad`, `secret`, `errors`),
> scripts `build.sh`, `heroscript` e `tests/run_tests.sh` comparando saídas esperadas. Crie também a documentação
> MkDocs Material gerenciada com uv (exemplos incluídos via `pymdownx.snippets`) e um playground web estático que
> roda o mesmo compilador no navegador com CheerpJ (JVM em WebAssembly) + ECJ, publicado no GitHub Pages.
