package heroscript.compiler;

import java.util.List;

/**
 * Árvore sintática abstrata (AST) da HeroScript.
 * Expressões Java não são analisadas: ficam guardadas como listas de tokens ("raw") e são copiadas para o Java gerado.
 */
public final class Ast {

    private Ast() {}

    public record Program(List<CharacterDecl> characters, List<SquadDecl> squads, List<BatcaveDecl> batcaves, Block battle) {}

    public record BatcaveDecl(Token name, List<FieldDecl> fields, List<CaveMethod> methods) {}

    public record CaveMethod(Token name, List<Token> header, List<Token> params, Block body, boolean secret) {}

    /** hero ou villain. */
    public record CharacterDecl(Token name, boolean villain, List<FieldDecl> fields, List<PropertyBlock> powers,
            List<PropertyBlock> gadgets, List<PropertyBlock> forms, List<SecretDecl> secrets,
            List<MissionDecl> missions, List<CaveMethod> methods) {}

    /** Atributo Java comum, ex.: {@code int energy = 100;} */
    public record FieldDecl(List<Token> tokens, boolean secret) {
        public FieldDecl(List<Token> tokens) { this(tokens, false); }
    }

    /** Blocos power / gadget / form / combo: um nome e uma lista de propriedades. */
    public record PropertyBlock(Token name, List<Property> props) {}

    /** {@code damage = 60;} ou, em formas, {@code damage *= 2;} */
    public record Property(Token name, Token op, List<Token> value) {}

    /** {@code secret String identity = "Tony" allow RevealIdentity;} */
    public record SecretDecl(Token name, List<Token> type, List<Token> init, List<Token> allowed) {}

    public record MissionDecl(Token name, List<Token> params, Block body, boolean secret) {}

    public record SquadDecl(Token name, List<Token> members, List<PropertyBlock> combos) {}

    // ---------------------------------------------------------------- comandos

    public sealed interface Stmt permits Block, Raw, Unleash, Transform, Revert, Evolve, Summon, Recruit,
            Assemble, Announce, ValueObservation, MethodObservation, Kryptonite, SecretLocal, FlashPoint, FlashTravel, DeadpoolAction, Batplan, HulkSmash, BlackWidow, HulkBuster {}

    public record HulkSmash(Token kw, List<Token> condition, Block body, Block sourceBody) implements Stmt {}
    public record BlackWidow(Token kw, Block body) implements Stmt {}
    public record HulkBuster(Token kw) implements Stmt {}

    public record Batplan(Token kw, Token target) implements Stmt {}

    public record DeadpoolAction(Token kw, int chance, Block body) implements Stmt {}

    public record FlashPoint(Token kw, Block body) implements Stmt {}
    public record FlashTravel(Token kw, Token destination) implements Stmt {}

    public record SecretLocal(Token kw, SecretDecl declaration) implements Stmt {}

    public record ValueObservation(Token kw, Token name) implements Stmt {}

    public record MethodObservation(Token kw, String query) implements Stmt {}

    public record Kryptonite(Token kw, Token type, Token name, boolean immutable, List<Token> init) implements Stmt {}

    public record Block(Token open, List<Stmt> stmts) implements Stmt {}

    /** Código Java repassado como está. Se {@code body} não for nulo, os tokens são o cabeçalho de um bloco (if, for...). */
    public record Raw(List<Token> tokens, Block body) implements Stmt {}

    /** {@code unleash [sujeito.]Poder [on alvo];} — sujeito nulo significa "this". */
    public record Unleash(Token kw, Token subject, Token power, List<Token> target) implements Stmt {}

    public record Transform(Token kw, Token subject, Token form) implements Stmt {}

    public record Revert(Token kw, Token subject) implements Stmt {}

    public record Evolve(Token kw, Token subject, Token power, List<Token> amount) implements Stmt {}

    public record Summon(Token kw, Token type, Token var) implements Stmt {}

    public record Recruit(Token kw, Token hero, Token squad) implements Stmt {}

    public record Assemble(Token kw, Token squad, Token combo, List<Token> target) implements Stmt {}

    public record Announce(Token kw, List<Token> expr) implements Stmt {}
}
