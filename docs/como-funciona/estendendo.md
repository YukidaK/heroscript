# Estendendo a linguagem

Exemplo: um novo comando `heal personagem by quantidade;`.

1. **Gramática** — `statement += "heal" target "by" expr ";"`.
2. **AST** (`Ast.java`) — `record Heal(Token kw, Token subject, Token target, List<Token> amount) implements Stmt`
   e inclua `Heal` no `permits` de `Stmt`.
3. **Parser** (`Parser.java`) — adicione `"heal"` em `STATEMENT_KEYWORDS` e um `case "heal"` em
   `parseHeroStatement`, reaproveitando `subjectAndName` e `collectExpr`.
4. **Runtime** (`Fighter.java`) — método `heal(int)` com a regra: não cura acima do máximo e lança
   `HeroError("CharacterDefeated", ...)` se o personagem já caiu.
5. **Gerador** (`JavaGenerator.java`) — em `genStmt`, traduza para `sujeito.heal(qtd);`.
6. **Testes e docs** — um exemplo em `examples/`, `tests/run_tests.sh --update`, uma página em `docs/exemplos/` e o
   nome no seletor do playground (`scripts/build_playground.sh`).
7. **Playground** — acrescente `heal` à lista de palavras-chave em `playground/playground.js` para o destaque de
   sintaxe.
