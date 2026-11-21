package com.sketchware.ai.github;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * v2.1.2 (owner's 21 Nov report: "updating v2.1.0 → v2.1.1 reset the account card to Sign in with GitHub"): what
 * happened to the GitHub sign-in on this phone, so the next such report can be answered from the phone itself. The last
 * 30 events (signed in, signed out with its reason, the app updated with whether it was signed in) in
 * {@code no_backup/session_log.json} (never in Android's backup). The signed-out account card shows the last sign-out's
 * reason, or, after an update that found no sign-in, that none was there. Writes run on their own thread; the card reads
 * the copy in memory ({@link #load} at start).
 */
public final class SessionLog {

    private static final String FILE = "session_log.json";
    private static final int KEEP = 30;
    private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SessionLog");
        t.setDaemon(true);
        return t;
    });
    private static volatile JsonArray events;

    private SessionLog() {
    }

    private static File file(Context context) {
        return new File(context.getApplicationContext().getNoBackupFilesDir(), FILE);
    }

    private static synchronized JsonArray read(Context context) {
        if (events != null) return events;
        JsonArray list = new JsonArray();
        try {
            File f = file(context);
            if (f.isFile()) {
                JsonElement e = JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                        StandardCharsets.UTF_8));
                if (e.isJsonArray()) list = e.getAsJsonArray();
            }
        } catch (Exception ignored) {
            // a damaged log starts again
        }
        events = list;
        return list;
    }

    /** Reads the log into memory (call at start, off the main thread). */
    public static void load(Context context) {
        Context app = context.getApplicationContext();
        WRITER.execute(() -> read(app));
    }

    /** Adds an event ("signed_in", "signed_out", "updated") with its words; written on the log's own thread. */
    static void record(Context context, String kind, String text) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        WRITER.execute(() -> add(app, kind, text, now));
    }

    private static synchronized void add(Context app, String kind, String text, long time) {
        JsonArray list = read(app);
        JsonObject o = new JsonObject();
        o.addProperty("time", time);
        o.addProperty("kind", kind);
        o.addProperty("text", text);
        o.addProperty("version", com.sketchware.remod.BuildConfig.VERSION_NAME);
        JsonArray next = new JsonArray();
        int start = Math.max(0, list.size() - (KEEP - 1));
        for (int i = start; i < list.size(); i++) next.add(list.get(i));
        next.add(o);
        events = next;
        try {
            File f = file(app);
            File tmp = new File(f.getPath() + ".tmp");
            java.nio.file.Files.write(tmp.toPath(), next.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception ignored) {
            // kept in memory for this run
        }
    }

    /**
     * At every start (off the main thread): an app update since the last start is recorded with whether a sign-in was
     * there; the first start of this install too.
     */
    public static void appStarted(Context context) {
        Context app = context.getApplicationContext();
        WRITER.execute(() -> {
            android.content.SharedPreferences prefs = app.getSharedPreferences("sketchware_ai_session_log", Context.MODE_PRIVATE);
            int last = prefs.getInt("version_code", -1);
            int now = com.sketchware.remod.BuildConfig.VERSION_CODE;
            if (last == now) return;
            String login = TokenStore.get(app).login();
            boolean fresh = last < 0;
            if (fresh) {
                // no log yet: a new install, or an update from a version before v2.1.2 (Android says which)
                try {
                    android.content.pm.PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                    fresh = info.firstInstallTime == info.lastUpdateTime;
                } catch (Exception ignored) {
                    // unknown: counted as a new install
                }
            }
            if (fresh) {
                add(app, "installed", "First start of " + com.sketchware.remod.BuildConfig.VERSION_NAME
                        + (login == null ? "" : " (signed in as @" + login + ")"), System.currentTimeMillis());
            } else {
                add(app, "updated", "Updated " + (last < 0 ? "from an earlier version" : "from version " + last) + " to " + now + (login == null ? ": no GitHub sign-in on "
                        + "this phone" : ": still signed in as @" + login), System.currentTimeMillis());
            }
            prefs.edit().putInt("version_code", now).apply();
        });
    }

    /**
     * The signed-out card's line: the last sign-out's reason and time, or (after an update that found no sign-in, with no
     * sign-out recorded) that none was there; null when there is nothing to say or the log isn't read yet.
     */
    public static String signedOutLine() {
        JsonArray list = events;
        if (list == null) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            JsonObject o = list.get(i).isJsonObject() ? list.get(i).getAsJsonObject() : null;
            if (o == null) continue;
            String kind = DeviceFlow.string(o, "kind");
            String text = DeviceFlow.string(o, "text");
            long time = o.has("time") && o.get("time").isJsonPrimitive() ? o.get("time").getAsLong() : 0;
            if ("signed_in".equals(kind)) return null;
            if ("signed_out".equals(kind)) return "Signed out " + when(time) + ": " + text + ".";
            if ("installed".equals(kind)) {
                return "Sketchware AI was installed on this phone " + when(time) + ": a new install starts signed out "
                        + "(Android removes an app's sign-in when the app is uninstalled).";
            }
            if ("updated".equals(kind) && text != null && text.contains("no GitHub sign-in")) {
                return "No sign-in was on this phone when it updated (" + when(time) + "); no sign-out was recorded.";
            }
        }
        return null;
    }

    static String when(long time) {
        return time <= 0 ? "" : new java.text.SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH).format(new java.util.Date(time));
    }

    /** Tests: forget the copy in memory. */
    static synchronized void resetForTests() {
        events = null;
    }
}
