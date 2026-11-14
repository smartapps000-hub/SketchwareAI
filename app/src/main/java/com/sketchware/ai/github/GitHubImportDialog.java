package com.sketchware.ai.github;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.remod.R;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.project.backup.BackupFactory;

/**
 * v2.0.0: the Restore card's "Import from GitHub" sheet. Signed in: the backups in the user's
 * {@code SketchwareAI-Projects} (each restores any of its versions). Always: a field for a GitHub link to a .swb file,
 * a folder or a repository ({@link GitHubImport}). The downloaded .swb goes to the app's own restore ({@link Restorer}),
 * the same as a .swb picked from the phone.
 * <p>
 * v2.0.1 (owner's 13 Nov phone report: hint and row cut): a short hint with the explanation on its own line, row titles
 * on up to two lines; the own backups list is kept and shown offline (restoring still needs the network); a linked
 * backup with local libraries asks before copying them (custom blocks always come with the project).
 */
public final class GitHubImportDialog {

    /** The app's restore of a downloaded file (BackupRestoreManager.doRestore with the projects list's callback). */
    public interface Restorer {
        /**
         * {@code librariesFrom}: the GitHub link (owner/repo) of someone else's backup, or null for an own one;
         * {@code done} (v2.0.2) hears the end on the main thread (the progress sheet shows it).
         */
        void restore(File swb, boolean withLocalLibraries, String librariesFrom, Restored done);
    }

    /** v2.0.2: the end of a restore. */
    public interface Restored {
        void done(boolean ok, String message);
    }

