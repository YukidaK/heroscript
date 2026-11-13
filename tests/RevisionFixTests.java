import heroscript.compiler.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RevisionFixTests {
    static int checks;
    static void expect(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static String run(String source) {
        PrintStream previous=System.out;
        var bytes=new ByteArrayOutputStream();
        try(var console=new PrintStream(bytes,true,StandardCharsets.UTF_8)) {
            System.setOut(console);
            int exit=JavaRunner.run("revision.hero",Main.transpile(source,"revision.hero"),console);
            console.println("[exit "+exit+"]");
        }finally{System.setOut(previous);}
        return bytes.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    static void denied(String declarations,String action) {
        String source="batcave C{public int value=42; public int read(){return value;} public static int staticRead(){return 42;}} "+declarations+
                " battle{C cave=new C();summon V as v;try{"+action+"}catch(HeroError e){announce e.kind();}announce cave.read();}";
        String output=run(source);
        expect(output.endsWith(">> BatcaveAccessDenied\n>> 42\n[exit 0]\n"),"deny villain then restore ordinary access: "+output);
    }
    public static void main(String[] args) {
        for(String literal:List.of("1e-3","1E+3","1.e-3",".5e+2","0x1.fp-2","0X1P+2","0xff","0b1010","1_000L","1.0f","1.","0x1.ep2")) {
            var issues=SpiderSense.analyze("battle{announce "+literal+";}","literal.hero");
            expect(issues.isEmpty(),"valid Java literal: "+literal+" "+issues);
        }
        expect(run("battle{announce 1e-3;announce 0x1.fp-2;announce 0x1e-3;}").equals(">> 0.001\n>> 0.484375\n>> 27\n[exit 0]\n"),"exponents and hexadecimal subtraction preserve meaning");
        for(String source:List.of("battle{announce \"abc"+'\\',"battle{announce '"+'\\')) {
            var issues=SpiderSense.analyze(source,"literal.hero");
            expect(issues.size()==1&&issues.get(0).code().equals("HERO_ERROR"),"unfinished escape is a located diagnostic");
        }
        expect(SpiderSense.analyze("battle{announce 1e+;}","literal.hero").stream().anyMatch(i->i.severity().equals("error")),"invalid exponent rejected");
        String locals="hero H{public void test(){int first=1, second=2;var inferred=\"hidden value\";java.util.List<String> list=new java.util.ArrayList<>();for(int index=0;index<1;index++){int nested=3;}secret int hidden=1;}}battle{int first=4;}";
        expect(Investigator.investigate(locals,"locals.hero","first").symbols().size()==2,"all common local origins");
        for(String name:List.of("second","inferred","list","index","nested"))expect(Investigator.investigate(locals,"locals.hero",name).code()==0,"local indexed: "+name);
        expect(Investigator.investigate(locals,"locals.hero","hidden").code()==1,"secret local omitted");
        for(String name:List.of("item","problem","array"))expect(Investigator.investigate("battle{int[] array={1};for(int item:array){}try{}catch(Exception problem){}}","locals.hero",name).code()==0,"array, foreach and catch locals: "+name);
        expect(Investigator.investigate("hero H{secret public void work(){int hidden=1;}}","locals.hero","hidden").code()==1,"secret method descendants omitted");
        denied("villain V{public int act(C c){return c.read();}}","announce v.act(cave);");
        denied("villain V{public int act(C c){return c.value;}}","announce v.act(cave);");
        denied("villain V{public void act(C c){c.value=99;}}","v.act(cave);");
        denied("villain V{public int act(){return C.staticRead();}}","announce v.act();");
        denied("villain V{public int act(){C c=new C();return c.read();}}","announce v.act();");
        denied("hero Helper{public int read(C c){return c.value;}} villain V{public int act(C c){Helper h=new Helper();return h.read(c);}}","announce v.act(cave);");
        denied("hero Helper{public int read(C c){return c.read();}} villain V{public int act(C c){Helper h=new Helper();return h.read(c);}}","announce v.act(cave);");
        denied("villain V{public static int act(C c){return c.value;}}","announce V.act(cave);");
        denied("villain V{mission Act(C c){announce c.read();}}","v.Act(cave);");
        String attack="hero H{}villain Enemy{}squad S{members H;combo C{cost=10;damage=20;}}battle{summon H as h;summon H as friend;summon S as s;recruit h into s;try{assemble s.C on friend;}catch(HeroError e){announce e.kind();}announce h.energy;announce friend.health;summon Enemy as target;assemble s.C on target;announce target.health;}";
        String output=run(attack);
        expect(output.contains(">> FriendlyFire\n>> 100\n>> 100\n"),"friendly combo rejected before spending or damage: "+output);
        expect(output.contains(">> 80\n[exit 0]"),"enemy combo still works");
        output=run("batcave C{public int count=0;}villain V{public void act(C[] a){a[0].count++;}}battle{C c=new C();C[] a={c};summon V as v;try{v.act(a);}catch(HeroError e){announce e.kind();}announce c.count;}");
        expect(output.equals(">> BatcaveAccessDenied\n>> 0\n[exit 0]\n"),"array alias cannot mutate cave field");
        System.out.println(checks+" revision fix checks passed");
    }
}
