import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * v2.1.0: the network side of the v2.1.0 parts (com.sketchware.ai.github: GitHubHttp quick mode, the 401 confirmed
 * twice, SingleDeviceGuard's mark, PlayStore search / store page / publish, GitHubGraphQL and the Discussions calls,
 * AppUpdater's host rule), compiled from the app's classes, against a small fake GitHub on 127.0.0.1 (JDK's
 * HttpServer) that records every request. On the JVM because ART in this workspace can't open sockets.
 * Run (from the repository root, after the app's Java compile):
 *   APPC=app/build/intermediates/javac/minApi26Debug/compileMinApi26DebugJavaWithJavac/classes
 *   javac -d out210 -cp $ANDROID_JAR:$APPC:$GSON tools/jvm-tests/V210ClientTest.java
 *   java -cp tools/art-harness/out/stubs-cls:out210:$APPC:$GSON:app/libs/*:$ANDROID_JAR V210ClientTest
 * (android.jar last: its classes only let the app's classes link; the harness stand-ins come first.)
 * Prints V210 CHECK lines and "V210CLIENT n/n ok".
 */
public class V210ClientTest {
    static int ok, total;
    static final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    static final List<String> bodies = java.util.Collections.synchronizedList(new ArrayList<>());
    static volatile int user401once;
    static volatile boolean backupRepo;
    static volatile String deviceFile;
    static volatile int deviceSha;
    static volatile String repoCreateBody;
    static final java.util.Map<String, String> demoFiles = new java.util.concurrent.ConcurrentHashMap<>();
    static volatile String graphqlVariables;

    static void check(String name, boolean result, Object detail) {
        total++;
        if (result) ok++;
        System.out.println("V210 CHECK " + (result ? "ok  " : "FAIL") + " " + name + (detail == null ? "" : " - " + detail));
    }

    /** A static or instance method of the app by name and argument count (package-private too), unwrapped. */
    static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> c = target instanceof Class ? (Class<?>) target : target.getClass();
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) != (target instanceof Class)) continue;
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

    static Class<?> c(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static String unb64(String s) {
        return new String(Base64.getMimeDecoder().decode(s), StandardCharsets.UTF_8);
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
        // the desktop JVM can't send PATCH: the app sends POST with GitHub's X-HTTP-Method-Override then
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

    static void route(HttpExchange x, String method, String api, String token, String body) throws Exception {
        if (api.equals("/slow")) {
            Thread.sleep(4000);
            answer(x, 200, "{\"slow\":true}");
            return;
        }
        if (api.equals("/user") && method.equals("GET")) {
            if ("t401".equals(token) || ("t401once".equals(token) && user401once++ == 0)) {
                answer(x, 401, "{\"message\":\"Bad credentials\"}");
            } else {
                answer(x, 200, "{\"login\":\"octo\",\"id\":1}");
            }
            return;
        }
        String mark = "/repos/octo/SketchwareAI-Projects/contents/.device-session.json";
        if (api.equals(mark)) {
            if (method.equals("GET")) {
                if (deviceFile == null) answer(x, 404, "{\"message\":\"Not Found\"}");
                else answer(x, 200, "{\"content\":\"" + b64(deviceFile) + "\\n\",\"sha\":\"s" + deviceSha + "\"}");
                return;
            }
            if (method.equals("PUT")) {
                if (!backupRepo) {
                    answer(x, 404, "{\"message\":\"Not Found\"}");
                    return;
                }
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                String sha = o.has("sha") ? o.get("sha").getAsString() : null;
                if (deviceFile != null && !("s" + deviceSha).equals(sha)) {
                    answer(x, 409, "{\"message\":\"sha does not match\"}");
                    return;
                }
                deviceFile = unb64(o.get("content").getAsString());
                deviceSha++;
                answer(x, deviceSha == 1 ? 201 : 200, "{\"content\":{\"sha\":\"s" + deviceSha + "\"}}");
                return;
            }
        }
        if (api.equals("/user/repos") && method.equals("POST")) {
            repoCreateBody = body;
            backupRepo = true;
            answer(x, 201, "{\"name\":\"SketchwareAI-Projects\"}");
            return;
        }
        if (api.startsWith("/search/repositories?")) {
            answer(x, 200, "{\"total_count\":3,\"items\":["
                    + "{\"id\":11,\"name\":\"SketchwareAI-Notes\",\"private\":false,\"owner\":{\"login\":\"ann\"},"
                    + "\"description\":\"A notes app\",\"stargazers_count\":7,\"pushed_at\":\"2026-11-18T10:00:00Z\","
                    + "\"default_branch\":\"main\"},"
                    + "{\"id\":12,\"name\":\"Secret\",\"private\":true,\"owner\":{\"login\":\"bob\"}},"
                    + "{\"id\":13,\"name\":\"SketchwareAI-Public-Timer\",\"private\":false,\"owner\":{\"login\":\"cy\"},"
                    + "\"description\":null,\"stargazers_count\":0,\"pushed_at\":\"2026-11-17T10:00:00Z\","
                    + "\"default_branch\":\"trunk\"}]}");
            return;
        }
        if (api.equals("/repos/ann/SketchwareAI-Notes/contents/playstore/listing.json?ref=main")) {
            answer(x, 200, "{\"content\":\"" + b64("{\"title\":\"  Notes Pro \",\"description\":\"Write things down.\","
                    + "\"video\":\"https://youtu.be/abc\"}") + "\",\"sha\":\"l1\"}");
            return;
        }
        if (api.equals("/repos/ann/SketchwareAI-Notes/contents/playstore/screenshots?ref=main")) {
            StringBuilder list = new StringBuilder("[");
            String[] names = {"10.jpg", "02.png", "notes.txt", "01.jpg", "03.webp", "04.jpg", "05.jpg", "06.jpg", "07.jpg",
                    "08.jpg", "09.jpg"};
            for (int i = 0; i < names.length; i++) {
                if (i > 0) list.append(',');
                list.append("{\"name\":\"").append(names[i]).append("\",\"download_url\":\"https://raw.githubusercontent.com/ann/")
                        .append("SketchwareAI-Notes/main/playstore/screenshots/").append(names[i]).append("\"}");
            }
            list.append(",{\"name\":\"evil.jpg\",\"download_url\":\"http://evil.example/evil.jpg\"}]");
            answer(x, 200, list.toString());
            return;
        }
        if (api.equals("/repos/cy/SketchwareAI-Public-Timer/contents/playstore/listing.json?ref=trunk")
                || api.equals("/repos/cy/SketchwareAI-Public-Timer/contents/playstore/screenshots?ref=trunk")) {
            answer(x, 404, "{\"message\":\"Not Found\"}");
            return;
        }
        String demo = "/repos/octo/SketchwareAI-Demo";
        if (api.equals(demo) && method.equals("PATCH")) {
            answer(x, 200, "{\"private\":false}");
            return;
        }
        if (api.equals(demo + "/topics")) {
            answer(x, 200, method.equals("GET") ? "{\"names\":[\"sketchware-ai\",\"tools\"]}" : body);
            return;
        }
        if (api.startsWith(demo + "/contents/")) {
            String file = api.substring((demo + "/contents/").length());
            if (method.equals("GET")) {
                if (demoFiles.containsKey(file)) answer(x, 200, "{\"sha\":\"" + demoFiles.get(file) + "\"}");
                else answer(x, 404, "{\"message\":\"Not Found\"}");
            } else {
                demoFiles.put(file, "f" + demoFiles.size());
                answer(x, 201, "{\"content\":{\"sha\":\"x\"}}");
            }
            return;
        }
        if (api.startsWith(demo + "/releases")) {
            answer(x, 200, "[]");
            return;
        }
        if (api.equals("/graphql") && method.equals("POST")) {
            if ("t401".equals(token)) {
                answer(x, 401, "{\"message\":\"Bad credentials\"}");
                return;
            }
            if ("gqlerr".equals(token)) {
                answer(x, 200, "{\"data\":null,\"errors\":[{\"type\":\"NOT_FOUND\",\"message\":\"Could not resolve to a "
                        + "Repository with the name 'ann/Nope'.\"}]}");
                return;
            }
            JsonObject q = JsonParser.parseString(body).getAsJsonObject();
            String query = q.get("query").getAsString();
            graphqlVariables = q.has("variables") ? q.get("variables").toString() : null;
            if (query.contains("createDiscussion")) {
                answer(x, 200, "{\"data\":{\"createDiscussion\":{\"discussion\":{\"number\":42}}}}");
            } else if (query.contains("addDiscussionComment")) {
                answer(x, 200, "{\"data\":{\"addDiscussionComment\":{\"comment\":{\"id\":\"DC_9\"}}}}");
            } else if (query.contains("discussion(number")) {
                answer(x, 200, "{\"data\":{\"repository\":{\"discussion\":{\"id\":\"D_7\",\"title\":\"★★★★☆ Nice\","
                        + "\"bodyText\":\"Works well\",\"author\":{\"login\":\"zed\"},\"createdAt\":\"2026-11-18T09:00:00Z\","
                        + "\"url\":\"https://github.com/ann/SketchwareAI-Notes/discussions/7\",\"comments\":{\"nodes\":["
                        + "{\"author\":{\"login\":\"ann\"},\"bodyText\":\"Thanks!\",\"createdAt\":\"2026-11-18T10:00:00Z\"},"
                        + "{\"author\":null,\"bodyText\":\"+1\",\"createdAt\":\"2026-11-18T11:00:00Z\"}]}}}}}");
            } else if (query.contains("discussions(first")) {
                answer(x, 200, "{\"data\":{\"repository\":{\"id\":\"R_1\",\"hasDiscussionsEnabled\":true,"
                        + "\"discussionCategories\":{\"nodes\":[{\"id\":\"C1\",\"name\":\"General\"},{\"id\":\"C2\","
                        + "\"name\":\"Reviews\"}]},\"discussions\":{\"nodes\":[{\"id\":\"D_7\",\"number\":7,\"title\":"
                        + "\"★★★★☆ Nice\",\"bodyText\":\"Works well\",\"author\":{\"login\":\"zed\"},\"createdAt\":"
                        + "\"2026-11-18T09:00:00Z\",\"comments\":{\"totalCount\":2},\"category\":{\"name\":\"Reviews\"},"
                        + "\"url\":\"https://github.com/ann/SketchwareAI-Notes/discussions/7\"}]}}}}");
            } else {
                answer(x, 200, "{\"data\":{}}");
            }
            return;
        }
        answer(x, 404, "{\"message\":\"no route " + method + " " + api + "\"}");
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
            run(http);
        } finally {
            server.stop(0);
        }
        System.out.println("V210CLIENT " + ok + "/" + total + (ok == total ? " ok" : " FAILED"));
        System.exit(ok == total ? 0 : 1);
    }

    static void run(Class<?> http) throws Exception {
        // --- quick mode: one try, the given timeout; the next call is normal again
        Class<?> callType = c("com.sketchware.ai.github.GitHubHttp$Call");
        Object slow = java.lang.reflect.Proxy.newProxyInstance(callType.getClassLoader(), new Class<?>[]{callType},
                (p, m, a) -> m.getName().equals("run") ? call(http, "api", "GET", "/slow", "tok", null) : null);
        long started = System.nanoTime();
        String failure = null;
        try {
            call(http, "quick", 1000, slow);
        } catch (Exception e) {
            failure = e.getClass().getSimpleName();
        }
        long quickMillis = (System.nanoTime() - started) / 1_000_000;
        int slowAsked = 0;
        for (String r : requests) if (r.startsWith("GET /slow")) slowAsked++;
        check("v2.1.0: quick mode gives up after its timeout (1 s here, 10 s in the viewer and the Play Store), one try",
                failure != null && quickMillis < 3000 && slowAsked == 1, failure + " after " + quickMillis + " ms, "
                        + slowAsked + " request(s)");
        Object answer = call(http, "api", "GET", "/slow", "tok", null);
        check("v2.1.0: after it the same thread waits as before (quick mode ends with its call)",
                Integer.valueOf(200).equals(field(answer, "code")), field(answer, "code"));

        // --- the 401 asked twice
        Object once = c("com.sketchware.ai.github.CloudBackup").getConstructor(String.class, String.class)
                .newInstance("t401once", "octo");
        Object always = c("com.sketchware.ai.github.CloudBackup").getConstructor(String.class, String.class)
                .newInstance("t401", "octo");
        requests.clear();
        boolean onceRefused = (Boolean) call(once, "refusesSignIn");
        int onceAsked = count("GET /user t401once");
        long t0 = System.nanoTime();
        boolean alwaysRefused = (Boolean) call(always, "refusesSignIn");
        long waited = (System.nanoTime() - t0) / 1_000_000;
        check("v2.1.0: a single 401 isn't a refusal (asked again after 3 s: accepted)", !onceRefused && onceAsked == 2,
                onceRefused + " " + onceAsked);
        check("v2.1.0: two 401s 3 s apart are a refusal", alwaysRefused && count("GET /user t401") == 2 && waited >= 2900,
                alwaysRefused + " after " + waited + " ms");

        // --- the one-device mark
        Class<?> guard = c("com.sketchware.ai.github.SingleDeviceGuard");
        String h1 = (String) call(guard, "hash", "install-1", "Octo");
        String h2 = (String) call(guard, "hash", "install-1", "octo");
        String h3 = (String) call(guard, "hash", "install-2", "octo");
        check("v2.1.0: the mark is a SHA-256 of this install's id and the login (any case), another install another mark",
                h1.matches("[0-9a-f]{64}") && h1.equals(h2) && !h1.equals(h3), h1);
        check("v2.1.0: no mark on GitHub yet (an account from v2.0.x): read says none",
                call(guard, "read", "tok", "octo") == null, null);
        call(guard, "write", "tok", "octo", h1, null);
        JsonObject made = JsonParser.parseString(repoCreateBody == null ? "{}" : repoCreateBody).getAsJsonObject();
        String[] there = (String[]) call(guard, "read", "tok", "octo");
        check("v2.1.0: the first write makes the private backup repository, then the mark (this phone's hash, its model, "
                        + "no token in it)",
                made.has("private") && made.get("private").getAsBoolean() && "SketchwareAI-Projects".equals(made.get("name").getAsString())
                        && there != null && h1.equals(there[0]) && deviceFile.contains("ART API 31 harness")
                        && !deviceFile.contains("tok"), deviceFile);
        call(guard, "write", "tok", "octo", h3, there[1]);
        String[] after = (String[]) call(guard, "read", "tok", "octo");
        check("v2.1.0: a sign-in on another phone replaces the mark (with the file's sha: no lost update)",
                h3.equals(after[0]) && !h1.equals(after[0]), after[0]);
        String sha = after[1];
        boolean conflict = false;
        try {
            call(guard, "write", "tok", "octo", h1, "s0");
        } catch (Exception e) {
            conflict = true;
        }
        check("v2.1.0: a write with an old sha is refused (two phones signing in at once: one wins, nothing mixed)",
                conflict && h3.equals(((String[]) call(guard, "read", "tok", "octo"))[0]), sha);

        String mineMark = "aaa", other = "bbb";
        check("v2.1.0 review: a check decides: no mark → claim; this phone's → keep; another phone's → sign out; while "
                        + "this phone's own sign-in isn't on GitHub yet → write it again, unless the other mark is newer",
                "claim".equals(call(guard, "decide", null, mineMark, false, 0L))
                        && "keep".equals(call(guard, "decide", new String[]{mineMark, "s", "5"}, mineMark, false, 0L))
                        && "signout".equals(call(guard, "decide", new String[]{other, "s", "5"}, mineMark, false, 0L))
                        && "claim".equals(call(guard, "decide", new String[]{other, "s", "100"}, mineMark, true, 200L))
                        && "signout".equals(call(guard, "decide", new String[]{other, "s", "300"}, mineMark, true, 200L))
                        && "claim".equals(call(guard, "decide", new String[]{other, "s", "0"}, mineMark, true, 200L))
                        && "claim".equals(call(guard, "decide", new String[]{mineMark, "s", "100"}, mineMark, true, 200L)), null);
        check("v2.1.0 review: the mark's time is read back (the newer sign-in decides while a claim waits)",
                Long.parseLong(((String[]) call(guard, "read", "tok", "octo"))[2]) > 1_700_000_000_000L, null);
        String[] before = (String[]) call(guard, "read", "tok", "octo");
        call(guard, "write", "tok", "octo", h3, before[1], 1_795_000_000_123L);
        check("v2.1.0 second review: the mark records when this phone signed in (not when the write happened)",
                "1795000000123".equals(((String[]) call(guard, "read", "tok", "octo"))[2]), null);

        // --- the Play Store
        Class<?> store = c("com.sketchware.ai.github.PlayStore");
        @SuppressWarnings("unchecked")
        List<Object> apps = (List<Object>) call(store, "search", (Object) null);
        check("v2.1.0: Play Store search: public repositories with the topic, newest first, the private one left out, "
                        + "signed out works",
                apps.size() == 2 && "ann".equals(field(apps.get(0), "owner")) && Long.valueOf(11).equals(field(apps.get(0), "number"))
                        && "trunk".equals(field(apps.get(1), "branch")) && last("GET /search/repositories").contains(
                        "q=topic:sketchwareai-playstore") && last("GET /search/repositories").endsWith(" -"),
                apps.size());
        Object notes = apps.get(0);
        call(store, "readListing", (Object) null, notes);
        @SuppressWarnings("unchecked")
        List<String> shots = (List<String>) field(notes, "screenshots");
        check("v2.1.0: the store page: title trimmed, words, the https video link; pictures sorted by name, pictures only, "
                        + "GitHub's file host only, eight at most",
                "Notes Pro".equals(call(notes, "name")) && "Write things down.".equals(field(notes, "description"))
                        && "https://youtu.be/abc".equals(field(notes, "video")) && shots.size() == 8
                        && shots.get(0).endsWith("/01.jpg") && shots.get(1).endsWith("/02.png")
                        && shots.stream().noneMatch(s -> s.contains("evil") || s.endsWith(".txt")),
                shots);
        Object timer = apps.get(1);
        call(store, "readListing", (Object) null, timer);
        check("v2.1.0: an app without a store page keeps the repository's words; its name from the repository",
                "Timer".equals(call(timer, "name")) && ((List<?>) field(timer, "screenshots")).isEmpty()
                        && Boolean.TRUE.equals(field(timer, "listingRead")), call(timer, "name"));
        check("v2.1.0: links: video https only, pictures from raw.githubusercontent.com only",
                call(store, "safeVideo", "javascript:alert(1)") == null && call(store, "safeVideo", "http://x.y/v") == null
                        && call(store, "safeImage", "https://raw.githubusercontent.com/a/b/../c.png") == null
                        && call(store, "safeImage", "https://example.com/a.png") == null, null);
        Object fresh = c("com.sketchware.ai.github.PlayStore$App").getConstructor().newInstance();
        set(fresh, "owner", "ann");
        set(fresh, "repo", "SketchwareAI-Notes");
        set(fresh, "updated", field(notes, "updated"));
        set(fresh, "stars", 9L);
        @SuppressWarnings("unchecked")
        List<Object> merged = (List<Object>) call(store, "merge", java.util.Arrays.asList(fresh), java.util.Arrays.asList(notes));
        Object fresh2 = c("com.sketchware.ai.github.PlayStore$App").getConstructor().newInstance();
        set(fresh2, "owner", "ann");
        set(fresh2, "repo", "SketchwareAI-Notes");
        set(fresh2, "updated", (Long) field(notes, "updated") + 1000);
        @SuppressWarnings("unchecked")
        List<Object> merged2 = (List<Object>) call(store, "merge", java.util.Arrays.asList(fresh2), java.util.Arrays.asList(notes));
        check("v2.1.0: a new search keeps the store page read before (new stars); a repository pushed since is read again",
                merged.get(0) == notes && Long.valueOf(9).equals(field(notes, "stars"))
                        && merged2.get(0) == fresh2 && Boolean.FALSE.equals(field(fresh2, "listingRead"))
                        && "Notes Pro".equals(field(fresh2, "title")), null);

        Object listing = c("com.sketchware.ai.github.PlayStore$Listing").getConstructor().newInstance();
        set(listing, "title", "Demo");
        set(listing, "description", "A demo");
        set(listing, "video", "ftp://nope");
        @SuppressWarnings("unchecked")
        List<byte[]> pics = (List<byte[]>) field(listing, "screenshots");
        pics.add(new byte[]{1, 2, 3});
        pics.add(new byte[]{4, 5});
        Class<?> stepsType = c("com.sketchware.ai.github.PlayStore$Steps");
        List<String> stepTexts = new ArrayList<>();
        Object steps = java.lang.reflect.Proxy.newProxyInstance(stepsType.getClassLoader(), new Class<?>[]{stepsType},
                (p, m, a) -> {
                    if (m.getName().equals("step")) stepTexts.add(a[0] + " " + a[1]);
                    return null;
                });
        boolean refused = false;
        requests.clear();
        try {
            call(store, "publish", "tok", "octo", "sketchwareai-projects", listing, steps);
        } catch (Exception e) {
            refused = e.getMessage().contains("never published");
        }
        check("v2.1.0: the private backup repository is never published (any spelling), no request sent",
                refused && requests.isEmpty(), requests);
        bodies.clear();
        String note = (String) call(store, "publish", "tok", "octo", "SketchwareAI-Demo", listing, steps);
        String patch = body("PATCH /repos/octo/SketchwareAI-Demo");
        String topics = body("PUT /repos/octo/SketchwareAI-Demo/topics");
        String page = body("PUT /repos/octo/SketchwareAI-Demo/contents/playstore/listing.json");
        JsonObject pageFile = page == null ? null : JsonParser.parseString(unb64(JsonParser.parseString(page)
                .getAsJsonObject().get("content").getAsString())).getAsJsonObject();
        check("v2.1.0: publish: public with Discussions on, the store topic added to its own (kept), two pictures, the "
                        + "store page (an unsafe video link left out)",
                patch != null && patch.contains("\"private\":false") && patch.contains("\"has_discussions\":true")
                        && topics != null && topics.contains("sketchwareai-playstore") && topics.contains("tools")
                        && topics.contains("sketchware-ai")
                        && demoFiles.containsKey("playstore/screenshots/01.jpg") && demoFiles.containsKey("playstore/screenshots/02.jpg")
                        && pageFile != null && "Demo".equals(pageFile.get("title").getAsString()) && !pageFile.has("video"),
                demoFiles.keySet());
        check("v2.1.0: publish says when there's no version to offer yet; steps move 5 → 95",
                note != null && note.startsWith("No version yet") && stepTexts.get(0).startsWith("5 ")
                        && stepTexts.get(stepTexts.size() - 1).startsWith("95 "), note + " " + stepTexts);

        // --- GraphQL and Discussions
        Class<?> gql = c("com.sketchware.ai.github.GitHubGraphQL");
        requests.clear();
        String signedOut = message(() -> call(gql, "run", null, "query{viewer{login}}", null));
        String four01 = message(() -> call(gql, "run", "t401", "query{viewer{login}}", null));
        String errors = message(() -> call(gql, "run", "gqlerr", "query{viewer{login}}", null));
        check("v2.1.0: GraphQL: signed out → asks for a sign-in without a request; 401 → the sign-in wasn't accepted; "
                        + "errors → GitHub's own words",
                signedOut != null && signedOut.contains("Sign in") && four01 != null && four01.contains("401")
                        && errors != null && errors.contains("Could not resolve") && requests.size() == 2,
                signedOut + " | " + four01 + " | " + errors);
        String[] info = new String[3];
        @SuppressWarnings("unchecked")
        List<Object> posts = (List<Object>) call(store, "posts", "tok", "ann", "SketchwareAI-Notes", info);
        check("v2.1.0: reviews: the repository's id, Discussions on, its categories, one review with its author and replies",
                "R_1".equals(info[0]) && "true".equals(info[1]) && info[2].equals("C1\tGeneral\nC2\tReviews")
                        && posts.size() == 1 && Integer.valueOf(7).equals(field(posts.get(0), "number"))
                        && "zed".equals(field(posts.get(0), "author")) && Integer.valueOf(2).equals(field(posts.get(0), "comments")),
                info[2]);
        check("v2.1.0: a review goes into \"Reviews\" when there is one, else General, else the first",
                "C2".equals(call(store, "reviewCategory", info[2]))
                        && "C1".equals(call(store, "reviewCategory", "C1\tGeneral\nC3\tIdeas"))
                        && "C3".equals(call(store, "reviewCategory", "C3\tIdeas")), null);
        @SuppressWarnings("unchecked")
        List<Object> thread = (List<Object>) call(store, "thread", "tok", "ann", "SketchwareAI-Notes", 7);
        check("v2.1.0: a discussion with its replies (a deleted account shows as ghost)",
                thread.size() == 3 && "D_7".equals(field(thread.get(0), "id")) && "ann".equals(field(thread.get(1), "author"))
                        && "ghost".equals(field(thread.get(2), "author")), thread.size());
        int number = (Integer) call(store, "newPost", "tok", "R_1", "C2", "  ★★★★★ Great ", " Love it ");
        String vars = graphqlVariables;
        check("v2.1.0: a new post is made in the chosen category (title and text trimmed), its number back",
                number == 42 && vars.contains("\"category\":\"C2\"") && vars.contains("\"title\":\"★★★★★ Great\"")
                        && vars.contains("\"body\":\"Love it\""), vars);
        call(store, "reply", "tok", "D_7", "Thanks");
        check("v2.1.0: a reply goes to the discussion", graphqlVariables.contains("\"id\":\"D_7\""), graphqlVariables);

        // --- the in-app updater: GitHub's hosts only
        Class<?> updater = c("com.sketchware.ai.github.AppUpdater");
        Class<?> bytesType = c("com.sketchware.ai.github.AppUpdater$Bytes");
        Object bytes = java.lang.reflect.Proxy.newProxyInstance(bytesType.getClassLoader(), new Class<?>[]{bytesType},
                (p, m, a) -> null);
        File to = File.createTempFile("update", ".apk");
        String away = message(() -> call(updater, "download", "https://evil.example/Sketchware_AI.apk", to, new boolean[1], bytes));
        String plain = message(() -> call(updater, "download", "http://github.com/smartapps000-hub/x.apk", to, new boolean[1], bytes));
        to.delete();
        check("v2.1.0: the updater downloads from GitHub's hosts over https only (anything else refused before connecting)",
                away != null && away.contains("away from GitHub") && plain != null && plain.contains("away from GitHub"),
                away + " | " + plain);
    }

    interface Throwing {
        Object run() throws Exception;
    }

    static String message(Throwing t) {
        try {
            t.run();
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    static int count(String prefix) {
        int n = 0;
        synchronized (requests) {
            for (String r : requests) if (r.equals(prefix)) n++;
        }
        return n;
    }

    static String last(String prefix) {
        synchronized (requests) {
            for (int i = requests.size() - 1; i >= 0; i--) if (requests.get(i).startsWith(prefix)) return requests.get(i);
        }
        return "";
    }

    static String body(String prefix) {
        synchronized (bodies) {
            for (int i = bodies.size() - 1; i >= 0; i--) {
                if (bodies.get(i).startsWith(prefix + " ")) return bodies.get(i).substring(prefix.length() + 1);
            }
        }
        return null;
    }

    static Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    static void set(Object o, String name, Object value) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }
}
