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
 * used up ({@code X-RateLimit-Remaining: 0}, or a 403/429 with {@code Retry-After}), no further API call with the same
 * credential goes out until then: the answer is HTTP 429 at once ({@link Response#rateLimited()}), so a screen or a
 * background backup never hammers GitHub. The pause is kept per credential (signed out: GitHub's per-network limit;
 * each token: the account's own), measured on the uptime clock from the server's own times (a wrong phone clock can't
 * stretch it), at most one hour. Writes (POST, PATCH, PUT, DELETE) are never sent twice.
 * <p>
 * v2.0.4 (owner's 16 Nov directive "per-project repositories, release assets, 30-100 MB, Cancel"): release assets go up
 * as raw bytes to {@code uploads.github.com} ({@link #uploadAsset}: no base64, no JSON around them) and come down from
 * the API's asset address, whose answer is a redirect to GitHub's file host: the redirect is followed by hand and the
 * token is never sent there ({@link #downloadAsset}). The token goes to the API and upload hosts only, as
 * {@code Authorization: Bearer} on every request. A job can be cancelled from another thread ({@link #cancel}): its
 * connection is closed and its loops stop at the next 32 KB.
 */
public final class GitHubHttp {

    static final String DEFAULT_WEB = "https://github.com";
    static final String DEFAULT_API = "https://api.github.com";
    static final String DEFAULT_UPLOADS = "https://uploads.github.com";
    private static String web = DEFAULT_WEB;
    private static String api = DEFAULT_API;
    private static String uploads = DEFAULT_UPLOADS;
    static String userAgent = "SketchwareAI";

    private GitHubHttp() {
    }

    /** Tests point both hosts at a local server. */
    static synchronized void useEndpoints(String webBase, String apiBase) {
        useEndpoints(webBase, apiBase, apiBase + "/uploads");
    }

    /** v2.0.4: tests point the upload host at the local server too. */
    static synchronized void useEndpoints(String webBase, String apiBase, String uploadsBase) {
        web = webBase;
        api = apiBase;
        uploads = uploadsBase;
    }

    static synchronized String uploads() {
        return uploads;
    }

    static synchronized String web() {
        return web;
    }

    static synchronized String api() {
        return api;
    }

    /** v2.1.5 review 3: GitHub's clock minus this phone's, from the last answer's Date header (0 until one came). */
    private static volatile long serverOffset;

    /** v2.1.5 review 3: now on GitHub's clock (this phone's when no answer said yet). */
    public static long serverNow() {
        return System.currentTimeMillis() + serverOffset;
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
        /** v2.0.1: the server's {@code Date} (ms since 1970), or 0. */
        public long serverDate;
        /** v2.0.1: how long the rate-limit pause lasts from now (ms), when {@link #rateLimited()}. */
        public long waitMillis;
        /** v2.0.1: the call was made without a token (GitHub's limit for this network, not the account's). */
        public boolean anonymous;
        /** v2.0.3: GitHub's {@code X-GitHub-Request-Id} (for an error report), or null. */
        public String requestId;
        /** v2.0.4: GitHub's {@code X-OAuth-Scopes} (what the token may do), or null. */
        public String scopes;
        /** v2.0.4: a redirect's {@code Location}, or null. */
        public String location;

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

    /**
     * v2.0.1: per credential ("anon", or a key of the token), the uptime ({@code SystemClock.elapsedRealtime()}) until
     * which no API call goes out, after GitHub said its limit is used up. Review: one shared pause let a signed-out
     * import (GitHub's per-network limit, often used up on mobile data) block the signed-in account and even the
     * account read right after a sign-in.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> BLOCKED = new java.util.concurrent.ConcurrentHashMap<>();
    /** v2.0.1: pauses between tries of a read; tests make them short. */
    static long[] backoff = {1000, 3000, 7000};
    /** v2.0.1: a Retry-After up to this long is waited out in place; a longer one ends the call. */
    static final int WAIT_IN_PLACE_SECONDS = 10;
    private static final long MAX_PAUSE = 60 * 60_000L;

    /** Tests: forget the rate-limit pauses. */
    static void resetLimits() {
        BLOCKED.clear();
    }

    private static String key(String token) {
        return token == null ? "anon" : "t" + Integer.toHexString(token.hashCode());
    }

    private static long uptime() {
        return android.os.SystemClock.elapsedRealtime();
    }

    /** POST an x-www-form-urlencoded form to github.com (Device Flow), JSON answer. */
    static Response postForm(String path, Map<String, String> form) throws IOException {
        return postForm(path, form, 0, 0);
    }

    /**
     * v2.0.3: the same with its own timeouts (0 = the defaults): the sign-in code request uses short ones and tries
     * again ({@link DeviceFlow#start}) instead of showing "Getting a sign-in code…" for minutes.
     */
    static Response postForm(String path, Map<String, String> form, int connectMillis, int readMillis) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection c = open(web() + path, "POST", null);
        if (connectMillis > 0) c.setConnectTimeout(connectMillis);
        if (readMillis > 0) c.setReadTimeout(readMillis);
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
        // v2.1.0: quick mode tries once (the screen offers Retry)
        boolean read = "GET".equals(method) && json == null && QUICK.get() == null;
        for (int attempt = 0; ; attempt++) {
            Response limited = limitedNow(token);
            if (limited != null) return limited;
            Response r;
            try {
                r = apiOnce(method, path, token, json);
            } catch (IOException e) {
                if (isCancelled()) throw new Cancelled();
                if (!read || attempt >= backoff.length || !worthRetry(e)) throw e;
                pause(backoff[attempt]);
                continue;
            }
            long set = note(r, token);
            if (read && attempt < backoff.length) {
                if (r.code == 502 || r.code == 503 || r.code == 504) {
                    pause(backoff[attempt]);
                    continue;
                }
                if (r.rateLimited() && r.retryAfter >= 0 && r.retryAfter <= WAIT_IN_PLACE_SECONDS && attempt == 0) {
                    pause(Math.max(1, r.retryAfter) * 1000L);
                    // only the pause this call set (a longer one another call set meanwhile stays)
                    BLOCKED.remove(key(token), set);
                    continue;
                }
            }
            return r;
        }
    }

    private static Response apiOnce(String method, String path, String token, String json) throws IOException {
        // review L2: a cancelled job's next request never starts (a disconnect before connecting does nothing)
        stopIfCancelled();
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

    /**
     * v2.1.0 (owner's 16 Nov report: the repository viewer stayed on "Reading … on GitHub…"): the calls a screen waits
     * for run in quick mode: connect and read timeouts of {@code millis}, one try. A long upload keeps the long ones.
     */
    private static final ThreadLocal<Integer> QUICK = new ThreadLocal<>();

    interface Call<T> {
        T run() throws IOException, GitHubException;
    }

    static <T> T quick(int millis, Call<T> call) throws IOException, GitHubException {
        Integer before = QUICK.get();
        QUICK.set(millis);
        try {
            return call.run();
        } finally {
            if (before == null) QUICK.remove();
            else QUICK.set(before);
        }
    }

    /** A GET of a file's raw text (Accept: application/vnd.github.raw), as a string; tried again like {@link #api}. */
    static Response apiRaw(String path, String token) throws IOException {
        for (int attempt = 0; ; attempt++) {
            Response limited = limitedNow(token);
            if (limited != null) return limited;
            Response r;
            try {
                HttpURLConnection c = open(api() + path, "GET", token);
                c.setRequestProperty("Accept", "application/vnd.github.raw");
                r = read(c);
            } catch (IOException e) {
                if (isCancelled()) throw new Cancelled();
                if (attempt >= backoff.length || !worthRetry(e)) throw e;
                pause(backoff[attempt]);
                continue;
            }
            note(r, token);
            if (attempt < backoff.length && (r.code == 502 || r.code == 503 || r.code == 504)) {
                pause(backoff[attempt]);
                continue;
            }
            return r;
        }
    }

    /** v2.0.2: bytes of a transfer so far and in all (-1 when the size isn't known); called on the worker thread. */
    public interface Progress {
        void bytes(long done, long total);
    }

    static Response postBlob(String path, String token, File file) throws IOException {
        return postBlob(path, token, file, null);
    }

    /**
     * POST /repos/.../git/blobs with the file's bytes streamed as base64 (no copy of the whole file in memory).
     * v2.0.2: {@code progress} hears the bytes sent.
     */
    static Response postBlob(String path, String token, File file, Progress progress) throws IOException {
        return postBlob(path, token, file, 0, file.length(), progress);
    }

    /**
     * v2.0.3: one part of a file ({@code length} bytes from {@code offset}) as a blob: backups over
     * {@link SwbParts#PART_BYTES} go up in parts, each its own short request that can be tried again.
     */
    static Response postBlob(String path, String token, File file, long offset, long length, Progress progress)
            throws IOException {
        Response limited = limitedNow(token);
        if (limited != null) return limited;
        HttpURLConnection c = open(api() + path, "POST", token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        long encoded = (length + 2) / 3 * 4;
        byte[] head = "{\"encoding\":\"base64\",\"content\":\"".getBytes(StandardCharsets.US_ASCII);
        byte[] tail = "\"}".getBytes(StandardCharsets.US_ASCII);
        c.setFixedLengthStreamingMode(head.length + encoded + tail.length);
        long total = head.length + encoded + tail.length;
        try (OutputStream raw = c.getOutputStream(); InputStream source = new FileInputStream(file)) {
            skipFully(source, offset);
            InputStream in = new Limited(source, length);
            OutputStream out = progress == null ? raw : new CountingStream(raw, total, progress);
            out.write(head);
            Base64Stream.encode(in, out);
            out.write(tail);
        }
        Response r = read(c);
        note(r, token);
        return r;
    }

    private static void skipFully(InputStream in, long bytes) throws IOException {
        long left = bytes;
        while (left > 0) {
            long n = in.skip(left);
            if (n <= 0) {
                // v2.1.3 review (nit): the phone's own file, not the network (never tried again by itself)
                if (in.read() < 0) throw new NoRetry("The file is shorter than expected", null);
                n = 1;
            }
            left -= n;
        }
    }

    /** v2.0.3: at most {@code left} bytes of a stream. */
    static final class Limited extends java.io.FilterInputStream {
        private long left;

        Limited(InputStream in, long left) {
            super(in);
            this.left = left;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) return -1;
            int b = super.read();
            if (b >= 0) left--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }

        @Override
        public void close() {
            // the caller closes the file
        }
    }

    /**
     * GET a file's raw bytes (Accept: application/vnd.github.raw) into {@code target}; at most {@code maxBytes}.
     * v2.0.1: a download cut by the network starts again (up to three more times, with pauses); the answer (code and
     * rate-limit fields; no body when it worked) is returned.
     */
    static Response download(String path, String token, File target, long maxBytes) throws IOException {
        return download(path, token, target, maxBytes, null);
    }

    /** v2.0.2: the same, {@code progress} hearing the bytes received (total from Content-Length, else -1). */
    static Response download(String path, String token, File target, long maxBytes, Progress progress) throws IOException {
        for (int attempt = 0; ; attempt++) {
            Response limited = limitedNow(token);
            if (limited != null) return limited;
            try {
                Response r = downloadOnce(path, token, target, maxBytes, progress);
                if (attempt < backoff.length && (r.code == 502 || r.code == 503 || r.code == 504)) {
                    pause(backoff[attempt]);
                    continue;
                }
                return r;
            } catch (IOException e) {
                if (isCancelled()) throw new Cancelled();
                if (attempt >= backoff.length || !worthRetry(e)) throw e;
                pause(backoff[attempt]);
            }
        }
    }

    /** A failure trying again can't fix (too large, the phone's own storage): never retried. */
    static class NoRetry extends IOException {
        NoRetry(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The file is larger than allowed. */
    static final class TooLarge extends NoRetry {
        TooLarge(String message) {
            super(message, null);
        }
    }

    private static Response downloadOnce(String path, String token, File target, long maxBytes, Progress progress)
            throws IOException {
        HttpURLConnection c = open(api() + path, "GET", token);
        c.setRequestProperty("Accept", "application/vnd.github.raw");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            Response r = read(c);
            note(r, token);
            return r;
        }
        return save(c, code, target, maxBytes, progress);
    }

    /** The answer's body into {@code target} (through a ".part" file, renamed at the end). */
    private static Response save(HttpURLConnection c, int code, File target, long maxBytes, Progress progress)
            throws IOException {
        File temp = new File(target.getAbsolutePath() + ".part");
        long total = 0;
        OutputStream file;
        try {
            file = new FileOutputStream(temp);
        } catch (IOException e) {
            c.disconnect();
            throw new NoRetry("Can't write " + temp, e);
        }
        // the file first: it is closed even when the answer's stream can't be opened (review)
        long length = c.getContentLength(); // int on every API level (parts stay under 2 GB)
        try (OutputStream out = file; InputStream in = c.getInputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                stopIfCancelled();
                if (progress != null) progress.bytes(total, length);
                if (total > maxBytes) throw new TooLarge("The file is larger than " + (maxBytes >> 20) + " MB");
                try {
                    out.write(buffer, 0, n);
                } catch (IOException e) {
                    // the phone's storage (full, removed): another download wouldn't help
                    throw new NoRetry("Can't write " + temp + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            temp.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new NoRetry("Couldn't save " + target, null);
        }
        return new Response(code, "");
    }

    // ------------------------------------------------------------------ v2.0.4: release assets

    /**
     * v2.0.4: {@code length} bytes of {@code file} from {@code offset} as a release asset: POST to the upload host with
     * the raw bytes ({@code path} = "/repos/o/r/releases/{id}/assets?name=..."). Never sent twice by itself (the caller
     * looks at the release first).
     */
    static Response uploadAsset(String path, String token, File file, long offset, long length, String contentType,
                                Progress progress) throws IOException {
        Response limited = limitedNow(token);
        if (limited != null) return limited;
        HttpURLConnection c = open(uploads() + path, "POST", token);
        try {
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("Content-Type", contentType);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(length);
            try {
                try (OutputStream raw = c.getOutputStream(); InputStream source = new FileInputStream(file)) {
                    skipFully(source, offset);
                    InputStream in = new Limited(source, length);
                    OutputStream out = new CountingStream(raw, length, progress);
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                Response r = read(c);
                note(r, token);
                return r;
            } catch (IOException e) {
                // v2.1.3: a cancel that closed the connection (while it sent, or waited for GitHub's answer) is a cancel
                if (isCancelled() && !(e instanceof Cancelled)) throw new Cancelled();
                throw e;
            }
        } finally {
            release();
        }
    }

    /**
     * v2.0.4: a release asset ({@code path} = "/repos/o/r/releases/assets/{id}") into {@code target}. GitHub answers
     * with a redirect to its file host; it is followed here (at most three times) and the token goes only to GitHub's
     * API and upload hosts, never to the file host. Tried again like {@link #download}.
     */
    static Response downloadAsset(String path, String token, File target, long maxBytes, Progress progress)
            throws IOException {
        try {
            for (int attempt = 0; ; attempt++) {
                Response limited = limitedNow(token);
                if (limited != null) return limited;
                try {
                    Response r = downloadAssetOnce(path, token, target, maxBytes, progress);
                    if (attempt < backoff.length && (r.code == 502 || r.code == 503 || r.code == 504)) {
                        pause(backoff[attempt]);
                        continue;
                    }
                    return r;
                } catch (IOException e) {
                    if (isCancelled()) throw new Cancelled();
                    if (attempt >= backoff.length || !worthRetry(e)) throw e;
                    pause(backoff[attempt]);
                }
            }
        } finally {
            release();
        }
    }

    private static Response downloadAssetOnce(String path, String token, File target, long maxBytes, Progress progress)
            throws IOException {
        String url = api() + path;
        for (int hop = 0; hop < 4; hop++) {
            HttpURLConnection c = open(url, "GET", token);
            c.setRequestProperty("Accept", "application/octet-stream");
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String next = c.getHeaderField("Location");
                c.disconnect();
                if (next == null) return new Response(code, "{\"message\":\"A redirect without an address\"}");
                url = new URL(new URL(url), next).toString();
                // only secure addresses (the local test server is the API's own host)
                if (!url.startsWith("https://") && !url.startsWith(hostOf(api()))) {
                    return new Response(code, "{\"message\":\"GitHub sent the file to an insecure address\"}");
                }
                continue;
            }
            if (code < 200 || code >= 300) {
                Response r = read(c);
                note(r, token);
                return r;
            }
            return save(c, code, target, maxBytes, progress);
        }
        return new Response(310, "{\"message\":\"Too many redirects\"}");
    }

    /** "scheme://host:port" of a base address. */
    private static String hostOf(String base) {
        try {
            URL u = new URL(base);
            return u.getProtocol() + "://" + u.getAuthority() + "/";
        } catch (java.net.MalformedURLException e) {
            return base;
        }
    }

    // ------------------------------------------------------------------ v2.0.1: retries and limits

    /** A dropped or timed-out connection is worth another try; a missing host (offline, no DNS) or TLS problem not. */
    static boolean worthRetry(IOException e) {
        if (e instanceof NoRetry || isCancelled()) return false;
        if (e instanceof java.net.UnknownHostException) return false;
        if (e instanceof javax.net.ssl.SSLHandshakeException) return false;
        return true;
    }

    private static void pause(long millis) throws IOException {
        stopIfCancelled();
        long jitter = millis <= 0 ? 0 : (long) (Math.random() * Math.min(500, millis / 4));
        try {
            Thread.sleep(millis + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Cancelled();
        }
        stopIfCancelled();
    }

    /** While this credential's rate-limit pause lasts: a 429 answer without going to GitHub; else null. */
    private static Response limitedNow(String token) {
        Long until = BLOCKED.get(key(token));
        long now = uptime();
        if (until == null || until <= now) return null;
        Response r = new Response(429, "{\"message\":\"API rate limit exceeded (waiting on this phone)\"}");
        r.retryAfter = (int) ((until - now + 999) / 1000);
        r.waitMillis = until - now;
        r.anonymous = token == null;
        return r;
    }

    /**
     * Remembers GitHub's "limit used up" for this credential so the next calls wait instead of asking again; returns
     * the uptime the pause ends at (0 when the answer wasn't a rate limit). The length comes from the server's own
     * clock (Retry-After, or reset minus the answer's Date), between one second and one hour.
     */
    private static long note(Response r, String token) {
        r.anonymous = token == null;
        if (!r.rateLimited()) return 0;
        long wait;
        if (r.retryAfter >= 0) {
            wait = r.retryAfter * 1000L;
        } else if (r.rateRemaining == 0 && r.rateReset > 0) {
            long serverNow = r.serverDate > 0 ? r.serverDate : System.currentTimeMillis();
            wait = r.rateReset * 1000L - serverNow;
        } else {
            // a secondary limit without a time: GitHub asks for at least a minute
            wait = 60_000L;
        }
        wait = Math.max(1000L, Math.min(MAX_PAUSE, wait));
        r.waitMillis = wait;
        long until = uptime() + wait;
        BLOCKED.merge(key(token), until, Math::max);
        return until;
    }

    private static HttpURLConnection open(String url, String method, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        // v2.0.1: 2G / 3G: a slow first answer is not a failure; v2.1.0: a screen that waits for an answer uses short
        // timeouts (quick mode)
        Integer quick = QUICK.get();
        c.setConnectTimeout(quick != null ? quick : 30_000);
        c.setReadTimeout(quick != null ? quick : 120_000);
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
        // the token goes only to the API and upload hosts (v2.0.4: never to the file host a download is sent on to)
        if (token != null && (url.startsWith(api() + "/") || url.startsWith(uploads() + "/"))) {
            c.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        ACTIVE.put(Thread.currentThread(), c);
        return c;
    }

    // ------------------------------------------------------------------ v2.0.4: cancel

    /** The connection each thread has open (the last one opened), for {@link #cancel}. */
    private static final java.util.concurrent.ConcurrentHashMap<Thread, HttpURLConnection> ACTIVE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** A job was cancelled: never tried again. */
    public static final class Cancelled extends NoRetry {
        Cancelled() {
            super("Cancelled", null);
        }
    }

    /**
     * v2.0.5 review (MEDIUM): the threads whose job was cancelled, paused or stopped. Android's HTTP stack (okio's
     * timeouts) clears a thread's interrupt flag and throws an InterruptedIOException, which looked like a dropped
     * connection worth another try: the cancel is kept here until the job ends ({@link #endJob}).
     */
    private static final java.util.Set<Thread> CANCELLED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** v2.0.4: stops {@code worker}'s transfer: interrupted, and its open connection closed. */
    static void cancel(Thread worker) {
        if (worker == null) return;
        stop(worker);
        // v2.1.3: the job's helper threads (parts sent side by side) stop with it
        for (java.util.Map.Entry<Thread, Thread> e : PARENT.entrySet()) {
            if (e.getValue() == worker) stop(e.getKey());
        }
    }

    private static void stop(Thread thread) {
        CANCELLED.add(thread);
        thread.interrupt();
        HttpURLConnection c = ACTIVE.get(thread);
        if (c != null) {
            try {
                c.disconnect();
            } catch (RuntimeException ignored) {
                // already closed
            }
        }
    }

    /**
     * v2.1.3 (owner's 22 Nov report: uploads slow while downloads are fast): a helper thread of a job's thread (one part
     * of a backup sent beside the others). A cancel, pause or stop of the job reaches it ({@link #cancel}), and it counts
     * as cancelled whenever its job does.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Thread, Thread> PARENT =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** The current thread works for {@code parent}'s job from now on (until {@link #disown}). */
    static void adopt(Thread parent) {
        Thread me = Thread.currentThread();
        if (parent == null || parent == me) return;
        CANCELLED.remove(me);
        PARENT.put(me, parent);
        // a cancel that came before this thread was known
        if (CANCELLED.contains(parent)) CANCELLED.add(me);
    }

    /** The current helper thread's work for its job is done (a pool thread may serve another job next). */
    static void disown() {
        Thread me = Thread.currentThread();
        PARENT.remove(me);
        CANCELLED.remove(me);
        ACTIVE.remove(me);
        Thread.interrupted();
    }

    /** Whether the current thread's job was cancelled (its interrupt flag, or the record a cleared flag can't lose). */
    static boolean isCancelled() {
        Thread t = Thread.currentThread();
        if (t.isInterrupted() || CANCELLED.contains(t)) return true;
        Thread parent = PARENT.get(t);
        return parent != null && CANCELLED.contains(parent);
    }

    /** Throws {@link Cancelled} when the current thread was cancelled. */
    static void stopIfCancelled() throws Cancelled {
        if (isCancelled()) throw new Cancelled();
    }

    /** v2.0.5: a job ends (or starts) on the current thread: its cancel record goes. */
    static void endJob() {
        CANCELLED.remove(Thread.currentThread());
    }

    /** The current thread's work is done with its connection. */
    static void release() {
        ACTIVE.remove(Thread.currentThread());
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
            r.requestId = c.getHeaderField("X-GitHub-Request-Id");
            r.scopes = c.getHeaderField("X-OAuth-Scopes");
            r.location = c.getHeaderField("Location");
            r.serverDate = c.getHeaderFieldDate("Date", 0);
            if (r.serverDate > 0) serverOffset = r.serverDate - System.currentTimeMillis();
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
            release();
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

    /** v2.0.2: counts what goes through to the network for {@link Progress} (about every 32 KB). */
    static final class CountingStream extends java.io.FilterOutputStream {
        private final long total;
        private final Progress progress;
        private long done;
        private long told;

        CountingStream(OutputStream out, long total, Progress progress) {
            super(out);
            this.total = total;
            this.progress = progress;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count(1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count(len);
        }

        private void count(long n) throws IOException {
            done += n;
            if (done - told >= 32 * 1024 || done >= total) {
                told = done;
                stopIfCancelled();
                if (progress != null) progress.bytes(done, total);
            }
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
