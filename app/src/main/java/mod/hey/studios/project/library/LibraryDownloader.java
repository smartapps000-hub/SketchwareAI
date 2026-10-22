package mod.hey.studios.project.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.tools.r8.D8;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.sketchware.remod.R;
import com.sketchware.ai.library.MavenFetcher;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import a.a.a.Dp;
import a.a.a.bB;
import mod.SketchwareUtil;
import mod.agus.jcoderz.dx.command.dexer.Main;
import mod.agus.jcoderz.lib.FileUtil;
import mod.hey.studios.lib.JarCheck;
import mod.hey.studios.lib.prdownloader.PRDownloader;
import mod.hey.studios.lib.prdownloader.PRDownloader.OnDownloadListener;
import mod.hey.studios.lib.prdownloader.PRDownloader.Status;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;

//changed in 6.3.0

public class LibraryDownloader {

    public static final File CONFIGURED_REPOSITORIES_FILE = new File(Environment.getExternalStorageDirectory(),
            ".sketchware" + File.separator + "libs" + File.separator + "repositories.json");
    /** The upstream list: dead or auth-only hosts first, so a lookup could fail before reaching Google or Maven Central. */
    private static final String OLD_DEFAULT_REPOSITORIES_FILE_CONTENT = "[{\"url\":\"https://repo.hortonworks.com/content/repositories/releases\",\"name\":\"HortanWorks\"},{\"url\":\"https://maven.atlassian.com/content/repositories/atlassian-public\",\"name\":\"Atlassian\"},{\"url\":\"https://jitpack.io\",\"name\":\"JitPack\"},{\"url\":\"https://jcenter.bintray.com\",\"name\":\"JCenter\"},{\"url\":\"https://oss.sonatype.org/content/repositories/releases\",\"name\":\"Sonatype\"},{\"url\":\"https://repo.spring.io/plugins-release\",\"name\":\"Spring Plugins\"},{\"url\":\"https://repo.spring.io/libs-milestone\",\"name\":\"Spring Milestone\"},{\"url\":\"https://repo.maven.apache.org/maven2\",\"name\":\"Apache Maven\"},{\"url\":\"https://dl.google.com/dl/android/maven2\",\"name\":\"Google Maven\"},{\"url\":\"https://repo1.maven.org/maven2\",\"name\":\"Maven Central\"}]";
    /** Busiest repositories first; dead (HortonWorks) and login-only (Spring) hosts removed. */
    private static final String DEFAULT_REPOSITORIES_FILE_CONTENT = "[{\"url\":\"https://dl.google.com/dl/android/maven2\",\"name\":\"Google Maven\"},{\"url\":\"https://repo1.maven.org/maven2\",\"name\":\"Maven Central\"},{\"url\":\"https://jitpack.io\",\"name\":\"JitPack\"},{\"url\":\"https://oss.sonatype.org/content/repositories/releases\",\"name\":\"Sonatype\"},{\"url\":\"https://maven.atlassian.com/content/repositories/atlassian-public\",\"name\":\"Atlassian\"},{\"url\":\"https://jcenter.bintray.com\",\"name\":\"JCenter\"}]";
    private final String downloadPath;
    private final ArrayList<String> repoUrls = new ArrayList<>();
    private final ArrayList<String> repoNames = new ArrayList<>();
    Activity context;
    boolean use_d8;
    private OnCompleteListener listener;
    private AlertDialog dialog;
    private boolean isAarAvailable = false, isAarDownloaded = false;
    private int downloadId;
    private String libName = "";
    private String currentRepo = "";
    private int counter = 0;
    /** Second pass: plain-jar libraries (Gson, ZXing core, ...) have no .aar. */
    private boolean searchingJar = false;
    private int attempts = 0, connectionFailures = 0;
    private String dependency = "";
    private EditText vLibrary;
    private TextView vMessage;
    private LinearLayout vStart, vPause, vResume, vCancel, vImport;
    private MavenFetcher fetcher;
    private ArrayList<HashMap<String, Object>> repoMap = new ArrayList<>();

    public LibraryDownloader(Activity context, boolean use_d8) {
        this.context = context;
        this.use_d8 = use_d8;

        downloadPath = FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs/";
    }

    private static void mkdirs(File file, String str) {
        File file2 = new File(file, str);
        if (!file2.exists()) {
            file2.mkdirs();
        }
    }

    private static String dirpart(String str) {
        int lastIndexOf = str.lastIndexOf(File.separatorChar);
        if (lastIndexOf == -1) {
            return null;
        }

        return str.substring(0, lastIndexOf);
    }

