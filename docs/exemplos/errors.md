# Todos os erros

Dispara e trata, em sequência, cada `HeroError` da linguagem.

[:material-play: Abrir no playground](../playground/index.html?example=errors){ .md-button .md-button--primary }

=== "errors.hero"

    ```java
    --8<-- "examples/errors.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/errors.out"
    ```

## O que observar

- Cada `try { ... } catch (HeroError e)` mostra a mensagem do erro.
- O último `unleash` não é tratado: o programa termina com `InsufficientEnergy` e código 1.
- Use como referência rápida das mensagens de erro.
