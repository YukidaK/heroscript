# Supervision e kryptonite

`supervision.getValue` observando variáveis comuns e uma variável `kryptonite`.

[:material-play: Abrir no playground](../playground/index.html?example=vision){ .md-button .md-button--primary }

=== "vision.hero"

    ```java
    --8<-- "examples/vision.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/vision.out"
    ```

## O que observar

- `reserve` é `kryptonite`: as duas observações mostram `[protegido]`, antes e depois do `+= 8`.
- A marcação não muda o programa: `reserve == 50` continua verdadeiro e o `announce` roda.
- A linha mostrada é a da **declaração** (`vision.hero:2`), não a da observação.
- `visible` e `message` não são marcadas e aparecem com o valor atual.

Regras completas: [Supervision e kryptonite](../linguagem/kryptonite.md).
