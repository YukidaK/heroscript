import heroscript.compiler.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class SupervisionTests {
    static int passed;
    static void expect(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
    static JavaGenerator.Output compile(String source) { return Main.transpile(source, "supervision_test.hero"); }
    static String run(String source) {
        var buffer = new ByteArrayOutputStream();
        var console = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        var original = System.out;
        int code;
        try { System.setOut(console); code = JavaRunner.run("supervision_test.hero", compile(source), console); }
        finally { System.setOut(original); }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n") + "[exit " + code + "]";
    }
    static boolean valid(String source) {
        try { return JavaRunner.validate("supervision_test.hero", compile(source), new PrintStream(OutputStream.nullOutputStream())); }
        catch (CompileError e) { return false; }
    }
    static String inspect(String declarations, String owner, String query) {
        String prefix = "hero " + owner + " {";
        String instrumented = declarations.replace(prefix, prefix + " mission SupervisionProbe { supervision.getMethod(" + query + "); } ");
        if (instrumented.equals(declarations)) throw new AssertionError("missing test owner");
        return run(instrumented + " battle { summon " + owner + " as observer; observer.SupervisionProbe(); }");
    }
    public static void main(String[] args) {
        String worker = "hero Worker { int calls=0; public int calculate(int input) { calls++; if(input>0) { return input*2; } return 0; } }";
        String result = run(worker.replace("hero Worker {", "hero Worker { mission Inspect { supervision.getMethod(Worker.calculate); } ") + " battle { summon Worker as w; w.Inspect(); announce w.calls; }");
        expect(result.contains("[supervision.getMethod] Worker.calculate(int)"), "typed method selected");
        expect(result.contains("return input * 2 ;") && result.contains("if ( input > 0 )"), "implementation structure visible");
        expect(result.contains(">> 0\n[exit 0]"), "inspection never invokes selected method");
        expect(!result.contains("partial analysis"), "complete supported method");
        result = inspect(worker, "Worker", "this.calculate");
        expect(result.contains("Worker.calculate(int)"), "receiver variable resolves statically");
        result = run("hero Worker { public int calculate(int x) { return x; } mission Read { supervision.getMethod(calculate); } } battle { summon Worker as w; w.Read(); }");
        expect(result.contains("Worker.calculate(int)"), "unqualified owner resolution");
        result = run("hero Worker { public int read(int input) { return input; } public int read(String input) { return input.length(); } mission Inspect { supervision.getMethod(Worker.read); supervision.getMethod(\"Worker.read(String)\"); } } battle { summon Worker as w; w.Inspect(); }");
        expect(result.contains("ambiguous selection") && result.contains("Worker.read(String) |"), "overloads require explicit selection");
        result = inspect(worker, "Worker", "Worker.calculate(int)");
        expect(result.contains("Worker.calculate(int) |"), "unquoted signature metadata does not invoke method");
        result = inspect("hero H { mission Task { int x=1; announce x; } }", "H", "H.Task");
        expect(result.contains("mission Task()") && result.contains("announce x;"), "mission inspection");

        String secrets = "hero H { secret String HIDDEN_FIELD=\"FIELD_SENTINEL\" allow Read; secret public int HIDDEN_METHOD() { return 123; } public void inspectable() { announce HIDDEN_FIELD; HIDDEN_METHOD(); int visible=7; announce visible; secret String HIDDEN_LOCAL=\"LOCAL_SENTINEL\"; announce HIDDEN_LOCAL; } mission Read {} }";
        result = inspect(secrets, "H", "H.inspectable");
        expect(!result.contains("HIDDEN_FIELD") && !result.contains("FIELD_SENTINEL") && !result.contains("HIDDEN_METHOD") && !result.contains("HIDDEN_LOCAL") && !result.contains("LOCAL_SENTINEL"), "protected declarations and dependencies never printed");
        expect(result.contains("partial analysis") && result.contains("int visible = 7 ;") && result.contains("announce visible;"), "filtered report keeps public operations");
        String hidden = inspect(secrets, "H", "H.HIDDEN_METHOD");
        expect(hidden.equals(inspect(secrets, "H", "H.missing")), "secret and nonexistent methods indistinguishable");
        result = run("batcave Vault { public int read() { return 123456; } } battle { supervision.getMethod(Vault.read); }");
        expect(result.contains("method unavailable") && !result.contains("123456") && !result.contains("Vault.read"), "batcave implementation unavailable");
        result = inspect("hero H { public int read() { kryptonite int reserve=987654; reserve++; return reserve; } }", "H", "H.read");
        expect(result.contains("partial analysis") && !result.contains("987654") && !result.contains("reserve"), "kryptonite initializers and affected operations filtered");
        result = inspect("hero H { secret int identity=1 allow Read; public int read(int identity) { return identity; } mission Read {} }", "H", "H.read");
        expect(result.contains("return identity ;") && !result.contains("partial analysis"), "parameter shadowing resolves independently of secret field");
        result = inspect("hero H { secret int value=1; public int read() { int value=2; return value; } }", "H", "H.read");
        expect(result.contains("int value = 2 ;") && !result.contains("partial analysis"), "ordinary local shadows secret field");
        result = inspect("hero SecretOwner { secret int code=1; } hero PublicOwner { int code=2; public int read() { return code; } }", "PublicOwner", "PublicOwner.read");
        expect(result.contains("return code ;") && !result.contains("partial analysis"), "unrelated public namesake remains visible");
        result = inspect("hero H { secret int code=1; public int read(Other other) { return other.code; } } hero Other { int code=2; }", "H", "H.read");
        expect(result.contains("return other . code ;") && !result.contains("partial analysis"), "known external receiver resolves public member");
        result = inspect("hero H { secret int HIDDEN=1; public int read(Other other) { return other.HIDDEN; } } hero Other { secret int HIDDEN=2; }", "H", "H.read");
        expect(result.contains("partial analysis") && !result.contains("other . HIDDEN"), "known secret receiver member filtered");
        result = inspect("hero H { secret int code=1; public int read(Object other) { return ((Other)other).code; } } hero Other { int code=2; }", "H", "H.read");
        expect(result.contains("partial analysis") && !result.contains("other ) . code"), "unresolved potentially protected receiver omitted");
        result = run("hero Public {} hero Holder { secret int identity=77 allow Read; mission Read {} public Holder self() { return this; } } battle { summon Holder as h; Public p=new Public(); announce ((Public)p).hashCode()+h.self().identity; }");
        expect(result.contains("SecretAccessDenied") && result.endsWith("[exit 1]"), "unrelated earlier cast never bypasses legacy secret access");
        result = inspect("hero H { secret int code=1; public String read() { return \"code\"; } }", "H", "H.read");
        expect(result.contains("return \"code\" ;") && !result.contains("partial analysis"), "literal text is not a symbol reference");

        result = run(worker.replace("hero Worker {", "hero Worker { public void inspect() { supervision.getMethod(calculate); } ") + " batcave Vault { public void inspect(Worker w) { w.inspect(); } } battle { summon Worker as w; new Vault().inspect(w); w.inspect(); }");
        expect(result.indexOf("Worker.calculate(int) |") == result.lastIndexOf("Worker.calculate(int) |") && result.contains("Worker.calculate(int) |"), "method inspection suppressed dynamically inside batcave");
        result = run(worker.replace("hero Worker {", "hero Worker { public void inspect() { supervision.getMethod(calculate); } ") + " hero Caller { secret public void inspect(Worker w) { w.inspect(); } } battle { summon Worker as w; summon Caller as c; c.inspect(w); w.inspect(); }");
        expect(result.indexOf("Worker.calculate(int) |") == result.lastIndexOf("Worker.calculate(int) |"), "method inspection suppressed in secret callback");
        result = run("battle {\n int count=1;\n count++;\n supervision.getValue(count);\n }");
        expect(result.contains("supervision_test.hero:2 count = 2"), "value report points to declaration not observation");
        result = run("hero H {\n int count=4;\n mission Read { supervision.getValue(count); }\n } battle { summon H as h; h.Read(); }");
        expect(result.contains("supervision_test.hero:2 count = 4"), "ordinary character field value and origin");
        result = run("battle { secret int code=123456; kryptonite int reserve=654321; supervision.getValue(code); supervision.getValue(reserve); }");
        expect(!result.contains("123456") && !result.contains("654321") && result.contains("[protegido]"), "protected values stay masked");
        expect(!compile("battle { secret int code=1; supervision.getValue(code); }").source().contains("Supervision.getValue(\"supervision_test.hero:1 code"), "protected value not passed to runtime helper");
        expect(!valid("battle { int x=1; smanvision x; }"), "old keyword removed");
        for (String invalid : new String[] {
            "battle { supervision.getValue(); }", "battle { int x=1; supervision.getValue(x++); }",
            "battle { supervision.getValor(x); }", "battle { supervision.getMethod(); }",
            "battle { supervision.getMethod(H.calculate++); }", "battle { supervision.getMethod(\"H.read\", 1); }"
        }) expect(!valid(invalid), "invalid supervision syntax rejected");
        result = inspect("hero H { public int spin() { return spin(); } }", "H", "H.spin");
        expect(result.contains("return spin ( ) ;") && result.contains("[exit 0]"), "recursive implementation inspected without executing or expanding");
        String isolated = "hero Other { public int foreign() { return 7654321; } } hero Local { public int own() { return 7; } mission Check { supervision.getMethod(Other.foreign); supervision.getMethod(\"Other.foreign()\"); Other other=new Other(); supervision.getMethod(other.foreign); supervision.getMethod(foreign); supervision.getMethod(own); } }";
        result = run(isolated + " battle { summon Local as local; local.Check(); }");
        expect(result.contains("Local.own() |") && !result.contains("7654321") && !result.contains("Other.foreign() |"), "foreign class contents inaccessible by every selection form");
        expect(result.split("method unavailable", -1).length - 1 == 4, "no fallback to foreign method by short name");
        result = run(worker + " battle { summon Worker as w; supervision.getMethod(Worker.calculate); supervision.getMethod(w.calculate); supervision.getMethod(calculate); }");
        expect(result.split("method unavailable", -1).length - 1 == 3 && !result.contains("Worker.calculate(int) |"), "battle cannot inspect any class");
        result = inspect("hero Local { public int same() { return 1; } } hero Other { public int same() { return 2; } }", "Local", "same");
        expect(result.contains("Local.same() |") && !result.contains("Other.same"), "foreign namesakes do not cause ambiguity");
        result = run("hero Local { public int read() { return 1; } mission Check { Local alias=this; supervision.getMethod(alias.read); } } battle { summon Local as local; local.Check(); }");
        expect(result.contains("Local.read() |"), "alias of own class remains allowed");
        result = run("hero Other { public int read(){return 1;} } hero Local { public int read(){return 2;} mission Check { Other Local=new Other(); supervision.getMethod(Local.read); } } battle { summon Local as local; local.Check(); }");
        expect(result.contains("method unavailable") && !result.contains("Local.read() |"), "shadowed class name cannot bypass owner restriction");
        expect(!valid("hero Other { int field=7; } hero Local { mission Check { Other other=new Other(); supervision.getValue(other.field); } } battle {}"), "getValue rejects external field selection");
        expect(!valid("hero Other { int field=7; } hero Local { mission Check { supervision.getValue(field); } } battle {}"), "getValue never searches foreign class fields");
        expect(!valid("hero Local { mission First { int privateLocal=7; } mission Second { supervision.getValue(privateLocal); } } battle {}"), "getValue cannot read another method local");
        expect(!valid("battle { { int inner=1; } supervision.getValue(inner); }"), "getValue cannot escape block scope");
        result = run("hero Local { int field=7; mission Check(int input) { int field=9; supervision.getValue(field); supervision.getValue(input); } } battle { summon Local as local; local.Check(5); }");
        expect(result.contains("field = 9") && result.contains("input = 5") && !result.contains("field = 7"), "getValue uses own locals and parameters before fields");
        System.out.println(passed + " supervision checks passed");
    }
}
