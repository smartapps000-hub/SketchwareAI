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
 * v1.9.8/v1.9.9/v2.0.0/v2.0.1: the parts of GitHub sign-in and cloud backup that run without a network, with the shipped APK's classes:
 * the token store (a software AES key stands in for the Android Keystore's, which can't run here), the streamed
 * base64, backup paths, and a real .swb of the project restored as a new project with a free id (ProjectIds), plus
 * the restore fixes (leftover folders, ../ entries). ART here can't open sockets (libcore always creates IPv6 ones
 * and this machine has no IPv6): the Device Flow and the REST calls run in tools/jvm-tests/GitHubClientTest.
 * Usage: githubtest <sc_id of a project to back up and restore>. Prints GITHUB CHECK lines and "GITHUBTEST n/n ok".
 */
public final class GitHubTest {
    private static int ok, total;

    static void check(String name, boolean result, Object detail) {
        total++;
        if (result) ok++;
        OUT.println("GITHUB CHECK " + (result ? "ok  " : "FAIL") + " " + name + (detail == null ? "" : " - " + detail));
    }

    /** v2.0.5: throws a checked exception (a job's Cancelled) out of a proxy handler as it is. */
    @SuppressWarnings("unchecked")
    static <T extends Throwable> void sneaky(Throwable t) throws T {
        throw (T) t;
    }

