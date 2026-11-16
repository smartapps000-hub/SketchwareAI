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
        /** v2.0.3: a remark added to the job's closing line (review L6: per job, two lanes run at once). */
        String note = "";
        volatile Thread thread;
        PublishDetails details;
        Import source;
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
            return IMPORT.equals(kind) && source != null ? source.key() : kind + ":" + scId;
        }

        String title() {
            String n = name == null ? "project #" + scId : name;
            if (IMPORT.equals(kind)) return "Importing " + n;
            if (PUBLISH.equals(kind)) return "Publishing " + n;
            return "Backing up " + n;
        }
    }

    static final String BACKUP = "backup";
    static final String PUBLISH = "publish";
    static final String IMPORT = "import";

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

    /** Backups and publishing, one at a time (one project's jobs never overlap). */
    private final Lane work = new Lane("CloudSync");
    /** Imports, one at a time, beside the backups. */
    private final Lane imports = new Lane("CloudSyncImport");

    private Lane laneOf(String kind) {
        return IMPORT.equals(kind) ? imports : work;
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
        Lane lane = laneOf(kind);
        if (lane.current != null && lane.current.scId.equals(scId) && lane.current.kind.equals(kind)) return lane.current;
        return findQueued(scId, kind);
    }

    private Job findQueued(String scId, String kind) {
        for (Job job : laneOf(kind).queue) {
            if (job.scId.equals(scId) && job.kind.equals(kind)) return job;
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
                progress.restart();
                sync.enqueue(again, progress::update, ending(sync, progress, key));
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
                // review L3: Retry only when there is something to try again (never a button that does nothing)
                progress.failed(message, sync.canRetry(key));
            }
        };
    }

    /** A project was just created: back it up when signed in, else nothing; offline it waits. */
    public void projectCreated(String scId) {
        if (scId == null || TokenStore.get(app).login() == null) return;
        enqueue(new Job(scId, BACKUP, null, Options.AUTOMATIC, false), null, (ok, message) -> {
            // a failure that waits says nothing (it is tried again); one that won't work again is shown
            if (ok || !pending().contains(scId)) Toast.makeText(app, message, Toast.LENGTH_SHORT).show();
        });
    }

    private long lastPendingTry;

    /** {@link #syncPending} from the main screen: at most every ten minutes, and only when something waits. */
    public void syncPendingSoon() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastPendingTry != 0 && now - lastPendingTry < 10 * 60_000L) return;
        if (pending().isEmpty()) return;
        lastPendingTry = now;
        syncPending();
    }

    /** Uploads the projects that are waiting (signed in only), after any backup a tap started. */
    public void syncPending() {
        if (TokenStore.get(app).login() == null) return;
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
        Job target;
        int ahead;
        Lane lane = laneOf(job.kind);
        synchronized (this) {
            if (IMPORT.equals(job.kind)) target = findImport(job.key());
            else target = job.afterRunning ? findQueued(job.scId, job.kind) : find(job.scId, job.kind);
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
            ahead = target.started ? 0 : positionOf(lane, target);
            if (lane.worker == null || !lane.worker.isAlive()) {
                lane.worker = new Thread(() -> loop(lane), lane.name);
                lane.worker.setDaemon(true);
                lane.worker.start();
            }
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
        String other = running == null || running.name == null ? "another job" : running.name;
        step(job, 1, "Waiting: " + other + (ahead > 1 ? " and " + (ahead - 1) + " more go first" : " goes first") + "…");
    }

    synchronized Job findByKey(String key) {
        for (Lane lane : new Lane[]{work, imports}) {
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
            for (Lane lane : new Lane[]{work, imports}) {
                if (lane.current != null && key.equals(lane.current.key())) {
                    lane.current.cancelled = true;
                    GitHubHttp.cancel(lane.current.thread);
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
        enqueue(again, null, (ok, message) -> Toast.makeText(app, message, Toast.LENGTH_LONG).show());
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
        again.link = old.link;
        return again;
    }

    // ------------------------------------------------------------------ the worker

    private void loop(Lane lane) {
        while (true) {
            Job job;
            synchronized (this) {
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
                job.thread = Thread.currentThread();
            }
            boolean ok = false;
            boolean retryable = false;
            String message;
            service(job, true);
            try {
                message = run(job);
                ok = true;
            } catch (MissingProject e) {
                // deleted meanwhile: nothing to wait for
                removePending(job.scId);
                message = e.getMessage();
            } catch (GitHubHttp.Cancelled e) {
                message = "Cancelled.";
            } catch (IOException e) {
                if (job.cancelled) {
                    message = "Cancelled.";
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
                if (BACKUP.equals(job.kind) && ("keystore".equals(e.code) || "unauthorized".equals(e.code)
                        || "refused".equals(e.code) || "unreadable".equals(e.code) || SignIn.isPassing(e.code))) {
                    // worth another try: a Keystore hiccup, a server error / rate limit, or a sign-in to come
                    addPending(job.scId, job.options);
                }
                retryable = true;
                message = e.getMessage();
            } catch (Throwable t) {
                // also an Error (out of memory with a large project): the job ends, the queue goes on
                LogUtil.e("CloudSync", "Job " + job.kind + " " + job.scId + " failed", t);
                retryable = true;
                message = (BACKUP.equals(job.kind) ? "The backup didn't work: " : "That didn't work: ") + t;
            } finally {
                synchronized (this) {
                    lane.current = null;
                    job.thread = null;
                }
                GitHubHttp.release();
            }
            if (ok) failed.remove(job.key());
            else if (retryable && job.user) failed.put(job.key(), retryCopy(job));
            finish(job, ok, message, retryable && job.user);
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
        if (runnerForTests == null && (job.user || !ok && job.started)) {
            try {
                CloudSyncService.finished(app, job.key(), job.title(), ok, text, retryable, job.link);
            } catch (Throwable t) {
                LogUtil.d("CloudSync", "No notification: " + t);
            }
        }
    }

    /** One job's work on the worker thread; the line shown when it worked. */
    private String run(Job job) throws Exception {
        if (job.cancelled) throw new GitHubHttp.Cancelled();
        Runner test = runnerForTests;
        if (test != null) return test.run(job);
        if (IMPORT.equals(job.kind)) return runImport(job);
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

    /** The notification of a running job (at most every 700 ms, or when the percentage moves). */
    private void service(Job job, boolean start) {
        if (runnerForTests != null || (!job.user && !start && job.percent < 100 && !job.started)) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (!start && job.percent == lastServicePercent && now - lastServiceUpdate < 700) return;
        lastServiceUpdate = now;
        lastServicePercent = job.percent;
        try {
            CloudSyncService.running(app, job.key(), job.title(), job.percent, job.text, job.link);
        } catch (Throwable t) {
            LogUtil.d("CloudSync", "No progress notification: " + t);
        }
    }

    private void serviceIdle() {
        if (runnerForTests != null) return;
        synchronized (this) {
            if (work.current != null || imports.current != null || !work.queue.isEmpty() || !imports.queue.isEmpty()) return;
        }
        try {
            CloudSyncService.idle(app);
        } catch (Throwable t) {
            LogUtil.d("CloudSync", "No service: " + t);
        }
    }

    // ------------------------------------------------------------------ the work

    private static void step(Progress progress, int percent, String text) {
        if (progress != null) progress.step(percent, text);
    }

    static String megabytes(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, bytes / 1024) + " KB";
        return String.format(java.util.Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static final class MissingProject extends Exception {
        MissingProject(String message) {
            super(message);
        }
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
            // review (M6): a save while the project is packed could give a torn copy: packed again when its files
            // changed meanwhile (at most three times)
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
            try {
                File apk = lastBuiltApk(scId);
                long apkTime = apk == null ? 0 : apk.lastModified();
                if (apk != null && apkTime <= ProjectLinks.lastApkTime(scId, login)) apk = null; // already up
                long size = swb.length() + (apk == null ? 0 : apk.length());
                int parts = (int) Math.max(1, (swb.length() + ProjectRepo.PART_BYTES - 1) / ProjectRepo.PART_BYTES);
                step(progress, 15, "Packed " + megabytes(size) + (apk != null ? " (with the APK)" : "")
                        + (parts > 1 ? " · uploading in " + parts + " parts…" : " · uploading to " + repo.name() + "…"));
                ProjectRepo.Backup backup = new ProjectRepo.Backup();
                backup.swb = swb;
                backup.apk = apk;
                backup.projectName = name;
                backup.versionName = versionName;
                backup.versionCode = versionCode;
                backup.notes = notes;
                backup.title = ProjectDiffAnalyzer.title(snapshot) + " v" + ProjectDiffAnalyzer.version(snapshot);
                int[] last = {15};
                long started = android.os.SystemClock.elapsedRealtime();
                ProjectRepo.Version stored = repo.upload(backup, new CloudBackup.UploadProgress() {
                    @Override
                    public void uploading(long sent, long total) {
                        int percent = 15 + (int) (75 * Math.min(1.0, sent / (double) Math.max(1, total)));
                        if (percent > last[0]) {
                            last[0] = percent;
                            long seconds = Math.max(1, (android.os.SystemClock.elapsedRealtime() - started) / 1000);
                            step(progress, percent, "Uploading… " + megabytes(sent) + " of " + megabytes(total)
                                    + " · " + megabytes(sent / seconds) + "/s");
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
                GitHubSession.clearRefused(app);
                ProjectLinks.saved(scId, login, stored.tag, options, fingerprint, apk == null ? 0 : apkTime);
                // review M3: a Switch to Private (or Publish) during this backup wrote the newer visibility
                if (!visibilityChangedSince(scId, began)) ProjectLinks.visibilityNow(scId, login, repo.isPrivate());
                // a public project keeps its page up to date (README unless edited, CHANGELOG, description)
                if (!repo.isPrivate()) {
                    step(progress, 96, "Updating README and CHANGELOG…");
                    try {
                        publishDocs(repo, details, ProjectDiffAnalyzer.commitMessage(snapshot, changes));
                    } catch (IOException | GitHubException e) {
                        job.note = " (its README / CHANGELOG weren't updated: " + e.getMessage() + ")";
                    }
                }
            } finally {
                // the .swb was made for the upload only (and its folder, when nothing else is in it)
                BackupFactory.deleteUploadedBackup(swb);
            }
        } catch (GitHubException e) {
            if ("http_401".equals(e.code) || "unauthorized".equals(e.code)) {
                // v2.0.3 (owner: "never auto-logout"): the session stays. GET /user says whether GitHub refuses this
                // sign-in or only refused one request
                if (account.signInWorks()) {
                    throw new GitHubException("http_401_once", e.getMessage() + " GitHub accepts the sign-in itself: "
                            + "you're still signed in; try again.");
                }
                String note = GitHubSession.refusedNote(null);
                // review (L1): only the sign-in this backup used (a new sign-in meanwhile isn't marked)
                GitHubSession.markRefused(app, note, session[2]);
                throw new GitHubException("refused", e.getMessage() + " " + note + " The backup waits.");
            }
            throw e;
        }
        return name;
    }

    /** The public page: description, topics, README (when on), CHANGELOG. */
    static void publishDocs(ProjectRepo repo, PublishDetails details, String message) throws IOException, GitHubException {
        repo.setDetails(details.description, details.topics);
        if (details.readmeOn && details.readme != null) repo.putFile("README.md", details.readme, message);
        repo.putFile("CHANGELOG.md", details.changelogText(), message);
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

    // ------------------------------------------------------------------ v2.0.4: imports

    /**
     * An import: the backup is downloaded into a staging folder of its own, checked, restored as a new project (never
     * over an existing one, never into an open one's folders), linked to its repository when it is the account's own,
     * and the staged file removed.
     */
    private String runImport(Job job) throws Exception {
        Import src = job.source;
        String login = TokenStore.get(app).login();
        String token = login == null ? null : TokenStore.get(app).token();
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
            synchronized (com.sketchware.ai.project.ProjectIds.RESTORE_LOCK) {
                newId = com.sketchware.ai.project.ProjectIds.free(lC.b());
                factory = new BackupFactory(newId);
                factory.setBackupLocalLibs(src.withLibraries);
                factory.setLibrariesFrom(src.librariesFrom);
                factory.restore(swb);
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
