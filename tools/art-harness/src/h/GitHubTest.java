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
 * v1.9.8/v1.9.9: the parts of GitHub sign-in and cloud backup that run without a network, with the shipped APK's classes:
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

    @SuppressWarnings("unchecked")
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
        current[0] = KeyGenerator.getInstance("AES").generateKey();
        check("other key: signed out, nothing returned", call(again, "token") == null && call(again, "login") == null, null);
        current[0] = key;
        call(store, "save", token, "octo", "583231");
        device[0] = "android-id-2|cert-A";
        check("app data copied to another phone (other ANDROID_ID): signed out", call(again, "token") == null
                && call(again, "login") == null && call(prefs, "getString", "token", null) == null, null);
        device[0] = "android-id-1|cert-A";
        call(store, "save", token, "octo", "583231");
        device[0] = "android-id-1|cert-B";
        check("another signing certificate (a re-signed copy): signed out", call(again, "token") == null, null);
        device[0] = "android-id-1|cert-A";
        call(store, "save", token, "octo", "583231");
        Object editor = call(prefs, "edit");
        call(editor, "putString", "user_id", "999");
        call(editor, "commit");
        check("session moved to another user id: signed out", call(again, "token") == null, null);
        call(store, "save", token, "octo", "583231");
        String v2 = (String) call(prefs, "getString", "token", null);
        Object editor1 = call(prefs, "edit");
        call(editor1, "putString", "token", "v1:" + v2.substring(3));
        call(editor1, "commit");
        check("a v1.9.8 token (v1, not bound): signed out, sign in again", call(again, "token") == null
                && call(again, "login") == null, null);
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

        // --- app-control.json (config/app-control.json of the repository): notices, never a lock
        String repoFile = new String(java.nio.file.Files.readAllBytes(new File(System.getProperty("h.appcontrol",
                "/home/user/SketchwareAI/config/app-control.json")).toPath()), "UTF-8");
        Object shipped = call(control, "parse", repoFile);
        check("repository's app-control.json reads: enabled, min 200, no login", shipped != null
                && Boolean.TRUE.equals(get(shipped, "appEnabled")) && Integer.valueOf(200).equals(get(shipped, "minVersionCode"))
                && Boolean.FALSE.equals(get(shipped, "requireLogin")), null);
        check("v1.9.9 (200) with it: no notice", call(control, "decide", shipped, 200, false, null) == null, null);
        Object old198 = call(control, "decide", shipped, 199, false, null);
        check("an older version (v1.9.8, 199): Update with the URL", old198 != null && "update".equals(get(old198, "kind"))
                && String.valueOf(get(old198, "url")).startsWith("https://github.com/smartapps000-hub/"), old198 == null ? null : get(old198, "url"));
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
        String fromList = (String) call(c("mod.hey.studios.project.backup.BackupFactory"), "getNewScId");
        check("getNewScId: numeric maximum + 1, free", Integer.parseInt(fromList) > Integer.parseInt(freeId)
                && !(Boolean) call(ids, "inUse", sketchware, Integer.parseInt(fromList)), fromList);

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

    static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        f.delete();
    }
}
