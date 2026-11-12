package h;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static h.Main.CTX;
import static h.Main.OUT;
import static h.R.*;

/**
 * v1.9.8: GitHub sign-in (Device Flow), the token store and cloud backup, with the shipped APK's classes, against a fake
 * GitHub on 127.0.0.1 (the real one needs the owner's OAuth app and account). The fake answers as GitHub's documented
 * endpoints do and checks every request. The Android Keystore can't run here: the token store gets a software AES
 * key through its KeySource, so its encryption and storage are what runs; the Keystore key itself is not.
 * Usage: githubtest <sc_id of a project to back up and restore>. Prints GITHUB CHECK lines and "GITHUBTEST n/n ok".
 */
public final class GitHubTest {
    private static int ok, total;

    static void check(String name, boolean result, Object detail) {
        total++;
        if (result) ok++;
        OUT.println("GITHUB CHECK " + (result ? "ok  " : "FAIL") + " " + name + (detail == null ? "" : " - " + detail));
    }

    static void run(String scId) throws Throwable {
        Fake fake = new Fake();
        fake.start();
        String base = "http://127.0.0.1:" + fake.port;
        Class<?> http = c("com.sketchware.ai.github.GitHubHttp");
        call(http, "useEndpoints", base + "/web", base + "/api");

        // --- base64 streaming = java.util.Base64
        int[] sizes = {0, 1, 2, 3, 4, 5, 49151, 49152, 49153, 100003};
        boolean same = true;
        for (int n : sizes) {
            byte[] data = new byte[n];
            new java.util.Random(n).nextBytes(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            call(c("com.sketchware.ai.github.GitHubHttp$Base64Stream"), "encode", new java.io.ByteArrayInputStream(data), out);
            same &= java.util.Base64.getEncoder().encodeToString(data).equals(out.toString("US-ASCII"));
        }
        check("base64 stream equals java.util.Base64 for " + sizes.length + " sizes", same, null);

        // --- Device Flow
        Class<?> flow = c("com.sketchware.ai.github.DeviceFlow");
        Object code = call(flow, "start", "Iv1.testclient", "repo");
        check("device code read", "WDJB-MJHT".equals(get(code, "userCode")) && "dc-1".equals(get(code, "deviceCode")),
                get(code, "userCode"));
        List<Long> sleeps = new ArrayList<>();
        Object waiting = proxy("com.sketchware.ai.github.DeviceFlow$Waiting", (m, a) -> {
            if (m.equals("sleep")) sleeps.add((Long) a[0]);
            return m.equals("cancelled") ? Boolean.FALSE : null;
        });
        String token = (String) call(flow, "waitForToken", "Iv1.testclient", code, waiting);
        check("token after pending + slow_down", "gho_fake_token_123".equals(token), token);
        check("waits interval, then interval + 5 after slow_down", sleeps.equals(java.util.Arrays.asList(5000L, 5000L, 10000L)), sleeps);
        check("device requests carry client_id, no secret", fake.formsOk, fake.formProblems);
        check("no Authorization header ever sent to github.com", !fake.webSawAuthorization, null);
        fake.mode = "disabled";
        try {
            call(flow, "start", "Iv1.testclient", "repo");
            check("device_flow_disabled explained", false, "no exception");
        } catch (Throwable t) {
            check("device_flow_disabled explained", String.valueOf(t.getMessage()).contains("Enable Device Flow"), t.getMessage());
        }
        fake.mode = "";

        // --- token store (software AES key standing in for the Keystore's)
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        Object[] current = {key};
        boolean[] deleted = {false};
        Object keys = proxy("com.sketchware.ai.github.TokenStore$KeySource", (m, a) -> {
            if (m.equals("key")) return current[0];
            if (m.equals("delete")) deleted[0] = true;
            return null;
        });
        Object prefs = call(CTX, "getSharedPreferences", "sketchware_ai_github_test", 0);
        Object store = neu("com.sketchware.ai.github.TokenStore", prefs, keys);
        call(store, "save", token, "octo");
        String stored = (String) call(prefs, "getString", "token", null);
        check("stored as v1:iv:ciphertext, token not readable", stored != null && stored.startsWith("v1:")
                && stored.split(":").length == 3 && !stored.contains(token), stored);
        Object again = neu("com.sketchware.ai.github.TokenStore", prefs, keys);
        check("read back", token.equals(call(again, "token")) && "octo".equals(call(again, "login")), null);
        String first = stored;
        call(store, "save", token, "octo");
        check("new IV every save", !first.equals(call(prefs, "getString", "token", null)), null);
        current[0] = KeyGenerator.getInstance("AES").generateKey();
        check("other key: signed out, nothing returned", call(again, "token") == null && call(again, "login") == null, null);
        current[0] = key;
        call(store, "save", token, "octo");
        call(store, "clear");
        check("sign-out removes token, login and key", call(store, "token") == null && deleted[0], null);
        call(store, "setClientId", "  Iv1.typed  ");
        check("typed Client ID wins over the built-in one", "Iv1.typed".equals(call(store, "clientId", "Iv1.builtin")), null);

        // --- cloud backup: login, repository, upload with a moved branch, list, history, download, restore
        Class<?> cloudClass = c("com.sketchware.ai.github.CloudBackup");
        check("login", "octo".equals(call(cloudClass, "login", token)), null);
        try {
            call(cloudClass, "login", "wrong");
            check("bad token refused", false, null);
        } catch (Throwable t) {
            check("bad token refused", t.getClass().getName().endsWith("GitHubException"), t.getMessage());
        }
        check("path keeps projects with the default package apart",
                "projects/com.my.newproject/My_App_2.swb".equals(call(cloudClass, "pathFor", "com.my.newproject", "My App 2"))
                        && "projects/no-package/project.swb".equals(call(cloudClass, "pathFor", "../x", "..")),
                call(cloudClass, "pathFor", "../x", ".."));

        Object cloud = neu("com.sketchware.ai.github.CloudBackup", token, "octo");
        fake.publicRepo = true;
        try {
            call(cloud, "ensureRepository");
            check("a public repository is refused", false, null);
        } catch (Throwable t) {
            check("a public repository is refused", String.valueOf(t.getMessage()).contains("is public"), t.getMessage());
        }
        fake.publicRepo = false;
        fake.repoExists = false;
        cloud = neu("com.sketchware.ai.github.CloudBackup", token, "octo");
        call(cloud, "ensureRepository");
        check("private repository created (private, auto_init)", fake.createdPrivate, fake.createBody);

        // the project's real .swb, made by the app's own backup
        Object factory = neu("mod.hey.studios.project.backup.BackupFactory", scId);
        call(factory, "setBackupCustomBlocks", true);
        Map<String, Object> project = (Map<String, Object>) call(c("a.a.a.lC"), "b", scId);
        String name = (String) call(c("a.a.a.yB"), "c", project, "my_ws_name");
        String pkg = (String) call(c("a.a.a.yB"), "c", project, "my_sc_pkg_name");
        call(factory, "backup", name);
        File swb = (File) call(factory, "getOutFile");
        check("project " + scId + " backed up to a .swb", swb != null && swb.isFile() && swb.length() > 1000, swb);
        byte[] swbBytes = java.nio.file.Files.readAllBytes(swb.toPath());
        fake.moveBranchOnce = true;
        String commit = (String) call(cloud, "upload", swb, name, pkg, "1.0", "v1.9.8-test");
        check("upload: blob = the .swb bytes", java.util.Arrays.equals(fake.blob, swbBytes), fake.blob == null ? null : fake.blob.length);
        String path = (String) call(cloudClass, "pathFor", pkg, name);
        check("upload: one tree with the .swb and index.json on the head's tree", fake.treeOk, fake.treeBody);
        check("upload: retried once after the branch moved", fake.refPatches == 2 && commit != null, "patches " + fake.refPatches);
        List<?> entries = (List<?>) call(cloud, "list");
        check("list reads index.json", entries.size() == 1 && path.equals(get(entries.get(0), "path"))
                && name.equals(get(entries.get(0), "name")), entries.size());
        List<?> versions = (List<?>) call(cloud, "history", path);
        check("history", versions.size() == 1 && "c-2".equals(get(versions.get(0), "sha")), versions.size());
        File downloaded = new File(swb.getParentFile(), "cloud-download.swb");
        call(cloud, "download", path, "c-2", downloaded);
        check("download = uploaded bytes", java.util.Arrays.equals(java.nio.file.Files.readAllBytes(downloaded.toPath()), swbBytes), downloaded.length());
        check("API requests carried the token", fake.apiAuthorizedAll, fake.apiUnauthorized);

        // restore as a new project, through the app's own restore and the v1.9.8 free id
        String freeId = (String) call(c("com.sketchware.ai.project.ProjectIds"), "free", call(c("a.a.a.lC"), "b"));
        Object restorer = neu("mod.hey.studios.project.backup.BackupFactory", freeId);
        call(restorer, "restore", downloaded);
        Map<String, Object> restored = (Map<String, Object>) call(c("a.a.a.lC"), "b", freeId);
        check("restored as new project #" + freeId, Boolean.TRUE.equals(call(restorer, "isRestoreSuccess")) && restored != null
                && name.equals(call(c("a.a.a.yB"), "c", restored, "my_ws_name")) && !freeId.equals(scId), call(restorer, "getError"));
        swb.delete();
        downloaded.delete();

        fake.stop();
        OUT.println("GITHUBTEST " + ok + "/" + total + (ok == total ? " ok" : " FAILED"));
    }

    /** A fake GitHub: /web = github.com (Device Flow), /api = api.github.com. One request per connection. */
    static final class Fake implements Runnable {
        ServerSocket server;
        int port;
        volatile String mode = "";
        int polls;
        boolean formsOk = true;
        final List<String> formProblems = new ArrayList<>();
        boolean webSawAuthorization;
        boolean apiAuthorizedAll = true;
        final List<String> apiUnauthorized = new ArrayList<>();
        boolean publicRepo, repoExists = true, createdPrivate;
        String createBody;
        byte[] blob;
        String index;
        boolean treeOk;
        String treeBody;
        boolean moveBranchOnce;
        int refPatches;
        String head = "c-0";

        void start() throws Exception {
            server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            port = server.getLocalPort();
            Thread t = new Thread(this, "fake-github");
            t.setDaemon(true);
            t.start();
        }

        void stop() throws Exception {
            server.close();
        }

        public void run() {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    handle(s);
                } catch (Exception e) {
                    if (!server.isClosed()) e.printStackTrace(OUT);
                }
            }
        }

