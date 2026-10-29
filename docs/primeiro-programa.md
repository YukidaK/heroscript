# Primeiro programa

Crie um arquivo `ola.hero`:

```java
--8<-- "examples/hello.hero"
```

E execute:

```bash
./heroscript run ola.hero
```

```text
--8<-- "tests/expected/hello.out"
```

!!! note "A linha `[exit 0]`"
    Nas saídas desta documentação a última linha mostra o código de saída do programa. Ela é adicionada pelos
    testes e pelo playground, não pelo seu programa.

## Lendo o programa

1. `hero Rookie { ... }` declara um **tipo** de herói, com nome e um poder.
2. `power Punch { cost = 10; damage = 15; }` define quanto de energia o poder gasta e quanto dano causa.
3. `villain Thug` declara um vilão com 30 de vida (`health`).
4. `battle { ... }` é o ponto de entrada — vira o `main` do Java.
5. `summon Rookie as rookie;` cria um personagem.
6. `unleash rookie.Punch on thug;` usa o poder: a linguagem desconta a energia e aplica o dano.
7. `announce` imprime uma mensagem; `rookie.status()` é um método comum do runtime.

## Veja o Java gerado

```bash
./heroscript build ola.hero
cat build/gen/OlaProgram.java
```

No [playground](playground/index.html?example=hello) o botão **Java gerado** mostra a mesma coisa.

Próximo passo: conheça os [personagens](linguagem/personagens.md) ou vá direto aos [exemplos](exemplos/index.md).
