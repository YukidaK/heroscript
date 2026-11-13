# Batcave

Uma `batcave` com membros privados por padrão: o `supervision` não enxerga nada lá dentro.

[:material-play: Abrir no playground](../playground/index.html?example=batcave){ .md-button .md-button--primary }

=== "batcave.hero"

    ```java
    --8<-- "examples/batcave.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/batcave.out"
    ```

## O que observar

- `validate` e os campos não têm modificador: dentro de `batcave` isso significa **privado**. Só `authenticate`, `getAttempts` e `simulateFailure` são `public`.
- Os três `supervision.getValue` escritos dentro da batcave (linhas 11, 13 e 23) não produzem nenhuma linha na saída — nem mesmo `[protegido]`.
- `attempts` é por instância: `security` conta 2 tentativas e `other` continua em 0.
- A exceção lançada dentro da batcave é tratada normalmente no `battle`, e a observação volta a funcionar depois (`visible = 7`).

Regras completas: [Batcave](../linguagem/batcave.md).
