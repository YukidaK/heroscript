# Deadpool action

Blocos que só executam se um sorteio permitir, com `chance` de 0 a 100.

[:material-play: Abrir no playground](../playground/index.html?example=deadpool_action){ .md-button .md-button--primary }

=== "deadpool_action.hero"

    ```java
    --8<-- "examples/deadpool_action.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/deadpool_action.out"
    ```

## O que observar

- `chance = 0` nunca executa e `chance = 100` sempre executa — por isso a saída deste exemplo é estável.
- Com qualquer outro valor, cada entrada no bloco faz um sorteio novo; não existe `seed`.
- No playground, o spiderSense marca com uma aranha amarela todo bloco com chance menor que 100.

Regras completas: [Deadpool action](../linguagem/deadpool-action.md).
