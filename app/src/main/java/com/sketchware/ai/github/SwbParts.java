package com.sketchware.ai.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v2.0.3 (owner's 15 Nov phone test: a large project's "Back up to GitHub" failed at 90%): a backup over
 * {@link #PART_BYTES} goes to GitHub in parts, each its own blob (Git Data API) that is checked and can be sent again on
 * its own, plus a small manifest: {@code <backup>.swb.parts.json} lists the parts ({@code <backup>.swb.parts/000} …)
 * with their sizes and git blob ids, and the whole file's size and SHA-256. Smaller backups stay one {@code .swb}
 * file, as before. Restoring (own backups and links to any repository) downloads the parts by their blob ids, joins
 * them and checks size and SHA-256 before anything is restored.
 */
public final class SwbParts {

    /** Parts of 4 MiB: about 5.6 MB of JSON per request, a few seconds each on mobile data. */
    public static final long PART_BYTES = 4L * 1024 * 1024;
    public static final String MANIFEST_SUFFIX = ".parts.json";
    static final String KIND = "sketchware-ai-swb-parts";

    private SwbParts() {
    }

    /** One part: where it is in the repository, its size and its git blob id. */
    public static final class Part {
        public String path;
        public long size;
        public String sha;

        Part(String path, long size, String sha) {
            this.path = path;
            this.size = size;
            this.sha = sha;
        }
    }

    /** What the manifest says. */
    public static final class Manifest {
        public String name;
        public long size;
        public String sha256;
        public long partBytes = PART_BYTES;
        public final List<Part> parts = new ArrayList<>();

        public String toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("kind", KIND);
            o.addProperty("format", 1);
            o.addProperty("name", name);
            o.addProperty("size", size);
            o.addProperty("sha256", sha256);
            o.addProperty("partBytes", partBytes);
            JsonArray list = new JsonArray();
            for (Part p : parts) {
                JsonObject e = new JsonObject();
                e.addProperty("path", p.path);
                e.addProperty("size", p.size);
                e.addProperty("sha", p.sha);
                list.add(e);
            }
            o.add("parts", list);
            return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
        }

        /** The manifest in {@code json}; a {@link GitHubException} when it isn't one this app wrote. */
        public static Manifest parse(String json) throws GitHubException {
            return parse(json, CloudBackup.MAX_BYTES);
        }

        /** v2.0.4: the same for a release's parts list (a larger backup allowed). */
        public static Manifest parse(String json, long maxTotal) throws GitHubException {
            try {
                JsonElement e = com.google.gson.JsonParser.parseString(json);
                if (!e.isJsonObject()) throw new IllegalStateException("not an object");
                JsonObject o = e.getAsJsonObject();
                if (!KIND.equals(DeviceFlow.string(o, "kind"))) throw new IllegalStateException("not a parts manifest");
                Manifest m = new Manifest();
                m.name = DeviceFlow.string(o, "name");
                m.size = o.get("size").getAsLong();
                m.sha256 = DeviceFlow.string(o, "sha256");
                long total = 0;
                // review: a list from a link can't make the phone fetch thousands of empty parts
                long most = m.size / (1024 * 1024) + 1;
                for (JsonElement pe : o.getAsJsonArray("parts")) {
                    JsonObject po = pe.getAsJsonObject();
                    String sha = DeviceFlow.string(po, "sha");
                    long size = po.get("size").getAsLong();
                    if (sha == null || !sha.matches("[0-9a-f]{40}") || size < 0 || size > 16L * 1024 * 1024
                            || (size == 0 && m.size > 0)) {
                        throw new IllegalStateException("bad part");
                    }
                    if (m.parts.size() >= most) throw new IllegalStateException("too many parts");
                    m.parts.add(new Part(DeviceFlow.string(po, "path"), size, sha));
                    total += size;
                }
                if (m.parts.isEmpty() || total != m.size || m.size > maxTotal
                        || m.sha256 == null || !m.sha256.matches("[0-9a-f]{64}")) {
                    throw new IllegalStateException("sizes don't add up");
                }
                return m;
            } catch (RuntimeException e) {
                throw new GitHubException("bad_manifest", "The backup's parts list can't be read (" + e.getMessage() + ").");
            }
        }
    }

    /** How many parts a backup of {@code size} bytes has (1 = one .swb, no parts). */
    public static int count(long size) {
        return (int) Math.max(1, (size + PART_BYTES - 1) / PART_BYTES);
    }

    public static String manifestPath(String swbPath) {
        return swbPath + MANIFEST_SUFFIX;
    }

    public static String partPath(String swbPath, int index) {
        return swbPath + ".parts/" + String.format(Locale.ENGLISH, "%03d", index);
    }

    /** True for a repository path of a manifest. */
    public static boolean isManifest(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".swb" + MANIFEST_SUFFIX);
    }

    /** Second review: the .swb path a manifest path stands for ("a/My_App.swb" for "a/My_App.swb.parts.json"). */
    public static String displayPath(String path) {
        return isManifest(path) ? path.substring(0, path.length() - MANIFEST_SUFFIX.length()) : path;
    }

    /** The backup's own file name for a .swb path or a manifest path ("My_App.swb"). */
    public static String displayName(String path) {
        String name = new File(path).getName();
        return isManifest(name) ? name.substring(0, name.length() - MANIFEST_SUFFIX.length()) : name;
    }

    /** Git's id for {@code length} bytes of {@code file} from {@code offset}: SHA-1 of "blob N\0" and the bytes. */
    public static String gitBlobSha(File file, long offset, long length) throws IOException {
        MessageDigest sha1 = digest("SHA-1");
        sha1.update(("blob " + length + "\0").getBytes(StandardCharsets.US_ASCII));
        try (InputStream in = new FileInputStream(file)) {
            long skip = offset;
            while (skip > 0) {
                long n = in.skip(skip);
                if (n <= 0) throw new java.io.EOFException("The file is shorter than expected");
                skip -= n;
            }
            update(sha1, in, length);
        }
        return hex(sha1.digest());
    }

    public static String sha256(File file) throws IOException {
        MessageDigest sha = digest("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            update(sha, in, Long.MAX_VALUE);
        }
        return hex(sha.digest());
    }

    /**
     * Downloads the parts of {@code m} from the repository at {@code repoPath} ("/repos/owner/name") by their blob ids,
     * joins them into {@code target} and checks every size and the SHA-256; on any failure nothing is left behind.
     */
    static void download(String token, String repoPath, Manifest m, File target, GitHubHttp.Progress progress)
            throws IOException, GitHubException {
        File joined = new File(target.getAbsolutePath() + ".joining");
        File piece = new File(target.getAbsolutePath() + ".piece");
        long before = 0;
        try {
            try (OutputStream out = new FileOutputStream(joined)) {
                int index = 0;
                for (Part p : m.parts) {
                    index++;
                    long done = before;
                    //noinspection ResultOfMethodCallIgnored
                    piece.delete();
                    // second review: room for GitHub's JSON answer (base64: 4/3 of the size) when it doesn't send raw
                    GitHubHttp.Response r = GitHubHttp.download(repoPath + "/git/blobs/" + p.sha, token, piece,
                            p.size * 4 / 3 + 64 * 1024, progress == null ? null
                                    : (got, all) -> progress.bytes(done + Math.min(got, p.size), m.size));
                    if (r.rateLimited()) throw CloudBackup.http("Downloading part " + index + " of " + m.parts.size(), r);
                    if (r.code < 200 || r.code >= 300) {
                        throw new GitHubException("http_" + r.code, "Downloading part " + index + " of " + m.parts.size()
                                + " failed (HTTP " + r.code + ").");
                    }
                    if (piece.length() != p.size) unwrapJson(piece);
                    if (piece.length() != p.size) {
                        throw new GitHubException("bad_part", "Part " + index + " of " + m.parts.size() + " has "
                                + piece.length() + " bytes instead of " + p.size + ".");
                    }
                    try (InputStream in = new FileInputStream(piece)) {
                        byte[] buffer = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    }
                    before += p.size;
                }
            }
            if (joined.length() != m.size || !m.sha256.equals(sha256(joined))) {
                throw new GitHubException("bad_backup", "The joined backup doesn't match its checksum: try again.");
            }
            if (target.exists() && !target.delete()) throw new IOException("Can't replace " + target);
            if (!joined.renameTo(target)) throw new IOException("Couldn't save " + target);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            piece.delete();
            //noinspection ResultOfMethodCallIgnored
            joined.delete();
        }
    }

    /**
     * Review (L10): GitHub answers a blob with its JSON (base64 content) when it doesn't take the raw media type: the
     * content is decoded in place; anything else is left for the size check.
     */
    private static void unwrapJson(File piece) throws IOException {
        if (piece.length() > 24L * 1024 * 1024) return;
        byte[] bytes = java.nio.file.Files.readAllBytes(piece.toPath());
        if (bytes.length == 0 || bytes[0] != '{') return;
        try {
            JsonObject o = com.google.gson.JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"base64".equals(DeviceFlow.string(o, "encoding"))) return;
            String content = DeviceFlow.string(o, "content");
            if (content == null) return;
            byte[] data = android.util.Base64.decode(content, android.util.Base64.DEFAULT);
            java.nio.file.Files.write(piece.toPath(), data);
        } catch (RuntimeException ignored) {
            // not GitHub's JSON: the size check says so
        }
    }

    private static void update(MessageDigest digest, InputStream in, long length) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long left = length;
        while (left > 0) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (n < 0) {
                if (length == Long.MAX_VALUE) break;
                throw new java.io.EOFException("The file is shorter than expected");
            }
            digest.update(buffer, 0, n);
            left -= n;
        }
    }

    private static MessageDigest digest(String name) {
        try {
            return MessageDigest.getInstance(name);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return out.toString();
    }
}
