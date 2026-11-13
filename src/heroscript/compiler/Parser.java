package heroscript.compiler;

import heroscript.compiler.Ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Análise sintática descendente recursiva. Cada método parseX corresponde a uma regra da gramática (ver PROMPT.md).
 */
public class Parser {

    /** Palavras que iniciam um comando HeroScript dentro de mission/battle. */
    private static final Set<String> STATEMENT_KEYWORDS =
            Set.of("unleash", "transform", "revert", "evolve", "summon", "recruit", "assemble", "announce", "kryptonite", "secret");

    private final List<Token> tokens;
    private final String file;
    private int pos = 0;
    private boolean inBatcave;
    private int flashDepth;
    private boolean rootFlash;
    private int battles;
    private int deadpoolDepth;
    private int hulkDepth;
    private final java.util.Deque<Boolean> hulkRegions=new java.util.ArrayDeque<>();

    public Parser(List<Token> tokens, String file) {
        this.tokens = tokens;
        this.file = file;
    }

    // program := declarations with at most one battle or root flashPoint entry.
    public Program parse() {
        List<CharacterDecl> characters = new ArrayList<>();
        List<SquadDecl> squads = new ArrayList<>();
        List<BatcaveDecl> batcaves = new ArrayList<>();
        Block battle = null;
        while (!check(TokenType.EOF)) {
            Token t = peek();
            if (t.is("hero") || t.is("villain")) {
                characters.add(parseCharacter());
            } else if (t.is("squad")) {
                squads.add(parseSquad());
            } else if (t.is("batcave")) {
                batcaves.add(parseBatcave());
            } else if (t.is("battle")) {
                if (battle != null) throw error(t, "só pode existir um bloco battle por arquivo");
                advance();
                battles++;
                battle = parseBlock();
            } else if (t.is("flashPoint")) {
                if (battle != null) throw error(t, "só pode existir uma entrada battle ou flashPoint por arquivo");
                rootFlash = true;
                FlashPoint point = parseFlashPoint();
                rootFlash = false;
                battle = new Block(point.body().open(), List.of(point));
            } else {
                throw error(t, "esperado 'hero', 'villain', 'squad', 'batcave', 'battle' ou 'flashPoint', encontrado '" + t.text() + "'");
            }
        }
        return new Program(characters, squads, batcaves, battle);
    }

    private BatcaveDecl parseBatcave() {
        advance();
        Token name = expectIdent("nome da batcave");
        expect("{");
        List<FieldDecl> fields = new ArrayList<>();
        List<CaveMethod> methods = new ArrayList<>();
        inBatcave = true;
        while (!check("}")) {
            Token start = peek();
            if (check(TokenType.EOF)) throw error(start, "'}' faltando para fechar batcave " + name.text());
            if (start.is("villain")) throw error(start, "villain não pode ser declarado dentro de batcave");
            if (Set.of("hero", "squad", "batcave", "class", "interface", "record", "kryptonite", "mission").contains(start.text())) {
                throw error(start, "dentro de batcave declare campos comuns e métodos; '" + start.text() + "' não é suportado aqui");
            }
            parseJavaMember(fields, methods);
        }
        expect("}");
        inBatcave = false;
        return new BatcaveDecl(name, fields, methods);
    }

