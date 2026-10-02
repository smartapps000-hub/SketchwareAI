package com.sketchware.ai.project;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-heal for Java compile errors: small, deterministic fixes applied to the GENERATED source
 * of the current build (mysc/&lt;id&gt;/app/src/main/java), followed by a recompile.
 * <p>
 * Safety: the project's blocks, MoreBlocks and custom Java files are never touched. The
 * generated files are rewritten from the blocks on every build, so a fix can't corrupt
 * anything; it is simply applied again on the next build until the user fixes the block. If the
 * patched code still doesn't compile, {@link #rollback()} restores the files and the original
 * error log is shown.
 * <p>
 * Only fixes that cannot change what the program does are made:
 * <ul>
 *     <li>Unreachable code after a return/break/continue/throw (in any form): the dead
 *     statements are removed up to the end of their block. They could never run.</li>
 *     <li>A well-known class used without its import (PackageManager, Uri, Settings, ...):
 *     the import is added.</li>
 * </ul>
 * Casts, try/catch wrapping and renames are deliberately not automated: they can change behaviour.
 */
public final class AutoHeal {

    private static final Pattern HEADER = Pattern.compile("ERROR in (.+?\\.java) \\(at line (\\d+)\\)");
    private static final Pattern UNRESOLVED = Pattern.compile(
            "^([A-Z]\\w*) cannot be resolved(?: to a (?:type|variable))?\\s*$");

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

    private final File allowedRoot;
    /** Original content of every file this session changed, for {@link #rollback()}. */
    private final Map<File, byte[]> originals = new LinkedHashMap<>();
    private final List<String> fixes = new ArrayList<>();

    /** @param generatedJavaRoot only files under this folder are ever modified */
    public AutoHeal(File generatedJavaRoot) {
        this.allowedRoot = generatedJavaRoot;
    }

    /** Human-readable description of every fix made so far ("MainActivity.java:6379 ..."). */
    public List<String> fixes() {
        return fixes;
    }

