> Atualização de 13/11/2026: autorização síncrona da batcave, locais Java no batinvestigate, FriendlyFire em combos e literais foram corrigidos. Contrato atual: [correcoes-revisao-2026-11-13.md](correcoes-revisao-2026-11-13.md). O texto abaixo preserva o estado anterior à correção.

# Revisão geral da HeroScript — 13/11/2026

Esta revisão considera a linguagem original, a expansão proposta, as decisões posteriores, o compilador, o runtime, as ferramentas, os testes, a documentação e a configuração de distribuição. Não altera comportamentos da linguagem.

O relatório de validação anterior confirma a aprovação dos casos automatizados existentes. Não demonstra que o projeto inteiro está concluído ou livre de defeitos: esta revisão encontrou situações que esses testes não cobrem.

## 1. Defeitos reproduzidos

### A. Alta prioridade: string incompleta pode derrubar a análise

Origem: [Lexer.java](../src/heroscript/compiler/Lexer.java), tratamento de escapes em `next`, e `advance`.

Ao terminar o arquivo imediatamente após uma barra invertida dentro de uma string ainda aberta, o lexer avança além do tamanho do texto. Resultado: `StringIndexOutOfBoundsException`, em vez de um `CompileError` localizado.

Isso pode acontecer naturalmente durante a digitação. `SpiderSense.analyze` captura `CompileError`, mas não essa exceção interna; o playground apresenta indisponibilidade da análise em vez de marcar a string como erro. A CLI também não oferece o diagnóstico normal.

Correção recomendada: verificar o limite antes do segundo avanço de um escape e adicionar regressões para string/caractere incompletos durante a edição. Não corrigido nesta revisão.

### B. Alta prioridade: custo positivo de combo pode gerar energia por overflow

Origem: [Squad.java](../src/heroscript/runtime/Squad.java), `assemble`, cálculo `(cost + requires - 1) / requires`.

Reprodução: combo com `cost = 2147483647` e `requires = 2`, dois heróis com energia 100. A soma excede `int`, a parcela vira `-1073741824` e cada participante termina com `1073741924` de energia. O programa é aceito por check e executa sem erro.

Esse é um defeito aritmético mesmo usando um custo positivo. Recomenda-se divisão com intermediário amplo ou fórmula sem overflow, além de testes de fronteira.

### C. Prioridade média: notação científica com sinal é alterada pelo lexer

Origem: [Lexer.java](../src/heroscript/compiler/Lexer.java), ramo de números.

`battle { announce 1e-3; }` produz Java equivalente a `Arena.announce(1e - 3);` e falha como literal inválido. O literal é válido em Java, mas não atravessa corretamente a camada de transpilação. Também revisar expoentes positivos e sufixos numéricos ao corrigir.

### D. Validação numérica insuficiente em combos

Origem: [Squad.java](../src/heroscript/runtime/Squad.java), `defineCombo` e `assemble`.

Com `cost = -10` e `requires = 1`, um herói passa de energia 100 para 110 após executar o combo. O custo negativo não é rejeitado. Falta consolidar o domínio aceito para custo/dano/recarga/usos/duração e definir quando rejeitar valores ou aplicar limites. Como propriedades aceitam expressões, validar somente literais no parser seria insuficiente.

O comportamento negativo foi reproduzido; a política completa de valores ainda exige decisão. Não presumir que todos os números negativos significam erro: `uses = -1` já tem significado de ilimitado.

## 2. Regras incompletas ou que exigem decisão

