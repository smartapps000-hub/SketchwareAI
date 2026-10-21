package android.text;
public class TextUtils {
    public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; }
    public static boolean equals(CharSequence a, CharSequence b) { if (a == b) return true; if (a == null || b == null) return false; return a.toString().equals(b.toString()); }
    public static String join(CharSequence d, Iterable<?> t) { StringBuilder sb = new StringBuilder(); boolean f = true; for (Object o : t) { if (!f) sb.append(d); sb.append(o); f = false; } return sb.toString(); }
    public static String join(CharSequence d, Object[] t) { return join(d, java.util.Arrays.asList(t)); }
    public static String[] split(String text, String expression) { return text.length() == 0 ? new String[0] : text.split(expression, -1); }
    public static boolean isDigitsOnly(CharSequence s) { for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false; return true; }
    public static int getTrimmedLength(CharSequence s) { return s.toString().trim().length(); }
    public static String htmlEncode(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
