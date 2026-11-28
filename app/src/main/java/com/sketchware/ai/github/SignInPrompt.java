package com.sketchware.ai.github;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * v2.1.6 (owner's directive "FIX POST-UPDATE LOGOUT + AUTO-OPEN SIGN-IN POPUP ON UPDATE/RETRY", screenshots of the
 * "GitHub didn't accept this phone's sign-in" card): when GitHub has refused the saved sign-in (HTTP 401 for the token
 * made at sign-in: GitHub ended it on its side), the sign-in window opens by itself instead of waiting for a tap on
 * Sign in again: after the start-up gate (also the first start after an update), when a refusal is found while Home or
 * a GitHub screen is in front, and when such a screen comes back to the front. After the new sign-in every job the
 * refusal stopped runs again by itself ({@link CloudSync#resumeAfterSignIn}).
 * <p>
 * Never in the editors (a project being edited is never covered by a window), never twice for the same refusal in one
 * run of the app, and not again for that refusal once it was closed three times (the account card keeps Sign in again).
 * Nothing is signed out here: the saved sign-in stays until the user signs out or signs in again.
 */
public final class SignInPrompt implements Application.ActivityLifecycleCallbacks {

    private static final String PREFS = "sketchware_ai_signin_prompt";
    /** Closes of the automatic window per refusal before it stops opening by itself. */
    static final int MAX_CLOSES = 3;

    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    /** The refusal (sealed token key) the window already opened for in this run of the app. */
    private static String askedFor;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SignInPrompt() {
    }

    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new SignInPrompt());
    }

    /**
     * Where the window may open by itself: Home ({@code MainActivity}), Settings and the GitHub screens; never the
     * start-up gate's own sign-in screen ({@link GitHubActivity}), the editors or the build screens.
     */
    static boolean allowedOn(Activity a) {
        if (a == null || a instanceof GitHubActivity || a instanceof GitHubLinkActivity) return false;
        String name = a.getClass().getName();
        return name.equals("com.besome.sketch.MainActivity")
                || name.equals("com.sketchware.ai.ui.SettingsActivity")
                || (name.startsWith("com.sketchware.ai.github.") && name.endsWith("Activity"));
    }

    /**
     * The pure rule ({@link #maybeAsk} on the main thread reads the inputs): signed in, the saved sign-in refused,
     * not yet asked for this refusal in this run, closed fewer than {@link #MAX_CLOSES} times.
     */
    static boolean shouldAsk(String login, boolean refused, String sealedKey, String alreadyAskedFor, int closes) {
        if (login == null || !refused || sealedKey == null) return false;
        if (sealedKey.equals(alreadyAskedFor)) return false;
        return closes < MAX_CLOSES;
    }

    /** After the start-up gate (and from the screens below): opens the window when {@link #shouldAsk} says so. */
    public static void maybeAsk(Activity a) {
        if (a == null || a.isFinishing() || a.isDestroyed() || !allowedOn(a)) return;
        if (a != resumed.get()) return;
        // the start-up gate asks first (an update, then the sign-in gate); Home asks once the gate has passed
        if (a.getClass().getName().equals("com.besome.sketch.MainActivity") && !AppControl.gatePassed()) return;
        TokenStore store = TokenStore.get(a);
        String login = store.login();
        String key = store.sealedKey();
        boolean refused = GitHubSession.isRefused(a);
        if (!shouldAsk(login, refused, key, askedFor, closes(a, key))) return;
        if (GitHubInAppAuthDialog.isOpen()) {
            // review (LOW): a window is already there for this refusal (a sheet's, a tap's): that one counts
            askedFor = key;
            return;
        }
        askedFor = key;
        Context app = a.getApplicationContext();
        Toast.makeText(app, "GitHub ended this phone's sign-in for @" + login + ". Sign in again: waiting backups go on "
                + "by themselves.", Toast.LENGTH_LONG).show();
        try {
            final GitHubInAppAuthDialog[] opened = new GitHubInAppAuthDialog[1];
            opened[0] = GitHubInAppAuthDialog.show(a, new GitHubInAppAuthDialog.Done() {
                private boolean done;

                @Override
                public void signedIn(String signedIn) {
                    done = true;
                    GitHubSession.changed();
                }

                @Override
                public void closed() {
                    // second review (LOW): only the user's own close counts (a window replaced by another screen's,
                    // or closed because its screen went, is not; Android tells the close a moment after dismiss)
                    GitHubInAppAuthDialog window = opened[0];
                    if (!done && window != null && window.closedByUser()) noteClosed(app, key);
                }
            });
        } catch (RuntimeException e) {
            // the screen is going away: the next one in front asks
            askedFor = null;
        }
    }

    /** v2.1.6 review (LOW): a sign-in window is opening (any path): for a refused sign-in, it counts as asked. */
    static void noteAsked(Context context) {
        try {
            if (GitHubSession.isRefused(context)) {
                String key = TokenStore.get(context).sealedKey();
                if (key != null) askedFor = key;
            }
        } catch (RuntimeException ignored) {
            // nothing read: nothing noted
        }
    }

    /** A refusal was just recorded (any thread): the screen in front asks, if it may. */
    static void refused() {
        MAIN.postDelayed(() -> maybeAsk(resumed.get()), 800);
    }

    private static int closes(Context context, String key) {
        if (key == null) return 0;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return key.equals(p.getString("key", null)) ? p.getInt("closes", 0) : 0;
    }

    /** The automatic window was closed without signing in (counted for this refusal only). */
    static void noteClosed(Context context, String key) {
        if (key == null) return;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int closes = key.equals(p.getString("key", null)) ? p.getInt("closes", 0) : 0;
        p.edit().putString("key", key).putInt("closes", closes + 1).apply();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumed = new WeakReference<>(activity);
        if (!allowedOn(activity)) return;
        WeakReference<Activity> ref = new WeakReference<>(activity);
        // after the screen has drawn (and after a gate dialog had its turn)
        MAIN.postDelayed(() -> maybeAsk(ref.get()), 900);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (resumed.get() == activity) resumed = new WeakReference<>(null);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
