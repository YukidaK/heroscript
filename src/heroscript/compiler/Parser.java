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
            Set.of("unleash", "transform", "revert", "evolve", "summon", "recruit", "assemble", "announce");

    private final List<Token> tokens;
    private final String file;
    private int pos = 0;

    public Parser(List<Token> tokens, String file) {
        this.tokens = tokens;
        this.file = file;
    }

    // program := (character | squad | battle)* EOF
    public Program parse() {
        List<CharacterDecl> characters = new ArrayList<>();
        List<SquadDecl> squads = new ArrayList<>();
        Block battle = null;
        while (!check(TokenType.EOF)) {
            Token t = peek();
            if (t.is("hero") || t.is("villain")) {
                characters.add(parseCharacter());
            } else if (t.is("squad")) {
                squads.add(parseSquad());
            } else if (t.is("battle")) {
                if (battle != null) throw error(t, "só pode existir um bloco battle por arquivo");
                advance();
                battle = parseBlock();
            } else {
                throw error(t, "esperado 'hero', 'villain', 'squad' ou 'battle', encontrado '" + t.text() + "'");
            }
        }
        return new Program(characters, squads, battle);
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
        while (!check("}")) {
            Token t = peek();
            if (check(TokenType.EOF)) throw error(t, "'}' faltando para fechar " + name.text());
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
                secrets.add(parseSecret());
            } else if (t.is("mission")) {
                missions.add(parseMission());
            } else {
                List<Token> field = collectUntilSemicolon("atributo");
                fields.add(new FieldDecl(field));
            }
        }
        expect("}");
        return new CharacterDecl(name, villain, fields, powers, gadgets, forms, secrets, missions);
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
        return new MissionDecl(name, params, parseBlock());
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
        if (t.is("{")) return parseBlock();
        if (t.isIdent() && STATEMENT_KEYWORDS.contains(t.text()) && !peekAt(1).is("=")) {
            return parseHeroStatement();
        }
        return parseRaw();
    }

    private Stmt parseHeroStatement() {
        Token kw = advance();
        switch (kw.text()) {
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
