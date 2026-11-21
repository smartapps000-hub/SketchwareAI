package com.sketchware.ai.github;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.sketchware.remod.R;

import java.io.File;

/**
 * v2.1.2 (owner's 21 Nov directive: "Hide moves the live progress to the system notification"): the app update's own
 * notification while its sheet is hidden: the download's percentage (at most about once a second), then "Tap to
 * install" (Android's installer, the checked file shared by the app's FileProvider) or the error. Channel "App updates",
 * low importance (no sound). Needs POST_NOTIFICATIONS on Android 13+, asked once by the sheet's Hide; without it nothing
 * shows and the sheet's toast says how it ended.
 */
final class UpdateNotice {

    static final String CHANNEL = "app_update";
    static final int ID = 7410;
    private static long lastPost;
    private static int lastPercent = -1;

    private UpdateNotice() {
    }

    private static NotificationManager manager(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return null;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Sketchware AI downloading its own update");
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
        return nm;
    }

    private static PendingIntent openApp(Context context) {
        // v2.1.2 review (LOW): the app's task as it is (no reordering under kept tabs)
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (open == null) open = new Intent(context, com.besome.sketch.MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(context, ID, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** The download's progress (posted when the percentage changed and a second has passed, or at the end). */
    static synchronized void progress(Context context, int percent, String text) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (percent == lastPercent || (now - lastPost < 1000 && percent < 95)) return;
        lastPost = now;
        lastPercent = percent;
        post(context, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ob_ic_export)
                .setContentTitle("Updating Sketchware AI · " + percent + "%")
                .setContentText(text)
                .setProgress(100, Math.max(0, Math.min(100, percent)), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(context)));
        progressShown = true;
    }

    /** Downloaded and checked: a tap opens Android's installer. */
    static synchronized void ready(Context context, File apk) {
        lastPercent = -1;
        progressShown = false;
        Intent install = new Intent(Intent.ACTION_VIEW);
        try {
            Uri uri = androidx.core.content.FileProvider.a(context, context.getPackageName() + ".provider", apk);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (RuntimeException e) {
            install = null;
        }
        post(context, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ob_ic_export)
                .setContentTitle("Sketchware AI update downloaded")
                .setContentText("Checked (SHA-256 matches). Tap to install.")
                .setAutoCancel(true)
                .setContentIntent(install == null ? openApp(context) : PendingIntent.getActivity(context, ID + 1, install,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)));
    }

    /** The download stopped: a tap opens the app, whose update card offers it again. */
    static synchronized void failed(Context context, String why) {
        lastPercent = -1;
        progressShown = false;
        post(context, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ob_ic_export)
                .setContentTitle("The update didn't download")
                .setContentText(why + " Open Sketchware AI to try again.")
                .setStyle(new Notification.BigTextStyle().bigText(why + " Open Sketchware AI to try again."))
                .setAutoCancel(true)
                .setContentIntent(openApp(context)));
    }

    /** A progress notification only ("downloaded" and the error stay until tapped). */
    private static volatile boolean progressShown;

    static synchronized void cancelProgress(Context context) {
        if (!progressShown) {
            // a new process: a progress notification of an earlier one may still be there
            try {
                NotificationManager nm = context.getSystemService(NotificationManager.class);
                if (nm == null) return;
                for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications()) {
                    if (n.getId() == ID && (n.getNotification().flags & android.app.Notification.FLAG_ONGOING_EVENT) != 0) {
                        nm.cancel(ID);
                    }
                }
            } catch (RuntimeException ignored) {
                // second review (LOW): never a crash at start for a notification
            }
            return;
        }
        cancel(context);
    }

    static synchronized void cancel(Context context) {
        progressShown = false;
        lastPercent = -1;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ID);
    }

    private static void post(Context context, Notification.Builder b) {
        try {
            NotificationManager nm = manager(context);
            if (nm != null) nm.notify(ID, b.build());
        } catch (RuntimeException ignored) {
            // notifications off or not allowed: the sheet / toast says it
        }
    }
}
