package com.sketchware.ai.library;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Finds and downloads a Maven artifact (Google Maven, Maven Central, JitPack, ...) and turns an
 * .aar / .jar / library .zip into a local-library folder (classes.jar + res + assets + manifest
 * ...). Plain Java (HttpURLConnection, java.util.zip) so the exact code is tested on a desktop
 * JVM against the real repositories.
 */
public final class MavenFetcher {

    public interface Listener {
        void onStatus(String status);

        void onProgress(int percent);
    }

    public static final class Result {
        public File file;          // downloaded archive, null if not found
        public boolean isJar;
        public String url;
        public final StringBuilder log = new StringBuilder();
        public boolean networkFailed;  // every request failed without an HTTP answer
    }

    private static final String USER_AGENT = "SketchwareAI/1.6 (Android; Maven resolver)";
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    /** "implementation 'g:a:v'", "g:a:v@aar", "g : a : v" -> {g, a, v}; null if not a coordinate. */
    public static String[] parse(String dependency) {
        if (dependency == null) return null;
        String d = dependency.trim();
        d = d.replaceFirst("^(implementation|api|compile|compileOnly)\\s*\\(?", "");
        d = d.replace("'", "").replace("\"", "").replace(")", "").replace(" ", "").trim();
        int at = d.indexOf('@');
        if (at >= 0) d = d.substring(0, at);
        String[] p = d.split(":");
        if (p.length < 3 || p[0].isEmpty() || p[1].isEmpty() || p[2].isEmpty()) return null;
        return new String[]{p[0], p[1], p[2]};
    }

    public static String artifactUrl(String repo, String[] gav, String extension) {
        String base = repo.endsWith("/") ? repo.substring(0, repo.length() - 1) : repo;
        return base + "/" + gav[0].replace('.', '/') + "/" + gav[1] + "/" + gav[2] + "/"
                + gav[1] + "-" + gav[2] + "." + extension;
    }

    /**
     * Tries every repository for the .aar, then every repository for the .jar.
     *
     * @param repos {name, url} pairs, in search order
     */
    public Result fetch(String dependency, List<String[]> repos, File outDir, Listener listener) {
        Result result = new Result();
        String[] gav = parse(dependency);
        if (gav == null) {
            result.log.append("Not a Maven coordinate: ").append(dependency).append('\n');
            return result;
        }
        int attempts = 0, networkErrors = 0;
        for (String extension : new String[]{"aar", "jar"}) {
            for (int i = 0; i < repos.size(); i++) {
                if (cancelled) return result;
                String[] repo = repos.get(i);
                String url = artifactUrl(repo[1], gav, extension);
                if (listener != null) {
                    listener.onStatus("Searching ." + extension + " " + (i + 1) + "/" + repos.size() + " [" + repo[0] + "]");
                }
                attempts++;
                File target = new File(outDir, gav[1] + "_V_" + gav[2] + "." + (extension.equals("jar") ? "jar" : "zip"));
                try {
                    int code = download(url, target, listener);
                    if (code == 200) {
                        result.file = target;
                        result.isJar = extension.equals("jar");
                        result.url = url;
                        result.log.append(repo[0]).append(": found ").append(url).append('\n');
                        return result;
                    }
                    result.log.append(repo[0]).append(" .").append(extension).append(": HTTP ").append(code).append('\n');
                } catch (IOException e) {
                    networkErrors++;
                    result.log.append(repo[0]).append(" .").append(extension).append(": ")
                            .append(e.getClass().getSimpleName()).append(' ').append(e.getMessage()).append('\n');
                }
            }
        }
        result.networkFailed = attempts > 0 && networkErrors == attempts;
        return result;
    }

