/**
 * v2.0.2: prints GitHubInAppAuthDialog.SOCIAL_SCRIPT (the "Continue with Google / Apple" interception) for
 * social-script-test.js (jsdom). Compiled against android.jar and the app's compiled classes; only the constant is read.
 *   java -cp $ANDROID_JAR:<app classes>:out SocialScriptPrint > social.js
 */
public class SocialScriptPrint {
    public static void main(String[] args) throws Exception {
        java.lang.reflect.Field f = Class.forName("com.sketchware.ai.github.GitHubInAppAuthDialog")
                .getDeclaredField("SOCIAL_SCRIPT");
        f.setAccessible(true);
        System.out.print(f.get(null));
    }
}
