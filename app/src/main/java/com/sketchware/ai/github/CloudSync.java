package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import a.a.a.lC;
import a.a.a.yB;
import com.sketchware.remod.R;

import mod.hey.studios.project.backup.BackupFactory;
import mod.jbk.util.LogUtil;

/**
 * v2.0.0 (owner's 13 Nov directive "individual project backup"): one project at a time to the user's private
 * {@code SketchwareAI-Projects} repository ({@link CloudBackup}), on one background thread for the whole app.
 * <ul>
 * <li>{@link #backUpFromMenu}: the project card's Back up > "To GitHub", with a toast when it starts and when it ends;</li>
 * <li>{@link #projectCreated}: a new project is backed up by itself when signed in; without a network (or when GitHub
 * doesn't answer) it is marked as waiting ({@link #pending}) and {@link #syncPending} uploads it later (the main
 * screen with a network, and after a sign-in).</li>
 * </ul>
 * The .swb is made by the app's own {@link BackupFactory}, uploaded and deleted again. v2.0.1 (owner's 13 Nov
 * directive): the project card's Back up sheet has the classic "Include used Local libraries" / "Include used Custom
 * Blocks" boxes, and "Back up to GitHub" uploads exactly the .swb "Save on phone" would make with the same boxes
 * ({@link Options}). Backups made by themselves (new and waiting projects) use {@link Options#AUTOMATIC}: custom blocks,
 * no local libraries (they can be large; the Library Resolver installs them again).
 */
public final class CloudSync {

    static final String PREFS = "sketchware_ai_cloud_sync";

    /** Called on the main thread when one backup ended. */
    public interface Result {
        void done(boolean ok, String message);
    }

    /** v2.0.2: a step of a backup (0-100 and what is happening), on the main thread. */
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

    private static CloudSync instance;

