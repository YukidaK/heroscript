# Missão de resgate

Missões com parâmetros que chamam outras missões, inclusive de outro herói.

[:material-play: Abrir no playground](../playground/index.html?example=rescue){ .md-button .md-button--primary }

=== "rescue.hero"

    ```java
    --8<-- "examples/rescue.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/rescue.out"
    ```

## O que observar

- `Rescue` chama `Engage` duas vezes e, sem energia, pede ajuda a `medic.Support(this)`.
- `unleash Adrenaline;` é um poder sem alvo.
- O relato mostra a ordem em que as missões começam.
