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
            return "https://search.maven.org/solrsearch/select?q=" + URLEncoder.encode(query.trim(), "UTF-8")
                    + "&rows=" + rows + "&wt=json";
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<Hit> search(String query, int rows, MavenResolver.Source source) throws IOException {
        byte[] data = (source != null ? source : (MavenResolver.Source) MavenResolver::download).fetch(url(query, rows));
        return parse(data == null ? "" : new String(data, StandardCharsets.UTF_8));
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
                if (hit.group != null && hit.name != null && hit.latestVersion != null) hits.add(hit);
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
