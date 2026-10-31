package h;

import java.io.File;
import java.util.*;

import static h.R.*;

/**
 * v1.8.13 "highest version wins" on the Android runtime, offline: LibraryInstaller.install with plans whose libraries
 * are already in local_libs (fake folders h.test:*, classes.jar + classes.dex + needs.json), in a scratch project's
 * library list. Usage: run.sh <apk> <root> versions <sc_id>   (prints CHECK ok/FAIL lines and VERSIONS n/m ok)
 */
public class Versions {
    static int checks = 0, fails = 0;
    static File root;
    static String sc;

    static void check(String what, boolean ok, String detail) {
        checks++;
        if (!ok) fails++;
        Main.OUT.println((ok ? "CHECK ok   " : "CHECK FAIL ") + what + (detail.isEmpty() ? "" : "  [" + detail + "]"));
    }

    /** A usable library folder with its coordinate (and what it needs) in needs.json. */
    static void lib(String artifact, String version, String... needs) throws Throwable {
        File dir = new File(root, artifact + "_V_" + version);
        Fixtures.deleteTree(dir);
        dir.mkdirs();
        new java.io.FileOutputStream(new File(dir, "classes.jar")).close();
        new java.io.FileOutputStream(new File(dir, "classes.dex")).close();
        Object n = call(c("com.sketchware.ai.library.LibraryNeeds"), "create", "h.test:" + artifact + ":" + version);
        @SuppressWarnings("unchecked") List<String> requires = (List<String>) get(n, "requires");
        requires.addAll(Arrays.asList(needs));
        call(n, "write", dir);
    }

    static Object artifact(String name, String version, String... needs) throws Throwable {
        Object a = neu("com.sketchware.ai.library.MavenResolver$Artifact");
        set(a, "group", "h.test");
        set(a, "name", name);
        set(a, "version", version);
        set(a, "packaging", "aar");
        @SuppressWarnings("unchecked") List<String> n = (List<String>) get(a, "needs");
        n.addAll(Arrays.asList(needs));
        return a;
    }

    static Object plan(Object rootArtifact, Object... others) throws Throwable {
        Object p = neu("com.sketchware.ai.library.MavenResolver$Plan");
        set(p, "root", rootArtifact);
        @SuppressWarnings("unchecked") List<Object> artifacts = (List<Object>) get(p, "artifacts");
        artifacts.add(rootArtifact);
        artifacts.addAll(Arrays.asList(others));
        return p;
    }

    static void on(String... folders) throws Throwable {
        ArrayList<HashMap<String, Object>> list = new ArrayList<>();
        for (String f : folders) {
            @SuppressWarnings("unchecked") HashMap<String, Object> e = (HashMap<String, Object>) call(c("com.sketchware.ai.library.LibraryInstaller"), "entry", f);
            list.add(e);
        }
        call(c("com.sketchware.ai.library.LibraryInstaller"), "save", sc, list);
    }

    static List<String> onNow() throws Throwable {
        List<String> names = new ArrayList<>();
        Object list = call(c("com.sketchware.ai.library.LibraryInstaller"), "enabled", sc);
        for (Object e : (List<?>) list) names.add(String.valueOf(((Map<?, ?>) e).get("name")));
        return names;
    }

    static Object install(Object plan) throws Throwable {
        Object outcome = call(c("com.sketchware.ai.library.LibraryInstaller"), "install", null, plan, sc, null);
        Main.OUT.println("  installed " + get(outcome, "installed") + " reused " + get(outcome, "reused") + " enabled "
                + get(outcome, "enabled") + " replaced " + get(outcome, "replaced") + " keptNewer " + get(outcome, "keptNewer")
                + " rootKeptBack " + get(outcome, "rootKeptBack") + " failed " + get(outcome, "failed") + " notSwitchedOn "
                + get(outcome, "notSwitchedOn") + " | on " + onNow());
        return outcome;
    }

    static boolean has(Object outcome, String field, String folder) throws Throwable {
        return ((List<?>) get(outcome, field)).contains(folder);
    }

