# spiderSense: diagnóstico visual — última etapa

**Estado:** primeira integração implementada no playground e na CLI. Contrato atual: [spider-sense.md](../docs/linguagem/spider-sense.md).
**Ordem:** implementar por último, após os demais recursos da linguagem e suas
regras estarem estabilizados. O editor inicial escolhido é o playground existente.

## Papel

SpiderSense é uma camada de diagnóstico visual no editor. Não é método, tipo
de variável, instrução HeroScript nem log de execução. Analisa durante a edição,
antes de compilar, e marca os trechos problemáticos sem modificar o arquivo.

| Marcador | Significado | Relação com o compilador |
|---|---|---|
| Aranha amarela | Aviso de possível problema, sem falha comprovada | O aviso sozinho não impede a compilação |
| Aranha vermelha | Erro confirmado de sintaxe, tipos ou regra da HeroScript | O compilador deve rejeitar o mesmo erro quando ele ainda existir |

O ícone fica na margem, associado à linha/coluna ou intervalo afetado. Ao passar
o mouse ou clicar, o editor apresenta explicação e, quando disponível, sugestão
de correção. Texto “Aviso”/“Erro” acompanha a cor para não depender só dela.
O desenho final da aranha será definido na integração visual; as duas severidades
e suas cores já estão alinhadas.

## Cobertura planejada

Apresentar os erros detectáveis estaticamente relacionados à HeroScript e às
construções que forem adicionadas ou modificadas. Isso inclui sintaxe e regras
de `secret`, `kryptonite`, `batcave`, `supervision`, `flashPoint`, `flashtravel` e
dos demais recursos existentes e futuros que entrarem no escopo da entrega.

Exemplos de erros vermelhos: combinação proibida de secret e kryptonite, destino
inválido de flashtravel e uso de variável fora do escopo aceito por supervision.
Avisos amarelos precisam de critérios e testes próprios; não afirmar como erro
um risco apenas possível, como uma suspeita de repetição infinita.

Não é possível antecipar todos os erros de execução. Falhas dependentes de valores
dinâmicos continuam sendo tratadas em runtime; alertas antecipados sobre elas
só serão oferecidos quando houver uma regra de análise definida e verificável.

## Compartilhamento de regras e prevenção de esquecimentos

O compilador continua validando o programa ao compilar, inclusive fora do editor.
SpiderSense deve apresentar os mesmos diagnósticos estáticos por uma interface
compartilhada, evitando uma segunda implementação das regras.

Durante a implementação de cada recurso da linguagem, antes de spiderSense:

1. Implementar e testar sua validação no compilador normalmente.
2. Registrar no contrato do recurso suas regras, erros esperados e exemplos
   válidos/inválidos, com localização esperada quando aplicável.
3. Manter o inventário de construções atualizado para a conferência final.

Na etapa final:

1. Conferir todos os contratos e testes do inventário, incluindo recursos novos.
2. Expor diagnósticos com identificação estável, severidade, mensagem, arquivo
   e intervalo de origem, mapeando erros do Java gerado quando aplicável.
3. Ligar os diagnósticos ao editor e às aranhas, sem executar o programa.
4. Validar que um erro vermelho coincide com a rejeição pelo compilador e que
   avisos amarelos isolados não bloqueiam código válido.
5. Testar atualização dos marcadores durante a edição: surgimento, mudança de
   posição e remoção após correção, sem manter resultados de uma versão antiga.

Essa ordem reduz retrabalho, mas a entrega não deve ser considerada automaticamente
completa só por estar no fim: a cobertura precisa ser conferida contra o inventário.
Se novos recursos forem adicionados posteriormente, atualizar os mesmos diagnósticos
e testes compartilhados, sem criar regras independentes apenas para o editor.

## Critério para iniciar

Incluir na conferência final o contrato [hulksmash-etapa-1.md](hulksmash-etapa-1.md):
erros de blackwidow/hulkbuster fora de hulksmash e avisos amarelos, sem bloqueio,
para hulksmash sem blackwidow e hulkbuster ignorado. Os corpos originais estão
preservados no AST; não inferir os avisos somente a partir do Java gerado.

Começar a implementação de spiderSense quando os demais recursos planejados
estiverem implementados ou explicitamente retirados/adiados pelo usuário, seus
contratos estiverem atualizados e os testes correspondentes estiverem passando.
A implementação foi autorizada pelo usuário em 13/11/2026. O histórico acima registra a ordem originalmente aprovada. A entrega atual implementa
o scanner visual, seus ícones e a integração com o editor inicial.

## Entrega atual

Diagnósticos compartilhados do parser/gerador e da compilação Java sem execução; avisos de hulksmash sem blackwidow e hulkbuster/flashtravel ignorados. Aranhas com cores e descrições acessíveis no playground, descarte de resultados antigos e CLI JSON. Testes: tests/SpiderSenseTests.java e tests/spiderSense-editor.mjs. Colunas de erros do Java são aproximadas pelo mapa de linhas. Ainda é necessário validar visualmente a distribuição CheerpJ em navegador real; os testes locais usam JDK e simulação do editor.
