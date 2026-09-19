package com.sketchware.ai.migration;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;

import com.besome.sketch.MainActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the Sketchware Pro import on one background thread and keeps its state across
 * Activity recreation.
 * <p>
 * The UI attaches a {@link Listener} while visible. When the user sends the import to the
 * background, progress moves to a notification (if notifications are allowed); tapping it
 * brings the progress dialog back.
 */
public final class LegacyImportController {

    public static final String EXTRA_SHOW_IMPORT_PROGRESS = "com.sketchware.ai.SHOW_IMPORT_PROGRESS";

    private static final String CHANNEL_ID = "project_import";
    private static final int NOTIFICATION_ID = 0x5A1;
    private static final long NOTIFICATION_INTERVAL_MS = 700;

    public interface Listener {
        void onProgress(LegacyProjectMigrator.Progress progress);

        void onFinished(LegacyProjectMigrator.Result result);
    }

    private static LegacyImportController instance;

    public static synchronized LegacyImportController get(Context context) {
        if (instance == null) instance = new LegacyImportController(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        }, "sketchware-ai-import");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean cancel = new AtomicBoolean();

    private Listener listener;
    private boolean running;
    private boolean inBackground;
    private LegacyProjectMigrator.Progress lastProgress;
    private LegacyProjectMigrator.Result pendingResult;
    private long lastNotification;

    private LegacyImportController(Context app) {
        this.app = app;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isInBackground() {
        return inBackground;
    }

    public LegacyProjectMigrator.Progress lastProgress() {
        return lastProgress;
    }

    /** Starts the import unless one is already running. Main thread only. */
    public void start() {
        if (running) return;
        running = true;
        inBackground = false;
        pendingResult = null;
        lastProgress = null;
        cancel.set(false);
        executor.execute(() -> {
            PowerManager.WakeLock wakeLock = null;
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SketchwareAI:import");
                    wakeLock.setReferenceCounted(false);
                    wakeLock.acquire(60L * 60 * 1000);
                }
            } catch (RuntimeException ignored) {
                wakeLock = null;
            }
            LegacyProjectMigrator.Result result;
            try {
                result = LegacyProjectMigrator.run(app,
                        progress -> main.post(() -> deliverProgress(progress)),
                        cancel::get);
            } catch (RuntimeException e) {
                result = new LegacyProjectMigrator.Result(0, 0, false,
                        "Import failed: " + e);
            } finally {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            }
            LegacyProjectMigrator.Result finalResult = result;
            main.post(() -> deliverResult(finalResult));
        });
    }

    public void cancel() {
        cancel.set(true);
    }

    /** Attach while an Activity is in the foreground; delivers any finished result at once. */
    public void attach(Listener listener) {
        this.listener = listener;
        if (lastProgress != null && running) listener.onProgress(lastProgress);
        if (pendingResult != null) {
            LegacyProjectMigrator.Result result = pendingResult;
            pendingResult = null;
            listener.onFinished(result);
        }
    }

    public void detach(Listener listener) {
        if (this.listener == listener) this.listener = null;
    }

    /** The user chose "Run in background". */
    public void moveToBackground() {
        inBackground = true;
        if (lastProgress != null) postNotification(lastProgress, true);
    }

    /** The progress dialog is visible again. */
    public void moveToForeground() {
        inBackground = false;
        cancelNotification();
    }

    private void deliverProgress(LegacyProjectMigrator.Progress progress) {
        lastProgress = progress;
        if (listener != null) listener.onProgress(progress);
        if (inBackground) postNotification(progress, false);
    }

    private void deliverResult(LegacyProjectMigrator.Result result) {
        running = false;
        boolean wasInBackground = inBackground;
        inBackground = false;
        cancelNotification();
        if (wasInBackground) postFinishedNotification(result);
        if (listener != null) {
            listener.onFinished(result);
        } else {
            pendingResult = result;
        }
    }

    // region Notification

    private NotificationManager notificationManager() {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return null;
        if (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled()) return null;
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Project import",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progress of copying projects from Sketchware Pro");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
        return nm;
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(app, MainActivity.class);
        intent.putExtra(EXTRA_SHOW_IMPORT_PROGRESS, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(app, NOTIFICATION_ID, intent, flags);
    }

    @SuppressWarnings("deprecation")
    private Notification.Builder builder() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(app, CHANNEL_ID)
                : new Notification.Builder(app);
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setColor(0xFFf4511e)
                .setContentIntent(contentIntent())
                .setShowWhen(false);
        return builder;
    }

    private void postNotification(LegacyProjectMigrator.Progress progress, boolean force) {
        long now = android.os.SystemClock.uptimeMillis();
        if (!force && now - lastNotification < NOTIFICATION_INTERVAL_MS) return;
        lastNotification = now;
        NotificationManager nm = notificationManager();
        if (nm == null) return;
        Notification.Builder builder = builder()
                .setContentTitle(ImportTexts.headline(progress))
                .setContentText(ImportTexts.detail(progress))
                .setSubText(progress.percent() + "%")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress.percent(), progress.bytesTotal <= 0);
        try {
            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (RuntimeException ignored) {
            // Notifications are optional.
        }
    }

    private void postFinishedNotification(LegacyProjectMigrator.Result result) {
        NotificationManager nm = notificationManager();
        if (nm == null) return;
        Notification.Builder builder = builder()
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(result.isSuccess() ? "Import complete"
                        : result.cancelled ? "Import stopped" : "Import failed")
                .setContentText(ImportTexts.summary(result))
                .setAutoCancel(true);
        try {
            nm.notify(NOTIFICATION_ID + 1, builder.build());
        } catch (RuntimeException ignored) {
        }
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    // endregion
}
