# flashPoint and flashtravel

`flashPoint { ... }` delimita uma região de saltos. `flashtravel N;` transfere a
execução para a **linha física N do próprio arquivo**, contada a partir de 1,
incluindo linhas vazias e comentários. O destino deve iniciar uma instrução
dentro do mesmo flashPoint. Não há timeline nem rótulos no código HeroScript.

O nome `flashtravel` tem t minúsculo; `flashPoint` tem P maiúsculo.

## Sair e voltar a um loop e a battle

O exemplo abaixo é o conteúdo completo de `examples/flashtravel.hero`.
Os números 14, 5 e 17 referem-se às linhas desse arquivo:

```hero
--8<-- "examples/flashtravel.hero"
```

A execução produz:

```text
--8<-- "tests/expected/flashtravel.out"
```

Na primeira passagem, visits torna-se 1 e o salto vai à linha 14, fora de
battle e do loop. O salto seguinte volta à linha 5, no corpo do loop: visits
passa de 1 para 2, sem testar antes a condição do while. Ao chegar ao fim do
corpo, o loop volta a avaliar sua condição normalmente. A linha 11 salta para
a linha 17, evitando repetir a passagem pelo trecho externo.

Um flashPoint de topo é uma entrada alternativa ao battle de topo e pode
envolver no máximo um bloco battle. Os trechos dentro e fora desse battle
compartilham a execução da região. Também é permitido colocar flashPoint
dentro de battle, missão ou método; nesse caso os destinos ficam nesse bloco.

## Regras de destino e estado

- O destino é um inteiro decimal positivo literal, não uma expressão ou variável.
- Só são válidos inícios de instrução. Linhas vazias, comentários, linhas contendo
  apenas chaves e linhas de continuação de uma instrução não são destinos.
- Se uma linha contiver várias instruções, o salto entra na primeira delas.
- Saltos podem avançar, retroceder, sair de loops e reentrar em seus corpos.
  Reentrar no corpo não reavalia automaticamente a condição nem a inicialização.
- Voltar à linha de um for executa novamente seu cabeçalho, inclusive a inicialização.
- O estado das variáveis já inicializadas é preservado; não há retorno automático
  nem restauração de valores anteriores.
- Executar novamente uma declaração executa seu inicializador novamente.
- Um salto pode pular uma declaração, mas ler a variável sem valor inicializado
  produz `HeroError: FlashUninitialized`. Uma atribuição simples pode inicializá-la
  antes da leitura. A verificação é conservadora por instrução: referências em
  expressões de curto-circuito também exigem inicialização prévia.
- Locais mantêm seus escopos: uma variável do corpo de battle não fica acessível
  no trecho externo só porque o estado foi preservado. Variáveis necessárias em
  toda a região devem ser declaradas no flashPoint externo.
- Ao terminar a região, a execução continua depois dela. Return e throw continuam
  saindo do método ou propagando a exceção, respectivamente.

**Adicionar ou remover linhas muda os destinos.** Atualize os números depois
de editar o arquivo. Um número que passar a apontar para outra instrução válida
continuará compilando: o compilador valida o destino, não a intenção do salto.

## Construções cobertas nesta versão

São aceitos if/else, while e for tradicional, todos com chaves, além de break e
continue de loops internos. Variáveis exigem tipo explícito e uma declaração
por instrução. Final exige inicializador e não pode ser alterado por outra
instrução. Recomenda-se inicializar locais externos antes de entrar na região.

Ainda não são aceitos flashPoints aninhados, do/while, switch, for-each,
try/catch/finally, synchronized, blocos de recursos e outras estruturas Java
dentro da região. Atribuições e incrementos em condições e inicializadores de
locais da região também não são cobertos; use instruções separadas. Chamadas
em inicializadores continuam podendo ter efeitos normais, sem execução duplicada.
Break e continue não podem apontar para loops externos ao flashPoint.

Try/finally envolvendo a região continua funcionando, inclusive após return
ou exceção. Saltos não podem atravessar métodos, arquivos ou flashPoints.

DeadpoolAction pode estar na região, mas suas instruções internas não podem ser
destinos de salto, e flashtravel não pode estar escrito dentro de seu corpo.
Saltar para a linha de abertura da instrução deadpoolAction é permitido e passa
pelo sorteio novamente. Veja [deadpoolAction](deadpool-action.md).

## Outras ferramentas

Supervision mantém o escopo da própria classe e as proteções de secret,
kryptonite e batcave. GetValue conserva nome e localização originais dos locais,
sem exibir nomes internos usados na tradução. GetMethod sinaliza análise parcial
para o bloco flashPoint em vez de apresentar a máquina de estados Java gerada.
Batinvestigate continua localizando declarações visíveis; locais kryptonite na
região continuam indexados e símbolos secret permanecem ocultos.

Para validar ou executar:

```powershell
java -cp build/classes heroscript.compiler.Main check examples/flashtravel.hero
java -cp build/classes heroscript.compiler.Main run examples/flashtravel.hero
```