    /** v2.0.3: polls {@code condition} until it holds or {@code millis} pass; true when it held. */
    static boolean waitFor(java.util.function.BooleanSupplier condition, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    static void run(String scId) throws Throwable {
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

        // --- Android's HttpURLConnection takes PATCH (the update-ref call); no connection is made
        java.net.HttpURLConnection patch = (java.net.HttpURLConnection) new java.net.URL("https://api.github.com/x").openConnection();
        String patchProblem = null;
        try {
            patch.setRequestMethod("PATCH");
        } catch (java.net.ProtocolException e) {
            patchProblem = e.toString();
        }
        check("Android's HttpURLConnection accepts PATCH", patchProblem == null && "PATCH".equals(patch.getRequestMethod()), patchProblem);

        // --- token store (software AES key standing in for the Keystore's), v1.9.9: bound to the device
        String token = "gho_fake_token_123";
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        Object[] current = {key};
        boolean[] deleted = {false};
        Object keys = proxy("com.sketchware.ai.github.TokenStore$KeySource", (m, a) -> {
            if (m.equals("key")) return current[0];
            if (m.equals("delete")) deleted[0] = true;
            return null;
        });
        String[] device = {"android-id-1|cert-A"};
        Object binding = proxy("com.sketchware.ai.github.TokenStore$DeviceBinding", (m, a) -> m.equals("device") ? device[0] : null);
        Object prefs = call(CTX, "getSharedPreferences", "sketchware_ai_github_test", 0);
        Object store = neu("com.sketchware.ai.github.TokenStore", prefs, keys, binding);
        call(store, "save", token, "octo", "583231");
        String stored = (String) call(prefs, "getString", "token", null);
        check("stored as v2:iv:ciphertext (AES-GCM), token not readable", stored != null && stored.startsWith("v2:")
                && stored.split(":").length == 3 && !stored.contains(token), stored);
        Object again = neu("com.sketchware.ai.github.TokenStore", prefs, keys, binding);
        check("read back with login and user id", token.equals(call(again, "token")) && "octo".equals(call(again, "login"))
                && "583231".equals(call(again, "userId")), null);
        String first = stored;
        call(store, "save", token, "octo", "583231");
        check("new IV every save", !first.equals(call(prefs, "getString", "token", null)), null);
        // v2.0.3 (owner: "never auto-logout"): a token that can't be read gives nothing, but stays stored (login kept),
        // with the reason; only "Sign out" (clear) removes it
        current[0] = KeyGenerator.getInstance("AES").generateKey();
        check("v2.0.3: other key: nothing returned, the sign-in kept, the reason given", call(again, "token") == null
                && "octo".equals(call(again, "login")) && String.valueOf(call(again, "problem")).contains("bound to another"),
                call(again, "problem"));
        current[0] = key;
        check("v2.0.3: ... the right key again: the same token (nothing was removed)", token.equals(call(again, "token"))
                && call(again, "problem") == null, null);
        device[0] = "android-id-2|cert-A";
        check("app data copied to another phone (other ANDROID_ID): unreadable there; v2.0.3: kept, not removed",
                call(again, "token") == null && "octo".equals(call(again, "login"))
                        && call(prefs, "getString", "token", null) != null, null);
        device[0] = "android-id-1|cert-B";
        check("another signing certificate (a re-signed copy): unreadable", call(again, "token") == null, null);
        device[0] = "android-id-1|cert-A";
        check("v2.0.3: back on this phone and app: readable again", token.equals(call(again, "token")), null);
        Object editor = call(prefs, "edit");
        call(editor, "putString", "user_id", "999");
        call(editor, "commit");
        check("session moved to another user id: unreadable (kept)", call(again, "token") == null
                && "octo".equals(call(again, "login")), null);
        call(store, "save", token, "octo", "583231");
        // v2.0.3: sealed in a run that couldn't read the certificate ("?"): opens with the full binding, sealed again
        device[0] = "android-id-1|?";
        call(store, "save", token, "octo", "583231");
        String partial = (String) call(prefs, "getString", "token", null);
        device[0] = "android-id-1|cert-A";
        boolean opened = token.equals(call(again, "token"));
        String resealed = (String) call(prefs, "getString", "token", null);
        device[0] = "android-id-9|cert-Z";
        boolean strict = call(again, "token") == null;
        device[0] = "android-id-1|cert-A";
        check("v2.0.3: a sign-in sealed while the certificate couldn't be read opens, is sealed again with the whole "
                + "binding, and still no other phone's binding opens it", opened && !partial.equals(resealed) && strict
                && token.equals(call(again, "token")), null);
        String v2 = (String) call(prefs, "getString", "token", null);
        Object editor1 = call(prefs, "edit");
        call(editor1, "putString", "token", "v1:" + v2.substring(3));
        call(editor1, "commit");
        check("a v1.9.8 token (v1, not bound): unreadable, the reason says so (sign in again)", call(again, "token") == null
                && String.valueOf(call(again, "problem")).contains("older version"), call(again, "problem"));
        call(store, "save", token, "octo", "583231");
        call(store, "clear");
        check("sign-out removes token, login, user id and key", call(store, "token") == null && call(store, "userId") == null
                && call(prefs, "getString", "user_id", null) == null && deleted[0], null);

        // --- AuthGuard: required only when the last app-control.json said so; a session = a token that decrypts
        Class<?> guard = c("com.sketchware.ai.github.AuthGuard");
        Class<?> control = c("com.sketchware.ai.github.AppControl");
        Object controlPrefs = call(CTX, "getSharedPreferences", "sketchware_ai_app_control", 0);
        Object e0 = call(controlPrefs, "edit");
        call(e0, "remove", "last");
        call(e0, "commit");
        call(guard, "reset");
        check("gate: never read app-control.json -> not required", Boolean.FALSE.equals(call(guard, "required", CTX)), null);
        Object e1 = call(controlPrefs, "edit");
        call(e1, "putString", "last", "{\"require_login\":true}");
        call(e1, "commit");
        set(control, "fetchedOnline", false);
        check("gate: require_login true, but not read from GitHub in this run (offline start) -> not required",
                Boolean.FALSE.equals(call(guard, "required", CTX)), null);
        set(control, "fetchedOnline", true);
        check("gate: require_login true, read online in this run -> required", Boolean.TRUE.equals(call(guard, "required", CTX)), null);
        Object appPrefs = call(CTX, "getSharedPreferences", "sketchware_ai_github", 0);
        check("gate: no stored sign-in -> no session", appPrefs != null && call(appPrefs, "getString", "token", null) == null
                && Boolean.FALSE.equals(call(guard, "verifyHardwareBoundSession", CTX)), null);
        Class<?> signIn = c("com.sketchware.ai.github.SignIn");
        boolean lifts = true;
        for (String code : new String[]{"device_flow_disabled", "no_client_id", "keystore", "incorrect_client_credentials"}) {
            lifts &= Boolean.TRUE.equals(call(signIn, "cannotWork", code));
        }
        boolean keeps = Boolean.FALSE.equals(call(signIn, "cannotWork", "network"))
                && Boolean.FALSE.equals(call(signIn, "cannotWork", "access_denied"))
                && Boolean.FALSE.equals(call(signIn, "cannotWork", "expired_token"));
        call(guard, "markUnavailable");
        check("gate: a sign-in that can't work here (Device Flow off, no Client ID, Keystore, no browser) lifts it for the run;"
                + " network, cancel, expiry don't", lifts && keeps && Boolean.FALSE.equals(call(guard, "required", CTX)), null);
        set(guard, "unavailable", false);
        check("account read after approval: 5xx, 429, 403 waited out; 401 and others not",
                Boolean.TRUE.equals(call(signIn, "isPassing", "http_502")) && Boolean.TRUE.equals(call(signIn, "isPassing", "http_429"))
                        && Boolean.TRUE.equals(call(signIn, "isPassing", "http_403")) && Boolean.FALSE.equals(call(signIn, "isPassing", "unauthorized"))
                        && Boolean.FALSE.equals(call(signIn, "isPassing", "http_404")) && Boolean.FALSE.equals(call(signIn, "isPassing", "bad_answer")), null);
        check("v2.0.1: GitHub's rate limit is waited out too; its message is one short line",
                Boolean.TRUE.equals(call(signIn, "isPassing", "rate_limited"))
                        && ((String) call(signIn, "shortMessage", "rate_limited")).startsWith("GitHub got too many requests"), null);
        // v2.0.1: closing the sign-in window pauses: the code stays saved for the next window; cancel removes it
        Object signer = call(signIn, "get", CTX);
        Object code = neu("com.sketchware.ai.github.DeviceFlow$Code");
        set(code, "deviceCode", "dc-pause");
        set(code, "userCode", "ABCD-1234");
        set(code, "interval", 5);
        set(code, "expiresAt", System.currentTimeMillis() + 600_000L);
        call(signer, "save", code);
        call(signer, "pause");
        Object kept = call(signer, "loadSaved");
        boolean keptOk = kept != null && "dc-pause".equals(get(kept, "deviceCode")) && call(signer, "pending") == null;
        call(signer, "cancel");
        check("v2.0.1: closing the window keeps the code (no new code next time); cancel removes it",
                keptOk && call(signer, "loadSaved") == null, kept);
        Object e2 = call(controlPrefs, "edit");
        call(e2, "putString", "last", "{\"require_login\":false}");
        call(e2, "commit");
        check("gate: require_login false -> not required", Boolean.FALSE.equals(call(guard, "required", CTX)), null);
        Object e3 = call(controlPrefs, "edit");
        call(e3, "remove", "last");
        call(e3, "commit");
        set(control, "fetchedOnline", false);
        check("sign-in errors are one short line", "Sign-in was cancelled.".equals(call(signIn, "shortMessage", "access_denied"))
                && "The code expired. Tap Sign in again.".equals(call(signIn, "shortMessage", "expired_token"))
                && "No internet connection.".equals(call(signIn, "shortMessage", "network"))
                && "Sign-in didn't work. Please try again.".equals(call(signIn, "shortMessage", "anything_else")), null);

        // --- v2.0.0: links for "Import from GitHub", the in-app sign-in window's allowed pages and code script
        Class<?> imp = c("com.sketchware.ai.github.GitHubImport");
        Object repo = call(imp, "parse", "https://github.com/pub/demo");
        Object git = call(imp, "parse", "github.com/pub/demo.git");
        Object blob = call(imp, "parse", "https://github.com/pub/demo/blob/main/backups/App%20One.swb");
        Object raw = call(imp, "parse", "https://raw.githubusercontent.com/o/r/refs/heads/dev/x/a.swb");
        Object tree = call(imp, "parse", "https://github.com/pub/demo/tree/v2/backups");
        check("import links: repository, .git, file (decoded), raw with refs/heads, folder", repo != null && "pub".equals(get(repo, "owner"))
                && "demo".equals(get(repo, "repo")) && "".equals(get(repo, "path")) && get(repo, "ref") == null
                && Boolean.FALSE.equals(get(repo, "file"))
                && git != null && "demo".equals(get(git, "repo"))
                && blob != null && Boolean.TRUE.equals(get(blob, "file")) && "backups/App One.swb".equals(get(blob, "path"))
                && "main".equals(get(blob, "ref"))
                && raw != null && "dev".equals(get(raw, "ref")) && "x/a.swb".equals(get(raw, "path"))
                && tree != null && "v2".equals(get(tree, "ref")) && "backups".equals(get(tree, "path"))
                && Boolean.FALSE.equals(get(tree, "file")), raw == null ? null : get(raw, "path"));
        boolean refused = true;
        for (String bad : new String[]{"https://github.com/pub/demo/blob/main/readme.md", "https://gitlab.com/pub/demo",
                "https://github.com/pub", "https://github.com/pub/demo/blob/main/../a.swb", "https://github.com/pub/demo/blob/main/%2e%2e/a.swb",
                "https://github.com/pub/demo/issues/1", "", "not a link at all", "https://evilgithub.com/pub/demo"}) {
            refused &= call(imp, "parse", bad) == null;
        }
        check("import links: other hosts, other pages, non-.swb files, .. refused", refused, null);
        Class<?> auth = c("com.sketchware.ai.github.GitHubInAppAuthDialog");
        Class<?> uriClass = c("android.net.Uri");
        boolean pages = Boolean.TRUE.equals(call(auth, "allowed", call(uriClass, "parse", "https://github.com/login/device")))
                && Boolean.TRUE.equals(call(auth, "allowed", call(uriClass, "parse", "https://gist.github.com/x")))
                && Boolean.FALSE.equals(call(auth, "allowed", call(uriClass, "parse", "http://github.com/login")))
                && Boolean.FALSE.equals(call(auth, "allowed", call(uriClass, "parse", "https://evilgithub.com/login")))
                && Boolean.FALSE.equals(call(auth, "allowed", call(uriClass, "parse", "https://accounts.google.com/o/oauth2")));
        check("sign-in window: https github.com and its subdomains only", pages, null);
        // v2.0.2: "Continue with Google / Apple" addresses start the browser sign-in instead of loading
        boolean social = Boolean.TRUE.equals(call(auth, "isSocialSignIn", call(uriClass, "parse", "https://github.com/sessions/social/google/initiate?return_to=x")))
                && Boolean.TRUE.equals(call(auth, "isSocialSignIn", call(uriClass, "parse", "https://accounts.google.com/o/oauth2/v2/auth?client_id=1")))
                && Boolean.TRUE.equals(call(auth, "isSocialSignIn", call(uriClass, "parse", "https://appleid.apple.com/auth/authorize")))
                && Boolean.FALSE.equals(call(auth, "isSocialSignIn", call(uriClass, "parse", "https://github.com/login/device")))
                && Boolean.FALSE.equals(call(auth, "isSocialSignIn", call(uriClass, "parse", "https://github.com/signup")))
                && Boolean.FALSE.equals(call(auth, "isSocialSignIn", call(uriClass, "parse", "https://www.google.com/search?q=github")));
        check("v2.0.2: GitHub's social sign-in and the Google / Apple sign-in hosts go to the browser; other pages don't", social, null);
        String social2 = (String) get(auth, "SOCIAL_SCRIPT");
        check("v2.0.2: the social-button script only navigates to the hand-off address (no bridge, no field read)",
                social2.contains("location.href='sketchware-ai://browser-sign-in'") && !social2.contains(".value")
                        && !social2.contains("password"), social2.length());
        String script = (String) call(auth, "fillScript", "AB12-CD34');alert(1);//");
        check("sign-in window: the code script carries only letters, digits and hyphens of the code",
                script.endsWith("('AB12-CD34alert1')") && script.contains("location.pathname.indexOf('/login/device')"), script.substring(script.length() - 24));

        // --- app-control.json (config/app-control.json of the repository): notices, never a lock
        String repoFile = new String(java.nio.file.Files.readAllBytes(new File(System.getProperty("h.appcontrol",
                "/home/user/SketchwareAI/config/app-control.json")).toPath()), "UTF-8");
        Object shipped = call(control, "parse", repoFile);
        check("repository's app-control.json reads: enabled, min 206, no login", shipped != null
                && Boolean.TRUE.equals(get(shipped, "appEnabled")) && Integer.valueOf(206).equals(get(shipped, "minVersionCode"))
                && Boolean.FALSE.equals(get(shipped, "requireLogin")), null);
        check("v2.0.5 (206) with it: no notice", call(control, "decide", shipped, 206, false, null) == null, null);
        Object old198 = call(control, "decide", shipped, 205, false, null);
        check("an older version (v2.0.4, 205): Update with the v2.0.5 URL", old198 != null && "update".equals(get(old198, "kind"))
                && String.valueOf(get(old198, "url")).equals("https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.0.5.apk"),
                old198 == null ? null : get(old198, "url"));
        Object msg = call(control, "parse", "{\"status_message\":\"Server maintenance tonight\"}");
        Object m1 = call(control, "decide", msg, 199, true, null);
        check("a status message once", m1 != null && "message".equals(get(m1, "kind"))
                && call(control, "decide", msg, 199, true, "Server maintenance tonight") == null, null);
        Object off = call(control, "parse", "{\"app_enabled\":false,\"status_message\":\"Paused\"}");
        Object o1 = call(control, "decide", off, 199, true, "Paused");
        check("app_enabled=false: its message at every start (a notice, nothing locks)", o1 != null && "message".equals(get(o1, "kind")), null);
        Object login = call(control, "parse", "{\"require_login\":true}");
        Object l1 = call(control, "decide", login, 199, false, null);
        check("require_login: a sign-in suggestion when signed out, none when signed in", l1 != null && "login".equals(get(l1, "kind"))
                && call(control, "decide", login, 199, true, null) == null, null);
        check("garbage, http or other-host links: ignored", call(control, "parse", "<html>") == null
                && get(call(control, "parse", "{\"update_url\":\"http://x\"}"), "updateUrl") == null
                && get(call(control, "parse", "{\"update_url\":\"https://evil.example/app.apk\"}"), "updateUrl") == null
                && get(call(control, "parse", "{\"message_url\":\"https://github.com/smartapps000-hub/../x\"}"), "messageUrl") == null, null);
        Object linked = call(control, "parse", "{\"status_message\":\"New guide\",\"message_url\":\"https://github.com/smartapps000-hub/SketchwareAI\","
                + "\"update_url\":\"https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/x.apk\"}");
        Object ml = call(control, "decide", linked, 199, true, null);
        check("a message opens message_url, never the update link", ml != null && "https://github.com/smartapps000-hub/SketchwareAI".equals(get(ml, "url")), get(ml, "url"));

        Class<?> cloudClass = c("com.sketchware.ai.github.CloudBackup");
        check("path keeps projects with the default package apart",
                "projects/com.my.newproject/My_App_2.swb".equals(call(cloudClass, "pathFor", "com.my.newproject", "My App 2"))
                        && "projects/no-package/project.swb".equals(call(cloudClass, "pathFor", "../x", "..")),
                call(cloudClass, "pathFor", "../x", ".."));

        // --- the project's .swb (the app's own backup), then restored as a new project with a free id
        Class<?> lC = c("a.a.a.lC"), yB = c("a.a.a.yB"), ids = c("com.sketchware.ai.project.ProjectIds");
        // third review: the project's GitHub record goes into the .swb with its data folder, but not into a restored copy
        File record = new File("/storage/emulated/0/.sketchware/data/" + scId + "/github_backup.json");
        writeText(record, "{\"url\":\"https://github.com/octo/SketchwareAI-Projects/blob/main/p.swb\",\"path\":\"p.swb\","
                + "\"sc_id\":\"" + scId + "\",\"login\":\"octo\",\"visibility\":\"public\",\"public_repo\":\"SketchwareAI-Public-X\"}");
        Object factory = neu("mod.hey.studios.project.backup.BackupFactory", scId);
        call(factory, "setBackupCustomBlocks", true);
        Map<String, Object> project = (Map<String, Object>) call(lC, "b", scId);
        String name = (String) call(yB, "c", project, "my_ws_name");
        call(factory, "backup", name);
        File swb = (File) call(factory, "getOutFile");
        check("project " + scId + " backed up to a .swb", swb != null && swb.isFile() && swb.length() > 1000, swb);

        String next = (String) call(lC, "b");
        File sketchware = new File("/storage/emulated/0/.sketchware");
        File leftover = new File(sketchware, "data/" + next);
        leftover.mkdirs();
        new java.io.FileOutputStream(new File(leftover, "stale")).close();
        String freeId = (String) call(ids, "free", next);
        check("id with leftover folders skipped (" + next + " -> " + freeId + ")", !freeId.equals(next)
                && Integer.parseInt(freeId) > Integer.parseInt(next), freeId);
        Object restorer = neu("mod.hey.studios.project.backup.BackupFactory", freeId);
        call(restorer, "restore", swb);
        Map<String, Object> restored = (Map<String, Object>) call(lC, "b", freeId);
        check("restored as new project #" + freeId, Boolean.TRUE.equals(call(restorer, "isRestoreSuccess")) && restored != null
                && name.equals(call(yB, "c", restored, "my_ws_name")), call(restorer, "getError"));
        check("leftover folder untouched, restore went elsewhere", new File(leftover, "stale").isFile()
                && !new File(sketchware, "data/" + freeId + "/stale").exists(), null);
        check("third review: a restored copy has no GitHub record (share link, public copy) of the project it came from",
                !new File(sketchware, "data/" + freeId + "/github_backup.json").exists()
                        && new File(sketchware, "data/" + freeId + "/logic").exists(), null);
        record.delete();
        String fromList = (String) call(c("mod.hey.studios.project.backup.BackupFactory"), "getNewScId");
        check("getNewScId: numeric maximum + 1, free", Integer.parseInt(fromList) > Integer.parseInt(freeId)
                && !(Boolean) call(ids, "inUse", sketchware, Integer.parseInt(fromList)), fromList);

        // --- v2.0.0: a background (cloud) backup reads the saved project privately: jC (the editor's copy) untouched
        Class<?> jC = c("a.a.a.jC");
        Object beforeA = get(jC, "a"), beforeB = get(jC, "b");
        Object cloudFactory = neu("mod.hey.studios.project.backup.BackupFactory", scId);
        call(cloudFactory, "setBackupCustomBlocks", true);
        call(cloudFactory, "setReadSavedDataOnly", true);
        call(cloudFactory, "backup", name);
        File cloudSwb = (File) call(cloudFactory, "getOutFile");
        check("cloud backup with custom blocks: a .swb, and jC's shared project not loaded or swapped",
                cloudSwb != null && cloudSwb.isFile() && cloudSwb.length() > 1000 && get(jC, "a") == beforeA
                        && get(jC, "b") == beforeB && !new File(BackupFactoryDir(), name + "_cloud_temp").exists(), cloudSwb);
        if (cloudSwb != null) cloudSwb.delete();

        // --- v2.0.1: the Back up sheet's boxes: "Back up to GitHub" uploads the same .swb as "Save on phone"
        Object automatic = get(c("com.sketchware.ai.github.CloudSync$Options"), "AUTOMATIC");
        check("automatic backups (new and waiting projects): custom blocks, no local libraries",
                Boolean.TRUE.equals(get(automatic, "customBlocks")) && Boolean.FALSE.equals(get(automatic, "localLibraries")), null);
        File libsRoot = new File(sketchware, "libs/local_libs");
        File testLib = new File(libsRoot, "ghtest_lib_v201");
        deleteTree(testLib);
        testLib.mkdirs();
        writeText(new File(testLib, "classes.dex"), "dex-original");
        writeText(new File(testLib, "config"), "com.example.ghtest");
        File usedLibs = new File(sketchware, "data/" + scId + "/local_library");
        String usedBefore = usedLibs.isFile() ? new String(java.nio.file.Files.readAllBytes(usedLibs.toPath()), "UTF-8") : null;
        writeText(usedLibs, "[{\"name\":\"ghtest_lib_v201\",\"dexPath\":\"" + new File(testLib, "classes.dex").getAbsolutePath()
                + "\",\"use_yn\":\"Y\"}]");
        Object boxes = neu("mod.hey.studios.project.backup.BackupFactory", scId);
        call(boxes, "setBackupLocalLibs", true);
        call(boxes, "setBackupCustomBlocks", true);
        call(boxes, "setReadSavedDataOnly", true);
        call(boxes, "backup", name);
        File boxesSwb = (File) call(boxes, "getOutFile");
        java.util.List<String> entries = new java.util.ArrayList<>();
        if (boxesSwb != null) {
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(boxesSwb)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> e = z.entries();
                while (e.hasMoreElements()) entries.add(e.nextElement().getName());
            }
        }
        boolean hasLib = false, hasBlocks = false;
        for (String entry : entries) {
            if (entry.endsWith("local_libs/ghtest_lib_v201/classes.dex")) hasLib = true;
            if (entry.endsWith("data/custom_blocks")) hasBlocks = true;
        }
        check("both boxes ticked: the .swb (as uploaded) carries the used local library and the custom blocks",
                hasLib && hasBlocks, entries.size() + " entries");
        // restored elsewhere: a missing library is copied, one already on the phone is never replaced
        deleteTree(testLib);
        String libId = (String) call(ids, "free", (String) call(lC, "b"));
        Object libRestore = neu("mod.hey.studios.project.backup.BackupFactory", libId);
        call(libRestore, "setBackupLocalLibs", true);
        call(libRestore, "restore", boxesSwb);
        boolean copied = new File(testLib, "classes.dex").isFile();
        writeText(new File(testLib, "classes.dex"), "dex-kept-on-phone");
        String libId2 = (String) call(ids, "free", String.valueOf(Integer.parseInt(libId) + 1));
        Object libRestore2 = neu("mod.hey.studios.project.backup.BackupFactory", libId2);
        call(libRestore2, "setBackupLocalLibs", true);
        call(libRestore2, "restore", boxesSwb);
        String keptText = new String(java.nio.file.Files.readAllBytes(new File(testLib, "classes.dex").toPath()), "UTF-8");
        check("restore with libraries: a missing one is copied, one already here is kept as it is",
                copied && "dex-kept-on-phone".equals(keptText) && Boolean.TRUE.equals(call(libRestore2, "isRestoreSuccess")), keptText);
        check("the restored project brings its custom blocks file",
                new File(sketchware, "data/" + libId + "/custom_blocks").isFile(), null);
        // v2.0.1 (review): libraries copied from someone else's backup are marked, and the build's automatic repair
        // never switches a marked library on in another project
        deleteTree(testLib);
        String linkId = (String) call(ids, "free", String.valueOf(Integer.parseInt(libId2) + 1));
        Object linkRestore = neu("mod.hey.studios.project.backup.BackupFactory", linkId);
        call(linkRestore, "setBackupLocalLibs", true);
        call(linkRestore, "setLibrariesFrom", "someone/repo");
        call(linkRestore, "restore", boxesSwb);
        File mark = new File(testLib, "imported_from_link.txt");
        check("a library copied from a link's backup is marked with where it came from", mark.isFile()
                && new String(java.nio.file.Files.readAllBytes(mark.toPath()), "UTF-8").contains("someone/repo"), null);
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(new File(testLib, "classes.jar")))) {
            z.putNextEntry(new java.util.zip.ZipEntry("com/example/ghtest/Printer.class"));
            z.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
            z.closeEntry();
        }
        String ecjLog = "----------\n1. ERROR in /storage/emulated/0/.sketchware/mysc/" + linkId
                + "/app/src/main/java/com/my/app/MainActivity.java (at line 5)\n\timport com.example.ghtest.Printer;\n"
                + "\t       ^^^^^^^^^^^\nThe import com.example.ghtest cannot be resolved\n----------\n1 problem (1 error)\n";
        Class<?> autoResolve = c("com.sketchware.ai.project.LibraryAutoResolve");
        java.lang.reflect.Method resolve = autoResolve.getMethod("resolve", String.class, java.util.List.class, File[].class);
        Object marked = resolve.invoke(null, ecjLog, new java.util.ArrayList<java.util.HashMap<String, Object>>(), new File[]{libsRoot});
        mark.delete();
        Object unmarked = resolve.invoke(null, ecjLog, new java.util.ArrayList<java.util.HashMap<String, Object>>(), new File[]{libsRoot});
        check("the automatic repair skips a marked library and would take it unmarked",
                ((java.util.List<?>) get(marked, "enabled")).isEmpty()
                        && ((java.util.List<?>) get(unmarked, "enabled")).contains("ghtest_lib_v201"),
                get(marked, "enabled") + " / " + get(unmarked, "enabled"));
        Class<?> installer = c("com.sketchware.ai.library.LibraryInstaller");
        boolean usableUnmarked = Boolean.TRUE.equals(call(installer, "usable", testLib));
        writeText(mark, "Copied from someone/repo by Import from GitHub.\n");
        boolean usableMarked = Boolean.TRUE.equals(call(installer, "usable", testLib));
        check("the Library Resolver never reuses a marked folder as the Maven library of that name",
                usableUnmarked && !usableMarked, usableUnmarked + " / " + usableMarked);
        for (String folder : new String[]{"data", "bak", "mysc", "mysc/list", "resources/images", "resources/icons",
                "resources/fonts", "resources/sounds"}) {
            deleteTree(new File(sketchware, folder + "/" + linkId));
        }
        for (String id : new String[]{libId, libId2}) {
            for (String folder : new String[]{"data", "bak", "mysc", "mysc/list", "resources/images", "resources/icons",
                    "resources/fonts", "resources/sounds"}) {
                deleteTree(new File(sketchware, folder + "/" + id));
            }
        }
        deleteTree(testLib);
        if (usedBefore == null) usedLibs.delete();
        else writeText(usedLibs, usedBefore);
        if (boxesSwb != null) boxesSwb.delete();

        // --- v2.0.2: the backup link kept with the project (share button on its card)
        Class<?> links = c("com.sketchware.ai.github.ProjectLinks");
        File linkFile = new File(sketchware, "data/" + scId + "/github_backup.json");
        linkFile.delete();
        boolean none = call(links, "get", scId) == null;
        String url = "https://github.com/octo/SketchwareAI-Projects/blob/main/projects/com.my.app/My_App.swb";
        call(links, "save", scId, url, "projects/com.my.app/My_App.swb", "Octo");
        Object saved = call(links, "get", scId);
        Object sameAccount = call(links, "get", scId, "octo");
        Object otherAccount = call(links, "get", scId, "someone");
        writeText(linkFile, "{\"url\":\"https://evil.example/x\",\"path\":\"p\",\"sc_id\":\"" + scId + "\"}");
        Object foreign = call(links, "get", scId);
        // a link written for another project (a restored or duplicated copy carries the file): not shown
        writeText(linkFile, "{\"url\":\"" + url + "\",\"path\":\"p\",\"sc_id\":\"601\",\"login\":\"octo\"}");
        Object otherProject = call(links, "get", scId);
        call(links, "save", "999999", "https://github.com/x/y", "p", "octo");
        boolean noFolder = !new File(sketchware, "data/999999/github_backup.json").exists();
        check("v2.0.2: share link: none before a backup, kept after (same account too), not under another account, only "
                        + "github.com links, not for a copy made for another project id, never for a missing project",
                none && url.equals(saved) && url.equals(sameAccount) && otherAccount == null && foreign == null
                        && otherProject == null && noFolder && call(links, "get", "../1") == null, saved);
        linkFile.delete();

        // --- v2.0.4: the project's record: its own repository (number, visibility), Standard tag, boxes, fingerprint
        Object autoOptions = get(c("com.sketchware.ai.github.CloudSync$Options"), "AUTOMATIC");
        Object libsOnly = neu("com.sketchware.ai.github.CloudSync$Options", true, false);
        String repoPage = "https://github.com/octo/SketchwareAI-My_App";
        call(links, "saved", scId, "octo", "v1.0-1", libsOnly, "fp-0", 0L);
        boolean noRepoNoRecord = !linkFile.exists();
        call(links, "repoFound", scId, "octo", "SketchwareAI-My_App", 4242L, true);
        call(links, "saved", scId, "octo", "v1.0-1", libsOnly, "fp-1", 1234L);
        Object lastOpts = call(links, "lastOptions", scId);
        check("v2.0.4: a backup's record starts when its repository is known: the repository page is the share link, "
                        + "private, its number, the boxes, fingerprint and APK time",
                noRepoNoRecord && "private".equals(call(links, "visibility", scId, "octo")) && repoPage.equals(call(links, "get", scId, "octo"))
                        && Long.valueOf(4242L).equals(call(links, "repoNumber", scId, "octo"))
                        && "SketchwareAI-My_App".equals(call(links, "repoName", scId, "octo"))
                        && Boolean.TRUE.equals(get(lastOpts, "localLibraries")) && Boolean.FALSE.equals(get(lastOpts, "customBlocks"))
                        && "fp-1".equals(call(links, "lastFingerprint", scId, "octo")) && Long.valueOf(1234L).equals(call(links, "lastApkTime", scId, "octo"))
                        && (Long) call(links, "lastBackup", scId) > 0, readText(linkFile));
        call(links, "visibilityNow", scId, "octo", false);
        call(links, "standardNow", scId, "octo", "v1.0-1");
        check("v2.0.4: Public (one PATCH) and the Standard pin as the card shows them; nothing under another account",
                "public".equals(call(links, "visibility", scId, "octo")) && Boolean.TRUE.equals(call(links, "isPublic", scId, "octo"))
                        && "v1.0-1".equals(call(links, "standardTag", scId, "octo")) && call(links, "visibility", scId, "someone") == null
                        && call(links, "repoNumber", scId, "someone").equals(0L) && call(links, "lastFingerprint", scId, "someone") == null, null);
        call(links, "saved", scId, "octo", "v1.1-2", autoOptions, "fp-2", 0L);
        call(links, "standardNow", scId, "octo", null);
        check("v2.0.4: a later backup keeps the repository and its visibility", "public".equals(call(links, "visibility", scId, "octo"))
                && repoPage.equals(call(links, "get", scId, "octo")) && "fp-2".equals(call(links, "lastFingerprint", scId, "octo"))
                && call(links, "standardTag", scId, "octo") == null, null);
        // a record without its account (carried in by a .swb) never names a repository; the backup repository never is one
        writeText(linkFile, "{\"url\":\"https://github.com/octo/SketchwareAI-Victim\",\"sc_id\":\"" + scId + "\",\"visibility\":\"public\","
                + "\"repo\":\"SketchwareAI-Victim\",\"repo_number\":77}");
        boolean noAccount = Long.valueOf(0L).equals(call(links, "repoNumber", scId, "octo")) && call(links, "repoName", scId, "octo") == null
                && !Boolean.TRUE.equals(call(links, "isPublic", scId, "octo")) && "private".equals(call(links, "visibility", scId, "octo"));
        call(links, "saved", scId, "octo", "v9-9", autoOptions, "fp-x", 0L);
        boolean notAdopted = readText(linkFile).contains("SketchwareAI-Victim") && !readText(linkFile).contains("fp-x");
        writeText(linkFile, "{\"url\":\"" + repoPage + "\",\"sc_id\":\"" + scId + "\",\"login\":\"octo\",\"repo\":\"sketchwareai-projects\","
                + "\"repo_number\":5}");
        check("v2.0.4 (v2.0.3 review L6/H1): a record without its account steers nothing; the backup repository is never "
                + "a project's repository", noAccount && notAdopted && call(links, "repoName", scId, "octo") == null, null);
        // a v2.0.3 public copy is adopted as the project's repository
        writeText(linkFile, "{\"url\":\"https://github.com/octo/SketchwareAI-Projects/blob/main/p.swb\",\"sc_id\":\"" + scId
                + "\",\"login\":\"octo\",\"visibility\":\"public\",\"public_repo\":\"SketchwareAI-Public-My_App\",\"public_number\":4343}");
        check("v2.0.4: a v2.0.3 project's public copy becomes its repository (versions are added there)",
                Long.valueOf(4343L).equals(call(links, "repoNumber", scId, "octo"))
                        && "SketchwareAI-Public-My_App".equals(call(links, "repoName", scId, "octo")), null);
        // restore: linked to the repository it came from, never while another project on this phone is
        linkFile.delete();
        String otherId = "999998";
        File otherData = new File(sketchware, "data/" + otherId);
        otherData.mkdirs();
        writeText(new File(otherData, "github_backup.json"), "{\"sc_id\":\"" + otherId + "\",\"login\":\"octo\","
                + "\"repo\":\"SketchwareAI-My_App\",\"repo_number\":4242,\"url\":\"" + repoPage + "\"}");
        Object restoreRefused = call(links, "linkRestored", scId, "octo", "SketchwareAI-My_App", 4242L, false, "v1.0-1");
        deleteTree(otherData);
        Object linkedNow = call(links, "linkRestored", scId, "octo", "SketchwareAI-My_App", 4242L, false, "v1.0-1");
        check("v2.0.4: a restored project is linked to its repository (public, Standard v1.0-1); refused while another "
                        + "project here is linked to it", Boolean.FALSE.equals(restoreRefused) && Boolean.TRUE.equals(linkedNow)
                        && "public".equals(call(links, "visibility", scId, "octo")) && "v1.0-1".equals(call(links, "standardTag", scId, "octo"))
                        && Long.valueOf(4242L).equals(call(links, "repoNumber", scId, "octo")), restoreRefused + " " + linkedNow);
        linkFile.delete();
        call(links, "repoFound", scId, "octo", "SketchwareAI-My_App", 4242L, true);
        call(links, "saved", scId, "octo", null, libsOnly, "fp-1", 0L);

        // --- v2.0.4: the change analyzer on this project: snapshot, comparison, notes, CHANGELOG, README, topics
        Class<?> an = c("com.sketchware.ai.project.ProjectDiffAnalyzer");
        Class<?> jsonParser = c("com.google.gson.JsonParser");
        Object snap = call(an, "snapshot", scId);
        Object snapScreens = call(snap, "getAsJsonObject", "screens");
        java.util.Set<?> screenNames = (java.util.Set<?>) call(snapScreens, "keySet");
        String firstScreen = screenNames.isEmpty() ? null : (String) screenNames.iterator().next();
        Object unchanged = call(an, "compare", call(an, "snapshot", scId), snap);
        check("v2.0.4: the analyzer reads the saved project (name, screens, widgets, events) and finds no change in it twice",
                firstScreen != null && !String.valueOf(call(snap, "toString")).contains("readError")
                        && (Integer) call(call(call(snapScreens, "getAsJsonObject", firstScreen), "getAsJsonObject", "views"), "size") > 0
                        && Boolean.TRUE.equals(call(unchanged, "isEmpty")), snap);
        Object later = call(call(jsonParser, "parseString", String.valueOf(snap)), "getAsJsonObject");
        call(later, "addProperty", "versionName", "9.1");
        call(later, "addProperty", "versionCode", "91");
        Object laterMain = call(call(later, "getAsJsonObject", "screens"), "getAsJsonObject", firstScreen);
        call(call(laterMain, "getAsJsonObject", "views"), "addProperty", "button99", "Button");
        call(call(laterMain, "getAsJsonObject", "events"), "add", "button99_onClick",
                call(call(jsonParser, "parseString", "[12345, 678]"), "getAsJsonArray"));
        call(call(later, "getAsJsonObject", "screens"), "add", "SecondActivity", call(call(jsonParser, "parseString",
                "{\"xml\":\"second.xml\",\"views\":{\"text1\":\"TextView\"},\"components\":{},\"events\":{},\"blocks\":0}"), "getAsJsonObject"));
        call(call(later, "getAsJsonArray", "permissions"), "add", "CAMERA");
        Object changes = call(an, "compare", snap, later);
        String notes = (String) call(an, "releaseNotes", later, changes);
        String commitLine = (String) call(an, "commitMessage", later, changes);
        String entry = (String) call(an, "changelogEntry", later, changes, 1763208000000L);
        check("v2.0.4: release notes from the difference: ✨ New Features (screen, widget, logic, permission), ⬆️ Upgrades "
                        + "(version); a commit line and a CHANGELOG entry", notes.startsWith("## What's New & Fixed in v9.1 (91)")
                        && notes.contains("### ✨ New Features") && notes.contains("New screen **SecondActivity** (1 widgets, 0 blocks)")
                        && notes.contains("Added to " + firstScreen + ": button99") && notes.contains("New logic in " + firstScreen)
                        && notes.contains("Permission: CAMERA") && notes.contains("### ⬆️ Upgrades") && notes.contains("→ 9.1 (91)")
                        && commitLine.startsWith("v9.1 (91): +1 screen, +1 widget, 2 blocks changed")
                        && entry.startsWith("## v9.1 (91) (2025-11-15)") && !entry.contains("What's New"), notes + "\n" + commitLine);
        Object changedLogic = call(call(jsonParser, "parseString", String.valueOf(later)), "getAsJsonObject");
        call(call(call(call(changedLogic, "getAsJsonObject", "screens"), "getAsJsonObject", firstScreen), "getAsJsonObject", "events"),
                "add", "button99_onClick", call(call(jsonParser, "parseString", "[12345, 999]"), "getAsJsonArray"));
        String fixNotes = (String) call(an, "releaseNotes", changedLogic, call(an, "compare", later, changedLogic));
        check("v2.0.4: a changed block in an event = 🛠 Logic & Bug Fixes with the count of blocks modified",
                fixNotes.contains("### 🛠 Logic & Bug Fixes") && fixNotes.contains("(1 block modified)")
                        && !fixNotes.contains("✨"), fixNotes);
        String firstNotes = (String) call(an, "releaseNotes", snap, call(an, "compare", null, snap));
        String readme = (String) call(an, "readme", later, null, null, "https://github.com/octo/SketchwareAI-My_App");
        String description = (String) call(an, "description", later);
        List<?> topics = (List<?>) call(an, "topics", later);
        check("v2.0.4: the first version gets a summary; README, description (≤ 350) and topics come from the snapshot",
                firstNotes.contains(": first release") && readme.startsWith("# ") && readme.contains("SecondActivity")
                        && readme.contains("https://github.com/octo/SketchwareAI-My_App") && description.contains("made with Sketchware AI")
                        && description.length() <= 350 && topics.contains("sketchware-ai") && topics.size() <= 20, readme);
        File snapDir = new File(sketchware, "data/" + scId + "/snapshots");
        deleteTree(snapDir);
        File saved1 = (File) call(an, "save", scId, snap);
        Object prevForLater = call(an, "previous", scId, later);
        Object prevForSame = call(an, "previous", scId, snap);
        check("v2.0.4: a version's snapshot is kept offline (snapshots/v<name>_<code>_snapshot.json) and is the previous "
                        + "one for the next version only", saved1 != null && saved1.getName().matches("v.+_.+_snapshot\\.json")
                        && prevForLater != null && String.valueOf(prevForLater).equals(String.valueOf(snap)) && prevForSame == null,
                saved1);
        deleteTree(snapDir);

        // --- v2.0.5: snapshot format 2: MoreBlocks, variables, lists, screen parts, custom views, own files
        Object mainSnap = call(snapScreens, "getAsJsonObject", firstScreen);
        check("v2.0.5: the snapshot is format 2: each screen with its MoreBlocks, variables, lists and parts; the project "
                        + "with its custom views and own files", Integer.valueOf(2).equals(call(call(snap, "get", "format"), "getAsInt"))
                        && Boolean.TRUE.equals(call(mainSnap, "has", "moreBlocks")) && Boolean.TRUE.equals(call(mainSnap, "has", "variables"))
                        && Boolean.TRUE.equals(call(mainSnap, "has", "lists")) && Boolean.TRUE.equals(call(mainSnap, "has", "parts"))
                        && Boolean.TRUE.equals(call(snap, "has", "customViewNames")) && Boolean.TRUE.equals(call(snap, "has", "files")),
                mainSnap);
        Object rich = call(call(jsonParser, "parseString", String.valueOf(later)), "getAsJsonObject");
        call(rich, "addProperty", "versionName", "9.2");
        call(rich, "addProperty", "versionCode", "92");
        Object richMain = call(call(rich, "getAsJsonObject", "screens"), "getAsJsonObject", firstScreen);
        call(call(richMain, "getAsJsonArray", "moreBlocks"), "add", "loadData");
        call(call(richMain, "getAsJsonObject", "variables"), "addProperty", "score", "Number");
        call(call(richMain, "getAsJsonArray", "parts"), "add", "landscape");
        call(call(rich, "getAsJsonArray", "customViewNames"), "add", "item_row");
        call(call(rich, "getAsJsonObject", "files"), "addProperty", "java", 2);
        Object richChanges = call(an, "compare", later, rich);
        String richNotes = (String) call(an, "releaseNotes", rich, richChanges);
        String richCommit = (String) call(an, "commitMessage", rich, richChanges);
        check("v2.0.5: release notes name new MoreBlocks, variables, screen parts, custom views and Java files",
                richNotes.contains("New MoreBlock in " + firstScreen + ": loadData")
                        && richNotes.contains("New variables in " + firstScreen + ": score (Number)")
                        && richNotes.contains("Screen parts added in " + firstScreen + ": landscape")
                        && richNotes.contains("New custom view: item_row") && richNotes.contains("2 new Java files")
                        && richCommit.contains("+1 MoreBlock"), richNotes + "\n" + richCommit);
        Object asOld = call(call(jsonParser, "parseString", String.valueOf(later)), "getAsJsonObject");
        call(asOld, "addProperty", "format", 1);
        String fromOld = (String) call(an, "releaseNotes", rich, call(an, "compare", asOld, rich));
        check("v2.0.5: a v2.0.4 (format 1) snapshot is still compared, without the new parts (nothing reported as new "
                        + "only because the older snapshot didn't record it)", fromOld.contains("→ 9.2 (92)")
                        && !fromOld.contains("MoreBlock") && !fromOld.contains("custom view") && !fromOld.contains("Java file"),
                fromOld);
        String richReadme = (String) call(an, "readme", rich, null, null, null);
        String richDescription = (String) call(an, "description", rich);
        check("v2.0.5: README with a screen table and the MoreBlocks; the description names the screens",
                richReadme.contains("## Screens") && richReadme.contains("| Screen | Widgets | Logic blocks | Components | Parts |")
                        && richReadme.contains("| " + firstScreen + " | ") && richReadme.contains("### MoreBlocks")
                        && richReadme.contains("- loadData (" + firstScreen + ")") && richDescription.contains("with 2 screens (")
                        && richDescription.length() <= 350, richDescription + "\n" + richReadme);

        // --- v2.0.5: project names up to 64 characters (the owner's 20-letter report), the Demo row, the carousel
        Class<?> names = c("com.sketchware.ai.project.ProjectNameValidator");
        String sixtyFour = "Sri_D_Varahi_Parking_Stand_abcdefghijabcdefghijabcdefghijabcdefg";
        check("v2.0.5: a project name may have 64 characters (letters, digits, _; a letter first); 65, a digit first, a "
                        + "space or a hyphen are refused", sixtyFour.length() == 64
                        && Boolean.TRUE.equals(call(names, "valid", "Sri_D_Varahi_Parking_Stand"))
                        && Boolean.TRUE.equals(call(names, "valid", sixtyFour))
                        && Boolean.FALSE.equals(call(names, "valid", sixtyFour + "x"))
                        && Boolean.FALSE.equals(call(names, "valid", "1Parking")) && Boolean.FALSE.equals(call(names, "valid", "My App"))
                        && Boolean.FALSE.equals(call(names, "valid", "My-App")), null);
        Class<?> demos = c("com.sketchware.ai.project.Demos");
        java.util.HashMap<String, Object> plain = new java.util.HashMap<>();
        plain.put("sc_id", scId);
        plain.put("my_sc_pkg_name", "com.my.app");
        boolean plainIsDemo = Boolean.TRUE.equals(call(demos, "isDemo", plain));
        java.util.HashMap<String, Object> oldDemo = new java.util.HashMap<>();
        oldDemo.put("sc_id", "9050");
        oldDemo.put("my_sc_pkg_name", "com.my.githubdemo");
        oldDemo.put("my_ws_name", "GitHub_API_Demo");
        // review: a project of the sample's package with another name, and a copy carrying another project's mark, are
        // the user's own projects
        java.util.HashMap<String, Object> ownApp = new java.util.HashMap<>();
        ownApp.put("sc_id", "9052");
        ownApp.put("my_sc_pkg_name", "com.my.githubdemo");
        ownApp.put("my_ws_name", "My_Own_App");
        File copiedMark = new File(sketchware, "data/9053/demo.json");
        copiedMark.getParentFile().mkdirs();
        writeText(copiedMark, "{\"demo\":\"github\",\"version\":3,\"sc_id\":\"9051\"}");
        java.util.HashMap<String, Object> copiedDemo = new java.util.HashMap<>();
        copiedDemo.put("sc_id", "9053");
        copiedDemo.put("my_sc_pkg_name", "com.my.githubdemo");
        copiedDemo.put("my_ws_name", "GitHub_API_Demo");
        boolean ownStays = Boolean.FALSE.equals(call(demos, "isDemo", ownApp)) && Boolean.FALSE.equals(call(demos, "isDemo", copiedDemo));
        deleteTree(new File(sketchware, "data/9053"));
        // second review (MEDIUM): a malformed mark from someone else's backup never throws (the projects screen)
        for (String bad : new String[]{"{\"sc_id\":null}", "{\"sc_id\":{}}", "{\"sc_id\":[]}", "{\"version\":{}}", "[1,2]", "not json"}) {
            writeText(copiedMark, bad);
            try {
                ownStays &= Boolean.FALSE.equals(call(demos, "isDemo", copiedDemo));
                ownStays &= Integer.valueOf(1).equals(call(demos, "version", "9053"));
            } catch (Throwable t) {
                ownStays = false;
                OUT.println("demo mark " + bad + ": " + t);
            }
        }
        deleteTree(new File(sketchware, "data/9053"));
        call(demos, "mark", "9051", "github", 3);
        java.util.HashMap<String, Object> newDemo = new java.util.HashMap<>();
        newDemo.put("sc_id", "9051");
        newDemo.put("my_sc_pkg_name", "com.my.githubdemo");
        List<java.util.HashMap<String, Object>> demoList = new ArrayList<>(java.util.Arrays.asList(oldDemo, newDemo));
        call(demos, "sort", demoList);
        check("v2.0.5: demo projects (the GitHub API Demo's package or a demo mark) go to the Demo row, the newest demo "
                        + "version first; an ordinary project doesn't", !plainIsDemo && Boolean.TRUE.equals(call(demos, "isDemo", oldDemo))
                        && Integer.valueOf(3).equals(call(demos, "version", "9051")) && Integer.valueOf(1).equals(call(demos, "version", "9050"))
                        && "9051".equals(demoList.get(0).get("sc_id")) && ownStays
                        && Boolean.TRUE.equals(call(demos, "isDemo", newDemo)), demoList + " " + ownStays);
        deleteTree(new File(sketchware, "data/9051"));
        Class<?> backupsScreen = c("com.sketchware.ai.github.GitHubBackupsActivity");
        List<Object> carousel = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            Object ver = neu("com.sketchware.ai.github.ProjectRepo$Version");
            set(ver, "tag", "v" + i + ".0-" + i);
            set(ver, "time", 1000L * i);
            set(ver, "standard", i == 2);
            carousel.add(ver);
        }
        List<?> front = (List<?>) call(backupsScreen, "ordered", carousel);
        long newest = (Long) call(backupsScreen, "newestTime", carousel);
        List<?> noPin = (List<?>) call(backupsScreen, "ordered", carousel.subList(2, 7));
        check("v2.0.5: the carousel: the pinned version in the front slot (\"★ Pinned\"), then the newest (\"New\") and the "
                        + "rest newest first, five at most; without a pin the newest is in front",
                front.size() == 5 && "v2.0-2".equals(get(front.get(0), "tag")) && "v7.0-7".equals(get(front.get(1), "tag"))
                        && "v4.0-4".equals(get(front.get(4), "tag")) && "v7.0-7".equals(get(noPin.get(0), "tag"))
                        && "★ Pinned".equals(call(backupsScreen, "badgeOf", front.get(0), false))
                        && "New".equals(call(backupsScreen, "badgeOf", front.get(1), (Long) get(front.get(1), "time") == newest))
                        && "".equals(call(backupsScreen, "badgeOf", front.get(2), false)), front.size());
        // review (MEDIUM): a cancel is kept for the thread even when the HTTP stack clears its interrupt flag
        Class<?> http = c("com.sketchware.ai.github.GitHubHttp");
        boolean[] seen = new boolean[3];
        java.util.concurrent.CountDownLatch cancelLatch = new java.util.concurrent.CountDownLatch(1);
        Thread transfer = new Thread(() -> {
            try {
                cancelLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // the flag is cleared here, as okio's timeout check does
            }
            try {
                while (!Thread.interrupted() && cancelLatch.getCount() > 0) Thread.sleep(5);
                seen[0] = Boolean.TRUE.equals(call(http, "isCancelled"));
                seen[1] = !Boolean.TRUE.equals(call(http, "worthRetry", new java.io.IOException("dropped")));
                call(http, "endJob");
                seen[2] = !Boolean.TRUE.equals(call(http, "isCancelled"));
            } catch (Throwable t) {
                OUT.println("cancel thread: " + t);
            }
        });
        transfer.start();
        Thread.sleep(100);
        call(http, "cancel", transfer);
        cancelLatch.countDown();
        transfer.join(10_000);
        check("v2.0.5 review: a cancelled transfer stays cancelled after its interrupt flag is cleared (no retry), "
                + "until its job ends", seen[0] && seen[1] && seen[2], seen[0] + " " + seen[1] + " " + seen[2]);
        // review (MEDIUM): the resume key changes with the project's images (not only data/<id>)
        Object automaticOptions = get(c("com.sketchware.ai.github.CloudSync$Options"), "AUTOMATIC");
        String keyBefore = (String) call(c("com.sketchware.ai.github.CloudSync"), "resumeInputs", scId, automaticOptions);
        File image = new File(sketchware, "resources/images/" + scId + "/v205_review.png");
        image.getParentFile().mkdirs();
        writeText(image, "not really a png");
        String keyAfter = (String) call(c("com.sketchware.ai.github.CloudSync"), "resumeInputs", scId, automaticOptions);
        image.delete();
        check("v2.0.5 review: a kept backup isn't resumed after an image (icon, sound, font, the project's list entry, "
                + "libraries) changed", !keyBefore.equals(keyAfter), keyBefore + " / " + keyAfter);
        Class<?> doctor = c("com.sketchware.ai.github.SyncDoctor");
        String advised401 = (String) call(doctor, "withAdvice", "http_401", "GitHub refused the upload (HTTP 401).");
        String advised5xx = (String) call(doctor, "withAdvice", "http_502", "Uploading the backup failed (HTTP 502).");
        String alreadySaid = (String) call(doctor, "withAdvice", "http_401", "GitHub didn't accept it: tap Sign in again.");
        check("v2.0.5: sync failures say what to do (Sign in again after a 401, Retry after a server error, nothing twice)",
                advised401.contains("What to do: tap Sign in again") && advised5xx.contains("What to do: Retry in a minute")
                        && alreadySaid.equals("GitHub didn't accept it: tap Sign in again.")
                        && call(doctor, "withAdvice", "no_such_code", "x").equals("x"), advised401 + " | " + advised5xx);

        // --- v2.0.4: deep links into the restore screen
        Class<?> linkActivity = c("com.sketchware.ai.github.GitHubLinkActivity");
        String direct = (String) call(linkActivity, "linkOf", call(uriClass, "parse", "https://github.com/octo/SketchwareAI-My_App"));
        String viaScheme = (String) call(linkActivity, "linkOf", call(uriClass, "parse",
                "sketchwareai://import?url=https%3A%2F%2Fgithub.com%2Focto%2FSketchwareAI-My_App"));
        String viaPath = (String) call(linkActivity, "linkOf", call(uriClass, "parse", "sketchwareai://import/octo/SketchwareAI-My_App"));
        Object elsewhere = call(linkActivity, "linkOf", call(uriClass, "parse", "https://evil.example/octo/SketchwareAI-My_App"));
        Object badScheme = call(linkActivity, "linkOf", call(uriClass, "parse", "sketchwareai://import?url=https%3A%2F%2Fevil.example%2Fx"));
        Object plainHttp = call(linkActivity, "linkOf", call(uriClass, "parse", "http://github.com/octo/SketchwareAI-My_App"));
        String repoLink = "https://github.com/octo/SketchwareAI-My_App";
        check("v2.0.4: deep links: github.com project pages and sketchwareai://import (url= or /owner/repo) open the "
                        + "restore screen; other hosts, plain http and non-GitHub addresses don't",
                repoLink.equals(direct) && repoLink.equals(viaScheme) && repoLink.equals(viaPath) && elsewhere == null
                        && badScheme == null && plainHttp == null, direct + " " + viaScheme + " " + viaPath);

        // --- v2.0.3: "never auto-logout": GitHub's refusal is a mark with a note; the session stays
        Class<?> session = c("com.sketchware.ai.github.GitHubSession");
        Object githubPrefs = call(CTX, "getSharedPreferences", "sketchware_ai_github", 0);
        Object pe = call(githubPrefs, "edit");
        call(pe, "putString", "token", "v2:AAAA:AAAA");
        call(pe, "putString", "login", "octo");
        call(pe, "putString", "user_id", "583231");
        call(pe, "commit");
        Object sealedNow = call(call(c("com.sketchware.ai.github.TokenStore"), "get", CTX), "sealedKey");
        call(session, "markRefused", CTX, "GitHub didn't accept the other sign-in.", "0000");
        Object notMarked = call(session, "profile", CTX);
        check("v2.0.3 review: a refusal of another sign-in (one replaced meanwhile) doesn't mark this one",
                !String.valueOf(get(notMarked, "problem")).contains("other sign-in"), get(notMarked, "problem"));
        call(session, "markRefused", CTX, "GitHub didn't accept the saved sign-in at 22:17. You're still signed in here.", sealedNow);
        Object refusedProfile = call(session, "profile", CTX);
        check("v2.0.3: a 401 marks the sign-in (the card says so), the login stays", refusedProfile != null
                && "octo".equals(get(refusedProfile, "login")) && String.valueOf(get(refusedProfile, "problem")).contains("didn't accept"),
                refusedProfile == null ? null : get(refusedProfile, "problem"));
        Object account = neu("com.sketchware.ai.github.CloudBackup$Account");
        set(account, "login", "octo");
        set(account, "id", "583231");
        set(account, "repositories", 12);
        call(session, "saveProfile", CTX, account, null);
        Object checkedProfile = call(session, "profile", CTX);
        check("v2.0.3: GitHub accepting the sign-in again clears the mark; the card's line counts 12 repositories "
                        + "(this harness can't read the stand-in token: its reason shows instead of the mark)",
                Integer.valueOf(12).equals(get(checkedProfile, "repositories"))
                        && !String.valueOf(get(checkedProfile, "problem")).contains("didn't accept")
                        && String.valueOf(get(checkedProfile, "problem")).contains("can't read"), get(checkedProfile, "problem"));
        check("v2.0.3: still signed in after all of it (only Sign out removes the sign-in)",
                "octo".equals(call(githubPrefs, "getString", "login", null)) && call(githubPrefs, "getString", "token", null) != null, null);

        // --- v2.0.3: the backup queue: a tap goes first, a second tap joins, a job's Error ends only that job
        Class<?> syncClass = c("com.sketchware.ai.github.CloudSync");
        Object cloudSync = call(syncClass, "get", CTX);
        List<String> ran = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release2 = new java.util.concurrent.CountDownLatch(1);
        Object runner = proxy("com.sketchware.ai.github.CloudSync$Runner", (m, a) -> {
            if (!m.equals("run")) return null;
            String id;
            try {
                id = (String) get(a[0], "scId");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ran.add(id);
            try {
                if (id.equals("9001")) release.await(20, java.util.concurrent.TimeUnit.SECONDS);
                if (id.equals("9006")) release2.await(20, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            if (id.equals("9004")) throw new OutOfMemoryError("test: a backup too large for memory");
            return "done " + id;
        });
        set(syncClass, "runnerForTests", runner);
        set(syncClass, "mainForTests", (java.util.concurrent.Executor) Runnable::run);
        List<String> results = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> steps = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.function.Function<String, Object> result = tag -> proxy("com.sketchware.ai.github.CloudSync$Result", (m, a) -> {
            if (m.equals("done")) results.add(tag + ":" + a[0] + ":" + a[1]);
            return null;
        });
        Object watcher = proxy("com.sketchware.ai.github.CloudSync$Progress", (m, a) -> {
            if (m.equals("step")) steps.add(a[0] + " " + a[1]);
            return null;
        });
        String jobClass = "com.sketchware.ai.github.CloudSync$Job";
        // v2.0.5: the running job is a tap's (a running automatic backup pauses for a tap: checked below)
        call(cloudSync, "enqueue", neu(jobClass, "9001", "backup", "First", autoOptions, true), null, result.apply("A"));
        waitFor(() -> ran.contains("9001"), 5000);
        call(cloudSync, "enqueue", neu(jobClass, "9003", "backup", null, autoOptions, false), null, result.apply("C"));
        call(cloudSync, "enqueue", neu(jobClass, "9002", "backup", "Second", autoOptions, true), watcher, result.apply("B1"));
        call(cloudSync, "enqueue", neu(jobClass, "9002", "backup", "Second", autoOptions, true), null, result.apply("B2"));
        // v2.0.5: the running job's name and percent are shown
        boolean waitingShown = steps.size() > 0 && steps.get(0).contains("Waiting: Backing up First (")
                && steps.get(0).contains("goes first");
        boolean busy = Boolean.TRUE.equals(call(cloudSync, "isRunning", "9002"));
        release.countDown();
        waitFor(() -> ran.size() >= 3 && results.size() >= 4, 10000);
        check("v2.0.3: a tap's backup waits visibly behind a running one, then goes before the automatic ones",
                waitingShown && busy && ran.subList(0, 3).equals(java.util.Arrays.asList("9001", "9002", "9003")), ran + " " + steps);
        check("v2.0.3: a second tap on the same project joins its backup (one upload, both hear the end)",
                results.contains("B1:true:done 9002") && results.contains("B2:true:done 9002")
                        && java.util.Collections.frequency(ran, "9002") == 1, results);
        call(cloudSync, "enqueue", neu(jobClass, "9004", "backup", null, autoOptions, false), null, result.apply("E"));
        call(cloudSync, "enqueue", neu(jobClass, "9005", "backup", null, autoOptions, false), null, result.apply("F"));
        waitFor(() -> ran.contains("9005") && results.size() >= 6, 10000);
        String eResult = null;
        for (String r : results) if (r.startsWith("E:")) eResult = r;
        check("v2.0.3: an Error in a backup (out of memory) ends that job with its message, frees the project, and the "
                        + "next job runs (no second-tap \"is being backed up\" forever)",
                eResult != null && eResult.startsWith("E:false:") && eResult.contains("OutOfMemoryError")
                        && results.contains("F:true:done 9005") && Boolean.FALSE.equals(call(cloudSync, "isRunning", "9004")),
                eResult);
        call(cloudSync, "enqueue", neu(jobClass, "9006", "backup", "Busy", autoOptions, false), null, result.apply("G"));
        waitFor(() -> ran.contains("9006"), 5000);
        call(cloudSync, "enqueue", neu(jobClass, "9007", "backup", null, autoOptions, false), null, result.apply("H"));
        call(cloudSync, "enqueue", neu(jobClass, "9008", "backup", null, autoOptions, false), null, result.apply("I"));
        call(cloudSync, "enqueue", neu(jobClass, "9008", "backup", "Tapped", libsOnly, true), null, result.apply("J"));
        release2.countDown();
        waitFor(() -> ran.contains("9007") && ran.contains("9008"), 10000);
        check("v2.0.3: a tap on a project whose automatic backup waits moves it forward", ran.indexOf("9008") < ran.indexOf("9007"),
                ran);
        // review M3: a backup after a save while that project's backup is already uploading waits as a job of its own
        java.util.concurrent.CountDownLatch release3 = new java.util.concurrent.CountDownLatch(1);
        Object runner3 = proxy("com.sketchware.ai.github.CloudSync$Runner", (m, a) -> {
            if (!m.equals("run")) return null;
            String id;
            try {
                id = (String) get(a[0], "scId");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ran.add(id);
            if (ran.size() == 1) {
                try {
                    release3.await(20, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }
            return "done " + id;
        });
        set(syncClass, "runnerForTests", runner3);
        ran.clear();
        call(cloudSync, "enqueue", neu(jobClass, "9010", "backup", "Saved", autoOptions, false), null, result.apply("K"));
        waitFor(() -> ran.contains("9010"), 5000);
        Object followUp = neu(jobClass, "9010", "backup", null, autoOptions, false);
        set(followUp, "afterRunning", true);
        call(cloudSync, "enqueue", followUp, null, result.apply("L"));
        Object followUp2 = neu(jobClass, "9010", "backup", null, autoOptions, false);
        set(followUp2, "afterRunning", true);
        call(cloudSync, "enqueue", followUp2, null, result.apply("M"));
        release3.countDown();
        waitFor(() -> java.util.Collections.frequency(ran, "9010") >= 2 && results.contains("M:true:done 9010"), 10000);
        check("v2.0.3 review: a save during a running backup of that project: one more backup after it (two saves "
                + "meanwhile: still one)", java.util.Collections.frequency(ran, "9010") == 2
                && results.contains("L:true:done 9010") && results.contains("M:true:done 9010"), ran);
        set(syncClass, "runnerForTests", runner);

        // --- v2.0.3: a save in the editor: a background backup only when signed in, the switch on, and the project changed
        ran.clear();
        call(cloudSync, "projectSaved", scId, true);
        boolean firstSave = waitFor(() -> ran.contains(scId), 5000);
        Object fingerprint = call(syncClass, "fingerprint", scId);
        call(links, "saved", scId, "octo", null, autoOptions, fingerprint, 0L);
        ran.clear();
        call(cloudSync, "projectSaved", scId, true);
        boolean unchangedSkipped = !waitFor(() -> ran.contains(scId), 1500);
        // review M4: every save rewrites every file: the same content written again is no change
        File logicFile = new File(sketchware, "data/" + scId + "/logic");
        boolean sameContent = true;
        if (logicFile.isFile()) {
            byte[] bytes = java.nio.file.Files.readAllBytes(logicFile.toPath());
            Thread.sleep(1100);
            java.nio.file.Files.write(logicFile.toPath(), bytes);
            sameContent = fingerprint.equals(call(syncClass, "fingerprint", scId));
        }
        check("v2.0.3 review: the change check reads contents (a save that writes the same bytes is no change)", sameContent, null);
        File touched = new File(sketchware, "data/" + scId + "/v203_test_change.txt");
        writeText(touched, "a change");
        call(cloudSync, "projectSaved", scId, false);
        boolean saveWithinFive = !waitFor(() -> ran.contains(scId), 1500);
        call(cloudSync, "projectSaved", scId, true);
        boolean leaving = waitFor(() -> ran.contains(scId), 5000);
        ran.clear();
        Object p12 = call(CTX, "getSharedPreferences", "P12", 0);
        Object p12e = call(p12, "edit");
        call(p12e, "putBoolean", "P12I_github_backup", false);
        call(p12e, "commit");
        call(cloudSync, "projectSaved", scId, true);
        boolean switchedOff = !waitFor(() -> ran.contains(scId), 1500);
        Object p12f = call(p12, "edit");
        call(p12f, "remove", "P12I_github_backup");
        call(p12f, "commit");
        check("v2.0.3: Save & exit backs the project up in the background; an unchanged project isn't sent again; a plain "
                        + "Save waits five minutes after the last backup; System Settings' switch turns it off",
                firstSave && unchangedSkipped && saveWithinFive && leaving && switchedOff,
                firstSave + " " + unchangedSkipped + " " + saveWithinFive + " " + leaving + " " + switchedOff);
        touched.delete();
        // --- v2.0.5: a tap pauses a running automatic backup (it goes on after the tap's job); the watchdog stops a
        // job with no progress and leaves a hung one behind so the queue goes on
        ran.clear();
        java.util.concurrent.atomic.AtomicInteger autoRuns = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch hang = new java.util.concurrent.CountDownLatch(1);
        Object runner5 = proxy("com.sketchware.ai.github.CloudSync$Runner", (m, a) -> {
            if (!m.equals("run")) return null;
            String id;
            try {
                id = (String) get(a[0], "scId");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ran.add(id);
            try {
                if (id.equals("9020") && autoRuns.incrementAndGet() == 1) {
                    Thread.sleep(20_000);
                }
                if (id.equals("9030")) Thread.sleep(20_000);
                if (id.equals("9031")) {
                    // hung: ignores the interrupt
                    while (hang.getCount() > 0) {
                        try {
                            hang.await(20, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            } catch (InterruptedException e) {
                Object cancelled;
                try {
                    cancelled = neu("com.sketchware.ai.github.GitHubHttp$Cancelled");
                } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }
                GitHubTest.<RuntimeException>sneaky((Throwable) cancelled);
            }
            return "done " + id;
        });
        set(syncClass, "runnerForTests", runner5);
        steps.clear();
        results.clear();
        call(cloudSync, "enqueue", neu(jobClass, "9020", "backup", "Auto", autoOptions, false), null, result.apply("P"));
        waitFor(() -> ran.contains("9020"), 5000);
        call(cloudSync, "enqueue", neu(jobClass, "9021", "backup", "Tapped", autoOptions, true), watcher, result.apply("Q"));
        boolean pausedText = waitFor(() -> steps.stream().anyMatch(t -> t.contains("Auto (") && t.contains("pauses for this one")), 3000);
        boolean bothDone = waitFor(() -> results.contains("Q:true:done 9021") && results.contains("P:true:done 9020"), 15000);
        check("v2.0.5: a tap pauses the running automatic backup (\"Auto (0%) pauses for this one\"), runs at once, and the "
                        + "paused backup runs again after it (one result each)",
                pausedText && bothDone && ran.equals(java.util.Arrays.asList("9020", "9021", "9020"))
                        && java.util.Collections.frequency(results, "P:true:done 9020") == 1, ran + " " + results + " " + steps);
        long stallBefore = (Long) get(syncClass, "STALL_MS");
        long abandonBefore = (Long) get(syncClass, "ABANDON_MS");
        set(syncClass, "STALL_MS", 600L);
        set(syncClass, "ABANDON_MS", 800L);
        ran.clear();
        results.clear();
        call(cloudSync, "enqueue", neu(jobClass, "9030", "backup", "Stuck", autoOptions, true), null, result.apply("S"));
        boolean stalled = waitFor(() -> results.stream().anyMatch(r -> r.startsWith("S:false:Stopped: nothing moved")), 10000);
        call(cloudSync, "enqueue", neu(jobClass, "9031", "backup", "Hung", autoOptions, true), null, result.apply("T"));
        waitFor(() -> ran.contains("9031"), 5000);
        call(cloudSync, "enqueue", neu(jobClass, "9032", "backup", "Next", autoOptions, true), null, result.apply("U"));
        boolean wentOn = waitFor(() -> results.contains("U:true:done 9032")
                && results.stream().anyMatch(r -> r.startsWith("T:false:")), 15000);
        hang.countDown();
        set(syncClass, "STALL_MS", stallBefore);
        set(syncClass, "ABANDON_MS", abandonBefore);
        Thread.sleep(300);
        check("v2.0.5: the watchdog stops a job with no progress (\"Stopped: nothing moved\", it can be retried) and a "
                        + "thread that doesn't stop is left behind: the next job runs (never \"Waiting\" forever)",
                stalled && wentOn && Boolean.TRUE.equals(call(cloudSync, "canRetry", call(neu(jobClass, "9030", "backup",
                        "Stuck", autoOptions, true), "key"))), results);
        Class<?> sessionClass = c("com.sketchware.ai.github.GitHubSession");
        call(sessionClass, "markRefused", CTX, "GitHub didn't accept the saved sign-in.", sealedNow);
        boolean refusedNow = Boolean.TRUE.equals(call(sessionClass, "isRefused", CTX));
        call(sessionClass, "saveProfile", CTX, account, null);
        boolean acceptedAgain = Boolean.FALSE.equals(call(sessionClass, "isRefused", CTX));
        check("v2.0.5: a refused sign-in is known at once (backups wait without sending more requests); GitHub accepting "
                + "it again clears that", refusedNow && acceptedAgain, refusedNow + " " + acceptedAgain);
        set(syncClass, "runnerForTests", null);
        set(syncClass, "mainForTests", null);
        Object cleanup = call(githubPrefs, "edit");
        call(cleanup, "remove", "token");
        call(cleanup, "remove", "login");
        call(cleanup, "remove", "user_id");
        call(cleanup, "commit");
        linkFile.delete();

        // --- a .swb with an entry outside its folder is refused
        File evilZip = new File(swb.getParentFile(), "evil.swb");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(evilZip))) {
            z.putNextEntry(new java.util.zip.ZipEntry("../evil-outside.txt"));
            z.write(1);
            z.closeEntry();
        }
        File into = new File(swb.getParentFile(), "evil-into");
        boolean unzipped = (Boolean) call(c("mod.hey.studios.project.backup.BackupFactory"), "unzip", evilZip, into);
        check("zip entry ../ refused", !unzipped && !new File(swb.getParentFile(), "evil-outside.txt").exists(), unzipped);

        // clean up the restored project
        for (String folder : new String[]{"data", "bak", "mysc", "mysc/list", "resources/images", "resources/icons",
                "resources/fonts", "resources/sounds"}) {
            deleteTree(new File(sketchware, folder + "/" + freeId));
        }
        deleteTree(leftover);
        deleteTree(into);
        evilZip.delete();
        swb.delete();
        OUT.println("GITHUBTEST " + ok + "/" + total + (ok == total ? " ok" : " FAILED"));
    }

    static File BackupFactoryDir() throws Throwable {
        return new File((String) call(c("mod.hey.studios.project.backup.BackupFactory"), "getBackupDir"));
    }

    static void writeText(File f, String text) throws Exception {
        f.getParentFile().mkdirs();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(text.getBytes("UTF-8"));
        }
    }

    static String readText(File f) {
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
        } catch (Exception e) {
            return String.valueOf(e);
        }
    }

    static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        f.delete();
    }
}
