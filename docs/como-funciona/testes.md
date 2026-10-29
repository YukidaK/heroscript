# Testes

`tests/run_tests.sh` executa cada programa de `examples/` e `tests/cases/` e compara a saída (mais o código de
saída) com `tests/expected/<nome>.out`.

```bash
tests/run_tests.sh            # 16 ok, 0 falha(s)
tests/run_tests.sh --update   # regrava as saídas esperadas
```

| Pasta | Conteúdo |
|---|---|
| `examples/` | programas de exemplo — também usados nesta documentação e no playground |
| `tests/cases/` | erros propositais: sintaxe, propriedade inválida, vilão em squad, erro no Java embutido |
| `tests/expected/` | saídas esperadas |

!!! success "Documentação sempre correta"
    As páginas de [exemplos](../exemplos/index.md) incluem o código de `examples/` e a saída de `tests/expected/`
    diretamente (via `pymdownx.snippets`). Se um exemplo mudar, o teste falha até a saída esperada ser revisada —
    e a documentação acompanha automaticamente.

Para adicionar um teste: crie o `.hero`, rode `tests/run_tests.sh --update`, **revise** o `.out` gerado e faça
commit dos dois.
