package heroscript.compiler;

/** Categorias de token reconhecidas pelo Lexer. Palavras-chave são IDENT; o Parser as identifica pelo texto. */
public enum TokenType {
    IDENT,
    NUMBER,
    STRING,
    CHAR,
    SYMBOL,
    EOF
}
