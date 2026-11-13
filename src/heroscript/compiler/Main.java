package heroscript.compiler;

import heroscript.compiler.Ast.Program;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Linha de comando da HeroScript.
 * <pre>
 *   heroscript run    arquivo.hero          transpila, compila e executa
 *   heroscript build  arquivo.hero [-o dir] grava o .java gerado
 *   heroscript check  arquivo.hero          só valida (léxico, sintaxe e semântica)
 *   heroscript tokens arquivo.hero          mostra os tokens do lexer
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("batinvestigate")) {
            System.exit(Investigator.command(args, System.out));
            return;
        }
        if (args.length < 2) {
            usage();
            System.exit(2);
        }
        String command = args[0];
        Path source = Paths.get(args[1]);
        String file = source.getFileName().toString();
        try {
            switch (command) {
                case "run" -> System.exit(JavaRunner.run(file, transpile(read(source), file), System.out));
                case "build" -> build(source, args.length >= 4 && args[2].equals("-o") ? Paths.get(args[3]) : genDir());
                case "check" -> {
                    var output = transpile(read(source), file);
                    if (!JavaRunner.validate(file, output, System.out)) System.exit(1);
                    System.out.println("OK: " + source + " é um programa HeroScript válido");
                }
                case "tokens" -> new Lexer(read(source), file).tokenize().forEach(System.out::println);
                case "spiderSense" -> {
                    var issues=SpiderSense.analyze(read(source),file);
                    System.out.println(SpiderSense.json(issues));
                    if(issues.stream().anyMatch(i->i.severity().equals("error")))System.exit(1);
                }
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        } catch (CompileError e) {
            System.out.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Erro ao ler/gravar arquivo: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usage() {
        System.out.println("""
                uso: heroscript <comando> arquivo.hero
                  run     transpila, compila e executa o programa
                  build   grava o código Java gerado (opcional: -o pasta)
                  check   apenas valida o programa
                  tokens  mostra os tokens produzidos pelo lexer
                  spiderSense  analisa sem executar e retorna diagnósticos JSON
                  batinvestigate diretorio-ou-arquivo.hero simbolo  lista origens não secret""");
    }

    /** Pipeline completo: texto → tokens → AST → Java. */
    public static JavaGenerator.Output transpile(String src, String file) {
        List<Token> tokens = new Lexer(src, file).tokenize();
        Program program = new Parser(tokens, file).parse();
        return new JavaGenerator(file, program).generate();
    }

    private static String read(Path source) throws IOException {
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static Path genDir() {
        return Paths.get(System.getProperty("heroscript.home", "."), "build", "gen");
    }

    private static void build(Path source, Path dir) throws IOException {
        JavaGenerator.Output out = transpile(read(source), source.getFileName().toString());
        Files.createDirectories(dir);
        Path javaFile = dir.resolve(out.className() + ".java");
        Files.writeString(javaFile, out.source(), StandardCharsets.UTF_8);
        System.out.println("Java gerado em " + javaFile);
    }
}
