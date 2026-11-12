package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import mod.jbk.util.LogUtil;

/**
 * v1.9.8 (owner's 12 Nov ask): release notices from {@code config/app-control.json} on the repository's main branch.
 * Read in the background at app start (cached for when the phone is offline) and shown as one dialog at most:
 * an update when this version is older than {@code min_version_code} (with {@code update_url}), the
 * {@code status_message}, or, with {@code require_login}, a suggestion to sign in.
 * <p>
 * Notices only: nothing here can stop the IDE from opening or a project from building. {@code app_enabled=false} shows
 * the message; it doesn't lock anything (a remote switch that disables an offline IDE was declined; see
 * engineering/v1.9.8-verification.md). No build expiry.
 */
public final class AppControl {

    public static final String URL = "https://raw.githubusercontent.com/smartapps000-hub/SketchwareAI/main/config/app-control.json";
    static final String PREFS = "sketchware_ai_app_control";
    private static boolean checkedThisStart;
    /** v1.9.9: the file was read from GitHub in this process (the network works); the sign-in gate needs it. */
    private static volatile boolean fetchedOnline;
    /** A notice read but not shown yet (another dialog was up, or the screen was recreated). */
    private static Notice pending;
    private static SharedPreferences pendingPrefs;

    private AppControl() {
    }

    /** The fields of app-control.json; missing ones keep these defaults. */
    public static final class Config {
        public boolean appEnabled = true;
        public int minVersionCode;
        public boolean requireLogin;
        public String statusMessage = "";
        public String updateUrl;
        /** A link for the status message (the update link is for updates only). */
        public String messageUrl;
    }

    /** Links only into the owner's repository: whoever can change the file can't send users anywhere else. */
    static String safeUrl(String url) {
        if (url == null) return null;
        for (String prefix : new String[]{"https://github.com/smartapps000-hub/", "https://raw.githubusercontent.com/smartapps000-hub/",
                "https://media.githubusercontent.com/media/smartapps000-hub/"}) {
            if (url.startsWith(prefix) && !url.contains("..")) return url;
        }
        return null;
    }

    /** One dialog to show. */
    public static final class Notice {
        /** "update", "message" or "login". */
        public String kind;
        public String title;
        public String text;
        /** https only, else null. */
        public String url;
    }

