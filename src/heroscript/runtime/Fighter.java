package heroscript.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base comum de {@code hero} e {@code villain}. Aqui ficam as regras das construções exclusivas da HeroScript:
 * unleash, shield, transform, revert, recharge, evolve e o controle de acesso de secret.
 */
public abstract class Fighter {

    public String name;
    public int energy = 100;
    public int health = 100;
    protected int maxEnergy;
    protected int maxHealth;

    private final Map<String, Power> powers = new LinkedHashMap<>();
    private final Map<String, Gadget> gadgets = new LinkedHashMap<>();
    private final Map<String, Form> forms = new LinkedHashMap<>();

    private Form activeForm;
    private int formActionsLeft;
    private int shieldDefense;

    protected Fighter(String typeName) {
        this.name = typeName;
    }

    // ------------------------------------------------------------ montagem (usada pelo código gerado)

    protected void definePower(String n, int cost, int damage, int cooldown) {
        Batplan.active(this);
        powers.put(n, new Power(n, cost, damage, cooldown));
    }

    protected void defineGadget(String n, int defense, int uses) {
        Batplan.active(this);
        gadgets.put(n, new Gadget(n, defense, uses));
    }

    protected Form defineForm(String n) {
        Batplan.active(this);
        Form f = new Form(n);
        forms.put(n, f);
        return f;
    }

    /** Chamado ao fim do construtor: os valores iniciais viram os máximos. */
    protected void ready() {
        Batplan.active(this);
        maxEnergy = energy;
        maxHealth = health;
    }

    // ------------------------------------------------------------ consultas

    public boolean isDefeated() {
        Batplan.active(this);
        return health <= 0;
    }

    public int maxEnergy() {
        Batplan.active(this);
        return maxEnergy;
    }

    public int maxHealth() {
        Batplan.active(this);
        return maxHealth;
    }

    public String form() {
        Batplan.active(this);
        Batplan.active(activeForm);
        return activeForm == null ? "normal" : activeForm.name;
    }

    public String status() {
        Batplan.active(this);
        return name + " [HP " + health + "/" + maxHealth + " | EN " + energy + "/" + maxEnergy + " | forma " + form() + "]";
    }

    @Override
    public String toString() {
        Batplan.active(this);
        return name;
    }

    // ------------------------------------------------------------ unleash

    public void unleash(String powerName, Fighter target) {
        Batplan.active(this);
        Batplan.active(target);
        requireAlive();
        Power p = powers.get(powerName);
        Batplan.active(p);
        if (p == null) throw new HeroError("PowerNotFound", name + " não possui o poder " + powerName);
        if (target != null && target != this && this instanceof Hero && target instanceof Hero) {
            throw new HeroError("FriendlyFire", name + " não pode atacar o herói " + target.name);
        }
        if (p.cooldownLeft > 0) {
            throw new HeroError("PowerOnCooldown", powerName + " de " + name + " recarrega em " + p.cooldownLeft + " ação(ões)");
        }
        int cost = Math.max(0, activeForm == null ? p.cost : activeForm.apply("cost", p.cost));
        if (energy < cost) {
            throw new HeroError("InsufficientEnergy", name + " precisa de " + cost + " de energia para " + powerName
                    + ", mas tem " + energy);
        }
        energy -= cost;
        int damage = Math.max(0, activeForm == null ? p.damage : activeForm.apply("damage", p.damage));
        Arena.log("unleash", name + " usa " + powerName + (target == null ? "" : " em " + target.name)
                + " (-" + cost + " EN, energia " + energy + ")");
        if (target != null) target.receive(damage, name);
        endAction(p);
    }

    /** Recebe dano já descontando a defesa do escudo ativo e da forma. O escudo absorve um único golpe. */
    public void receive(int damage, String source) {
        Batplan.active(this);
        int defense = activeForm == null ? shieldDefense : activeForm.apply("defense", shieldDefense);
        int taken = Math.max(0, damage - Math.max(0, defense));
        boolean wasStanding = !isDefeated();
        shieldDefense = 0;
        health = Math.max(0, health - taken);
        String blocked = damage > taken ? " (" + (damage - taken) + " bloqueado)" : "";
        Arena.log("hit", name + " sofre " + taken + " de dano" + blocked + " → HP " + health);
        if (wasStanding && isDefeated()) Arena.log("down", name + " foi derrotado por " + source + "!");
    }

