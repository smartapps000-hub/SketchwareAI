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
        /**
         * v2.1.6: the update's download links in order (app-control's {@code update_urls}, then {@code update_url}):
         * the permanent GitHub Release asset first, the binaries branch's copy after it. Only links to the owner's
         * GitHub ({@link #safeUrl}); the SHA-256 is checked whichever one answers.
         */
        public java.util.List<String> updateUrls = new java.util.ArrayList<>();
        /** A link for the status message (the update link is for updates only). */
        public String messageUrl;
        /** v2.1.0: an update below {@link #minVersionCode} can't be put off (no Later; Create waits for it). */
        public boolean forceUpdate;
        /** v2.1.0: the update APK's SHA-256 (64 hex digits): the in-app updater installs only a file that matches. */
        public String updateSha256;
        /** v2.1.0: the newest release (Settings > App updates says Latest or offers it). */
        public int latestVersionCode;
        public String latestVersionName;
        /** v2.1.0: a line about what comes next (Settings > App updates, a badge; empty: none). */
        public String comingSoon = "";
        /**
         * v2.1.2: the status message is for versions up to this code only (0: all). Lets the file give older versions'
         * plain update dialog its text ("A newer Sketchware AI is ready." otherwise) without the updated version showing it
         * again as a message.
         */
        public int statusMessageMaxVersionCode;
        /** v2.1.2: the update card's words: why it is needed (empty: the card's own) and up to three lines of what's new. */
        public String updateReason = "";
        public final java.util.List<String> updateNotes = new java.util.ArrayList<>();
        /** v2.1.3: up to three lines of what the update fixes or makes faster (the card's "Bugs fixed" section). */
        public final java.util.List<String> updateFixes = new java.util.ArrayList<>();

        /** The status message as {@code versionCode} sees it (v2.1.2: empty above its last version). */
        public String statusMessageFor(int versionCode) {
            return statusMessageMaxVersionCode > 0 && versionCode > statusMessageMaxVersionCode ? "" : statusMessage;
        }
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
        /** v2.1.0: an update that can't be put off. */
        public boolean force;
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
            if (o.has("update_urls") && o.get("update_urls").isJsonArray()) {
                for (com.google.gson.JsonElement e : o.getAsJsonArray("update_urls")) {
                    if (c.updateUrls.size() >= 4) break;
                    String link = e.isJsonPrimitive() ? safeUrl(e.getAsString()) : null;
                    if (link != null && !c.updateUrls.contains(link)) c.updateUrls.add(link);
                }
            }
            if (c.updateUrl != null && !c.updateUrls.contains(c.updateUrl)) c.updateUrls.add(c.updateUrl);
            // the first link stands for the update everywhere a single one is shown (an old version reads update_url)
            if (c.updateUrl == null && !c.updateUrls.isEmpty()) c.updateUrl = c.updateUrls.get(0);
            c.messageUrl = safeUrl(DeviceFlow.string(o, "message_url"));
            if (o.has("force_update") && o.get("force_update").isJsonPrimitive()) c.forceUpdate = o.get("force_update").getAsBoolean();
            String sha = DeviceFlow.string(o, "update_sha256");
            c.updateSha256 = sha != null && sha.trim().toLowerCase(java.util.Locale.ROOT).matches("[0-9a-f]{64}")
                    ? sha.trim().toLowerCase(java.util.Locale.ROOT) : null;
            if (o.has("latest_version_code") && o.get("latest_version_code").isJsonPrimitive()) {
                c.latestVersionCode = o.get("latest_version_code").getAsInt();
            }
            String latestName = DeviceFlow.string(o, "latest_version_name");
            c.latestVersionName = latestName == null || latestName.length() > 40 ? null : latestName.trim();
            String soon = DeviceFlow.string(o, "coming_soon");
            c.comingSoon = soon == null ? "" : soon.trim().length() > 200 ? soon.trim().substring(0, 200) : soon.trim();
            if (o.has("status_message_max_version_code") && o.get("status_message_max_version_code").isJsonPrimitive()) {
                try {
                    c.statusMessageMaxVersionCode = o.get("status_message_max_version_code").getAsInt();
                } catch (RuntimeException ignored) {
                    // v2.1.2 review (LOW): a bad value: the message goes to every version, the rest of the file stands
                }
            }
            String reason = DeviceFlow.string(o, "update_reason");
            c.updateReason = reason == null ? "" : reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
            lines(o, "update_notes", c.updateNotes);
            lines(o, "update_fixes", c.updateFixes);
            return c;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Up to three non-empty lines of a string array (120 characters each); anything else is left out. */
    private static void lines(JsonObject o, String key, java.util.List<String> out) {
        if (!o.has(key) || !o.get(key).isJsonArray()) return;
        for (JsonElement e : o.getAsJsonArray(key)) {
            if (out.size() >= 3) break;
            if (!e.isJsonPrimitive()) continue;
            String line = e.getAsString().trim();
            if (line.isEmpty()) continue;
            out.add(line.length() > 120 ? line.substring(0, 120) : line);
        }
    }

    /**
     * What to show, at most one notice: an update first, then a message (a plain status message only once per text;
     * with app_enabled=false at every start), then the sign-in suggestion.
     */
    public static Notice decide(Config c, int versionCode, boolean signedIn, String lastMessage) {
        if (c == null) return null;
        Notice n = new Notice();
        String message = c.statusMessageFor(versionCode);
        if (versionCode < c.minVersionCode) {
            n.kind = "update";
            n.title = "Update available";
            n.text = message.isEmpty() ? "A newer Sketchware AI is ready." : message;
            n.url = c.updateUrl;
            n.force = c.forceUpdate;
            return n;
        }
        if (!c.appEnabled || (!message.isEmpty() && !message.equals(lastMessage))) {
            if (message.isEmpty()) return null;
            n.kind = "message";
            n.title = "Sketchware AI";
            n.text = message;
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
        if (json == null) return null;
        // v2.1.2 (zero-lag): parsed once per text (screens ask for it on the main thread)
        Object[] memo = parsedMemo;
        if (memo != null && json.equals(memo[0])) return (Config) memo[1];
        Config c = parse(json);
        parsedMemo = new Object[]{json, c};
        return c;
    }

    private static volatile Object[] parsedMemo;

    // ------------------------------------------------------------------ v2.1.0: the startup gate

    private static volatile boolean gatePassed;
    private static volatile boolean updateRequired;
    private static boolean gateScheduled;

    /** The startup gate cleared (no update needed, or put off): the sign-in gate may ask now. */
    public static boolean gatePassed() {
        return gatePassed;
    }

    /** An update that can't be put off is waiting: Create waits for it. */
    public static boolean updateRequired() {
        return updateRequired;
    }

    /**
     * v2.1.0 (owner's 16 Nov directive "sequential startup gate"): the projects list shows at once; 3.5 seconds later
     * app-control.json is read (GitHub, else the copy on this phone): first the update gate (Update with the in-app
     * updater, Later only when it may be put off), and only when that clears the sign-in gate ({@link AuthGuard}) and
     * the one-device check ({@link SingleDeviceGuard}). Once per process.
     */
    public static void scheduleGate(Activity activity) {
        synchronized (AppControl.class) {
            if (gateScheduled) return;
            gateScheduled = true;
        }
        java.lang.ref.WeakReference<Activity> ref = new java.lang.ref.WeakReference<>(activity);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Activity a = ref.get();
            if (a == null) {
                synchronized (AppControl.class) {
                    gateScheduled = false;
                }
                return;
            }
            check(a);
        }, 3500);
    }

    /** The gate's second step: the sign-in gate and the one-device check, on the main screen in front. */
    static void passGate(Activity a) {
        gatePassed = true;
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        // the main screen in front asks now; else its next onResume does (a project may be open meanwhile)
        boolean gateOpen = a.hasWindowFocus() && !AuthGuard.check(a);
        SingleDeviceGuard.check(a);
        // v2.1.6: a sign-in GitHub refused (also found at the first start after an update): the window opens now;
        // review (LOW): not while the sign-in gate's own screen just opened
        if (!gateOpen) SignInPrompt.maybeAsk(a);
    }

    /** {@link #passGate} on the main thread, or only the mark when the screen is gone. */
    private static void passGateLater(Activity a) {
        if (a == null) {
            gatePassed = true;
            return;
        }
        a.runOnUiThread(() -> passGate(a));
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
                Activity a = ref.get();
                if (notice == null || !"update".equals(notice.kind)) {
                    // second review (LOW): an installed update's checked APK isn't kept in the cache
                    AppUpdater.deleteDownloaded(app);
                    // v2.1.0: no update: the sign-in gate next (then a message, if any)
                    passGateLater(a);
                    // the sign-in gate itself asks (passGate): no second sign-in prompt
                    if (notice == null || "login".equals(notice.kind)) return;
                } else {
                    // review (MEDIUM): forced only with a link to update from (never a Create that stays shut)
                    // v2.1.6 review (LOW): a copy signed with another key can't install it: Create never waits
                    updateRequired = notice.force && notice.url != null && SignatureGuard.trusted();
                }
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
                // never a gate that stays shut
                passGateLater(ref.get());
            }
        }, "AppControl");
        thread.setDaemon(true);
        thread.start();
    }

    private static volatile long lastRefresh;
    private static volatile boolean refreshing;

    /**
     * v2.0.0 (owner's 13 Nov "periodic online re-check"): the main screen reads the file again in the background when
     * the last read is older than {@link GitHubSession#RECHECK_MILLIS}; no dialogs, only the cache and the sign-in
     * requirement (a sign-in that became required opens the sign-in screen while the main screen has the focus).
     */
    public static void refresh(Activity activity) {
        long now = System.currentTimeMillis();
        if (refreshing || !checkedThisStart || now - lastRefresh < GitHubSession.RECHECK_MILLIS) return;
        if (lastRefresh == 0) {
            // the start's own read counts as the first one
            lastRefresh = now;
            return;
        }
        refreshing = true;
        lastRefresh = now;
        Context app = activity.getApplicationContext();
        java.lang.ref.WeakReference<Activity> ref = new java.lang.ref.WeakReference<>(activity);
        Thread thread = new Thread(() -> {
            try {
                String json = fetch();
                if (json == null || parse(json) == null) return;
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("last", json).apply();
                fetchedOnline = true;
                Activity a = ref.get();
                if (a != null && AuthGuard.required(app)) a.runOnUiThread(() -> {
                    if (!a.isFinishing() && !a.isDestroyed() && a.hasWindowFocus()) AuthGuard.check(a);
                });
            } catch (Throwable t) {
                LogUtil.e("AppControl", "Couldn't read app-control.json again", t);
            } finally {
                refreshing = false;
            }
        }, "AppControlRefresh");
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

    /**
     * v2.1.0 review (MEDIUM): the update notice again (Create while an update can't wait: the first notice was used up
     * when it showed).
     */
    public static boolean showUpdateAgain(Activity a) {
        Context app = a.getApplicationContext();
        Config config = cached(a);
        Notice notice = decide(config, com.sketchware.remod.BuildConfig.VERSION_CODE, TokenStore.get(app).login() != null, null);
        if (notice == null || !"update".equals(notice.kind) || !notice.force || notice.url == null) {
            // second review (LOW): the file read since no longer requires it: Create goes on
            updateRequired = false;
            return false;
        }
        synchronized (AppControl.class) {
            // never two identical update dialogs
            if (pending != null && "update".equals(pending.kind)) pending = null;
        }
        show(a, notice, app.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        return true;
    }

    private static void show(Activity a, Notice notice, SharedPreferences prefs) {
        if (a.isFinishing() || a.isDestroyed()) return;
        if ("message".equals(notice.kind)) prefs.edit().putString("shown_message", notice.text).apply();
        if ("update".equals(notice.kind)) {
            // v2.1.0: the in-app updater (download, SHA-256 check, Android's installer); Later only when it may wait.
            // v2.1.2 (owner's 21 Nov directive "premium Obsidian glass update card"): the card (UpdateCard)
            Config config = cached(a);
            boolean forced = notice.force && notice.url != null;
            UpdateCard.show(a, config, forced, () -> {
                AppUpdater.start(a, config);
                // review (MEDIUM): an update that may wait doesn't hold the sign-in gate meanwhile
                if (!forced) passGate(a);
            }, forced ? null : () -> passGate(a));
            return;
        }
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