    private void parseJavaMember(List<FieldDecl> fields, List<CaveMethod> methods) {
            boolean secret = match("secret");
            List<Token> header = new ArrayList<>();
            boolean initializer = false;
            while (true) {
                Token t = peek();
                if (t.is("secret") || t.is("kryptonite")) throw error(t, "modificadores repetidos ou combinação secret + kryptonite não permitida");
                if (t.is("(") && !initializer) {
                    if (header.size() < 2 || !header.get(header.size() - 1).isIdent()) {
                        throw error(t, "use um método com tipo de retorno; construtores explícitos não são suportados");
                    }
                    Token methodName = header.get(header.size() - 1);
                    if (header.subList(0, header.size() - 1).stream().allMatch(token -> Set.of("public", "private", "protected", "static", "final", "synchronized").contains(token.text()))) {
                        throw error(methodName, "construtores explícitos não são suportados em batcave");
                    }
                    advance();
                    List<Token> params = new ArrayList<>();
                    int depth = 1;
                    while (depth > 0) {
                        Token param = advance();
                        if (param.type() == TokenType.EOF || param.is("{")) throw error(param, "')' faltando nos parâmetros da batcave");
                        if (param.is("(")) depth++;
                        if (param.is(")")) depth--;
                        if (depth > 0) params.add(param);
                    }
                    rejectUnsupportedModifiers(params);
                    methods.add(new CaveMethod(methodName, header, params, parseBlock(), secret));
                    break;
                }
                if (t.is(";") || initializer) {
                    header.addAll(collectUntilSemicolon("campo da batcave"));
                    rejectUnsupportedModifiers(header);
                    if (header.stream().anyMatch(token -> token.is("allow"))) throw error(header.get(0), "allow só é permitido em campos secret de personagens");
                    fields.add(new FieldDecl(header, secret));
                    break;
                }
                if (t.type() == TokenType.EOF || t.is("}") || t.is("{")) throw error(t, "campo ou método incompleto em batcave");
                header.add(advance());
                initializer = t.is("=");
            }
    }

    private void rejectUnsupportedModifiers(List<Token> tokens) {
        for (Token token : tokens) {
            if (token.is("secret") || token.is("kryptonite")) throw error(token, "secret e kryptonite não podem ser combinados ou usados nesta posição");
        }
    }

    private boolean secretMethodAhead() {
        for (int i = 1; pos + i < tokens.size(); i++) {
            Token token = peekAt(i);
            if (token.is("(") || token.is("mission")) return true;
            if (token.is("=") || token.is(";") || token.is("}") || token.type() == TokenType.EOF) return false;
        }
        return false;
    }

    private boolean javaMethodAhead() {
        for (int i = 0; pos + i < tokens.size(); i++) {
            Token token = peekAt(i);
            if (token.is("@")) return false;
            if (token.is("(")) return true;
            if (token.is("=") || token.is(";") || token.is("}") || token.type() == TokenType.EOF) return false;
        }
        return false;
    }

    private Stmt parseSupervision() {
        Token kw = advance();
        expect(".");
        Token operation = expectIdent("getValue ou getMethod");
        expect("(");
        if (operation.is("getValue")) {
            Token name = expectIdent("variável para supervision.getValue");
            expect(")");
            expect(";");
            return new ValueObservation(kw, name);
        }
        if (!operation.is("getMethod")) throw error(operation, "supervision aceita getValue ou getMethod");
        StringBuilder query = new StringBuilder();
        if (check(TokenType.STRING)) {
            String literal = advance().text();
            String value = literal.substring(1, literal.length() - 1);
            if (value.contains("\\")) throw error(operation, "use uma seleção de método sem escapes");
            query.append(value);
        } else {
            int depth = 0;
            while (!(check(")") && depth == 0)) {
                Token token = peek();
                if (token.type() == TokenType.EOF || token.is(";") || token.is("{") || token.is("}")) {
                    throw error(operation, "seleção incompleta em supervision.getMethod");
                }
                if (!token.isIdent() && !Set.of(".", ",", "(", ")", "[", "]", "<", ">", ">>", ">>>", "?", "...").contains(token.text())) {
                    throw error(token, "getMethod recebe uma identificação de método, não uma expressão");
                }
                advance();
                if (token.is("(")) depth++;
                if (token.is(")")) depth--;
                query.append(token.text());
            }
        }
        expect(")");
        expect(";");
        if (query.toString().isBlank()) throw error(operation, "informe o método para supervision.getMethod");
        return new MethodObservation(kw, query.toString());
    }

