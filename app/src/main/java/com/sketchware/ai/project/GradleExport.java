package com.sketchware.ai.project;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import mod.hey.studios.build.BuildSettings;

/**
 * The Gradle files of a project exported for Android Studio (v1.8.5): the stack Sketchware AI itself is built with,
 * Android Gradle plugin 8.12 on Gradle 8.13 (JDK 17), instead of upstream's AGP 3.4.2 + jcenter, which current Android
 * Studio can't open. The settings are the ones the on-device build uses, so the exported project builds the same app:
 * <ul>
 * <li>{@code namespace} = the package (AGP 8 no longer reads the manifest's package attribute);</li>
 * <li>compileSdk 36, the highest AGP 8.12 supports; targetSdk and minSdk as in the project;</li>
 * <li>the project's Java version, core library desugaring with desugar_jdk_libs_nio 2.1.5 (the same library D8 adds
 * on the phone, {@link CoreLibDesugaring});</li>
 * <li>{@code android.nonTransitiveRClass=false} and {@code android.nonFinalResIds=false}: generated code reads library
 * resources through the app's R class and custom code may use R ids in switch statements, as with AAPT2 on the phone;</li>
 * <li>repositories Google Maven and Maven Central only (jcenter is read-only since 2021).</li>
 * </ul>
 */
public final class GradleExport {

    public static final String ANDROID_GRADLE_PLUGIN = "8.12.0";
    public static final String GRADLE = "8.13";
    /**
     * compileSdk of every exported project: the highest Android Gradle plugin 8.12 supports (material 1.13.0 and
     * appcompat 1.7.1 need 35 or higher). A higher targetSdk (37) is written as is.
     */
    public static final int COMPILE_SDK = 36;

    private GradleExport() {
    }

    public static int compileSdk() {
        return COMPILE_SDK;
    }

    public static String settingsGradle() {
        return "pluginManagement {\r\n" +
                "    repositories {\r\n" +
                "        google()\r\n" +
                "        mavenCentral()\r\n" +
                "        gradlePluginPortal()\r\n" +
                "    }\r\n" +
                "}\r\n" +
                "\r\n" +
                "dependencyResolutionManagement {\r\n" +
                "    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\r\n" +
                "    repositories {\r\n" +
                "        google()\r\n" +
                "        mavenCentral()\r\n" +
                "    }\r\n" +
                "}\r\n" +
                "\r\n" +
                "include ':app'\r\n";
    }

    public static String topLevelBuildGradle(String androidGradlePluginVersion) {
        return "// Top-level build file. Android Gradle plugin " + androidGradlePluginVersion + ", Gradle " + GRADLE
                + ", JDK 17: the stack Sketchware AI is built with.\r\n" +
                "plugins {\r\n" +
                "    id 'com.android.application' version '" + androidGradlePluginVersion + "' apply false\r\n" +
                "}\r\n";
    }

    /**
     * {@code compileOptions} block of app/build.gradle (inside {@code android { }}), indented by Lx.j. Core library
     * desugaring from min SDK 21 (below, AGP wants legacy multidex for it).
     */
    public static String compileOptions(String sc_id, int minSdk) {
        String java = javaVersion(sc_id);
        return "compileOptions {\r\n" +
                "sourceCompatibility JavaVersion." + java + "\r\n" +
                "targetCompatibility JavaVersion." + java + "\r\n" +
                (minSdk >= 21 ? "coreLibraryDesugaringEnabled true\r\n" : "") +
                "}\r\n";
    }

    public static String desugaringDependency(int minSdk) {
        if (minSdk < 21) return "";
        return "coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs_nio:" + CoreLibDesugaring.VERSION + "'\r\n";
    }

    /** Widget libraries (palette widgets of jcoderz/sayuti) that exist on Maven Central at the built-in versions. */
    private static final String[][] MAVEN_WIDGET_LIBRARIES = {
            {"lottie", "com.airbnb.android:lottie:3.7.2"},
            {"circleimageview", "de.hdodenhof:circleimageview:3.1.0"},
    };

