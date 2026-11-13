# Membros secret

`secret` em campos, métodos e variáveis locais, dentro e fora de uma batcave.

[:material-play: Abrir no playground](../playground/index.html?example=secret_members){ .md-button .md-button--primary }

=== "secret_members.hero"

    ```java
    --8<-- "examples/secret_members.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/secret_members.out"
    ```

## O que observar

- `ticket` é um local `secret`: o programa usa o valor normalmente, mas o `supervision` mostra `[protegido]`.
- `calculate` é um método `secret`: o `supervision.getValue(result)` escrito dentro dele não gera evento algum.
- `secret` em método não é controle de acesso — `analyst.calculate(3)` é chamado do `battle` e devolve 6.
- Na batcave, `validate` é `secret` e privado; `authenticate` é a única porta de entrada pública.

Regras completas: [Membros secret](../linguagem/secret.md).
