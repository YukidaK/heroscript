# Parser e AST

## AST

A árvore sintática usa `record`s Java imutáveis, um para cada construção:

```java
public record CharacterDecl(Token name, boolean villain, List<FieldDecl> fields, List<PropertyBlock> powers,
        List<PropertyBlock> gadgets, List<PropertyBlock> forms, List<SecretDecl> secrets, List<MissionDecl> missions) {}
public record PropertyBlock(Token name, List<Property> props) {}      // power, gadget, form, combo
public sealed interface Stmt permits Block, Raw, Unleash, Transform, Revert, Evolve, Summon, Recruit, Assemble, Announce {}
public record Raw(List<Token> tokens, Block body) implements Stmt {}  // código Java repassado
```

Os nós guardam `Token`s (não só `String`s) para sempre saber linha e coluna.

## Parser descendente recursivo

Cada regra da [gramática](../linguagem/referencia.md#gramatica-ebnf) vira um método: `parseCharacter`,
`parsePropertyBlock`, `parseSecret`, `parseMission`, `parseSquad`, `parseBlock`, `parseStatement`.

### O truque: `parseRaw`

Qualquer comando que não começa com palavra-chave HeroScript é **Java**. O parser junta tokens até achar `;` em
**profundidade zero** — contando `(` `)` e `[` `]`, para que o `;` de um `for (...;...;...)` não termine o comando.

Se encontrar `{` em profundidade zero, o que veio antes é um cabeçalho (`if (...)`, `for (...)`, `try`, `catch`,
`else`, `case X ->`) e o bloco é analisado **recursivamente**. É isso que permite comandos HeroScript dentro de
estruturas Java.

```java
private Raw parseRaw() {
    List<Token> toks = new ArrayList<>();
    int depth = 0;
    while (true) {
        Token t = peek();
        if (depth == 0 && t.is("{")) return new Raw(toks, parseBlock());   // if (...) { ... }
        if (depth == 0 && t.is("}")) return new Raw(toks, null);
        advance(); toks.add(t);
        if (t.is("(") || t.is("[") || t.is("{")) depth++;
        if (t.is(")") || t.is("]") || t.is("}")) depth--;
        if (depth == 0 && t.is(";")) return new Raw(toks, null);
    }
}
```

Erros de sintaxe saem como `HeroSyntaxError em arquivo.hero:linha:coluna — mensagem`.
