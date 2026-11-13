package com.sketchware.ai.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * v2.0.0 (owner's 13 Nov directive "Import from GitHub"): a project backup ({@code .swb}) from a GitHub link the user
 * pastes: a file ({@code github.com/<owner>/<repo>/blob|raw/<ref>/<path>.swb}, {@code raw.githubusercontent.com/...}),
 * a folder ({@code .../tree/<ref>/<dir>}) or a whole repository ({@code github.com/<owner>/<repo>}), whose {@code .swb}
 * files are listed to pick from. Only GitHub hosts; downloads go through the API (the token, when signed in, so the
 * user's private repositories work too; public ones work signed out). The restore itself is the app's own
 * ({@code BackupRestoreManager}: a new project, entries outside the folder refused).
 */
public final class GitHubImport {

    private GitHubImport() {
    }

    /** Where a link points. {@code ref} null = the default branch; {@code path} "" = the repository's root. */
    public static final class Source {
        public String owner;
        public String repo;
        public String ref;
        public String path;
        /** The link names one file (it must end in .swb). */
        public boolean file;

        @Override
        public String toString() {
            return owner + "/" + repo + (ref == null ? "" : "@" + ref) + (path.isEmpty() ? "" : ":" + path);
        }
    }

    private static final String NAME = "[A-Za-z0-9_.-]+";

    /** The link as a {@link Source}, or null when it isn't a GitHub repository, folder or file link. */
    public static Source parse(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty() || s.contains(" ")) return null;
        if (!s.startsWith("https://") && !s.startsWith("http://")) s = "https://" + s;
        URI uri;
        try {
            uri = new URI(s);
        } catch (Exception e) {
            return null;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath();
        List<String> seg = new ArrayList<>();
        for (String part : rawPath.split("/")) {
            if (part.isEmpty()) continue;
            String decoded;
            try {
                decoded = java.net.URLDecoder.decode(part.replace("+", "%2B"), "UTF-8");
            } catch (Exception e) {
                return null;
            }
            if (decoded.equals("..") || decoded.equals(".") || decoded.contains("/") || decoded.contains("\\")) return null;
            seg.add(decoded);
        }
        Source src = new Source();
        if (host.equals("github.com") || host.equals("www.github.com")) {
            if (seg.size() < 2) return null;
            src.owner = seg.get(0);
            src.repo = stripGit(seg.get(1));
            if (seg.size() == 2) {
                src.path = "";
            } else {
                String kind = seg.get(2);
                if (seg.size() < 4 || !(kind.equals("blob") || kind.equals("raw") || kind.equals("tree"))) return null;
                src.ref = seg.get(3);
                src.path = join(seg, 4);
                src.file = !kind.equals("tree");
            }
        } else if (host.equals("raw.githubusercontent.com")) {
            if (seg.size() < 4) return null;
            src.owner = seg.get(0);
            src.repo = seg.get(1);
            int at = 2;
            if (seg.size() >= 6 && seg.get(2).equals("refs") && (seg.get(3).equals("heads") || seg.get(3).equals("tags"))) at = 4;
            src.ref = seg.get(at);
            src.path = join(seg, at + 1);
            src.file = true;
        } else {
            return null;
        }
        if (!src.owner.matches(NAME) || !src.repo.matches(NAME) || src.repo.isEmpty()) return null;
        if (src.file && (src.path.isEmpty() || !src.path.toLowerCase(Locale.ROOT).endsWith(".swb"))) return null;
        return src;
    }

    private static String stripGit(String repo) {
        return repo.endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
    }

    private static String join(List<String> seg, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < seg.size(); i++) {
            if (out.length() > 0) out.append('/');
            out.append(seg.get(i));
        }
        return out.toString();
    }

    private static String repoPath(Source s) {
        return "/repos/" + CloudBackup.enc(s.owner) + "/" + CloudBackup.enc(s.repo);
    }

    /** The branch the link means: its own, else the repository's default branch. */
    static String ref(String token, Source s) throws IOException, GitHubException {
        if (s.ref != null) return s.ref;
        GitHubHttp.Response r = GitHubHttp.api("GET", repoPath(s), token, null);
        if (r.code == 404) throw notFound(token, s);
        if (!r.ok()) throw CloudBackup.http("Opening " + s.owner + "/" + s.repo, r);
        String branch = DeviceFlow.string(DeviceFlow.parse(r.body), "default_branch");
        return branch == null ? "main" : branch;
    }

    /** The .swb files of a repository or folder link (paths, sorted); for a file link just that file. */
    public static List<String> listBackups(String token, Source s) throws IOException, GitHubException {
        List<String> found = new ArrayList<>();
        if (s.file) {
            found.add(s.path);
            return found;
        }
        String ref = ref(token, s);
        GitHubHttp.Response r = GitHubHttp.api("GET", repoPath(s) + "/git/trees/" + CloudBackup.enc(ref) + "?recursive=1",
                token, null);
        if (r.code == 404) throw notFound(token, s);
        if (!r.ok()) throw CloudBackup.http("Reading " + s.owner + "/" + s.repo, r);
        JsonObject tree = DeviceFlow.parse(r.body);
        JsonElement entries = tree.get("tree");
        if (entries == null || !entries.isJsonArray()) return found;
        String prefix = s.path.isEmpty() ? "" : s.path + "/";
        for (JsonElement e : (JsonArray) entries) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String path = DeviceFlow.string(o, "path");
            if (path == null || !"blob".equals(DeviceFlow.string(o, "type"))) continue;
            if (!path.toLowerCase(Locale.ROOT).endsWith(".swb") || !path.startsWith(prefix)) continue;
            found.add(path);
        }
        Collections.sort(found);
        return found;
    }

    /** Downloads one .swb of the link's repository into {@code folder}; the file's name is kept. */
    public static File download(String token, Source s, String path, File folder) throws IOException, GitHubException {
        String ref = ref(token, s);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Can't create " + folder);
        String name = new File(path).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        File target = new File(folder, s.owner + "-" + s.repo + "-" + name);
        GitHubHttp.Response r = GitHubHttp.download(repoPath(s) + "/contents/" + CloudBackup.encPath(path) + "?ref="
                + CloudBackup.enc(ref), token, target, CloudBackup.MAX_BYTES + (5 << 20));
        int code = r.code;
        if (code == 404) throw notFound(token, s);
        if (r.rateLimited()) throw CloudBackup.http("Downloading " + name, r);
        if (code < 200 || code >= 300) {
            throw new GitHubException("http_" + code, "Downloading " + name + " failed (HTTP " + code + ").");
        }
        return target;
    }

    private static GitHubException notFound(String token, Source s) {
        return new GitHubException("not_found", token == null
                ? s.owner + "/" + s.repo + " wasn't found. A private repository needs you to sign in with GitHub first."
                : s.owner + "/" + s.repo + " or the file wasn't found (check the link).");
    }
}
