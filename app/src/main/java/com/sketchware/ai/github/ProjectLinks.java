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
 */
public final class ProjectLinks {

    static final String FILE = "github_backup.json";

    private ProjectLinks() {
    }

    static File file(String scId) {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId + "/" + FILE);
    }

    /** The project's backup link, or null (none yet, not a github.com link, or written for another project). */
    public static String get(String scId) {
        return get(scId, null);
    }

    /**
     * The same, also null when {@code signedInLogin} (the account signed in now, or null) differs from the account that
     * made the backup.
     */
    public static String get(String scId, String signedInLogin) {
        if (scId == null || !scId.matches("\\d+")) return null;
        File f = file(scId);
        if (!f.isFile() || f.length() > 4096) return null;
        try {
            JsonObject json = DeviceFlow.parse(FileUtil.readFile(f.getAbsolutePath()));
            if (!scId.equals(DeviceFlow.string(json, "sc_id"))) return null;
            String owner = DeviceFlow.string(json, "login");
            if (signedInLogin != null && owner != null && !owner.equalsIgnoreCase(signedInLogin)) return null;
            String url = DeviceFlow.string(json, "url");
            return url != null && url.startsWith("https://github.com/") ? url : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The projects screen's card refresh (held weakly: a closed screen is never kept). */
    private static volatile java.lang.ref.WeakReference<Runnable> changed;

    public static void onChanged(Runnable refresh) {
        changed = refresh == null ? null : new java.lang.ref.WeakReference<>(refresh);
    }

    /** After a backup to GitHub: the link of the project's .swb in the repository, and the account that made it. */
    static void save(String scId, String url, String path, String login) {
        if (scId == null || !scId.matches("\\d+") || url == null) return;
        File dir = file(scId).getParentFile();
        if (dir == null || !dir.isDirectory()) return; // the project is gone
        JsonObject json = new JsonObject();
        json.addProperty("url", url);
        json.addProperty("path", path);
        json.addProperty("sc_id", scId);
        if (login != null) json.addProperty("login", login);
        json.addProperty("time", System.currentTimeMillis());
        FileUtil.writeFile(file(scId).getAbsolutePath(), json.toString());
        java.lang.ref.WeakReference<Runnable> ref = changed;
        Runnable refresh = ref == null ? null : ref.get();
        if (refresh != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(refresh);
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
