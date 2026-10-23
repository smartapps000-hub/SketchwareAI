package h;

import java.util.*;

import static h.R.*;

/**
 * Fixture projects written with the IDE's own project classes, the way MyProjectSettingActivity creates a project
 * and DesignActivity.ProjectSaver saves it: lC.a(sc_id, metadata), data folder emptied, ModernProjectDefaults,
 * then jC managers filled and saved with kC.a(); hC.m(); eC.j(); kC.x(); iC.l().
 *
 * Specs:
 *  libs  - AppCompat + every widget type of the palette that needs no Firebase/AdMob/Maps, toolbar + FAB + drawer,
 *          RequestNetwork (OkHttp + Gson), Camera, FilePicker, TimerTask, Dialog, Notification, Glide (setImageUrl),
 *          Gson (strToMap): every updated built-in library is compiled, linked and dexed.
 *  held  - AppCompat + Firebase + AdMob + Google Maps, their components and widgets: the libraries that were NOT
 *          updated must still build on top of the updated AndroidX.
 *  plain - no AppCompat (framework Activity), a few framework widgets.
 *  m3    - (v1.8.4) the libs widgets with the Material 3 AppTheme (the new-project default) and a block that uses
 *          Material 1.13 classes (DynamicColors, MaterialSwitch, Chip, MaterialAlertDialogBuilder).
 *  telugu- (v1.8.4) AppCompat, TextView/Button/EditText, two project fonts (a TrueType font and an OpenType/CFF font
 *          stored as name.ttf, as the font manager stores every font) set by font blocks: SketchFonts.
 *  jni   - (v1.8.4) AppCompat; the project's own native libraries (arm64-v8a 4 KB-aligned, x86_64 16 KB) and an enabled
 *          local library with classes.jar/classes.dex and jni/ libraries (arm64-v8a 16 KB, x86_64 4 KB), called from a
 *          block: NativeAlignment warnings, and the AAB must carry the local library's jni/ files too.
 *          Files from make-native-fixture.sh (system property h.nativefx).
 *  desugar- (v1.8.5) AppCompat, min SDK 24 (Android 7.0), a Java file of the Java manager (DesugarHelper) that uses
 *          Java 8-16 library APIs Android 7.0 doesn't have (java.time, java.nio.file, Map.of, Stream.toList,
 *          String.repeat/strip, InputStream.transferTo), called from a block: D8 must rewrite them to the desugared
 *          library (j$.*) and the build must add its DEX (L8). CallDesugar runs DesugarHelper.run() from the APK.
 * held keeps the MaterialComponents (Bridge) theme of projects made before v1.8.4.
 */
public class Fixtures {
    static final int LINEAR = 0;

    /** What the palette's getBean() gives a new widget: image, text, and for the library widgets convert/inject/size. */
    static void defaults(Object vb, int type, boolean material3) throws Exception {
        set(get(vb, "image"), "resName", "default_image");
        set(get(vb, "text"), "text", get(vb, "id"));
        for (Object[] row : IconDefaults.ROWS) {
            if ((Integer) row[0] != type) continue;
            set(vb, "convert", row[1]);
            set(vb, "inject", row[2]);
            if ((Integer) row[3] != 0) set(get(vb, "layout"), "width", row[3]);
            if ((Integer) row[4] != 0) set(get(vb, "layout"), "height", row[4]);
        }
        // In a Material 3 project the palette (DesignTheme.material3()) gives these two widgets theme styles
        // (IconMaterialButton / IconTextInputLayout since v1.8.4).
        if (material3 && type == 41) set(vb, "inject", IconDefaults.M3_MATERIALBUTTON);
        if (material3 && type == 38) set(vb, "inject", IconDefaults.M3_TEXTINPUTLAYOUT);
    }