| Tema | Estado real | Consequência / decisão necessária |
|---|---|---|
| Vilões e batcave | Declarar villain dentro da batcave é proibido; chamadas a métodos públicos continuam permitidas | A ideia ampla de “vilões não entram” não foi concluída. Definir chamador direto, chamadas indiretas e métodos públicos antes de implementar autorização |
| Batinvestigate e variáveis locais | Localiza campos e declarações específicas, mas não locais Java comuns nem parâmetros | `battle { int local=1; }` não retorna origem para `local`. É uma limitação real em relação ao objetivo geral de localizar variáveis |
| Kryptonite em campos | Somente variáveis locais suportadas | Campos ainda são extensão pendente, não um recurso concluído |
| FriendlyFire em combos | Verificação existe em Fighter.unleash, não em Squad.assemble | Reprodução: combo com dano 20 reduz outro herói de HP 100 para 80. Decidir se a proteção deve valer para todo ataque ou somente unleash; o texto geral de erros hoje sugere um alcance maior |
| Secret | Campos antigos com allow têm controle de leitura; novos locais/métodos têm regras próprias de acesso e ocultação | Não confundir marcar método secret com torná-lo inacessível. Em batcave, visibilidade Java e ocultação são independentes |
| Batplan | Desativa identidades nas operações instrumentadas | Não elimina referências, entradas de listas, classes ou memória física; código Java externo pode operar fora dessas verificações |
| Java embutido | Reutiliza Java, com restrições adicionais em flashPoint e em programas com batplan | “Java normal em qualquer lugar” seria uma descrição excessiva |
| Concorrência | Arena usa uma pilha global de missões; saída e exit handler também são globais | O editor serializa operações. Não considerar comprovada a execução concorrente independente; exigiria contrato e testes próprios |

## 3. Inventário completo e decisões vigentes

| Área | Entrega / situação |
|---|---|
| Hero e villain | Tipos de personagem, estado por instância, atributos e métodos gerados; exemplos passam |
| Power / unleash | Energia, dano, existência, recarga e FriendlyFire; revisar fronteiras numéricas e alcance das invariantes |
| Gadget / shield | Defesa e usos; comportamento base coberto pelos exemplos; revisar domínio de valores junto às demais propriedades |
| Form / transform / revert | Modificadores, duração e usos; duração avança nas ações de unleash, não em qualquer instrução do programa |
| Mission / allow | Contexto de missão e leitura autorizada de secret; manter distinção entre acesso e observação |
| Squad / recruit / assemble | Tipos aceitos, participantes e combos; defeitos numéricos reproduzidos acima |
| Battle e Java embutido | Entrada, blocos, exceções e expressões; limites do parser/transpilador precisam estar explícitos |
| Batcave | Classe separada, privado por padrão e supressão de observação síncrona; autorização por chamador pendente |
| Supervision | getValue para valores permitidos e getMethod para estrutura sanitizada da própria classe; sem executar método e sem visão interna da batcave |
| Batinvestigate | Busca recursiva de origens, múltiplos resultados, caminho/linha, filtro secret; lacuna de locais Java comuns e parâmetros |
| Secret e kryptonite | Ocultação e proteção conforme contratos atuais; combinação entre ambos é proibida por decisão do usuário |
| FlashPoint / flashtravel | Linhas físicas dentro da mesma região; estado e saltos gerenciados; subconjunto de controles suportados |
| Hulksmash / blackwidow / hulkbuster | Somente regiões blackwidow executam; controles externos e flashtravel externo à região são ignorados; saída pelo hulksmash mais próximo |
| DeadpoolAction | Sorteio em cada entrada executável, chance literal 0–100, sem seed; bloqueio de saltos para contornar o sorteio |
| Batplan | Identidade, aliases e nova instância; limites de instrumentação documentados |
| SpiderSense | Parser/gerador e Java compartilhados, erros/avisos, CLI JSON e marcadores no playground; defeito de string incompleta afeta a análise |
| Lexluthor | Descartado explicitamente pelo usuário; não implementar |
| Nomes antigos | Smanvision foi substituído por supervision; batinvestigate find/inspect/trace foram retirados; timeline e seed não pertencem à sintaxe atual |
| SpiderSense antigo | Ideia de método/log foi substituída pela análise visual durante a edição |
| Batkey | Retirado da proposta |

## 4. Documentação e promessa de comportamento

Foram encontradas referências que ainda descrevem o projeto original ou propostas superadas:

