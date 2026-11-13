import heroscript.compiler.*;
import heroscript.runtime.HeroError;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class BatplanTests {
    static int passed;
    static void expect(boolean value,String name){if(!value)throw new AssertionError(name);passed++;}
    static String run(String source) {
        source=prepare(source);
        var buffer=new ByteArrayOutputStream();var console=new PrintStream(buffer,true,StandardCharsets.UTF_8);var original=System.out;int exit;
        try{System.setOut(console);exit=JavaRunner.run("batplan_test.hero",Main.transpile(source,"batplan_test.hero"),console);}
        finally{System.setOut(original);}
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n","\n")+"[exit "+exit+"]";
    }
    static boolean valid(String source) {
        try{return JavaRunner.validate("batplan_test.hero",Main.transpile(prepare(source),"batplan_test.hero"),new PrintStream(OutputStream.nullOutputStream()));}
        catch(CompileError e){return false;}
    }
    static String attempt(String operation){return "try { "+operation+" } catch(HeroError e) { announce e.kind(); }";}
    static String prepare(String source) {
        Matcher matcher=Pattern.compile("@([A-Z]+)@").matcher(source);StringBuffer result=new StringBuffer();
        while(matcher.find()) {
            int destination=-1;String[] lines=source.split("\n",-1);
            for(int i=0;i<lines.length;i++)if(lines[i].contains("// "+matcher.group(1))){destination=i+1;break;}
            if(destination<0)throw new AssertionError("missing test destination");matcher.appendReplacement(result,Integer.toString(destination));
        }
        matcher.appendTail(result);return result.toString();
    }
    public static void main(String[] args) {
        String actor="hero H { int value=7; public int read(){return value;} mission Ping {announce name;} } ";
        String result=run(actor+"battle { summon H as original; H copy=original; summon H as other; batplan(original); "+attempt("announce copy.read();")+" announce other.read(); }");
        expect(result.contains(">> ObjectInactive\n>> 7\n[exit 0]"),"all aliases inactive while unrelated object remains active: "+result);
        result=run(actor+"battle { summon H as h; H copy=h; batplan(h); "+attempt("announce copy.value;")+" }");
        expect(result.contains("ObjectInactive")&&!result.contains(">> 7"),"direct field access through alias blocked: "+result);
        result=run(actor+"battle { summon H as h; batplan(h); "+attempt("h.Ping();")+" }");
        expect(result.contains("ObjectInactive")&&!result.contains("inicia a missão"),"inactive mission blocked before entry logging: "+result);
        result=run("battle { Object original=new Object(); Object copy=original; Object other=new Object(); batplan(original); "+attempt("announce copy.toString();")+" announce other!=null; }");
        expect(result.contains(">> ObjectInactive\n>> true\n[exit 0]"),"ordinary Java object aliases: "+result);
        result=run("battle { java.util.ArrayList<Object> list=new java.util.ArrayList<>(); Object first=new Object(); Object middle=new Object(); Object last=new Object(); list.add(first);list.add(middle);list.add(last);batplan(middle);announce list.size();announce list.get(0)==first;announce list.get(2)==last; "+attempt("announce list.get(1).toString();")+" }");
        expect(result.contains(">> 3\n>> true\n>> true\n>> ObjectInactive\n[exit 0]"),"collection keeps size order and unaffected elements: "+result);
        result=run("battle { java.util.ArrayList<String> list=new java.util.ArrayList<>();java.util.List<String> alias=list;batplan(list); "+attempt("alias.clear();")+" }");
        expect(result.contains("ObjectInactive"),"generic interface alias blocked: "+result);
        result=run(actor+"battle { H[] refs=new H[1];summon H as h;refs[0]=h;batplan(h); "+attempt("announce refs[0].value;")+" }");
        expect(result.contains("ObjectInactive"),"array-held object reference blocked: "+result);
        result=run("battle { int[] array=new int[2];int[] alias=array;batplan(array); "+attempt("announce alias[0];")+" }");
        expect(result.contains("ObjectInactive"),"deactivated array alias blocked: "+result);
        result=run("battle { String text=new String(\"value\");String alias=text;batplan(text); "+attempt("announce alias.length();")+" }");
        expect(result.contains("ObjectInactive"),"String identity: "+result);
        result=run("battle { Integer boxed=Integer.valueOf(1);batplan(boxed);announce 1;int value=1;announce value; "+attempt("announce boxed.intValue();")+" }");
        expect(result.contains(">> 1\n>> 1\n>> ObjectInactive\n[exit 0]"),"boxed identity never deactivates primitive values: "+result);
        result=run(actor+"battle { summon H as h; H old=h;batplan(h);h=new H();announce h.read(); "+attempt("announce old.read();")+" }");
        expect(result.contains(">> 7\n>> ObjectInactive\n[exit 0]"),"new instance does not reactivate old aliases: "+result);
        result=run("battle { Object value=null; "+attempt("batplan(value);")+" }");
        expect(result.contains("BatplanNullTarget"),"null target has clear error: "+result);
        result=run("battle { Object object=new Object();Object copy=object;batplan(object);batplan(copy);announce \"done\"; }");
        expect(result.contains(">> done\n[exit 0]"),"deactivation is idempotent: "+result);
        for(String source:List.of("battle {int value=1;batplan(value);}","battle {batplan(missing);}","battle {batplan(5);}","battle {batplan();}","hero H{mission Read{}}battle{batplan(Read);}","battle {var value=new Object();batplan(value);}","battle {Object object=new Object();batplan(object,cleanup);}"))expect(!valid(source),"invalid target rejected");
        expect(valid("battle {Object object=new Object();batplan(object);}"),"explicit object target accepted");
        Object first=new Object(),second=new Object();
        heroscript.runtime.Batplan.deactivate(first);
        expect(!heroscript.runtime.Batplan.isActive(first)&&heroscript.runtime.Batplan.isActive(second),"identity registry");
        class EqualObjects {public boolean equals(Object other){throw new AssertionError("equals invoked");}public int hashCode(){throw new AssertionError("hashCode invoked");}}
        Object equalFirst=new EqualObjects(),equalSecond=new EqualObjects();
        heroscript.runtime.Batplan.deactivate(equalFirst);
        expect(heroscript.runtime.Batplan.isActive(equalSecond),"user equals/hashCode never used by tombstones");
        result=run("batcave C { public int read(){return 8;} } battle { C first=new C();C alias=first;C other=new C();batplan(first); "+attempt("announce alias.read();")+" announce other.read(); }");
        expect(result.contains(">> ObjectInactive\n>> 8\n[exit 0]"),"batcave object deactivation keeps other instance alive: "+result);
        result=run(actor+"battle { summon H as h;H alias=h;batplan(h); "+attempt("alias.value=3;")+" }");
        expect(result.contains("ObjectInactive"),"inactive field writes blocked: "+result);
        result=run("battle {String text=new String(\"x\");batplan(text); "+attempt("text+=\"y\";")+" }");
        expect(result.contains("ObjectInactive"),"compound reference mutation checks old value: "+result);
        result=run("battle {Integer boxed=Integer.valueOf(2);batplan(boxed); "+attempt("boxed++;")+" }");
        expect(result.contains("ObjectInactive"),"boxed mutation checks old object: "+result);
        result=run("hero H {public int read(){return 7;}public void retire(H other){batplan(this);announce \"finishing\";announce other.read();}} battle {summon H as h;summon H as other;h.retire(other);"+attempt("announce h.read();")+"}");
        expect(result.contains(">> finishing\n>> 7\n>> ObjectInactive\n[exit 0]"),"entered method may finish using locals and unrelated objects: "+result);
        result=run("hero H {int value=7;public void retire(){batplan(this);announce value;}} battle {summon H as h;"+attempt("h.retire();")+"}");
        expect(result.contains("ObjectInactive")&&!result.contains(">> 7"),"implicit self field read blocked after self deactivation: "+result);
        result=run("hero H {public void retire(){try{batplan(this);}finally{announce \"cleanup\";}}} battle {summon H as h;h.retire();"+attempt("h.retire();")+"}");
        expect(result.contains(">> cleanup\n>> ObjectInactive\n[exit 0]"),"deactivation does not skip finally: "+result);
        String backwards=actor+"""
            battle {
                summon H as h;
                int visits=0;
                try {
                    flashPoint {
                        visits++; // AGAIN
                        if(visits==1){batplan(h);flashtravel @AGAIN@;}
                        announce h.read();
                    }
                }catch(HeroError e){announce e.kind();}
            }
            """;
        expect(run(backwards).contains(">> ObjectInactive\n[exit 0]"),"flashtravel never reactivates old object");
        String recreated=actor+"""
            battle {
                H old=null;
                int visits=0;
                flashPoint {
                    summon H as h; // CREATE
                    visits++;
                    if(visits==1){old=h;batplan(h);flashtravel @CREATE@;}
                    announce h.read();
                }
                try{announce old.read();}catch(HeroError e){announce e.kind();}
            }
            """;
        result=run(recreated);
        expect(result.contains(">> 7\n>> ObjectInactive\n[exit 0]"),"summon executed again creates independent identity: "+result);
        result=run("battle {Object h=new Object();deadpoolAction(chance=0){batplan(h);}announce h!=null;deadpoolAction(chance=100){batplan(h);}"+attempt("announce h.toString();")+"}");
        expect(result.contains(">> true\n>> ObjectInactive\n[exit 0]"),"deadpool controls whether removal executes: "+result);
        result=run("hero H {} squad Team {members H;} battle {summon H as a;summon H as b;summon Team as team;recruit a into team;recruit b into team;batplan(a);announce team.members().size();announce b.name;}");
        expect(result.contains(">> 1\n>> H\n[exit 0]"),"managed squad excludes inactive members without disabling others: "+result);
        result=run("squad Team {} battle {summon Team as team;Team copy=team;batplan(team);"+attempt("announce copy.members();")+"}");
        expect(result.contains("ObjectInactive"),"squad alias invalidated: "+result);
        result=run("hero H {public static int read(){return 5;}} battle {summon H as h;batplan(h);announce H.read();}");
        expect(result.contains(">> 5\n[exit 0]"),"object removal does not remove class static methods: "+result);
        result=run("hero H {secret Object child=new Object() allow Read;mission Read{batplan(child);"+attempt("announce \"child=\"+child;")+"}} battle{summon H as h;h.Read();}");
        expect(result.contains("ObjectInactive")&&!result.contains("child=java"),"secret getter preserves permission and child lifecycle: "+result);
        result=run("hero H {secret Object child=new Object() allow Read;mission Read{} mission Denied{batplan(child);}}battle{summon H as h;"+attempt("h.Denied();")+"}");
        expect(result.contains("SecretAccessDenied"),"batplan does not bypass secret allow: "+result);
        result=run("hero H {public int read(){return 7;}}battle{java.util.ArrayList<H> list=new java.util.ArrayList<>();summon H as h;list.add(h);batplan(h);"+attempt("announce \"value=\"+list.get(0);")+"}");
        expect(result.contains("ObjectInactive")&&!result.contains("value=H"),"computed reference in concatenation checked before formatting: "+result);
        result=run("battle {Object[] refs=new Object[1];Object h=new Object();refs[0]=h;batplan(h);"+attempt("announce \"value=\"+refs[0];")+"}");
        expect(result.contains("ObjectInactive")&&!result.contains("value=java"),"array value in concatenation checked: "+result);
        result=run("battle {Object h=new Object();batplan(h);"+attempt("Object alias=h;")+"announce \"done\";}");
        expect(result.contains(">> ObjectInactive\n>> done\n[exit 0]"),"inactive reference cannot become a new active alias: "+result);
        result=run("hero H {int calls=0;Object item=new Object();public Object next(){calls++;return item;}}battle{summon H as h;Object item=h.next();batplan(item);"+attempt("announce h.next().toString();")+"announce h.calls;}");
        expect(result.contains(">> ObjectInactive\n>> 2\n[exit 0]"),"receiver evaluation not duplicated: "+result);
        result=run("battle {Object h=new Object();batplan(h);"+attempt("h.toString();")+"announce new java.util.ArrayList<String>().size();}");
        expect(result.contains(">> ObjectInactive\n>> 0\n[exit 0]"),"qualified generic constructor chain remains valid: "+result);
        expect(valid("battle {int a=1,b=2;Object h=new Object();batplan(h);announce a+b;}"),"multiple primitive declarations unaffected");
        expect(valid("hero H {public void work(java.util.List<Object> list){batplan(list);}}"),"generic parameter target supported");
        expect(valid("hero H {public void work(Object[] array){batplan(array);}}"),"array parameter target supported");
        result=run("battle {String text=\"session value\";batplan(text);}");
        expect(result.endsWith("[exit 0]"),"first execution completes with inactive interned reference");
        result=run("battle {String text=\"session value\";supervision.getValue(text);}");
        expect(result.contains("text = session value")&&result.endsWith("[exit 0]"),"independent execution does not inherit inactive objects");
        result=run("hero H {public void remove(H target){batplan(target);} mission Inspect{supervision.getMethod(remove);}}battle{summon H as h;h.Inspect();announce h.status();}");
        expect(result.contains("batplan(target);")&&result.contains(">> H [HP"),"supervision shows operation without executing it: "+result);
        expect(Investigator.investigate("hero H {public void remove(H target){batplan(target);}}","test.hero","remove").code()==0,"investigation still locates declaration");
        expect(!valid("battle {Object h=new Object();batplan(h);Runnable job=()->h.toString();}"),"unsupported lambda cannot bypass lifecycle instrumentation");
        expect(!valid("battle {Object h=new Object();batplan(h);Runnable job=h::toString;}"),"unsupported method reference rejected");
        expect(!valid("battle {Object h=new Object();batplan(h);Object other=new Object(){public String toString(){return \"x\";}};}"),"unsupported anonymous implementation rejected");
        result=run("battle {int[][] matrix=new int[1][1];int[] row=matrix[0];batplan(row);"+attempt("announce matrix[0][0];")+"}");
        expect(result.contains("ObjectInactive"),"multidimensional array identity and creation remain valid: "+result);
        result=run("hero H {Integer count=Integer.valueOf(3);}battle{summon H as h;Integer child=h.count;batplan(child);"+attempt("h.count++;")+"}");
        expect(result.contains("ObjectInactive"),"reference field compound mutation checks stored object: "+result);
        expect(!valid("battle {Integer count=1;Object h=new Object();batplan(h);for(;count<2;count++){} }"),"unsafe reference mutation in header rejected");
        expect(!valid("battle {Integer count=1;Object h=new Object();batplan(h);announce count++;}"),"unsafe reference mutation in value expression rejected");
        System.out.println(passed+" batplan checks passed");
    }
}
