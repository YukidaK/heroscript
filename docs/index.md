---
hide:
  - navigation
---

<div class="hs-hero" markdown>

# :material-lightning-bolt: HeroScript

Uma linguagem de programação de super-heróis, poderes e missões — **transpilada para Java**.

[Começar](instalacao.md){ .md-button .md-button--primary }
[Abrir o playground](playground/index.html){ .md-button }
[Ver exemplos](exemplos/index.md){ .md-button }

</div>

## Por que HeroScript?

A HeroScript não troca apenas `class` por `hero`. Ela reaproveita do Java o que já funciona bem — tipos,
expressões, `if`, `for`, `while`, `try/catch` — e acrescenta **conceitos novos com regras próprias**, verificadas
pela linguagem:

<div class="grid cards" markdown>

- :material-flash: **Poderes com custo e recarga**

    `unleash` confere energia, recarga e fogo amigo antes de agir.

- :material-shield-half-full: **Gadgets com usos limitados**

    `shield(Armor)` bloqueia o próximo golpe e gasta um uso.

- :material-auto-fix: **Transformações temporárias**

    `transform` aplica modificadores que expiram sozinhos depois de N ações.

- :material-incognito: **Identidade secreta**

    `secret` só pode ser lido dentro das missões autorizadas — não é um `private` renomeado.

- :material-account-group: **Squads e combos**

    Heróis cooperam; o custo do combo é dividido entre os membros disponíveis.

- :material-bug: **Erros temáticos**

    `HeroError: InsufficientEnergy`, `PowerOnCooldown`, `SecretAccessDenied`...

</div>

## Um gostinho

=== "programa.hero"

    ```java
    --8<-- "examples/hello.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/hello.out"
    ```

## Como funciona em uma linha

```text
arquivo.hero → Lexer → tokens → Parser → AST → JavaGenerator → Programa.java → javac → execução
```

O mesmo compilador roda no terminal e, via WebAssembly, [no navegador](playground/index.html).
Os detalhes estão em [Como funciona](como-funciona/index.md).
