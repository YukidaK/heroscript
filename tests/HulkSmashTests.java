import heroscript.compiler.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class HulkSmashTests {
    static int checks;
    static void expect(boolean value,String message) {
        checks++;
        if(!value)throw new AssertionError(message);
    }
    static boolean valid(String source) {
        try {
            var output=Main.transpile(source,"hulk_test.hero");
            return JavaRunner.validate("hulk_test.hero",output,new PrintStream(OutputStream.nullOutputStream()));
        } catch(CompileError error) { return false; }
    }
    static String run(String source) throws Exception {
        Path directory=Files.createTempDirectory("hero hulk tests ");
        Path file=directory.resolve("hulk_test.hero");
        Process process=null;
        try {
            Files.writeString(file,source,StandardCharsets.UTF_8);
            process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8",
                    "-cp",System.getProperty("java.class.path"),"heroscript.compiler.Main","run",file.toString())
                    .redirectErrorStream(true).start();
            if(!process.waitFor(12,TimeUnit.SECONDS))throw new AssertionError("program did not terminate");
            return new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n")+"[exit "+process.exitValue()+"]";
        } finally {
            if(process!=null&&process.isAlive()){process.destroyForcibly();process.waitFor();}
            Files.deleteIfExists(file); Files.deleteIfExists(directory);
        }
    }
    static void output(String source,String expected,String reason) throws Exception {
        String actual=run(source);
        expect(actual.equals(expected+"[exit 0]"),reason+": "+actual);
    }
    public static void main(String[] args) throws Exception {
        output("battle { int x=0; hulksmash(x<3){announce \"ignored\"; blackwidow{x++; announce x;}} }",">> 1\n>> 2\n>> 3\n","repeat and ignore ordinary commands");
        output("battle {int x=0;hulksmash(x<3){if(false){blackwidow{x++;}}else{blackwidow{announce x;}}}}",">> 1\n>> 2\n>> 3\n","ignore conditions and visit both branches in source order");
        output("battle {int x=0;hulksmash(x<3){for(int i=0;i<0;i++){blackwidow{x++;}}}announce x;}",">> 3\n","ignore enclosing loop including initializer");
        output("battle {int x=0;hulksmash(x<3){hulkbuster; blackwidow{x++;}}announce x;}",">> 3\n","buster outside widow ignored");
        output("battle {int x=0;hulksmash(true){blackwidow{while(x<10){x++;hulkbuster;}announce \"never\";}blackwidow{announce \"never\";}}announce x;}",">> 1\n","buster exits hulk across ordinary loop and skips later regions");
        output("battle {int x=0;hulksmash(x<3){blackwidow{x++;if(false){hulkbuster;}}}announce x;}",">> 3\n","conditions inside widow respected");
        output("battle {int x=0;hulksmash(false){blackwidow{x++;}}announce x;}",">> 0\n","false skips first iteration");
        output("battle {int x=0;hulksmash(x++<2){}announce x;}",">> 3\n","no widow valid and condition side effects retained");
        output("battle {int x=0;hulksmash(x<2){blackwidow{x++;blackwidow{announce x;}}}}",">> 1\n>> 2\n","nested widow executes once");
        output("battle {int x=0;hulksmash(x<2){blackwidow{hulksmash(true){blackwidow{hulkbuster;}}x++;}}announce x;}",">> 2\n","nested hulk exits nearest hulk");
        output("battle {int x=0;hulksmash(x<2){hulksmash(true){blackwidow{announce \"never\";}}blackwidow{x++;}}announce x;}",">> 2\n","ignored nested hulk does not donate regions");
        output("battle {int x=0;hulksmash(x<3){blackwidow{x++;if(x<3){continue;}announce x;}}}",">> 3\n","ordinary continue reaches hulk condition");
        output("battle {hulksmash(true){blackwidow{try{hulkbuster;}finally{announce \"finally\";}}}announce \"done\";}",">> finally\n>> done\n","finally runs on hulkbuster");
        output("battle {int x=0;hulksmash(x<2){blackwidow{deadpoolAction(chance=0){announce \"never\";}deadpoolAction(chance=100){x++;}}}announce x;}",">> 2\n","deadpool inside executable region retains decision");
        output("battle {Object o=new Object();hulksmash(true){blackwidow{batplan(o);hulkbuster;}}try{announce o;}catch(HeroError e){announce e.kind();}}",">> ObjectInactive\n","batplan instrumentation inside widow");
        output("hero H {public int read(){int x=0;hulksmash(x<2){blackwidow{x++;}}return x;}} battle{summon H as h;announce h.read();}",">> 2\n","method context");
        output("battle{flashPoint{int x=0;hulksmash(x<3){if(false){blackwidow{x++;if(x==2){hulkbuster;}}}}announce x;}}",">> 2\n","hulk in flash state machine");
        output("battle{flashPoint{int x=0;hulksmash(x<3){blackwidow{while(true){x++;hulkbuster;}}}announce x;}}",">> 1\n","flash buster exits hulk across inner ordinary loop");
        output("battle{hulksmash(true){blackwidow{flashPoint{announce \"inside\";hulkbuster;}}}announce \"done\";}",">> inside\n>> done\n","flash region inside hulk can exit surrounding hulk");
        for(String source:new String[]{
                "battle{blackwidow{}}", "battle{hulkbuster;}",
                "battle{if(false){blackwidow{}}}", "battle{deadpoolAction(chance=0){hulkbuster;}}",
                "battle{hulksmash(){}}", "battle{hulksmash(1){}}", "battle{hulksmash(true) blackwidow{}}",
                "battle{hulksmash(false){blackwidow{int x=\"bad\";}}}",
                "battle{hulksmash(false){int x=1;blackwidow{announce x;}}}"
        })expect(!valid(source),"reject invalid scope, condition or executable code: "+source);
        expect(valid("battle{hulksmash(true){}}"),"potential infinite loop accepted without warnings as errors");
        expect(valid("battle{hulksmash(false){int x=\"ignored\";unknown();hulkbuster;}}"),"ignored Java is not emitted or type checked");
        output("battle{try{hulksmash(true){blackwidow{throw new HeroError(\"RealError\",\"test\");}}}catch(HeroError e){announce e.kind();}}",">> RealError\n","exceptions propagate without forced loops");
        output("battle{int x=0;hulksmash(x<2){if((++x)>0){blackwidow{x++;}}}announce x;}",">> 2\n","ignored conditions have no side effects");
        output("battle{int x=0;hulksmash(x<1){blackwidow{int local=4;announce local;x++;}blackwidow{int local=5;announce local;}}}",">> 4\n>> 5\n","separate region scopes");
        output("battle{flashPoint{int x=0;hulksmash(x<2){blackwidow{hulksmash(true){blackwidow{hulkbuster;}}x++;}}announce x;}}",">> 2\n","nested hulk inside flash exits correct hulk");
        output("battle{flashPoint{int x=0;hulksmash(x<2){blackwidow{deadpoolAction(chance=100){x++;if(x==1){hulkbuster;}}}}announce x;}}",">> 1\n","hulkbuster inside deadpool and flash");
        String jump="battle {\nflashPoint {\nint x=0;\nhulksmash(x<3) {\nblackwidow {\nx++;\nflashtravel 10;\n}\n}\nannounce x;\n}\n}";
        output(jump,">> 1\n","flashtravel exits hulk from executable region");
        String enter="battle {\nflashPoint {\nint x=0;\nflashtravel 8;\nhulksmash(x<3) {\nif(false) {\nblackwidow {\nx++;\nif(x==2){hulkbuster;}\n}\n}\n}\nannounce x;\n}\n}";
        output(enter,">> 2\n","flashtravel enters executable region and preserves hulkbuster target");
        expect(!valid(jump.replace("flashtravel 10","flashtravel 9")),"cannot target ignored brace line");
        expect(!valid("battle{hulksmash(false){blackwidow{int local=1;}blackwidow{announce local;}}}"),"region locals do not leak");
        String inspection=run("hero H{public void sample(){hulksmash(false){announce \"ignored\";blackwidow{announce \"active\";hulkbuster;}}}public void inspect(){supervision.getMethod(sample);}}battle{summon H as h;h.inspect();}");
        expect(inspection.contains("hulksmash")&&inspection.contains("blackwidow")&&inspection.contains("hulkbuster")&&!inspection.contains(">> active\n"),"supervision describes structure without running loop");
        System.out.println(checks+" hulksmash checks passed");
    }
}
