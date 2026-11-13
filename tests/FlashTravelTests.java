import heroscript.compiler.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

public class FlashTravelTests {
    static int passed;
    static void expect(boolean value,String name) { if(!value)throw new AssertionError(name);passed++; }
    static String prepare(String source) {
        Matcher matcher=Pattern.compile("@([A-Z]+)@").matcher(source);
        StringBuffer result=new StringBuffer();
        while(matcher.find()) {
            String marker="// "+matcher.group(1); int found=-1;
            String[] lines=source.split("\n",-1);
            for(int i=0;i<lines.length;i++)if(lines[i].contains(marker)) {found=i+1;break;}
            if(found<0)throw new AssertionError("missing test destination");
            matcher.appendReplacement(result,Integer.toString(found));
        }
        matcher.appendTail(result);return result.toString();
    }
    static boolean valid(String source) {
        try {
            var out=Main.transpile(prepare(source),"flash_test.hero");
            return JavaRunner.validate("flash_test.hero",out,new PrintStream(OutputStream.nullOutputStream()));
        } catch(CompileError e) {return false;}
    }
    static String run(String source) throws Exception {
        Path dir=Files.createTempDirectory("hero flash tests ");Path file=dir.resolve("flash_test.hero");
        Process process=null;
        try {
            Files.writeString(file,prepare(source),StandardCharsets.UTF_8);
            process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",System.getProperty("java.class.path"),"heroscript.compiler.Main","run",file.toString()).redirectErrorStream(true).start();
            if(!process.waitFor(12,TimeUnit.SECONDS)) {process.destroyForcibly();process.waitFor();throw new AssertionError("flash program did not terminate");}
            return new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n")+"[exit "+process.exitValue()+"]";
        } finally {
            if(process!=null&&process.isAlive()){process.destroyForcibly();process.waitFor();}
            Files.deleteIfExists(file);Files.deleteIfExists(dir);
        }
    }
    public static void main(String[] args) throws Exception {
        expect(run("battle { flashPoint {} announce \"done\"; }").contains(">> done\n[exit 0]"),"empty region completes");
        String forward="""
            battle {
                flashPoint {
                    flashtravel @END@;
                    announce "SKIPPED";
                    announce "done"; // END
                }
            }
            """;
        String result=run(forward);
        expect(result.contains(">> done")&&!result.contains("SKIPPED"),"forward physical-line jump");
        String backward="""
            battle {
                flashPoint {
                    int count=0;
                    count++; // AGAIN
                    announce count;
                    if(count<3) {
                        flashtravel @AGAIN@;
                    }
                }
            }
            """;
        expect(run(backward).contains(">> 1\n>> 2\n>> 3\n[exit 0]"),"backward jump preserves state");
        String loop="""
            flashPoint {
                int visits=0;
                battle {
                    while(visits<2) {
                        visits++; // RESUME
                        if(visits==1) {
                            flashtravel @OUT@;
                        }
                        announce visits;
                    }
                    flashtravel @END@;
                }
                announce "outside"; // OUT
                flashtravel @RESUME@;
                announce "done"; // END
            }
            """;
        expect(run(loop).contains(">> outside\n>> 2\n>> done\n[exit 0]"),"leave and reenter loop and battle in one region");
        String forLoop="""
            battle {
                flashPoint {
                    int total=0;
                    for(int i=0;i<5;i++) {
                        if(i==1) { continue; }
                        if(i==4) { break; }
                        total+=i;
                    }
                    announce total;
                }
            }
            """;
        expect(run(forLoop).contains(">> 5\n[exit 0]"),"for initialization update break and continue");
        expect(run("battle { flashPoint { int x=2; if(x==0){announce 0;} else if(x==1){announce 1;} else {announce 2;} } }").contains(">> 2\n[exit 0]"),"else-if and else");
        String skip="""
            battle {
                flashPoint {
                    flashtravel @READ@;
                    int x=1;
                    announce x; // READ
                }
            }
            """;
        result=run(skip);
        expect(result.contains("FlashUninitialized")&&result.endsWith("[exit 1]"),"skipped initialization never fabricates a default value");
        String assign="""
            battle {
                flashPoint {
                    flashtravel @ASSIGN@;
                    int x;
                    x=8; // ASSIGN
                    announce x;
                }
            }
            """;
        expect(run(assign).contains(">> 8\n[exit 0]"),"assignment after skipped declaration initializes variable");
        expect(run("battle { int outside=3; flashPoint { outside++; } announce outside; }").contains(">> 4\n[exit 0]"),"enclosing initialized local remains usable");
        expect(run("hero H { public int read() { flashPoint { return 8; } } } battle { summon H as h; announce h.read(); }").contains(">> 8\n[exit 0]"),"return leaves enclosing method");
        result=run("hero H { secret public void read() { flashPoint { int x=1; supervision.getValue(x); } throw new IllegalStateException(); } } battle { summon H as h; try { h.read(); } catch(IllegalStateException e) {} int visible=7; supervision.getValue(visible); }");
        expect(result.contains("visible = 7")&&!result.contains(" x ="),"secret method boundary restored on exception");
        result=run("batcave C { public void read() { flashPoint { int x=1; supervision.getValue(x); } } } battle { new C().read(); }");
        expect(!result.contains("[supervision]")&&result.contains("[exit 0]"),"batcave observation remains suppressed");
        result=run("battle { flashPoint { secret int code=123456; kryptonite int reserve=654321; supervision.getValue(code); supervision.getValue(reserve); } }");
        expect(result.contains("code = [protegido]")&&result.contains("reserve = [protegido]")&&!result.contains("123456")&&!result.contains("654321"),"slot lowering preserves protection and display names");
        expect(run("battle { flashPoint { int x=1; supervision.getValue(x); } }").contains("flash_test.hero:1 x = 1"),"observation preserves source origin");
        expect(run("hero H {} battle { flashPoint { summon H as h; announce h.name; } }").contains(">> H\n[exit 0]"),"summon state persists");
        expect(run("battle { flashPoint { final int x=2; announce x; } }").contains(">> 2\n[exit 0]"),"final local initialization");
        expect(run("battle { try { flashPoint { announce 1; } } finally { announce 2; } }").contains(">> 1\n>> 2\n[exit 0]"),"enclosing finally retained");
        expect(run("battle { flashPoint { announce 1; } flashPoint { announce 2; } }").contains(">> 1\n>> 2\n[exit 0]"),"independent regions");
        for(String source:new String[] {
            "battle { flashtravel 1; }",
            "battle { flashPoint { flashtravel 0; } }",
            "battle { flashPoint { flashtravel -1; } }",
            "battle { flashPoint { flashtravel 1.5; } }",
            "battle { flashPoint { flashtravel missing; } }",
            "battle { flashPoint { flashtravel 2147483648; } }",
            "battle { flashPoint { flashtravel 999; } }",
            "battle { flashPoint { flashPoint {} } }",
            "battle { flashPoint { try { announce 1; } finally {} } }",
            "battle { flashPoint { switch(1) { default: break; } } }",
            "battle { flashPoint { break; } }",
            "battle { flashPoint { continue; } }",
            "battle { flashPoint { var x=1; } }",
            "battle { flashPoint { final int x=1; x++; } }",
            "battle { flashPoint { int x=1,y=2; } }",
            "battle { int x=1; flashPoint { int x=2; } }",
            "battle { flashPoint { int x=1; { int x=2; } } }",
            "flashPoint { battle {} battle {} }",
            "battle {} flashPoint {}",
            "battle { flashPoint { battle {} } }"
        })expect(!valid(source),"reject invalid jump or unsupported flow: "+source);
        expect(!valid("battle {\n flashPoint {\n  flashtravel 4;\n  // no instruction\n }\n}"),"comment line is not destination");
        expect(!valid("battle {\n flashPoint {\n  flashtravel 4;\n }\n}"),"closing brace is not destination");
        expect(!valid("battle {\n flashPoint { flashtravel 3; }\n announce 1;\n}"),"jump cannot escape region");
        expect(!valid("battle {\n flashPoint { flashtravel 4; }\n flashPoint {\n announce 1;\n }\n}"),"jump cannot enter separate region");
        result=run("battle { flashPoint { int total=0; for(int i=0;i<2;i++){int each=1;total+=each;} announce total; } }");
        expect(result.contains(">> 2\n[exit 0]"),"loop locals reinitialize normally");
        expect(valid("battle { flashPoint { int x=1; } flashPoint { int x=2; } }"),"separate regions have separate locals");
        String forReentry="""
            battle {
                flashPoint {
                    int total=0;
                    boolean left=false;
                    for(int i=0;i<3;i++) {
                        total+=i; // RESUME
                        if(!left) { left=true; flashtravel @OUT@; }
                    }
                    flashtravel @END@;
                    announce "outside"; // OUT
                    flashtravel @RESUME@;
                    announce total; // END
                }
            }
            """;
        expect(run(forReentry).contains(">> outside\n>> 3\n[exit 0]"),"reenter for body without resetting counter");
        String multiline="""
            battle {
                flashPoint {
                    flashtravel @DECL@;
                    announce "skipped";
                    int // DECL
                    value=6;
                    announce value;
                }
            }
            """;
        expect(run(multiline).contains(">> 6\n[exit 0]"),"physical target is declaration start including multiline declaration");
        expect(!valid("hero H {} villain V {} squad S {members H;} battle { flashPoint { summon V as v; summon S as s; recruit v into s; } }"),"summon lowering preserves villain validation");
        result=run("battle { try { flashPoint { throw new IllegalStateException(); } } catch(IllegalStateException e) { announce \"caught\"; } finally { announce \"cleaned\"; } }");
        expect(result.contains(">> caught\n>> cleaned\n[exit 0]"),"exception leaves region and executes enclosing cleanup");
        expect(valid("hero H { public int read() { flashPoint { while(true) { return 8; } } } }"),"reachable return analysis does not fabricate region fallthrough");
        expect(run(backward.replace("\n","\r\n")).contains(">> 1\n>> 2\n>> 3\n[exit 0]"),"CRLF retains physical line numbers");
        System.out.println(passed+" flashtravel checks passed");
    }
}
