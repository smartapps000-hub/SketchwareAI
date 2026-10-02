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
 *     <li>Unreachable code after a return/break/continue/throw: the jump becomes
 *     {@code if (true) return;}. The code after it still never runs, exactly as written, but
 *     it compiles.</li>
 *     <li>A well-known class used without its import (PackageManager, Uri, Settings, ...):
 *     the import is added.</li>
 * </ul>
 * Casts, try/catch wrapping and renames are deliberately not automated: they can change behaviour.
 */
public final class AutoHeal {

    private static final Pattern HEADER = Pattern.compile("ERROR in (.+?\\.java) \\(at line (\\d+)\\)");
    private static final Pattern UNRESOLVED = Pattern.compile(
            "^([A-Z]\\w*) cannot be resolved(?: to a (?:type|variable))?\\s*$");
    private static final Pattern JUMP = Pattern.compile(
            "^(return\\b[^;{}]*;|break\\s*;|continue\\s*;|throw\\b[^;{}]*;)\\s*(//.*)?$");

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
        Map<File, Set<Integer>> unreachable = new LinkedHashMap<>();
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
                unreachable.computeIfAbsent(file, k -> new LinkedHashSet<>()).add(Integer.parseInt(m.group(2)));
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
                for (int line : unreachable.getOrDefault(file, new LinkedHashSet<>())) {
                    fixUnreachable(file, lines, line);
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

    /** {@code return x;} right before the unreachable line becomes {@code if (true) return x;}. */
    private void fixUnreachable(File file, List<String> lines, int errorLine) {
        int index = errorLine - 2; // 0-based index of the line before the error line
        while (index >= 0) {
            String t = lines.get(index).trim();
            if (!t.isEmpty() && !t.startsWith("//")) break;
            index--;
        }
        if (index < 0) return;
        String line = lines.get(index);
        String trimmed = line.trim();
        if (!JUMP.matcher(trimmed).matches() || !balanced(trimmed)) return;
        String indent = line.substring(0, line.indexOf(trimmed));
        lines.set(index, indent + "if (true) " + trimmed + " // auto-fix: code below was unreachable");
        fixes.add(file.getName() + ":" + (index + 1) + " unreachable code after \"" + shorten(trimmed)
                + "\": changed to \"if (true) " + shorten(trimmed) + "\" so it compiles. The code after it still "
                + "never runs; move or delete it in your block.");
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

    private static boolean balanced(String s) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (depth < 0) return false;
        }
        return depth == 0 && !inString;
    }

    private static String shorten(String s) {
        return s.length() > 40 ? s.substring(0, 37) + "..." : s;
    }
}
