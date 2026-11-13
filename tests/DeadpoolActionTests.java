import heroscript.compiler.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.regex.*;

public class DeadpoolActionTests {
    static int passed;
    static void expect(boolean condition,String name) { if(!condition)throw new AssertionError(name);passed++; }
    static String prepare(String source) {
        Matcher matcher=Pattern.compile("@([A-Z]+)@").matcher(source);StringBuffer result=new StringBuffer();
        while(matcher.find()) {
            int line=-1;String[] lines=source.split("\n",-1);
            for(int i=0;i<lines.length;i++)if(lines[i].contains("// "+matcher.group(1))){line=i+1;break;}
            if(line<0)throw new AssertionError("missing target");matcher.appendReplacement(result,Integer.toString(line));
        }
        matcher.appendTail(result);return result.toString();
    }
    static boolean valid(String source) {
        try { var out=Main.transpile(prepare(source),"deadpool_test.hero");return JavaRunner.validate("deadpool_test.hero",out,new PrintStream(OutputStream.nullOutputStream())); }
        catch(CompileError error){return false;}
    }
    static String run(String source) throws Exception {
        Path dir=Files.createTempDirectory("hero deadpool tests ");Path file=dir.resolve("deadpool_test.hero");Process process=null;
        try {
            Files.writeString(file,prepare(source),StandardCharsets.UTF_8);
            process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",System.getProperty("java.class.path"),"heroscript.compiler.Main","run",file.toString()).redirectErrorStream(true).start();
            if(!process.waitFor(12,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor();throw new AssertionError("program did not terminate");}
            return new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n")+"[exit "+process.exitValue()+"]";
        } finally {
            if(process!=null&&process.isAlive()){process.destroyForcibly();process.waitFor();}
            Files.deleteIfExists(file);Files.deleteIfExists(dir);
        }
    }
    public static void main(String[] args) throws Exception {
        Method choose=heroscript.runtime.DeadpoolAction.class.getDeclaredMethod("choose",int.class,IntSupplier.class);choose.setAccessible(true);
        for(int chance:new int[]{0,25,50,100}) {
            int accepted=0;AtomicInteger calls=new AtomicInteger();
            for(int value=0;value<100;value++) {
                final int draw=value;IntSupplier supplier=()->{calls.incrementAndGet();return draw;};
                if((boolean)choose.invoke(null,chance,supplier))accepted++;
            }
            expect(accepted==chance,"exact percentage boundary "+chance);
            expect(calls.get()==100,"fresh draw including endpoints "+chance);
        }
        AtomicInteger entries=new AtomicInteger();IntSupplier sequence=()->entries.getAndIncrement()==0?90:10;
        expect(!(boolean)choose.invoke(null,50,sequence)&&(boolean)choose.invoke(null,50,sequence)&&entries.get()==2,"new draw on every runtime entry without cache");
        expect(!heroscript.runtime.DeadpoolAction.choose(0)&&heroscript.runtime.DeadpoolAction.choose(100),"public runtime endpoint behavior");
        String result=run("battle { int x=0; deadpoolAction(chance=0) { x++; throw new IllegalStateException(); } announce x; deadpoolAction(chance=100) { x++; } announce x; }");
        expect(result.contains(">> 0\n>> 1\n[exit 0]"),"skip and execute with normal continuation");
        result=run("battle { int x=0; for(int i=0;i<5;i++) { deadpoolAction(chance=100) { x++; } } announce x; }");
        expect(result.contains(">> 5\n[exit 0]"),"every loop entry executes independent decision");
        expect(run("battle { deadpoolAction(chance=0) { announce \"NEVER\"; } announce \"done\"; }").equals(">> done\n[exit 0]"),"skip is not an error and emits no automatic log");
        result=run("battle { try { deadpoolAction(chance=100) { throw new IllegalStateException(); } } catch(IllegalStateException e) { announce \"caught\"; } }");
        expect(result.contains(">> caught\n[exit 0]"),"real exceptions propagate");
        result=run("battle { deadpoolAction(chance=100) { throw new HeroError(\"RealError\",\"test\"); } }");
        expect(result.contains("HeroError: RealError")&&result.endsWith("[exit 1]"),"uncaught HeroError not swallowed");
        result=run("hero H { public int read() { deadpoolAction(chance=100) { return 5; } return 8; } } battle { summon H as h; announce h.read(); }");
        expect(result.contains(">> 5\n[exit 0]"),"return retains method behavior");
        result=run("battle { int x=0; for(int i=0;i<4;i++){deadpoolAction(chance=100){if(i==1){continue;}if(i==3){break;}}x++;}announce x; }");
        expect(result.contains(">> 2\n[exit 0]"),"break and continue keep ordinary loop behavior");
        result=run("battle { deadpoolAction(chance=0) { deadpoolAction(chance=100) { announce \"NEVER\"; } } deadpoolAction(chance=100) { deadpoolAction(chance=0) { announce \"NEVER\"; } announce \"outer\"; } }");
        expect(result.equals(">> outer\n[exit 0]"),"nested decisions only reached after outer decision");
        expect(valid("battle { deadpoolAction(chance=50) {} }"),"ordinary chance accepted without seed");
        expect(!valid("battle { deadpoolAction(chance=0) { int x=\"bad\"; } }"),"ignored code is still type checked");
        for(String source:new String[]{
            "battle { deadpoolAction() {} }", "battle { deadpoolAction(chance=-1) {} }",
            "battle { deadpoolAction(chance=101) {} }", "battle { deadpoolAction(chance=50.5) {} }",
            "battle { deadpoolAction(chance=9999999999999999) {} }", "battle { int x=50; deadpoolAction(chance=x) {} }",
            "battle { deadpoolAction(chance=50,seed=42) {} }", "battle { deadpoolAction(chance=50,chance=30) {} }",
            "battle { deadpoolAction(seed=42) {} }", "battle { deadpoolAction(chance=50); }",
            "battle { deadpoolAction(chance=50) { flashtravel 1; } }",
            "battle { deadpoolAction(chance=0) { if(true) { flashtravel 1; } } }",
            "battle { deadpoolAction(chance=100) { flashPoint { flashtravel 1; } } }",
            "battle { flashPoint { deadpoolAction(chance=50) { flashtravel 1; } } }",
            "battle { deadpoolAction(chance=100) { int local=1; } announce local; }"
        })expect(!valid(source),"invalid arguments or forbidden jump: "+source);
        String reentry="""
            battle {
                flashPoint {
                    int passes=0;
                    int hits=0;
                    deadpoolAction(chance=100) { // ENTRY
                        hits++;
                    }
                    passes++;
                    if(passes<3) { flashtravel @ENTRY@; }
                    announce hits;
                }
            }
            """;
        expect(run(reentry).contains(">> 3\n[exit 0]"),"jumping to opening repeats decision and executes body");
        expect(run(reentry.replace("chance=100","chance=0")).contains(">> 0\n[exit 0]"),"opening cannot bypass zero chance");
        String incoming="""
            battle {
                flashPoint {
                    flashtravel @BODY@;
                    deadpoolAction(chance=0) {
                        announce "NEVER"; // BODY
                    }
                }
            }
            """;
        expect(!valid(incoming),"direct incoming jump to body rejected");
        expect(!valid(incoming.replace("announce \"NEVER\"; // BODY","for(int i=0;i<1;i++) { announce i; } // BODY")),"incoming jump to for header rejected");
        expect(!valid(incoming.replace("announce \"NEVER\"; // BODY","if(true) { announce 1; } // BODY")),"incoming jump to nested control rejected");
        String sameLine="""
            battle {
                flashPoint {
                    flashtravel @ENTRY@;
                    deadpoolAction(chance=0) { announce "NEVER"; } // ENTRY
                    announce "done";
                }
            }
            """;
        expect(run(sameLine).equals(">> done\n[exit 0]"),"opening and body sharing a line still require decision");
        String closing="""
            battle {
                flashPoint {
                    flashtravel @BODY@;
                    deadpoolAction(chance=0) {
                        announce "NEVER"; } announce "after"; // BODY
                }
            }
            """;
        expect(!valid(closing),"mixed interior and exterior line cannot silently change destination");
        expect(run("battle { deadpoolAction(chance=100) { flashPoint { announce 1; } } }").contains(">> 1\n[exit 0]"),"flashPoint without jumps may execute inside selected block");
        result=run("battle { flashPoint { int x=0; for(int i=0;i<4;i++) { deadpoolAction(chance=100) { if(i==1){continue;}if(i==3){break;} } x++; } announce x; } }");
        expect(result.contains(">> 2\n[exit 0]"),"state-machine loop control through selected block");
        result=run("hero H { public int work() { deadpoolAction(chance=100) { return 5; } return 8; } mission Inspect { supervision.getMethod(work); } } battle { summon H as h; h.Inspect(); }");
        expect(result.contains("deadpoolAction(chance = 100)")&&result.contains("return 5 ;")&&!result.contains(">> 5"),"supervision describes without executing decision or body");
        result=run("hero H { public void work() { deadpoolAction(chance=100) { secret int HIDDEN=123456; announce HIDDEN; } } mission Inspect { supervision.getMethod(work); } } battle { summon H as h; h.Inspect(); }");
        expect(!result.contains("HIDDEN")&&!result.contains("123456")&&result.contains("partial analysis"),"supervision filtering preserved inside block");
        String searchable="hero H { mission Read { deadpoolAction(chance=50) { kryptonite int reserve=1; secret int hidden=2; } } }";
        expect(Investigator.investigate(searchable,"test.hero","reserve").code()==0&&Investigator.investigate(searchable,"test.hero","hidden").code()==1,"investigation traverses conditional block with secret filtering");
        result=run("batcave C { public void work() { deadpoolAction(chance=100) { int value=1; supervision.getValue(value); } } } battle { new C().work(); int visible=7; supervision.getValue(visible); }");
        expect(result.contains("visible = 7")&&!result.contains("value = 1"),"batcave suppression unchanged");
        result=run("hero H { secret public void work() { deadpoolAction(chance=100) { throw new IllegalStateException(); } } } battle { summon H as h; try { h.work(); } catch(IllegalStateException e) {} int visible=8; supervision.getValue(visible); }");
        expect(result.contains("visible = 8"),"secret cleanup restored after exception");
        result=run("hero H { secret int runtime=1 allow Work; mission Work { flashPoint { deadpoolAction(chance=100) { announce 1; } } } } battle { summon H as h; h.Work(); }");
        expect(result.contains(">> 1\n[exit 0]"),"generated decision is not rewritten as a secret member reference");
        System.out.println(passed+" deadpoolAction checks passed");
    }
}
