package heroscript.runtime;

/** Observation boundaries shared by batcaves and secret methods. */
public final class Supervision {
    private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    private Supervision() {}

    public static void enterBatcave() { depth.set(depth.get() + 1); }
    public static void exitBatcave() {
        int current = depth.get();
        if (current <= 1) depth.remove(); else depth.set(current - 1);
    }
    public static void enterSecret() { enterBatcave(); }
    public static void exitSecret() { exitBatcave(); }
    public static boolean enabled() { return depth.get() == 0; }

    public static void getValue(String location, boolean value) { print(location, Boolean.toString(value)); }
    public static void getValue(String location, char value) { print(location, Character.toString(value)); }
    public static void getValue(String location, byte value) { print(location, Byte.toString(value)); }
    public static void getValue(String location, short value) { print(location, Short.toString(value)); }
    public static void getValue(String location, int value) { print(location, Integer.toString(value)); }
    public static void getValue(String location, long value) { print(location, Long.toString(value)); }
    public static void getValue(String location, float value) { print(location, Float.toString(value)); }
    public static void getValue(String location, double value) { print(location, Double.toString(value)); }
    public static void getValue(String location, String value) { print(location, value); }
    private static void print(String location, String value) {
        if (enabled()) Arena.log("supervision", location + value);
    }
    public static void getMethod(String report) {
        if (enabled()) System.out.println(report);
    }
}
