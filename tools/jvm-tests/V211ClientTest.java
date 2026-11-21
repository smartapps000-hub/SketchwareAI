import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * v2.1.1: the network side of the v2.1.1 parts (com.sketchware.ai.github: RepoIcons' icon upload and read, PlayStore's
 * publish with the icon, old pictures removed, the version line and the stats post, the counters (reactions, react),
 * search by words and creator, a creator's page, the Community feed in one GraphQL request with a repository that is
 * gone), compiled from the app's classes, against a small fake GitHub on 127.0.0.1 (JDK's HttpServer) that records
 * every request. On the JVM because ART in this workspace can't open sockets.
 * Run (from the repository root, after the app's Java compile): as V210ClientTest, class V211ClientTest.
 * Prints V211 CHECK lines and "V211CLIENT n/n ok".
 */
public class V211ClientTest {
    static int ok, total;
    static final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    static final List<String> bodies = java.util.Collections.synchronizedList(new ArrayList<>());
    /** The fake repository's files (path → bytes). */
    static final Map<String, byte[]> files = new java.util.concurrent.ConcurrentHashMap<>();
    static final List<String> graphql = java.util.Collections.synchronizedList(new ArrayList<>());
    static volatile String releasesJson = "[]";
    static volatile int discussions;

    static void check(String name, boolean result, Object detail) {
        total++;
        if (result) ok++;
        System.out.println("V211 CHECK " + (result ? "ok  " : "FAIL") + " " + name + (detail == null ? "" : " - " + detail));
    }

    static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> c = target instanceof Class ? (Class<?>) target : target.getClass();
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) != (target instanceof Class)) continue;
            Class<?>[] ps = m.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < ps.length && fits; i++) {
                if (args[i] == null) continue;
                Class<?> p = ps[i].isPrimitive() ? box(ps[i]) : ps[i];
                fits = p.isInstance(args[i]);
            }
            if (!fits) continue;
            m.setAccessible(true);
            try {
                return m.invoke(target instanceof Class ? null : target, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                throw new RuntimeException(cause);
            }
        }
        throw new NoSuchMethodException(c.getName() + "." + name + "/" + args.length);
    }

    static Class<?> box(Class<?> p) {
        if (p == int.class) return Integer.class;
        if (p == long.class) return Long.class;
        if (p == boolean.class) return Boolean.class;
        return p;
    }

    static Class<?> c(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    static String gitSha(byte[] b) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-1");
        d.update(("blob " + b.length + "\0").getBytes(StandardCharsets.UTF_8));
        d.update(b);
        StringBuilder s = new StringBuilder();
        for (byte x : d.digest()) s.append(String.format("%02x", x));
        return s.toString();
    }

    static void answer(HttpExchange x, int code, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = x.getResponseBody()) {
                out.write(bytes);
            }
        }
        x.close();
    }

    static void handle(HttpExchange x) throws java.io.IOException {
        String method = x.getRequestMethod();
        String override = x.getRequestHeaders().getFirst("X-HTTP-Method-Override");
        if (override != null) method = override;
        String path = x.getRequestURI().getRawPath() + (x.getRequestURI().getRawQuery() == null ? "" : "?" + x.getRequestURI().getRawQuery());
        String auth = x.getRequestHeaders().getFirst("Authorization");
        String token = auth == null ? null : auth.substring(auth.lastIndexOf(' ') + 1);
        String body;
        try (InputStream in = x.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String api = path.startsWith("/api") ? path.substring(4) : path;
        requests.add(method + " " + api + " " + (token == null ? "-" : token));
        if (!body.isEmpty()) bodies.add(method + " " + api + " " + body);
        try {
            route(x, method, api, token, body);
        } catch (Exception e) {
            answer(x, 500, "{\"message\":\"" + e + "\"}");
        }
    }

    static final String REPO = "/repos/octo/SketchwareAI-Notes";

    static void route(HttpExchange x, String method, String api, String token, String body) throws Exception {
        if (api.equals(REPO) && method.equals("PATCH")) {
            answer(x, 200, "{\"private\":false}");
            return;
        }
        if (api.equals(REPO + "/topics")) {
            answer(x, 200, method.equals("GET") ? "{\"names\":[\"sketchware-ai\"]}" : body);
            return;
        }
        if (api.startsWith(REPO + "/releases")) {
            answer(x, 200, releasesJson);
            return;
        }
        if (api.startsWith(REPO + "/contents/")) {
            String file = api.substring((REPO + "/contents/").length());
            int q = file.indexOf('?');
            if (q >= 0) file = file.substring(0, q);
            if (method.equals("GET")) {
                if (file.equals("playstore/screenshots")) {
                    StringBuilder list = new StringBuilder("[");
                    for (String k : new java.util.TreeSet<>(files.keySet())) {
                        if (!k.startsWith("playstore/screenshots/")) continue;
                        if (list.length() > 1) list.append(',');
                        String name = k.substring("playstore/screenshots/".length());
                        list.append("{\"name\":\"").append(name).append("\",\"sha\":\"").append(gitSha(files.get(k))).append("\"}");
                    }
                    answer(x, list.length() == 1 ? 404 : 200, list.length() == 1 ? "{\"message\":\"Not Found\"}" : list + "]");
                    return;
                }
                byte[] there = files.get(file);
                if (there == null) answer(x, 404, "{\"message\":\"Not Found\"}");
                else answer(x, 200, "{\"content\":\"" + b64(there) + "\",\"sha\":\"" + gitSha(there) + "\"}");
                return;
            }
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            byte[] there = files.get(file);
            if (method.equals("DELETE")) {
                if (there == null || !gitSha(there).equals(o.get("sha").getAsString())) {
                    answer(x, 409, "{\"message\":\"sha does not match\"}");
                } else {
                    files.remove(file);
                    answer(x, 200, "{}");
                }
                return;
            }
            if (there != null && (!o.has("sha") || !gitSha(there).equals(o.get("sha").getAsString()))) {
                answer(x, 409, "{\"message\":\"sha does not match\"}");
                return;
            }
            files.put(file, Base64.getMimeDecoder().decode(o.get("content").getAsString()));
            answer(x, there == null ? 201 : 200, "{\"content\":{\"sha\":\"x\"}}");
            return;
        }
        if (api.startsWith("/search/repositories?")) {
            String q = java.net.URLDecoder.decode(api.substring(api.indexOf("q=") + 2).split("&")[0], "UTF-8");
            if (q.contains("topic:sketchwareai-community")) {
                answer(x, 200, "{\"items\":[" + repo(31, "x", "Forum", "trunk") + "]}");
            } else if (q.contains("topic:sketchwareai-playstore") && q.contains("user:ann")) {
                answer(x, 200, "{\"items\":[" + repo(11, "ann", "SketchwareAI-Notes", "main") + "]}");
            } else if (q.contains("topic:sketchware-ai") && q.contains("user:ann")) {
                answer(x, 200, "{\"items\":[" + repo(11, "ann", "SketchwareAI-Notes", "main") + ","
                        + repo(14, "ann", "SketchwareAI-Timer", "main") + "]}");
            } else if (q.contains("topic:sketchwareai-playstore")) {
                answer(x, 200, "{\"items\":[" + repo(11, "ann", "SketchwareAI-Notes", "main") + ","
                        + repo(31, "x", "Forum", "trunk") + "]}");
            } else {
                answer(x, 200, "{\"items\":[]}");
            }
            return;
        }
        if (api.equals("/users/ann")) {
            answer(x, 200, "{\"login\":\"ann\",\"name\":\"Ann Lee\",\"bio\":\"Makes small apps\",\"avatar_url\":"
                    + "\"https://avatars.githubusercontent.com/u/9?v=4\",\"followers\":42,\"public_repos\":7}");
            return;
        }
        if (api.equals("/users/ghost-404")) {
            answer(x, 404, "{\"message\":\"Not Found\"}");
            return;
        }
        if (api.equals("/graphql") && method.equals("POST")) {
            JsonObject q = JsonParser.parseString(body).getAsJsonObject();
            String query = q.get("query").getAsString();
            graphql.add(query + " " + (q.has("variables") ? q.get("variables") : ""));
            if (query.contains("createDiscussion")) {
                discussions = 77;
                answer(x, 200, "{\"data\":{\"createDiscussion\":{\"discussion\":{\"number\":77}}}}");
            } else if (query.contains("addReaction") || query.contains("removeReaction")) {
                answer(x, 200, "{\"data\":{\"x\":{\"reaction\":{\"content\":\"HEART\"}}}}");
            } else if (query.contains("reactionGroups")) {
                answer(x, 200, "{\"data\":{\"repository\":{\"discussion\":{\"id\":\"D_77\",\"reactionGroups\":["
                        + "{\"content\":\"HEART\",\"viewerHasReacted\":true,\"reactors\":{\"totalCount\":5}},"
                        + "{\"content\":\"EYES\",\"viewerHasReacted\":false,\"reactors\":{\"totalCount\":12}},"
                        + "{\"content\":\"ROCKET\",\"viewerHasReacted\":false,\"reactors\":{\"totalCount\":2}},"
                        + "{\"content\":\"THUMBS_UP\",\"viewerHasReacted\":false,\"reactors\":{\"totalCount\":1}}]}}}}");
            } else if (query.contains("discussion(number:$number){id}")) {
                boolean there = discussions > 0 && query.length() > 0 && q.get("variables").getAsJsonObject().get("number").getAsInt() == discussions;
                answer(x, 200, there ? "{\"data\":{\"repository\":{\"discussion\":{\"id\":\"D_77\"}}}}"
                        : "{\"data\":{\"repository\":{\"discussion\":null}},\"errors\":[{\"message\":\"Could not resolve\"}]}");
            } else if (query.contains("discussionCategories(first:20)")) {
                answer(x, 200, "{\"data\":{\"repository\":{\"id\":\"R_N\",\"hasDiscussionsEnabled\":true,"
                        + "\"discussionCategories\":{\"nodes\":[{\"id\":\"C0\",\"name\":\"Announcements\"},{\"id\":\"C1\","
                        + "\"name\":\"General\"}]}}}}");
            } else if (query.contains("viewer{repositories")) {
                answer(x, 200, "{\"data\":{\"viewer\":{\"repositories\":{\"nodes\":[{\"nameWithOwner\":\"octo/Mine\","
                        + "\"hasDiscussionsEnabled\":true},{\"nameWithOwner\":\"octo/Off\",\"hasDiscussionsEnabled\":false}]}}}}");
            } else if (query.contains("r0:repository")) {
                // r0 = Sketchware AI's own (Discussions off), r1 x/Forum, r2 ann/Notes, r3 octo/Mine (gone: null + error)
                answer(x, 200, "{\"data\":{\"r0\":{\"hasDiscussionsEnabled\":false,\"discussions\":{\"nodes\":[]}},"
                        + "\"r1\":{\"hasDiscussionsEnabled\":true,\"discussions\":{\"nodes\":[" + post("D1", 1, "Welcome", "x",
                        "2026-11-19T10:00:00Z", "General") + "]}},"
                        + "\"r2\":{\"hasDiscussionsEnabled\":true,\"discussions\":{\"nodes\":[" + post("D2", 7, "★★★★★ Great", "zed",
                        "2026-11-20T10:00:00Z", "Reviews") + "," + post("D3", 9, "📊 Notes · Play Store", "ann",
                        "2026-11-21T10:00:00Z", "General") + "]}},\"r3\":null},"
                        + "\"errors\":[{\"type\":\"NOT_FOUND\",\"path\":[\"r3\"],\"message\":\"Could not resolve to a Repository\"}]}");
            } else {
                answer(x, 200, "{\"data\":{}}");
            }
            return;
        }
        answer(x, 404, "{\"message\":\"no route " + method + " " + api + "\"}");
    }

    static String repo(long id, String owner, String name, String branch) {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"private\":false,\"owner\":{\"login\":\"" + owner + "\"},"
                + "\"description\":\"" + name + " app\",\"stargazers_count\":3,\"pushed_at\":\"2026-11-18T10:00:00Z\","
                + "\"default_branch\":\"" + branch + "\"}";
    }

    static String post(String id, int number, String title, String author, String updated, String category) {
        return "{\"id\":\"" + id + "\",\"number\":" + number + ",\"title\":\"" + title + "\",\"bodyText\":\"text\",\"author\":"
                + "{\"login\":\"" + author + "\"},\"createdAt\":\"2026-11-18T09:00:00Z\",\"updatedAt\":\"" + updated + "\","
                + "\"comments\":{\"totalCount\":1},\"category\":{\"name\":\"" + category + "\"},\"url\":\"https://github.com/"
                + id + "\"}";
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", x -> handle(x));
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Class<?> http = c("com.sketchware.ai.github.GitHubHttp");
        call(http, "useEndpoints", base + "/web", base + "/api", base + "/uploads");
        try {
            run();
        } finally {
            server.stop(0);
        }
        System.out.println("V211CLIENT " + ok + "/" + total + (ok == total ? " ok" : " FAILED"));
        System.exit(ok == total ? 0 : 1);
    }

    static String release(String name, String code, String notes, long time, long apkDownloads) throws Exception {
        JsonObject facts = new JsonObject();
        facts.addProperty("versionName", name);
        facts.addProperty("versionCode", code);
        facts.addProperty("time", time);
        facts.addProperty("swb", "Notes.swb");
        facts.addProperty("apk", "Notes.apk");
        String body = (String) call(c("com.sketchware.ai.github.ProjectRepo"), "body", notes, facts);
        JsonObject r = new JsonObject();
        r.addProperty("id", time);
        r.addProperty("tag_name", "v" + name + "-" + code);
        r.addProperty("name", "v" + name);
        r.addProperty("draft", false);
        r.addProperty("body", body);
        r.add("assets", JsonParser.parseString("[{\"id\":1,\"name\":\"Notes.apk\",\"size\":10,\"download_count\":"
                + apkDownloads + "},{\"id\":2,\"name\":\"Notes.swb\",\"size\":20,\"download_count\":3}]"));
        return r.toString();
    }

    static void run() throws Exception {
        Class<?> icons = c("com.sketchware.ai.github.RepoIcons");
        Class<?> store = c("com.sketchware.ai.github.PlayStore");

        // --- the app icon on GitHub
        byte[] icon = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
        boolean wrote = (Boolean) call(icons, "upload", "tok", "octo", "SketchwareAI-Notes", icon);
        requests.clear();
        boolean wroteAgain = (Boolean) call(icons, "upload", "tok", "octo", "SketchwareAI-Notes", icon);
        int putsAfter = 0;
        for (String r : requests) if (r.startsWith("PUT ")) putsAfter++;
        byte[] icon2 = {(byte) 0x89, 'P', 'N', 'G', 9, 9};
        boolean replaced = (Boolean) call(icons, "upload", "tok", "octo", "SketchwareAI-Notes", icon2);
        byte[] read = (byte[]) call(icons, "fetch", "tok", "octo", "SketchwareAI-Notes");
        byte[] none = (byte[]) call(icons, "fetch", "tok", "octo", "SketchwareAI-Other");
        check("v2.1.1: the icon goes up as playstore/icon.png; the same bytes aren't sent again (git blob id compared); "
                        + "a new icon replaces it (with the file's sha); read back as it is; no icon: none",
                wrote && !wroteAgain && putsAfter == 0 && replaced && java.util.Arrays.equals(icon2, read) && none == null,
                wrote + " " + wroteAgain + " " + putsAfter + " " + replaced);

        // --- publish: icon, pictures (old extra ones removed), the version line, the stats post
        Object listing = c("com.sketchware.ai.github.PlayStore$Listing").getConstructor().newInstance();
        set(listing, "title", "Notes Pro");
        set(listing, "description", "Write things down.");
        set(listing, "icon", icon);
        @SuppressWarnings("unchecked")
        List<byte[]> pics = (List<byte[]>) field(listing, "screenshots");
        pics.add(new byte[]{1});
        pics.add(new byte[]{2});
        files.put("playstore/screenshots/03.jpg", new byte[]{3});
        files.put("playstore/screenshots/04.jpg", new byte[]{4});
        releasesJson = "[" + release("1.0", "1", "✨ First version", 1_795_000_000_000L, 4) + "]";
        Class<?> stepsType = c("com.sketchware.ai.github.PlayStore$Steps");
        List<String> stepTexts = java.util.Collections.synchronizedList(new ArrayList<>());
        Object steps = java.lang.reflect.Proxy.newProxyInstance(stepsType.getClassLoader(), new Class<?>[]{stepsType},
                (p, m, a) -> {
                    if (m.getName().equals("step")) stepTexts.add(a[0] + " " + a[1]);
                    return null;
                });
        Object note1 = call(store, "publish", "tok", "octo", "SketchwareAI-Notes", listing, steps);
        JsonObject page1 = JsonParser.parseString(new String(files.get("playstore/listing.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        check("v2.1.1: publish uploads the icon, two pictures and removes the older 03/04; first publish: \"published\" "
                        + "#1 v1.0 (1); the stats post made in General (not Announcements) and kept on the page",
                java.util.Arrays.equals(icon, files.get("playstore/icon.png")) && files.containsKey("playstore/screenshots/01.jpg")
                        && files.containsKey("playstore/screenshots/02.jpg") && !files.containsKey("playstore/screenshots/03.jpg")
                        && !files.containsKey("playstore/screenshots/04.jpg") && "published".equals(page1.get("action").getAsString())
                        && page1.get("publish_number").getAsInt() == 1 && "1.0".equals(page1.get("version_name").getAsString())
                        && page1.get("stats_discussion").getAsInt() == 77 && graphql.stream().anyMatch(g -> g.contains("createDiscussion")
                        && g.contains("\"category\":\"C1\"") && g.contains("📊 Notes Pro · Play Store")) && note1 == null,
                page1 + " " + note1);
        graphql.clear();
        call(store, "publish", "tok", "octo", "SketchwareAI-Notes", listing, steps);
        JsonObject page2 = JsonParser.parseString(new String(files.get("playstore/listing.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        boolean noSecondPost = graphql.stream().noneMatch(g -> g.contains("createDiscussion"));
        releasesJson = "[" + release("1.1", "2", "🛠 Fixed the list", 1_795_100_000_000L, 6) + ","
                + release("1.0", "1", "✨ First version", 1_795_000_000_000L, 4) + "]";
        call(store, "publish", "tok", "octo", "SketchwareAI-Notes", listing, steps);
        JsonObject page3 = JsonParser.parseString(new String(files.get("playstore/listing.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        check("v2.1.1: the same version again: \"republished\" #2 (the stats post found, not made twice); a new version "
                        + "code: \"updated\" #3 v1.1 (2) with its changelog",
                "republished".equals(page2.get("action").getAsString()) && page2.get("publish_number").getAsInt() == 2
                        && noSecondPost && "updated".equals(page3.get("action").getAsString())
                        && page3.get("publish_number").getAsInt() == 3 && "2".equals(page3.get("version_code").getAsString())
                        && "🛠 Fixed the list".equals(page3.get("changelog").getAsString()), page2 + " " + page3);
        check("v2.1.1: publish steps run 5 → 97 (icon 18, pictures, version 74, counters 80, page 88)",
                stepTexts.get(0).startsWith("5 ") && stepTexts.stream().anyMatch(s -> s.startsWith("18 "))
                        && stepTexts.stream().anyMatch(s -> s.startsWith("80 ")) && stepTexts.get(stepTexts.size() - 1).startsWith("97 "),
                stepTexts.size());
        Object app = c("com.sketchware.ai.github.PlayStore$App").getConstructor().newInstance();
        set(app, "owner", "octo");
        set(app, "repo", "SketchwareAI-Notes");
        set(app, "branch", "main");
        call(store, "readListing", "tok", app);
        check("v2.1.1: the app page reads the version line and the stats post from the store page",
                "Updated to v1.1 (2)".equals(call(app, "status")) && Integer.valueOf(77).equals(field(app, "statsDiscussion"))
                        && "🛠 Fixed the list".equals(field(app, "changelog")), call(app, "status"));
        check("v2.1.1: Downloads = the versions' APK and .swb download counts (6 + 3 + 4 + 3)",
                Long.valueOf(16).equals(call(store, "downloads", "tok", app)), call(store, "downloads", "tok", app));
        Object stats = c("com.sketchware.ai.github.PlayStore$Stats").getConstructor().newInstance();
        call(store, "reactions", "tok", app, stats);
        check("v2.1.1: likes ❤️ 5 (this account's), views 👀 12, shares 🚀 2 from the stats post's reactions (one per account)",
                Integer.valueOf(5).equals(field(stats, "likes")) && Boolean.TRUE.equals(field(stats, "liked"))
                        && Integer.valueOf(12).equals(field(stats, "views")) && Integer.valueOf(2).equals(field(stats, "shares"))
                        && "D_77".equals(field(stats, "subjectId")), null);
        Object signedOut = c("com.sketchware.ai.github.PlayStore$Stats").getConstructor().newInstance();
        call(store, "reactions", null, app, signedOut);
        check("v2.1.1: signed out: the counters stay unknown (GitHub shows reactions to signed-in accounts)",
                Integer.valueOf(-1).equals(field(signedOut, "likes")), null);
        graphql.clear();
        call(store, "react", "tok", "D_77", "HEART", false);
        call(store, "react", "tok", "D_77", "EYES", true);
        boolean odd = false;
        try {
            call(store, "react", "tok", "D_77", "LAUGH", true);
        } catch (IllegalArgumentException e) {
            odd = true;
        }
        check("v2.1.1: Like toggles the ❤️ reaction (removeReaction / addReaction); only ❤️ 👀 🚀 can be sent",
                graphql.size() == 2 && graphql.get(0).contains("removeReaction") && graphql.get(0).contains("\"content\":\"HEART\"")
                        && graphql.get(1).contains("addReaction") && graphql.get(1).contains("\"content\":\"EYES\"") && odd,
                graphql);

        // --- search and a creator's page
        requests.clear();
        call(store, "search", "tok", "notes app", "ann");
        String q = "";
        for (String r : requests) if (r.startsWith("GET /search/repositories")) q = r;
        String decoded = java.net.URLDecoder.decode(q.substring(q.indexOf("q=") + 2).split("&")[0], "UTF-8");
        check("v2.1.1: search: the store topic, the creator, the words in names and descriptions (encoded)",
                decoded.equals("topic:sketchwareai-playstore user:ann notes app in:name,description") && !q.contains(" notes"),
                decoded);
        Object dev = call(store, "developer", "tok", "ann");
        @SuppressWarnings("unchecked")
        List<Object> devApps = (List<Object>) field(dev, "apps");
        @SuppressWarnings("unchecked")
        List<Object> devProjects = (List<Object>) field(dev, "projects");
        String missing = null;
        try {
            call(store, "developer", "tok", "ghost-404");
        } catch (Exception e) {
            missing = e.getMessage();
        }
        check("v2.1.1: a creator's page: name, bio, picture (GitHub's avatar host), followers, the store apps and the other "
                        + "projects (an app isn't listed twice); an account that isn't there says so",
                "Ann Lee".equals(field(dev, "name")) && "Makes small apps".equals(field(dev, "bio"))
                        && String.valueOf(field(dev, "avatar")).startsWith("https://avatars.githubusercontent.com/")
                        && Integer.valueOf(42).equals(field(dev, "followers")) && devApps.size() == 1 && devProjects.size() == 1
                        && "SketchwareAI-Timer".equals(field(devProjects.get(0), "repo")) && missing != null
                        && missing.contains("isn't on GitHub"), devApps.size() + " " + devProjects.size() + " " + missing);

        // --- the Community feed
        graphql.clear();
        Object feed = call(store, "feed", "tok", "octo");
        @SuppressWarnings("unchecked")
        List<Object> posts = (List<Object>) field(feed, "posts");
        @SuppressWarnings("unchecked")
        List<String> open = (List<String>) field(feed, "open");
        String aliased = "";
        for (String g : graphql) if (g.contains("r0:repository")) aliased = g;
        check("v2.1.1: the Community feed: one request for every repository (Sketchware AI's own first), a repository "
                        + "gone doesn't stop it, Discussions off left out of New post, the stats posts aren't topics, newest first",
                aliased.contains("r0:repository(owner:\"smartapps000-hub\",name:\"SketchwareAI\")")
                        && aliased.contains("octo/Mine".replace("octo/Mine", "name:\"Mine\""))
                        && !aliased.contains("name:\"Off\"") && open.equals(java.util.Arrays.asList("x/Forum", "ann/SketchwareAI-Notes"))
                        && posts.size() == 2 && "D2".equals(field(posts.get(0), "id")) && "ann".equals(field(posts.get(0), "owner"))
                        && "SketchwareAI-Notes".equals(field(posts.get(0), "repo")) && "D1".equals(field(posts.get(1), "id")),
                open + " " + posts.size());
    }

    static Object field(Object o, String name) throws Exception {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try {
                java.lang.reflect.Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    static void set(Object o, String name, Object value) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }
}
