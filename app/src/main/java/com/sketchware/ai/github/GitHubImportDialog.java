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

        // v2.0.5: a field that takes typing (GitHubViews.input)
        link = v.input("Paste a GitHub link", false, 1);
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
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

    /**
     * v2.0.3 (owner's 15 Nov phone test: "Loading…" for long, then "No connection to GitHub."): the list kept on this
     * phone is shown at once (backups made from this phone are added to it as they finish), and GitHub is asked in the
     * background; GitHub's answer replaces it. When GitHub can't be reached the kept list stays, marked, with Try again.
     */
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
        String login = store.login();
        long[] at = new long[1];
        List<CloudBackup.Entry> kept = cachedList(activity, login, at);
        if (kept != null) {
            showOwn(kept, login, "Checking GitHub for changes…", false);
        } else {
            backups.addView(v.line("Loading…", false), v.full(-1, 8));
        }
        work.execute(() -> {
            try {
                CloudBackup cloud = new CloudBackup(token(), login);
                // looking must not create the repository: none yet = no backups yet
                List<CloudBackup.Entry> entries = cloud.openIfExists() ? cloud.list() : new java.util.ArrayList<>();
                synchronized (LIST_LOCK) {
                    saveCachedList(activity, login, entries, true);
                }
                ui(() -> showOwn(entries, login, null, false));
            } catch (Exception e) {
                String why = text(e);
                ui(() -> {
                    if (kept != null) {
                        showOwn(kept, login, "Kept on this phone" + (at[0] > 0 ? " · checked " + CloudBackup.formatTime(at[0])
                                : "") + " · " + why, true);
                    } else {
                        backups.removeAllViews();
                        backups.addView(v.line(why, true), v.full(-1, 8));
                        backups.addView(v.smallPill("Try again", true, true, x -> loadOwnBackups()), pillAt(6));
                    }
                });
            }
        });
    }

    /** v2.0.3 review: the kept list is read and written by this sheet and by backups as they end: one at a time. */
    static final Object LIST_LOCK = new Object();

    /** v2.0.1: the last list of own backups, for showing it offline (names and times only, no token). */
    static void saveCachedList(android.content.Context context, String login, List<CloudBackup.Entry> entries,
                               boolean fromGitHub) {
        android.content.SharedPreferences.Editor edit = context.getSharedPreferences(CloudSync.PREFS,
                android.content.Context.MODE_PRIVATE).edit()
                .putString("backup_list_login", login)
                .putString("backup_list", new com.google.gson.Gson().toJson(entries));
        if (fromGitHub) edit.putLong("backup_list_at", System.currentTimeMillis());
        edit.apply();
    }

    static List<CloudBackup.Entry> cachedList(android.content.Context context, String login, long[] at) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(CloudSync.PREFS,
                android.content.Context.MODE_PRIVATE);
        if (login == null || !login.equals(prefs.getString("backup_list_login", null))) return null;
        try {
            CloudBackup.Entry[] list = new com.google.gson.Gson().fromJson(prefs.getString("backup_list", null),
                    CloudBackup.Entry[].class);
            if (list == null) return null;
            at[0] = prefs.getLong("backup_list_at", 0);
            List<CloudBackup.Entry> out = new java.util.ArrayList<>(java.util.Arrays.asList(list));
            // newest first
            java.util.Collections.sort(out, (x, y) -> Long.compare(y.time, x.time));
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void showOwn(List<CloudBackup.Entry> list, String login, String note, boolean failed) {
        backups.removeAllViews();
        // newest first, from GitHub as from the phone
        List<CloudBackup.Entry> entries = new java.util.ArrayList<>(list);
        java.util.Collections.sort(entries, (x, y) -> Long.compare(y.time, x.time));
        if (note != null) {
            backups.addView(v.line(note, failed), v.full(-1, 4));
            if (failed) backups.addView(v.smallPill("Try again", true, true, x -> loadOwnBackups()), pillAt(6));
        }
        if (entries.isEmpty()) {
            backups.addView(v.line("No backups yet. Use Back up > Back up to GitHub on a project.", false), v.full(-1, 8));
            return;
        }
        for (CloudBackup.Entry entry : entries) {
            String name = entry.name == null ? entry.path : entry.name;
            String detail = (entry.packageName == null ? "" : entry.packageName + " · ") + CloudBackup.formatTime(entry.time)
                    + (entry.size > 0 ? " · " + CloudSync.megabytes(entry.size) : "");
            backups.addView(v.row(name, detail, v.smallPill("Restore", true, true, x -> versions(login, entry, name))),
                    v.full(-1, 8));
        }
    }

    private void versions(String login, CloudBackup.Entry entry, String name) {
        if (busy) return;
        setBusy(true, "Reading the backups of " + name + "…");
        work.execute(() -> {
            try {
                CloudBackup cloud = new CloudBackup(token(), login);
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
                downloaded(progress, target, libraries, true, null);
            } catch (Exception e) {
                failed(progress, e);
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

    /**
     * v2.0.2 review: a download's end always says something: on the sheet, or as a toast after "Hide" or when the
     * screen went away meanwhile (the file stays in the backup folder for Restore > ".swb file").
     */
    private void downloaded(GitHubProgress progress, File target, boolean libraries, boolean own, String from) {
        android.content.Context app = activity.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                android.widget.Toast.makeText(app, "Downloaded " + target.getName() + " to " + target.getParent()
                        + ": restore it with Restore > .swb file.", android.widget.Toast.LENGTH_LONG).show();
            } else {
                finish(progress, target, libraries, own, from);
            }
        });
    }

    private void failed(GitHubProgress progress, Exception e) {
        android.content.Context app = activity.getApplicationContext();
        String message = text(e);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (progress.hidden()) {
                android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_LONG).show();
            } else {
                progress.failed(message);
            }
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
                            String file = SwbParts.displayName(path);
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
        GitHubProgress progress = startImport(SwbParts.displayName(path));
        background(() -> {
            try {
                File target = GitHubImport.download(token, source, path, new File(BackupFactory.getBackupDir(), "github"),
                        downloadSteps(progress, -1));
                boolean libraries = BackupFactory.zipContainsFile(target.getAbsolutePath(), "local_libs");
                String from = source.owner + "/" + source.repo;
                downloaded(progress, target, libraries, false, from);
            } catch (Exception e) {
                failed(progress, e);
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
                android.widget.Toast.makeText(activity.getApplicationContext(), message,
                        android.widget.Toast.LENGTH_LONG).show();
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
        TokenStore store = TokenStore.get(activity);
        String token = store.token();
        if (token == null) {
            String problem = store.problem();
            throw new GitHubException("keystore", problem == null
                    ? "The sign-in couldn't be read from the Android Keystore just now. Try again."
                    : "This phone can't read the saved sign-in (" + problem + "): tap Sign in again on the account card.");
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

    /** A pill's own width under a line. */
    private LinearLayout.LayoutParams pillAt(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = v.dp(topDp);
        return params;
    }

    /** v2.0.3: what went wrong, in words, with the network's own reason (no more bare "No connection"). */
    static String text(Exception e) {
        if (e instanceof GitHubException) return e.getMessage();
        if (e instanceof java.net.UnknownHostException) {
            return "No connection to GitHub: github.com couldn't be found (no internet, or the network's DNS failed).";
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return "No connection to GitHub: it didn't answer in time (slow or busy network). Try again.";
        }
        if (e instanceof java.net.ConnectException) {
            return "No connection to GitHub: the connection was refused (no internet, or a firewall).";
        }
        if (e instanceof javax.net.ssl.SSLException) {
            return "The secure connection to GitHub failed (" + e.getMessage() + "): check the phone's date and time.";
        }
        if (e instanceof java.io.IOException) {
            return "Network error talking to GitHub (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ").";
        }
        return "Something went wrong: " + e;
    }
}
