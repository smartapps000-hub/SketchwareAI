package com.sketchware.ai.github;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;

/**
 * v2.1.1 (owner's 21 Nov directive "real app icon everywhere"): a project's launcher icon wherever the project shows:
 * <ul>
 * <li>on this phone: {@code resources/icons/<sc_id>/icon.png} when the project has its own icon (custom_icon);</li>
 * <li>in a .swb backup: its {@code resources/icons/icon.png} entry;</li>
 * <li>on GitHub: {@value #PATH} in the project's repository, written by a backup or Publish when the icon changed
 * (the git blob id is compared first) and read with the contents API (a private repository with the token), kept in
 * {@code files/repo_icons/<repository number>.png}; a repository without one is asked again after a day.</li>
 * </ul>
 * A project without its own icon keeps the Home card's two letters on its tile (owner, 21 Nov: the default app icon
 * stays the template's, as it is).
 */
public final class RepoIcons {

    static final String PATH = "playstore/icon.png";
    static final long MISSING_RETRY_MILLIS = 24 * 3600_000L;
    static final int MAX_BYTES = 1024 * 1024;

    private RepoIcons() {
    }

    /** The project's own icon file on this phone, or null when it uses the template's. */
    public static File localIcon(String scId) {
        try {
            HashMap<String, Object> project = lC.b(scId);
            if (project == null || !yB.a(project, "custom_icon")) return null;
            File f = new File(wq.e() + File.separator + scId + File.separator + "icon.png");
            return f.isFile() && f.length() > 0 && f.length() <= MAX_BYTES ? f : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The icon inside a .swb backup ({@code resources/icons/icon.png}), or null. */
    public static byte[] fromSwb(File swb) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(swb)) {
            java.util.zip.ZipEntry e = zip.getEntry("resources/icons/icon.png");
            if (e == null || e.getSize() > MAX_BYTES) return null;
            try (InputStream in = zip.getInputStream(e)) {
                return read(in, MAX_BYTES);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static byte[] read(InputStream in, int most) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) {
            out.write(b, 0, n);
            if (out.size() > most) throw new IOException("icon over " + most + " bytes");
        }
        return out.toByteArray();
    }

    /** A small bitmap of the icon bytes (decoded no larger than {@code px}), or null. */
    public static Bitmap decode(byte[] bytes, int px) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        int s = 1;
        while (px > 0 && Math.max(bounds.outWidth, bounds.outHeight) / (s * 2) >= px) s *= 2;
        o.inSampleSize = s;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
    }

