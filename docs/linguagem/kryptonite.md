# Kryptonite

`kryptonite` marca uma variável local para que o [`supervision`](supervision.md) não possa observar seu valor. O
programa continua lendo e alterando a variável normalmente.

```java
--8<-- "examples/vision.hero"
```

```text
--8<-- "tests/expected/vision.out"
```

As duas observações de `reserve` mostram `[protegido]`, antes e depois do `+= 8`; o valor nunca chega ao relatório.
O cálculo e o `if` continuam funcionando.

## Regras

- Forma: `kryptonite [final] Tipo nome = valor;` — uma variável por instrução, com inicializador obrigatório.
- Tipos aceitos: `byte`, `short`, `int`, `long`, `float`, `double`, `boolean`, `char` e `String`. `var`, arrays e
  objetos não são aceitos.
- Vale para variáveis locais: em `battle`, em `mission` e em métodos de personagens e de [batcave](batcave.md).
  Campos `kryptonite` não são suportados.
- `final` mantém a regra do Java: a variável não pode ser reatribuída.
- A marcação pertence à declaração e dura todo o escopo. Reatribuir ou incrementar não a remove.
- `secret` e `kryptonite` não podem ser combinados, em nenhuma ordem.

## O que kryptonite não faz

- **Não é controle de acesso.** Quem pode ler a variável pelo Java continua podendo. Para restringir leitura use
  [`secret ... allow`](secret.md).
- **Não se propaga para cópias.** `int copy = reserve;` cria uma variável comum, e `supervision.getValue(copy)`
  mostra o valor.
- **Não filtra impressão explícita.** `announce reserve;` e `System.out` imprimem o valor.
- **Não esconde o código-fonte.** O [batinvestigate](batinvestigate.md) localiza a declaração (sem mostrar conteúdo), e
  o literal continua visível no arquivo e no Java gerado.

## Relação com os outros recursos

| Declaração | Programa | `supervision.getValue` | `batinvestigate` |
|---|---|---|---|
| variável comum | regras normais | mostra o valor | localiza |
| `kryptonite` | regras normais | `[protegido]` | localiza |
| `secret` | respeita `allow` (campos) | `[protegido]` | omite |
| qualquer uma, dentro de `batcave` ou método `secret` | regras normais | nenhum evento | conforme a linha acima |

Em [`supervision.getMethod`](supervision.md#getmethod), declarações `kryptonite` e operações que poderiam expor seu
valor são omitidas do relatório.

Exemplo executável: `./heroscript run examples/vision.hero` — veja também a página do
[exemplo](../exemplos/vision.md).
