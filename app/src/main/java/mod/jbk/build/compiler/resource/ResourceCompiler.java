package mod.jbk.build.compiler.resource;

import android.content.Context;
import android.content.pm.PackageManager;

import com.besome.sketch.SketchApplication;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import a.a.a.Dp;
import a.a.a.Jp;
import a.a.a.zy;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.agus.jcoderz.lib.BinaryExecutor;
import mod.agus.jcoderz.lib.FileUtil;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.diagnostic.MissingFileException;
import mod.jbk.util.LogUtil;

/**
 * A class responsible for compiling a Project's resources.
 * Supports AAPT2.
 */
public class ResourceCompiler {

    /**
     * About log tags: add ":" and the first letter of the function's name camelCase'd.
     * For example, in thisIsALongFunctionName, you should use this:
     * <pre>
     *     TAG + ":tIALFN"
     * </pre>
     */
    private static final String TAG = "AppBuilder";
    private final boolean willBuildAppBundle;
    private final File aaptFile;
    private final BuildProgressReceiver progressReceiver;
    private final Dp dp;

    public ResourceCompiler(Dp dp, File aapt, boolean willBuildAppBundle, BuildProgressReceiver receiver) {
        this.willBuildAppBundle = willBuildAppBundle;
        aaptFile = aapt;
        progressReceiver = receiver;
        this.dp = dp;
    }

    public void compile() throws IOException, zy, MissingFileException {
        Compiler resourceCompiler;
        resourceCompiler = new Aapt2Compiler(dp, aaptFile, willBuildAppBundle);

        resourceCompiler.setProgressListener(new Compiler.ProgressListener() {
            @Override
            void onProgressUpdate(String newProgress) {
                if (progressReceiver != null) progressReceiver.onProgress(newProgress);
            }
        });
        resourceCompiler.compile();
    }

    /**
     * A base class of a resource compiler.
     */
    interface Compiler {

        /**
         * Compile a project's resources fully.
         */
        void compile() throws zy, MissingFileException;

        /**
         * Set a progress listener to compiling.
         *
         * @param listener The listener object
         */
        void setProgressListener(ProgressListener listener);

        /**
         * A listener for progress on compilation.
         */
        abstract class ProgressListener {
            /**
             * The compiler has reached a new phase the user should know about.
             *
             * @param newProgress A String provided by the resource compiler the user should see.
             */
            abstract void onProgressUpdate(String newProgress);
        }
    }

    /**
     * A {@link Compiler} implementing AAPT2.
     */
    static class Aapt2Compiler implements Compiler {

        private final boolean buildAppBundle;

        private final File aapt2;
        private final Dp buildHelper;
        private final File compiledBuiltInLibraryResourcesDirectory;
        private ProgressListener progressListener;

        public Aapt2Compiler(Dp buildHelper, File aapt2, boolean buildAppBundle) {
            this.buildHelper = buildHelper;
            this.aapt2 = aapt2;
            this.buildAppBundle = buildAppBundle;
            compiledBuiltInLibraryResourcesDirectory = new File(SketchApplication.getContext().getCacheDir(), "compiledLibs");
        }

