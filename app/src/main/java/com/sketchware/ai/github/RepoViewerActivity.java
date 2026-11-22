package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.remod.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v2.0.5 (owner's 16 Nov directive "in-app repository viewer on Open on GitHub"): a project repository inside the app,
 * full screen in the app's glass: its description, Public/Private, topics, the versions (★ Pinned or New first) with
 * Restore and, when a version has one, the APK (downloaded with progress, then Android's installer); README.md and the
 * files at the top of the repository; Open in browser and Share. "Open on GitHub" in the progress sheet and the
 * notification, and shared repository links, open it. Reads only; a private repository needs its owner's sign-in.
 */
public class RepoViewerActivity extends BaseAppCompatActivity {

    static final String EXTRA_OWNER = "owner";
    static final String EXTRA_REPO = "repo";

    private GitHubViews v;
    private LinearLayout list;
    private String owner;
    private String repo;
    private final ExecutorService work = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "RepoViewer");
        t.setDaemon(true);
        return t;
    });

    /** Opens the repository a github.com link names (a file or folder link opens its repository). */
    public static void open(Activity activity, String link) {
        GitHubImport.Source source = link == null ? null : GitHubImport.parse(link);
        if (source == null) {
            try {
                if (link != null) activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            } catch (RuntimeException ignored) {
                // no browser
            }
            return;
        }
        activity.startActivity(intent(activity, source.owner, source.repo));
    }

    static Intent intent(android.content.Context context, String owner, String repo) {
        return new Intent(context, RepoViewerActivity.class).putExtra(EXTRA_OWNER, owner).putExtra(EXTRA_REPO, repo);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github_screen);
        ObsidianWindow.apply(this);
        v = new GitHubViews(this);
        list = findViewById(R.id.gs_list);
        findViewById(R.id.gs_back).setOnClickListener(x -> finish());
        owner = getIntent().getStringExtra(EXTRA_OWNER);
        repo = getIntent().getStringExtra(EXTRA_REPO);
        if (owner == null || repo == null || !owner.matches("[A-Za-z0-9-]{1,39}") || !repo.matches("[A-Za-z0-9_.-]{1,100}")) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.gs_title)).setText(displayName(repo));
        ((TextView) findViewById(R.id.gs_subtitle)).setText("github.com/" + owner + "/" + repo);
        load();
    }

    @Override
    public void onDestroy() {
        work.shutdownNow();
        super.onDestroy();
    }

    static String displayName(String repo) {
        String n = repo.regionMatches(true, 0, ProjectRepo.PREFIX, 0, ProjectRepo.PREFIX.length())
                ? repo.substring(ProjectRepo.PREFIX.length()) : repo;
        if (n.regionMatches(true, 0, "Public-", 0, 7)) n = n.substring(7);
        return n.replace('_', ' ');
    }

    private String link() {
        return ProjectRepo.link(owner, repo);
    }

    /** The signed-in token, unless GitHub refused it (a public repository reads without one). */
    private String token() {
        TokenStore store = TokenStore.get(this);
        if (store.login() == null || GitHubSession.isRefused(this)) return null;
        return store.token();
    }

    private boolean own() {
        String login = TokenStore.get(this).login();
        return login != null && login.equalsIgnoreCase(owner);
    }

    /**
     * v2.1.0 (owner's 16 Nov report: "Reading … on GitHub…" forever): every call to GitHub here waits 10 seconds at
     * most (connect and read, one try: {@link GitHubHttp#quick}); the page this phone saw last is shown at once while
     * GitHub is asked; a refusal or a timeout says so with Try again (and the saved page stays).
     */
    static final int TIMEOUT_MILLIS = 10_000;
    private LinearLayout statusRow;
    private boolean cachedShown;

    private void load() {
        list.removeAllViews();
        statusRow = null;
        cachedShown = false;
        JsonObject cached = readCache();
        JsonObject cachedInfo = cached == null || !cached.has("info") || !cached.get("info").isJsonObject() ? null
                : cached.getAsJsonObject("info");
        if (cachedInfo != null) {
            showInfo(cachedInfo);
            cachedShown = true;
            String readme = DeviceFlow.string(cached, "readme");
            if (readme != null) showReadme(readme);
            if (own()) {
                List<ProjectRepo.Version> saved = RepoCache.versions(this, TokenStore.get(this).login(), ProjectRepo.number(cachedInfo));
                if (saved != null && !saved.isEmpty()) {
                    showVersions(saved, ProjectRepo.number(cachedInfo),
                            !cachedInfo.has("private") || cachedInfo.get("private").getAsBoolean());
                }
            }
            setStatus("Saved on this phone · checking GitHub…", false, false);
        } else {
            list.addView(v.line("Reading " + owner + "/" + repo + " on GitHub…", false), v.full(-1, 8));
        }
        work.execute(() -> {
            String token = token();
            try {
                GitHubHttp.Response r = GitHubHttp.quick(TIMEOUT_MILLIS, () -> GitHubHttp.api("GET", "/repos/"
                        + CloudBackup.enc(owner) + "/" + CloudBackup.enc(repo), token, null));
                if (r.code == 404) {
                    ui(() -> notFound(token == null));
                    return;
                }
                if (!r.ok()) throw CloudBackup.http("Opening " + owner + "/" + repo, r);
                JsonObject info = DeviceFlow.parse(r.body);
                writeCache(info, null);
                ui(() -> showInfo(info));
                loadVersions(token, info);
                loadReadme(token, DeviceFlow.string(info, "default_branch"));
                loadFiles(token, DeviceFlow.string(info, "default_branch"));
            } catch (Exception e) {
                String why = whyFailed(e);
                ui(() -> {
                    if (cachedShown) {
                        setStatus("Saved on this phone. " + why, true, true);
                        return;
                    }
                    list.removeAllViews();
                    list.addView(v.line(why, true), v.full(-1, 8));
                    list.addView(v.smallPill("Try again", true, true, x -> load()), pillAt(6));
                    browserRow();
                });
            }
        });
    }

    /** "GitHub didn't answer within 10 seconds" for a timeout, else the error's own words. */
    static String whyFailed(Exception e) {
        Throwable t = e;
        for (int i = 0; t != null && i < 5; i++, t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException) {
                return "GitHub didn't answer within " + (TIMEOUT_MILLIS / 1000) + " seconds (a slow network?).";
            }
        }
        return GitHubImportDialog.text(e);
    }

    /** The line at the top of the page: where the page comes from, or why GitHub couldn't be read (with Try again). */
    private void setStatus(String text, boolean error, boolean retry) {
        if (list == null) return;
        if (statusRow != null && statusRow.getParent() != null) ((android.view.ViewGroup) statusRow.getParent()).removeView(statusRow);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView line = v.line(text, error);
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(line, new LinearLayout.LayoutParams(0, -2, 1f));
        if (retry) {
            TextView again = v.smallPill("Try again", true, true, x -> load());
            GitHubViews.oneLine(again);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, v.dp(34));
            p.leftMargin = v.dp(8);
            row.addView(again, p);
        }
        statusRow = row;
        list.addView(row, 0, v.full(-1, 0));
    }

    private File cacheFile() {
        return new File(getFilesDir(), "repo_viewer/" + owner.toLowerCase(java.util.Locale.ROOT) + "__"
                + repo.toLowerCase(java.util.Locale.ROOT) + ".json");
    }

    private JsonObject readCache() {
        File f = cacheFile();
        if (!f.isFile() || f.length() > 2L * 1024 * 1024) return null;
        try {
            return DeviceFlow.parse(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** Keeps the repository's details (and its README when given) for the next visit; atomic. */
    private void writeCache(JsonObject info, String readme) {
        try {
            JsonObject o = readCache();
            if (o == null) o = new JsonObject();
            if (info != null) o.add("info", info);
            if (readme != null) o.addProperty("readme", readme.length() > 200_000 ? readme.substring(0, 200_000) : readme);
            File f = cacheFile();
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(f.getPath() + ".tmp");
            java.nio.file.Files.write(tmp.toPath(), o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Exception ignored) {
            // a cache only
        }
    }

    private void notFound(boolean signedOut) {
        list.removeAllViews();
        list.addView(v.line(signedOut
                ? "This repository is private, or the link is wrong: sign in with the owner's GitHub account to open a "
                + "private project."
                : "This repository isn't there, or it is private and belongs to another account.", true), v.full(-1, 8));
        if (signedOut) {
            list.addView(v.smallPill("Sign in", true, true, x -> GitHubInAppAuthDialog.show(this, l -> {
                // review (LOW): a screen destroyed meanwhile (its executor shut down) loads nothing
                if (!isFinishing() && !isDestroyed()) load();
            })), pillAt(6));
        }
        browserRow();
    }

    // ------------------------------------------------------------------ the parts of the page

    private LinearLayout infoBox;
    private LinearLayout versionsBox;
    private LinearLayout readmeBox;
    private LinearLayout filesBox;

    private void showInfo(JsonObject info) {
        list.removeAllViews();
        statusRow = null;
        boolean isPrivate = !info.has("private") || info.get("private").getAsBoolean();
        infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        infoBox.setBackgroundResource(R.drawable.ob_pill16);
        infoBox.setPadding(v.dp(14), v.dp(12), v.dp(14), v.dp(12));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this, null, 0, R.style.Ob_Text_Label);
        name.setText(displayName(repo));
        name.setTypeface(v.bold());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        GitHubViews.oneLine(name);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView badge = badge(isPrivate ? "Private" : "Public", !isPrivate);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, v.dp(28));
        bp.leftMargin = v.dp(6);
        top.addView(badge, bp);
        infoBox.addView(top);
        String description = DeviceFlow.string(info, "description");
        if (description != null && !description.trim().isEmpty()) {
            TextView d = v.line(description.trim(), false);
            d.setTextColor(v.color(R.color.ob_text));
            infoBox.addView(d, v.full(-1, 8));
        }
        long updated = ProjectRepo.isoTime(DeviceFlow.string(info, "pushed_at"), DeviceFlow.string(info, "updated_at"));
        long stars = info.has("stargazers_count") && info.get("stargazers_count").isJsonPrimitive()
                ? info.get("stargazers_count").getAsLong() : 0;
        TextView meta = v.line((updated > 0 ? "Updated " + CloudBackup.formatTime(updated) + " · " : "") + "★ " + stars
                + " · " + owner, false);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        infoBox.addView(meta, v.full(-1, 6));
        JsonElement topics = info.get("topics");
        if (topics != null && topics.isJsonArray() && topics.getAsJsonArray().size() > 0) {
            PublishActivity.FlowLayout chips = new PublishActivity.FlowLayout(this, v.dp(6));
            for (JsonElement t : topics.getAsJsonArray()) {
                if (!t.isJsonPrimitive()) continue;
                TextView chip = badge("#" + t.getAsString(), false);
                chips.addView(chip, new android.view.ViewGroup.LayoutParams(-2, v.dp(28)));
            }
            infoBox.addView(chips, v.full(-1, 10));
        }
        list.addView(infoBox, v.full(-1, 4));
        browserRow();

        list.addView(title("Versions"), v.full(-1, 22));
        versionsBox = new LinearLayout(this);
        versionsBox.setOrientation(LinearLayout.VERTICAL);
        versionsBox.addView(v.line("Reading the versions…", false));
        list.addView(versionsBox, v.full(-1, 6));

        list.addView(title("README.md"), v.full(-1, 22));
        readmeBox = new LinearLayout(this);
        readmeBox.setOrientation(LinearLayout.VERTICAL);
        readmeBox.addView(v.line("Reading…", false));
        list.addView(readmeBox, v.full(-1, 6));

        list.addView(title("Files"), v.full(-1, 22));
        filesBox = new LinearLayout(this);
        filesBox.setOrientation(LinearLayout.VERTICAL);
        filesBox.addView(v.line("Reading…", false));
        list.addView(filesBox, v.full(-1, 6));
    }

    private void browserRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView browser = v.smallPill("Open in browser", false, true, x -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link())));
            } catch (RuntimeException ignored) {
                Toast.makeText(this, "No browser on this phone", Toast.LENGTH_SHORT).show();
            }
        });
        GitHubViews.oneLine(browser);
        row.addView(browser, new LinearLayout.LayoutParams(0, v.dp(40), 1f));
        TextView share = v.smallPill("Share", false, true, x -> ProjectLinks.share(this, displayName(repo), link()));
        GitHubViews.oneLine(share);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, v.dp(40), 1f);
        sp.leftMargin = v.dp(8);
        row.addView(share, sp);
        list.addView(row, v.full(-1, 12));
    }

    private void loadVersions(String token, JsonObject info) {
        try {
            List<ProjectRepo.Version> versions = GitHubHttp.quick(TIMEOUT_MILLIS,
                    () -> ProjectRepo.versions(token, owner, repo, false));
            long number = ProjectRepo.number(info);
            boolean isPrivate = !info.has("private") || info.get("private").getAsBoolean();
            ui(() -> showVersions(versions, number, isPrivate));
        } catch (Exception e) {
            String why = whyFailed(e);
            ui(() -> {
                if (versionsBox == null) return;
                versionsBox.removeAllViews();
                versionsBox.addView(v.line(why, true));
            });
        }
    }

    private void showVersions(List<ProjectRepo.Version> versions, long number, boolean isPrivate) {
        if (versionsBox == null) return;
        versionsBox.removeAllViews();
        List<ProjectRepo.Version> shown = GitHubBackupsActivity.ordered(versions);
        long newest = GitHubBackupsActivity.newestTime(versions);
        if (shown.isEmpty()) {
            versionsBox.addView(v.line("No versions made by Sketchware AI in this repository.", false));
            return;
        }
        for (int i = 0; i < shown.size(); i++) {
            ProjectRepo.Version ver = shown.get(i);
            String mark = GitHubBackupsActivity.badgeOf(ver, ver.time == newest);
            String detail = (mark.isEmpty() ? "" : mark + " · ") + (ver.time > 0 ? CloudBackup.formatTime(ver.time) : ver.tag)
                    + (ver.size > 0 ? " · " + CloudSync.megabytes(ver.size) : "");
            List<View> actions = new ArrayList<>();
            TextView restore = v.smallPill("Restore", true, true, x -> restore(this, owner, repo, ver, number, isPrivate));
            GitHubViews.oneLine(restore);
            actions.add(restore);
            ProjectRepo.Asset apk = ver.apk == null ? null : apkAsset(ver);
            if (apk != null) {
                TextView get = v.smallPill("APK", false, true, x -> downloadApk(this, owner, repo, ver, apk));
                GitHubViews.oneLine(get);
                actions.add(get);
            }
            versionsBox.addView(v.row(ver.label(), detail, actions.toArray(new View[0])), v.full(-1, i == 0 ? 0 : 8));
        }
    }

    private static ProjectRepo.Asset apkAsset(ProjectRepo.Version ver) {
        for (ProjectRepo.Asset a : ver.assets) if (a.name.equals(ver.apk)) return a;
        return null;
    }

    /** v2.1.0: also the Play Store's "Import .SWB" (an app of another account: the libraries question first). */
    static void restore(Activity activity, String owner, String repo, ProjectRepo.Version ver, long number,
                        boolean isPrivate) {
        if (!AuthGuard.check(activity)) return;
        String login = TokenStore.get(activity).login();
        boolean own = login != null && login.equalsIgnoreCase(owner);
        CloudSync.Import src = new CloudSync.Import();
        src.owner = owner;
        src.repo = repo;
        src.number = own ? number : 0;
        src.repoPrivate = isPrivate;
        src.version = ver;
        src.size = ver.size;
        src.title = displayName(repo) + " " + ver.label();
        if (own) {
            src.withLibraries = true;
            CloudSync.importProject(activity, src);
            return;
        }
        String from = owner + "/" + repo;
        new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Local libraries from " + from)
                .setMessage("If this backup brings local libraries, their code goes into the apps you build with them: "
                        + "copy them only if you trust " + from + ". Libraries already on this phone are never replaced.")
                .setPositiveButton("Without libraries", (d, w) -> {
                    src.withLibraries = false;
                    CloudSync.importProject(activity, src);
                })
                .setNegativeButton("Copy libraries", (d, w) -> {
                    src.withLibraries = true;
                    src.librariesFrom = from;
                    CloudSync.importProject(activity, src);
                })
                .show();
    }

    /**
     * Review (LOW): APK downloads go one after the other on a queue of their own that outlives this screen (leaving it
     * doesn't stop a download), and the same file is never written twice at once.
     */
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RepoViewerDownload");
        t.setDaemon(true);
        return t;
    });
    private static final java.util.Set<String> DOWNLOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The version's APK into .sketchware/downloads, then Android's installer (it asks before installing). */
    /** v2.1.0: also the Play Store's "Download & Install APK". */
    static void downloadApk(Activity activity, String owner, String repo, ProjectRepo.Version ver, ProjectRepo.Asset apk) {
        File dir = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/downloads");
        File target = new File(dir, ProjectRepo.safeFileName(apk.name));
        if (!DOWNLOADING.add(target.getAbsolutePath())) {
            Toast.makeText(activity, "That APK is already downloading.", Toast.LENGTH_SHORT).show();
            return;
        }
        GitHubProgress progress = GitHubProgress.show(activity, R.drawable.ob_ic_github, "APK of " + displayName(repo) + " "
                + ver.label());
        progress.update(2, "Connecting to GitHub…");
        android.content.Context app = activity.getApplicationContext();
        DOWNLOADS.execute(() -> {
            String message;
            boolean ok = false;
            // review (LOW): the token is read here, not on the main thread (Keystore)
            String token = TokenStore.get(app).login() == null || GitHubSession.isRefused(app) ? null
                    : TokenStore.get(app).token();
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Can't create " + dir);
                File part = new File(target.getPath() + ".part");
                GitHubHttp.Response r = GitHubHttp.downloadAsset("/repos/" + CloudBackup.enc(owner) + "/"
                        + CloudBackup.enc(repo) + "/releases/assets/" + apk.id, token, part, 512L * 1024 * 1024,
                        (done, total) -> {
                            long all = total > 0 ? total : apk.size;
                            int percent = all > 0 ? 5 + (int) (90 * Math.min(1.0, done / (double) all)) : 50;
                            activity.runOnUiThread(() -> {
                                if (activity.isFinishing() || activity.isDestroyed()) return;
                                progress.update(percent, "Downloading… " + CloudSync.megabytes(done)
                                        + (all > 0 ? " of " + CloudSync.megabytes(all) : ""));
                            });
                        });
                if (!r.ok()) throw CloudBackup.http("Downloading the APK", r);
                if (target.exists() && !target.delete()) throw new java.io.IOException("Can't replace " + target);
                if (!part.renameTo(target)) throw new java.io.IOException("Couldn't save " + target);
                ok = true;
                message = "Saved to .sketchware/downloads/" + target.getName() + ": Android's installer opens.";
            } catch (Exception e) {
                message = GitHubImportDialog.text(e);
            } finally {
                DOWNLOADING.remove(target.getAbsolutePath());
            }
            boolean success = ok;
            String text = message;
            // second review: decided on the main thread, where the screen can't go away in between
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    // the screen is gone: the end as a toast (no installer started from the background)
                    Toast.makeText(app, success ? "Saved to .sketchware/downloads/" + target.getName() : text,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (!success) {
                    progress.failed(text, false);
                    return;
                }
                progress.done(text);
                install(activity, target);
            });
        });
    }

    static void install(Activity activity, File apk) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = androidx.core.content.FileProvider.a(activity.getApplicationContext(), activity.getPackageName() + ".provider", apk);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(activity, "The APK is in .sketchware/downloads (no installer could open it)", Toast.LENGTH_LONG).show();
        }
    }

    private void loadReadme(String token, String branch) {
        String text;
        try {
            GitHubHttp.Response r = GitHubHttp.quick(TIMEOUT_MILLIS, () -> GitHubHttp.api("GET", "/repos/"
                    + CloudBackup.enc(owner) + "/" + CloudBackup.enc(repo) + "/contents/README.md"
                    + (branch == null ? "" : "?ref=" + CloudBackup.enc(branch)), token, null));
            if (r.code == 404) {
                text = null;
            } else {
                if (!r.ok()) throw CloudBackup.http("Reading README.md", r);
                JsonObject o = DeviceFlow.parse(r.body);
                String b64 = DeviceFlow.string(o, "content");
                text = b64 == null ? "" : new String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            String why = whyFailed(e);
            ui(() -> {
                if (readmeBox == null) return;
                readmeBox.removeAllViews();
                readmeBox.addView(v.line(why, true));
            });
            return;
        }
        String shown = text;
        writeCache(null, shown == null ? "" : shown);
        ui(() -> showReadme(shown == null ? "" : shown));
    }

    /** The README's first 400 lines (empty: "No README.md in this repository."). */
    private void showReadme(String text) {
        if (readmeBox == null) return;
        readmeBox.removeAllViews();
        if (text == null || text.isEmpty()) {
            readmeBox.addView(v.line("No README.md in this repository.", false));
            return;
        }
        TextView t = new TextView(this, null, 0, R.style.Ob_Text);
        t.setText(shorten(text, 400));
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextIsSelectable(true);
        t.setBackgroundResource(R.drawable.ob_field);
        t.setPadding(v.dp(14), v.dp(12), v.dp(14), v.dp(12));
        readmeBox.addView(t);
    }

    /** v2.0.5 render: "66 B", "1.8 KB", "2.3 MB" (a small file never shows as "0.0 MB"). */
    static String fileSize(long bytes) {
        if (bytes < 1000) return bytes + " B";
        // v2.1.3: decimal units, as Android's own screens count them (CloudSync.megabytes)
        if (bytes < 1_000_000) return String.format(java.util.Locale.ENGLISH, "%.1f KB", bytes / 1000.0);
        return CloudSync.megabytes(bytes);
    }

    static String shorten(String text, int maxLines) {
        String[] lines = text.split("\n", -1);
        if (lines.length <= maxLines) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) sb.append(lines[i]).append('\n');
        return sb.append("… (").append(lines.length - maxLines).append(" more lines: Open in browser)").toString();
    }

    private void loadFiles(String token, String branch) {
        List<String[]> rows = new ArrayList<>();
        String failed = null;
        try {
            GitHubHttp.Response r = GitHubHttp.quick(TIMEOUT_MILLIS, () -> GitHubHttp.api("GET", "/repos/"
                    + CloudBackup.enc(owner) + "/" + CloudBackup.enc(repo) + "/contents"
                    + (branch == null ? "" : "?ref=" + CloudBackup.enc(branch)), token, null));
            if (r.code == 404) {
                failed = "The repository is empty.";
            } else {
                if (!r.ok()) throw CloudBackup.http("Listing the files", r);
                JsonElement parsed = JsonParser.parseString(r.body);
                if (parsed.isJsonArray()) {
                    JsonArray items = parsed.getAsJsonArray();
                    for (JsonElement e : items) {
                        if (!e.isJsonObject()) continue;
                        JsonObject o = e.getAsJsonObject();
                        boolean dir = "dir".equals(DeviceFlow.string(o, "type"));
                        long size = o.has("size") && o.get("size").isJsonPrimitive() ? o.get("size").getAsLong() : 0;
                        rows.add(new String[]{(dir ? "📁 " : "📄 ") + DeviceFlow.string(o, "name"),
                                dir ? "folder" : fileSize(size)});
                    }
                }
            }
        } catch (Exception e) {
            failed = whyFailed(e);
        }
        String why = failed;
        ui(() -> {
            if (filesBox == null) return;
            filesBox.removeAllViews();
            if (why != null) {
                filesBox.addView(v.line(why, !why.startsWith("The repository is empty")));
                return;
            }
            rows.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
            for (String[] row : rows) {
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = new TextView(this, null, 0, R.style.Ob_Text);
                name.setText(row[0]);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                GitHubViews.oneLine(name);
                line.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView size = v.line(row[1], false);
                size.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                line.addView(size);
                filesBox.addView(line, v.full(-1, 6));
            }
            if (rows.isEmpty()) filesBox.addView(v.line("No files at the top of the repository.", false));
        });
    }

    // ------------------------------------------------------------------ helpers

    private TextView title(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Title);
        t.setText(text);
        GitHubViews.oneLine(t);
        return t;
    }

    private TextView badge(String text, boolean accent) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTypeface(v.bold());
        t.setGravity(Gravity.CENTER);
        GitHubViews.oneLine(t);
        t.setTextColor(accent ? v.color(R.color.ob_accent) : v.color(R.color.ob_text_2));
        t.setBackgroundResource(R.drawable.ob_pill16_state);
        t.setPadding(v.dp(10), 0, v.dp(10), 0);
        return t;
    }

    private LinearLayout.LayoutParams pillAt(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, v.dp(36));
        params.topMargin = v.dp(topDp);
        return params;
    }

    private void ui(Runnable r) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) r.run();
        });
    }
}
