# Identidade secreta

`secret` com acesso liberado por missão — diferente de `private`.

[:material-play: Abrir no playground](../playground/index.html?example=secret){ .md-button .md-button--primary }

=== "secret.hero"

    ```java
    --8<-- "examples/secret.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/secret.out"
    ```

## O que observar

- `hideouts` pode ser lido em `Unmask` e `Patrol`; `identity` só em `Unmask`.
- Ler `identity` em `Patrol` gera `SecretAccessDenied`, tratado com `catch`.
- No `battle`, `owl.identity` nunca é permitido: o erro não tratado encerra o programa (código 1).
