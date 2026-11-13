package android.os;

/** Harness stand-in (v2.0.1): one main looper object; nothing loops here. */
public final class Looper {
    private static final Looper MAIN = new Looper();

    private Looper() {
    }

    public static Looper getMainLooper() {
        return MAIN;
    }

    public static Looper myLooper() {
        return MAIN;
    }
}
