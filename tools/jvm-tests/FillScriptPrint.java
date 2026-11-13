/**
 * v2.0.0: prints the code fill script of GitHubInAppAuthDialog for one code, for fill-script-test.js (jsdom).
 * The class is compiled against android.jar; only its static fillScript runs (no Android code is executed).
 *   java -cp $ANDROID_JAR:out FillScriptPrint ABCD-1234 > script.js
 */
public class FillScriptPrint {
    public static void main(String[] args) throws Exception {
        java.lang.reflect.Method m = Class.forName("com.sketchware.ai.github.GitHubInAppAuthDialog")
                .getDeclaredMethod("fillScript", String.class);
        m.setAccessible(true);
        System.out.print(m.invoke(null, args[0]));
    }
}