    /** Expected result of DesugarHelper.run(): 2026-11-01|120|2|X,Y,Z|Y|abab|q|y|3 */
    static final String DESUGAR_HELPER = "package com.fixture.desugar;\n"
            + "\n"
            + "import java.io.ByteArrayInputStream;\n"
            + "import java.io.ByteArrayOutputStream;\n"
            + "import java.nio.file.Path;\n"
            + "import java.nio.file.Paths;\n"
            + "import java.time.Duration;\n"
            + "import java.time.LocalDate;\n"
            + "import java.time.format.DateTimeFormatter;\n"
            + "import java.util.List;\n"
            + "import java.util.Map;\n"
            + "import java.util.Optional;\n"
            + "import java.util.stream.Collectors;\n"
            + "import java.util.stream.Stream;\n"
            + "\n"
            + "/** Java 8-16 library APIs newer than Android 7.0 (API 24): the build desugars them. */\n"
            + "public class DesugarHelper {\n"
            + "    public static String run() throws Exception {\n"
            + "        LocalDate date = LocalDate.of(2026, 10, 22).plusDays(10); // java.time: API 26\n"
            + "        String day = date.format(DateTimeFormatter.ISO_LOCAL_DATE);\n"
            + "        long seconds = Duration.ofMinutes(2).getSeconds(); // API 26\n"
            + "        Map<String, Integer> map = Map.of(\"a\", 1, \"b\", 2); // Java 9: API 30\n"
            + "        List<String> upper = Stream.of(\"x\", \"y\", \"z\").map(String::toUpperCase).toList(); // Java 16: API 34\n"
            + "        Optional<String> first = upper.stream().filter(s -> s.startsWith(\"Y\")).findFirst();\n"
            + "        String joined = upper.stream().collect(Collectors.joining(\",\"));\n"
            + "        String repeated = \"ab\".repeat(2); // Java 11: API 33\n"
            + "        String stripped = \"  q \".strip(); // Java 11: API 33\n"
            + "        Path path = Paths.get(\"/data\", \"y\"); // java.nio.file: API 26\n"
            + "        ByteArrayOutputStream out = new ByteArrayOutputStream();\n"
            + "        long copied = new ByteArrayInputStream(new byte[]{1, 2, 3}).transferTo(out); // Java 9: API 33\n"
            + "        return day + \"|\" + seconds + \"|\" + map.get(\"b\") + \"|\" + joined + \"|\" + first.orElse(\"-\")\n"
            + "                + \"|\" + repeated + \"|\" + stripped + \"|\" + path.getFileName() + \"|\" + copied;\n"
            + "    }\n"
            + "}\n";

