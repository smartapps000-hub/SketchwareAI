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
    /**
     * Second review: the instance the flags belong to (set when it is in the foreground, cleared when it is told to
     * stop), so the onDestroy of an instance that is stopping never clears the flags of the next start; and a count of
     * running() calls, so an idle() decided before a job of the other lane started does nothing.
     */
    private static CloudSyncService instance;
    private static long generation;

    static long generation() {
        synchronized (LOCK) {
            return generation;
        }
    }

    // ------------------------------------------------------------------ from CloudSync

    /** A job runs: the service in the foreground with its notification (or the notification alone). */
    static void running(Context context, String key, String title, int percent, String text, String link) {
        Notification n = build(context, key, title, percent, text, link);
        synchronized (LOCK) {
            generation++;
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

    /**
     * v2.1.3: the end notice's id of {@code key}: its own range, apart from the app update's (7410) and the running
     * notification (v2.0.4-v2.1.2: 7341 + 12 bits of the key's hash, which could meet 7410).
     */
    static int finishedId(String key) {
        return 21000 + (key.hashCode() & 0xfff);
    }

    /**
     * v2.1.3 (owner's 22 Nov report: after Retry the backup ran, but "Didn't work: Vehicle_Parking" stayed and invited a
     * second tap): the end notice of {@code key}'s earlier run goes when the job runs again (also one an earlier version
     * posted under the old id).
     */
    static void clearFinished(Context context, String key) {
        if (key == null) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        try {
            nm.cancel(finishedId(key));
            // review (LOW): the old range (7341 + 12 bits) also holds the app update's notices (7410, 7411)
            int legacy = ID_RUNNING + 1 + (key.hashCode() & 0xfff);
            if (legacy != UpdateNotice.ID && legacy != UpdateNotice.ID + 1) nm.cancel(legacy);
        } catch (RuntimeException ignored) {
            // nothing to take away
        }
    }

    /** A job ended: its own notification (done, or failed with Retry). */
    static void finished(Context context, String key, String title, boolean ok, String message, boolean retryable,
                         String link) {
        channel(context);
        clearFinished(context, key);
        int id = finishedId(key);
        String heading = (ok ? "Done: " : "Didn't work: ") + title.replaceFirst("^(Backing up|Importing|Publishing) ", "");
        Notification.Builder b = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ob_ic_github)
                .setContentTitle(heading)
                .setContentText(message)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(context));
        // v2.1.3: the app's glass card
        GlassNotice.apply(context, b, ok ? R.drawable.ob_ic_check : R.drawable.ob_ic_github, heading, message, -1, false,
                ok ? GlassNotice.DONE : GlassNotice.FAILED);
        if (!ok && retryable) {
            b.addAction(new Notification.Action.Builder(null, "Retry", action(context, ACTION_RETRY, key, id)).build());
        }
        if (link != null && link.startsWith("https://github.com/")) {
            b.addAction(new Notification.Action.Builder(null, "Open on GitHub", PendingIntent.getActivity(context, id,
                    viewer(context, link), PendingIntent.FLAG_IMMUTABLE
                            | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        notify(context, id, b.build());
    }

    /** Nothing runs any more: the service stops and its notification goes. */
    static void idle(Context context, long seen) {
        synchronized (LOCK) {
            // second review: a job started since the caller looked: it keeps the service and its notification
            if (seen != generation) return;
            current = null;
            // review H1: a service still on its way to startForeground is never stopped from here (Android would
            // crash the app: "did not then call startForeground"); it stops itself in onStartCommand instead
            if (foreground) {
                started = false;
                foreground = false;
                instance = null;
                context.stopService(new Intent(context, CloudSyncService.class));
            }
            // third review: inside the lock (a job of the other lane can't post its notification in between)
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(ID_RUNNING);
        }
    }

    private static Notification build(Context context, String key, String title, int percent, String text, String link) {
        channel(context);
        int shown = Math.max(0, Math.min(100, percent));
        String heading = title + " · " + shown + "%";
        Notification.Builder b = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ob_ic_github)
                .setContentTitle(heading)
                .setContentText(text)
                .setProgress(100, shown, percent <= 1)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openApp(context));
        // v2.1.0: several jobs share this notification (key null): a line per job, no Cancel (each sheet has its own)
        if (key != null) {
            b.addAction(new Notification.Action.Builder(null, "Cancel", action(context, ACTION_CANCEL, key, ID_RUNNING))
                    .build());
        }
        // v2.1.3: the app's glass card (a line per job when several share it)
        GlassNotice.apply(context, b, R.drawable.ob_ic_github, heading, text, shown, percent <= 1, GlassNotice.RUNNING);
        if (link != null && link.startsWith("https://github.com/")) {
            b.addAction(new Notification.Action.Builder(null, "Open on GitHub", PendingIntent.getActivity(context, 1,
                    viewer(context, link), PendingIntent.FLAG_IMMUTABLE
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

    /** v2.0.5: "Open on GitHub" opens the repository in the app (RepoViewerActivity); other links the browser. */
    static Intent viewer(Context context, String link) {
        GitHubImport.Source source = GitHubImport.parse(link);
        Intent i = source == null ? new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                : RepoViewerActivity.intent(context, source.owner, source.repo);
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
            // third review: stopped inside the lock (a running() in between would start it again only to be stopped)
            synchronized (LOCK) {
                started = false;
                foreground = false;
                if (current != null) notify(this, ID_RUNNING, current);
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        synchronized (LOCK) {
            foreground = true;
            instance = this;
            if (current == null) {
                // everything ended before the service came up: it stops now, in the foreground as required
                started = false;
                foreground = false;
                instance = null;
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
            if (instance == this) {
                started = false;
                foreground = false;
                instance = null;
            }
            // third review: inside the lock, as idle() and the self-stop do
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        synchronized (LOCK) {
            // only this instance's own flags (one told to stop has already given them up)
            if (instance == this) {
                started = false;
                foreground = false;
                instance = null;
            }
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
