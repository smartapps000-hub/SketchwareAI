package android.os;

/**
 * Harness stand-in (v2.0.1): there is no main thread on the harness, so posted work is dropped (the classes under test
 * post only listener notifications to the screen). Lets SignIn and GitHubSession be created in h.GitHubTest.
 */
public class Handler {
    public Handler() {
    }

    public Handler(Looper looper) {
    }

    public final boolean post(Runnable r) {
        return true;
    }

    public final boolean postDelayed(Runnable r, long delayMillis) {
        return true;
    }

    public final void removeCallbacks(Runnable r) {
    }

    public final void removeCallbacksAndMessages(Object token) {
    }
}
