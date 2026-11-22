/**
 * v2.1.2: prints OtpAssist's fill script for one code (otp-script-test.js runs it in jsdom), or "REFUSED" when the
 * script refuses the text (not 6 or 8 digits). Compiled against android.jar; only static Java runs.
 *   java -cp $ANDROID_JAR:out OtpScriptPrint 123456 > otp.js
 * v2.1.4: "probe" prints the page reader (PROBE_SCRIPT), "send" the "Send a code via email" press (SEND_SCRIPT).
 */
public class OtpScriptPrint {
    public static void main(String[] args) throws Exception {
        if ("probe".equals(args[0])) {
            System.out.print(com.sketchware.ai.github.OtpAssist.PROBE_SCRIPT);
            return;
        }
        if ("send".equals(args[0])) {
            System.out.print(com.sketchware.ai.github.OtpAssist.SEND_SCRIPT);
            return;
        }
        try {
            System.out.print(com.sketchware.ai.github.OtpAssist.fillScript(args[0]));
        } catch (IllegalArgumentException e) {
            System.out.print("REFUSED");
        }
    }
}
