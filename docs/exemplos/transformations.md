# Transformações

Formas com duração, número de usos, modificadores e `revert`.

[:material-play: Abrir no playground](../playground/index.html?example=transformations){ .md-button .md-button--primary }

=== "transformations.hero"

    ```java
    --8<-- "examples/transformations.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/transformations.out"
    ```

## O que observar

- `Beast` triplica o dano, soma defesa e expira após 2 ações.
- `Titan` não tem duração: soma 100 de vida e reduz o custo até o `revert`, que corta a vida de volta ao máximo.
- Com `uses = 2`, a terceira transformação gera `TransformationExpired`.
