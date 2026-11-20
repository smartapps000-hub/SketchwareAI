package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import com.sketchware.remod.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * v2.1.0 (owner's 16 Nov directive "in-built app updater"): the update named in app-control.json, downloaded in the
 * app with a 0-100% sheet (Hide keeps it going), checked against {@code update_sha256} before Android's installer opens
 * (a file that doesn't match is deleted and never offered). Without a checksum in app-control.json the link opens in
 * the browser as before. The download only follows GitHub's own hosts. Android asks "Install unknown apps" once for
 * Sketchware AI; the installer itself always asks the user.
 */
public final class AppUpdater {

    static final String FILE = "Sketchware_AI_update.apk";
    private static volatile boolean running;

    private AppUpdater() {
    }

    /** Whether the configuration offers a newer version than this one (a required or a newest one). */
    public static boolean newerAvailable(AppControl.Config c) {
        int mine = com.sketchware.remod.BuildConfig.VERSION_CODE;
        return c != null && c.updateUrl != null && (mine < c.minVersionCode || mine < c.latestVersionCode);
    }

    public static void start(Activity activity, AppControl.Config config) {
        if (config == null || config.updateUrl == null) {
            Toast.makeText(activity, "No update link yet: try again in a minute.", Toast.LENGTH_LONG).show();
            return;
        }
        if (config.updateSha256 == null) {
            // no checksum to check against: GitHub's page in the browser, as before v2.1.0
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(config.updateUrl)));
            } catch (RuntimeException e) {
                Toast.makeText(activity, "No browser on this phone", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (running) {
            Toast.makeText(activity, "The update is downloading.", Toast.LENGTH_SHORT).show();
            return;
        }
        running = true;
        GitHubProgress progress = GitHubProgress.show(activity, R.drawable.ob_ic_export, "Updating Sketchware AI");
        progress.update(1, "Connecting to GitHub…");
        boolean[] cancelled = {false};
        Thread[] worker = new Thread[1];
        progress.actions(() -> {
            cancelled[0] = true;
            if (worker[0] != null) worker[0].interrupt();
        }, () -> {
            running = false;
            start(activity, config);
        }, null);
        Context app = activity.getApplicationContext();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        worker[0] = new Thread(() -> {
            File dir = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/downloads");
            File target = new File(dir, FILE);
            File part = new File(dir, FILE + ".part");
            String failure = null;
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
                String sha = download(config.updateUrl, part, cancelled, (done, total) -> main.post(() -> {
                    int percent = total > 0 ? 2 + (int) (93 * Math.min(1.0, done / (double) total)) : 50;
                    progress.update(percent, "Downloading… " + CloudSync.megabytes(done)
                            + (total > 0 ? " of " + CloudSync.megabytes(total) : ""));
                }));
                if (!config.updateSha256.equals(sha)) {
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    throw new IOException("The downloaded file isn't the release (its SHA-256 doesn't match): it was deleted.");
                }
                if (target.exists() && !target.delete()) throw new IOException("Can't replace " + target);
                if (!part.renameTo(target)) throw new IOException("Couldn't save " + target);
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                failure = cancelled[0] ? "Cancelled." : (e.getMessage() == null ? e.toString() : e.getMessage());
            } finally {
                running = false;
            }
            String why = failure;
            main.post(() -> {
                if (why != null) {
                    if (progress.hidden()) Toast.makeText(app, "The update didn't download: " + why, Toast.LENGTH_LONG).show();
                    else progress.failed(why, !cancelled[0]);
                    return;
                }
                if (!progress.hidden()) progress.done("Checked (SHA-256 matches): Android's installer opens.");
                install(activity, target);
            });
        }, "AppUpdater");
        worker[0].setDaemon(true);
        worker[0].start();
    }

    interface Bytes {
        void got(long done, long total);
    }

    /** Downloads {@code url} (GitHub's hosts only, redirects followed by hand) into {@code to}; returns its SHA-256. */
    static String download(String url, File to, boolean[] cancelled, Bytes bytes) throws Exception {
        String next = url;
        for (int hop = 0; hop < 6; hop++) {
            URL u = new URL(next);
            String host = u.getHost();
            if (!"https".equals(u.getProtocol()) || !(host.equals("github.com") || host.equals("raw.githubusercontent.com")
                    || host.equals("media.githubusercontent.com") || host.equals("objects.githubusercontent.com")
                    || host.endsWith(".githubusercontent.com"))) {
                throw new IOException("The update link leads away from GitHub (" + host + "): not downloaded.");
            }
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20_000);
            c.setReadTimeout(60_000);
            c.setRequestProperty("User-Agent", "SketchwareAI/" + com.sketchware.remod.BuildConfig.VERSION_NAME);
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("GitHub answered HTTP " + code + " without a place.");
                    next = new URL(u, location).toString();
                    continue;
                }
                if (code != 200) throw new IOException("GitHub answered HTTP " + code + ".");
                long total = c.getContentLengthLong();
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long done = 0;
                long told = 0;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        if (cancelled[0] || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled.");
                        out.write(buffer, 0, n);
                        sha.update(buffer, 0, n);
                        done += n;
                        if (done - told > 256 * 1024) {
                            told = done;
                            bytes.got(done, total);
                        }
                    }
                }
                bytes.got(done, total);
                return SwbParts.hex(sha.digest());
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("Too many redirects.");
    }

    /** Android's installer for {@code apk} (Android 8+: "Install unknown apps" for Sketchware AI first, once). */
    static void install(Activity activity, File apk) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "Allow \"Install unknown apps\" for Sketchware AI, then tap Update again.",
                    Toast.LENGTH_LONG).show();
            try {
                activity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (RuntimeException ignored) {
                // no such settings screen
            }
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = androidx.core.content.FileProvider.a(activity.getApplicationContext(),
                    activity.getPackageName() + ".provider", apk);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(activity, "The update is in .sketchware/downloads/" + FILE + " (no installer could open it)",
                    Toast.LENGTH_LONG).show();
        }
    }
}
