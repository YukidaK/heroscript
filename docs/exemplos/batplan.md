# Batplan

`batplan(objeto)` desativa uma instância e todos os seus aliases.

[:material-play: Abrir no playground](../playground/index.html?example=batplan){ .md-button .md-button--primary }

=== "batplan.hero"

    ```java
    --8<-- "examples/batplan.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/batplan.out"
    ```

## O que observar

- `alias` aponta para o mesmo objeto que `original`: depois do `batplan`, usar qualquer um dos dois gera `ObjectInactive`.
- `other` é outra instância de `Worker` e continua funcionando.
- `original = new Worker()` cria um objeto novo e ativo; `alias` continua preso ao antigo.

Regras completas: [Batplan](../linguagem/batplan.md).
