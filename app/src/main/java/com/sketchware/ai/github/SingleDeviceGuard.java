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
 * <p>
 * v2.1.2 (owner's 21 Nov report: signed out after updating v2.1.0 → v2.1.1): nothing in an update removes a sign-in;
 * this rule is the only automatic sign-out, and it compared installs, not phones, so another install of Sketchware AI
 * on the same phone (a reinstall, a cloned / dual app) counted as another phone. The mark now also holds
 * {@code device_key} = SHA-256 of this phone's ANDROID_ID (the same for every install of this app on this phone and
 * Android user) and the login; a mark with this phone's key never signs this phone out. Marks written before v2.1.2 have
 * no key: their phone model stands in for it. Every sign-out is recorded with its reason ({@link SessionLog}), and the
 * signed-out account card says it.
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
        if (idCached != null) return idCached;
        File f = new File(context.getApplicationContext().getNoBackupFilesDir(), "device_install_id");
        try {
            if (f.isFile()) {
                String id = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                if (id.matches("[0-9a-f-]{36}")) {
                    idCached = id;
                    idSaved = true;
                    return id;
                }
            }
        } catch (IOException ignored) {
            // made again below
        }
        String id = java.util.UUID.randomUUID().toString();
        try {
            File tmp = new File(f.getPath() + ".tmp");
            java.nio.file.Files.write(tmp.toPath(), id.getBytes(StandardCharsets.UTF_8));
            idSaved = tmp.renameTo(f);
            if (!idSaved) tmp.delete();
        } catch (IOException ignored) {
            // second review (LOW): kept for this run only; checks then change nothing (see check)
            idSaved = false;
        }
        idCached = id;
        return id;
    }

    private static String idCached;
    /** Whether the id is on disk (a full phone: a check could compare against another run's id: it does nothing). */
    private static volatile boolean idSaved;

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

    /** v2.1.2: this phone's key for {@code login}: SHA-256 of ANDROID_ID | login (null when ANDROID_ID can't be read). */
    static String deviceKey(Context context, String login) {
        String androidId = null;
        try {
            androidId = android.provider.Settings.Secure.getString(context.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);
        } catch (RuntimeException ignored) {
            // no key: the model stands in
        }
        if (androidId == null || androidId.isEmpty() || login == null) return null;
        return hash("device:" + androidId, login);
    }

    /** v2.1.2: this phone's model as the mark names it. */
    static String model() {
        return (Build.MANUFACTURER + " " + Build.MODEL).trim();
    }

    /**
     * The file as GitHub has it: {hash, sha, time written (ms, "0" when unknown), device key (v2.1.2, else null), phone
     * model (else null), app version (else null)}; null when there is none (404).
     */
    static String[] read(String token, String login) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.REPO
                + "/contents/" + PATH, token, null);
        if (r.code == 404) return null;
        if (!r.ok()) throw CloudBackup.http("Reading the device mark", r);
        JsonObject o = DeviceFlow.parse(r.body);
        String content = DeviceFlow.string(o, "content");
        String sha = DeviceFlow.string(o, "sha");
        if (content == null) return new String[]{null, sha, "0", null, null, null};
        JsonObject file = DeviceFlow.parse(new String(android.util.Base64.decode(content, android.util.Base64.DEFAULT),
                StandardCharsets.UTF_8));
        long time = 0;
        try {
            if (file.has("time") && file.get("time").isJsonPrimitive()) time = file.get("time").getAsLong();
        } catch (RuntimeException ignored) {
            // no time
        }
        return new String[]{DeviceFlow.string(file, "active_device_hash"), sha, String.valueOf(time),
                DeviceFlow.string(file, "device_key"), DeviceFlow.string(file, "device"), DeviceFlow.string(file, "app_version")};
    }

    /** Writes this phone's mark (makes the private repository when the account has none). */
    static void write(String token, String login, String mark, String sha) throws IOException, GitHubException {
        write(token, login, mark, sha, System.currentTimeMillis(), null);
    }

    /**
     * second review (MEDIUM): {@code time} is when this phone signed in (not when the mark happened to be written:
     * a claim that waited offline must not look newer than a later sign-in on another phone).
     */
    static void write(String token, String login, String mark, String sha, long signedInAt)
            throws IOException, GitHubException {
        write(token, login, mark, sha, signedInAt, null);
    }

    static void write(String token, String login, String mark, String sha, long signedInAt, String deviceKey)
            throws IOException, GitHubException {
        JsonObject file = new JsonObject();
        file.addProperty("active_device_hash", mark);
        if (deviceKey != null) file.addProperty("device_key", deviceKey);
        file.addProperty("device", (Build.MANUFACTURER + " " + Build.MODEL).trim());
        file.addProperty("app_version", com.sketchware.remod.BuildConfig.VERSION_NAME);
        file.addProperty("time", signedInAt > 0 ? signedInAt : System.currentTimeMillis());
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
        synchronized (SingleDeviceGuard.class) {
            prefs(app).edit().putBoolean("claim_pending", true).putLong("signed_in_at", System.currentTimeMillis())
                    .putBoolean("claimed", false).remove("checked_at").commit();
        }
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
        long signedInAt = prefs(app).getLong("signed_in_at", 0);
        String mine = hash(installId(app), login);
        String key = deviceKey(app, login);
        String[] there = read(token, login);
        // second review (MEDIUM): another phone that signed in after this one keeps its mark (the next check signs
        // this phone out)
        if ("signout".equals(decide(there, mine, true, signedInAt, key, model()))) return;
        write(token, login, mine, there == null ? null : there[1], signedInAt, key);
        markClaimed(app, signedInAt);
    }

    /** second review (LOW): the claim is done only for the sign-in it was made for (not a newer one meanwhile). */
    private static void markClaimed(Context app, long signedInAt) {
        SharedPreferences prefs = prefs(app);
        synchronized (SingleDeviceGuard.class) {
            if (prefs.getLong("signed_in_at", 0) != signedInAt) return;
            prefs.edit().putLong("checked_at", System.currentTimeMillis()).putBoolean("claimed", true)
                    .putBoolean("claim_pending", false).apply();
        }
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
        return decide(there, mine, pending, signedInAt, null, null);
    }

    /**
     * v2.1.2: as above, and a mark of this same phone (its {@code device_key}; a mark from before v2.1.2: its phone
     * model) is never another phone: "claim" when this phone's sign-in is the newer one waiting to be recorded, else
     * "keep".
     */
    static String decide(String[] there, String mine, boolean pending, long signedInAt, String myKey, String myModel) {
        if (there == null || there[0] == null) return "claim";
        if (there[0].equals(mine)) return pending ? "claim" : "keep";
        if (samePhone(there, myKey, myModel)) return pending ? "claim" : "keep";
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

    /** v2.1.2: the mark was written on this phone (another install of the app here: a reinstall, a cloned app). */
    static boolean samePhone(String[] there, String myKey, String myModel) {
        if (there == null || there.length < 5) return false;
        String key = there[3];
        if (key != null) return myKey != null && key.equals(myKey);
        // written before v2.1.2 (no key): its phone model stands in
        String model = there[4];
        return model != null && myModel != null && !model.trim().isEmpty() && model.trim().equalsIgnoreCase(myModel.trim());
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
                    long signedInAt = prefs.getLong("signed_in_at", 0);
                    String mine = hash(installId(app), login);
                    // second review (LOW): an id that couldn't be saved can't be compared with the mark of an earlier
                    // run: nothing changes
                    if (!idSaved) return;
                    String[] there = read(token, login);
                    String key = deviceKey(app, login);
                    String what = decide(there, mine, prefs.getBoolean("claim_pending", false), signedInAt, key, model());
                    if ("signout".equals(what)) {
                        // another phone signed in later: this phone signs out (the same sign-in, still)
                        if (!token.equals(TokenStore.get(app).token())) return;
                        LogUtil.d("SingleDeviceGuard", "Another phone holds the session: signed out here");
                        String other = there == null || there.length < 5 || there[4] == null ? "another phone" : there[4];
                        String version = there == null || there.length < 6 || there[5] == null ? "" : " · " + there[5];
                        GitHubSession.signOut(app, "the account signed in on another phone (" + other + version + ")");
                        prefs.edit().putBoolean("signed_out_elsewhere", true).apply();
                        Activity a = ref.get();
                        if (a != null) a.runOnUiThread(() -> showSignedOut(a));
                        return;
                    }
                    if ("claim".equals(what)) write(token, login, mine, there == null ? null : there[1], signedInAt, key);
                    // v2.1.2: a mark of this phone without its key (written before v2.1.2) gets it
                    else if ("keep".equals(what) && key != null && there != null && there.length > 3 && there[3] == null
                            && mine.equals(there[0])) {
                        write(token, login, mine, there[1], signedInAt, key);
                    }
                    markClaimed(app, signedInAt);
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
