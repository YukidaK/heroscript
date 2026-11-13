import heroscript.compiler.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/** Declaration origins, directory traversal, secret filtering and unified CLI. */
public class InvestigatorTests {
    static int passed;
    static final String SOURCE = """
        hero Agent {
            secret String HIDDEN_FIELD = "HIDDEN_LITERAL" allow Read;
            int visibleField = 2, secondField = Math.max(3, 4);
            mission Read {
                secret int HIDDEN_LOCAL = 42;
                kryptonite int reserve = 7;
                { kryptonite int repeated = 1; }
                { kryptonite int repeated = 2; }
            }
            secret mission HIDDEN_MISSION { kryptonite int HIDDEN_CHILD = 1; }
        }
        batcave Vault {
            int ordinary = 1;
            secret String HIDDEN_CAVE_FIELD = "HIDDEN_LITERAL";
            private int privateMethod(String code) { return code.length(); }
            public boolean authenticate(String code) { return privateMethod(code) > 0; }
            public boolean authenticate(int code) { return code > 0; }
            secret public int HIDDEN_METHOD() { kryptonite int HIDDEN_METHOD_CHILD = 1; return 1; }
        }
        battle { kryptonite int reserve=9; int ordinaryLocal=1; announce "MUST_NOT_RUN"; }
        """;
    static void expect(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
    static Investigator.Result investigate(String source, String query) {
        return Investigator.investigate(source, "investigation.hero", query);
    }
    static String render(Investigator.Result result) {
        var buffer = new ByteArrayOutputStream();
        result.print(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return result.code() + "\n" + buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
    record Cli(int code, String output) {}
    static Cli cli(String... args) throws Exception {
        var command = new java.util.ArrayList<>(List.of(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-cp", System.getProperty("java.class.path"), "heroscript.compiler.Main"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        return new Cli(process.waitFor(), output);
    }
    static Path write(Path path, String source) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, source, StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        var result = investigate(SOURCE, "Vault");
        expect(result.code() == 0 && result.symbols().size() == 1, "container location without listing its contents");
        expect(!render(result).contains("authenticate") && !render(result).contains("ordinary"), "no container contents");
        result = investigate(SOURCE, "Vault.authenticate");
        expect(result.symbols().size() == 2, "all overload origins");
        expect(result.symbols().get(0).line() == 16 && result.symbols().get(0).column() == 20, "original method coordinates");
        String text = render(result);
        expect(!text.contains("boolean") && !text.contains("String") && !text.contains("return") && !text.contains("privateMethod"), "locations never contain signature or implementation");
        expect(investigate(SOURCE, "Vault.authenticate( String )").symbols().size() == 1, "optional signature query");
        expect(investigate(SOURCE, "authenticate(int)").symbols().size() == 1, "short signature query");
        expect(investigate(SOURCE, "privateMethod").code() == 0, "private non-secret batcave member visible");
        String absent = render(investigate(SOURCE, "missing"));
        for (String query : List.of("HIDDEN_FIELD", "Agent.HIDDEN_FIELD", "HIDDEN_LOCAL", "HIDDEN_MISSION",
                "HIDDEN_CHILD", "HIDDEN_CAVE_FIELD", "Vault.HIDDEN_CAVE_FIELD", "HIDDEN_METHOD", "HIDDEN_METHOD_CHILD")) {
            expect(render(investigate(SOURCE, query)).equals(absent), "hidden identical to absent: " + query);
        }
        result = investigate(SOURCE, "reserve");
        expect(result.symbols().size() == 2, "all kryptonite local origins");
        expect(investigate(SOURCE, "repeated").symbols().size() == 2, "same name in independent blocks");
        text = render(investigate("hero H { mission Read(String input) { kryptonite int reserve=1; } }", "reserve"));
        expect(text.contains("H.Read::reserve") && !text.contains("String") && !text.contains("input"), "local origin does not display method parameter contents");
        expect(investigate(SOURCE, "ordinaryLocal").code() == 0, "ordinary raw locals are indexed");
        expect(!render(investigate(SOURCE, "secondField")).contains("Math.max"), "field initializer never displayed");
        expect(investigate("hero H { int duplicate=1; int duplicate=2; }", "duplicate").symbols().size() == 2, "duplicate fields remain searchable");
        expect(investigate("batcave C { public int same(){return 1;} public int same(){return 2;} }", "same").symbols().size() == 2, "duplicate method declarations remain searchable");
        expect(investigate("batcave C { public int bad(){return \"TYPE_ERROR\";} }", "bad").code() == 0, "type errors do not block origin lookup");
        expect(!render(investigate("hero H { @SuppressWarnings(\"HIDDEN_LITERAL\") int value=1; }", "value")).contains("HIDDEN_LITERAL"), "annotations never displayed");
        expect(investigate("hero A { secret int same=1; } batcave B { int same=2; }", "same").symbols().size() == 1, "visible namesake not suppressed");
        expect(investigate("hero H { power Beam {} gadget Armor {} form Strong {} }", "Beam").code() == 0, "power origin");
        expect(investigate("squad Team { combo Strike {} }", "Strike").code() == 0, "combo origin");
        result = investigate("batcave C { secret int HIDDEN_FIELD = ;", "C");
        expect(result.code() == 2 && !render(result).contains("HIDDEN"), "syntax errors filtered");
        expect(investigate(SOURCE, " ").code() == 2, "blank query");
        String property = "heroscript.investigator.test.executed";
        System.clearProperty(property);
        result = investigate("hero H { static String value=System.setProperty(\"" + property + "\",\"ran\"); } battle { announce \"MUST_NOT_RUN\"; }", "value");
        expect(result.code() == 0 && System.getProperty(property) == null && !render(result).contains("MUST_NOT_RUN"), "no execution or initializer display");

        Path directory = Files.createTempDirectory("hero investigation tests ");
        try {
            Path first = write(directory.resolve("one/source with spaces.hero"), SOURCE);
            Path second = write(directory.resolve("two/other.hero"), "batcave Vault { public int authenticate(){return 3;} int reserve=4; }");
            write(directory.resolve("three/hidden.HERO"), "batcave Vault { secret public int authenticate(){return 4;} secret int reserve=5; }");
            write(directory.resolve("build/ignored.hero"), "batcave Vault { public int authenticate(){return 0;} }");
            write(directory.resolve(".git/ignored.hero"), "batcave Vault { public int authenticate(){return 0;} }");
            write(directory.resolve("notes.txt"), "this is not HeroScript");
            result = Investigator.investigate(directory, "authenticate");
            expect(result.code() == 0 && result.symbols().size() == 3, "all visible origins across recursive files including overloads");
            expect(result.symbols().get(0).file().equals(first.toString()) && result.symbols().get(2).file().equals(second.toString()), "deterministic file and position order");
            expect(Investigator.investigate(directory, "Vault").symbols().size() == 3, "all container declarations including secret-only containers");
            expect(Investigator.investigate(directory, "reserve").symbols().size() == 3, "all field and local namesakes across files");
            expect(Investigator.investigate(first, "authenticate").symbols().size() == 2, "explicit file remains supported");
            Cli found = cli("batinvestigate", directory.toString(), "authenticate");
            expect(found.code() == 0 && found.output().lines().count() == 3 && !found.output().contains("return") && !found.output().contains("String"), "unified CLI prints every origin without code");
            expect(!found.output().contains("hidden.HERO") && !found.output().contains("ignored.hero"), "secrets and excluded directories filtered");
            Cli hidden = cli("batinvestigate", directory.toString(), "HIDDEN_METHOD");
            Cli missing = cli("batinvestigate", directory.toString(), "missing");
            expect(hidden.code() == 1 && hidden.equals(missing), "recursive CLI hidden and absent identical");
            expect(cli("batinvestigate", first.toString(), "Vault").output().lines().count() == 1, "file lookup prints only selected declaration");
            expect(cli("batinvestigate").code() == 2, "missing arguments");
            expect(cli("batinvestigate", directory.toString(), "").code() == 2, "empty query");
            expect(cli("batinvestigate", directory.resolve("missing").toString(), "Vault").code() == 2, "missing root");
            expect(cli("batinvestigate", "find", first.toString(), "Vault").code() == 2, "old find subcommand removed");
            expect(cli("batinvestigate", "inspect", first.toString(), "Vault").code() == 2, "old inspect subcommand removed");
            expect(cli("batinvestigate", "trace", first.toString(), "Vault").code() == 2, "old trace subcommand removed");
            Path invalid = write(directory.resolve("invalid.hero"), "batcave Broken { secret int HIDDEN_LITERAL = ;");
            result = Investigator.investigate(directory, "authenticate");
            expect(result.code() == 2 && result.symbols().size() == 3 && result.message().contains("parcial"), "partial scan preserves every known origin");
            expect(!render(result).contains("HIDDEN_LITERAL"), "partial diagnostics filtered");
            Cli partial = cli("batinvestigate", directory.toString(), "authenticate");
            expect(partial.code() == 2 && partial.output().contains("parcial") && partial.output().contains(second.toString()), "CLI partial results explicit");
            expect(Investigator.investigate(invalid, "Broken").code() == 2, "invalid explicit file");
            expect(Investigator.investigate(directory.resolve("notes.txt"), "x").code() == 2, "non-hero file rejected");
            Path empty = Files.createDirectory(directory.resolve("empty"));
            expect(Investigator.investigate(empty, "x").code() == 1, "empty directory has no origins");
            Files.delete(invalid);
            expect(render(Investigator.investigate(directory, "authenticate")).equals(render(Investigator.investigate(directory, "authenticate"))), "repeatable ordering");
        } finally {
            // Delete only the exact files created by this test, within its fresh temporary directory.
            for (String file : List.of("one/source with spaces.hero", "two/other.hero", "three/hidden.HERO", "build/ignored.hero", ".git/ignored.hero", "notes.txt", "invalid.hero")) Files.deleteIfExists(directory.resolve(file));
            for (String folder : List.of("one", "two", "three", "build", ".git", "empty")) Files.deleteIfExists(directory.resolve(folder));
            Files.deleteIfExists(directory);
        }
        System.out.println(passed + " investigator checks passed");
    }
}
