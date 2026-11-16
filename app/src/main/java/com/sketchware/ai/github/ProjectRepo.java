package com.sketchware.ai.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v2.0.4 (owner's 16 Nov directive "one dedicated repository per project, versioned releases and assets, 5 versions with
 * a Standard pin, instant Public / Private"): every project backed up to GitHub has its own repository
 * {@code SketchwareAI-<name>} ({@code -2} … when the name is taken), created private. Each backup is a <b>release</b>
 * tagged with the app's version ({@code v<versionName>-<versionCode>}): the project's .swb as a release asset (16 MiB
 * parts plus a parts list for larger ones), the last built APK when there is one, and the release notes the on-device
 * analyzer wrote ({@link com.sketchware.ai.project.ProjectDiffAnalyzer}). A backup of the same app version updates its
 * release (the new files first, then the old ones are removed). At most {@link #MAX_VERSIONS} versions are kept: the
 * oldest that isn't the pinned Standard one is deleted (release and tag). Public / Private is one PATCH of the
 * repository; nothing is uploaded again.
 * <p>
 * A repository is known by its number (GitHub's id: it stays through a rename and is never given to another one), kept
 * in the project's record ({@link ProjectLinks}); the backup repository {@link CloudBackup#REPO} is never used for this
 * and never changed (old backups there are only read, for import).
 * <p>
 * Facts per release are a hidden line at the end of its notes ({@code <!-- sketchware-ai {...} -->}): the backup's
 * time, file, size and SHA-256 and whether it is the Standard version, so nothing else has to be stored on GitHub.
 */
public final class ProjectRepo {

    public static final String PREFIX = "SketchwareAI-";
    /** The start of a project repository's description (how the import screen tells them from other repositories). */
    public static final String MARK = "Sketchware AI project";
    /**
     * The topic every project repository keeps: discovery on another phone finds the repository by it even after its
     * description was rewritten for publishing (the description's {@link #MARK} is only there until then).
     */
    public static final String TOPIC = "sketchware-ai";
    public static final int MAX_VERSIONS = 5;
    /** Release assets up to this size go up in one request; larger backups in parts of this size. */
    /** v2.0.5: 8 MiB (16 MiB in v2.0.4): a retry resumes from the last finished part, so smaller parts lose less. */
    public static final long PART_BYTES = 8L * 1024 * 1024;
    /** What a backup may weigh (release assets go up to 2 GB each; a phone's patience is the real limit). */
    public static final long MAX_BYTES = 1024L * 1024 * 1024;
    static final String MARKER_START = "<!-- sketchware-ai ";
    static final String MARKER_END = " -->";

    final String token;
    final String login;
    String name;
    long number;
    boolean isPrivate = true;
    String branch = "main";

    ProjectRepo(String token, String login, String name, long number) {
        this.token = token;
        this.login = login;
        this.name = name;
        this.number = number;
    }

    public String name() {
        return name;
    }

    public long number() {
        return number;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    /** The repository's page (the share link). */
    public String link() {
        return link(login, name);
    }

    public static String link(String login, String repo) {
        return "https://github.com/" + login + "/" + repo;
    }

    // ------------------------------------------------------------------ finding and creating

    /** "SketchwareAI-My_App" for a project name (GitHub's name characters; never the backup repository's name). */
    public static String repositoryName(String projectName) {
        String n = (projectName == null ? "" : projectName).trim().replaceAll("[^A-Za-z0-9_.-]", "_")
                .replaceAll("^[._-]+", "");
        if (n.isEmpty()) n = "Project";
        if (n.length() > 80) n = n.substring(0, 80);
        String repo = PREFIX + n;
        // a project called "Projects" must not land in (or be confused with) the backup repository
        if (repo.equalsIgnoreCase(CloudBackup.REPO) || n.toLowerCase(Locale.ROOT).startsWith("public-")) repo += "-App";
        return repo;
    }

    /**
     * The repository by its number (renames followed), or null when it is gone, isn't this account's, or is the backup
     * repository.
     */
    static ProjectRepo byNumber(String token, String login, long number) throws IOException, GitHubException {
        if (number <= 0) return null;
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repositories/" + number, token, null);
        if (r.code == 404) return null;
        if (!r.ok()) throw CloudBackup.http("Opening the project's repository", r);
        JsonObject a = DeviceFlow.parse(r.body);
        String name = DeviceFlow.string(a, "name");
        JsonObject owner = a.has("owner") && a.get("owner").isJsonObject() ? a.getAsJsonObject("owner") : null;
        String ownerLogin = owner == null ? null : DeviceFlow.string(owner, "login");
        if (name == null || name.equalsIgnoreCase(CloudBackup.REPO) || ownerLogin == null
                || !ownerLogin.equalsIgnoreCase(login) || number(a) != number) {
            return null;
        }
        ProjectRepo repo = new ProjectRepo(token, login, name, number);
        repo.read(a);
        return repo;
    }

    /**
     * review L1: whether the repository of that number is gone from GitHub (404), as opposed to moved to another owner
     * (it still answers). No number: nothing on GitHub.
     */
    static boolean gone(String token, long number) throws IOException, GitHubException {
        if (number <= 0) return true;
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repositories/" + number, token, null);
        if (r.code == 404) return true;
        if (!r.ok()) throw CloudBackup.http("Opening the project's repository", r);
        return false;
    }

    /** The project's repository as its record names it (for this account), or null. */
    static ProjectRepo open(String token, String login, String scId) throws IOException, GitHubException {
        return byNumber(token, login, ProjectLinks.repoNumber(scId, login));
    }

    /**
     * The project's repository: the recorded one, else a new private one ({@code SketchwareAI-<name>}, the next free
     * name). The record is written at once (a backup cut off later finds the same repository again).
     */
    static ProjectRepo ensure(String token, String login, String scId, String projectName, String pkg)
            throws IOException, GitHubException {
        ProjectRepo found = open(token, login, scId);
        if (found != null) {
            if (!found.name.equals(ProjectLinks.repoName(scId, login))) {
                ProjectLinks.repoFound(scId, login, found.name, found.number, found.isPrivate);
            }
            return found;
        }
        String base = repositoryName(projectName);
        for (int i = 1; i <= 20; i++) {
            String candidate = i == 1 ? base : base + "-" + i;
            GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(login) + "/"
                    + CloudBackup.enc(candidate), token, null);
            if (r.code == 404) {
                ProjectRepo made = create(token, login, candidate, projectName, pkg);
                ProjectLinks.repoFound(scId, login, made.name, made.number, true);
                return made;
            }
            // taken (another project, the user's own repository, or a renamed repository's old name): the next one
            if (r.ok() || (r.code >= 300 && r.code < 400)) continue;
            throw CloudBackup.http("Looking for a free repository name", r);
        }
        throw new GitHubException("no_name", "Repositories " + base + " to " + base + "-20 exist already: rename the "
                + "project.");
    }

    private static ProjectRepo create(String token, String login, String repo, String projectName, String pkg)
            throws IOException, GitHubException {
        JsonObject body = new JsonObject();
        body.addProperty("name", repo);
        body.addProperty("private", true);
        body.addProperty("auto_init", true);
        body.addProperty("has_issues", false);
        body.addProperty("has_wiki", false);
        body.addProperty("has_projects", false);
        body.addProperty("description", defaultDescription(projectName, pkg));
        GitHubHttp.Response r = GitHubHttp.api("POST", "/user/repos", token, body.toString());
        if (!r.ok()) throw CloudBackup.http("Creating the repository " + repo, r);
        JsonObject a = DeviceFlow.parse(r.body);
        ProjectRepo made = new ProjectRepo(token, login, repo, number(a));
        made.read(a);
        made.isPrivate = true;
        try {
            made.setDetails(null, new ArrayList<>());
        } catch (IOException | GitHubException ignored) {
            // the description's mark finds it until the topic is written (the next publish writes it again)
        }
        return made;
    }

    static String defaultDescription(String projectName, String pkg) {
        return MARK + ": " + (projectName == null ? "" : projectName) + (pkg == null || pkg.isEmpty() ? "" : " (" + pkg + ")")
                + ". Versions are this repository's releases.";
    }

    private void read(JsonObject a) {
        String n = DeviceFlow.string(a, "name");
        if (n != null) name = n;
        long id = number(a);
        if (id > 0) number = id;
        isPrivate = !a.has("private") || !a.get("private").isJsonPrimitive() || a.get("private").getAsBoolean();
        String b = DeviceFlow.string(a, "default_branch");
        if (b != null) branch = b;
    }

    static long number(JsonObject a) {
        try {
            return a.has("id") && a.get("id").isJsonPrimitive() ? a.get("id").getAsLong() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private String path(String rest) {
        return "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(name) + rest;
    }

    // ------------------------------------------------------------------ visibility and details

    /** Public / Private: one PATCH (nothing uploaded). The backup repository is refused by name. */
    void setPrivate(boolean hidden) throws IOException, GitHubException {
        if (name.equalsIgnoreCase(CloudBackup.REPO)) {
            throw new GitHubException("not_a_project", "That is the backup repository: nothing was changed.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("private", hidden);
        GitHubHttp.Response r = GitHubHttp.api("PATCH", path(""), token, body.toString());
        if (!r.ok()) throw CloudBackup.http(hidden ? "Making " + name + " private" : "Publishing " + name, r);
        read(DeviceFlow.parse(r.body));
        isPrivate = hidden;
    }

    /** The repository's description (≤ 350 characters) and topics ({@link #TOPIC} always first). */
    void setDetails(String description, List<String> topics) throws IOException, GitHubException {
        if (description != null) {
            JsonObject body = new JsonObject();
            String d = description.replace('\n', ' ').trim();
            body.addProperty("description", d.length() > 350 ? d.substring(0, 350) : d);
            GitHubHttp.Response r = GitHubHttp.api("PATCH", path(""), token, body.toString());
            if (!r.ok()) throw CloudBackup.http("Saving the description", r);
        }
        if (topics != null) {
            JsonObject body = new JsonObject();
            JsonArray names = new JsonArray();
            names.add(TOPIC);
            for (String t : topics) {
                String clean = com.sketchware.ai.project.ProjectDiffAnalyzer.topic(t);
                if (clean != null && !clean.equals(TOPIC) && names.size() < 20) names.add(clean);
            }
            body.add("names", names);
            GitHubHttp.Response r = GitHubHttp.api("PUT", path("/topics"), token, body.toString());
            if (!r.ok()) throw CloudBackup.http("Saving the topics", r);
        }
    }

    /** Writes a text file (README.md, CHANGELOG.md) on the default branch: one commit; unchanged text = no commit. */
    void putFile(String filePath, String text, String message) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", path("/contents/" + CloudBackup.encPath(filePath) + "?ref="
                + CloudBackup.enc(branch)), token, null);
        String sha = null;
        if (r.ok()) {
            JsonObject o = DeviceFlow.parse(r.body);
            sha = DeviceFlow.string(o, "sha");
            String old = DeviceFlow.string(o, "content");
            if (old != null) {
                try {
                    String was = new String(android.util.Base64.decode(old, android.util.Base64.DEFAULT),
                            StandardCharsets.UTF_8);
                    if (was.equals(text)) return;
                } catch (RuntimeException ignored) {
                    // not ours to read: written again
                }
            }
        } else if (r.code != 404) {
            throw CloudBackup.http("Reading " + filePath, r);
        }
        JsonObject body = new JsonObject();
        body.addProperty("message", message);
        body.addProperty("content", android.util.Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP));
        body.addProperty("branch", branch);
        if (sha != null) body.addProperty("sha", sha);
        GitHubHttp.Response w = GitHubHttp.api("PUT", path("/contents/" + CloudBackup.encPath(filePath)), token,
                body.toString());
        if (!w.ok()) throw CloudBackup.http("Writing " + filePath, w);
    }

    // ------------------------------------------------------------------ versions

    /** One version: a release of the repository with this app's facts. */
    public static final class Version {
        public long id;
        public String tag;
        public String title;
        public boolean draft;
        public boolean standard;
        /** When it was backed up (ms since 1970). */
        public long time;
        public String versionName;
        public String versionCode;
        public long size;
        public String sha256;
        /** The asset holding the backup: "<name>.swb", or "<name>.swb.parts.json" for one in parts. */
        public String swb;
        public String apk;
        /** The notes without the hidden facts line. */
        public String notes;
        public String htmlUrl;
        public final List<Asset> assets = new ArrayList<>();

        public String label() {
            return "v" + (versionName == null ? "?" : versionName) + " (" + (versionCode == null ? "?" : versionCode) + ")";
        }

        Asset asset(String assetName) {
            for (Asset a : assets) if (a.name.equals(assetName)) return a;
            return null;
        }
    }

    public static final class Asset {
        public long id;
        public String name;
        public long size;
        public String state;
    }

    /** The versions, newest first (drafts left out unless {@code withDrafts}). */
    List<Version> versions(boolean withDrafts) throws IOException, GitHubException {
        return versions(token, login, name, withDrafts);
    }

    /** The same for any repository ({@code token} may be null for a public one). */
    static List<Version> versions(String token, String owner, String repo, boolean withDrafts)
            throws IOException, GitHubException {
        List<Version> out = new ArrayList<>();
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(owner) + "/" + CloudBackup.enc(repo)
                + "/releases?per_page=100", token, null);
        if (!r.ok()) throw CloudBackup.http("Reading the versions of " + repo, r);
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(r.body);
        } catch (RuntimeException e) {
            throw new GitHubException("bad_answer", "GitHub's list of versions couldn't be read.");
        }
        if (!parsed.isJsonArray()) return out;
        for (JsonElement e : parsed.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            Version v = parse(e.getAsJsonObject());
            if (v != null && (withDrafts || !v.draft)) out.add(v);
        }
        sortNewestFirst(out);
        return out;
    }

    static void sortNewestFirst(List<Version> list) {
        Collections.sort(list, (x, y) -> Long.compare(y.time, x.time));
    }

    /** A release this app made (it has the facts line), or null. */
    static Version parse(JsonObject o) {
        String body = DeviceFlow.string(o, "body");
        JsonObject facts = facts(body);
        if (facts == null) return null;
        Version v = new Version();
        v.id = number(o);
        v.tag = DeviceFlow.string(o, "tag_name");
        v.title = DeviceFlow.string(o, "name");
        v.draft = o.has("draft") && o.get("draft").isJsonPrimitive() && o.get("draft").getAsBoolean();
        v.htmlUrl = DeviceFlow.string(o, "html_url");
        v.notes = notesOf(body);
        v.standard = facts.has("standard") && facts.get("standard").isJsonPrimitive() && facts.get("standard").getAsBoolean();
        v.time = longOf(facts, "time");
        v.versionName = DeviceFlow.string(facts, "versionName");
        v.versionCode = DeviceFlow.string(facts, "versionCode");
        v.size = longOf(facts, "size");
        v.sha256 = DeviceFlow.string(facts, "sha256");
        v.swb = DeviceFlow.string(facts, "swb");
        v.apk = DeviceFlow.string(facts, "apk");
        JsonElement assets = o.get("assets");
        if (assets != null && assets.isJsonArray()) {
            for (JsonElement ae : assets.getAsJsonArray()) {
                if (!ae.isJsonObject()) continue;
                JsonObject ao = ae.getAsJsonObject();
                Asset a = new Asset();
                a.id = number(ao);
                a.name = DeviceFlow.string(ao, "name");
                a.size = longOf(ao, "size");
                a.state = DeviceFlow.string(ao, "state");
                if (a.name != null) v.assets.add(a);
            }
        }
        return v;
    }

    /** The hidden facts line of a release's notes, or null when it has none. */
    static JsonObject facts(String body) {
        if (body == null) return null;
        int start = body.lastIndexOf(MARKER_START);
        if (start < 0) return null;
        int end = body.indexOf(MARKER_END, start + MARKER_START.length());
        if (end < 0) return null;
        try {
            JsonElement e = JsonParser.parseString(body.substring(start + MARKER_START.length(), end));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String notesOf(String body) {
        if (body == null) return "";
        int start = body.lastIndexOf(MARKER_START);
        return (start < 0 ? body : body.substring(0, start)).trim();
    }

    static String body(String notes, JsonObject facts) {
        // "--" can't end the HTML comment early: Gson writes no "-->" (a "-" inside a string is fine)
        String json = facts.toString().replace("-->", "--\\u003e");
        return (notes == null ? "" : notes.trim()) + "\n\n" + MARKER_START + json + MARKER_END;
    }

    private static long longOf(JsonObject o, String field) {
        try {
            return o.has(field) && o.get(field).isJsonPrimitive() ? o.get(field).getAsLong() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** "v1.0-1" for an app version (a git tag's characters). */
    public static String tagFor(String versionName, String versionCode) {
        return "v" + tagPart(versionName, "1.0") + "-" + tagPart(versionCode, "1");
    }

    private static String tagPart(String s, String fallback) {
        String t = s == null ? "" : s.trim().replaceAll("[^A-Za-z0-9._]", "_").replaceAll("\\.{2,}", ".")
                .replaceAll("^[._]+|[._]+$", "");
        if (t.isEmpty()) t = fallback;
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    // ------------------------------------------------------------------ backup

    /** What a version is made of. */
    public static final class Backup {
        public File swb;
        /** The last built APK, or null. */
        public File apk;
        public String projectName;
        public String versionName;
        public String versionCode;
        /** The release notes ("What's New & Fixed"). */
        public String notes;
        public String title;
    }

    /** v2.0.4 tests: pauses between tries of one asset. */
    static long[] assetPauses = {2000, 6000};

    /**
     * Uploads {@code backup} as the release of its app version (made, or updated when that version was backed up
     * before), then keeps at most {@link #MAX_VERSIONS} versions. Returns the version as stored.
     */
    Version upload(Backup backup, CloudBackup.UploadProgress progress) throws IOException, GitHubException {
        File swb = backup.swb;
        if (swb == null || !swb.isFile()) throw new GitHubException("no_file", "The backup file wasn't made.");
        if (swb.length() > MAX_BYTES) {
            throw new GitHubException("too_large", "The backup is " + (swb.length() >> 20) + " MB: GitHub backups take up "
                    + "to " + (MAX_BYTES >> 20) + " MB. Back it up without local libraries, or keep it on the phone.");
        }
        String tag = tagFor(backup.versionName, backup.versionCode);
        long now = System.currentTimeMillis();
        // v2.0.5: the files are named after the packed file's time, so a retry with the same packed file finds the
        // parts it already sent (checkpoint resume)
        long packed = swb.lastModified() > 0 ? swb.lastModified() : now;
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH).format(new java.util.Date(packed));
        String base = safeFileName(backup.projectName) + "_" + stamp;
        long size = swb.length();
        String sha256 = SwbParts.sha256(swb);

        // drafts this app left behind (a backup cut off before it was published) go first, except the one of this
        // very backup (same version, same SHA-256): it is resumed
        List<Version> all = versions(true);
        Version resume = null;
        for (Version v : all) {
            if (!v.draft) continue;
            if (resume == null && tag.equals(v.tag) && sha256.equals(v.sha256)) {
                resume = v;
            } else {
                deleteRelease(v);
            }
        }
        Version existing = null;
        for (Version v : all) {
            if (!v.draft && tag.equals(v.tag)) existing = v;
        }
        if (existing != null && resume != null) {
            deleteRelease(resume);
            resume = null;
        }
        boolean standard = existing != null && existing.standard;
        JsonObject facts = new JsonObject();
        facts.addProperty("kind", "sketchware-ai-backup");
        facts.addProperty("format", 1);
        facts.addProperty("time", now);
        facts.addProperty("versionName", backup.versionName);
        facts.addProperty("versionCode", backup.versionCode);
        facts.addProperty("size", size);
        facts.addProperty("sha256", sha256);
        facts.addProperty("standard", standard);
        long releaseId;
        // v2.0.5: the files already on that release (a resumed draft, or the same version's release after a cut-off
        // update): one with the right name and size isn't sent again
        java.util.Map<String, Asset> present = new java.util.HashMap<>();
        if (existing != null) {
            releaseId = existing.id;
            for (Asset a : existing.assets) present.put(a.name, a);
        } else if (resume != null) {
            releaseId = resume.id;
            for (Asset a : resume.assets) present.put(a.name, a);
        } else {
            JsonObject body = new JsonObject();
            body.addProperty("tag_name", tag);
            body.addProperty("target_commitish", branch);
            body.addProperty("name", backup.title == null ? backup.projectName + " " + tag : backup.title);
            body.addProperty("body", body(backup.notes, facts));
            body.addProperty("draft", true);
            GitHubHttp.Response r = GitHubHttp.api("POST", path("/releases"), token, body.toString());
            if (!r.ok()) throw CloudBackup.http("Creating the release " + tag, r);
            releaseId = number(DeviceFlow.parse(r.body));
        }

        // the files: one .swb, or parts with their list
        this.present = present;
        skippedLast = 0;
        Set<String> uploaded = new HashSet<>();
        long apkSize = backup.apk != null && backup.apk.isFile() ? backup.apk.length() : 0;
        long total = size + apkSize;
        if (size <= PART_BYTES) {
            String asset = base + ".swb";
            uploadAsset(releaseId, asset, swb, 0, size, "application/zip", 0, total, progress, "the backup");
            facts.addProperty("swb", asset);
            uploaded.add(asset);
        } else {
            SwbParts.Manifest manifest = new SwbParts.Manifest();
            manifest.name = base + ".swb";
            manifest.size = size;
            manifest.sha256 = sha256;
            manifest.partBytes = PART_BYTES;
            int count = (int) ((size + PART_BYTES - 1) / PART_BYTES);
            long done = 0;
            for (int i = 0; i < count; i++) {
                long offset = i * PART_BYTES;
                long length = Math.min(PART_BYTES, size - offset);
                String asset = base + ".swb.part" + String.format(Locale.ENGLISH, "%03d", i);
                uploadAsset(releaseId, asset, swb, offset, length, "application/octet-stream", done, total, progress,
                        "part " + (i + 1) + " of " + count);
                manifest.parts.add(new SwbParts.Part(asset, length, SwbParts.gitBlobSha(swb, offset, length)));
                uploaded.add(asset);
                done += length;
            }
            File list = File.createTempFile("parts", ".json", swb.getParentFile());
            try {
                writeText(list, manifest.toJson());
                String asset = base + ".swb" + SwbParts.MANIFEST_SUFFIX;
                uploadAsset(releaseId, asset, list, 0, list.length(), "application/json", total, total, null,
                        "the parts list");
                facts.addProperty("swb", asset);
                uploaded.add(asset);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                list.delete();
            }
        }
        if (apkSize == 0 && existing != null && existing.apk != null && existing.asset(existing.apk) != null) {
            // review M1: no newer APK: the version keeps the one it has (it isn't deleted below)
            facts.addProperty("apk", existing.apk);
            uploaded.add(existing.apk);
        }
        if (apkSize > 0) {
            String asset = safeFileName(backup.projectName) + "_" + tag + "_" + stamp + ".apk";
            uploadAsset(releaseId, asset, backup.apk, 0, apkSize, "application/vnd.android.package-archive", size, total,
                    progress, "the APK");
            facts.addProperty("apk", asset);
            uploaded.add(asset);
        }
        if (progress != null) progress.saving();

        // publish (a new version) or update (the same version again), then the old files of that version go
        JsonObject patch = new JsonObject();
        patch.addProperty("body", body(backup.notes, facts));
        if (backup.title != null) patch.addProperty("name", backup.title);
        if (existing == null) patch.addProperty("draft", false);
        GitHubHttp.Response p = GitHubHttp.api("PATCH", path("/releases/" + releaseId), token, patch.toString());
        if (!p.ok()) throw CloudBackup.http("Saving the release " + tag, p);
        Version stored = parse(DeviceFlow.parse(p.body));
        if (existing != null) {
            for (Asset old : existing.assets) {
                if (!uploaded.contains(old.name)) deleteAsset(old.id);
            }
        }
        prune();
        if (stored == null) {
            stored = new Version();
            stored.id = releaseId;
            stored.tag = tag;
            stored.time = now;
        }
        return stored;
    }

    /**
     * One asset, checked by its size; tried again (up to three times) after a dropped connection, a server error, or a
     * 401 while GET /user still accepts the sign-in. A half-uploaded asset of that name is removed before a new try.
     */
    /** v2.0.5: the files already on the release being written (checkpoint resume); set by upload. */
    private java.util.Map<String, Asset> present = new java.util.HashMap<>();
    /** v2.0.5 tests: how many files the last upload didn't send again. */
    int skippedLast;

    private void uploadAsset(long releaseId, String assetName, File file, long offset, long length, String type,
                             long before, long total, CloudBackup.UploadProgress progress, String what)
            throws IOException, GitHubException {
        Asset there = present.get(assetName);
        if (there != null) {
            if (there.size == length && (there.state == null || "uploaded".equals(there.state))) {
                // already on GitHub from an earlier try: resumed after it
                skippedLast++;
                if (progress != null) progress.uploading(before + length, total);
                return;
            }
            // half there: removed, then sent again
            deleteAsset(there.id);
            present.remove(assetName);
        }
        int tries = assetPauses.length + 1;
        for (int attempt = 1; ; attempt++) {
            GitHubHttp.stopIfCancelled();
            GitHubHttp.Response r;
            try {
                r = GitHubHttp.uploadAsset(path("/releases/" + releaseId + "/assets?name=" + CloudBackup.enc(assetName)),
                        token, file, offset, length, type, progress == null ? null
                                : (sent, all) -> progress.uploading(before + Math.min(length, sent), total));
            } catch (GitHubHttp.Cancelled e) {
                throw e;
            } catch (IOException e) {
                if (attempt >= tries || !GitHubHttp.worthRetry(e)) throw e;
                retrying(progress, "Sending " + what + " again: the network dropped it (try " + (attempt + 1) + " of "
                        + tries + ")…", attempt);
                removeAsset(releaseId, assetName);
                continue;
            }
            if (r.ok()) {
                JsonObject a = DeviceFlow.parse(r.body);
                long stored = longOf(a, "size");
                if (stored != length) {
                    throw new GitHubException("bad_answer", "GitHub stored " + what + " with " + stored + " bytes instead "
                            + "of " + length + ": try again.");
                }
                return;
            }
            boolean again = attempt < tries;
            if (r.code == 422 && again) {
                // a file of that name is there (a try whose answer was lost): replaced
                removeAsset(releaseId, assetName);
                continue;
            }
            if (again && (r.code >= 500 || r.code == 408)) {
                retrying(progress, "Sending " + what + " again: GitHub answered HTTP " + r.code + " (try " + (attempt + 1)
                        + " of " + tries + ")…", attempt);
                removeAsset(releaseId, assetName);
                continue;
            }
            if (again && r.code == 401 && new CloudBackup(token, login).signInWorks()) {
                retrying(progress, "Sending " + what + " again: GitHub refused it once (HTTP 401" + CloudBackup.said(r)
                        + ") but accepts the sign-in (try " + (attempt + 1) + " of " + tries + ")…", attempt);
                removeAsset(releaseId, assetName);
                continue;
            }
            throw CloudBackup.http("Uploading " + what, r);
        }
    }

    private static void retrying(CloudBackup.UploadProgress progress, String text, int attempt) throws IOException {
        if (progress != null) progress.retrying(text);
        try {
            Thread.sleep(assetPauses[Math.min(attempt, assetPauses.length) - 1]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubHttp.Cancelled();
        }
    }

    /** Removes an asset of that name from the release, if there is one (a half-finished try). */
    private void removeAsset(long releaseId, String assetName) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", path("/releases/" + releaseId + "/assets?per_page=100"), token, null);
        if (!r.ok()) return;
        try {
            for (JsonElement e : JsonParser.parseString(r.body).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                if (assetName.equals(DeviceFlow.string(o, "name"))) deleteAsset(number(o));
            }
        } catch (RuntimeException ignored) {
            // the next try says what is wrong
        }
    }

    private void deleteAsset(long assetId) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("DELETE", path("/releases/assets/" + assetId), token, null);
        if (!r.ok() && r.code != 404) throw CloudBackup.http("Removing an old file", r);
    }

    /** A version and its tag (the tag is what keeps a release's commit). */
    void deleteRelease(Version v) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("DELETE", path("/releases/" + v.id), token, null);
        if (!r.ok() && r.code != 404) throw CloudBackup.http("Removing the old version " + v.label(), r);
        if (v.tag != null && !v.draft) {
            GitHubHttp.Response t = GitHubHttp.api("DELETE", path("/git/refs/tags/" + CloudBackup.encPath(v.tag)), token, null);
            if (!t.ok() && t.code != 404 && t.code != 422) throw CloudBackup.http("Removing the tag " + v.tag, t);
        }
    }

    /**
     * At most {@link #MAX_VERSIONS} versions: the oldest that isn't the Standard one go (oldest first). Returns the
     * versions removed.
     */
    List<Version> prune() throws IOException, GitHubException {
        List<Version> list = versions(false);
        List<Version> removed = new ArrayList<>();
        for (Version v : keepAtMost(list, MAX_VERSIONS)) {
            deleteRelease(v);
            removed.add(v);
        }
        return removed;
    }

    /** Which versions go so that at most {@code most} stay: the oldest non-Standard ones (list newest first). */
    static List<Version> keepAtMost(List<Version> newestFirst, int most) {
        List<Version> out = new ArrayList<>();
        int count = newestFirst.size();
        for (int i = newestFirst.size() - 1; i >= 0 && count > most; i--) {
            Version v = newestFirst.get(i);
            if (v.standard) continue;
            out.add(v);
            count--;
        }
        return out;
    }

    /** Pins {@code version} as the Standard one (or unpins it); at most one Standard version per repository. */
    void setStandard(long versionId, boolean on) throws IOException, GitHubException {
        for (Version v : versions(false)) {
            boolean want = on && v.id == versionId;
            if (v.standard == want) continue;
            JsonObject facts = factsOf(v);
            facts.addProperty("standard", want);
            JsonObject patch = new JsonObject();
            patch.addProperty("body", body(v.notes, facts));
            GitHubHttp.Response r = GitHubHttp.api("PATCH", path("/releases/" + v.id), token, patch.toString());
            if (!r.ok()) throw CloudBackup.http(want ? "Pinning " + v.label() : "Unpinning " + v.label(), r);
        }
    }

    /** New notes for a version (its facts kept). */
    void setNotes(Version v, String notes) throws IOException, GitHubException {
        JsonObject patch = new JsonObject();
        patch.addProperty("body", body(notes, factsOf(v)));
        GitHubHttp.Response r = GitHubHttp.api("PATCH", path("/releases/" + v.id), token, patch.toString());
        if (!r.ok()) throw CloudBackup.http("Saving the notes of " + v.label(), r);
        v.notes = notes;
    }

    private static JsonObject factsOf(Version v) {
        JsonObject facts = new JsonObject();
        facts.addProperty("kind", "sketchware-ai-backup");
        facts.addProperty("format", 1);
        facts.addProperty("time", v.time);
        facts.addProperty("versionName", v.versionName);
        facts.addProperty("versionCode", v.versionCode);
        facts.addProperty("size", v.size);
        if (v.sha256 != null) facts.addProperty("sha256", v.sha256);
        facts.addProperty("standard", v.standard);
        if (v.swb != null) facts.addProperty("swb", v.swb);
        if (v.apk != null) facts.addProperty("apk", v.apk);
        return facts;
    }

    // ------------------------------------------------------------------ restore

    /**
     * Downloads the backup of {@code v} into {@code target} (its parts joined) and checks size and SHA-256 before
     * anything is restored. Works without a token for a public repository.
     */
    static void download(String token, String owner, String repo, Version v, File target, GitHubHttp.Progress progress)
            throws IOException, GitHubException {
        String base = "/repos/" + CloudBackup.enc(owner) + "/" + CloudBackup.enc(repo) + "/releases/assets/";
        Asset swb = v.swb == null ? null : v.asset(v.swb);
        if (swb == null) {
            // a release without its facts' file name: the newest .swb or parts list in it
            for (Asset a : v.assets) {
                if (a.name.toLowerCase(Locale.ROOT).endsWith(".swb") || SwbParts.isManifest(a.name)) {
                    if (swb == null || a.name.compareTo(swb.name) > 0) swb = a;
                }
            }
        }
        if (swb == null) throw new GitHubException("no_backup", "Version " + v.label() + " has no .swb backup in it.");
        if (!SwbParts.isManifest(swb.name)) {
            GitHubHttp.Response r = GitHubHttp.downloadAsset(base + swb.id, token, target, MAX_BYTES + (1 << 20), progress);
            if (!r.ok()) throw CloudBackup.http("Downloading " + v.label(), r);
            if (v.sha256 != null && v.sha256.matches("[0-9a-f]{64}") && !v.sha256.equals(SwbParts.sha256(target))) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                throw new GitHubException("bad_backup", "The downloaded backup doesn't match its checksum: try again.");
            }
            return;
        }
        File list = new File(target.getAbsolutePath() + ".list");
        File joined = new File(target.getAbsolutePath() + ".joining");
        File piece = new File(target.getAbsolutePath() + ".piece");
        try {
            GitHubHttp.Response r = GitHubHttp.downloadAsset(base + swb.id, token, list, 1 << 20, null);
            if (!r.ok()) throw CloudBackup.http("Downloading the parts list of " + v.label(), r);
            SwbParts.Manifest m = SwbParts.Manifest.parse(new String(java.nio.file.Files.readAllBytes(list.toPath()),
                    StandardCharsets.UTF_8), MAX_BYTES);
            long before = 0;
            try (OutputStream out = new FileOutputStream(joined)) {
                int index = 0;
                for (SwbParts.Part p : m.parts) {
                    index++;
                    Asset a = v.asset(p.path);
                    if (a == null) throw new GitHubException("bad_backup", "Part " + index + " of " + v.label() + " is missing.");
                    long done = before;
                    //noinspection ResultOfMethodCallIgnored
                    piece.delete();
                    GitHubHttp.Response pr = GitHubHttp.downloadAsset(base + a.id, token, piece, p.size + 1024,
                            progress == null ? null : (got, all) -> progress.bytes(done + Math.min(got, p.size), m.size));
                    if (!pr.ok()) throw CloudBackup.http("Downloading part " + index + " of " + m.parts.size(), pr);
                    if (piece.length() != p.size || !p.sha.equals(SwbParts.gitBlobSha(piece, 0, p.size))) {
                        throw new GitHubException("bad_part", "Part " + index + " of " + m.parts.size() + " doesn't match: "
                                + "try again.");
                    }
                    try (InputStream in = new java.io.FileInputStream(piece)) {
                        byte[] buffer = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    }
                    before += p.size;
                }
            }
            if (joined.length() != m.size || !m.sha256.equals(SwbParts.sha256(joined))) {
                throw new GitHubException("bad_backup", "The joined backup doesn't match its checksum: try again.");
            }
            if (target.exists() && !target.delete()) throw new IOException("Can't replace " + target);
            if (!joined.renameTo(target)) throw new IOException("Couldn't save " + target);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            list.delete();
            //noinspection ResultOfMethodCallIgnored
            piece.delete();
            //noinspection ResultOfMethodCallIgnored
            joined.delete();
        }
    }

    // ------------------------------------------------------------------ the account's project repositories

    /** One project repository of the account (the import screen's cards). */
    public static final class Info {
        public String name;
        public long number;
        public boolean isPrivate;
        public String description;
        public long updated;
        public String owner;
    }

    /**
     * The account's project repositories (names starting {@link #PREFIX}, with this app's topic or description, and
     * the v2.0.3 public copies), newest first; never the backup repository. Up to 1000 repositories are looked at.
     */
    static List<Info> listOwn(String token) throws IOException, GitHubException {
        List<Info> out = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            GitHubHttp.Response r = GitHubHttp.api("GET", "/user/repos?affiliation=owner&sort=updated&per_page=100&page="
                    + page, token, null);
            if (!r.ok()) throw CloudBackup.http("Listing your repositories", r);
            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(r.body);
            } catch (RuntimeException e) {
                throw new GitHubException("bad_answer", "GitHub's list of repositories couldn't be read.");
            }
            if (!parsed.isJsonArray() || parsed.getAsJsonArray().size() == 0) break;
            for (JsonElement e : parsed.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String name = DeviceFlow.string(o, "name");
                if (name == null || !name.regionMatches(true, 0, PREFIX, 0, PREFIX.length())
                        || name.equalsIgnoreCase(CloudBackup.REPO)) {
                    continue;
                }
                String description = DeviceFlow.string(o, "description");
                boolean tagged = false;
                JsonElement topics = o.get("topics");
                if (topics != null && topics.isJsonArray()) {
                    for (JsonElement t : topics.getAsJsonArray()) {
                        if (t.isJsonPrimitive() && TOPIC.equals(t.getAsString())) tagged = true;
                    }
                }
                boolean ours = tagged || (description != null && description.startsWith(MARK))
                        || name.regionMatches(true, 0, "SketchwareAI-Public-", 0, 20);
                if (!ours) continue;
                Info info = new Info();
                info.name = name;
                info.number = number(o);
                info.isPrivate = !o.has("private") || o.get("private").getAsBoolean();
                info.description = description;
                info.updated = isoTime(DeviceFlow.string(o, "pushed_at"), DeviceFlow.string(o, "updated_at"));
                JsonObject owner = o.has("owner") && o.get("owner").isJsonObject() ? o.getAsJsonObject("owner") : null;
                info.owner = owner == null ? null : DeviceFlow.string(owner, "login");
                out.add(info);
            }
            if (parsed.getAsJsonArray().size() < 100) break;
        }
        Collections.sort(out, (x, y) -> Long.compare(y.updated, x.updated));
        return out;
    }

    static long isoTime(String... values) {
        long best = 0;
        for (String value : values) {
            if (value == null) continue;
            try {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                best = Math.max(best, f.parse(value).getTime());
            } catch (java.text.ParseException | RuntimeException ignored) {
                // not a time
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ helpers

    static String safeFileName(String projectName) {
        String n = (projectName == null ? "" : projectName).replaceAll("[^A-Za-z0-9_.-]", "_").replaceAll("^[._-]+", "");
        if (n.isEmpty()) n = "project";
        return n.length() > 60 ? n.substring(0, 60) : n;
    }

    private static void writeText(File f, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
