> Atualização de 13/11/2026: autorização síncrona da batcave, locais Java no batinvestigate, FriendlyFire em combos e literais foram corrigidos. Contrato atual: [correcoes-revisao-2026-11-13.md](correcoes-revisao-2026-11-13.md). O texto abaixo preserva o estado anterior à correção.

# Batinvestigate: contrato reajustado

**Estado:** comando único `batinvestigate` implementado. Este contrato substitui a
divisão anterior em find, inspect e trace e a ideia de mostrar implementações.

## Objetivo

Ajudar quem faz manutenção a encontrar a origem de uma declaração para abrir
o arquivo correto. Analisar internamente o fonte é permitido; exibir o conteúdo
da declaração não é o objetivo nem faz parte da saída.

```powershell
java -cp build/classes heroscript.compiler.Main batinvestigate examples authenticate
```

Recebe um diretório ou arquivo `.hero` e um símbolo. Em diretórios, percorre
subdiretórios e agrega todas as origens visíveis correspondentes. A saída tem
somente categoria, nome qualificado, caminho, linha e coluna.

## Regras consolidadas

- Exibir todas as origens, inclusive homônimos, sobrecargas e declarações
  duplicadas no mesmo arquivo. Nenhum candidato é escolhido automaticamente.
- Ordenar por caminho e posição para que os resultados sejam determinísticos.
- Não mostrar corpo, inicializador, valor ou assinatura detalhada. Consultas
  por assinatura continuam possíveis como filtro interno opcional.
- Buscar uma classe mostra somente a declaração da classe, não seus membros.
- Membros comuns de batcave, inclusive privados, podem ser localizados.
- Secret prevalece: campos, locais, métodos, missões e descendentes de métodos
  ou missões secret não entram no índice público. Batcave não recebe secret.
- Nome secret e nome inexistente têm resposta genérica e código 1, sem ecoar
  a consulta. Homônimos públicos continuam aparecendo normalmente.
- Kryptonite não oculta a origem e seus valores também não são exibidos.
- Não compilar nem executar: código com erros de tipos e declarações duplicadas
  precisa continuar pesquisável. A análise depende da sintaxe reconhecida pelo parser.
- Em falha de leitura/sintaxe, preservar origens dos outros arquivos e informar
  busca parcial com código 2. Nunca apresentar resultados parciais como completos.

## Cobertura entregue

Personagens, batcaves, squads, campos comuns, missões/métodos não secret, poderes,
gadgets, formas, combos e locais kryptonite. Locais Java comuns, parâmetros e
arquivos Java externos continuam fora do índice, como na implementação inicial.

Na recursão são ignorados links simbólicos e subdiretórios de metadados,
dependências e saídas: `.git`, `.codex`, `.agents`, `.venv`, `node_modules`,
`build`, `site`, `.terraform`. A raiz explicitamente informada é respeitada.

## Validação

`tests/InvestigatorTests.java` cobre diretórios com nomes repetidos em arquivos
diferentes, sobrecargas, duplicatas, ordem, caminhos com espaços, filtragem secret,
conteúdo não exibido, ausência de execução e busca parcial. Os antigos formatos
com find/inspect/trace são rejeitados pela CLI.

`docs/linguagem/batinvestigate.md` descreve o comando atual. As seções de propostas
antigas em outros documentos devem ser lidas à luz deste contrato.
