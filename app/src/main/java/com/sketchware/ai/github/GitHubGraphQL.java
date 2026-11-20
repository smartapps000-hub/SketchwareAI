package com.sketchware.ai.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;

/**
 * v2.1.0: GitHub's GraphQL API ({@code POST /graphql}), for what the REST API doesn't have: repository Discussions
 * (Community, Play Store reviews). GraphQL always needs a sign-in. One try, in the caller's quick mode when it has one.
 */
public final class GitHubGraphQL {

    private GitHubGraphQL() {
    }

    /** Runs {@code query} with {@code variables}; the "data" object, or a GitHubException with GitHub's own words. */
    public static JsonObject run(String token, String query, JsonObject variables) throws IOException, GitHubException {
        if (token == null) throw new GitHubException("signed_out", "Sign in with GitHub first (GitHub's Discussions need it).");
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        if (variables != null) body.add("variables", variables);
        GitHubHttp.Response r = GitHubHttp.api("POST", "/graphql", token, body.toString());
        if (r.code == 401) throw new GitHubException("http_401", "GitHub didn't accept the saved sign-in (HTTP 401).");
        if (!r.ok()) throw CloudBackup.http("Asking GitHub", r);
        JsonObject o = DeviceFlow.parse(r.body);
        JsonElement errors = o.get("errors");
        if (errors != null && errors.isJsonArray() && errors.getAsJsonArray().size() > 0) {
            JsonArray list = errors.getAsJsonArray();
            JsonElement first = list.get(0);
            String message = first.isJsonObject() ? DeviceFlow.string(first.getAsJsonObject(), "message") : null;
            throw new GitHubException("graphql", "GitHub said: " + (message == null ? "an error" : message));
        }
        JsonElement data = o.get("data");
        if (data == null || !data.isJsonObject()) throw new GitHubException("bad_answer", "GitHub's answer had no data.");
        return data.getAsJsonObject();
    }

    /** A nested object or null ("a.b.c"). */
    public static JsonObject object(JsonObject root, String path) {
        JsonObject o = root;
        for (String part : path.split("\\.")) {
            if (o == null) return null;
            JsonElement e = o.get(part);
            o = e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        }
        return o;
    }

    /** A nested array or an empty one. */
    public static JsonArray array(JsonObject root, String path) {
        int cut = path.lastIndexOf('.');
        JsonObject parent = cut < 0 ? root : object(root, path.substring(0, cut));
        JsonElement e = parent == null ? null : parent.get(cut < 0 ? path : path.substring(cut + 1));
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }
}
