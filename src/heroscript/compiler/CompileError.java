package heroscript.compiler;

/** Erro detectado em tempo de compilação (léxico, sintático ou semântico), sempre com arquivo:linha:coluna. */
public class CompileError extends RuntimeException {

    public CompileError(String file, int line, int col, String message) {
        super("HeroSyntaxError em " + file + ":" + line + ":" + col + " — " + message);
    }

    public CompileError(String file, Token at, String message) {
        this(file, at.line(), at.col(), message);
    }
}