    // character := ('hero' | 'villain') IDENT '{' member* '}'
    private CharacterDecl parseCharacter() {
        boolean villain = advance().is("villain");
        Token name = expectIdent("nome do " + (villain ? "vilão" : "herói"));
        expect("{");
        List<FieldDecl> fields = new ArrayList<>();
        List<PropertyBlock> powers = new ArrayList<>(), gadgets = new ArrayList<>(), forms = new ArrayList<>();
        List<SecretDecl> secrets = new ArrayList<>();
        List<MissionDecl> missions = new ArrayList<>();
        List<CaveMethod> methods = new ArrayList<>();
        while (!check("}")) {
            Token t = peek();
            if (check(TokenType.EOF)) throw error(t, "'}' faltando para fechar " + name.text());
            if (t.is("kryptonite")) throw error(t, "kryptonite nesta etapa só é permitido em variáveis locais de battle, mission ou métodos de batcave");
            if (t.is("power")) {
                advance();
                powers.add(parsePropertyBlock());
            } else if (t.is("gadget")) {
                advance();
                gadgets.add(parsePropertyBlock());
            } else if (t.is("form")) {
                advance();
                forms.add(parsePropertyBlock());
            } else if (t.is("secret")) {
                if (peekAt(1).is("mission")) {
                    advance();
                    MissionDecl mission = parseMission();
                    missions.add(new MissionDecl(mission.name(), mission.params(), mission.body(), true));
                } else if (secretMethodAhead()) {
                    parseJavaMember(fields, methods);
                } else {
                    secrets.add(parseSecret());
                }
            } else if (t.is("mission")) {
                missions.add(parseMission());
            } else if (javaMethodAhead()) {
                parseJavaMember(fields, methods);
            } else {
                List<Token> field = collectUntilSemicolon("atributo");
                fields.add(new FieldDecl(field));
            }
        }
        expect("}");
        return new CharacterDecl(name, villain, fields, powers, gadgets, forms, secrets, missions, methods);
    }

    // propertyBlock := IDENT '{' (IDENT ('=' | '+=' | '-=' | '*=') expr ';')* '}'
    private PropertyBlock parsePropertyBlock() {
        Token name = expectIdent("nome");
        expect("{");
        List<Property> props = new ArrayList<>();
        while (!check("}")) {
            Token prop = expectIdent("nome de propriedade");
            Token op = advance();
            if (!(op.is("=") || op.is("+=") || op.is("-=") || op.is("*="))) {
                throw error(op, "esperado '=', '+=', '-=' ou '*=' depois de '" + prop.text() + "'");
            }
            List<Token> value = collectUntilSemicolon("valor");
            value.remove(value.size() - 1);
            if (value.isEmpty()) throw error(op, "valor faltando para '" + prop.text() + "'");
            props.add(new Property(prop, op, value));
        }
        expect("}");
        return new PropertyBlock(name, props);
    }

    // secret := 'secret' type IDENT ('=' expr)? ('allow' IDENT (',' IDENT)*)? ';'
    private SecretDecl parseSecret() {
        Token kw = advance();
        if (check("kryptonite")) throw error(peek(), "a combinação secret + kryptonite não é permitida");
        List<Token> decl = new ArrayList<>();
        while (!check(";") && !check("allow")) {
            if (check(TokenType.EOF) || check("}")) throw error(peek(), "';' faltando no secret");
            decl.add(advance());
        }
        List<Token> allowed = new ArrayList<>();
        if (match("allow")) {
            allowed.add(expectIdent("nome de missão"));
            while (match(",")) allowed.add(expectIdent("nome de missão"));
        }
        expect(";");
        int eq = -1;
        for (int i = 0; i < decl.size(); i++) {
            if (decl.get(i).is("=")) {
                eq = i;
                break;
            }
        }
        int nameIdx = eq == -1 ? decl.size() - 1 : eq - 1;
        if (nameIdx < 1 || !decl.get(nameIdx).isIdent()) throw error(kw, "use: secret Tipo nome = valor allow Missao;");
        rejectUnsupportedModifiers(decl);
        int depth = 0;
        for (int i = Math.max(0, eq + 1); eq >= 0 && i < decl.size(); i++) {
            Token token = decl.get(i);
            if (token.is("(") || token.is("[") || token.is("{")) depth++;
            if (token.is(")") || token.is("]") || token.is("}")) depth--;
            if (depth == 0 && token.is(",")) throw error(token, "declare uma variável secret por instrução");
        }
        List<Token> init = eq == -1 ? List.of() : decl.subList(eq + 1, decl.size());
        return new SecretDecl(decl.get(nameIdx), decl.subList(0, nameIdx), init, allowed);
    }

    // mission := 'mission' IDENT ('(' params ')')? block
    private MissionDecl parseMission() {
        advance();
        Token name = expectIdent("nome da missão");
        List<Token> params = new ArrayList<>();
        if (match("(")) {
            while (!check(")")) {
                if (check(TokenType.EOF) || check("{")) throw error(peek(), "')' faltando nos parâmetros da missão");
                params.add(advance());
            }
            expect(")");
        }
        rejectUnsupportedModifiers(params);
        return new MissionDecl(name, params, parseBlock(), false);
    }

