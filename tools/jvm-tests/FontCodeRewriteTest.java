import com.sketchware.ai.project.FontCode;
/** FontCode.rewrite on generated font blocks and on user lines it must leave alone; line counts never change.
 * Run: javac -d out tools/jvm-tests/FontCodeRewriteTest.java app/src/main/java/com/sketchware/ai/project/FontCode.java && java -cp out FontCodeRewriteTest */
public class FontCodeRewriteTest {
    static int fails = 0;
    static void check(String name, String in, boolean expectRewrite) {
        String out = FontCode.rewrite(in);
        boolean rewritten = !out.equals(in);
        int li = in.split("\n", -1).length, lo = out.split("\n", -1).length;
        boolean ok = rewritten == expectRewrite && li == lo;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + name + " lines " + li + "->" + lo + "\n     " + out.replace("\n", "\\n"));
    }
    public static void main(String[] a) {
        check("generated block", "\t\ttextview1.setTypeface(Typeface.createFromAsset(getAssets(),\"fonts/ramabhadra.ttf\"), 0);", true);
        check("fragment/getContext", "button1.setTypeface(Typeface.createFromAsset(getContext().getAssets(),\"fonts/tenali.ttf\"), Typeface.BOLD);", true);
        check("qualified receiver", "binding.textview1.setTypeface(Typeface.createFromAsset(getAssets(), \"fonts/a.ttf\"), 1);", true);
        check("cast receiver", "((VH) holder).textview1.setTypeface(Typeface.createFromAsset(getAssets(),\"fonts/a.ttf\"), 0);", false);
        check("array receiver", "views[0].setTypeface(Typeface.createFromAsset(getAssets(),\"fonts/a.ttf\"), 0);", false);
        check("call receiver", "getView().findViewById(R.id.t).setTypeface(Typeface.createFromAsset(getAssets(),\"fonts/a.ttf\"), 0);", false);
        check("split over lines", "textview1.setTypeface(Typeface.createFromAsset(getAssets(),\n\"fonts/a.ttf\"), 0);", false);
        check("style on next line", "textview1.setTypeface(Typeface.createFromAsset(getAssets(),\"fonts/a.ttf\"),\n 0);", false);
        check("two blocks + text", "a();\ntextview1.setTypeface(Typeface.createFromAsset(getAssets(),\"fonts/a.ttf\"), 0);\nb();\ntextview2.setTypeface(Typeface.createFromAsset(getAssets(),\"fonts/b.ttf\"), 2);\n", true);
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
    }
}
