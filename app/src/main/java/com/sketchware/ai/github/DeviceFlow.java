package com.sketchware.ai.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v1.9.8: GitHub sign-in with the OAuth Device Flow: the app shows a one-time code, the user enters it at
 * github.com/login/device in any browser, the app asks GitHub for the token until it is approved. Only the OAuth app's
 * public Client ID is used; no client secret exists in the app.
 * <p>
 * A browser redirect with PKCE was the first plan; GitHub's token endpoint still requires the client secret for that
 * flow (docs.github.com "Authorizing OAuth apps", read 12 Nov 2026), so it can't be done without one. The Device Flow
 * needs only the Client ID and "Enable Device Flow" ticked in the OAuth app's settings.
 */
public final class DeviceFlow {

    /** What cloud backup needs: a private repository of the user's (GitHub's OAuth scopes can't narrow it further). */
    public static final String SCOPE = "repo";

    private DeviceFlow() {
    }

    public static final class Code {
        public String deviceCode;
        public String userCode;
        public String verificationUri;
        /** The page with the code already filled in, when GitHub sends one (https://github.com/... only). */
        public String verificationUriComplete;
        public int expiresIn;
        public int interval;
        /** v1.9.9: when the code stops working (System.currentTimeMillis()), kept with a sign-in that is resumed. */
        public long expiresAt;
    }

    public interface Waiting {
        /** True to stop waiting. */
        boolean cancelled();

        /** Waits up to {@code millis} (less when the caller wants an early check, e.g. the app came back). */
        void sleep(long millis) throws InterruptedException;

        /** v1.9.9: a check failed on the network; waiting goes on (Android cuts the network of background apps). */
        default void networkTrouble(IOException e) {
        }
    }

    /** One answer of the token endpoint. */
    public static final class Poll {
        public String token;
        public String error;
        public String description;
        public int interval;
        public int httpCode;
    }

    /** Step 1: a device code and the code the user types on github.com. */
    public static Code start(String clientId, String scope) throws IOException, GitHubException {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new GitHubException("no_client_id", "No OAuth Client ID is set for GitHub sign-in.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId.trim());
        form.put("scope", scope);
        GitHubHttp.Response r = GitHubHttp.postForm("/login/device/code", form);
        JsonObject json = parse(r.body);
        String error = string(json, "error");
        if (error != null || !r.ok()) throw failure(error == null ? "http_" + r.code : error, string(json, "error_description"));
        Code code = new Code();
        code.deviceCode = string(json, "device_code");
        code.userCode = string(json, "user_code");
        code.verificationUri = string(json, "verification_uri");
        code.expiresIn = integer(json, "expires_in", 900);
        code.interval = Math.max(5, integer(json, "interval", 5));
        code.expiresAt = System.currentTimeMillis() + code.expiresIn * 1000L;
        if (code.deviceCode == null || code.userCode == null) throw failure("bad_answer", "GitHub's answer had no code.");
        if (code.verificationUri == null || !code.verificationUri.startsWith("https://github.com/")) {
            code.verificationUri = GitHubHttp.DEFAULT_WEB + "/login/device";
        }
        String complete = string(json, "verification_uri_complete");
        code.verificationUriComplete = complete != null && complete.startsWith("https://github.com/") ? complete : null;
        return code;
    }

    /** Step 3, once: asks GitHub whether the user approved the code. */
    public static Poll poll(String clientId, String deviceCode) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId.trim());
        form.put("device_code", deviceCode);
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        GitHubHttp.Response r = GitHubHttp.postForm("/login/oauth/access_token", form);
        JsonObject json = parse(r.body);
        Poll p = new Poll();
        p.httpCode = r.code;
        p.token = string(json, "access_token");
        if (p.token != null && p.token.isEmpty()) p.token = null;
        p.error = string(json, "error");
        p.description = string(json, "error_description");
        p.interval = integer(json, "interval", 0);
        return p;
    }

    /**
     * Step 3: asks for the token every {@code interval} seconds (more after "slow_down") until the user approved,
     * denied, the code expired or {@code waiting} says stop. Returns the access token.
     * <p>
     * v1.9.9: a check that fails on the network (or a GitHub server error) doesn't end the sign-in: Android 15 cuts the
     * network of an app in the background while the user approves in the browser, and v1.9.8 gave up on the first such
     * failure, after GitHub had already said "you're all set". It goes on until the code expires.
     */
    public static String waitForToken(String clientId, Code code, Waiting waiting) throws IOException, GitHubException {
        long deadline = code.expiresAt > 0 ? code.expiresAt : System.currentTimeMillis() + code.expiresIn * 1000L;
        int interval = Math.max(1, code.interval);
        while (true) {
            try {
                waiting.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                throw new GitHubException("cancelled", "Sign-in was stopped.");
            }
            if (waiting.cancelled()) throw new GitHubException("cancelled", "Sign-in was stopped.");
            if (System.currentTimeMillis() > deadline) throw failure("expired_token", null);
            Poll p;
            try {
                p = poll(clientId, code.deviceCode);
            } catch (IOException e) {
                waiting.networkTrouble(e);
                continue;
            }
            if (p.token != null) return p.token;
            if ("authorization_pending".equals(p.error)) continue;
            if ("slow_down".equals(p.error)) {
                interval = Math.max(interval + 5, p.interval);
                continue;
            }
            if (p.error == null && p.httpCode >= 500) {
                waiting.networkTrouble(new IOException("GitHub answered HTTP " + p.httpCode));
                continue;
            }
            throw failure(p.error == null ? "http_" + p.httpCode : p.error, p.description);
        }
    }

    /** The user's message for an error code of the Device Flow. */
    static GitHubException failure(String error, String description) {
        String text;
        switch (error) {
            case "device_flow_disabled":
                text = "Device sign-in is switched off for this OAuth app. On github.com open Settings > Developer "
                        + "settings > OAuth Apps > the app, tick \"Enable Device Flow\" and save.";
                break;
            case "incorrect_client_credentials":
            case "http_404":
                text = "GitHub doesn't know this Client ID. Copy it again from the OAuth app's page on github.com.";
                break;
            case "access_denied":
                text = "Sign-in was cancelled on GitHub.";
                break;
            case "expired_token":
            case "token_expired":
                text = "The code expired before it was entered. Start again.";
                break;
            case "unsupported_grant_type":
            case "incorrect_device_code":
                text = "GitHub refused the sign-in request (" + error + "). Start again.";
                break;
            default:
                text = "GitHub sign-in failed (" + error + (description == null ? "" : ": " + description) + ").";
        }
        return new GitHubException(error, text);
    }

    static JsonObject parse(String body) {
        try {
            JsonElement e = JsonParser.parseString(body == null || body.isEmpty() ? "{}" : body);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    static String string(JsonObject json, String name) {
        JsonElement e = json.get(name);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    static int integer(JsonObject json, String name, int fallback) {
        try {
            JsonElement e = json.get(name);
            return e == null || e.isJsonNull() ? fallback : e.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
