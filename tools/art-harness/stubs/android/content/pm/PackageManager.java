package android.content.pm;
public class PackageManager {
    public static class NameNotFoundException extends Exception { public NameNotFoundException() {} public NameNotFoundException(String s) { super(s); } }
    public PackageInfo getPackageInfo(String p, int f) throws NameNotFoundException { throw new NameNotFoundException(p); }
    public ApplicationInfo getApplicationInfo(String p, int f) throws NameNotFoundException { throw new NameNotFoundException(p); }
}
