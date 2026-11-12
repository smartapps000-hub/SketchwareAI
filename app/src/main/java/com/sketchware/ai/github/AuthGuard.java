package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * v1.9.9 (owner's 12 Nov directive): sign-in required before the main screen, new projects, opening a project,
 * restoring a backup, building and exporting, when {@code config/app-control.json} says {@code "require_login": true}.
 * Only while sign-in can work: the file must have been read from GitHub in this run (an offline start never asks:
 * the IDE stays offline-first), and a sign-in that failed because it can't work on this phone (device sign-in switched
 * off for the OAuth app, no Client ID, the Keystore can't store it, no browser) lifts the requirement for the rest of
 * the run ({@link #markUnavailable}). So the gate can't lock anyone out of their projects for good: at worst until
 * the app is started again without a network.
 * <p>
 * A session counts when the stored token decrypts on this phone ({@link TokenStore}: a Keystore key, bound to
 * ANDROID_ID, the GitHub user id and the app's signing certificate). The answer is kept for the process; signing in or
 * out resets it.
 */
public final class AuthGuard {

    private static Boolean session;
    private static volatile boolean unavailable;

    private AuthGuard() {
    }

    /** True when app-control.json, read from GitHub in this run, requires sign-in and sign-in can work here. */
    public static boolean required(Context context) {
        if (unavailable || !AppControl.fetchedOnline()) return false;
        AppControl.Config config = AppControl.cached(context);
        return config != null && config.requireLogin;
    }

    /** Sign-in can't work on this phone in this run (see the class comment): nothing is required until the next start. */
    public static void markUnavailable() {
        unavailable = true;
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