    /** @return HTTP status; 200 only when a real zip (PK header) was saved to {@code target}. */
    private int download(String url, File target, Listener listener) throws IOException {
        HttpURLConnection connection = null;
        for (int redirects = 0; redirects < 6; redirects++) {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "*/*");
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400 && connection.getHeaderField("Location") != null) {
                url = new URL(new URL(url), connection.getHeaderField("Location")).toString();
                connection.disconnect();
                continue;
            }
            if (code != 200) {
                connection.disconnect();
                return code;
            }
            break;
        }
        if (connection == null) return -1;
        long total = connection.getContentLength();
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File partial = new File(target.getPath() + ".part");
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new FileOutputStream(partial)) {
            byte[] buffer = new byte[16384];
            long done = 0;
            int read, lastPercent = -1;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled) throw new IOException("Cancelled");
                out.write(buffer, 0, read);
                done += read;
                if (total > 0 && listener != null) {
                    int percent = (int) (done * 100 / total);
                    if (percent != lastPercent) listener.onProgress(lastPercent = percent);
                }
            }
        } finally {
            connection.disconnect();
        }
        if (!isZip(partial)) {
            // e.g. an HTML error page served with 200: not the library.
            partial.delete();
            return 404;
        }
        target.delete();
        if (!partial.renameTo(target)) throw new IOException("Can't write " + target);
        return 200;
    }

    public static boolean isZip(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return in.read() == 'P' && in.read() == 'K';
        } catch (IOException e) {
            return false;
        }
    }

    /** Entries a Sketchware local library keeps. */
    private static boolean keep(String name) {
        return name.equals("classes.jar") || name.equals("AndroidManifest.xml") || name.equals("proguard.txt")
                || name.equals("R.txt") || name.startsWith("res/") || name.startsWith("assets/")
                || name.startsWith("jni/") || name.startsWith("libs/");
    }

    /**
     * Installs an archive into {@code libDir}:
     * <ul>
     *     <li>.aar (or a .zip laid out like one): extracted;</li>
     *     <li>.jar: becomes classes.jar;</li>
     *     <li>.zip holding a single .aar or .jar (release downloads): that file is installed;</li>
     *     <li>anything else (e.g. GitHub "Download ZIP" source code): refused with a message,
     *     since source can't be compiled into a library on the phone.</li>
     * </ul>
     *
     * @return null on success, otherwise the reason it failed
     */
    public static String install(File archive, File libDir) throws IOException {
        if (!isZip(archive)) return "Not a .aar, .jar or .zip file.";
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) names.add(e.getName());
        }
        libDir.mkdirs();
        if (names.contains("classes.jar")) {
            unzip(archive, libDir, "");
        } else if (hasClasses(names)) {
            copy(archive, new File(libDir, "classes.jar"));
        } else {
            // v1.8.11: an .aar inside wins over a .jar (a release zip often has the library's .aar plus sample jars)
            String inner = null;
            for (String n : names) {
                String lower = n.toLowerCase(Locale.ROOT);
                if ((lower.endsWith(".aar") || lower.endsWith(".jar")) && !lower.contains("sources") && !lower.contains("javadoc")
                        && !lower.contains("gradle-wrapper")) {
                    if (inner == null || lower.endsWith(".aar") && !inner.toLowerCase(Locale.ROOT).endsWith(".aar")) inner = n;
                }
            }
            if (inner == null) {
                return "This archive has no compiled library (no classes.jar, .aar or .jar inside). "
                        + "A GitHub \"Download ZIP\" is source code and can't be used directly: "
                        + "download the .aar/.jar (e.g. from JitPack or the project's Releases) instead.";
            }
            File extracted = new File(libDir, "_inner_" + new File(inner).getName());
            extractOne(archive, inner, extracted);
            String reason = install(extracted, libDir);
            extracted.delete();
            return reason;
        }
        return new File(libDir, "classes.jar").exists() ? null : "No classes.jar found in the archive.";
    }

    /**
     * v1.8.11: the jars an .aar bundles in libs/ (they are not part of the build: a local library is its classes.jar
     * and classes.dex). Merging them was tried and dropped: their classes and resources collide with the built-in
     * libraries (Gson, OkHttp, Kotlin …) in ways that only fail at the app's build. The install names them instead.
     */
    public static List<String> bundledJars(File libDir) {
        List<String> names = new ArrayList<>();
        File[] jars = new File(libDir, "libs").listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars != null) for (File jar : jars) names.add(jar.getName());
        java.util.Collections.sort(names);
        return names;
    }

    /** The warning for {@link #bundledJars}, or null when there are none. */
    public static String bundledJarsWarning(String library, File libDir) {
        List<String> jars = bundledJars(libDir);
        if (jars.isEmpty()) return null;
        return library + " bundles " + String.join(", ", jars) + " in its .aar (libs/). They aren't part "
                + "of the build: if the app needs them, add each as a library of its own (+ Add → Import file, or its "
                + "Maven coordinate). Many are copies of libraries Sketchware AI already has built in.";
    }

    private static boolean hasClasses(List<String> names) {
        for (String n : names) if (n.endsWith(".class")) return true;
        return false;
    }

    private static void unzip(File archive, File dir, String prefix) throws IOException {
        String root = dir.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry e;
            byte[] buffer = new byte[16384];
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || !keep(name)) continue;
                File out = new File(dir, name);
                if (!out.getCanonicalPath().startsWith(root)) continue; // zip-slip guard
                out.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int r;
                    while ((r = zip.read(buffer)) != -1) os.write(buffer, 0, r);
                }
            }
        }
    }

    private static void extractOne(File archive, String entry, File out) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry e;
            byte[] buffer = new byte[16384];
            while ((e = zip.getNextEntry()) != null) {
                if (!e.getName().equals(entry)) continue;
                try (OutputStream os = new FileOutputStream(out)) {
                    int r;
                    while ((r = zip.read(buffer)) != -1) os.write(buffer, 0, r);
                }
                return;
            }
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[16384];
            int r;
            while ((r = in.read(buffer)) != -1) out.write(buffer, 0, r);
        }
    }
}
