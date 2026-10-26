package com.sketchware.ai.library;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.agus.jcoderz.lib.FileUtil;

/**
 * v1.8.9: what the Library Resolver's (i) shows about an installed library, read only from files on the phone: the
 * library's POM (kept in its folder as pom.xml since v1.8.9, else the resolver's cache, plus one cached parent for the
 * licence), its AndroidManifest.xml (permissions it adds to the app) and its classes.jar (packages, the import line).
 * Nothing is downloaded.
 */
public final class LibraryInfo {

    public String coordinate;
    public String name;
    public String description;
    public String url;
    public final List<String> licences = new ArrayList<>();
    public final List<String> permissions = new ArrayList<>();
    public final List<String> packages = new ArrayList<>();
    public int classes;
    public boolean pomFound;

    private LibraryInfo() {
    }

    public static LibraryInfo read(File dir, String coordinate, File cache) {
        LibraryInfo info = new LibraryInfo();
        info.coordinate = coordinate;
        String pom = text(new File(dir, "pom.xml"));
        String[] gav = coordinate == null ? null : coordinate.split(":");
        if (pom == null && gav != null && gav.length >= 3 && cache != null) {
            pom = text(new File(cache, MavenResolver.path(gav[0], gav[1], gav[2], "pom")));
        }
        if (pom != null) {
            info.pomFound = true;
            String own = withoutBlocks(pom);
            info.name = tag(own, "name");
            info.description = tag(own, "description");
            info.url = tag(own, "url");
            licences(pom, info.licences);
            if (info.licences.isEmpty() && cache != null) {
                // one parent up, if it is in the cache
                String parent = block(pom, "parent");
                if (parent != null) {
                    String g = tag(parent, "groupId"), a = tag(parent, "artifactId"), v = tag(parent, "version");
                    if (g != null && a != null && v != null) {
                        String parentPom = text(new File(cache, MavenResolver.path(g, a, v, "pom")));
                        if (parentPom != null) licences(parentPom, info.licences);
                    }
                }
            }
        }
        String manifest = text(new File(dir, "AndroidManifest.xml"));
        if (manifest != null) {
            Matcher m = Pattern.compile("<uses-permission(?:-sdk-23)?\\b[^>]*?android:name\\s*=\\s*\"([^\"]+)\"").matcher(manifest);
            Set<String> seen = new LinkedHashSet<>();
            while (m.find()) seen.add(m.group(1));
            info.permissions.addAll(seen);
        }
        Map<String, Set<String>> packages = LibraryImports.packages(new File(dir, "classes.jar"));
        for (Set<String> names : packages.values()) info.classes += names.size();
        info.packages.addAll(packages.keySet());   // every package: import a.b.* doesn't reach a.b.c
        return info;
    }

    /**
     * v1.8.11: what a library card shows: {name, project link}. The name is the POM's &lt;name&gt; ("ESCPOS Thermal
     * Printer"), else the artifact made readable ("ESCPOS-ThermalPrinter-Android" stays, "core" becomes "zxing core"
     * from its group); the link is the GitHub page for com.github.user:repo, else the POM's &lt;url&gt; or &lt;scm&gt;,
     * else the library's page on Maven Central (Google Maven libraries: maven.google.com). Files only, no network.
     */
    public static String[] summary(File dir, String coordinate, File cache) {
        String[] gav = coordinate == null ? null : coordinate.split(":");
        String pom = text(new File(dir, "pom.xml"));
        if (pom == null && gav != null && gav.length >= 3 && cache != null) {
            pom = text(new File(cache, MavenResolver.path(gav[0], gav[1], gav[2], "pom")));
        }
        String name = null, url = null;
        if (pom != null) {
            String own = withoutBlocks(pom);
            name = tag(own, "name");
            url = tag(own, "url");
            if (url == null) {
                String scm = block(pom, "scm");
                if (scm != null) url = tag(scm, "url");
            }
        }
        if (name != null) name = clean(name);
        if (gav != null && gav.length >= 2) {
            if (name == null || name.isEmpty() || name.equals(gav[1]) || name.startsWith("${")) name = pretty(readable(gav[0], gav[1]));
            String[] owner = gav[0].startsWith("com.github.") ? gav[0].substring(11).split("\\.") : null;
            if (owner != null && owner.length > 0 && !owner[0].isEmpty()) {
                // com.github.User:Repo, or a module of a multi-module JitPack build: com.github.User.Repo:module
                url = "https://github.com/" + owner[0] + "/" + (owner.length > 1 ? owner[1] : gav[1]);
            } else if (url == null || !url.startsWith("http")) {
                url = gav[0].startsWith("androidx.") || gav[0].startsWith("com.google.android.")
                        ? "https://maven.google.com/web/index.html#" + gav[0] + ":" + gav[1]
                        : "https://central.sonatype.com/artifact/" + gav[0] + "/" + gav[1] + (gav.length > 2 ? "/" + gav[2] : "");
            }
        } else {
            name = dir.getName();
            url = null;
        }
        return new String[]{name, url};
    }

