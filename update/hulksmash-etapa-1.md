# Hulksmash — primeira etapa implementada

Contrato: [hulksmash](../docs/linguagem/hulksmash.md). Grafia: `hulksmash`, `blackwidow` e `hulkbuster`.

A condição do hulksmash é avaliada antes de cada repetição. Regiões blackwidow são selecionadas lexicalmente na ordem do arquivo; os controles externos a elas são ignorados. Dentro delas, o fluxo é normal. Hulkbuster alcançado encerra o hulksmash mais próximo, atravessando loops comuns e respeitando finally. Hulkbuster fora de região executável é ignorado.

Loops hulksmash aninhados possuem suas próprias regiões. Os escopos das regiões são independentes. Instruções ignoradas não criam variáveis utilizáveis. O AST conserva tanto o corpo original quanto as regiões executáveis para investigação, inspeção e futuros avisos.

## Catálogo para spiderSense

Não implementar a interface visual nesta etapa. Na integração final:

- **Vermelho:** blackwidow ou hulkbuster fora de qualquer hulksmash. Localizar no respectivo comando.
- **Vermelho:** condição ausente, delimitadores inválidos ou condição sem tipo booleano; erros de tipos/escopo do código executável continuam sendo os do compilador.
- **Amarelo:** hulksmash sem região blackwidow executável pertencente a ele. Não considerar regiões de outro hulksmash aninhado como pertencentes ao externo. Código válido, compilação permitida.
- **Amarelo:** hulkbuster no corpo ignorado do hulksmash, fora de blackwidow. Não bloqueia compilação.
- Não afirmar que todo hulksmash sem blackwidow é infinito: a condição pode ter efeitos e tornar-se falsa.

Os avisos amarelos ainda não são emitidos pelo compilador. Não executar programas para inferir avisos.

## Validação

`tests/HulkSmashTests.java` e `examples/hulksmash.hero`: repetição, condição falsa, efeitos ignorados, regiões múltiplas e aninhadas, escopos, saída de loops internos, hulksmash aninhado, finally, exceções, métodos, inspeção, batplan, deadpoolAction e saltos flashtravel. As suítes PowerShell e Bash incluem esses testes.