    public static Bitmap decode(File f, int px) {
        try {
            return f == null ? null : decode(java.nio.file.Files.readAllBytes(f.toPath()), px);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Git's blob id of {@code bytes} (the contents API's {@code sha}). */
    static String blobSha(byte[] bytes) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-1");
            d.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
            d.update(bytes);
            return SwbParts.hex(d.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Writes {@code icon} as {@value #PATH} in {@code login/repo} unless the same bytes are there already; true when
     * written. Network errors are thrown (the caller decides whether they matter).
     */
    static boolean upload(String token, String login, String repo, byte[] icon) throws IOException, GitHubException {
        if (icon == null || icon.length == 0 || icon.length > MAX_BYTES) return false;
        String path = "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(repo) + "/contents/" + PATH;
        GitHubHttp.Response there = GitHubHttp.api("GET", path, token, null);
        String sha = null;
        if (there.ok()) {
            sha = DeviceFlow.string(DeviceFlow.parse(there.body), "sha");
            if (blobSha(icon).equals(sha)) return false;
        } else if (there.code != 404) {
            throw CloudBackup.http("Reading the app icon on GitHub", there);
        }
        JsonObject body = new JsonObject();
        body.addProperty("message", "Sketchware AI: app icon");
        body.addProperty("content", android.util.Base64.encodeToString(icon, android.util.Base64.NO_WRAP));
        if (sha != null) body.addProperty("sha", sha);
        GitHubHttp.Response r = GitHubHttp.api("PUT", path, token, body.toString());
        if (!r.ok()) throw CloudBackup.http("Writing the app icon", r);
        return true;
    }

    /** The project's own icon to {@code repo} after a backup or Publish; quiet (a failure leaves the old icon). */
    static void uploadQuietly(String token, String login, String repo, String scId) {
        File f = localIcon(scId);
        if (f == null) return;
        try {
            upload(token, login, repo, java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Exception e) {
            mod.jbk.util.LogUtil.d("RepoIcons", "Icon not written now: " + e);
        }
    }

    /** {@value #PATH} of {@code owner/repo} as GitHub has it; null when there is none (404). */
    static byte[] fetch(String token, String owner, String repo) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(owner) + "/" + CloudBackup.enc(repo)
                + "/contents/" + PATH, token, null);
        if (r.code == 404) return null;
        if (!r.ok()) throw CloudBackup.http("Reading the app icon", r);
        String content = DeviceFlow.string(DeviceFlow.parse(r.body), "content");
        if (content == null) return null;
        byte[] bytes = android.util.Base64.decode(content, android.util.Base64.DEFAULT);
        return bytes.length > MAX_BYTES ? null : bytes;
    }

    /** The phone's copy for repository {@code number}: the icon, or "none" with its time, or nothing yet. */
    static File cacheFile(Context context, long number) {
        return new File(context.getApplicationContext().getFilesDir(), "repo_icons/" + number + ".png");
    }

    static File missingMark(Context context, long number) {
        return new File(context.getApplicationContext().getFilesDir(), "repo_icons/" + number + ".none");
    }

    /**
     * The icon of a repository card: the linked local project's own icon, else the phone's copy, else (background
     * threads only) GitHub's {@value #PATH}, kept for next time. Null when there is none.
     */
    public static Bitmap forRepo(Context context, String token, String owner, String repo, long number, String localScId,
                                 int px) {
        if (localScId != null) {
            File local = localIcon(localScId);
            if (local != null) return decode(local, px);
        }
        File cached = number > 0 ? cacheFile(context, number) : null;
        if (cached != null && cached.isFile()) return decode(cached, px);
        File none = number > 0 ? missingMark(context, number) : null;
        if (none != null && none.isFile() && System.currentTimeMillis() - none.lastModified() < MISSING_RETRY_MILLIS) {
            return null;
        }
        try {
            byte[] bytes = fetch(token, owner, repo);
            if (cached != null) {
                File dir = cached.getParentFile();
                if (dir != null && !dir.isDirectory()) //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                if (bytes == null) {
                    java.nio.file.Files.write(none.toPath(), new byte[0]);
                    //noinspection ResultOfMethodCallIgnored
                    none.setLastModified(System.currentTimeMillis());
                } else {
                    File tmp = new File(cached.getPath() + ".tmp");
                    java.nio.file.Files.write(tmp.toPath(), bytes);
                    if (!tmp.renameTo(cached)) //noinspection ResultOfMethodCallIgnored
                        tmp.delete();
                    //noinspection ResultOfMethodCallIgnored
                    none.delete();
                }
            }
            return decode(bytes, px);
        } catch (Exception e) {
            return null;
        }
    }

    /** Repository number → the local project linked to it under {@code login} (the cards' own icons). */
    public static Map<Long, String> linkedProjects(String login) {
        Map<Long, String> out = new HashMap<>();
        if (login == null) return out;
        try {
            for (HashMap<String, Object> p : lC.a()) {
                String scId = yB.c(p, "sc_id");
                if (scId.isEmpty()) continue;
                long n = ProjectLinks.repoNumber(scId, login);
                if (n > 0) out.put(n, scId);
            }
        } catch (RuntimeException ignored) {
            // no list: GitHub's copies only
        }
        return out;
    }
}
