# Lexer (análise léxica)

O lexer lê o texto caractere por caractere e produz **tokens** com linha e coluna — essenciais para mensagens de
erro precisas.

```java
public enum TokenType { IDENT, NUMBER, STRING, CHAR, SYMBOL, EOF }
public record Token(TokenType type, String text, int line, int col) { ... }
```

## Ideias principais

- **Palavras-chave são `IDENT`**: o parser as reconhece pelo texto. Por isso `on`, `by`, `as` e `allow` podem ser
  usadas como nomes fora dos comandos onde são especiais.
- **Comentários e espaços** (`//`, `/* */`) são descartados.
- **Strings e chars** são copiados exatamente como no fonte, com aspas e escapes — o Java gerado os recebe iguais.
- **Símbolos** vêm de uma tabela ordenada do mais longo para o mais curto (`>>>=` antes de `>>` antes de `>`),
  garantindo que o operador mais longo seja escolhido.

```java
for (String s : SYMBOLS) {
    if (src.startsWith(s, pos)) { ...; return new Token(TokenType.SYMBOL, s, startLine, startCol); }
}
throw new CompileError(file, startLine, startCol, "caractere inesperado '" + c + "'");
```

## Experimente

```bash
./heroscript tokens examples/hello.hero
```

```text
3:1 IDENT  hero
3:6 IDENT  Rookie
3:13 SYMBOL {
4:5 IDENT  String
...
```

No [playground](../playground/index.html) o botão **Tokens** mostra o mesmo.
