package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.sketchware.ai.project.ProjectDiffAnalyzer;
import com.sketchware.remod.R;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.project.backup.BackupFactory;
import mod.jbk.util.LogUtil;

/**
 * v2.0.0: project backups to GitHub on a background thread for the whole app; v2.0.3: a queue of jobs (a tap goes
 * before the app's own backups, a second tap joins, everything ends in {@code finally}); backups after a save.
 * <p>
 * v2.0.4 (owner's 16 Nov directive "one repository per project, versioned releases, Publish to Public, foreground
 * dataSync service with Cancel / Retry, imports that never collide with open projects"):
 * <ul>
 * <li>a backup goes to the project's own repository as the release of its app version ({@link ProjectRepo}), with the
 * release notes the on-device analyzer writes from the change since the previous version
 * ({@link ProjectDiffAnalyzer}); the last built APK goes with it when it is newer than the one already up; a public
 * project's README, CHANGELOG and description are kept up to date at the same time;</li>
 * <li>"Publish Public" ({@link #publish}) and "Switch to Private" ({@link #makePrivate}): one PATCH of the
 * repository (the first backup is made first when the project has none);</li>
 * <li>imports run on a lane of their own (a download never waits behind a 100 MB backup): the file is downloaded into
 * a staging folder, checked, then restored as a new project (never over an open one) and linked to its repository
 * when it is one of the account's own;</li>
 * <li>every job can be cancelled ({@link #cancel}) and a failed one tried again ({@link #retry}); while jobs run, the
 * foreground service {@link CloudSyncService} shows the progress in a notification with Cancel, and a failure with
 * Retry.</li>
 * </ul>
 */
public final class CloudSync {

    static final String PREFS = "sketchware_ai_cloud_sync";

    /** Called on the main thread when one job ended. */
    public interface Result {
        void done(boolean ok, String message);
    }

    /** v2.0.2: a step of a job (0-100 and what is happening), on the main thread. */
    public interface Progress {
        void step(int percent, String text);
    }

    /** v2.0.1: what goes into the .swb (the Back up sheet's two boxes). */
    public static final class Options {
        /** Backups made by themselves: custom blocks yes, local libraries no. */
        public static final Options AUTOMATIC = new Options(false, true);

        public final boolean localLibraries;
        public final boolean customBlocks;

        public Options(boolean localLibraries, boolean customBlocks) {
            this.localLibraries = localLibraries;
            this.customBlocks = customBlocks;
        }
    }

    /** v2.0.4: what an import restores. */
    public static final class Import {
        /** The repository it comes from (owner / name), and its number when it is the account's own. */
        public String owner;
        public String repo;
        public long number;
        public boolean repoPrivate;
        /** A release of a project repository, or (old backups) a commit of a file in a repository. */
        public ProjectRepo.Version version;
        public String legacyPath;
        public String legacySha;
        public GitHubImport.Source link;
        public String linkPath;
        public long size;
        public boolean withLibraries;
        /** "owner/repo" of someone else's backup (its copied libraries are marked), or null for an own one. */
        public String librariesFrom;
        public String title;

        String key() {
            return "import:" + owner + "/" + repo + ":" + (version != null ? "r" + version.id
                    : legacySha != null ? legacyPath + "@" + legacySha : String.valueOf(linkPath));
        }
    }

    private static CloudSync instance;

