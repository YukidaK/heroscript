package heroscript.runtime;

/** Base de todo {@code hero}. Heróis podem formar squads e não atacam outros heróis (FriendlyFire). */
public abstract class Hero extends Fighter {

    protected Hero(String typeName) {
        super(typeName);
    }
}
