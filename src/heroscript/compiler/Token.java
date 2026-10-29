package heroscript.compiler;

/** Um pedaço do código-fonte com sua posição (linha/coluna começam em 1). */
public record Token(TokenType type, String text, int line, int col) {

    public boolean is(String s) {
        return type != TokenType.STRING && type != TokenType.CHAR && text.equals(s);
    }

    public boolean isIdent() {
        return type == TokenType.IDENT;
    }

    @Override
    public String toString() {
        return String.format("%d:%d %-6s %s", line, col, type, text);
    }
}
