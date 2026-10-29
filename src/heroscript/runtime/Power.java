package heroscript.runtime;

/** Habilidade declarada com {@code power}: custo de energia, dano e recarga (em ações do personagem). */
public class Power {

    public final String name;
    public final int cost;
    public int damage;
    public final int cooldown;
    int cooldownLeft = 0;

    public Power(String name, int cost, int damage, int cooldown) {
        this.name = name;
        this.cost = cost;
        this.damage = damage;
        this.cooldown = cooldown;
    }
}
