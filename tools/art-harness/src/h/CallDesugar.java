package h;

/** Calls DesugarHelper.run() from fixture 911's built APK on ART (API 31): Java 9-16 APIs through the desugared library. */
public class CallDesugar {
    public static void main(String[] a) throws Exception {
        Object r = Class.forName("com.fixture.desugar.DesugarHelper").getMethod("run").invoke(null);
        System.out.println("DesugarHelper.run() = " + r);
        System.out.println("expected            = 2026-11-01|120|2|X,Y,Z|Y|abab|q|y|3");
    }
}
