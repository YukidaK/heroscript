# A linguagem

Um programa HeroScript é um arquivo `.hero` com estas declarações de topo:

| Declaração | Para quê |
|---|---|
| [`hero` / `villain`](personagens.md) | tipos de personagem, com atributos, [poderes](poderes.md), [gadgets](gadgets.md), [formas](formas.md), [segredos](secret.md) e [missões](missoes.md) |
| [`squad`](squads.md) | equipes de heróis com combos cooperativos |
| [`batcave`](batcave.md) | classe protegida: membros privados por padrão, sem acesso de vilões e sem observação interna |
| [`battle`](battle.md) | o ponto de entrada do programa (no máximo um por arquivo) |
| [`flashPoint`](flashtravel.md) | entrada alternativa: uma região com saltos por linha, que pode conter o `battle` |

```java
hero Nome { ... }
villain Nome { ... }
squad Nome { ... }
batcave Nome { ... }
battle { ... }        // ou flashPoint { ... battle { ... } ... }
```

## O que é novo e o que é Java

A HeroScript **não reinventa** o que o Java já faz bem. Dentro de `mission` e `battle` você escreve Java normal
— variáveis, `if`, `for`, `while`, `switch`, `try/catch`, chamadas de método — misturado com os comandos da
HeroScript. Veja [Java embutido](java-embutido.md).

| Novo na HeroScript | Continua Java |
|---|---|
| `hero`, `villain`, `power`, `gadget`, `form`, `secret`, `mission`, `squad`, `combo`, `battle` | `int`, `String`, `boolean`, arrays, classes do JDK |
| `unleash`, `transform`, `revert`, `evolve`, `summon`, `recruit`, `assemble`, `announce`, `shield(...)` | `if`, `for`, `while`, `switch`, `try/catch`, expressões |
| `batcave`, `kryptonite`, `supervision`, `batplan(...)` | métodos e campos Java dentro de personagens e batcaves |
| `flashPoint`, `flashtravel`, `deadpoolAction`, `hulksmash`, `blackwidow`, `hulkbuster` | `break`, `continue`, `return` |

## Recursos da expansão

| Recurso | Para quê |
|---|---|
| [Batcave](batcave.md) | guardar estado e métodos longe de vilões e do `supervision` |
| [Secret](secret.md) em métodos e locais | ocultar membros da observação e da investigação |
| [Kryptonite](kryptonite.md) | variável local cujo valor o `supervision` não mostra |
| [Supervision](supervision.md) | observar valores (`getValue`) e a estrutura de métodos (`getMethod`) |
| [Batinvestigate](batinvestigate.md) | comando de terminal que localiza onde um símbolo foi declarado |
| [Flashtravel](flashtravel.md) | saltar para uma linha do arquivo dentro de um `flashPoint` |
| [Deadpool action](deadpool-action.md) | bloco que executa conforme um sorteio |
| [Batplan](batplan.md) | desativar um objeto e seus aliases |
| [Hulksmash](hulksmash.md) | laço que executa só as regiões `blackwidow` |
| [SpiderSense](spider-sense.md) | erros e avisos durante a edição no playground |

Com esses recursos, o Java embutido tem algumas restrições: dentro de `flashPoint` só um subconjunto dos controles é
aceito, e programas com `batplan` não podem usar lambdas, referências de método nem classes locais ou anônimas.

## Regras gerais

- Comentários: `//` e `/* ... */`.
- Palavras como `on`, `by`, `as`, `into` e `allow` só são especiais dentro do comando onde aparecem; fora dele
  podem ser nomes de variáveis.
- Erros de compilação mostram arquivo, linha e coluna. Erros de execução são [`HeroError`](erros.md).
