package com.sketchware.ai.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v1.9.8: project backups in the signed-in user's private repository {@value #REPO}. A backup is the project's .swb
 * (the same file Back up makes) at {@code projects/<package>.swb}; each backup is one commit, so the repository's
 * history is the backup history, and {@code index.json} lists what is there (name, package, time, size). Restoring
 * downloads one commit's .swb and restores it as a new project (never over an existing one).
 * <p>
 * Uses the Git Data API (blob, tree, commit, ref) so files up to {@link #MAX_BYTES} work and the bytes are streamed.
 * <p>
 * v2.0.3: a backup over {@link SwbParts#PART_BYTES} goes up in parts (each blob checked against its git id and sent
 * again on its own after a dropped connection, a server error or a one-off 401 while GET /user still works), with a
 * manifest ({@link SwbParts}); files a backup no longer uses (a single .swb replaced by parts, or the other way round,
 * or parts left over) are removed in the same commit. The same class writes a project's public copy
 * ({@link PublicCopy}) to its own repository.
 */
public final class CloudBackup {

    public static final String REPO = "SketchwareAI-Projects";
    /** GitHub refuses files over 100 MB; a little room is kept for the encoding. */
    public static final long MAX_BYTES = 95L * 1024 * 1024;

    private final String token;
    private final String owner;
    /** v2.0.3: the repository ({@link #REPO}, or a project's public copy). */
    private final String repo;
    private final boolean publicCopy;
    private String branch;
    private boolean headRetried;

    public CloudBackup(String token, String owner) {
        this(token, owner, REPO, false);
    }

    /** v2.0.3: the same for another repository of the account (a project's public copy: public is expected there). */
    CloudBackup(String token, String owner, String repo, boolean publicCopy) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
        this.publicCopy = publicCopy;
    }

    public String repository() {
        return repo;
    }

    /** The signed-in user's login (GET /user). */
    public static String login(String token) throws IOException, GitHubException {
        return account(token).login;
    }

    /** v1.9.9: who the token belongs to. */
    public static final class Account {
        public String login;
        /** GitHub's numeric user id (stays the same when the login is renamed). */
        public String id;
        public String avatarUrl;
        /** v2.0.0: the profile name (may be null). */
        public String name;
        /** v2.0.0: the public e-mail of the profile (may be null; {@link #primaryEmail} reads the primary one). */
        public String email;
        /** v2.0.3: public + private repositories GitHub counts for the account, or -1. */
        public int repositories = -1;
    }

    /** GET /user: login, numeric id and avatar. */
    public static Account account(String token) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/user", token, null);
        if (r.code == 401) {
            throw new GitHubException("unauthorized", "GitHub didn't accept the saved sign-in (HTTP 401" + said(r) + ").");
        }
        if (!r.ok()) throw http("Reading your GitHub account", r);
        JsonObject user = DeviceFlow.parse(r.body);
        Account a = new Account();
        a.login = DeviceFlow.string(user, "login");
        a.id = DeviceFlow.string(user, "id");
        String avatar = DeviceFlow.string(user, "avatar_url");
        a.avatarUrl = avatar != null && avatar.startsWith("https://avatars.githubusercontent.com/") ? avatar : null;
        a.name = DeviceFlow.string(user, "name");
        a.email = DeviceFlow.string(user, "email");
        int open = DeviceFlow.integer(user, "public_repos", -1);
        int closed = DeviceFlow.integer(user, "total_private_repos", -1);
        a.repositories = open < 0 ? -1 : open + Math.max(0, closed);
        if (a.login == null) throw new GitHubException("bad_answer", "GitHub's answer had no user name.");
        if (a.id == null) a.id = a.login;
        return a;
    }

    /**
     * v2.0.0: the account's primary, verified e-mail (GET /user/emails, scope {@code user:email}); null when the token
     * lacks the scope (a v1.9.x sign-in), GitHub doesn't answer, or there is none. Never throws for a missing scope.
     */
    public static String primaryEmail(String token) throws IOException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/user/emails", token, null);
        if (!r.ok()) return null;
        try {
            JsonElement parsed = com.google.gson.JsonParser.parseString(r.body);
            if (!parsed.isJsonArray()) return null;
            for (JsonElement e : parsed.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                boolean primary = o.has("primary") && o.get("primary").isJsonPrimitive() && o.get("primary").getAsBoolean();
                boolean verified = o.has("verified") && o.get("verified").isJsonPrimitive() && o.get("verified").getAsBoolean();
                String email = DeviceFlow.string(o, "email");
                if (primary && verified && email != null) return email;
            }
        } catch (RuntimeException ignored) {
            // not JSON: no e-mail
        }
        return null;
    }

    /** One entry of index.json. */
    public static final class Entry {
        public String path;
        public String name;
        public String packageName;
        public String versionName;
        public long time;
        public long size;
        /** v2.0.3: the manifest's path when the backup is in parts, else null. */
        public String parts;
    }

    /** One backup of a project (a commit that changed its .swb). */
    public static final class Version {
        public String sha;
        public String message;
        public String date;
    }

    /**
     * Creates the private repository when it isn't there ({@code auto_init}: its branch exists from the start); refuses
     * a public one with the same name. v2.0.3: a project's public copy is opened as it is (it must exist).
     */
    public void ensureRepository() throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", repoPath(""), token, null);
        if (r.code == 404 && !publicCopy) {
            JsonObject body = new JsonObject();
            body.addProperty("name", repo);
            body.addProperty("private", true);
            body.addProperty("auto_init", true);
            body.addProperty("description", "Sketchware AI project backups");
            r = GitHubHttp.api("POST", "/user/repos", token, body.toString());
            if (!r.ok()) throw http("Creating the private repository " + repo, r);
        } else if (!r.ok()) {
            throw http("Opening " + owner + "/" + repo, r);
        }
        JsonObject answer = DeviceFlow.parse(r.body);
        JsonElement isPrivate = answer.get("private");
        if (!publicCopy && (isPrivate == null || !isPrivate.getAsBoolean())) {
            throw new GitHubException("public_repo", owner + "/" + repo + " exists and is public. Backups go only to a "
                    + "private repository: make it private on github.com (Settings > Danger Zone) or rename it.");
        }
        branch = DeviceFlow.string(answer, "default_branch");
        if (branch == null) branch = "main";
    }

    /**
     * v2.0.0: opens the backup repository only if it exists (reading must never create it); false when there is none.
     * A public repository of that name is refused as in {@link #ensureRepository}.
     */
    public boolean openIfExists() throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", repoPath(""), token, null);
        if (r.code == 404) return false;
        if (!r.ok()) throw http("Opening " + owner + "/" + repo, r);
        JsonObject answer = DeviceFlow.parse(r.body);
        JsonElement isPrivate = answer.get("private");
        if (!publicCopy && (isPrivate == null || !isPrivate.getAsBoolean())) {
            throw new GitHubException("public_repo", owner + "/" + repo + " exists and is public. Backups go only to a "
                    + "private repository: make it private on github.com (Settings > Danger Zone) or rename it.");
        }
        branch = DeviceFlow.string(answer, "default_branch");
        if (branch == null) branch = "main";
        return true;
    }

    /** What index.json lists; empty when nothing was backed up yet. */
    public List<Entry> list() throws IOException, GitHubException {
        List<Entry> entries = new ArrayList<>();
        JsonObject index = readIndex();
        JsonArray projects = index.has("projects") && index.get("projects").isJsonArray() ? index.getAsJsonArray("projects") : new JsonArray();
        for (JsonElement e : projects) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            Entry entry = new Entry();
            entry.path = DeviceFlow.string(o, "path");
            entry.name = DeviceFlow.string(o, "name");
            entry.packageName = DeviceFlow.string(o, "package");
            entry.versionName = DeviceFlow.string(o, "versionName");
            entry.time = o.has("time") ? o.get("time").getAsLong() : 0;
            entry.size = o.has("size") ? o.get("size").getAsLong() : 0;
            entry.parts = DeviceFlow.string(o, "parts");
            if (entry.parts != null && !entry.parts.equals(SwbParts.manifestPath(String.valueOf(entry.path)))) entry.parts = null;
            if (entry.path != null && entry.path.startsWith("projects/") && entry.path.endsWith(".swb")) entries.add(entry);
        }
        return entries;
    }

    /**
     * The path a project's backup goes to: {@code projects/<package>/<name>.swb}. Many projects keep the default
     * package name, so the name is part of the path too.
     */
    public static String pathFor(String packageName, String projectName) {
        String pkg = packageName != null && packageName.matches("[A-Za-z0-9_.]+") && !packageName.startsWith(".")
                ? packageName : "no-package";
        String name = (projectName == null ? "" : projectName).replaceAll("[^A-Za-z0-9_.-]", "_").replaceAll("^[.]+", "");
        if (name.isEmpty()) name = "project";
        if (name.length() > 80) name = name.substring(0, 80);
        return "projects/" + pkg + "/" + name + ".swb";
    }

    /**
     * Uploads {@code swb} as the newest backup of the project and updates index.json, in one commit. Retries once
     * when another phone moved the branch in between.
     */
    public String upload(File swb, String projectName, String packageName, String versionName, String appVersion)
            throws IOException, GitHubException {
        return upload(swb, projectName, packageName, versionName, appVersion, null);
    }

    /** v2.0.2: what an upload reports while it runs (on the worker thread). */
    public interface UploadProgress {
        /** v2.0.3: the backup's bytes sent so far, of {@code total} (all parts together). */
        void uploading(long sent, long total);

        /** The file is on GitHub; the commit and index.json are being written. */
        void saving();

        /** v2.0.3: a part is sent again (what happened, which try). */
        default void retrying(String text) {
        }
    }

    /** v2.0.2: the path the last {@link #upload} wrote, and the link of the file on github.com. */
    String lastPath;

    public String link(String path) {
        return "https://github.com/" + owner + "/" + repo + "/blob/" + (branch == null ? "main" : branch) + "/"
                + encPath(path);
    }

    /** v2.0.3: tries of one blob (the first and two more), and the pauses between them; tests make them short. */
    static long[] blobPauses = {2000, 6000};

    public String upload(File swb, String projectName, String packageName, String versionName, String appVersion,
                         UploadProgress progress) throws IOException, GitHubException {
        if (!swb.isFile()) throw new GitHubException("no_file", "The backup file wasn't made.");
        if (swb.length() > MAX_BYTES) {
            throw new GitHubException("too_large", "The backup is " + ((swb.length() + (1 << 20) - 1) >> 20) + " MB; cloud "
                    + "backup takes files up to 95 MB. Back it up without local libraries, or keep it on the phone.");
        }
        if (branch == null) ensureRepository();
        String path = pathFor(packageName, projectName);
        String manifestPath = SwbParts.manifestPath(path);
        long size = swb.length();
        int count = SwbParts.count(size);
        boolean inParts = size > SwbParts.PART_BYTES;
        String blobSha = null;
        // review (v2.0.3, M7): every backup has its parts list, a single .swb too (one part, the file itself), and the
        // share link names the list: the link stays the same when a project grows past 4 MiB or shrinks again
        SwbParts.Manifest manifest = new SwbParts.Manifest();
        manifest.name = new File(path).getName();
        manifest.size = size;
        manifest.sha256 = SwbParts.sha256(swb);
        if (!inParts) {
            blobSha = uploadBlob(swb, 0, size, 0, size, progress, "the backup");
            manifest.parts.add(new SwbParts.Part(path, size, blobSha));
        } else {
            long done = 0;
            for (int i = 0; i < count; i++) {
                long offset = i * SwbParts.PART_BYTES;
                long length = Math.min(SwbParts.PART_BYTES, size - offset);
                String sha = uploadBlob(swb, offset, length, done, size, progress, "part " + (i + 1) + " of " + count);
                manifest.parts.add(new SwbParts.Part(SwbParts.partPath(path, i), length, sha));
                done += length;
            }
        }
        if (progress != null) progress.saving();
        for (int attempt = 0; ; attempt++) {
            String head = headCommit();
            String baseTree = treeOf(head);
            JsonObject index = readIndex();
            Entry entry = new Entry();
            entry.path = path;
            entry.name = projectName;
            entry.packageName = packageName;
            entry.versionName = versionName;
            entry.time = System.currentTimeMillis();
            entry.size = size;
            entry.parts = manifestPath;
            String indexJson = withEntry(index, entry);

            JsonArray tree = new JsonArray();
            java.util.Set<String> keep = new java.util.HashSet<>();
            for (SwbParts.Part part : manifest.parts) {
                tree.add(treeItem(part.path, part.sha, null));
                keep.add(part.path);
            }
            tree.add(treeItem(manifestPath, null, manifest.toJson()));
            keep.add(manifestPath);
            // what this backup no longer uses: the single file, the manifest, parts past the new count
            for (String old : backupFiles(baseTree, path)) {
                if (!keep.contains(old)) tree.add(deletion(old));
            }
            tree.add(treeItem("index.json", null, indexJson));
            JsonObject treeBody = new JsonObject();
            treeBody.addProperty("base_tree", baseTree);
            treeBody.add("tree", tree);
            GitHubHttp.Response t = GitHubHttp.api("POST", repoPath("/git/trees"), token, treeBody.toString());
            if (!t.ok()) throw http("Writing the backup", t);
            String newTree = DeviceFlow.string(DeviceFlow.parse(t.body), "sha");

            JsonObject commitBody = new JsonObject();
            commitBody.addProperty("message", "Backup of " + projectName + (versionName == null ? "" : " v" + versionName)
                    + " (" + (packageName == null ? "?" : packageName) + ") from Sketchware AI " + appVersion
                    + (inParts ? " in " + count + " parts" : ""));
            commitBody.addProperty("tree", newTree);
            JsonArray parents = new JsonArray();
            parents.add(head);
            commitBody.add("parents", parents);
            GitHubHttp.Response c = GitHubHttp.api("POST", repoPath("/git/commits"), token, commitBody.toString());
            if (!c.ok()) throw http("Writing the backup", c);
            String commit = DeviceFlow.string(DeviceFlow.parse(c.body), "sha");

            JsonObject refBody = new JsonObject();
            refBody.addProperty("sha", commit);
            refBody.addProperty("force", false);
            GitHubHttp.Response ref = GitHubHttp.api("PATCH", repoPath("/git/refs/heads/" + branch), token, refBody.toString());
            if (ref.ok()) {
                lastPath = manifestPath;
                return commit;
            }
            if (ref.code == 422 && attempt < 2) continue; // the branch moved (another phone): again on top of it
            throw http("Saving the backup", ref);
        }
    }

    /**
     * v2.0.3: one blob ({@code length} bytes of {@code file} from {@code offset}), checked against its git id. A blob
     * is named by its content, so sending it again is harmless: after a dropped connection, a server error, a short
     * rate-limit wait, or a 401 while GET /user still accepts the sign-in (a one-off refusal), up to three tries.
     */
    private String uploadBlob(File file, long offset, long length, long before, long total, UploadProgress progress,
                              String what) throws IOException, GitHubException {
        String expected = SwbParts.gitBlobSha(file, offset, length);
        int tries = blobPauses.length + 1;
        for (int attempt = 1; ; attempt++) {
            GitHubHttp.Response r;
            try {
                r = GitHubHttp.postBlob(repoPath("/git/blobs"), token, file, offset, length, progress == null ? null
                        : (sent, all) -> progress.uploading(before + Math.min(length, sent * length / Math.max(1, all)), total));
            } catch (IOException e) {
                if (attempt >= tries || !GitHubHttp.worthRetry(e)) throw e;
                retry(progress, "Sending " + what + " again: the network dropped it (try " + (attempt + 1) + " of "
                        + tries + ")…", attempt);
                continue;
            }
            if (r.ok()) {
                String sha = DeviceFlow.string(DeviceFlow.parse(r.body), "sha");
                if (sha == null || !sha.equals(expected)) {
                    throw new GitHubException("bad_answer", "GitHub stored " + what + " with another checksum ("
                            + sha + " instead of " + expected + "): try again.");
                }
                return sha;
            }
            boolean again = attempt < tries;
            if (again && (r.code >= 500 || r.code == 408)) {
                retry(progress, "Sending " + what + " again: GitHub answered HTTP " + r.code + " (try " + (attempt + 1)
                        + " of " + tries + ")…", attempt);
                continue;
            }
            if (again && r.code == 401 && signInWorks()) {
                retry(progress, "Sending " + what + " again: GitHub refused it once (HTTP 401" + said(r)
                        + ") but accepts the sign-in (try " + (attempt + 1) + " of " + tries + ")…", attempt);
                continue;
            }
            throw http("Uploading " + what, r);
        }
    }

    private static void retry(UploadProgress progress, String text, int attempt) throws IOException {
        if (progress != null) progress.retrying(text);
        try {
            Thread.sleep(blobPauses[Math.min(attempt, blobPauses.length) - 1]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("Stopped while waiting to send again");
        }
    }

    /** v2.0.3: GET /user answers 200 with this token (a 401 elsewhere was a one-off); false on 401. */
    public boolean signInWorks() throws IOException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/user", token, null);
        return r.ok();
    }

    /**
     * v2.0.3: the files of one backup in a tree: its .swb, its manifest and its parts (what a new backup of it may
     * replace or remove). Empty when GitHub truncates the listing (a huge repository): nothing is removed then.
     */
    private List<String> backupFiles(String tree, String path) throws IOException, GitHubException {
        List<String> found = new ArrayList<>();
        GitHubHttp.Response r = GitHubHttp.api("GET", repoPath("/git/trees/" + enc(tree) + "?recursive=1"), token, null);
        if (!r.ok()) throw http("Reading the repository", r);
        JsonObject answer = DeviceFlow.parse(r.body);
        if (answer.has("truncated") && answer.get("truncated").isJsonPrimitive() && answer.get("truncated").getAsBoolean()) {
            return found;
        }
        JsonElement entries = answer.get("tree");
        if (entries == null || !entries.isJsonArray()) return found;
        String manifest = SwbParts.manifestPath(path);
        String partsFolder = path + ".parts/";
        for (JsonElement e : entries.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String p = DeviceFlow.string(o, "path");
            if (p == null || !"blob".equals(DeviceFlow.string(o, "type"))) continue;
            if (p.equals(path) || p.equals(manifest) || p.startsWith(partsFolder)) found.add(p);
        }
        return found;
    }

    /** The backups of one project, newest first (commits that changed its file or, v2.0.3, its parts list). */
    public List<Version> history(String path) throws IOException, GitHubException {
        if (branch == null) ensureRepository();
        List<Version> versions = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String p : new String[]{path, SwbParts.manifestPath(path)}) {
            GitHubHttp.Response r = GitHubHttp.api("GET", repoPath("/commits?sha=" + enc(branch) + "&path=" + enc(p)
                    + "&per_page=100"), token, null);
            if (!r.ok()) throw http("Reading the backup history", r);
            try {
                for (JsonElement e : com.google.gson.JsonParser.parseString(r.body).getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    Version v = new Version();
                    v.sha = DeviceFlow.string(o, "sha");
                    JsonObject commit = o.getAsJsonObject("commit");
                    String message = commit == null ? null : DeviceFlow.string(commit, "message");
                    v.message = message == null ? "" : message;
                    JsonObject author = commit == null || !commit.has("author") || !commit.get("author").isJsonObject()
                            ? null : commit.getAsJsonObject("author");
                    String date = author == null ? null : DeviceFlow.string(author, "date");
                    v.date = date == null ? "" : date;
                    if (v.sha != null && seen.add(v.sha)) versions.add(v);
                }
            } catch (RuntimeException e) {
                throw new GitHubException("bad_answer", "GitHub's answer for the backup history couldn't be read.");
            }
        }
        // newest first (ISO dates sort as text)
        java.util.Collections.sort(versions, (x, y) -> y.date.compareTo(x.date));
        return versions;
    }

    /** Downloads the .swb of {@code path} as it was in commit {@code sha}. */
    public void download(String path, String sha, File target) throws IOException, GitHubException {
        download(path, sha, target, null);
    }

    /**
     * v2.0.2: the same with the bytes received reported. v2.0.3: a backup in parts (its manifest is in that commit) is
     * downloaded part by part, joined and checked.
     */
    public void download(String path, String sha, File target, GitHubHttp.Progress progress)
            throws IOException, GitHubException {
        GitHubHttp.Response m = GitHubHttp.apiRaw(repoPath("/contents/" + encPath(SwbParts.manifestPath(path)) + "?ref="
                + enc(sha)), token);
        if (m.ok()) {
            SwbParts.download(token, repoPath(""), SwbParts.Manifest.parse(m.body), target, progress);
            return;
        }
        if (m.code != 404) throw http("Reading the backup's parts list", m);
        GitHubHttp.Response r = GitHubHttp.download(repoPath("/contents/" + encPath(path) + "?ref=" + enc(sha)), token,
                target, MAX_BYTES + (5 << 20), progress);
        if (r.rateLimited()) throw http("Downloading the backup", r);
        if (r.code < 200 || r.code >= 300) {
            throw new GitHubException("http_" + r.code, "Downloading the backup failed (HTTP " + r.code + ").");
        }
    }

    // ------------------------------------------------------------------ helpers

    private String headCommit() throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", repoPath("/git/ref/heads/" + branch), token, null);
        if ((r.code == 404 || r.code == 409) && !headRetried) {
            // a repository created a moment ago can answer before its first commit is readable
            headRetried = true;
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            r = GitHubHttp.api("GET", repoPath("/git/ref/heads/" + branch), token, null);
        }
        if (!r.ok()) throw http("Reading the repository", r);
        JsonObject object = DeviceFlow.parse(r.body).getAsJsonObject("object");
        String sha = object == null ? null : DeviceFlow.string(object, "sha");
        if (sha == null) throw new GitHubException("bad_answer", "The repository's branch couldn't be read.");
        return sha;
    }

    private String treeOf(String commit) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", repoPath("/git/commits/" + commit), token, null);
        if (!r.ok()) throw http("Reading the repository", r);
        JsonObject tree = DeviceFlow.parse(r.body).getAsJsonObject("tree");
        String sha = tree == null ? null : DeviceFlow.string(tree, "sha");
        if (sha == null) throw new GitHubException("bad_answer", "The repository's files couldn't be read.");
        return sha;
    }

    private JsonObject readIndex() throws IOException, GitHubException {
        if (branch == null) ensureRepository();
        // the raw file (the JSON answer leaves "content" empty for files over 1 MB: the list would read as empty and
        // the next backup would write it again with one project)
        GitHubHttp.Response r = GitHubHttp.apiRaw(repoPath("/contents/index.json?ref=" + enc(branch)), token);
        if (r.code == 404) return new JsonObject();
        if (!r.ok()) throw http("Reading the backup list", r);
        try {
            JsonElement e = com.google.gson.JsonParser.parseString(r.body);
            if (!e.isJsonObject()) throw new IllegalStateException("not an object");
            return e.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new GitHubException("bad_index", "index.json in " + owner + "/" + repo + " can't be read; fix or delete "
                    + "it on github.com (the backups themselves are in projects/).");
        }
    }

    static String withEntry(JsonObject index, Entry entry) {
        JsonArray old = index.has("projects") && index.get("projects").isJsonArray() ? index.getAsJsonArray("projects") : new JsonArray();
        JsonArray projects = new JsonArray();
        for (JsonElement e : old) {
            if (e.isJsonObject() && entry.path.equals(DeviceFlow.string(e.getAsJsonObject(), "path"))) continue;
            projects.add(e);
        }
        JsonObject o = new JsonObject();
        o.addProperty("path", entry.path);
        o.addProperty("name", entry.name);
        o.addProperty("package", entry.packageName);
        o.addProperty("versionName", entry.versionName);
        o.addProperty("time", entry.time);
        o.addProperty("size", entry.size);
        if (entry.parts != null) o.addProperty("parts", entry.parts);
        projects.add(o);
        JsonObject out = new JsonObject();
        out.addProperty("format", 1);
        out.addProperty("app", "Sketchware AI");
        out.add("projects", projects);
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out) + "\n";
    }

    /** v2.0.3: a tree entry that removes {@code path} (its sha null). */
    private static JsonObject deletion(String path) {
        JsonObject o = new JsonObject();
        o.addProperty("path", path);
        o.addProperty("mode", "100644");
        o.addProperty("type", "blob");
        o.add("sha", com.google.gson.JsonNull.INSTANCE);
        return o;
    }

    /** v2.0.3: ": <GitHub's message>, request <id>" for an error line (empty parts left out). */
    static String said(GitHubHttp.Response r) {
        String message = r.body == null ? null : DeviceFlow.string(DeviceFlow.parse(r.body), "message");
        return (message == null ? "" : ": " + message) + (r.requestId == null ? "" : ", request " + r.requestId);
    }

    private static JsonObject treeItem(String path, String sha, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("path", path);
        o.addProperty("mode", "100644");
        o.addProperty("type", "blob");
        if (sha != null) o.addProperty("sha", sha);
        else o.addProperty("content", content);
        return o;
    }

    private String repoPath(String rest) {
        return "/repos/" + enc(owner) + "/" + enc(repo) + rest;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String encPath(String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("/")) {
            if (out.length() > 0) out.append('/');
            out.append(enc(part));
        }
        return out.toString();
    }

    static GitHubException http(String what, GitHubHttp.Response r) {
        String message = DeviceFlow.string(DeviceFlow.parse(r.body), "message");
        if (r.rateLimited()) {
            // v2.0.1: GitHub's rate limit is not a permission problem: say when it is over
            String when = r.waitMillis <= 0 ? "in a minute" : "after " + new java.text.SimpleDateFormat("HH:mm",
                    Locale.ENGLISH).format(new java.util.Date(System.currentTimeMillis() + r.waitMillis + 59_999L));
            if (r.anonymous) {
                return new GitHubException("rate_limited", what + " has to wait: GitHub's limit for requests without "
                        + "sign-in (shared by everyone on this network) is used up. Sign in, or try again " + when + ".");
            }
            return new GitHubException("rate_limited", what + " has to wait: GitHub's request limit is used up for "
                    + "now. Nothing is lost; try again " + when + ".");
        }
        String text;
        if (r.code == 401) text = what + " failed: GitHub didn't accept the saved sign-in (HTTP 401" + said(r) + ").";
        else if (r.code == 403 || r.code == 404) text = what + " failed (HTTP " + r.code + (message == null ? "" : ": " + message)
                + "). The sign-in may lack repository access: sign out and in again.";
        else text = what + " failed (HTTP " + r.code + said(r) + ").";
        return new GitHubException("http_" + r.code, text);
    }

    /** "12 Nov 2026, 14:05" for an entry's time. */
    public static String formatTime(long time) {
        return new java.text.SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).format(new java.util.Date(time));
    }
}
