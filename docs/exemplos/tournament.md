# Torneio

Arrays e for-each do Java: o campeão enfrenta vários vilões em sequência.

[:material-play: Abrir no playground](../playground/index.html?example=tournament){ .md-button .md-button--primary }

=== "tournament.hero"

    ```java
    --8<-- "examples/tournament.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/tournament.out"
    ```

## O que observar

- `Villain[] rivals = { brute, ninja, wizard };` é Java puro.
- Todos os vilões têm um poder chamado `Attack`, então `unleash rival.Attack` funciona para qualquer um.
- O escudo `Bracers` não tem `uses`: é ilimitado.
