---
template: home.html
title: HeroScript
hide:
  - navigation
  - toc
---

## Um gostinho

<div class="hs-taste" markdown>

<div class="hs-taste__panel" markdown>
<span class="hs-caption">programa.hero</span>

```java
--8<-- "examples/hello.hero"
```
</div>

<div class="hs-taste__panel" markdown>
<span class="hs-caption hs-caption--orange">Saída</span>

```text
--8<-- "tests/expected/hello.out"
```
</div>

</div>

[Rodar este programa no playground](playground/index.html?example=hello){ .md-button .md-button--primary }
[Ver todos os exemplos](exemplos/index.md){ .md-button }

## Do arquivo à execução

A HeroScript reaproveita do Java o que já funciona bem — tipos, expressões, `if`, `for`, `while`, `try/catch` — e
acrescenta conceitos novos com regras próprias, verificadas pela linguagem.

<div class="hs-pipeline" markdown>
<span>arquivo.hero</span><span>Lexer</span><span>Parser</span><span>AST</span><span>JavaGenerator</span><span>javac</span><span>execução</span>
</div>

O mesmo compilador roda no terminal e [no playground](playground/index.html). Os detalhes estão em
[Como funciona](como-funciona/index.md).
