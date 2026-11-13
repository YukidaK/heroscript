# Hulksmash

Um laço que executa somente as regiões `blackwidow` do seu corpo.

[:material-play: Abrir no playground](../playground/index.html?example=hulksmash){ .md-button .md-button--primary }

=== "hulksmash.hero"

    ```java
    --8<-- "examples/hulksmash.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/hulksmash.out"
    ```

## O que observar

- `announce "Ignored"` e o `if (false)` estão fora de `blackwidow`: não são executados nem avaliados.
- A região `blackwidow` roda a cada repetição, mesmo estando dentro do `if (false)`.
- O `hulkbuster` da linha 10 encerra o laço quando `rage` chega a 3; o da linha 14 está fora de `blackwidow` e é ignorado (o spiderSense avisa).

Regras completas: [Hulksmash](../linguagem/hulksmash.md).
