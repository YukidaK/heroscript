package heroscript.runtime;

/** Synchronous authorization follows the complete call chain, including static helpers. */
public final class BatcaveAccess {
    public interface Protected {}
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private BatcaveAccess() {}
    public static void check() {
        if(WALKER.walk(frames->frames.anyMatch(frame->Villain.class.isAssignableFrom(frame.getDeclaringClass()))))
            throw new HeroError("BatcaveAccessDenied","villains cannot access batcave members");
    }
}
