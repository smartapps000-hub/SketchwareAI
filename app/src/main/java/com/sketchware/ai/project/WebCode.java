package com.sketchware.ai.project;

/**
 * v1.9.4: WebView blocks beyond 6.4.0-rc05's (owner's priority 1, engineering/v1.9.3-ecosystem-audit.md): downloads
 * through Android's DownloadManager, desktop mode, clearing cookies and cache, file upload from a web page, and the
 * WebView events "download starts" / "download completes". The blocks call the generated helper {@code SketchWeb}
 * ({@link #helperClass}). No permission is added for downloads: on Android 10+ DownloadManager writes to Downloads
 * without one, below that the file goes to the app's own folder unless the app holds WRITE_EXTERNAL_STORAGE. There is
 * no block that ignores SSL errors.
 */
public final class WebCode {

    public static final String CLASS_NAME = "SketchWeb";
    public static final String FILE_NAME = CLASS_NAME + ".java";

    public static final String OP_ALLOW_DOWNLOADS = "webAllowDownloads";
    public static final String OP_DESKTOP_MODE = "webSetDesktopMode";
    public static final String OP_CLEAR_DATA = "webClearCookiesAndCache";
    public static final String OP_FILE_UPLOAD = "webEnableFileUpload";
    // v1.9.6
    public static final String OP_AD_BLOCK = "webSetAdBlock";
    public static final String OP_POPUP_BLOCK = "webSetPopupBlock";
    public static final String OP_MIXED_CONTENT = "webSetMixedContent";
    public static final String OP_SEARCH_GOOGLE = "webSearchGoogle";
    public static final String OP_HISTORY = "webGetHistory";
    public static final String OP_USER_AGENT = "webSetUserAgent";
    public static final String OP_DOWNLOAD_FILE = "downloadFile";

    /** Blocks whose code passes {@code {host}} (the activity or fragment). */
    public static boolean usesHost(String opCode) {
        return OP_FILE_UPLOAD.equals(opCode) || OP_DOWNLOAD_FILE.equals(opCode);
    }

    public static final String LISTENER = "webDownloadListener";
    public static final String EVENT_DOWNLOAD_START = "onDownloadStart";
    public static final String EVENT_DOWNLOAD_COMPLETE = "onDownloadComplete";

    private WebCode() {
    }

    public static boolean uses(String code) {
        return code != null && code.contains(CLASS_NAME + ".");
    }

    /** The WebView's download listener code (Lx.getListenerCode): registers the events' object with the helper. */
    public static String listenerCode(String webView, String events) {
        return CLASS_NAME + ".setDownloadEvents(" + webView + ", new " + CLASS_NAME + ".DownloadEvents() {\r\n"
                + events + "\r\n"
                + "});";
    }

    /** The events' code (Lx.getEventCode): the helper's DownloadEvents methods. */
    public static String eventCode(String eventName, String logic) {
        if (EVENT_DOWNLOAD_START.equals(eventName)) {
            return "@Override\r\n"
                    + "public void onDownloadStart(final String _url, final String _mimeType, final double _contentLength) {\r\n"
                    + logic + "\r\n"
                    + "}";
        }
        return "@Override\r\n"
                + "public void onDownloadComplete(final String _url, final String _fileName, final boolean _success) {\r\n"
                + logic + "\r\n"
                + "}";
    }

    /** Source of {@code <package>.SketchWeb} without AppCompat (framework dialog). */
    public static String helperClass(String packageName) {
        return helperClass(packageName, false);
    }

    /**
     * Source of {@code <package>.SketchWeb}: Java 7, min SDK 19, ASCII. Since v1.9.6 the helper is a real Java file in
     * the IDE's resources ({@code SketchWeb.java.txt} next to this class, compiled on the host before every release);
     * the package line is filled in, and with AppCompat (Material) the image dialog is a MaterialAlertDialogBuilder.
     */
    public static String helperClass(String packageName, boolean appCompat) {
        String source = template().replace("package __PACKAGE__;", "package " + packageName + ";");
        if (appCompat) {
            source = source.replace("new AlertDialog.Builder(",
                    "new com.google.android.material.dialog.MaterialAlertDialogBuilder(");
        }
        return source;
    }

    private static String template;

    private static synchronized String template() {
        if (template == null) {
            java.io.InputStream in = WebCode.class.getResourceAsStream("SketchWeb.java.txt");
            if (in == null) throw new IllegalStateException("SketchWeb.java.txt missing from the IDE");
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                template = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            } catch (java.io.IOException e) {
                throw new IllegalStateException("SketchWeb.java.txt not readable", e);
            } finally {
                try {
                    in.close();
                } catch (java.io.IOException ignored) {
                }
            }
        }
        return template;
    }
}
