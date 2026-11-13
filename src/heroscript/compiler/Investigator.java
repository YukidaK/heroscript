package heroscript.compiler;

import heroscript.compiler.Ast.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Static declaration lookup. Public results never contain secret declarations or raw source. */
public final class Investigator {
    private static final String UNAVAILABLE = "símbolo não disponível para investigação";
    private static final String ANALYSIS_ERROR = "não foi possível analisar o arquivo; detalhes de código foram omitidos";
    private static final String PARTIAL = "busca parcial: um ou mais arquivos não puderam ser lidos ou analisados; conteúdo e detalhes de código foram omitidos";
    private static final Set<String> EXCLUDED = Set.of(".git", ".codex", ".agents", ".venv", "node_modules", "build", "site", ".terraform");
    private static final Set<String> MODIFIERS = Set.of("public", "private", "protected", "static", "final", "transient", "volatile", "synchronized", "strictfp");

    public record Symbol(String name, String qualifiedName, String kind,
                         String file, int line, int column, String selector) {}

    public record Result(int code, List<Symbol> symbols, String message) {
        public Result { symbols = List.copyOf(symbols); }
        public void print(PrintStream console) {
            if (!message.isEmpty()) console.println(message);
            for (Symbol symbol : symbols) {
                console.println("[" + symbol.kind() + "] " + symbol.qualifiedName() + " | "
                        + symbol.file() + ":" + symbol.line() + ":" + symbol.column());
            }
        }
    }

    private final String file;
    private final List<Symbol> visible = new ArrayList<>();

    private Investigator(String file) { this.file = file; }

    /** Parse declarations without compiling or executing; invalid types and duplicate declarations are searchable. */
    public static Result investigate(String source, String file, String query) {
        if (query == null || query.isBlank()) return new Result(2, List.of(), "informe o símbolo a localizar");
        try {
            Program program = new Parser(new Lexer(source, file).tokenize(), file).parse();
            Investigator index = new Investigator(file);
            index.collect(program);
            String normalized = compact(query);
            List<Symbol> matches = index.visible.stream().filter(s -> s.name().equals(normalized)
                    || compact(s.qualifiedName()).equals(normalized) || compact(s.selector()).equals(normalized)
                    || ((s.kind().equals("method") || s.kind().equals("mission"))
                        && compact(s.name() + s.selector().substring(s.qualifiedName().length())).equals(normalized))).toList();
            if (matches.isEmpty()) return new Result(1, List.of(), UNAVAILABLE);
            List<Symbol> results = sorted(matches);
            return new Result(0, results, "");
        } catch (CompileError | IllegalArgumentException | IndexOutOfBoundsException e) {
            // Do not forward exception messages, queries or snippets through this report.
            return new Result(2, List.of(), ANALYSIS_ERROR);
        }
    }

