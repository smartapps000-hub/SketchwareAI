package com.sketchware.ai.project;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-heal for Java compile errors: small, deterministic, offline fixes applied to the GENERATED
 * source of the current build (mysc/&lt;id&gt;/app/src/main/java), followed by a recompile. See
 * {@link AutoHealLoop} for the loop; this class makes one pass over one compiler log.
 * <p>
 * Safety: the project's blocks, MoreBlocks and custom Java files are never touched. The generated
 * files are rewritten from the blocks on every build, so a fix can't corrupt the project; it is
 * applied again on the next build until the user changes the block. Every changed file is kept
 * in memory and restored by {@link #rollback()} when the result doesn't compile.
 * <p>
 * Fixes, all of which keep what the program does:
 * <ul>
 *     <li>Unreachable code. {@link Mode#KEEP}: the statement in front of it (a return, a
 *     try/catch that returns on every path, ...) becomes {@code if (true) { ... }}, which the
 *     language rules (JLS 14.22) treat as able to continue, so the code behind it compiles; it
 *     still never runs. {@link Mode#REMOVE}: the dead statements are taken out up to the end of
 *     their block; this is what a method that must return a value needs.</li>
 *     <li>A class used without its import: the import is added.</li>
 *     <li>A catch block for an exception the try block can't throw: the catch is taken out.</li>
 *     <li>A missing ';' where the compiler names the exact place.</li>
 *     <li>A number assigned to a smaller number type (double to int ...): the cast is added.</li>
 * </ul>
 */
public final class AutoHeal {

    public enum Mode {KEEP, REMOVE}

    private static final Pattern HEADER = Pattern.compile("ERROR in (.+?\\.java) \\(at line (\\d+)\\)");
    private static final Pattern UNRESOLVED = Pattern.compile(
            "^([A-Z]\\w*) cannot be resolved(?: to a (?:type|variable))?\\s*$");
    private static final Pattern SEMICOLON = Pattern.compile("^Syntax error, insert \";\" to complete \\w+\\s*$");
    private static final Pattern NARROWING = Pattern.compile(
            "^Type mismatch: cannot convert from (double|float|long|int) to (int|long|float|short|byte|char)\\s*$");
    private static final List<String> NUMBER_WIDTH = java.util.Arrays.asList("byte", "short", "char", "int", "long", "float", "double");

    /** Classes custom code commonly uses that generated activities don't import by default. */
    private static final Map<String, String> IMPORTS = new HashMap<>();

    static {
        String[] names = {
                "android.Manifest", "android.content.pm.PackageManager", "android.content.pm.PackageInfo",
                "android.content.pm.ApplicationInfo", "android.content.pm.ActivityInfo", "android.net.Uri",
                "android.net.ConnectivityManager", "android.net.NetworkInfo", "android.net.NetworkCapabilities",
                "android.net.wifi.WifiManager", "android.provider.Settings", "android.provider.MediaStore",
                "android.provider.DocumentsContract", "android.provider.OpenableColumns",
                "android.util.Log", "android.util.Base64", "android.util.TypedValue", "android.util.DisplayMetrics",
                "android.util.SparseArray", "android.util.SparseBooleanArray", "android.text.TextUtils",
                "android.text.Html", "android.text.TextWatcher", "android.text.Editable", "android.text.InputType",
                "android.text.SpannableString", "android.text.Spanned",
                "android.bluetooth.BluetoothAdapter", "android.bluetooth.BluetoothDevice",
                "android.bluetooth.BluetoothSocket", "android.bluetooth.BluetoothManager",
                "android.location.Location", "android.location.LocationManager", "android.location.LocationListener",
                "android.location.Geocoder", "android.location.Address",
                "android.media.MediaPlayer", "android.media.AudioManager", "android.media.RingtoneManager",
                "android.media.MediaScannerConnection", "android.webkit.WebView", "android.webkit.WebViewClient",
                "android.webkit.WebChromeClient", "android.webkit.WebSettings", "android.webkit.MimeTypeMap",
                "android.webkit.ValueCallback", "android.webkit.CookieManager",
                "android.print.PrintManager", "android.print.PrintAttributes", "android.print.PrintDocumentAdapter",
                "android.database.Cursor", "android.database.sqlite.SQLiteDatabase",
                "android.database.sqlite.SQLiteOpenHelper", "android.animation.ObjectAnimator",
                "android.animation.ValueAnimator", "android.animation.AnimatorSet", "android.animation.Animator",
                "android.view.inputmethod.InputMethodManager", "android.view.animation.Animation",
                "android.view.animation.AnimationUtils", "android.telephony.TelephonyManager",
                "android.telephony.SmsManager", "android.speech.tts.TextToSpeech",
                "android.speech.RecognizerIntent", "android.hardware.SensorManager", "android.hardware.Sensor",
                "android.hardware.SensorEvent", "android.hardware.SensorEventListener",
                "android.graphics.drawable.Drawable", "android.graphics.drawable.GradientDrawable",
                "android.graphics.drawable.ColorDrawable", "android.graphics.drawable.BitmapDrawable",
                "android.graphics.pdf.PdfDocument", "android.annotation.SuppressLint",
                "android.os.Handler", "android.os.Looper", "android.os.Message", "android.os.Environment",
                "android.os.Build", "android.os.Bundle", "android.os.Vibrator", "android.os.SystemClock",
                "android.graphics.Bitmap", "android.graphics.BitmapFactory", "android.graphics.Canvas",
                "android.graphics.Paint", "android.graphics.Color", "android.graphics.Typeface",
                "android.graphics.Rect", "android.graphics.Path", "android.graphics.Matrix",
                "android.content.Intent", "android.content.Context", "android.content.SharedPreferences",
                "android.content.ClipData", "android.content.ClipboardManager", "android.content.ContentValues",
                "android.content.DialogInterface", "android.content.BroadcastReceiver", "android.content.IntentFilter",
                "android.app.AlertDialog", "android.app.Activity", "android.app.ProgressDialog",
                "android.app.NotificationManager", "android.app.NotificationChannel", "android.app.Notification",
                "android.app.PendingIntent", "android.app.DatePickerDialog", "android.app.TimePickerDialog",
                "android.view.View", "android.view.ViewGroup", "android.view.LayoutInflater", "android.view.Gravity",
                "android.view.MotionEvent", "android.view.Window", "android.view.WindowManager",
                "android.widget.Toast", "android.widget.TextView", "android.widget.EditText", "android.widget.Button",
                "android.widget.ImageView", "android.widget.LinearLayout", "android.widget.ListView",
                "java.io.File", "java.io.IOException", "java.io.InputStream", "java.io.OutputStream",
                "java.io.FileInputStream", "java.io.FileOutputStream", "java.io.FileWriter", "java.io.FileReader",
                "java.io.BufferedReader", "java.io.BufferedWriter", "java.io.InputStreamReader",
                "java.io.OutputStreamWriter", "java.io.ByteArrayOutputStream", "java.io.ByteArrayInputStream",
                "java.io.PrintWriter", "java.io.Serializable",
                "java.util.ArrayList", "java.util.HashMap", "java.util.List", "java.util.Map", "java.util.Set",
                "java.util.HashSet", "java.util.LinkedHashMap", "java.util.Collections", "java.util.Arrays",
                "java.util.Calendar", "java.util.Date", "java.util.Locale", "java.util.Random", "java.util.UUID",
                "java.util.Timer", "java.util.TimerTask", "java.util.Iterator", "java.util.TimeZone",
                "java.util.concurrent.TimeUnit", "java.util.concurrent.Executors",
                "java.util.concurrent.ExecutorService", "java.util.regex.Pattern", "java.util.regex.Matcher",
                "java.text.SimpleDateFormat", "java.text.DecimalFormat", "java.text.NumberFormat",
                "java.text.ParseException", "java.net.URL", "java.net.HttpURLConnection", "java.net.URLEncoder",
                "java.net.URLDecoder", "java.net.Socket", "java.nio.charset.StandardCharsets",
                "java.security.MessageDigest", "java.math.BigDecimal",
                "org.json.JSONObject", "org.json.JSONArray", "org.json.JSONException",
        };
        for (String name : names) IMPORTS.put(name.substring(name.lastIndexOf('.') + 1), name);
    }

    static final class Problem {
        File file;
        int line;
        /** First line of the source excerpt, without the tab ECJ puts in front. */
        String excerpt = "";
        /** Offset of the marked code inside the excerpt, and its length. */
        int column;
        int length;
        String message = "";
    }

    private static final class Edit {
        final int offset, delete;
        final String insert;
        /** Edits of one fix share a group: they are applied together or not at all. */
        int group;

        Edit(int offset, int delete, String insert) {
            this.offset = offset;
            this.delete = delete;
            this.insert = insert;
        }
    }

    private final File allowedRoot;
    private final Mode mode;
    private final ClassIndex classIndex;
    /** "File.java:line" of dead code that is removed even in {@link Mode#KEEP}. */
    private final java.util.Set<String> removeSites;
    /** Every {@link Mode#KEEP} rewrite: {site key, file name, first line of the member it is in, last line}. */
    private final List<Object[]> wraps = new ArrayList<>();
    /** Original content of every file this session changed, for {@link #rollback()}. */
    private final Map<File, byte[]> originals = new LinkedHashMap<>();
    private final List<String> fixes = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private boolean sawUnreachable;

    /**
     * @param generatedJavaRoot only files under this folder are ever modified
     * @param classIndex        looks up classes on the build classpath for missing imports; may be null
     */
    public AutoHeal(File generatedJavaRoot, Mode mode, ClassIndex classIndex, java.util.Set<String> removeSites) {
        this.allowedRoot = generatedJavaRoot;
        this.mode = mode;
        this.classIndex = classIndex;
        this.removeSites = removeSites == null ? new java.util.HashSet<String>() : removeSites;
    }

    public AutoHeal(File generatedJavaRoot) {
        this(generatedJavaRoot, Mode.KEEP, null, null);
    }

    /**
     * The kept-code rewrites whose method (or other class member) has an error in {@code compilerLog}:
     * there the rewrite didn't work out (typically "This method must return a result"), and the
     * dead code has to be removed instead.
     */
    public java.util.Set<String> wrapsNear(String compilerLog) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Problem problem : parse(compilerLog)) {
            for (Object[] wrap : wraps) {
                if (wrap[1].equals(problem.file.getName()) && problem.line >= (Integer) wrap[2] && problem.line <= (Integer) wrap[3]) {
                    keys.add((String) wrap[0]);
                }
            }
        }
        return keys;
    }

    /** Human-readable description of every fix made so far ("MainActivity.java:6379 ..."). */
    public List<String> fixes() {
        return fixes;
    }

    /** Why a reported problem was left alone; for the compile log. */
    public List<String> notes() {
        return notes;
    }

    /** Whether any log passed to {@link #apply} had an "Unreachable code" error (the only fix {@link Mode} changes). */
    public boolean sawUnreachable() {
        return sawUnreachable;
    }

    /**
     * Applies every safe fix the compiler log calls for.
     *
     * @return how many fixes were made in this pass (0: nothing this engine can safely fix)
     */
    public int apply(String compilerLog) {
        Map<File, List<Problem>> byFile = new LinkedHashMap<>();
        for (Problem problem : parse(compilerLog)) {
            if (!isAllowed(problem.file)) {
                if (isFixable(problem.message)) {
                    note(problem.file.getName() + ":" + problem.line + " is outside this build's generated code ("
                            + problem.file.getParent() + "), so it was left alone.");
                }
                continue;
            }
            List<Problem> list = byFile.get(problem.file);
            if (list == null) byFile.put(problem.file, list = new ArrayList<>());
            list.add(problem);
        }
        int before = fixes.size();
        for (Map.Entry<File, List<Problem>> entry : byFile.entrySet()) {
            File file = entry.getKey();
            try {
                byte[] original = read(file);
                String text = new String(original, StandardCharsets.UTF_8);
                JavaTokens tokens = new JavaTokens(text);
                int[] lineStarts = lineStarts(text);
                List<Edit> edits = new ArrayList<>();
                List<String> made = new ArrayList<>();
                int group = 0;
                for (Problem problem : entry.getValue()) {
                    List<Edit> problemEdits = new ArrayList<>();
                    List<String> problemMade = new ArrayList<>();
                    try {
                        fix(problem, text, tokens, lineStarts, problemEdits, problemMade);
                    } catch (RuntimeException e) {
                        note(file.getName() + ":" + problem.line + " (" + problem.message + ") couldn't be fixed: " + e);
                        continue;
                    }
                    if (problemEdits.isEmpty() || overlaps(problemEdits, edits)) continue;
                    group++;
                    for (Edit edit : problemEdits) edit.group = group;
                    edits.addAll(problemEdits);
                    for (String line : problemMade) if (!made.contains(line)) made.add(line);
                }
                String patched = applyEdits(text, edits);
                if (patched != null && !patched.equals(text)) {
                    if (!originals.containsKey(file)) originals.put(file, original);
                    write(file, patched.getBytes(StandardCharsets.UTF_8));
                    fixes.addAll(made);
                }
            } catch (IOException | RuntimeException e) {
                note(file.getName() + " couldn't be read or written for auto-fix: " + e);
            }
        }
        return fixes.size() - before;
    }

    /** Restores every file changed by this session. */
    public void rollback() {
        for (Map.Entry<File, byte[]> entry : originals.entrySet()) {
            try {
                write(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                note(entry.getKey().getName() + " couldn't be restored: " + e);
            }
        }
        originals.clear();
        fixes.clear();
    }

    // ------------------------------------------------------------------ log parsing

    /** ECJ prints one block per problem, separated by lines of dashes: header, excerpt, ^^^ marker, message. */
    static List<Problem> parse(String compilerLog) {
        List<Problem> problems = new ArrayList<>();
        List<String> block = new ArrayList<>();
        String[] lines = compilerLog.split("\n", -1);
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? stripCr(lines[i]) : "----------";
            if (!isDashes(line)) {
                block.add(line);
                continue;
            }
            Problem problem = parseBlock(block);
            if (problem != null) problems.add(problem);
            block.clear();
        }
        return problems;
    }

    private static Problem parseBlock(List<String> block) {
        int header = -1;
        Matcher m = null;
        for (int i = 0; i < block.size() && header < 0; i++) {
            m = HEADER.matcher(block.get(i));
            if (m.find()) header = i;
        }
        if (header < 0) return null;
        int last = block.size() - 1;
        while (last > header && block.get(last).trim().isEmpty()) last--;
        if (last <= header) return null;
        Problem problem = new Problem();
        problem.file = new File(m.group(1));
        problem.line = Integer.parseInt(m.group(2));
        problem.message = block.get(last).trim();
        // The marker is the last line before the message that consists of ^ only.
        for (int i = last - 1; i > header; i--) {
            String marker = block.get(i);
            String trimmed = marker.trim();
            if (trimmed.isEmpty() || trimmed.replace("^", "").length() != 0) continue;
            int first = marker.indexOf('^');
            problem.column = Math.max(0, first - 1); // ECJ starts the marker line with a tab
            problem.length = trimmed.length();
            break;
        }
        if (header + 1 < last) {
            String excerpt = block.get(header + 1);
            problem.excerpt = excerpt.startsWith("\t") ? excerpt.substring(1) : excerpt;
        }
        return problem;
    }

    private static boolean isDashes(String line) {
        String t = line.trim();
        return t.length() >= 5 && t.replace("-", "").isEmpty();
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static boolean isFixable(String message) {
        return message.equals("Unreachable code") || message.startsWith("Unreachable catch block for ")
                || UNRESOLVED.matcher(message).matches() || SEMICOLON.matcher(message).matches()
                || NARROWING.matcher(message).matches();
    }

    // ------------------------------------------------------------------ locating

    private boolean isAllowed(File file) {
        if (!file.isFile()) return false;
        String root = allowedRoot.getAbsolutePath();
        if (!root.endsWith(File.separator)) root += File.separator;
        if (file.getAbsolutePath().startsWith(root)) return true;
        try {
            String canonicalRoot = allowedRoot.getCanonicalPath();
            if (!canonicalRoot.endsWith(File.separator)) canonicalRoot += File.separator;
            return file.getCanonicalPath().startsWith(canonicalRoot);
        } catch (IOException e) {
            return false;
        }
    }

    /** Start offset of every line, with the compiler's rules: \n, \r\n and a lone \r each end a line. */
    static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                starts.add(i + 1);
            } else if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                starts.add(i + 1);
            }
        }
        int[] result = new int[starts.size()];
        for (int i = 0; i < result.length; i++) result[i] = starts.get(i);
        return result;
    }

    private static String lineText(String text, int[] lineStarts, int line) {
        int from = lineStarts[line - 1];
        int to = line < lineStarts.length ? lineStarts[line] : text.length();
        while (to > from && (text.charAt(to - 1) == '\n' || text.charAt(to - 1) == '\r')) to--;
        return text.substring(from, to);
    }

    /** The log is written byte by byte, so non-ASCII characters arrive garbled: compare without them. */
    private static String comparable(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80 && !Character.isWhitespace(c)) b.append(c);
        }
        return b.toString();
    }

    /**
     * @return the offset of the code the compiler marked, or -1 if the file's line doesn't hold
     * the code quoted in the log (then nothing is touched)
     */
    private int locate(Problem problem, String text, int[] lineStarts) {
        int line = problem.line;
        String wanted = comparable(problem.excerpt);
        if (line < 1 || line > lineStarts.length
                || (!wanted.isEmpty() && !comparable(lineText(text, lineStarts, line)).equals(wanted))) {
            // Not where the log says: accept the one line in the file that matches the quoted code.
            int found = -1;
            for (int i = 1; i <= lineStarts.length && !wanted.isEmpty(); i++) {
                if (comparable(lineText(text, lineStarts, i)).equals(wanted)) {
                    if (found > 0) return -1;
                    found = i;
                }
            }
            if (found < 0) return -1;
            line = found;
        }
        String content = lineText(text, lineStarts, line);
        int indent = 0;
        while (indent < content.length() && (content.charAt(indent) == ' ' || content.charAt(indent) == '\t')) indent++;
        int offset = lineStarts[line - 1] + indent + problem.column;
        return offset <= text.length() ? offset : -1;
    }

    // ------------------------------------------------------------------ fixes

    private void fix(Problem problem, String text, JavaTokens tokens, int[] lineStarts, List<Edit> edits, List<String> made) {
        String where = problem.file.getName() + ":" + problem.line;
        String message = problem.message;
        if (message.equals("Unreachable code")) {
            sawUnreachable = true;
            int offset = locate(problem, text, lineStarts);
            if (offset < 0) {
                note(where + " unreachable code: the line in the file doesn't match the log, left alone.");
                return;
            }
            fixUnreachable(problem.file.getName(), text, tokens, lineStarts, offset, edits, made);
            return;
        }
        if (message.startsWith("Unreachable catch block for ")) {
            int offset = locate(problem, text, lineStarts);
            if (offset >= 0) fixUnreachableCatch(where, text, tokens, offset, edits, made);
            return;
        }
        if (SEMICOLON.matcher(message).matches()) {
            int offset = locate(problem, text, lineStarts);
            if (offset < 0 || problem.length <= 0) return;
            int index = tokens.tokenAt(offset);
            // The compiler marks the token the ';' has to follow.
            if (index >= tokens.count || tokens.start[index] != offset) return;
            int end = offset + problem.length;
            edits.add(new Edit(end, 0, ";"));
            made.add(where + " added the missing \";\" after \"" + shorten(text.substring(offset, Math.min(end, text.length()))) + "\"");
            return;
        }
        Matcher narrowing = NARROWING.matcher(message);
        if (narrowing.matches()) {
            String from = narrowing.group(1), to = narrowing.group(2);
            if (NUMBER_WIDTH.indexOf(to) >= NUMBER_WIDTH.indexOf(from)) return;
            int offset = locate(problem, text, lineStarts);
            if (offset < 0 || problem.length <= 0 || offset + problem.length > text.length()) return;
            int index = tokens.tokenAt(offset);
            int endIndex = tokens.tokenAt(offset + problem.length);
            if (index >= tokens.count || tokens.start[index] != offset || endIndex <= index
                    || tokens.end[endIndex - 1] != offset + problem.length) return;
            String expression = text.substring(offset, offset + problem.length);
            edits.add(new Edit(offset, 0, "(" + to + ") ("));
            edits.add(new Edit(offset + problem.length, 0, ")"));
            made.add(where + " added the cast (" + to + ") to \"" + shorten(expression) + "\" (a " + from
                    + " can't be stored in a " + to + " without it)");
            return;
        }
        Matcher unresolved = UNRESOLVED.matcher(message);
        if (unresolved.matches()) {
            String name = unresolved.group(1);
            String fqn = IMPORTS.get(name);
            if (fqn == null && classIndex != null) fqn = classIndex.find(name);
            if (fqn != null) {
                addImport(problem.file, text, tokens, fqn, edits, made);
                return;
            }
        }
        // Any other error inside code that can never run (it follows a return/throw/... in its
        // block): the compiler doesn't call such code unreachable while it has other errors, so
        // the block structure decides, and the dead statements are removed.
        int offset = locate(problem, text, lineStarts);
        if (offset < 0) return;
        int[] site = tokens.deadSiteContaining(tokens.tokenAt(offset));
        if (site == null) return;
        String deadWhere = problem.file.getName() + ":" + lineOf(lineStarts, tokens.start[site[2]]);
        removeDead(deadWhere, text, tokens, site[2], shorten(firstLine(text.substring(tokens.start[site[2]]))), edits, made);
    }

    /**
     * The pass before compiling: finds, from the block structure alone, code that follows a
     * statement which always leaves the method or loop (the compiler would stop with "Unreachable
     * code") and makes it compilable by wrapping that statement in if (true) { }. Only done in
     * methods that return nothing, where this can't cause another error; what it does to the
     * program is nothing at all.
     *
     * @return how many places were rewritten
     */
    public int wrapDeadCode() {
        List<File> files = new ArrayList<>();
        collect(allowedRoot, files, 0);
        int before = fixes.size();
        for (File file : files) {
            try {
                byte[] original = read(file);
                String text = new String(original, StandardCharsets.UTF_8);
                if (text.indexOf("return") < 0 && text.indexOf("throw") < 0 && text.indexOf("break") < 0
                        && text.indexOf("continue") < 0) continue;
                JavaTokens tokens = new JavaTokens(text);
                int[] lineStarts = null;
                List<Edit> edits = new ArrayList<>();
                List<String> made = new ArrayList<>();
                for (int[] site : tokens.deadCodeSites()) {
                    if (tokens.inValueMethod(site[2])) continue;
                    if (lineStarts == null) lineStarts = lineStarts(text);
                    int from = tokens.start[site[0]], to = tokens.end[site[1] - 1];
                    String where = file.getName() + ":" + lineOf(lineStarts, tokens.start[site[2]]);
                    recordWrap(where, file.getName(), tokens, lineStarts, site[2]);
                    edits.add(new Edit(from, 0, "if (true) { "));
                    edits.add(new Edit(to, 0, " }"));
                    made.add(where + " unreachable code (\"" + shorten(firstLine(text.substring(tokens.start[site[2]])))
                            + "\") made compilable before compiling: the statement in front of it (\""
                            + shorten(firstLine(text.substring(from, to))) + "\") is wrapped in if (true) { }. It still never "
                            + "runs; your blocks are unchanged.");
                }
                String patched = applyEdits(text, edits);
                if (patched != null && !patched.equals(text)) {
                    if (!originals.containsKey(file)) originals.put(file, original);
                    write(file, patched.getBytes(StandardCharsets.UTF_8));
                    fixes.addAll(made);
                }
            } catch (IOException | RuntimeException e) {
                note(file.getName() + " couldn't be checked before compiling: " + e);
            }
        }
        return fixes.size() - before;
    }

    private static void collect(File directory, List<File> out, int depth) {
        File[] children = directory.listFiles();
        if (children == null || depth > 30) return;
        java.util.Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory()) collect(child, out, depth + 1);
            else if (child.getName().endsWith(".java")) out.add(child);
        }
    }

    private void recordWrap(String where, String fileName, JavaTokens tokens, int[] lineStarts, int target) {
        // The class member (method, initializer) this is in: the outermost block but one.
        int member = tokens.enclosingOpen(target);
        for (int up = member; up >= 0; up = tokens.enclosingOpen(up)) {
            if (tokens.enclosingOpen(up) >= 0) member = up;
        }
        if (member < 0) return;
        int header = member;
        while (header > 0 && !tokens.is(header - 1, ";") && !tokens.is(header - 1, "}") && !tokens.is(header - 1, "{")) header--;
        int memberClose = tokens.matching(member);
        if (memberClose < 0) return;
        wraps.add(new Object[]{where, fileName, lineOf(lineStarts, tokens.start[header]), lineOf(lineStarts, tokens.start[memberClose])});
    }

    private static int lineOf(int[] lineStarts, int offset) {
        int low = 0, high = lineStarts.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (lineStarts[mid] <= offset) low = mid;
            else high = mid - 1;
        }
        return low + 1;
    }

    private void fixUnreachable(String fileName, String text, JavaTokens tokens, int[] lineStarts, int offset, List<Edit> edits, List<String> made) {
        int target = tokens.tokenAt(offset);
        if (target >= tokens.count) return;
        String where = fileName + ":" + lineOf(lineStarts, tokens.start[target]);
        int[] previous = tokens.statementBefore(target);
        if (previous == null) {
            // e.g. the body of while (false): there is no statement in front to work with.
            note(where + " unreachable code: it doesn't follow another statement in its block, left alone.");
            return;
        }
        int deadStart = tokens.start[target];
        String firstDead = shorten(firstLine(text.substring(deadStart)));
        if (mode == Mode.KEEP && !removeSites.contains(where) && !tokens.inValueMethod(target)) {
            recordWrap(where, fileName, tokens, lineStarts, target);
            int from = tokens.start[previous[0]];
            int to = tokens.end[previous[1] - 1];
            edits.add(new Edit(from, 0, "if (true) { "));
            edits.add(new Edit(to, 0, " }"));
            made.add(where + " unreachable code (\"" + firstDead + "\") now compiles: the statement in front of it (\""
                    + shorten(firstLine(text.substring(from, to))) + "\") is wrapped in if (true) { }. It still never runs; "
                    + "your blocks are unchanged.");
            return;
        }
        removeDead(where, text, tokens, target, firstDead, edits, made);
    }

    /** Takes out the dead statements from token {@code target} to the end of its block (or the next case label). */
    private void removeDead(String where, String text, JavaTokens tokens, int target, String firstDead, List<Edit> edits, List<String> made) {
        int deadStart = tokens.start[target];
        int open = tokens.enclosingOpen(target);
        int close = open < 0 ? -1 : tokens.matching(open);
        if (close < 0) return;
        int endToken = close;
        if (tokens.isSwitchBody(open)) {
            // Dead code in a switch ends at the next case label of this switch.
            int depth = 0;
            for (int i = target; i < close; i++) {
                if (tokens.is(i, "{")) depth++;
                else if (tokens.is(i, "}")) depth--;
                else if (depth == 0 && i > target && (tokens.is(i, "case") || (tokens.is(i, "default") && tokens.is(i + 1, ":")))) {
                    endToken = i;
                    break;
                }
            }
        }
        if (endToken <= target) return;
        int deadEnd = tokens.end[endToken - 1];
        String region = text.substring(deadStart, deadEnd);
        StringBuilder replacement = new StringBuilder("/* auto-fix: unreachable code removed */");
        int lines = 1;
        for (int i = 0; i < region.length(); i++) {
            char c = region.charAt(i);
            if (c == '\n' || c == '\r') replacement.append(c);
            if (c == '\n') lines++;
        }
        edits.add(new Edit(deadStart, deadEnd - deadStart, replacement.toString()));
        made.add(where + " removed " + lines + (lines == 1 ? " line" : " lines") + " of unreachable code starting at \""
                + firstDead + "\" (it could never run); your blocks are unchanged.");
    }

    private void fixUnreachableCatch(String where, String text, JavaTokens tokens, int offset, List<Edit> edits, List<String> made) {
        int at = tokens.tokenAt(offset);
        // Back to the '(' of this catch clause.
        int round = -1;
        int depth = 0;
        for (int i = at; i >= 0 && i > at - 40; i--) {
            if (tokens.is(i, ")")) depth++;
            else if (tokens.is(i, "(")) {
                if (depth == 0) {
                    round = i;
                    break;
                }
                depth--;
            }
        }
        if (round < 1 || !tokens.is(round - 1, "catch")) return;
        int roundClose = tokens.matching(round);
        if (roundClose < 0 || !tokens.is(roundClose + 1, "{")) return;
        for (int i = round; i < roundClose; i++) {
            if (tokens.is(i, "|")) return; // multi-catch: only one alternative is unreachable
        }
        int bodyClose = tokens.matching(roundClose + 1);
        if (bodyClose < 0) return;
        int catchToken = round - 1;
        boolean otherHandlers = tokens.is(bodyClose + 1, "catch") || tokens.is(bodyClose + 1, "finally");
        int tryToken = -1;
        if (!otherHandlers && tokens.is(catchToken - 1, "}")) {
            int open = tokens.matchingBackward(catchToken - 1);
            if (open < 1) return;
            if (tokens.is(open - 1, "try")) {
                tryToken = open - 1; // try { } with this as its only handler: becomes a plain block
            } else if (tokens.is(open - 1, ")")) {
                int back = tokens.matchingBackward(open - 1);
                if (back < 1) return;
                if (tokens.is(back - 1, "catch")) otherHandlers = true;
                else if (!tokens.is(back - 1, "try")) return; // try (resources) { } stands without a catch
            } else {
                return;
            }
        }
        int from = tokens.start[catchToken], to = tokens.end[bodyClose];
        StringBuilder replacement = new StringBuilder("/* auto-fix: catch removed, the try block can't throw it */");
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') replacement.append(c);
        }
        if (tryToken >= 0) edits.add(new Edit(tokens.start[tryToken], 3, "   "));
        edits.add(new Edit(from, to - from, replacement.toString()));
        made.add(where + " removed \"" + shorten(firstLine(text.substring(from, to)))
                + "\": the try block can't throw that exception, so the catch could never run.");
    }

    private void addImport(File file, String text, JavaTokens tokens, String fqn, List<Edit> edits, List<String> made) {
        String statement = "import " + fqn + ";";
        String packageName = fqn.substring(0, fqn.lastIndexOf('.'));
        if (Pattern.compile("import\\s+" + Pattern.quote(fqn) + "\\s*;").matcher(text).find()
                || Pattern.compile("import\\s+" + Pattern.quote(packageName) + "\\s*\\.\\s*\\*\\s*;").matcher(text).find()) {
            return;
        }
        // Appended to the package line, so every line number in later messages stays the same.
        int offset = 0;
        if (tokens.is(0, "package")) {
            int semicolon = 1;
            while (semicolon < tokens.count && !tokens.is(semicolon, ";")) semicolon++;
            if (semicolon >= tokens.count) return;
            offset = tokens.end[semicolon];
        }
        edits.add(new Edit(offset, 0, " " + statement + " "));
        made.add(file.getName() + ": added the missing \"" + statement + "\"");
    }

    // ------------------------------------------------------------------ helpers

    /** Whether any of {@code candidate} touches text an accepted edit deletes, or the other way round. */
    private static boolean overlaps(List<Edit> candidate, List<Edit> accepted) {
        for (Edit c : candidate) {
            for (Edit a : accepted) {
                boolean sameInsert = c.delete == 0 && a.delete == 0 && c.offset == a.offset && c.insert.equals(a.insert);
                if (sameInsert) return true; // the same fix twice (two errors, one cause)
                if (a.delete > 0 && c.offset >= a.offset && c.offset < a.offset + a.delete) return true;
                if (c.delete > 0 && a.offset >= c.offset && a.offset < c.offset + c.delete) return true;
                if (a.delete > 0 && c.delete > 0 && c.offset < a.offset + a.delete && a.offset < c.offset + c.delete) return true;
            }
        }
        return false;
    }

    private static String applyEdits(String text, List<Edit> edits) {
        if (edits.isEmpty()) return null;
        List<Edit> sorted = new ArrayList<>(edits);
        // Stable sort: inserts at one offset keep the order they were made in.
        Collections.sort(sorted, (a, b) -> Integer.compare(a.offset, b.offset));
        StringBuilder out = new StringBuilder(text.length() + 256);
        int position = 0;
        for (Edit edit : sorted) {
            out.append(text, position, edit.offset).append(edit.insert);
            position = edit.offset + edit.delete;
        }
        out.append(text, position, text.length());
        return out.toString();
    }

    private void note(String note) {
        if (!notes.contains(note)) notes.add(note);
    }

    private static String firstLine(String s) {
        int end = 0;
        while (end < s.length() && s.charAt(end) != '\n' && s.charAt(end) != '\r') end++;
        return s.substring(0, end).trim();
    }

    private static String shorten(String s) {
        return s.length() > 40 ? s.substring(0, 37) + "..." : s;
    }

    private static byte[] read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(1024, file.length()));
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static void write(File file, byte[] content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
