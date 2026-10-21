package h;

import java.io.File;
import java.util.*;

import static h.R.*;

/**
 * Runs Sketchware AI's own Run-build path on the Android runtime, with the classes of the shipped APK.
 * main create <spec> <sc_id> : writes a project with the IDE's own project classes (lC/hC/iC/eC/kC)
 * main build <sc_id>         : DesignActivity.BuildAsyncTask.b() step by step (minus the Activity)
 * System properties: h.apk (the IDE APK: assets), h.root (app dirs), h.native (dir with libaapt2.so).
 */
public class Main {
    static final android.content.Context CTX = new android.content.Context();
    static final List<String> progress = new ArrayList<>();
    /** SystemLogPrinter (Dp constructor) sends System.out/err to /.sketchware/debug.txt; the harness keeps the terminal. */
    static final java.io.PrintStream OUT = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true);

    public static void main(String[] args) throws Throwable {
        // kotlinc leaves non-daemon threads behind: without System.exit the runtime never ends after a Kotlin build
        try {
            set(c("com.besome.sketch.SketchApplication"), "mApplicationContext", CTX);
            switch (args[0]) {
                case "create":
                    Fixtures.create(args[1], args[2]);
                    break;
                case "build":
                    FROM = args.length > 2 ? args[2] : null;
                    build(args[1]);
                    break;
                default:
                    throw new IllegalArgumentException(args[0]);
            }
        } catch (Throwable t) {
            t.printStackTrace(OUT);
            System.exit(1);
        }
        System.exit(0);
    }

    static Object receiver() {
        return proxy("mod.jbk.build.BuildProgressReceiver", (m, a) -> {
            if (a.length > 0) {
                progress.add(String.valueOf(a[0]));
                OUT.println("progress: " + a[0]);
            }
            return null;
        });
    }

    /** Resume a build at this step (earlier steps whose output is on disk are skipped; set-up steps always run). */
    static String FROM;
    static final List<String> ALWAYS = Arrays.asList("yq", "Dp", "aapt2", "built-in libraries");

    static void step(String name, ThrowingRunnable r) throws Throwable {
        if (FROM != null) {
            if (name.equals(FROM)) FROM = null;
            else if (!ALWAYS.contains(name)) { OUT.println("STEP " + name + " skipped (resume)"); return; }
        }
        long t = System.currentTimeMillis();
        OUT.println("STEP " + name);
        try {
            r.run();
        } catch (Throwable e) {
            OUT.println("STEP " + name + " FAILED: " + e);
            e.printStackTrace(OUT);
            throw e;
        }
        OUT.println("STEP " + name + " ok (" + (System.currentTimeMillis() - t) + " ms)");
    }

    interface ThrowingRunnable {
        void run() throws Throwable;
    }

    static void build(String sc_id) throws Throwable {
        Class<?> jC = c("a.a.a.jC"), wq = c("a.a.a.wq"), lC = c("a.a.a.lC");
        Class<?> buildMap = c("com.sketchware.ai.buildmap.BuildMap");
        Class<?> warnings = c("com.sketchware.ai.project.BuildWarnings");
        Object rcv = receiver();
        Object[] q = new Object[1];
        Object[] dp = new Object[1];
        step("yq", () -> q[0] = neu("a.a.a.yq", CTX, call(wq, "d", sc_id), call(lC, "b", sc_id)));
        String mysc = (String) get(q[0], "projectMyscPath");
        step("prepare", () -> {
            call(buildMap, "begin", sc_id, mysc);
            call(buildMap, "task", get(buildMap, "PREPARE"));
            call(c("mod.agus.jcoderz.lib.FileUtil"), "deleteFile", mysc);
            call(call(jC, "a", sc_id), "k");
            call(q[0], "c", CTX);
            call(q[0], "a");
            call(q[0], "a", CTX, call(wq, "e", "600"));
            String res = (String) get(q[0], "resDirectoryPath");
            call(call(jC, "d", sc_id), "b", res + File.separator + "drawable-xhdpi");
            call(call(jC, "d", sc_id), "c", res + File.separator + "raw");
            call(call(jC, "d", sc_id), "a", get(q[0], "assetsPath") + File.separator + "fonts");
        });
        step("library integrity", () -> {
            call(buildMap, "task", get(buildMap, "LIBRARIES"));
            Object cb = proxy(callbackType(), (m, a) -> null);
            call(warnings, "carry", call(c("com.sketchware.ai.project.LibraryIntegrity"), "restore", sc_id, cb));
        });
        step("generate", () -> {
            call(buildMap, "task", get(buildMap, "GENERATE"));
            call(q[0], "b", call(jC, "b", sc_id), call(jC, "a", sc_id), call(jC, "c", sc_id));
            call(q[0], "f");
            call(q[0], "e");
        });
        step("Dp", () -> {
            dp[0] = neu("a.a.a.Dp", rcv, CTX, q[0]);
            call(warnings, "begin", q[0]);
        });
        step("aapt2", () -> call(dp[0], "maybeExtractAapt2"));
        step("built-in libraries", () -> call(dp[0], "getBuiltInLibrariesReady"));
        List<String> libs = new ArrayList<>();
        for (Object jp : (List<?>) call(get(dp[0], "builtInLibraryManager"), "a")) libs.add((String) call(jp, "a"));
        OUT.println("BUILT-IN LIBRARIES (" + libs.size() + "): " + libs);
        step("resources", () -> {
            call(buildMap, "task", get(buildMap, "RESOURCES"));
            call(dp[0], "compileResources");
        });
        step("kotlin", () -> {
            call(buildMap, "task", get(buildMap, "KOTLIN"));
            call(c("mod.hey.studios.compiler.kotlin.KotlinCompilerBridge"), "compileKotlinCodeIfPossible", rcv, dp[0]);
        });
        step("java", () -> {
            call(buildMap, "task", get(buildMap, "JAVA"));
            call(dp[0], "compileJavaCode");
        });
        step("dex", () -> {
            call(buildMap, "task", get(buildMap, "DEX"));
            call(dp[0], "createDexFilesFromClasses");
        });
        step("dex merge", () -> {
            call(buildMap, "task", get(buildMap, "DEX_MERGE"));
            call(dp[0], "getDexFilesReady");
        });
        step("package", () -> {
            call(buildMap, "task", get(buildMap, "PACKAGE"));
            call(dp[0], "buildApk");
        });
        step("sign", () -> {
            call(buildMap, "task", get(buildMap, "SIGN"));
            call(dp[0], "signDebugApk");
        });
        OUT.println("WARNINGS " + call(warnings, "count"));
        OUT.println(call(warnings, "report"));
        OUT.println("APK " + get(q[0], "finalToInstallApkPath"));
    }

    /** The functional interface LibraryIntegrity.restore takes for progress text. */
    static String callbackType() {
        for (java.lang.reflect.Method m : c("com.sketchware.ai.project.LibraryIntegrity").getDeclaredMethods()) {
            if (m.getName().equals("restore") && m.getParameterCount() == 2) return m.getParameterTypes()[1].getName();
        }
        throw new IllegalStateException("LibraryIntegrity.restore(String, ?) not found");
    }
}
