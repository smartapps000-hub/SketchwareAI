package com.sketchware.ai.github;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

import mod.jbk.util.LogUtil;

/**
 * GitHub sign-in for the whole app (v1.9.9), shown in the app's own sign-in window since v2.0.0
 * ({@link GitHubInAppAuthDialog}): the app stays in front the whole time, so Android 15 never cuts its network while
 * the code is checked. Polling runs on its own thread and goes on through network failures until the code expires
 * ({@link DeviceFlow#waitForToken}); a code is saved (device code, expiry) so a sign-in the system interrupted by stopping
 * the app continues when the window opens again ({@link #resumeSaved}); {@link #pollNow} asks for a check at once
 * (never closer than GitHub's interval). After the token: the account (login, id, name, primary e-mail, picture).
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
    private String error;
    private String errorCode;
    private String errorDetail;
    private String lastNetwork;
    private String signedInNow;
    private int generation;
    private Thread worker;
    /** v2.0.1: when the last new code was asked for (uptime clock), and no new code before this time. */
    private long lastStartAt;
    private long startBlockedUntil;
    /** v2.0.1: the generation {@link #pause} ended: an answer still on its way for it is not thrown away. */
    private int pausedGeneration = -1;

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

    /** v2.0.1: the code of the last failure (e.g. {@code expired_token}, {@code rate_limited}), or null. */
    public synchronized String errorCode() {
        return errorCode;
    }

    /** v2.0.1: seconds until a new code may be asked for after GitHub's "too many requests", or 0. */
    public synchronized int waitSeconds() {
        long rest = startBlockedUntil - android.os.SystemClock.elapsedRealtime();
        return rest <= 0 ? 0 : (int) ((rest + 999) / 1000);
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

    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------ actions

    /**
     * Starts a sign-in (a new code), unless one is already under way. v2.0.1 (owner's 13 Nov report "Too many
     * requests"): never two new codes closer than {@link #MIN_START_GAP} (a second ask waits for the rest of the gap),
     * and none at all while GitHub's "too many requests" answer is being waited out.
     */
    public synchronized void start(String clientId) {
        if (starting || finishing || pending != null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now < startBlockedUntil) {
            error = shortMessage("rate_limited");
            errorCode = "rate_limited";
            changed();
            return;
        }
        long gap = lastStartAt == 0 ? MIN_START_GAP : now - lastStartAt;
        error = null;
        errorCode = null;
        errorDetail = null;
        lastNetwork = null;
        starting = true;
        lastStartAt = now;
        int gen = ++generation;
        long delay = gap >= MIN_START_GAP ? 0 : MIN_START_GAP - gap;
        spawn(() -> {
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    return;
                }
                synchronized (SignIn.this) {
                    if (gen != generation) return;
                    lastStartAt = android.os.SystemClock.elapsedRealtime();
                }
            }
            run(gen, clientId, null);
        });
        changed();
    }

    /** v2.0.1: the shortest time between two new codes (GitHub limits code requests per app and network). */
    static final long MIN_START_GAP = 10_000L;

    /** A code saved before the app was stopped: waiting for it goes on, until it expires. */
    public synchronized void resumeSaved(String clientId) {
        if (starting || finishing || pending != null) return;
        DeviceFlow.Code saved = loadSaved();
        if (saved == null) return;
        pending = saved;
        int gen = ++generation;
        spawn(() -> run(gen, clientId, saved));
        changed();
    }

    /** Checks with GitHub now instead of at the next interval (the user just submitted the code). */
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
        errorCode = null;
        errorDetail = null;
        clearSaved();
        pollNow();
        changed();
    }

    /**
     * v2.0.1: the sign-in window was closed before GitHub's approval. Checking stops, but the code stays saved: opening
     * the window again continues with the same code ({@link #resumeSaved}) instead of asking GitHub for a new one
     * (owner's 13 Nov report "Too many requests": every close and open used to cost a new code). An approval reading
     * the account ({@link #finishing()}) is not paused.
     */
    public synchronized void pause() {
        if (finishing) return;
        // review: a token (or a new code) already on its way for this generation still counts when it arrives
        pausedGeneration = generation;
        generation++;
        pending = null;
        starting = false;
        error = null;
        errorCode = null;
        errorDetail = null;
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
        synchronized (wake) {
            // a pollNow() from before this sign-in must not skip its first wait
            wakeUp = false;
        }
        // the generation this run works for (an approval that arrives after a pause takes a new one)
        int[] g = {gen};
        try {
            DeviceFlow.Code code = resumed;
            if (code == null) {
                code = DeviceFlow.start(clientId, DeviceFlow.SCOPE);
                synchronized (this) {
                    if (gen != generation) {
                        // the window was closed while the code was on its way: kept for the next window
                        if (gen == pausedGeneration) save(code);
                        return;
                    }
                    pending = code;
                    starting = false;
                }
                save(code);
                changed();
            }
            String token = DeviceFlow.waitForToken(clientId, code, new DeviceFlow.Waiting() {
                /** When the last check started (uptime clock): GitHub answers slow_down (+5 s for good) to closer checks. */
                private long lastCheck = android.os.SystemClock.elapsedRealtime();

                @Override
                public boolean cancelled() {
                    return !current(gen);
                }

                @Override
                public void sleep(long millis) throws InterruptedException {
                    waitOrWake(millis);
                    // woken early (the user submitted the code): at once if the interval has passed, else when it
                    // has; a cancel still ends the wait
                    long rest = lastCheck + millis - android.os.SystemClock.elapsedRealtime();
                    while (rest > 0 && current(gen)) {
                        synchronized (wake) {
                            wake.wait(rest);
                            wakeUp = false;
                        }
                        rest = lastCheck + millis - android.os.SystemClock.elapsedRealtime();
                    }
                    lastCheck = android.os.SystemClock.elapsedRealtime();
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
                if (gen != generation) {
                    if (gen != pausedGeneration) return;
                    // review: the window was closed while GitHub's "yes" was on its way: the user approved, so it
                    // counts (a run started meanwhile for the same code is ended by the new generation)
                    pausedGeneration = -1;
                    g[0] = ++generation;
                }
                finishing = true;
            }
            changed();
            CloudBackup.Account account = null;
            long until = System.currentTimeMillis() + 10 * 60_000L;
            while (account == null) {
                if (!current(g[0])) return; // cancelled while finishing: the token is dropped
                try {
                    account = CloudBackup.account(token);
                } catch (IOException | GitHubException e) {
                    // GitHub said yes: keep the token through a cut network, a server error or a rate limit
                    boolean passing = e instanceof IOException || isPassing(((GitHubException) e).code);
                    if (!passing || System.currentTimeMillis() > until || !current(g[0])) throw e;
                    synchronized (this) {
                        lastNetwork = e.toString();
                    }
                    waitOrWake(5000);
                }
            }
            if (!current(g[0])) return;
            // review: the token is saved first; the e-mail (optional, retried on a slow network) is read afterwards
            TokenStore.get(app).save(token, account.login, account.id);
            GitHubSession.saveProfile(app, account, null);
            AuthGuard.reset();
            synchronized (this) {
                if (g[0] != generation) {
                    // cancelled during the save: the screen shows "signed out", so the phone must be too
                    TokenStore.get(app).clear();
                    AuthGuard.reset();
                    return;
                }
                pending = null;
                finishing = false;
                signedInNow = account.login;
            }
            clearSaved();
            changed();
            GitHubSession.changed();
            // projects made while signed out of GitHub or offline go up now
            CloudSync.get(app).syncPending();
            try {
                String email = CloudBackup.primaryEmail(token);
                if (email != null && token.equals(TokenStore.get(app).token())) {
                    GitHubSession.saveProfile(app, account, email);
                    GitHubSession.changed();
                }
            } catch (IOException ignored) {
                // the e-mail is only shown on the account card; the next online re-check reads it
            }
            // the picture for the account card (every screen reads the file); a failure only means no picture
            if (downloadAvatar(app, account.avatarUrl)) GitHubSession.changed();
        } catch (GitHubException e) {
            fail(g[0], e.code, e.code + ": " + e.getMessage());
            if ("rate_limited".equals(e.code) || "http_429".equals(e.code)) {
                synchronized (this) {
                    // GitHub said "too many requests": no new code for a minute (the window counts it down)
                    startBlockedUntil = android.os.SystemClock.elapsedRealtime() + 60_000L;
                }
                changed();
            }
        } catch (Throwable t) {
            fail(g[0], t instanceof IOException ? "network" : t instanceof java.security.GeneralSecurityException
                    ? "keystore" : "error", t.toString());
            LogUtil.e("SignIn", "Sign-in failed", t);
        }
    }

    /** A GitHub answer worth waiting out: a server error or a rate limit. */
    static boolean isPassing(String code) {
        return code != null && (code.matches("http_5\\d\\d") || code.equals("http_429") || code.equals("http_403")
                || code.equals("rate_limited"));
    }

    private void fail(int gen, String code, String detail) {
        if (cannotWork(code)) AuthGuard.markUnavailable();
        synchronized (this) {
            if (gen != generation) return;
            pending = null;
            starting = false;
            finishing = false;
            error = "cancelled".equals(code) ? null : shortMessage(code);
            errorCode = "cancelled".equals(code) ? null : code;
            errorDetail = detail + (lastNetwork == null ? "" : "\nlast network error while waiting: " + lastNetwork);
        }
        clearSaved();
        changed();
    }

    /** Sign-in can't work on this phone or for this app as it is: a required sign-in is lifted for this run. */
    static boolean cannotWork(String code) {
        switch (code) {
            case "device_flow_disabled":
            case "incorrect_client_credentials":
            case "http_404":
            case "no_client_id":
            case "keystore":
            case "unsupported_grant_type":
                return true;
            default:
                return false;
        }
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
            case "rate_limited":
            case "http_429":
            case "slow_down":
                return "GitHub got too many requests from this network. Wait a minute, then try again.";
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

    /** The account picture, kept in the app's files (temp file + rename); false when there is none. */
    static boolean downloadAvatar(Context app, String url) {
        if (url == null) return false;
        java.io.File file = new java.io.File(app.getFilesDir(), "github_avatar.png");
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url + (url.contains("?") ? "&" : "?") + "s=128").openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            if (c.getResponseCode() != 200) return false;
            android.graphics.Bitmap bitmap;
            try (java.io.InputStream in = c.getInputStream()) {
                bitmap = android.graphics.BitmapFactory.decodeStream(in);
            }
            if (bitmap == null) return false;
            java.io.File temp = new java.io.File(file.getPath() + ".tmp");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
            if (!temp.renameTo(file)) {
                temp.delete();
                return false;
            }
            return true;
        } catch (Exception e) {
            LogUtil.d("SignIn", "No avatar: " + e);
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
