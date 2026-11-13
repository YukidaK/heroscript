# Testes

`tests/run_tests.sh` tem três etapas; qualquer falha interrompe o script (e o build do site).

1. **Programas**: executa cada `.hero` de `examples/` e `tests/cases/` e compara a saída (mais o código de saída)
   com `tests/expected/<nome>.out`.
2. **Suítes Java**: arquivos de `tests/*.java`, executados direto pelo `java` contra `build/classes`. Cada um
   compila pequenos programas em memória e confere saída, erros e Java gerado.
3. **Editor**: se houver Node.js, `tests/spiderSense-editor.mjs` carrega o `playground.js` com um DOM simulado.

```bash
tests/run_tests.sh            # 24 ok, 0 falha(s) + suítes Java + teste do editor
tests/run_tests.sh --update   # regrava as saídas esperadas (só da etapa 1)
```

No Windows, `tests/run_tests.ps1` faz o mesmo.

| Pasta | Conteúdo |
|---|---|
| `examples/` | programas de exemplo — também usados nesta documentação e no playground |
| `tests/cases/` | erros propositais: sintaxe, propriedade inválida, vilão em squad, erro no Java embutido |
| `tests/expected/` | saídas esperadas |

| Suíte | Cobre |
|---|---|
| `FeatureTests` | batcave, `secret` em membros e locais, kryptonite, visibilidade |
| `InvestigatorTests` | `batinvestigate`: origens, filtro de `secret`, locais Java |
| `SupervisionTests` | `getValue` e `getMethod` |
| `FlashTravelTests` | saltos, escopo e inicialização em `flashPoint` |
| `DeadpoolActionTests` | sorteio, limites de `chance`, saltos proibidos |
| `BatplanTests` | identidade, aliases, novas instâncias |
| `HulkSmashTests` | regiões `blackwidow`, `hulkbuster`, laços aninhados |
| `SpiderSenseTests` | erros e avisos em JSON, sem executar o programa |
| `ValidationTests` | recursos combinados e paridade entre `check` e spiderSense |
| `RevisionFixTests` | vilões na batcave, `FriendlyFire` em combos, literais numéricos, strings incompletas |
| `RunnerServerTests` | o servidor do playground: mesma saída da linha de comando, limite de tempo, de saída e de memória, respostas 413/404/405/503 |
| `spiderSense-editor.mjs` | marcadores do editor, descarte de análises antigas, Executar durante a análise |
| `playground-server.mjs` | playground usando o servidor sem iniciar o CheerpJ, e caindo para o CheerpJ quando o servidor falha |

!!! warning "O que esses testes não cobrem"
    Tudo acima roda na JVM local, com o `javac`. O modo navegador do playground usa outro compilador (ECJ) e outra
    JVM (CheerpJ); para testar esse caminho veja [Playground](playground.md#testando-o-caminho-do-navegador). O
    isolamento do runner (rede, disco, limites do container) só é verificável com a stack em Docker.

!!! success "Documentação sempre correta"
    As páginas de [exemplos](../exemplos/index.md) incluem o código de `examples/` e a saída de `tests/expected/`
    diretamente (via `pymdownx.snippets`). Se um exemplo mudar, o teste falha até a saída esperada ser revisada —
    e a documentação acompanha automaticamente.

Para adicionar um teste: crie o `.hero`, rode `tests/run_tests.sh --update`, **revise** o `.out` gerado e faça
commit dos dois.
