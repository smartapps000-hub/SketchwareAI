package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.gson.JsonObject;
import com.sketchware.remod.R;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import mod.jbk.util.LogUtil;

/**
 * v2.1.0 (owner's 16 Nov directive "strict single-device session lock: 1 account = 1 phone"): the phone that signed in
 * last owns the account's session. Its mark is {@code .device-session.json} in the account's private
 * {@code SketchwareAI-Projects} repository: {@code active_device_hash} = SHA-256 of this install's random id and the
 * GitHub login (no hardware id, no phone number), with the phone's model and the app version for the owner to read.
 * A sign-in writes it ({@link #claim}); the startup gate and the six-hourly re-check read it ({@link #check}): the mark
 * of another phone signs this one out ("Signed out: this account was signed in on another device"). Only a read GitHub
 * answered decides: no network, a server error, a rate limit or a refused sign-in change nothing. An account without the
 * file (an upgrade from v2.0.x) is claimed by the first phone that checks. The projects on the phone stay; the sign-out
 * is this phone's alone (GitHub's token isn't revoked).
 */
public final class SingleDeviceGuard {

    static final String PATH = ".device-session.json";
    static final String PREFS = "sketchware_ai_device";
    private static final long RECHECK_MILLIS = 6 * 3600_000L;
    private static volatile boolean checking;

    private SingleDeviceGuard() {
    }

    /** This install's random id (made once; a reinstall is a new device). */
    static synchronized String installId(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString("install_id", null);
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("install_id", id).apply();
        }
        return id;
    }

    /** The mark of this phone for {@code login}. */
    static String hash(String installId, String login) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return SwbParts.hex(d.digest((installId + "|" + login.toLowerCase(java.util.Locale.ROOT))
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The file as GitHub has it: {hash, sha}; null when there is none (404). */
    static String[] read(String token, String login) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.REPO
                + "/contents/" + PATH, token, null);
        if (r.code == 404) return null;
        if (!r.ok()) throw CloudBackup.http("Reading the device mark", r);
        JsonObject o = DeviceFlow.parse(r.body);
        String content = DeviceFlow.string(o, "content");
        String sha = DeviceFlow.string(o, "sha");
        if (content == null) return new String[]{null, sha};
        String text = new String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
        return new String[]{DeviceFlow.string(DeviceFlow.parse(text), "active_device_hash"), sha};
    }

    /** Writes this phone's mark (makes the private repository when the account has none). */
    static void write(String token, String login, String mark, String sha) throws IOException, GitHubException {
        JsonObject file = new JsonObject();
        file.addProperty("active_device_hash", mark);
        file.addProperty("device", (Build.MANUFACTURER + " " + Build.MODEL).trim());
        file.addProperty("app_version", com.sketchware.remod.BuildConfig.VERSION_NAME);
        file.addProperty("time", System.currentTimeMillis());
        JsonObject body = new JsonObject();
        body.addProperty("message", "Sketchware AI: signed in on " + (Build.MANUFACTURER + " " + Build.MODEL).trim());
        body.addProperty("content", android.util.Base64.encodeToString(file.toString().getBytes(StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP));
        if (sha != null) body.addProperty("sha", sha);
        String path = "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.REPO + "/contents/" + PATH;
        GitHubHttp.Response r = GitHubHttp.api("PUT", path, token, body.toString());
        if (r.code == 404) {
            // no backup repository yet: made private, then the file
            JsonObject repo = new JsonObject();
            repo.addProperty("name", CloudBackup.REPO);
            repo.addProperty("private", true);
            repo.addProperty("auto_init", true);
            repo.addProperty("description", "Sketchware AI: this account's sign-in mark and older backups (private)");
            GitHubHttp.Response made = GitHubHttp.api("POST", "/user/repos", token, repo.toString());
            if (!made.ok() && made.code != 422) throw CloudBackup.http("Making " + CloudBackup.REPO, made);
            r = GitHubHttp.api("PUT", path, token, body.toString());
        }
        if (!r.ok()) throw CloudBackup.http("Writing the device mark", r);
    }

    /** After a sign-in: this phone is the account's phone now (background; a failure is tried at the next check). */
    public static void claim(Context context) {
        Context app = context.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                TokenStore store = TokenStore.get(app);
                String login = store.login();
                String token = store.token();
                if (login == null || token == null) return;
                String[] there = read(token, login);
                write(token, login, hash(installId(app), login), there == null ? null : there[1]);
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putLong("checked_at", System.currentTimeMillis()).putBoolean("claimed", true).apply();
            } catch (Exception e) {
                LogUtil.d("SingleDeviceGuard", "Device mark not written now: " + e);
            }
        }, "DeviceClaim");
        t.setDaemon(true);
        t.start();
    }

    /**
     * The startup gate's (and the six-hourly) check: another phone's mark signs this phone out; no mark: claimed.
     * {@code activity} shows the message when it is still in front.
     */
    public static void check(Activity activity) {
        Context app = activity.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (checking || TokenStore.get(app).login() == null || GitHubSession.isRefused(app)) return;
        if (System.currentTimeMillis() - prefs.getLong("checked_at", 0) < RECHECK_MILLIS && prefs.getBoolean("claimed", false)) {
            return;
        }
        checking = true;
        java.lang.ref.WeakReference<Activity> ref = new java.lang.ref.WeakReference<>(activity);
        Thread t = new Thread(() -> {
            try {
                TokenStore store = TokenStore.get(app);
                String login = store.login();
                String token = store.token();
                if (login == null || token == null) return;
                String mine = hash(installId(app), login);
                String[] there = read(token, login);
                if (there == null || there[0] == null) {
                    write(token, login, mine, there == null ? null : there[1]);
                } else if (!there[0].equals(mine)) {
                    // another phone signed in later: this phone signs out (the same sign-in, still)
                    if (!token.equals(TokenStore.get(app).token())) return;
                    LogUtil.d("SingleDeviceGuard", "Another phone holds the session: signed out here");
                    GitHubSession.signOut(app);
                    prefs.edit().putBoolean("signed_out_elsewhere", true).putBoolean("claimed", false).apply();
                    Activity a = ref.get();
                    if (a != null) a.runOnUiThread(() -> showSignedOut(a));
                    return;
                }
                prefs.edit().putLong("checked_at", System.currentTimeMillis()).putBoolean("claimed", true).apply();
            } catch (Exception e) {
                // no network, a server error, a rate limit: nothing changes
                LogUtil.d("SingleDeviceGuard", "Device check skipped: " + e);
            } finally {
                checking = false;
            }
        }, "DeviceCheck");
        t.setDaemon(true);
        t.start();
    }

    /** The message, once (also when the screen comes back later). */
    public static void showSignedOut(Activity a) {
        SharedPreferences prefs = a.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("signed_out_elsewhere", false) || a.isFinishing() || a.isDestroyed()) return;
        prefs.edit().remove("signed_out_elsewhere").apply();
        new com.sketchware.ai.ui.ObAlertBuilder(a, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Signed out")
                .setMessage("Signed out: this account was signed in on another device. Your projects on this phone stay; "
                        + "sign in again here to use GitHub on this phone (the other phone is then signed out).")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
