# Treino com evolve

Um laço `while` do Java em que o poder evolui a cada golpe.

[:material-play: Abrir no playground](../playground/index.html?example=evolve_loop){ .md-button .md-button--primary }

=== "evolve_loop.hero"

    ```java
    --8<-- "examples/evolve_loop.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/evolve_loop.out"
    ```

## O que observar

- Variáveis Java (`int round`) convivem com comandos HeroScript.
- `kid.recharge(50)` não passa do máximo de energia (50).
- `evolve kid.Spark by 5` aumenta o dano permanentemente.
