package android.os;
import java.io.File;
public class Environment {
    public static final String MEDIA_MOUNTED = "mounted";
    public static String DIRECTORY_DOWNLOADS = "Download", DIRECTORY_DCIM = "DCIM", DIRECTORY_PICTURES = "Pictures", DIRECTORY_MUSIC = "Music", DIRECTORY_MOVIES = "Movies", DIRECTORY_DOCUMENTS = "Documents";
    public static File getExternalStorageDirectory() { return new File("/storage/emulated/0"); }
    public static File getExternalStoragePublicDirectory(String type) { return new File(getExternalStorageDirectory(), type); }
    public static String getExternalStorageState() { return MEDIA_MOUNTED; }
    public static File getDataDirectory() { return new File("/data"); }
    public static File getRootDirectory() { return new File("/system"); }
    public static boolean isExternalStorageManager() { return true; }
}
