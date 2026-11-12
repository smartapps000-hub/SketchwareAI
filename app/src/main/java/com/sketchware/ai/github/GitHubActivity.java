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
        work.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ screen

    private void render() {
        if (closed) return;
        list.removeAllViews();
        String login = store.login();
        ((TextView) findViewById(R.id.gh_subtitle)).setText(login == null ? "Sign-in and cloud backup" : "Signed in as @" + login);
        LinearLayout account = card();
        account.addView(title("Account"));
        if (!TokenStore.supported()) {
            account.addView(body("GitHub sign-in needs Android 6.0 or newer (its token is kept in the Android Keystore)."));
        } else if (login != null) {
            account.addView(body("Signed in as @" + login + ". The access token is encrypted with a key in this phone's "
                    + "Android Keystore; it is sent only to api.github.com."));
            account.addView(pill("Sign out", v -> signOut()));
        } else if (shownCode != null) {
            account.addView(body("On github.com/login/device, enter this code:"));
            TextView code = new TextView(this, null, 0, R.style.Ob_Text_Screen);
            code.setText(shownCode.userCode);
            code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            code.setLetterSpacing(0.08f);
            code.setGravity(Gravity.CENTER);
            code.setTextIsSelectable(true);
            code.setPadding(0, dp(10), 0, dp(10));
            account.addView(code, matchWrap());
            account.addView(cta("Copy code and open GitHub", v -> openVerification()));
            account.addView(caption(status == null ? "Waiting for you to approve on GitHub…" : status));
            account.addView(pill("Cancel", v -> {
                waiting = false;
                shownCode = null;
                status = null;
                render();
            }));
        } else {
            account.addView(body("Sign in with your GitHub account to back up projects to a private repository of yours ("
                    + CloudBackup.REPO + "). You enter a one-time code on github.com; no password is typed here. "
                    + "GitHub asks you to allow repository access (the \"repo\" permission: GitHub can't limit an app to "
                    + "one repository)."));
            EditText clientId = new EditText(this, null, 0, R.style.Ob_Field);
            clientId.setHint("OAuth app Client ID");
            clientId.setSingleLine(true);
            clientId.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            clientId.setText(store.clientId(getString(R.string.github_client_id)));
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            fieldParams.topMargin = dp(10);
            account.addView(clientId, fieldParams);
            account.addView(caption("The Client ID of the GitHub OAuth app (github.com > Settings > Developer settings > "
                    + "OAuth Apps), with \"Enable Device Flow\" ticked. It is public; no client secret is used."));
            if (status != null) account.addView(error(status));
            account.addView(cta(busy ? "Starting…" : "Sign in with GitHub", v -> {
                if (busy) return;
                store.setClientId(clientId.getText().toString());
                startSignIn();
            }));
        }
        list.addView(account, cardParams());

        if (login != null) {
            LinearLayout cloud = card();
            cloud.addView(title("Cloud backup"));
            cloud.addView(body("Projects are saved as .swb files in the private repository " + login + "/" + CloudBackup.REPO
                    + ". Every backup is a commit, so older backups stay and can be restored. A restore always makes a "
                    + "new project; existing projects are never overwritten."));
            cloud.addView(cta(busy ? "Working…" : "Back up a project", v -> {
                if (!busy) pickProjectToBackUp();
            }));
            if (status != null) cloud.addView(caption(status));
            list.addView(cloud, cardParams());

            LinearLayout saved = card();
            saved.addView(title("Backups in your repository"));
            if (loadingBackups) {
                saved.addView(caption("Loading…"));
            } else if (backupsError != null) {
                saved.addView(error(backupsError));
                saved.addView(pill("Try again", v -> loadBackups()));
            } else if (backups == null || backups.isEmpty()) {
                saved.addView(caption("No backups yet."));
            } else {
                for (CloudBackup.Entry entry : backups) saved.addView(backupRow(entry));
                saved.addView(pill("Refresh", v -> loadBackups()));
            }
            list.addView(saved, cardParams());
        }
    }

    private View backupRow(CloudBackup.Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.ob_pill16_state);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setClickable(true);
        row.setOnClickListener(v -> showHistory(entry));
        TextView name = new TextView(this, null, 0, R.style.Ob_Text_Label);
        name.setText(entry.name == null ? entry.path : entry.name);
        row.addView(name);
        row.addView(caption((entry.packageName == null ? "" : entry.packageName + " · ")
                + (entry.versionName == null ? "" : "v" + entry.versionName + " · ")
                + CloudBackup.formatTime(entry.time) + " · " + Math.max(1, entry.size >> 10) + " KB"));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    // ------------------------------------------------------------------ sign-in

    private void startSignIn() {
        String clientId = store.clientId(getString(R.string.github_client_id));
        busy = true;
        status = null;
        render();
        work.execute(() -> {
            try {
                DeviceFlow.Code code = DeviceFlow.start(clientId, DeviceFlow.SCOPE);
                waiting = true;
                ui(() -> {
                    busy = false;
                    shownCode = code;
                    render();
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
                    status = "Approved. Reading your account…";
                    render();
                });
                String login = CloudBackup.login(token);
                store.save(token, login);
                ui(() -> {
                    waiting = false;
                    shownCode = null;
                    status = null;
                    toast("Signed in as @" + login);
                    render();
                    loadBackups();
                });
            } catch (GitHubException e) {
                ui(() -> {
                    busy = false;
                    waiting = false;
                    shownCode = null;
                    status = "cancelled".equals(e.code) ? null : e.getMessage();
                    render();
                });
            } catch (Exception e) {
                ui(() -> {
                    busy = false;
                    waiting = false;
                    shownCode = null;
                    status = networkText(e);
                    render();
                });
            }
        });
    }

    private void openVerification() {
        if (shownCode == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", shownCode.userCode));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(shownCode.verificationUri)));
            toast("Code " + shownCode.userCode + " copied");
        } catch (android.content.ActivityNotFoundException e) {
            status = "No browser found. Open " + shownCode.verificationUri + " on any device and enter the code.";
            render();
        }
    }

    private void signOut() {
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Sign out of GitHub?")
                .setMessage("The token and its key are deleted from this phone. To withdraw the app's access completely, "
                        + "open github.com > Settings > Applications > Authorized OAuth Apps. Backups in your repository stay.")
                .setPositiveButton("Sign out", (d, w) -> {
                    store.clear();
                    backups = null;
                    status = null;
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
        status = "Making the backup of " + name + "…";
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
                String token = store.token();
                if (token == null) throw new GitHubException("unauthorized", "You were signed out. Sign in again.");
                ui(() -> {
                    status = "Uploading " + name + "…";
                    render();
                });
                CloudBackup cloud = new CloudBackup(token, store.login());
                cloud.ensureRepository();
                cloud.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME);
                // the .swb was made for the upload only
                swb.delete();
                ui(() -> {
                    busy = false;
                    status = name + " was backed up (" + CloudBackup.formatTime(System.currentTimeMillis()) + ").";
                    render();
                    loadBackups();
                });
            } catch (GitHubException e) {
                File kept = swb;
                ui(() -> {
                    busy = false;
                    status = e.getMessage() + (kept != null && kept.isFile() ? " The backup is on the phone: " + kept : "");
                    if ("unauthorized".equals(e.code) || "http_401".equals(e.code)) store.clear();
                    render();
                });
            } catch (Exception e) {
                File kept = swb;
                ui(() -> {
                    busy = false;
                    status = networkText(e) + (kept != null && kept.isFile() ? " The backup is on the phone: " + kept : "");
                    render();
                });
            }
        });
    }

    private void loadBackups() {
        String token = store.token();
        if (token == null) {
            render();
            return;
        }
        loadingBackups = true;
        backupsError = null;
        render();
        work.execute(() -> {
            try {
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
        String token = store.token();
        if (token == null || busy) return;
        busy = true;
        status = "Reading the backups of " + entry.name + "…";
        render();
        work.execute(() -> {
            try {
                CloudBackup cloud = new CloudBackup(token, store.login());
                List<CloudBackup.Version> versions = cloud.history(entry.path);
                ui(() -> {
                    busy = false;
                    status = null;
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
                    status = e instanceof GitHubException ? e.getMessage() : networkText(e);
                    render();
                });
            }
        });
    }

    private void restore(CloudBackup.Entry entry, CloudBackup.Version version) {
        String token = store.token();
        if (token == null) return;
        busy = true;
        status = "Downloading the backup…";
        render();
        work.execute(() -> {
            try {
                File folder = new File(BackupFactory.getBackupDir(), "cloud");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Can't create " + folder);
                String base = new File(entry.path).getName().replace(".swb", "");
                File target = new File(folder, base + " " + version.sha.substring(0, 7) + ".swb");
                new CloudBackup(token, store.login()).download(entry.path, version.sha, target);
                boolean withLibraries = BackupFactory.zipContainsFile(target.getAbsolutePath(), "local_libs");
                ui(() -> {
                    busy = false;
                    status = "Restoring " + entry.name + " as a new project…";
                    render();
                    // the app's own restore (new project id, progress and result messages)
                    new BackupRestoreManager(this).doRestore(target.getAbsolutePath(), withLibraries);
                });
            } catch (Exception e) {
                ui(() -> {
                    busy = false;
                    status = e instanceof GitHubException ? e.getMessage() : networkText(e);
                    render();
                });
            }
        });
    }

    // ------------------------------------------------------------------ views

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_accent_box);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(12);
        return params;
    }

    private TextView title(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Title);
        t.setText(text);
        return t;
    }

    private TextView body(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Body);
        t.setText(text);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private TextView caption(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private TextView error(String text) {
        TextView t = caption(text);
        t.setTextColor(getResources().getColor(R.color.ob_error, getTheme()));
        return t;
    }

    private TextView cta(String text, View.OnClickListener click) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Button_Cta);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_cta_state);
        t.setStateListAnimator(AnimatorInflater.loadStateListAnimator(this, R.animator.sw_press));
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.topMargin = dp(12);
        t.setLayoutParams(params);
        return t;
    }

    private TextView pill(String text, View.OnClickListener click) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Button);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_pill16_state);
        t.setStateListAnimator(AnimatorInflater.loadStateListAnimator(this, R.animator.sw_press));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.topMargin = dp(10);
        t.setLayoutParams(params);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
