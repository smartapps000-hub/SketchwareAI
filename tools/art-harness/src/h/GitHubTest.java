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
 * v1.9.8: the parts of GitHub sign-in and cloud backup that run without a network, with the shipped APK's classes:
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

        // --- token store (software AES key standing in for the Keystore's)
        String token = "gho_fake_token_123";
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
        check("stored as v1:iv:ciphertext (AES-GCM), token not readable", stored != null && stored.startsWith("v1:")
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

        // --- app-control.json (config/app-control.json of the repository): notices, never a lock
        Class<?> control = c("com.sketchware.ai.github.AppControl");
        String repoFile = new String(java.nio.file.Files.readAllBytes(new File(System.getProperty("h.appcontrol",
                "/home/user/SketchwareAI/config/app-control.json")).toPath()), "UTF-8");
        Object shipped = call(control, "parse", repoFile);
        check("repository's app-control.json reads: enabled, min 199, no login", shipped != null
                && Boolean.TRUE.equals(get(shipped, "appEnabled")) && Integer.valueOf(199).equals(get(shipped, "minVersionCode"))
                && Boolean.FALSE.equals(get(shipped, "requireLogin")), null);
        check("v1.9.8 (199) with it: no notice", call(control, "decide", shipped, 199, false, null) == null, null);
        Object old198 = call(control, "decide", shipped, 198, false, null);
        check("an older version (198): Update with the URL", old198 != null && "update".equals(get(old198, "kind"))
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
        check("garbage, http update URL: ignored", call(control, "parse", "<html>") == null
                && get(call(control, "parse", "{\"update_url\":\"http://x\"}"), "updateUrl") == null, null);

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
