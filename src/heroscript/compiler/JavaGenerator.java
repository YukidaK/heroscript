package heroscript.compiler;

import heroscript.compiler.Ast.*;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private final Map<String, BatcaveDecl> batcaves = new LinkedHashMap<>();
    private boolean inBatcave;
    private final Set<String> allSecrets = new HashSet<>();
    private final Deque<Map<String, Boolean>> locals = new ArrayDeque<>();
    private final Deque<Map<String, String>> localTypes = new ArrayDeque<>();
    private final Deque<Map<String, Token>> localOrigins = new ArrayDeque<>();
    private final MethodInspector methodInspector;
    private static final String BATPLAN="((heroscript.runtime.Batplan)null)";
    private final boolean usesBatplan;
    private final java.util.Deque<String> hulkLabels=new java.util.ArrayDeque<>();
    private int hulkSerial;
    private final Set<Token> declarationTokens=new HashSet<>();
    private Set<String> instanceFields=Set.of();
    private boolean instanceContext=true;
    private String currentOwner = "";
    private int flashSerial;
    private final Map<String,Token> flashNames = new HashMap<>();
    private List<SecretDecl> secretDeclarations = List.of();

    // Contexto do trecho sendo gerado.
    private Set<String> currentSecrets = Set.of();
    private boolean inMission = false;
    private final Map<String, String> summoned = new HashMap<>();

    public JavaGenerator(String file, Program program) {
        this.file = file;
        this.program = program;
        this.className = classNameFor(file);
        this.methodInspector = new MethodInspector(file, program);
        this.usesBatplan=hasBatplan(program.battle())
                ||program.characters().stream().anyMatch(c->c.missions().stream().anyMatch(m->hasBatplan(m.body()))||c.methods().stream().anyMatch(m->hasBatplan(m.body())))
                ||program.batcaves().stream().anyMatch(c->c.methods().stream().anyMatch(m->hasBatplan(m.body())));
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
        for (BatcaveDecl cave : program.batcaves()) genBatcave(cave);
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
        for (BatcaveDecl cave : program.batcaves()) {
            String n = cave.name().text();
            if (characters.containsKey(n) || squads.containsKey(n) || batcaves.containsKey(n) || className.equals(n)) {
                throw error(cave.name(), "'" + n + "' já foi declarado");
            }
            if (Set.of("Supervision", "Arena", "Hero", "Villain", "Fighter", "Squad", "HeroError", "String", "System").contains(n)) {
                throw error(cave.name(), "nome reservado para batcave: '" + n + "'");
            }
            batcaves.put(n, cave);
        }
    }

    private void genBatcave(BatcaveDecl cave) {
        currentOwner = cave.name().text();
        instanceFields=fieldNames(cave.fields());
        inBatcave = true;
        pushScope();
        // A top-level class has a real Java private boundary, unlike sibling nested classes.
        emit(cave.name().line(), "");
        emit(cave.name().line(), "final class " + cave.name().text() + " implements heroscript.runtime.BatcaveAccess.Protected {");
        indent++;
        emit(cave.name().line(),"{ heroscript.runtime.BatcaveAccess.check(); }");
        for (FieldDecl field : cave.fields()) {
            registerLocals(field.tokens());
            if (field.secret()) {
                int eq = 0;
                while (eq < field.tokens().size() && !field.tokens().get(eq).is("=") && !field.tokens().get(eq).is(";")) eq++;
                if (eq > 0) locals.peek().put(field.tokens().get(eq - 1).text(), true);
            }
        }
        for (FieldDecl field : cave.fields()) {
            List<Token> tokens = field.tokens();
            int eq = -1;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).is("=")) { eq = i; break; }
            }
            List<Token> declaration = tokens.subList(0, eq < 0 ? tokens.size() - 1 : eq);
            for (Token token : declaration) {
                if (token.is(",") || token.is("static") || token.is("protected")) {
                    throw error(token, "campos de batcave nesta etapa são de instância, public/private, uma declaração por instrução");
                }
            }
            if (declaration.size() < 2 || !declaration.get(declaration.size() - 1).isIdent()) {
                throw error(tokens.get(0), "use: Tipo campo = valor; dentro de batcave");
            }
            String visibility = declaration.stream().anyMatch(t -> t.is("public") || t.is("private")) ? "" : "private ";
            int line = tokens.get(0).line();
            emit(line, visibility + java(declaration,false) + ";");
            if (eq >= 0) {
                // Initializers are part of the protected execution too, including callbacks.
                emit(line, "{");
                indent++;
                emit(line, "Supervision.enterBatcave();");
                emit(line, "try {");
                indent++;
                emit(line, "this." + declaration.get(declaration.size() - 1).text() + " = " + java(tokens.subList(eq + 1, tokens.size() - 1)) + ";");
                indent--;
                emit(line, "} finally { Supervision.exitBatcave(); }");
                indent--;
                emit(line, "}");
            }
        }
        for (CaveMethod method : cave.methods()) genMethod(method);
        indent--;
        emit(cave.name().line(), "}");
        popScope();
        inBatcave = false;
        currentOwner = "";
        instanceFields=Set.of();
    }

    private void genMethod(CaveMethod method) {
        boolean previousInstance=instanceContext;
        instanceContext=method.header().stream().noneMatch(t->t.is("static"));
        pushScope();
        registerLocals(method.params());
        if (method.header().stream().anyMatch(t -> t.is("protected") || t.is("abstract") || t.is("native"))) {
            throw error(method.name(), "métodos devem ter corpo e visibilidade public ou private");
        }
        boolean explicit = method.header().stream().anyMatch(t -> t.is("public") || t.is("private"));
        emit(method.name().line(), (explicit ? "" : "private ")
                + java(method.header().subList(0, method.header().size() - 1),false) + " " + method.name().text()
                + "(" + java(method.params(),false) + ") {");
        indent++;
        if(inBatcave)emit(method.name().line(),"heroscript.runtime.BatcaveAccess.check();");
        if(usesBatplan&&instanceContext)emit(method.name().line(),BATPLAN+".active(this);");
        boolean suppress = inBatcave || method.secret();
        if (suppress) {
            emit(method.name().line(), "Supervision.enterSecret();");
            emit(method.name().line(), "try {");
            indent++;
        }
        genStmts(method.body().stmts());
        if (suppress) {
            indent--;
            emit(method.name().line(), "} finally { Supervision.exitSecret(); }");
        }
        indent--;
        emit(method.name().line(), "}");
        popScope();
        instanceContext=previousInstance;
    }

    private void genCharacter(CharacterDecl c) {
        String n = c.name().text();
        currentOwner = n;
        Set<String> characterFields=new HashSet<>(fieldNames(c.fields()));
        characterFields.addAll(CHARACTER_FIELDS);
        c.secrets().forEach(s->characterFields.add(s.name().text()));
        instanceFields=characterFields;
        int line = c.name().line();
        currentSecrets = new HashSet<>();
        secretDeclarations = c.secrets();
        c.secrets().forEach(s -> currentSecrets.add(s.name().text()));
        pushScope();
        for (FieldDecl field : c.fields()) registerLocals(field.tokens());

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
            emit(s.name().line(), "private " + java(s.type(),false) + " " + s.name().text() + init + ";");
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
            emit(s.name().line(), "public " + java(s.type(),false) + " __reveal_" + sn + "() {");
            indent++;
            if(usesBatplan)emit(s.name().line(),BATPLAN+".active(this);");
            emit(s.name().line(), "checkSecret(\"" + sn + "\"" + allowed + ");");
            emit(s.name().line(), "return " + sn + ";");
            indent--;
            emit(s.name().line(), "}");
        }

        for (MissionDecl m : c.missions()) {
            int ml = m.name().line();
            pushScope();
            registerLocals(m.params());
            emit(ml, "");
            emit(ml, "public void " + m.name().text() + "(" + java(m.params(),false) + ") {");
            indent++;
            if(usesBatplan)emit(ml,BATPLAN+".active(this);");
            emit(ml, "Arena.enterMission(this, \"" + m.name().text() + "\");");
            emit(ml, "try {");
            indent++;
            if (m.secret()) {
                emit(ml, "Supervision.enterSecret();");
                emit(ml, "try {");
                indent++;
            }
            inMission = true;
            genStmts(m.body().stmts());
            popScope();
            inMission = false;
            if (m.secret()) {
                indent--;
                emit(ml, "} finally { Supervision.exitSecret(); }");
            }
            indent--;
            emit(ml, "} finally {");
            emit(ml, "    Arena.exitMission();");
            emit(ml, "}");
            indent--;
            emit(ml, "}");
        }

        for (CaveMethod method : c.methods()) genMethod(method);
        indent--;
        emit(line, "}");
        currentSecrets = Set.of();
        secretDeclarations = List.of();
        popScope();
        currentOwner = "";
        instanceFields=Set.of();
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
        pushScope();
        for (Stmt s : stmts) genStmt(s);
        popScope();
    }

    private void pushScope() {
        locals.push(new HashMap<>());
        localTypes.push(new HashMap<>());
        localOrigins.push(new HashMap<>());
    }

    private void popScope() {
        locals.pop();
        localTypes.pop();
        localOrigins.pop();
    }

    private Boolean localProtection(String name) {
        for (Map<String, Boolean> scope : locals) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }

    // Track declaration names for thematic visibility; javac validates actual types and scopes.
    private void registerLocals(List<Token> tokens) {
        if (locals.isEmpty()) return;
        for(JavaBindings.Binding binding:JavaBindings.collect(tokens)) {
            locals.peek().put(binding.name().text(),false);
            localTypes.peek().put(binding.name().text(),binding.type());
            localOrigins.peek().put(binding.name().text(),binding.name());
            declarationTokens.add(binding.name());declarationTokens.addAll(binding.typeTokens());
        }
        for (int i = 1; i < tokens.size(); i++) {
            Token name = tokens.get(i), previous = tokens.get(i - 1);
            if (name.isIdent() && previous.isIdent()
                    && !Set.of("return", "throw", "new", "instanceof", "case", "break", "continue").contains(previous.text())
                    && (i + 1 == tokens.size() || Set.of("=", ";", ",", ")", ":").contains(tokens.get(i + 1).text()))) {
                locals.peek().put(name.text(), false);
                localTypes.peek().putIfAbsent(name.text(), previous.text());
                localOrigins.peek().put(name.text(), name);
                declarationTokens.add(name);
            }
        }
    }

    private void genStmt(Stmt s) {
        if(s instanceof HulkSmash hulk) {
            String label="__heroHulk"+(hulkSerial++);
            while(program.toString().contains(label))label+="x";
            emit(hulk.kw().line(),label+": while (Boolean.TRUE.booleanValue() && ("+java(hulk.condition())+")) {");
            indent++;
            hulkLabels.push(label);
            genStmts(hulk.body().stmts());
            hulkLabels.pop();
            indent--;
            emit(hulk.kw().line(),"}");
        } else if(s instanceof BlackWidow widow) {
            emit(widow.kw().line(),"{"); indent++;
            genStmts(widow.body().stmts());
            indent--; emit(widow.kw().line(),"}");
        } else if(s instanceof HulkBuster buster) {
            emit(buster.kw().line(),"if (Boolean.TRUE.booleanValue()) break "+hulkLabels.peek()+";");
        } else if(s instanceof Ast.Batplan plan) {
            String name=plan.target().text();
            String type=visibleType(name);
            if(name.equals("this")&&!currentOwner.isEmpty()&&instanceContext)type=currentOwner;
            if(type==null&&currentSecrets.contains(name))type=String.join("",secretDeclarations.stream().filter(d->d.name().is(name)).findFirst().orElseThrow().type().stream().map(Token::text).toList());
            if(type==null||type.equals("var")||JavaBindings.primitive(type))throw error(plan.target(),"batplan exige referência de objeto com tipo explícito, não variável primitiva ou método");
            if(instanceContext&&ownInstanceField(name))emit(plan.kw().line(),BATPLAN+".active(this);");
            emit(plan.kw().line(),BATPLAN+".deactivate("+java(List.of(plan.target()),false)+");");
        } else if (s instanceof Ast.DeadpoolAction action) {
            emit(action.kw().line(),"if (heroscript.runtime.DeadpoolAction.choose("+action.chance()+")) {");
            indent++;
            genStmts(action.body().stmts());
            indent--;
            emit(action.kw().line(),"}");
        } else if (s instanceof FlashPoint point) {
            genFlashPoint(point);
        } else if (s instanceof FlashTravel travel) {
            throw error(travel.kw(),"flashtravel só pode ser gerado dentro de flashPoint");
        } else if (s instanceof Block b) {
            emit(b.open().line(), "{");
            indent++;
            genStmts(b.stmts());
            indent--;
            emit(b.open().line(), "}");
        } else if (s instanceof Raw r) {
            if(usesBatplan&&r.body()!=null&&!r.tokens().isEmpty()
                    &&!Set.of("if","else","for","while","do","switch","try","catch","finally","synchronized").contains(r.tokens().get(0).text())
                    &&r.tokens().stream().anyMatch(t->t.is("new")||t.is("class")||t.is("interface")||t.is("record"))) {
                throw error(r.tokens().get(0),"classes Java locais/anônimas ainda não são suportadas em programas com batplan");
            }
            int line = r.tokens().isEmpty() ? r.body().open().line() : r.tokens().get(0).line();
            if (r.body() == null) {
                registerLocals(r.tokens());
                if(usesBatplan)mutationGuards(r.tokens());
                emit(line, java(r.tokens(),true,true));
            } else {
                pushScope();
                registerLocals(r.tokens());
                emit(line, (r.tokens().isEmpty() ? "" : java(r.tokens()) + " ") + "{");
                indent++;
                genStmts(r.body().stmts());
                indent--;
                emit(line, "}");
                popScope();
            }
        } else if (s instanceof SecretLocal local) {
            SecretDecl declaration = local.declaration();
            locals.peek().put(declaration.name().text(), true);
            String type = java(declaration.type(),false);
            localTypes.peek().put(declaration.name().text(), type.replaceFirst("^final\\s+", ""));
            localOrigins.peek().put(declaration.name().text(), declaration.name());
            emit(declaration.name().line(), type + " " + declaration.name().text() + " = " + java(declaration.init()) + ";");
        } else if (s instanceof Kryptonite k) {
            locals.peek().put(k.name().text(), true);
            localTypes.peek().put(k.name().text(), k.type().text());
            localOrigins.peek().put(k.name().text(), k.name());
            String init = java(k.init());
            emit(k.kw().line(), (k.immutable() ? "final " : "") + k.type().text() + " " + k.name().text() + " = " + init + ";");
        } else if (s instanceof ValueObservation v) {
            String name = v.name().text();
            Boolean protection = localProtection(name);
            for (Map<String, String> types : localTypes) {
                if (!types.containsKey(name)) continue;
                if (!Set.of("byte", "short", "int", "long", "float", "double", "boolean", "char", "String").contains(types.get(name))) {
                    throw error(v.name(), "supervision.getValue aceita apenas tipos primitivos ou String com tipo explícito");
                }
                break;
            }
            boolean secret = protection == null && currentSecrets.contains(name);
            if (protection == null && !secret) {
                throw error(v.name(), "variável não resolvida para supervision.getValue: '" + name + "' (use variável local ou parâmetro com tipo explícito)");
            }
            Token declarationOrigin = v.name();
            for (Map<String, Token> origins : localOrigins) {
                if (origins.containsKey(name)) { declarationOrigin = origins.get(name); break; }
            }
            if (secret) declarationOrigin = secretDeclarations.stream().filter(d -> d.name().is(name)).findFirst().orElseThrow().name();
            String displayName=flashNames.containsKey(name) ? flashNames.get(name).text() : name;
            String location = quoted(file + ":" + declarationOrigin.line() + " " + displayName + " = ");
            if (secret) {
                SecretDecl declaration = secretDeclarations.stream().filter(d -> d.name().is(name)).findFirst().orElseThrow();
                if (!Set.of("byte", "short", "int", "long", "float", "double", "boolean", "char", "String").contains(java(declaration.type()))) {
                    throw error(v.name(), "supervision.getValue aceita apenas tipos primitivos ou String");
                }
                String allowed = declaration.allowed().stream().map(t -> ", \"" + t.text() + "\"").reduce("", String::concat);
                emit(v.kw().line(), "checkSecret(\"" + name + "\"" + allowed + ");");
            }
            if (secret || Boolean.TRUE.equals(protection)) {
                emit(v.kw().line(), "if (Supervision.enabled()) Arena.log(\"supervision\", " + location + " + \"[protegido]\");");
            } else {
                emit(v.kw().line(), "if (Supervision.enabled()) Supervision.getValue(" + location + ", " + java(List.of(v.name())) + ");");
            }
        } else if (s instanceof MethodObservation observation) {
            Map<String, String> receivers = new HashMap<>();
            for (Map<String, String> scope : localTypes) scope.forEach(receivers::putIfAbsent);
            String report = methodInspector.describe(observation.query(), currentOwner, receivers);
            emit(observation.kw().line(), "if (Supervision.enabled()) Supervision.getMethod(" + quoted(report) + ");");
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
            locals.peek().put(sm.var().text(), false);
            localTypes.peek().put(sm.var().text(), sm.type().text());
            String type = sm.type().text();
            if (!characters.containsKey(type) && !squads.containsKey(type)) {
                throw error(sm.type(), "tipo desconhecido '" + type + "' (declare-o com hero, villain ou squad)");
            }
            summoned.put(sm.var().text(), type);
            String generatedType = inBatcave ? className + "." + type : type;
            emit(sm.kw().line(), generatedType + " " + sm.var().text() + " = new " + generatedType + "();");
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

    private void genFlashPoint(FlashPoint point) {
        Set<String> occupied=new HashSet<>();
        for(Map<String,Boolean> scope:locals)occupied.addAll(scope.keySet());
        Set<String> forbidden=new HashSet<>();
        int index=0;
        for(Map<String,Boolean> scope:locals) {
            if(currentOwner.isEmpty() || index<locals.size()-1)forbidden.addAll(scope.keySet());
            index++;
        }
        FlashPlan plan=new FlashPlan(file,point,++flashSerial,occupied,forbidden);
        String pc=plan.prefix+"pc", loop=plan.prefix+"dispatch";
        int line=point.kw().line();
        emit(line,"{"); indent++; pushScope();
        for(FlashPlan.Slot slot:plan.slots) {
            String name=slot.generated().text();
            String type=java(slot.type(),false);
            String initial=type.contains("[") ? "null" : switch(type) {
                case "boolean" -> "false"; case "char" -> "'\\0'";
                case "byte","short","int","long","float","double" -> "0"; default -> "null";
            };
            emit(slot.original().line(),type+" "+name+" = "+initial+";");
            emit(slot.original().line(),"boolean "+name+"_initialized = false;");
            locals.peek().put(name,slot.protectedValue());
            localTypes.peek().put(name,String.join("",slot.type().stream().map(Token::text).toList()));
            if(characters.containsKey(type)||squads.containsKey(type))summoned.put(name,type);
            localOrigins.peek().put(name,slot.original()); flashNames.put(name,slot.original());
        }
        emit(line,"int "+pc+" = "+plan.entry+";");
        emit(line,loop+": while (true) {"); indent++;
        emit(line,"switch ("+pc+") {"); indent++;
        for(FlashPlan.Node node:plan.nodes) {
            emit(node.at.line(),"case "+node.id+" -> {"); indent++;
            for(FlashPlan.Slot read:node.reads) emit(node.at.line(),"if (!"+read.generated().text()+"_initialized) throw new HeroError(\"FlashUninitialized\", \"variable not initialized before destination at line "+node.at.line()+"\");");
            if(node.action!=null) genStmt(FlashPlan.rewrite(node.action,node.scope));
            if(node.initialized!=null) emit(node.at.line(),node.initialized.generated().text()+"_initialized = true;");
            for(FlashPlan.Slot written:node.writes)emit(node.at.line(),written.generated().text()+"_initialized = true;");
            if(node.condition!=null) {
                String condition=node.deadpoolChance==null ? java(FlashPlan.rewrite(node.condition,node.scope))
                        : "heroscript.runtime.DeadpoolAction.choose("+node.deadpoolChance+")";
                emit(node.at.line(),pc+" = ("+condition+") ? "+node.next+" : "+node.other+";");
                emit(node.at.line(),"continue "+loop+";");
            } else if(node.terminal) {
                if(node.action==null)emit(node.at.line(),plan.completes ? "break "+loop+";" : "throw new IllegalStateException(\"Unreachable flashPoint end\");");
            } else {
                emit(node.at.line(),pc+" = "+node.next+";");
                emit(node.at.line(),"continue "+loop+";");
            }
            indent--;emit(node.at.line(),"}");
        }
        emit(line,"default -> throw new IllegalStateException(\"Invalid flashPoint state\");");
        indent--;emit(line,"}"); indent--;emit(line,"}");
        for(FlashPlan.Slot slot:plan.slots)flashNames.remove(slot.generated().text());
        popScope();indent--;emit(line,"}");
    }

    /**
     * Converte tokens de volta para texto Java aplicando as reescritas da HeroScript:
     * leitura de {@code secret} vira chamada protegida e {@code shield(Gadget)} recebe o nome como string.
     */
    private String java(List<Token> toks) { return java(toks,true); }

    private String java(List<Token> toks,boolean guardObjects) {return java(toks,guardObjects,false);}

    private String java(List<Token> toks,boolean guardObjects,boolean mutationsChecked) {
        if(usesBatplan&&guardObjects)for(Token token:toks)if(token.is("->")||token.is("::"))throw error(token,"lambdas e referências de método Java ainda não são suportadas com batplan; use método tipado");
        StringBuilder sb = new StringBuilder();
        Map<String,String> visibleTypes=new HashMap<>();
        for(Map<String,String> scope:localTypes)scope.forEach(visibleTypes::putIfAbsent);
        for(SecretDecl secret:secretDeclarations)visibleTypes.putIfAbsent(secret.name().text(),String.join("",secret.type().stream().map(Token::text).toList()));
        if(instanceContext&&!currentOwner.isEmpty())visibleTypes.putIfAbsent("this",currentOwner);
        if(usesBatplan&&guardObjects&&!mutationsChecked)for(int i=0;i<toks.size();i++) {
            Token token=toks.get(i);
            String after=i+1<toks.size()?toks.get(i+1).text():"";
            String before=i>0?toks.get(i-1).text():"";
            if(Set.of("+=","-=","*=","/=","%=","++","--").contains(after)||before.equals("++")||before.equals("--")) {
                String type=visibleTypes.get(token.text());
                if(token.is("]")||before.equals(".")||(type!=null&&!JavaBindings.primitive(type)))throw error(token,"alteração de referência em expressão/cabeçalho ainda não é suportada com batplan; use instrução separada");
            }
        }
        Set<ObjectGuards.Range> guards=(usesBatplan||!batcaves.isEmpty())&&guardObjects?ObjectGuards.collect(file,toks,visibleTypes,declarationTokens,instanceFields):Set.of();
        Token prev = null;
        for (int i = 0; i < toks.size(); i++) {
            Token t = toks.get(i);
            String text = t.text();
            if (t.isIdent()) {
                boolean afterDot = prev != null && prev.is(".");
                String receiver = afterDot && i >= 2 ? toks.get(i - 2).text() : "";
                boolean caveReceiver = (inBatcave && receiver.equals("this")) || batcaves.containsKey(receiver);
                String receiverType = receiver.equals("this") ? currentOwner : receiver;
                for (Map<String, String> types : localTypes) {
                    if (!types.containsKey(receiver)) continue;
                    receiverType = types.get(receiver);
                    caveReceiver = batcaves.containsKey(receiverType);
                    break;
                }
                if (receiver.equals(")")) {
                    // A cast gives a statically known receiver even when its surrounding expression is raw.
                    int nesting = 1;
                    int open = i - 3;
                    for (; open >= 0; open--) {
                        if (toks.get(open).is(")")) nesting++;
                        if (toks.get(open).is("(") && --nesting == 0) break;
                    }
                    if (open >= 0 && open + 3 < i - 2 && toks.get(open + 1).is("(")
                            && characters.containsKey(toks.get(open + 2).text()) && toks.get(open + 3).is(")")) {
                        receiverType = toks.get(open + 2).text();
                    }
                }
                boolean secretMember = characters.containsKey(receiverType)
                        ? characters.get(receiverType).secrets().stream().anyMatch(declaration -> declaration.name().is(t.text()))
                        : allSecrets.contains(text);
                boolean call = i + 1 < toks.size() && toks.get(i + 1).is("(");
                if (!call && ((afterDot && !caveReceiver && secretMember) || (!afterDot && currentSecrets.contains(text) && localProtection(text) == null))) {
                    text = "__reveal_" + text + "()";
                } else if (inBatcave && !afterDot && (characters.containsKey(text) || squads.containsKey(text))) {
                    text = className + "." + text;
                } else if (text.equals("shield") && i + 3 < toks.size() && toks.get(i + 1).is("(")
                        && toks.get(i + 2).isIdent() && toks.get(i + 3).is(")")) {
                    text = "shield(\"" + toks.get(i + 2).text() + "\")";
                    i += 3;
                }
                if(usesBatplan&&guardObjects&&instanceContext&&!afterDot&&!call&&!declarationTokens.contains(t)
                        &&ownInstanceField(t.text()))text="("+BATPLAN+".active(this))."+text;
            }
            if (prev != null && needsSpace(prev, t)) sb.append(' ');
            final int position=i;
            long openings=guards.stream().filter(range->range.start()==position).count();
            sb.append((BATPLAN+".active(").repeat((int)openings));
            sb.append(text);
            long closings=guards.stream().filter(range->range.end()==position).count();
            sb.append(")".repeat((int)closings));
            prev = toks.get(i);
        }
        return sb.toString();
    }

    private String visibleType(String name) {
        for(Map<String,String> scope:localTypes)if(scope.containsKey(name))return scope.get(name);
        return null;
    }
    private void mutationGuards(List<Token> tokens) {
        Set<String> guarded=new HashSet<>();
        for(int i=0;i<tokens.size();i++) {
            Token token=tokens.get(i);
            if(declarationTokens.contains(token))continue;
            String type=visibleType(token.text());
            String after=i+1<tokens.size()?tokens.get(i+1).text():"";
            String before=i>0?tokens.get(i-1).text():"";
            boolean mutation=Set.of("+=","-=","*=","/=","%=","++","--").contains(after)||before.equals("++")||before.equals("--");
            if(!mutation)continue;
            if(token.is("]"))throw error(token,"alteração composta de elemento de array ainda não é suportada com batplan");
            if((before.equals("++")||before.equals("--"))&&(after.equals(".")||after.equals("[")))throw error(token,"alteração de membro com prefixo ainda não é suportada com batplan");
            if(before.equals(".")) {
                if(i<2||!tokens.get(i-2).isIdent()||(i>2&&tokens.get(i-3).is(".")))throw error(token,"alteração composta com receptor complexo ainda não é suportada com batplan");
                emit(token.line(),BATPLAN+".active("+java(tokens.subList(i-2,i+1))+");");
                continue;
            }
            if(type!=null&&!JavaBindings.primitive(type)&&guarded.add(token.text())
                    &&mutation) {
                emit(token.line(),BATPLAN+".active("+java(List.of(token),false)+");");
            }
        }
    }
    private boolean ownInstanceField(String name) {
        if(currentOwner.isEmpty()||!instanceFields.contains(name))return false;
        int index=0;
        for(Map<String,Boolean> scope:locals) {
            if(scope.containsKey(name))return index==locals.size()-1;
            index++;
        }
        return true;
    }
    private static Set<String> fieldNames(List<FieldDecl> fields) {
        Set<String> names=new HashSet<>();
        for(FieldDecl field:fields)if(field.tokens().stream().noneMatch(t->t.is("static")))
            for(JavaBindings.Binding binding:JavaBindings.collect(field.tokens()))names.add(binding.name().text());
        return names;
    }
    private static boolean hasBatplan(Stmt statement) {
        if(statement instanceof HulkSmash hulk)return hasBatplan(hulk.body());
        if(statement instanceof BlackWidow widow)return hasBatplan(widow.body());
        if(statement instanceof Ast.Batplan)return true;
        if(statement instanceof Block block)return block.stmts().stream().anyMatch(JavaGenerator::hasBatplan);
        if(statement instanceof Raw raw)return raw.body()!=null&&hasBatplan(raw.body());
        if(statement instanceof FlashPoint point)return hasBatplan(point.body());
        if(statement instanceof Ast.DeadpoolAction action)return hasBatplan(action.body());
        return false;
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

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    private void emit(int srcLine, String text) {
        out.append(text.isEmpty() ? "" : "    ".repeat(indent) + text).append('\n');
        lineMap.add(srcLine);
    }

    private CompileError error(Token at, String message) {
        return new CompileError(file, at, message);
    }
}