    /** Reads app-control.json leniently; null when it isn't a JSON object. */
    public static Config parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json == null ? "" : json);
            if (!root.isJsonObject()) return null;
            JsonObject o = root.getAsJsonObject();
            Config c = new Config();
            if (o.has("app_enabled") && o.get("app_enabled").isJsonPrimitive()) c.appEnabled = o.get("app_enabled").getAsBoolean();
            if (o.has("min_version_code") && o.get("min_version_code").isJsonPrimitive()) {
                c.minVersionCode = o.get("min_version_code").getAsInt();
            }
            if (o.has("require_login") && o.get("require_login").isJsonPrimitive()) c.requireLogin = o.get("require_login").getAsBoolean();
            String message = DeviceFlow.string(o, "status_message");
            c.statusMessage = message == null ? "" : message.trim();
            if (c.statusMessage.length() > 500) c.statusMessage = c.statusMessage.substring(0, 500);
            c.updateUrl = safeUrl(DeviceFlow.string(o, "update_url"));
            c.messageUrl = safeUrl(DeviceFlow.string(o, "message_url"));
            return c;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * What to show, at most one notice: an update first, then a message (a plain status message only once per text;
     * with app_enabled=false at every start), then the sign-in suggestion.
     */
    public static Notice decide(Config c, int versionCode, boolean signedIn, String lastMessage) {
        if (c == null) return null;
        Notice n = new Notice();
        if (versionCode < c.minVersionCode) {
            n.kind = "update";
            n.title = "Update available";
            n.text = c.statusMessage.isEmpty() ? "A newer Sketchware AI is ready." : c.statusMessage;
            n.url = c.updateUrl;
            return n;
        }
        if (!c.appEnabled || (!c.statusMessage.isEmpty() && !c.statusMessage.equals(lastMessage))) {
            if (c.statusMessage.isEmpty()) return null;
            n.kind = "message";
            n.title = "Sketchware AI";
            n.text = c.statusMessage;
            n.url = c.messageUrl;
            return n;
        }
        if (c.requireLogin && !signedIn) {
            n.kind = "login";
            n.title = "Back up your projects";
            n.text = "Sign in with GitHub to keep your projects safe.";
            return n;
        }
        return null;
    }

    /** v1.9.9: true once app-control.json was read from GitHub in this process. */
    public static boolean fetchedOnline() {
        return fetchedOnline;
    }

    /** v1.9.9: the file as last read on this phone (offline starts use it), or null when it was never read. */
    public static Config cached(Context context) {
        String json = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last", null);
        return json == null ? null : parse(json);
    }

    /** At app start: reads the file in the background (once per process) and shows its notice on {@code activity}. */
    public static void check(Activity activity) {
        synchronized (AppControl.class) {
            if (checkedThisStart) return;
            checkedThisStart = true;
        }
        Context app = activity.getApplicationContext();
        java.lang.ref.WeakReference<Activity> ref = new java.lang.ref.WeakReference<>(activity);
        Thread thread = new Thread(() -> {
            try {
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String json = fetch();
                if (json != null && parse(json) != null) {
                    prefs.edit().putString("last", json).apply();
                    fetchedOnline = true;
                } else {
                    json = prefs.getString("last", null);
                }
                Config config = parse(json);
                boolean signedIn = TokenStore.get(app).login() != null;
                Notice notice = decide(config, com.sketchware.remod.BuildConfig.VERSION_CODE, signedIn,
                        prefs.getString("shown_message", null));
                if (notice == null) return;
                Activity a = ref.get();
                if ("login".equals(notice.kind)) {
                    // v1.9.9: required sign-in is the sign-in screen itself (AuthGuard), not a dialog
                    // only while the main screen is in front (the read takes seconds: the user may have opened a
                    // project meanwhile; then the main screen's next onResume asks)
                    if (a != null) a.runOnUiThread(() -> {
                        if (!a.isFinishing() && !a.isDestroyed() && a.hasWindowFocus()) AuthGuard.check(a);
                    });
                    return;
                }
                synchronized (AppControl.class) {
                    pending = notice;
                    pendingPrefs = prefs;
                }
                if (a != null) a.runOnUiThread(() -> showPending(a));
            } catch (Throwable t) {
                LogUtil.e("AppControl", "Couldn't read app-control.json", t);
            }
        }, "AppControl");
        thread.setDaemon(true);
        thread.start();
    }

    private static String fetch() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(URL).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            c.setRequestProperty("User-Agent", "SketchwareAI/" + com.sketchware.remod.BuildConfig.VERSION_NAME);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    if (out.size() > 64 * 1024) return null;
                    out.write(buffer, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null; // offline: the cached copy is used
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * Shows the notice read at start on {@code a} (MainActivity: also from onResume, for a screen recreated meanwhile),
     * once no other dialog has the focus; it waits up to half a minute for that, else until the next resume.
     */
    public static void showPending(Activity a) {
        showPending(a, 0);
    }

    private static void showPending(Activity a, int tries) {
        Notice notice;
        SharedPreferences prefs;
        synchronized (AppControl.class) {
            notice = pending;
            prefs = pendingPrefs;
        }
        if (notice == null || a.isFinishing() || a.isDestroyed()) return;
        if (!a.hasWindowFocus()) {
            // another dialog (What's new, the import offer, a restore) is up: after it
            if (tries < 20) a.getWindow().getDecorView().postDelayed(() -> showPending(a, tries + 1), 1500);
            return;
        }
        synchronized (AppControl.class) {
            if (pending != notice) return;
            pending = null;
        }
        show(a, notice, prefs);
    }

    private static void show(Activity a, Notice notice, SharedPreferences prefs) {
        if (a.isFinishing() || a.isDestroyed()) return;
        if ("message".equals(notice.kind)) prefs.edit().putString("shown_message", notice.text).apply();
        com.sketchware.ai.ui.ObAlertBuilder b = new com.sketchware.ai.ui.ObAlertBuilder(a,
                com.sketchware.remod.R.style.SketchwareAI_GlassAlertDialog);
        b.setTitle(notice.title).setMessage(notice.text);
        if ("login".equals(notice.kind)) {
            b.setPositiveButton("Sign in", (d, w) -> a.startActivity(new Intent(a, GitHubActivity.class)));
            b.setNegativeButton("Later", null);
        } else if (notice.url != null) {
            b.setPositiveButton("update".equals(notice.kind) ? "Update" : "Open", (d, w) -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(notice.url)));
                } catch (android.content.ActivityNotFoundException ignored) {
                }
            });
            b.setNegativeButton("Later", null);
        } else {
            b.setPositiveButton(android.R.string.ok, null);
        }
        b.show();
    }
}
