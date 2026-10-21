package android.content;
import java.io.File; import java.util.HashMap;
/** Harness Context: directories under -Dh.root, assets from the APK. */
public class Context {
    public static final int MODE_PRIVATE = 0;
    private static final HashMap<String, SharedPreferences> PREFS = new HashMap<>();
    private final File root = new File(System.getProperty("h.root", "/tmp/h"));
    public Context getApplicationContext() { return this; }
    public File getCacheDir() { File f = new File(root, "cache"); f.mkdirs(); return f; }
    public File getFilesDir() { File f = new File(root, "files"); f.mkdirs(); return f; }
    public File getExternalFilesDir(String type) { File f = new File(root, "ext" + (type == null ? "" : "/" + type)); f.mkdirs(); return f; }
    public File getExternalCacheDir() { File f = new File(root, "extcache"); f.mkdirs(); return f; }
    public File getDir(String name, int mode) { File f = new File(root, "app_" + name); f.mkdirs(); return f; }
    public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }
    public String getPackageName() { return "com.sketchware.ai"; }
    public int getColor(int id) { return 0xFF000000; }
    public String getString(int id) { return "string#" + id; }
    public String getString(int id, Object... args) { return "string#" + id; }
    public synchronized SharedPreferences getSharedPreferences(String name, int mode) { return PREFS.computeIfAbsent(name, n -> new MapPrefs()); }
    public Object getSystemService(String name) { return null; }
    public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
    public android.content.pm.ApplicationInfo getApplicationInfo() { return new android.content.pm.ApplicationInfo(); }
}
