# Validação geral — 13/11/2026

## Escopo e decisão

`lexluthor` foi retirado do escopo por decisão explícita do usuário, que não considera o recurso útil o suficiente. Não será implementado. As referências à definição futura de lexluthor na proposta histórica estão superadas.

Os demais recursos do inventário possuem implementações e testes para seus contratos de primeira etapa. Isso não significa que todas as extensões mencionadas nos documentos históricos tenham sido entregues.

## Verificações

Ambiente: Windows, Java com compilação para Java 17 e Node.js. Testes executados na raiz do projeto.

| Grupo | Verificações |
|---|---:|
| Programas e erros com saída/código de retorno esperados | 24 |
| Recursos e visibilidade | 108 |
| Batinvestigate | 54 |
| Supervision | 47 |
| Flashtravel | 50 |
| DeadpoolAction | 50 |
| Batplan | 57 |
| Hulksmash | 40 |
| SpiderSense | 56 |
| Integração e paridade check/editor | 39 |

Total: 501 verificações Java, além dos 24 programas e dos testes simulados do editor. Os grupos não representam quantidade de cenários independentes: alguns verificam vários resultados do mesmo cenário.

O teste de integração confirma proteção de observações dentro da batcave, secret interno, hulksmash ignorando if externo e flashtravel fora de blackwidow, deadpoolAction em região executável, hulkbuster, alias inativo, criação de nova instância e máscara kryptonite. Os testes de paridade comparam os diagnósticos de erro do spiderSense com a aceitação do check para exemplos e construções inválidas.

O teste JavaScript verifica cores e mensagens acessíveis, remoção imediata de marcadores, descarte de resultados atrasados e ausência de execução de código editado. Verificados também a sintaxe do JavaScript e `git diff --check`.

## Limites que continuam registrados

- Batcave: autorização de chamadas por identidade/tipo do chamador ainda não implementada. Proibir a declaração de villain dentro da batcave não impede chamadas de vilões a métodos públicos. Ver [contrato](batcave-etapa-1.md).
- Kryptonite: suporte entregue para variáveis locais; campos continuam uma extensão pendente. Ver [contrato](kryptonite-etapa-1.md).
- Batplan: desativação nas operações instrumentadas; não modifica a JVM ou os acessos internos de bibliotecas Java externas. Ver [contrato](batplan-etapa-1.md).
- SpiderSense: erros do Java têm linha mapeada e coluna aproximada; o parser não recupera todos os erros de sintaxe de uma vez. Ver [contrato](../docs/linguagem/spider-sense.md).
- Navegador: a integração visual real com CheerpJ/ECJ ainda não foi validada nesta execução. Os testes locais usam o compilador do JDK e simulação do editor. Não confundir aprovação local com aprovação dessa distribuição web.

Esses limites não foram transformados em novas implementações durante a validação.

## Reproduzir

```powershell
powershell -ExecutionPolicy Bypass -File tests/run_tests.ps1
node --check playground/playground.js
```

A suíte inclui `tests/ValidationTests.java`; havendo Node instalado, executa também `tests/spiderSense-editor.mjs`. O script Bash equivalente inclui os mesmos grupos, mas não foi executado neste ambiente Windows.
