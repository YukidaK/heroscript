package heroscript.runtime;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/** A fresh draw on every entry, including the deterministic 0% and 100% endpoints. */
public final class DeadpoolAction {
    private DeadpoolAction() {}

    public static boolean choose(int chance) {
        return choose(chance, () -> ThreadLocalRandom.current().nextInt(100));
    }

    // Internal seam for exact tests without a seed or configurable randomness in the DSL.
    static boolean choose(int chance, IntSupplier draw) {
        if(chance<0 || chance>100)throw new IllegalArgumentException("chance must be between 0 and 100");
        int value=draw.getAsInt();
        if(value<0 || value>=100)throw new IllegalArgumentException("draw must be between 0 and 99");
        return value<chance;
    }
}
