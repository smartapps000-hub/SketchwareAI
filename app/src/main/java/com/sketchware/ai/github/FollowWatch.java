package com.sketchware.ai.github;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.sketchware.remod.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v2.1.5 (owner's 23 Nov review: "like YouTube: after Follow, that creator's new apps come with a notification"): every
 * few hours, with a network, Android runs this check: the store's apps from creators this account follows are compared
 * with the last look, and each new app or new version gets a notification (a tap opens its store page). No permission
 * is added (JobScheduler; the job isn't kept across a phone restart: the app schedules it again when it opens).
 * Notifications need Android 13+'s notification permission, which Follow asks for.
 */
public final class FollowWatch extends JobService {

    static final int JOB_ID = 72150;
    static final long PERIOD_MILLIS = 4 * 3600_000L;
    static final String CHANNEL = "followed_creators";
    private static final String PREFS = "playstore_follow_watch";

    /** Review 3 (LOW): the record of one account (a second account on the phone starts its own). */
    static android.content.SharedPreferences record(Context app, String me) {
        return app.getSharedPreferences(PREFS + "_" + me.toLowerCase(java.util.Locale.ROOT), Context.MODE_PRIVATE);
    }

    private volatile Thread worker;
    /** Review 2 (LOW): set by onStopJob (an interrupt alone can be cleared by the network library). */
    private volatile boolean stopped;

    /** Scheduled when signed in and following someone (again at each app start: harmless when already there). */
    public static void schedule(Context context) {
        Context app = context.getApplicationContext();
        String me = TokenStore.get(app).login();
        JobScheduler js = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        try {
            if (me == null || !PlayStore.noticesOn(app, me) || PlayStore.followedSet(app, me).isEmpty()) {
                js.cancel(JOB_ID);
                // review 3 (LOW): following no one: the record goes (a creator followed again later starts fresh)
                if (me != null && PlayStore.followedSet(app, me).isEmpty()) record(app, me).edit().clear().apply();
                return;
            }
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(app, FollowWatch.class))
                    .setPeriodic(PERIOD_MILLIS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build());
        } catch (RuntimeException ignored) {
            // the scheduler refused (too many jobs): the next app start tries again
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Context app = getApplicationContext();
        stopped = false;
        Thread t = new Thread(() -> {
            try {
                check(app, this);
            } catch (Throwable ignored) {
                // tried again at the next run
            } finally {
                jobFinished(params, false);
            }
        }, "FollowWatch");
        worker = t;
        t.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        Thread t = worker;
        if (t != null) t.interrupt();
        return true;
    }

    /** One look: the store's apps, the ones of followed creators compared with the last look, a notice for each new one. */
    static void check(Context app, FollowWatch job) throws Exception {
        String me = TokenStore.get(app).login();
        if (me == null || !PlayStore.noticesOn(app, me)) return;
        String token = GitHubSession.isRefused(app) ? null : TokenStore.get(app).token();
        // the following list itself, when the sign-in can read it (a Follow from github.com counts too)
        if (token != null) {
            try {
                PlayStore.saveFollowing(app, me, PlayStore.myFollowing(token));
            } catch (Exception ignored) {
                // the list on the phone stays
            }
        }
        Set<String> followed = PlayStore.followedSet(app, me);
        if (followed.isEmpty()) {
            record(app, me).edit().clear().commit();
            return;
        }
        List<PlayStore.App> found = PlayStore.search(token, null, null);
        // review (MEDIUM): news = a publish to the store (the store page's own time), never a backup's push; at most 40
        // pages read (15 without a sign-in: GitHub's limit for anonymous reads is 60 an hour)
        List<PlayStore.App> theirs = PlayStore.fromFollowed(found, followed);
        int limit = token == null ? 15 : 40;
        for (int i = 0; i < theirs.size() && i < limit; i++) {
            if (Thread.currentThread().isInterrupted() || (job != null && job.stopped)) return;
            try {
                PlayStore.readStorePage(token, theirs.get(i));
            } catch (java.io.IOException e) {
                theirs.get(i).publishedAt = 0;
            }
        }
        SharedPreferences prefs = record(app, me);
        Map<String, Long> seen = new HashMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getValue() instanceof Long) seen.put(e.getKey(), (Long) e.getValue());
        }
        if (job != null && job.stopped) return;
        // review 3 (LOW): first looks on GitHub's clock, as the store pages' publish times
        List<PlayStore.App> fresh = PlayStore.newFromFollowed(found, followed, seen, GitHubHttp.serverNow());
        // the whole record replaced (creators no longer followed go); review (LOW): written before the job ends
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (Map.Entry<String, Long> e : seen.entrySet()) edit.putLong(e.getKey(), e.getValue());
        edit.commit();
        int n = 0;
        for (PlayStore.App a : fresh) {
            if (n++ >= 5) break;
            notifyNew(app, a);
        }
    }

    static void notifyNew(Context app, PlayStore.App a) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Creators you follow",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(app, PlayStoreActivity.class)
                .putExtra(PlayStoreActivity.EXTRA_OWNER, a.owner)
                .putExtra(PlayStoreActivity.EXTRA_REPO, a.repo)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // review (LOW): its own range (CloudSync's end notices use 21000-25095)
        int id = 30000 + ((a.owner + "/" + a.repo).toLowerCase(java.util.Locale.ROOT).hashCode() & 0xfff);
        PendingIntent pi = PendingIntent.getActivity(app, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String status = a.status();
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(app, CHANNEL) : new android.app.Notification.Builder(app);
        b.setSmallIcon(R.drawable.ob_ic_store)
                .setContentTitle("New from @" + a.owner + ": " + a.name())
                .setContentText(status != null ? status : "Published on the Play Store")
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            // v2.1.6: switched off in Settings while this check ran: nothing shown
            if (!PlayStore.noticesOn(app, TokenStore.get(app).login())) return;
            nm.notify(id, b.build());
        } catch (RuntimeException ignored) {
            // notifications off
        }
    }

    /** v2.1.6: the follow notifications shown now (ids 30000-34095) go when the switch is turned off. */
    public static void clearNotices(NotificationManager nm) {
        try {
            for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications()) {
                if (n.getId() >= 30000 && n.getId() <= 30000 + 0xfff) nm.cancel(n.getTag(), n.getId());
            }
        } catch (RuntimeException ignored) {
            // nothing to clear
        }
    }
}
