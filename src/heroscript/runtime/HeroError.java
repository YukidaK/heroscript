package heroscript.runtime;

/** Erro de execução da HeroScript, ex.: {@code HeroError: InsufficientEnergy — ...}. */
public class HeroError extends RuntimeException {

    private final String kind;

    public HeroError(String kind, String detail) {
        super("HeroError: " + kind + " — " + detail);
        this.kind = kind;
    }

    /** Nome do erro (InsufficientEnergy, PowerNotFound...), útil em {@code catch}. */
    public String kind() {
        return kind;
    }
}