        @Override
        public void compile() throws zy, MissingFileException {
            String outputPath = buildHelper.yq.binDirectoryPath + File.separator + "res";
            emptyOrCreateDirectory(outputPath);

            long savedTimeMillis = System.currentTimeMillis();
            if (progressListener != null) {
                progressListener.onProgressUpdate("Compiling resources with AAPT2...");
            }
            compileBuiltInLibraryResources();
            LogUtil.d(TAG + ":c", "Compiling built-in library resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            savedTimeMillis = System.currentTimeMillis();
            compileLocalLibraryResources(outputPath);
            LogUtil.d(TAG + ":c", "Compiling local library resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            savedTimeMillis = System.currentTimeMillis();
            compileProjectResources(outputPath);
            LogUtil.d(TAG + ":c", "Compiling project generated resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            savedTimeMillis = System.currentTimeMillis();
            compileImportedResources(outputPath);
            LogUtil.d(TAG + ":c", "Compiling project imported resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");

            savedTimeMillis = System.currentTimeMillis();
            File projectMysc = new File(buildHelper.yq.projectMyscPath);
            try {
                link();
                com.sketchware.ai.project.ResourceResolver.save(projectMysc, null);
            } catch (zy failure) {
                // A layout names an image that isn't in the build: put the project's own file back
                // (image folder, saved SVG icon, the project's backups) and link once more.
                com.sketchware.ai.project.ResourceResolver.Outcome outcome = null;
                try {
                    outcome = com.sketchware.ai.project.ProjectImages.resolve(buildHelper.yq.sc_id,
                            new File(buildHelper.yq.resDirectoryPath), failure.getMessage());
                } catch (Throwable t) {
                    LogUtil.e(TAG, "Resource resolver failed", t);
                }
                if (outcome == null || outcome.items.isEmpty()) throw failure;
                // What the project doesn't have anywhere gets an empty stand-in in this build only, so the
                // app can be built and run; the log and the Build Doctor list each one until the image is added.
                java.util.List<String> standIns = com.sketchware.ai.project.ResourceResolver.placeholders(outcome,
                        new File(buildHelper.yq.resDirectoryPath));
                com.sketchware.ai.project.ResourceResolver.save(projectMysc, outcome);
                if (outcome.restored == 0 && standIns.isEmpty()) throw new zy(failure.getMessage() + outcome.report());
                if (progressListener != null) {
                    progressListener.onProgressUpdate("Putting back missing images (" + outcome.restored + " found, "
                            + standIns.size() + " stand-ins)...");
                }
                compileProjectResources(outputPath);
                try {
                    link();
                } catch (zy second) {
                    StringBuilder log = new StringBuilder(second.getMessage()).append(outcome.report());
                    if (!outcome.notes.isEmpty()) log.append("\n\nPut back before this link:");
                    for (String note : outcome.notes) log.append("\n    ").append(note.replaceFirst("^auto-fix: ", ""));
                    throw new zy(log.toString());
                }
                // The link passed: only now are the repairs reported as done.
                for (String note : outcome.notes) com.sketchware.ai.project.BuildWarnings.add(note);
                if (!standIns.isEmpty()) {
                    StringBuilder names = new StringBuilder();
                    for (com.sketchware.ai.project.ResourceResolver.Missing item : outcome.items) {
                        if (item.restored || !standIns.contains(item.name) || names.indexOf(item.name + " (") >= 0) continue;
                        if (names.length() > 0) names.append(", ");
                        names.append(item.name).append(" (").append(item.file)
                                .append(item.viewId == null ? "" : ", " + item.viewId).append(")");
                    }
                    com.sketchware.ai.project.BuildWarnings.add("auto-fix: " + standIns.size() + (standIns.size() == 1
                            ? " image that a screen uses is" : " images that screens use are") + " not in the project, its saved "
                            + "icons or its backups: " + names + ". An empty stand-in was used for each so the app could be "
                            + "built; those widgets show nothing. Temporary repair: redone at every build. Add each image "
                            + "in Image Manager under that name, or remove the references in the Build Doctor.");
                }
            }
            LogUtil.d(TAG + ":c", "Linking resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        }

        /**
         * Links the project's resources using AAPT2.
         *
         * @throws zy Thrown to be caught by DesignActivity to show an error Snackbar.
         */
        public void link() throws zy, MissingFileException {
            String resourcesPath = buildHelper.yq.binDirectoryPath + File.separator + "res";
            if (progressListener != null)
                progressListener.onProgressUpdate("Linking resources with AAPT2...");

            ArrayList<String> args = new ArrayList<>();
            args.add(aapt2.getAbsolutePath());
            args.add("link");
            if (buildAppBundle) {
                args.add("--proto-format");
            }
            args.add("--allow-reserved-package-id");
            args.add("--auto-add-overlay");
            args.add("--no-version-vectors");
            args.add("--no-version-transitions");

            args.add("--min-sdk-version");
            args.add(String.valueOf(buildHelper.settings.getMinSdkVersion()));
            args.add("--target-sdk-version");
            args.add(buildHelper.settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION,
                    "28"));

            args.add("--version-code");
            String versionCode = buildHelper.yq.versionCode;
            args.add((versionCode == null || versionCode.isEmpty()) ? "1" : versionCode);
            args.add("--version-name");
            String versionName = buildHelper.yq.versionName;
            args.add((versionName == null || versionName.isEmpty()) ? "1.0" : versionName);

            args.add("-I");
            // The custom android.jar of Build Settings if that file exists, otherwise the bundled one
            // (a.a.a.Dp decides and says so in the build's warnings).
            linkingAssertFileExists(buildHelper.androidJarPath);
            args.add(buildHelper.androidJarPath);

            /* Add assets imported by vanilla method */
            linkingAssertDirectoryExists(buildHelper.yq.assetsPath);
            args.add("-A");
            args.add(buildHelper.yq.assetsPath);

            /* Add imported assets */
            String importedAssetsPath = buildHelper.fpu.getPathAssets(buildHelper.yq.sc_id);
            if (FileUtil.isExistFile(importedAssetsPath)) {
                args.add("-A");
                args.add(importedAssetsPath);
            }

            /* Add built-in libraries' assets */
            for (Jp library : buildHelper.builtInLibraryManager.a()) {
                if (library.d()) {
                    String assetsPath = BuiltInLibraries.getLibraryAssetsPath(library.a());

                    linkingAssertDirectoryExists(assetsPath);
                    args.add("-A");
                    args.add(assetsPath);
                }
            }

            /* Add local libraries' assets */
            for (String localLibraryAssetsDirectory : buildHelper.mll.getAssets()) {
                ensureLocalLibraryDirectory(localLibraryAssetsDirectory, MissingFileException.STEP_RESOURCE_LINKING);
                args.add("-A");
                args.add(localLibraryAssetsDirectory);
            }

            /* Include compiled built-in library resources */
            for (Jp library : buildHelper.builtInLibraryManager.a()) {
                if (library.c()) {
                    args.add("-R");
                    args.add(new File(compiledBuiltInLibraryResourcesDirectory, library.a() + ".zip").getAbsolutePath());
                }
            }

            /* Include compiled local libraries' resources */
            File[] filesInCompiledResourcesPath = new File(resourcesPath).listFiles();
            if (filesInCompiledResourcesPath != null) {
                for (File file : filesInCompiledResourcesPath) {
                    if (file.isFile()) {
                        if (!file.getName().equals("project.zip") && !file.getName().equals("project-imported.zip")) {
                            args.add("-R");
                            args.add(file.getAbsolutePath());
                        }
                    }
                }
            }

            /* Include compiled project resources */
            File projectArchive = new File(resourcesPath, "project.zip");
            if (projectArchive.exists()) {
                args.add("-R");
                args.add(projectArchive.getAbsolutePath());
            }

            /* Include compiled imported project resources */
            File projectImportedArchive = new File(resourcesPath, "project-imported.zip");
            if (projectImportedArchive.exists()) {
                args.add("-R");
                args.add(projectImportedArchive.getAbsolutePath());
            }

            /* Add R.java */
            linkingAssertDirectoryExists(buildHelper.yq.rJavaDirectoryPath);
            args.add("--java");
            args.add(buildHelper.yq.rJavaDirectoryPath);

            /* Output AAPT2's generated ProGuard rules to a.a.a.yq.aapt_rules */
            args.add("--proguard");
            args.add(buildHelper.yq.aaptProGuardRules);

            /* Add AndroidManifest.xml */
            linkingAssertFileExists(buildHelper.yq.androidManifestPath);
            args.add("--manifest");
            args.add(buildHelper.yq.androidManifestPath);

            /* Use the generated R.java for used libraries */
            String extraPackages = buildHelper.getLibraryPackageNames();
            if (!extraPackages.isEmpty()) {
                args.add("--extra-packages");
                args.add(extraPackages);
            }

            /* Output the APK only with resources to a.a.a.yq.C */
            args.add("-o");
            args.add(buildHelper.yq.resourcesApkPath);

            LogUtil.d(TAG + ":l", args.toString());
            BinaryExecutor executor = new BinaryExecutor();
            executor.setCommands(args);
            if (!executor.execute().isEmpty()) {
                LogUtil.e(TAG + ":l", executor.getLog());
                throw new zy(executor.getLog());
            }
        }

        private void compileProjectResources(String outputPath) throws zy, MissingFileException {
            compilingAssertDirectoryExists(buildHelper.yq.resDirectoryPath);

            // A damaged project image that can be repaired is repaired in the project too.
            com.sketchware.ai.project.PngSanitizer.keeper(
                    com.sketchware.ai.project.ProjectImages.keeper(buildHelper.yq.sc_id));
            try {
                compileDirectory(buildHelper.yq.resDirectoryPath, outputPath + File.separator + "project.zip", true, "project");
            } finally {
                com.sketchware.ai.project.PngSanitizer.keeper(null);
            }
        }

        /**
         * Runs "aapt2 compile --dir". Damaged PNGs are repaired first (a cut-off PNG makes AAPT2
         * crash with exit code 139): in place when the folder is the build's own copy, otherwise
         * in a copy of the folder under bin/res-fixed. If AAPT2 still rejects a PNG, that image is
         * repaired and the folder compiled once more.
         */
        private void compileDirectory(String directory, String outputArchive, boolean ownedByBuild, String mirrorName) throws zy {
            java.util.List<String> notes = new ArrayList<>();
            File source = new File(directory);
            File mirror = new File(buildHelper.yq.binDirectoryPath, "res-fixed" + File.separator + mirrorName);
            File use = source;
            try {
                if (ownedByBuild) {
                    notes.addAll(com.sketchware.ai.project.PngSanitizer.fixTree(source));
                } else {
                    use = com.sketchware.ai.project.PngSanitizer.usable(source, mirror, notes);
                }
            } catch (RuntimeException e) {
                LogUtil.e(TAG, "PNG check failed", e);
            }
            String log = runCompile(use, outputArchive);
            if (!log.isEmpty()) {
                File again = null;
                try {
                    again = com.sketchware.ai.project.PngSanitizer.afterFailure(log, use,
                            ownedByBuild || use != source, mirror, notes);
                } catch (RuntimeException e) {
                    LogUtil.e(TAG, "PNG repair failed", e);
                }
                if (again != null) log = runCompile(again, outputArchive);
            }
            for (String note : notes) com.sketchware.ai.project.BuildWarnings.add(note);
            if (!log.isEmpty()) {
                LogUtil.e(TAG, log);
                throw new zy(log);
            }
        }

        /** @return "" on success, otherwise AAPT2's output */
        private String runCompile(File directory, String outputArchive) {
            ArrayList<String> commands = new ArrayList<>();
            commands.add(aapt2.getAbsolutePath());
            commands.add("compile");
            commands.add("--dir");
            commands.add(directory.getAbsolutePath());
            commands.add("-o");
            commands.add(outputArchive);
            LogUtil.d(TAG + ":cD", "Now executing: " + commands);
            BinaryExecutor executor = new BinaryExecutor();
            executor.setCommands(commands);
            return executor.execute().isEmpty() ? "" : executor.getLog();
        }

        private void emptyOrCreateDirectory(String path) {
            if (FileUtil.isExistFile(path)) {
                FileUtil.deleteFile(path);
            }
            FileUtil.makeDir(path);
        }

        private void compileLocalLibraryResources(String outputPath) throws zy, MissingFileException {
            int localLibrariesCount = buildHelper.mll.getResLocalLibrary().size();
            LogUtil.d(TAG + ":cLLR", "About to compile " + localLibrariesCount
                    + " local " + (localLibrariesCount == 1 ? "library" : "libraries"));
            for (String localLibraryResDirectory : buildHelper.mll.getResLocalLibrary()) {
                File localLibraryDirectory = new File(localLibraryResDirectory).getParentFile();
                if (localLibraryDirectory != null) {
                    ensureLocalLibraryDirectory(localLibraryResDirectory, MissingFileException.STEP_RESOURCE_COMPILING);

                    compileDirectory(localLibraryResDirectory,
                            outputPath + File.separator + localLibraryDirectory.getName() + ".zip",
                            false, "lib-" + localLibraryDirectory.getName());
                }
            }
        }

        private void compileBuiltInLibraryResources() throws zy, MissingFileException {
            compiledBuiltInLibraryResourcesDirectory.mkdirs();
            for (Jp builtInLibrary : buildHelper.builtInLibraryManager.a()) {
                if (builtInLibrary.c()) {
                    File cachedCompiledResources = new File(compiledBuiltInLibraryResourcesDirectory, builtInLibrary.a() + ".zip");
                    String libraryResources = BuiltInLibraries.getLibraryResourcesPath(builtInLibrary.a());

                    compilingAssertDirectoryExists(libraryResources);

                    if (isBuiltInLibraryRecompilingNeeded(cachedCompiledResources)) {
                        ArrayList<String> commands = new ArrayList<>();
                        commands.add(aapt2.getAbsolutePath());
                        commands.add("compile");
                        commands.add("--dir");
                        commands.add(libraryResources);
                        commands.add("-o");
                        commands.add(cachedCompiledResources.getAbsolutePath());

                        LogUtil.d(TAG + ":cBILR", "Now executing: " + commands);
                        BinaryExecutor executor = new BinaryExecutor();
                        executor.setCommands(commands);
                        if (!executor.execute().isEmpty()) {
                            LogUtil.e(TAG + ":cBILR", executor.getLog());
                            throw new zy(executor.getLog());
                        }
                    } else {
                        LogUtil.d(TAG + ":cBILR", "Skipped resource recompilation for built-in library " + builtInLibrary.a());
                    }
                }
            }
        }

        private boolean isBuiltInLibraryRecompilingNeeded(File cachedCompiledResources) {
            if (cachedCompiledResources.exists()) {
                try {
                    Context context = SketchApplication.getContext();
                    return context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                            .lastUpdateTime > cachedCompiledResources.lastModified();
                } catch (PackageManager.NameNotFoundException e) {
                    LogUtil.e(TAG + ":iBILRN", "Couldn't get package info about ourselves: " + e.getMessage(), e);
                }
            } else {
                LogUtil.d(TAG + ":iBILRN", "File " + cachedCompiledResources.getAbsolutePath()
                        + " doesn't exist, forcing compilation");
            }
            return true;
        }

        private void compileImportedResources(String outputPath) throws zy {
            if (FileUtil.isExistFile(buildHelper.fpu.getPathResource(buildHelper.yq.sc_id))
                    && new File(buildHelper.fpu.getPathResource(buildHelper.yq.sc_id)).length() != 0) {
                compileDirectory(buildHelper.fpu.getPathResource(buildHelper.yq.sc_id),
                        outputPath + File.separator + "project-imported.zip", false, "imported");
            }
        }

        /**
         * A local library without res/ or assets/ (plain-jar libraries, or ones copied by hand) is
         * normal: create the empty folder once, right here, and keep building. aapt2 compiles an
         * empty res/ folder to an empty archive. Only if the folder can't be created does the
         * build stop with the "Missing directory" dialog - no rebuild loop.
         */
        private void ensureLocalLibraryDirectory(String directoryPath, int step) throws MissingFileException {
            File directory = new File(directoryPath);
            if (directory.isDirectory()) return;
            if (!directory.mkdirs() && !directory.isDirectory()) {
                throw new MissingFileException(directory, step, true);
            }
            LogUtil.d(TAG, "Created missing local library folder " + directory);
        }

        private void compilingAssertDirectoryExists(String directoryPath) throws MissingFileException {
            File directory = new File(directoryPath);
            if (!directory.exists()) {
                throw new MissingFileException(directory, MissingFileException.STEP_RESOURCE_COMPILING, true);
            }
        }

        public void linkingAssertFileExists(String filePath) throws MissingFileException {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new MissingFileException(file, MissingFileException.STEP_RESOURCE_LINKING, false);
            }
        }

        public void linkingAssertDirectoryExists(String filePath) throws MissingFileException {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new MissingFileException(file, MissingFileException.STEP_RESOURCE_LINKING, true);
            }
        }

        @Override
        public void setProgressListener(ProgressListener listener) {
            progressListener = listener;
        }
    }
}
