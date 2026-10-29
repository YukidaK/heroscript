package heroscript.runtime;

/** Base de todo {@code villain}. Vilões atacam qualquer um, mas não entram em squads. */
public abstract class Villain extends Fighter {

    protected Villain(String typeName) {
        super(typeName);
    }
}
