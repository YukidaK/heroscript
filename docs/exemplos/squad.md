# Squad e combos

Três heróis com estilos diferentes (gadgets, poderes naturais) cooperando.

[:material-play: Abrir no playground](../playground/index.html?example=squad){ .md-button .md-button--primary }

=== "squad.hero"

    ```java
    --8<-- "examples/squad.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/squad.out"
    ```

## O que observar

- O custo de `JusticeStorm` (60) é dividido entre os 3 membros: 20 cada.
- Sem energia, Storm Rider não participa e o segundo `JusticeStorm` falha com `SquadUnavailable`.
- `TwinStrike` exige só 2 membros e funciona; `league.members()` é usado num `for` Java.
