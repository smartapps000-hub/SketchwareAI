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

    /** v2.1.2 review (LOW): at start: a download notification left by a stopped process goes. */
    public static void clearStaleNotice(Context context) {
        if (!running) UpdateNotice.cancelProgress(context);
    }

    public static void start(Activity activity, AppControl.Config config) {
        // v2.1.6: a copy signed with another key never installs updates by itself (SignatureGuard)
        if (!SignatureGuard.trusted()) {
            Toast.makeText(activity, SignatureGuard.REFUSAL, Toast.LENGTH_LONG).show();
            return;
        }
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
        // v2.1.2 (owner's 21 Nov directive): no Cancel while it downloads, only Hide (the notification goes on showing
        // it); after a failure Hide and Retry
        progress.actions(null, () -> {
            running = false;
            UpdateNotice.cancel(activity.getApplicationContext());
            start(activity, config);
        }, null);
        Context app = activity.getApplicationContext();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        worker[0] = new Thread(() -> {
            // v2.1.0 review (MEDIUM): in the app's own cache folder (shared with the installer through the
            // FileProvider), so no other app can swap the checked file before the installer reads it
            File dir = updatesDir(app);
            File target = new File(dir, FILE);
            File part = new File(dir, FILE + ".part");
            String failure = null;
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
                // review (LOW): back from "Install unknown apps": the checked file is still there
                if (target.isFile() && config.updateSha256.equals(sha256(target))) {
                    main.post(() -> progress.update(96, "Downloaded before and checked again"));
                } else {
                    // v2.1.6: the links in order (the GitHub Release asset first): the first whose file matches
                    java.util.List<String> links = config.updateUrls.isEmpty()
                            ? java.util.Collections.singletonList(config.updateUrl) : config.updateUrls;
                    String sha = downloadFirst(links, config.updateSha256, part, cancelled, (done, total) -> main.post(() -> {
                        int percent = total > 0 ? 2 + (int) (93 * Math.min(1.0, done / (double) total)) : 50;
                        String text = "Downloading… " + CloudSync.megabytes(done)
                                + (total > 0 ? " of " + CloudSync.megabytes(total) : "");
                        progress.update(percent, text);
                        // v2.1.2: hidden, the download shows in the notification
                        if (progress.hidden()) UpdateNotice.progress(app, percent, text);
                    }));
                    if (!config.updateSha256.equals(sha)) {
                        //noinspection ResultOfMethodCallIgnored
                        part.delete();
                        throw new IOException("The downloaded file isn't the release (its SHA-256 doesn't match): it was deleted.");
                    }
                    if (target.exists() && !target.delete()) throw new IOException("Can't replace " + target);
                    if (!part.renameTo(target)) throw new IOException("Couldn't save " + target);
                }
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
                    if (progress.hidden()) {
                        UpdateNotice.failed(app, why);
                        Toast.makeText(app, "The update didn't download: " + why, Toast.LENGTH_LONG).show();
                    } else {
                        progress.failedHideRetry(why);
                    }
                    return;
                }
                if (!progress.hidden()) progress.done("Checked (SHA-256 matches): Android's installer opens.");
                // v2.1.2 review (MEDIUM): Android refuses to start the installer for an app in the background: then the
                // notification opens it ("Tap to install")
                if (activity.isFinishing() || activity.isDestroyed() || !CloudSync.UploadSpeed.inFront()) {
                    UpdateNotice.ready(app, target);
                    return;
                }
                UpdateNotice.cancel(app);
                install(activity, target);
            });
        }, "AppUpdater");
        worker[0].setDaemon(true);
        worker[0].start();
    }

    interface Bytes {
        void got(long done, long total);
    }

    /**
     * v2.1.6: {@link #download} from each link in turn until one gives the file with {@code sha256}; returns that
     * SHA-256, else the last one read (the caller then says it doesn't match), or throws the last failure.
     */
    static String downloadFirst(java.util.List<String> links, String sha256, File to, boolean[] cancelled, Bytes bytes)
            throws Exception {
        Exception last = null;
        String lastSha = null;
        for (String link : links) {
            if (link == null) continue;
            if (cancelled[0]) throw new IOException("Cancelled.");
            try {
                String sha = download(link, to, cancelled, bytes);
                if (sha256 == null || sha256.equals(sha)) return sha;
                lastSha = sha;
                //noinspection ResultOfMethodCallIgnored
                to.delete();
            } catch (Exception e) {
                if (cancelled[0]) throw e;
                last = e;
                //noinspection ResultOfMethodCallIgnored
                to.delete();
            }
        }
        if (lastSha != null) return lastSha;
        if (last != null) throw last;
        throw new IOException("No update link.");
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

    /** The update's folder: the app's own cache (provider_paths.xml shares it with the installer as "updates"). */
    static File updatesDir(Context context) {
        return new File(context.getApplicationContext().getCacheDir(), "updates");
    }

    /** The downloaded update (after it was installed, or when none is needed any more). */
    static void deleteDownloaded(Context context) {
        File[] files = updatesDir(context).listFiles();
        if (files == null || running) return;
        //noinspection ResultOfMethodCallIgnored
        for (File f : files) f.delete();
    }

    /** A file's SHA-256 in lower-case hex. */
    static String sha256(File f) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) sha.update(buffer, 0, n);
        }
        return SwbParts.hex(sha.digest());
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
            Toast.makeText(activity, "No installer could open the update: try Settings › App updates again.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
