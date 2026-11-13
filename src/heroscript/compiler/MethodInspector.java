package heroscript.compiler;

import heroscript.compiler.Ast.*;
import java.util.*;

/** Source structure only: never invokes the selected method or expands its callees. */
final class MethodInspector {
    private record Binding(String type, boolean protectedValue) {}
    private record Method(String owner, Token name, List<Token> header, List<Token> params, Block body) {}
    private static final String UNAVAILABLE = "[supervision.getMethod] method unavailable";
    private final String file;
    private final List<Method> methods = new ArrayList<>();
    private final Map<String, Map<String, Binding>> fields = new HashMap<>();
    private final Map<String, Set<String>> secretMethods = new HashMap<>();
    private final Map<String, Set<String>> knownMethods = new HashMap<>();
    private final Set<String> caves = new HashSet<>();
    private final Set<String> protectedNames = new HashSet<>();

    MethodInspector(String file, Program program) {
        this.file = file;
        for (CharacterDecl character : program.characters()) {
            String owner = character.name().text();
            addOwner(owner, character.fields(), character.methods());
            for (SecretDecl secret : character.secrets()) {
                fields.get(owner).put(secret.name().text(), new Binding(text(secret.type()), true));
                protectedNames.add(secret.name().text());
            }
            for (MissionDecl mission : character.missions()) {
                knownMethods.get(owner).add(mission.name().text());
                if (mission.secret()) {
                    secretMethods.get(owner).add(mission.name().text());
                    protectedNames.add(mission.name().text());
                } else {
                    methods.add(new Method(owner, mission.name(), List.of(), mission.params(), mission.body()));
                }
            }
        }
        for (BatcaveDecl cave : program.batcaves()) {
            caves.add(cave.name().text());
            addOwner(cave.name().text(), cave.fields(), cave.methods());
        }
    }

    private void addOwner(String owner, List<FieldDecl> declarations, List<CaveMethod> declaredMethods) {
        Map<String, Binding> bindings = new HashMap<>();
        for (FieldDecl declaration : declarations) {
            Map<String, Binding> declared = new HashMap<>();
            register(declaration.tokens(), declared);
            if (declaration.secret()) {
                for (String name : List.copyOf(declared.keySet())) {
                    declared.put(name, new Binding(declared.get(name).type(), true));
                    protectedNames.add(name);
                }
            }
            bindings.putAll(declared);
        }
        fields.put(owner, bindings);
        secretMethods.put(owner, new HashSet<>());
        knownMethods.put(owner, new HashSet<>());
        for (CaveMethod method : declaredMethods) {
            knownMethods.get(owner).add(method.name().text());
            if (method.secret()) {
                secretMethods.get(owner).add(method.name().text());
                protectedNames.add(method.name().text());
            } else {
                methods.add(new Method(owner, method.name(), method.header(), method.params(), method.body()));
            }
        }
    }

    String describe(String selection, String currentOwner, Map<String, String> receivers) {
        // Authorization is lexical: a call in battle has no declaring class to inspect.
        if (currentOwner.isEmpty() || caves.contains(currentOwner)) return UNAVAILABLE;
        String query = selection.replaceAll("\\s+", "");
        int dot = query.indexOf('.');
        int open = query.indexOf('(');
        if (open >= 0 && dot > open) dot = -1;
        if (dot > 0) {
            String receiver = query.substring(0, dot);
            String owner = receiver.equals("this") ? currentOwner : receivers.getOrDefault(receiver, receiver);
            if (!owner.equals(currentOwner)) return UNAVAILABLE;
            query = owner + query.substring(dot);
        }
        String requested = query;
        List<Method> candidates = methods.stream().filter(method -> method.owner().equals(currentOwner))
                .filter(method -> matches(method, requested)).toList();
        if (candidates.isEmpty()) return UNAVAILABLE;
        if (candidates.size() != 1) return "[supervision.getMethod] ambiguous selection; specify parameter types";
        Method method = candidates.get(0);
        Renderer renderer = new Renderer(method);
        String signature = method.owner() + "." + method.name().text() + "(" + parameterTypes(method.params()) + ")";
        String header = method.header().isEmpty() ? "mission " + method.name().text() : text(method.header());
        renderer.out.append(header).append("(").append(text(method.params())).append(") {\n");
        renderer.block(method.body(), 1);
        renderer.out.append("}");
        String report = "[supervision.getMethod] " + signature + " | " + file + ":" + method.name().line() + "\n"
                + (renderer.partial ? "partial analysis: protected or unresolved operations omitted\n" : "") + renderer.out;
        if (report.length() > 24000) return "[supervision.getMethod] partial analysis: method exceeds report size limit";
        return report;
    }

    private static boolean matches(Method method, String query) {
        String name = method.name().text();
        String qualified = method.owner() + "." + name;
        String params = "(" + parameterTypes(method.params()) + ")";
        return query.equals(name) || query.equals(qualified) || query.equals(name + params) || query.equals(qualified + params);
    }

