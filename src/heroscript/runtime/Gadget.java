package heroscript.runtime;

/** Equipamento declarado com {@code gadget}. {@code uses = -1} significa usos ilimitados. */
public class Gadget {

    public final String name;
    public final int defense;
    int usesLeft;

    public Gadget(String name, int defense, int uses) {
        this.name = name;
        this.defense = defense;
        this.usesLeft = uses;
    }

    public int usesLeft() {
        return usesLeft;
    }
}