        void handle(Socket s) throws Exception {
            InputStream in = s.getInputStream();
            String requestLine = line(in);
            Map<String, String> headers = new HashMap<>();
            for (String h = line(in); !h.isEmpty(); h = line(in)) {
                int colon = h.indexOf(':');
                if (colon > 0) headers.put(h.substring(0, colon).trim().toLowerCase(), h.substring(colon + 1).trim());
            }
            int length = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
            byte[] body = new byte[length];
            for (int read = 0; read < length; ) {
                int n = in.read(body, read, length - read);
                if (n < 0) break;
                read += n;
            }
            String[] parts = requestLine.split(" ");
            String method = parts[0], target = parts[1];
            String text = new String(body, StandardCharsets.UTF_8);
            Object[] answer = route(method, target, headers, text);
            byte[] bytes = answer[1] instanceof byte[] ? (byte[]) answer[1] : String.valueOf(answer[1]).getBytes(StandardCharsets.UTF_8);
            OutputStream out = s.getOutputStream();
            out.write(("HTTP/1.1 " + answer[0] + " X\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        }

        Object[] route(String method, String target, Map<String, String> headers, String body) throws Exception {
            if (target.startsWith("/web/")) {
                if (headers.containsKey("authorization")) webSawAuthorization = true;
                Map<String, String> form = form(body);
                if (!"Iv1.testclient".equals(form.get("client_id")) || form.containsKey("client_secret")) {
                    formsOk = false;
                    formProblems.add(target + " " + form);
                }
                if (target.equals("/web/login/device/code")) {
                    if ("disabled".equals(mode)) return new Object[]{400, "{\"error\":\"device_flow_disabled\"}"};
                    if (!"repo".equals(form.get("scope"))) formProblems.add("scope " + form.get("scope"));
                    return new Object[]{200, "{\"device_code\":\"dc-1\",\"user_code\":\"WDJB-MJHT\",\"verification_uri\":"
                            + "\"https://github.com/login/device\",\"expires_in\":900,\"interval\":5}"};
                }
                if (target.equals("/web/login/oauth/access_token")) {
                    if (!"dc-1".equals(form.get("device_code")) || !"urn:ietf:params:oauth:grant-type:device_code".equals(form.get("grant_type"))) {
                        formsOk = false;
                        formProblems.add("poll " + form);
                    }
                    polls++;
                    if (polls == 1) return new Object[]{200, "{\"error\":\"authorization_pending\"}"};
                    if (polls == 2) return new Object[]{200, "{\"error\":\"slow_down\",\"interval\":10}"};
                    return new Object[]{200, "{\"access_token\":\"gho_fake_token_123\",\"token_type\":\"bearer\",\"scope\":\"repo\"}"};
                }
                return new Object[]{404, "{}"};
            }
            String auth = headers.get("authorization");
            boolean authorized = "Bearer gho_fake_token_123".equals(auth);
            String api = target.substring("/api".length());
            if (api.equals("/user")) {
                return authorized ? new Object[]{200, "{\"login\":\"octo\"}"} : new Object[]{401, "{\"message\":\"Bad credentials\"}"};
            }
            if (!authorized) {
                apiAuthorizedAll = false;
                apiUnauthorized.add(method + " " + api);
                return new Object[]{401, "{\"message\":\"Bad credentials\"}"};
            }
            String repo = "/repos/octo/SketchwareAI-Projects";
            if (api.equals(repo) && method.equals("GET")) {
                if (!repoExists) return new Object[]{404, "{\"message\":\"Not Found\"}"};
                return new Object[]{200, "{\"private\":" + !publicRepo + ",\"default_branch\":\"main\"}"};
            }
            if (api.equals("/user/repos") && method.equals("POST")) {
                createBody = body;
                createdPrivate = body.contains("\"private\":true") && body.contains("\"auto_init\":true")
                        && body.contains("\"name\":\"SketchwareAI-Projects\"");
                repoExists = true;
                return new Object[]{201, "{\"private\":true,\"default_branch\":\"main\"}"};
            }
            if (api.equals(repo + "/git/ref/heads/main")) return new Object[]{200, "{\"object\":{\"sha\":\"" + head + "\"}}"};
            if (api.startsWith(repo + "/git/commits/") && method.equals("GET")) {
                return new Object[]{200, "{\"tree\":{\"sha\":\"t-" + api.substring(api.lastIndexOf('/') + 1) + "\"}}"};
            }
            if (api.startsWith(repo + "/contents/index.json")) {
                if (index == null) return new Object[]{404, "{\"message\":\"Not Found\"}"};
                return new Object[]{200, "{\"content\":\"" + java.util.Base64.getMimeEncoder().encodeToString(index.getBytes(StandardCharsets.UTF_8))
                        .replace("\r\n", "\\n") + "\"}"};
            }
            if (api.equals(repo + "/git/blobs")) {
                String content = body.substring(body.indexOf("\"content\":\"") + 11, body.lastIndexOf('"'));
                blob = java.util.Base64.getDecoder().decode(content);
                return new Object[]{201, "{\"sha\":\"b-1\"}"};
            }
            if (api.equals(repo + "/git/trees")) {
                treeBody = body.length() > 300 ? body.substring(0, 300) : body;
                com.google.gson.JsonObject t = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                com.google.gson.JsonArray items = t.getAsJsonArray("tree");
                String swbPath = items.get(0).getAsJsonObject().get("path").getAsString();
                treeOk = ("t-" + head).equals(t.get("base_tree").getAsString()) && items.size() == 2
                        && swbPath.startsWith("projects/") && swbPath.endsWith(".swb")
                        && "b-1".equals(items.get(0).getAsJsonObject().get("sha").getAsString())
                        && "index.json".equals(items.get(1).getAsJsonObject().get("path").getAsString());
                index = items.get(1).getAsJsonObject().get("content").getAsString();
                return new Object[]{201, "{\"sha\":\"t-new\"}"};
            }
            if (api.equals(repo + "/git/commits") && method.equals("POST")) {
                return new Object[]{201, "{\"sha\":\"c-" + (refPatches + 1) + "\"}"};
            }
            if (api.equals(repo + "/git/refs/heads/main") && method.equals("PATCH")) {
                refPatches++;
                if (moveBranchOnce) {
                    moveBranchOnce = false;
                    head = "c-x"; // another phone backed up in between
                    return new Object[]{422, "{\"message\":\"Update is not a fast forward\"}"};
                }
                head = com.google.gson.JsonParser.parseString(body).getAsJsonObject().get("sha").getAsString();
                return new Object[]{200, "{}"};
            }
            if (api.startsWith(repo + "/commits?")) {
                return new Object[]{200, "[{\"sha\":\"" + head + "\",\"commit\":{\"message\":\"Backup\",\"author\":{\"date\":\"2026-11-12T10:00:00Z\"}}}]"};
            }
            if (api.startsWith(repo + "/contents/projects/")) {
                if (!"application/vnd.github.raw".equals(headers.get("accept"))) return new Object[]{415, "{}"};
                return new Object[]{200, blob};
            }
            return new Object[]{404, "{\"message\":\"no route " + method + " " + api + "\"}"};
        }

        static Map<String, String> form(String body) throws Exception {
            Map<String, String> out = new LinkedHashMap<>();
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) out.put(java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8"), java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            }
            return out;
        }

        static String line(InputStream in) throws Exception {
            StringBuilder b = new StringBuilder();
            int c;
            while ((c = in.read()) >= 0 && c != '\n') if (c != '\r') b.append((char) c);
            return b.toString();
        }
    }
}
