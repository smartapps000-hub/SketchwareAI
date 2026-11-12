package com.sketchware.ai.github;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

import mod.jbk.util.LogUtil;

/**
 * v1.9.9: GitHub sign-in for the whole app, not for one screen (the owner's phone, 12 Nov: GitHub said "you're all set",
 * the app said "Sign-in didn't work"). While the user approves in Chrome, Android 15 cuts the network of the app in the
 * background; v1.9.8 polled from the screen and gave up on the first failed request. Now:
 * <ul>
 * <li>polling runs on its own thread and goes on through network failures until the code expires
 * ({@link DeviceFlow#waitForToken});</li>
 * <li>the code is saved (device code, expiry), so a sign-in the system interrupted by stopping the app continues when
 * the screen opens again ({@link #resumeSaved});</li>
 * <li>the screen asks for an immediate check when it comes back ({@link #pollNow});</li>
 * <li>when the token arrives while the browser is in front, the GitHub screen is brought back (CLEAR_TOP | SINGLE_TOP:
 * the Custom Tab, opened from that screen into the app's task, is closed with it). Android can refuse that from the
 * background; then the next return to the app shows the signed-in screen at once.</li>
 * </ul>
 */
public final class SignIn {

    public interface Listener {
        /** Called on the main thread whenever anything here changed. */
        void changed();
    }

    static final String PREFS = "sketchware_ai_github_signin";
    private static SignIn instance;

