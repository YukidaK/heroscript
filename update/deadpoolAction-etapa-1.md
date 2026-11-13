> Atualização de 13/11/2026: aviso amarelo DEADPOOL_MAY_SKIP aprovado pelo usuário e implementado no spiderSense para chance menor que 100. Informa a chance de não execução do bloco e das chamadas que dependem dele. Não bloqueia a compilação; chance 100 não recebe o aviso. Estruturas ignoradas pelo hulksmash não realizam sorteio e não recebem esse aviso.

# deadpoolAction: contrato implementado

**Estado:** primeira etapa implementada. Nome em camelCase, sem seed público.
O bloco é uma construção de controle de fluxo, não um método que execute o
código de outro local nem uma maneira de ignorar erros de compilação.

```hero
deadpoolAction(chance = 50) {
    announce "Today I decided to work!";
}
```

## Decisões e semântica

- Chance obrigatória, como inteiro literal decimal de 0 a 100.
- Um novo sorteio a cada entrada; também sortear em 0 e 100, com resultado certo.
- Sem seed, decisão persistente ou mensagem automática de execução/recusa.
- Ignorar o bloco continua depois dele sem executar efeitos do corpo.
- Executar o bloco preserva exceções, retornos, break e continue normais.
- Blocos internos só são alcançados após a seleção do externo; escopos continuam locais.
- Todo o corpo é validado estaticamente, inclusive com chance 0.

## Garantia com flashtravel

Flashtravel é proibido lexicalmente dentro de deadpoolAction, inclusive em
blocos internos. Destinos externos para instruções do interior também são
rejeitados. A linha da instrução de abertura pode ser destino e sempre passa
pelo sorteio novamente. Linhas compartilhadas não permitem pular diretamente
para o corpo nem redirecionar silenciosamente o salto para um trecho posterior.

A tradução de flashPoint cria um estado de decisão antes do corpo, sem registrar
suas instruções internas como destinos públicos. A decisão é executada toda vez
que esse estado é alcançado. As regras anteriores de flashPoint permanecem.

## Integração

Supervision não executa a decisão ao descrever um método e filtra operações
protegidas do corpo. Batinvestigate atravessa o bloco para localizar declarações
visíveis. A supressão de observações por batcave e secret permanece dinâmica.
Check continua sem executar o programa ou fazer sorteios.

## Catálogo para spiderSense, cuja implementação continua por último

| Regra | Severidade futura | Evidência atual |
|---|---|---|
| Chance ausente, fora de 0–100, fracionária ou não literal | Erro vermelho | Parser e testes negativos |
| Seed, parâmetro desconhecido ou repetido | Erro vermelho | Parser e testes negativos |
| Corpo sem chaves ou sintaxe incompleta | Erro vermelho | Parser |
| Flashtravel escrito em qualquer nível do corpo | Erro vermelho | Validação lexical e testes negativos |
| Salto externo para uma instrução interna | Erro vermelho | Validação de destinos e testes de integração |
| Erros de tipos/escopo no corpo, mesmo com chance 0 | Erro vermelho | Compilador Java e testes de validação |

Avisos amarelos sobre aleatoriedade não estão definidos nem implementados;
não considerar a construção válida um erro só por ser aleatória. Não implementar
a interface de spiderSense nesta etapa.

## Validação e limites

`tests/DeadpoolActionTests.java` verifica as decisões e limites com sorteios
controlados internamente, sem criar seed/configuração aleatória na DSL. Os testes
não dependem de uma distribuição aleatória observada nem de uma quantidade fixa
de acertos em execuções de 50%. Há integração por processos com limite de tempo
para saltos, escopos, exceções, supervisão e investigação.

O exemplo `examples/deadpool_action.hero` usa 0 e 100 para saída reproduzível.
Referência completa: `docs/linguagem/deadpool-action.md`.
