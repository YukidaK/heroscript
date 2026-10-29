# Geração de Java

O `JavaGenerator` percorre a AST, **valida a semântica** e escreve um único arquivo `<Nome>Program.java`.

| HeroScript | Java gerado |
|---|---|
| `hero X { ... }` | `public static class X extends Hero { ... }` |
| `int energy = 120;` | `this.energy = 120;` no construtor |
| `power P { cost = 25; damage = 60; }` | `definePower("P", (25), (60), 0);` |
| `gadget G { defense = 40; uses = 2; }` | `defineGadget("G", (40), (2));` |
| `form F { duration = 2; damage *= 2; }` | `defineForm("F").set("duration", 2).modify("damage", "*=", 2);` |
| `secret String id = "x" allow M;` | campo privado + `__reveal_id()` que chama `checkSecret("id", "M")` |
| `mission M(Fighter t) { ... }` | método com `Arena.enterMission` / `exitMission` em `try/finally` |
| `battle { ... }` | `public static void main` com `catch (HeroError e) { Arena.fail(e); }` |
| `unleash tony.P on x;` | `tony.unleash("P", x);` |
| `shield(Armor)` | `shield("Armor")` |

## Validação semântica

Erros detectados aqui, com linha e coluna: nomes duplicados, propriedade desconhecida, operador inválido em
propriedade, `allow` de missão inexistente, `summon` de tipo desconhecido, vilão em squad, `recruit` de vilão e
comando sem personagem fora de missão.

## Mapa de linhas

Cada linha gerada guarda a linha do `.hero` que a originou (`emit(srcLine, texto)`). Quando o compilador Java
reclama de algo no Java embutido, o `JavaRunner` traduz a posição:

```text
HeroCompileError em java_error.hero:8 — incompatible types: java.lang.String cannot be converted to int
    Java gerado (linha 18): int x = "texto";
```

## Veja você mesmo

`./heroscript build examples/secret.hero` grava `build/gen/SecretProgram.java`:

```java
public void Patrol() {
    Arena.enterMission(this, "Patrol");
    try {
        Arena.announce(name + " patrulha " + __reveal_hideouts() + " esconderijos");
        Arena.announce("identidade: " + __reveal_identity());
    } finally {
        Arena.exitMission();
    }
}
```

## Compilação e execução: `JavaRunner`

O `JavaRunner` compila o Java gerado numa pasta temporária, lê os `.class` e os carrega com um `ClassLoader`
próprio. Ele usa o `javac` do JDK quando disponível e, senão, o **ECJ** (Eclipse Compiler for Java) — é assim que
o [playground](playground.md) compila no navegador. Para testar exatamente o caminho do navegador no terminal
(depois de `scripts/build_playground.sh`):

```bash
java -Dheroscript.compiler=ecj -Dheroscript.platform=build/playground/jdk17-base.jar \
     -cp build/classes:build/vendor/ecj-3.38.0.jar heroscript.compiler.Main run examples/iron.hero
```
