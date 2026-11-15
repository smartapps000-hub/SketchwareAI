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
 * v2.0.2 (owner's 14 Nov directive "permanent offline-stored share button on every project card"): the link of a
 * project's GitHub backup, kept with the project ({@code data/<sc_id>/github_backup.json}: url, path, time; written
 * atomically after every backup to GitHub, the same link each time since the backup path stays). The projects list
 * shows a share button next to the "#id" when a link is there; sharing works offline. The backup repository is private:
 * the link opens for the account that made it (and imports on another phone signed in to it).
 * <p>
 * v2.0.2 review: the file travels inside .swb backups (the whole data folder is packed), so a link counts only for the
 * project id it was written for (a restored or duplicated project gets a new id) and never while another GitHub account
 * is signed in; the projects list is told when a link is written so the button shows at once.
 * <p>
 * v2.0.3 (owner's 15 Nov directive "per-project Public / Private and Share pills next to #number"): the file also keeps
 * the project's visibility and, when it was made public, its public copy's repository and link ({@link PublicCopy});
 * the share pill then shares the public link. It also keeps the backup's boxes and a fingerprint of the saved project
 * at that backup ({@link CloudSync#fingerprint}): a save without changes makes no new automatic backup.
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
        if (!f.isFile() || f.length() > 8192) return null;
        try {
            JsonObject json = DeviceFlow.parse(FileUtil.readFile(f.getAbsolutePath()));
            if (!scId.equals(DeviceFlow.string(json, "sc_id"))) return null;
            return json;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean ownedBy(JsonObject json, String signedInLogin) {
        String owner = DeviceFlow.string(json, "login");
        return signedInLogin == null || owner == null || owner.equalsIgnoreCase(signedInLogin);
    }

    private static String github(String url) {
        return url != null && url.startsWith("https://github.com/") ? url : null;
    }

    /** The project's backup link, or null (none yet, not a github.com link, or written for another project). */
    public static String get(String scId) {
        return get(scId, null);
    }

    /**
     * The link the share pill shares: the public copy's while the project is public, else the private backup's; null
     * when {@code signedInLogin} (the account signed in now, or null) differs from the account that made the backup.
     */
    public static String get(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        if (json == null || !ownedBy(json, signedInLogin)) return null;
        if ("public".equals(DeviceFlow.string(json, "visibility"))) {
            String shared = github(DeviceFlow.string(json, "public_url"));
            if (shared != null) return shared;
        }
        return github(DeviceFlow.string(json, "url"));
    }

    /** v2.0.3: "public", "private", or null when there is no backup link (the card then shows no pills). */
    public static String visibility(String scId, String signedInLogin) {
        JsonObject json = read(scId);
        if (json == null || !ownedBy(json, signedInLogin) || github(DeviceFlow.string(json, "url")) == null) return null;
        return "public".equals(DeviceFlow.string(json, "visibility")) ? "public" : "private";
    }

    static boolean isPublic(String scId) {
        JsonObject json = read(scId);
        return json != null && "public".equals(DeviceFlow.string(json, "visibility"));
    }

    /** v2.0.3: the public copy's repository name kept for the project (also while it is private again), or null. */
    static String publicRepository(String scId) {
        JsonObject json = read(scId);
        String repo = json == null ? null : DeviceFlow.string(json, "public_repo");
        return repo != null && repo.matches("[A-Za-z0-9_.-]{1,100}") ? repo : null;
    }

    /** v2.0.3: the fingerprint of the saved project at its last backup, or null. */
    static String lastFingerprint(String scId) {
        JsonObject json = read(scId);
        return json == null ? null : DeviceFlow.string(json, "fingerprint");
    }

    /** v2.0.3: the boxes of the project's last backup (an automatic backup after a save uses them), or null. */
    static CloudSync.Options lastOptions(String scId) {
        JsonObject json = read(scId);
        String flags = json == null ? null : DeviceFlow.string(json, "options");
        return flags == null ? null : new CloudSync.Options(flags.contains("L"), flags.contains("C"));
    }

    /** v2.0.3: when the project was last backed up to GitHub (ms since 1970), or 0. */
    static long lastBackup(String scId) {
        JsonObject json = read(scId);
        try {
            return json != null && json.has("time") ? json.get("time").getAsLong() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The projects screen's card refresh (held weakly: a closed screen is never kept). */
    private static volatile java.lang.ref.WeakReference<Runnable> changed;

    public static void onChanged(Runnable refresh) {
        changed = refresh == null ? null : new java.lang.ref.WeakReference<>(refresh);
    }

    /** v2.0.3: the projects list should load again (a project was added, e.g. the GitHub sample). */
    public static void listChanged() {
        notifyChanged();
    }

    private static void notifyChanged() {
        java.lang.ref.WeakReference<Runnable> ref = changed;
        Runnable refresh = ref == null ? null : ref.get();
        if (refresh != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(refresh);
    }

    /** After a backup to GitHub: the link of the project's .swb in the repository, and the account that made it. */
    static void save(String scId, String url, String path, String login) {
        saved(scId, url, path, login, null, null);
    }

    /**
     * v2.0.3: after a backup to GitHub: its link and path, the account, the boxes and the project's fingerprint; the
     * visibility and the public copy's fields are kept.
     */
    static synchronized void saved(String scId, String url, String path, String login, CloudSync.Options options,
                                   String fingerprint) {
        if (scId == null || !scId.matches("\\d+") || url == null) return;
        File dir = file(scId).getParentFile();
        if (dir == null || !dir.isDirectory()) return; // the project is gone
        JsonObject old = read(scId);
        if (old != null && !ownedBy(old, login)) old = null; // another account's record: not carried over
        JsonObject json = new JsonObject();
        json.addProperty("url", url);
        json.addProperty("path", path);
        json.addProperty("sc_id", scId);
        if (login != null) json.addProperty("login", login);
        json.addProperty("time", System.currentTimeMillis());
        if (options != null) {
            json.addProperty("options", (options.localLibraries ? "L" : "") + (options.customBlocks ? "C" : ""));
        }
        if (fingerprint != null) json.addProperty("fingerprint", fingerprint);
        if (old != null) {
            for (String key : new String[]{"visibility", "public_repo", "public_url", "public_path"}) {
                String value = DeviceFlow.string(old, key);
                if (value != null) json.addProperty(key, value);
            }
        }
        write(scId, json);
    }

    /** v2.0.3: the project is public now: its public copy's repository and the link the share pill shares. */
    static synchronized void publicNow(String scId, String repo, String url, String path) {
        JsonObject json = read(scId);
        if (json == null) return;
        json.addProperty("visibility", "public");
        json.addProperty("public_repo", repo);
        json.addProperty("public_url", url);
        json.addProperty("public_path", path);
        write(scId, json);
    }

    /** v2.0.3: the project is private again (its public copy's repository is kept, private, for a later "Public"). */
    static synchronized void privateNow(String scId) {
        JsonObject json = read(scId);
        if (json == null) return;
        json.addProperty("visibility", "private");
        write(scId, json);
    }

    private static void write(String scId, JsonObject json) {
        FileUtil.writeFile(file(scId).getAbsolutePath(), json.toString());
        notifyChanged();
    }

    /** The project card's share button: the link copied, then Android's share sheet. */
    public static void share(Activity activity, String projectName, String url) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GitHub backup link", url));
        Toast.makeText(activity, "Link copied", Toast.LENGTH_SHORT).show();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, projectName + " (Sketchware AI backup)");
        send.putExtra(Intent.EXTRA_TEXT, url);
        try {
            activity.startActivity(Intent.createChooser(send, "Share the backup link"));
        } catch (RuntimeException ignored) {
            // no app to share with: the link is on the clipboard
        }
    }
}
