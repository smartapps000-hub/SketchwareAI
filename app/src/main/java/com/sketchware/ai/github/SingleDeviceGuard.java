package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.gson.JsonObject;
import com.sketchware.remod.R;

import java.io.File;
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
    /** One read-and-write of the mark at a time (a sign-in's claim and a check never interleave). */
    private static final Object LOCK = new Object();

    private SingleDeviceGuard() {
    }

    /**
     * This install's random id (made once; a reinstall is a new device). v2.1.0 review (LOW): in the no-backup folder,
     * so Android's backup never restores it onto another phone (two phones with one id would never see each other).
     */
    static synchronized String installId(Context context) {
        File f = new File(context.getApplicationContext().getNoBackupFilesDir(), "device_install_id");
        try {
            if (f.isFile()) {
                String id = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                if (id.matches("[0-9a-f-]{36}")) return id;
            }
        } catch (IOException ignored) {
            // made again below
        }
        String id = java.util.UUID.randomUUID().toString();
        try {
            File tmp = new File(f.getPath() + ".tmp");
            java.nio.file.Files.write(tmp.toPath(), id.getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (IOException ignored) {
            // this run uses it; the next one makes another (signed out at worst, never locked)
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

    /** The file as GitHub has it: {hash, sha, time written (ms, "0" when unknown)}; null when there is none (404). */
    static String[] read(String token, String login) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.REPO
                + "/contents/" + PATH, token, null);
        if (r.code == 404) return null;
        if (!r.ok()) throw CloudBackup.http("Reading the device mark", r);
        JsonObject o = DeviceFlow.parse(r.body);
        String content = DeviceFlow.string(o, "content");
        String sha = DeviceFlow.string(o, "sha");
        if (content == null) return new String[]{null, sha, "0"};
        JsonObject file = DeviceFlow.parse(new String(android.util.Base64.decode(content, android.util.Base64.DEFAULT),
                StandardCharsets.UTF_8));
        long time = 0;
        try {
            if (file.has("time") && file.get("time").isJsonPrimitive()) time = file.get("time").getAsLong();
        } catch (RuntimeException ignored) {
            // no time
        }
        return new String[]{DeviceFlow.string(file, "active_device_hash"), sha, String.valueOf(time)};
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

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * After a sign-in: this phone is the account's phone now. v2.1.0 review (HIGH): the sign-in is recorded on the
     * phone first ({@code claim_pending}, {@code signed_in_at}); until its mark is on GitHub a check writes the mark
     * again instead of comparing (a check right after the sign-in, or a claim that failed offline, never signs out the
     * phone that signed in last).
     */
    public static void claim(Context context) {
        Context app = context.getApplicationContext();
        prefs(app).edit().putBoolean("claim_pending", true).putLong("signed_in_at", System.currentTimeMillis())
                .putBoolean("claimed", false).remove("checked_at").commit();
        Thread t = new Thread(() -> {
            try {
                synchronized (LOCK) {
                    claimNow(app);
                }
            } catch (Exception e) {
                LogUtil.d("SingleDeviceGuard", "Device mark not written now (the next check writes it): " + e);
            }
        }, "DeviceClaim");
        t.setDaemon(true);
        t.start();
    }

    /** Writes this phone's mark over whatever is there; marks the claim done. Under {@link #LOCK}. */
    private static void claimNow(Context app) throws IOException, GitHubException {
        TokenStore store = TokenStore.get(app);
        String login = store.login();
        String token = store.token();
        if (login == null || token == null) return;
        String[] there = read(token, login);
        write(token, login, hash(installId(app), login), there == null ? null : there[1]);
        prefs(app).edit().putLong("checked_at", System.currentTimeMillis()).putBoolean("claimed", true)
                .putBoolean("claim_pending", false).apply();
    }

    /** At sign-out: nothing claimed any more (the next sign-in claims again). */
    static void forget(Context context) {
        prefs(context).edit().remove("claimed").remove("claim_pending").remove("checked_at").remove("signed_in_at").apply();
    }

    /**
     * What a check does with the mark GitHub has: "claim" (none there, or this phone's sign-in not yet recorded and
     * newer than the mark), "keep" (this phone's), "signout" (another phone's, written after this phone's own).
     */
    static String decide(String[] there, String mine, boolean pending, long signedInAt) {
        if (there == null || there[0] == null) return "claim";
        if (there[0].equals(mine)) return pending ? "claim" : "keep";
        if (pending) {
            long theirs = 0;
            try {
                theirs = Long.parseLong(there[2]);
            } catch (RuntimeException ignored) {
                // unknown: this phone's newer sign-in wins
            }
            return theirs > signedInAt && signedInAt > 0 ? "signout" : "claim";
        }
        return "signout";
    }

    /**
     * The startup gate's (and the six-hourly) check: another phone's later mark signs this phone out; no mark, or a
     * sign-in not yet recorded: claimed. {@code activity} shows the message when it is still in front.
     */
    public static void check(Activity activity) {
        Context app = activity.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        if (checking || TokenStore.get(app).login() == null || GitHubSession.isRefused(app)) return;
        boolean pendingNow = prefs.getBoolean("claim_pending", false);
        if (!pendingNow && System.currentTimeMillis() - prefs.getLong("checked_at", 0) < RECHECK_MILLIS
                && prefs.getBoolean("claimed", false)) {
            return;
        }
        checking = true;
        java.lang.ref.WeakReference<Activity> ref = new java.lang.ref.WeakReference<>(activity);
        Thread t = new Thread(() -> {
            try {
                synchronized (LOCK) {
                    TokenStore store = TokenStore.get(app);
                    String login = store.login();
                    String token = store.token();
                    if (login == null || token == null) return;
                    String mine = hash(installId(app), login);
                    String[] there = read(token, login);
                    String what = decide(there, mine, prefs.getBoolean("claim_pending", false),
                            prefs.getLong("signed_in_at", 0));
                    if ("signout".equals(what)) {
                        // another phone signed in later: this phone signs out (the same sign-in, still)
                        if (!token.equals(TokenStore.get(app).token())) return;
                        LogUtil.d("SingleDeviceGuard", "Another phone holds the session: signed out here");
                        GitHubSession.signOut(app);
                        prefs.edit().putBoolean("signed_out_elsewhere", true).apply();
                        Activity a = ref.get();
                        if (a != null) a.runOnUiThread(() -> showSignedOut(a));
                        return;
                    }
                    if ("claim".equals(what)) write(token, login, mine, there == null ? null : there[1]);
                    prefs.edit().putLong("checked_at", System.currentTimeMillis()).putBoolean("claimed", true)
                            .putBoolean("claim_pending", false).apply();
                }
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
