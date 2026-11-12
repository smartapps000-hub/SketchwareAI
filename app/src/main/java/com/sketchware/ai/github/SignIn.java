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
 * <li>when the token arrives while the browser is in front, the Custom Tab is closed: the screen opened it for a result
 * ({@link #browserOpenedBy}), so {@code finishActivity(requestCode)} removes it and the screen under it, in the same
 * task, comes back. That is not an activity start, so Android's background-start rules (stricter on Android 15) don't
 * apply. When the screen is gone or that didn't bring it back, the GitHub screen is started (CLEAR_TOP | SINGLE_TOP),
 * which Android may refuse from the background; then the next return to the app shows the signed-in screen at once.</li>
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
    /** The screen that opened the Custom Tab for a result (weak: the singleton must not keep a closed screen). */
    private java.lang.ref.WeakReference<android.app.Activity> browserOwner;
    private int browserRequest;

    /** Screens of the app in front (resumed), counted from when sign-in was first used in this run. */
    private int resumedScreens;

    SignIn(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (app instanceof android.app.Application) {
            // created from a screen's onCreate: the screen before it is already paused, so the count starts right
            ((android.app.Application) app).registerActivityLifecycleCallbacks(new ResumedCounter());
        }
    }

    private final class ResumedCounter implements android.app.Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityResumed(android.app.Activity activity) {
            resumedScreens++;
        }

        @Override
        public void onActivityPaused(android.app.Activity activity) {
            resumedScreens = Math.max(0, resumedScreens - 1);
        }

        @Override
        public void onActivityCreated(android.app.Activity activity, android.os.Bundle state) {
        }

        @Override
        public void onActivityStarted(android.app.Activity activity) {
        }

        @Override
        public void onActivityStopped(android.app.Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle state) {
        }

        @Override
        public void onActivityDestroyed(android.app.Activity activity) {
        }
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

    /** The screen opened the Custom Tab with {@code startActivityForResult(…, requestCode)}. */
    public synchronized void browserOpenedBy(android.app.Activity activity, int requestCode) {
        browserOwner = new java.lang.ref.WeakReference<>(activity);
        browserRequest = requestCode;
    }

    /** The screen is going away: it can't close the tab any more. */
    public synchronized void forgetBrowser(android.app.Activity activity) {
        if (browserOwner != null && browserOwner.get() == activity) browserOwner = null;
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
        synchronized (wake) {
            // a pollNow() from before this sign-in must not skip its first wait
            wakeUp = false;
        }
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
                /** When the last check started (uptime clock): GitHub answers slow_down (+5 s for good) to closer checks. */
                private long lastCheck = android.os.SystemClock.elapsedRealtime();

                @Override
                public boolean cancelled() {
                    return !current(gen);
                }

                @Override
                public void sleep(long millis) throws InterruptedException {
                    waitOrWake(millis);
                    // woken early (back from the browser): at once if the interval has passed (the app was frozen or
                    // offline for a while), else when it has; a cancel still ends the wait
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
                if (gen != generation) return;
                finishing = true;
            }
            changed();
            CloudBackup.Account account = null;
            long until = System.currentTimeMillis() + 10 * 60_000L;
            while (account == null) {
                if (!current(gen)) return; // cancelled while finishing: the token is dropped
                try {
                    account = CloudBackup.account(token);
                } catch (IOException | GitHubException e) {
                    // GitHub said yes: keep the token through a cut network, a server error or a rate limit
                    boolean passing = e instanceof IOException || isPassing(((GitHubException) e).code);
                    if (!passing || System.currentTimeMillis() > until || !current(gen)) throw e;
                    synchronized (this) {
                        lastNetwork = e.toString();
                    }
                    waitOrWake(5000);
                }
            }
            if (!current(gen)) return;
            TokenStore.get(app).save(token, account.login, account.id);
            prefs.edit().putString("avatar_url", account.avatarUrl).apply();
            AuthGuard.reset();
            synchronized (this) {
                if (gen != generation) {
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
            bringToFront();
            // the picture for the account card (any screen shows it from the file); a failure only means no picture
            if (downloadAvatar(app, account.avatarUrl)) changed();
        } catch (GitHubException e) {
            fail(gen, e.code, e.code + ": " + e.getMessage());
        } catch (Throwable t) {
            fail(gen, t instanceof IOException ? "network" : t instanceof java.security.GeneralSecurityException
                    ? "keystore" : "error", t.toString());
            LogUtil.e("SignIn", "Sign-in failed", t);
        }
    }

    /** A GitHub answer worth waiting out: a server error or a rate limit. */
    static boolean isPassing(String code) {
        return code != null && (code.matches("http_5\\d\\d") || code.equals("http_429") || code.equals("http_403"));
    }

    private void fail(int gen, String code, String detail) {
        if (cannotWork(code)) AuthGuard.markUnavailable();
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

    /**
     * No screen of ours is in front (the browser is): close the Custom Tab the screen opened, so the screen under it
     * comes back; if no screen is in front half a second later, start the GitHub screen (may be refused from the
     * background on Android 15+: then the user's own return shows the result).
     */
    private void bringToFront() {
        main.post(() -> {
            if (!listeners.isEmpty()) return;
            android.app.Activity owner;
            int request;
            synchronized (SignIn.this) {
                owner = browserOwner == null ? null : browserOwner.get();
                request = browserRequest;
                browserOwner = null;
            }
            if (owner != null && !owner.isFinishing() && !owner.isDestroyed()) {
                try {
                    owner.finishActivity(request);
                } catch (RuntimeException e) {
                    LogUtil.e("SignIn", "Couldn't close the browser tab", e);
                }
            }
            main.postDelayed(() -> {
                // only when no screen of the app is in front: the closed tab brought one back (the GitHub screen, or
                // the screen under a gate that finished itself), or the user is elsewhere in the app
                boolean unseen;
                synchronized (SignIn.this) {
                    unseen = signedInNow != null;
                }
                // and only while nobody has shown the result yet (a gate that finished itself took it)
                if (!unseen || !listeners.isEmpty() || resumedScreens > 0) return;
                try {
                    Intent intent = new Intent(app, GitHubActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    app.startActivity(intent);
                } catch (RuntimeException e) {
                    LogUtil.e("SignIn", "Couldn't bring the app back", e);
                }
            }, owner != null ? 500 : 0);
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

    /** The avatar address of the signed-in user, or null. */
    public String avatarUrl() {
        return prefs.getString("avatar_url", null);
    }
}
