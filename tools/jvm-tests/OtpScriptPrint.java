/**
 * v2.1.2: prints OtpAssist's fill script for one code (otp-script-test.js runs it in jsdom), or "REFUSED" when the
 * script refuses the text (not 6 or 8 digits). Compiled against android.jar; only static Java runs.
 *   java -cp $ANDROID_JAR:out OtpScriptPrint 123456 > otp.js
 */
public class OtpScriptPrint {
    public static void main(String[] args) throws Exception {
        try {
            System.out.print(com.sketchware.ai.github.OtpAssist.fillScript(args[0]));
        } catch (IllegalArgumentException e) {
            System.out.print("REFUSED");
        }
    }
}
