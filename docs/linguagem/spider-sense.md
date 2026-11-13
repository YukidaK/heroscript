# SpiderSense

`spiderSense` analisa o código durante a edição, sem executar o programa. Não é método, variável ou comando dentro de um arquivo `.hero`.

No playground, após uma pausa na digitação, o compilador verifica o programa e apresenta:

- **Aranha vermelha — Erro:** erro confirmado pelo mesmo parser, gerador ou compilador Java usado por `check` e `run`.
- **Aranha amarela — Aviso:** construção válida que merece atenção. Não bloqueia a compilação.

A margem mostra os ícones. Passe o mouse para ler a mensagem ou clique em uma mensagem na lista abaixo do editor para ir à origem. Texto e descrição acessível acompanham a cor. Alterar o código remove os marcadores antigos imediatamente; resultados de análises anteriores são descartados.

O editor inicial é o playground. Não há extensão de VS Code nesta entrega. A análise começa depois do carregamento da JVM e usa a mesma ponte Java do playground, sem executar o código editado. Operações de execução e análise são serializadas.

## Cobertura

Erros de léxico, sintaxe, tipos, escopo e regras de HeroScript são apresentados a partir do compilador existente, incluindo `secret`, `kryptonite`, `batcave`, `supervision`, `flashPoint`, `flashtravel`, `deadpoolAction`, `batplan` e `hulksmash`.

Os avisos desta etapa são:

| Código | Aviso |
|---|---|
| `HULK_NO_BLACKWIDOW` | Hulksmash sem região blackwidow própria; a condição pode permanecer verdadeira |
| `HULKBUSTER_IGNORED` | Hulkbuster fora da região blackwidow, ignorado pelo hulksmash |
| `FLASHTRAVEL_IGNORED` | Flashtravel fora da região blackwidow, ignorado pelo hulksmash |
| `DEADPOOL_MAY_SKIP` | DeadpoolAction com chance menor que 100: o bloco e as operações/métodos que dependem dele podem não executar |

O aviso de deadpoolAction informa a porcentagem de execução e a de não execução, incluindo chance 0 (o bloco nunca executa). Chance 100 não recebe esse aviso. Não bloqueia a compilação nem altera o sorteio. Um deadpoolAction ignorado pelo hulksmash fora de blackwidow não recebe aviso de sorteio, pois essa estrutura não realiza sorteio.

Um flashtravel ignorado não exige flashPoint nem valida se seu destino existe. Sua sintaxe numérica continua sendo analisada. Quando executável dentro de blackwidow, exige flashPoint e mantém suas restrições de destino e de deadpoolAction.

Erros léxicos/sintáticos/semânticos têm código `HERO_ERROR`; erros do Java gerado têm código `JAVA_ERROR`. As mensagens preservam o diagnóstico original. Erros de Java são mapeados à linha `.hero`; como o gerador mantém um mapa de linhas, sua coluna é aproximada (início da linha). A primeira falha de parser/gerador interrompe essa fase; o compilador Java pode retornar vários erros.

SpiderSense não prevê todos os erros de execução: valores nulos, objetos desativados ou energia insuficiente podem depender do fluxo real. Falha na infraestrutura de análise aparece como indisponibilidade, sem ser marcada como erro do programa.

## Terminal e integração

```bash
./heroscript spiderSense examples/hulksmash.hero
```

Retorna uma lista JSON com `code`, `severity`, `file`, `line`, `column`, `endColumn` e `message`. Coordenadas começam em 1; o fim da coluna é exclusivo. Código de saída 1 indica erros, 0 indica ausência de erros, inclusive quando existem avisos. O comando não executa o programa. `check` e `run` conservam suas saídas existentes.

Outros editores podem consumir `SpiderSense.analyze(source, file)` ou o comando JSON. Novas validações do compilador passam automaticamente pelo mesmo caminho; novos avisos precisam de critérios e testes explícitos.
