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
 */
public final class ProjectLinks {

    static final String FILE = "github_backup.json";

    private ProjectLinks() {
    }

    static File file(String scId) {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId + "/" + FILE);
    }

    /** The project's backup link, or null (none yet, or not a github.com link). */
    public static String get(String scId) {
        if (scId == null || !scId.matches("\\d+")) return null;
        File f = file(scId);
        if (!f.isFile() || f.length() > 4096) return null;
        try {
            JsonObject json = DeviceFlow.parse(FileUtil.readFile(f.getAbsolutePath()));
            String url = DeviceFlow.string(json, "url");
            return url != null && url.startsWith("https://github.com/") ? url : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** After a backup to GitHub: the link of the project's .swb in the repository. */
    static void save(String scId, String url, String path) {
        if (scId == null || !scId.matches("\\d+") || url == null) return;
        File dir = file(scId).getParentFile();
        if (dir == null || !dir.isDirectory()) return; // the project is gone
        JsonObject json = new JsonObject();
        json.addProperty("url", url);
        json.addProperty("path", path);
        json.addProperty("time", System.currentTimeMillis());
        FileUtil.writeFile(file(scId).getAbsolutePath(), json.toString());
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
