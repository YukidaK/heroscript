package heroscript.playground;

import heroscript.compiler.CompileError;
import heroscript.compiler.JavaRunner;
import heroscript.compiler.Lexer;
import heroscript.compiler.Main;
import heroscript.compiler.Token;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Ponte para o playground web: chamada pelo JavaScript via CheerpJ, sempre devolve texto e nunca encerra a JVM. */
public final class Playground {

    private static final String FILE = "playground.hero";

    private Playground() {}

    /** Executa o programa e devolve tudo que ele imprimiu, terminando com {@code [exit N]}. */
    public static String run(String source) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        PrintStream original = System.out;
        System.setOut(console);
        int code;
        long start = System.currentTimeMillis();
        try {
            var out = Main.transpile(source, FILE);
            log("transpilado em " + (System.currentTimeMillis() - start) + " ms; compilando e executando...");
            code = JavaRunner.run(FILE, out, console);
            log("concluído em " + (System.currentTimeMillis() - start) + " ms");
        } catch (CompileError e) {
            console.println(e.getMessage());
            code = 1;
        } catch (RuntimeException | Error e) {
            // Nada pode escapar daqui: uma exceção não tratada deixaria o JavaScript esperando para sempre.
            console.println("Erro interno: " + e);
            code = 1;
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8) + "[exit " + code + "]";
    }

    /** Progresso no console do navegador (System.err não é redirecionado para a saída do programa). */
    private static void log(String message) {
        System.err.println("[heroscript] " + message);
    }

    /** Código Java gerado, ou a mensagem de erro de compilação. */
    public static String java(String source) {
        try {
            return Main.transpile(source, FILE).source();
        } catch (CompileError e) {
            return e.getMessage();
        }
    }

    /** Lista de tokens produzida pelo lexer, um por linha. */
    public static String tokens(String source) {
        try {
            return new Lexer(source, FILE).tokenize().stream().map(Token::toString).collect(Collectors.joining("\n"));
        } catch (CompileError e) {
            return e.getMessage();
        }
    }
}
