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
 * {@link #RECHECK_MILLIS}, GET /user with the token in the background. Offline the session is what {@link TokenStore}
 * decrypts on this phone.
 * <p>
 * v2.0.3 (owner's 15 Nov directive "never auto-logout once signed in"): only the account card's "Sign out" signs out.
 * When GitHub answers 401 (a re-check, a backup) the session stays; it is marked "not accepted" ({@link #markRefused})
 * with the time and GitHub's message, the account card says so and offers "Sign in again" (a new sign-in replaces the
 * token and clears the mark; so does any later call GitHub accepts with the same token).
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
        /** v2.0.3: public + private repositories GitHub counted at the last check, or -1. */
        public int repositories = -1;
        /** v2.0.3: when GitHub last confirmed the sign-in (ms since 1970), or 0. */
        public long checkedAt;
        /**
         * v2.0.3: why the saved sign-in can't be used now (GitHub didn't accept it, or this phone can't read it), else
         * null. The session stays; "Sign in again" renews it.
         */
        public String problem;
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
        p.repositories = prefs.getInt("repositories", -1);
        p.checkedAt = prefs.getLong("checked_at", 0);
        TokenStore store = TokenStore.get(context);
        String refused = prefs.getString("refused", null);
        if (refused != null && refused.equals(store.sealedKey())) {
            p.problem = prefs.getString("refused_note", "GitHub didn't accept the saved sign-in.");
        } else if (store.token() == null && store.problem() != null) {
            // a quick Keystore read: the card says why this phone can't use the saved sign-in (it stays saved)
            p.problem = "This phone can't read the saved sign-in: " + store.problem() + ". Tap Sign in again.";
        }
        return p;
    }

    /**
     * v2.0.3: the account card's line about the sign-in's state: GitHub's refusal, or why this phone can't read the saved
     * sign-in (a quick Keystore read), else null.
     */
    public static String problemOf(Context context) {
        Profile p = profile(context);
        return p == null ? null : p.problem;
    }

    /** v2.0.3: the account card's and the messages' words for a 401. */
    static String refusedNote(String gitHubMessage) {
        String when = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH).format(new java.util.Date());
        String said = gitHubMessage == null || gitHubMessage.trim().isEmpty() ? "" : " (" + gitHubMessage.trim() + ")";
        return "GitHub didn't accept the saved sign-in at " + when + said + ". You're still signed in here: tap Sign in "
                + "again to renew it.";
    }

    /**
     * v2.0.3: GitHub answered 401 for the saved token: the session stays, marked "not accepted" with {@code note}
     * (shown on the account card and in messages) until a new sign-in or a call GitHub accepts.
     */
    static void markRefused(Context context, String note) {
        TokenStore store = TokenStore.get(context);
        if (store.login() == null) return;
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("refused", store.sealedKey())
                .putString("refused_note", note)
                .apply();
        changed();
    }

    /** v2.0.3: GitHub accepted the saved token again (or a new sign-in): the mark goes. */
    static void clearRefused(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getString("refused", null) == null) return;
        prefs.edit().remove("refused").remove("refused_note").apply();
        changed();
    }

    /** Saves what GET /user (and /user/emails) said; the picture is downloaded separately. */
    static void saveProfile(Context context, CloudBackup.Account account, String primaryEmail) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name", account.name)
                .putString("email", primaryEmail != null ? primaryEmail : account.email)
                .putString("avatar_url", account.avatarUrl)
                .putInt("repositories", account.repositories)
                .putLong("checked_at", System.currentTimeMillis())
                .remove("refused")
                .remove("refused_note")
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
     * In the background, when signed in and the last check is older than {@link #RECHECK_MILLIS}: GET /user. v2.0.3: a
     * 401 marks the session "not accepted" ({@link #markRefused}) and never signs out; a network problem changes
     * nothing (offline, the hardware-bound session stands).
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
                    // only when it is still this token (a new sign-in meanwhile keeps the new session)
                    if ("unauthorized".equals(e.code) && token.equals(TokenStore.get(app).token())) {
                        LogUtil.d("GitHubSession", "GitHub didn't accept the saved token: marked, not signed out");
                        markRefused(app, refusedNote(e.getMessage()));
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
