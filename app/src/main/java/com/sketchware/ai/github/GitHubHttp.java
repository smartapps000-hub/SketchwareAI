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
 * no library. The token is sent only to the API host, never to github.com's pages or anywhere else.
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

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }
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

    /** A REST call to api.github.com with the token; {@code json} may be null. */
    static Response api(String method, String path, String token, String json) throws IOException {
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

    /** A GET of a file's raw text (Accept: application/vnd.github.raw), as a string. */
    static Response apiRaw(String path, String token) throws IOException {
        HttpURLConnection c = open(api() + path, "GET", token);
        c.setRequestProperty("Accept", "application/vnd.github.raw");
        return read(c);
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

    /** GET a file's raw bytes (Accept: application/vnd.github.raw) into {@code target}; at most {@code maxBytes}. */
    static int download(String path, String token, File target, long maxBytes) throws IOException {
        HttpURLConnection c = open(api() + path, "GET", token);
        c.setRequestProperty("Accept", "application/vnd.github.raw");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            read(c);
            return code;
        }
        File temp = new File(target.getAbsolutePath() + ".part");
        long total = 0;
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > maxBytes) throw new IOException("The file is larger than " + (maxBytes >> 20) + " MB");
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

    private static HttpURLConnection open(String url, String method, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(90_000);
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
            return new Response(code, body);
        } finally {
            c.disconnect();
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
