import heroscript.compiler.*;
public class ReviewProbe {
    static void probe(String name,String source,boolean run) {
        System.out.println("CASE "+name);
        try {
            var output=Main.transpile(source,"review.hero");
            System.out.println("CHECK "+JavaRunner.validate("review.hero",output,System.out));
            if(run)System.out.println("EXIT "+JavaRunner.run("review.hero",output,System.out));
        }catch(Throwable e){System.out.println(e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    public static void main(String[] args) {
        System.out.println("CASE ordinary local investigation: "+Investigator.investigate("battle{int local=1;}","review.hero","local"));
        probe("combo positive overflow","hero H{} squad S{members H;combo C{cost=2147483647;requires=2;}}battle{summon H as h;summon H as h2;summon S as s;recruit h into s;recruit h2 into s;assemble s.C;announce h.energy;}",true);
        probe("unterminated escaped string","battle{announce \"abc"+'\\',false);
        probe("exponent literal","battle{announce 1e-3;}",false);
        probe("duplicate power","hero H{power P{damage=1;}power P{damage=2;}}battle{summon H as h;unleash h.P;}",true);
        probe("negative combo cost","hero H{} squad S{members H;combo C{cost=-10;requires=1;}}battle{summon H as h;summon S as s;recruit h into s;assemble s.C;announce h.energy;}",true);
        probe("combo friendly fire","hero H{} squad S{members H;combo C{damage=20;}}battle{summon H as h;summon H as target;summon S as s;recruit h into s;assemble s.C on target;announce target.health;}",true);
        probe("villain cave call","batcave C{public int read(){return 42;}}villain V{public int enter(){C c=new C();return c.read();}}battle{summon V as v;announce v.enter();}",true);
    }
}
