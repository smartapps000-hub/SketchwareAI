package com.sketchware.ai.github;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v2.1.0 (owner's 16 Nov directive "GitHub-powered Play Store: APK + SWB + screenshots + video + reviews"): the store is
 * GitHub itself. An app in the store is a public project repository made by Sketchware AI with the topic
 * {@value #TOPIC}; its store page is {@value #LISTING} (title, description, a demo video link) and the pictures in
 * {@value #SHOTS}; what it offers is its newest version's release files (the APK built on the phone and the project's
 * .swb, {@link ProjectRepo}); its reviews are the repository's GitHub Discussions. Browsing works signed out (GitHub's
 * search allows 10 searches a minute then); the list is kept on the phone for offline use. Publishing needs the
 * project's own repository (a backup to GitHub first) and makes it public after asking.
 */
public final class PlayStore {

    public static final String TOPIC = "sketchwareai-playstore";
    public static final String LISTING = "playstore/listing.json";
    public static final String SHOTS = "playstore/screenshots";
    static final int MAX_SHOTS = 8;

    private PlayStore() {
    }

    /** One app of the store. */
    public static final class App {
        public String owner;
        public String repo;
        /** The repository's number (GitHub's id: kept across renames). */
        public long number;
        public String title;
        public String description;
        public long stars;
        public long updated;
        public String branch = "main";
        public String video;
        public final List<String> screenshots = new ArrayList<>();
        public boolean listingRead;

        public String name() {
            return title != null && !title.isEmpty() ? title : displayName(repo);
        }

        /** A copy to read the store page into (the one on screen keeps its own lists meanwhile). */
        public App copy() {
            App a = new App();
            a.owner = owner;
            a.repo = repo;
            a.number = number;
            a.title = title;
            a.description = description;
            a.stars = stars;
            a.updated = updated;
            a.branch = branch;
            a.video = video;
            a.screenshots.addAll(screenshots);
            a.listingRead = listingRead;
            return a;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("owner", owner);
            o.addProperty("repo", repo);
            o.addProperty("number", number);
            o.addProperty("title", title);
            o.addProperty("description", description);
            o.addProperty("stars", stars);
            o.addProperty("updated", updated);
            o.addProperty("branch", branch);
            o.addProperty("video", video);
            JsonArray shots = new JsonArray();
            for (String s : screenshots) shots.add(s);
            o.add("screenshots", shots);
            o.addProperty("listingRead", listingRead);
            return o;
        }

        static App fromJson(JsonObject o) {
            App a = new App();
            a.owner = DeviceFlow.string(o, "owner");
            a.repo = DeviceFlow.string(o, "repo");
            a.number = o.has("number") && o.get("number").isJsonPrimitive() ? o.get("number").getAsLong() : 0;
            a.title = DeviceFlow.string(o, "title");
            a.description = DeviceFlow.string(o, "description");
            a.stars = o.has("stars") && o.get("stars").isJsonPrimitive() ? o.get("stars").getAsLong() : 0;
            a.updated = o.has("updated") && o.get("updated").isJsonPrimitive() ? o.get("updated").getAsLong() : 0;
            String b = DeviceFlow.string(o, "branch");
            if (b != null) a.branch = b;
            a.video = safeVideo(DeviceFlow.string(o, "video"));
            JsonElement shots = o.get("screenshots");
            if (shots != null && shots.isJsonArray()) {
                for (JsonElement e : shots.getAsJsonArray()) {
                    if (e.isJsonPrimitive() && safeImage(e.getAsString()) != null) a.screenshots.add(e.getAsString());
                }
            }
            a.listingRead = o.has("listingRead") && o.get("listingRead").getAsBoolean();
            return a.owner == null || a.repo == null ? null : a;
        }
    }

    static String displayName(String repo) {
        if (repo == null) return "";
        String n = repo.regionMatches(true, 0, ProjectRepo.PREFIX, 0, ProjectRepo.PREFIX.length())
                ? repo.substring(ProjectRepo.PREFIX.length()) : repo;
        if (n.regionMatches(true, 0, "Public-", 0, 7)) n = n.substring(7);
        return n.replace('_', ' ');
    }

    /** A demo video link: https only (YouTube or any https page), else null. */
    static String safeVideo(String url) {
        if (url == null) return null;
        String u = url.trim();
        return u.startsWith("https://") && u.length() < 300 && !u.contains(" ") ? u : null;
    }

    /** A screenshot address: only GitHub's file host for public files, else null. */
    static String safeImage(String url) {
        if (url == null) return null;
        return url.startsWith("https://raw.githubusercontent.com/") && !url.contains("..") ? url : null;
    }

    // ------------------------------------------------------------------ browsing

    /** The store's apps, newest first (GitHub search by topic: public repositories only). */
    public static List<App> search(String token) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/search/repositories?q=topic:" + TOPIC
                + "&sort=updated&order=desc&per_page=50", token, null);
        if (!r.ok()) throw CloudBackup.http("Reading the Play Store", r);
        JsonObject o = DeviceFlow.parse(r.body);
        List<App> out = new ArrayList<>();
        JsonElement items = o.get("items");
        if (items == null || !items.isJsonArray()) return out;
        for (JsonElement e : items.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject item = e.getAsJsonObject();
            if (item.has("private") && item.get("private").getAsBoolean()) continue;
            JsonObject ownerObj = item.has("owner") && item.get("owner").isJsonObject() ? item.getAsJsonObject("owner") : null;
            App a = new App();
            a.owner = ownerObj == null ? null : DeviceFlow.string(ownerObj, "login");
            a.repo = DeviceFlow.string(item, "name");
            a.number = item.has("id") && item.get("id").isJsonPrimitive() ? item.get("id").getAsLong() : 0;
            a.description = DeviceFlow.string(item, "description");
            a.stars = item.has("stargazers_count") && item.get("stargazers_count").isJsonPrimitive()
                    ? item.get("stargazers_count").getAsLong() : 0;
            a.updated = ProjectRepo.isoTime(DeviceFlow.string(item, "pushed_at"), DeviceFlow.string(item, "updated_at"));
            String branch = DeviceFlow.string(item, "default_branch");
            if (branch != null) a.branch = branch;
            if (a.owner != null && a.repo != null) out.add(a);
        }
        return out;
    }

    /** Reads {@code app}'s store page and pictures (missing ones leave the repository's own words). */
    public static void readListing(String token, App app) throws IOException, GitHubException {
        String base = "/repos/" + CloudBackup.enc(app.owner) + "/" + CloudBackup.enc(app.repo) + "/contents/";
        GitHubHttp.Response r = GitHubHttp.api("GET", base + LISTING + "?ref=" + CloudBackup.enc(app.branch), token, null);
        if (r.ok()) {
            JsonObject file = DeviceFlow.parse(r.body);
            String content = DeviceFlow.string(file, "content");
            if (content != null) {
                JsonObject listing = parseListing(new String(android.util.Base64.decode(content, android.util.Base64.DEFAULT),
                        StandardCharsets.UTF_8));
                if (listing != null) {
                    String title = DeviceFlow.string(listing, "title");
                    String description = DeviceFlow.string(listing, "description");
                    if (title != null && !title.trim().isEmpty()) app.title = clip(title.trim(), 80);
                    if (description != null && !description.trim().isEmpty()) app.description = clip(description.trim(), 4000);
                    app.video = safeVideo(DeviceFlow.string(listing, "video"));
                }
            }
        } else if (r.code != 404) {
            throw CloudBackup.http("Reading " + app.repo + "'s store page", r);
        }
        app.screenshots.clear();
        GitHubHttp.Response shots = GitHubHttp.api("GET", base + SHOTS + "?ref=" + CloudBackup.enc(app.branch), token, null);
        if (shots.ok()) {
            JsonElement list = JsonParser.parseString(shots.body);
            if (list.isJsonArray()) {
                List<String> names = new ArrayList<>();
                for (JsonElement e : list.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    String url = safeImage(DeviceFlow.string(e.getAsJsonObject(), "download_url"));
                    String name = DeviceFlow.string(e.getAsJsonObject(), "name");
                    if (url == null || name == null || !name.toLowerCase(Locale.ROOT).matches(".+\\.(png|jpe?g|webp)")) continue;
                    names.add(name + "\n" + url);
                }
                java.util.Collections.sort(names);
                for (String n : names) {
                    if (app.screenshots.size() < MAX_SHOTS) app.screenshots.add(n.substring(n.indexOf('\n') + 1));
                }
            }
        }
        app.listingRead = true;
    }

    /** listing.json leniently (null when it isn't an object). */
    static JsonObject parseListing(String text) {
        try {
            JsonElement e = JsonParser.parseString(text);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String clip(String s, int most) {
        return s.length() > most ? s.substring(0, most) : s;
    }

    // ------------------------------------------------------------------ the copy on the phone

    static File cacheFile(Context context) {
        return new File(context.getFilesDir(), "playstore/apps.json");
    }

    public static List<App> cached(Context context) {
        List<App> out = new ArrayList<>();
        File f = cacheFile(context);
        if (!f.isFile() || f.length() > 4L * 1024 * 1024) return out;
        try {
            JsonElement e = JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (!e.isJsonArray()) return out;
            for (JsonElement item : e.getAsJsonArray()) {
                if (!item.isJsonObject()) continue;
                App a = App.fromJson(item.getAsJsonObject());
                if (a != null) out.add(a);
            }
        } catch (Exception ignored) {
            // a cache only
        }
        return out;
    }

    public static synchronized void save(Context context, List<App> apps) {
        try {
            JsonArray list = new JsonArray();
            for (App a : apps) list.add(a.toJson());
            File f = cacheFile(context);
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(f.getPath() + ".tmp");
            java.nio.file.Files.write(tmp.toPath(), list.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Exception ignored) {
            // a cache only
        }
    }

    /** The app on the phone's list (the store page read later replaces the search answer's words). */
    public static synchronized void remember(Context context, App app) {
        List<App> apps = cached(context);
        boolean found = false;
        for (int i = 0; i < apps.size(); i++) {
            App a = apps.get(i);
            if (a.owner.equalsIgnoreCase(app.owner) && a.repo.equalsIgnoreCase(app.repo)) {
                apps.set(i, app);
                found = true;
            }
        }
        if (!found) apps.add(app);
        save(context, apps);
    }

    /** The search answer merged with what the phone read before (store pages, pictures), in the answer's order. */
    static List<App> merge(List<App> found, List<App> before) {
        List<App> out = new ArrayList<>();
        for (App f : found) {
            App old = null;
            for (App b : before) {
                if (b.owner.equalsIgnoreCase(f.owner) && b.repo.equalsIgnoreCase(f.repo)) old = b;
            }
            if (old != null && old.listingRead && old.updated >= f.updated) {
                old.stars = f.stars;
                old.number = f.number != 0 ? f.number : old.number;
                old.branch = f.branch;
                out.add(old);
            } else {
                if (old != null) {
                    // pushed since: the store page is read again on the app's page, the old words shown until then
                    f.title = old.title;
                    f.video = old.video;
                    f.screenshots.addAll(old.screenshots);
                }
                out.add(f);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ publishing

    /** What the publish form gives. */
    public static final class Listing {
        public String title;
        public String description;
        public String video;
        /** JPEG files of the screenshots, in order (at most {@link #MAX_SHOTS}). */
        public final List<byte[]> screenshots = new ArrayList<>();
    }

    public interface Steps {
        void step(int percent, String text);
    }

    /**
     * Publishes the project repository {@code login/repo} to the store: public (the caller asked), the store topic
     * (the repository's own topics kept), Discussions on (the reviews), the pictures, then the store page. Returns a
     * note when the newest version carries no APK yet (the store offers the .swb alone then).
     */
    public static String publish(String token, String login, String repo, Listing listing, Steps steps)
            throws IOException, GitHubException {
        String base = "/repos/" + CloudBackup.enc(login) + "/" + CloudBackup.enc(repo);
        if (CloudBackup.REPO.equalsIgnoreCase(repo)) {
            throw new GitHubException("backup_repo", CloudBackup.REPO + " holds private backups: it is never published.");
        }
        steps.step(5, "Making the repository public…");
        JsonObject vis = new JsonObject();
        vis.addProperty("private", false);
        vis.addProperty("has_discussions", true);
        GitHubHttp.Response r = GitHubHttp.api("PATCH", base, token, vis.toString());
        if (!r.ok()) throw CloudBackup.http("Making " + repo + " public", r);

        steps.step(15, "Adding it to the Play Store…");
        GitHubHttp.Response t = GitHubHttp.api("GET", base + "/topics", token, null);
        JsonArray names = new JsonArray();
        java.util.LinkedHashSet<String> topics = new java.util.LinkedHashSet<>();
        topics.add(TOPIC);
        topics.add(ProjectRepo.TOPIC);
        if (t.ok()) {
            JsonElement old = DeviceFlow.parse(t.body).get("names");
            if (old != null && old.isJsonArray()) for (JsonElement e : old.getAsJsonArray()) topics.add(e.getAsString());
        }
        int count = 0;
        for (String topic : topics) if (count++ < 20) names.add(topic);
        JsonObject body = new JsonObject();
        body.add("names", names);
        GitHubHttp.Response put = GitHubHttp.api("PUT", base + "/topics", token, body.toString());
        if (!put.ok()) throw CloudBackup.http("Adding the Play Store topic", put);

        for (int i = 0; i < listing.screenshots.size() && i < MAX_SHOTS; i++) {
            steps.step(25 + 50 * i / Math.max(1, listing.screenshots.size()), "Uploading screenshot " + (i + 1) + " of "
                    + Math.min(MAX_SHOTS, listing.screenshots.size()) + "…");
            putBytes(token, base, SHOTS + "/" + String.format(Locale.ROOT, "%02d.jpg", i + 1), listing.screenshots.get(i),
                    "Play Store screenshot " + (i + 1));
        }
        steps.step(85, "Writing the store page…");
        JsonObject page = new JsonObject();
        page.addProperty("title", clip(listing.title == null ? displayName(repo) : listing.title.trim(), 80));
        page.addProperty("description", clip(listing.description == null ? "" : listing.description.trim(), 4000));
        String video = safeVideo(listing.video);
        if (video != null) page.addProperty("video", video);
        page.addProperty("made_with", "Sketchware AI");
        putBytes(token, base, LISTING, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(page)
                .getBytes(StandardCharsets.UTF_8), "Play Store page");

        steps.step(95, "Checking the newest version…");
        List<ProjectRepo.Version> versions = ProjectRepo.versions(token, login, repo, false);
        if (versions.isEmpty()) return "No version yet: back the project up so the store offers it.";
        ProjectRepo.Version front = ProjectRepo.ordered(versions).get(0);
        if (front.apk == null) {
            return "Its newest version " + front.label() + " has no APK: Run the project, then back it up, so the store "
                    + "can offer the APK too (the .swb is offered now).";
        }
        return null;
    }

    /** Writes one file through the contents API (replacing the one there). */
    static void putBytes(String token, String base, String path, byte[] bytes, String message)
            throws IOException, GitHubException {
        String sha = null;
        GitHubHttp.Response there = GitHubHttp.api("GET", base + "/contents/" + path, token, null);
        if (there.ok()) sha = DeviceFlow.string(DeviceFlow.parse(there.body), "sha");
        JsonObject body = new JsonObject();
        body.addProperty("message", "Sketchware AI: " + message);
        body.addProperty("content", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
        if (sha != null) body.addProperty("sha", sha);
        GitHubHttp.Response r = GitHubHttp.api("PUT", base + "/contents/" + path, token, body.toString());
        if (!r.ok()) throw CloudBackup.http("Writing " + path, r);
    }

    // ------------------------------------------------------------------ reviews (GitHub Discussions)

    /** One review or community post. */
    public static final class Post {
        public String id;
        public int number;
        public String title;
        public String body;
        public String author;
        public long time;
        public int comments;
        public String category;
        public String url;
    }

    static final String POSTS_QUERY = "query($owner:String!,$name:String!){repository(owner:$owner,name:$name){id "
            + "hasDiscussionsEnabled discussionCategories(first:20){nodes{id name}} "
            + "discussions(first:30,orderBy:{field:UPDATED_AT,direction:DESC}){nodes{id number title bodyText "
            + "author{login} createdAt comments{totalCount} category{name} url}}}}";

    /** The repository's discussions: {repositoryId, enabled ("true"/"false"), categories (id name ...)} and the posts. */
    public static List<Post> posts(String token, String owner, String repo, String[] info) throws IOException, GitHubException {
        JsonObject vars = new JsonObject();
        vars.addProperty("owner", owner);
        vars.addProperty("name", repo);
        JsonObject data = GitHubGraphQL.run(token, POSTS_QUERY, vars);
        JsonObject r = GitHubGraphQL.object(data, "repository");
        if (r == null) throw new GitHubException("not_found", owner + "/" + repo + " isn't there (or it is private).");
        info[0] = DeviceFlow.string(r, "id");
        info[1] = String.valueOf(r.has("hasDiscussionsEnabled") && r.get("hasDiscussionsEnabled").getAsBoolean());
        StringBuilder categories = new StringBuilder();
        for (JsonElement c : GitHubGraphQL.array(r, "discussionCategories.nodes")) {
            if (!c.isJsonObject()) continue;
            if (categories.length() > 0) categories.append('\n');
            categories.append(DeviceFlow.string(c.getAsJsonObject(), "id")).append('\t')
                    .append(DeviceFlow.string(c.getAsJsonObject(), "name"));
        }
        info[2] = categories.toString();
        List<Post> out = new ArrayList<>();
        for (JsonElement e : GitHubGraphQL.array(r, "discussions.nodes")) {
            if (!e.isJsonObject()) continue;
            JsonObject d = e.getAsJsonObject();
            Post p = new Post();
            p.id = DeviceFlow.string(d, "id");
            p.number = d.has("number") ? d.get("number").getAsInt() : 0;
            p.title = DeviceFlow.string(d, "title");
            p.body = DeviceFlow.string(d, "bodyText");
            JsonObject author = GitHubGraphQL.object(d, "author");
            p.author = author == null ? "ghost" : DeviceFlow.string(author, "login");
            p.time = ProjectRepo.isoTime(DeviceFlow.string(d, "createdAt"));
            JsonObject comments = GitHubGraphQL.object(d, "comments");
            p.comments = comments != null && comments.has("totalCount") ? comments.get("totalCount").getAsInt() : 0;
            JsonObject category = GitHubGraphQL.object(d, "category");
            p.category = category == null ? null : DeviceFlow.string(category, "name");
            p.url = DeviceFlow.string(d, "url");
            out.add(p);
        }
        return out;
    }

    static final String THREAD_QUERY = "query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,"
            + "name:$name){discussion(number:$number){id title bodyText author{login} createdAt url "
            + "comments(first:50){nodes{author{login} bodyText createdAt}}}}}";

    /** One discussion and its replies (the first entry is the discussion itself). */
    public static List<Post> thread(String token, String owner, String repo, int number) throws IOException, GitHubException {
        JsonObject vars = new JsonObject();
        vars.addProperty("owner", owner);
        vars.addProperty("name", repo);
        vars.addProperty("number", number);
        JsonObject d = GitHubGraphQL.object(GitHubGraphQL.run(token, THREAD_QUERY, vars), "repository.discussion");
        if (d == null) throw new GitHubException("not_found", "That discussion isn't there any more.");
        List<Post> out = new ArrayList<>();
        Post head = new Post();
        head.id = DeviceFlow.string(d, "id");
        head.number = number;
        head.title = DeviceFlow.string(d, "title");
        head.body = DeviceFlow.string(d, "bodyText");
        JsonObject author = GitHubGraphQL.object(d, "author");
        head.author = author == null ? "ghost" : DeviceFlow.string(author, "login");
        head.time = ProjectRepo.isoTime(DeviceFlow.string(d, "createdAt"));
        head.url = DeviceFlow.string(d, "url");
        out.add(head);
        for (JsonElement e : GitHubGraphQL.array(d, "comments.nodes")) {
            if (!e.isJsonObject()) continue;
            JsonObject c = e.getAsJsonObject();
            Post p = new Post();
            JsonObject a = GitHubGraphQL.object(c, "author");
            p.author = a == null ? "ghost" : DeviceFlow.string(a, "login");
            p.body = DeviceFlow.string(c, "bodyText");
            p.time = ProjectRepo.isoTime(DeviceFlow.string(c, "createdAt"));
            out.add(p);
        }
        return out;
    }

    /** A new discussion (a review, a community post); its number. */
    public static int newPost(String token, String repositoryId, String categoryId, String title, String body)
            throws IOException, GitHubException {
        JsonObject vars = new JsonObject();
        vars.addProperty("repo", repositoryId);
        vars.addProperty("category", categoryId);
        vars.addProperty("title", clip(title.trim(), 200));
        vars.addProperty("body", clip(body.trim(), 20000));
        JsonObject d = GitHubGraphQL.object(GitHubGraphQL.run(token, "mutation($repo:ID!,$category:ID!,$title:String!,"
                + "$body:String!){createDiscussion(input:{repositoryId:$repo,categoryId:$category,title:$title,body:$body})"
                + "{discussion{number}}}", vars), "createDiscussion.discussion");
        return d != null && d.has("number") ? d.get("number").getAsInt() : 0;
    }

    /** A reply to a discussion. */
    public static void reply(String token, String discussionId, String body) throws IOException, GitHubException {
        JsonObject vars = new JsonObject();
        vars.addProperty("id", discussionId);
        vars.addProperty("body", clip(body.trim(), 20000));
        GitHubGraphQL.run(token, "mutation($id:ID!,$body:String!){addDiscussionComment(input:{discussionId:$id,body:$body})"
                + "{comment{id}}}", vars);
    }

    /** The category a review goes into: "Reviews" when there is one, else General, else the first. */
    static String reviewCategory(String categories) {
        String general = null;
        String first = null;
        for (String line : categories.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String id = line.substring(0, tab);
            String name = line.substring(tab + 1).toLowerCase(Locale.ROOT);
            if (first == null) first = id;
            if (name.startsWith("review")) return id;
            if (name.equals("general")) general = id;
        }
        return general != null ? general : first;
    }
}
