package heroscript.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntConsumer;

/** Ambiente de execução: registra a missão em andamento (usada por {@code secret}) e imprime o relato da batalha. */
public final class Arena {

    private record Frame(Fighter owner, String mission) {}

    private static final Deque<Frame> missions = new ArrayDeque<>();
    private static IntConsumer exitHandler = System::exit;

    private Arena() {}

    public static void enterMission(Fighter owner, String mission) {
        missions.push(new Frame(owner, mission));
        log("mission", owner + " inicia a missão " + mission);
    }

    public static void exitMission() {
        missions.pop();
    }

    /** A missão em execução agora pertence a {@code owner} e está na lista {@code allowed}? */
    static boolean inMissionOf(Fighter owner, String... allowed) {
        Frame f = missions.peek();
        if (f == null || f.owner() != owner) return false;
        for (String m : allowed) {
            if (m.equals(f.mission())) return true;
        }
        return false;
    }

    static String currentMission() {
        Frame f = missions.peek();
        return f == null ? "battle" : f.owner() + "." + f.mission();
    }

    public static void log(String tag, String message) {
        System.out.println("[" + tag + "] " + message);
    }

    public static void announce(Object message) {
        System.out.println(">> " + message);
    }

    /** Chamado pelo battle quando um HeroError não é tratado: mostra o erro e encerra com código 1. */
    public static void fail(HeroError e) {
        System.out.println(e.getMessage());
        exitHandler.accept(1);
    }

    /** Troca o encerramento do programa (padrão: System.exit). O JavaRunner o usa para não derrubar a JVM. */
    public static void setExitHandler(IntConsumer handler) {
        exitHandler = handler;
    }
}
