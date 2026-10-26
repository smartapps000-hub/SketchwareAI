package com.sketchware.ai.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Library Resolver search (v1.8.8): Maven Central's search service (search.maven.org, the index Maven Central's own
 * site uses). Google Maven has no search: its libraries (androidx.*, com.google.android.*) are added by coordinate.
 */
public final class MavenSearch {

    public static final class Hit {
        public String group;
        public String name;
        public String latestVersion;
        public String packaging;

        public String coordinate() {
            return group + ":" + name + ":" + latestVersion;
        }
    }

    private MavenSearch() {
    }

    public static String url(String query, int rows) {
        try {
            return "https://search.maven.org/solrsearch/select?q=" + URLEncoder.encode(solrQuery(query), "UTF-8")
                    + "&rows=" + rows + "&wt=json";
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * v1.8.9: the search service answers HTTP 400 to a raw colon. "group:artifact[:version]" becomes the field query
     * g:"group" AND a:"artifact"; any other colon or quote becomes a space.
     */
    static String solrQuery(String query) {
        String q = normalize(query);
        String[] parts = q.split(":");
        if (parts.length >= 2 && parts.length <= 4 && parts[0].matches("[\\w.\\-]+") && parts[1].matches("[\\w.\\-]+")) {
            return "g:\"" + parts[0] + "\" AND a:\"" + parts[1] + "\"";
        }
        return q.replace(':', ' ').replace('"', ' ').replaceAll("\\s+", " ").trim();
    }

    /** A Gradle line (implementation 'g:a:v', implementation("g:a:v")) reduced to its coordinate. */
    static String normalize(String query) {
        String q = query == null ? "" : query.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\w+\\s*\\(?\\s*['\"]([^'\"]+)['\"]\\s*\\)?$").matcher(q);
        return m.matches() ? m.group(1).trim() : q;
    }

    /** "group:artifact[:version]" typed into search, or null. */
    static String[] coordinate(String query) {
        String q = normalize(query);
        String[] parts = q.split(":");
        if (parts.length >= 2 && parts.length <= 4 && parts[0].matches("[\\w.\\-]+") && parts[1].matches("[\\w.\\-]+")) {
            return parts;
        }
        return null;
    }

    public static List<Hit> search(String query, int rows, MavenResolver.Source source) throws IOException {
        MavenResolver.Source src = source != null ? source : (MavenResolver.Source) MavenResolver::download;
        String[] c = coordinate(query);
        List<Hit> hits;
        try {
            byte[] data = src.fetch(url(query, rows));
            hits = parse(data == null ? "" : new String(data, StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (c == null) throw e;   // a coordinate is still looked up in Google Maven and JitPack
            hits = new ArrayList<>();
        }
        if (hits.isEmpty() && c != null) {
            // Not on Maven Central (JitPack and Google Maven have no search): read the coordinate's
            // maven-metadata.xml from those two repositories.
            String path = c[0].replace('.', '/') + "/" + c[1] + "/maven-metadata.xml";
            String[] repos = {"https://dl.google.com/dl/android/maven2/", "https://jitpack.io/"};
            for (String repo : repos) {
                try {
                    byte[] meta = src.fetch(repo + path);
                    String latest = latestStable(meta == null ? "" : new String(meta, StandardCharsets.UTF_8));
                    if (latest != null) {
                        Hit hit = new Hit();
                        hit.group = c[0];
                        hit.name = c[1];
                        hit.latestVersion = latest;
                        hits.add(hit);
                        break;
                    }
                } catch (IOException ignored) {
                    // try the next repository
                }
            }
        }
        return hits;
    }

    /** Newest stable version listed in a maven-metadata.xml, or null. */
    static String latestStable(String metadata) {
        String best = null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>").matcher(metadata);
        while (m.find()) {
            String v = m.group(1);
            if (MavenResolver.stable(v) && (best == null || MavenResolver.compareVersions(v, best) > 0)) best = v;
        }
        return best;
    }

    /**
     * v1.8.11: only what can be an Android library: packaging aar, jar or bundle (or not stated), not a web jar
     * (org.webjars: JavaScript packaged for Java servers, no classes.jar), not a POM-only parent or BOM, not a Kotlin/
     * Native or JS target (klib, js, wasm, ios, macos, linux, mingw).
     */
    static boolean usable(Hit hit) {
        if (hit.group == null || hit.name == null) return false;
        if (hit.group.startsWith("org.webjars") || hit.group.contains(".npm") || hit.group.startsWith("org.mvnpm")) return false;
        String p = hit.packaging == null ? "" : hit.packaging.toLowerCase(java.util.Locale.ROOT);
        if (!(p.isEmpty() || p.equals("aar") || p.equals("jar") || p.equals("bundle"))) return false;
        String n = hit.name.toLowerCase(java.util.Locale.ROOT);
        return !n.matches(".*-(js|wasm|wasm-js|wasmjs|iosx64|iosarm64|iossimulatorarm64|macosx64|macosarm64|linuxx64|"
                + "linuxarm64|mingwx64|tvos.*|watchos.*|native)$") && !n.endsWith("-parent") && !n.endsWith("-bom");
    }

    static List<Hit> parse(String json) {
        List<Hit> hits = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray docs = root.getAsJsonObject("response").getAsJsonArray("docs");
            for (JsonElement element : docs) {
                JsonObject doc = element.getAsJsonObject();
                Hit hit = new Hit();
                hit.group = string(doc, "g");
                hit.name = string(doc, "a");
                hit.latestVersion = string(doc, "latestVersion");
                hit.packaging = string(doc, "p");
                if (hit.group != null && hit.name != null && hit.latestVersion != null && usable(hit)) hits.add(hit);
            }
        } catch (RuntimeException ignored) {
            // no results
        }
        return hits;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
