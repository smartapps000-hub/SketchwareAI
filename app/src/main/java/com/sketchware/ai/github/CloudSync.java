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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.lC;
import a.a.a.yB;
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
 * The .swb is made by the app's own {@link BackupFactory} (without local libraries: they can be large and are on the
 * Maven repositories), uploaded and deleted again.
 */
public final class CloudSync {

    static final String PREFS = "sketchware_ai_cloud_sync";

    /** Called on the main thread when one backup ended. */
    public interface Result {
        void done(boolean ok, String message);
    }

    private static CloudSync instance;

    public static synchronized CloudSync get(Context context) {
        if (instance == null) instance = new CloudSync(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CloudSync");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> running = new HashSet<>();

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
        Set<String> set = pending();
        if (set.add(scId)) prefs.edit().putStringSet("pending", set).apply();
    }

    synchronized void removePending(String scId) {
        Set<String> set = pending();
        if (set.remove(scId)) prefs.edit().putStringSet("pending", set).apply();
    }

    public synchronized boolean isRunning(String scId) {
        return running.contains(scId);
    }

    // ------------------------------------------------------------------ entry points

    /** The project card's Back up > "To GitHub": sign in first when needed, then the backup with toasts. */
    public static void backUpFromMenu(Activity activity, String scId, String name) {
        CloudSync sync = get(activity);
        if (TokenStore.get(activity).login() == null) {
            GitHubInAppAuthDialog.show(activity, login -> backUpFromMenu(activity, scId, name));
            return;
        }
        if (sync.isRunning(scId)) {
            Toast.makeText(activity, name + " is being backed up", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(activity, "Backing up " + name + " to GitHub…", Toast.LENGTH_SHORT).show();
        sync.backUp(scId, (ok, message) -> Toast.makeText(sync.app, message, Toast.LENGTH_LONG).show());
    }

    /** A project was just created: back it up when signed in, else nothing; offline it waits. */
    public void projectCreated(String scId) {
        if (scId == null || TokenStore.get(app).login() == null) return;
        backUp(scId, (ok, message) -> {
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

    /** Uploads the projects that are waiting (signed in only). */
    public void syncPending() {
        if (TokenStore.get(app).login() == null) return;
        for (String scId : pending()) {
            backUp(scId, (ok, message) -> {
                if (ok) Toast.makeText(app, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    /** One project, in the background; {@code result} on the main thread. */
    public void backUp(String scId, Result result) {
        synchronized (this) {
            if (!running.add(scId)) return;
        }
        work.execute(() -> {
            boolean ok = false;
            String message;
            try {
                String name = upload(scId);
                removePending(scId);
                ok = true;
                message = name + " backed up to GitHub";
            } catch (MissingProject e) {
                // deleted meanwhile: nothing to wait for
                removePending(scId);
                message = e.getMessage();
            } catch (IOException e) {
                // no network (or GitHub didn't answer): it waits for the next chance
                addPending(scId);
                message = "No connection to GitHub: the backup waits for the next chance.";
                LogUtil.d("CloudSync", "Backup of " + scId + " waits: " + e);
            } catch (GitHubException e) {
                if ("keystore".equals(e.code) || "unauthorized".equals(e.code) || "signed_out".equals(e.code)
                        || SignIn.isPassing(e.code)) {
                    // worth another try: a Keystore hiccup, a server error / rate limit, or a sign-in to come
                    addPending(scId);
                }
                message = e.getMessage();
            } catch (Exception e) {
                LogUtil.e("CloudSync", "Backup of " + scId + " failed", e);
                message = "The backup didn't work: " + e;
            }
            synchronized (CloudSync.this) {
                running.remove(scId);
            }
            boolean success = ok;
            String text = message;
            main.post(() -> {
                if (result != null) result.done(success, text);
            });
        });
    }

    // ------------------------------------------------------------------ the work

    static final class MissingProject extends Exception {
        MissingProject(String message) {
            super(message);
        }
    }

    /** Makes the .swb of one project, uploads it and deletes it again; returns the project's name. */
    String upload(String scId) throws Exception {
        HashMap<String, Object> project = lC.b(scId);
        if (project == null) throw new MissingProject("Project #" + scId + " isn't on this phone any more.");
        String name = yB.c(project, "my_ws_name");
        String pkg = yB.c(project, "my_sc_pkg_name");
        String version = yB.c(project, "sc_ver_name");
        TokenStore store = TokenStore.get(app);
        String login = store.login();
        if (login == null) throw new GitHubException("unauthorized", "Sign in with GitHub first.");
        String token = store.token();
        if (token == null) {
            throw new GitHubException("keystore", "The sign-in couldn't be read from the Android Keystore just now. Try again.");
        }
        CloudBackup cloud = new CloudBackup(token, login);
        try {
            // the network and the token first: offline nothing is zipped
            cloud.ensureRepository();
            BackupFactory factory = new BackupFactory(scId);
            factory.setBackupLocalLibs(false);
            factory.setBackupCustomBlocks(true);
            // the saved project, read with its own instances: never the editor's shared copy (it may be open)
            factory.setReadSavedDataOnly(true);
            factory.backup(name);
            File swb = factory.getOutFile();
            if (swb == null) throw new GitHubException("backup", "The backup file couldn't be made: " + factory.getError());
            try {
                cloud.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME);
            } finally {
                // the .swb was made for the upload only (and its folder, when nothing else is in it)
                //noinspection ResultOfMethodCallIgnored
                swb.delete();
                File folder = swb.getParentFile();
                String[] left = folder == null ? null : folder.list();
                //noinspection ResultOfMethodCallIgnored
                if (left != null && left.length == 0) folder.delete();
            }
        } catch (GitHubException e) {
            if ("http_401".equals(e.code) && token.equals(store.token())) {
                // this very token was revoked on GitHub: signed out here too; the project waits for the next sign-in
                GitHubSession.signOut(app);
                throw new GitHubException("signed_out", "GitHub doesn't accept the sign-in any more. Sign in again: the backup waits.");
            }
            throw e;
        }
        return name;
    }
}
