package com.sketchware.ai.github;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * v1.9.8: the HTTP calls of GitHub sign-in and cloud backup (github.com and api.github.com only). HttpURLConnection,
 * no library (on Android it is the platform's own OkHttp). The token is sent only to the API host, never to
 * github.com's pages or anywhere else.
 * <p>
 * v2.0.1 (owner's 13 Nov directive "2G/3G ready, rate-limit protection"): timeouts for slow networks; reads (GET) are
 * tried again on a dropped connection or a passing server error (502/503/504) with growing pauses (1 s, 3 s, 7 s plus
 * a little jitter); GitHub's {@code Retry-After} is honoured when it is short; when GitHub says the hourly limit is
 * used up ({@code X-RateLimit-Remaining: 0}, or a 403/429 with {@code Retry-After}), no further API call goes out
 * until the given time: the answer is HTTP 429 at once ({@link Response#rateLimited()}), so a screen or a background
 * backup never hammers GitHub. Writes (POST, PATCH, PUT, DELETE) are never sent twice.
 */
public final class GitHubHttp {

    static final String DEFAULT_WEB = "https://github.com";
    static final String DEFAULT_API = "https://api.github.com";
    private static String web = DEFAULT_WEB;
    private static String api = DEFAULT_API;
    static String userAgent = "SketchwareAI";

    private GitHubHttp() {
    }

    /** Tests point both hosts at a local server. */
    static synchronized void useEndpoints(String webBase, String apiBase) {
        web = webBase;
        api = apiBase;
    }

    static synchronized String web() {
        return web;
    }

    static synchronized String api() {
        return api;
    }

    public static final class Response {
        public final int code;
        public final String body;
        /** v2.0.1: GitHub's {@code Retry-After} in seconds, or -1. */
        public int retryAfter = -1;
        /** v2.0.1: {@code X-RateLimit-Remaining}, or -1. */
        public int rateRemaining = -1;
        /** v2.0.1: {@code X-RateLimit-Reset} (seconds since 1970), or 0. */
        public long rateReset;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }

        /** v2.0.1: GitHub's rate limit (primary or secondary) answered, not a permission problem. */
        public boolean rateLimited() {
            if (code == 429) return true;
            return code == 403 && (rateRemaining == 0 || retryAfter >= 0
                    || (body != null && body.toLowerCase(java.util.Locale.ROOT).contains("rate limit")));
        }
    }

    /** v2.0.1: no API call before this time (System.currentTimeMillis()), after GitHub said the limit is used up. */
    private static volatile long blockedUntil;
    /** v2.0.1: pauses between tries of a read; tests make them short. */
    static long[] backoff = {1000, 3000, 7000};
    /** v2.0.1: a Retry-After up to this long is waited out in place; a longer one ends the call. */
    static final int WAIT_IN_PLACE_SECONDS = 10;

    /** v2.0.1: when the API may be called again, or 0 (shown in "try again at …" messages). */
    public static long blockedUntil() {
        long until = blockedUntil;
        return until > System.currentTimeMillis() ? until : 0;
    }

    /** Tests: forget a rate-limit pause. */
    static void resetLimits() {
        blockedUntil = 0;
    }

    /** POST an x-www-form-urlencoded form to github.com (Device Flow), JSON answer. */
    static Response postForm(String path, Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection c = open(web() + path, "POST", null);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(bytes);
        }
        return read(c);
    }

    /**
     * A REST call to api.github.com with the token; {@code json} may be null. v2.0.1: a GET is tried again on a dropped
     * connection or a passing server error; every call stops at once while a rate-limit pause lasts.
     */
    static Response api(String method, String path, String token, String json) throws IOException {
        boolean read = "GET".equals(method) && json == null;
        for (int attempt = 0; ; attempt++) {
            Response limited = limitedNow();
            if (limited != null) return limited;
            Response r;
            try {
                r = apiOnce(method, path, token, json);
            } catch (IOException e) {
                if (!read || attempt >= backoff.length || !worthRetry(e)) throw e;
                pause(backoff[attempt]);
                continue;
            }
            note(r);
            if (read && attempt < backoff.length) {
                if (r.code == 502 || r.code == 503 || r.code == 504) {
                    pause(backoff[attempt]);
                    continue;
                }
                if (r.rateLimited() && r.retryAfter >= 0 && r.retryAfter <= WAIT_IN_PLACE_SECONDS && attempt == 0) {
                    pause(Math.max(1, r.retryAfter) * 1000L);
                    blockedUntil = 0;
                    continue;
                }
            }
            return r;
        }
    }

    private static Response apiOnce(String method, String path, String token, String json) throws IOException {
        HttpURLConnection c = open(api() + path, method, token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        if (json != null) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(bytes);
            }
        }
        return read(c);
    }

    /** A GET of a file's raw text (Accept: application/vnd.github.raw), as a string; tried again like {@link #api}. */
    static Response apiRaw(String path, String token) throws IOException {
        for (int attempt = 0; ; attempt++) {
            Response limited = limitedNow();
            if (limited != null) return limited;
            Response r;
            try {
                HttpURLConnection c = open(api() + path, "GET", token);
                c.setRequestProperty("Accept", "application/vnd.github.raw");
                r = read(c);
            } catch (IOException e) {
                if (attempt >= backoff.length || !worthRetry(e)) throw e;
                pause(backoff[attempt]);
                continue;
            }
            note(r);
            if (attempt < backoff.length && (r.code == 502 || r.code == 503 || r.code == 504)) {
                pause(backoff[attempt]);
                continue;
            }
            return r;
        }
    }

    /**
     * POST /repos/.../git/blobs with the file's bytes streamed as base64 (no copy of the whole file in memory).
     */
    static Response postBlob(String path, String token, File file) throws IOException {
        HttpURLConnection c = open(api() + path, "POST", token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        long encoded = (file.length() + 2) / 3 * 4;
        byte[] head = "{\"encoding\":\"base64\",\"content\":\"".getBytes(StandardCharsets.US_ASCII);
        byte[] tail = "\"}".getBytes(StandardCharsets.US_ASCII);
        c.setFixedLengthStreamingMode(head.length + encoded + tail.length);
        try (OutputStream out = c.getOutputStream(); InputStream in = new FileInputStream(file)) {
            out.write(head);
            Base64Stream.encode(in, out);
            out.write(tail);
        }
        return read(c);
    }

    /**
     * GET a file's raw bytes (Accept: application/vnd.github.raw) into {@code target}; at most {@code maxBytes}.
     * v2.0.1: a download cut by the network starts again (up to three more times, with pauses).
     */
    static int download(String path, String token, File target, long maxBytes) throws IOException {
        for (int attempt = 0; ; attempt++) {
            Response limited = limitedNow();
            if (limited != null) return limited.code;
            try {
                int code = downloadOnce(path, token, target, maxBytes);
                if (attempt < backoff.length && (code == 502 || code == 503 || code == 504)) {
                    pause(backoff[attempt]);
                    continue;
                }
                return code;
            } catch (IOException e) {
                if (attempt >= backoff.length || !worthRetry(e) || e instanceof TooLarge) throw e;
                pause(backoff[attempt]);
            }
        }
    }

    /** The file is larger than allowed: trying again doesn't help. */
    static final class TooLarge extends IOException {
        TooLarge(String message) {
            super(message);
        }
    }

    private static int downloadOnce(String path, String token, File target, long maxBytes) throws IOException {
        HttpURLConnection c = open(api() + path, "GET", token);
        c.setRequestProperty("Accept", "application/vnd.github.raw");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            note(read(c));
            return code;
        }
        File temp = new File(target.getAbsolutePath() + ".part");
        long total = 0;
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > maxBytes) throw new TooLarge("The file is larger than " + (maxBytes >> 20) + " MB");
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            temp.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Couldn't save " + target);
        }
        return code;
    }

    // ------------------------------------------------------------------ v2.0.1: retries and limits

    /** A dropped or timed-out connection is worth another try; a missing host (offline, no DNS) or TLS problem not. */
    static boolean worthRetry(IOException e) {
        if (e instanceof java.net.UnknownHostException) return false;
        if (e instanceof javax.net.ssl.SSLHandshakeException) return false;
        return true;
    }

    private static void pause(long millis) throws IOException {
        long jitter = millis <= 0 ? 0 : (long) (Math.random() * Math.min(500, millis / 4));
        try {
            Thread.sleep(millis + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("Stopped while waiting to try again");
        }
    }

    /** While a rate-limit pause lasts: a 429 answer without going to GitHub; else null. */
    private static Response limitedNow() {
        long until = blockedUntil;
        long now = System.currentTimeMillis();
        if (until <= now) return null;
        Response r = new Response(429, "{\"message\":\"API rate limit exceeded (waiting on this phone)\"}");
        r.retryAfter = (int) ((until - now + 999) / 1000);
        return r;
    }

    /** Remembers GitHub's "limit used up" so the next calls wait instead of asking again. */
    private static void note(Response r) {
        if (!r.rateLimited()) return;
        long now = System.currentTimeMillis();
        long until;
        if (r.retryAfter >= 0) {
            until = now + Math.max(1, r.retryAfter) * 1000L;
        } else if (r.rateRemaining == 0 && r.rateReset > 0) {
            until = r.rateReset * 1000L;
        } else {
            // a secondary limit without a time: GitHub asks for at least a minute
            until = now + 60_000L;
        }
        // never more than an hour (a wrong clock on the phone must not block the account for days)
        blockedUntil = Math.min(until, now + 60 * 60_000L);
    }

    private static HttpURLConnection open(String url, String method, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        // v2.0.1: 2G / 3G: a slow first answer is not a failure
        c.setConnectTimeout(30_000);
        c.setReadTimeout(120_000);
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        try {
            c.setRequestMethod(method);
        } catch (java.net.ProtocolException e) {
            // Android's HttpURLConnection sends PATCH; Java SE's (the desktop test) has none
            c.setRequestMethod("POST");
            c.setRequestProperty("X-HTTP-Method-Override", method);
        }
        c.setRequestProperty("User-Agent", userAgent);
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        // the token goes only to the API host
        if (token != null && url.startsWith(api())) c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    private static Response read(HttpURLConnection c) throws IOException {
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = "";
            if (in != null) {
                try (InputStream s = in) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16 * 1024];
                    int n;
                    while ((n = s.read(buffer)) > 0) {
                        if (out.size() > 8 * 1024 * 1024) throw new IOException("Answer too large");
                        out.write(buffer, 0, n);
                    }
                    body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            Response r = new Response(code, body);
            r.retryAfter = intHeader(c, "Retry-After");
            r.rateRemaining = intHeader(c, "X-RateLimit-Remaining");
            String reset = c.getHeaderField("X-RateLimit-Reset");
            try {
                r.rateReset = reset == null ? 0 : Long.parseLong(reset.trim());
            } catch (NumberFormatException ignored) {
                r.rateReset = 0;
            }
            return r;
        } finally {
            c.disconnect();
        }
    }

    private static int intHeader(HttpURLConnection c, String name) {
        String value = c.getHeaderField(name);
        if (value == null) return -1;
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Base64 (RFC 4648, no line breaks) of a stream, written as it is read. */
    static final class Base64Stream {
        private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

        private Base64Stream() {
        }

        static void encode(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[3 * 16 * 1024];
            byte[] encoded = new byte[4 * 16 * 1024];
            int filled = 0;
            while (true) {
                int n = in.read(buffer, filled, buffer.length - filled);
                if (n < 0) break;
                filled += n;
                if (filled == buffer.length) {
                    out.write(encoded, 0, encodeBlock(buffer, filled, encoded));
                    filled = 0;
                }
            }
            if (filled > 0) out.write(encoded, 0, encodeBlock(buffer, filled, encoded));
        }

        /** Encodes {@code length} bytes (a multiple of 3 except at the very end); returns the number of chars. */
        static int encodeBlock(byte[] data, int length, byte[] out) {
            int o = 0;
            int i = 0;
            for (; i + 2 < length; i += 3) {
                int v = (data[i] & 0xff) << 16 | (data[i + 1] & 0xff) << 8 | (data[i + 2] & 0xff);
                out[o++] = (byte) ALPHABET[v >>> 18 & 63];
                out[o++] = (byte) ALPHABET[v >>> 12 & 63];
                out[o++] = (byte) ALPHABET[v >>> 6 & 63];
                out[o++] = (byte) ALPHABET[v & 63];
            }
            int rest = length - i;
            if (rest == 1) {
                int v = (data[i] & 0xff) << 16;
                out[o++] = (byte) ALPHABET[v >>> 18 & 63];
                out[o++] = (byte) ALPHABET[v >>> 12 & 63];
                out[o++] = '=';
                out[o++] = '=';
            } else if (rest == 2) {
                int v = (data[i] & 0xff) << 16 | (data[i + 1] & 0xff) << 8;
                out[o++] = (byte) ALPHABET[v >>> 18 & 63];
                out[o++] = (byte) ALPHABET[v >>> 12 & 63];
                out[o++] = (byte) ALPHABET[v >>> 6 & 63];
                out[o++] = '=';
            }
            return o;
        }
    }
}
