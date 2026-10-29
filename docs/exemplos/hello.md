# Olá, herói

O menor programa completo: um herói, um vilão e dois golpes.

[:material-play: Abrir no playground](../playground/index.html?example=hello){ .md-button .md-button--primary }

=== "hello.hero"

    ```java
    --8<-- "examples/hello.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/hello.out"
    ```

## O que observar

- `summon` cria os personagens; `unleash rookie.Punch on thug` usa o poder.
- Cada golpe gasta 10 de energia e causa 15 de dano.
- Quando a vida chega a 0 aparece `[down]`.
