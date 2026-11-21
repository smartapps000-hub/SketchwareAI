package com.sketchware.ai.github;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.widget.Toast;

import com.google.gson.JsonObject;

import java.io.File;

import mod.agus.jcoderz.lib.FileUtil;

/**
 * v2.0.2: the project's GitHub record ({@code data/<sc_id>/github_backup.json}), kept with the project and read offline:
 * the share pill's link and the account that made it. It counts only for the project id it was written for and only
 * while that account is signed in; a restored or imported .swb drops it (v2.0.3 review).
 * <p>
 * v2.0.4 (owner's 16 Nov directive "one dedicated repository per project"): the record names the project's own
 * repository ({@code repo}, and GitHub's number for it, {@code repo_number}: renames are followed by number), its
 * visibility (the Public / Private pill), the last version's tag, the Standard version's tag, the backup's boxes and the
 * fingerprint of the saved project at that backup. A v2.0.3 public copy ({@code public_repo} / {@code public_number}) is
 * taken as the project's repository. A record of v2.0.2 / v2.0.3 that only links a file in {@link CloudBackup#REPO}
 * still shows its pills; the next backup moves the project into its own repository. Restoring a version of the
 * project's repository on another phone links the restored project to it ({@link #linkRestored}), so its next backups
 * go to the same repository instead of a new one.
 */
public final class ProjectLinks {

    static final String FILE = "github_backup.json";

    private ProjectLinks() {
    }

