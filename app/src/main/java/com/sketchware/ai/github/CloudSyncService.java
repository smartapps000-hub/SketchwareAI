package com.sketchware.ai.github;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import com.sketchware.remod.R;

/**
 * v2.0.4 (owner's 16 Nov directive "high-priority foreground dataSync service with a live notification, Cancel and
 * Retry"): while backups, publishing or imports run ({@link CloudSync}), this foreground service of type
 * {@code dataSync} keeps the app's process and network at foreground priority (Android 15 limits the network of apps
 * in the background), and its notification shows the job, the percentage, the bytes and the speed, with Cancel. A job's
 * end is a notification of its own: done (with Open on GitHub when it has a repository) or failed with Retry. The
 * service stops when nothing is left to do, and on Android 15's dataSync time limit ({@link #onTimeout}: the work goes
 * on without it).
 * <p>
 * Started only from the app in the foreground (a tap, a save in the editor, the projects list); Android refuses a start
 * from the background, and then the progress is a plain notification. Notifications need the POST_NOTIFICATIONS
 * permission on Android 13+ (asked when "Hide" is tapped, {@link GitHubProgress}); without it the jobs run all the same.
 */
public class CloudSyncService extends Service {

    static final String CHANNEL = "github_sync";
    static final int ID_RUNNING = 7340;
    static final String ACTION_CANCEL = "com.sketchware.ai.github.action.CANCEL";
    static final String ACTION_RETRY = "com.sketchware.ai.github.action.RETRY";
    static final String EXTRA_KEY = "key";
    static final String EXTRA_ID = "notification_id";

    /** A start was asked for (startForegroundService) and the service hasn't stopped since. */
    private static boolean started;
    /** review H1: the service is in the foreground (startForeground done): only then may it be stopped from outside. */
    private static boolean foreground;
    private static volatile Notification current;
    private static final Object LOCK = new Object();

    // ------------------------------------------------------------------ from CloudSync

    /** A job runs: the service in the foreground with its notification (or the notification alone). */
    static void running(Context context, String key, String title, int percent, String text, String link) {
        Notification n = build(context, key, title, percent, text, link);
        synchronized (LOCK) {
            current = n;
            if (!started) {
                try {
                    started = true;
                    context.startForegroundService(new Intent(context, CloudSyncService.class));
                    return;
                } catch (RuntimeException e) {
                    // Android refuses a foreground service from the background: the notification alone
                    started = false;
                }
            }
        }
        notify(context, ID_RUNNING, n);
    }

    /** A job ended: its own notification (done, or failed with Retry). */
    static void finished(Context context, String key, String title, boolean ok, String message, boolean retryable,
                         String link) {
        channel(context);
        int id = ID_RUNNING + 1 + (key.hashCode() & 0xfff);
        Notification.Builder b = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ob_ic_github)
                .setContentTitle((ok ? "Done: " : "Didn't work: ") + title.replaceFirst("^(Backing up|Importing|Publishing) ", ""))
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(context));
        if (!ok && retryable) {
            b.addAction(new Notification.Action.Builder(null, "Retry", action(context, ACTION_RETRY, key, id)).build());
        }
        if (link != null && link.startsWith("https://github.com/")) {
            b.addAction(new Notification.Action.Builder(null, "Open on GitHub", PendingIntent.getActivity(context, id,
                    new Intent(Intent.ACTION_VIEW, Uri.parse(link)), PendingIntent.FLAG_IMMUTABLE
                            | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        notify(context, id, b.build());
    }

    /** Nothing runs any more: the service stops and its notification goes. */
    static void idle(Context context) {
        synchronized (LOCK) {
            current = null;
            // review H1: a service still on its way to startForeground is never stopped from here (Android would
            // crash the app: "did not then call startForeground"); it stops itself in onStartCommand instead
            if (started && foreground) {
                started = false;
                foreground = false;
                context.stopService(new Intent(context, CloudSyncService.class));
            }
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ID_RUNNING);
    }

    private static Notification build(Context context, String key, String title, int percent, String text, String link) {
        channel(context);
        Notification.Builder b = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ob_ic_github)
                .setContentTitle(title + " · " + Math.max(0, Math.min(100, percent)) + "%")
                .setContentText(text)
                .setProgress(100, Math.max(0, Math.min(100, percent)), percent <= 1)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openApp(context))
                .addAction(new Notification.Action.Builder(null, "Cancel", action(context, ACTION_CANCEL, key, ID_RUNNING))
                        .build());
        if (link != null && link.startsWith("https://github.com/")) {
            b.addAction(new Notification.Action.Builder(null, "Open on GitHub", PendingIntent.getActivity(context, 1,
                    new Intent(Intent.ACTION_VIEW, Uri.parse(link)), PendingIntent.FLAG_IMMUTABLE
                            | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    private static PendingIntent action(Context context, String action, String key, int id) {
        Intent i = new Intent(context, Actions.class).setAction(action).putExtra(EXTRA_KEY, key).putExtra(EXTRA_ID, id);
        return PendingIntent.getBroadcast(context, (action + key).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent openApp(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) launch = new Intent();
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void channel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel c = new NotificationChannel(CHANNEL, "GitHub backups & imports", NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Progress of backups, publishing and imports, with Cancel and Retry");
        c.setShowBadge(false);
        nm.createNotificationChannel(c);
    }

    private static void notify(Context context, int id, Notification n) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        try {
            nm.notify(id, n);
        } catch (RuntimeException e) {
            // no permission to post (Android 13+): the job runs all the same
        }
    }

    // ------------------------------------------------------------------ the service

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = current;
        if (n == null) n = build(this, "idle", "GitHub", 1, "Starting…", null);
        try {
            // always first: Android requires it of a service started with startForegroundService
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID_RUNNING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(ID_RUNNING, n);
            }
        } catch (RuntimeException e) {
            // not allowed now: the notification alone (the jobs don't depend on the service)
            synchronized (LOCK) {
                started = false;
                foreground = false;
            }
            if (current != null) notify(this, ID_RUNNING, current);
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (LOCK) {
            foreground = true;
            if (current == null) {
                // everything ended before the service came up: it stops now, in the foreground as required
                started = false;
                foreground = false;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            } else if (current != n) {
                notify(this, ID_RUNNING, current);
            }
        }
        return START_NOT_STICKY;
    }

    /** Android 15: a dataSync service's daily time is used up: it stops (the jobs go on as far as Android lets them). */
    @Override
    public void onTimeout(int startId, int fgsType) {
        synchronized (LOCK) {
            started = false;
            foreground = false;
        }
        stopForeground(STOP_FOREGROUND_DETACH);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        synchronized (LOCK) {
            started = false;
            foreground = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** The notification buttons: Cancel and Retry (not exported: only this app's notifications reach it). */
    public static class Actions extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String key = intent.getStringExtra(EXTRA_KEY);
            if (key == null) return;
            if (ACTION_CANCEL.equals(intent.getAction())) {
                CloudSync.get(context).cancel(key);
            } else if (ACTION_RETRY.equals(intent.getAction())) {
                NotificationManager nm = context.getSystemService(NotificationManager.class);
                if (nm != null) nm.cancel(intent.getIntExtra(EXTRA_ID, 0));
                CloudSync.get(context).retry(key);
            }
        }
    }
}
