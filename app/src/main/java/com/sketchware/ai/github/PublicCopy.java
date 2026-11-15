package com.sketchware.ai.github;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

/**
 * v2.0.3 (owner's 15 Nov directive "per-project Public / Private toggle: PATCH /repos/{owner}/{repo}"): the backups
 * stay in the one private repository {@link CloudBackup#REPO}; a project made public gets its own repository
 * ({@code SketchwareAI-<name>}, created public with {@code auto_init}), holding the newest backup of that project only,
 * updated after each of its backups. "Private" makes that repository private again ({@code PATCH private: true}), so
 * the shared link stops working for others at once; "Public" again makes it public and updates it. Nothing of the
 * other projects is ever in a public repository. A repository of that name that this app didn't make is never used
 * (a number is added to the name).
 */
final class PublicCopy {

    /** The description that marks a repository this app made for a public copy. */
    static final String MARK = "Sketchware AI project";

    private PublicCopy() {
    }

    /** "SketchwareAI-My_App" for a project name (GitHub's repository name characters only). */
    static String repositoryName(String projectName) {
        String name = (projectName == null ? "" : projectName).replaceAll("[^A-Za-z0-9_.-]", "_").replaceAll("^[._-]+", "");
        if (name.isEmpty()) name = "project";
        if (name.length() > 60) name = name.substring(0, 60);
        return "SketchwareAI-" + name;
    }

    /**
     * Makes {@code scId} public: its repository found or created (public), the backup {@code swb} uploaded to it, the
     * link stored. Returns the public link.
     */
    static String makePublic(String token, String login, String scId, String name, String pkg, String version, File swb,
                             CloudBackup.UploadProgress progress) throws IOException, GitHubException {
        String repo = ProjectLinks.publicRepository(scId);
        if (repo == null || !ours(token, login, repo)) repo = choose(token, login, name, pkg);
        setPrivate(token, login, repo, false);
        CloudBackup cloud = new CloudBackup(token, login, repo, true);
        cloud.ensureRepository();
        cloud.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME, progress);
        String link = cloud.link(cloud.lastPath);
        ProjectLinks.publicNow(scId, repo, link, cloud.lastPath);
        return link;
    }

    /** Makes the project's public copy private again (if it has one); the project is private either way. */
    static void makePrivate(String token, String login, String scId) throws IOException, GitHubException {
        String repo = ProjectLinks.publicRepository(scId);
        if (repo != null && ours(token, login, repo)) setPrivate(token, login, repo, true);
        ProjectLinks.privateNow(scId);
    }

    /** After a backup of a public project: its public copy gets the same .swb. */
    static void update(String token, String login, String scId, String name, String pkg, String version,
                       File swb, CloudBackup.UploadProgress progress) throws IOException, GitHubException {
        String repo = ProjectLinks.publicRepository(scId);
        if (repo == null) return;
        CloudBackup cloud = new CloudBackup(token, login, repo, true);
        if (!cloud.openIfExists()) {
            // deleted on github.com: the project is private again
            ProjectLinks.privateNow(scId);
            return;
        }
        cloud.upload(swb, name, pkg, version, com.sketchware.remod.BuildConfig.VERSION_NAME, progress);
        ProjectLinks.publicNow(scId, repo, cloud.link(cloud.lastPath), cloud.lastPath);
    }

    /** A free name of ours: the plain one, else with -2 … -9; created public when it doesn't exist. */
    static String choose(String token, String login, String name, String pkg) throws IOException, GitHubException {
        String base = repositoryName(name);
        for (int i = 1; i <= 9; i++) {
            String repo = i == 1 ? base : base + "-" + i;
            GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(repo),
                    token, null);
            if (r.code == 404) {
                create(token, repo, name, pkg);
                return repo;
            }
            if (!r.ok()) throw CloudBackup.http("Opening " + login + "/" + repo, r);
            String description = DeviceFlow.string(DeviceFlow.parse(r.body), "description");
            if (description != null && description.startsWith(MARK)) return repo;
        }
        throw new GitHubException("no_name", "Repositories " + base + " to " + base + "-9 exist already and weren't made "
                + "by Sketchware AI: rename the project or one of them.");
    }

    /** True when {@code repo} exists and was made by this app (its description). */
    private static boolean ours(String token, String login, String repo) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(repo),
                token, null);
        if (r.code == 404) return false;
        if (!r.ok()) throw CloudBackup.http("Opening " + login + "/" + repo, r);
        String description = DeviceFlow.string(DeviceFlow.parse(r.body), "description");
        return description != null && description.startsWith(MARK);
    }

    private static void create(String token, String repo, String name, String pkg) throws IOException, GitHubException {
        JsonObject body = new JsonObject();
        body.addProperty("name", repo);
        body.addProperty("private", false);
        body.addProperty("auto_init", true);
        body.addProperty("has_issues", false);
        body.addProperty("has_wiki", false);
        body.addProperty("description", MARK + ": " + (name == null ? "" : name) + (pkg == null ? "" : " (" + pkg + ")")
                + ". Import: Sketchware AI > Restore projects > GitHub import project, paste this repository's link.");
        GitHubHttp.Response r = GitHubHttp.api("POST", "/user/repos", token, body.toString());
        if (!r.ok()) throw CloudBackup.http("Creating the public repository " + repo, r);
    }

    /** PATCH /repos/{owner}/{repo} {"private": …}; a repository deleted meanwhile is fine for "private". */
    static void setPrivate(String token, String login, String repo, boolean hidden) throws IOException, GitHubException {
        JsonObject body = new JsonObject();
        body.addProperty("private", hidden);
        GitHubHttp.Response r = GitHubHttp.api("PATCH", "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(repo),
                token, body.toString());
        if (r.code == 404 && hidden) return;
        if (!r.ok()) {
            throw CloudBackup.http((hidden ? "Making " : "Publishing ") + login + "/" + repo + (hidden ? " private" : ""), r);
        }
    }

    /** The words for the card's pill: "Public" / "Private". */
    static String label(String visibility) {
        return "public".equals(visibility) ? "Public" : "Private";
    }
}
