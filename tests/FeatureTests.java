import heroscript.compiler.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Integration checks for syntax, javac validation, execution and protected code generation. */
public class FeatureTests {
    static int passed;
    static JavaGenerator.Output transpile(String source) { return Main.transpile(source, "feature.hero"); }
    static boolean valid(String source) {
        try {
            return JavaRunner.validate("feature.hero", transpile(source), new PrintStream(new ByteArrayOutputStream()));
        } catch (CompileError e) { return false; }
    }
    static void expect(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
    static String run(String source) {
        var buffer = new ByteArrayOutputStream();
        var console = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        var original = System.out;
        int code;
        try {
            System.setOut(console);
            code = JavaRunner.run("feature.hero", transpile(source), console);
        } finally { System.setOut(original); }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n") + "[exit " + code + "]";
    }
    public static void main(String[] args) {
        String basic = "battle { kryptonite int reserve = 424242; supervision.getValue(reserve); reserve++; supervision.getValue(reserve); int visible = 7; supervision.getValue(visible); }";
        String result = run(basic);
        expect(result.contains("reserve = [protegido]") && !result.contains("424242") && !result.contains("424243") && result.contains("visible = 7"), "mask and positive control");
        expect(!transpile(basic).source().contains("Supervision.getValue(\"feature.hero:1 reserve"), "protected value not passed to observation");
        expect(run("battle { kryptonite int x = 42; int copy = x; supervision.getValue(copy); announce x; }").contains("copy = 42\n>> 42"), "documented copies and explicit printing");
        expect(run("battle { String text = null; supervision.getValue(text); boolean b = true; supervision.getValue(b); char c = 'é'; supervision.getValue(c); }").contains("c = é"), "null and primitive values");
        expect(run("battle { byte b=1; short s=2; long l=3L; float f=4.5f; double d=5.5; supervision.getValue(b); supervision.getValue(s); supervision.getValue(l); supervision.getValue(f); supervision.getValue(d); }").contains("[exit 0]"), "all numeric types");
        expect(run("battle { { kryptonite String x = null; supervision.getValue(x); } { String x = \"public\"; supervision.getValue(x); } }").contains("x = public"), "independent block scopes");
        expect(run("hero H { mission A { kryptonite int x=1; supervision.getValue(x); } mission B { int x=2; supervision.getValue(x); } } battle { summon H as h; h.A(); h.B(); }").contains("x = 2"), "independent mission scopes");
        String secret = "hero H { secret String code=\"SENTINEL\" allow Read; mission Read { supervision.getValue(code); } mission Denied { supervision.getValue(code); } } battle { summon H as h; h.Read(); h.Denied(); }";
        result = run(secret);
        expect(result.contains("code = [protegido]") && !result.contains("SENTINEL") && result.contains("SecretAccessDenied") && result.endsWith("[exit 1]"), "secret permissions without reading value");
        expect(run("hero H { secret int code=99 allow Read; mission Read(int code) { supervision.getValue(code); } } battle { summon H as h; h.Read(3); }").contains("code = 3"), "parameter shadows secret");
        expect(run("hero H { secret int code=99 allow Read; mission Read { int code=3; supervision.getValue(code); } } battle { summon H as h; h.Read(); }").contains("code = 3"), "local shadows secret");
        expect(valid("battle { kryptonite final int x=1; supervision.getValue(x); }"), "final accepted");
        String[] rejected = {
            "battle { supervision.getValue(missing); }",
            "battle { int x=0; supervision.getValue(x++); }",
            "battle { supervision.getValue(next()); }",
            "battle { supervision.getValue(); }",
            "battle { int x=0; supervision.getValue(x) }",
            "battle { Object x=new Object(); supervision.getValue(x); }",
            "battle { Integer x=1; supervision.getValue(x); }",
            "battle { var x=1; supervision.getValue(x); }",
            "battle { int[] x=new int[1]; supervision.getValue(x); }",
            "battle { { kryptonite int x=1; } supervision.getValue(x); }",
            "battle { kryptonite int x; }",
            "battle { kryptonite var x=1; }",
            "battle { kryptonite Object x=new Object(); }",
            "battle { kryptonite int x=1,y=2; }",
            "battle { kryptonite kryptonite int x=1; }",
            "battle { kryptonite final int x=1; x=2; }",
            "battle { kryptonite int x=\"wrong\"; supervision.getValue(x); }"
        };
        for (String source : rejected) expect(!valid(source), "reject " + source);
        expect(valid("battle { kryptonite int x=Math.max(1,2); supervision.getValue(x); }"), "comma in initializer call");
        result = run("hero H { int calls=0; mission Tick { calls++; } } battle { summon H as h; kryptonite int x=(h.calls++); supervision.getValue(x); supervision.getValue(x); announce h.calls; }");
        expect(result.contains(">> 1\n[exit 0]"), "initializer exactly once");
        expect(valid("battle { announce \"VALIDATION_MUST_NOT_EXECUTE\"; }"), "compile only validation");
        expect(run("battle { int x=9; supervision.getValue(x); }").contains("x = 9"), "second execution same JVM unaffected");
        expect(!valid("hero H { kryptonite int x=1; } battle {}"), "fields deferred");
        expect(!valid("hero H { secret kryptonite int x=1; } battle {}"), "combined modifier deferred");
        expect(!valid("hero H { secret int x=1 allow Read; mission Read { kryptonite int x=x; } } battle {}"), "initializer obeys Java local scope");
        expect(run("battle { for(int i=0;i<2;i++) { kryptonite int x=i; supervision.getValue(x); supervision.getValue(i); } }").contains("i = 1"), "loop scopes");
        expect(valid("batcave Empty {} battle { Empty e=new Empty(); }"), "empty batcave");
        String cave = "batcave C { int attempts=0; boolean accept(String code) { return code!=null; } public boolean authenticate(String code) { attempts++; supervision.getValue(attempts); kryptonite int internal=123456; supervision.getValue(internal); return accept(code); } public int count() { return attempts; } }";
        result = run(cave + " battle { C a=new C(); C b=new C(); announce a.authenticate(\"x\"); announce a.count(); announce b.count(); int visible=7; supervision.getValue(visible); }");
        expect(result.contains(">> true\n>> 1\n>> 0") && !result.contains("attempts =") && !result.contains("internal =") && result.contains("visible = 7"), "private state per instance and suppression of masked and public events");
        expect(!valid(cave + " battle { C c=new C(); announce c.attempts; }"), "external private field rejected");
        expect(!valid(cave + " battle { C c=new C(); announce c.accept(\"x\"); }"), "external default private method rejected");
        expect(!valid(cave + " hero H { mission Read { C c=new C(); announce c.attempts; } } battle {}"), "hero cannot read cave private field");
        expect(valid("batcave C { public int value=3; public static int answer() { int x=5; supervision.getValue(x); return x; } } battle { C c=new C(); announce c.value; announce C.answer(); }"), "explicit public field and static method");
        String callback = "hero Observer { mission Read { int callbackValue=2468; supervision.getValue(callbackValue); } } batcave C { public void invoke(Observer h) { h.Read(); } }";
        result = run(callback + " battle { summon Observer as h; C c=new C(); c.invoke(h); int visible=8; supervision.getValue(visible); h.Read(); }");
        expect(result.indexOf("callbackValue = 2468") == result.lastIndexOf("callbackValue = 2468") && result.contains("callbackValue = 2468") && result.indexOf("visible = 8") < result.indexOf("callbackValue = 2468"), "callback suppression restored on return");
        String nested = "batcave Inner { public void fail() { int innerValue=22; supervision.getValue(innerValue); throw new IllegalStateException(); } } batcave Outer { public void call() { try { new Inner().fail(); } catch(IllegalStateException e) { int caughtValue=33; supervision.getValue(caughtValue); } int outerValue=44; supervision.getValue(outerValue); } }";
        result = run(nested + " battle { new Outer().call(); int visible=9; supervision.getValue(visible); }");
        expect(result.contains("visible = 9") && !result.contains("innerValue =") && !result.contains("caughtValue =") && !result.contains("outerValue ="), "nested caves restore depth after caught exception");
        String failing = "batcave C { public void fail() { int internal=77; supervision.getValue(internal); throw new IllegalStateException(); } }";
        result = run(failing + " battle { try { new C().fail(); } catch(IllegalStateException e) {} int visible=10; supervision.getValue(visible); }");
        expect(result.contains("visible = 10") && !result.contains("internal ="), "escaping exception restores suppression");
        run(failing + " battle { new C().fail(); }");
        expect(run("battle { int visible=11; supervision.getValue(visible); }").contains("visible = 11"), "failed run does not contaminate next JVM execution");
        // Field initialization has its own boundary, before the constructor returns.
        result = run("batcave C { int value=init(); int init() { int initValue=999; supervision.getValue(initValue); return 4; } public int read() { return value; } } battle { announce new C().read(); int visible=12; supervision.getValue(visible); }");
        expect(result.contains(">> 4") && result.contains("visible = 12") && !result.contains("initValue ="), "field initializer protected");
        result = run("hero Observer { mission Read { int callbackValue=888; supervision.getValue(callbackValue); } } batcave C { int value=java.util.stream.IntStream.of(1).peek(x -> new Observer().Read()).sum(); public int read() { return value; } } battle { announce new C().read(); int visible=13; supervision.getValue(visible); }");
        expect(result.contains(">> 1") && result.contains("visible = 13") && !result.contains("callbackValue ="), "external callback suppressed in field initializer");
        result = run("batcave C { final int value=fail(); int fail() { throw new IllegalStateException(); } } battle { try { new C(); } catch(IllegalStateException e) {} int visible=14; supervision.getValue(visible); }");
        expect(result.contains("visible = 14"), "failed initialization restores suppression");
        result = run("batcave C { public static int answer() { int internal=18; supervision.getValue(internal); return internal; } } battle { announce C.answer(); int visible=15; supervision.getValue(visible); }");
        expect(result.contains(">> 18") && result.contains("visible = 15") && !result.contains("internal ="), "static method boundary");
        expect(valid("batcave C { int value=java.util.Objects.requireNonNull(null); } battle { new C(); }"), "check does not initialize batcave");
        expect(valid("batcave C { public int read() { return 1; } } villain V { mission Read { announce new C().read(); } } battle {}"), "dynamic cave authorization is checked at execution, not by executing during check");
        result = run("hero H { secret int code=5 allow Read; mission Read {} } batcave C { int code=3; public int read() { supervision.getValue(code); return this.code; } } battle { C c=new C(); announce c.read(); }");
        expect(result.contains(">> 3\n[exit 0]"), "cave field does not collide with unrelated hero secret");
        String[] badCaves = {
            "batcave C { villain Joker {} }",
            "batcave C { public void f() { villain Joker {} } }",
            "batcave C {} hero C {}",
            "batcave C {} villain C {}",
            "batcave C {} squad C {}",
            "batcave C {} batcave C {}",
            "batcave C { public int f() { return 1; }",
            "batcave C { public int f( { return 1; } }",
            "batcave C { kryptonite int x=1; }",
            "batcave C { public C() {} }",
            "batcave C { static int x=1; }",
            "batcave C { int x=1,y=2; }",
            "batcave C { public void f() { supervision.getValue(missing); } }",
            "battle { secret kryptonite int x=1; }",
            "battle { kryptonite secret int x=1; }"
        };
        for (String source : badCaves) expect(!valid(source), "reject cave " + source);
        try {
            transpile("batcave C {\n villain Joker {}\n}");
            throw new AssertionError("villain declaration accepted");
        } catch (CompileError e) {
            expect(e.getMessage().contains(":2:") && e.getMessage().contains("villain"), "villain diagnostic has source line");
        }
        result = run("battle { secret int code=987654; supervision.getValue(code); code++; supervision.getValue(code); int visible=20; supervision.getValue(visible); announce code==987655; }");
        expect(result.contains("code = [protegido]") && !result.contains("987654") && !result.contains("987655") && result.contains("visible = 20") && result.contains(">> true"), "secret local masks and remains usable");
        result = run("battle { secret final String value=null; supervision.getValue(value); { secret int hidden=1; supervision.getValue(hidden); } { int hidden=2; supervision.getValue(hidden); } }");
        expect(result.contains("value = [protegido]") && result.contains("hidden = 2"), "final null and independent local scopes");
        result = run("hero H { secret int code=99 allow Read; mission Read { secret int code=3; supervision.getValue(code); announce code; } } battle { summon H as h; h.Read(); }");
        expect(result.contains("code = [protegido]") && result.contains(">> 3") && !result.contains("SecretAccessDenied"), "secret local shadows legacy field");
        String secretCave = "batcave C { secret String code=\"EXAMPLE\"; secret boolean validate(String input) { return code.equals(input); } public boolean authenticate(String input) { secret int attempts=1; supervision.getValue(attempts); return validate(input); } }";
        result = run(secretCave + " battle { announce new C().authenticate(\"EXAMPLE\"); int visible=21; supervision.getValue(visible); }");
        expect(result.contains(">> true") && result.contains("visible = 21") && !result.contains("attempts ="), "secret field method and local inside cave");
        expect(!valid(secretCave + " battle { C c=new C(); announce c.code; }"), "secret cave field remains private by default");
        expect(!valid(secretCave + " battle { C c=new C(); announce c.validate(\"EXAMPLE\"); }"), "secret cave method remains private by default");
        expect(run("batcave C { secret public int read() { return 22; } } battle { announce new C().read(); }").contains(">> 22"), "secret method visibility independent of hiding");
        String secretHero = "hero H { secret public int calculate(int input) { secret int local=input+1; supervision.getValue(local); return local; } }";
        result = run(secretHero + " battle { summon H as h; announce h.calculate(2); int visible=23; supervision.getValue(visible); }");
        expect(result.contains(">> 3") && result.contains("visible = 23") && !result.contains("local ="), "typed secret hero method suppresses observations");
        result = run("villain V { secret public static int read(int x) { supervision.getValue(x); return x; } } battle { announce V.read(5); int visible=24; supervision.getValue(visible); }");
        expect(result.contains(">> 5") && result.contains("visible = 24") && !result.contains(" x ="), "static secret villain method");
        String hiddenMission = "hero H { secret String identity=\"EXAMPLE\" allow Read; secret mission Read { supervision.getValue(identity); int local=2; supervision.getValue(local); announce identity.equals(\"EXAMPLE\"); } }";
        result = run(hiddenMission + " battle { summon H as h; h.Read(); int visible=25; supervision.getValue(visible); }");
        expect(result.contains(">> true") && result.contains("visible = 25") && !result.contains("identity =") && !result.contains("local ="), "secret mission preserves allow and suppresses observations");
        result = run("hero H { mission Observe { int callbackValue=654321; supervision.getValue(callbackValue); } secret public void hidden() { Observe(); throw new IllegalStateException(); } } battle { summon H as h; try { h.hidden(); } catch(IllegalStateException e) {} int visible=26; supervision.getValue(visible); h.Observe(); }");
        expect(result.indexOf("visible = 26") < result.indexOf("callbackValue = 654321") && result.indexOf("callbackValue = 654321") == result.lastIndexOf("callbackValue = 654321"), "secret callback and exception boundary");
        result = run("hero H { secret int code=5 allow Read; secret public int code() { return 3; } mission Read { announce code(); } } battle { summon H as h; h.Read(); }");
        expect(result.contains(">> 3"), "method and secret field can share a name");
        result = run("hero H { int count=0; } battle { summon H as h; secret int local=h.count++; supervision.getValue(local); supervision.getValue(local); announce h.count; }");
        expect(result.contains(">> 1\n[exit 0]"), "secret initializer executes once");
        expect(valid("battle { secret Object x=new Object(); announce x!=null; }"), "secret local object usable without observation");
        String[] badSecrets = {
            "secret batcave C {}",
            "batcave C { secret batcave Hidden {} }",
            "batcave C { secret int x=1 allow Read; }",
            "batcave C { secret kryptonite int x=1; }",
            "batcave C { secret final kryptonite int x=1; }",
            "batcave C { secret public kryptonite int f() { return 1; } }",
            "battle { secret final kryptonite int x=1; }",
            "battle { kryptonite final secret int x=1; }",
            "battle { secret int x=1 allow Read; }",
            "battle { secret int x; }",
            "battle { secret int x=1,y=2; }",
            "battle { secret secret int x=1; }",
            "battle { secret var x=1; }",
            "battle { secret int x=\"invalid\"; }",
            "battle { secret final int x=1; x=2; }",
            "battle { { secret int x=1; } supervision.getValue(x); }",
            "battle { secret Object x=new Object(); supervision.getValue(x); }",
            "hero H { secret public int f(secret int x) { return x; } } battle {}",
            "hero H { secret mission Read { secret int x=1; } secret int code=1 allow Missing; } battle {}"
        };
        for (String source : badSecrets) expect(!valid(source), "reject secret " + source);
        var ast = new Parser(new Lexer(secretCave, "metadata.hero").tokenize(), "metadata.hero").parse();
        expect(ast.batcaves().get(0).fields().get(0).secret(), "AST secret field metadata");
        expect(ast.batcaves().get(0).methods().get(0).secret() && !ast.batcaves().get(0).methods().get(1).secret(), "AST method visibility metadata");
        expect(ast.batcaves().get(0).methods().get(1).body().stmts().get(0) instanceof heroscript.compiler.Ast.SecretLocal, "AST secret local metadata");
        ast = new Parser(new Lexer(hiddenMission, "metadata.hero").tokenize(), "metadata.hero").parse();
        expect(ast.characters().get(0).missions().get(0).secret(), "AST secret mission metadata");
        result = run("hero H { secret int code=42 allow Read; secret public int peek() { return code; } mission Read { announce peek(); } } battle { summon H as h; h.Read(); try { h.peek(); } catch(HeroError e) { announce e.kind(); } }");
        expect(result.contains(">> 42") && result.contains(">> SecretAccessDenied"), "secret typed method does not grant legacy field access");
        expect(run("batcave C { secret public int value=4; } battle { C c=new C(); announce c.value; }").contains(">> 4"), "secret cave field keeps explicit public visibility");
        System.out.println(passed + " feature checks passed");
    }
}
