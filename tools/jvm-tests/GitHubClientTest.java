import com.sketchware.ai.github.CloudBackup;
import com.sketchware.ai.github.DeviceFlow;
import com.sketchware.ai.github.GitHubException;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.9.8: the network side of GitHub sign-in and cloud backup (com.sketchware.ai.github: GitHubHttp, DeviceFlow,
 * CloudBackup), compiled from the app's sources, against a fake GitHub on 127.0.0.1 that answers as GitHub's documented
 * endpoints do and checks every request. On the JVM because ART in this workspace can't open sockets (no IPv6); the
 * Android parts (token store, .swb backup and restore) run on the ART harness (h.GitHubTest).
 * Run (from the repository root; the android.util / android.content stand-ins of the harness at run time):
 *   javac -d out -cp $ANDROID_JAR:$GSON tools/jvm-tests/GitHubClientTest.java app/src/main/java/com/sketchware/ai/github/{GitHubHttp,DeviceFlow,GitHubException,TokenStore,CloudBackup}.java
 *   java -cp tools/art-harness/out/stubs-cls:out:$GSON GitHubClientTest
 * Prints CLIENT CHECK lines and "GITHUBCLIENT n/n ok".
 */
public class GitHubClientTest {
    static int ok, total;

    static void check(String name, boolean result, Object detail) {
        total++;
        if (result) ok++;
        System.out.println("CLIENT CHECK " + (result ? "ok  " : "FAIL") + " " + name + (detail == null ? "" : " - " + detail));
    }

