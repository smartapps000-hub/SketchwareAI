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
 */
public final class GitHubImportDialog {

    /** The app's restore of a downloaded file (BackupRestoreManager.doRestore with the projects list's callback). */
    public interface Restorer {
        void restore(File swb, boolean withLocalLibraries);
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

        content.addView(v.header(null, "Import from GitHub", "Your backups or a GitHub link"));

        link = new EditText(activity, null, 0, R.style.Ob_Text);
        link.setHint("github.com/owner/repo or a .swb link");
        link.setHintTextColor(activity.getResources().getColor(R.color.ob_text_2, activity.getTheme()));
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        link.setSingleLine(true);
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        link.setBackgroundResource(R.drawable.ob_field);
        link.setPadding(v.dp(14), 0, v.dp(14), 0);
        content.addView(link, v.full(48, 14));
        importButton = v.pill("Import from link", false, true, x -> importLink());
        content.addView(importButton, v.full(46, 10));

        status = v.line("", false);
        status.setVisibility(View.GONE);
        content.addView(status, v.full(-1, 10));

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
        work.execute(() -> {
            try {
                String token = token();
                CloudBackup cloud = new CloudBackup(token, store.login());
                cloud.ensureRepository();
                List<CloudBackup.Entry> entries = cloud.list();
                ui(() -> showOwn(entries, cloud));
            } catch (Exception e) {
                ui(() -> {
                    backups.removeAllViews();
                    backups.addView(v.line(text(e), true), v.full(-1, 8));
                });
            }
        });
    }

    private void showOwn(List<CloudBackup.Entry> entries, CloudBackup cloud) {
        backups.removeAllViews();
        if (entries.isEmpty()) {
            backups.addView(v.line("No backups yet. Use Back up > To GitHub on a project.", false), v.full(-1, 8));
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
                List<CloudBackup.Version> list = cloud.history(entry.path);
                ui(() -> {
                    setBusy(false, null);
                    if (list.isEmpty()) {
                        setBusy(false, "No backups of " + name + " were found.");
                        return;
                    }
                    String[] labels = new String[list.size()];
                    for (int i = 0; i < labels.length; i++) {
                        labels[i] = list.get(i).date.replace('T', ' ').replace("Z", " UTC") + (i == 0 ? "  (newest)" : "");
                    }
                    new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                            .setTitle("Restore " + name + " as a new project")
                            .setItems(labels, (d, which) -> downloadOwn(cloud, entry, list.get(which), name))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                ui(() -> setBusy(false, text(e)));
            }
        });
    }

    private void downloadOwn(CloudBackup cloud, CloudBackup.Entry entry, CloudBackup.Version version, String name) {
        setBusy(true, "Downloading " + name + "…");
        work.execute(() -> {
            try {
                File folder = new File(BackupFactory.getBackupDir(), "cloud");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Can't create " + folder);
                String base = new File(entry.path).getName().replace(".swb", "");
                File target = new File(folder, base + " " + version.sha.substring(0, 7) + ".swb");
                cloud.download(entry.path, version.sha, target);
                ui(() -> finish(target));
            } catch (Exception e) {
                ui(() -> setBusy(false, text(e)));
            }
        });
    }

    // ------------------------------------------------------------------ a link

    private void importLink() {
        if (busy) return;
        GitHubImport.Source source = GitHubImport.parse(link.getText().toString());
        if (source == null) {
            setBusy(false, "That isn't a GitHub repository, folder or .swb link.");
            status.setTextColor(activity.getResources().getColor(R.color.ob_error, activity.getTheme()));
            return;
        }
        setBusy(true, "Looking at " + source.owner + "/" + source.repo + "…");
        work.execute(() -> {
            try {
                String token = TokenStore.get(activity).login() == null ? null : token();
                List<String> found = GitHubImport.listBackups(token, source);
                ui(() -> {
                    if (found.isEmpty()) {
                        setBusy(false, "No .swb backups in " + source.owner + "/" + source.repo + ".");
                    } else if (found.size() == 1) {
                        downloadLink(token, source, found.get(0));
                    } else {
                        setBusy(false, null);
                        new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                                .setTitle("Pick a backup")
                                .setItems(found.toArray(new String[0]), (d, which) -> downloadLink(token, source, found.get(which)))
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                });
            } catch (Exception e) {
                ui(() -> setBusy(false, text(e)));
            }
        });
    }

    private void downloadLink(String token, GitHubImport.Source source, String path) {
        setBusy(true, "Downloading " + new File(path).getName() + "…");
        work.execute(() -> {
            try {
                File target = GitHubImport.download(token, source, path, new File(BackupFactory.getBackupDir(), "github"));
                ui(() -> finish(target));
            } catch (Exception e) {
                ui(() -> setBusy(false, text(e)));
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /** Hands the file to the app's restore (a new project) and closes the sheet. */
    private void finish(File swb) {
        setBusy(false, null);
        dialog.dismiss();
        restorer.restore(swb, BackupFactory.zipContainsFile(swb.getAbsolutePath(), "local_libs"));
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
        status.setTextColor(activity.getResources().getColor(R.color.ob_text_2, activity.getTheme()));
        status.setText(text == null ? "" : text);
        status.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void ui(Runnable r) {
        activity.runOnUiThread(() -> {
            if (dialog != null && dialog.isShowing() && !activity.isFinishing()) r.run();
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
