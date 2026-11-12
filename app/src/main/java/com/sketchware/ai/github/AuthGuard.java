package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * v1.9.9 (owner's 12 Nov directive): sign-in required before the main screen, new projects, opening a project,
 * restoring a backup, building and exporting, when {@code config/app-control.json} says {@code "require_login": true}
 * (read at start by {@link AppControl}, the last answer kept for offline starts). Not required while the file says
 * false or has never been read on this phone: a sign-in that can't work (no network on the very first start, GitHub
 * unreachable) never locks anyone out of their projects.
 * <p>
 * A session counts when the stored token decrypts on this phone ({@link TokenStore}: a Keystore key, bound to
 * ANDROID_ID, the GitHub user id and the app's signing certificate). The answer is kept for the process; signing in or
 * out resets it.
 */
public final class AuthGuard {

    private static Boolean session;

    private AuthGuard() {
    }

    /** True when app-control.json (as last read) requires sign-in. */
    public static boolean required(Context context) {
        AppControl.Config config = AppControl.cached(context);
        return config != null && config.requireLogin;
    }

    /** True when a sign-in exists that this phone can decrypt (hardware-bound). */
    public static synchronized boolean verifyHardwareBoundSession(Context context) {
        if (session == null || !session) {
            TokenStore store = TokenStore.get(context);
            session = store.login() != null && store.token() != null;
        }
        return session;
    }

    /** Forget the kept answer (sign-in, sign-out). */
    public static synchronized void reset() {
        session = null;
    }

    /**
     * True when the operation may go on. Otherwise the sign-in screen opens (it can't be dismissed while sign-in is
     * required) and the caller stops.
     */
    public static boolean check(Activity activity) {
        try {
            if (!required(activity) || verifyHardwareBoundSession(activity)) return true;
        } catch (RuntimeException e) {
            return true; // a broken check never locks anyone out of their projects
        }
        if (!(activity instanceof GitHubActivity)) {
            Intent intent = new Intent(activity, GitHubActivity.class);
            intent.putExtra(GitHubActivity.EXTRA_GATE, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(intent);
        }
        return false;
    }
}
