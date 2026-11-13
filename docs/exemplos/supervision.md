# Supervision

`getValue` mostra o valor de uma variável; `getMethod` mostra a estrutura de um método sem executá-lo.

[:material-play: Abrir no playground](../playground/index.html?example=supervision){ .md-button .md-button--primary }

=== "supervision.hero"

    ```java
    --8<-- "examples/supervision.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/supervision.out"
    ```

## O que observar

- `supervision.getValue(count)` imprime o valor atual e onde `count` foi declarada.
- `supervision.getMethod(doubleValue)` está escrito dentro de `Worker`: só métodos da própria classe podem ser inspecionados.
- O relatório é reconstruído a partir da AST — `doubleValue` não é chamado.

Regras completas: [Supervision](../linguagem/supervision.md).
