package h;

import java.util.ArrayList;
import java.util.List;

import static h.Main.OUT;
import static h.R.*;

/**
 * v1.9.8: Build Doctor's new rules and the generated manifest's android:exported repair, with the shipped APK's classes
 * on ART (ICU regexes). Prints DOCTOR CHECK lines and "DOCTORTEST n/n ok".
 */
public final class DoctorTest {
    private static int ok, total;

    static void check(String name, boolean result, Object detail) {
        total++;
        if (result) ok++;
        OUT.println("DOCTOR CHECK " + (result ? "ok  " : "FAIL") + " " + name + (detail == null ? "" : " - " + detail));
    }

    @SuppressWarnings("unchecked")
    static void run() throws Throwable {
        Class<?> ix = c("a.a.a.Ix");
        String manifest = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <application>\n"
                + "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n"
                + "            <intent-filter><action android:name=\"android.intent.action.MAIN\"/></intent-filter>\n"
                + "        </activity>\n"
                + "        <activity android:name=\".ShareActivity\">\n"
                + "            <intent-filter><action android:name=\"android.intent.action.SEND\"/></intent-filter>\n"
                + "        </activity>\n"
                + "        <activity android:name=\".PlainActivity\"/>\n"
                + "        <activity android:name=\".Second\"></activity>\n"
                + "        <service\n            android:name=\".Sync\">\n"
                + "            <intent-filter><action android:name=\"x.SYNC\"/></intent-filter>\n"
                + "        </service>\n"
                + "        <receiver android:name=\".Boot\" tools:node=\"merge\">\n"
                + "            <intent-filter><action android:name=\"android.intent.action.BOOT_COMPLETED\"/></intent-filter>\n"
                + "        </receiver>\n"
                + "        <activity-alias android:name=\".Alias\" android:targetActivity=\".MainActivity\">\n"
                + "            <intent-filter><action android:name=\"x.ALIAS\"/></intent-filter>\n"
                + "        </activity-alias>\n"
                + "        <receiver android:name=\".Own\" android:exported=\"false\">\n"
                + "            <intent-filter><action android:name=\"x.OWN\"/></intent-filter>\n"
                + "        </receiver>\n"
                + "    </application>\n</manifest>\n";
        List<String> names = new ArrayList<>();
        String out = (String) call(ix, "settleExported", manifest, 37, names);
        check("exported added to 4 components with intent filters", names.size() == 4, names);
        check("ShareActivity", out.contains("<activity android:exported=\"true\" android:name=\".ShareActivity\">"), null);
        check("service over two lines", out.contains("<service android:exported=\"true\"\n            android:name=\".Sync\">"), null);
        check("receiver and alias", out.contains("<receiver android:exported=\"true\" android:name=\".Boot\"")
                && out.contains("<activity-alias android:exported=\"true\" android:name=\".Alias\""), null);
        check("own exported=false kept, no filter = untouched", out.contains("<receiver android:name=\".Own\" android:exported=\"false\">")
                && out.contains("<activity android:name=\".PlainActivity\"/>") && out.contains("<activity android:name=\".Second\"></activity>"), null);
        check("same line count", out.split("\n", -1).length == manifest.split("\n", -1).length, null);
        check("target 30: unchanged", manifest.equals(call(ix, "settleExported", manifest, 30, null)), null);
        check("idempotent", out.equals(call(ix, "settleExported", out, 37, null)), null);
        String quoted = "<application>\n        <activity android:label=\"Price > 5\" android:name=\".Deal\" android:exported=\"false\">\n"
                + "            <intent-filter><action android:name=\"x.DEAL\"/></intent-filter>\n        </activity>\n</application>\n";
        check("a > inside an attribute value: its own exported is seen", quoted.equals(call(ix, "settleExported", quoted, 37, null)), null);

        Class<?> diag = c("com.sketchware.ai.diagnostic.CompileLogDiagnostics");
        Object m3 = call(diag, "analyze", "/storage/emulated/0/.sketchware/mysc/950/app/src/main/res/layout/main.xml:12: error: "
                + "attr/colorPrimaryContainer (aka com.my.app:colorPrimaryContainer) not found.\nerror: failed linking file resources.");
        List<String> advice = (List<String>) get(m3, "advice");
        check("Material 3 attribute explained", advice.size() >= 1 && advice.get(0).contains("Material 3"), advice);
        Object style = call(diag, "analyze", "main.xml:9: error: style/Widget.Material3.Button.TonalButton (aka x:style/Widget.Material3.Button.TonalButton) not found.");
        check("Material 3 style name explained", ((List<String>) get(style, "advice")).toString().contains("Widget.Material3.Button.TonalButton is a Material 3"), get(style, "advice"));
        Object level = call(diag, "analyze", "ERROR in /x/A.java (at line 1)\n\timport y.Z;\n\t^\nThe type y.Z is not compatible with the configured source level");
        check("source-level case has no empty version", ((List<String>) get(level, "advice")).toString().contains("newer Java")
                && !((List<String>) get(level, "advice")).toString().contains("version ;"), get(level, "advice"));
        Object name = call(diag, "analyze", "res/drawable/My-Image.png: error: 'My-Image' is not a valid file-based resource name character.");
        check("invalid file name explained", ((List<String>) get(name, "advice")).toString().contains("a-z, 0-9 and _"), get(name, "advice"));
        Object ecj = call(diag, "analyze", "ERROR in /x/MainActivity.java (at line 3)\n\timport x.Y;\n\t^^^\n"
                + "The type x.Y cannot be resolved. It is indirectly referenced from required .class files\nclass file has wrong version 65.0, should be 61.0");
        check("class file version explained", ((List<String>) get(ecj, "advice")).toString().contains("version 65"), get(ecj, "advice"));

        Class<?> doctor = c("com.sketchware.ai.doctor.BuildDoctor");
        String dup = "Type androidx.core.app.NotificationCompat is defined multiple times: "
                + "/storage/emulated/0/.sketchware/libs/local_libs/core_V_1.6.0/classes.dex, "
                + "/data/user/0/com.sketchware.ai/cache/libs/dexs/core-1.13.1.dex\n\n\nBuild map:\n    FAILED  DEX merge\n";
        Object d = call(doctor, "diagnose", dup);
        check("duplicate: both libraries named", String.valueOf(get(d, "found")).contains("local library core_V_1.6.0")
                && String.valueOf(get(d, "found")).contains("built-in library core-1.13.1"), get(d, "found"));
        check("duplicate: keep the higher version", String.valueOf(get(d, "found")).contains("Keep the higher one (core-1.13.1)"), get(d, "found"));
        String dup2 = "Type okhttp3.Call is defined multiple times: /storage/emulated/0/.sketchware/libs/local_libs/okhttp_V_4.12.0/classes.dex, "
                + "/storage/emulated/0/.sketchware/libs/local_libs/okhttp_V_3.14.9/classes.dex\n\n\nBuild map:\n    FAILED  DEX merge\n";
        Object d2 = call(doctor, "diagnose", dup2);
        check("duplicate: switch off the lower local one", String.valueOf(get(d2, "found")).contains("switch off okhttp_V_3.14.9"), get(d2, "found"));
        String dup3 = "Type okhttp3.Call is defined multiple times: /storage/emulated/0/.sketchware/libs/local_libs/okhttp_V_4.12.0/classes.dex, "
                + "/data/user/0/com.sketchware.ai/cache/libs/dexs/okhttp-3.12.12.dex\n\n\nBuild map:\n    FAILED  DEX merge\n";
        Object d3 = call(doctor, "diagnose", dup3);
        check("duplicate: older built-in stays, the local one goes", String.valueOf(get(d3, "found")).contains("the built-in one (okhttp-3.12.12) is older")
                && String.valueOf(get(d3, "found")).contains("switch off okhttp_V_4.12.0"), get(d3, "found"));
        check("versions compare numerically", (Integer) call(doctor, "compareVersions", "1.9.0", "1.13.1") < 0, null);

        // v2.0.2: the GitHub component's advice and the token warning
        String gh = "ERROR in /storage/emulated/0/.sketchware/mysc/934/app/src/main/java/com/my/app/MainActivity.java (at line 40)\n"
                + "\tprivate SketchGitHub github1;\n\t        ^^^^^^^^^^^^\nSketchGitHub cannot be resolved to a type\n"
                + "\n\nBuild map:\n    FAILED  Java compile\n";
        Object dg = call(doctor, "diagnose", gh);
        check("v2.0.2: SketchGitHub not resolved: add the GitHub component", String.valueOf(get(dg, "advice"))
                .contains("Components > + > GitHub"), get(dg, "advice"));
        Class<?> code = c("com.sketchware.ai.project.GitHubCode");
        Class<?> bean = c("com.besome.sketch.beans.SrcCodeBean");
        java.lang.reflect.Constructor<?> make = bean.getConstructor(String.class, String.class);
        List<Object> clean = new ArrayList<>();
        clean.add(make.newInstance("MainActivity.java", "github1.setToken(\"\");"));
        List<Object> classic = new ArrayList<>();
        classic.add(make.newInstance("MainActivity.java", "github1.setToken(\"ghp_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8\");"));
        List<Object> fine = new ArrayList<>();
        fine.add(make.newInstance("Second.java", "String t = \"github_pat_" + "11ABCDEFG0123456789_abcdefghij\";"));
        java.lang.reflect.Method warn = code.getMethod("tokenWarning", List.class);
        Object w1 = warn.invoke(null, classic), w2 = warn.invoke(null, fine);
        check("v2.0.2: token warning: classic and fine-grained tokens found (file named, 8 characters shown), none without",
                warn.invoke(null, clean) == null && String.valueOf(w1).startsWith("MainActivity.java holds a GitHub token (ghp_A1b2")
                        && !String.valueOf(w1).contains("C3d4E5") && String.valueOf(w2).startsWith("Second.java holds"), w1);

        OUT.println("DOCTORTEST " + ok + "/" + total + (ok == total ? " ok" : " FAILED"));
    }
}