    // squad := 'squad' IDENT '{' ('members' IDENT (',' IDENT)* ';' | 'combo' propertyBlock)* '}'
    private SquadDecl parseSquad() {
        advance();
        Token name = expectIdent("nome do squad");
        expect("{");
        List<Token> members = new ArrayList<>();
        List<PropertyBlock> combos = new ArrayList<>();
        while (!check("}")) {
            if (match("members")) {
                members.add(expectIdent("tipo de herói"));
                while (match(",")) members.add(expectIdent("tipo de herói"));
                expect(";");
            } else if (match("combo")) {
                combos.add(parsePropertyBlock());
            } else {
                throw error(peek(), "dentro de squad use 'members' ou 'combo'");
            }
        }
        expect("}");
        return new SquadDecl(name, members, combos);
    }

    // block := '{' statement* '}'
    private Block parseBlock() {
        Token open = expect("{");
        List<Stmt> stmts = new ArrayList<>();
        while (!check("}")) {
            if (check(TokenType.EOF)) throw error(open, "bloco aberto aqui nunca foi fechado com '}'");
            stmts.add(parseStatement());
        }
        expect("}");
        return new Block(open, stmts);
    }

    private Stmt parseStatement() {
        Token t = peek();
        if(t.is("hulksmash")) {
            advance(); expect("(");
            List<Token> condition=new ArrayList<>();
            int depth=1;
            while(depth>0) {
                Token part=advance();
                if(part.type()==TokenType.EOF)throw error(t,"condição de hulksmash não foi fechada");
                if(part.is("("))depth++;
                if(part.is(")"))depth--;
                if(depth>0)condition.add(part);
            }
            if(condition.isEmpty())throw error(t,"hulksmash exige condição");
            hulkDepth++;
            hulkRegions.push(false);
            Block body;
            try { body=parseBlock(); } finally { hulkDepth--; hulkRegions.pop(); }
            return new HulkSmash(t,List.copyOf(condition),HulkRegions.executable(body),body);
        }
        if(t.is("blackwidow")) {
            advance();
            if(hulkDepth==0)throw error(t,"blackwidow só pode ser usado dentro de hulksmash");
            boolean previous=hulkRegions.pop(); hulkRegions.push(true);
            try { return new BlackWidow(t,parseBlock()); }
            finally { hulkRegions.pop(); hulkRegions.push(previous); }
        }
        if(t.is("hulkbuster")) {
            advance();
            if(hulkDepth==0)throw error(t,"hulkbuster só pode ser usado dentro de hulksmash");
            expect(";");
            return new HulkBuster(t);
        }
        if(t.is("batplan")) {
            advance(); expect("(");
            Token target=expectIdent("referência de objeto para batplan");
            expect(")"); expect(";");
            return new Batplan(t,target);
        }
        if (t.is("deadpoolAction")) return parseDeadpoolAction();
        if (t.is("flashPoint")) return parseFlashPoint();
        if (t.is("flashtravel")) {
            advance();
            boolean ignored=!hulkRegions.isEmpty()&&!hulkRegions.peek();
            if (!ignored&&deadpoolDepth>0) throw error(t,"flashtravel não é permitido dentro de deadpoolAction, inclusive em blocos internos");
            if (!ignored&&flashDepth == 0) throw error(t, "flashtravel só pode ser usado dentro de flashPoint");
            Token destination = advance();
            if (destination.type() != TokenType.NUMBER || !destination.text().matches("[1-9][0-9]*")) {
                throw error(destination, "flashtravel exige um número inteiro positivo de linha física");
            }
            try { Integer.parseInt(destination.text()); }
            catch (NumberFormatException e) { throw error(destination, "número de linha fora do intervalo suportado"); }
            expect(";");
            return new FlashTravel(t, destination);
        }
        if (t.is("battle") && flashDepth > 0) {
            if (!rootFlash || ++battles > 1) throw error(t, "battle só pode aparecer uma vez dentro do flashPoint de entrada");
            advance();
            return parseBlock();
        }
        if (t.is("supervision") && peekAt(1).is(".")) return parseSupervision();
        if (inBatcave && t.is("villain")) throw error(t, "villain não pode ser declarado dentro de batcave");
        if (t.is("secret") && peekAt(1).is("kryptonite")) throw error(t, "a combinação secret + kryptonite não é permitida");
        if (t.is("kryptonite") && peekAt(1).is("secret")) throw error(t, "a combinação secret + kryptonite não é permitida");
        if (t.is("{")) return parseBlock();
        if (t.isIdent() && STATEMENT_KEYWORDS.contains(t.text()) && !peekAt(1).is("=")) {
            return parseHeroStatement();
        }
        return parseRaw();
    }

