package heroscript.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Equipe declarada com {@code squad}. Um combo exige {@code requires} membros disponíveis (vivos e com energia
 * para sua parte do custo); o custo é dividido igualmente entre eles.
 */
public abstract class Squad {

    private record Combo(String name, int cost, int damage, int requires) {}

    public final String name;
    private final Set<String> allowedTypes = new LinkedHashSet<>();
    private final List<Hero> members = new ArrayList<>();
    private final Map<String, Combo> combos = new LinkedHashMap<>();

    protected Squad(String name) {
        this.name = name;
    }

    protected void allowMember(String heroType) {
        allowedTypes.add(heroType);
    }

    protected void defineCombo(String n, int cost, int damage, int requires) {
        combos.put(n, new Combo(n, cost, damage, Math.max(1, requires)));
    }

    public List<Hero> members() {
        return List.copyOf(members);
    }

    public void recruit(Hero hero) {
        String type = hero.getClass().getSimpleName();
        if (!allowedTypes.isEmpty() && !allowedTypes.contains(type)) {
            throw new HeroError("NotASquadMember", hero.name + " (" + type + ") não pode entrar em " + name
                    + "; membros aceitos: " + String.join(", ", allowedTypes));
        }
        if (!members.contains(hero)) members.add(hero);
        Arena.log("squad", hero.name + " entra em " + name + " (" + members.size() + " membro(s))");
    }

    public void assemble(String comboName, Fighter target) {
        Combo c = combos.get(comboName);
        if (c == null) throw new HeroError("ComboNotFound", name + " não possui o combo " + comboName);
        int share = (c.cost() + c.requires() - 1) / c.requires();
        List<Hero> ready = new ArrayList<>();
        for (Hero h : members) {
            if (!h.isDefeated() && h.energy >= share) ready.add(h);
        }
        if (ready.size() < c.requires()) {
            throw new HeroError("SquadUnavailable", comboName + " precisa de " + c.requires() + " membro(s) com "
                    + share + " de energia, mas " + name + " tem " + ready.size());
        }
        List<Hero> team = ready.subList(0, c.requires());
        List<String> names = new ArrayList<>();
        for (Hero h : team) {
            h.energy -= share;
            names.add(h.name);
        }
        Arena.log("assemble", name + " executa " + comboName + " com " + String.join(" + ", names)
                + (target == null ? "" : " em " + target.name) + " (-" + share + " EN cada)");
        if (target != null) target.receive(c.damage(), name);
    }

    @Override
    public String toString() {
        return name;
    }
}
