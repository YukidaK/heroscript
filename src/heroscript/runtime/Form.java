package heroscript.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Forma declarada com {@code form}. Modifica atributos enquanto ativa.
 * {@code duration}: ações até reverter (0 = até {@code revert}). {@code uses}: ativações permitidas (-1 = ilimitado).
 */
public class Form {

    private record Modifier(String stat, String op, int value) {}

    public final String name;
    int duration = 0;
    int usesLeft = -1;
    private final List<Modifier> modifiers = new ArrayList<>();

    public Form(String name) {
        this.name = name;
    }

    public Form set(String setting, int value) {
        if (setting.equals("duration")) duration = value;
        else if (setting.equals("uses")) usesLeft = value;
        return this;
    }

    public Form modify(String stat, String op, int value) {
        modifiers.add(new Modifier(stat, op, value));
        return this;
    }

    /** Aplica, em ordem, todos os modificadores de {@code stat} sobre o valor base. */
    int apply(String stat, int base) {
        int v = base;
        for (Modifier m : modifiers) {
            if (!m.stat().equals(stat)) continue;
            switch (m.op()) {
                case "=" -> v = m.value();
                case "+=" -> v += m.value();
                case "-=" -> v -= m.value();
                case "*=" -> v *= m.value();
                default -> throw new IllegalStateException(m.op());
            }
        }
        return v;
    }
}