- [Referência rápida](../docs/linguagem/referencia.md): EBNF e palavras especiais não incluem a expansão.
- [Índice da linguagem](../docs/linguagem/index.md): ainda apresenta apenas os três grupos de declarações antigos; não inclui batcave e entrada flashPoint.
- [Erros](../docs/linguagem/erros.md): tabela não inclui ObjectInactive, BatplanNullTarget ou FlashUninitialized; afirma genericamente coluna disponível, embora erros Java tenham linha mapeada e coluna aproximada no spiderSense.
- [Testes](../docs/como-funciona/testes.md): ainda mostra “16 ok” e apenas a comparação de arquivos; não descreve as suítes específicas e os testes simulados do editor.
- [Playground](../docs/como-funciona/playground.md): tabela da ponte ainda cita somente run/java/tokens; falta diagnostics e seu ciclo de atualização.
- [Proposta histórica](HeroScript_Proposta_Expansao_Atualizada.md): avisos no início corrigem várias decisões, mas o corpo ainda contém find/inspect/trace, conteúdo no batinvestigate, timeline, funcionalidades marcadas como proposta e spiderSense adiado.

Recomendação: separar explicitamente especificação atual de arquivo histórico. Um leitor não deveria precisar reconstruir a conversa para saber o que a linguagem faz. Não atualizar a gramática silenciosamente nesta revisão, pois isso pode disfarçar decisões de escopo que ainda precisam ser confirmadas.

## 5. Build, distribuição e qualidade das evidências

Revisados os scripts de build/teste, montagem do playground/site, workflow de CI, Dockerfile, nginx, compose e configuração de build/stack Terraform. A cadeia prevista é testes → jars/playground → MkDocs → artefato → imagem/stack. Isso é inspeção de configuração local, não comprovação de execução da infraestrutura.

- O nginx configura revalidação de cache e evita gzip dos jars, coerente com o carregamento por Range do playground.
- O workflow separa build/validação de apply; não foi acionado por esta revisão.
- O script `heroscript` recompila automaticamente apenas quando a pasta de classes não existe. Após editar fontes, é necessário build explícito para não executar classes antigas.
- A execução dos exemplos nas suítes Bash/PowerShell não tem timeout geral. Um exemplo que entre em loop pode travar essa fase, embora vários testes específicos já tenham limite de tempo.
- Testes simulados do editor verificam lógica assíncrona e marcadores, mas não garantem layout nem compatibilidade real com CheerpJ/ECJ.
- Não foram executados build MkDocs, distribuição CheerpJ, Docker, Terraform ou deploy remoto. Não há aprovação de produção baseada nesta revisão.

## 6. Como reproduzir os achados

```powershell
java -Dfile.encoding=UTF-8 -cp build/classes scripts/ReviewProbe.java
```

O probe não é um teste de aceitação: imprime o comportamento atual, inclusive defeitos, e não transforma esses resultados em saídas esperadas da suíte. Abrange string incompleta, literal científico, custo negativo, overflow positivo, fogo amigo em combo, acesso de vilão à batcave e busca de local comum. Também confirma que poder duplicado é rejeitado.

A suíte existente foi executada novamente: 24 programas e 501 verificações Java passaram, assim como os testes simulados do editor, a verificação de sintaxe do JavaScript e `git diff --check`. Os defeitos deste relatório não estão cobertos por essas asserções e permanecem abertos até serem corrigidos e cobertos por regressões.

## 7. Sequência recomendada

1. Corrigir o lexer para entradas incompletas, pois afeta diretamente o spiderSense durante edição, e o overflow do custo de combos.
2. Definir os contratos de autorização da batcave, domínio numérico e alcance de FriendlyFire.
3. Concluir as lacunas de localização de variáveis do batinvestigate e decidir o escopo de kryptonite em campos.
4. Atualizar referência, gramática, índice, erros e testes para uma especificação atual única.
5. Validar a distribuição web em navegador real e os builds de documentação/infraestrutura em seus ambientes adequados.

Nenhuma dessas correções foi aplicada nesta revisão. O recurso lexluthor continua descartado.
