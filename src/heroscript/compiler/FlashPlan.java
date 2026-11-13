package heroscript.compiler;

import heroscript.compiler.Ast.*;
import java.util.*;

/** Lowers one lexical flashPoint to instruction states. Targets refer only to original source lines. */
final class FlashPlan {
    record Slot(Token original, Token generated, List<Token> type, boolean protectedValue, boolean immutable) {}
    static final class Node {
        final int id;
        final Token at;
        final Stmt action;
        final List<Token> condition;
        final Map<String, Slot> scope;
        final List<Slot> reads = new ArrayList<>(), writes = new ArrayList<>();
        Slot initialized;
        int next = -1, other = -1;
        Integer destination;
        Integer deadpoolChance;
        boolean terminal;
        Node(int id, Token at, Stmt action, List<Token> condition, Map<String, Slot> scope) {
            this.id=id; this.at=at; this.action=action; this.condition=condition; this.scope=Map.copyOf(scope);
        }
    }
    private record Edge(Node node, boolean alternate) {}
    private record Fragment(int entry, List<Edge> exits) {}
    private record Conditional(Fragment fragment, int nextIndex) {}
    private static final class Loop {
        final int continuing;
        final List<Edge> breaks = new ArrayList<>();
        Loop(int continuing) { this.continuing=continuing; }
    }
    final List<Node> nodes = new ArrayList<>();
    final List<Slot> slots = new ArrayList<>();
    final int entry;
    final boolean completes;
    final String prefix;
    private final String file;
    private final Map<Integer, Node> lines = new HashMap<>();
    private final Map<Integer,Integer> protectedColumns = new HashMap<>();
    private int deadpoolDepth;
    private final Deque<Loop> hulks=new ArrayDeque<>();
    private final Set<String> forbidden;

    FlashPlan(String file, FlashPoint point, int serial,Set<String> occupied,Set<String> forbidden) {
        this.file=file;
        this.forbidden=forbidden;
        String candidate="__heroFlash" + serial + "_";
        while(point.body().toString().contains(candidate) || collides(occupied,candidate)) candidate+="x";
        prefix=candidate;
        Fragment graph = sequence(point.body().stmts(), new HashMap<>(), null);
        Node end = node(point.kw(), null, null, Map.of(), false);
        end.terminal=true;
        connect(graph.exits(), end.id);
        entry=graph.entry()<0 ? end.id : graph.entry();
        for (Node node : nodes) if (node.destination != null) {
            Node target=lines.get(node.destination);
            if (target==null || protectedColumns.getOrDefault(node.destination,Integer.MAX_VALUE)<target.at.col()) {
                throw error(node.at, "flashtravel deve apontar para o início de uma instrução dentro do mesmo flashPoint, fora do interior de deadpoolAction");
            }
            node.next=target.id;
        }
        completes=reachesEnd(end.id);
    }

    private boolean reachesEnd(int end) {
        Set<Integer> visited=new HashSet<>();
        Deque<Integer> pending=new ArrayDeque<>();
        pending.add(entry);
        while(!pending.isEmpty()) {
            int id=pending.removeFirst();
            if(id==end)return true;
            if(id<0||!visited.add(id))continue;
            Node node=nodes.get(id);
            if(node.terminal)continue;
            boolean constant=node.condition!=null&&node.condition.size()==1;
            boolean alwaysTrue=constant&&node.condition.get(0).is("true");
            boolean alwaysFalse=constant&&node.condition.get(0).is("false");
            if(!alwaysFalse)pending.add(node.next);
            if(node.condition!=null&&!alwaysTrue)pending.add(node.other);
        }
        return false;
    }