    private FlashPoint parseFlashPoint() {
        Token keyword = expect("flashPoint");
        flashDepth++;
        Block body = parseBlock();
        flashDepth--;
        return new FlashPoint(keyword, body);
    }

    private DeadpoolAction parseDeadpoolAction() {
        Token keyword=expect("deadpoolAction");
        expect("(");
        expect("chance");
        expect("=");
        Token value=advance();
        int chance;
        try {
            if(value.type()!=TokenType.NUMBER || !value.text().matches("[0-9]+")) throw new NumberFormatException();
            chance=Integer.parseInt(value.text());
            if(chance>100)throw new NumberFormatException();
        } catch(NumberFormatException e) {
            throw error(value,"chance de deadpoolAction deve ser um inteiro literal de 0 a 100");
        }
        if(check(","))throw error(peek(),"deadpoolAction aceita somente chance; seed e outros parâmetros não são suportados");
        expect(")");
        deadpoolDepth++;
        Block body=parseBlock();
        deadpoolDepth--;
        return new DeadpoolAction(keyword,chance,body);
    }

    private Stmt parseHeroStatement() {
        Token kw = advance();
        switch (kw.text()) {
            case "secret": {
                pos--;
                SecretDecl declaration = parseSecret();
                if (!declaration.allowed().isEmpty()) throw error(kw, "allow não é permitido em variáveis locais secret");
                if (declaration.init().isEmpty()) throw error(kw, "variável local secret exige inicializador");
                if (declaration.type().stream().anyMatch(t -> t.is("var") || t.is("public") || t.is("private") || t.is("static"))) {
                    throw error(kw, "use secret [final] Tipo nome = valor;");
                }
                return new SecretLocal(kw,declaration);
            }
            case "kryptonite": {
                boolean immutable = match("final");
                Token type = expectIdent("tipo primitivo ou String");
                if (!Set.of("byte", "short", "int", "long", "float", "double", "boolean", "char", "String").contains(type.text())) {
                    throw error(type, "kryptonite aceita apenas tipos primitivos ou String");
                }
                Token name = expectIdent("nome da variável kryptonite");
                expect("=");
                List<Token> init = collectExpr("inicializador kryptonite");
                int depth = 0;
                for (Token token : init) {
                    if (token.is("(") || token.is("[") || token.is("{")) depth++;
                    if (token.is(")") || token.is("]") || token.is("}")) depth--;
                    if (depth == 0 && token.is(",")) throw error(token, "declare uma variável kryptonite por instrução");
                }
                expect(";");
                return new Kryptonite(kw, type, name, immutable, init);
            }
            case "unleash": {
                Token[] sp = subjectAndName("poder");
                List<Token> target = match("on") ? collectExpr("alvo") : List.of();
                expect(";");
                return new Unleash(kw, sp[0], sp[1], target);
            }
            case "transform": {
                Token[] sp = subjectAndName("forma");
                expect(";");
                return new Transform(kw, sp[0], sp[1]);
            }
            case "revert": {
                Token subject = check(";") ? null : expectIdent("personagem");
                expect(";");
                return new Revert(kw, subject);
            }
            case "evolve": {
                Token[] sp = subjectAndName("poder");
                expect("by");
                List<Token> amount = collectExpr("quantidade");
                expect(";");
                return new Evolve(kw, sp[0], sp[1], amount);
            }
            case "summon": {
                Token type = expectIdent("tipo de herói, vilão ou squad");
                expect("as");
                Token var = expectIdent("nome da variável");
                expect(";");
                return new Summon(kw, type, var);
            }
            case "recruit": {
                Token hero = expectIdent("herói");
                expect("into");
                Token squad = expectIdent("squad");
                expect(";");
                return new Recruit(kw, hero, squad);
            }
            case "assemble": {
                Token squad = expectIdent("squad");
                expect(".");
                Token combo = expectIdent("combo");
                List<Token> target = match("on") ? collectExpr("alvo") : List.of();
                expect(";");
                return new Assemble(kw, squad, combo, target);
            }
            case "announce": {
                List<Token> expr = collectExpr("mensagem");
                expect(";");
                return new Announce(kw, expr);
            }
            default:
                throw error(kw, "comando desconhecido");
        }
    }

