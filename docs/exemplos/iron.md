# Iron Guardian

O exemplo da proposta original: herói com armadura, dois poderes, uma forma temporária e uma missão.

[:material-play: Abrir no playground](../playground/index.html?example=iron){ .md-button .md-button--primary }

=== "iron.hero"

    ```java
    --8<-- "examples/iron.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/iron.out"
    ```

## O que observar

- A missão `SaveCity` usa `if (energy < 80)` (Java) junto com `recharge`, `shield` e `transform`.
- A forma `OverdriveSuit` dobra o dano e soma 20 de defesa ao escudo: o golpe de 70 vira 10.
- Depois de 2 ações a forma expira sozinha; `evolve` deixa o RepulsorBlast mais forte para o laço `for`.