    public static synchronized SignIn get(Context context) {
        if (instance == null) instance = new SignIn(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object wake = new Object();
    private boolean wakeUp;

    // guarded by this
    private DeviceFlow.Code pending;
    private boolean starting;
    private boolean finishing;
    private boolean browserOpened;
    private String error;
    private String errorDetail;
    private String lastNetwork;
    private String signedInNow;
    private int generation;
    private Thread worker;

    SignIn(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ what the screen reads

    /** The code GitHub waits for, or null. */
    public synchronized DeviceFlow.Code pending() {
        return pending;
    }

    /** Asking GitHub for a code, or reading the account after approval. */
    public synchronized boolean busy() {
        return starting || finishing;
    }

    public synchronized boolean finishing() {
        return finishing;
    }

    /** One short line for the user, or null. */
    public synchronized String error() {
        return error;
    }

    /** The technical cause of the last failure (copied by a long press on the error line), or null. */
    public synchronized String errorDetail() {
        return errorDetail;
    }

    /** The login of a sign-in that just finished, once (the screen says "Signed in as …"). */
    public synchronized String takeSignedIn() {
        String login = signedInNow;
        signedInNow = null;
        return login;
    }

    /** True once per code: the screen opens the browser for it (only while it is in front). */
    public synchronized boolean takeOpenBrowser() {
        if (pending == null || browserOpened) return false;
        browserOpened = true;
        return true;
    }

    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------ actions

    /** Starts a sign-in (a new code), unless one is already under way. */
    public synchronized void start(String clientId) {
        if (starting || finishing || pending != null) return;
        error = null;
        errorDetail = null;
        lastNetwork = null;
        starting = true;
        browserOpened = false;
        int gen = ++generation;
        spawn(() -> run(gen, clientId, null));
        changed();
    }

    /** A code saved before the app was stopped: waiting for it goes on, until it expires. */
    public synchronized void resumeSaved(String clientId) {
        if (starting || finishing || pending != null) return;
        DeviceFlow.Code saved = loadSaved();
        if (saved == null) return;
        pending = saved;
        browserOpened = true; // the user has seen it; the Open GitHub button stays
        int gen = ++generation;
        spawn(() -> run(gen, clientId, saved));
        changed();
    }

    /** Checks with GitHub now instead of at the next interval (the app came back from the browser). */
    public void pollNow() {
        synchronized (wake) {
            wakeUp = true;
            wake.notifyAll();
        }
    }

    public synchronized void cancel() {
        generation++;
        pending = null;
        starting = false;
        finishing = false;
        error = null;
        errorDetail = null;
        clearSaved();
        pollNow();
        changed();
    }

    // ------------------------------------------------------------------ the work

    private void spawn(Runnable r) {
        worker = new Thread(r, "GitHubSignIn");
        worker.setDaemon(true);
        worker.start();
    }

    private synchronized boolean current(int gen) {
        return gen == generation;
    }

    private void run(int gen, String clientId, DeviceFlow.Code resumed) {
        try {
            DeviceFlow.Code code = resumed;
            if (code == null) {
                code = DeviceFlow.start(clientId, DeviceFlow.SCOPE);
                synchronized (this) {
                    if (gen != generation) return;
                    pending = code;
                    starting = false;
                }
                save(code);
                changed();
            }
            String token = DeviceFlow.waitForToken(clientId, code, new DeviceFlow.Waiting() {
                @Override
                public boolean cancelled() {
                    return !current(gen);
                }

                @Override
                public void sleep(long millis) throws InterruptedException {
                    waitOrWake(millis);
                }

                @Override
                public void networkTrouble(IOException e) {
                    synchronized (SignIn.this) {
                        lastNetwork = e.toString();
                    }
                    LogUtil.d("SignIn", "GitHub check failed, waiting goes on: " + e);
                }
            });
            synchronized (this) {
                if (gen != generation) return;
                finishing = true;
            }
            changed();
            CloudBackup.Account account = null;
            long until = System.currentTimeMillis() + 10 * 60_000L;
            while (account == null) {
                try {
                    account = CloudBackup.account(token);
                } catch (IOException e) {
                    // still in the background: the token is kept until the network is back
                    if (System.currentTimeMillis() > until || !current(gen)) throw e;
                    synchronized (this) {
                        lastNetwork = e.toString();
                    }
                    waitOrWake(5000);
                }
            }
            TokenStore.get(app).save(token, account.login, account.id);
            prefs.edit().putString("avatar_url", account.avatarUrl).apply();
            AuthGuard.reset();
            synchronized (this) {
                if (gen != generation) return;
                pending = null;
                finishing = false;
                signedInNow = account.login;
            }
            clearSaved();
            changed();
            bringToFront();
        } catch (GitHubException e) {
            fail(gen, e.code, e.code + ": " + e.getMessage());
        } catch (Throwable t) {
            fail(gen, t instanceof IOException ? "network" : t instanceof java.security.GeneralSecurityException
                    ? "keystore" : "error", t.toString());
            LogUtil.e("SignIn", "Sign-in failed", t);
        }
    }

    private void fail(int gen, String code, String detail) {
        synchronized (this) {
            if (gen != generation) return;
            pending = null;
            starting = false;
            finishing = false;
            error = "cancelled".equals(code) ? null : shortMessage(code);
            errorDetail = detail + (lastNetwork == null ? "" : "\nlast network error while waiting: " + lastNetwork);
        }
        clearSaved();
        changed();
    }

    /** One short line for the user; the cause stays in {@link #errorDetail()}. */
    static String shortMessage(String code) {
        switch (code) {
            case "access_denied":
                return "Sign-in was cancelled.";
            case "expired_token":
            case "token_expired":
                return "The code expired. Tap Sign in again.";
            case "device_flow_disabled":
            case "incorrect_client_credentials":
            case "http_404":
            case "no_client_id":
                return "GitHub sign-in isn't available yet. Please try later.";
            case "network":
                return "No internet connection.";
            case "keystore":
                return "Your phone couldn't store the sign-in. Please try again.";
            default:
                return "Sign-in didn't work. Please try again.";
        }
    }

    private void waitOrWake(long millis) throws InterruptedException {
        synchronized (wake) {
            if (!wakeUp) wake.wait(millis);
            wakeUp = false;
        }
    }

    private void changed() {
        main.post(() -> {
            for (Listener listener : listeners) {
                try {
                    listener.changed();
                } catch (RuntimeException e) {
                    LogUtil.e("SignIn", "Listener failed", e);
                }
            }
        });
    }

    /** No screen of ours is in front (the browser is): bring the GitHub screen back. */
    private void bringToFront() {
        main.post(() -> {
            if (!listeners.isEmpty()) return;
            try {
                Intent intent = new Intent(app, GitHubActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                app.startActivity(intent);
            } catch (RuntimeException e) {
                LogUtil.e("SignIn", "Couldn't bring the app back", e);
            }
        });
    }

    // ------------------------------------------------------------------ the saved code

    private void save(DeviceFlow.Code code) {
        prefs.edit()
                .putString("device_code", code.deviceCode)
                .putString("user_code", code.userCode)
                .putString("verification_uri", code.verificationUri)
                .putString("verification_uri_complete", code.verificationUriComplete)
                .putInt("interval", code.interval)
                .putLong("expires_at", code.expiresAt)
                .apply();
    }

    /** The saved code while it still works, else null (and the saved one is removed). */
    DeviceFlow.Code loadSaved() {
        String device = prefs.getString("device_code", null);
        long expiresAt = prefs.getLong("expires_at", 0);
        if (device == null) return null;
        if (expiresAt <= System.currentTimeMillis() + 5000) {
            clearSaved();
            return null;
        }
        DeviceFlow.Code code = new DeviceFlow.Code();
        code.deviceCode = device;
        code.userCode = prefs.getString("user_code", "");
        code.verificationUri = prefs.getString("verification_uri", GitHubHttp.DEFAULT_WEB + "/login/device");
        code.verificationUriComplete = prefs.getString("verification_uri_complete", null);
        code.interval = Math.max(5, prefs.getInt("interval", 5));
        code.expiresAt = expiresAt;
        code.expiresIn = (int) ((expiresAt - System.currentTimeMillis()) / 1000);
        return code;
    }

    private void clearSaved() {
        prefs.edit().remove("device_code").remove("user_code").remove("verification_uri")
                .remove("verification_uri_complete").remove("interval").remove("expires_at").apply();
    }

    /** The avatar address of the signed-in user, or null. */
    public String avatarUrl() {
        return prefs.getString("avatar_url", null);
    }
}