    /** Lê {@code Nome} ou {@code sujeito.Nome}. Retorna [sujeito ou null, nome]. */
    private Token[] subjectAndName(String what) {
        Token first = expectIdent(what);
        if (match(".")) return new Token[] {first, expectIdent(what)};
        return new Token[] {null, first};
    }

    /** Comando Java comum: tokens até ';' (inclusive) ou até '{', que abre um bloco analisado recursivamente. */
    private Raw parseRaw() {
        List<Token> toks = new ArrayList<>();
        int depth = 0;
        while (true) {
            Token t = peek();
            if (check(TokenType.EOF)) throw error(t, "';' ou '}' faltando");
            if (depth == 0 && t.is("{")) return new Raw(toks, parseBlock());
            if (depth == 0 && t.is("}")) return new Raw(toks, null);
            advance();
            toks.add(t);
            if (t.is("(") || t.is("[") || t.is("{")) depth++;
            if (t.is(")") || t.is("]") || t.is("}")) depth--;
            if (depth == 0 && t.is(";")) return new Raw(toks, null);
        }
    }

    /** Tokens até ';' em profundidade zero (o ';' é incluído). Usado para atributos. */
    private List<Token> collectUntilSemicolon(String what) {
        List<Token> toks = new ArrayList<>();
        int depth = 0;
        while (true) {
            Token t = peek();
            if (check(TokenType.EOF)) throw error(t, "';' faltando no " + what);
            if (depth == 0 && (t.is("{") || t.is("}"))) {
                String hint = what.equals("atributo") ? " (métodos não são permitidos aqui; use 'mission')" : "";
                throw error(t, "';' faltando no " + what + hint);
            }
            advance();
            toks.add(t);
            if (t.is("(") || t.is("[") || t.is("{")) depth++;
            if (t.is(")") || t.is("]") || t.is("}")) depth--;
            if (depth == 0 && t.is(";")) return toks;
        }
    }

    /** Expressão Java até ';' em profundidade zero (o ';' NÃO é consumido). */
    private List<Token> collectExpr(String what) {
        List<Token> toks = new ArrayList<>();
        int depth = 0;
        while (!(depth == 0 && check(";"))) {
            Token t = peek();
            if (check(TokenType.EOF) || (depth == 0 && t.is("}"))) throw error(t, "';' faltando depois do " + what);
            advance();
            toks.add(t);
            if (t.is("(") || t.is("[") || t.is("{")) depth++;
            if (t.is(")") || t.is("]") || t.is("}")) depth--;
        }
        if (toks.isEmpty()) throw error(peek(), what + " faltando");
        return toks;
    }

    // ---------------------------------------------------------------- utilitários

    private Token peek() {
        return tokens.get(pos);
    }

    private Token peekAt(int offset) {
        return tokens.get(Math.min(pos + offset, tokens.size() - 1));
    }

    private Token advance() {
        Token t = tokens.get(pos);
        if (t.type() != TokenType.EOF) pos++;
        return t;
    }

    private boolean check(String text) {
        return peek().is(text) && peek().type() != TokenType.EOF;
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean match(String text) {
        if (check(text)) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(String text) {
        if (!check(text)) throw error(peek(), "esperado '" + text + "', encontrado '" + peek().text() + "'");
        return advance();
    }

    private Token expectIdent(String what) {
        if (!peek().isIdent()) throw error(peek(), "esperado " + what + ", encontrado '" + peek().text() + "'");
        return advance();
    }

    private CompileError error(Token at, String message) {
        return new CompileError(file, at, message);
    }
}
