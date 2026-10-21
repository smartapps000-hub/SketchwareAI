package android.content.pm;
public class ApplicationInfo { public String nativeLibraryDir = System.getProperty("h.native", "/nonexistent"), sourceDir = System.getProperty("h.apk"), dataDir = System.getProperty("h.root"), packageName = "com.sketchware.ai"; public int targetSdkVersion = 34, minSdkVersion = 26, flags; }