    private Node node(Token at, Stmt action, List<Token> condition, Map<String, Slot> scope, boolean target) {
        Node node=new Node(nodes.size(), at, action, condition, scope);
        nodes.add(node);
        if(target) {
            if(deadpoolDepth>0)protectedColumns.merge(at.line(),at.col(),Math::min);
            else lines.compute(at.line(), (line, previous) -> previous==null || at.col()<previous.at.col() ? node : previous);
        }
        return node;
    }
    private static void connect(List<Edge> exits, int target) {
        for (Edge edge : exits) { if (edge.alternate()) edge.node().other=target; else edge.node().next=target; }
    }
    private static Fragment single(Node node) { return new Fragment(node.id, List.of(new Edge(node,false))); }
    private Fragment sequence(List<Stmt> statements, Map<String, Slot> scope, Loop loop) {
        int entry=-1;
        List<Edge> exits=List.of();
        for (int i=0; i<statements.size(); i++) {
            Stmt statement=statements.get(i);
            Fragment fragment;
            if (statement instanceof Raw raw && starts(raw,"if")) {
                Conditional built=conditional(raw, statements, i+1, scope, loop);
                fragment=built.fragment(); i=built.nextIndex()-1;
            } else fragment=statement(statement,scope,loop);
            if (fragment.entry()<0) continue;
            if (entry<0) entry=fragment.entry();
            connect(exits,fragment.entry()); exits=fragment.exits();
        }
        return new Fragment(entry,exits);
    }
    private Conditional conditional(Raw raw, List<Stmt> statements, int following, Map<String, Slot> scope, Loop loop) {
        Node condition=condition(raw.tokens().get(0), parentheses(raw.tokens()),scope,true);
        Fragment yes=sequence(raw.body().stmts(),new HashMap<>(scope),loop);
        List<Edge> exits=new ArrayList<>();
        if (yes.entry()<0) exits.add(new Edge(condition,false));
        else { condition.next=yes.entry(); exits.addAll(yes.exits()); }
        if (following<statements.size() && statements.get(following) instanceof Raw alternative && starts(alternative,"else")) {
            Fragment no;
            if (alternative.body()==null) throw error(alternative.tokens().get(0),"use blocos com chaves no else de flashPoint");
            if (alternative.tokens().size()>1 && alternative.tokens().get(1).is("if")) {
                Conditional nested=conditional(new Raw(alternative.tokens().subList(1,alternative.tokens().size()),alternative.body()),statements,following+1,scope,loop);
                no=nested.fragment(); following=nested.nextIndex();
            } else {
                no=sequence(alternative.body().stmts(),new HashMap<>(scope),loop); following++;
            }
            if (no.entry()<0) exits.add(new Edge(condition,true));
            else { condition.other=no.entry(); exits.addAll(no.exits()); }
        } else exits.add(new Edge(condition,true));
        return new Conditional(new Fragment(condition.id,exits),following);
    }
    private Fragment statement(Stmt statement, Map<String, Slot> scope, Loop loop) {
        if(statement instanceof HulkSmash hulk) {
            Node test=condition(hulk.kw(),hulk.condition(),scope,true);
            Loop inside=new Loop(test.id);
            hulks.push(inside);
            Fragment body;
            try { body=sequence(hulk.body().stmts(),new HashMap<>(scope),inside); }
            finally { hulks.pop(); }
            test.next=body.entry()<0?test.id:body.entry();
            connect(body.exits(),test.id);
            List<Edge> exits=new ArrayList<>(inside.breaks);
            exits.add(new Edge(test,true));
            return new Fragment(test.id,exits);
        }
        if(statement instanceof BlackWidow widow)return sequence(widow.body().stmts(),new HashMap<>(scope),loop);
        if(statement instanceof HulkBuster buster&&!hulks.isEmpty()) {
            Node jump=node(buster.kw(),null,null,scope,true);
            hulks.peek().breaks.add(new Edge(jump,false));
            return new Fragment(jump.id,List.of());
        }
        if (statement instanceof FlashPoint nested) throw error(nested.kw(),"flashPoint aninhado ainda não é suportado");
        if (statement instanceof Ast.DeadpoolAction action) {
            List<Token> expression=new Lexer("heroscript.runtime.DeadpoolAction.choose("+action.chance()+")",file).tokenize();
            Node decision=node(action.kw(),null,expression.subList(0,expression.size()-1),Map.of(),true);
            decision.deadpoolChance=action.chance();
            Fragment body;
            deadpoolDepth++;
            try { body=sequence(action.body().stmts(),new HashMap<>(scope),loop); }
            finally { deadpoolDepth--; }
            List<Edge> exits=new ArrayList<>(body.exits());
            if(body.entry()<0)exits.add(new Edge(decision,false));
            else decision.next=body.entry();
            exits.add(new Edge(decision,true));
            return new Fragment(decision.id,exits);
        }
        if (statement instanceof Block block) return sequence(block.stmts(),new HashMap<>(scope),loop);
        if (statement instanceof FlashTravel travel) {
            Node node=node(travel.kw(),null,null,scope,true);
            node.destination=Integer.parseInt(travel.destination().text());
            return new Fragment(node.id,List.of());
        }
        if (statement instanceof Raw raw) {
            if (raw.tokens().isEmpty()) throw error(raw.body().open(),"bloco não reconhecido em flashPoint");
            Token first=raw.tokens().get(0);
            if (raw.body()!=null) {
                if (first.is("while")) {
                    Node condition=condition(first,parentheses(raw.tokens()),scope,true);
                    Loop inside=new Loop(condition.id);
                    Fragment body=sequence(raw.body().stmts(),new HashMap<>(scope),inside);
                    condition.next=body.entry()<0 ? condition.id : body.entry(); connect(body.exits(),condition.id);
                    List<Edge> exits=new ArrayList<>(inside.breaks); exits.add(new Edge(condition,true));
                    return new Fragment(condition.id,exits);
                }
                if (first.is("for")) return forLoop(raw,scope);
                throw error(first,"flashPoint suporta blocos, if/else, while e for com chaves; try/finally, do, switch e outros blocos ainda não são suportados");
            }
            if (first.is("break") || first.is("continue")) {
                if (loop==null || raw.tokens().size()!=2) throw error(first,"break/continue em flashPoint exigem um loop interno e não aceitam rótulo");
                Node node=node(first,null,null,scope,true);
                if (first.is("continue")) node.next=loop.continuing; else loop.breaks.add(new Edge(node,false));
                return new Fragment(node.id,List.of());
            }
            if (Set.of("if","while","for","do","switch","try","catch","finally","else").contains(first.text())) {
                throw error(first,"controle de fluxo em flashPoint exige blocos com chaves e uma construção suportada");
            }
            return action(raw,scope,true);
        }
        if (statement instanceof SecretLocal local) {
            SecretDecl declaration=local.declaration();
            return declaration(local.kw(),declaration.name(),declaration.type(),declaration.init(),true,scope,true);
        }
        if (statement instanceof Kryptonite local) {
            List<Token> type=new ArrayList<>();
            if (local.immutable()) type.add(token(local.kw(),"final"));
            type.add(local.type());
            return declaration(local.kw(),local.name(),type,local.init(),true,scope,true);
        }
        if (statement instanceof Summon summon) {
            List<Token> init=List.of(token(summon.kw(),"new"),summon.type(),token(summon.kw(),"("),token(summon.kw(),")"));
            return declaration(summon.kw(),summon.var(),List.of(summon.type()),init,false,scope,true);
        }
        Token at=at(statement);
        Node node=node(at,statement,null,scope,true);
        references(node,tokens(statement),false);
        return single(node);
    }
    private Fragment forLoop(Raw raw, Map<String, Slot> parent) {
        Map<String, Slot> scope=new HashMap<>(parent);
        List<Token> header=parentheses(raw.tokens());
        List<List<Token>> sections=new ArrayList<>();
        int depth=0,start=0;
        for (int i=0;i<header.size();i++) {
            Token token=header.get(i);
            if (token.is("(") || token.is("[")) depth++;
            if (token.is(")") || token.is("]")) depth--;
            if (token.is(";") && depth==0) { sections.add(header.subList(start,i)); start=i+1; }
        }
        sections.add(header.subList(start,header.size()));
        if (sections.size()!=3) throw error(raw.tokens().get(0),"use for(inicialização; condição; atualização) dentro de flashPoint");
        Fragment init=sections.get(0).isEmpty() ? new Fragment(-1,List.of()) : action(new Raw(withSemicolon(sections.get(0),raw.tokens().get(0)),null),scope,false);
        List<Token> expression=sections.get(1).isEmpty() ? List.of(token(raw.tokens().get(0),"true")) : sections.get(1);
        Node condition=condition(raw.tokens().get(0),expression,scope,init.entry()<0);
        if (init.entry()>=0) {
            Token loopStart=raw.tokens().get(0);
            if(deadpoolDepth>0)protectedColumns.merge(loopStart.line(),loopStart.col(),Math::min);
            else lines.putIfAbsent(loopStart.line(),nodes.get(init.entry()));
            connect(init.exits(),condition.id);
        }
        Fragment update=sections.get(2).isEmpty() ? new Fragment(-1,List.of()) : action(new Raw(withSemicolon(sections.get(2),raw.tokens().get(0)),null),scope,false);
        int continuing=update.entry()<0 ? condition.id : update.entry();
        connect(update.exits(),condition.id);
        Loop inside=new Loop(continuing);
        Fragment body=sequence(raw.body().stmts(),scope,inside);
        condition.next=body.entry()<0 ? continuing : body.entry(); connect(body.exits(),continuing);
        List<Edge> exits=new ArrayList<>(inside.breaks); exits.add(new Edge(condition,true));
        return new Fragment(init.entry()<0 ? condition.id : init.entry(),exits);
    }
    private Node condition(Token at,List<Token> expression,Map<String,Slot> scope,boolean target) {
        Node node=node(at,null,expression,scope,target);
        references(node,expression,false);
        if (!node.writes.isEmpty()) throw error(at,"atribuições em condições de flashPoint ainda não são suportadas");
        return node;
    }
    private Fragment action(Raw raw,Map<String,Slot> scope,boolean target) {
        List<Token> source=raw.tokens();
        int eq=-1;
        for (int i=0;i<source.size();i++) if (source.get(i).is("=")) { eq=i; break; }
        int name=(eq<0 ? source.size()-1 : eq)-1;
        if (name>0 && source.get(name).isIdent() && type(source.subList(0,name))) {
            return declaration(source.get(0),source.get(name),source.subList(0,name),eq<0 ? List.of() : source.subList(eq+1,source.size()-1),false,scope,target);
        }
        Node node=node(source.get(0),raw,null,scope,target);
        references(node,source,true);
        node.terminal=source.get(0).is("return") || source.get(0).is("throw");
        return node.terminal ? new Fragment(node.id,List.of()) : single(node);
    }
    private Fragment declaration(Token at,Token name,List<Token> declaredType,List<Token> init,boolean protectedValue,Map<String,Slot> scope,boolean target) {
        if (scope.containsKey(name.text()) || forbidden.contains(name.text())) throw error(name,"variável já declarada neste escopo de flashPoint");
        boolean immutable=declaredType.stream().anyMatch(t->t.is("final"));
        List<Token> type=declaredType.stream().filter(t->!t.is("final")).toList();
        if (type.isEmpty() || type.stream().anyMatch(t->t.is("var"))) throw error(name,"variáveis em flashPoint exigem tipo explícito");
        if (immutable && init.isEmpty()) throw error(name,"variável final em flashPoint exige inicializador");
        int depth=0;
        for (Token token:init) {
            if (token.is("(") || token.is("[")) depth++;
            if (token.is(")") || token.is("]")) depth--;
            if (depth==0 && token.is(",")) throw error(name,"declare uma variável por instrução em flashPoint");
        }
        Slot slot=new Slot(name,new Token(TokenType.IDENT,prefix+"v"+slots.size(),name.line(),name.col()),type,protectedValue,immutable);
        slots.add(slot); scope.put(name.text(),slot);
        List<Token> assignment=new ArrayList<>();
        if (!init.isEmpty()) { assignment.add(name); assignment.add(token(name,"=")); assignment.addAll(init); }
        assignment.add(token(name,";"));
        Node node=node(at,new Raw(assignment,null),null,scope,target);
        references(node,init,false);
        node.initialized=init.isEmpty() ? null : slot;
        return single(node);
    }
    private void references(Node node,List<Token> tokens,boolean assignments) {
        for (int i=0;i<tokens.size();i++) {
            Token token=tokens.get(i);
            if (i>0 && tokens.get(i-1).is(".")) continue;
            Slot slot=node.scope.get(token.text());
            if (slot==null) continue;
            String after=i+1<tokens.size() ? tokens.get(i+1).text() : "";
            String before=i>0 ? tokens.get(i-1).text() : "";
            boolean write=Set.of("=","+=","-=","*=","/=","%=","++","--","&=","|=","^=","<<=",">>=",">>>=").contains(after) || before.equals("++") || before.equals("--");
            if (write) {
                if (slot.immutable()) throw error(token,"variável final não pode ser alterada em flashPoint");
                if (!node.writes.contains(slot)) node.writes.add(slot);
                if (!assignments) throw error(token,"efeitos de escrita nesta expressão de flashPoint ainda não são suportados");
            }
            if (!after.equals("=") && !node.reads.contains(slot)) node.reads.add(slot);
        }
    }
    static List<Token> rewrite(List<Token> tokens,Map<String,Slot> scope) {
        List<Token> result=new ArrayList<>();
        for (int i=0;i<tokens.size();i++) {
            Token token=tokens.get(i);
            Slot slot=scope.get(token.text());
            result.add(slot!=null && (i==0 || (!tokens.get(i-1).is(".")&&!tokens.get(i-1).is("new")&&!tokens.get(i-1).is("instanceof"))) ? slot.generated() : token);
        }
        return result;
    }
    static Stmt rewrite(Stmt statement,Map<String,Slot> scope) {
        if (statement instanceof Raw r) return new Raw(rewrite(r.tokens(),scope),null);
        if (statement instanceof Announce a) return new Announce(a.kw(),rewrite(a.expr(),scope));
        if (statement instanceof ValueObservation v) return new ValueObservation(v.kw(),rename(v.name(),scope));
        if (statement instanceof MethodObservation m) {
            String query=m.query(); int dot=query.indexOf('.');
            if (dot>0 && scope.containsKey(query.substring(0,dot))) query=scope.get(query.substring(0,dot)).generated().text()+query.substring(dot);
            return new MethodObservation(m.kw(),query);
        }
        if(statement instanceof Ast.Batplan b)return new Ast.Batplan(b.kw(),rename(b.target(),scope));
        if (statement instanceof Unleash u) return new Unleash(u.kw(),rename(u.subject(),scope),u.power(),rewrite(u.target(),scope));
        if (statement instanceof Transform t) return new Transform(t.kw(),rename(t.subject(),scope),t.form());
        if (statement instanceof Revert r) return new Revert(r.kw(),rename(r.subject(),scope));
        if (statement instanceof Evolve e) return new Evolve(e.kw(),rename(e.subject(),scope),e.power(),rewrite(e.amount(),scope));
        if (statement instanceof Recruit r) return new Recruit(r.kw(),rename(r.hero(),scope),rename(r.squad(),scope));
        if (statement instanceof Assemble a) return new Assemble(a.kw(),rename(a.squad(),scope),a.combo(),rewrite(a.target(),scope));
        return statement;
    }
    private static Token rename(Token token,Map<String,Slot> scope) { return token==null ? null : scope.containsKey(token.text()) ? scope.get(token.text()).generated() : token; }
    private static List<Token> tokens(Stmt s) {
        if(s instanceof Ast.Batplan b)return List.of(b.target());
        if (s instanceof Announce a) return a.expr();
        if (s instanceof ValueObservation v) return List.of(v.name());
        if (s instanceof Unleash u) { List<Token> result=new ArrayList<>(u.target()); if(u.subject()!=null)result.add(u.subject()); return result; }
        if (s instanceof Transform t) return t.subject()==null ? List.of() : List.of(t.subject());
        if (s instanceof Revert r) return r.subject()==null ? List.of() : List.of(r.subject());
        if (s instanceof Evolve e) { List<Token> result=new ArrayList<>(e.amount()); if(e.subject()!=null)result.add(e.subject()); return result; }
        if (s instanceof Recruit r) return List.of(r.hero(),r.squad());
        if (s instanceof Assemble a) { List<Token> result=new ArrayList<>(a.target()); result.add(a.squad()); return result; }
        return List.of();
    }
    private static Token at(Stmt s) {
        if(s instanceof HulkBuster b)return b.kw();
        if(s instanceof Ast.Batplan b)return b.kw();
        if(s instanceof Announce a)return a.kw(); if(s instanceof ValueObservation v)return v.kw();
        if(s instanceof MethodObservation m)return m.kw(); if(s instanceof Unleash u)return u.kw();
        if(s instanceof Transform t)return t.kw(); if(s instanceof Revert r)return r.kw();
        if(s instanceof Evolve e)return e.kw(); if(s instanceof Recruit r)return r.kw();
        if(s instanceof Assemble a)return a.kw(); throw new IllegalArgumentException("unsupported flash statement");
    }
    private static boolean starts(Raw raw,String word) { return !raw.tokens().isEmpty() && raw.tokens().get(0).is(word); }
    private static boolean collides(Set<String> occupied,String prefix) { return occupied.stream().anyMatch(name->name.startsWith(prefix)); }
    private List<Token> parentheses(List<Token> tokens) {
        int first=0; while(first<tokens.size()&&!tokens.get(first).is("("))first++;
        if(first==tokens.size()||!tokens.get(tokens.size()-1).is(")"))throw error(tokens.get(0),"cabeçalho de controle inválido em flashPoint");
        return tokens.subList(first+1,tokens.size()-1);
    }
    private static boolean type(List<Token> tokens) {
        if(tokens.isEmpty()||Set.of("return","throw","break","continue","new","if","while","for").contains(tokens.get(0).text()))return false;
        Token last=tokens.get(tokens.size()-1);
        if(last.is("."))return false;
        return tokens.stream().allMatch(t->t.isIdent()||Set.of(".","<",">",">>",">>>","[","]",",","?").contains(t.text()));
    }
    private static List<Token> withSemicolon(List<Token> tokens,Token at) { List<Token> result=new ArrayList<>(tokens);result.add(token(at,";"));return result; }
    private static Token token(Token at,String text) { return new Token(Character.isJavaIdentifierStart(text.charAt(0))?TokenType.IDENT:TokenType.SYMBOL,text,at.line(),at.col()); }
    private CompileError error(Token at,String message) { return new CompileError(file,at,message); }
}