    static File file(String scId) {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId + "/" + FILE);
    }

    /** The stored record of a numeric project id written for that id, or null. */
    private static JsonObject read(String scId) {
        if (scId == null || !scId.matches("\\d+")) return null;
        File f = file(scId);
        // v2.1.2 (zero-lag): Home's cards ask while they are drawn; a record is read once per version of its file
        long stamp = f.lastModified();
        long size = f.length();
        Object[] memo = READ_MEMO.get(scId);
        if (memo != null && (Long) memo[0] == stamp && (Long) memo[1] == size) {
            return memo[2] == null ? null : ((JsonObject) memo[2]).deepCopy();
        }
        JsonObject json = null;
        if (f.isFile() && size <= 16384) {
            try {
                JsonObject parsed = DeviceFlow.parse(FileUtil.readFile(f.getAbsolutePath()));
                if (scId.equals(DeviceFlow.string(parsed, "sc_id"))) json = parsed;
            } catch (RuntimeException ignored) {
                // not a record
            }
        }
        READ_MEMO.put(scId, new Object[]{stamp, size, json});
        return json == null ? null : json.deepCopy();
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object[]> READ_MEMO =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean ownedBy(JsonObject json, String signedInLogin) {
        String owner = DeviceFlow.string(json, "login");
        return signedInLogin == null || owner == null || owner.equalsIgnoreCase(signedInLogin);
    }

    /**
     * v2.0.3 review: the repository fields count only for the account signed in now that made them (a record carried
     * in some other way, or one without an account, never steers a visibility change).
     */
    private static boolean mine(JsonObject json, String signedInLogin) {
        String owner = DeviceFlow.string(json, "login");
        return signedInLogin != null && owner != null && owner.equalsIgnoreCase(signedInLogin);
    }

    private static String github(String url) {
        return url != null && url.startsWith("https://github.com/") ? url : null;
    }

    private static long longOf(JsonObject json, String key) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsLong() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ the project's repository

    /** v2.0.4: the project's repository number (or the v2.0.3 public copy's), for this account; 0 when none. */
    static long repoNumber(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        if (json == null || !mine(json, signedInLogin)) return 0;
        long n = longOf(json, "repo_number");
        return n > 0 ? n : longOf(json, "public_number");
    }

    /** v2.0.4: the project's repository name as recorded, or null. */
    static String repoName(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        if (json == null || !mine(json, signedInLogin)) return null;
        String repo = DeviceFlow.string(json, "repo");
        if (repo == null) repo = DeviceFlow.string(json, "public_repo");
        return repo != null && repo.matches("[A-Za-z0-9_.-]{1,100}") && !repo.equalsIgnoreCase(CloudBackup.REPO) ? repo : null;
    }

    /** v2.0.4: the repository was found or made: its name, number and visibility (a new record when there is none). */
    static synchronized void repoFound(String scId, String login, String repo, long number, boolean isPrivate) {
        if (scId == null || !scId.matches("\\d+") || login == null) return;
        File dir = file(scId).getParentFile();
        if (dir == null || !dir.isDirectory()) return;
        JsonObject json = read(scId);
        if (json == null || !mine(json, login)) {
            json = new JsonObject();
            json.addProperty("sc_id", scId);
            json.addProperty("login", login);
        }
        json.addProperty("repo", repo);
        json.addProperty("repo_number", number);
        json.addProperty("visibility", isPrivate ? "private" : "public");
        json.addProperty("url", ProjectRepo.link(login, repo));
        json.addProperty("path", "");
        write(scId, json);
    }

    /**
     * The link the share pill shares: the project's repository page; for a record of v2.0.2 / v2.0.3 without one, the
     * backup file's link. Null when another account made it.
     */
    public static String get(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        if (json == null || !ownedBy(json, signedInLogin)) return null;
        if (mine(json, signedInLogin)) {
            String repo = DeviceFlow.string(json, "repo");
            if (repo != null) return ProjectRepo.link(signedInLogin, repo);
            if ("public".equals(DeviceFlow.string(json, "visibility"))) {
                String shared = github(DeviceFlow.string(json, "public_url"));
                if (shared != null) return shared;
            }
        }
        return github(DeviceFlow.string(json, "url"));
    }

    public static String get(String scId) {
        return get(scId, null);
    }

    /** "public", "private", or null when the project has no GitHub record (the card then shows no pills). */
    public static String visibility(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        if (json == null || !ownedBy(json, signedInLogin) || github(DeviceFlow.string(json, "url")) == null) return null;
        return mine(json, signedInLogin) && "public".equals(DeviceFlow.string(json, "visibility")) ? "public" : "private";
    }

    static boolean isPublic(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        return json != null && mine(json, signedInLogin) && "public".equals(DeviceFlow.string(json, "visibility"));
    }

    /** v2.0.4: the Standard version's tag as last seen, or null. */
    static String standardTag(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        return json == null || !mine(json, signedInLogin) ? null : DeviceFlow.string(json, "standard_tag");
    }

    /** v2.0.3: the fingerprint of the saved project at its last backup by this account, or null. */
    static String lastFingerprint(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        return json == null || !mine(json, signedInLogin) ? null : DeviceFlow.string(json, "fingerprint");
    }

    /** v2.0.4: when the APK that went up with a backup was built (its file time), or 0. */
    static long lastApkTime(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        return json == null || !mine(json, signedInLogin) ? 0 : longOf(json, "apk_time");
    }

    /** v2.0.3: the boxes of the project's last backup, or null. */
    static CloudSync.Options lastOptions(String scId) {
        JsonObject json = read(scId);
        String flags = json == null ? null : DeviceFlow.string(json, "options");
        return flags == null ? null : new CloudSync.Options(flags.contains("L"), flags.contains("C"));
    }

    /** v2.0.3: when the project was last backed up to GitHub (ms since 1970), or 0. */
    static long lastBackup(String scId) {
        JsonObject json = read(scId);
        return json == null ? 0 : longOf(json, "time");
    }

    /** v2.0.4: after a backup: the version's tag, the boxes, the fingerprint and the APK's time. */
    static synchronized void saved(String scId, String login, String tag, CloudSync.Options options, String fingerprint,
                                   long apkTime) {
        JsonObject json = read(scId);
        if (json == null || !mine(json, login)) return;
        json.addProperty("time", System.currentTimeMillis());
        if (tag != null) json.addProperty("tag", tag);
        if (options != null) {
            json.addProperty("options", (options.localLibraries ? "L" : "") + (options.customBlocks ? "C" : ""));
        }
        if (fingerprint != null) json.addProperty("fingerprint", fingerprint);
        if (apkTime > 0) json.addProperty("apk_time", apkTime);
        write(scId, json);
    }

    /** v2.0.4: the repository's visibility as GitHub has it now. */
    static synchronized void visibilityNow(String scId, String login, boolean isPrivate) {
        JsonObject json = read(scId);
        if (json == null || !mine(json, login)) return;
        json.addProperty("visibility", isPrivate ? "private" : "public");
        write(scId, json);
    }

    /** v2.0.4: the Standard version's tag (null: none pinned). */
    static synchronized void standardNow(String scId, String login, String tag) {
        JsonObject json = read(scId);
        if (json == null || !mine(json, login)) return;
        if (tag == null) json.remove("standard_tag");
        else json.addProperty("standard_tag", tag);
        write(scId, json);
    }

    /**
     * v2.0.4 (owner: "re-hydrate the project's metadata on restore so later backups update the exact same repository"):
     * a project restored from a version of the account's own project repository is linked to it, unless another
     * project on this phone already is (two projects never back up into one repository).
     */
    static synchronized boolean linkRestored(String scId, String login, String repo, long number, boolean isPrivate,
                                             String standardTag) {
        if (number <= 0 || login == null) return false;
        File data = new File(Environment.getExternalStorageDirectory(), ".sketchware/data");
        File[] projects = data.listFiles();
        if (projects != null) {
            for (File p : projects) {
                if (!p.getName().matches("\\d+") || p.getName().equals(scId)) continue;
                if (repoNumber(p.getName(), login) == number) return false;
            }
        }
        repoFound(scId, login, repo, number, isPrivate);
        if (standardTag != null) standardNow(scId, login, standardTag);
        return true;
    }

    // ------------------------------------------------------------------ the projects list

    /** The projects screen's card refresh (held weakly: a closed screen is never kept). */
    private static volatile java.lang.ref.WeakReference<Runnable> changed;

    public static void onChanged(Runnable refresh) {
        changed = refresh == null ? null : new java.lang.ref.WeakReference<>(refresh);
    }

    /** v2.0.3: the projects list should load again (a project was added, e.g. the GitHub sample). */
    public static void listChanged() {
        // v2.1.2: a restore or an import may have written records behind this class: read them again
        READ_MEMO.clear();
        notifyChanged();
    }

    private static void notifyChanged() {
        java.lang.ref.WeakReference<Runnable> ref = changed;
        Runnable refresh = ref == null ? null : ref.get();
        if (refresh != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(refresh);
    }

    private static void write(String scId, JsonObject json) {
        // v2.1.2: the copy in memory goes with every write (two writes in one second can have the same time and size)
        READ_MEMO.remove(scId);
        FileUtil.writeFile(file(scId).getAbsolutePath(), json.toString());
        READ_MEMO.remove(scId);
        notifyChanged();
    }

    /** Tests and older callers: a record naming a link only (v2.0.2's shape). */
    static void save(String scId, String url, String path, String login) {
        if (scId == null || !scId.matches("\\d+") || url == null) return;
        File dir = file(scId).getParentFile();
        if (dir == null || !dir.isDirectory()) return;
        JsonObject json = new JsonObject();
        json.addProperty("url", url);
        json.addProperty("path", path);
        json.addProperty("sc_id", scId);
        if (login != null) json.addProperty("login", login);
        json.addProperty("time", System.currentTimeMillis());
        write(scId, json);
    }

    /** The project card's share button: the link copied, then Android's share sheet. */
    public static void share(Activity activity, String projectName, String url) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GitHub link", url));
        Toast.makeText(activity, "Link copied", Toast.LENGTH_SHORT).show();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, projectName + " (Sketchware AI project)");
        send.putExtra(Intent.EXTRA_TEXT, url);
        try {
            activity.startActivity(Intent.createChooser(send, "Share the project's link"));
        } catch (RuntimeException ignored) {
            // no app to share with: the link is on the clipboard
        }
    }
}
