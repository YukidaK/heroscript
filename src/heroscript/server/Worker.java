package heroscript.server;

import heroscript.compiler.CompileError;
import heroscript.compiler.JavaRunner;
import heroscript.compiler.Main;
import heroscript.playground.Playground;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * JVM filha do {@link RunnerServer}: lê a operação e o programa da entrada padrão, executa e termina.
 * Usa as mesmas rotinas do playground no navegador, para as duas formas de execução darem a mesma saída.
 */
public final class Worker {

    private static final String FILE = "playground.hero";

    private Worker() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("warm")) warmUp();
        // Entrada: a operação na primeira linha, depois o programa.
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        int newline = input.indexOf('\n');
        String kind = input.substring(0, Math.max(newline, 0));
        String source = input.substring(newline + 1);
        // A saída vai direto para o pipe, linha a linha: se o programa for interrompido por tempo, o que ele já
        // imprimiu chega ao usuário.
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.setOut(out);
        System.setErr(out);
        int code = 0;
        switch (kind) {
            case "run" -> code = run(source, out);
            case "java" -> out.println(Playground.java(source));
            case "tokens" -> out.println(Playground.tokens(source));
            case "diagnostics" -> out.println(Playground.diagnostics(source));
            default -> throw new IllegalArgumentException(kind);
        }
        out.flush();
        // halt, não exit: threads ou shutdown hooks deixados pelo programa não podem segurar o processo.
        Runtime.getRuntime().halt(code);
    }

    /**
     * Compila (sem executar) um programa mínimo, para carregar o compilador da HeroScript e o javac antes de o
     * programa do usuário chegar. Não imprime nada e não deixa estado: uma falha aqui só torna a execução mais lenta.
     */
    private static void warmUp() {
        try {
            String source = "hero Warmup { power P { cost = 1; } }\nbattle { summon Warmup as w; unleash w.P; }";
            JavaRunner.validate(FILE, Main.transpile(source, FILE), new PrintStream(OutputStream.nullOutputStream()));
        } catch (RuntimeException | Error ignored) {
            // segue sem aquecimento
        }
    }

    private static int run(String source, PrintStream out) {
        try {
            return JavaRunner.run(FILE, Main.transpile(source, FILE), out);
        } catch (CompileError e) {
            out.println(e.getMessage());
            return 1;
        } catch (RuntimeException | Error e) {
            out.println("Erro interno: " + e);
            return 1;
        }
    }
}
