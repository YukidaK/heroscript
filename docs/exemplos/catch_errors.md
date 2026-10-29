# Tratando erros

`switch` do Java sobre `e.kind()` para reagir a cada tipo de `HeroError`.

[:material-play: Abrir no playground](../playground/index.html?example=catch_errors){ .md-button .md-button--primary }

=== "catch_errors.hero"

    ```java
    --8<-- "examples/catch_errors.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/catch_errors.out"
    ```

## O que observar

- Na tentativa 2 o poder está recarregando: o herói blefa (uma ação, que avança a recarga).
- Na tentativa 3 falta energia: o `case "InsufficientEnergy"` recarrega.
- Comandos HeroScript funcionam dentro dos blocos de `case`.
