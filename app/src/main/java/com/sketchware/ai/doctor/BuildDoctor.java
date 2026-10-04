package com.sketchware.ai.doctor;

import com.sketchware.ai.diagnostic.CompileLogDiagnostics;
import com.sketchware.ai.project.ResourceResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a build log the way the build ran: which step failed, what that step said, and what the
 * build had already repaired before it. The diagnosis is about the step that failed now, never
 * about a problem an earlier step already solved (those are listed as what the build changed).
 * Works on the log text alone, so a saved log is diagnosed the same way. Offline, no model.
 */
public final class BuildDoctor {

    /** The build succeeded. */
    public static final String OK = "OK";
    public static final String MISSING_RESOURCE = "MISSING_RESOURCE";
    public static final String RESOURCE_ERROR = "RESOURCE_ERROR";
    public static final String JAVA_ERROR = "JAVA_ERROR";
    public static final String MISSING_FILE = "MISSING_FILE";
    public static final String DUPLICATE_CLASS = "DUPLICATE_CLASS";
    public static final String UNSUPPORTED_BYTECODE = "UNSUPPORTED_BYTECODE";
    public static final String MISSING_CLASS = "MISSING_CLASS";
    public static final String DESUGAR_FAILURE = "DESUGAR_FAILURE";
    public static final String MIN_API = "MIN_API_TOO_LOW";
    public static final String CORRUPT_JAR = "CORRUPT_JAR";
    public static final String METHOD_LIMIT = "METHOD_LIMIT";
    public static final String OUT_OF_MEMORY = "OUT_OF_MEMORY";
    public static final String NO_SPACE = "NO_SPACE";
    public static final String UNKNOWN = "UNKNOWN";

    /** One step of the build, as the log's build map lists it. */
    public static final class Step {
        public String name;
        /** "ok", "FAILED", "-" (not reached) or "restarted". */
        public String status;
        public String time;
    }

    /** One thing the build changed by itself. */
    public static final class Change {
        /** The build's own sentence, without the "auto-fix: " in front. */
        public String text;
        /** TRUE: saved in the project; FALSE: only in this build's copy; null: the sentence doesn't say. */
        public Boolean permanent;
    }

    public static final class Diagnosis {
        public boolean succeeded;
        /** True when there is no log at all. */
        public boolean empty;
        /** The step that failed ("DEX"), or null. */
        public String stage;
        /** One of the constants of this class. */
        public String code = UNKNOWN;
        /** One line: what is wrong now. */
        public String title;
        /** The line of the log that says it ("home.xml:33 · resource drawable/logo not found"), or null. */
        public String firstError;
        /** What was found, in plain words. */
        public String found;
        /** Known-pattern advice for the failed step's own output. */
        public final List<String> advice = new ArrayList<>();
        public final List<Change> changes = new ArrayList<>();
        /** Warnings of the build that are not repairs. */
        public final List<String> warnings = new ArrayList<>();
        public final List<Step> steps = new ArrayList<>();
        /** "v1.8.2-abc (172)": the app version that made the log; null for logs of 1.8.1 and older. */
        public String builtWith;
        /** True when the same build can end differently without any change to the project (network, memory). */
        public boolean retryCanHelp;
        /** The failed step's own output: the log without the warnings and the build map. */
        public String failureText = "";
        /** For MISSING_RESOURCE: every missing resource is an image. */
        public boolean imagesOnly;
        /** A local library the failure names, or null. */
        public String library;
    }

    /** What the compile log screen shows when nothing was saved. */
    private static final String NO_LOG = "No compile errors have been saved yet.";
    private static final Pattern OUT_OF_MEMORY_LINE = Pattern.compile("(?m)^\\s*(?:Caused by: |Exception in thread .*)?java\\.lang\\.OutOfMemoryError");
    private static final Pattern NO_SPACE_LINE = Pattern.compile("(?m)^\\s*(?:Caused by: )?[\\w.$]*(?:IOException|ErrnoException|FileNotFoundException)\\b.*(?:ENOSPC|No space left on device)");

    private BuildDoctor() {
    }

