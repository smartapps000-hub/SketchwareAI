package android.os;

/** Harness stand-in (v2.0.1): the uptime clocks from the JVM's monotonic clock. */
public final class SystemClock {
    private SystemClock() {
    }

    public static long elapsedRealtime() {
        return System.nanoTime() / 1_000_000L;
    }

    public static long uptimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }
}