    /** Search independent .hero files recursively, keeping every visible origin. Never follow symbolic links. */
    public static Result investigate(Path root, String query) {
        if (query == null || query.isBlank()) return new Result(2, List.of(), "informe o símbolo a localizar");
        List<Path> files = new ArrayList<>();
        boolean[] incomplete = {false};
        try {
            if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS) && heroFile(root)) {
                files.add(root);
            } else if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        return !dir.equals(root) && EXCLUDED.contains(dir.getFileName().toString())
                                ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && heroFile(file)) files.add(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file, IOException error) {
                        incomplete[0] = true;
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) {
                        if (error != null) incomplete[0] = true;
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                return new Result(2, List.of(), "informe um diretório ou arquivo .hero legível");
            }
        } catch (IOException | SecurityException e) { incomplete[0] = true; }
        List<Symbol> origins = new ArrayList<>();
        for (Path file : files) {
            try {
                Result result = investigate(Files.readString(file, StandardCharsets.UTF_8), file.toString(), query);
                origins.addAll(result.symbols());
                if (result.code() == 2) incomplete[0] = true;
            } catch (IOException | SecurityException e) { incomplete[0] = true; }
        }
        if (incomplete[0]) return new Result(2, sorted(origins), PARTIAL);
        return new Result(origins.isEmpty() ? 1 : 0, sorted(origins), origins.isEmpty() ? UNAVAILABLE : "");
    }

    private static boolean heroFile(Path path) {
        return path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".hero");
    }

    private static List<Symbol> sorted(List<Symbol> symbols) {
        return symbols.stream().sorted(Comparator.comparing(Symbol::file).thenComparingInt(Symbol::line)
                .thenComparingInt(Symbol::column).thenComparing(Symbol::qualifiedName)).toList();
    }

    public static int command(String[] args, PrintStream console) {
        if (args.length != 3) {
            console.println("uso: heroscript batinvestigate diretorio-ou-arquivo.hero simbolo");
            return 2;
        }
        try {
            Path path = Path.of(args[1]);
            Result result = investigate(path, args[2]);
            result.print(console);
            return result.code();
        } catch (InvalidPathException | SecurityException e) {
            console.println("não foi possível pesquisar o caminho informado");
            return 2;
        }
    }

    private void add(Token name, String qualified, String kind, String selector) {
        visible.add(new Symbol(name.text(), qualified, kind, file, name.line(), name.col(), selector));
    }

    private void collect(Program program) {
        for (CharacterDecl character : program.characters()) {
            String owner = character.name().text();
            String kind = character.villain() ? "villain" : "hero";
            add(character.name(), owner, kind, owner);
            fields(character.fields(), owner);
            // character.secrets() is deliberately never traversed.
            abilities(character.powers(), owner, "power");
            abilities(character.gadgets(), owner, "gadget");
            abilities(character.forms(), owner, "form");
            for (MissionDecl mission : character.missions()) {
                if (mission.secret()) continue;
                String qualified = owner + "." + mission.name().text();
                String selector = selector(qualified, mission.params());
                add(mission.name(), qualified, "mission", selector);
                locals(mission.body(), selector);
            }
            methods(character.methods(), owner);
        }
        for (BatcaveDecl cave : program.batcaves()) {
            String owner = cave.name().text();
            add(cave.name(), owner, "batcave", owner);
            fields(cave.fields(), owner);
            methods(cave.methods(), owner);
        }
        for (SquadDecl squad : program.squads()) {
            String owner = squad.name().text();
            add(squad.name(), owner, "squad", owner);
            abilities(squad.combos(), owner, "combo");
        }
        if (program.battle() != null) locals(program.battle(), "battle");
    }

    private void abilities(List<PropertyBlock> blocks, String owner, String kind) {
        for (PropertyBlock block : blocks) {
            String qualified = owner + "." + block.name().text();
            add(block.name(), qualified, kind, qualified);
        }
    }

    private void methods(List<CaveMethod> methods, String owner) {
        for (CaveMethod method : methods) {
            if (method.secret()) continue; // Includes all declarations inside this body.
            String qualified = owner + "." + method.name().text();
            String selector = selector(qualified, method.params());
            add(method.name(), qualified, "method", selector);
            locals(method.body(), selector);
        }
    }

    private void fields(List<FieldDecl> fields, String owner) {
        for (FieldDecl field : fields) {
            if (field.secret()) continue;
            List<Token> tokens = field.tokens();
            int firstEnd = 0;
            int angle = 0;
            int nesting = 0;
            while (firstEnd < tokens.size()) {
                Token token = tokens.get(firstEnd);
                angle += angleDelta(token);
                if (token.is("(")) nesting++;
                if (token.is(")")) nesting--;
                if (angle == 0 && nesting == 0 && (token.is("=") || token.is(";") || token.is(","))) break;
                firstEnd++;
            }
            int nameIndex = firstEnd - 1;
            while (nameIndex >= 0 && (tokens.get(nameIndex).is("[") || tokens.get(nameIndex).is("]"))) nameIndex--;
            if (nameIndex < 0 || !tokens.get(nameIndex).isIdent()) throw new IllegalArgumentException();
            Token name = tokens.get(nameIndex);
            field(name, owner);
            // Common Java fields may declare several names. Never render initializers.
            int parens = 0, brackets = 0, generics = 0;
            boolean afterNew = false;
            for (int i = firstEnd; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.is("new")) afterNew = true;
                if (token.is("(") || token.is("{")) { parens++; afterNew = false; }
                if (token.is(")") || token.is("}")) parens--;
                if (token.is("[")) brackets++;
                if (token.is("]")) brackets--;
                if (afterNew || generics > 0) generics = Math.max(0, generics + angleDelta(token));
                if (token.is(",") && parens == 0 && brackets == 0 && generics == 0) {
                    Token next = tokens.get(++i);
                    if (!next.isIdent()) throw new IllegalArgumentException();
                    field(next, owner);
                    afterNew = false;
                }
            }
        }
    }

    private void field(Token name, String owner) {
        String qualified = owner + "." + name.text();
        add(name, qualified, "field", qualified);
    }

    private void locals(Block block, String scope) {
        for (Stmt statement : block.stmts()) {
            if (statement instanceof Kryptonite local) {
                String selector = scope + "::" + local.name().text();
                String qualified = scope.replaceAll("\\([^)]*\\)", "") + "::" + local.name().text();
                add(local.name(), qualified, "kryptonite", selector);
            } else if (statement instanceof Block nested) {
                locals(nested, scope);
            } else if (statement instanceof HulkSmash hulk) {
                locals(hulk.sourceBody(),scope);
            } else if (statement instanceof BlackWidow widow) {
                locals(widow.body(),scope);
            } else if (statement instanceof FlashPoint point) {
                locals(point.body(),scope);
            } else if (statement instanceof Ast.DeadpoolAction action) {
                locals(action.body(),scope);
            } else if (statement instanceof Raw raw) {
                for(var binding:JavaBindings.collect(raw.tokens())) {
                    String name=binding.name().text();
                    add(binding.name(),scope.replaceAll("\\([^)]*\\)","")+"::"+name,"local",scope+"::"+name);
                }
                if(raw.body()!=null)locals(raw.body(), scope);
            }
            // SecretLocal and descendants of secret methods remain hidden.
        }
    }

    private static String selector(String qualified, List<Token> params) {
        try { return qualified + "(" + parameterTypes(params) + ")"; }
        catch (IllegalArgumentException e) { return qualified; }
    }

    private static String parameterTypes(List<Token> params) {
        if (params.isEmpty()) return "";
        List<String> types = new ArrayList<>();
        int start = 0, depth = 0;
        for (int i = 0; i <= params.size(); i++) {
            if (i < params.size()) depth += angleDelta(params.get(i));
            if (i == params.size() || (depth == 0 && params.get(i).is(","))) {
                List<Token> parameter = params.subList(start, i);
                int name = parameter.size() - 1;
                while (name >= 0 && (parameter.get(name).is("[") || parameter.get(name).is("]"))) name--;
                if (name < 1 || !parameter.get(name).isIdent()) throw new IllegalArgumentException();
                types.add(typeText(parameter.subList(0, name)) + compactText(parameter.subList(name + 1, parameter.size())));
                start = i + 1;
            }
        }
        return String.join(",", types);
    }

    private static String typeText(List<Token> tokens) {
        for (Token token : tokens) {
            if (!token.isIdent() && !Set.of(".", "<", ">", ">>", ">>>", "?", "[", "]", "...", "&", ",").contains(token.text())) {
                throw new IllegalArgumentException();
            }
        }
        return compactText(tokens.stream().filter(token -> !MODIFIERS.contains(token.text())).toList());
    }

    private static String compactText(List<Token> tokens) {
        StringBuilder text = new StringBuilder();
        for (Token token : tokens) text.append(token.text());
        return text.toString();
    }

    private static int angleDelta(Token token) {
        return switch (token.text()) { case "<" -> 1; case ">" -> -1; case ">>" -> -2; case ">>>" -> -3; default -> 0; };
    }

    private static String compact(String text) { return text.replaceAll("\\s+", ""); }
}
