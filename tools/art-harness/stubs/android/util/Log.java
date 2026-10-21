package android.util;
public final class Log {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6, ASSERT = 7;
    static final boolean QUIET = System.getenv("H_QUIET") != null;
    static final java.io.PrintStream ERR = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true);
    private static int p(String l, String t, String m, Throwable e) {
        if (QUIET && (l.equals("V") || l.equals("D"))) return 0;
        ERR.println(l + "/" + t + ": " + m);
        if (e != null) e.printStackTrace(ERR);
        return 0;
    }
    public static int v(String t, String m) { return p("V", t, m, null); }
    public static int v(String t, String m, Throwable e) { return p("V", t, m, e); }
    public static int d(String t, String m) { return p("D", t, m, null); }
    public static int d(String t, String m, Throwable e) { return p("D", t, m, e); }
    public static int i(String t, String m) { return p("I", t, m, null); }
    public static int i(String t, String m, Throwable e) { return p("I", t, m, e); }
    public static int w(String t, String m) { return p("W", t, m, null); }
    public static int w(String t, String m, Throwable e) { return p("W", t, m, e); }
    public static int w(String t, Throwable e) { return p("W", t, "", e); }
    public static int e(String t, String m) { return p("E", t, m, null); }
    public static int e(String t, String m, Throwable e) { return p("E", t, m, e); }
    public static int wtf(String t, String m) { return p("F", t, m, null); }
    public static int wtf(String t, String m, Throwable e) { return p("F", t, m, e); }
    public static int println(int prio, String t, String m) { return p("P", t, m, null); }
    public static boolean isLoggable(String t, int level) { return true; }
    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter(); tr.printStackTrace(new java.io.PrintWriter(sw)); return sw.toString();
    }
}