    public static synchronized CloudSync get(Context context) {
        if (instance == null) instance = new CloudSync(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    /**
     * v2.0.3: one job of the queue. v2.0.4: also a publish or an import, cancellable, with the thread running it.
     */
    static final class Job {
        final String scId;
        final String kind;
        /** The project's name for the "waiting" line (may be null). */
        final String name;
        Options options;
        boolean user;
        boolean started;
        /**
         * Review (M3): a backup after a save joins only a job that hasn't started (a running one packed the project
         * before this save): otherwise it waits behind it as a job of its own.
         */
        boolean afterRunning;
        int percent = 1;
        String text = "Starting…";
        final java.util.List<Progress> watchers = new java.util.ArrayList<>();
        final java.util.List<Result> results = new java.util.ArrayList<>();
        /** v2.0.4 */
        volatile boolean cancelled;
        /**
         * v2.0.5 (owner's 16 Nov phone report: "1% • Waiting: another job goes first…"): an automatic job paused for a
         * tap (it goes back in the queue and resumes where it stopped), a job the watchdog stopped for making no
         * progress, a job whose thread didn't stop in time (the lane goes on without it), and when it last moved.
         */
        volatile boolean deferred, stalled, abandoned, finished, packing;
        volatile long lastActivity, stopAskedAt;
        /** v2.0.3: a remark added to the job's closing line (review L6: per job, two lanes run at once). */
        String note = "";
        volatile Thread thread;
        PublishDetails details;
        Import source;
        Store store;
        /**
         * v2.1.1 review (HIGH): the account the job was started under (set when it is queued); a Play Store publish or a
         * Publish never runs under another account (making a repository public needs that account's own "yes").
         */
        String login;
        /** v2.0.4: the repository page "Open on GitHub" opens, when known. */
        volatile String link;

        Job(String scId, String kind, String name, Options options, boolean user) {
            this.scId = scId;
            this.kind = kind;
            this.name = name;
            this.options = options;
            this.user = user;
        }

        /** What the job is (the notification's id and Retry's key). */
        String key() {
            if (STORE.equals(kind) && store != null) return STORE + ":" + store.number;
            return IMPORT.equals(kind) && source != null ? source.key() : kind + ":" + scId;
        }

        String title() {
            String n = name == null ? "project #" + scId : name;
            if (STORE.equals(kind)) return "Publishing " + n + " to the Play Store";
            if (IMPORT.equals(kind)) return "Importing " + n;
            if (PUBLISH.equals(kind)) return "Publishing " + n;
            return "Backing up " + n;
        }
    }

    static final String BACKUP = "backup";
    static final String PUBLISH = "publish";
    static final String IMPORT = "import";
    /** v2.1.1: a Play Store publish (its form and pictures staged on the phone). */
    static final String STORE = "store";

    /** v2.1.1: what a Play Store publish needs. */
    public static final class Store {
        /** Second review (LOW): the account the publish screen showed (the job runs only under it). */
        public String login;
        public String repo;
        public long number;
        public String title;
        /** The staged form and pictures (PlayStore.stagingDir). */
        public File staging;
        /** The local project linked to the repository, or null. */
        public String scId;
    }

    /** One queue and its worker thread. */
    private static final class Lane {
        final String name;
        final java.util.ArrayDeque<Job> queue = new java.util.ArrayDeque<>();
        Job current;
        Thread worker;

        Lane(String name) {
            this.name = name;
        }
    }

    /**
     * Backups and publishing you start, one at a time per lane. v2.1.0 (owner's 16 Nov directive "2-3 simultaneous
     * backups"): two lanes for jobs you start and one for the automatic backups, so up to three projects upload at once;
     * one project's jobs always share a lane ({@link #laneFor}), so they never overlap.
     */
    private final Lane work = new Lane("CloudSync");
    private final Lane work2 = new Lane("CloudSync2");
    /** v2.1.0: backups the app starts by itself (after saving, pending ones). */
    private final Lane auto = new Lane("CloudSyncAuto");
    /** Imports, one at a time, beside the backups (any number wait in its queue). */
    private final Lane imports = new Lane("CloudSyncImport");

    private Lane[] lanes() {
        return new Lane[]{work, work2, auto, imports};
    }

    private Lane[] workLanes() {
        return new Lane[]{work, work2, auto};
    }

    /** The lane {@code job} lives in (running or waiting), else null. */
    private Lane laneHolding(Job job) {
        for (Lane lane : lanes()) {
            if (lane.current == job || lane.queue.contains(job)) return lane;
        }
        return null;
    }

    /**
     * v2.1.0: where a new job goes: an import to the imports lane; a job of a project that has a job in a lane already
     * to that lane (after it); a job you start to the less busy of the two lanes for taps; an automatic backup to its
     * own lane.
     */
    private Lane laneFor(Job job) {
        if (IMPORT.equals(job.kind)) return imports;
        for (Lane lane : workLanes()) {
            if (lane.current != null && lane.current.scId.equals(job.scId) && !lane.current.finished) return lane;
            for (Job j : lane.queue) if (j.scId.equals(job.scId)) return lane;
        }
        if (!job.user) return auto;
        return load(work2) < load(work) ? work2 : work;
    }

    private static int load(Lane lane) {
        return (lane.current != null ? 1 : 0) + lane.queue.size();
    }

    /** Tests: a job's work instead of the real one ({@link #run}), and where results go instead of the main thread. */
    interface Runner {
        String run(Job job) throws Exception;
    }

    static volatile Runner runnerForTests;
    static volatile java.util.concurrent.Executor mainForTests;

    private void post(Runnable r) {
        java.util.concurrent.Executor direct = mainForTests;
        if (direct != null) direct.execute(r);
        else main.post(r);
    }

    CloudSync(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ pending

    /** Projects waiting for their first backup (created offline). */
    public synchronized Set<String> pending() {
        return new HashSet<>(prefs.getStringSet("pending", new HashSet<>()));
    }

    synchronized void addPending(String scId) {
        addPending(scId, Options.AUTOMATIC);
    }

    /** v2.0.1 (review): a waiting backup keeps the Back up sheet's boxes for the later upload. */
    synchronized void addPending(String scId, Options options) {
        Set<String> set = pending();
        set.add(scId);
        prefs.edit().putStringSet("pending", set)
                .putString("pending_options_" + scId, (options.localLibraries ? "L" : "") + (options.customBlocks ? "C" : ""))
                .apply();
    }

    synchronized Options pendingOptions(String scId) {
        String flags = prefs.getString("pending_options_" + scId, null);
        if (flags == null) return Options.AUTOMATIC;
        return new Options(flags.contains("L"), flags.contains("C"));
    }

    synchronized void removePending(String scId) {
        Set<String> set = pending();
        if (set.remove(scId)) prefs.edit().putStringSet("pending", set).remove("pending_options_" + scId).apply();
    }


    /** v2.0.3: a job of this project is queued or running. */
    public synchronized boolean isRunning(String scId) {
        return find(scId, BACKUP) != null || find(scId, PUBLISH) != null;
    }

    private Job find(String scId, String kind) {
        for (Lane lane : workLanes()) {
            if (lane.current != null && lane.current.scId.equals(scId) && lane.current.kind.equals(kind)) return lane.current;
        }
        return findQueued(scId, kind);
    }

    private Job findQueued(String scId, String kind) {
        for (Lane lane : workLanes()) {
            for (Job job : lane.queue) {
                if (job.scId.equals(scId) && job.kind.equals(kind)) return job;
            }
        }
        return null;
    }

    private Job findImport(String key) {
        if (imports.current != null && key.equals(imports.current.key())) return imports.current;
        for (Job job : imports.queue) if (key.equals(job.key())) return job;
        return null;
    }

    // ------------------------------------------------------------------ entry points

    /** The project card's Back up > "To GitHub": sign in first when needed, then the backup with toasts. */
    public static void backUpFromMenu(Activity activity, String scId, String name) {
        backUpFromMenu(activity, scId, name, Options.AUTOMATIC);
    }

    /**
     * v2.0.1: the same with the Back up sheet's boxes. v2.0.3: a backup of this project already queued or running is
     * followed. v2.0.4: the sheet has Hide (the notification goes on), Cancel and, after a failure, Retry.
     */
    public static void backUpFromMenu(Activity activity, String scId, String name, Options options) {
        CloudSync sync = get(activity);
        if (TokenStore.get(activity).login() == null) {
            GitHubInAppAuthDialog.show(activity, login -> backUpFromMenu(activity, scId, name, options));
            return;
        }
        Job job = new Job(scId, BACKUP, name, options == null ? Options.AUTOMATIC : options, true);
        follow(activity, sync, job, R.drawable.ob_ic_github, "Back up " + name);
    }

    /** A progress sheet following {@code job} (queued here, or the same job already queued or running). */
    static GitHubProgress follow(Activity activity, CloudSync sync, Job job, int icon, String title) {
        GitHubProgress progress = GitHubProgress.show(activity, icon, title);
        progress.update(1, "Starting…");
        String key = job.key();
        progress.actions(() -> sync.cancel(key), () -> {
            Job again = sync.retryJob(key);
            if (again != null) {
                if (sync.blocked(again)) {
                    // review (HIGH): Sign in again with another account: that account didn't ask for this
                    sync.failed.put(key, again);
                    progress.failed(sync.blockedText(again), false);
                    return;
                }
                progress.restart();
                sync.enqueue(again, progress::update, ending(sync, progress, key));
            } else if (sync.findByKey(key) != null) {
                // v2.1.1: Sign in again already resumed it (resumeAfterSignIn): the sheet follows that run
                progress.restart();
                if (!sync.join(key, progress::update, ending(sync, progress, key))) {
                    progress.failed("That run already ended: its notification says how.", false);
                }
            } else if (TokenStore.get(sync.app).login() == null) {
                // second review (MEDIUM): signed out meanwhile (Sign out forgets the waiting jobs)
                progress.failed("You signed out, so it didn't run. Sign in and start it again.", false);
            } else if (sync.blocked(job)) {
                progress.failed(sync.blockedText(job), false);
            } else {
                // review (LOW): the run the sign-in started already ended
                progress.failed("It ran again after the sign-in: its notification says how it ended.", false);
            }
        }, job.link);
        sync.enqueue(job, progress::update, ending(sync, progress, key));
        Job running = sync.findByKey(key);
        if (running != null && running.link != null) progress.link(running.link);
        return progress;
    }

    private static Result ending(CloudSync sync, GitHubProgress progress, String key) {
        return (ok, message) -> {
            if (progress.hidden()) {
                Toast.makeText(sync.app, message, Toast.LENGTH_LONG).show();
            } else if (ok) {
                progress.done(message);
            } else {
                // review L3: Retry only when there is something to try again (never a button that does nothing);
                // v2.0.5: a refused sign-in offers Sign in again (then the job runs again)
                if (sync.needsSignIn(key)) progress.failedSignIn(message);
                else progress.failed(message, sync.canRetry(key));
            }
        };
    }

    /** A project was just created: back it up when signed in, else nothing; offline it waits. */
    public void projectCreated(String scId) {
        if (scId == null || TokenStore.get(app).login() == null) return;
        if (GitHubSession.isRefused(app)) {
            addPending(scId);
            return;
        }
        enqueue(new Job(scId, BACKUP, null, Options.AUTOMATIC, false), null, (ok, message) -> {
            // a failure that waits says nothing (it is tried again); one that won't work again is shown
            if (ok || !pending().contains(scId)) Toast.makeText(app, message, Toast.LENGTH_SHORT).show();
        });
    }

    private long lastPendingTry;

    /** {@link #syncPending} from the main screen: at most every ten minutes, and only when something waits. */
    private static volatile boolean resumeCleaned;

    public void syncPendingSoon() {
        if (!resumeCleaned) {
            resumeCleaned = true;
            Thread t = new Thread(this::cleanResumeFiles, "CloudSyncResumeClean");
            t.setDaemon(true);
            t.start();
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastPendingTry != 0 && now - lastPendingTry < 10 * 60_000L) return;
        if (pending().isEmpty()) return;
        lastPendingTry = now;
        syncPending();
    }

    /** Uploads the projects that are waiting (signed in only), after any backup a tap started. */
    public void syncPending() {
        if (TokenStore.get(app).login() == null || GitHubSession.isRefused(app)) return;
        for (String scId : pending()) {
            enqueue(new Job(scId, BACKUP, null, pendingOptions(scId), false), null, (ok, message) -> {
                if (ok) Toast.makeText(app, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    /** One project, in the background; {@code result} on the main thread. */
    public void backUp(String scId, Result result) {
        backUp(scId, Options.AUTOMATIC, result);
    }

    /** v2.0.1: one project with the given contents. */
    public void backUp(String scId, Options options, Result result) {
        backUp(scId, options, null, result);
    }

    /** v2.0.2: the same, with its steps reported on the main thread (started by the app: after a tap's backups). */
    public void backUp(String scId, Options options, Progress progress, Result result) {
        enqueue(new Job(scId, BACKUP, null, options == null ? Options.AUTOMATIC : options, false), progress, result);
    }

    /**
     * v2.0.4: "Publish Public" of the publish screen: the project's repository gets {@code details} (description,
     * topics, README, CHANGELOG, the newest version's notes) and is made public; a project without a version on GitHub
     * is backed up first.
     */
    public static GitHubProgress publish(Activity activity, String scId, String name, PublishDetails details) {
        CloudSync sync = get(activity);
        Job job = new Job(scId, PUBLISH, name, ProjectLinks.lastOptions(scId) == null ? Options.AUTOMATIC
                : ProjectLinks.lastOptions(scId), true);
        job.details = details;
        return follow(activity, sync, job, R.drawable.ob_ic_public, "Publish " + name);
    }

    /**
     * v2.1.1 (owner's 21 Nov directive): a Play Store publish as a job of the queue: its progress in the sheet and the
     * notification, a refused sign-in pauses it and it goes on by itself after Sign in again (the form and pictures stay
     * staged on the phone until it worked).
     */
    public static GitHubProgress publishToStore(Activity activity, Store store) {
        CloudSync sync = get(activity);
        Job job = new Job(store.scId != null ? store.scId : "store-" + store.number, STORE, store.title,
                Options.AUTOMATIC, true);
        job.store = store;
        job.login = store.login;
        job.link = ProjectRepo.link(store.login != null ? store.login : TokenStore.get(activity).login(), store.repo);
        return follow(activity, sync, job, R.drawable.ob_ic_store, "Publishing " + store.title);
    }

    /** v2.1.1 review (LOW): the same without a screen (it closed meanwhile): the notification follows it, a toast ends it. */
    public static void publishToStoreQuietly(Context context, Store store) {
        CloudSync sync = get(context);
        Job job = new Job(store.scId != null ? store.scId : "store-" + store.number, STORE, store.title,
                Options.AUTOMATIC, true);
        job.store = store;
        job.login = store.login;
        job.link = ProjectRepo.link(store.login != null ? store.login : TokenStore.get(context).login(), store.repo);
        sync.enqueue(job, null, (ok, message) -> Toast.makeText(sync.app, message, Toast.LENGTH_LONG).show());
    }

    /**
     * v2.1.1: after a new sign-in: every job a refused sign-in stopped runs again by itself (a backup from its
     * checkpoint, a Play Store publish from its staged files), then the backups that wait.
     */
    public void resumeAfterSignIn() {
        java.util.List<String> keys = new java.util.ArrayList<>(signInNeeded);
        for (String key : keys) {
            Job again = retryJob(key);
            signInNeeded.remove(key);
            if (again == null) continue;
            // review (HIGH): another account signed in: that account never said yes to this job; it stays for its own
            // account's Retry (second review: the sheet then says why it didn't run)
            if (blocked(again)) {
                failed.put(key, again);
                continue;
            }
            enqueue(again, null, (ok, message) -> Toast.makeText(app, message, Toast.LENGTH_LONG).show());
        }
        lastPendingTry = 0;
        syncPending();
    }

    /** v2.0.4: an import into a new project, with its sheet. */
    public static GitHubProgress importProject(Activity activity, Import source) {
        CloudSync sync = get(activity);
        Job job = new Job("import", IMPORT, source.title, Options.AUTOMATIC, true);
        job.source = source;
        job.link = source.owner != null && source.repo != null ? ProjectRepo.link(source.owner, source.repo) : null;
        return follow(activity, sync, job, R.drawable.ob_ic_github, "Import " + (source.title == null ? source.repo
                : source.title));
    }

    /**
     * v2.0.4: the Public pill's "Switch to Private": one PATCH, at once (never behind a running backup). {@code done}
     * hears the end on the main thread.
     */
    public static void makePrivate(Activity activity, String scId, Result done) {
        CloudSync sync = get(activity);
        if (sync.isRunning(scId)) {
            // review M3: a running Publish would make it public again right after
            done.done(false, "A backup or Publish of this project is running: switch it to Private when it has "
                    + "ended (or cancel it in its notification).");
            return;
        }
        Thread t = new Thread(() -> {
            boolean ok = false;
            String message;
            try {
                String[] session = sync.session();
                sync.visibilityChanged(scId);
                ProjectRepo repo = ProjectRepo.open(session[1], session[0], scId);
                if (repo != null) {
                    repo.setPrivate(true);
                    message = "Private: the project's repository opens only for you now.";
                } else if (ProjectRepo.gone(session[1], ProjectLinks.repoNumber(scId, session[0]))) {
                    message = "Private: its repository isn't on GitHub any more (nothing there to hide).";
                } else {
                    // review L1: moved to another owner or an organisation: never say Private when nothing changed
                    throw new GitHubException("not_found", "The project's repository isn't on your account any more "
                            + "(moved to another owner or an organisation?). Nothing was changed: make it private on "
                            + "github.com.");
                }
                sync.visibilityChanged(scId);
                ProjectLinks.visibilityNow(scId, session[0], true);
                ok = true;
            } catch (Exception e) {
                message = GitHubImportDialog.text(e);
            }
            boolean success = ok;
            String text = message;
            sync.post(() -> done.done(success, text));
        }, "CloudSyncPrivate");
        t.setDaemon(true);
        t.start();
    }

    /**
     * v2.0.3: queues {@code job}, or joins the same job already queued or running: {@code progress} hears its steps
     * (the current one at once) and {@code result} its end, both on the main thread. A tap's job goes before the jobs
     * the app made by itself; a tap joining an automatic job that hasn't started moves it forward and gives it the
     * tap's boxes. v2.0.4: imports on their own lane.
     */
    void enqueue(Job job, Progress progress, Result result) {
        if (job.login == null) job.login = TokenStore.get(app).login();
        if (job.user && runnerForTests == null) {
            // v2.1.3: a tap (Retry, Back up again) takes the job's earlier "Didn't work" notice away at once
            try {
                CloudSyncService.clearFinished(app, job.key());
            } catch (Throwable t) {
                LogUtil.d("CloudSync", "No notification: " + t);
            }
        }
        Job target;
        int ahead;
        Lane lane;
        synchronized (this) {
            if (IMPORT.equals(job.kind)) target = findImport(job.key());
            else target = job.afterRunning ? findQueued(job.scId, job.kind) : find(job.scId, job.kind);
            Lane holding = target == null ? null : laneHolding(target);
            lane = holding != null ? holding : laneFor(job);
            if (target == null) {
                target = job;
                if (job.user) {
                    // after the other taps' jobs, before the automatic ones
                    java.util.ArrayDeque<Job> users = new java.util.ArrayDeque<>();
                    while (!lane.queue.isEmpty() && lane.queue.peekFirst().user) users.addLast(lane.queue.pollFirst());
                    lane.queue.addFirst(job);
                    while (!users.isEmpty()) lane.queue.addFirst(users.pollLast());
                } else {
                    lane.queue.addLast(job);
                }
            } else if (job.user && !target.started) {
                target.options = job.options;
                if (job.details != null) target.details = job.details;
                if (!target.user) {
                    target.user = true;
                    lane.queue.remove(target);
                    java.util.ArrayDeque<Job> users = new java.util.ArrayDeque<>();
                    while (!lane.queue.isEmpty() && lane.queue.peekFirst().user) users.addLast(lane.queue.pollFirst());
                    lane.queue.addFirst(target);
                    while (!users.isEmpty()) lane.queue.addFirst(users.pollLast());
                }
            }
            if (progress != null) target.watchers.add(progress);
            if (result != null) target.results.add(result);
            // v2.0.5: a tap never waits behind an automatic backup: that one pauses (its parts on GitHub are kept,
            // it resumes after the taps' jobs)
            Job running = lane.current;
            if (target.user && !target.started && running != null && running != target && !running.user
                    && !running.deferred && !running.finished && BACKUP.equals(running.kind)) {
                running.deferred = true;
                running.stopAskedAt = android.os.SystemClock.elapsedRealtime();
                GitHubHttp.cancel(running.thread);
                ensureWatchdog();
            }
            ahead = target.started ? 0 : positionOf(lane, target);
            ensureWorker(lane);
        }
        if (progress != null) {
            Job j = target;
            if (ahead > 0) {
                waiting(lane, j, ahead);
            } else {
                post(() -> progress.step(j.percent, j.text));
            }
        }
    }

    /** How many jobs run or wait before {@code job} (0 = it is next or running). */
    private int positionOf(Lane lane, Job job) {
        int n = lane.current != null && lane.current != job ? 1 : 0;
        for (Job j : lane.queue) {
            if (j == job) return n;
            n++;
        }
        return n;
    }

    private void waiting(Lane lane, Job job, int ahead) {
        Job running;
        synchronized (this) {
            running = lane.current;
        }
        // v2.0.5: which job, and how far it is
        if (running != null && running.deferred && ahead <= 1) {
            step(job, 1, "Waiting: " + running.title() + " (" + running.percent + "%) pauses for this one…");
            return;
        }
        String other = running == null ? "another job" : running.title() + " (" + running.percent + "%)";
        step(job, 1, "Waiting: " + other + (ahead > 1 ? " and " + (ahead - 1) + " more go first" : " goes first") + "…");
    }

    /**
     * v2.1.1: a screen follows a job already waiting or running with {@code key} (its sheet after Sign in again, when
     * the sign-in itself resumed the job); false when there is none.
     */
    synchronized boolean join(String key, Progress progress, Result result) {
        Job j = findByKey(key);
        if (j == null || j.finished) return false;
        if (progress != null) j.watchers.add(progress);
        if (result != null) j.results.add(result);
        return true;
    }

    synchronized Job findByKey(String key) {
        for (Lane lane : lanes()) {
            if (lane.current != null && key.equals(lane.current.key())) return lane.current;
            for (Job j : lane.queue) if (key.equals(j.key())) return j;
        }
        return null;
    }

    // ------------------------------------------------------------------ v2.0.4: cancel and retry

    /**
     * Cancels the job with {@code key}: a waiting one leaves the queue, a running one stops at its next 32 KB (its
     * connection is closed). Nothing half-made is kept: a release that wasn't published is a draft, removed by the
     * next backup; a download's file is deleted.
     */
    public void cancel(String key) {
        Job waiting = null;
        synchronized (this) {
            for (Lane lane : lanes()) {
                if (lane.current != null && key.equals(lane.current.key())) {
                    lane.current.cancelled = true;
                    lane.current.stopAskedAt = android.os.SystemClock.elapsedRealtime();
                    GitHubHttp.cancel(lane.current.thread);
                    // v2.0.5: a thread that doesn't stop (packing, a slow disk) is left behind; the lane goes on
                    ensureWatchdog();
                    return;
                }
                for (java.util.Iterator<Job> it = lane.queue.iterator(); it.hasNext(); ) {
                    Job j = it.next();
                    if (key.equals(j.key())) {
                        it.remove();
                        waiting = j;
                        break;
                    }
                }
                if (waiting != null) break;
            }
        }
        if (waiting != null) finish(waiting, false, "Cancelled.", false);
    }

    /** review M3: when a project's visibility was last written by Switch to Private or Publish (elapsed time). */
    private final java.util.Map<String, Long> visibilityAt = new java.util.concurrent.ConcurrentHashMap<>();

    void visibilityChanged(String scId) {
        visibilityAt.put(scId, android.os.SystemClock.elapsedRealtime());
    }

    boolean visibilityChangedSince(String scId, long since) {
        Long at = visibilityAt.get(scId);
        return at != null && at >= since;
    }

    /** Jobs that failed and can be tried again, by key (the notification's Retry and the sheet's). */
    private final java.util.Map<String, Job> failed = new java.util.concurrent.ConcurrentHashMap<>();

    /** A copy of the failed job {@code key} to queue again (a tap's), or null. */
    Job retryJob(String key) {
        Job old = failed.remove(key);
        return old == null ? null : retryCopy(old);
    }

    /** The notification's Retry: the failed job again, followed by a toast at its end. */
    public void retry(String key) {
        Job again = retryJob(key);
        if (again == null) return;
        if (blocked(again)) {
            // second review (LOW): the notification's Retry follows the same account rule as the sheet
            failed.put(key, again);
            Toast.makeText(app, blockedText(again), Toast.LENGTH_LONG).show();
            return;
        }
        enqueue(again, null, (ok, message) -> Toast.makeText(app, message, Toast.LENGTH_LONG).show());
    }

    /**
     * v2.0.5: a 401 outside the queue (Save details): when GET /user refuses the sign-in too, it is marked refused
     * (the card says so, backups wait); the message to show.
     */
    String refusedNow(GitHubException e) {
        try {
            String[] session = session();
            if (!new CloudBackup(session[1], session[0]).refusesSignIn()) {
                return e.getMessage() + " GitHub didn't refuse the sign-in itself: try again.";
            }
            String note = GitHubSession.refusedNote(app, null);
            GitHubSession.markRefused(app, note, session[2]);
            return note;
        } catch (Exception other) {
            return e.getMessage();
        }
    }

    /** v2.0.5: jobs that failed because GitHub refused the saved sign-in (they run again after Sign in again). */
    private final java.util.Set<String> signInNeeded = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<>());

    boolean needsSignIn(String key) {
        return key != null && signInNeeded.contains(key);
    }

    /** Whether the failed job {@code key} can be tried again (it failed in a way worth another try). */
    boolean canRetry(String key) {
        return key != null && failed.containsKey(key);
    }

    /** review L3: what Retry needs of a failed job, without its screens (watchers and results hold activities). */
    private static Job retryCopy(Job old) {
        Job again = new Job(old.scId, old.kind, old.name, old.options, true);
        again.details = old.details;
        again.source = old.source;
        again.store = old.store;
        again.link = old.link;
        again.login = old.login;
        return again;
    }

    // ------------------------------------------------------------------ the worker

    private void loop(Lane lane) {
        Thread me = Thread.currentThread();
        while (true) {
            Job job;
            synchronized (this) {
                // v2.0.5: a worker left behind by the watchdog (its job didn't stop) ends here
                if (lane.worker != me) return;
                job = lane.queue.pollFirst();
                lane.current = job;
                if (job == null) {
                    lane.worker = null;
                    break;
                }
                job.started = true;
                // a cancel of an earlier job must not stop this one; cleared before the thread is published, so a
                // cancel of this job from now on always lands (review L2)
                Thread.interrupted();
                GitHubHttp.endJob();
                job.thread = me;
                job.lastActivity = android.os.SystemClock.elapsedRealtime();
            }
            ensureWatchdog();
            boolean ok = false;
            boolean retryable = false;
            boolean requeueNow = false;
            String message;
            service(job, true);
            // v2.1.3 (owner's 22 Nov report: Retry ran but "Didn't work: Vehicle_Parking" stayed): the end notice of
            // this job's earlier run goes as soon as it runs again; only a new end is shown
            if (runnerForTests == null) {
                try {
                    CloudSyncService.clearFinished(app, job.key());
                } catch (Throwable t) {
                    LogUtil.d("CloudSync", "No notification: " + t);
                }
            }
            try {
                message = runWithRetries(job);
                ok = true;
            } catch (MissingProject e) {
                // deleted meanwhile: nothing to wait for
                removePending(job.scId);
                clearResume(job.scId);
                message = e.getMessage();
            } catch (OtherAccount e) {
                message = e.getMessage();
            } catch (GitHubHttp.Cancelled e) {
                message = stoppedMessage(job);
                if (job.stalled) {
                    retryable = true;
                    if (BACKUP.equals(job.kind)) addPending(job.scId, job.options);
                }
            } catch (IOException e) {
                if (job.cancelled || job.deferred || job.stalled) {
                    message = stoppedMessage(job);
                    if (job.stalled) {
                        retryable = true;
                        if (BACKUP.equals(job.kind)) addPending(job.scId, job.options);
                    }
                } else if (SyncDoctor.isStorageFull(e)) {
                    // v2.0.5: a full phone is not "no connection"
                    if (BACKUP.equals(job.kind)) addPending(job.scId, job.options);
                    retryable = true;
                    message = "The phone's storage is full (" + e.getMessage() + ")."
                            + SyncDoctor.forThrowable(e);
                } else {
                    // no network (or GitHub didn't answer): a backup waits for the next chance, with the same boxes
                    if (BACKUP.equals(job.kind)) addPending(job.scId, job.options);
                    retryable = true;
                    message = "No connection to GitHub (" + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()) + "). "
                            + (BACKUP.equals(job.kind) ? "You stay signed in; the backup waits for the next chance."
                            : "You stay signed in; try again.");
                    LogUtil.d("CloudSync", "Job " + job.kind + " " + job.scId + " waits: " + e);
                }
            } catch (GitHubException e) {
                if ("refused".equals(e.code) || "unreadable".equals(e.code) || "http_401".equals(e.code)) {
                    signInNeeded.add(job.key());
                }
                if (BACKUP.equals(job.kind) && ("keystore".equals(e.code) || "unauthorized".equals(e.code)
                        || "refused".equals(e.code) || "unreadable".equals(e.code) || SignIn.isPassing(e.code))) {
                    // worth another try: a Keystore hiccup, a server error / rate limit, or a sign-in to come
                    addPending(job.scId, job.options);
                }
                retryable = true;
                // v2.0.5: what to do, when the message doesn't say it
                message = SyncDoctor.withAdvice(e.code, e.getMessage());
            } catch (Throwable t) {
                // also an Error (out of memory with a large project): the job ends, the queue goes on
                LogUtil.e("CloudSync", "Job " + job.kind + " " + job.scId + " failed", t);
                retryable = true;
                message = (BACKUP.equals(job.kind) ? "The backup didn't work: " : "That didn't work: ") + t
                        + SyncDoctor.forThrowable(t);
            } finally {
                synchronized (this) {
                    // v2.1.0 review (LOW): a paused job goes back into its lane in the same lock that frees the lane,
                    // so a job of that project enqueued meanwhile can't land in another lane and run beside it
                    requeueNow = job.deferred && !ok && !job.cancelled && !job.abandoned && lane.worker == me;
                    if (requeueNow) requeueDeferred(lane, job);
                    if (lane.current == job) lane.current = null;
                    job.thread = null;
                }
                GitHubHttp.release();
                GitHubHttp.endJob();
            }
            // second review (LOW): a paused job (already back in its lane: its deferred mark cleared) counts as stopped
            if (!ok && (job.cancelled || requeueNow || job.deferred || job.stalled)) {
                // review (MEDIUM): a cancel / pause / stop that came out as another error (a wrapped I/O error) is
                // still that stop
                message = stoppedMessage(job);
                retryable = job.stalled;
                if (job.stalled && BACKUP.equals(job.kind)) addPending(job.scId, job.options);
                signInNeeded.remove(job.key());
            }
            boolean requeued = false;
            synchronized (this) {
                // its end was already told by the watchdog, and the lane has another worker now
                if (job.abandoned || lane.worker != me) return;
                // v2.0.5: paused for a tap: back in the queue after the taps' jobs (it resumes from its parts);
                // v2.1.0: already put back in the finally above
                requeued = requeueNow;
            }
            if (requeued) {
                // review (LOW): a screen following the paused job hears it
                step(job, 1, "Paused for a backup you started; it goes on after it…");
                continue;
            }
            // review (LOW): a tap that joined an automatic job already running follows it: it can Retry too
            boolean followed;
            synchronized (this) {
                followed = job.user || !job.watchers.isEmpty();
            }
            if (ok) failed.remove(job.key());
            else if (retryable && followed) failed.put(job.key(), retryCopy(job));
            if (ok) signInNeeded.remove(job.key());
            if (!job.finished) {
                job.finished = true;
                finish(job, ok, message, retryable && followed);
            }
            // v2.1.0 review (LOW): the shared notification follows the jobs still running in the other lanes
            Job stillRunning = null;
            synchronized (this) {
                for (Lane l : lanes()) {
                    if (l.current != null && !l.current.finished && l.current != job) {
                        stillRunning = l.current;
                        break;
                    }
                }
            }
            if (stillRunning != null) service(stillRunning, true);
            // the ones still waiting: their place moved up
            java.util.List<Job> waitingJobs;
            synchronized (this) {
                waitingJobs = new java.util.ArrayList<>(lane.queue);
            }
            for (Job w : waitingJobs) {
                if (!w.watchers.isEmpty()) {
                    int ahead;
                    synchronized (this) {
                        ahead = positionOf(lane, w);
                    }
                    if (ahead > 0) waiting(lane, w, ahead);
                }
            }
        }
        serviceIdle();
    }

    /** v2.0.5: the closing line of a job that was stopped (Cancel, the watchdog, or paused for a tap). */
    private static String stoppedMessage(Job job) {
        if (job.stalled) {
            return "Stopped: nothing moved for " + (STALL_MS / 60_000) + " minutes (the network?). "
                    + (BACKUP.equals(job.kind) ? "The backup waits for the next chance; its finished parts are kept."
                    : "Try again.");
        }
        return "Cancelled.";
    }

    /** A paused automatic job goes back, right after the taps' jobs, as it was (same watchers and results). */
    private void requeueDeferred(Lane lane, Job job) {
        job.started = false;
        job.deferred = false;
        job.stalled = false;
        job.percent = 0;
        job.text = "Waiting…";
        java.util.ArrayDeque<Job> users = new java.util.ArrayDeque<>();
        while (!lane.queue.isEmpty() && lane.queue.peekFirst().user) users.addLast(lane.queue.pollFirst());
        lane.queue.addFirst(job);
        while (!users.isEmpty()) lane.queue.addFirst(users.pollLast());
    }

    private void ensureWorker(Lane lane) {
        synchronized (this) {
            if (lane.worker == null || !lane.worker.isAlive()) {
                lane.worker = new Thread(() -> loop(lane), lane.name);
                lane.worker.setDaemon(true);
                lane.worker.start();
            }
        }
    }

    // ------------------------------------------------------------------ v2.0.5: the watchdog

    /** No step, no byte for this long (not while packing): the job is stopped and the queue goes on. */
    static long STALL_MS = 5 * 60_000L;
    /** A stopped job's thread that hasn't returned after this long is left behind; the lane gets a new worker. */
    static long ABANDON_MS = 20_000L;
    private Thread watchdog;

    private void ensureWatchdog() {
        synchronized (this) {
            if (watchdog != null && watchdog.isAlive()) return;
            watchdog = new Thread(this::watch, "CloudSyncWatchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    private void watch() {
        while (true) {
            try {
                Thread.sleep(Math.min(15_000L, Math.max(200L, ABANDON_MS / 4)));
            } catch (InterruptedException e) {
                return;
            }
            java.util.List<Object[]> left = new java.util.ArrayList<>();
            synchronized (this) {
                boolean any = false;
                long now = android.os.SystemClock.elapsedRealtime();
                for (Lane lane : lanes()) {
                    Job j = lane.current;
                    if (j == null) continue;
                    any = true;
                    if (!j.stalled && !j.cancelled && !j.deferred && !j.packing && now - j.lastActivity > STALL_MS) {
                        j.stalled = true;
                        j.stopAskedAt = now;
                        GitHubHttp.cancel(j.thread);
                    } else if ((j.stalled || j.cancelled || j.deferred) && !j.packing && j.stopAskedAt > 0
                            && now - j.stopAskedAt > ABANDON_MS) {
                        // its thread didn't stop: the lane goes on without it
                        j.abandoned = true;
                        lane.current = null;
                        lane.worker = null;
                        if (j.deferred && !j.cancelled) {
                            requeueDeferred(lane, copyOf(j));
                        } else {
                            left.add(new Object[]{j});
                        }
                        ensureWorkerLocked(lane);
                    }
                }
                if (!any) {
                    watchdog = null;
                    return;
                }
            }
            for (Object[] o : left) {
                Job j = (Job) o[0];
                if (j.stalled && BACKUP.equals(j.kind)) addPending(j.scId, j.options);
                if (j.stalled && j.user) failed.put(j.key(), retryCopy(j));
                if (!j.finished) {
                    j.finished = true;
                    finish(j, false, stoppedMessage(j), j.stalled && j.user);
                }
            }
        }
    }

    private void ensureWorkerLocked(Lane lane) {
        lane.worker = new Thread(() -> loop(lane), lane.name);
        lane.worker.setDaemon(true);
        lane.worker.start();
    }

    /** A paused job left behind: the same job again (its watchers and results move with it). */
    private static Job copyOf(Job old) {
        Job again = retryCopy(old);
        again.user = old.user;
        again.afterRunning = old.afterRunning;
        again.watchers.addAll(old.watchers);
        again.results.addAll(old.results);
        old.finished = true;
        return again;
    }

    private void finish(Job job, boolean ok, String message, boolean retryable) {
        java.util.List<Result> results;
        synchronized (this) {
            results = new java.util.ArrayList<>(job.results);
        }
        String text = message;
        post(() -> {
            for (Result r : results) {
                try {
                    r.done(ok, text);
                } catch (RuntimeException e) {
                    LogUtil.e("CloudSync", "A result listener failed", e);
                }
            }
        });
        // review (LOW): while GitHub refuses the sign-in, automatic jobs end quietly (the card and the pending list say it)
        boolean quiet = !ok && !job.user && GitHubSession.isRefused(app);
        if (runnerForTests == null && !quiet && (job.user || !ok && job.started)) {
            try {
                CloudSyncService.finished(app, job.key(), job.title(), ok, text, retryable, job.link);
            } catch (Throwable t) {
                LogUtil.d("CloudSync", "No notification: " + t);
            }
        }
    }

    /**
     * v2.1.3 (owner's 22 Nov report: a backup failed with UnknownHostException "uploads.github.com" and needed a Retry
     * tap): after a lost connection a backup or an import runs again by itself, up to three times, after these pauses
     * and once a network is back (up to two minutes each). A backup goes on from where it stopped: the packed file is
     * kept and the parts already on GitHub aren't sent again (checkpoint resume). Only then does it end with Retry.
     */
    static long[] AUTO_RETRY_MS = {5_000L, 15_000L, 30_000L};
    static long NETWORK_WAIT_MS = 120_000L;
    /** Tests: the automatic tries also with the test runner. */
    static boolean autoRetryInTests;

    private String runWithRetries(Job job) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                return run(job);
            } catch (IOException e) {
                if (attempt >= AUTO_RETRY_MS.length || !autoRetry(job, e)) throw e;
                waitToRetry(job, e, attempt);
                job.lastActivity = android.os.SystemClock.elapsedRealtime();
            }
        }
    }

    private boolean autoRetry(Job job, IOException e) {
        if (runnerForTests != null && !autoRetryInTests) return false;
        if (!BACKUP.equals(job.kind) && !IMPORT.equals(job.kind)) return false;
        if (job.cancelled || job.deferred || job.stalled || GitHubHttp.isCancelled()) return false;
        return !(e instanceof GitHubHttp.NoRetry) && !SyncDoctor.isStorageFull(e);
    }

    private void waitToRetry(Job job, IOException e, int attempt) throws GitHubHttp.Cancelled {
        String why = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        if (why.length() > 90) why = why.substring(0, 90) + "…";
        long start = android.os.SystemClock.elapsedRealtime();
        long until = start + AUTO_RETRY_MS[attempt];
        long networkUntil = until + NETWORK_WAIT_MS;
        String what = BACKUP.equals(job.kind) ? "the backup goes on from where it stopped" : "the download starts again";
        while (true) {
            if (job.cancelled || job.deferred || job.stalled || GitHubHttp.isCancelled()) throw new GitHubHttp.Cancelled();
            long now = android.os.SystemClock.elapsedRealtime();
            boolean online = online();
            if (now >= until && (online || now >= networkUntil)) return;
            int seconds = (int) Math.max(1, (until - now + 999) / 1000);
            step(job, Math.max(1, job.percent), online || now < until
                    ? "Connection lost (" + why + "). Trying again in " + seconds + " s (" + (attempt + 1) + " of "
                    + AUTO_RETRY_MS.length + "); " + what + "…"
                    : "Waiting for the network to come back (" + why + "); " + what + "…");
            try {
                Thread.sleep(Math.min(1000L, Math.max(10L, AUTO_RETRY_MS[attempt])));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new GitHubHttp.Cancelled();
            }
        }
    }

    /** Whether Android has a network with internet now (unknown: yes). */
    private boolean online() {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        try {
            android.net.ConnectivityManager cm = app.getSystemService(android.net.ConnectivityManager.class);
            if (cm == null) return true;
            android.net.Network n = cm.getActiveNetwork();
            if (n == null) return false;
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            return caps == null || caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** One job's work on the worker thread; the line shown when it worked. */
    private String run(Job job) throws Exception {
        if (job.cancelled) throw new GitHubHttp.Cancelled();
        // v2.1.1 review (HIGH): never under another account (checked before the test runner: the suite covers it)
        if (blocked(job)) throw new OtherAccount(blockedText(job));
        Runner test = runnerForTests;
        if (test != null) return test.run(job);
        if (IMPORT.equals(job.kind)) return runImport(job);
        signInNeeded.remove(job.key());
        if (GitHubSession.isRefused(app)) {
            // v2.0.5: GitHub refused this sign-in: no request until it is renewed (no stream of 401s); it waits
            throw new GitHubException("refused", GitHubSession.problemOf(app) == null
                    ? "GitHub didn't accept the saved sign-in: tap Sign in again; the backup waits."
                    : GitHubSession.problemOf(app));
        }
        if (job.afterRunning && !job.user) {
            // second review: a backup after a save that waited behind a running one: skipped when that one (or an
            // earlier one) already holds these saved files
            String login = TokenStore.get(app).login();
            String last = ProjectLinks.lastFingerprint(job.scId, login);
            if (last != null && last.equals(fingerprint(job.scId))) {
                removePending(job.scId);
                return "No changes since the last backup to GitHub";
            }
        }
        if (PUBLISH.equals(job.kind)) return runPublish(job);
        if (STORE.equals(job.kind)) return runStore(job);
        String name = upload(job, job.options, (percent, text) -> step(job, percent, text));
        removePending(job.scId);
        return name + " backed up to its GitHub repository · the share link is on its card" + job.note;
    }

    /** v2.0.3: the signed-in login and its token; the session is never removed here. */
    String[] session() throws GitHubException {
        TokenStore store = TokenStore.get(app);
        String login = store.login();
        if (login == null) throw new GitHubException("unauthorized", "Sign in with GitHub first.");
        String token = store.token();
        if (token == null) {
            // the sign-in stays; it is renewed by signing in again
            String problem = store.problem();
            throw new GitHubException(problem == null ? "keystore" : "unreadable", problem == null
                    ? "The sign-in couldn't be read from the Android Keystore just now. Try again."
                    : "This phone can't read the saved sign-in (" + problem + "). You're still signed in: tap Sign in "
                    + "again on the account card; the backup waits.");
        }
        return new String[]{login, token, store.sealedKey()};
    }

    // ------------------------------------------------------------------ v2.0.3: after a save in the editor

    /** System Settings' switch (preferences "P12", like the editor's "Automatically save"). */
    public static final String AUTO_BACKUP_KEY = "P12I_github_backup";

    public static boolean autoBackupOn(Context context) {
        return context.getSharedPreferences("P12", Context.MODE_PRIVATE).getBoolean(AUTO_BACKUP_KEY, true);
    }

    /**
     * v2.0.3 (owner's 15 Nov directive "auto-backup on project auto-save and Save & Exit"): the editor saved the
     * project (Save, Save &amp; exit, or Back with "Automatically save" on). When signed in and the switch is on, the
     * project goes up in the background with the boxes of its last backup, silently, only when its saved files
     * changed since that backup; a Save without leaving the editor at most every five minutes.
     */
    public void projectSaved(String scId, boolean leaving) {
        if (scId == null || !scId.matches("\\d+") || !autoBackupOn(app) || TokenStore.get(app).login() == null) return;
        Thread t = new Thread(() -> {
            try {
                if (lC.b(scId) == null) return;
                String now = fingerprint(scId);
                if (now.equals(ProjectLinks.lastFingerprint(scId, TokenStore.get(app).login()))) return;
                if (!leaving && System.currentTimeMillis() - ProjectLinks.lastBackup(scId) < 5 * 60_000L) return;
                Options options = ProjectLinks.lastOptions(scId);
                if (GitHubSession.isRefused(app)) {
                    // review (LOW): nothing is sent while GitHub refuses the sign-in: a changed project waits for
                    // Sign in again (second review: only after the change checks above)
                    addPending(scId, options == null ? Options.AUTOMATIC : options);
                    return;
                }
                Job job = new Job(scId, BACKUP, null, options == null ? Options.AUTOMATIC : options, false);
                job.afterRunning = true;
                enqueue(job, null, (ok, message) -> LogUtil.d("CloudSync", "Backup after save of " + scId + ": " + message));
            } catch (RuntimeException e) {
                LogUtil.e("CloudSync", "Backup after save not started", e);
            }
        }, "CloudSyncSaved");
        t.setDaemon(true);
        t.start();
    }

    /** A step of {@code job} for every screen following it (main thread) and the notification. */
    private void step(Job job, int percent, String text) {
        job.lastActivity = android.os.SystemClock.elapsedRealtime();
        java.util.List<Progress> watchers;
        synchronized (this) {
            job.percent = percent;
            job.text = text;
            watchers = new java.util.ArrayList<>(job.watchers);
        }
        service(job, false);
        if (watchers.isEmpty()) return;
        post(() -> {
            for (Progress p : watchers) p.step(percent, text);
        });
    }

    private long lastServiceUpdate;
    private int lastServicePercent = -1;

    /**
     * The notification of a running job (at most every 700 ms, or when the percentage moves). v2.1.0: several jobs at
     * once share one notification: "3 GitHub jobs" with a line per job (no Cancel there: each job's sheet has its own).
     */
    private void service(Job job, boolean start) {
        if (runnerForTests != null || (!job.user && !start && job.percent < 100 && !job.started)) return;
        java.util.List<Job> running = new java.util.ArrayList<>();
        synchronized (this) {
            for (Lane lane : lanes()) {
                if (lane.current != null && !lane.current.finished) running.add(lane.current);
            }
        }
        int fingerprint = job.percent;
        if (running.size() > 1) {
            fingerprint = 0;
            for (Job j : running) fingerprint = fingerprint * 31 + j.percent;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (!start && fingerprint == lastServicePercent && now - lastServiceUpdate < 700) return;
        lastServiceUpdate = now;
        lastServicePercent = fingerprint;
        try {
            if (running.size() > 1) {
                String[] combined = combined(running);
                CloudSyncService.running(app, null, combined[0], Integer.parseInt(combined[2]), combined[1], null);
                return;
            }
            CloudSyncService.running(app, job.key(), job.title(), job.percent, job.text, job.link);
        } catch (Throwable t) {
            LogUtil.d("CloudSync", "No progress notification: " + t);
        }
    }

    /** v2.1.0: the shared notification of several running jobs: {title, one line per job, the least percent}. */
    static String[] combined(java.util.List<Job> running) {
        StringBuilder lines = new StringBuilder();
        int least = 100;
        for (Job j : running) {
            int percent = Math.max(0, Math.min(100, j.percent));
            if (lines.length() > 0) lines.append('\n');
            lines.append(j.title()).append(" · ").append(percent).append('%');
            least = Math.min(least, percent);
        }
        return new String[]{running.size() + " GitHub jobs", lines.toString(), String.valueOf(least)};
    }

    private void serviceIdle() {
        if (runnerForTests != null) return;
        long generation;
        synchronized (this) {
            for (Lane lane : lanes()) {
                if (lane.current != null || !lane.queue.isEmpty()) return;
            }
            // second review: a job the other lane starts after this check makes this idle() a no-op
            generation = CloudSyncService.generation();
        }
        try {
            CloudSyncService.idle(app, generation);
        } catch (Throwable t) {
            LogUtil.d("CloudSync", "No service: " + t);
        }
    }

    // ------------------------------------------------------------------ the work

    private static void step(Progress progress, int percent, String text) {
        if (progress != null) progress.step(percent, text);
    }

    /**
     * v2.1.2 (owner's 21 Nov report: 6.2 MB took 10 minutes at "15 KB/s", 90% for minutes): the upload's speed over the
     * last ~8 seconds (not since the job began, which counted the release set-up), and a note when it stays very slow
     * while Sketchware AI isn't on screen (some phones' battery managers slow a background app's network even with its
     * notification).
     */
    static final class UploadSpeed {
        private final java.util.ArrayDeque<long[]> samples = new java.util.ArrayDeque<>();
        private long firstAt;

        synchronized void add(long now, long bytes) {
            if (firstAt == 0) firstAt = now;
            samples.addLast(new long[]{now, bytes});
            while (samples.size() > 2 && now - samples.peekFirst()[0] > 8000) samples.removeFirst();
        }

        /** Bytes per second over the window, or -1 when not known yet. */
        synchronized long bytesPerSecond(long now) {
            if (samples.size() < 2) return -1;
            long[] a = samples.peekFirst();
            long[] b = samples.peekLast();
            long ms = Math.max(1, b[0] - a[0]);
            if (ms < 1000) return -1;
            return (b[1] - a[1]) * 1000 / ms;
        }

        String rate(long now) {
            long bps = bytesPerSecond(now);
            return bps < 0 ? "" : " · " + megabytes(bps) + "/s";
        }

        /** Said after 20 s below 30 KB/s while no screen of the app is in front. */
        String slowNote(long now) {
            long bps = bytesPerSecond(now);
            if (bps < 0 || bps >= 30 * 1024 || now - firstAt < 20_000 || inFront()) return "";
            return " · slow: Android may be holding back Sketchware AI's network in the background (open the app, or "
                    + "allow it in the phone's battery settings)";
        }

        static boolean inFront() {
            android.app.ActivityManager.RunningAppProcessInfo info = new android.app.ActivityManager.RunningAppProcessInfo();
            try {
                android.app.ActivityManager.getMyMemoryState(info);
            } catch (RuntimeException e) {
                return true;
            }
            return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        }
    }

    /**
     * v2.1.3 (owner's 22 Nov question: "the APK was 142 MB, the update showed 136.2 MB: was something removed?"): sizes
     * in decimal units, as Android's own screens (Files, downloads, app info) count them: 142,824,153 bytes = 142.8 MB.
     * Up to v2.1.2 the app divided by 1024 × 1024 (136.2 "MB" for the same file).
     */
    static String megabytes(long bytes) {
        if (bytes < 1_000_000) return Math.max(1, bytes / 1000) + " KB";
        return String.format(java.util.Locale.ENGLISH, "%.1f MB", bytes / 1_000_000.0);
    }

    static final class MissingProject extends Exception {
        MissingProject(String message) {
            super(message);
        }
    }

    /** v2.1.1 review (HIGH): a job started under another account than the one signed in now: never run, not retried. */
    static final class OtherAccount extends Exception {
        OtherAccount(String message) {
            super(message);
        }
    }

    /** Whether {@code job} belongs to the account signed in now (jobs from before v2.1.1 have no account: they do). */
    boolean sameAccount(Job job) {
        String now = TokenStore.get(app).login();
        return job.login == null || job.login.equalsIgnoreCase(now);
    }

    /**
     * Second review: the account rule applies to what makes a repository public (a Play Store publish, Publish); a
     * backup or an import of a public project needs no particular account.
     */
    boolean blocked(Job job) {
        return (STORE.equals(job.kind) || PUBLISH.equals(job.kind)) && !sameAccount(job);
    }

    /** Why a blocked job didn't run ("you signed out" when nobody is signed in: never "@null"). */
    String blockedText(Job job) {
        String now = TokenStore.get(app).login();
        String what = STORE.equals(job.kind) ? "Not published" : "Not changed";
        if (now == null) return what + ": it was started as @" + job.login + " and you signed out. Sign in as @" + job.login
                + " and start it again.";
        return what + ": it was started as @" + job.login + " and this phone is signed in as @" + now + " now. Start it "
                + "again from this account if you want it there.";
    }

    /** v2.1.1 review (HIGH): Sign out forgets the jobs that waited for a sign-in or a Retry (they were that account's). */
    public void signedOut() {
        signInNeeded.clear();
        failed.clear();
    }

    /** Makes the .swb of one project, uploads it and deletes it again; returns the project's name. */
    String upload(String scId) throws Exception {
        return upload(new Job(scId, BACKUP, null, Options.AUTOMATIC, false), Options.AUTOMATIC, null);
    }

    /** The last built APK of the project (the Run build's), or null. */
    static File lastBuiltApk(String scId) {
        File bin = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/mysc/" + scId + "/bin");
        File[] files = bin.listFiles();
        File best = null;
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".apk") && f.length() > 0
                        && (best == null || f.lastModified() > best.lastModified())) {
                    best = f;
                }
            }
        }
        return best;
    }

    /**
     * v2.0.4: one backup into the project's repository: the analyzer's snapshot and notes, the .swb (made from the
     * saved project with its own instances), the APK when a newer one was built, the release of the app version, at
     * most five versions; then the record, and a public project's README / CHANGELOG / description.
     */
    String upload(Job job, Options options, Progress progress) throws Exception {
        String scId = job.scId;
        HashMap<String, Object> project = lC.b(scId);
        if (project == null) throw new MissingProject("Project #" + scId + " isn't on this phone any more.");
        String name = yB.c(project, "my_ws_name");
        String pkg = yB.c(project, "my_sc_pkg_name");
        String versionName = yB.c(project, "sc_ver_name");
        String versionCode = yB.c(project, "sc_ver_code");
        String[] session = session();
        String login = session[0];
        String token = session[1];
        CloudBackup account = new CloudBackup(token, login);
        try {
            // what changed since the previous version, offline (snapshots are outside the fingerprint)
            step(progress, 3, "Reading what changed since the previous version…");
            JsonObject snapshot = ProjectDiffAnalyzer.snapshot(scId);
            JsonObject previous = ProjectDiffAnalyzer.previous(scId, snapshot);
            ProjectDiffAnalyzer.Changes changes = ProjectDiffAnalyzer.compare(previous, snapshot);
            String notes = ProjectDiffAnalyzer.releaseNotes(snapshot, changes);
            String tag = ProjectRepo.tagFor(versionName, versionCode);
            ProjectDiffAnalyzer.save(scId, snapshot);
            String fingerprint = fingerprint(scId);
            String stamp = stamp(scId);

            step(progress, 6, "Opening the project's repository on GitHub…");
            long began = android.os.SystemClock.elapsedRealtime();
            ProjectRepo repo = ProjectRepo.ensure(token, login, scId, name, pkg);
            job.link = repo.link();
            PublishDetails details = PublishDetails.load(scId);
            details.refresh(snapshot, tag, notes, ProjectDiffAnalyzer.changelogEntry(snapshot, changes,
                    System.currentTimeMillis()), repo.link());
            details.save(scId);

            step(progress, 12, options.localLibraries || options.customBlocks
                    ? "Packing the project" + (options.localLibraries ? ", local libraries" : "")
                    + (options.customBlocks ? " & custom blocks" : "") + "…" : "Packing the project…");
            File swb = null;
            // v2.0.5 (checkpoint resume): the packed backup of an earlier try that didn't finish, when the project's
            // saved files, the boxes and the version are still the same: its finished parts on GitHub are kept
            File resumeSwb = resumeFile(scId, ".swb");
            File resumeInfo = resumeFile(scId, ".json");
            // review (MEDIUM): a stop before this point keeps an earlier checkpoint (never judged on a stopped thread)
            GitHubHttp.stopIfCancelled();
            String inputs = resumeInputs(scId, options);
            if (resumable(resumeInfo, resumeSwb, fingerprint + "|" + inputs, options, versionName, versionCode)) {
                swb = resumeSwb;
                step(progress, 14, "Resuming the upload: the parts already on GitHub are kept…");
            } else {
                clearResume(scId);
                job.packing = true;
                try {
                    // review (M6): a save while the project is packed could give a torn copy: packed again when its
                    // files changed meanwhile (at most three times)
                    for (int pack = 0; ; pack++) {
                        GitHubHttp.stopIfCancelled();
                        BackupFactory factory = new BackupFactory(scId);
                        factory.setBackupLocalLibs(options.localLibraries);
                        factory.setBackupCustomBlocks(options.customBlocks);
                        // the saved project, read with its own instances: never the editor's shared copy (it may be open)
                        factory.setReadSavedDataOnly(true);
                        factory.backup(name);
                        swb = factory.getOutFile();
                        if (swb == null) throw new GitHubException("backup", "The backup file couldn't be made: " + factory.getError());
                        String after = fingerprint(scId);
                        // second review: also by size and time, so a save that wrote the same bytes while the files were
                        // copied (each file is rewritten in place) packs again too
                        String stampAfter = stamp(scId);
                        if ((after.equals(fingerprint) && stampAfter.equals(stamp)) || pack >= 2) {
                            fingerprint = after;
                            break;
                        }
                        BackupFactory.deleteUploadedBackup(swb);
                        fingerprint = after;
                        stamp = stampAfter;
                    }
                } finally {
                    synchronized (this) {
                        job.packing = false;
                        // review (LOW): the 20 s for a stop count from the end of packing (it can't stop inside it)
                        if (job.stopAskedAt > 0) job.stopAskedAt = android.os.SystemClock.elapsedRealtime();
                    }
                }
                job.lastActivity = android.os.SystemClock.elapsedRealtime();
                // second review: the inputs read before packing (a change while packing makes the checkpoint stale)
                swb = keepForResume(swb, resumeSwb, resumeInfo, fingerprint + "|" + inputs, options, versionName,
                        versionCode);
            }
            boolean uploadedAll = false;
            boolean kept = swb.equals(resumeSwb);
            try {
                File apk = lastBuiltApk(scId);
                long apkTime = apk == null ? 0 : apk.lastModified();
                if (apk != null && apkTime <= ProjectLinks.lastApkTime(scId, login)) apk = null; // already up
                long size = swb.length() + (apk == null ? 0 : apk.length());
                int parts = ProjectRepo.partsFor(swb.length());
                step(progress, 15, "Packed " + megabytes(size) + (apk != null ? " (with the APK)" : "")
                        + (parts > 1 ? " · uploading in " + parts + " parts, " + Math.min(ProjectRepo.PARALLEL, parts)
                        + " at a time…" : " · uploading to " + repo.name() + "…"));
                ProjectRepo.Backup backup = new ProjectRepo.Backup();
                backup.swb = swb;
                backup.apk = apk;
                backup.projectName = name;
                backup.versionName = versionName;
                backup.versionCode = versionCode;
                backup.notes = notes;
                backup.title = ProjectDiffAnalyzer.title(snapshot) + " v" + ProjectDiffAnalyzer.version(snapshot);
                int[] last = {15};
                UploadSpeed speed = new UploadSpeed();
                ProjectRepo.Version stored = repo.upload(backup, new CloudBackup.UploadProgress() {
                    @Override
                    public void uploading(long sent, long total) {
                        long now = android.os.SystemClock.elapsedRealtime();
                        job.lastActivity = now;
                        speed.add(now, sent);
                        int percent = 15 + (int) (75 * Math.min(1.0, sent / (double) Math.max(1, total)));
                        if (sent >= total && total > 0) {
                            // v2.1.2 (owner's 21 Nov report: "stalled at 90%"): every byte is handed to the network;
                            // what is left is the phone sending its buffered part and GitHub's answer
                            last[0] = Math.max(last[0], percent);
                            step(progress, percent, "All " + megabytes(total) + " handed to the network · waiting for "
                                    + "GitHub to confirm" + speed.slowNote(now) + "…");
                        } else if (percent > last[0]) {
                            last[0] = percent;
                            step(progress, percent, "Uploading… " + megabytes(sent) + " of " + megabytes(total)
                                    + speed.rate(now) + speed.slowNote(now));
                        }
                    }

                    @Override
                    public void retrying(String text) {
                        step(progress, last[0], text);
                    }

                    @Override
                    public void saving() {
                        step(progress, 92, "Publishing version " + tag + " and keeping the newest " + ProjectRepo.MAX_VERSIONS
                                + "…");
                    }
                });
                uploadedAll = true;
                GitHubSession.clearRefused(app);
                ProjectLinks.saved(scId, login, stored.tag, options, fingerprint, apk == null ? 0 : apkTime);
                // v2.1.1: the app's own icon beside the backup (cards on other phones and the Play Store show it)
                RepoIcons.uploadQuietly(token, login, repo.name(), scId);
                // review M3: a Switch to Private (or Publish) during this backup wrote the newer visibility
                if (!visibilityChangedSince(scId, began)) ProjectLinks.visibilityNow(scId, login, repo.isPrivate());
                // a public project keeps its page up to date (README unless edited, CHANGELOG, description)
                if (!repo.isPrivate()) {
                    step(progress, 96, "Updating README and CHANGELOG…");
                    try {
                        // review (MEDIUM): the details as saved now (a Save on Public details during this backup wins)
                        PublishDetails current = PublishDetails.load(scId);
                        current.refresh(snapshot, tag, notes, ProjectDiffAnalyzer.changelogEntry(snapshot, changes,
                                System.currentTimeMillis()), repo.link());
                        current.save(scId);
                        publishDocs(repo, current, ProjectDiffAnalyzer.commitMessage(snapshot, changes));
                    } catch (IOException | GitHubException e) {
                        job.note = job.cancelled || job.deferred || job.stalled || GitHubHttp.isCancelled()
                                ? " (stopped before its README / CHANGELOG were updated: the next backup updates them)"
                                : " (its README / CHANGELOG weren't updated: " + e.getMessage() + ")";
                    }
                }
            } catch (GitHubException e) {
                // review (LOW): a failure that won't change by trying again doesn't keep a checkpoint
                if ("too_large".equals(e.code) || "no_file".equals(e.code)) clearResume(scId);
                throw e;
            } finally {
                // v2.0.5: kept for a resume until it is on GitHub (then removed with its notes)
                if (uploadedAll) clearResume(scId);
                // a packed file that couldn't be kept for a resume isn't left in the backups folder
                if (!kept) BackupFactory.deleteUploadedBackup(swb);
            }
        } catch (GitHubException e) {
            if ("http_401".equals(e.code) || "unauthorized".equals(e.code)) {
                // v2.0.3 (owner: "never auto-logout"): the session stays. GET /user says whether GitHub refuses this
                // sign-in or only refused one request
                // review (MEDIUM): marked refused only when GET /user answers exactly 401 (a 5xx / 429 / 403 / no
                // network isn't a refusal: it would stop every backup until Sign in again)
                if (!account.refusesSignIn()) {
                    throw new GitHubException("http_401_once", e.getMessage() + " GitHub didn't refuse the sign-in "
                            + "itself: you're still signed in; try again.");
                }
                String note = GitHubSession.refusedNote(app, null);
                // review (L1): only the sign-in this backup used (a new sign-in meanwhile isn't marked)
                GitHubSession.markRefused(app, note, session[2]);
                throw new GitHubException("refused", e.getMessage() + " " + note + " The backup waits.");
            }
            throw e;
        }
        return name;
    }

    // ------------------------------------------------------------------ v2.0.5: checkpoint resume

    /** The kept packed backup of a project ({@code .swb}) and what it was made from ({@code .json}). */
    static File resumeFile(String scId, String suffix) {
        return new File(BackupFactory.getBackupDir(), "staging/resume/" + scId + suffix);
    }

    /**
     * Review (MEDIUM): what else goes into a .swb besides {@code data/<id>} (the fingerprint): the project's list entry
     * (name, app name, package, colours), its images / sounds / fonts / icons, and with their boxes the local libraries
     * and the custom block definitions. Names, sizes and times (the list entry by content); a change to any of them
     * means a new pack, never an old .swb uploaded as the current version.
     */
    static String resumeInputs(String scId, Options options) {
        File root = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware");
        StringBuilder sb = new StringBuilder();
        File listEntry = new File(root, "mysc/list/" + scId + "/project");
        sb.append("project:").append(listEntry.length()).append(':').append(hashOf(listEntry)).append('\n');
        for (String kind : new String[]{"fonts", "icons", "images", "sounds"}) {
            listing(new File(root, "resources/" + kind + "/" + scId), kind, sb, 0);
        }
        if (options.localLibraries) {
            File libraries = new File(root, "data/" + scId + "/local_library");
            try {
                com.google.gson.JsonElement list = com.google.gson.JsonParser.parseString(readSmall(libraries));
                if (list.isJsonArray()) {
                    for (com.google.gson.JsonElement e : list.getAsJsonArray()) {
                        if (!e.isJsonObject()) continue;
                        String dex = DeviceFlow.string(e.getAsJsonObject(), "dexPath");
                        File folder = dex == null ? null : new File(dex).getParentFile();
                        // second review: only a library's own folder (.sketchware/libs/local_libs/<name>), never a
                        // wider one; third review: by its path's last parts, so /sdcard/… and /storage/emulated/0/…
                        // spellings of the same folder both count
                        File parent = folder == null ? null : folder.getParentFile();
                        File libs = parent == null ? null : parent.getParentFile();
                        File sketchwareDir = libs == null ? null : libs.getParentFile();
                        if (sketchwareDir != null && "local_libs".equals(parent.getName()) && "libs".equals(libs.getName())
                                && ".sketchware".equals(sketchwareDir.getName()) && !folder.getName().isEmpty()) {
                            listing(folder, "lib", sb, 0);
                        }
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                sb.append("libraries:unreadable\n");
            }
        }
        if (options.customBlocks) listing(new File(root, "resources/block"), "blocks", sb, 0);
        return sb.length() + ":" + Integer.toHexString(sb.toString().hashCode());
    }

    private static void listing(File dir, String prefix, StringBuilder sb, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 4) return;
        java.util.Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) {
                listing(f, prefix + "/" + f.getName(), sb, depth + 1);
            } else {
                sb.append(prefix).append('/').append(f.getName()).append(':').append(f.length()).append(':')
                        .append(f.lastModified()).append('\n');
            }
        }
    }

    private static String hashOf(File f) {
        if (!f.isFile() || f.length() > 1024 * 1024) return "-";
        try {
            return Integer.toHexString(readSmall(f).hashCode());
        } catch (IOException e) {
            return "?";
        }
    }

    /** Review (MEDIUM): plain streams, not java.nio.file (an interrupted thread makes those throw ClosedByInterrupt). */
    private static String readSmall(File f) throws IOException {
        if (f.length() > 8L * 1024 * 1024) throw new IOException("Too large: " + f);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Review (LOW): kept backups older than 7 days (by the time in their notes), without notes, or of projects no longer
     * on the phone are removed at app start, per project and never while that project's backup runs (second review).
     */
    void cleanResumeFiles() {
        try {
            File dir = resumeFile("0", ".swb").getParentFile();
            File[] files = dir == null ? null : dir.listFiles();
            if (files == null) return;
            java.util.Set<String> ids = new java.util.TreeSet<>();
            for (File f : files) ids.add(f.getName().replaceAll("\\..*$", ""));
            long now = System.currentTimeMillis();
            for (String id : ids) {
                if (id.matches("\\d+") && isRunning(id)) continue;
                boolean drop = !id.matches("\\d+") || lC.b(id) == null;
                if (!drop) {
                    File info = resumeFile(id, ".json");
                    long time = 0;
                    try {
                        JsonObject o = DeviceFlow.parse(readSmall(info));
                        time = o.has("time") ? o.get("time").getAsLong() : 0;
                    } catch (IOException | RuntimeException ignored) {
                        // no notes (or unreadable): the kept backup can't be resumed
                    }
                    drop = now - time > 7L * 24 * 3600_000L;
                }
                if (drop) {
                    for (File f : files) {
                        if (f.getName().replaceAll("\\..*$", "").equals(id)) {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            LogUtil.d("CloudSync", "Resume files not cleaned: " + e);
        }
    }

    /** Whether the kept backup still is this project as saved now, with these boxes and this version (≤ 7 days old). */
    static boolean resumable(File info, File swb, String fingerprint, Options options, String versionName,
                             String versionCode) {
        if (!info.isFile() || !swb.isFile()) return false;
        try {
            JsonObject o = DeviceFlow.parse(readSmall(info));
            long size = o.has("size") ? o.get("size").getAsLong() : -1;
            long time = o.has("time") ? o.get("time").getAsLong() : 0;
            return size == swb.length() && fingerprint.equals(DeviceFlow.string(o, "fingerprint"))
                    && String.valueOf(options.localLibraries).equals(DeviceFlow.string(o, "localLibraries"))
                    && String.valueOf(options.customBlocks).equals(DeviceFlow.string(o, "customBlocks"))
                    && String.valueOf(versionName).equals(DeviceFlow.string(o, "versionName"))
                    && String.valueOf(versionCode).equals(DeviceFlow.string(o, "versionCode"))
                    && System.currentTimeMillis() - time < 7L * 24 * 3600_000L;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Moves the packed backup to the resume place (same volume: a rename) with its notes; returns the kept file. */
    static File keepForResume(File packed, File swb, File info, String fingerprint, Options options, String versionName,
                              String versionCode) throws IOException {
        File dir = swb.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return packed;
        //noinspection ResultOfMethodCallIgnored
        swb.delete();
        if (!packed.renameTo(swb)) {
            // another volume: copied with plain streams (review: java.nio.file throws on an interrupted thread); on a
            // failure the packed file is used as it is and no checkpoint is kept
            try (java.io.InputStream in = new java.io.FileInputStream(packed);
                 java.io.OutputStream out = new java.io.FileOutputStream(swb)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                swb.delete();
                return packed;
            }
            //noinspection ResultOfMethodCallIgnored
            swb.setLastModified(packed.lastModified());
        }
        // the folder the backup was made in (when nothing else is in it)
        BackupFactory.deleteUploadedBackup(packed);
        JsonObject o = new JsonObject();
        o.addProperty("fingerprint", fingerprint);
        o.addProperty("localLibraries", String.valueOf(options.localLibraries));
        o.addProperty("customBlocks", String.valueOf(options.customBlocks));
        o.addProperty("versionName", String.valueOf(versionName));
        o.addProperty("versionCode", String.valueOf(versionCode));
        o.addProperty("size", swb.length());
        o.addProperty("time", System.currentTimeMillis());
        File tmp = new File(info.getPath() + ".tmp");
        try (java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(info)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Can't write " + info);
        }
        return swb;
    }

    static void clearResume(String scId) {
        //noinspection ResultOfMethodCallIgnored
        resumeFile(scId, ".swb").delete();
        //noinspection ResultOfMethodCallIgnored
        resumeFile(scId, ".json").delete();
        //noinspection ResultOfMethodCallIgnored
        resumeFile(scId, ".json.tmp").delete();
    }

    /** The public page: description, topics, README (when on), CHANGELOG. */
    static void publishDocs(ProjectRepo repo, PublishDetails details, String message) throws IOException, GitHubException {
        repo.setDetails(details.description, details.topics);
        if (details.readmeOn && details.readme != null) repo.putFile("README.md", details.readme, message);
        repo.putFile("CHANGELOG.md", details.changelogText(), message);
    }

    /**
     * v2.0.5 (owner's 16 Nov phone report: "Public details" Save did nothing visible): the details of a project already
     * on GitHub saved at once on their own thread (never behind a running backup): description and topics, README.md
     * (when on), CHANGELOG.md and the newest version's notes, each step on the sheet; the details are kept on the phone
     * first. Main thread only.
     */
    public static GitHubProgress saveDetails(Activity activity, String scId, String name, PublishDetails details) {
        CloudSync sync = get(activity);
        GitHubProgress progress = GitHubProgress.show(activity, R.drawable.ob_ic_github, "Save details of " + name);
        progress.update(2, "Saving on this phone…");
        details.save(scId);
        Thread t = new Thread(() -> {
            String message;
            boolean ok = false;
            try {
                String[] session = sync.session();
                sync.post(() -> progress.update(15, "Opening the project's repository on GitHub…"));
                ProjectRepo repo = ProjectRepo.open(session[1], session[0], scId);
                if (repo == null) {
                    throw new GitHubException("not_found", "The project's repository isn't on GitHub (or not on this "
                            + "account): back the project up first.");
                }
                String link = repo.link();
                sync.post(() -> progress.link(link));
                sync.post(() -> progress.update(40, "Saving the description and " + details.topics.size() + " topics…"));
                repo.setDetails(details.description, details.topics);
                if (details.readmeOn && details.readme != null) {
                    sync.post(() -> progress.update(60, "Writing README.md…"));
                    repo.putFile("README.md", details.readme, "Update README");
                }
                sync.post(() -> progress.update(75, "Writing CHANGELOG.md…"));
                repo.putFile("CHANGELOG.md", details.changelogText(), "Update CHANGELOG");
                java.util.List<ProjectRepo.Version> versions = repo.versions(false);
                if (!versions.isEmpty() && details.notes != null) {
                    ProjectRepo.Version newest = versions.get(0);
                    if (!details.notes.trim().equals(newest.notes == null ? "" : newest.notes.trim())) {
                        sync.post(() -> progress.update(90, "Saving the release notes of " + newest.label() + "…"));
                        repo.setNotes(newest, details.notes);
                    }
                }
                ok = true;
                message = "Saved on GitHub: description, " + details.topics.size() + " topics"
                        + (details.readmeOn ? ", README.md" : "") + ", CHANGELOG.md.";
            } catch (GitHubException e) {
                message = e.getMessage();
                if ("http_401".equals(e.code)) message = sync.refusedNow(e) ;
            } catch (Exception e) {
                message = GitHubImportDialog.text(e);
            }
            boolean success = ok;
            String text = message;
            sync.post(() -> {
                if (success) progress.done(text);
                else progress.failed(text, false);
            });
        }, "CloudSyncDetails");
        t.setDaemon(true);
        t.start();
        return progress;
    }

    /**
     * v2.0.4: Publish Public: the first version is backed up when there is none, then the page (description, topics,
     * README, CHANGELOG), the newest version's notes, and the repository made public.
     */
    private String runPublish(Job job) throws Exception {
        String[] session = session();
        String login = session[0];
        String token = session[1];
        HashMap<String, Object> project = lC.b(job.scId);
        if (project == null) throw new MissingProject("Project #" + job.scId + " isn't on this phone any more.");
        String name = yB.c(project, "my_ws_name");
        step(job, 3, "Opening the project's repository on GitHub…");
        ProjectRepo repo = ProjectRepo.ensure(token, login, job.scId, name, yB.c(project, "my_sc_pkg_name"));
        job.link = repo.link();
        java.util.List<ProjectRepo.Version> versions = repo.versions(false);
        if (versions.isEmpty()) {
            upload(job, job.options, (percent, text) -> step(job, Math.min(85, percent), text));
            versions = repo.versions(false);
        }
        PublishDetails details = job.details != null ? job.details : PublishDetails.load(job.scId);
        details.save(job.scId);
        step(job, 88, "Writing the description, topics, README and CHANGELOG…");
        publishDocs(repo, details, "Publish " + name);
        if (!versions.isEmpty() && details.notes != null) {
            ProjectRepo.Version newest = versions.get(0);
            if (!details.notes.trim().equals(newest.notes == null ? "" : newest.notes.trim())) {
                step(job, 94, "Saving the release notes of " + newest.label() + "…");
                repo.setNotes(newest, details.notes);
            }
        }
        step(job, 97, "Making " + repo.name() + " public…");
        // review L2: a Cancel up to here never ends in a public repository
        if (job.cancelled || Thread.currentThread().isInterrupted()) throw new GitHubHttp.Cancelled();
        repo.setPrivate(false);
        visibilityChanged(job.scId);
        ProjectLinks.visibilityNow(job.scId, login, false);
        return name + " is public: " + repo.link();
    }

    /** v2.1.1: a Play Store publish (PlayStore.publish with the staged form). */
    private String runStore(Job job) throws Exception {
        Store store = job.store;
        if (store == null || store.staging == null) throw new MissingProject("The publish form isn't on this phone any more.");
        String[] session = session();
        String login = session[0];
        String token = session[1];
        // second review (LOW): the account read for the requests is the job's own
        if (job.login != null && !job.login.equalsIgnoreCase(login)) throw new OtherAccount(blockedText(job));
        PlayStore.Listing listing = PlayStore.Listing.load(store.staging);
        if (listing.title == null || listing.title.trim().isEmpty()) listing.title = store.title;
        File icon = store.scId == null ? null : RepoIcons.localIcon(store.scId);
        if (icon != null) listing.icon = java.nio.file.Files.readAllBytes(icon.toPath());
        step(job, 3, "Opening " + store.repo + " on GitHub…");
        // review (LOW): a backup running meanwhile doesn't write the card back to Private
        if (store.scId != null) visibilityChanged(store.scId);
        String note;
        try {
            note = PlayStore.publish(token, login, store.repo, listing, (percent, text) -> step(job, percent, text));
        } catch (GitHubException e) {
            if ("http_401".equals(e.code) || "unauthorized".equals(e.code)) {
                // as a backup: marked refused only when GET /user answers exactly 401; the staged form and pictures
                // stay, and Sign in again runs the publish again by itself
                if (!new CloudBackup(token, login).refusesSignIn()) {
                    throw new GitHubException("http_401_once", e.getMessage() + " GitHub didn't refuse the sign-in "
                            + "itself: you're still signed in; try again.");
                }
                String refused = GitHubSession.refusedNote(app, null);
                GitHubSession.markRefused(app, refused, session[2]);
                throw new GitHubException("refused", e.getMessage() + " " + refused + " The publish waits: after Sign "
                        + "in again it goes on by itself (the pictures stay on this phone).");
            }
            throw e;
        }
        if (store.scId != null) {
            visibilityChanged(store.scId);
            ProjectLinks.visibilityNow(store.scId, login, false);
        }
        GitHubSession.clearRefused(app);
        // review (HIGH): the staged words and pictures stay for the next publish (a republish without them would
        // have nothing to upload; the store keeps its pictures then anyway)
        return store.title + " is in the Play Store" + (note == null ? "." : ". " + note);
    }

    // ------------------------------------------------------------------ v2.0.4: imports

    /**
     * An import: the backup is downloaded into a staging folder of its own, checked, restored as a new project (never
     * over an existing one, never into an open one's folders), linked to its repository when it is the account's own,
     * and the staged file removed.
     */
    private String runImport(Job job) throws Exception {
        Import src = job.source;
        String login = TokenStore.get(app).login();
        // v2.0.5: a sign-in GitHub refused isn't sent (a public project still imports without it)
        String token = login == null || GitHubSession.isRefused(app) ? null : TokenStore.get(app).token();
        File staging = new File(BackupFactory.getBackupDir(), "staging/" + Long.toHexString(System.nanoTime()));
        if (!staging.isDirectory() && !staging.mkdirs()) throw new IOException("Can't create " + staging);
        File swb = new File(staging, ProjectRepo.safeFileName(src.title == null ? src.repo : src.title) + ".swb");
        try {
            step(job, 3, "Connecting to GitHub…");
            GitHubHttp.Progress bytes = downloadSteps(job, src.size);
            if (src.version != null) {
                ProjectRepo.download(token, src.owner, src.repo, src.version, swb, bytes);
            } else if (src.legacySha != null) {
                new CloudBackup(token, login).download(src.legacyPath, src.legacySha, swb, bytes);
            } else {
                File got = GitHubImport.download(token, src.link, src.linkPath, staging, bytes);
                if (!got.renameTo(swb)) swb = got;
            }
            GitHubHttp.stopIfCancelled();
            step(job, 85, "Restoring as a new project" + (src.withLibraries ? " with its local libraries" : "") + "…");
            String newId;
            BackupFactory factory;
            // review (LOW): restoring writes a project: never abandoned by the watchdog meanwhile
            job.packing = true;
            try {
                synchronized (com.sketchware.ai.project.ProjectIds.RESTORE_LOCK) {
                    newId = com.sketchware.ai.project.ProjectIds.free(lC.b());
                    factory = new BackupFactory(newId);
                    factory.setBackupLocalLibs(src.withLibraries);
                    factory.setLibrariesFrom(src.librariesFrom);
                    factory.restore(swb);
                }
            } finally {
                synchronized (this) {
                    job.packing = false;
                    if (job.stopAskedAt > 0) job.stopAskedAt = android.os.SystemClock.elapsedRealtime();
                }
            }
            if (!factory.isRestoreSuccess()) {
                throw new GitHubException("restore", "Couldn't restore: " + factory.getError());
            }
            boolean linked = false;
            if (src.number > 0 && login != null && src.owner != null && src.owner.equalsIgnoreCase(login)) {
                linked = ProjectLinks.linkRestored(newId, login, src.repo, src.number, src.repoPrivate,
                        src.version != null && src.version.standard ? src.version.tag : null);
            }
            ProjectLinks.listChanged();
            return "Imported as project #" + newId + (linked ? " · its backups go on into " + src.repo : "")
                    + " (custom blocks included)";
        } finally {
            BackupFactory.deleteUploadedBackup(swb);
            File[] left = staging.listFiles();
            if (left != null) for (File f : left) //noinspection ResultOfMethodCallIgnored
                f.delete();
            //noinspection ResultOfMethodCallIgnored
            staging.delete();
        }
    }

    /** Download bytes → 5-80%. */
    private GitHubHttp.Progress downloadSteps(Job job, long knownSize) {
        int[] last = {4};
        long started = android.os.SystemClock.elapsedRealtime();
        return (done, total) -> {
            job.lastActivity = android.os.SystemClock.elapsedRealtime();
            long all = total > 0 ? total : knownSize;
            int percent = all > 0 ? 5 + (int) (75 * Math.min(1.0, done / (double) all))
                    : Math.min(75, 5 + (int) (done / (512 * 1024)));
            if (percent <= last[0]) return;
            last[0] = percent;
            long seconds = Math.max(1, (android.os.SystemClock.elapsedRealtime() - started) / 1000);
            step(job, percent, "Downloading… " + megabytes(done) + (all > 0 ? " of " + megabytes(all) : "") + " · "
                    + megabytes(done / seconds) + "/s");
        };
    }

    /**
     * v2.0.3: a fingerprint of the project's saved files (names and contents of the data folder): an automatic backup
     * after a save is skipped when nothing changed since the last backup. Review (M4): by content, since every save
     * rewrites every file (sizes and times alone would always differ); files over 4 MiB by size only.
     */
    static String fingerprint(String scId) {
        File data = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId);
        java.util.TreeMap<String, File> files = new java.util.TreeMap<>();
        collect(data, "", files, 0);
        java.security.MessageDigest all;
        try {
            all = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[64 * 1024];
        for (java.util.Map.Entry<String, File> e : files.entrySet()) {
            File f = e.getValue();
            all.update((e.getKey() + "\0" + f.length() + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (f.length() > 4L * 1024 * 1024) continue;
            try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                int n;
                while ((n = in.read(buffer)) > 0) all.update(buffer, 0, n);
            } catch (IOException ex) {
                all.update(("unreadable@" + f.lastModified()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return SwbParts.hex(all.digest()) + ":" + files.size();
    }

    /** Second review: names, sizes and modification times of the project's saved files (a save rewrites them all). */
    static String stamp(String scId) {
        File data = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId);
        java.util.TreeMap<String, File> files = new java.util.TreeMap<>();
        collect(data, "", files, 0);
        StringBuilder out = new StringBuilder();
        for (java.util.Map.Entry<String, File> e : files.entrySet()) {
            out.append(e.getKey()).append(':').append(e.getValue().length()).append(':')
                    .append(e.getValue().lastModified()).append('\n');
        }
        return out.toString();
    }

    private static void collect(File dir, String prefix, java.util.Map<String, File> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 6) return;
        for (File f : files) {
            String name = prefix + f.getName();
            // the GitHub record and the publish details change with every backup; v2.0.4: the version snapshots too
            if (name.equals(ProjectLinks.FILE) || name.equals(PublishDetails.FILE)
                    || name.equals(com.sketchware.ai.project.ProjectDiffAnalyzer.FOLDER)) {
                continue;
            }
            if (f.isDirectory()) {
                collect(f, name + "/", out, depth + 1);
            } else {
                out.put(name, f);
            }
        }
    }

}
