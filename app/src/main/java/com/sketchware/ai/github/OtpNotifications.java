package com.sketchware.ai.github;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * v2.1.4 (owner's 22 Nov report, second time: "the code GitHub sends to that e-mail should come into our app"): with the
 * user's own switch (Android's "Notification access" for Sketchware AI, off until the user turns it on there), GitHub's
 * e-mailed code goes from the e-mail app's notification into GitHub's field in the sign-in window, and GitHub's own
 * Verify is pressed. Only while a sign-in window waits for a code ({@link #arm}); the rest of the time the service asks
 * Android to unbind it ({@link #requestUnbind()}), so it doesn't run or read anything. Only a notification whose text
 * mentions GitHub and holds a 6- or 8-digit code is used; nothing is stored, logged or sent anywhere. No permission is
 * added to the app (the service is protected by Android's {@code BIND_NOTIFICATION_LISTENER_SERVICE}, which only the
 * system holds).
 */
public final class OtpNotifications extends NotificationListenerService {

    /** The sign-in window: gets the code on the main thread. */
    public interface Sink {
        void codeFromNotification(String code, String from);
    }

    private static volatile Sink sink;
    /**
     * Review (MEDIUM): only notifications posted from this time on count (wall clock): when the page offered "Send a
     * code via email" or E-mail me a code was tapped, else shortly before the code page came; never an older code.
     */
    private static volatile long floor;
    private static volatile OtpNotifications connected;

    /**
     * Review (MEDIUM): Android 15 hides one-time codes from notification listeners that aren't trusted system apps, so
     * the switch is offered on Android 8-14 only (Android 15+: copy the code and come back).
     */
    public static boolean offered() {
        return android.os.Build.VERSION.SDK_INT < 35;
    }

    /** Whether the user gave Sketchware AI notification access in Android's settings. */
    public static boolean enabled(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager) context
                        .getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) return nm.isNotificationListenerAccessGranted(component(context));
            } catch (RuntimeException | LinkageError ignored) {
                // read the setting as androidx does
            }
        }
        // as androidx's NotificationManagerCompat reads it: the listeners the user turned on, "pkg/cls:pkg/cls"
        try {
            String on = android.provider.Settings.Secure.getString(context.getContentResolver(),
                    "enabled_notification_listeners");
            if (on == null) return false;
            for (String entry : on.split(":")) {
                ComponentName c = ComponentName.unflattenFromString(entry);
                if (c != null && context.getPackageName().equals(c.getPackageName())) return true;
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static ComponentName component(Context context) {
        return new ComponentName(context.getApplicationContext(), OtpNotifications.class);
    }

    /**
     * The sign-in window waits for an e-mailed code posted from {@code since} on (wall clock; a later floor of the
     * same window moves it on, never back); the service is bound again when access is on.
     */
    static void arm(Context context, Sink waiting, long since) {
        boolean fresh = sink != waiting;
        sink = waiting;
        floor = fresh ? since : Math.max(floor, since);
        OtpNotifications live = connected;
        if (live != null) {
            live.scanActive();
        } else {
            rebind(context);
        }
    }

    private static void rebind(Context context) {
        if (!enabled(context)) return;
        try {
            requestRebind(component(context));
        } catch (RuntimeException ignored) {
            // access just turned off, or Android refused: the window still has Paste & fill
        }
    }

    /** That window no longer waits (closed, signed in, or the page has no code field): the service lets go. */
    static void disarm(Sink waiting) {
        if (sink != waiting) return;
        sink = null;
        OtpNotifications live = connected;
        if (live != null) live.letGo();
    }

    static boolean armed() {
        return sink != null;
    }

    @Override
    public void onListenerConnected() {
        connected = this;
        if (sink == null) {
            letGo();
        } else {
            scanActive();
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (connected == this) connected = null;
        // review (MEDIUM): a window started waiting again while Android was unbinding: bound again
        if (sink != null) rebind(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Sink waiting = sink;
        if (waiting == null) {
            letGo();
            return;
        }
        take(sbn, waiting);
    }

    private void letGo() {
        // review (MEDIUM): unbinding: the next arm asks for a new binding instead of using this one
        if (connected == this) connected = null;
        try {
            requestUnbind();
        } catch (RuntimeException ignored) {
            // already unbound
        }
    }

    /** An e-mail that came just before the window waited (the page and the e-mail race). */
    private void scanActive() {
        Sink waiting = sink;
        if (waiting == null) return;
        StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (RuntimeException e) {
            return;
        }
        if (active == null) return;
        StatusBarNotification newest = null;
        String newestCode = null;
        for (StatusBarNotification n : active) {
            String code = codeOf(n);
            if (code != null && (newest == null || n.getPostTime() > newest.getPostTime())) {
                newest = n;
                newestCode = code;
            }
        }
        if (newestCode != null) waiting.codeFromNotification(newestCode, appName(newest.getPackageName()));
    }

    private void take(StatusBarNotification sbn, Sink waiting) {
        String code = codeOf(sbn);
        if (code != null) waiting.codeFromNotification(code, appName(sbn.getPackageName()));
    }

    /** The code in a GitHub notification posted since the window's floor, else null (never throws). */
    private String codeOf(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return null;
            if (getPackageName().equals(sbn.getPackageName())) return null;
            if (sbn.getPostTime() < floor) return null;
            return OtpAssist.codeFromGitHubMail(textOf(sbn.getNotification()));
        } catch (RuntimeException e) {
            // review (LOW): another app's extras that can't be read (BadParcelableException) are skipped
            return null;
        }
    }

    /** The notification's title, text, expanded text and lines (Gmail puts the e-mail's start in the expanded text). */
    static String textOf(Notification n) {
        Bundle extras = n.extras;
        if (extras == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String key : new String[]{Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG, Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT,
                Notification.EXTRA_INFO_TEXT}) {
            CharSequence value = extras.getCharSequence(key);
            if (value != null) sb.append(value).append('\n');
        }
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) if (line != null) sb.append(line).append('\n');
        if (n.tickerText != null) sb.append(n.tickerText);
        return sb.toString();
    }

    private String appName(String pkg) {
        if ("com.google.android.gm".equals(pkg)) return "Gmail";
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return "your e-mail app";
        }
    }
}