    /**
     * Applies every safe fix the compiler log calls for.
     *
     * @return how many fixes were made in this pass (0: nothing this engine can safely fix)
     */
    public int apply(String compilerLog) {
        // file -> (line -> column of the first unreachable character, or 0)
        Map<File, Map<Integer, Integer>> unreachable = new LinkedHashMap<>();
        Map<File, Set<String>> imports = new LinkedHashMap<>();
        // ECJ prints one block per problem, separated by lines of dashes: a header, the source
        // excerpt (one or more lines), a ^^^^ marker and, last, the message.
        for (String block : compilerLog.split("(?m)^-{5,}\\s*$")) {
            Matcher m = HEADER.matcher(block);
            if (!m.find()) continue;
            File file = new File(m.group(1));
            if (!isAllowed(file)) continue;
            String[] blockLines = block.trim().split("\\r?\\n");
            String message = blockLines[blockLines.length - 1].trim();
            if (message.equals("Unreachable code")) {
                int column = 0;
                // Single-line excerpt: header, source, marker, message. The marker's first ^ gives
                // the column inside the (indent-stripped) source line.
                if (blockLines.length == 4 && blockLines[2].indexOf('^') > 0) {
                    column = blockLines[2].indexOf('^') - 1;
                }
                unreachable.computeIfAbsent(file, k -> new java.util.TreeMap<>(java.util.Collections.reverseOrder()))
                        .put(Integer.parseInt(m.group(2)), column);
            } else {
                Matcher u = UNRESOLVED.matcher(message);
                if (u.matches() && IMPORTS.containsKey(u.group(1))) {
                    imports.computeIfAbsent(file, k -> new LinkedHashSet<>()).add(IMPORTS.get(u.group(1)));
                }
            }
        }
        int before = fixes.size();
        Set<File> files = new LinkedHashSet<>(unreachable.keySet());
        files.addAll(imports.keySet());
        for (File file : files) {
            try {
                byte[] original = Files.readAllBytes(file.toPath());
                String text = new String(original, StandardCharsets.UTF_8);
                String eol = text.contains("\r\n") ? "\r\n" : "\n";
                List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\r?\\n", -1)));
                int made = fixes.size();
                for (Map.Entry<Integer, Integer> at : unreachable.getOrDefault(file, new LinkedHashMap<>()).entrySet()) {
                    removeUnreachable(file, lines, at.getKey(), at.getValue());
                }
                for (String fqn : imports.getOrDefault(file, new LinkedHashSet<>())) {
                    addImport(file, lines, fqn);
                }
                if (fixes.size() > made) {
                    if (!originals.containsKey(file)) originals.put(file, original);
                    Files.write(file.toPath(), String.join(eol, lines).getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException | RuntimeException e) {
                // Leave this file as it is: the original error is reported.
            }
        }
        return fixes.size() - before;
    }

    /** Restores every file changed by this session. */
    public void rollback() {
        for (Map.Entry<File, byte[]> entry : originals.entrySet()) {
            try {
                Files.write(entry.getKey().toPath(), entry.getValue());
            } catch (IOException ignored) {
            }
        }
        originals.clear();
        fixes.clear();
    }

    private boolean isAllowed(File file) {
        try {
            String root = allowedRoot.getCanonicalPath() + File.separator;
            return file.isFile() && file.getCanonicalPath().startsWith(root);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Removes the dead statements: from the first unreachable character to the end of the
     * enclosing block (or the next case/default label). Every statement after a jump in the same
     * block is unreachable, so nothing that could ever run is removed. Line breaks are kept, so
     * line numbers in later messages don't move.
     */
    private void removeUnreachable(File file, List<String> lines, int errorLine, int column) {
        if (errorLine < 1 || errorLine > lines.size()) return;
        String first = lines.get(errorLine - 1);
        int indent = first.length() - first.replaceAll("^\\s+", "").length();
        int startInLine = Math.min(first.length(), indent + Math.max(0, column));

        StringBuilder text = new StringBuilder();
        int start = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (i == errorLine - 1) start = text.length() + startInLine;
            text.append(lines.get(i)).append('\n');
        }
        int end = deadRegionEnd(text, start);
        if (end <= start) return;

        StringBuilder kept = new StringBuilder();
        int removedLines = 1;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == '\n') {
                kept.append('\n');
                removedLines++;
            }
        }
        String region = text.substring(start, end).trim();
        if (region.isEmpty()) return;
        String patched = text.substring(0, start) + "/* auto-fix: unreachable code removed */" + kept + text.substring(end);
        List<String> result = new ArrayList<>(Arrays.asList(patched.split("\n", -1)));
        result.remove(result.size() - 1); // the '\n' appended after the last line
        lines.clear();
        lines.addAll(result);
        fixes.add(file.getName() + ":" + errorLine + " removed " + removedLines
                + (removedLines == 1 ? " line" : " lines") + " of unreachable code starting at \""
                + shorten(region.split("\\r?\\n")[0].trim()) + "\" (it could never run: it follows a return/break/"
                + "continue/throw in the same block). Your blocks are unchanged.");
    }

    /** Offset of the '}' closing the block that contains {@code start}, or of the next case label. */
    private static int deadRegionEnd(CharSequence text, int start) {
        int depth = 0;
        boolean statementStart = true;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (c == '/' && next == '/') {
                while (i < text.length() && text.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < text.length() && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < text.length() && text.charAt(i) != quote && text.charAt(i) != '\n') {
                    if (text.charAt(i) == '\\') i++;
                    i++;
                }
                statementStart = false;
                continue;
            }
            if (c == '{') {
                depth++;
                statementStart = true;
                continue;
            }
            if (c == '}') {
                if (depth == 0) return i;
                depth--;
                statementStart = true;
                continue;
            }
            if (c == ';') {
                statementStart = true;
                continue;
            }
            if (Character.isWhitespace(c)) continue;
            if (depth == 0 && statementStart && Character.isJavaIdentifierStart(c)) {
                int e = i;
                while (e < text.length() && Character.isJavaIdentifierPart(text.charAt(e))) e++;
                String word = text.subSequence(i, e).toString();
                if (i > start && word.equals("case")) return i;
                if (i > start && word.equals("default")) {
                    int k = e;
                    while (k < text.length() && Character.isWhitespace(text.charAt(k))) k++;
                    if (k < text.length() && text.charAt(k) == ':') return i;
                }
            }
            statementStart = false;
        }
        return -1;
    }

    private void addImport(File file, List<String> lines, String fqn) {
        String wildcard = "import " + fqn.substring(0, fqn.lastIndexOf('.')) + ".*;";
        String statement = "import " + fqn + ";";
        int packageLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.equals(statement) || t.equals(wildcard)) return;
            if (packageLine < 0 && t.startsWith("package ") && t.endsWith(";")) packageLine = i;
            if (t.startsWith("public class ") || t.startsWith("class ")) break;
        }
        // Appended to the package line, so every line number in later messages stays the same.
        if (packageLine < 0) return;
        lines.set(packageLine, lines.get(packageLine) + " " + statement);
        fixes.add(file.getName() + ": added missing \"" + statement + "\"");
    }

    private static String shorten(String s) {
        return s.length() > 40 ? s.substring(0, 37) + "..." : s;
    }
}