    static void copy(java.io.File from, java.io.File to) throws java.io.IOException {
        to.getParentFile().mkdirs();
        java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static void create(String spec, String sc_id) throws Throwable {
        Class<?> jC = c("a.a.a.jC"), wq = c("a.a.a.wq"), lC = c("a.a.a.lC");
        HashMap<String, Object> data = new HashMap<>();
        data.put("sc_id", sc_id);
        data.put("my_sc_pkg_name", "com.fixture." + spec);
        data.put("my_ws_name", "Fixture_" + spec);
        data.put("my_app_name", "Fixture " + spec);
        data.put("my_sc_reg_dt", "20261021000000");
        data.put("custom_icon", false);
        data.put("sc_ver_code", "1");
        data.put("sc_ver_name", "1.0");
        data.put("sketchware_ver", 150);
        data.put("color_accent", 0xFFF4511E);
        data.put("color_primary", 0xFFF4511E);
        data.put("color_primary_dark", 0xFFBF360C);
        data.put("color_control_highlight", 0x20F4511E);
        data.put("color_control_normal", 0xFF757575);
        call(lC, "a", sc_id, data);
        call(wq, "a", Main.CTX, sc_id);
        call(neu("a.a.a.oB"), "b", call(wq, "b", sc_id));
        call(c("com.sketchware.ai.project.ModernProjectDefaults"), "applyToNewProject", sc_id);
        call(jC, "a");

        Object files = call(jC, "b", sc_id);
        Object libs = call(jC, "c", sc_id);
        Object proj = call(jC, "a", sc_id);
        Object res = call(jC, "d", sc_id);

        @SuppressWarnings("unchecked") List<Object> activities = (List<Object>) call(files, "b");
        Main.OUT.println("activities after creation: " + activities.size());
        Object main = activities.get(0);
        String javaName = (String) call(main, "getJavaName");
        String xmlName = (String) call(main, "getXmlName");
        Main.OUT.println("main: " + javaName + " / " + xmlName);

        Object compat = call(libs, "c");
        set(compat, "useYn", spec.equals("plain") ? "N" : "Y");
        if (spec.equals("held")) {
            Object firebase = call(libs, "d");
            set(firebase, "useYn", "Y");
            set(firebase, "data", "fixture-held-default-rtdb.firebaseio.com");
            set(firebase, "reserved1", "1:123456789012:android:0123456789abcdef");
            set(firebase, "reserved2", "AIzaSyFixtureFixtureFixtureFixture12345");
            set(firebase, "reserved3", "fixture-held.appspot.com");
            Object admob = call(libs, "b");
            set(admob, "useYn", "Y");
            Object maps = call(libs, "e");
            set(maps, "useYn", "Y");
            set(maps, "data", "AIzaSyFixtureMapsKeyFixtureFixture1234");
        }
        if (!spec.equals("plain")) {
            // toolbar + FAB + drawer, as the activity options dialog sets them
            int opts = (Integer) get(main, "options");
            set(main, "options", opts | 1 | 8 | 4);
            // the drawer's own layout file, as the activity options dialog adds it (fileType 2)
            ((List<Object>) call(files, "c")).add(neu("com.besome.sketch.beans.ProjectFileBean", 2, "_drawer_main"));
        }

        boolean libsLike = spec.equals("libs") || spec.equals("min19") || spec.equals("m3");
        List<int[]> widgets = new ArrayList<>();
        if (libsLike) {
            int[] types = {2, 12, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                    30, 31, 32, 34, 35, 36, 37, 38, 39, 40, 41, 43, 44, 45, 46, 47, 48};
            for (int t : types) widgets.add(new int[]{t});
        } else if (spec.equals("held")) {
            for (int t : new int[]{3, 4, 6, 17, 18, 42}) widgets.add(new int[]{t});
        } else if (spec.equals("telugu")) {
            for (int t : new int[]{3, 4, 5}) widgets.add(new int[]{t}); // Button, TextView, EditText
        } else if (spec.equals("jni")) {
            for (int t : new int[]{3, 4}) widgets.add(new int[]{t});
        } else if (spec.equals("m3code")) {
            // Button, TextView, EditText x2, CheckBox, Switch, RadioButton (v1.8.5 code scan, M3Widgets.scanCode)
            for (int[] t : new int[][]{{3}, {4}, {5}, {5, 2}, {11}, {13}, {19}}) widgets.add(t);
        } else if (spec.equals("m3dark")) {
            // v1.8.6: Button x4 (default, blue, yellow, pink read from code), TextView, EditText, CheckBox, Switch
            for (int[] t : new int[][]{{3}, {3, 2}, {3, 3}, {3, 4}, {4}, {5}, {11}, {13}}) widgets.add(t);
        } else {
            for (int t : new int[]{3, 4, 5, 6, 9, 11}) widgets.add(new int[]{t});
        }
        // the root linear the widgets live in
        Object root = neu("com.besome.sketch.beans.ViewBean", "linear1", LINEAR);
        set(root, "parent", "root");
        set(root, "parentType", LINEAR);
        set(root, "index", 0);
        call(proj, "a", xmlName, root); // eC.a(String xml, ViewBean) adds; eC.a(ProjectFileBean, ViewBean) removes
        int index = 0;
        for (int[] w : widgets) {
            Object vb = neu("com.besome.sketch.beans.ViewBean", (String) call(wq, "b", w[0]) + (w.length > 1 ? w[1] : 1), w[0]);
            defaults(vb, w[0], spec.equals("m3") || spec.equals("desugar") || spec.equals("m3code") || spec.equals("m3dark"));
            if (spec.equals("m3dark")) {
                Object layout = get(vb, "layout");
                String id = (String) get(vb, "id");
                // the background colour property: opaque colours -> tinted SketchButton (M3Widgets.tintedButton)
                if (id.equals("button2")) set(layout, "backgroundColor", 0xFF2196F3);
                if (id.equals("button3")) set(layout, "backgroundColor", 0xFFFFEB3B);
                if (id.equals("button4")) set(layout, "backgroundColor", 0xFFE91E63);
                if (id.equals("edittext1")) {
                    // the owner's project #659 field: 8dp padding, a hint wider than the text, 12sp
                    set(get(vb, "text"), "hint", "Telugu language title create");
                    for (String side : new String[]{"paddingLeft", "paddingTop", "paddingRight", "paddingBottom"}) set(layout, side, 8);
                }
            }
            set(vb, "parent", "linear1");
            set(vb, "parentType", LINEAR);
            set(vb, "index", index++);
            call(proj, "a", xmlName, vb);
        }

        List<Object> comps = new ArrayList<>();
        if (libsLike) {
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 17, "net"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 15, "cam"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 16, "picker"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 5, "timer"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 7, "dialog"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 26, "notify"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 2, "prefs"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 1, "intent"));
        } else if (spec.equals("held")) {
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 6, "db", "users"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 12, "auth"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 14, "store", "files"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 13, "inter"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 22, "reward"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 28, "phone"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 30, "fcm"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 31, "google"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 29, "links"));
            comps.add(neu("com.besome.sketch.beans.ComponentBean", 17, "net"));
        }
        @SuppressWarnings("unchecked") HashMap<String, ArrayList<Object>> compMap = (HashMap<String, ArrayList<Object>>) get(proj, "h");
        compMap.computeIfAbsent(javaName, k -> new ArrayList<>()).addAll(comps);

        String source = null;
        if (spec.equals("kotlin")) {
            // a Kotlin file in the Java/Kotlin manager folder, called from a block: kotlinc (1.7.10) against kotlin-stdlib 1.8.22
            java.io.File dir = new java.io.File((String) call(neu("mod.agus.jcoderz.lib.FilePathUtil"), "getPathJava", sc_id));
            dir.mkdirs();
            java.nio.file.Files.write(new java.io.File(dir, "KtHelper.kt").toPath(), ("package com.fixture.kotlin\n\n"
                    + "object KtHelper {\n"
                    + "    fun greet(names: List<String>): String =\n"
                    + "        names.filter { it.isNotBlank() }.joinToString(\", \") { it.uppercase() }\n"
                    + "}\n").getBytes("UTF-8"));
            source = "String greeting = com.fixture.kotlin.KtHelper.INSTANCE.greet(java.util.Arrays.asList(\"a\", \" \", \"b\"));\nandroid.util.Log.d(\"kt\", greeting);";
        } else if (spec.equals("autofix")) {
            // the #639 shape: a statement after a try/catch that leaves the method on every path (ECJ: Unreachable code)
            source = "try {\n    android.util.Log.d(\"fixture\", \"try\");\n    return;\n} catch (Exception e) {\n    return;\n}\nandroid.util.Log.d(\"fixture\", \"after\");";
        } else if (spec.equals("typo")) {
            source = "String label = \"abc\";\nint n = label.lenght();";
        } else if (spec.equals("jni")) {
            source = "android.util.Log.d(\"native\", com.fixture.nativelib.Native.hello());";
        } else if (spec.equals("desugar")) {
            java.io.File dir = new java.io.File((String) call(neu("mod.agus.jcoderz.lib.FilePathUtil"), "getPathJava", sc_id));
            dir.mkdirs();
            java.nio.file.Files.write(new java.io.File(dir, "DesugarHelper.java").toPath(), DESUGAR_HELPER.getBytes("UTF-8"));
            source = "try {\n    android.util.Log.d(\"desugar\", com.fixture.desugar.DesugarHelper.run());\n"
                    + "} catch (Exception e) {\n    android.util.Log.e(\"desugar\", \"failed\", e);\n}";
        } else if (spec.equals("m3code")) {
            // code written for the framework widgets: switch1 typed Switch, edittext1's LinearLayout params,
            // checkbox1's own button drawable stay framework widgets; edittext2 (outlined) gets a string-resource hint;
            // button1 (SketchButton) gets a background from code
            source = "android.widget.Switch typed = switch1;\n"
                    + "typed.setChecked(true);\n"
                    + "((android.widget.LinearLayout.LayoutParams) edittext1.getLayoutParams()).topMargin = 8;\n"
                    + "checkbox1.setButtonDrawable(android.R.drawable.checkbox_off_background);\n"
                    + "edittext2.setHint(android.R.string.ok);\n"
                    + "edittext2.setHint(\"Your name\");\n"
                    + "button1.setBackgroundResource(android.R.drawable.btn_default);";
        } else if (spec.equals("m3dark")) {
            // button4's background read as a ColorDrawable: it must stay a plain Button (M3Widgets.scanCode)
            source = "android.graphics.drawable.Drawable bg4 = button4.getBackground();\n"
                    + "if (bg4 instanceof android.graphics.drawable.ColorDrawable) {\n"
                    + "    android.util.Log.d(\"m3dark\", \"button4 \" + ((android.graphics.drawable.ColorDrawable) bg4).getColor());\n"
                    + "}\n"
                    + "button2.setBackgroundColor(0xFF3F51B5);";
        } else if (spec.equals("m3")) {
            // Material 1.13 API that needs the android-34 compile platform and the Material 3 theme
            source = "com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);\n"
                    + "com.google.android.material.materialswitch.MaterialSwitch m3switch = new com.google.android.material.materialswitch.MaterialSwitch(this);\n"
                    + "com.google.android.material.chip.Chip m3chip = new com.google.android.material.chip.Chip(this);\n"
                    + "m3chip.setText(\"Material 3\");\n"
                    + "new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(\"M3\").create();\n"
                    + "android.util.Log.d(\"m3\", String.valueOf(m3switch.isChecked()) + m3chip.getText());";
        }
        if (source != null) {
            ArrayList<Object> blocks = new ArrayList<>();
            Object b = neu("com.besome.sketch.beans.BlockBean", "10", "add source directly %s.inputOnly", " ", "addSourceDirectly");
            ((List<String>) get(b, "parameters")).add(source);
            blocks.add(b);
            call(proj, "a", javaName, "onCreate_initializeLogic", blocks);
        }
        if (libsLike) {
            ArrayList<Object> blocks = new ArrayList<>();
            Object glide = neu("com.besome.sketch.beans.BlockBean", "10", "%m.imageview set image from url %s", " ", "setImageUrl");
            ((List<String>) get(glide, "parameters")).addAll(Arrays.asList("imageview1", "https://example.com/a.png"));
            set(glide, "nextBlock", 11);
            Object toast = neu("com.besome.sketch.beans.BlockBean", "11", "Toast %s", " ", "doToast");
            ((List<String>) get(toast, "parameters")).add("hello");
            blocks.add(glide);
            blocks.add(toast);
            if (spec.equals("m3")) {
                // v1.8.5: setHint on an EditText shown as an outlined field goes to the field's label (SketchTextField.hint)
                set(toast, "nextBlock", 12);
                Object hint = neu("com.besome.sketch.beans.BlockBean", "12", "%m.edittext setHint %s", " ", "setHint");
                ((List<String>) get(hint, "parameters")).addAll(Arrays.asList("edittext1", "Your name"));
                blocks.add(hint);
            }
            call(proj, "a", javaName, "onCreate_initializeLogic", blocks);
        }

        if (spec.equals("telugu")) {
            // fonts as the font manager stores them: <resources/fonts/<sc_id>>/<name>.ttf whatever the format
            String fontsDir = (String) get(res, "g");
            new java.io.File(fontsDir).mkdirs();
            String src = System.getProperty("h.fonts"); // tools/art-harness/make-test-fonts.py output
            java.nio.file.Files.copy(new java.io.File(src, "ramabhadra_tight.ttf").toPath(), new java.io.File(fontsDir, "ramabhadra.ttf").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(new java.io.File(src, "tenali.otf").toPath(), new java.io.File(fontsDir, "tenali.ttf").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            @SuppressWarnings("unchecked") List<Object> fonts = (List<Object>) get(res, "d");
            fonts.add(neu("com.besome.sketch.beans.ProjectResourceBean", 1, "ramabhadra", "ramabhadra.ttf"));
            fonts.add(neu("com.besome.sketch.beans.ProjectResourceBean", 1, "tenali", "tenali.ttf"));
            ArrayList<Object> blocks = new ArrayList<>();
            Object tf1 = neu("com.besome.sketch.beans.BlockBean", "10", "%m.textview setTypeface %m.font %m.typeface", " ", "setTypeface");
            ((List<String>) get(tf1, "parameters")).addAll(Arrays.asList("textview1", "ramabhadra", "normal"));
            set(tf1, "nextBlock", 11);
            Object tf2 = neu("com.besome.sketch.beans.BlockBean", "11", "%m.textview setTypeface %m.font %m.typeface", " ", "setTypeface");
            ((List<String>) get(tf2, "parameters")).addAll(Arrays.asList("button1", "tenali", "bold"));
            set(tf2, "nextBlock", 12);
            Object text = neu("com.besome.sketch.beans.BlockBean", "12", "%m.textview setText %s", " ", "setText");
            ((List<String>) get(text, "parameters")).addAll(Arrays.asList("textview1", "\u0c05\u0c28\u0c41 \u0c2b\u0c3e\u0c02\u0c1f\u0c4d\u0c38\u0c4d"));
            blocks.add(tf1);
            blocks.add(tf2);
            blocks.add(text);
            call(proj, "a", javaName, "onCreate_initializeLogic", blocks);
        }
        if (spec.equals("jni")) {
            java.io.File fx = new java.io.File(System.getProperty("h.nativefx"));
            java.io.File own = new java.io.File((String) call(neu("mod.agus.jcoderz.lib.FilePathUtil"), "getPathNativelibs", sc_id));
            copy(new java.io.File(fx, "arm64-v8a/lib4k.so"), new java.io.File(own, "arm64-v8a/libproj4k.so"));
            copy(new java.io.File(fx, "x86_64/lib16k.so"), new java.io.File(own, "x86_64/libproj16k.so"));
            java.io.File lib = new java.io.File((java.io.File) call(c("com.sketchware.ai.project.LibraryHeal"), "localLibs"), "nativelib_V_1.0");
            copy(new java.io.File(fx, "classes.jar"), new java.io.File(lib, "classes.jar"));
            copy(new java.io.File(fx, "classes.dex"), new java.io.File(lib, "classes.dex"));
            copy(new java.io.File(fx, "arm64-v8a/lib16k.so"), new java.io.File(lib, "jni/arm64-v8a/libnative16k.so"));
            copy(new java.io.File(fx, "x86_64/lib4k.so"), new java.io.File(lib, "jni/x86_64/libnative4k.so"));
            HashMap<String, Object> entry = new HashMap<>();
            entry.put("name", "nativelib_V_1.0");
            entry.put("jarPath", new java.io.File(lib, "classes.jar").getAbsolutePath());
            entry.put("dexPath", new java.io.File(lib, "classes.dex").getAbsolutePath());
            ArrayList<HashMap<String, Object>> list = new ArrayList<>();
            list.add(entry);
            call(c("com.sketchware.ai.project.LibraryHeal"), "save", sc_id, list);
        }
        if (spec.equals("m3dark")) {
            // v1.8.6: dynamic colour on (Library Manager); the dark theme is the new-project default
            Object ps = neu("mod.hey.studios.project.ProjectSettings", sc_id);
            call(ps, "putValue", "m3_dynamic_color", "true");
            call(ps, "saveNow");
        }
        if (spec.equals("m3code")) {
            // v1.8.6: a Material 3 project of v1.8.4/v1.8.5 never stored the dark theme setting: it stays light
            Object ps = neu("mod.hey.studios.project.ProjectSettings", sc_id);
            call(ps, "putValue", "m3_dark_theme", "");
            call(ps, "saveNow");
        }
        if (spec.equals("held")) {
            // a project made before v1.8.4: no stored Material 3 choice, the MaterialComponents (Bridge) theme
            Object ps = neu("mod.hey.studios.project.ProjectSettings", sc_id);
            call(ps, "putValue", "material3_theme", "false");
            call(ps, "saveNow");
        }

        call(res, "a");
        call(files, "m");
        call(proj, "j");
        call(res, "x");
        call(libs, "l");
        if (spec.equals("desugar")) {
            Object ps = neu("mod.hey.studios.project.ProjectSettings", sc_id);
            call(ps, "putValue", "min_sdk", "24");
            call(ps, "saveNow");
        }
        if (spec.equals("min19")) {
            // below 21: multidex + every DEX merged by the IDE's D8 (the debug path at 21+ adds them unmerged)
            Object ps = neu("mod.hey.studios.project.ProjectSettings", sc_id);
            call(ps, "putValue", "min_sdk", "19");
            call(ps, "saveNow");
        }
        Main.OUT.println("CREATED " + spec + " as #" + sc_id + ": " + widgets.size() + " widgets, " + comps.size() + " components");
    }
}
