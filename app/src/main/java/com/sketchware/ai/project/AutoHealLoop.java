package com.sketchware.ai.project;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The closed loop around {@link AutoHeal}: fix what the compiler log calls for, compile again,
 * repeat while that makes progress. Unreachable code is first kept (the statement in front
 * becomes {@code if (true) { ... }}); where that leaves an error in the same method (one that
 * must return a value), the files are restored and the dead code is removed there instead.
 * Whatever happens ends up in
 * {@link Result#report}, so a build that still fails says what was tried.
 */
public final class AutoHealLoop {

    /** Printed with every report, so a log shows which engine wrote it. */
    public static final String VERSION = "engine 3";

    private static final int MAX_PASSES = 8;

    public interface Compiler {
        /** Compiles the project's sources as they are on disk. @return null if they compile, else the error log */
        String compile();
    }

    public static final class Result {
        public boolean compiled;
        /** What was fixed, when {@link #compiled}. */
        public final List<String> fixes = new ArrayList<>();
        /** What was tried and why it wasn't enough, when not {@link #compiled}. */
        public final List<String> report = new ArrayList<>();
    }

    private AutoHealLoop() {
    }

    public static Result run(File generatedJavaRoot, String classpath, String errors, Compiler compiler) {
        Result result = new Result();
        ClassIndex index = new ClassIndex(classpath);
        // Dead code is kept (wrapped) by default; where that leaves an error in the same method,
        // the next attempt removes it at that place instead.
        Set<String> removeSites = new HashSet<>();
        for (int attempt = 1; attempt <= 6; attempt++) {
            AutoHeal heal = new AutoHeal(generatedJavaRoot, AutoHeal.Mode.KEEP, index, removeSites);
            String log = errors;
            Set<String> seen = new HashSet<>();
            seen.add(signature(log));
            String stopped = null;
            try {
                for (int pass = 0; pass < MAX_PASSES; pass++) {
                    int made = heal.apply(log);
                    if (made == 0) {
                        stopped = heal.fixes().isEmpty()
                                ? "no fix applies to: " + firstError(log)
                                : heal.fixes().size() + " fix(es) made, then the compiler reported: " + firstError(log);
                        break;
                    }
                    String next = compiler.compile();
                    if (next == null) {
                        result.compiled = true;
                        result.report.clear();
                        result.fixes.addAll(heal.fixes());
                        return result;
                    }
                    log = next;
                    if (!seen.add(signature(next))) {
                        stopped = heal.fixes().size() + " fix(es) made, but the same errors came back: " + firstError(next);
                        break;
                    }
                }
                if (stopped == null) stopped = "stopped after " + MAX_PASSES + " rounds; remaining: " + firstError(log);
            } catch (RuntimeException e) {
                stopped = "failed with " + e;
            }
            result.report.add("Attempt " + attempt + ": " + stopped);
            int listed = 0;
            for (String fix : heal.fixes()) {
                if (listed++ == 12) {
                    result.report.add("    ... and " + (heal.fixes().size() - 12) + " more");
                    break;
                }
                result.report.add("    tried: " + fix);
            }
            for (String note : heal.notes()) result.report.add("    " + note);
            Set<String> failed = heal.wrapsNear(log);
            heal.rollback();
            if (failed.isEmpty() || !removeSites.addAll(failed)) break;
        }
        result.report.add("The generated files were restored. Nothing in your project was changed.");
        return result;
    }

    /** The errors of a log without line numbers shifting: file, line and message of each. */
    private static String signature(String log) {
        StringBuilder b = new StringBuilder();
        for (String line : log.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("^") || t.startsWith("-----")) continue;
            if (t.contains("ERROR in ")) b.append('|').append(t.substring(t.indexOf("ERROR in ")));
        }
        String[] lines = log.split("\n");
        for (int i = 0; i + 1 < lines.length; i++) {
            if (lines[i + 1].trim().startsWith("-----") && !lines[i].trim().isEmpty()) b.append('|').append(lines[i].trim());
        }
        return b.toString();
    }

    private static String firstError(String log) {
        String[] lines = log.split("\n");
        for (int i = 0; i < lines.length; i++) {
            int at = lines[i].indexOf("ERROR in ");
            if (at < 0) continue;
            String header = lines[i].substring(at + 9).trim();
            String name = header.replaceAll("^.*/", "").replace(" (at line ", ":").replace(")", "");
            for (int j = i + 1; j < lines.length; j++) {
                if (lines[j].trim().startsWith("-----")) {
                    return name + " " + lines[j - 1].trim();
                }
            }
            return name;
        }
        return "(no error message)";
    }
}