    /**
     * Built-in widget libraries with no Maven artifact at the built-in version (YouTube player 10.0.5 and
     * PatternLockView 1.0.0 were on JCenter only, the others never were on a repository): the export writes them to
     * app/libs as .aar files made from the built-in copy (classes.jar, res, assets, manifest). name, package, the
     * Maven dependencies the library needs.
     */
    private static final String[][] LOCAL_WIDGET_LIBRARIES = {
            {"android-youtube-player-10.0.5", "com.pierfrancescosoffritti.androidyoutubeplayer",
                    "androidx.appcompat:appcompat:1.7.1|androidx.recyclerview:recyclerview:1.3.2"},
            {"OTPView-0.1.0", "affan.ahmad.otp",
                    "androidx.appcompat:appcompat:1.7.1|androidx.core:core-ktx:1.13.1|androidx.constraintlayout:constraintlayout:2.2.2"},
            {"code-view", "br.tiagohm.codeview", ""},
            {"pattern-lock-view", "com.andrognito.patternlockview", "androidx.core:core:1.13.1"},
            {"wave-side-bar", "com.sayuti.lib", ""},
    };

    private static boolean uses(a.a.a.jq metadata, String library) {
        mod.agus.jcoderz.handle.component.ConstVarComponent x = metadata.x;
        switch (library) {
            case "lottie":
                return x.isLottieUsed;
            case "circleimageview":
                return x.isCircleImageViewUsed;
            case "android-youtube-player-10.0.5":
                return x.isYoutubePlayerUsed;
            case "OTPView-0.1.0":
                return x.isOTPViewUsed;
            case "code-view":
                return x.isCodeViewUsed;
            case "pattern-lock-view":
                return x.isPatternLockViewUsed;
            case "wave-side-bar":
                return x.isWaveSideBarUsed;
            default:
                return false;
        }
    }

    /**
     * Dependencies besides the AppCompat/Material/Firebase ones Lx writes: the built-in AppCompat set has
     * SwipeRefreshLayout (Maven's appcompat/material don't), widget libraries from Maven, and what the local .aar
     * files need.
     */
    public static String extraDependencies(a.a.a.jq metadata) {
        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();
        if (metadata.g) lines.add("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0");
        for (String[] library : MAVEN_WIDGET_LIBRARIES) {
            if (uses(metadata, library[0])) lines.add(library[1]);
        }
        for (String[] library : LOCAL_WIDGET_LIBRARIES) {
            if (!uses(metadata, library[0]) || library[2].isEmpty()) continue;
            lines.addAll(java.util.Arrays.asList(library[2].split("\\|")));
        }
        // components Lx's list never had (the export didn't compile with them): the built-in versions
        mod.agus.jcoderz.handle.component.ConstVarComponent x = metadata.x;
        if (x.isFCMUsed) {
            lines.add("com.google.firebase:firebase-messaging:19.0.0");
            lines.add("com.google.firebase:firebase-iid:19.0.0"); // the generated code calls FirebaseInstanceId
        }
        if (x.isFBGoogleUsed) lines.add("com.google.android.gms:play-services-auth:19.0.0");
        if (x.isOneSignalUsed) lines.add("com.onesignal:OneSignal:3.14.0");
        if (x.isFBAdsUsed) lines.add("com.facebook.android:audience-network-sdk:5.9.0");
        StringBuilder out = new StringBuilder();
        for (String line : lines) out.append("implementation '").append(line).append("'\r\n");
        return out.toString();
    }

