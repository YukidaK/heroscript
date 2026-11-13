package heroscript.compiler;

import heroscript.compiler.Ast.*;
import java.util.*;

/** Editor diagnostics. Never loads or executes user classes. */
public final class SpiderSense {
    private SpiderSense() {}
    public record Issue(String code,String severity,String file,int line,int column,int endColumn,String message) {}

    public static List<Issue> analyze(String source,String file) {
        List<Issue> issues=new ArrayList<>();
        try {
            Program program=new Parser(new Lexer(source,file).tokenize(),file).parse();
            if(program.battle()!=null)scan(program.battle(),file,false,issues);
            for(var c:program.characters()) {
                for(var m:c.methods())scan(m.body(),file,false,issues);
                for(var m:c.missions())scan(m.body(),file,false,issues);
            }
            for(var c:program.batcaves())for(var m:c.methods())scan(m.body(),file,false,issues);
            JavaRunner.diagnose(file,new JavaGenerator(file,program).generate(),issues::add);
        } catch(CompileError error) {
            issues.add(new Issue("HERO_ERROR","error",file,error.line,error.col,error.col+1,error.getMessage()));
        }
        issues.sort(Comparator.comparingInt(Issue::line).thenComparingInt(Issue::column).thenComparing(Issue::code));
        return List.copyOf(issues);
    }

    private static void warning(Token at,String file,String code,String message,List<Issue> issues) {
        issues.add(new Issue(code,"warning",file,at.line(),at.col(),at.col()+at.text().length(),message));
    }
    private static void scan(Block block,String file,boolean ignored,List<Issue> issues) {
        for(Stmt stmt:block.stmts()) {
            if(stmt instanceof HulkSmash hulk) {
                if(hulk.body().stmts().isEmpty())warning(hulk.kw(),file,"HULK_NO_BLACKWIDOW",
                        "Hulksmash sem blackwidow: a condição pode permanecer verdadeira sem trabalho executável.",issues);
                scan(hulk.sourceBody(),file,true,issues);
            } else if(stmt instanceof BlackWidow widow)scan(widow.body(),file,false,issues);
            else if(stmt instanceof HulkBuster buster&&ignored)warning(buster.kw(),file,"HULKBUSTER_IGNORED",
                    "Hulkbuster fora de blackwidow é ignorado.",issues);
            else if(stmt instanceof FlashTravel travel&&ignored)warning(travel.kw(),file,"FLASHTRAVEL_IGNORED",
                    "Flashtravel fora de blackwidow é ignorado pelo hulksmash.",issues);
            else if(stmt instanceof Block nested)scan(nested,file,ignored,issues);
            else if(stmt instanceof Raw raw&&raw.body()!=null)scan(raw.body(),file,ignored,issues);
            else if(stmt instanceof FlashPoint point)scan(point.body(),file,ignored,issues);
            else if(stmt instanceof DeadpoolAction action) {
                if(!ignored&&action.chance()<100)warning(action.kw(),file,"DEADPOOL_MAY_SKIP",
                        "deadpoolAction tem chance de execução de "+action.chance()+"%: há "+(100-action.chance())
                        +"% de chance de o bloco não executar. Se uma operação ou chamada de método depender desse bloco, ela pode não acontecer. Este aviso não impede a compilação.",issues);
                scan(action.body(),file,ignored,issues);
            }
        }
    }

    public static String json(List<Issue> issues) {
        StringJoiner result=new StringJoiner(",","[","]");
        for(Issue i:issues)result.add("{\"code\":"+quote(i.code())+",\"severity\":"+quote(i.severity())
                +",\"file\":"+quote(i.file())+",\"line\":"+i.line()+",\"column\":"+i.column()
                +",\"endColumn\":"+i.endColumn()+",\"message\":"+quote(i.message())+"}");
        return result.toString();
    }
    private static String quote(String text) {
        StringBuilder out=new StringBuilder("\"");
        for(char c:text.toCharArray())switch(c) {
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> {if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
        }
        return out.append('"').toString();
    }
}
