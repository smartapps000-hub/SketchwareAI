package com.sketchware.ai.buildmap;

import com.google.gson.Gson;
import com.sketchware.ai.project.AutoHeal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What one Run build did, task by task: status, time, and for the task that failed how many
 * errors it reported and how many repair attempts were made. Kept in
 * mysc/&lt;sc_id&gt;/buildmap.json and added as text to a failed build's log.
 * <p>
 * Not recorded (yet): each task's input and output files, and cache hits; the build has no cache.
 */
public final class BuildMap {

    public static final String FILE_NAME = "buildmap.json";

    public static final String PREPARE = "Prepare project files";
    public static final String GENERATE = "Generate sources";
    public static final String LIBRARIES = "Libraries";
    public static final String RESOURCES = "Resources (AAPT2)";
    public static final String KOTLIN = "Kotlin compile";
    public static final String JAVA = "Java compile";
    public static final String STRINGFOG = "StringFog";
    public static final String PROGUARD = "ProGuard / R8";
    public static final String DEX = "DEX";
    public static final String DEX_MERGE = "DEX merge";
    public static final String PACKAGE = "Package APK";
    public static final String SIGN = "Sign APK";
    public static final String INSTALL = "Install";

    /** The order a build runs them in. */
    private static final String[] PLAN = {PREPARE, GENERATE, LIBRARIES, RESOURCES, KOTLIN, JAVA, STRINGFOG, PROGUARD,
            DEX, DEX_MERGE, PACKAGE, SIGN, INSTALL};

    public static final String OK = "ok";
    public static final String FAILED = "failed";
    public static final String RESTARTED = "restarted";
    public static final String NOT_REACHED = "not reached";
    public static final String RUNNING = "running";

    public static final class Task {
        public String name;
        public String status = NOT_REACHED;
        public long durationMs;
        public int errors;
        public int repairAttempts;
        transient long startedAt;
    }

    /** What is written to buildmap.json. */
    public static final class Record {
        public int version = 1;
        public String scId;
        public long startedAt;
        public long durationMs;
        /** "ok", "failed" or "restarted" (a missing library was enabled and the build began again). */
        public String result = RUNNING;
        public int warnings;
        public List<Task> tasks = new ArrayList<>();
    }

    private static Record current;
    private static File directory;

    private BuildMap() {
    }

    /** A build starts. */
    public static synchronized void begin(String scId, String projectMyscPath) {
        current = new Record();
        current.scId = scId;
        current.startedAt = System.currentTimeMillis();
        for (String name : PLAN) {
            Task task = new Task();
            task.name = name;
            current.tasks.add(task);
        }
        directory = new File(projectMyscPath);
    }

    /** The build moves on to the task {@code name}: the one before it is done. */
    public static synchronized void task(String name) {
        if (current == null) return;
        long now = System.currentTimeMillis();
        close(OK, now);
        Task next = null;
        for (Task task : current.tasks) {
            if (task.name.equals(name)) next = task;
        }
        if (next == null) {
            next = new Task();
            next.name = name;
            current.tasks.add(next);
        }
        next.status = RUNNING;
        next.startedAt = now;
    }

    private static void close(String status, long now) {
        for (Task task : current.tasks) {
            if (RUNNING.equals(task.status)) {
                task.status = status;
                task.durationMs += now - task.startedAt;
            }
        }
    }

    /** The build finished and the app is being installed. */
    public static synchronized void succeeded(int warnings) {
        finish(OK, OK, warnings, null);
    }

    /**
     * The build stopped in the task that is running.
     *
     * @param log what goes to the compile log
     */
    public static synchronized void failed(String log) {
        finish(FAILED, FAILED, 0, log);
    }

    /** The build enabled a missing library and starts again from the beginning. */
    public static synchronized void restarted() {
        finish(RESTARTED, RESTARTED, 0, null);
    }

    private static void finish(String result, String taskStatus, int warnings, String log) {
        if (current == null || !RUNNING.equals(current.result)) return;
        long now = System.currentTimeMillis();
        if (log != null) {
            for (Task task : current.tasks) {
                if (!RUNNING.equals(task.status)) continue;
                try {
                    task.errors = AutoHeal.parse(log).size();
                    Matcher attempts = ATTEMPT.matcher(log);
                    while (attempts.find()) task.repairAttempts++;
                } catch (RuntimeException e) {
                    // Counts stay at 0.
                }
            }
        }
        close(taskStatus, now);
        current.result = result;
        current.warnings = warnings;
        current.durationMs = now - current.startedAt;
        save();
    }

    private static final Pattern ATTEMPT = Pattern.compile("(?m)^Attempt \\d+:");

    private static void save() {
        try {
            if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) return;
            File temporary = new File(directory, FILE_NAME + ".tmp");
            File target = new File(directory, FILE_NAME);
            try (OutputStream out = new FileOutputStream(temporary)) {
                out.write(new Gson().toJson(current).getBytes(StandardCharsets.UTF_8));
            }
            if (!temporary.renameTo(target)) {
                target.delete();
                temporary.renameTo(target);
            }
        } catch (Exception e) {
            // The record is a convenience; the build's result doesn't depend on it.
        }
    }

    /** The section a failed build adds to its log, or "" before any build. */
    public static synchronized String report() {
        if (current == null) return "";
        StringBuilder text = new StringBuilder("Build map:\n");
        int width = 0;
        for (Task task : current.tasks) width = Math.max(width, task.name.length());
        for (Task task : current.tasks) {
            text.append("    ").append(pad(mark(task.status), 8)).append(pad(task.name, width + 2));
            if (!NOT_REACHED.equals(task.status)) text.append(seconds(task.durationMs));
            if (task.errors > 0) text.append("   ").append(task.errors).append(task.errors == 1 ? " error" : " errors");
            if (task.repairAttempts > 0) {
                text.append(", ").append(task.repairAttempts).append(task.repairAttempts == 1 ? " repair attempt" : " repair attempts");
            }
            text.append('\n');
        }
        text.append("    total ").append(seconds(current.durationMs));
        return text.toString();
    }

    private static String mark(String status) {
        if (OK.equals(status)) return "ok";
        if (FAILED.equals(status)) return "FAILED";
        if (NOT_REACHED.equals(status)) return "-";
        return status;
    }

    private static String pad(String text, int width) {
        StringBuilder out = new StringBuilder(text);
        while (out.length() < width) out.append(' ');
        return out.toString();
    }

    private static String seconds(long ms) {
        return String.format(Locale.US, "%.1f s", ms / 1000.0);
    }
}
