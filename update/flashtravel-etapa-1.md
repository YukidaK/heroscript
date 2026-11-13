# Flashtravel: contrato implementado

Decisões do usuário: manter `flashtravel` em minúsculas; usar a linha física do
arquivo como destino; restringir os saltos ao mesmo `flashPoint { ... }`.
Não há timeline nem marcadores numéricos declarados no fonte.

Integração com deadpoolAction: saltos podem apontar para sua instrução de abertura,
sempre repetindo o sorteio. O interior do corpo não pode ser destino e flashtravel
não pode estar escrito nele. Consulte `deadpoolAction-etapa-1.md`.

## Comportamento

`flashtravel 14;` salta para o início da primeira instrução na linha física 14,
desde que esteja dentro da mesma região. Comentários e linhas vazias contam
na numeração, mas não podem ser destinos. Destinos inexistentes, estruturais,
de continuação ou externos à região são erros de compilação.

É possível saltar para frente ou para trás e sair/reentrar em loops. O estado
já inicializado fica preservado. A reentrada no corpo continua daquele ponto,
sem repetir condição ou inicialização do loop antes da instrução de destino.
Uma leitura de local ainda não inicializado sinaliza FlashUninitialized.

FlashPoint pode ficar dentro de métodos, missões ou battle. Um flashPoint de
topo também pode envolver battle, permitindo sair e voltar ao seu corpo dentro
da mesma região e da mesma execução. Há no máximo um battle na entrada.
Os escopos de variáveis continuam respeitados; variáveis compartilhadas entre
trechos devem ser declaradas no bloco externo apropriado.

## Implementação e limites

A AST guarda FlashPoint e FlashTravel. O compilador gera uma máquina de estados
local e mapeia destinos por tokens de origem, sem depender das linhas Java
geradas. Locais têm estado e sinalizadores de inicialização; metadados de origem
e proteção são preservados para supervision e análise. Nomes internos são
escolhidos para evitar colisões com identificadores do programa.

Primeira versão: if/else, while, for tradicional com chaves, break/continue de
loops internos, comandos temáticos, return e throw. Uma variável por declaração,
tipo explícito e final com inicializador. As verificações de inicialização são
conservadoras por instrução. Atribuições em condições/inicializadores de locais,
flashPoints aninhados, try/finally interno, do/while, switch, for-each e estruturas
Java sem tradução correspondente recebem diagnóstico em vez de saltos inseguros.
Finally externo, controle de acesso e supressão de observação permanecem ativos.

Saltos não são chamadas entre métodos nem viagens que desfazem estado.
Não há término automático para um ciclo de saltos. Editar linhas exige revisar
os números, mesmo quando o destino novo ainda corresponde a uma instrução válida.

## Evidências e uso

`examples/flashtravel.hero` demonstra sair do loop e de battle e reentrar no corpo.
`tests/FlashTravelTests.java` cobre saltos, estado, for, escopos, destinos inválidos,
inicialização pulada, retorno, exceções, finally externo e proteções existentes.
Os programas de teste executados têm limite de tempo para detectar ciclos acidentais.
Referência completa: `docs/linguagem/flashtravel.md`.
