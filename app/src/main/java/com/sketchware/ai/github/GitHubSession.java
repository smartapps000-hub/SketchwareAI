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

    /**
     * The signed-in account, or null when signed out. Reads preferences and (v2.0.3) decrypts the saved token once to say
     * why it can't be used: a short Keystore operation, also on the main thread (the drawer card).
     */
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
        } else {
            // v2.1.2 (zero-lag): the Keystore read once per saved sign-in (the card is drawn on the main thread; the
            // answer is kept until the saved sign-in changes)
            String why = readProblem(store);
            if (why != null) p.problem = "This phone can't read the saved sign-in: " + why + ". Tap Sign in again.";
        }
        return p;
    }

    /** {sealed key, problem or ""}: the last Keystore read's answer. */
    private static volatile String[] readMemo;

    private static String readProblem(TokenStore store) {
        String sealed = store.sealedKey();
        String[] memo = readMemo;
        if (memo != null && sealed != null && sealed.equals(memo[0])) return memo[1].isEmpty() ? null : memo[1];
        String problem = store.token() == null ? store.problem() : null;
        // a passing Keystore hiccup isn't kept: the next card asks again
        if (problem == null || !problem.startsWith("the Android Keystore didn't answer")) {
            readMemo = new String[]{sealed, problem == null ? "" : problem};
        }
        return problem;
    }

    /** v2.1.2: at start, off the main thread: the Keystore read the account card needs, done ahead. */
    public static void warmUp(Context context) {
        Context app = context.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                TokenStore store = TokenStore.get(app);
                if (store.login() != null) readProblem(store);
            } catch (RuntimeException ignored) {
                // the card reads it then
            }
        }, "SessionWarmUp");
        t.setDaemon(true);
        t.start();
    }

    /**
     * v2.0.3: the account card's line about the sign-in's state: GitHub's refusal, or why this phone can't read the saved
     * sign-in (a quick Keystore read), else null.
     */
    public static String problemOf(Context context) {
        Profile p = profile(context);
        return p == null ? null : p.problem;
    }

    /**
     * v2.1.0 (owner's 16 Nov report: "HTTP 401 after installing the new APK"): the sign-in's own history, kept so a
     * refusal can say when this sign-in was made (and with which app version), how many sign-ins this phone made, and
     * when GitHub last accepted it. A token GitHub ends is GitHub's decision (its 10-per-app limit, access removed, a
     * token found in a public place); these lines show which it can have been.
     */
    static void noteSignedIn(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        prefs.edit().putLong("signed_in_at", now).putLong("accepted_at", now)
                .putString("signed_in_version", appVersion(context))
                .putInt("signin_count", prefs.getInt("signin_count", 0) + 1).apply();
    }

    /** v2.1.0: GitHub accepted the saved sign-in just now (a backup, the re-check). */
    static void noteAccepted(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("accepted_at", System.currentTimeMillis()).apply();
    }

    static String appVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** v2.1.0: the refusal's words with the sign-in's history (see {@link #noteSignedIn}). */
    static String refusedNote(Context context, String gitHubMessage) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.ENGLISH);
        StringBuilder sb = new StringBuilder(refusedNote(gitHubMessage));
        long signedIn = prefs.getLong("signed_in_at", 0);
        long accepted = prefs.getLong("accepted_at", 0);
        if (signedIn > 0) {
            sb.append(" This sign-in: made ").append(f.format(new java.util.Date(signedIn)));
            String version = prefs.getString("signed_in_version", null);
            if (version != null) sb.append(" with ").append(version);
            int count = prefs.getInt("signin_count", 0);
            if (count > 0) sb.append(" (sign-in ").append(count).append(" on this phone)");
            if (accepted > 0) sb.append(", last accepted ").append(f.format(new java.util.Date(accepted)));
            sb.append('.');
        }
        return sb.toString();
    }

    /** v2.0.3: the account card's and the messages' words for a 401. */
    static String refusedNote(String gitHubMessage) {
        String when = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH).format(new java.util.Date());
        String said = gitHubMessage == null || gitHubMessage.trim().isEmpty() ? "" : " (" + answerOf(gitHubMessage) + ")";
        return "GitHub didn't accept the saved sign-in at " + when + said + ". GitHub keeps 10 sign-ins per app for an "
                + "account and ends the least used one when there are more (other phones, Sign in again), or when its "
                + "access is removed in GitHub's settings. You're still signed in here: tap Sign in again to renew it; backups wait.";
    }

    /**
     * v2.1.1 (owner's 21 Nov screenshot: "…didn't accept the saved sign-in at 08:33 (GitHub didn't accept the saved
     * sign-in (HTTP 401: …).)."): only GitHub's answer of a message that already says it ("HTTP 401: Bad credentials,
     * request …").
     */
    static String answerOf(String message) {
        String m = message.trim();
        int http = m.indexOf("HTTP 401");
        if (http >= 0) {
            int end = m.indexOf(')', http);
            return (end > http ? m.substring(http, end) : m.substring(http)).trim();
        }
        return m.endsWith(".") ? m.substring(0, m.length() - 1) : m;
    }

    /**
     * v2.0.5: whether GitHub refused the saved sign-in (marked by a 401 and not renewed since): backups then wait
     * without asking GitHub again (no stream of 401s) until Sign in again.
     */
    public static boolean isRefused(Context context) {
        String refused = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("refused", null);
        return refused != null && refused.equals(TokenStore.get(context).sealedKey());
    }

    /**
     * v2.0.3: GitHub answered 401 for the saved token: the session stays, marked "not accepted" with {@code note}
     * (shown on the account card and in messages) until a new sign-in or a call GitHub accepts.
     */
    static void markRefused(Context context, String note, String sealedKey) {
        TokenStore store = TokenStore.get(context);
        // review (L1): only the sign-in GitHub refused (a new sign-in meanwhile stays unmarked)
        if (store.login() == null || sealedKey == null || !sealedKey.equals(store.sealedKey())) return;
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("refused", sealedKey)
                .putString("refused_note", note)
                .apply();
        changed();
    }

    /** v2.0.3: GitHub accepted the saved token again (or a new sign-in): the mark goes. */
    static void clearRefused(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        noteAccepted(context);
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
        signOut(context, "you tapped Sign out");
    }

    /** v2.1.2: as {@link #signOut(Context)}, with the reason recorded ({@link SessionLog}; the signed-out card says it). */
    public static void signOut(Context context, String reason) {
        String login = TokenStore.get(context).login();
        SessionLog.record(context, "signed_out", (login == null ? "" : "@" + login + ": ") + reason);
        TokenStore.get(context).clear();
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // v2.1.0 review (LOW): the sign-in history stays (the next refusal's note counts the sign-ins on this phone)
        int count = prefs.getInt("signin_count", 0);
        prefs.edit().clear().putInt("signin_count", count).apply();
        //noinspection ResultOfMethodCallIgnored
        avatarFile(context).delete();
        // v2.1.0: this phone's mark is no longer claimed; the repository viewer's saved pages (private ones too) go
        SingleDeviceGuard.forget(context);
        deleteTree(new java.io.File(context.getApplicationContext().getFilesDir(), "repo_viewer"));
        AuthGuard.reset();
        // v2.1.1 review (HIGH): the jobs waiting for a sign-in or a Retry were this account's
        CloudSync.get(context).signedOut();
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

    private static void deleteTree(java.io.File f) {
        java.io.File[] children = f.listFiles();
        if (children != null) for (java.io.File c : children) deleteTree(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
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
                TokenStore store = TokenStore.get(app);
                String token = store.token();
                String sealed = store.sealedKey();
                if (token == null) return;
                CloudBackup.Account account;
                try {
                    account = CloudBackup.account(token);
                } catch (GitHubException e) {
                    if (!"unauthorized".equals(e.code)) return;
                    // v2.1.0: asked once more after 3 s (a refusal is never decided on one answer)
                    try {
                        Thread.sleep(3000);
                        account = CloudBackup.account(token);
                    } catch (GitHubException again) {
                        // only when it is still this token (a new sign-in meanwhile keeps the new session)
                        if ("unauthorized".equals(again.code) && token.equals(TokenStore.get(app).token())) {
                            LogUtil.d("GitHubSession", "GitHub didn't accept the saved token twice: marked, not signed out");
                            markRefused(app, refusedNote(app, again.getMessage()), sealed);
                        }
                        return;
                    } catch (InterruptedException interrupted) {
                        return;
                    }
                }
                noteAccepted(app);
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