    private static void extractFile(ZipInputStream zipInputStream, File file, String str) throws IOException {
        byte[] bArr = new byte[4096];
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(new File(file, str)));
        while (true) {
            int read = zipInputStream.read(bArr);

            if (read == -1) {
                bufferedOutputStream.close();
                return;
            }

            bufferedOutputStream.write(bArr, 0, read);
        }
    }

    public void showDialog(OnCompleteListener listener) {
        this.listener = listener;

        AlertDialog.Builder builder = new com.sketchware.ai.ui.ObAlertBuilder(context);
        LayoutInflater inflater = context.getLayoutInflater();
        View view = inflater.inflate(R.layout.library_downloader_dialog, null);

        final LinearLayout linear1 = view.findViewById(R.id.linear1);
        final LinearLayout progressBarContainer = view.findViewById(R.id.linear3);
        vMessage = view.findViewById(R.id.textview3);
        vStart = view.findViewById(R.id.linear8);
        vPause = view.findViewById(R.id.linear9);
        vResume = view.findViewById(R.id.linear10);
        vCancel = view.findViewById(R.id.linear11);
        vImport = view.findViewById(R.id.ob_lib_import);
        vLibrary = view.findViewById(R.id.edittext1);

        linear1.removeView(progressBarContainer);

        builder.setView(view);
        dialog = builder.show();

        // Pause/Resume belonged to the old downloader; downloads are small, Cancel stays.
        vPause.setVisibility(View.GONE);
        vResume.setVisibility(View.GONE);
        resetButtons();

        vStart.setOnClickListener(v -> startOnlineDownload());
        vImport.setOnClickListener(v -> pickArchive());
        vCancel.setOnClickListener(v -> {
            if (fetcher != null) fetcher.cancel();
            dialog.dismiss();
        });
    }

    /** Request code the host activity forwards to {@link #onArchivePicked}. */
    public static final int REQUEST_IMPORT_ARCHIVE = 7301;

    private void startOnlineDownload() {
        String dep = vLibrary.getText().toString();
        final String[] gav = MavenFetcher.parse(dep);
        if (gav == null) {
            SketchwareUtil.toastError("Use group:artifact:version, e.g. com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0");
            return;
        }
        dependency = gav[0] + ":" + gav[1] + ":" + gav[2];
        libName = downloadPath + gav[1] + "_V_" + gav[2];
        setBusy(true);

        _getRepository();
        final List<String[]> repos = new ArrayList<>();
        for (int i = 0; i < repoUrls.size(); i++) repos.add(new String[]{repoNames.get(i), repoUrls.get(i)});

        fetcher = new MavenFetcher();
        final MavenFetcher current = fetcher;
        new Thread(() -> {
            MavenFetcher.Result result = current.fetch(dependency, repos, new File(downloadPath), new MavenFetcher.Listener() {
                @Override
                public void onStatus(String status) {
                    context.runOnUiThread(() -> vMessage.setText(status));
                }

                @Override
                public void onProgress(int percent) {
                    context.runOnUiThread(() -> vMessage.setText("Library found. Downloading... " + percent + "%"));
                }
            });
            context.runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                if (result.file == null) {
                    setBusy(false);
                    vMessage.setText((result.networkFailed
                            ? "Downloading failed: no network connection."
                            : "Library was not found in loaded repositories (searched .aar and .jar). "
                            + "Check the name and version, or use Import file.") + "\n\n" + result.log);
                } else {
                    vMessage.setText("Downloaded from " + result.url + "\nInstalling...");
                    installArchive(result.file, libName, dependency);
                }
            });
        }, "LibraryDownloader").start();
    }

    /** Offline fallback: pick a .aar, .jar or library .zip from storage. */
    private void pickArchive() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            context.startActivityForResult(intent, REQUEST_IMPORT_ARCHIVE);
        } catch (Exception e) {
            SketchwareUtil.toastError("No file picker available");
        }
    }

    /** Called by the host activity's onActivityResult for {@link #REQUEST_IMPORT_ARCHIVE}. */
    public void onArchivePicked(Uri uri) {
        if (uri == null || dialog == null) return;
        String display = "library";
        try (android.database.Cursor c = context.getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) display = c.getString(0);
        } catch (Exception ignored) {
        }
        String base = display.replaceAll("\\.(zip|aar|jar)$", "").replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty()) base = "library";
        setBusy(true);
        vMessage.setText("Importing " + display + "...");
        final File copy = new File(context.getCacheDir(), "import_" + System.currentTimeMillis());
        final String target = downloadPath + base;
        final String hint = base;
        new Thread(() -> {
            boolean ok = false;
            try (java.io.InputStream in = context.getContentResolver().openInputStream(uri);
                 java.io.OutputStream out = new FileOutputStream(copy)) {
                byte[] buffer = new byte[16384];
                int r;
                while ((r = in.read(buffer)) != -1) out.write(buffer, 0, r);
                ok = true;
            } catch (Exception e) {
                Log.w("LibraryDownloader", "Import copy failed", e);
            }
            final boolean copied = ok;
            context.runOnUiThread(() -> {
                if (!copied) {
                    setBusy(false);
                    vMessage.setText("Couldn't read the selected file.");
                    return;
                }
                libName = target;
                installArchive(copy, target, hint);
            });
        }, "LibraryImport").start();
    }

    /** Extracts the archive into the local library folder, then dexes classes.jar. */
    private void installArchive(File archive, String libDirectory, String packageHint) {
        String reason;
        try {
            reason = MavenFetcher.install(archive, new File(libDirectory));
        } catch (IOException e) {
            reason = "Couldn't extract the library: " + e.getMessage();
        }
        archive.delete();
        if (reason != null) {
            FileUtil.deleteFile(libDirectory);
            setBusy(false);
            vMessage.setText(reason);
            return;
        }
        String classesJar = libDirectory + "/classes.jar";
        if (!use_d8 && !JarCheck.checkJar(classesJar, 44, 51)) {
            setBusy(false);
            vMessage.setText("This jar is not supported by Dx since Dx only supports up to Java 1.7. In order to proceed, you need to switch to D8 (if your Android version is 8+)");
            return;
        }
        FileUtil.writeFile(libDirectory + "/config", findPackageName(libDirectory + "/", packageHint));
        if (packageHint != null && MavenFetcher.parse(packageHint) != null) {
            // Where the library came from, so a later build can fetch it again if its files get lost.
            com.sketchware.ai.project.LibraryIntegrity.writeSource(new File(libDirectory), packageHint, null);
        }
        vMessage.setText("Download completed.");
        setBusy(false);
        new BackTask().execute(classesJar);
    }

    private void setBusy(boolean busy) {
        vLibrary.setEnabled(!busy);
        vStart.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        vStart.setEnabled(!busy);
        vImport.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        vImport.setEnabled(!busy);
        vCancel.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        vCancel.setEnabled(busy);
    }

    private void resetButtons() {
        setBusy(false);
    }

    private String _getAarDownloadLink(String str) {
        String[] split = str.split(":");
        String str2 = "/";

        for (int i = 0; i < split.length - 1; i++) {
            str2 = str2.concat(split[i].replace(".", "/") + "/");
        }

        return str2.concat(split[split.length - 1]).concat("/").concat(_getAarName(str));
    }

    private String _getAarName(String str) {
        String[] split = str.split(":");
        return split[split.length - 2] + "-" + split[split.length - 1] + ".aar";
    }

    private String _getLibName(String str) {
        String[] split = str.split(":");
        return split[split.length - 2] + "_V_" + split[split.length - 1];
    }

    private void _jar2dex(String _path) throws Exception {
        // 6.3.0
        if (use_d8) {
            File libs = BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH;

            ArrayList<String> cm = new ArrayList<>();
            cm.add("--release");
            cm.add("--intermediate");

            cm.add("--lib");
            cm.add(new File(libs, "android.jar").getAbsolutePath());

            cm.add("--classpath");
            cm.add(new File(libs, "core-lambda-stubs.jar").getAbsolutePath());

            cm.add("--output");
            cm.add(new File(_path).getParentFile().getAbsolutePath());

            // Input
            cm.add(_path);

            // Libraries built with javac 21 (AndroidX since 2024) carry nameless MethodParameters entries that
            // stop this D8 with a NullPointerException; the jar is the one just extracted from the download.
            com.sketchware.ai.project.ClassFileSanitizer.sanitizeJar(new File(_path));
            D8.main(cm.toArray(new String[0]));
        } else {
            // 6.3.0 fix2
            Main.clearInternTables();

            // dx
            Main.main(new String[]{
                    // 6.3.0 fix1
                    // "--dex",
                    "--debug",
                    "--verbose",
                    "--multi-dex",
                    "--output=" + new File(_path).getParentFile().getAbsolutePath(),
                    _path
            });
        }
    }

    private void _unZipFile(String str, String str2) {
        try {
            File file = new File(str2);
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(str));

            while (true) {
                ZipEntry nextEntry = zipInputStream.getNextEntry();

                if (nextEntry == null) {
                    zipInputStream.close();
                    return;
                }

                String name = nextEntry.getName();

                if (nextEntry.isDirectory()) {
                    mkdirs(file, name);
                } else {
                    String dirpart = dirpart(name);

                    if (dirpart != null) {
                        mkdirs(file, dirpart);
                    }

                    extractFile(zipInputStream, file, name);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getLastSegment(String path) {
        return Uri.parse(path).getLastPathSegment();
    }

    private String findPackageName(String path, String defaultValue) {
        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(path, files);

        // Method 1: use manifest
        for (String f : files) {
            if (getLastSegment(f).equals("AndroidManifest.xml")) {
                String content = FileUtil.readFile(f);

                Pattern p = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL);
                Matcher m = p.matcher(content);

                if (m.find()) {
                    return m.group(1);
                }
            }
        }

        // Method 2: screw manifest. use dependency
        if (defaultValue.contains(":")) {
            return defaultValue.split(":")[0];
        }

        // Method 3: nothing worked. return empty string (lmao) (yeah lmao)
        return "";
    }

    private void deleteUnnecessaryFiles(String path) {

        // 6.3.0
        String[] list = {
                "res",
                "classes.dex",
                "classes.jar",
                "config",
                "AndroidManifest.xml",
                "jni",
                "assets",
                "proguard.txt",
                com.sketchware.ai.project.LibraryIntegrity.SOURCE_FILE
        };

        List<String> validFiles = new ArrayList<>(Arrays.asList(list));
        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(path, files);

        for (String f : files) {
            // 6.3.0
            // Skip all dex files
            String p = getLastSegment(f);

            if (p.startsWith("classes") && p.endsWith(".dex")) continue;
            if (!validFiles.contains(p)) FileUtil.deleteFile(f);
        }
    }

    /** Always searched first, whatever repositories.json says (it is shared with Sketchware Pro). */
    private static final String[][] BUILT_IN_REPOSITORIES = {
            {"Google Maven", "https://dl.google.com/dl/android/maven2"},
            {"Maven Central", "https://repo1.maven.org/maven2"},
            {"JitPack", "https://jitpack.io"},
    };
    /** Shut down or login-only hosts: they can only answer with SSL errors or 401. */
    private static final String[] DEAD_HOSTS = {"repo.hortonworks.com", "repo.spring.io", "jcenter.bintray.com"};

    private static String normalize(String url) {
        String u = url.trim().toLowerCase(java.util.Locale.ROOT);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u.replace("http://", "https://").replace("repo.maven.apache.org/maven2", "repo1.maven.org/maven2");
    }

    /**
     * Search order: Google Maven, Maven Central, JitPack, then the custom repositories from
     * /.sketchware/libs/repositories.json (duplicates and dead hosts left out). The file itself
     * is only read, never rewritten, because Sketchware Pro uses the same file.
     */
    private void _getRepository() {
        repoUrls.clear();
        repoNames.clear();
        counter = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String[] repo : BUILT_IN_REPOSITORIES) {
            repoNames.add(repo[0]);
            repoUrls.add(repo[1]);
            seen.add(normalize(repo[1]));
        }
        ArrayList<HashMap<String, Object>> configured = null;
        String json = CONFIGURED_REPOSITORIES_FILE.exists()
                ? FileUtil.readFile(CONFIGURED_REPOSITORIES_FILE.getAbsolutePath()) : "";
        if (json.trim().isEmpty()) json = DEFAULT_REPOSITORIES_FILE_CONTENT;
        try {
            configured = new Gson().fromJson(json, Helper.TYPE_MAP_LIST);
        } catch (JsonParseException e) {
            SketchwareUtil.toastError("repositories.json is invalid; using the built-in repositories");
        }
        if (configured == null) return;
        for (HashMap<String, Object> configuration : configured) {
            Object url = configuration.get("url");
            if (!(url instanceof String) || ((String) url).trim().isEmpty()) continue;
            String key = normalize((String) url);
            boolean dead = false;
            for (String host : DEAD_HOSTS) if (key.contains(host)) dead = true;
            if (dead || !seen.add(key)) continue;
            Object name = configuration.get("name");
            repoNames.add(name instanceof String ? (String) name : (String) url);
            repoUrls.add(((String) url).trim());
        }
    }

    public interface OnCompleteListener {
        void onComplete();
    }

    private class BackTask extends AsyncTask<String, String, String> implements BuildProgressReceiver {
        private ProgressDialog progressDialog;
        boolean success = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(context, com.sketchware.remod.R.style.SketchwareAI_GlassAlertDialog);
            progressDialog.setTitle("Please wait");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                Dp.maybeExtractAndroidJar(this);
                publishProgress((use_d8 ? "D8" : "Dx") + " is running...");
                _jar2dex(params[0]);
                success = true;
            } catch (Exception e) {
                success = false;
                return Log.getStackTraceString(e);
            }

            return "true";
        }

        @Override
        protected void onPostExecute(String s) {
            if (success) {
                bB.a(context, "The library has been downloaded and imported to local libraries successfully.", 1).show();
                listener.onComplete();
            } else {
                SketchwareUtil.showAnErrorOccurredDialog(context, s);
            }

            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }

            progressDialog.dismiss();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            progressDialog.setMessage(values[0]);
        }

        @Override
        public void onProgress(String progress) {
            publishProgress(progress);
        }
    }
}
