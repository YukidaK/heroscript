# Batinvestigate: localizar origens

Batinvestigate tem um único comando, `batinvestigate`. Ele pesquisa declarações em
arquivos `.hero` de um diretório e seus subdiretórios, mostrando onde abrir o
arquivo para manutenção. Também aceita um arquivo específico.

```powershell
java -cp build/classes heroscript.compiler.Main batinvestigate examples authenticate
java -cp build/classes heroscript.compiler.Main batinvestigate examples reserve
java -cp build/classes heroscript.compiler.Main batinvestigate examples/batcave.hero WayneSecurity.validate
```

No Bash:

```bash
./heroscript batinvestigate examples authenticate
```

Exemplo de resultado (separadores de caminho variam conforme o sistema):

```text
[method] WayneSecurity.authenticate | examples/batcave.hero:9:20
[method] Vault.authenticate | examples/secret_members.hero:8:20
```

Cada linha informa categoria, nome qualificado e arquivo:linha:coluna da
declaração no HeroScript. **Todas as origens visíveis correspondentes são
mostradas**, em ordem de caminho e posição. Homônimos, sobrecargas, declarações
duplicadas e arquivos independentes não são reduzidos a um único resultado.

Não são exibidos corpos, inicializadores, valores, tipos de retorno ou listas
de parâmetros. Buscar uma classe mostra somente onde ela foi declarada, sem
listar o que há dentro dela. Quem faz a manutenção abre o arquivo indicado.
Opcionalmente, pode-se pesquisar por assinatura, como `'Vault.authenticate(String)'`,
mas ela é usada apenas para selecionar a origem e não é impressa como código.

## Cobertura e proteção

| Declaração | Pode ser localizada? |
|---|---|
| `hero`, `villain`, `squad`, `batcave` | Sim |
| Campos, missões e métodos comuns | Sim, inclusive membros privados de batcave |
| Poderes, gadgets, formas e combos | Sim |
| Variáveis locais `kryptonite` | Sim, com escopo e posição |
| Campos, locais, métodos e missões `secret` | Não |
| Declarações dentro de método ou missão `secret` | Não |
| Variáveis locais Java comuns | Sim, incluindo var, arrays, múltiplas declarações e variáveis de for/for-each/catch |
| Parâmetros de métodos e missões | Ainda fora do índice |

O filtro usa as marcações da AST: um nome público não fica oculto só porque
existe outro símbolo secret com o mesmo nome. A própria batcave continua
localizável. Procurar um secret retorna a mesma mensagem de um nome inexistente:
`símbolo não disponível para investigação`.

## Como a busca funciona

O comando lê e analisa a estrutura do fonte, sem compilar, carregar classes ou
executar o programa. Erros de tipos e declarações duplicadas não impedem a
localização, para que o comando também ajude a corrigir código confuso.
Os arquivos são pesquisados independentemente; a ferramenta não os transforma
em um único programa nem afirma que declarações homônimas sejam equivalentes.

São pesquisados somente arquivos `.hero` (extensão sem distinção de maiúsculas).
Na busca recursiva, são ignorados links simbólicos e os subdiretórios `.git`,
`.codex`, `.agents`, `.venv`, `node_modules`, `build`, `site` e `.terraform`.
Se um desses diretórios for informado explicitamente como raiz, ele é pesquisado.

Se um arquivo não puder ser lido ou analisado sintaticamente, os resultados dos
demais são preservados e o comando informa que a busca é **parcial**. Não imprime
trechos do arquivo com erro. Um resultado parcial não garante todas as origens.

| Código de saída | Significado |
|---|---|
| 0 | Busca completa, uma ou mais origens visíveis encontradas |
| 1 | Busca completa, símbolo não disponível, inclusive secret ou inexistente |
| 2 | Uso/caminho incorreto ou busca parcial por falha de leitura/análise |

Os antigos subcomandos `find`, `inspect` e `trace` foram retirados. A ferramenta
não exibe implementações nem rastreia chamadas/usos e não pesquisa arquivos Java externos.
