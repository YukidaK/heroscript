package heroscript.compiler;

import heroscript.compiler.Ast.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Análise semântica + geração de código: percorre a AST, valida as regras da HeroScript e escreve um arquivo Java
 * equivalente que usa as classes de {@code heroscript.runtime}. Guarda, para cada linha gerada, a linha do .hero que
 * a originou, para que erros do javac apontem para o código HeroScript.
 */
public class JavaGenerator {

    public record Output(String className, String source, int[] lineMap) {}

    private static final Set<String> CHARACTER_FIELDS = Set.of("name", "energy", "health");
    private static final Set<String> POWER_PROPS = Set.of("cost", "damage", "cooldown");
    private static final Set<String> GADGET_PROPS = Set.of("defense", "uses");
    private static final Set<String> COMBO_PROPS = Set.of("cost", "damage", "requires");
    private static final Set<String> FORM_SETTINGS = Set.of("duration", "uses");
    private static final Set<String> FORM_STATS = Set.of("damage", "defense", "energy", "health", "cost");
    private static final Set<String> MODIFIERS =
            Set.of("public", "private", "protected", "static", "final", "transient", "volatile");

    private final String file;
    private final Program program;
    private final String className;

    private final StringBuilder out = new StringBuilder();
    private final List<Integer> lineMap = new ArrayList<>();
    private int indent = 0;

    private final Map<String, CharacterDecl> characters = new LinkedHashMap<>();
    private final Map<String, SquadDecl> squads = new LinkedHashMap<>();
    private final Set<String> allSecrets = new HashSet<>();

    // Contexto do trecho sendo gerado.
    private Set<String> currentSecrets = Set.of();
    private boolean inMission = false;
    private final Map<String, String> summoned = new HashMap<>();

    public JavaGenerator(String file, Program program) {
        this.file = file;
        this.program = program;
        this.className = classNameFor(file);
    }