    private static final Pattern STEP = Pattern.compile("(?m)^ {4}(ok|FAILED|-|restarted|running)\\s+(.+?)(?:\\s{2,}([\\d.]+ s.*))?\\s*$");
    private static final Pattern WARNING = Pattern.compile("(?ms)^\\d+\\. (warning: |auto-fix: )(.*?)(?=\\n\\n\\d+\\. |\\n\\nNotes for this target|\\n\\n\\nBuild map:|\\n\\nBuild map:|\\z)");
    private static final Pattern BUILT_WITH = Pattern.compile("(?m)^ {4}built with Sketchware AI (.+?) on ");
    private static final Pattern NO_SUCH_FILE = Pattern.compile("NoSuchFileException: (\\S+)");
    private static final Pattern LIBRARY_PATH = Pattern.compile("/local_libs/([^/\\s]+)/");
    private static final Pattern DUPLICATE = Pattern.compile(
            "Type ([\\w.$]+) is defined multiple times: ([^,\\n]+), ([^\\n]+)|Multiple dex files define L([\\w/$]+);|Duplicate class ([\\w.$]+)");
    private static final Pattern MAJOR = Pattern.compile("(?i)(?:unsupported class file (?:major )?version|class file version) (\\d{1,3})\\b");

    public static Diagnosis diagnose(String log) {
        Diagnosis d = new Diagnosis();
        if (log == null) log = "";
        String text = log.replace("\r\n", "\n");

        // The build's own section is the last one; the words can also occur in the user's code or a warning.
        int map = text.lastIndexOf("\nBuild map:\n");
        if (map >= 0) map++;
        else if (text.startsWith("Build map:\n")) map = 0;
        if (map >= 0) {
            Matcher step = STEP.matcher(text.substring(map));
            while (step.find()) {
                if (step.group(2).startsWith("total") || step.group(2).startsWith("built with")) continue;
                Step s = new Step();
                s.status = step.group(1);
                s.name = step.group(2).trim();
                s.time = step.group(3) == null ? "" : step.group(3).trim();
                d.steps.add(s);
                if ("FAILED".equals(s.status)) d.stage = s.name;
            }
            Matcher with = BUILT_WITH.matcher(text.substring(map));
            if (with.find()) d.builtWith = with.group(1).trim();
        }

        d.succeeded = text.startsWith("Build succeeded with");
        int appendix = map >= 0 ? text.lastIndexOf("\n\nBefore the build failed (", map) : text.lastIndexOf("\n\nBefore the build failed (");
        int end = appendix >= 0 ? appendix : (map >= 0 ? map : text.length());
        d.failureText = d.succeeded ? "" : text.substring(0, end).trim();

        // What the build repaired or warned about before it ended.
        String notes = d.succeeded ? text : (appendix >= 0 ? text.substring(appendix, map > appendix ? map : text.length()) : "");
        Matcher w = WARNING.matcher(notes);
        while (w.find()) {
            String body = w.group(2).trim().replaceAll("\\s*\\n\\s*", " ");
            if (w.group(1).startsWith("auto-fix")) {
                Change change = new Change();
                change.text = body;
                // The build ends each repair's sentence with its kind; text the build quotes from the
                // user's code comes before that, so the last marker decides.
                int permanent = body.lastIndexOf(" Permanent: ");
                int temporary = Math.max(body.lastIndexOf(" Temporary repair"), Math.max(body.lastIndexOf("your blocks are unchanged"),
                        body.lastIndexOf("your file was not changed")));
                if (permanent > temporary || permanent < 0 && temporary < 0 && (body.startsWith("enabled local library")
                        || body.startsWith("downloaded missing library") || body.startsWith("restored the files of local library")
                        || body.contains("had no classes.jar;") || body.contains("had no classes.dex;"))) {
                    change.permanent = Boolean.TRUE;
                } else if (temporary >= 0) {
                    change.permanent = Boolean.FALSE;
                }
                d.changes.add(change);
            } else {
                d.warnings.add(body);
            }
        }

        if (d.succeeded) {
            d.code = OK;
            d.title = d.changes.isEmpty() ? "The build succeeded." : "The build succeeded after " + d.changes.size()
                    + (d.changes.size() == 1 ? " repair." : " repairs.");
            d.found = d.warnings.isEmpty() ? null : d.warnings.size() + (d.warnings.size() == 1 ? " warning is" : " warnings are")
                    + " listed in the log.";
            return d;
        }
        if (text.trim().isEmpty() || text.trim().equals(NO_LOG)) {
            d.title = "There is no compile log to diagnose.";
            d.failureText = "";
            d.empty = true;
            return d;
        }

        CompileLogDiagnostics.Result known = CompileLogDiagnostics.analyze(d.failureText);
        d.firstError = known.firstError;
        if (d.firstError != null && d.firstError.startsWith("com.android.tools.r8.CompilationFailedException")) d.firstError = null;
        String stage = d.stage == null ? guessStage(d.failureText) : d.stage;
        if (d.stage == null) d.stage = stage;

        if (OUT_OF_MEMORY_LINE.matcher(d.failureText).find()) {
            d.code = OUT_OF_MEMORY;
            d.title = "The phone ran out of memory during " + stageWords(stage) + ".";
            d.found = "This depends on what else is running, not on the project. Close other apps and build again.";
            d.retryCanHelp = true;
        } else if (NO_SPACE_LINE.matcher(d.failureText).find()) {
            d.code = NO_SPACE;
            d.title = "The phone's storage is full.";
            d.found = "The build couldn't write a file. Free some storage and build again.";
            d.retryCanHelp = true;
        } else if ("Resources (AAPT2)".equals(stage)) {
            resources(d);
        } else if ("Java compile".equals(stage) || "Kotlin compile".equals(stage)) {
            d.code = JAVA_ERROR;
            int errors = count(d.failureText, "ERROR in ");
            d.title = errors > 0 ? errors + (errors == 1 ? " error" : " errors") + " in the project's code stopped the compiler."
                    : "The compiler stopped.";
            d.found = "Each error is listed below with the block it comes from. What the build could repair by itself, it "
                    + "already tried: the Auto-fix report at the end of the log says what and why it wasn't enough.";
        } else if ("DEX".equals(stage) || "DEX merge".equals(stage) || "ProGuard / R8".equals(stage)) {
            dex(d, stage);
        } else {
            d.code = UNKNOWN;
            d.title = stage == null ? "The build stopped." : "The build stopped during " + stageWords(stage) + ".";
            d.found = firstLine(d.failureText);
        }
        // Advice only for what the failed step itself said.
        for (String advice : known.advice) {
            if (d.found != null && advice.startsWith("Auto-fix report")) continue;
            // Said already, and more precisely, by the diagnosis of this step.
            if (MISSING_RESOURCE.equals(d.code) && advice.startsWith("A layout or setting refers")) continue;
            if (MISSING_FILE.equals(d.code) || DUPLICATE_CLASS.equals(d.code)) continue;
            d.advice.add(advice);
        }
        for (String warning : d.warnings) {
            if (warning.contains("couldn't be downloaded (no internet connection)") || warning.contains("no internet connection")) {
                d.retryCanHelp = true;
            }
        }
        if (d.firstError == null || d.firstError.isEmpty()) d.firstError = firstLine(d.failureText);
        return d;
    }