    private final class Renderer {
        final Method method;
        final StringBuilder out = new StringBuilder();
        final Deque<Map<String, Binding>> scopes = new ArrayDeque<>();
        boolean partial;
        Renderer(Method method) {
            this.method = method;
            scopes.push(new HashMap<>(fields.getOrDefault(method.owner(), Map.of())));
            scopes.push(new HashMap<>());
            register(method.params(), scopes.peek());
        }
        Binding binding(String name) {
            for (Map<String, Binding> scope : scopes) if (scope.containsKey(name)) return scope.get(name);
            return null;
        }
        boolean protectedTokens(List<Token> tokens) {
            for (int i = 0; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (!token.isIdent()) continue;
                String name = token.text();
                if (i > 0 && tokens.get(i - 1).is(".")) {
                    String receiver = i > 1 ? tokens.get(i - 2).text() : "";
                    Binding local = binding(receiver);
                    String owner = receiver.equals("this") ? method.owner() : local != null ? local.type() : receiver;
                    if (fields.containsKey(owner)) {
                        Binding member = fields.get(owner).get(name);
                        if ((member != null && member.protectedValue()) || secretMethods.get(owner).contains(name)) return true;
                    } else if (protectedNames.contains(name)) return true;
                } else {
                    Binding local = binding(name);
                    if (local != null) {
                        if (local.protectedValue()) return true;
                    } else if (secretMethods.get(method.owner()).contains(name)) return true;
                    else if (!knownMethods.get(method.owner()).contains(name) && protectedNames.contains(name)) return true;
                }
            }
            return false;
        }
        void line(int indent, String value) { out.append("    ".repeat(indent)).append(value).append('\n'); }
        void omitted(int indent) { partial = true; line(indent, "/* protected or unresolved operation */"); }
        void block(Block block, int indent) {
            scopes.push(new HashMap<>());
            for (Stmt statement : block.stmts()) statement(statement, indent);
            scopes.pop();
        }
        void statement(Stmt statement, int indent) {
            if (statement instanceof SecretLocal local) {
                scopes.peek().put(local.declaration().name().text(), new Binding(text(local.declaration().type()), true));
                omitted(indent);
            } else if (statement instanceof Kryptonite local) {
                scopes.peek().put(local.name().text(), new Binding(local.type().text(), true));
                omitted(indent);
            } else if (statement instanceof Raw raw) {
                scopes.push(new HashMap<>());
                register(raw.tokens(), scopes.peek());
                if (raw.body() == null) {
                    Map<String, Binding> declarations = scopes.pop();
                    scopes.peek().putAll(declarations);
                    if (protectedTokens(raw.tokens())) omitted(indent); else line(indent, text(raw.tokens()));
                } else {
                    if (protectedTokens(raw.tokens())) omitted(indent);
                    else {
                        line(indent, text(raw.tokens()) + " {");
                        block(raw.body(), indent + 1);
                        line(indent, "}");
                    }
                    scopes.pop();
                }
            } else if (statement instanceof Block nested) {
                line(indent, "{"); block(nested, indent + 1); line(indent, "}");
            } else if (statement instanceof Ast.DeadpoolAction action) {
                line(indent,"deadpoolAction(chance = "+action.chance()+") {");
                block(action.body(),indent+1);
                line(indent,"}");
            } else if(statement instanceof HulkSmash hulk) {
                if(protectedTokens(hulk.condition()))omitted(indent);
                else {
                    line(indent,"hulksmash ("+text(hulk.condition())+") {");
                    block(hulk.sourceBody(),indent+1); line(indent,"}");
                }
            } else if(statement instanceof BlackWidow widow) {
                line(indent,"blackwidow {"); block(widow.body(),indent+1); line(indent,"}");
            } else if(statement instanceof HulkBuster) {
                line(indent,"hulkbuster;");
            } else if (statement instanceof Announce announce) {
                if (protectedTokens(announce.expr())) omitted(indent); else line(indent, "announce " + text(announce.expr()) + ";");
            } else if (statement instanceof ValueObservation value) {
                if (protectedTokens(List.of(value.name()))) omitted(indent);
                else line(indent, "supervision.getValue(" + value.name().text() + ");");
            } else if (statement instanceof MethodObservation) {
                line(indent, "supervision.getMethod(/* selection omitted */);");
            } else if(statement instanceof Ast.Batplan plan) {
                if(protectedTokens(List.of(plan.target())))omitted(indent);
                else line(indent,"batplan("+plan.target().text()+");");
            } else {
                // Do not guess at thematic effects or dump AST record fields containing expressions.
                partial = true;
                line(indent, "/* thematic operation; detailed analysis unavailable */");
            }
        }
    }

    private static void register(List<Token> tokens, Map<String, Binding> bindings) {
        for (int i = 1; i < tokens.size(); i++) {
            Token name = tokens.get(i), type = tokens.get(i - 1);
            if (name.isIdent() && type.isIdent() && !Set.of("return", "throw", "new", "case", "instanceof", "break", "continue").contains(type.text())
                    && (i + 1 == tokens.size() || Set.of("=", ";", ",", ")", ":").contains(tokens.get(i + 1).text()))) {
                bindings.put(name.text(), new Binding(type.text(), false));
            }
        }
    }
    private static String parameterTypes(List<Token> params) {
        List<String> types = new ArrayList<>();
        int start = 0, depth = 0;
        for (int i = 0; i <= params.size(); i++) {
            if (i < params.size()) {
                String token = params.get(i).text();
                if (token.equals("<")) depth++;
                if (token.matches(">{1,3}")) depth -= token.length();
            }
            if (i == params.size() || (depth == 0 && params.get(i).is(","))) {
                if (i > start) {
                    List<Token> parameter = params.subList(start, i);
                    int name = parameter.size() - 1;
                    while (name >= 0 && (parameter.get(name).is("[") || parameter.get(name).is("]"))) name--;
                    StringBuilder type = new StringBuilder();
                    for (int j = 0; j < parameter.size(); j++) if (j != name && !parameter.get(j).is("final")) type.append(parameter.get(j).text());
                    types.add(type.toString());
                }
                start = i + 1;
            }
        }
        return String.join(",", types);
    }
    private static String text(List<Token> tokens) {
        return String.join(" ", tokens.stream().map(Token::text).toList());
    }
}
