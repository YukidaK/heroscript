# A linguagem

Um programa HeroScript é um arquivo `.hero` com três tipos de declaração de topo:

| Declaração | Para quê |
|---|---|
| [`hero` / `villain`](personagens.md) | tipos de personagem, com atributos, [poderes](poderes.md), [gadgets](gadgets.md), [formas](formas.md), [segredos](secret.md) e [missões](missoes.md) |
| [`squad`](squads.md) | equipes de heróis com combos cooperativos |
| [`battle`](battle.md) | o ponto de entrada do programa (no máximo um por arquivo) |

```java
hero Nome { ... }
villain Nome { ... }
squad Nome { ... }
battle { ... }
```

## O que é novo e o que é Java

A HeroScript **não reinventa** o que o Java já faz bem. Dentro de `mission` e `battle` você escreve Java normal
— variáveis, `if`, `for`, `while`, `switch`, `try/catch`, chamadas de método — misturado com os comandos da
HeroScript. Veja [Java embutido](java-embutido.md).

| Novo na HeroScript | Continua Java |
|---|---|
| `hero`, `villain`, `power`, `gadget`, `form`, `secret`, `mission`, `squad`, `combo`, `battle` | `int`, `String`, `boolean`, arrays, classes do JDK |
| `unleash`, `transform`, `revert`, `evolve`, `summon`, `recruit`, `assemble`, `announce`, `shield(...)` | `if`, `for`, `while`, `switch`, `try/catch`, expressões |

## Regras gerais

- Comentários: `//` e `/* ... */`.
- Palavras como `on`, `by`, `as`, `into` e `allow` só são especiais dentro do comando onde aparecem; fora dele
  podem ser nomes de variáveis.
- Erros de compilação mostram arquivo, linha e coluna. Erros de execução são [`HeroError`](erros.md).
