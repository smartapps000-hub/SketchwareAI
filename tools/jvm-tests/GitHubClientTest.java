import com.sketchware.ai.github.CloudBackup;
import com.sketchware.ai.github.DeviceFlow;
import com.sketchware.ai.github.GitHubException;
import com.sketchware.ai.github.GitHubImport;

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
 * v1.9.8/v1.9.9: the network side of GitHub sign-in and cloud backup (com.sketchware.ai.github: GitHubHttp, DeviceFlow,
 * CloudBackup), compiled from the app's sources, against a fake GitHub on 127.0.0.1 that answers as GitHub's documented
 * endpoints do and checks every request. On the JVM because ART in this workspace can't open sockets (no IPv6); the
 * Android parts (token store, .swb backup and restore) run on the ART harness (h.GitHubTest).
 * Run (from the repository root, after the app's Java compile; the android.util / android.content stand-ins of the
 * harness at run time; v2.0.3: against the app's compiled classes, PublicCopy reaches into the app):
 *   APPC=app/build/intermediates/javac/minApi26Debug/compileMinApi26DebugJavaWithJavac/classes
 *   javac -d out -cp $ANDROID_JAR:$APPC:$GSON tools/jvm-tests/GitHubClientTest.java
 *   java -cp tools/art-harness/out/stubs-cls:out:$APPC:$GSON GitHubClientTest
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

        // --- v1.9.9: polling goes on through network failures (the app in the background while Chrome is in front)
        fake.mode = "flaky";
        fake.polls = 0;
        List<String> trouble = new ArrayList<>();
        String flakyToken = DeviceFlow.waitForToken("Iv1.testclient", code, new DeviceFlow.Waiting() {
            public boolean cancelled() { return false; }
            public void sleep(long millis) { }
            public void networkTrouble(java.io.IOException e) { trouble.add(e.toString()); }
        });
        check("dropped connection and HTTP 502 while waiting: waiting goes on, token arrives", "gho_fake_token_123".equals(flakyToken)
                && fake.polls == 4 && trouble.size() == 2 && trouble.get(1).contains("502"), trouble);
        fake.mode = "denied";
        try {
            DeviceFlow.waitForToken("Iv1.testclient", code, new DeviceFlow.Waiting() {
                public boolean cancelled() { return false; }
                public void sleep(long millis) { }
            });
            check("access_denied ends the sign-in", false, null);
        } catch (GitHubException e) {
            check("access_denied ends the sign-in", "access_denied".equals(e.code), e.code);
        }
        fake.mode = "";
        DeviceFlow.Code old = new DeviceFlow.Code();
        old.deviceCode = "dc-1";
        old.interval = 5;
        old.expiresAt = System.currentTimeMillis() - 1000;
        fake.polls = 0;
        try {
            DeviceFlow.waitForToken("Iv1.testclient", old, new DeviceFlow.Waiting() {
                public boolean cancelled() { return false; }
                public void sleep(long millis) { }
            });
            check("a saved code past expires_at: expired, no request", false, null);
        } catch (GitHubException e) {
            check("a saved code past expires_at: expired, no request", "expired_token".equals(e.code) && fake.polls == 0, e.code);
        }
        check("expires_at = now + expires_in", Math.abs(code.expiresAt - (System.currentTimeMillis() + 900_000L)) < 120_000L, code.expiresAt);

        // --- REST
        check("login", "octo".equals(CloudBackup.login(token)), null);
        CloudBackup.Account account = CloudBackup.account(token);
        check("account: login, numeric id, avatar from avatars.githubusercontent.com", "octo".equals(account.login)
                && "583231".equals(account.id) && fake.avatar.equals(account.avatarUrl), account.id + " " + account.avatarUrl);
        fake.avatar = "https://evil.example/a.png";
        check("account: an avatar on another host is dropped", CloudBackup.account(token).avatarUrl == null, null);
        fake.avatar = "https://avatars.githubusercontent.com/u/583231?v=4";
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
        // --- v2.0.0: profile name and primary e-mail; import from a link
        CloudBackup.Account named = CloudBackup.account(token);
        check("account: profile name", "The Octocat".equals(named.name) && named.email == null, named.name);
        check("primary e-mail: the primary, verified one of /user/emails", "octo@example.com".equals(CloudBackup.primaryEmail(token)),
                CloudBackup.primaryEmail(token));
        fake.mode = "noscope";
        check("primary e-mail: a v1.9.x token without user:email gives none, no error", CloudBackup.primaryEmail(token) == null, null);
        fake.mode = "";
        GitHubImport.Source pub = GitHubImport.parse("https://github.com/pub/demo");
        java.util.List<String> found = GitHubImport.listBackups(null, pub);
        check("import: a repository link lists its .swb files (any case), not folders or other files",
                found.equals(java.util.Arrays.asList("backups/App One.swb", "other/Two.SWB")), found);
        GitHubImport.Source folder = GitHubImport.parse("github.com/pub/demo/tree/main/backups");
        check("import: a folder link lists only that folder", GitHubImport.listBackups(null, folder).equals(
                java.util.Collections.singletonList("backups/App One.swb")), GitHubImport.listBackups(null, folder));
        File importDir = java.nio.file.Files.createTempDirectory("import").toFile();
        File got = GitHubImport.download(null, pub, "backups/App One.swb", importDir);
        check("import: a public repository downloads signed out, the file named after owner, repo and file",
                java.util.Arrays.equals(java.nio.file.Files.readAllBytes(got.toPath()), fake.importBytes)
                        && got.getName().equals("pub-demo-App One.swb") && !fake.importSawToken, got.getName());
        GitHubImport.Source priv = GitHubImport.parse("https://github.com/priv/secret/blob/dev/a.swb");
        try {
            GitHubImport.download(null, priv, priv.path, importDir);
            check("import: a private repository signed out asks for a sign-in", false, null);
        } catch (GitHubException e) {
            check("import: a private repository signed out asks for a sign-in", "not_found".equals(e.code)
                    && e.getMessage().contains("sign in"), e.getMessage());
        }
        File privFile = GitHubImport.download(token, priv, priv.path, importDir);
        check("import: a private repository with the sign-in downloads", privFile.isFile() && privFile.length() == fake.importBytes.length, privFile.length());
        // --- v2.0.1: retries with pauses, Retry-After, the rate-limit gate, writes never sent twice
        Class<?> http = Class.forName("com.sketchware.ai.github.GitHubHttp");
        java.lang.reflect.Field backoffField = http.getDeclaredField("backoff");
        backoffField.setAccessible(true);
        backoffField.set(null, new long[]{20, 40, 60});
        java.lang.reflect.Method resetLimits = http.getDeclaredMethod("resetLimits");
        resetLimits.setAccessible(true);
        fake.mode = "user503";
        fake.userGets = 0;
        CloudBackup.Account again = CloudBackup.account(token);
        check("v2.0.1: a GET answered 503 is tried again and works", "octo".equals(again.login) && fake.userGets == 2, fake.userGets);
        fake.mode = "userDrop";
        fake.userGets = 0;
        again = CloudBackup.account(token);
        check("v2.0.1: a GET whose connection drops is tried again", "octo".equals(again.login) && fake.userGets == 2, fake.userGets);
        fake.mode = "retry1";
        fake.userGets = 0;
        long t0 = System.currentTimeMillis();
        again = CloudBackup.account(token);
        long waited = System.currentTimeMillis() - t0;
        check("v2.0.1: a short Retry-After (429) is waited out, then the GET works", "octo".equals(again.login)
                && fake.userGets == 2 && waited >= 1000, fake.userGets + " requests, " + waited + " ms");
        fake.mode = "limited";
        fake.userGets = 0;
        try {
            CloudBackup.account(token);
            check("v2.0.1: GitHub's hourly limit (403, remaining 0) is rate_limited, not a permission error", false, null);
        } catch (GitHubException e) {
            check("v2.0.1: GitHub's hourly limit (403, remaining 0) is rate_limited, not a permission error",
                    "rate_limited".equals(e.code) && e.getMessage().contains("try again after"), e.code + ": " + e.getMessage());
        }
        int before = fake.userGets;
        fake.mode = "";
        try {
            CloudBackup.account(token);
            check("v2.0.1: while the limit lasts no request goes to GitHub", false, "answered");
        } catch (GitHubException e) {
            check("v2.0.1: while the limit lasts no request goes to GitHub", "rate_limited".equals(e.code)
                    && fake.userGets == before && before == 1, before + " -> " + fake.userGets);
        }
        resetLimits.invoke(null);
        check("v2.0.1: after the limit the API answers again", "octo".equals(CloudBackup.account(token).login), null);
        // review: GitHub's per-network limit for signed-out calls never blocks the signed-in account
        fake.mode = "anonLimited";
        try {
            GitHubImport.listBackups(null, pub);
            check("v2.0.1: a signed-out import over GitHub's network limit says so (sign in or wait)", false, null);
        } catch (GitHubException e) {
            check("v2.0.1: a signed-out import over GitHub's network limit says so (sign in or wait)",
                    "rate_limited".equals(e.code) && e.getMessage().contains("without sign-in"), e.getMessage());
        }
        fake.mode = "";
        fake.userGets = 0;
        check("v2.0.1: ... and the signed-in account (e.g. the read right after a sign-in) is not blocked by it",
                "octo".equals(CloudBackup.account(token).login) && fake.userGets == 1, fake.userGets);
        resetLimits.invoke(null);
        // a reset far away (a wrong phone clock, or GitHub's own): never more than an hour
        fake.mode = "limitedFar";
        try {
            CloudBackup.account(token);
        } catch (GitHubException expected) {
            // rate_limited
        }
        java.lang.reflect.Field blockedField = http.getDeclaredField("BLOCKED");
        blockedField.setAccessible(true);
        long until = (Long) ((java.util.Map<?, ?>) blockedField.get(null)).values().iterator().next();
        long rest = until - android.os.SystemClock.elapsedRealtime();
        check("v2.0.1: a reset five hours away pauses at most one hour", rest > 3_500_000L && rest <= 3_600_000L, rest);
        resetLimits.invoke(null);
        fake.mode = "limitedServerClock";
        try {
            CloudBackup.account(token);
        } catch (GitHubException expected) {
            // rate_limited
        }
        until = (Long) ((java.util.Map<?, ?>) blockedField.get(null)).values().iterator().next();
        rest = until - android.os.SystemClock.elapsedRealtime();
        check("v2.0.1: the pause is reset minus the server's Date (a phone clock 4.5 h behind doesn't stretch it)",
                rest > 1_790_000L && rest <= 1_800_000L, rest);
        resetLimits.invoke(null);
        fake.mode = "";
        fake.postHits = 0;
        java.lang.reflect.Method apiCall = http.getDeclaredMethod("api", String.class, String.class, String.class, String.class);
        apiCall.setAccessible(true);
        Object postAnswer = apiCall.invoke(null, "POST", "/test/post503", token, "{}");
        java.lang.reflect.Field codeField = postAnswer.getClass().getField("code");
        check("v2.0.1: a POST answered 503 is not sent again", fake.postHits == 1 && (Integer) codeField.get(postAnswer) == 503,
                fake.postHits);
        // v2.0.2: the progress of an upload (bytes sent) and of a download (bytes received, Content-Length)
        fake.mode = "";
        java.util.List<long[]> sent = new java.util.ArrayList<>();
        java.lang.reflect.Method postBlob = http.getDeclaredMethod("postBlob", String.class, String.class, File.class,
                Class.forName("com.sketchware.ai.github.GitHubHttp$Progress"));
        postBlob.setAccessible(true);
        File progressFile = File.createTempFile("progress", ".swb");
        java.nio.file.Files.write(progressFile.toPath(), new byte[200_000]);
        Object listener = java.lang.reflect.Proxy.newProxyInstance(GitHubClientTest.class.getClassLoader(),
                new Class<?>[]{Class.forName("com.sketchware.ai.github.GitHubHttp$Progress")}, (proxy, m, values) -> {
                    if (m.getName().equals("bytes")) sent.add(new long[]{(Long) values[0], (Long) values[1]});
                    return null;
                });
        postBlob.invoke(null, "/repos/octo/SketchwareAI-Projects/git/blobs", token, progressFile, listener);
        long[] lastSent = sent.isEmpty() ? new long[]{0, -1} : sent.get(sent.size() - 1);
        check("v2.0.2: upload progress: several reports, ending at the total", sent.size() > 3 && lastSent[0] == lastSent[1]
                && lastSent[1] > 200_000, sent.size() + " reports, last " + lastSent[0] + "/" + lastSent[1]);
        java.util.List<long[]> received = new java.util.ArrayList<>();
        Object downListener = java.lang.reflect.Proxy.newProxyInstance(GitHubClientTest.class.getClassLoader(),
                new Class<?>[]{Class.forName("com.sketchware.ai.github.GitHubHttp$Progress")}, (proxy, m, values) -> {
                    if (m.getName().equals("bytes")) received.add(new long[]{(Long) values[0], (Long) values[1]});
                    return null;
                });
        GitHubImport.download(null, pub, "backups/App One.swb", importDir,
                (com.sketchware.ai.github.GitHubHttp.Progress) downListener);
        long[] lastReceived = received.isEmpty() ? new long[]{0, 0} : received.get(received.size() - 1);
        check("v2.0.2: download progress: bytes received with the total from Content-Length",
                !received.isEmpty() && lastReceived[0] == fake.importBytes.length && lastReceived[1] == fake.importBytes.length,
                lastReceived[0] + "/" + lastReceived[1]);
        CloudBackup linkCloud = new CloudBackup(token, "octo");
        check("v2.0.2: the backup link points at the file on github.com",
                linkCloud.link("projects/com.my.app/My App.swb").equals(
                        "https://github.com/octo/SketchwareAI-Projects/blob/main/projects/com.my.app/My%20App.swb"),
                linkCloud.link("projects/com.my.app/My App.swb"));
        progressFile.delete();
        fake.mode = "dropDownloadOnce";
        fake.downloadHits = 0;
        File again2 = GitHubImport.download(null, pub, "backups/App One.swb", importDir);
        check("v2.0.1: a download cut by the network starts again and completes",
                java.util.Arrays.equals(java.nio.file.Files.readAllBytes(again2.toPath()), fake.importBytes)
                        && fake.downloadHits == 2, fake.downloadHits);
        // --- v2.0.3: backups in parts, retries of one part, a refused sign-in, stale files, history, link imports
        java.lang.reflect.Field pauses = CloudBackup.class.getDeclaredField("blobPauses");
        pauses.setAccessible(true);
        pauses.set(null, new long[]{10, 20});
        CloudBackup partsCloud = new CloudBackup(token, "octo");
        long partBytes = com.sketchware.ai.github.SwbParts.PART_BYTES;
        File large = File.createTempFile("large", ".swb");
        byte[] largeBytes = new byte[(int) (2 * partBytes + 123_457)];
        new java.util.Random(11).nextBytes(largeBytes);
        java.nio.file.Files.write(large.toPath(), largeBytes);
        java.util.List<long[]> partProgress = new java.util.ArrayList<>();
        java.util.List<String> retryLines = new java.util.ArrayList<>();
        CloudBackup.UploadProgress collect = new CloudBackup.UploadProgress() {
            public void uploading(long sentBytes, long all) { partProgress.add(new long[]{sentBytes, all}); }
            public void saving() { }
            public void retrying(String text) { retryLines.add(text); }
        };
        fake.blobPosts.clear();
        String largeCommit = partsCloud.upload(large, "Large App", "com.large.app", "2.0", "v2.0.3-test", collect);
        com.sketchware.ai.github.CloudBackup.Entry largeEntry = null;
        for (CloudBackup.Entry e : partsCloud.list()) if ("Large App".equals(e.name)) largeEntry = e;
        String largePath = "projects/com.large.app/Large_App.swb";
        check("v2.0.3: a backup over 4 MiB goes up in 3 parts (one request each), the list names its parts list",
                fake.blobPosts.size() == 3 && largeEntry != null && largePath.equals(largeEntry.path)
                        && (largePath + ".parts.json").equals(largeEntry.parts), fake.blobPosts.size() + " posts, " + (largeEntry == null ? null : largeEntry.parts));
        check("v2.0.3: the commit holds the parts, the manifest and index.json (no single .swb)",
                fake.lastTree.contains(largePath + ".parts/000") && fake.lastTree.contains(largePath + ".parts/002")
                        && fake.lastTree.contains(largePath + ".parts.json") && !fake.lastTree.contains("\"path\":\"" + largePath + "\""), null);
        long[] lastPart = partProgress.isEmpty() ? new long[]{0, 0} : partProgress.get(partProgress.size() - 1);
        check("v2.0.3: upload progress counts the backup's bytes over all parts", lastPart[0] == largeBytes.length
                && lastPart[1] == largeBytes.length, lastPart[0] + "/" + lastPart[1]);
        File largeBack = new File(large.getParent(), "large-back.swb");
        largeBack.delete();
        fake.blobGets = 0;
        partsCloud.download(largePath, largeCommit, largeBack);
        check("v2.0.3: download of a backup in parts: 3 blobs, joined, checked, equal to the original",
                java.util.Arrays.equals(java.nio.file.Files.readAllBytes(largeBack.toPath()), largeBytes) && fake.blobGets == 3,
                fake.blobGets);
        GitHubImport.Source own = GitHubImport.parse("https://github.com/octo/SketchwareAI-Projects");
        java.util.List<String> listed = GitHubImport.listBackups(token, own);
        check("v2.0.3: a repository link lists a backup in parts by its parts list", listed.contains(largePath + ".parts.json")
                && !listed.contains(largePath), listed);
        File linked = GitHubImport.download(token, GitHubImport.parse("https://github.com/octo/SketchwareAI-Projects/blob/main/"
                + largePath + ".parts.json"), largePath + ".parts.json", importDir);
        check("v2.0.3: a link to the parts list imports the whole backup (named Large_App.swb)",
                java.util.Arrays.equals(java.nio.file.Files.readAllBytes(linked.toPath()), largeBytes)
                        && linked.getName().equals("octo-SketchwareAI-Projects-Large_App.swb"), linked.getName());
        // a one-off 401 on a part while GET /user accepts the sign-in: the part is sent again
        fake.mode = "blob401once";
        fake.blobFailures = 0;
        fake.blobPosts.clear();
        retryLines.clear();
        partsCloud.upload(large, "Large App", "com.large.app", "2.1", "v2.0.3-test", collect);
        check("v2.0.3: a part refused once (401) while the sign-in works is sent again; the backup completes",
                fake.blobPosts.size() == 4 && retryLines.size() == 1 && retryLines.get(0).contains("refused it once")
                        && retryLines.get(0).contains("AB12:CD34"), retryLines);
        fake.mode = "blob503once";
        fake.blobFailures = 0;
        fake.blobPosts.clear();
        retryLines.clear();
        partsCloud.upload(large, "Large App", "com.large.app", "2.2", "v2.0.3-test", collect);
        check("v2.0.3: a part answered 503 is sent again", fake.blobPosts.size() == 4 && retryLines.size() == 1
                && retryLines.get(0).contains("HTTP 503"), retryLines);
        fake.mode = "refusedAll";
        try {
            partsCloud.upload(large, "Large App", "com.large.app", "2.3", "v2.0.3-test", collect);
            check("v2.0.3: GitHub refusing the sign-in: a 401 with GitHub's message and request id", false, null);
        } catch (GitHubException e) {
            // (the desktop JVM's HttpURLConnection gives no error body for a streamed POST's 401; Android's does)
            check("v2.0.3: GitHub refusing the sign-in: a 401 naming the part and GitHub's request id",
                    "http_401".equals(e.code) && e.getMessage().contains("part 1 of 3") && e.getMessage().contains("HTTP 401")
                            && e.getMessage().contains("EF56:7890"), e.getMessage());
        }
        check("v2.0.3: ... and GET /user says the sign-in itself is refused", !partsCloud.signInWorks(), null);
        fake.mode = "";
        // the same project, small again: one .swb; the parts and the manifest go in the same commit
        File small = File.createTempFile("small", ".swb");
        byte[] smallBytes = new byte[100_003];
        new java.util.Random(5).nextBytes(smallBytes);
        java.nio.file.Files.write(small.toPath(), smallBytes);
        fake.deleted.clear();
        String smallCommit = partsCloud.upload(small, "Large App", "com.large.app", "3.0", "v2.0.3-test", collect);
        check("v2.0.3: back to one .swb: its parts list and its 3 parts are removed in that commit",
                fake.deleted.contains(largePath + ".parts.json") && fake.deleted.contains(largePath + ".parts/000")
                        && fake.deleted.contains(largePath + ".parts/002") && fake.deleted.size() == 4, fake.deleted);
        java.util.List<CloudBackup.Version> largeHistory = partsCloud.history(largePath);
        // (the backups of 2.1 and 2.2 sent the same bytes: their parts and parts list didn't change, only index.json)
        check("v2.0.3: history lists the backup in parts and the single one after it, newest first",
                largeHistory.size() == 2 && smallCommit.equals(largeHistory.get(0).sha)
                        && largeCommit.equals(largeHistory.get(1).sha), largeHistory.size());
        File oldBack = new File(large.getParent(), "large-old.swb");
        oldBack.delete();
        partsCloud.download(largePath, largeCommit, oldBack);
        File newBack = new File(large.getParent(), "large-new.swb");
        newBack.delete();
        partsCloud.download(largePath, smallCommit, newBack);
        check("v2.0.3: each version downloads as it was (parts then, one file now)",
                java.util.Arrays.equals(java.nio.file.Files.readAllBytes(oldBack.toPath()), largeBytes)
                        && java.util.Arrays.equals(java.nio.file.Files.readAllBytes(newBack.toPath()), smallBytes), null);
        // the account card's repository count
        check("v2.0.3: account: public + private repositories", CloudBackup.account(token).repositories == 12,
                CloudBackup.account(token).repositories);
        // a project's public copy: its own repository, created public; Private / Public = PATCH private
        Class<?> publicCopy = Class.forName("com.sketchware.ai.github.PublicCopy");
        java.lang.reflect.Method choose = publicCopy.getDeclaredMethod("choose", String.class, String.class, String.class, String.class);
        choose.setAccessible(true);
        java.lang.reflect.Method setPrivate = publicCopy.getDeclaredMethod("setPrivate", String.class, String.class, String.class, boolean.class);
        setPrivate.setAccessible(true);
        String publicRepo = (String) choose.invoke(null, token, "octo", "My App", "com.my.app");
        check("v2.0.3: Public: the project's own repository SketchwareAI-My_App, created public with auto_init",
                "SketchwareAI-My_App".equals(publicRepo) && fake.createdRepos.contains("SketchwareAI-My_App public auto_init"),
                fake.createdRepos);
        Fake.Repo taken = new Fake.Repo();
        taken.description = "Someone's own repository";
        fake.repos.put("SketchwareAI-Taken", taken);
        String second = (String) choose.invoke(null, token, "octo", "Taken", "com.taken");
        check("v2.0.3: a repository of that name not made by Sketchware AI is never used (-2)",
                "SketchwareAI-Taken-2".equals(second) && fake.repos.get("SketchwareAI-Taken").log.isEmpty(), second);
        String again3 = (String) choose.invoke(null, token, "octo", "My App", "com.my.app");
        check("v2.0.3: the project's public repository is found again (no second one)", "SketchwareAI-My_App".equals(again3)
                && fake.createdRepos.size() == 2, fake.createdRepos);
        java.lang.reflect.Constructor<CloudBackup> forRepo = CloudBackup.class.getDeclaredConstructor(String.class, String.class,
                String.class, boolean.class);
        forRepo.setAccessible(true);
        CloudBackup copy = forRepo.newInstance(token, "octo", "SketchwareAI-My_App", true);
        copy.ensureRepository();
        copy.upload(small, "My App", "com.my.app", "1.0", "v2.0.3-test");
        check("v2.0.3: the public copy holds the backup; its link is the public repository's",
                fake.repos.get("SketchwareAI-My_App").filesAt("main").containsKey("projects/com.my.app/My_App.swb"), null);
        setPrivate.invoke(null, token, "octo", "SketchwareAI-My_App", true);
        boolean hidden = fake.repos.get("SketchwareAI-My_App").isPrivate;
        setPrivate.invoke(null, token, "octo", "SketchwareAI-My_App", false);
        check("v2.0.3: Private / Public = PATCH /repos/octo/SketchwareAI-My_App {private: true / false}",
                hidden && !fake.repos.get("SketchwareAI-My_App").isPrivate && fake.patches.size() == 2
                        && fake.patches.get(0).contains("\"private\":true") && fake.patches.get(1).contains("\"private\":false"),
                fake.patches);
        setPrivate.invoke(null, token, "octo", "SketchwareAI-Gone", true);
        check("v2.0.3: Private for a public copy deleted on github.com is fine (nothing to hide)", true, null);
        // the sign-in code request: short timeouts, tried again when the network drops it
        java.lang.reflect.Field startPauses = DeviceFlow.class.getDeclaredField("startPauses");
        startPauses.setAccessible(true);
        startPauses.set(null, new long[]{10, 20});
        fake.mode = "codeDrop";
        fake.codeRequests = 0;
        java.util.List<String> tries = new java.util.ArrayList<>();
        DeviceFlow.Code retried = DeviceFlow.start("Iv1.testclient", DeviceFlow.SCOPE, (attempt, of, last) -> tries.add(attempt + "/" + of));
        check("v2.0.3: a code request the network dropped is asked again at once (try 2 of 3)", "WDJB-MJHT".equals(retried.userCode)
                && fake.codeRequests == 2 && tries.equals(java.util.Collections.singletonList("2/3")), tries);
        fake.mode = "";
        for (File f : new File[]{large, largeBack, small, oldBack, newBack}) f.delete();
        fake.mode = "poll429";
        fake.polls = 0;
        DeviceFlow.Code code429 = DeviceFlow.start("Iv1.testclient", DeviceFlow.SCOPE);
        List<Long> sleeps429 = new ArrayList<>();
        String token429 = DeviceFlow.waitForToken("Iv1.testclient", code429, new DeviceFlow.Waiting() {
            public boolean cancelled() { return false; }
            public void sleep(long millis) { sleeps429.add(millis); }
        });
        check("v2.0.1: a 429 while waiting for the approval keeps the code and asks less often",
                "gho_fake_token_123".equals(token429) && sleeps429.equals(java.util.Arrays.asList(5000L, 30000L)), sleeps429);
        fake.mode = "code429";
        try {
            DeviceFlow.start("Iv1.testclient", DeviceFlow.SCOPE);
            check("v2.0.1: too many code requests: a clear rate-limit message", false, null);
        } catch (GitHubException e) {
            check("v2.0.1: too many code requests: a clear rate-limit message", "http_429".equals(e.code)
                    && e.getMessage().contains("too many"), e.getMessage());
        }
        fake.mode = "";
        for (File f : importDir.listFiles()) f.delete();
        importDir.delete();
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
        boolean importSawToken;
        volatile int userGets, postHits, downloadHits, blobGets, codeRequests;
        String lastTree;
        final List<String> deleted = new ArrayList<>();
        byte[] importBytes = "PK-fake-swb".getBytes(StandardCharsets.UTF_8);
        String avatar = "https://avatars.githubusercontent.com/u/583231?v=4";

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
            if (Integer.valueOf(-1).equals(answer[0])) return; // connection dropped without an answer
            byte[] bytes = answer[1] instanceof byte[] ? (byte[]) answer[1] : String.valueOf(answer[1]).getBytes(StandardCharsets.UTF_8);
            OutputStream out = s.getOutputStream();
            String extra = answer.length > 2 ? (String) answer[2] : "";
            out.write(("HTTP/1.1 " + answer[0] + " X\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length
                    + "\r\n" + extra + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
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
                    codeRequests++;
                    if ("codeDrop".equals(mode) && codeRequests == 1) return new Object[]{-1, ""};
                    if ("disabled".equals(mode)) return new Object[]{400, "{\"error\":\"device_flow_disabled\"}"};
                    if ("code429".equals(mode)) return new Object[]{429, "<html>Too many requests</html>", "Retry-After: 60\r\n"};
                    if (!"repo user:email".equals(form.get("scope"))) formProblems.add("scope " + form.get("scope"));
                    return new Object[]{200, "{\"device_code\":\"dc-1\",\"user_code\":\"WDJB-MJHT\",\"verification_uri\":"
                            + "\"https://github.com/login/device\",\"expires_in\":900,\"interval\":5}"};
                }
                if (target.equals("/web/login/oauth/access_token")) {
                    if (!"dc-1".equals(form.get("device_code")) || !"urn:ietf:params:oauth:grant-type:device_code".equals(form.get("grant_type"))) {
                        formsOk = false;
                        formProblems.add("poll " + form);
                    }
                    polls++;
                    if ("flaky".equals(mode)) {
                        // v1.9.9: what the app sees in the background on Android 15 (no network, then a bad gateway)
                        if (polls == 1) return new Object[]{-1, ""};
                        if (polls == 2) return new Object[]{502, "<html>Bad gateway</html>"};
                        if (polls == 3) return new Object[]{200, "{\"error\":\"authorization_pending\"}"};
                        return new Object[]{200, "{\"access_token\":\"gho_fake_token_123\",\"token_type\":\"bearer\",\"scope\":\"repo\"}"};
                    }
                    if ("denied".equals(mode)) return new Object[]{200, "{\"error\":\"access_denied\"}"};
                    if ("poll429".equals(mode)) {
                        if (polls == 1) return new Object[]{429, "<html>Too many requests</html>", "Retry-After: 30\r\n"};
                        return new Object[]{200, "{\"access_token\":\"gho_fake_token_123\",\"token_type\":\"bearer\",\"scope\":\"repo\"}"};
                    }
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
                userGets++;
                if ("user503".equals(mode) && userGets == 1) return new Object[]{503, "{\"message\":\"Unavailable\"}"};
                if ("userDrop".equals(mode) && userGets == 1) return new Object[]{-1, ""};
                if ("refusedAll".equals(mode)) return new Object[]{401, "{\"message\":\"Bad credentials\"}"};
                if ("retry1".equals(mode) && userGets == 1) {
                    return new Object[]{429, "{\"message\":\"secondary rate limit\"}", "Retry-After: 1\r\n"};
                }
                if ("limitedServerClock".equals(mode)) {
                    // the server's clock is 4.5 h ahead of the phone's; its reset is 30 min after its own Date
                    long serverNow = System.currentTimeMillis() + 270 * 60_000L;
                    java.text.SimpleDateFormat rfc = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US);
                    rfc.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
                    return new Object[]{403, "{\"message\":\"API rate limit exceeded\"}",
                            "Date: " + rfc.format(new java.util.Date(serverNow)) + "\r\nX-RateLimit-Remaining: 0\r\nX-RateLimit-Reset: "
                                    + (serverNow / 1000 + 1800) + "\r\n"};
                }
                if ("limitedFar".equals(mode)) {
                    // the server's clock says the reset is five hours away
                    return new Object[]{403, "{\"message\":\"API rate limit exceeded\"}",
                            "X-RateLimit-Remaining: 0\r\nX-RateLimit-Reset: " + (System.currentTimeMillis() / 1000 + 5 * 3600) + "\r\n"};
                }
                if ("limited".equals(mode)) {
                    return new Object[]{403, "{\"message\":\"API rate limit exceeded for user ID 583231.\"}",
                            "X-RateLimit-Remaining: 0\r\nX-RateLimit-Reset: " + (System.currentTimeMillis() / 1000 + 1800) + "\r\n"};
                }
                return authorized ? new Object[]{200, "{\"login\":\"octo\",\"id\":583231,\"name\":\"The Octocat\",\"email\":null,\"public_repos\":8,\"total_private_repos\":4,\"avatar_url\":\"" + avatar + "\"}"} : new Object[]{401, "{\"message\":\"Bad credentials\"}"};
            }
            // v2.0.0: primary e-mail (scope user:email) and import from other repositories
            if (api.equals("/user/emails")) {
                if (!authorized || "noscope".equals(mode)) return new Object[]{404, "{\"message\":\"Not Found\"}"};
                return new Object[]{200, "[{\"email\":\"old@example.com\",\"primary\":false,\"verified\":true},"
                        + "{\"email\":\"octo@example.com\",\"primary\":true,\"verified\":true,\"visibility\":\"private\"}]"};
            }
            if (api.equals("/test/post503")) {
                postHits++;
                return new Object[]{503, "{\"message\":\"Unavailable\"}"};
            }
            if (api.startsWith("/repos/pub/demo")) {
                if (headers.containsKey("authorization")) importSawToken = true;
                if ("anonLimited".equals(mode) && !headers.containsKey("authorization")) {
                    return new Object[]{403, "{\"message\":\"API rate limit exceeded for 203.0.113.7.\"}",
                            "X-RateLimit-Remaining: 0\r\nX-RateLimit-Reset: " + (System.currentTimeMillis() / 1000 + 1200) + "\r\n"};
                }
                if (api.equals("/repos/pub/demo")) return new Object[]{200, "{\"default_branch\":\"main\",\"private\":false}"};
                if (api.equals("/repos/pub/demo/git/trees/main?recursive=1")) {
                    return new Object[]{200, "{\"sha\":\"t\",\"tree\":[{\"path\":\"backups\",\"type\":\"tree\"},"
                            + "{\"path\":\"backups/App One.swb\",\"type\":\"blob\"},{\"path\":\"backups/notes.txt\",\"type\":\"blob\"},"
                            + "{\"path\":\"other/Two.SWB\",\"type\":\"blob\"},{\"path\":\"backups/dir.swb\",\"type\":\"tree\"}],\"truncated\":false}"};
                }
                if (api.equals("/repos/pub/demo/contents/backups/App%20One.swb?ref=main")
                        && "application/vnd.github.raw".equals(headers.get("accept"))) {
                    downloadHits++;
                    if ("dropDownloadOnce".equals(mode) && downloadHits == 1) return new Object[]{-1, ""};
                    return new Object[]{200, importBytes};
                }
                return new Object[]{404, "{\"message\":\"Not Found\"}"};
            }
            if (api.startsWith("/repos/priv/secret")) {
                if (!authorized) return new Object[]{404, "{\"message\":\"Not Found\"}"};
                if (api.equals("/repos/priv/secret/contents/a.swb?ref=dev")) return new Object[]{200, importBytes};
                return new Object[]{404, "{\"message\":\"Not Found\"}"};
            }
            if (!authorized) {
                apiAuthorizedAll = false;
                apiUnauthorized.add(method + " " + api);
                return new Object[]{401, "{\"message\":\"Bad credentials\"}"};
            }
            // v2.0.3: the account's repositories as a small git store (blobs by their git ids, trees, commits, one
            // branch), so parts, deletions, history and downloads at a commit work as on GitHub
            Repo main = repos.get("SketchwareAI-Projects");
            main.exists = repoExists;
            main.isPrivate = !publicRepo;
            if (api.equals("/user/repos") && method.equals("POST")) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                String name = o.get("name").getAsString();
                if (name.equals("SketchwareAI-Projects")) {
                    createBody = body;
                    createdPrivate = body.contains("\"private\":true") && body.contains("\"auto_init\":true");
                    repoExists = true;
                    main.exists = true;
                    main.isPrivate = true;
                } else {
                    Repo r = repos.computeIfAbsent(name, k -> new Repo());
                    r.exists = true;
                    r.isPrivate = o.get("private").getAsBoolean();
                    r.description = o.has("description") ? o.get("description").getAsString() : "";
                    createdRepos.add(name + (r.isPrivate ? " private" : " public") + (body.contains("\"auto_init\":true") ? " auto_init" : ""));
                }
                return new Object[]{201, "{\"private\":" + o.get("private").getAsBoolean() + ",\"default_branch\":\"main\"}"};
            }
            java.util.regex.Matcher rm = java.util.regex.Pattern.compile("^/repos/octo/([A-Za-z0-9_.-]+)(.*)$").matcher(api);
            if (rm.matches()) {
                Repo r = repos.get(rm.group(1));
                String rest = rm.group(2);
                if (r == null || !r.exists) return new Object[]{404, "{\"message\":\"Not Found\"}"};
                return repoRoute(r, method, rest, headers, body);
            }
            return new Object[]{404, "{\"message\":\"no route " + method + " " + api + "\"}"};
        }

        /** v2.0.3: one repository of the fake account. */
        static final class Repo {
            boolean exists = true, isPrivate = true;
            String description = "";
            final Map<String, byte[]> blobs = new HashMap<>();
            final Map<String, Map<String, String>> trees = new HashMap<>();
            final Map<String, String> commitTree = new HashMap<>();
            final List<String[]> log = new ArrayList<>();
            final Map<String, String> pending = new HashMap<>();
            String head = "c-0";
            int treeCount, commitCount;

            Repo() {
                trees.put("t-0", new HashMap<>());
                commitTree.put("c-0", "t-0");
            }

            Map<String, String> filesAt(String ref) {
                String commit = ref.equals("main") ? head : ref;
                String tree = commitTree.get(commit);
                return tree == null ? null : trees.get(tree);
            }
        }

        final Map<String, Repo> repos = new HashMap<>();
        final List<String> createdRepos = new ArrayList<>();
        final List<String> patches = new ArrayList<>();
        final List<String> blobPosts = new ArrayList<>();
        volatile int blobFailures;

        {
            repos.put("SketchwareAI-Projects", new Repo());
        }

        static String gitSha(byte[] data) throws Exception {
            java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
            sha1.update(("blob " + data.length + "\0").getBytes(StandardCharsets.US_ASCII));
            sha1.update(data);
            StringBuilder out = new StringBuilder();
            for (byte b : sha1.digest()) out.append(String.format("%02x", b));
            return out.toString();
        }

        static String decodePath(String encoded) throws Exception {
            StringBuilder out = new StringBuilder();
            for (String part : encoded.split("/")) {
                if (out.length() > 0) out.append('/');
                out.append(java.net.URLDecoder.decode(part.replace("+", "%2B"), "UTF-8"));
            }
            return out.toString();
        }

        Object[] repoRoute(Repo r, String method, String rest, Map<String, String> headers, String body) throws Exception {
            boolean raw = "application/vnd.github.raw".equals(headers.get("accept"));
            if (rest.isEmpty() && method.equals("GET")) {
                return new Object[]{200, "{\"private\":" + r.isPrivate + ",\"default_branch\":\"main\",\"description\":"
                        + new com.google.gson.Gson().toJson(r.description) + "}"};
            }
            if (rest.isEmpty() && method.equals("PATCH")) {
                patches.add(body);
                r.isPrivate = body.contains("\"private\":true");
                return new Object[]{200, "{\"private\":" + r.isPrivate + "}"};
            }
            if (rest.equals("/git/ref/heads/main")) return new Object[]{200, "{\"object\":{\"sha\":\"" + r.head + "\"}}"};
            if (rest.startsWith("/git/commits/") && method.equals("GET")) {
                String tree = r.commitTree.get(rest.substring("/git/commits/".length()));
                if (tree == null) return new Object[]{404, "{}"};
                return new Object[]{200, "{\"tree\":{\"sha\":\"" + tree + "\"}}"};
            }
            if (rest.startsWith("/git/trees/") && method.equals("GET")) {
                String id = rest.substring("/git/trees/".length()).replace("?recursive=1", "");
                Map<String, String> files = r.trees.get(java.net.URLDecoder.decode(id, "UTF-8"));
                if (files == null) files = r.filesAt(java.net.URLDecoder.decode(id, "UTF-8")); // a branch or commit
                if (files == null) return new Object[]{404, "{}"};
                StringBuilder out = new StringBuilder("{\"sha\":\"" + id + "\",\"truncated\":false,\"tree\":[");
                boolean firstItem = true;
                for (Map.Entry<String, String> e : new java.util.TreeMap<>(files).entrySet()) {
                    if (!firstItem) out.append(',');
                    firstItem = false;
                    out.append("{\"path\":").append(new com.google.gson.Gson().toJson(e.getKey()))
                            .append(",\"type\":\"blob\",\"sha\":\"").append(e.getValue()).append("\"}");
                }
                return new Object[]{200, out.append("]}").toString()};
            }
            if (rest.equals("/git/blobs") && method.equals("POST")) {
                blobPosts.add(String.valueOf(body.length()));
                if (("blob401once".equals(mode) || "blob503once".equals(mode)) && blobFailures == 0 && blobPosts.size() == 2) {
                    blobFailures++;
                    return "blob401once".equals(mode) ? new Object[]{401, "{\"message\":\"Bad credentials\"}", "X-GitHub-Request-Id: AB12:CD34\r\n"}
                            : new Object[]{503, "{\"message\":\"Unavailable\"}"};
                }
                if ("refusedAll".equals(mode)) {
                    return new Object[]{401, "{\"message\":\"Bad credentials\"}", "X-GitHub-Request-Id: EF56:7890\r\n"};
                }
                String content = body.substring(body.indexOf("\"content\":\"") + 11, body.lastIndexOf('"'));
                byte[] data = java.util.Base64.getDecoder().decode(content);
                blob = data;
                String sha = gitSha(data);
                r.blobs.put(sha, data);
                return new Object[]{201, "{\"sha\":\"" + sha + "\"}"};
            }
            if (rest.startsWith("/git/blobs/") && method.equals("GET")) {
                byte[] data = r.blobs.get(rest.substring("/git/blobs/".length()));
                if (data == null) return new Object[]{404, "{}"};
                if (!raw) return new Object[]{415, "{}"};
                blobGets++;
                return new Object[]{200, data};
            }
            if (rest.equals("/git/trees") && method.equals("POST")) {
                treeBody = body.length() > 300 ? body.substring(0, 300) : body;
                lastTree = body;
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                Map<String, String> base = r.trees.get(o.get("base_tree").getAsString());
                if (base == null) return new Object[]{422, "{\"message\":\"base_tree not found\"}"};
                Map<String, String> files = new HashMap<>(base);
                List<String> paths = new ArrayList<>();
                for (com.google.gson.JsonElement e : o.getAsJsonArray("tree")) {
                    com.google.gson.JsonObject item = e.getAsJsonObject();
                    String path = item.get("path").getAsString();
                    paths.add(path);
                    if (item.has("content")) {
                        byte[] data = item.get("content").getAsString().getBytes(StandardCharsets.UTF_8);
                        String sha = gitSha(data);
                        r.blobs.put(sha, data);
                        files.put(path, sha);
                        if (path.equals("index.json")) index = item.get("content").getAsString();
                    } else if (item.get("sha").isJsonNull()) {
                        if (files.remove(path) == null) return new Object[]{422, "{\"message\":\"no such path to delete\"}"};
                        deleted.add(path);
                    } else {
                        String sha = item.get("sha").getAsString();
                        if (!r.blobs.containsKey(sha)) return new Object[]{422, "{\"message\":\"unknown blob\"}"};
                        files.put(path, sha);
                    }
                }
                String swbPath = paths.isEmpty() ? "" : paths.get(0);
                treeOk = r.commitTree.get(r.head).equals(o.get("base_tree").getAsString()) && paths.size() == 2
                        && swbPath.startsWith("projects/") && swbPath.endsWith(".swb") && "index.json".equals(paths.get(1));
                String id = "t-n" + (++r.treeCount);
                r.trees.put(id, files);
                return new Object[]{201, "{\"sha\":\"" + id + "\"}"};
            }
            if (rest.equals("/git/commits") && method.equals("POST")) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                String tree = o.get("tree").getAsString();
                String parent = o.getAsJsonArray("parents").get(0).getAsString();
                String id = r == repos.get("SketchwareAI-Projects") ? "c-" + (refPatches + 1) : "p-" + (++r.commitCount);
                r.commitTree.put(id, tree);
                Map<String, String> before = r.trees.get(r.commitTree.get(parent));
                Map<String, String> after = r.trees.get(tree);
                java.util.Set<String> changed = new java.util.TreeSet<>();
                for (String path : after.keySet()) if (!after.get(path).equals(before.get(path))) changed.add(path);
                for (String path : before.keySet()) if (!after.containsKey(path)) changed.add(path);
                r.pending.put(id, String.join("\n", changed));
                return new Object[]{201, "{\"sha\":\"" + id + "\"}"};
            }
            if (rest.equals("/git/refs/heads/main") && method.equals("PATCH")) {
                if (r == repos.get("SketchwareAI-Projects")) {
                    refPatches++;
                    if (moveBranchOnce) {
                        moveBranchOnce = false;
                        // another phone backed up in between
                        r.commitTree.put("c-x", r.commitTree.get(r.head));
                        r.head = "c-x";
                        return new Object[]{422, "{\"message\":\"Update is not a fast forward\"}"};
                    }
                }
                r.head = first("\"sha\":\"([^\"]+)\"", body);
                String changedPaths = r.pending.remove(r.head);
                if (changedPaths != null) {
                    r.log.add(new String[]{r.head, String.format("2026-11-15T10:%02d:00Z", r.log.size()), changedPaths});
                }
                if (r == repos.get("SketchwareAI-Projects")) head = r.head;
                return new Object[]{200, "{}"};
            }
            if (rest.startsWith("/commits?")) {
                String path = java.net.URLDecoder.decode(first("[?&]path=([^&]+)", rest), "UTF-8");
                StringBuilder out = new StringBuilder("[");
                for (int i = r.log.size() - 1; i >= 0; i--) {
                    String[] c = r.log.get(i);
                    if (!java.util.Arrays.asList(c[2].split("\n")).contains(path)) continue;
                    if (out.length() > 1) out.append(',');
                    out.append("{\"sha\":\"").append(c[0]).append("\",\"commit\":{\"message\":\"Backup\",\"author\":{\"date\":\"")
                            .append(c[1]).append("\"}}}");
                }
                return new Object[]{200, out.append("]").toString()};
            }
            if (rest.startsWith("/contents/")) {
                String path = rest.substring("/contents/".length());
                String ref = "main";
                int q = path.indexOf("?ref=");
                if (q >= 0) {
                    ref = java.net.URLDecoder.decode(path.substring(q + 5), "UTF-8");
                    path = path.substring(0, q);
                }
                Map<String, String> files = r.filesAt(ref);
                String sha = files == null ? null : files.get(decodePath(path));
                if (sha == null) return new Object[]{404, "{\"message\":\"Not Found\"}"};
                if (!raw) return new Object[]{415, "{}"};
                return new Object[]{200, r.blobs.get(sha)};
            }
            return new Object[]{404, "{\"message\":\"no repo route " + method + " " + rest + "\"}"};
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
