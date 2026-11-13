package heroscript.runtime;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Object identity tombstones: aliases share the state, but unrelated objects never do. */
public final class Batplan {
    private static final class Registry {
        final ReferenceQueue<Object> queue=new ReferenceQueue<>();
        final Set<IdentityReference> inactive=ConcurrentHashMap.newKeySet();
    }
    private static final Registry fallback=new Registry();
    private static final InheritableThreadLocal<Registry> context=new InheritableThreadLocal<>() {
        @Override protected Registry initialValue(){return fallback;}
    };
    private Batplan() {}

    private static final class IdentityReference extends WeakReference<Object> {
        private final int hash;
        IdentityReference(Object object, ReferenceQueue<Object> queue) {
            super(object,queue); hash=System.identityHashCode(object);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if(this==other)return true;
            Object object=get();
            return object!=null && other instanceof IdentityReference reference && object==reference.get();
        }
    }
    public static final class Execution implements AutoCloseable {
        private final Registry previous;
        private boolean closed;
        private Execution(){previous=context.get();context.set(new Registry());}
        @Override public void close(){if(!closed){context.set(previous);closed=true;}}
    }
    public static Execution beginExecution(){return new Execution();}
    private static void clean(Registry registry) {
        IdentityReference reference;
        while((reference=(IdentityReference)registry.queue.poll())!=null)registry.inactive.remove(reference);
    }
    public static void deactivate(Object object) {
        if(object==null)throw new HeroError("BatplanNullTarget","batplan requires an existing object");
        Registry registry=context.get();
        clean(registry);
        registry.inactive.add(new IdentityReference(object,registry.queue));
    }
    public static boolean isActive(Object object) {
        Registry registry=context.get();
        clean(registry);
        return object!=null && !registry.inactive.contains(new IdentityReference(object,null));
    }
    public static <T> T active(T object) {
        if(object instanceof BatcaveAccess.Protected)BatcaveAccess.check();
        if(object!=null && !isActive(object))throw new HeroError("ObjectInactive","object was deactivated by batplan");
        return object;
    }
    // Primitive expressions are values, not object identities, even when Java could box them.
    public static boolean active(boolean value) { return value; }
    public static byte active(byte value) { return value; }
    public static short active(short value) { return value; }
    public static int active(int value) { return value; }
    public static long active(long value) { return value; }
    public static float active(float value) { return value; }
    public static double active(double value) { return value; }
    public static char active(char value) { return value; }
}