    public static synchronized CloudSync get(Context context) {
        if (instance == null) instance = new CloudSync(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    /**
     * v2.0.3 (owner's 15 Nov phone test: a backup stuck at 1%, a second tap answered "is being backed up"): the work is
     * a queue of jobs on one thread. A tap's backup goes before the ones the app makes by itself (new, waiting and
     * saved projects); a second tap on a project that is queued or running follows that job (its sheet shows the same
     * steps) instead of being refused; a job waiting behind another says so. Whatever happens in a job (also an
     * {@link Error} such as running out of memory), it ends in {@code finally}: the project is free again and every
     * screen following it hears the end.
     */
    static final class Job {
        final String scId;
        final String kind;
        /** The project's name for the "waiting" line (may be null). */
        final String name;
        Options options;
        boolean user;
        boolean started;
        int percent = 1;
        String text = "Starting…";
        final java.util.List<Progress> watchers = new java.util.ArrayList<>();
        final java.util.List<Result> results = new java.util.ArrayList<>();

        Job(String scId, String kind, String name, Options options, boolean user) {
            this.scId = scId;
            this.kind = kind;
            this.name = name;
            this.options = options;
            this.user = user;
        }
    }

    static final String BACKUP = "backup";
    static final String MAKE_PUBLIC = "public";
    static final String MAKE_PRIVATE = "private";

    private final java.util.ArrayDeque<Job> queue = new java.util.ArrayDeque<>();
    private Job current;
    private Thread worker;

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
        return find(scId, BACKUP) != null || find(scId, MAKE_PUBLIC) != null || find(scId, MAKE_PRIVATE) != null;
    }

    private Job find(String scId, String kind) {
        if (current != null && current.scId.equals(scId) && current.kind.equals(kind)) return current;
        for (Job job : queue) {
            if (job.scId.equals(scId) && job.kind.equals(kind)) return job;
        }
        return null;
    }

    // ------------------------------------------------------------------ entry points

    /** The project card's Back up > "To GitHub": sign in first when needed, then the backup with toasts. */
    public static void backUpFromMenu(Activity activity, String scId, String name) {
        backUpFromMenu(activity, scId, name, Options.AUTOMATIC);
    }

    /**
     * v2.0.1: the same with the Back up sheet's boxes. v2.0.3: a backup of this project already queued or running is
     * followed (its sheet shows where it is); one waiting behind another project says so.
     */
    public static void backUpFromMenu(Activity activity, String scId, String name, Options options) {
        CloudSync sync = get(activity);
        if (TokenStore.get(activity).login() == null) {
            GitHubInAppAuthDialog.show(activity, login -> backUpFromMenu(activity, scId, name, options));
            return;
        }
        // v2.0.2 (owner's 14 Nov phone test: "Back up to GitHub did not visibly run"): the live progress sheet
        GitHubProgress progress = GitHubProgress.show(activity, R.drawable.ob_ic_github, "Back up " + name);
        progress.update(1, "Starting…");
        sync.enqueue(new Job(scId, BACKUP, name, options == null ? Options.AUTOMATIC : options, true), progress::update,
                (ok, message) -> {
                    if (progress.hidden()) {
                        Toast.makeText(sync.app, message, Toast.LENGTH_LONG).show();
                    } else if (ok) {
                        progress.done(message);
                    } else {
                        progress.failed(message);
                    }
                });
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
     * v2.0.3: queues {@code job}, or joins the same job already queued or running: {@code progress} hears its steps
     * (the current one at once) and {@code result} its end, both on the main thread. A tap's job goes before the jobs
     * the app made by itself; a tap joining an automatic job that hasn't started moves it forward and gives it the
     * tap's boxes.
     */
    void enqueue(Job job, Progress progress, Result result) {
        Job target;
        int ahead;
        synchronized (this) {
            target = find(job.scId, job.kind);
            if (target == null) {
                target = job;
                if (job.user) {
                    // after the other taps' jobs, before the automatic ones
                    java.util.ArrayDeque<Job> users = new java.util.ArrayDeque<>();
                    while (!queue.isEmpty() && queue.peekFirst().user) users.addLast(queue.pollFirst());
                    queue.addFirst(job);
                    while (!users.isEmpty()) queue.addFirst(users.pollLast());
                } else {
                    queue.addLast(job);
                }
            } else if (job.user && !target.started) {
                target.options = job.options;
                if (!target.user) {
                    target.user = true;
                    queue.remove(target);
                    java.util.ArrayDeque<Job> users = new java.util.ArrayDeque<>();
                    while (!queue.isEmpty() && queue.peekFirst().user) users.addLast(queue.pollFirst());
                    queue.addFirst(target);
                    while (!users.isEmpty()) queue.addFirst(users.pollLast());
                }
            }
            if (progress != null) target.watchers.add(progress);
            if (result != null) target.results.add(result);
            ahead = target.started ? 0 : positionOf(target);
            if (worker == null || !worker.isAlive()) {
                worker = new Thread(this::loop, "CloudSync");
                worker.setDaemon(true);
                worker.start();
            }
        }
        if (progress != null) {
            Job j = target;
            if (ahead > 0) {
                waiting(j, ahead);
            } else {
                post(() -> progress.step(j.percent, j.text));
            }
        }
    }

    /** How many jobs run or wait before {@code job} (0 = it is next or running). */
    private int positionOf(Job job) {
        int n = current != null && current != job ? 1 : 0;
        for (Job j : queue) {
            if (j == job) return n;
            n++;
        }
        return n;
    }

    private void waiting(Job job, int ahead) {
        Job running;
        synchronized (this) {
            running = current;
        }
        String other = running == null || running.name == null ? "another backup" : running.name;
        step(job, 1, "Waiting: " + other + (ahead > 1 ? " and " + (ahead - 1) + " more go first" : " goes first") + "…");
    }

    private void loop() {
        while (true) {
            Job job;
            synchronized (this) {
                job = queue.pollFirst();
                current = job;
                if (job == null) {
                    worker = null;
                    return;
                }
                job.started = true;
            }
            boolean ok = false;
            String message;
            try {
                message = run(job);
                ok = true;
            } catch (MissingProject e) {
                // deleted meanwhile: nothing to wait for
                removePending(job.scId);
                message = e.getMessage();
            } catch (IOException e) {
                // no network (or GitHub didn't answer): it waits for the next chance, with the same boxes
                if (BACKUP.equals(job.kind)) addPending(job.scId, job.options);
                message = "No connection to GitHub (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + "). "
                        + (BACKUP.equals(job.kind) ? "You stay signed in; the backup waits for the next chance."
                        : "You stay signed in; try again.");
                LogUtil.d("CloudSync", "Job " + job.kind + " " + job.scId + " waits: " + e);
            } catch (GitHubException e) {
                if (BACKUP.equals(job.kind) && ("keystore".equals(e.code) || "unauthorized".equals(e.code)
                        || "refused".equals(e.code) || "unreadable".equals(e.code) || SignIn.isPassing(e.code))) {
                    // worth another try: a Keystore hiccup, a server error / rate limit, or a sign-in to come
                    addPending(job.scId, job.options);
                }
                message = e.getMessage();
            } catch (Throwable t) {
                // also an Error (out of memory with a large project): the job ends, the queue goes on
                LogUtil.e("CloudSync", "Job " + job.kind + " " + job.scId + " failed", t);
                message = (BACKUP.equals(job.kind) ? "The backup didn't work: " : "That didn't work: ") + t;
            } finally {
                synchronized (this) {
                    current = null;
                }
            }
            boolean success = ok;
            String text = message;
            java.util.List<Result> results;
            synchronized (this) {
                results = new java.util.ArrayList<>(job.results);
            }
            post(() -> {
                for (Result r : results) {
                    try {
                        r.done(success, text);
                    } catch (RuntimeException e) {
                        LogUtil.e("CloudSync", "A result listener failed", e);
                    }
                }
            });
            // the ones still waiting: their place moved up
            java.util.List<Job> waiting;
            synchronized (this) {
                waiting = new java.util.ArrayList<>(queue);
            }
            for (Job w : waiting) {
                if (!w.watchers.isEmpty()) {
                    int ahead;
                    synchronized (this) {
                        ahead = positionOf(w);
                    }
                    if (ahead > 0) waiting(w, ahead);
                }
            }
        }
    }

    /** v2.0.3: a remark about the job that ran last (worker thread only), added to its line. */
    private String lastNote;

    /** One job's work on the worker thread; the line shown when it worked. */
    private String run(Job job) throws Exception {
        lastNote = "";
        Runner test = runnerForTests;
        if (test != null) return test.run(job);
        if (MAKE_PUBLIC.equals(job.kind) || MAKE_PRIVATE.equals(job.kind)) {
            return changeVisibility(job, MAKE_PUBLIC.equals(job.kind));
        }
        String name = upload(job.scId, job.options, (percent, text) -> step(job, percent, text));
        removePending(job.scId);
        return name + " backed up to GitHub · the share link is on its card" + lastNote;
    }

    /** v2.0.3: the signed-in login and its token; the session is never removed here. */
    private String[] session() throws GitHubException {
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
        return new String[]{login, token};
    }

    // ------------------------------------------------------------------ v2.0.3: public / private

    /**
     * The card's Public / Private pill: {@code makePublic} puts the project's newest backup (the boxes of its last
     * backup) into its own public repository, else that repository is made private again. A progress sheet follows.
     */
    public static void changeVisibility(Activity activity, String scId, String name, boolean makePublic) {
        CloudSync sync = get(activity);
        if (TokenStore.get(activity).login() == null) {
            GitHubInAppAuthDialog.show(activity, login -> changeVisibility(activity, scId, name, makePublic));
            return;
        }
        GitHubProgress progress = GitHubProgress.show(activity, makePublic ? R.drawable.ob_ic_public : R.drawable.ob_ic_lock,
                (makePublic ? "Make public: " : "Make private: ") + name);
        progress.update(1, "Starting…");
        sync.enqueue(new Job(scId, makePublic ? MAKE_PUBLIC : MAKE_PRIVATE, name, Options.AUTOMATIC, true), progress::update,
                (ok, message) -> {
                    if (progress.hidden()) {
                        Toast.makeText(sync.app, message, Toast.LENGTH_LONG).show();
                    } else if (ok) {
                        progress.done(message);
                    } else {
                        progress.failed(message);
                    }
                });
    }

    private String changeVisibility(Job job, boolean makePublic) throws Exception {
        HashMap<String, Object> project = lC.b(job.scId);
        if (project == null) throw new MissingProject("Project #" + job.scId + " isn't on this phone any more.");
        String name = yB.c(project, "my_ws_name");
        String[] session = session();
        if (!makePublic) {
            step(job, 40, "Making its public copy private…");
            PublicCopy.makePrivate(session[1], session[0], job.scId);
            return name + " is private: its link opens only for you (and your repository's collaborators).";
        }
        Options options = ProjectLinks.lastOptions(job.scId);
        if (options == null) options = Options.AUTOMATIC;
        step(job, 10, "Packing the project for its public copy…");
        BackupFactory factory = new BackupFactory(job.scId);
        factory.setBackupLocalLibs(options.localLibraries);
        factory.setBackupCustomBlocks(options.customBlocks);
        factory.setReadSavedDataOnly(true);
        factory.backup(name);
        File swb = factory.getOutFile();
        if (swb == null) throw new GitHubException("backup", "The backup file couldn't be made: " + factory.getError());
        try {
            long size = swb.length();
            step(job, 25, "Creating or opening its public repository…");
            int[] last = {25};
            PublicCopy.makePublic(session[1], session[0], job.scId, name, yB.c(project, "my_sc_pkg_name"),
                    yB.c(project, "sc_ver_name"), swb, new CloudBackup.UploadProgress() {
                        @Override
                        public void uploading(long sent, long total) {
                            int percent = 30 + (int) (60 * Math.min(1.0, sent / (double) Math.max(1, total)));
                            if (percent > last[0]) {
                                last[0] = percent;
                                step(job, percent, "Uploading the public copy… " + megabytes(sent) + " of " + megabytes(size));
                            }
                        }

                        @Override
                        public void retrying(String text) {
                            step(job, last[0], text);
                        }

                        @Override
                        public void saving() {
                            step(job, 92, "Saving the public copy…");
                        }
                    });
        } finally {
            BackupFactory.deleteUploadedBackup(swb);
        }
        return name + " is public: anyone with its link can download the project. The share pill shares that link.";
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
                if (now.equals(ProjectLinks.lastFingerprint(scId))) return;
                if (!leaving && System.currentTimeMillis() - ProjectLinks.lastBackup(scId) < 5 * 60_000L) return;
                Options options = ProjectLinks.lastOptions(scId);
                enqueue(new Job(scId, BACKUP, null, options == null ? Options.AUTOMATIC : options, false), null,
                        (ok, message) -> LogUtil.d("CloudSync", "Backup after save of " + scId + ": " + message));
            } catch (RuntimeException e) {
                LogUtil.e("CloudSync", "Backup after save not started", e);
            }
        }, "CloudSyncSaved");
        t.setDaemon(true);
        t.start();
    }

    /** A step of {@code job} for every screen following it (main thread). */
    private void step(Job job, int percent, String text) {
        java.util.List<Progress> watchers;
        synchronized (this) {
            job.percent = percent;
            job.text = text;
            watchers = new java.util.ArrayList<>(job.watchers);
        }
        if (watchers.isEmpty()) return;
        post(() -> {
            for (Progress p : watchers) p.step(percent, text);
        });
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
        return upload(scId, Options.AUTOMATIC);
    }

    String upload(String scId, Options options) throws Exception {
        return upload(scId, options, null);
    }

    /**
     * v2.0.1: the same with the given contents (the .swb "Save on phone" makes with the same boxes). v2.0.2: the steps
     * go to {@code progress} (any thread), and the link of the uploaded file is kept with the project
     * ({@link ProjectLinks}).
     */
    String upload(String scId, Options options, Progress progress) throws Exception {
        HashMap<String, Object> project = lC.b(scId);
        if (project == null) throw new MissingProject("Project #" + scId + " isn't on this phone any more.");
        String name = yB.c(project, "my_ws_name");
        String pkg = yB.c(project, "my_sc_pkg_name");
        String version = yB.c(project, "sc_ver_name");
        String[] session = session();
        String login = session[0];
        String token = session[1];
        CloudBackup cloud = new CloudBackup(token, login);
        String fingerprint = fingerprint(scId);
        try {
            // the network and the token first: offline nothing is zipped
            step(progress, 5, "Checking your private repository " + CloudBackup.REPO + "…");
            cloud.ensureRepository();
            step(progress, 15, options.localLibraries || options.customBlocks
                    ? "Packing the project" + (options.localLibraries ? ", local libraries" : "")
                    + (options.customBlocks ? " & custom blocks" : "") + "…" : "Packing the project…");
            BackupFactory factory = new BackupFactory(scId);
            factory.setBackupLocalLibs(options.localLibraries);
            factory.setBackupCustomBlocks(options.customBlocks);
            // the saved project, read with its own instances: never the editor's shared copy (it may be open)
            factory.setReadSavedDataOnly(true);
            factory.backup(name);
            File swb = factory.getOutFile();
            if (swb == null) throw new GitHubException("backup", "The backup file couldn't be made: " + factory.getError());
            try {
                long size = swb.length();
                int parts = SwbParts.count(size);
                step(progress, 30, "Packed " + megabytes(size) + (size > SwbParts.PART_BYTES ? " · uploading in " + parts
                        + " parts…" : " · uploading to GitHub…"));
                int[] last = {30};
                cloud.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME,
                        new CloudBackup.UploadProgress() {
                            @Override
                            public void uploading(long sent, long total) {
                                int percent = 30 + (int) (60 * Math.min(1.0, sent / (double) Math.max(1, total)));
                                if (percent > last[0]) {
                                    last[0] = percent;
                                    int part = (int) Math.min(parts, sent / SwbParts.PART_BYTES + 1);
                                    step(progress, percent, "Uploading to GitHub… " + megabytes(sent) + " of "
                                            + megabytes(total) + (parts > 1 ? " · part " + part + " of " + parts : ""));
                                }
                            }

                            @Override
                            public void retrying(String text) {
                                step(progress, last[0], text);
                            }

                            @Override
                            public void saving() {
                                step(progress, 92, "Saving the backup in " + CloudBackup.REPO + "…");
                            }
                        });
                GitHubSession.clearRefused(app);
                String path = cloud.lastPath;
                if (path != null) {
                    ProjectLinks.saved(scId, cloud.link(path), path, login, options, fingerprint);
                    rememberInList(login, name, pkg, version, size, path);
                }
                // v2.0.3: a project made public keeps its public copy up to date (the private backup is safe either way)
                if (ProjectLinks.isPublic(scId)) {
                    step(progress, 95, "Updating the public copy…");
                    try {
                        PublicCopy.update(token, login, scId, name, pkg, version, swb, null);
                    } catch (IOException | GitHubException e) {
                        lastNote = " (its public copy wasn't updated: " + e.getMessage() + ")";
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
                if (cloud.signInWorks()) {
                    throw new GitHubException("http_401_once", e.getMessage() + " GitHub accepts the sign-in itself: "
                            + "you're still signed in; try again.");
                }
                String note = GitHubSession.refusedNote(null);
                GitHubSession.markRefused(app, note);
                throw new GitHubException("refused", e.getMessage() + " " + note + " The backup waits.");
            }
            throw e;
        }
        return name;
    }

    /**
     * v2.0.3: a fingerprint of the project's saved files (names, sizes, times of the data folder and its files): an
     * automatic backup after a save is skipped when nothing changed since the last backup.
     */
    static String fingerprint(String scId) {
        File data = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId);
        java.util.TreeMap<String, String> seen = new java.util.TreeMap<>();
        collect(data, "", seen, 0);
        return Integer.toHexString(seen.toString().hashCode()) + ":" + seen.size();
    }

    private static void collect(File dir, String prefix, java.util.Map<String, String> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 6) return;
        for (File f : files) {
            String name = prefix + f.getName();
            // the backup link's own file changes with every backup
            if (name.equals(ProjectLinks.FILE)) continue;
            if (f.isDirectory()) {
                collect(f, name + "/", out, depth + 1);
            } else {
                out.put(name, f.length() + "@" + f.lastModified());
            }
        }
    }

    /**
     * v2.0.3: the import sheet's list of own backups is kept on this phone; a backup from here updates it at once, so
     * the list shows it even before the next time GitHub can be asked.
     */
    private void rememberInList(String login, String name, String pkg, String version, long size, String path) {
        try {
            String swbPath = SwbParts.isManifest(path) ? path.substring(0, path.length() - SwbParts.MANIFEST_SUFFIX.length()) : path;
            java.util.List<CloudBackup.Entry> list = GitHubImportDialog.cachedList(app, login, new long[1]);
            if (list == null) list = new java.util.ArrayList<>();
            for (java.util.Iterator<CloudBackup.Entry> it = list.iterator(); it.hasNext(); ) {
                if (swbPath.equals(it.next().path)) it.remove();
            }
            CloudBackup.Entry e = new CloudBackup.Entry();
            e.path = swbPath;
            e.name = name;
            e.packageName = pkg;
            e.versionName = version;
            e.time = System.currentTimeMillis();
            e.size = size;
            e.parts = SwbParts.isManifest(path) ? path : null;
            list.add(e);
            GitHubImportDialog.saveCachedList(app, login, list, false);
        } catch (RuntimeException e) {
            LogUtil.d("CloudSync", "Backup list not updated: " + e);
        }
    }
}
