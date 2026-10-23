package mod.jbk.build.compiler.dex;

import android.annotation.TargetApi;
import android.os.Build;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringConsumer;

import mod.jbk.util.LogUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

import a.a.a.Dp;
import mod.agus.jcoderz.command.ProcessingFiles;
import mod.hey.studios.project.ProjectSettings;

public class DexCompiler {

    @TargetApi(Build.VERSION_CODES.O)
    public static void compileDexFiles(Dp compileHelper) throws CompilationFailedException {
        int minApiLevel;

        try {
            minApiLevel = Integer.parseInt(compileHelper.settings.getValue(
                    ProjectSettings.SETTING_MINIMUM_SDK_VERSION, "21"));
        } catch (NumberFormatException e) {
            throw failure("Invalid minSdkVersion specified in Project Settings", e);
        }

        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            throw new IllegalStateException("Can't use d8 as API level " + Build.VERSION.SDK_INT + " < 26");
        }

        Collection<Path> programFiles = new ArrayList<>();
        if (compileHelper.proguard.isProguardEnabled()) {
            programFiles.add(new File(compileHelper.yq.classesProGuardPath).toPath());
        } else {
            for (String filePath : ProcessingFiles.getListResource(compileHelper.yq.compiledClassesPath)) {
                programFiles.add(new File(filePath).toPath());
            }
        }

        Collection<Path> libraryFiles = new ArrayList<>();
        for (String jarPath : compileHelper.getClasspath().split(":")) {
            // D8 stops the whole build with NoSuchFileException at a path that doesn't exist.
            if (jarPath.trim().isEmpty() || !new File(jarPath).exists()) continue;
            libraryFiles.add(new File(jarPath).toPath());
        }

        File dexDirectory = new File(compileHelper.yq.binDirectoryPath, "dex");
        D8Command.Builder builder = d8(minApiLevel, libraryFiles, programFiles, dexDirectory);

        /* v1.8.5: core library desugaring (desugar_jdk_libs_nio 2.1.5): D8 rewrites java.time, streams, nio.file ...
         * to the j$ library where the min SDK lacks them and reports which j$ classes the code needs. */
        boolean desugarLibrary = com.sketchware.ai.project.CoreLibDesugaring.available();
        StringBuilder keepRules = new StringBuilder();
        if (desugarLibrary) {
            try {
                builder.addDesugaredLibraryConfiguration(com.sketchware.ai.project.CoreLibDesugaring.configuration());
                builder.setDesugaredLibraryKeepRuleConsumer(new StringConsumer() {
                    @Override
                    public void accept(String string, DiagnosticsHandler handler) {
                        keepRules.append(string);
                    }
                });
            } catch (java.io.IOException e) {
                desugarLibrary = false;
                LogUtil.e("DexCompiler", "Desugared library configuration unreadable, compiling without it", e);
            }
        }
        try {
            D8.run(builder.build());
        } catch (CompilationFailedException | RuntimeException e) {
            if (!desugarLibrary) throw e;
            /* D8 failed with the desugared library configuration: compiled again as before v1.8.5, without it. If
             * this fails too, the error is the project's own and is reported as such. */
            LogUtil.e("DexCompiler", "D8 with core library desugaring failed, compiling without it", e);
            deleteDexFiles(dexDirectory);
            D8.run(d8(minApiLevel, libraryFiles, programFiles, dexDirectory).build());
            withoutDesugaredLibrary(e);
            return;
        }

        if (desugarLibrary && !keepRules.toString().trim().isEmpty()) {
            try {
                File libraryDex = com.sketchware.ai.project.CoreLibDesugaring.libraryDex(minApiLevel, keepRules.toString(),
                        new File(compileHelper.androidJarPath));
                /* next to the project's DEX files: packaged (and merged when the build merges) like them */
                java.nio.file.Files.copy(libraryDex.toPath(), new File(dexDirectory, "desugar_jdk_libs.dex").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable e) {
                /* L8 failed (also out of memory): the project's DEX now refers to j$ classes that won't exist, so it is
                 * compiled again as before v1.8.5, without the desugared library. Java 8+ library APIs then need an
                 * Android version that has them. */
                LogUtil.e("DexCompiler", "Core library desugaring failed, compiling without it", e);
                deleteDexFiles(dexDirectory);
                D8.run(d8(minApiLevel, libraryFiles, programFiles, dexDirectory).build());
                withoutDesugaredLibrary(e);
            }
        }
    }

    /**
     * The build continues without the desugared library: says so, and has the next build extract
     * assets/libs/desugar.zip again (in case its files were damaged).
     */
    private static void withoutDesugaredLibrary(Throwable cause) {
        com.sketchware.ai.project.CoreLibDesugaring.reset();
        com.sketchware.ai.project.BuildWarnings.add("Core library desugaring (desugar_jdk_libs "
                + com.sketchware.ai.project.CoreLibDesugaring.VERSION + ") failed (" + cause
                + "), so this build has no desugared library: java.time, streams and other Java 8+ library APIs "
                + "work only on Android versions that have them (Android 8.0+ for java.time). The next build extracts "
                + "the library again and tries again.");
    }

    private static D8Command.Builder d8(int minApiLevel, Collection<Path> libraryFiles, Collection<Path> programFiles,
                                        File dexDirectory) {
        return D8Command.builder()
                .setMode(CompilationMode.RELEASE)
                .setIntermediate(true)
                .setMinApiLevel(minApiLevel)
                .addLibraryFiles(libraryFiles)
                .setOutput(dexDirectory.toPath(), OutputMode.DexIndexed)
                .addProgramFiles(programFiles);
    }

    private static void deleteDexFiles(File dexDirectory) {
        File[] files = dexDirectory.listFiles((dir, name) -> name.endsWith(".dex"));
        if (files != null) for (File file : files) file.delete();
    }

    /** R8 8.x has no public (message, cause) constructor. */
    private static CompilationFailedException failure(String message, Throwable cause) {
        CompilationFailedException exception = new CompilationFailedException(message);
        exception.initCause(cause);
        return exception;
    }
}
