package com.sketchware.ai.github;

import android.animation.AnimatorInflater;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.remod.R;

import mod.jbk.util.LogUtil;

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

/**
 * v1.9.8 (owner's directive of 12 Nov): GitHub sign-in (Device Flow, token in the Android Keystore) and cloud backup
 * of projects to the user's private repository {@value CloudBackup#REPO}. Opened from the main drawer.
 */
public class GitHubActivity extends BaseAppCompatActivity {

    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private TokenStore store;
    /** Set while the Device Flow waits; cleared to stop it. */
    private volatile boolean waiting;
    private volatile boolean closed;
    private DeviceFlow.Code shownCode;
    private String status;
    private boolean statusIsError;
    private List<CloudBackup.Entry> backups;
    private String backupsError;
    private boolean loadingBackups;
    private boolean busy;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github);
        ObsidianWindow.apply(this);
        list = findViewById(R.id.gh_list);
        findViewById(R.id.gh_back).setOnClickListener(v -> finish());
        store = TokenStore.get(this);
        GitHubHttp.userAgent = "SketchwareAI/" + com.sketchware.remod.BuildConfig.VERSION_NAME;
        render();
        if (store.login() != null) loadBackups();
    }

    @Override
    public void onDestroy() {
        closed = true;
        waiting = false;
        // no interrupt: an upload under way finishes (and removes its .swb) after the screen is gone
        work.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ screen

    private void render() {
        if (closed) return;
        String login = store.login();
        ((TextView) findViewById(R.id.gh_subtitle)).setText(login == null ? "Not signed in" : "@" + login);
        GitHubScreen.State s = new GitHubScreen.State();
        s.supported = TokenStore.supported();
        s.login = login;
        s.userCode = shownCode == null ? null : shownCode.userCode;
        s.status = status;
        s.statusIsError = statusIsError;
        s.busy = busy;
        s.loadingBackups = loadingBackups;
        s.backupsError = backupsError;
        if (backups != null) {
            s.backups = new ArrayList<>();
            for (CloudBackup.Entry entry : backups) {
                s.backups.add(new String[]{entry.name == null ? entry.path : entry.name,
                        (entry.packageName == null ? "" : entry.packageName + " · ")
                                + (entry.versionName == null ? "" : "v" + entry.versionName + " · ")
                                + CloudBackup.formatTime(entry.time) + " · " + Math.max(1, entry.size >> 10) + " KB"});
            }
        }
        GitHubScreen.render(this, list, s, new GitHubScreen.Actions() {
            @Override
            public void signIn() {
                if (!busy) startSignIn();
            }

            @Override
            public void openVerification() {
                GitHubActivity.this.openVerification();
            }

            @Override
            public void cancelSignIn() {
                waiting = false;
                shownCode = null;
                setStatus(null, false);
                render();
            }

            @Override
            public void signOut() {
                GitHubActivity.this.signOut();
            }

            @Override
            public void backUp() {
                if (!busy) pickProjectToBackUp();
            }

            @Override
            public void refreshBackups() {
                loadBackups();
            }

            @Override
            public void openBackup(int index) {
                if (backups != null && index < backups.size()) showHistory(backups.get(index));
            }
        });
    }

    private void setStatus(String text, boolean isError) {
        status = text;
        statusIsError = isError && text != null;
    }

    // ------------------------------------------------------------------ sign-in

    private void startSignIn() {
        // the app's own public Client ID (strings.xml); users never see or type it
        String clientId = getString(R.string.github_client_id);
        busy = true;
        setStatus(null, false);
        render();
        work.execute(() -> {
            try {
                DeviceFlow.Code code = DeviceFlow.start(clientId, DeviceFlow.SCOPE);
                waiting = true;
                ui(() -> {
                    busy = false;
                    shownCode = code;
                    render();
                    // one tap: the code is on the clipboard and GitHub's page opens
                    openVerification();
                });
                String token = DeviceFlow.waitForToken(clientId, code, new DeviceFlow.Waiting() {
                    @Override
                    public boolean cancelled() {
                        return !waiting || closed;
                    }

                    @Override
                    public void sleep(long millis) throws InterruptedException {
                        for (long slept = 0; slept < millis && waiting && !closed; slept += 250) Thread.sleep(250);
                    }
                });
                ui(() -> {
                    setStatus("Approved. Reading your account…", false);
                    render();
                });
                String login = CloudBackup.login(token);
                store.save(token, login);
                ui(() -> {
                    waiting = false;
                    shownCode = null;
                    setStatus(null, false);
                    toast("Signed in as @" + login);
                    render();
                    loadBackups();
                });
            } catch (GitHubException e) {
                ui(() -> {
                    busy = false;
                    waiting = false;
                    shownCode = null;
                    LogUtil.e("GitHubActivity", "Sign-in failed: " + e.code + ": " + e.getMessage());
                    setStatus("cancelled".equals(e.code) ? null : shortMessage(e), true);
                    render();
                });
            } catch (Exception e) {
                ui(() -> {
                    busy = false;
                    waiting = false;
                    shownCode = null;
                    LogUtil.e("GitHubActivity", "Sign-in failed", e);
                    setStatus(shortNetworkText(e), true);
                    render();
                });
            }
        });
    }

    /** One short line for the user; the full text is in the log. */
    static String shortMessage(GitHubException e) {
        switch (e.code) {
            case "access_denied":
                return "Sign-in was cancelled.";
            case "expired_token":
            case "token_expired":
                return "The code expired. Tap Sign in again.";
            case "device_flow_disabled":
            case "incorrect_client_credentials":
            case "http_404":
            case "no_client_id":
                return "GitHub sign-in isn't available yet. Please try later.";
            default:
                return "Sign-in didn't work. Please try again.";
        }
    }

    static String shortNetworkText(Exception e) {
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                || e instanceof java.net.SocketTimeoutException) {
            return "No internet connection.";
        }
        return "Sign-in didn't work. Please try again.";
    }

    private void openVerification() {
        if (shownCode == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", shownCode.userCode));
        String url = shownCode.verificationUriComplete != null ? shownCode.verificationUriComplete : shownCode.verificationUri;
        try {
            startActivity(customTab(url));
            toast("Code copied — paste on GitHub");
        } catch (android.content.ActivityNotFoundException e) {
            setStatus("No browser found. Open github.com/login/device", true);
            render();
        }
    }

    private void signOut() {
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Sign out of GitHub?")
                .setMessage("Your backups on GitHub stay.")
                .setPositiveButton("Sign out", (d, w) -> {
                    store.clear();
                    backups = null;
                    setStatus(null, false);
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ backup

    private void pickProjectToBackUp() {
        busy = true;
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
                busy = false;
                render();
                if (found.isEmpty()) {
                    toast("There are no projects to back up.");
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = yB.c(found.get(i), "my_ws_name") + "  ·  #" + yB.c(found.get(i), "sc_id");
                }
                new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                        .setTitle("Back up which project?")
                        .setItems(names, (d, which) -> confirmBackup(found.get(which)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void confirmBackup(HashMap<String, Object> project) {
        String name = yB.c(project, "my_ws_name");
        CheckBox libraries = new CheckBox(this);
        libraries.setText("Include the project's local libraries (can be large)");
        libraries.setTextColor(0xFFF5F5F7);
        libraries.setPadding(dp(4), dp(8), dp(4), dp(8));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(libraries);
        AlertDialog dialog = new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Back up " + name + "?")
                .setMessage("A .swb of the project (with its custom blocks) is uploaded to " + store.login() + "/"
                        + CloudBackup.REPO + " as a new commit.")
                .setView(box)
                .setPositiveButton("Back up", (d, w) -> backUp(project, libraries.isChecked()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    private void backUp(HashMap<String, Object> project, boolean includeLibraries) {
        String scId = yB.c(project, "sc_id");
        String name = yB.c(project, "my_ws_name");
        String pkg = yB.c(project, "my_sc_pkg_name");
        String version = yB.c(project, "sc_ver_name");
        busy = true;
        setStatus("Making the backup of " + name + "…", false);
        render();
        work.execute(() -> {
            File swb = null;
            try {
                BackupFactory factory = new BackupFactory(scId);
                factory.setBackupLocalLibs(includeLibraries);
                factory.setBackupCustomBlocks(true);
                factory.backup(name);
                swb = factory.getOutFile();
                if (swb == null) throw new GitHubException("backup", "The backup file couldn't be made: " + factory.getError());
                String token = requireToken();
                ui(() -> {
                    setStatus("Uploading " + name + "…", false);
                    render();
                });
                CloudBackup cloud = new CloudBackup(token, store.login());
                cloud.ensureRepository();
                cloud.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME);
                // the .swb was made for the upload only
                swb.delete();
                ui(() -> {
                    busy = false;
                    setStatus(name + " was backed up (" + CloudBackup.formatTime(System.currentTimeMillis()) + ").", false);
                    render();
                    loadBackups();
                });
            } catch (GitHubException e) {
                File kept = swb;
                ui(() -> {
                    busy = false;
                    setStatus(e.getMessage() + (kept != null && kept.isFile() ? " The backup is on the phone: " + kept : ""), true);
                    if ("unauthorized".equals(e.code) || "http_401".equals(e.code)) store.clear();
                    render();
                });
            } catch (Exception e) {
                File kept = swb;
                ui(() -> {
                    busy = false;
                    setStatus(networkText(e) + (kept != null && kept.isFile() ? " The backup is on the phone: " + kept : ""), true);
                    render();
                });
            }
        });
    }

    private void loadBackups() {
        if (store.login() == null) {
            render();
            return;
        }
        loadingBackups = true;
        backupsError = null;
        render();
        work.execute(() -> {
            try {
                String token = requireToken();
                CloudBackup cloud = new CloudBackup(token, store.login());
                cloud.ensureRepository();
                List<CloudBackup.Entry> entries = cloud.list();
                ui(() -> {
                    loadingBackups = false;
                    backups = entries;
                    render();
                });
            } catch (GitHubException e) {
                ui(() -> {
                    loadingBackups = false;
                    backupsError = e.getMessage();
                    if ("unauthorized".equals(e.code) || "http_401".equals(e.code)) store.clear();
                    render();
                });
            } catch (Exception e) {
                ui(() -> {
                    loadingBackups = false;
                    backupsError = networkText(e);
                    render();
                });
            }
        });
    }

    // ------------------------------------------------------------------ restore

    private void showHistory(CloudBackup.Entry entry) {
        if (store.login() == null || busy) return;
        busy = true;
        setStatus("Reading the backups of " + entry.name + "…", false);
        render();
        work.execute(() -> {
            try {
                String token = requireToken();
                CloudBackup cloud = new CloudBackup(token, store.login());
                List<CloudBackup.Version> versions = cloud.history(entry.path);
                ui(() -> {
                    busy = false;
                    setStatus(null, false);
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
                            .setTitle("Restore " + entry.name)
                            .setItems(labels, (d, which) -> restore(entry, versions.get(which)))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                ui(() -> {
                    busy = false;
                    setStatus(e instanceof GitHubException ? e.getMessage() : networkText(e), true);
                    render();
                });
            }
        });
    }

    private void restore(CloudBackup.Entry entry, CloudBackup.Version version) {
        if (store.login() == null || busy) return;
        busy = true;
        setStatus("Downloading the backup…", false);
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
                ui(() -> {
                    busy = false;
                    setStatus("Restoring " + entry.name + " as a new project…", false);
                    render();
                    // the app's own restore (new project id, progress and result messages)
                    new BackupRestoreManager(this).doRestore(target.getAbsolutePath(), withLibraries);
                });
            } catch (Exception e) {
                ui(() -> {
                    busy = false;
                    setStatus(e instanceof GitHubException ? e.getMessage() : networkText(e), true);
                    render();
                });
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A Chrome Custom Tab for {@code url} without the androidx.browser library: the documented extras of the Custom
     * Tabs protocol (a session key, here none, and the toolbar colour). A browser without Custom Tabs opens it as a
     * normal page.
     */
    private static Intent customTab(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        Bundle extras = new Bundle();
        extras.putBinder("android.support.customtabs.extra.SESSION", null);
        intent.putExtras(extras);
        intent.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF0B0B0D);
        intent.putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2);
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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

    static String networkText(Exception e) {
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                || e instanceof java.net.SocketTimeoutException) {
            return "No connection to GitHub. Check the internet connection and try again.";
        }
        if (e instanceof java.security.GeneralSecurityException) {
            return "The token couldn't be stored in the Android Keystore (" + e.getMessage() + ").";
        }
        return "Failed: " + e;
    }
}
