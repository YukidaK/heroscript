package heroscript.compiler;

import heroscript.runtime.Arena;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Compila o Java gerado numa pasta temporária e executa o {@code main} do programa sem encerrar a JVM.
 * Usa o compilador do JDK quando existe; senão, o ECJ (Eclipse Compiler for Java), que roda no navegador via CheerpJ.
 */
public final class JavaRunner {

    /** Sinal interno usado no lugar de System.exit enquanto um programa HeroScript executa. */
    private static final class ExitSignal extends Error {
        final int code;

        ExitSignal(int code) {
            super(null, null, false, false);
            this.code = code;
        }
    }

    private JavaRunner() {}

    /** Compila e executa; mensagens de erro vão para {@code console}. Retorna o código de saída do programa. */
    public static int run(String file, JavaGenerator.Output out, PrintStream console) {
        JavaCompiler javac = findCompiler();
        if (javac == null) {
            console.println("Erro: nenhum compilador Java disponível (instale um JDK, não apenas JRE).");
            return 1;
        }
        long start = System.currentTimeMillis();
        Path dir = compile(javac, file, out, console);
        debug(javac.getClass().getSimpleName() + ": " + (System.currentTimeMillis() - start) + " ms");
        if (dir == null) return 1;

        Arena.setExitHandler(code -> {
            throw new ExitSignal(code);
        });
        // URLClassLoader em vez de defineClass: no CheerpJ, defineClass chamado pelo nosso código trava.
        try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.resolve("classes").toUri().toURL()},
                JavaRunner.class.getClassLoader())) {
            Class<?> program = Class.forName(out.className(), true, loader);
            debug("classe carregada");
            Method main;
            try {
                main = program.getMethod("main", String[].class);
            } catch (NoSuchMethodException e) {
                console.println("Erro: " + file + " não tem bloco battle { ... } para executar.");
                return 1;
            }
            main.invoke(null, (Object) new String[0]);
            debug("main terminou");
            return 0;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof ExitSignal exit) return exit.code;
            console.println("Erro de execução: " + e.getCause());
            return 1;
        } catch (ReflectiveOperationException | IOException | LinkageError e) {
            console.println("Erro ao carregar o programa: " + e);
            return 1;
        } finally {
            Arena.setExitHandler(System::exit);
            deleteQuietly(dir);
        }
    }

    /** Com -Dheroscript.debug=true, mostra o andamento em System.err (no navegador, o console do desenvolvedor). */
    private static void debug(String message) {
        if (Boolean.getBoolean("heroscript.debug")) System.err.println("[heroscript] " + message);
    }

    static JavaCompiler findCompiler() {
        // -Dheroscript.compiler=ecj força o ECJ (para testar localmente o mesmo caminho usado no navegador).
        JavaCompiler javac = "ecj".equals(System.getProperty("heroscript.compiler")) ? null
                : ToolProvider.getSystemJavaCompiler();
        if (javac != null) return javac;
        try {
            return (JavaCompiler) Class.forName("org.eclipse.jdt.internal.compiler.tool.EclipseCompiler")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /**
     * Compila para {@code <pasta temporária>/classes} e devolve a pasta temporária (o chamador a apaga),
     * ou null se houve erro (já reportado em {@code console}).
     */
    private static Path compile(JavaCompiler javac, String file, JavaGenerator.Output out,
            PrintStream console) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fm = javac.getStandardFileManager(diagnostics, Locale.getDefault(), StandardCharsets.UTF_8);
        // Bytecode Java 17: é o máximo que o CheerpJ executa. O javac usa --release (que também limita a API);
        // o ECJ usa -source/-target, porque o --release dele depende do jrt:/ que o navegador não tem.
        boolean ecj = javac.getClass().getName().startsWith("org.eclipse.");
        List<String> target = ecj ? List.of("-source", "17", "-target", "17") : List.of("--release", "17");
        List<String> options = new ArrayList<>(List.of("-classpath", System.getProperty("java.class.path", ""),
                "-encoding", "UTF-8", "-nowarn", "-proc:none"));
        options.addAll(target);
        // O ECJ só aceita fontes que existem em disco e precisa receber o gerenciador de arquivos padrão (sem
        // embrulho), senão ignora as localizações configuradas. Por isso fonte e .class passam por uma pasta temporária.
        Path dir = null;
        try {
            dir = Files.createTempDirectory("heroscript");
            Path classesDir = Files.createDirectories(dir.resolve("classes"));
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            // No navegador o ECJ não enxerga as classes do JDK (não há jrt:/): recebe a API do java.base num jar.
            String platform = System.getProperty("heroscript.platform");
            if (platform != null) {
                fm.setLocation(StandardLocation.SYSTEM_MODULES, List.of());
                fm.setLocation(StandardLocation.PLATFORM_CLASS_PATH, List.of(new File(platform)));
            }

            Path javaFile = dir.resolve(out.className() + ".java");
            Files.writeString(javaFile, out.source(), StandardCharsets.UTF_8);
            StringWriter compilerLog = new StringWriter();
            boolean ok = javac.getTask(compilerLog, fm, diagnostics, options, null,
                    fm.getJavaFileObjects(javaFile.toFile())).call();
            if (!ok) {
                reportJavaErrors(file, out, diagnostics, console);
                if (diagnostics.getDiagnostics().isEmpty()) console.print(compilerLog);
                deleteQuietly(dir);
                return null;
            }
            return dir;
        } catch (IOException e) {
            console.println("Erro ao preparar a compilação: " + e.getMessage());
            deleteQuietly(dir);
            return null;
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var files = Files.walk(dir)) {
            for (Path f : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
        } catch (IOException ignored) {
            // pasta temporária: o sistema limpa depois
        }
    }

    /** Traduz erros do compilador Java para a linha correspondente do arquivo .hero. */
    private static void reportJavaErrors(String file, JavaGenerator.Output out,
            DiagnosticCollector<JavaFileObject> diagnostics, PrintStream console) {
        String[] lines = out.source().split("\n", -1);
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) continue;
            int genLine = (int) d.getLineNumber();
            int heroLine = genLine >= 1 && genLine <= out.lineMap().length ? out.lineMap()[genLine - 1] : 0;
            console.println("HeroCompileError em " + file + ":" + heroLine + " — " + d.getMessage(Locale.getDefault()));
            if (genLine >= 1 && genLine <= lines.length) {
                console.println("    Java gerado (linha " + genLine + "): " + lines[genLine - 1].strip());
            }
        }
    }
}
