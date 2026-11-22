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
        /** v2.1.1: the published version and how it was published (from the store page). */
        public String versionName;
        public String versionCode;
        /** 1 = the first publish; every Publish adds one. */
        public int publishNumber;
        /** "published" (first), "republished" (the same version code again), "updated" (a new version code). */
        public String action;
        public String changelog;
        public long publishedAt;
        /** The Discussion whose reactions count likes, views and shares (0: none yet). */
        public int statsDiscussion;

        public String name() {
            return title != null && !title.isEmpty() ? title : displayName(repo);
        }

        /** The app's icon on GitHub's file host ({@link RepoIcons#PATH} of its default branch). */
        public String iconUrl() {
            return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + RepoIcons.PATH;
        }

        /** "Re-published v4.7.6 (84) • Update #3", "Updated to v4.7.7 (85)", "v1.0 (1)", or null when unknown. */
        public String status() {
            return statusOf(versionName, versionCode, action, publishNumber);
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
            a.versionName = versionName;
            a.versionCode = versionCode;
            a.publishNumber = publishNumber;
            a.action = action;
            a.changelog = changelog;
            a.publishedAt = publishedAt;
            a.statsDiscussion = statsDiscussion;
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
            o.addProperty("version_name", versionName);
            o.addProperty("version_code", versionCode);
            o.addProperty("publish_number", publishNumber);
            o.addProperty("action", action);
            o.addProperty("changelog", changelog);
            o.addProperty("published_at", publishedAt);
            o.addProperty("stats_discussion", statsDiscussion);
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
            readVersionFields(a, o);
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

    /**
     * v2.1.1 (owner's 21 Nov directive): the version line of a card or an app page: "Re-published v4.7.6 (84) • Update
     * #3" (the same version code published again), "Updated to v4.7.7 (85)" (a new version code), "v1.0 (1)" (the first
     * publish); null when unknown.
     */
    static String statusOf(String versionName, String versionCode, String action, int publishNumber) {
        if (versionName == null || versionName.isEmpty()) return null;
        String label = "v" + versionName + (versionCode == null || versionCode.isEmpty() ? "" : " (" + versionCode + ")");
        if ("republished".equals(action)) return "Re-published " + label + " • Update #" + Math.max(2, publishNumber);
        if ("updated".equals(action)) return "Updated to " + label;
        return label;
    }

    /** The version fields of a store page (or of the phone's copy) into {@code a}. */
    static void readVersionFields(App a, JsonObject o) {
        String vn = DeviceFlow.string(o, "version_name");
        if (vn != null) a.versionName = clip(vn, 40);
        String vc = DeviceFlow.string(o, "version_code");
        if (vc != null) a.versionCode = clip(vc, 20);
        a.publishNumber = intOf(o, "publish_number");
        String action = DeviceFlow.string(o, "action");
        a.action = "republished".equals(action) || "updated".equals(action) || "published".equals(action) ? action : null;
        String changelog = DeviceFlow.string(o, "changelog");
        a.changelog = changelog == null ? null : clip(changelog, 4000);
        a.publishedAt = longOf(o, "published_at");
        a.statsDiscussion = intOf(o, "stats_discussion");
    }

    static int intOf(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static long longOf(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** A profile picture: GitHub's avatar host only, else null. */
    static String safeAvatar(String url) {
        if (url == null) return null;
        return url.startsWith("https://avatars.githubusercontent.com/") && !url.contains("..") ? url : null;
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
        return search(token, null, null);
    }

    /**
     * v2.1.1: the store's apps matching {@code text} (name and description) and made by {@code user}, both optional.
     */
    public static List<App> search(String token, String text, String user) throws IOException, GitHubException {
        return searchRepos(token, "topic:" + TOPIC, text, user, "Reading the Play Store");
    }

    static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Public repositories of a search ({@code first} = the topic qualifier). */
    static List<App> searchRepos(String token, String first, String text, String user, String what)
            throws IOException, GitHubException {
        StringBuilder q = new StringBuilder(first);
        if (user != null && user.matches("[A-Za-z0-9-]{1,39}")) q.append(" user:").append(user);
        if (text != null && !text.trim().isEmpty()) q.append(' ').append(clip(text.trim(), 80)).append(" in:name,description");
        GitHubHttp.Response r = GitHubHttp.api("GET", "/search/repositories?q=" + encode(q.toString())
                + "&sort=updated&order=desc&per_page=50", token, null);
        if (!r.ok()) throw CloudBackup.http(what, r);
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
                    readVersionFields(app, listing);
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
        File f = cacheFile(context);
        // v2.1.2 (zero-lag): the Play Store tab opens from memory; the file is parsed once per version of it
        long stamp = f.lastModified();
        long size = f.length();
        Object[] memo = cachedMemo;
        if (memo != null && (Long) memo[0] == stamp && (Long) memo[1] == size) {
            List<App> copy = new ArrayList<>();
            for (Object o : (List<?>) memo[2]) copy.add(((App) o).copy());
            return copy;
        }
        List<App> out = readCached(f);
        List<App> kept = new ArrayList<>();
        for (App a : out) kept.add(a.copy());
        cachedMemo = new Object[]{stamp, size, kept};
        return out;
    }

    private static volatile Object[] cachedMemo;

    private static List<App> readCached(File f) {
        List<App> out = new ArrayList<>();
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

    // ------------------------------------------------------------------ v2.1.4: just published

    /** v2.1.4: how long an app published from this phone stays first and marked "Just published". */
    static final long RECENT_MILLIS = 24 * 3600_000L;
    private static final String RECENT_PREFS = "playstore_recent";
    /** v2.1.4: the app the list shows first and lit after a publish's Done ("owner/repo" in lower case), once. */
    private static volatile String highlight;

    private static String key(String owner, String repo) {
        return (owner + "/" + repo).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * v2.1.4 (owner's 22 Nov review: "after publishing, Done should go to the Play Store and show the app at the top,
     * as in the list"): the published app goes first into the phone's copy of the store at once (GitHub's search may
     * take minutes to list a newly public repository), with its new words; its store page is read again when opened.
     */
    public static synchronized void notePublished(Context context, String owner, String repo, long number, String title,
                                                  String description) {
        if (owner == null || repo == null) return;
        List<App> apps = cached(context);
        App mine = null;
        for (java.util.Iterator<App> it = apps.iterator(); it.hasNext(); ) {
            App a = it.next();
            if (a.owner.equalsIgnoreCase(owner) && a.repo.equalsIgnoreCase(repo)) {
                mine = a;
                it.remove();
            }
        }
        if (mine == null) {
            mine = new App();
            mine.owner = owner;
            mine.repo = repo;
        }
        long now = System.currentTimeMillis();
        if (number != 0) mine.number = number;
        if (title != null && !title.trim().isEmpty()) mine.title = title.trim();
        if (description != null && !description.trim().isEmpty()) mine.description = description.trim();
        mine.updated = now;
        mine.listingRead = false;
        apps.add(0, mine);
        save(context, apps);
        context.getApplicationContext().getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(key(owner, repo), now).apply();
    }

    /** v2.1.4: published from this phone in the last day. */
    public static boolean recentlyPublished(Context context, App a) {
        long at = context.getApplicationContext().getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
                .getLong(key(a.owner, a.repo), 0);
        return at > 0 && System.currentTimeMillis() - at < RECENT_MILLIS;
    }

    /**
     * v2.1.4: {@code list} with the apps published from this phone in the last day first (newest first), also those
     * GitHub's search doesn't list yet (from the phone's copy).
     */
    static List<App> withRecent(Context context, List<App> list) {
        java.util.Map<String, ?> marks = context.getApplicationContext().getSharedPreferences(RECENT_PREFS,
                Context.MODE_PRIVATE).getAll();
        if (marks.isEmpty()) return list;
        long now = System.currentTimeMillis();
        List<App> recent = new ArrayList<>();
        List<App> rest = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (App a : list) {
            Object at = marks.get(key(a.owner, a.repo));
            seen.add(key(a.owner, a.repo));
            if (at instanceof Long && now - (Long) at < RECENT_MILLIS) recent.add(a);
            else rest.add(a);
        }
        for (App a : cached(context)) {
            String k = key(a.owner, a.repo);
            Object at = marks.get(k);
            if (!seen.contains(k) && at instanceof Long && now - (Long) at < RECENT_MILLIS) {
                recent.add(a);
                seen.add(k);
            }
        }
        recent.sort((x, y) -> {
            Object ax = marks.get(key(x.owner, x.repo));
            Object ay = marks.get(key(y.owner, y.repo));
            return Long.compare(ay instanceof Long ? (Long) ay : 0, ax instanceof Long ? (Long) ax : 0);
        });
        recent.addAll(rest);
        return recent;
    }

    /** v2.1.4 review (LOW): made private again: not kept first or listed before GitHub's search says so. */
    static void forgetRecent(Context context, String owner, String repo) {
        if (owner == null || repo == null) return;
        context.getApplicationContext().getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE).edit()
                .remove(key(owner, repo)).apply();
    }

    /**
     * v2.1.4 review (LOW): GitHub's answer merged into the phone's copy and saved in one step under the same lock as
     * {@link #notePublished} (a refresh that read the copy before a publish can't save over the new entry).
     */
    static synchronized List<App> refreshCache(Context context, List<App> found) {
        List<App> merged = withRecent(context, merge(found, cached(context)));
        save(context, merged);
        return merged;
    }

    static void highlight(String owner, String repo) {
        highlight = key(owner, repo);
    }

    /** The app to light up once (after a publish's Done), else null. */
    static String takeHighlight() {
        String h = highlight;
        highlight = null;
        return h;
    }

    static boolean is(App a, String ownerSlashRepo) {
        return ownerSlashRepo != null && ownerSlashRepo.equals(key(a.owner, a.repo));
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
        /** v2.1.1: the app's own icon (PNG), or null. */
        public byte[] icon;

        /**
         * v2.1.1 (owner's 21 Nov report: pictures gone after Sign in again): the publish form kept on the phone, in
         * {@code dir}: {@code form.json} (title, description, video) and the pictures {@code NN.jpg}.
         */
        public static Listing load(File dir) {
            Listing l = loadForm(dir);
            for (File f : stagedPictures(dir)) {
                try {
                    l.screenshots.add(java.nio.file.Files.readAllBytes(f.toPath()));
                } catch (IOException ignored) {
                    // left out
                }
            }
            return l;
        }

        /** The form's words only (no pictures read): quick enough for the main thread. */
        public static Listing loadForm(File dir) {
            Listing l = new Listing();
            File form = new File(dir, "form.json");
            if (form.isFile() && form.length() < 64 * 1024) {
                try {
                    JsonObject o = DeviceFlow.parse(new String(java.nio.file.Files.readAllBytes(form.toPath()),
                            StandardCharsets.UTF_8));
                    l.title = DeviceFlow.string(o, "title");
                    l.description = DeviceFlow.string(o, "description");
                    l.video = DeviceFlow.string(o, "video");
                } catch (IOException | RuntimeException ignored) {
                    // an empty form
                }
            }
            return l;
        }

        /** The form's words into {@code dir} (atomic). */
        public void saveForm(File dir) throws IOException {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
            JsonObject o = new JsonObject();
            o.addProperty("title", title);
            o.addProperty("description", description);
            o.addProperty("video", video);
            File form = new File(dir, "form.json");
            File tmp = new File(dir, "form.json.tmp");
            java.nio.file.Files.write(tmp.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(form)) throw new IOException("Couldn't save " + form);
        }
    }

    /** The staged pictures of a publish form, in order ({@code 01.jpg} …). */
    public static List<File> stagedPictures(File dir) {
        List<File> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.matches("\\d{2}\\.jpg"));
        if (files == null) return out;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : files) if (out.size() < MAX_SHOTS) out.add(f);
        return out;
    }

    /**
     * Adds a picture to the form as the next {@code NN.jpg} (written to a temp file, then renamed); false when the form
     * has {@link #MAX_SHOTS} already.
     */
    public static synchronized boolean stagePicture(File dir, byte[] jpeg) throws IOException {
        List<File> have = stagedPictures(dir);
        if (have.size() >= MAX_SHOTS) return false;
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
        renumber(dir);
        File target = new File(dir, String.format(Locale.ROOT, "%02d.jpg", stagedPictures(dir).size() + 1));
        File tmp = new File(dir, target.getName() + ".tmp");
        java.nio.file.Files.write(tmp.toPath(), jpeg);
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Couldn't save " + target);
        }
        return true;
    }

    /** Removes the form's picture at {@code index}; the others keep their order as 01.jpg, 02.jpg … */
    public static synchronized void unstagePicture(File dir, int index) {
        List<File> have = stagedPictures(dir);
        if (index < 0 || index >= have.size()) return;
        //noinspection ResultOfMethodCallIgnored
        have.get(index).delete();
        renumber(dir);
    }

    /** 01.jpg, 02.jpg … without gaps, in the same order (each name only ever moves down: no clash). */
    static void renumber(File dir) {
        List<File> have = stagedPictures(dir);
        for (int i = 0; i < have.size(); i++) {
            File want = new File(dir, String.format(Locale.ROOT, "%02d.jpg", i + 1));
            if (!have.get(i).getName().equals(want.getName())) //noinspection ResultOfMethodCallIgnored
                have.get(i).renameTo(want);
        }
    }

    /** The staging folder of a publish form for repository {@code number} (filesDir/publish_staging/<number>). */
    public static File stagingDir(Context context, long number) {
        return new File(context.getApplicationContext().getFilesDir(), "publish_staging/" + number);
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

        steps.step(12, "Adding it to the Play Store…");
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

        // v2.1.1: the app's own icon (cards and the app page show it)
        if (listing.icon != null) {
            steps.step(18, "Uploading the app icon…");
            RepoIcons.upload(token, login, repo, listing.icon);
        }
        int shots = Math.min(MAX_SHOTS, listing.screenshots.size());
        for (int i = 0; i < shots; i++) {
            steps.step(22 + 48 * i / Math.max(1, shots), "Uploading screenshot " + (i + 1) + " of " + shots + "…");
            putBytes(token, base, SHOTS + "/" + String.format(Locale.ROOT, "%02d.jpg", i + 1), listing.screenshots.get(i),
                    "Play Store screenshot " + (i + 1));
        }
        // review (LOW): a publish with fewer pictures leaves none of the older ones behind; v2.1.1 review (HIGH): a
        // publish without any picture keeps the store's pictures as they are (never "remove them all")
        if (shots > 0) removeExtraShots(token, base, shots);

        steps.step(74, "Reading the newest version…");
        List<ProjectRepo.Version> versions = ProjectRepo.versions(token, login, repo, false);
        ProjectRepo.Version front = versions.isEmpty() ? null : ProjectRepo.ordered(versions).get(0);
        JsonObject before = readPage(token, base);
        steps.step(80, "Setting up likes, views and shares…");
        int stats = before == null ? 0 : intOf(before, "stats_discussion");
        try {
            stats = ensureStatsDiscussion(token, login, repo, stats, listing.title == null ? displayName(repo) : listing.title);
        } catch (IOException | GitHubException e) {
            // the store page works without the counters (the next publish sets them up)
            mod.jbk.util.LogUtil.d("PlayStore", "No stats discussion now: " + e);
        }

        steps.step(88, "Writing the store page…");
        JsonObject page = page(listing, repo, front, before, stats, System.currentTimeMillis());
        putBytes(token, base, LISTING, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(page)
                .getBytes(StandardCharsets.UTF_8), "Play Store page " + DeviceFlow.string(page, "version_name"));

        steps.step(97, "Checking the newest version…");
        if (front == null) return "No version yet: back the project up so the store offers it.";
        if (front.apk == null) {
            return "Its newest version " + front.label() + " has no APK: Run the project, then back it up, so the store "
                    + "can offer the APK too (the .swb is offered now).";
        }
        return null;
    }

    /**
     * v2.1.1 (owner's 21 Nov directive "Re-published vX.Y • Update #N / Updated to vX.Y"): the store page: the form's
     * words, the newest version, how it compares with the page before (first publish, the same version code again, a
     * new one), the publish count, the version's notes as the changelog, the stats discussion.
     */
    static JsonObject page(Listing listing, String repo, ProjectRepo.Version front, JsonObject before, int stats, long now) {
        JsonObject page = new JsonObject();
        page.addProperty("title", clip(listing.title == null || listing.title.trim().isEmpty() ? displayName(repo)
                : listing.title.trim(), 80));
        page.addProperty("description", clip(listing.description == null ? "" : listing.description.trim(), 4000));
        String video = safeVideo(listing.video);
        if (video != null) page.addProperty("video", video);
        page.addProperty("made_with", "Sketchware AI");
        int number = (before == null ? 0 : intOf(before, "publish_number")) + 1;
        page.addProperty("publish_number", number);
        page.addProperty("published_at", now);
        if (front != null) {
            String code = front.versionCode == null ? "" : front.versionCode;
            page.addProperty("version_name", front.versionName == null ? "" : front.versionName);
            page.addProperty("version_code", code);
            String beforeCode = before == null ? null : DeviceFlow.string(before, "version_code");
            String action = before == null || beforeCode == null ? "published"
                    : beforeCode.equals(code) ? "republished" : "updated";
            page.addProperty("action", action);
            if (front.notes != null && !front.notes.trim().isEmpty()) page.addProperty("changelog", clip(front.notes.trim(), 4000));
        }
        if (stats > 0) page.addProperty("stats_discussion", stats);
        return page;
    }

    /** The store page as it is on GitHub, or null. */
    static JsonObject readPage(String token, String base) throws IOException {
        GitHubHttp.Response r = GitHubHttp.api("GET", base + "/contents/" + LISTING, token, null);
        if (!r.ok()) return null;
        String content = DeviceFlow.string(DeviceFlow.parse(r.body), "content");
        if (content == null) return null;
        return parseListing(new String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), StandardCharsets.UTF_8));
    }

    /** Deletes the store's pictures numbered above {@code kept}. */
    static void removeExtraShots(String token, String base, int kept) throws IOException, GitHubException {
        GitHubHttp.Response list = GitHubHttp.api("GET", base + "/contents/" + SHOTS, token, null);
        if (!list.ok()) return;
        JsonElement files = JsonParser.parseString(list.body);
        if (!files.isJsonArray()) return;
        for (JsonElement e : files.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            String name = DeviceFlow.string(e.getAsJsonObject(), "name");
            String sha = DeviceFlow.string(e.getAsJsonObject(), "sha");
            if (name == null || sha == null || !name.matches("\\d{2}\\.jpg")) continue;
            if (Integer.parseInt(name.substring(0, 2)) <= kept) continue;
            JsonObject body = new JsonObject();
            body.addProperty("message", "Sketchware AI: Play Store screenshot removed");
            body.addProperty("sha", sha);
            GitHubHttp.Response d = GitHubHttp.api("DELETE", base + "/contents/" + SHOTS + "/" + name, token, body.toString());
            if (!d.ok() && d.code != 404) throw CloudBackup.http("Removing an old screenshot", d);
        }
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
        /** v2.1.1: the repository the post is in (the Community feed mixes several). */
        public String owner;
        public String repo;
        public long updated;
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

    // ------------------------------------------------------------------ v2.1.1: stats (downloads, likes, views, shares)

    /** 1234 → "1.2K", 2500000 → "2.5M"; -1 → "–" (not known). */
    public static String count(long n) {
        if (n < 0) return "–";
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return trim(n / 1000.0) + "K";
        return trim(n / 1_000_000.0) + "M";
    }

    private static String trim(double d) {
        String s = d >= 100 ? Long.toString(Math.round(d)) : String.format(Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** The phone's filter: the name, the creator ("@name" too) and the description. */
    static boolean matches(App a, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        String f = filter.startsWith("@") ? filter.substring(1) : filter;
        if (f.isEmpty()) return true;
        if (a.owner != null && a.owner.toLowerCase(Locale.ROOT).contains(f)) return true;
        if (filter.startsWith("@")) return false;
        return a.name().toLowerCase(Locale.ROOT).contains(f)
                || a.description != null && a.description.toLowerCase(Locale.ROOT).contains(f);
    }

    // ------------------------------------------------------------------ v2.1.3: Follow

    /**
     * v2.1.3 (owner's 22 Nov directive: "a Follow pill next to the version, Follow / Following"): whether the signed-in
     * account follows {@code login} on GitHub (GET /user/following/{login}: 204 yes, 404 no); null when unknown.
     */
    static Boolean following(String token, String login) throws IOException {
        GitHubHttp.Response r = GitHubHttp.api("GET", "/user/following/" + CloudBackup.enc(login), token, null);
        if (r.code == 204) return Boolean.TRUE;
        if (r.code == 404) return Boolean.FALSE;
        return null;
    }

    /**
     * Follows or unfollows {@code login} (PUT / DELETE /user/following/{login}). GitHub lets a sign-in do it only with
     * the {@code user:follow} permission, which sign-ins made before v2.1.3 don't have: then a GitHubException with code
     * "scope" (the page asks for Sign in again).
     */
    static void follow(String token, String login, boolean on) throws IOException, GitHubException {
        GitHubHttp.Response r = GitHubHttp.api(on ? "PUT" : "DELETE", "/user/following/" + CloudBackup.enc(login), token,
                on ? "" : null);
        if (r.code == 204) return;
        if (lacksFollowScope(r)) {
            throw new GitHubException("scope", "Following needs one more GitHub permission (follow accounts) that your "
                    + "sign-in from before v2.1.3 doesn't have: tap Sign in again on Settings' account card, then Follow.");
        }
        throw CloudBackup.http(on ? "Following @" + login : "Unfollowing @" + login, r);
    }

    /** A 403 / 404 from a sign-in whose permissions (X-OAuth-Scopes) don't include following. */
    static boolean lacksFollowScope(GitHubHttp.Response r) {
        if (r.code != 403 && r.code != 404) return false;
        if (r.scopes == null) return r.code == 404;
        for (String scope : r.scopes.split(",")) {
            String t = scope.trim();
            if (t.equals("user") || t.equals("user:follow")) return false;
        }
        return true;
    }

    /**
     * GitHub's download counts of the versions' APK and .swb files. v2.1.3: a version whose backup is in parts (no .swb
     * file) counts its parts list once per import (every import reads it once); a version with a .swb counts the .swb
     * (older backups wrote a parts list beside a single file too).
     */
    static long downloadsOf(List<ProjectRepo.Version> versions) {
        long total = 0;
        for (ProjectRepo.Version ver : versions) {
            boolean single = false;
            for (ProjectRepo.Asset x : ver.assets) if (x.name != null && x.name.endsWith(".swb")) single = true;
            for (ProjectRepo.Asset x : ver.assets) {
                if (x.name == null) continue;
                if (x.name.endsWith(".apk") || x.name.endsWith(".swb") || (!single && SwbParts.isManifest(x.name))) {
                    total += Math.max(0, x.downloads);
                }
            }
        }
        return total;
    }


    /** An app page's numbers; -1 = not known (signed out, no stats discussion yet, no network). */
    public static final class Stats {
        public String subjectId;
        public long downloads = -1;
        public int likes = -1;
        public int views = -1;
        public int shares = -1;
        public boolean liked;
        public boolean viewed;
        public boolean shared;
    }

    static final String LIKE = "HEART";
    static final String VIEW = "EYES";
    static final String SHARE = "ROCKET";

    /** Downloads = GitHub's download counts of the published versions' APK and .swb files (any visitor reads them). */
    public static long downloads(String token, App app) throws IOException, GitHubException {
        return downloadsOf(ProjectRepo.versions(token, app.owner, app.repo, false));
    }

    static final String STATS_QUERY = "query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,"
            + "name:$name){discussion(number:$number){id reactionGroups{content viewerHasReacted reactors{totalCount}}}}}";

    /**
     * Likes (❤️), views (👀) and shares (🚀) = the GitHub accounts that reacted so on the app's stats discussion: each
     * account counts once. Needs a sign-in (GraphQL).
     */
    public static void reactions(String token, App app, Stats stats) throws IOException, GitHubException {
        if (token == null || app.statsDiscussion <= 0) return;
        JsonObject vars = new JsonObject();
        vars.addProperty("owner", app.owner);
        vars.addProperty("name", app.repo);
        vars.addProperty("number", app.statsDiscussion);
        JsonObject d = GitHubGraphQL.object(GitHubGraphQL.run(token, STATS_QUERY, vars), "repository.discussion");
        if (d == null) return;
        stats.subjectId = DeviceFlow.string(d, "id");
        stats.likes = 0;
        stats.views = 0;
        stats.shares = 0;
        for (JsonElement e : GitHubGraphQL.array(d, "reactionGroups")) {
            if (!e.isJsonObject()) continue;
            JsonObject g = e.getAsJsonObject();
            String content = DeviceFlow.string(g, "content");
            JsonObject reactors = GitHubGraphQL.object(g, "reactors");
            int n = reactors == null ? 0 : intOf(reactors, "totalCount");
            boolean mine = g.has("viewerHasReacted") && g.get("viewerHasReacted").getAsBoolean();
            if (LIKE.equals(content)) {
                stats.likes = n;
                stats.liked = mine;
            } else if (VIEW.equals(content)) {
                stats.views = n;
                stats.viewed = mine;
            } else if (SHARE.equals(content)) {
                stats.shares = n;
                stats.shared = mine;
            }
        }
    }

    /** Adds (or takes back) this account's reaction {@code content} on the stats discussion {@code subjectId}. */
    public static void react(String token, String subjectId, String content, boolean add) throws IOException, GitHubException {
        if (!LIKE.equals(content) && !VIEW.equals(content) && !SHARE.equals(content)) throw new IllegalArgumentException(content);
        JsonObject vars = new JsonObject();
        vars.addProperty("id", subjectId);
        vars.addProperty("content", content);
        GitHubGraphQL.run(token, "mutation($id:ID!,$content:ReactionContent!){" + (add ? "addReaction" : "removeReaction")
                + "(input:{subjectId:$id,content:$content}){reaction{content}}}", vars);
    }

    static final String REPO_INFO_QUERY = "query($owner:String!,$name:String!){repository(owner:$owner,name:$name){id "
            + "hasDiscussionsEnabled discussionCategories(first:20){nodes{id name}}}}";

    /**
     * The app's stats discussion: {@code existing} when it is still there, else a new one in General (else the first
     * category) titled "📊 <title> · Play Store". Its number.
     */
    static int ensureStatsDiscussion(String token, String owner, String repo, int existing, String title)
            throws IOException, GitHubException {
        JsonObject vars = new JsonObject();
        vars.addProperty("owner", owner);
        vars.addProperty("name", repo);
        if (existing > 0) {
            JsonObject v2 = vars.deepCopy();
            v2.addProperty("number", existing);
            // review (LOW): a deleted post answers with an error next to a null discussion: then a new one is made
            JsonObject d = GitHubGraphQL.object(GitHubGraphQL.runLenient(token, "query($owner:String!,$name:String!,"
                    + "$number:Int!){repository(owner:$owner,name:$name){discussion(number:$number){id}}}", v2),
                    "repository.discussion");
            if (d != null) return existing;
        }
        JsonObject r = GitHubGraphQL.object(GitHubGraphQL.run(token, REPO_INFO_QUERY, vars), "repository");
        if (r == null) return 0;
        StringBuilder categories = new StringBuilder();
        for (JsonElement c : GitHubGraphQL.array(r, "discussionCategories.nodes")) {
            if (!c.isJsonObject()) continue;
            if (categories.length() > 0) categories.append('\n');
            categories.append(DeviceFlow.string(c.getAsJsonObject(), "id")).append('\t')
                    .append(DeviceFlow.string(c.getAsJsonObject(), "name"));
        }
        String category = generalCategory(categories.toString());
        if (category == null) return 0;
        // second review (LOW): a stats post already there (one GitHub answered for with another error) is used again
        try {
            for (Post p : posts(token, owner, repo, new String[3])) {
                if (p.title != null && p.title.startsWith("📊 ") && p.number > 0) return p.number;
            }
        } catch (IOException | GitHubException ignored) {
            // a new one then
        }
        return newPost(token, DeviceFlow.string(r, "id"), category, "📊 " + clip(title, 120) + " · Play Store",
                "Reactions on this post are the app's counters in Sketchware AI's Play Store: ❤️ likes, 👀 views, 🚀 "
                        + "shares (one per GitHub account). Please keep this post.");
    }

    /** General, else the first category that isn't Announcements, else the first. */
    static String generalCategory(String categories) {
        String first = null;
        String notAnnouncements = null;
        for (String line : categories.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String id = line.substring(0, tab);
            String name = line.substring(tab + 1).toLowerCase(Locale.ROOT);
            if (first == null) first = id;
            if (name.equals("general")) return id;
            if (notAnnouncements == null && !name.startsWith("announcement")) notAnnouncements = id;
        }
        return notAnnouncements != null ? notAnnouncements : first;
    }

    // ------------------------------------------------------------------ v2.1.1: developer profile

    public static final class Developer {
        public String login;
        public String name;
        public String bio;
        public String avatar;
        public int followers;
        public int publicRepos;
        /** v2.1.4: the profile's own Location line and when the account was made (0: unknown). */
        public String location;
        public long createdAt;
        public final List<App> apps = new ArrayList<>();
        public final List<App> projects = new ArrayList<>();
    }

    /** v2.1.4: a GitHub account's public profile (GET /users/{login}). */
    public static final class Profile {
        public String login;
        public String name;
        public String bio;
        public String location;
        public int followers;
        public int publicRepos;
        public long createdAt;
    }

    /** v2.1.4: the account's public profile: followers for the Follow pill, the rest for the creator's badge. */
    public static Profile profile(String token, String login) throws IOException, GitHubException {
        if (login == null || !login.matches("[A-Za-z0-9-]{1,39}")) throw new GitHubException("bad_login", "No such account.");
        GitHubHttp.Response r = GitHubHttp.api("GET", "/users/" + login, token, null);
        if (r.code == 404) throw new GitHubException("not_found", "@" + login + " isn't on GitHub (any more).");
        if (!r.ok()) throw CloudBackup.http("Reading @" + login, r);
        JsonObject u = DeviceFlow.parse(r.body);
        Profile p = new Profile();
        p.login = DeviceFlow.string(u, "login");
        if (p.login == null) p.login = login;
        p.name = DeviceFlow.string(u, "name");
        p.bio = DeviceFlow.string(u, "bio");
        p.location = DeviceFlow.string(u, "location");
        p.followers = intOf(u, "followers");
        p.publicRepos = intOf(u, "public_repos");
        p.createdAt = time(DeviceFlow.string(u, "created_at"));
        return p;
    }

    /** "2019-04-01T10:00:00Z" → milliseconds, else 0. */
    static long time(String iso) {
        if (iso == null) return 0;
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date d = f.parse(iso);
            return d == null ? 0 : d.getTime();
        } catch (java.text.ParseException | RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ v2.1.4: creator badges

    /**
     * v2.1.4 (owner's 22 Nov review: "verified users, non-verified users and trusted users should be known"): a
     * creator's level, worked out from the account's public GitHub profile and the store (GitHub has no "verified
     * person" mark for users, so the rules are the app's own and are shown with the badge).
     */
    public static final class Badge {
        public static final int NOT_VERIFIED = 0;
        public static final int VERIFIED = 1;
        public static final int TRUSTED = 2;
        public static final int OFFICIAL = 3;
        public final int level;
        public final String label;
        /** Why this level (what is missing for the next one). */
        public final String why;

        Badge(int level, String label, String why) {
            this.level = level;
            this.label = label;
            this.why = why;
        }
    }

    public static final long DAY_MILLIS = 24 * 3600_000L;

    /** How the levels are worked out (shown when a badge is tapped). */
    public static final String BADGE_RULES = "Worked out by Sketchware AI from the creator's public GitHub profile and "
            + "the store (GitHub has no \"verified person\" mark for accounts):\n"
            + "• ✔ Verified: a GitHub account older than 30 days, with a public name or bio and at least one app in the "
            + "store.\n"
            + "• ★ Trusted: Verified, older than a year, at least two apps, and 10 or more followers or 100 or more "
            + "downloads.\n"
            + "• Not verified: a newer account or no app yet. It doesn't mean anything bad: check what an app asks "
            + "for before installing it.";

    public static Badge badge(String login, String name, String bio, long createdAt, int apps, int followers,
                              long downloads, long now) {
        if (COMMUNITY_OWNER.equalsIgnoreCase(login)) {
            return new Badge(Badge.OFFICIAL, "✔ Official", "The Sketchware AI account.");
        }
        long age = createdAt > 0 ? now - createdAt : -1;
        boolean named = (name != null && !name.trim().isEmpty()) || (bio != null && !bio.trim().isEmpty());
        boolean verified = age >= 30 * DAY_MILLIS && named && apps >= 1;
        if (!verified) {
            String missing;
            if (age < 0) missing = "the account's age isn't known";
            else if (age < 30 * DAY_MILLIS) missing = "the GitHub account is " + Math.max(0, age / DAY_MILLIS) + " days old";
            else if (!named) missing = "the profile has no public name or bio";
            else missing = "no app in the store yet";
            return new Badge(Badge.NOT_VERIFIED, "Not verified", "Not verified: " + missing + ".");
        }
        boolean trusted = age >= 365 * DAY_MILLIS && apps >= 2 && (followers >= 10 || downloads >= 100);
        if (trusted) {
            return new Badge(Badge.TRUSTED, "★ Trusted", "Trusted: an account of " + (age / (365 * DAY_MILLIS))
                    + "+ years with " + apps + " apps" + (followers >= 10 ? " and " + followers + " followers" : " and "
                    + downloads + " downloads") + ".");
        }
        return new Badge(Badge.VERIFIED, "✔ Verified", "Verified: an account of " + (age / DAY_MILLIS) + " days with "
                + apps + (apps == 1 ? " app" : " apps") + " in the store.");
    }

    /** A creator's page: the account (GitHub's profile), the store apps and the other public projects. */
    public static Developer developer(String token, String login) throws IOException, GitHubException {
        if (login == null || !login.matches("[A-Za-z0-9-]{1,39}")) throw new GitHubException("bad_login", "No such account.");
        GitHubHttp.Response r = GitHubHttp.api("GET", "/users/" + login, token, null);
        if (r.code == 404) throw new GitHubException("not_found", "@" + login + " isn't on GitHub (any more).");
        if (!r.ok()) throw CloudBackup.http("Reading @" + login, r);
        JsonObject u = DeviceFlow.parse(r.body);
        Developer d = new Developer();
        d.login = DeviceFlow.string(u, "login");
        if (d.login == null) d.login = login;
        d.name = DeviceFlow.string(u, "name");
        d.bio = DeviceFlow.string(u, "bio");
        d.avatar = safeAvatar(DeviceFlow.string(u, "avatar_url"));
        d.followers = intOf(u, "followers");
        d.publicRepos = intOf(u, "public_repos");
        d.location = DeviceFlow.string(u, "location");
        d.createdAt = time(DeviceFlow.string(u, "created_at"));
        d.apps.addAll(searchRepos(token, "topic:" + TOPIC, null, d.login, "Reading @" + login + "'s apps"));
        java.util.Set<Long> inStore = new java.util.HashSet<>();
        for (App a : d.apps) inStore.add(a.number);
        for (App p : searchRepos(token, "topic:" + ProjectRepo.TOPIC, null, d.login, "Reading @" + login + "'s projects")) {
            if (!inStore.contains(p.number)) d.projects.add(p);
        }
        return d;
    }

    // ------------------------------------------------------------------ v2.1.1: the Community feed

    /** The official community repository (its Discussions join the feed when they are on). */
    public static final String COMMUNITY_OWNER = "smartapps000-hub";
    public static final String COMMUNITY_REPO = "SketchwareAI";
    /** Topic a repository can carry to bring its Discussions into the Community feed. */
    public static final String COMMUNITY_TOPIC = "sketchwareai-community";
    static final int FEED_REPOS = 15;

    public static final class Feed {
        public final List<Post> posts = new ArrayList<>();
        /** "owner/name" of the repositories whose Discussions are on (where a new post can go). */
        public final List<String> open = new ArrayList<>();
    }

    /**
     * v2.1.1 (owner's 21 Nov report: Community "Open on GitHub" → 404): the Community feed in the app: Discussions of
     * the official repository (when on), the store's apps, repositories with the {@value #COMMUNITY_TOPIC} topic and
     * the account's own repositories with Discussions on, newest first, in one GraphQL request.
     */
    public static Feed feed(String token, String login) throws IOException, GitHubException {
        java.util.LinkedHashSet<String> repos = new java.util.LinkedHashSet<>();
        repos.add(COMMUNITY_OWNER + "/" + COMMUNITY_REPO);
        for (App a : searchRepos(token, "topic:" + COMMUNITY_TOPIC, null, null, "Reading the community")) {
            repos.add(a.owner + "/" + a.repo);
        }
        for (App a : searchRepos(token, "topic:" + TOPIC, null, null, "Reading the Play Store")) {
            repos.add(a.owner + "/" + a.repo);
        }
        if (login != null) {
            JsonObject own = GitHubGraphQL.run(token, "query{viewer{repositories(first:50,ownerAffiliations:OWNER,"
                    + "orderBy:{field:UPDATED_AT,direction:DESC}){nodes{nameWithOwner hasDiscussionsEnabled}}}}", null);
            for (JsonElement e : GitHubGraphQL.array(own, "viewer.repositories.nodes")) {
                if (!e.isJsonObject()) continue;
                JsonObject n = e.getAsJsonObject();
                if (n.has("hasDiscussionsEnabled") && n.get("hasDiscussionsEnabled").getAsBoolean()) {
                    repos.add(DeviceFlow.string(n, "nameWithOwner"));
                }
            }
        }
        StringBuilder q = new StringBuilder("query{");
        List<String[]> asked = new ArrayList<>();
        for (String full : repos) {
            if (asked.size() >= FEED_REPOS || full == null) break;
            int slash = full.indexOf('/');
            if (slash <= 0) continue;
            String owner = full.substring(0, slash);
            String name = full.substring(slash + 1);
            if (!owner.matches("[A-Za-z0-9-]{1,39}") || !name.matches("[A-Za-z0-9_.-]{1,100}")) continue;
            q.append("r").append(asked.size()).append(":repository(owner:\"").append(owner).append("\",name:\"")
                    .append(name).append("\"){hasDiscussionsEnabled discussions(first:8,orderBy:{field:UPDATED_AT,"
                    + "direction:DESC}){nodes{id number title bodyText author{login} createdAt updatedAt "
                    + "comments{totalCount} category{name} url}}} ");
            asked.add(new String[]{owner, name});
        }
        q.append("}");
        Feed feed = new Feed();
        if (asked.isEmpty()) return feed;
        // a repository gone or renamed answers with an error next to the data of the others: its slot is null
        JsonObject data = GitHubGraphQL.runLenient(token, q.toString(), null);
        for (int i = 0; i < asked.size(); i++) {
            JsonObject r = GitHubGraphQL.object(data, "r" + i);
            if (r == null || !r.has("hasDiscussionsEnabled") || !r.get("hasDiscussionsEnabled").getAsBoolean()) continue;
            feed.open.add(asked.get(i)[0] + "/" + asked.get(i)[1]);
            for (JsonElement e : GitHubGraphQL.array(r, "discussions.nodes")) {
                if (!e.isJsonObject()) continue;
                Post p = post(e.getAsJsonObject());
                if (p.title != null && p.title.startsWith("📊 ")) continue;  // an app's stats post isn't a topic
                p.owner = asked.get(i)[0];
                p.repo = asked.get(i)[1];
                feed.posts.add(p);
            }
        }
        java.util.Collections.sort(feed.posts, (a, b) -> Long.compare(b.updated, a.updated));
        return feed;
    }

    static Post post(JsonObject d) {
        Post p = new Post();
        p.id = DeviceFlow.string(d, "id");
        p.number = intOf(d, "number");
        p.title = DeviceFlow.string(d, "title");
        p.body = DeviceFlow.string(d, "bodyText");
        JsonObject author = GitHubGraphQL.object(d, "author");
        p.author = author == null ? "ghost" : DeviceFlow.string(author, "login");
        p.time = ProjectRepo.isoTime(DeviceFlow.string(d, "createdAt"));
        p.updated = ProjectRepo.isoTime(DeviceFlow.string(d, "updatedAt"), DeviceFlow.string(d, "createdAt"));
        JsonObject comments = GitHubGraphQL.object(d, "comments");
        p.comments = comments == null ? 0 : intOf(comments, "totalCount");
        JsonObject category = GitHubGraphQL.object(d, "category");
        p.category = category == null ? null : DeviceFlow.string(category, "name");
        p.url = DeviceFlow.string(d, "url");
        return p;
    }

    /** The feed kept on the phone (offline first). */
    public static void saveFeed(Context context, Feed feed) {
        try {
            JsonObject o = new JsonObject();
            JsonArray posts = new JsonArray();
            for (Post p : feed.posts) {
                JsonObject j = new JsonObject();
                j.addProperty("id", p.id);
                j.addProperty("owner", p.owner);
                j.addProperty("repo", p.repo);
                j.addProperty("number", p.number);
                j.addProperty("title", p.title);
                j.addProperty("body", p.body == null ? null : clip(p.body, 600));
                j.addProperty("author", p.author);
                j.addProperty("time", p.time);
                j.addProperty("updated", p.updated);
                j.addProperty("comments", p.comments);
                j.addProperty("category", p.category);
                j.addProperty("url", p.url);
                posts.add(j);
            }
            o.add("posts", posts);
            JsonArray open = new JsonArray();
            for (String r : feed.open) open.add(r);
            o.add("open", open);
            File f = new File(context.getApplicationContext().getFilesDir(), "community/feed.json");
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(f.getPath() + ".tmp");
            java.nio.file.Files.write(tmp.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) //noinspection ResultOfMethodCallIgnored
                tmp.delete();
        } catch (Exception ignored) {
            // a cache only
        }
    }

    public static Feed cachedFeed(Context context) {
        Feed feed = new Feed();
        File f = new File(context.getApplicationContext().getFilesDir(), "community/feed.json");
        if (!f.isFile() || f.length() > 2L * 1024 * 1024) return null;
        try {
            JsonObject o = DeviceFlow.parse(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (JsonElement e : o.has("posts") && o.get("posts").isJsonArray() ? o.getAsJsonArray("posts") : new JsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject j = e.getAsJsonObject();
                Post p = new Post();
                p.id = DeviceFlow.string(j, "id");
                p.owner = DeviceFlow.string(j, "owner");
                p.repo = DeviceFlow.string(j, "repo");
                p.number = intOf(j, "number");
                p.title = DeviceFlow.string(j, "title");
                p.body = DeviceFlow.string(j, "body");
                p.author = DeviceFlow.string(j, "author");
                p.time = longOf(j, "time");
                p.updated = longOf(j, "updated");
                p.comments = intOf(j, "comments");
                p.category = DeviceFlow.string(j, "category");
                p.url = DeviceFlow.string(j, "url");
                if (p.owner != null && p.repo != null) feed.posts.add(p);
            }
            for (JsonElement e : o.has("open") && o.get("open").isJsonArray() ? o.getAsJsonArray("open") : new JsonArray()) {
                if (e.isJsonPrimitive()) feed.open.add(e.getAsString());
            }
            return feed;
        } catch (Exception e) {
            return null;
        }
    }
}