    public static String classNameFor(String file) {
        String base = file.replaceAll(".*[/\\\\]", "").replaceAll("\\.hero$", "");
        String clean = base.replaceAll("[^A-Za-z0-9]", "_");
        if (clean.isEmpty() || !Character.isLetter(clean.charAt(0))) clean = "H" + clean;
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1) + "Program";
    }

    public Output generate() {
        collectDeclarations();
        emit(0, "// Gerado automaticamente pelo compilador HeroScript a partir de " + file + ". Não edite.");
        emit(0, "import heroscript.runtime.*;");
        emit(0, "");
        emit(0, "public class " + className + " {");
        indent++;
        for (CharacterDecl c : program.characters()) genCharacter(c);
        for (SquadDecl s : program.squads()) genSquad(s);
        if (program.battle() != null) genBattle(program.battle());
        indent--;
        emit(0, "}");
        int[] map = lineMap.stream().mapToInt(Integer::intValue).toArray();
        return new Output(className, out.toString(), map);
    }

    // ---------------------------------------------------------------- declarações

    private void collectDeclarations() {
        for (CharacterDecl c : program.characters()) {
            String n = c.name().text();
            if (characters.containsKey(n) || squads.containsKey(n)) throw error(c.name(), "'" + n + "' já foi declarado");
            characters.put(n, c);
            c.secrets().forEach(s -> allSecrets.add(s.name().text()));
        }
        for (SquadDecl s : program.squads()) {
            String n = s.name().text();
            if (characters.containsKey(n) || squads.containsKey(n)) throw error(s.name(), "'" + n + "' já foi declarado");
            squads.put(n, s);
        }
    }

    private void genCharacter(CharacterDecl c) {
        String n = c.name().text();
        int line = c.name().line();
        currentSecrets = new HashSet<>();
        c.secrets().forEach(s -> currentSecrets.add(s.name().text()));

        Set<String> missionNames = new HashSet<>();
        for (MissionDecl m : c.missions()) {
            if (!missionNames.add(m.name().text())) throw error(m.name(), "missão '" + m.name().text() + "' duplicada");
        }

        emit(line, "");
        emit(line, "public static class " + n + " extends " + (c.villain() ? "Villain" : "Hero") + " {");
        indent++;

        for (FieldDecl f : c.fields()) {
            if (characterFieldIndex(f) >= 0) continue;
            boolean hasModifier = MODIFIERS.contains(f.tokens().get(0).text());
            emit(f.tokens().get(0).line(), (hasModifier ? "" : "public ") + java(f.tokens()));
        }
        for (SecretDecl s : c.secrets()) {
            for (Token m : s.allowed()) {
                if (!missionNames.contains(m.text())) {
                    throw error(m, "secret '" + s.name().text() + "' autoriza a missão '" + m.text()
                            + "', que não existe em " + n);
                }
            }
            String init = s.init().isEmpty() ? "" : " = " + java(s.init());
            emit(s.name().line(), "private " + java(s.type()) + " " + s.name().text() + init + ";");
        }

        emit(line, "");
        emit(line, "public " + n + "() {");
        indent++;
        emit(line, "super(\"" + n + "\");");
        for (FieldDecl f : c.fields()) {
            int idx = characterFieldIndex(f);
            if (idx < 0) continue;
            List<Token> value = f.tokens().subList(idx + 2, f.tokens().size() - 1);
            emit(f.tokens().get(0).line(), "this." + f.tokens().get(idx).text() + " = " + java(value) + ";");
        }
        Set<String> abilityNames = new HashSet<>();
        for (PropertyBlock p : c.powers()) {
            unique(abilityNames, p.name(), "poder");
            Map<String, String> v = props(p, "power", POWER_PROPS);
            emit(p.name().line(), "definePower(\"" + p.name().text() + "\", " + v.getOrDefault("cost", "0") + ", "
                    + v.getOrDefault("damage", "0") + ", " + v.getOrDefault("cooldown", "0") + ");");
        }
        for (PropertyBlock g : c.gadgets()) {
            unique(abilityNames, g.name(), "gadget");
            Map<String, String> v = props(g, "gadget", GADGET_PROPS);
            emit(g.name().line(), "defineGadget(\"" + g.name().text() + "\", " + v.getOrDefault("defense", "0") + ", "
                    + v.getOrDefault("uses", "-1") + ");");
        }
        for (PropertyBlock f : c.forms()) {
            unique(abilityNames, f.name(), "forma");
            emit(f.name().line(), "defineForm(\"" + f.name().text() + "\")" + formChain(f) + ";");
        }
        emit(line, "ready();");
        indent--;
        emit(line, "}");

        for (SecretDecl s : c.secrets()) {
            String sn = s.name().text();
            StringBuilder allowed = new StringBuilder();
            for (Token m : s.allowed()) allowed.append(", \"").append(m.text()).append('"');
            emit(s.name().line(), "");
            emit(s.name().line(), "public " + java(s.type()) + " __reveal_" + sn + "() {");
            indent++;
            emit(s.name().line(), "checkSecret(\"" + sn + "\"" + allowed + ");");
            emit(s.name().line(), "return " + sn + ";");
            indent--;
            emit(s.name().line(), "}");
        }

        for (MissionDecl m : c.missions()) {
            int ml = m.name().line();
            emit(ml, "");
            emit(ml, "public void " + m.name().text() + "(" + java(m.params()) + ") {");
            indent++;
            emit(ml, "Arena.enterMission(this, \"" + m.name().text() + "\");");
            emit(ml, "try {");
            indent++;
            inMission = true;
            genStmts(m.body().stmts());
            inMission = false;
            indent--;
            emit(ml, "} finally {");
            emit(ml, "    Arena.exitMission();");
            emit(ml, "}");
            indent--;
            emit(ml, "}");
        }

        indent--;
        emit(line, "}");
        currentSecrets = Set.of();
    }

    /** Posição do nome em atributos como {@code int energy = 100;} ou {@code energy = 100;}; -1 se for atributo comum. */
    private int characterFieldIndex(FieldDecl f) {
        List<Token> t = f.tokens();
        for (int i = 0; i <= 1 && i + 1 < t.size(); i++) {
            if (CHARACTER_FIELDS.contains(t.get(i).text()) && t.get(i + 1).is("=")) return i;
        }
        return -1;
    }

    private String formChain(PropertyBlock f) {
        StringBuilder sb = new StringBuilder();
        for (Property p : f.props()) {
            String name = p.name().text();
            if (FORM_SETTINGS.contains(name)) {
                if (!p.op().is("=")) throw error(p.op(), "'" + name + "' só aceita '='");
                sb.append(".set(\"").append(name).append("\", ").append(java(p.value())).append(')');
            } else if (FORM_STATS.contains(name)) {
                sb.append(".modify(\"").append(name).append("\", \"").append(p.op().text()).append("\", ")
                        .append(java(p.value())).append(')');
            } else {
                throw error(p.name(), "propriedade desconhecida '" + name + "' em form " + f.name().text()
                        + " (use duration, uses, damage, defense, energy, health ou cost)");
            }
        }
        return sb.toString();
    }

    private Map<String, String> props(PropertyBlock b, String kind, Set<String> allowed) {
        Map<String, String> values = new HashMap<>();
        for (Property p : b.props()) {
            String name = p.name().text();
            if (!allowed.contains(name)) {
                throw error(p.name(), "propriedade desconhecida '" + name + "' em " + kind + " " + b.name().text()
                        + " (esperado: " + String.join(", ", allowed.stream().sorted().toList()) + ")");
            }
            if (!p.op().is("=")) throw error(p.op(), "em " + kind + " use apenas '='");
            if (values.put(name, "(" + java(p.value()) + ")") != null) {
                throw error(p.name(), "propriedade '" + name + "' repetida");
            }
        }
        return values;
    }

    private void unique(Set<String> seen, Token name, String kind) {
        if (!seen.add(name.text())) throw error(name, kind + " '" + name.text() + "' usa um nome já declarado");
    }

    private void genSquad(SquadDecl s) {
        int line = s.name().line();
        emit(line, "");
        emit(line, "public static class " + s.name().text() + " extends Squad {");
        indent++;
        emit(line, "public " + s.name().text() + "() {");
        indent++;
        emit(line, "super(\"" + s.name().text() + "\");");
        for (Token m : s.members()) {
            CharacterDecl c = characters.get(m.text());
            if (c == null) throw error(m, "herói desconhecido '" + m.text() + "'");
            if (c.villain()) throw error(m, "'" + m.text() + "' é um villain e não pode ser membro de squad");
            emit(m.line(), "allowMember(\"" + m.text() + "\");");
        }
        Set<String> names = new HashSet<>();
        for (PropertyBlock combo : s.combos()) {
            unique(names, combo.name(), "combo");
            Map<String, String> v = props(combo, "combo", COMBO_PROPS);
            emit(combo.name().line(), "defineCombo(\"" + combo.name().text() + "\", " + v.getOrDefault("cost", "0")
                    + ", " + v.getOrDefault("damage", "0") + ", " + v.getOrDefault("requires", "1") + ");");
        }
        indent--;
        emit(line, "}");
        indent--;
        emit(line, "}");
    }

    private void genBattle(Block battle) {
        int line = battle.open().line();
        emit(line, "");
        emit(line, "public static void main(String[] args) {");
        indent++;
        emit(line, "try {");
        indent++;
        genStmts(battle.stmts());
        indent--;
        emit(line, "} catch (HeroError e) {");
        emit(line, "    Arena.fail(e);");
        emit(line, "}");
        indent--;
        emit(line, "}");
    }

    // ---------------------------------------------------------------- comandos

    private void genStmts(List<Stmt> stmts) {
        for (Stmt s : stmts) genStmt(s);
    }

    private void genStmt(Stmt s) {
        if (s instanceof Block b) {
            emit(b.open().line(), "{");
            indent++;
            genStmts(b.stmts());
            indent--;
            emit(b.open().line(), "}");
        } else if (s instanceof Raw r) {
            int line = r.tokens().isEmpty() ? r.body().open().line() : r.tokens().get(0).line();
            if (r.body() == null) {
                emit(line, java(r.tokens()));
            } else {
                emit(line, (r.tokens().isEmpty() ? "" : java(r.tokens()) + " ") + "{");
                indent++;
                genStmts(r.body().stmts());
                indent--;
                emit(line, "}");
            }
        } else if (s instanceof Unleash u) {
            String target = u.target().isEmpty() ? "null" : java(u.target());
            emit(u.kw().line(), subject(u.kw(), u.subject()) + ".unleash(\"" + u.power().text() + "\", " + target + ");");
        } else if (s instanceof Transform t) {
            emit(t.kw().line(), subject(t.kw(), t.subject()) + ".transform(\"" + t.form().text() + "\");");
        } else if (s instanceof Revert r) {
            emit(r.kw().line(), subject(r.kw(), r.subject()) + ".revert();");
        } else if (s instanceof Evolve e) {
            emit(e.kw().line(), subject(e.kw(), e.subject()) + ".evolve(\"" + e.power().text() + "\", "
                    + java(e.amount()) + ");");
        } else if (s instanceof Summon sm) {
            String type = sm.type().text();
            if (!characters.containsKey(type) && !squads.containsKey(type)) {
                throw error(sm.type(), "tipo desconhecido '" + type + "' (declare-o com hero, villain ou squad)");
            }
            summoned.put(sm.var().text(), type);
            emit(sm.kw().line(), type + " " + sm.var().text() + " = new " + type + "();");
        } else if (s instanceof Recruit rc) {
            String heroType = summoned.get(rc.hero().text());
            if (heroType != null && characters.containsKey(heroType) && characters.get(heroType).villain()) {
                throw error(rc.hero(), "'" + rc.hero().text() + "' é um " + heroType
                        + " (villain) e não pode entrar em squads");
            }
            String squadType = summoned.get(rc.squad().text());
            if (squadType != null && !squads.containsKey(squadType)) {
                throw error(rc.squad(), "'" + rc.squad().text() + "' não é um squad");
            }
            emit(rc.kw().line(), rc.squad().text() + ".recruit(" + rc.hero().text() + ");");
        } else if (s instanceof Assemble a) {
            String target = a.target().isEmpty() ? "null" : java(a.target());
            emit(a.kw().line(), a.squad().text() + ".assemble(\"" + a.combo().text() + "\", " + target + ");");
        } else if (s instanceof Announce an) {
            emit(an.kw().line(), "Arena.announce(" + java(an.expr()) + ");");
        }
    }

    private String subject(Token kw, Token subject) {
        if (subject != null) return subject.text();
        if (!inMission) {
            throw error(kw, "'" + kw.text() + "' sem personagem só funciona dentro de uma mission; "
                    + "no battle use personagem.Nome (ex.: " + kw.text() + " tony.Nome)");
        }
        return "this";
    }

    /**
     * Converte tokens de volta para texto Java aplicando as reescritas da HeroScript:
     * leitura de {@code secret} vira chamada protegida e {@code shield(Gadget)} recebe o nome como string.
     */
    private String java(List<Token> toks) {
        StringBuilder sb = new StringBuilder();
        Token prev = null;
        for (int i = 0; i < toks.size(); i++) {
            Token t = toks.get(i);
            String text = t.text();
            if (t.isIdent()) {
                boolean afterDot = prev != null && prev.is(".");
                if ((afterDot && allSecrets.contains(text)) || (!afterDot && currentSecrets.contains(text))) {
                    text = "__reveal_" + text + "()";
                } else if (text.equals("shield") && i + 3 < toks.size() && toks.get(i + 1).is("(")
                        && toks.get(i + 2).isIdent() && toks.get(i + 3).is(")")) {
                    text = "shield(\"" + toks.get(i + 2).text() + "\")";
                    i += 3;
                }
            }
            if (prev != null && needsSpace(prev, t)) sb.append(' ');
            sb.append(text);
            prev = toks.get(i);
        }
        return sb.toString();
    }

    private static boolean needsSpace(Token prev, Token cur) {
        String p = prev.text(), c = cur.text();
        if (prev.type() == TokenType.SYMBOL && (p.equals(".") || p.equals("(") || p.equals("[") || p.equals("@")
                || p.equals("!") || p.equals("~") || p.equals("::"))) {
            return false;
        }
        if (cur.type() == TokenType.SYMBOL) {
            if (c.equals(".") || c.equals(",") || c.equals(";") || c.equals(")") || c.equals("]") || c.equals("::")) {
                return false;
            }
            if (c.equals("(") || c.equals("[")) {
                boolean keyword = Set.of("if", "for", "while", "switch", "catch", "synchronized", "return", "try")
                        .contains(p);
                return keyword || !(prev.isIdent() || p.equals(")") || p.equals("]") || p.equals(">"));
            }
            if (c.equals("++") || c.equals("--")) return !(prev.isIdent() || p.equals(")") || p.equals("]"));
        }
        return true;
    }

    // ---------------------------------------------------------------- saída

    private void emit(int srcLine, String text) {
        out.append(text.isEmpty() ? "" : "    ".repeat(indent) + text).append('\n');
        lineMap.add(srcLine);
    }

    private CompileError error(Token at, String message) {
        return new CompileError(file, at, message);
    }
}