    /** Fim de uma ação: avança recargas e o tempo da forma ativa. */
    private void endAction(Power used) {
        for (Power p : powers.values()) {
            if(!Batplan.isActive(p))continue;
            if (p.cooldownLeft > 0) p.cooldownLeft--;
        }
        used.cooldownLeft = used.cooldown;
        Batplan.active(activeForm);
        if (activeForm != null && formActionsLeft > 0 && --formActionsLeft == 0) {
            Arena.log("transform", "a forma " + activeForm.name + " de " + name + " expirou");
            revert();
        }
    }

    // ------------------------------------------------------------ gadgets, energia e evolução

    public void shield(String gadgetName) {
        Batplan.active(this);
        requireAlive();
        Gadget g = gadgets.get(gadgetName);
        Batplan.active(g);
        if (g == null) throw new HeroError("GadgetNotFound", name + " não possui o gadget " + gadgetName);
        if (g.usesLeft == 0) throw new HeroError("GadgetUnavailable", gadgetName + " de " + name + " não tem mais usos");
        if (g.usesLeft > 0) g.usesLeft--;
        shieldDefense = g.defense;
        String uses = g.usesLeft < 0 ? "" : ", " + g.usesLeft + " uso(s) restante(s)";
        Arena.log("shield", name + " ativa " + gadgetName + " (defesa " + g.defense + uses + ")");
    }

    public void recharge(int amount) {
        Batplan.active(this);
        requireAlive();
        int before = energy;
        energy = Math.min(Math.max(maxEnergy, energy), energy + amount);
        Arena.log("recharge", name + " recupera " + (energy - before) + " de energia → " + energy);
    }

    public void evolve(String powerName, int amount) {
        Batplan.active(this);
        Power p = powers.get(powerName);
        Batplan.active(p);
        if (p == null) throw new HeroError("PowerNotFound", name + " não possui o poder " + powerName);
        p.damage += amount;
        Arena.log("evolve", powerName + " de " + name + " agora causa " + p.damage + " de dano");
    }

    // ------------------------------------------------------------ transformações

    public void transform(String formName) {
        Batplan.active(this);
        requireAlive();
        Form f = forms.get(formName);
        Batplan.active(f);
        if (f == null) throw new HeroError("FormNotFound", name + " não possui a forma " + formName);
        if (f.usesLeft == 0) {
            throw new HeroError("TransformationExpired", "a forma " + formName + " de " + name + " já foi esgotada");
        }
        if (activeForm != null) revert();
        if (f.usesLeft > 0) f.usesLeft--;
        activeForm = f;
        formActionsLeft = f.duration;
        energy = f.apply("energy", energy);
        health = f.apply("health", health);
        String time = f.duration > 0 ? " por " + f.duration + " ação(ões)" : "";
        Arena.log("transform", name + " assume a forma " + formName + time + " → " + status());
    }

    /** Volta à forma normal. Energia e vida acima do máximo são cortadas. */
    public void revert() {
        Batplan.active(this);
        if (activeForm == null) return;
        Batplan.active(activeForm);
        String old = activeForm.name;
        activeForm = null;
        energy = Math.min(energy, maxEnergy);
        health = Math.min(health, maxHealth);
        Arena.log("revert", name + " deixa a forma " + old);
    }

    // ------------------------------------------------------------ secret

    /** Leitura de um secret só é permitida dentro das missões deste personagem listadas em {@code allow}. */
    protected void checkSecret(String secret, String... allowedMissions) {
        Batplan.active(this);
        if (!Arena.inMissionOf(this, allowedMissions)) {
            String where = allowedMissions.length == 0 ? "nenhuma missão" : String.join(", ", allowedMissions);
            throw new HeroError("SecretAccessDenied", "o secret " + secret + " de " + name + " só pode ser lido em "
                    + where + " (tentativa em " + Arena.currentMission() + ")");
        }
    }

    private void requireAlive() {
        if (isDefeated()) throw new HeroError("CharacterDefeated", name + " está fora de combate");
    }
}
