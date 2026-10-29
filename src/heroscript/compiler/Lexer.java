package heroscript.compiler;

import java.util.ArrayList;
import java.util.List;

/** Análise léxica: transforma o texto .hero em uma lista de tokens, descartando espaços e comentários. */
public class Lexer {

    // Ordenados do maior para o menor para que a busca pegue sempre o operador mais longo.
    private static final String[] SYMBOLS = {
        ">>>=", "<<=", ">>=", ">>>", "...",
        "->", "::", "++", "--", "&&", "||", "==", "!=", "<=", ">=",
        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>",
        "(", ")", "{", "}", "[", "]", ";", ",", ".", "@", "=", "<", ">",
        "!", "~", "?", ":", "+", "-", "*", "/", "&", "|", "^", "%"
    };

    private final String src;
    private final String file;
    private int pos = 0;
    private int line = 1;
    private int col = 1;

    public Lexer(String src, String file) {
        this.src = src;
        this.file = file;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipSpacesAndComments();
            if (pos >= src.length()) {
                tokens.add(new Token(TokenType.EOF, "<fim do arquivo>", line, col));
                return tokens;
            }
            tokens.add(next());
        }
    }

    private Token next() {
        int startLine = line, startCol = col, start = pos;
        char c = peek(0);

        if (Character.isJavaIdentifierStart(c)) {
            while (pos < src.length() && Character.isJavaIdentifierPart(peek(0))) advance();
            return new Token(TokenType.IDENT, src.substring(start, pos), startLine, startCol);
        }
        if (Character.isDigit(c) || (c == '.' && Character.isDigit(peek(1)))) {
            while (pos < src.length() && (Character.isLetterOrDigit(peek(0)) || peek(0) == '_'
                    || (peek(0) == '.' && Character.isDigit(peek(1))))) {
                advance();
            }
            return new Token(TokenType.NUMBER, src.substring(start, pos), startLine, startCol);
        }
        if (c == '"' || c == '\'') {
            if (c == '"' && src.startsWith("\"\"\"", pos)) {
                throw new CompileError(file, startLine, startCol, "text blocks (\"\"\") não são suportados");
            }
            advance();
            while (pos < src.length() && peek(0) != c) {
                if (peek(0) == '\n') break;
                if (peek(0) == '\\') advance();
                advance();
            }
            if (pos >= src.length() || peek(0) != c) {
                throw new CompileError(file, startLine, startCol, (c == '"' ? "string" : "caractere") + " sem fechamento");
            }
            advance();
            TokenType type = c == '"' ? TokenType.STRING : TokenType.CHAR;
            return new Token(type, src.substring(start, pos), startLine, startCol);
        }
        for (String s : SYMBOLS) {
            if (src.startsWith(s, pos)) {
                for (int i = 0; i < s.length(); i++) advance();
                return new Token(TokenType.SYMBOL, s, startLine, startCol);
            }
        }
        throw new CompileError(file, startLine, startCol, "caractere inesperado '" + c + "'");
    }

    private void skipSpacesAndComments() {
        while (pos < src.length()) {
            char c = peek(0);
            if (Character.isWhitespace(c)) {
                advance();
            } else if (c == '/' && peek(1) == '/') {
                while (pos < src.length() && peek(0) != '\n') advance();
            } else if (c == '/' && peek(1) == '*') {
                int l = line, k = col;
                advance();
                advance();
                while (pos < src.length() && !(peek(0) == '*' && peek(1) == '/')) advance();
                if (pos >= src.length()) throw new CompileError(file, l, k, "comentário /* sem fechamento");
                advance();
                advance();
            } else {
                return;
            }
        }
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    private void advance() {
        if (src.charAt(pos) == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        pos++;
    }
}