    private static void resources(Diagnosis d) {
        List<ResourceResolver.Missing> missing = ResourceResolver.parse(d.failureText);
        if (!missing.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (ResourceResolver.Missing item : missing) {
                if (!names.contains(item.type + "/" + item.name)) names.add(item.type + "/" + item.name);
            }
            d.code = MISSING_RESOURCE;
            d.title = names.size() == 1 ? "A screen uses " + names.get(0) + ", which is not in the project."
                    : names.size() + " resources that screens use are not in the project.";
            boolean images = true;
            for (ResourceResolver.Missing item : missing) images &= "drawable".equals(item.type);
            d.imagesOnly = images;
            d.found = images ? "The build looked for each one in the project's image folder, its saved SVG icons and this project's "
                    + "own backups, and put back what it found. The ones listed below are nowhere on this phone."
                    : "A layout names a resource (a colour, string, style or image) that the project doesn't define. Check "
                    + "the spelling in the widget's properties or in Inject; images can be added in Image Manager.";
            return;
        }
        d.code = RESOURCE_ERROR;
        d.title = "AAPT2 rejected a resource file.";
        d.found = d.firstError;
    }

    private static void dex(Diagnosis d, String stage) {
        String text = d.failureText;
        Matcher file = NO_SUCH_FILE.matcher(text);
        Matcher duplicate = DUPLICATE.matcher(text);
        Matcher major = MAJOR.matcher(text);
        if (file.find()) {
            d.code = MISSING_FILE;
            String path = file.group(1);
            d.firstError = "NoSuchFileException: " + path;
            Matcher library = LIBRARY_PATH.matcher(path);
            if (library.find()) {
                d.library = library.group(1);
                d.title = "Local library " + d.library + " is enabled, but its files are not on this phone.";
                d.found = "The dexer was given " + path + ", which doesn't exist. From v1.8.2 on, a library with missing "
                        + "files is restored if it can be identified (its folder, this project's backup, or its exact "
                        + "download), and otherwise left out of the build, so this can't stop the build any more.";
            } else {
                d.title = "The dexer was given a file that doesn't exist.";
                d.found = path + " is named in the project's classpath but isn't there. From v1.8.2 on, missing classpath "
                        + "entries are left out and reported as a warning.";
            }
        } else if (duplicate.find()) {
            d.code = DUPLICATE_CLASS;
            d.firstError = duplicate.group(0).length() > 300 ? duplicate.group(0).substring(0, 297) + "…" : duplicate.group(0);
            String name = duplicate.group(1) != null ? duplicate.group(1)
                    : duplicate.group(4) != null ? duplicate.group(4).replace('/', '.') : duplicate.group(5);
            d.title = "The class " + name + " is in the app twice.";
            d.found = duplicate.group(2) == null
                    ? "Two libraries (or a library and the project) both contain it. Switch off one of them in Local library manager."
                    : "It is in " + shortPath(duplicate.group(2)) + " and in " + shortPath(duplicate.group(3))
                    + ". An app can hold a class only once: switch off one of the two libraries in Local library manager.";
            Matcher library = LIBRARY_PATH.matcher(duplicate.group(0));
            if (library.find()) d.library = library.group(1);
        } else if (major.find()) {
            d.code = UNSUPPORTED_BYTECODE;
            int version = Integer.parseInt(major.group(1));
            d.title = "A library was compiled for Java " + (version - 44) + ", which this dexer can't read.";
            d.found = "Use an older release of that library. If the project is set to Dx in Build Settings, switch to "
                    + "D8: Dx reads Java 7 class files only.";
        } else if (text.contains("Cannot fit requested classes in a single dex file") || text.contains("not in [0, 0xffff]")
                || text.contains("Too many classes in --main-dex-list")) {
            d.code = METHOD_LIMIT;
            d.title = "The app has more than 65,536 methods or fields for one dex file.";
            d.found = "Set the minimum SDK to 21 or higher in Project settings (the app then gets several dex files), or "
                    + "switch off libraries the project doesn't use.";
        } else if (text.contains("ZipException") || text.contains("zip END header not found") || text.contains("invalid CEN header")
                || text.contains("error in opening zip file") || text.contains("EOFException")) {
            d.code = CORRUPT_JAR;
            Matcher library = LIBRARY_PATH.matcher(text);
            if (library.find()) d.library = library.group(1);
            d.title = d.library == null ? "A .jar the build uses is damaged." : "The files of local library " + d.library + " are damaged.";
            d.found = "The file can't be opened as an archive (cut off or corrupt, usually an interrupted download or copy). "
                    + "Download the library again in Local library manager.";
        } else if (Pattern.compile("only supported starting with Android [A-Z]|requires min-api|--min-api").matcher(text).find()) {
            d.code = MIN_API;
            d.title = "A library uses a Java feature that needs a higher minimum Android version.";
            d.found = firstLine(text) + " Raise the minimum SDK in Project settings to the level the message names.";
        } else if (text.toLowerCase(java.util.Locale.ROOT).contains("desugar")) {
            d.code = DESUGAR_FAILURE;
            d.title = "D8 couldn't convert newer Java code for older Android versions (desugaring).";
            d.found = firstLine(text);
        } else if (Pattern.compile("Missing class |Type `?[\\w.$]+`? was not found|Class not found").matcher(text).find()) {
            d.code = MISSING_CLASS;
            d.title = "A class that the code or a library needs is not part of the build.";
            d.found = firstLine(text) + " Enable the library that contains it.";
        } else {
            d.code = UNKNOWN;
            d.title = "The step \"" + stage + "\" failed with an error this version doesn't recognise.";
            d.found = firstLine(text) + " Send the log with Copy log.";
        }
    }

    /** For logs without a build map (older versions, or a crash before it was written). */
    private static String guessStage(String failure) {
        if (failure.contains("ERROR in ") && failure.contains(".java")) return "Java compile";
        if (failure.contains("aapt2") || Pattern.compile("\\.xml:\\d+: error").matcher(failure).find()
                || failure.contains("error: resource ")) return "Resources (AAPT2)";
        if (failure.contains("com.android.tools.r8") || failure.contains("mod.agus.jcoderz.dx")) return "DEX";
        return null;
    }

    private static String stageWords(String stage) {
        return stage == null ? "the build" : "the step \"" + stage + "\"";
    }

    private static String shortPath(String path) {
        String p = path.trim();
        Matcher library = LIBRARY_PATH.matcher(p);
        if (library.find()) return "local library " + library.group(1);
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) n++;
        return n;
    }

    /** The first line that says something: an exception's message rather than a stack frame. */
    static String firstLine(String text) {
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("at ") || l.startsWith("...")) continue;
            return l.length() > 300 ? l.substring(0, 297) + "…" : l;
        }
        return "";
    }
}
