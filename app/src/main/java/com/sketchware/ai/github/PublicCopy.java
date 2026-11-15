package com.sketchware.ai.github;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * v2.0.3 (owner's 15 Nov directive "per-project Public / Private toggle: PATCH /repos/{owner}/{repo}"): the backups
 * stay in the one private repository {@link CloudBackup#REPO}; a project made public gets its own repository
 * ({@code SketchwareAI-Public-<name>}), holding the newest backup of that project only, updated after each of its
 * backups. "Private" makes that repository private again ({@code PATCH private: true}), so the shared link stops
 * working for others at once; "Public" again uploads the newest backup and makes it public.
 * <p>
 * Review (v2.0.3, two HIGH findings): a copy is identified by its own random id, kept in the project's record
 * ({@link ProjectLinks}) and in the repository's description ({@link #MARK} + id); a repository is reused only when its
 * description holds this project's id (another project of the same name, a deleted project's copy or a repository the
 * user made is never touched), and the private backup repository is refused by name whatever happens. A copy is made and
 * filled while private and made public only after the upload worked, so a failed upload never publishes old content.
 */
final class PublicCopy {

    /** The start of a public copy's description: "Sketchware AI public copy <id>: …". */
    static final String MARK = "Sketchware AI public copy ";

    private PublicCopy() {
    }

    /** "SketchwareAI-Public-My_App" for a project name (GitHub's repository name characters only). */
    static String repositoryName(String projectName) {
        String name = (projectName == null ? "" : projectName).replaceAll("[^A-Za-z0-9_.-]", "_").replaceAll("^[._-]+", "");
        if (name.isEmpty()) name = "project";
        if (name.length() > 60) name = name.substring(0, 60);
        return "SketchwareAI-Public-" + name;
    }

    /** A new copy id: 12 hex digits. */
    static String newId() {
        byte[] bytes = new byte[6];
        new java.security.SecureRandom().nextBytes(bytes);
        return SwbParts.hex(bytes);
    }

    /** Never the private backup repository (any letter case: GitHub's names aren't case-sensitive), only copies. */
    static void refuseBackupRepository(String repo) throws GitHubException {
        if (repo == null || repo.equalsIgnoreCase(CloudBackup.REPO)
                || !repo.toLowerCase(Locale.ROOT).startsWith("sketchwareai-public-")) {
            throw new GitHubException("not_a_copy", (repo == null ? "That" : repo) + " isn't a public copy made by "
                    + "Sketchware AI: nothing was changed.");
        }
    }

    /**
     * Makes {@code scId} public: its own copy found (by its id) or created private, the backup {@code swb} uploaded to
     * it, then made public and the link stored. Returns the public link.
     */
    static String makePublic(String token, String login, String scId, String name, String pkg, String version, File swb,
                             CloudBackup.UploadProgress progress) throws IOException, GitHubException {
        String id = ProjectLinks.publicId(scId, login);
        String repo = id == null ? null : ProjectLinks.publicRepository(scId, login);
        if (repo != null && !ours(token, login, repo, id)) repo = null;
        if (repo == null) {
            id = newId();
            repo = choose(token, login, name, pkg, id);
            ProjectLinks.copyMade(scId, repo, id);
        }
        refuseBackupRepository(repo);
        CloudBackup cloud = new CloudBackup(token, login, repo, true);
        cloud.ensureRepository();
        cloud.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME, progress);
        // only now: public
        setPrivate(token, login, repo, false);
        String link = cloud.link(cloud.lastPath);
        ProjectLinks.publicNow(scId, repo, link, cloud.lastPath);
        return link;
    }

    /**
     * Makes the project's public copy private again. Its repository is the one this project's record names (refused
     * when it would be the backup repository); one deleted on github.com is fine. Any other failure leaves the record
     * public (the card keeps saying so) and is reported.
     */
    static void makePrivate(String token, String login, String scId) throws IOException, GitHubException {
        String repo = ProjectLinks.publicRepository(scId, login);
        if (repo != null) setPrivate(token, login, repo, true);
        ProjectLinks.privateNow(scId);
    }

    /** After a backup of a public project: its public copy gets the same .swb. */
    static void update(String token, String login, String scId, String name, String pkg, String version,
                       File swb, CloudBackup.UploadProgress progress) throws IOException, GitHubException {
        String repo = ProjectLinks.publicRepository(scId, login);
        String id = ProjectLinks.publicId(scId, login);
        if (repo == null || id == null) return;
        refuseBackupRepository(repo);
        CloudBackup cloud = new CloudBackup(token, login, repo, true);
        if (!cloud.openIfExists() || !ours(token, login, repo, id)) {
            // deleted (or replaced) on github.com: the project is private again
            ProjectLinks.privateNow(scId);
            return;
        }
        cloud.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME, progress);
        ProjectLinks.publicNow(scId, repo, cloud.link(cloud.lastPath), cloud.lastPath);
    }

    /** A free repository name for a new copy (the plain one, else -2 … -9), created private with the copy's id. */
    static String choose(String token, String login, String name, String pkg, String id) throws IOException, GitHubException {
        String base = repositoryName(name);
        for (int i = 1; i <= 9; i++) {
            String repo = i == 1 ? base : base + "-" + i;
            GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(repo),
                    token, null);
            if (r.code == 404) {
                create(token, repo, name, pkg, id);
                return repo;
            }
            if (!r.ok()) throw CloudBackup.http("Opening " + login + "/" + repo, r);
        }
        throw new GitHubException("no_name", "Repositories " + base + " to " + base + "-9 exist already: rename the "
                + "project or one of them.");
    }

    /** True when {@code repo} exists and its description names this copy's id. */
    static boolean ours(String token, String login, String repo, String id) throws IOException, GitHubException {
        if (id == null || repo == null || repo.equalsIgnoreCase(CloudBackup.REPO)) return false;
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(repo),
                token, null);
        if (r.code == 404) return false;
        if (!r.ok()) throw CloudBackup.http("Opening " + login + "/" + repo, r);
        String description = DeviceFlow.string(DeviceFlow.parse(r.body), "description");
        return description != null && description.startsWith(MARK + id + ":");
    }

    private static void create(String token, String repo, String name, String pkg, String id) throws IOException, GitHubException {
        refuseBackupRepository(repo);
        JsonObject body = new JsonObject();
        body.addProperty("name", repo);
        // private until its backup is in it (review M1)
        body.addProperty("private", true);
        body.addProperty("auto_init", true);
        body.addProperty("has_issues", false);
        body.addProperty("has_wiki", false);
        body.addProperty("description", MARK + id + ": " + (name == null ? "" : name) + (pkg == null ? "" : " (" + pkg + ")")
                + ". Import: Sketchware AI > Restore projects > GitHub import project, paste this repository's link.");
        GitHubHttp.Response r = GitHubHttp.api("POST", "/user/repos", token, body.toString());
        if (!r.ok()) throw CloudBackup.http("Creating the repository " + repo, r);
    }

    /** PATCH /repos/{owner}/{repo} {"private": …}; never the backup repository; a deleted copy is fine for "private". */
    static void setPrivate(String token, String login, String repo, boolean hidden) throws IOException, GitHubException {
        refuseBackupRepository(repo);
        JsonObject body = new JsonObject();
        body.addProperty("private", hidden);
        GitHubHttp.Response r = GitHubHttp.api("PATCH", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(repo),
                token, body.toString());
        if (r.code == 404 && hidden) return;
        if (!r.ok()) {
            throw CloudBackup.http((hidden ? "Making " : "Publishing ") + login + "/" + repo + (hidden ? " private" : ""), r);
        }
    }
}
