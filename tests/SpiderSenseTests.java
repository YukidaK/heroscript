import heroscript.compiler.*;
import java.util.*;
import java.nio.file.*;

public class SpiderSenseTests {
    static int checks;
    static void expect(boolean condition,String message) {
        checks++; if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        expect(SpiderSense.analyze("battle{announce 1;}","test.hero").isEmpty(),"valid program clean");
        for(int chance:new int[]{0,1,50,99,100}) {
            String source="battle{\n  deadpoolAction(chance="+chance+"){announce 1;}\n}";
            var issues=SpiderSense.analyze(source,"chance.hero");
            expect(issues.size()==(chance<100?1:0),"warning only below 100: "+chance);
            if(chance<100) {
                var issue=issues.get(0);
                expect(issue.severity().equals("warning")&&issue.code().equals("DEADPOOL_MAY_SKIP"),"yellow warning, never error");
                expect(issue.line()==2&&issue.column()==3&&issue.message().contains(chance+"%")&&issue.message().contains((100-chance)+"% de chance de o bloco não executar"),"explicit probability and location");
            }
            expect(JavaRunner.validate("chance.hero",Main.transpile(source,"chance.hero"),new java.io.PrintStream(java.io.OutputStream.nullOutputStream())),"warning does not prevent compilation");
        }
        expect(SpiderSense.analyze("battle{hulksmash(false){deadpoolAction(chance=50){blackwidow{}}}}","chance.hero").stream().noneMatch(i->i.code().equals("DEADPOOL_MAY_SKIP")),"no lottery warning for an ignored wrapper");
        expect(SpiderSense.analyze("hero H{public void act(){deadpoolAction(chance=20){}}}battle{}","chance.hero").stream().anyMatch(i->i.code().equals("DEADPOOL_MAY_SKIP")),"method body warning");
        for(String source:List.of(
            "battle{blackwidow{}}", "battle{hulkbuster;}",
            "battle{secret kryptonite int x=1;}",
            "battle{supervision.getValue(missing);}",
            "battle{flashtravel 1;}", "battle{flashPoint{flashtravel 999;}}",
            "battle{deadpoolAction(chance=101){}}", "battle{int x=0;batplan(x);}",
            "batcave C{villain V{}}", "battle{announce missing;}",
            "battle{int x=\"bad\";}", "battle{announce \"unterminated;}"
        )) {
            var issues=SpiderSense.analyze(source,"test.hero");
            expect(issues.stream().anyMatch(i->i.severity().equals("error")),"confirmed error: "+source);
            expect(issues.stream().allMatch(i->i.line()>0&&i.column()>0&&i.endColumn()>i.column()&&i.file().equals("test.hero")),"source positions");
        }
        var warnings=SpiderSense.analyze("battle{\nhulksmash(false){\nhulkbuster;\nflashtravel 999;\n}\n}","test.hero");
        expect(warnings.size()==3&&warnings.stream().allMatch(i->i.severity().equals("warning")),"ignored jumps and missing region only warn");
        expect(warnings.get(0).line()==2&&warnings.get(1).line()==3&&warnings.get(2).line()==4,"warning locations");
        expect(SpiderSense.analyze("battle{hulksmash(false){blackwidow{hulkbuster;}}}","test.hero").isEmpty(),"active buster clean");
        expect(SpiderSense.analyze("battle{hulksmash(false){blackwidow{flashtravel 999;}}}","test.hero").stream().anyMatch(i->i.severity().equals("error")),"active travel requires flashPoint");
        var capture=new java.io.ByteArrayOutputStream();
        String ignoredTravel="battle{int x=0;hulksmash(x<1){flashtravel 999;blackwidow{x++;}}announce x;}";
        int exit=JavaRunner.run("test.hero",Main.transpile(ignoredTravel,"test.hero"),new java.io.PrintStream(capture));
        expect(exit==0,"ignored flashtravel does not jump or require flashPoint during execution");
        expect(SpiderSense.analyze("battle{hulksmash(false){hulksmash(false){blackwidow{}}}}","test.hero").stream().anyMatch(i->i.code().equals("HULK_NO_BLACKWIDOW")),"nested regions belong to inner loop");
        var mapped=SpiderSense.analyze("battle {\nint x=\"bad\";\n}","test.hero");
        expect(mapped.stream().anyMatch(i->i.code().equals("JAVA_ERROR")&&i.line()==2),"Java errors map to hero lines");
        Path marker=Path.of("build","spiderSense-must-not-execute.txt");
        expect(!Files.exists(marker),"execution marker initially absent");
        String source="battle {try{java.nio.file.Files.writeString(java.nio.file.Path.of(\"build/spiderSense-must-not-execute.txt\"),\"BAD\");}catch(Exception e){}}";
        expect(SpiderSense.analyze(source,"test.hero").isEmpty()&&!Files.exists(marker),"analysis never executes code");
        expect(SpiderSense.analyze("battle{}","test.hero").isEmpty(),"old diagnostics do not persist");
        String json=SpiderSense.json(List.of(new SpiderSense.Issue("TEST","warning","x.hero",1,1,2,"quote\" slash\\ newline\n")));
        expect(json.contains("quote\\\"")&&json.contains("slash\\\\")&&json.contains("newline\\n"),"JSON escaping");
        for(Path example:Files.list(Path.of("examples")).filter(p->p.toString().endsWith(".hero")).toList()) {
            expect(SpiderSense.analyze(Files.readString(example),example.toString()).stream().noneMatch(i->i.severity().equals("error")),"example diagnostics: "+example);
        }
        System.out.println(checks+" spiderSense checks passed");
    }
}
