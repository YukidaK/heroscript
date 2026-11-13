# Flashtravel

`flashPoint` como entrada do programa e saltos para linhas físicas com `flashtravel`.

[:material-play: Abrir no playground](../playground/index.html?example=flashtravel){ .md-button .md-button--primary }

=== "flashtravel.hero"

    ```java
    --8<-- "examples/flashtravel.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/flashtravel.out"
    ```

## O que observar

- O número depois de `flashtravel` é a **linha do arquivo**, contando linhas vazias.
- `flashtravel 14` sai do `while` e do `battle`; `flashtravel 5` volta para dentro do laço sem reavaliar a condição.
- `visits` é declarada no `flashPoint`, por isso sobrevive aos saltos para dentro e para fora do `battle`.
- Inserir ou remover uma linha muda os destinos: os números precisam ser revistos.

Regras completas: [Flashtravel](../linguagem/flashtravel.md).