    private final Activity activity;
    private final Restorer restorer;
    private final GitHubViews v;
    private final ExecutorService work = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GitHubImport");
        t.setDaemon(true);
        return t;
    });
    private AlertDialog dialog;
    private LinearLayout backups;
    private TextView status;
    private EditText link;
    private TextView importButton;
    private LinearLayout found;
    private boolean busy;

    public static void show(Activity activity, Restorer restorer) {
        new GitHubImportDialog(activity, restorer).open();
    }

    private GitHubImportDialog(Activity activity, Restorer restorer) {
        this.activity = activity;
        this.restorer = restorer;
        this.v = new GitHubViews(activity);
    }

    private void open() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(v.dp(20), v.dp(8), v.dp(20), v.dp(4));
        scroll.addView(content);

        LinearLayout header = v.header(R.drawable.ob_ic_github, "GitHub import project", "Your backups or a GitHub link");
        // v2.0.2 (owner's 14 Nov directive): ⓘ opens the step-by-step guide
        android.widget.ImageView info = v.iconButton(R.drawable.ob_ic_help, "How to import from GitHub");
        info.setOnClickListener(x -> showGuide(activity));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(v.dp(40), v.dp(40));
        ip.leftMargin = v.dp(8);
        header.addView(info, ip);
        content.addView(header);

        link = new EditText(activity, null, 0, R.style.Ob_Text);
        link.setHint("Paste a GitHub link");
        link.setHintTextColor(v.color(R.color.ob_text_2));
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        link.setSingleLine(true);
        link.setGravity(android.view.Gravity.CENTER_VERTICAL);
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        link.setBackgroundResource(R.drawable.ob_field);
        link.setPadding(v.dp(14), 0, v.dp(14), 0);
        content.addView(link, v.full(48, 14));
        TextView explain = v.line("A repository, a folder or a .swb file on github.com", false);
        explain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        content.addView(explain, v.full(-1, 6));
        importButton = v.pill("Import from link", false, true, x -> importLink());
        content.addView(importButton, v.full(46, 10));

        status = v.line("", false);
        status.setVisibility(View.GONE);
        content.addView(status, v.full(-1, 10));
        // v2.0.2: the backups a repository or folder link holds, as rows (no plain list)
        found = new LinearLayout(activity);
        found.setOrientation(LinearLayout.VERTICAL);
        content.addView(found, v.full(-1, 4));

        TextView title = new TextView(activity, null, 0, R.style.Ob_Text_Title);
        title.setText("Your backups");
        content.addView(title, v.full(-1, 18));
        backups = new LinearLayout(activity);
        backups.setOrientation(LinearLayout.VERTICAL);
        content.addView(backups, v.full(-1, 4));

        dialog = new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setView(scroll)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnDismissListener(d -> work.shutdown());
        dialog.show();
        loadOwnBackups();
    }

    // ------------------------------------------------------------------ own backups

    private void loadOwnBackups() {
        backups.removeAllViews();
        TokenStore store = TokenStore.get(activity);
        if (store.login() == null) {
            backups.addView(v.row("Sign in to see your backups", "Projects you backed up from any phone",
                    v.smallPill("Sign in", true, true, x -> GitHubInAppAuthDialog.show(activity, login -> {
                        if (dialog.isShowing()) loadOwnBackups();
                    }))), v.full(-1, 8));
            return;
        }
        backups.addView(v.line("Loading…", false), v.full(-1, 8));
        String login = store.login();
        work.execute(() -> {
            CloudBackup cloud = null;
            try {
                String token = token();
                cloud = new CloudBackup(token, login);
                // looking must not create the repository: none yet = no backups yet
                List<CloudBackup.Entry> entries = cloud.openIfExists() ? cloud.list() : new java.util.ArrayList<>();
                saveCachedList(login, entries);
                CloudBackup c = cloud;
                ui(() -> showOwn(entries, c, 0));
            } catch (java.io.IOException e) {
                // offline (or GitHub didn't answer): the list from the last time, marked as such
                long[] at = new long[1];
                List<CloudBackup.Entry> cached = cachedList(login, at);
                CloudBackup c = cloud;
                ui(() -> {
                    if (cached != null && c != null) {
                        showOwn(cached, c, at[0]);
                    } else {
                        backups.removeAllViews();
                        backups.addView(v.line(text(e), true), v.full(-1, 8));
                    }
                });
            } catch (Exception e) {
                ui(() -> {
                    backups.removeAllViews();
                    backups.addView(v.line(text(e), true), v.full(-1, 8));
                });
            }
        });
    }

    /** v2.0.1: the last list of own backups, for showing it offline (names and times only, no token). */
    private void saveCachedList(String login, List<CloudBackup.Entry> entries) {
        activity.getSharedPreferences(CloudSync.PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putString("backup_list_login", login)
                .putString("backup_list", new com.google.gson.Gson().toJson(entries))
                .putLong("backup_list_at", System.currentTimeMillis())
                .apply();
    }

    private List<CloudBackup.Entry> cachedList(String login, long[] at) {
        android.content.SharedPreferences prefs = activity.getSharedPreferences(CloudSync.PREFS,
                android.content.Context.MODE_PRIVATE);
        if (login == null || !login.equals(prefs.getString("backup_list_login", null))) return null;
        try {
            CloudBackup.Entry[] list = new com.google.gson.Gson().fromJson(prefs.getString("backup_list", null),
                    CloudBackup.Entry[].class);
            if (list == null) return null;
            at[0] = prefs.getLong("backup_list_at", 0);
            return new java.util.ArrayList<>(java.util.Arrays.asList(list));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void showOwn(List<CloudBackup.Entry> entries, CloudBackup cloud, long offlineSince) {
        backups.removeAllViews();
        if (offlineSince > 0) {
            backups.addView(v.line("Offline · the list from " + CloudBackup.formatTime(offlineSince), false),
                    v.full(-1, 4));
        }
        if (entries.isEmpty()) {
            backups.addView(v.line("No backups yet. Use Back up > Back up to GitHub on a project.", false), v.full(-1, 8));
            return;
        }
        for (CloudBackup.Entry entry : entries) {
            String name = entry.name == null ? entry.path : entry.name;
            String detail = (entry.packageName == null ? "" : entry.packageName + " · ") + CloudBackup.formatTime(entry.time);
            backups.addView(v.row(name, detail, v.smallPill("Restore", true, true, x -> versions(cloud, entry, name))),
                    v.full(-1, 8));
        }
    }

    private void versions(CloudBackup cloud, CloudBackup.Entry entry, String name) {
        if (busy) return;
        setBusy(true, "Reading the backups of " + name + "…");
        work.execute(() -> {
            try {
                // review: the list may be the offline copy; looking must never create the repository
                if (!cloud.openIfExists()) {
                    throw new GitHubException("not_found", "Your backup repository " + CloudBackup.REPO
                            + " isn't on GitHub any more.");
                }
                List<CloudBackup.Version> list = cloud.history(entry.path);
                ui(() -> {
                    setBusy(false, null);
                    if (list.isEmpty()) {
                        setBusy(false, "No backups of " + name + " were found.");
                        return;
                    }
                    // v2.0.2: the versions as glass rows, newest first
                    ScrollView scroll = new ScrollView(activity);
                    LinearLayout rows = new LinearLayout(activity);
                    rows.setOrientation(LinearLayout.VERTICAL);
                    rows.setPadding(v.dp(20), v.dp(8), v.dp(20), v.dp(4));
                    scroll.addView(rows);
                    rows.addView(v.header(R.drawable.ob_ic_restore, "Restore " + name, "Always as a new project"));
                    AlertDialog[] sheet = new AlertDialog[1];
                    for (int i = 0; i < list.size(); i++) {
                        CloudBackup.Version version = list.get(i);
                        String when = version.date.replace('T', ' ').replace("Z", " UTC");
                        rows.addView(v.row(i == 0 ? "Newest backup" : "Backup " + (list.size() - i), when,
                                v.smallPill("Restore", true, true, x -> {
                                    sheet[0].dismiss();
                                    downloadOwn(cloud, entry, version, name);
                                })), v.full(-1, i == 0 ? 14 : 8));
                    }
                    sheet[0] = new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                            .setView(scroll)
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                ui(() -> setBusy(false, text(e)));
            }
        });
    }

    private void downloadOwn(CloudBackup cloud, CloudBackup.Entry entry, CloudBackup.Version version, String name) {
        GitHubProgress progress = startImport(name);
        background(() -> {
            try {
                File folder = new File(BackupFactory.getBackupDir(), "cloud");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Can't create " + folder);
                String base = new File(entry.path).getName().replace(".swb", "");
                File target = new File(folder, base + " " + version.sha.substring(0, 7) + ".swb");
                cloud.download(entry.path, version.sha, target, downloadSteps(progress, entry.size));
                boolean libraries = BackupFactory.zipContainsFile(target.getAbsolutePath(), "local_libs");
                onMain(() -> finish(progress, target, libraries, true, null));
            } catch (Exception e) {
                onMain(() -> progress.failed(text(e)));
            }
        });
    }

    /** v2.0.2: the import's progress sheet; the import sheet itself closes. */
    private GitHubProgress startImport(String name) {
        try {
            dialog.dismiss();
        } catch (RuntimeException ignored) {
        }
        GitHubProgress progress = GitHubProgress.show(activity, R.drawable.ob_ic_github, "Import " + name);
        progress.update(2, "Connecting to GitHub…");
        return progress;
    }

    /** Download bytes → 5-80% (the size from Content-Length, else the backup list's, else a slow creep). */
    private GitHubHttp.Progress downloadSteps(GitHubProgress progress, long knownSize) {
        int[] last = {4};
        return (done, total) -> {
            long all = total > 0 ? total : knownSize;
            int percent = all > 0 ? 5 + (int) (75 * Math.min(1.0, done / (double) all))
                    : Math.min(75, 5 + (int) (done / (512 * 1024)));
            if (percent <= last[0]) return;
            last[0] = percent;
            String text = "Downloading… " + CloudSync.megabytes(done) + (all > 0 ? " of " + CloudSync.megabytes(all) : "");
            onMain(() -> progress.update(percent, text));
        };
    }

    /** A download that outlives the import sheet (its own thread: the sheet's executor stops with the sheet). */
    private static void background(Runnable r) {
        Thread t = new Thread(r, "GitHubImportDownload");
        t.setDaemon(true);
        t.start();
    }

    private void onMain(Runnable r) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) r.run();
        });
    }

    // ------------------------------------------------------------------ a link

    private void importLink() {
        if (busy) return;
        GitHubImport.Source source = GitHubImport.parse(link.getText().toString());
        if (source == null) {
            setBusy(false, "That isn't a GitHub repository, folder or .swb link.");
            status.setTextColor(v.color(R.color.ob_error));
            return;
        }
        setBusy(true, "Looking at " + source.owner + "/" + source.repo + "…");
        work.execute(() -> {
            try {
                String token = TokenStore.get(activity).login() == null ? null : token();
                List<String> list = GitHubImport.listBackups(token, source);
                ui(() -> {
                    found.removeAllViews();
                    if (list.isEmpty()) {
                        setBusy(false, "No .swb backups in " + source.owner + "/" + source.repo + ".");
                    } else if (list.size() == 1 && source.file) {
                        setBusy(false, null);
                        downloadLink(token, source, list.get(0));
                    } else {
                        // v2.0.2: every backup found in the repository or folder, as rows with Import
                        setBusy(false, list.size() == 1 ? "1 backup in " + source.owner + "/" + source.repo + ":"
                                : list.size() + " backups in " + source.owner + "/" + source.repo + ":");
                        for (String path : list) {
                            String file = new File(path).getName();
                            String folder = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "(top folder)";
                            found.addView(v.row(file, folder, v.smallPill("Import", true, true,
                                    x -> downloadLink(token, source, path))), v.full(-1, 8));
                        }
                    }
                });
            } catch (Exception e) {
                ui(() -> setBusy(false, text(e)));
            }
        });
    }

    private void downloadLink(String token, GitHubImport.Source source, String path) {
        GitHubProgress progress = startImport(new File(path).getName());
        background(() -> {
            try {
                File target = GitHubImport.download(token, source, path, new File(BackupFactory.getBackupDir(), "github"),
                        downloadSteps(progress, -1));
                boolean libraries = BackupFactory.zipContainsFile(target.getAbsolutePath(), "local_libs");
                String from = source.owner + "/" + source.repo;
                onMain(() -> finish(progress, target, libraries, false, from));
            } catch (Exception e) {
                onMain(() -> progress.failed(text(e)));
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Hands the file to the app's restore (a new project, custom blocks included) and closes the sheet. A backup with
     * local libraries asks the question the .swb restore asks; v2.0.1: a linked backup (someone else's repository) asks
     * too, with a trust note, and "Without libraries" first. Libraries already on the phone are never replaced
     * (BackupFactory copies only missing ones).
     */
    private void finish(GitHubProgress progress, File swb, boolean hasLibraries, boolean own, String from) {
        progress.update(82, "Downloaded " + CloudSync.megabytes(swb.length()));
        if (!hasLibraries) {
            restoreWith(progress, swb, false, null);
        } else if (own) {
            new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                    .setTitle("Local libraries")
                    .setMessage("This backup holds local libraries. Copy them to this phone's libraries? Libraries "
                            + "already here are kept as they are.")
                    .setCancelable(false)
                    .setPositiveButton("Copy", (d, w) -> restoreWith(progress, swb, true, null))
                    .setNegativeButton("Don't copy", (d, w) -> restoreWith(progress, swb, false, null))
                    .show();
        } else {
            new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                    .setTitle("Local libraries from " + from)
                    .setMessage("This backup brings local libraries. Their code goes into the apps you build with "
                            + "them: copy them only if you trust " + from + ". Libraries already on this phone are "
                            + "never replaced; Sketchware AI never switches these on in another project by itself "
                            + "(the Library Resolver installs the Maven library of the same name over them). "
                            + "Without them, the Library Resolver can install the libraries from Maven.")
                    .setCancelable(false)
                    .setPositiveButton("Without libraries", (d, w) -> restoreWith(progress, swb, false, null))
                    .setNegativeButton("Copy libraries", (d, w) -> restoreWith(progress, swb, true, from))
                    .show();
        }
    }

    /** v2.0.2: the restore (a new project, custom blocks included) with its end on the progress sheet. */
    private void restoreWith(GitHubProgress progress, File swb, boolean libraries, String from) {
        progress.update(88, "Restoring as a new project" + (libraries ? " with its local libraries" : "") + "…");
        restorer.restore(swb, libraries, from, (ok, message) -> {
            if (progress.hidden()) {
                android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
            } else if (ok) {
                progress.done(message + " (custom blocks included)");
            } else {
                progress.failed(message);
            }
        });
    }

    /** v2.0.2: the ⓘ guide (three steps with diagrams, all drawn by the app: nothing to download). */
    public static void showGuide(Activity activity) {
        new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setView(new GitHubViews(activity).importGuide())
                .setPositiveButton("Got it", null)
                .show();
    }

    private String token() throws GitHubException {
        String token = TokenStore.get(activity).token();
        if (token == null) {
            throw new GitHubException("keystore", "The sign-in couldn't be read from the Android Keystore just now. Try again.");
        }
        return token;
    }

    private void setBusy(boolean busy, String text) {
        this.busy = busy;
        importButton.setEnabled(!busy);
        importButton.setAlpha(busy ? 0.5f : 1f);
        status.setTextColor(v.color(R.color.ob_text_2));
        status.setText(text == null ? "" : text);
        status.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void ui(Runnable r) {
        activity.runOnUiThread(() -> {
            if (dialog != null && dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed()) r.run();
        });
    }

    static String text(Exception e) {
        if (e instanceof GitHubException) return e.getMessage();
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                || e instanceof java.net.SocketTimeoutException) {
            return "No connection to GitHub.";
        }
        return "Something went wrong: " + e.getMessage();
    }
}
