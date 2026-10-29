# Recarga de poderes

Um poder forte com `cooldown = 2` alternando com um fraco enquanto recarrega.

[:material-play: Abrir no playground](../playground/index.html?example=cooldown){ .md-button .md-button--primary }

=== "cooldown.hero"

    ```java
    --8<-- "examples/cooldown.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/cooldown.out"
    ```

## O que observar

- `PowerOnCooldown` é tratado com `try/catch` e o herói usa `Ember` no lugar.
- A recarga é contada em ações bem-sucedidas do personagem: Inferno volta no turno 4.
- `e.kind()` devolve só o nome do erro.