    public static void main(String[] args) throws Exception {
        Fake fake = new Fake();
        fake.start();
        String base = "http://127.0.0.1:" + fake.port;
        java.lang.reflect.Method use = Class.forName("com.sketchware.ai.github.GitHubHttp").getDeclaredMethod("useEndpoints", String.class, String.class);
        use.setAccessible(true);
        use.invoke(null, base + "/web", base + "/api");

        // --- Device Flow
        DeviceFlow.Code code = DeviceFlow.start("Iv1.testclient", DeviceFlow.SCOPE);
        check("device code read", "WDJB-MJHT".equals(code.userCode) && "dc-1".equals(code.deviceCode) && code.interval == 5, code.userCode);
        List<Long> sleeps = new ArrayList<>();
        String token = DeviceFlow.waitForToken("Iv1.testclient", code, new DeviceFlow.Waiting() {
            public boolean cancelled() { return false; }
            public void sleep(long millis) { sleeps.add(millis); }
        });
        check("token after authorization_pending + slow_down", "gho_fake_token_123".equals(token), token);
        check("waits the interval, then interval + 5 after slow_down", sleeps.equals(java.util.Arrays.asList(5000L, 5000L, 10000L)), sleeps);
        check("device requests carry client_id and grant type, no client secret", fake.formsOk, fake.formProblems);
        check("no Authorization header ever sent to github.com", !fake.webSawAuthorization, null);
        fake.mode = "disabled";
        try {
            DeviceFlow.start("Iv1.testclient", DeviceFlow.SCOPE);
            check("device_flow_disabled explained", false, "no exception");
        } catch (GitHubException e) {
            check("device_flow_disabled explained", e.getMessage().contains("Enable Device Flow"), e.getMessage());
        }
        fake.mode = "";
        try {
            DeviceFlow.start("", DeviceFlow.SCOPE);
            check("empty Client ID refused before any request", false, null);
        } catch (GitHubException e) {
            check("empty Client ID refused before any request", "no_client_id".equals(e.code), e.getMessage());
        }
        boolean[] stop = {false};
        try {
            fake.polls = 0;
            DeviceFlow.waitForToken("Iv1.testclient", code, new DeviceFlow.Waiting() {
                public boolean cancelled() { return stop[0]; }
                public void sleep(long millis) { stop[0] = true; }
            });
            check("cancel stops waiting", false, null);
        } catch (GitHubException e) {
            check("cancel stops waiting", "cancelled".equals(e.code) && fake.polls == 0, fake.polls);
        }

        // --- REST
        check("login", "octo".equals(CloudBackup.login(token)), null);
        try {
            CloudBackup.login("wrong");
            check("bad token refused", false, null);
        } catch (GitHubException e) {
            check("bad token refused", "unauthorized".equals(e.code), e.getMessage());
        }
        CloudBackup cloud = new CloudBackup(token, "octo");
        fake.publicRepo = true;
        try {
            cloud.ensureRepository();
            check("a public repository of that name is refused", false, null);
        } catch (GitHubException e) {
            check("a public repository of that name is refused", e.getMessage().contains("is public"), e.getMessage());
        }
        fake.publicRepo = false;
        fake.repoExists = false;
        cloud = new CloudBackup(token, "octo");
        cloud.ensureRepository();
        check("private repository created (private, auto_init)", fake.createdPrivate, fake.createBody);

        File swb = File.createTempFile("backup", ".swb");
        byte[] bytes = new byte[300_001];
        new java.util.Random(7).nextBytes(bytes);
        java.nio.file.Files.write(swb.toPath(), bytes);
        fake.moveBranchOnce = true;
        String commit = cloud.upload(swb, "My App", "com.my.newproject", "1.0", "v1.9.8-test");
        check("upload: blob = the file's bytes (streamed base64)", java.util.Arrays.equals(fake.blob, bytes), fake.blob == null ? null : fake.blob.length);
        check("upload: one tree with the .swb and index.json on the head's tree", fake.treeOk, fake.treeBody);
        check("upload: retried once after the branch moved", fake.refPatches == 2 && "c-2".equals(commit), "patches " + fake.refPatches);
        List<CloudBackup.Entry> entries = cloud.list();
        check("list reads index.json", entries.size() == 1 && "projects/com.my.newproject/My_App.swb".equals(entries.get(0).path)
                && "My App".equals(entries.get(0).name) && entries.get(0).size == bytes.length, entries.size());
        List<CloudBackup.Version> versions = cloud.history(entries.get(0).path);
        check("history", versions.size() == 1 && "c-2".equals(versions.get(0).sha), versions.size());
        File downloaded = File.createTempFile("download", ".swb");
        downloaded.delete();
        cloud.download(entries.get(0).path, "c-2", downloaded);
        check("download = uploaded bytes (raw media type)", java.util.Arrays.equals(java.nio.file.Files.readAllBytes(downloaded.toPath()), bytes), downloaded.length());
        check("every API request carried the token", fake.apiAuthorizedAll, fake.apiUnauthorized);
        File big = File.createTempFile("big", ".swb");
        try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(big, "rw")) {
            r.setLength(CloudBackup.MAX_BYTES + 1);
        }
        try {
            cloud.upload(big, "Big", "com.big", "1", "t");
            check("files over the limit refused before upload", false, null);
        } catch (GitHubException e) {
            check("files over the limit refused before upload", "too_large".equals(e.code), e.getMessage());
        }
        big.delete();
        swb.delete();
        downloaded.delete();
        fake.stop();
        System.out.println("GITHUBCLIENT " + ok + "/" + total + (ok == total ? " ok" : " FAILED"));
        System.exit(ok == total ? 0 : 1);
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
                    if (!server.isClosed()) e.printStackTrace();
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
            if (headers.containsKey("x-http-method-override")) method = headers.get("x-http-method-override");
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
                List<String> paths = all("\"path\":\"([^\"]+)\"", body);
                String swbPath = paths.isEmpty() ? "" : paths.get(0);
                treeOk = ("t-" + head).equals(first("\"base_tree\":\"([^\"]+)\"", body)) && paths.size() == 2
                        && swbPath.startsWith("projects/") && swbPath.endsWith(".swb")
                        && "b-1".equals(first("\"sha\":\"([^\"]+)\"", body)) && "index.json".equals(paths.get(1));
                index = unescape(first("\"content\":\"((?:[^\"\\\\]|\\\\.)*)\"", body));
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
                head = first("\"sha\":\"([^\"]+)\"", body);
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

        static String first(String regex, String text) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
            return m.find() ? m.group(1) : null;
        }

        static List<String> all(String regex, String text) {
            List<String> out = new ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
            while (m.find()) out.add(m.group(1));
            return out;
        }

        static String unescape(String s) {
            if (s == null) return null;
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch != '\\' || i + 1 >= s.length()) { b.append(ch); continue; }
                char n = s.charAt(++i);
                if (n == 'n') b.append('\n');
                else if (n == 't') b.append('\t');
                else if (n == 'u') { b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
                else b.append(n);
            }
            return b.toString();
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
