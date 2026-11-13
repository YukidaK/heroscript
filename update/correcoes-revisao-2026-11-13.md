# Correções após revisão — 13/11/2026

Solicitação do usuário: localizar locais Java comuns, impedir fogo amigo em combos, corrigir literais e impedir acesso de vilões aos membros da batcave.

## Entrega

- Batinvestigate usa os metadados de declarações Java para indexar locais comuns, incluindo var, arrays, múltiplos declaradores, loops e catch. Mostra todas as origens sem inicializadores/conteúdo. Secret e descendentes de métodos secret continuam ocultos. Parâmetros de métodos/missões permanecem fora do índice.
- Squad.assemble rejeita alvo Hero com FriendlyFire antes de gastar energia ou aplicar dano. Combo inexistente conserva prioridade de diagnóstico; sem alvo e alvo vilão continuam permitidos.
- Lexer preserva notação científica decimal e hexadecimal, sinais de expoente, ponto decimal final e sufixos. Distingue o sinal de expoente hexadecimal p/P de uma subtração após dígito hexadecimal e/E. Entradas de string/caractere terminando em escape incompleto produzem CompileError localizado, sem exceção interna.
- Batcave verifica construção e entrada de métodos públicos/privados/estáticos. Guardas de receptores protegem leituras/escritas de campos no código gerado. A autorização usa a cadeia síncrona inteira, inclusive métodos estáticos de vilões, missões e intermediários heróis. A tentativa negada gera BatcaveAccessDenied sem desativar a batcave ou deixar autorização residual após tratar o erro.

Contrato de autorização: [batcave](../docs/linguagem/batcave.md). Check não executa a autorização dinâmica. A marcação public não concede exceção a vilões. Não há rastreamento de proveniência de valores copiados, de tarefas assíncronas ou isolamento de reflexão/Java externo. O runtime usa StackWalker do Java 17; a distribuição CheerpJ continua precisando de validação real no navegador.

## Validação

Suíte completa: 24 programas e todos os grupos anteriores aprovados. `tests/RevisionFixTests.java`: 39 verificações específicas aprovadas (35 na execução completa e quatro casos adicionais aprovados na execução direcionada posterior). Total atual: 540 verificações Java, além dos exemplos e da simulação do editor.

As saídas antigas foram preservadas. A expectativa antiga que excluía locais Java do índice foi atualizada para a nova regra. A aceitação estática de código com chamada de vilão à batcave continua válida; o teste agora deixa explícito que a autorização ocorre em execução.

Os achados de overflow/custo negativo de combos e demais itens não solicitados na revisão continuam abertos. Lexluthor continua descartado. As seções anteriores que descrevem ausência de autorização da batcave, ausência de locais comuns e defeitos de literais são histórico anterior a esta correção.
