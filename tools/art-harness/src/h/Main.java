package h;

import java.io.File;
import java.util.*;

import static h.R.*;

/**
 * Runs Sketchware AI's own Run-build path on the Android runtime, with the classes of the shipped APK.
 * main create <spec> <sc_id> : writes a project with the IDE's own project classes (lC/hC/iC/eC/kC)
 * main build <sc_id>         : DesignActivity.BuildAsyncTask.b() step by step (minus the Activity)
 * main aab <sc_id>           : the export screen's "Export AAB" with the test key
 * main export <sc_id>        : the export screen's "Export to Android Studio" (the Gradle project), unzipped
 * main javaedit <sc_id>      : (v1.8.8) the Java editor's saves on fixture javaedit (JavaEdit), then a build
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
                case "aab":
                    FROM = args.length > 2 ? args[2] : null;
                    exportAab(args[1]);
                    break;
                case "export":
                    exportSource(args[1]);
                    break;
                case "javaedit-open":
                    JavaEdit.openAll(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "javaedit":
                    JavaEdit.run(args[1], args.length < 3 || !args[2].equals("nobuild"));
                    break;
                case "libinfo": {
                    // v1.8.9: the Library Resolver's (i) text for an installed library, read on ART (offline)
                    java.io.File dir = new java.io.File((java.io.File) call(c("com.sketchware.ai.project.LibraryHeal"), "localLibs"), args[1]);
                    Object info = call(c("com.sketchware.ai.library.LibraryInfo"), "read", dir, args.length > 2 ? args[2] : null, null);
                    OUT.println("LIBINFO\n" + call(info, "describe", args[1], "size") + "\nLIBINFO END");
                    break;
                }
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

    /**
     * ExportProjectActivity.BuildingAsyncTask.b() with "Export AAB" and the test key, minus the Activity: an export
     * build (no source map, DEX files merged), AppBundleCompiler (module zip, bundletool 1.18.3 BuildBundleCommand with
     * 16 KB page alignment for native libraries), then the AAB signed exactly as the export screen does since v1.8.5:
     * BundleSigner (apksig, JAR signature with SHA-256 digests). Writes <root>/signed_aab/<name>.aab.
     */
    static void exportAab(String sc_id) throws Throwable {
        Class<?> wq = c("a.a.a.wq"), lC = c("a.a.a.lC");
        Object rcv = receiver();
        Object[] q = new Object[1];
        Object[] dp = new Object[1];
        Object[] m = new Object[4];
        step("yq", () -> q[0] = neu("a.a.a.yq", CTX, call(wq, "d", sc_id), call(lC, "b", sc_id)));
        step("prepare", () -> {
            call(c("mod.agus.jcoderz.lib.FileUtil"), "deleteFile", get(q[0], "projectMyscPath"));
            m[0] = neu("a.a.a.hC", sc_id);
            m[1] = neu("a.a.a.kC", sc_id);
            m[2] = neu("a.a.a.eC", sc_id);
            m[3] = neu("a.a.a.iC", sc_id);
            call(m[0], "i");
            call(m[1], "s");
            call(m[2], "g");
            call(m[2], "e");
            call(m[3], "i");
            call(q[0], "c", CTX);
            call(q[0], "a", CTX, call(wq, "e", "600"));
            call(q[0], "a");
            String res = (String) get(q[0], "resDirectoryPath");
            call(m[1], "b", res + File.separator + "drawable-xhdpi");
            call(m[1], "c", res + File.separator + "raw");
            call(m[1], "a", get(q[0], "assetsPath") + File.separator + "fonts");
        });
        step("generate", () -> call(q[0], "b", m[0], m[2], m[3], true));
        step("Dp", () -> {
            dp[0] = neu("a.a.a.Dp", rcv, CTX, q[0]);
            call(dp[0], "setBuildAppBundle", true);
        });
        step("aapt2", () -> call(dp[0], "maybeExtractAapt2"));
        step("built-in libraries", () -> call(dp[0], "getBuiltInLibrariesReady"));
        step("resources", () -> call(dp[0], "compileResources"));
        step("kotlin", () -> call(c("mod.hey.studios.compiler.kotlin.KotlinCompilerBridge"), "compileKotlinCodeIfPossible", rcv, dp[0]));
        step("java", () -> call(dp[0], "compileJavaCode"));
        step("stringfog", () -> call(neu("mod.hey.studios.project.stringfog.StringfogHandler", sc_id), "start", rcv, dp[0]));
        step("proguard", () -> call(neu("mod.hey.studios.project.proguard.ProguardHandler", sc_id), "start", rcv, dp[0]));
        step("dex", () -> call(dp[0], "createDexFilesFromClasses"));
        step("dex merge", () -> call(dp[0], "getDexFilesReady"));
        Object[] compiler = new Object[1];
        step("module", () -> {
            compiler[0] = neu("mod.jbk.build.compiler.bundle.AppBundleCompiler", dp[0]);
            call(compiler[0], "createModuleMainArchive");
        });
        step("bundletool", () -> call(compiler[0], "buildBundle"));
        String created = ((File) call(c("mod.jbk.build.compiler.bundle.AppBundleCompiler"), "getDefaultAppBundleOutputFile", q[0])).getAbsolutePath();
        File outDir = new File(System.getProperty("h.root"), "signed_aab");
        outDir.mkdirs();
        String signed = new File(outDir, new File(created).getName()).getAbsolutePath();
        step("sign", () -> call(c("com.sketchware.ai.project.BundleSigner"), "signWithTestKey", created, signed));
        OUT.println("WARNINGS " + call(c("com.sketchware.ai.project.BuildWarnings"), "count"));
        OUT.println(call(c("com.sketchware.ai.project.BuildWarnings"), "report"));
        OUT.println("AAB " + signed);
    }

    /**
     * ExportProjectActivity.exportSrc() minus the Activity and the zip: the project generated as an exported project
     * (AGP 8.12 Gradle files from v1.8.5, manifest without its package attribute), the user's own Java/resources/assets/
     * native libraries copied in. The files the zip leaves out (DebugActivity.java, SketchApplication.java unless the
     * user wrote one) are deleted. Prints the folder.
     */
    static void exportSource(String sc_id) throws Throwable {
        Class<?> wq = c("a.a.a.wq"), lC = c("a.a.a.lC");
        Object[] q = new Object[1];
        Object[] m = new Object[4];
        step("yq", () -> q[0] = neu("a.a.a.yq", CTX, call(wq, "d", sc_id), call(lC, "b", sc_id)));
        String mysc = (String) get(q[0], "projectMyscPath");
        step("export", () -> {
            call(c("mod.agus.jcoderz.lib.FileUtil"), "deleteFile", mysc);
            m[0] = neu("a.a.a.hC", sc_id);
            m[1] = neu("a.a.a.kC", sc_id);
            m[2] = neu("a.a.a.eC", sc_id);
            m[3] = neu("a.a.a.iC", sc_id);
            call(m[0], "i");
            call(m[1], "s");
            call(m[2], "g");
            call(m[2], "e");
            call(m[3], "i");
            call(q[0], "a", CTX, call(wq, "e", "600"));
            call(q[0], "b", m[0], m[2], m[3], true);
            call(c("com.sketchware.ai.project.GradleExport"), "stripPackageAttribute", new File((String) get(q[0], "androidManifestPath")));
            call(c("com.sketchware.ai.project.GradleExport"), "writeWidgetLibraries", new File(mysc, "app"), get(q[0], "N"));
            OUT.println("LOCAL LIBRARIES in app/libs: " + call(c("com.sketchware.ai.project.GradleExport"), "writeLocalLibraries", new File(mysc), sc_id));
            call(q[0], "a");
            String res = (String) get(q[0], "resDirectoryPath");
            call(m[1], "b", res + File.separator + "drawable-xhdpi");
            call(m[1], "c", res + File.separator + "raw");
            call(m[1], "a", get(q[0], "assetsPath") + File.separator + "fonts");
            call(q[0], "f");
            Object util = neu("mod.agus.jcoderz.lib.FilePathUtil");
            Class<?> fu = c("mod.agus.jcoderz.lib.FileUtil");
            File java = new File((String) call(util, "getPathJava", sc_id));
            File resources = new File((String) call(util, "getPathResource", sc_id));
            File assets = new File((String) call(util, "getPathAssets", sc_id));
            File natives = new File((String) call(util, "getPathNativelibs", sc_id));
            if (java.exists()) copyTree(java, new File(get(q[0], "javaFilesPath") + File.separator + get(q[0], "packageNameAsFolders")));
            if (resources.exists()) copyTree(resources, new File((String) get(q[0], "resDirectoryPath")));
            if (assets.exists()) copyTree(assets, new File((String) get(q[0], "assetsPath")));
            if (natives.exists()) copyTree(natives, new File((String) get(q[0], "generatedFilesPath"), "jniLibs"));
            boolean ownApplication = new File(java, "SketchApplication.java").exists();
            deleteNamed(new File(mysc), "DebugActivity.java");
            if (!ownApplication) deleteNamed(new File(mysc), "SketchApplication.java");
        });
        OUT.println("EXPORTED " + mysc);
    }

    static void copyTree(File from, File to) throws java.io.IOException {
        if (from.isDirectory()) {
            to.mkdirs();
            File[] children = from.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(to, child.getName()));
        } else {
            to.getParentFile().mkdirs();
            java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void deleteNamed(File dir, String name) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) deleteNamed(child, name);
            else if (child.getName().equals(name)) child.delete();
        }
    }

    /** The functional interface LibraryIntegrity.restore takes for progress text. */
    static String callbackType() {
        for (java.lang.reflect.Method m : c("com.sketchware.ai.project.LibraryIntegrity").getDeclaredMethods()) {
            if (m.getName().equals("restore") && m.getParameterCount() == 2) return m.getParameterTypes()[1].getName();
        }
        throw new IllegalStateException("LibraryIntegrity.restore(String, ?) not found");
    }
}
