package com.sketchware.ai.github;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;

import mod.jbk.util.LogUtil;

/**
 * v2.0.0: what the app shows about the signed-in GitHub account (the drawer's account card) and the session's upkeep:
 * the profile (name, login, primary e-mail, picture file) kept in the app's private preferences, sign-out, and the
 * online re-check (owner's 13 Nov directive): whenever the app comes to the front with a network, at most every
 * {@link #RECHECK_MILLIS}, GET /user with the token in the background. HTTP 401 = the token was revoked on GitHub: the
 * session is removed on this phone too. Offline the session is what {@link TokenStore} decrypts on this phone.
 */
public final class GitHubSession {

    static final String PREFS = "sketchware_ai_github_profile";
    static final long RECHECK_MILLIS = 6 * 60 * 60 * 1000L;

    /** What the account card shows. */
    public static final class Profile {
        public String login;
        public String name;
        public String email;
        /** The picture in the app's files, or null. */
        public File avatar;
    }

    /** Called on the main thread when the profile or the session changed (sign-in, sign-out, re-check). */
    public interface Listener {
        void changed();
    }

    private static final java.util.concurrent.CopyOnWriteArrayList<Listener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile boolean checking;

    private GitHubSession() {
    }

    public static void addListener(Listener listener) {
        LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    static void changed() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            for (Listener listener : LISTENERS) {
                try {
                    listener.changed();
                } catch (RuntimeException e) {
                    LogUtil.e("GitHubSession", "Listener failed", e);
                }
            }
        });
    }

    static File avatarFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "github_avatar.png");
    }

    /** The signed-in account, or null when signed out (only reads preferences: fine on the main thread). */
    public static Profile profile(Context context) {
        String login = TokenStore.get(context).login();
        if (login == null) return null;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Profile p = new Profile();
        p.login = login;
        p.name = prefs.getString("name", null);
        p.email = prefs.getString("email", null);
        File avatar = avatarFile(context);
        p.avatar = avatar.isFile() ? avatar : null;
        return p;
    }

    /** Saves what GET /user (and /user/emails) said; the picture is downloaded separately. */
    static void saveProfile(Context context, CloudBackup.Account account, String primaryEmail) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name", account.name)
                .putString("email", primaryEmail != null ? primaryEmail : account.email)
                .putString("avatar_url", account.avatarUrl)
                .putLong("checked_at", System.currentTimeMillis())
                .apply();
    }

    /** Signs out on this phone: token, login, id, Keystore key, profile and picture. Backups on GitHub stay. */
    public static void signOut(Context context) {
        TokenStore.get(context).clear();
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        //noinspection ResultOfMethodCallIgnored
        avatarFile(context).delete();
        AuthGuard.reset();
        // GitHub's web sign-in in the app's sign-in window goes too (its cookies are the only WebView cookies of the app)
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                android.webkit.CookieManager cookies = android.webkit.CookieManager.getInstance();
                cookies.removeAllCookies(null);
                cookies.flush();
            } catch (RuntimeException e) {
                LogUtil.d("GitHubSession", "No WebView cookies to remove: " + e);
            }
        });
        changed();
    }

    /**
     * In the background, when signed in and the last check is older than {@link #RECHECK_MILLIS}: GET /user. A revoked
     * token (401) signs out; a network problem changes nothing (offline, the hardware-bound session stands).
     */
    public static void recheckOnline(Context context) {
        Context app = context.getApplicationContext();
        if (checking || TokenStore.get(app).login() == null) return;
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("checked_at", 0) < RECHECK_MILLIS) return;
        // a failed try (offline, GitHub errors) waits half an hour, not until the next resume
        if (now - prefs.getLong("tried_at", 0) < 30 * 60_000L) return;
        prefs.edit().putLong("tried_at", now).apply();
        checking = true;
        Thread thread = new Thread(() -> {
            try {
                String token = TokenStore.get(app).token();
                if (token == null) return;
                CloudBackup.Account account;
                try {
                    account = CloudBackup.account(token);
                } catch (GitHubException e) {
                    // only when it is still this token (a sign-out and a new sign-in meanwhile keep the new session)
                    if ("unauthorized".equals(e.code) && token.equals(TokenStore.get(app).token())) {
                        LogUtil.d("GitHubSession", "The GitHub token was revoked: signed out");
                        signOut(app);
                    }
                    return;
                }
                String email = null;
                try {
                    email = CloudBackup.primaryEmail(token);
                } catch (IOException ignored) {
                }
                // signed out (or another account) meanwhile: nothing is written back
                if (!token.equals(TokenStore.get(app).token())) return;
                saveProfile(app, account, email);
                // a new picture on GitHub replaces the kept one (a failed download keeps the old file)
                SignIn.downloadAvatar(app, account.avatarUrl);
                AuthGuard.reset();
                changed();
            } catch (IOException e) {
                LogUtil.d("GitHubSession", "Re-check skipped (no network): " + e);
            } catch (RuntimeException e) {
                LogUtil.e("GitHubSession", "Re-check failed", e);
            } finally {
                checking = false;
            }
        }, "GitHubRecheck");
        thread.setDaemon(true);
        thread.start();
    }
}
