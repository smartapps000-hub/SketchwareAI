package com.sketchware.ai.github;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.remod.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.jbk.util.LogUtil;

/**
 * "Cloud Sync & Account" (v1.9.8 GitHub screen, redone in v1.9.9 after the owner's phone test of 12 Nov): GitHub sign-in
 * through {@link SignIn} (it outlives this screen and the trip to the browser), the projects of this phone with Back up
 * and Restore each, and the backups that are only on GitHub. Opened from the main drawer, or by {@link AuthGuard} as
 * the sign-in gate ({@link #EXTRA_GATE}): then Back leaves the app while sign-in is required.
 */
public class GitHubActivity extends BaseAppCompatActivity {

    public static final String EXTRA_GATE = "sketchware_ai_gate";
    private static final int REQUEST_BROWSER = 7311;

    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final SignIn.Listener listener = this::onSignInChanged;
    private LinearLayout list;
    private TokenStore store;
    private SignIn signIn;
    private String clientId;
    private boolean gate;
    private volatile boolean closed;
    private boolean resumed;
    private String status;
    private boolean statusIsError;
    private String statusDetail;
    private boolean busy;
    private String workingPath;
    private List<HashMap<String, Object>> local;
    private List<CloudBackup.Entry> cloud;
    private boolean loadingProjects;
    private String projectsError;
    private Drawable avatar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github);
        ObsidianWindow.apply(this);
        list = findViewById(R.id.gh_list);
        gate = getIntent().getBooleanExtra(EXTRA_GATE, false);
        findViewById(R.id.gh_back).setOnClickListener(v -> onBackPressed());
        store = TokenStore.get(this);
        signIn = SignIn.get(this);
        // the app's own public Client ID (strings.xml); users never see or type it
        clientId = getString(R.string.github_client_id);
        GitHubHttp.userAgent = "SketchwareAI/" + com.sketchware.remod.BuildConfig.VERSION_NAME;
        // a sign-in the system stopped (the app was closed while the browser was open) goes on
        signIn.resumeSaved(clientId);
        avatar = loadAvatar();
        render();
        if (store.login() != null) loadProjects();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_GATE, false)) gate = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        if (gate && store.login() != null && AuthGuard.verifyHardwareBoundSession(this)) {
            // signed in (the tab was closed for us, or another screen): nothing to gate
            String login = signIn.takeSignedIn();
            if (login != null) toast("Signed in as @" + login);
            finish();
            return;
        }
        signIn.addListener(listener);
        // back from the browser: ask GitHub at once instead of at the next interval
        signIn.pollNow();
        onSignInChanged();
    }

    @Override
    public void onPause() {
        resumed = false;
        signIn.removeListener(listener);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        signIn.forgetBrowser(this);
        // no interrupt: an upload under way finishes (and removes its .swb) after the screen is gone
        work.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (gate && AuthGuard.required(this) && !AuthGuard.verifyHardwareBoundSession(this)) {
            // the gate can't be dismissed: Back leaves the app
            finishAffinity();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ sign-in

    private void onSignInChanged() {
        if (closed) return;
        String login = signIn.takeSignedIn();
        if (login != null) {
            AuthGuard.reset();
            toast("Signed in as @" + login);
            if (gate) {
                // the gate's job is done: back to where the app was going
                finish();
                return;
            }
            loadProjects();
        }
        // SignIn saves the picture after the sign-in; it may arrive a moment later
        if (avatar == null) avatar = loadAvatar();
        if (resumed && signIn.takeOpenBrowser()) openVerification();
        render();
    }

    private void openVerification() {
        DeviceFlow.Code code = signIn.pending();
        if (code == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", code.userCode));
        String url = code.verificationUriComplete != null ? code.verificationUriComplete : code.verificationUri;
        try {
            // for a result: the tab then belongs to this screen, and SignIn can close it (finishActivity) when GitHub
            // answers, which brings this screen back without starting an activity from the background
            startActivityForResult(customTab(this, url), REQUEST_BROWSER);
            signIn.browserOpenedBy(this, REQUEST_BROWSER);
            toast("Code copied — paste on GitHub");
        } catch (android.content.ActivityNotFoundException e) {
            // sign-in can't work without a browser: a required sign-in is lifted for this run
            AuthGuard.markUnavailable();
            setStatus("No browser found. Open github.com/login/device", true, e.toString());
            render();
        }
    }

    private void signOut() {
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Sign out of GitHub?")
                .setMessage("Your backups on GitHub stay.")
                .setPositiveButton("Sign out", (d, w) -> {
                    store.clear();
                    AuthGuard.reset();
                    cloud = null;
                    avatar = null;
                    new File(getFilesDir(), "github_avatar.png").delete();
                    setStatus(null, false, null);
                    render();
                    if (AuthGuard.required(this)) gate = true;
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ screen

    private void render() {
        if (closed) return;
        String login = store.login();
        ((TextView) findViewById(R.id.gh_subtitle)).setText(login == null ? (gate ? "Sign in to continue" : "Not signed in")
                : "@" + login);
        findViewById(R.id.gh_back).setVisibility(gate && login == null ? View.INVISIBLE : View.VISIBLE);
        GitHubScreen.State s = new GitHubScreen.State();
        s.supported = TokenStore.supported();
        s.gate = gate;
        s.login = login;
        s.avatar = avatar;
        DeviceFlow.Code code = signIn.pending();
        s.userCode = code == null ? null : code.userCode;
        s.finishing = signIn.finishing();
        s.busy = busy || signIn.busy();
        if (login == null && signIn.error() != null && code == null) {
            s.status = signIn.error();
            s.statusIsError = true;
            s.statusDetail = signIn.errorDetail();
        } else {
            s.status = status;
            s.statusIsError = statusIsError;
            s.statusDetail = statusDetail;
        }
        s.loadingProjects = loadingProjects;
        s.projectsError = projectsError;
        if (local != null) {
            s.projects = new ArrayList<>();
            for (HashMap<String, Object> project : local) {
                CloudBackup.Entry entry = cloudEntryOf(project);
                GitHubScreen.Row row = new GitHubScreen.Row();
                row.title = yB.c(project, "my_ws_name");
                row.detail = entry != null ? "Backed up " + shortTime(entry.time)
                        : cloud == null ? yB.c(project, "my_sc_pkg_name") : "Not backed up yet";
                row.canBackUp = true;
                row.canRestore = entry != null;
                row.working = pathOf(project).equals(workingPath) ? "Backing up…" : null;
                s.projects.add(row);
            }
            s.cloudOnly = new ArrayList<>();
            for (CloudBackup.Entry entry : cloudOnly()) {
                GitHubScreen.Row row = new GitHubScreen.Row();
                row.title = entry.name == null ? entry.path : entry.name;
                row.detail = (entry.packageName == null ? "" : entry.packageName + " · ") + shortTime(entry.time);
                row.canRestore = true;
                s.cloudOnly.add(row);
            }
        }
        GitHubScreen.render(this, list, s, new GitHubScreen.Actions() {
            @Override
            public void signIn() {
                if (!busy) GitHubActivity.this.signIn.start(clientId);
            }

            @Override
            public void openVerification() {
                GitHubActivity.this.openVerification();
            }

            @Override
            public void cancelSignIn() {
                GitHubActivity.this.signIn.cancel();
            }

            @Override
            public void signOut() {
                GitHubActivity.this.signOut();
            }

            @Override
            public void backUp(int index) {
                if (!busy && local != null && index < local.size()) GitHubActivity.this.backUp(local.get(index));
            }

            @Override
            public void restore(int index) {
                if (busy || local == null || index >= local.size()) return;
                CloudBackup.Entry entry = cloudEntryOf(local.get(index));
                if (entry != null) showHistory(entry);
            }

            @Override
            public void restoreCloudOnly(int index) {
                List<CloudBackup.Entry> only = cloudOnly();
                if (!busy && index < only.size()) showHistory(only.get(index));
            }

            @Override
            public void refresh() {
                loadProjects();
            }

            @Override
            public void copyDetails() {
                String detail = statusDetail != null ? statusDetail : GitHubActivity.this.signIn.errorDetail();
                if (detail == null) return;
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Sign-in details", "Sketchware AI "
                            + com.sketchware.remod.BuildConfig.VERSION_NAME + "\n" + detail));
                }
                toast("Details copied");
            }
        });
    }

    private void setStatus(String text, boolean isError, String detail) {
        status = text;
        statusIsError = isError && text != null;
        statusDetail = isError ? detail : null;
    }

    // ------------------------------------------------------------------ projects

    private String pathOf(HashMap<String, Object> project) {
        return CloudBackup.pathFor(yB.c(project, "my_sc_pkg_name"), yB.c(project, "my_ws_name"));
    }

    private CloudBackup.Entry cloudEntryOf(HashMap<String, Object> project) {
        if (cloud == null) return null;
        String path = pathOf(project);
        for (CloudBackup.Entry entry : cloud) if (path.equals(entry.path)) return entry;
        return null;
    }

    private List<CloudBackup.Entry> cloudOnly() {
        List<CloudBackup.Entry> only = new ArrayList<>();
        if (cloud == null || local == null) return only;
        for (CloudBackup.Entry entry : cloud) {
            boolean here = false;
            for (HashMap<String, Object> project : local) here |= pathOf(project).equals(entry.path);
            if (!here) only.add(entry);
        }
        return only;
    }

    /** The phone's projects at once, then what is on GitHub. */
    private void loadProjects() {
        if (store.login() == null || loadingProjects) {
            render();
            return;
        }
        loadingProjects = true;
        projectsError = null;
        render();
        work.execute(() -> {
            List<HashMap<String, Object>> projects;
            try {
                projects = lC.a();
            } catch (RuntimeException e) {
                projects = new ArrayList<>();
            }
            List<HashMap<String, Object>> found = projects;
            ui(() -> {
                local = found;
                render();
            });
            try {
                String token = requireToken();
                CloudBackup backup = new CloudBackup(token, store.login());
                backup.ensureRepository();
                List<CloudBackup.Entry> entries = backup.list();
                ui(() -> {
                    loadingProjects = false;
                    cloud = entries;
                    render();
                });
            } catch (GitHubException e) {
                ui(() -> {
                    loadingProjects = false;
                    projectsError = e.getMessage();
                    if ("unauthorized".equals(e.code) || "http_401".equals(e.code)) {
                        store.clear();
                        AuthGuard.reset();
                    }
                    render();
                });
            } catch (Exception e) {
                ui(() -> {
                    loadingProjects = false;
                    projectsError = networkText(e);
                    render();
                });
            }
        });
    }

    private void backUp(HashMap<String, Object> project) {
        String scId = yB.c(project, "sc_id");
        String name = yB.c(project, "my_ws_name");
        String pkg = yB.c(project, "my_sc_pkg_name");
        String version = yB.c(project, "sc_ver_name");
        busy = true;
        workingPath = pathOf(project);
        setStatus(null, false, null);
        render();
        work.execute(() -> {
            File swb = null;
            try {
                BackupFactory factory = new BackupFactory(scId);
                factory.setBackupLocalLibs(false);
                factory.setBackupCustomBlocks(true);
                factory.backup(name);
                swb = factory.getOutFile();
                if (swb == null) throw new GitHubException("backup", "The backup file couldn't be made: " + factory.getError());
                String token = requireToken();
                CloudBackup backup = new CloudBackup(token, store.login());
                backup.ensureRepository();
                backup.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME);
                // the .swb was made for the upload only
                swb.delete();
                List<CloudBackup.Entry> entries = backup.list();
                ui(() -> {
                    busy = false;
                    workingPath = null;
                    cloud = entries;
                    toast(name + " backed up to GitHub");
                    render();
                });
            } catch (Exception e) {
                File kept = swb;
                ui(() -> {
                    busy = false;
                    workingPath = null;
                    String text = e instanceof GitHubException ? e.getMessage() : networkText(e);
                    setStatus("Back up of " + name + " failed: " + text, true, e.toString()
                            + (kept != null && kept.isFile() ? "\nThe backup is on the phone: " + kept : ""));
                    if (e instanceof GitHubException && ("unauthorized".equals(((GitHubException) e).code)
                            || "http_401".equals(((GitHubException) e).code))) {
                        store.clear();
                        AuthGuard.reset();
                    }
                    render();
                });
            }
        });
    }

    // ------------------------------------------------------------------ restore

    private void showHistory(CloudBackup.Entry entry) {
        if (store.login() == null || busy) return;
        busy = true;
        setStatus("Reading the backups of " + entry.name + "…", false, null);
        render();
        work.execute(() -> {
            try {
                String token = requireToken();
                List<CloudBackup.Version> versions = new CloudBackup(token, store.login()).history(entry.path);
                ui(() -> {
                    busy = false;
                    setStatus(null, false, null);
                    render();
                    if (versions.isEmpty()) {
                        toast("No backups of " + entry.name + " were found.");
                        return;
                    }
                    String[] labels = new String[versions.size()];
                    for (int i = 0; i < labels.length; i++) {
                        CloudBackup.Version v = versions.get(i);
                        labels[i] = v.date.replace('T', ' ').replace("Z", " UTC") + (i == 0 ? "  (newest)" : "");
                    }
                    new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                            .setTitle("Restore " + entry.name + " as a new project")
                            .setItems(labels, (d, which) -> restore(entry, versions.get(which)))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                ui(() -> {
                    busy = false;
                    setStatus(e instanceof GitHubException ? e.getMessage() : networkText(e), true, e.toString());
                    render();
                });
            }
        });
    }

    private void restore(CloudBackup.Entry entry, CloudBackup.Version version) {
        if (store.login() == null || busy) return;
        busy = true;
        setStatus("Downloading " + entry.name + "…", false, null);
        render();
        work.execute(() -> {
            try {
                String token = requireToken();
                File folder = new File(BackupFactory.getBackupDir(), "cloud");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Can't create " + folder);
                String base = new File(entry.path).getName().replace(".swb", "");
                File target = new File(folder, base + " " + version.sha.substring(0, 7) + ".swb");
                new CloudBackup(token, store.login()).download(entry.path, version.sha, target);
                boolean withLibraries = BackupFactory.zipContainsFile(target.getAbsolutePath(), "local_libs");
                int before = local == null ? 0 : local.size();
                ui(() -> {
                    busy = false;
                    setStatus(null, false, null);
                    render();
                    // the app's own restore (new project id, progress and result messages)
                    new BackupRestoreManager(this).doRestore(target.getAbsolutePath(), withLibraries);
                    watchForRestored(before, 0);
                });
            } catch (Exception e) {
                ui(() -> {
                    busy = false;
                    setStatus(e instanceof GitHubException ? e.getMessage() : networkText(e), true, e.toString());
                    render();
                });
            }
        });
    }

    /** The restore runs on its own: the list is read again until the new project is there (up to a minute). */
    private void watchForRestored(int before, int tries) {
        if (closed || tries > 30) return;
        // the Handler's callbacks are removed in onDestroy (a View's postDelayed survives the screen and would hand
        // work to the shut-down executor)
        handler.postDelayed(() -> {
            if (closed) return;
            try {
                work.execute(() -> readRestored(before, tries));
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }, 2000);
    }

    private void readRestored(int before, int tries) {
        List<HashMap<String, Object>> projects;
        try {
            projects = lC.a();
        } catch (RuntimeException e) {
            return;
        }
        List<HashMap<String, Object>> found = projects;
        ui(() -> {
            if (found.size() > before) {
                local = found;
                render();
            } else {
                watchForRestored(before, tries + 1);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A Chrome Custom Tab for {@code url} without the androidx.browser library: the Custom Tabs protocol's extras (a
     * session key, here none; the toolbar colour; no share button) and the keep-alive extra naming
     * {@link KeepAliveService}, which the browser binds to while the tab is open. A browser without Custom Tabs opens a
     * normal page.
     */
    static Intent customTab(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        Bundle extras = new Bundle();
        extras.putBinder("android.support.customtabs.extra.SESSION", null);
        intent.putExtras(extras);
        intent.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF0B0B0D);
        intent.putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2);
        intent.putExtra("android.support.customtabs.extra.KEEP_ALIVE",
                new Intent().setClassName(context.getPackageName(), KeepAliveService.class.getName()));
        return intent;
    }

    /** The token, read on the worker thread (a Keystore decryption). */
    private String requireToken() throws GitHubException {
        String token = store.token();
        if (token == null) {
            throw new GitHubException(store.login() == null ? "unauthorized" : "keystore",
                    store.login() == null ? "You're signed out. Sign in again."
                            : "The sign-in couldn't be read from the Android Keystore just now. Try again.");
        }
        return token;
    }

    private Drawable loadAvatar() {
        File file = new File(getFilesDir(), "github_avatar.png");
        if (store.login() == null || !file.isFile()) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        return bitmap == null ? null : new BitmapDrawable(getResources(), bitmap);
    }

    private void ui(Runnable r) {
        if (closed) return;
        runOnUiThread(() -> {
            if (!closed && !isFinishing()) r.run();
        });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /** "12 Nov, 14:05" this year, "3 Nov 2025" before (fits a project row next to its two buttons). */
    static String shortTime(long time) {
        java.util.Calendar then = java.util.Calendar.getInstance();
        then.setTimeInMillis(time);
        boolean thisYear = then.get(java.util.Calendar.YEAR) == java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        return new java.text.SimpleDateFormat(thisYear ? "d MMM, HH:mm" : "d MMM yyyy", java.util.Locale.ENGLISH)
                .format(new java.util.Date(time));
    }

    static String networkText(Exception e) {
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                || e instanceof java.net.SocketTimeoutException) {
            return "No connection to GitHub.";
        }
        if (e instanceof java.security.GeneralSecurityException) {
            return "The sign-in couldn't be read on this phone.";
        }
        return "Something went wrong. Please try again.";
    }
}