    public static void run(String sc_id) throws Throwable {
        sc = sc_id;
        root = (File) call(c("com.sketchware.ai.project.LibraryHeal"), "localLibs");
        new File(root.getParentFile().getParentFile(), "data/" + sc).mkdirs();
        lib("hwx", "1.0");
        lib("hwx", "2.0");
        lib("hwroot", "1.0", "h.test:hwx:1.0");
        lib("hwroot", "0.9", "h.test:hwdep:0.9");
        lib("hwdep", "0.9");
        Fixtures.deleteTree(new File(root, "hwx_V_1.0"));   // 1.0 isn't installed: the first install would need it

        // A. 2.0 is on; hwroot 1.0 needs hwx 1.0, which isn't installed: 2.0 is used, nothing is fetched
        on("hwx_V_2.0");
        Object o = install(plan(artifact("hwroot", "1.0", "h.test:hwx:1.0"), artifact("hwx", "1.0")));
        List<String> now = onNow();
        check("A: needed lower version, higher on: the higher one is used", has(o, "reused", "hwx_V_2.0")
                && ((List<?>) get(o, "failed")).isEmpty() && !new File(root, "hwx_V_1.0").exists(), "");
        check("A: root on, hwx 2.0 stays on, no 1.0", now.contains("hwroot_V_1.0") && now.contains("hwx_V_2.0")
                && !now.contains("hwx_V_1.0"), now.toString());

        // B. hwx 1.0 installed too: it is not switched on over 2.0 (no downgrade)
        lib("hwx", "1.0");
        o = install(plan(artifact("hwroot", "1.0", "h.test:hwx:1.0"), artifact("hwx", "1.0")));
        now = onNow();
        check("B: installed lower version not switched on over the higher one", now.contains("hwx_V_2.0")
                && !now.contains("hwx_V_1.0") && !has(o, "replaced", "hwx_V_2.0"), now.toString());

        // C. the library installed is lower than the version on: nothing of it goes on (not its dependency either)
        on("hwroot_V_1.0", "hwx_V_2.0");
        o = install(plan(artifact("hwroot", "0.9", "h.test:hwdep:0.9"), artifact("hwdep", "0.9")));
        now = onNow();
        check("C: lower root kept back", (Boolean) get(o, "rootKeptBack") && has(o, "keptNewer", "hwroot_V_1.0"), "");
        check("C: 1.0 stays on, 0.9 and its dependency stay off", now.contains("hwroot_V_1.0") && !now.contains("hwroot_V_0.9")
                && !now.contains("hwdep_V_0.9"), now.toString());

        // D. a higher version replaces the lower one that is on
        on("hwx_V_1.0");
        o = install(plan(artifact("hwx", "2.0")));
        now = onNow();
        check("D: higher version replaces the lower one", has(o, "replaced", "hwx_V_1.0") && now.contains("hwx_V_2.0")
                && !now.contains("hwx_V_1.0"), now.toString());

        // E. nothing is switched on: only installed (no project) -> the needed lower version is not replaced by 2.0
        String keep = sc;
        sc = null;
        Fixtures.deleteTree(new File(root, "hwx_V_1.0"));
        Object outcome = call(c("com.sketchware.ai.library.LibraryInstaller"), "install", null,
                plan(artifact("hwroot", "1.0", "h.test:hwx:1.0"), artifact("hwx", "1.0")), null, null);
        sc = keep;
        check("E: without a project, no other version is reused (1.0 has to be fetched: fails offline)",
                !has(outcome, "reused", "hwx_V_2.0") && !((List<?>) get(outcome, "failed")).isEmpty(), String.valueOf(get(outcome, "failed")));

        for (String f : new String[]{"hwx_V_1.0", "hwx_V_2.0", "hwroot_V_1.0", "hwroot_V_0.9", "hwdep_V_0.9"}) Fixtures.deleteTree(new File(root, f));
        Main.OUT.println("VERSIONS " + (checks - fails) + "/" + checks + " ok");
    }
}