    /** v1.8.11: "ESCPOS-ThermalPrinter-Android" → "ESCPOS Thermal Printer Android", "lottie" → "Lottie". */
    static String pretty(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("[-_ ]+")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            // ThermalPrinter → Thermal Printer (a capital after a small letter); ESCPOS and okhttp3 stay
            String split = word.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
            out.append(Character.toUpperCase(split.charAt(0))).append(split.substring(1));
        }
        return out.length() == 0 ? id : out.toString();
    }

    /** "core" of com.google.zxing → "zxing core"; names with a capital or a dash stay as they are. */
    static String readable(String group, String artifact) {
        if (!artifact.equals(artifact.toLowerCase(java.util.Locale.ROOT)) || artifact.contains("-") || artifact.length() > 12) {
            return artifact;
        }
        String[] parts = group.split("\\.");
        // com.airbnb.android:lottie → "airbnb lottie": words like android / library say nothing about whose it is
        String owner = parts[parts.length - 1];
        for (int i = parts.length - 1; i > 0 && java.util.Arrays.asList("android", "library", "lib", "libs", "sdk", "core", "java")
                .contains(parts[i].toLowerCase(java.util.Locale.ROOT)); i--) owner = parts[i - 1];
        return owner.equals(artifact) ? artifact : owner + " " + artifact;
    }

    /** The text shown in the dialog. */
    public String describe(String folder, String size) {
        StringBuilder s = new StringBuilder();
        if (name != null && !name.equals(folder)) s.append(name).append("\n\n");
        if (description != null) s.append(clean(description)).append("\n\n");
        s.append("Coordinate: ").append(coordinate != null ? coordinate : "imported file (no Maven coordinate)").append('\n');
        s.append("Folder: ").append(folder).append(" · ").append(size).append('\n');
        if (!licences.isEmpty()) s.append("Licence: ").append(android.text.TextUtils.join(", ", licences)).append('\n');
        else if (pomFound) s.append("Licence: not stated in its POM\n");
        if (url != null) s.append("Project: ").append(url).append('\n');
        s.append('\n');
        if (permissions.isEmpty()) {
            s.append("Permissions it adds to the app: none\n");
        } else {
            s.append("Permissions it adds to the app:\n");
            for (String p : permissions) s.append("• ").append(p).append('\n');
        }
        s.append('\n');
        if (packages.isEmpty()) {
            s.append("No public classes found in classes.jar.");
        } else {
            s.append("Public classes: ").append(classes).append('\n');
            s.append("Imports (the build adds the ones your code uses):\n");
            for (String p : packages) s.append("import ").append(p).append(".*;\n");
        }
        if (!pomFound && coordinate != null) s.append("\nIts POM isn't on this phone, so there is no description or licence.");
        return s.toString().trim();
    }

    private static void licences(String pom, List<String> out) {
        String block = block(pom, "licenses");
        if (block == null) return;
        Matcher m = Pattern.compile("<license>(.*?)</license>", Pattern.DOTALL).matcher(block);
        while (m.find()) {
            String n = tag(m.group(1), "name");
            if (n == null) n = tag(m.group(1), "url");
            if (n != null && !out.contains(clean(n))) out.add(clean(n));
        }
    }

    /** The POM without the blocks whose name/url/description belong to something else. */
    private static String withoutBlocks(String pom) {
        String s = pom.replaceAll("(?s)<!--.*?-->", "");
        for (String b : new String[]{"parent", "licenses", "developers", "contributors", "organization", "scm",
                "issueManagement", "ciManagement", "distributionManagement", "dependencies", "dependencyManagement",
                "build", "profiles", "repositories", "pluginRepositories", "mailingLists", "reporting", "properties"}) {
            s = s.replaceAll("(?s)<" + b + "\\b[^>]*>.*?</" + b + ">", "");
        }
        return s;
    }

    private static String block(String xml, String tag) {
        Matcher m = Pattern.compile("(?s)<" + tag + "\\b[^>]*>(.*?)</" + tag + ">").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static String tag(String xml, String tag) {
        String v = block(xml, tag);
        if (v == null) return null;
        v = v.replace("<![CDATA[", "").replace("]]>", "").trim();
        return v.isEmpty() || v.contains("${") ? null : v;
    }

    private static String clean(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replaceAll("\\s+", " ").trim();
    }

    private static String text(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            return FileUtil.readFile(f.getAbsolutePath());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