    /**
     * Writes app/libs/<name>.aar for every local widget library the project uses, from the IDE's built-in copy
     * (assets/libs/libs.zip). Only the export calls this.
     */
    public static void writeWidgetLibraries(File appDir, a.a.a.jq metadata) throws IOException {
        java.util.Map<String, String> wanted = new java.util.HashMap<>();
        for (String[] library : LOCAL_WIDGET_LIBRARIES) {
            if (uses(metadata, library[0])) wanted.put(library[0], library[1]);
        }
        if (wanted.isEmpty()) return;
        File libs = new File(appDir, "libs");
        if (!libs.isDirectory() && !libs.mkdirs()) throw new IOException("Can't create " + libs);
        java.util.Map<String, java.util.zip.ZipOutputStream> outputs = new java.util.HashMap<>();
        java.util.Set<String> withManifest = new java.util.HashSet<>();
        java.util.Set<String> withSymbols = new java.util.HashSet<>();
        // res/ files of each library, for an R.txt when the built-in copy has none
        java.util.Map<String, java.util.Map<String, byte[]>> resources = new java.util.HashMap<>();
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(
                com.besome.sketch.SketchApplication.getContext().getAssets().open("libs/libs.zip"))) {
            byte[] buffer = new byte[16384];
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                int slash = name.indexOf('/');
                if (entry.isDirectory() || slash <= 0) continue;
                String library = name.substring(0, slash);
                if (!wanted.containsKey(library)) continue;
                String path = name.substring(slash + 1);
                java.util.zip.ZipOutputStream out = outputs.get(library);
                if (out == null) {
                    out = new java.util.zip.ZipOutputStream(new FileOutputStream(new File(libs, library + ".aar")));
                    outputs.put(library, out);
                }
                if (path.equals("AndroidManifest.xml")) withManifest.add(library);
                if (path.equals("R.txt")) withSymbols.add(library);
                java.io.ByteArrayOutputStream content = path.startsWith("res/") ? new java.io.ByteArrayOutputStream() : null;
                out.putNextEntry(new java.util.zip.ZipEntry(path));
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    if (content != null) content.write(buffer, 0, n);
                }
                out.closeEntry();
                if (content != null) {
                    java.util.Map<String, byte[]> files = resources.get(library);
                    if (files == null) resources.put(library, files = new java.util.TreeMap<>());
                    files.put(path.substring("res/".length()), content.toByteArray());
                }
            }
            for (java.util.Map.Entry<String, java.util.zip.ZipOutputStream> output : outputs.entrySet()) {
                if (withSymbols.contains(output.getKey())) continue;
                // Without R.txt, AGP generates no R classes for the library and its code fails at run time
                // (NoClassDefFoundError: <package>.R$styleable): the names are listed here, AGP takes the values from
                // the app's resource table.
                java.util.Map<String, byte[]> files = resources.get(output.getKey());
                output.getValue().putNextEntry(new java.util.zip.ZipEntry("R.txt"));
                output.getValue().write(textSymbols(files == null ? new java.util.TreeMap<String, byte[]>() : files)
                        .getBytes(StandardCharsets.UTF_8));
                output.getValue().closeEntry();
            }
            for (java.util.Map.Entry<String, java.util.zip.ZipOutputStream> output : outputs.entrySet()) {
                if (!withManifest.contains(output.getKey())) {
                    output.getValue().putNextEntry(new java.util.zip.ZipEntry("AndroidManifest.xml"));
                    output.getValue().write(("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\""
                            + wanted.get(output.getKey()) + "\" />\n").getBytes(StandardCharsets.UTF_8));
                    output.getValue().closeEntry();
                }
            }
        } finally {
            for (java.util.zip.ZipOutputStream out : outputs.values()) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static final java.util.regex.Pattern NEW_ID = java.util.regex.Pattern.compile("@\\+id/([A-Za-z0-9_.]+)");

    /**
     * The resource names of a library's res/ folder in aapt2's R.txt format (values 0: an app's R classes for its
     * libraries take the names from the library's R.txt and the values from the app's own table).
     *
     * @param files paths below res/ ("values/attrs.xml", "drawable-v21/a.xml") and their content
     */
    static String textSymbols(java.util.Map<String, byte[]> files) {
        java.util.Set<String> symbols = new java.util.TreeSet<>();
        java.util.Map<String, java.util.List<String>> styleables = new java.util.TreeMap<>();
        for (java.util.Map.Entry<String, byte[]> file : files.entrySet()) {
            String path = file.getKey();
            int slash = path.indexOf('/');
            if (slash <= 0) continue;
            String folder = path.substring(0, slash);
            String type = folder.contains("-") ? folder.substring(0, folder.indexOf('-')) : folder;
            String fileName = path.substring(slash + 1);
            String text = new String(file.getValue(), StandardCharsets.UTF_8);
            if (fileName.endsWith(".xml")) {
                java.util.regex.Matcher id = NEW_ID.matcher(text);
                while (id.find()) symbols.add("id " + id.group(1).replace('.', '_'));
            }
            if (!type.equals("values")) {
                String name = fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName;
                symbols.add(type + " " + name);
                continue;
            }
            try {
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                org.w3c.dom.Element root = factory.newDocumentBuilder()
                        .parse(new java.io.ByteArrayInputStream(file.getValue())).getDocumentElement();
                for (org.w3c.dom.Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                    if (!(node instanceof org.w3c.dom.Element)) continue;
                    org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                    String tag = element.getTagName();
                    String name = element.getAttribute("name").replace('.', '_');
                    if (name.isEmpty() || name.startsWith("android:")) continue;
                    switch (tag) {
                        case "declare-styleable": {
                            java.util.List<String> attrs = new java.util.ArrayList<>();
                            for (org.w3c.dom.Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                                if (!(child instanceof org.w3c.dom.Element) || !((org.w3c.dom.Element) child).getTagName().equals("attr")) continue;
                                org.w3c.dom.Element attr = (org.w3c.dom.Element) child;
                                String attrName = attr.getAttribute("name");
                                if (attrName.isEmpty()) continue;
                                attrs.add(attrName.replace(':', '_'));
                                // an attr with a format or values is defined here; without, it names one defined elsewhere
                                if (!attrName.contains(":") && (!attr.getAttribute("format").isEmpty() || attr.hasChildNodes())) {
                                    symbols.add("attr " + attrName);
                                }
                                enumIds(attr, symbols);
                            }
                            // aapt2's order: by attribute id, i.e. android: attributes first, then by name
                            java.util.Collections.sort(attrs, new java.util.Comparator<String>() {
                                @Override
                                public int compare(String one, String two) {
                                    boolean androidOne = one.startsWith("android_"), androidTwo = two.startsWith("android_");
                                    if (androidOne != androidTwo) return androidOne ? -1 : 1;
                                    return one.compareTo(two);
                                }
                            });
                            styleables.put(name, attrs);
                            break;
                        }
                        case "item":
                            if (!element.getAttribute("type").isEmpty()) symbols.add(element.getAttribute("type") + " " + name);
                            break;
                        case "string-array":
                        case "integer-array":
                        case "array":
                            symbols.add("array " + name);
                            break;
                        case "eat-comment":
                        case "public":
                        case "skip":
                            break;
                        case "attr":
                            symbols.add("attr " + name);
                            enumIds(element, symbols);
                            break;
                        default: // color, dimen, string, style, integer, bool, plurals, fraction, drawable, id
                            symbols.add(tag + " " + name);
                    }
                }
            } catch (Exception e) {
                // an unreadable values file: its names are left out (AGP then has no R field for them)
            }
        }
        StringBuilder out = new StringBuilder();
        for (String symbol : symbols) out.append("int ").append(symbol).append(" 0x0\n");
        for (java.util.Map.Entry<String, java.util.List<String>> styleable : styleables.entrySet()) {
            out.append("int[] styleable ").append(styleable.getKey()).append(" {");
            for (int i = 0; i < styleable.getValue().size(); i++) out.append(i == 0 ? " " : ", ").append("0x0");
            out.append(" }\n");
            for (int i = 0; i < styleable.getValue().size(); i++) {
                out.append("int styleable ").append(styleable.getKey()).append('_').append(styleable.getValue().get(i))
                        .append(' ').append(i).append('\n');
            }
        }
        return out.toString();
    }

    /** aapt2 makes an id resource of every enum/flag value name of an attr. */
    private static void enumIds(org.w3c.dom.Element attr, java.util.Set<String> symbols) {
        for (org.w3c.dom.Node child = attr.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof org.w3c.dom.Element)) continue;
            String tag = ((org.w3c.dom.Element) child).getTagName();
            String name = ((org.w3c.dom.Element) child).getAttribute("name");
            if ((tag.equals("enum") || tag.equals("flag")) && !name.isEmpty()) symbols.add("id " + name);
        }
    }

    /** The project's Java version (Build settings) as a Gradle JavaVersion constant; 1.7 builds as 1.8. */
    static String javaVersion(String sc_id) {
        String value;
        try {
            value = new BuildSettings(sc_id).getValue(BuildSettings.SETTING_JAVA_VERSION,
                    ModernProjectDefaults.defaultJavaVersion());
        } catch (RuntimeException e) {
            value = ModernProjectDefaults.defaultJavaVersion();
        }
        return javaVersionConstant(value);
    }

    static String javaVersionConstant(String value) {
        if (value == null) return "VERSION_11";
        switch (value.trim()) {
            case BuildSettings.SETTING_JAVA_VERSION_1_7:
            case BuildSettings.SETTING_JAVA_VERSION_1_8:
                return "VERSION_1_8";
            case BuildSettings.SETTING_JAVA_VERSION_1_9:
                return "VERSION_1_9";
            case BuildSettings.SETTING_JAVA_VERSION_10:
                return "VERSION_1_10";
            default:
                return "VERSION_11";
        }
    }

    public static String gradleProperties() {
        return "# Written by Sketchware AI\r\n" +
                "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\r\n" +
                "android.useAndroidX=true\r\n" +
                "# Generated code reads library resources through the app's R class (as with AAPT2 on the phone)\r\n" +
                "android.nonTransitiveRClass=false\r\n" +
                "# Custom code may use R ids as switch case labels\r\n" +
                "android.nonFinalResIds=false\r\n";
    }

    public static String wrapperProperties() {
        return "distributionBase=GRADLE_USER_HOME\r\n" +
                "distributionPath=wrapper/dists\r\n" +
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-" + GRADLE + "-bin.zip\r\n" +
                "zipStoreBase=GRADLE_USER_HOME\r\n" +
                "zipStorePath=wrapper/dists\r\n";
    }

    /** gradle.properties and gradle/wrapper/gradle-wrapper.properties next to settings.gradle. */
    public static void writeProjectFiles(File projectDir) {
        write(new File(projectDir, "gradle.properties"), gradleProperties());
        write(new File(projectDir, "gradle" + File.separator + "wrapper" + File.separator + "gradle-wrapper.properties"),
                wrapperProperties());
    }

    /**
     * The exported AndroidManifest.xml without the package attribute of its manifest tag: AGP 8 takes the package from
     * {@code namespace} and warns about the attribute. Only the export uses this; AAPT2 on the phone needs it.
     */
    public static String withoutPackageAttribute(String manifest) {
        int start = manifest.indexOf("<manifest");
        if (start < 0) return manifest;
        int end = manifest.indexOf('>', start);
        if (end < 0) return manifest;
        String tag = manifest.substring(start, end);
        String stripped = tag.replaceFirst("\\s+package\\s*=\\s*\"[^\"]*\"", "");
        return manifest.substring(0, start) + stripped + manifest.substring(end);
    }

    /** Rewrites an exported AndroidManifest.xml without its package attribute ({@link #withoutPackageAttribute}). */
    public static void stripPackageAttribute(File manifest) {
        if (!manifest.isFile()) return;
        try {
            String content = read(manifest);
            String stripped = withoutPackageAttribute(content);
            if (!stripped.equals(content)) write(manifest, stripped);
        } catch (IOException | RuntimeException ignored) {
            // the manifest stays as generated; AGP only warns about the attribute
        }
    }

    private static String read(File file) throws IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void write(File file, String content) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
        File temp = new File(file.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            temp.delete();
            return;
        }
        if (!temp.renameTo(file)) {
            file.delete();
            if (!temp.renameTo(file)) temp.delete();
        }
    }
}
