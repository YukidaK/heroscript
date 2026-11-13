import heroscript.compiler.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Cross-feature acceptance checks: editor/compiler parity and combined runtime behavior. */
public class ValidationTests {
    static int checks;
    static void expect(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    static boolean check(String source) {
        try {return JavaRunner.validate("validation.hero",Main.transpile(source,"validation.hero"),
                new PrintStream(OutputStream.nullOutputStream()));}
        catch(CompileError error){return false;}
    }
    static void parity(String source,boolean expected) {
        boolean accepted=check(source);
        boolean editorAccepted=SpiderSense.analyze(source,"validation.hero").stream().noneMatch(i->i.severity().equals("error"));
        expect(accepted==expected&&editorAccepted==accepted,"editor/check parity: "+source);
    }
    public static void main(String[] args) throws Exception {
        try(var files=Files.list(Path.of("examples"))) {
            for(Path example:files.filter(p->p.toString().endsWith(".hero")).toList())parity(Files.readString(example),true);
        }
        for(String source:List.of(
            "battle{blackwidow{}}", "battle{hulkbuster;}",
            "battle{secret kryptonite int x=1;}", "battle{supervision.getValue(absent);}",
            "battle{flashPoint{flashtravel 999;}}", "battle{deadpoolAction(chance=101){}}",
            "battle{int x=1;batplan(x);}", "batcave C{villain V{}}", "battle{int x=\"bad\";}",
            "battle{hulksmash(false){blackwidow{flashtravel 999;}}}",
            "battle{flashPoint{deadpoolAction(chance=0){flashtravel 1;}}}"
        ))parity(source,false);
        parity("battle{hulksmash(false){flashtravel 999;hulkbuster;}}",true);
        parity("battle{int x=0;hulksmash(x++<1){}}",true);
        String combined="""
            batcave Vault {
                secret int pin=42;
                public int read(){supervision.getValue(pin);return pin;}
            }
            hero Worker {
                public int read(){return health;}
            }
            battle {
                Vault vault=new Vault();
                announce vault.read();
                summon Worker as original;
                Worker alias=original;
                int rounds=0;
                flashPoint {
                    int localRounds=0;
                    hulksmash(localRounds<5) {
                        flashtravel 999;
                        if(false) {
                            blackwidow {
                                deadpoolAction(chance=100){localRounds++;}
                                if(localRounds==2){hulkbuster;}
                            }
                        }
                    }
                    announce localRounds;
                }
                batplan(original);
                try{announce alias.read();}catch(HeroError error){announce error.kind();}
                original=new Worker();
                announce original.read();
                kryptonite String protectedValue="PRIVATE_LITERAL";
                supervision.getValue(protectedValue);
                announce "done";
            }
            """;
        parity(combined,true);
        var diagnostics=SpiderSense.analyze(combined,"validation.hero");
        expect(diagnostics.size()==1&&diagnostics.get(0).code().equals("FLASHTRAVEL_IGNORED"),"combined warning classification");
        PrintStream previous=System.out;
        var bytes=new ByteArrayOutputStream();
        try(var output=new PrintStream(bytes,true,StandardCharsets.UTF_8)) {
            System.setOut(output);
            expect(JavaRunner.run("validation.hero",Main.transpile(combined,"validation.hero"),output)==0,"combined runtime exit");
        }finally{System.setOut(previous);}
        String result=bytes.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
        expect(result.startsWith(">> 42\n>> 2\n>> ObjectInactive\n>> 100\n"),"combined execution order and identity: "+result);
        expect(!result.contains("PRIVATE_LITERAL")&&!result.contains("[supervision.getValue] pin"),"observation protection retained");
        expect(result.contains(">> done\n"),"execution continues after caught inactive alias");
        System.out.println(checks+" cross-feature validation checks passed");
    }
}
