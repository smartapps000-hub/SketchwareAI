package com.sketchware.ai.project;

/**
 * v2.0.2 (owner's 14 Nov directive "built-in GitHub IDE component, events, blocks, library and diagnostics"): the GitHub
 * component an app can add beside RequestNetwork, Firebase or Bluetooth.
 * <ul>
 * <li>Component type {@link #TYPE} (a built-in like AsyncTask's 36, in {@code ComponentsHandler}; its class info
 * {@code Component.SketchGitHub}, field {@code private SketchGitHub <name>;}, created as {@code new SketchGitHub(this)}).
 * Adding it puts INTERNET into the manifest (yq), as RequestNetwork does.</li>
 * <li>Events through one listener, {@link #LISTENER}: user loaded, repositories listed, file downloaded, file uploaded,
 * workflow started, error (with GitHub's answer explained: {@code SketchGitHub.explain}).</li>
 * <li>A "GitHub" block category ({@link #PALETTE_ID}): set token, get user, list repositories, get file, upload file,
 * run workflow, has token.</li>
 * <li>The helper {@code <package>.SketchGitHub} (resource {@code SketchGitHub.java.txt}: Java 7, min SDK 19, only the
 * Android framework and org.json, no library to download) is written into the project when its code uses it.</li>
 * </ul>
 */
public final class GitHubCode {

    /** The component's type: a value no Sketchware built-in uses, above the custom-component threshold (36). */
    public static final int TYPE = 2026;
    /** What the component is called in the IDE, and the block menu ({@code %m.GitHub}). */
    public static final String TYPE_NAME = "GitHub";
    public static final String CLASS_NAME = "SketchGitHub";
    public static final String FILE_NAME = CLASS_NAME + ".java";
    public static final String CLASS_INFO = "Component." + CLASS_NAME;

    public static final String LISTENER = "gitHubListener";
    public static final String EVENT_USER = "onGitHubUserLoaded";
    public static final String EVENT_REPOS = "onGitHubReposListed";
    public static final String EVENT_FILE = "onGitHubFileDownloaded";
    public static final String EVENT_UPLOADED = "onGitHubFileUploaded";
    public static final String EVENT_WORKFLOW = "onGitHubWorkflowTriggered";
    public static final String EVENT_ERROR = "onGitHubError";
    public static final String[] EVENTS = {EVENT_USER, EVENT_REPOS, EVENT_FILE, EVENT_UPLOADED, EVENT_WORKFLOW, EVENT_ERROR};

    public static final int PALETTE_ID = 101;
    public static final int PALETTE_COLOR = 0xff24292f;
    public static final String COLOR = "#24292F";

    public static final String OP_SET_TOKEN = "gitHubSetToken";
    public static final String OP_GET_USER = "gitHubGetUser";
    public static final String OP_LIST_REPOS = "gitHubListRepos";
    public static final String OP_GET_FILE = "gitHubGetFile";
    public static final String OP_UPLOAD_FILE = "gitHubUploadFile";
    public static final String OP_TRIGGER_WORKFLOW = "gitHubTriggerWorkflow";
    public static final String OP_HAS_TOKEN = "gitHubHasToken";

    private GitHubCode() {
    }

    public static boolean isEvent(String name) {
        for (String e : EVENTS) if (e.equals(name)) return true;
        return false;
    }

    /** The field's initializer in _initialize (Lx). */
    public static String initializer(String componentName) {
        return componentName + " = new " + CLASS_NAME + "(this);";
    }

    /** The listener's code (Lx.getListenerCode): the events' object given to the component. */
    public static String listenerCode(String componentName, String events) {
        return componentName + ".setListener(new " + CLASS_NAME + ".Listener() {\r\n"
                + events + "\r\n"
                + "});";
    }

    /** One event's method (Lx.getEventCode / ManageEvent): the helper's Listener method with the block's logic. */
    public static String eventCode(String eventName, String logic) {
        String signature;
        switch (eventName) {
            case EVENT_USER:
                signature = "onUserLoaded(final String _login, final String _name, final String _avatarUrl, final String _json)";
                break;
            case EVENT_REPOS:
                signature = "onReposListed(final String _user, final String _reposJson, final double _count)";
                break;
            case EVENT_FILE:
                signature = "onFileDownloaded(final String _path, final String _content, final String _sha)";
                break;
            case EVENT_UPLOADED:
                signature = "onFileUploaded(final String _path, final String _sha, final String _htmlUrl)";
                break;
            case EVENT_WORKFLOW:
                signature = "onWorkflowTriggered(final String _workflow, final String _ref)";
                break;
            default:
                signature = "onError(final String _action, final double _code, final String _message)";
                break;
        }
        return "@Override\r\n"
                + "public void " + signature + " {\r\n"
                + logic + "\r\n"
                + "}";
    }

    /** The event block's parameters (EventsHandler.getBlocks). */
    public static String parameters(String eventName) {
        switch (eventName) {
            case EVENT_USER:
                return "%s.login %s.name %s.avatarUrl %s.json";
            case EVENT_REPOS:
                return "%s.user %s.reposJson %d.count";
            case EVENT_FILE:
                return "%s.path %s.content %s.sha";
            case EVENT_UPLOADED:
                return "%s.path %s.sha %s.htmlUrl";
            case EVENT_WORKFLOW:
                return "%s.workflow %s.ref";
            default:
                return "%s.action %d.code %s.message";
        }
    }

    /** The event block's title (EventsHandler.getSpec). */
    public static String spec(String componentName, String eventName) {
        String when = "When " + componentName + " ";
        switch (eventName) {
            case EVENT_USER:
                return when + "user loaded %s.login %s.name %s.avatarUrl %s.json";
            case EVENT_REPOS:
                return when + "repositories listed %s.user %s.reposJson %d.count";
            case EVENT_FILE:
                return when + "file downloaded %s.path %s.content %s.sha";
            case EVENT_UPLOADED:
                return when + "file uploaded %s.path %s.sha %s.htmlUrl";
            case EVENT_WORKFLOW:
                return when + "workflow started %s.workflow %s.ref";
            default:
                return when + "error %s.action %d.code %s.message";
        }
    }

    /** The event's line in the event list (EventsHandler.getDesc). */
    public static String description(String eventName) {
        switch (eventName) {
            case EVENT_USER:
                return "On GitHub user loaded";
            case EVENT_REPOS:
                return "On GitHub repositories listed";
            case EVENT_FILE:
                return "On GitHub file downloaded";
            case EVENT_UPLOADED:
                return "On GitHub file uploaded";
            case EVENT_WORKFLOW:
                return "On GitHub workflow started";
            default:
                return "On GitHub error (explained)";
        }
    }

    /** GitHub's token formats: classic and OAuth tokens (gh?_ + 36) and fine-grained ones (github_pat_...). */
    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile(
            "\\b(gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})\\b");

    /**
     * v2.0.2 (Build Doctor's GitHub check): a warning when the generated code holds a GitHub token (anyone with the APK
     * can read it), naming the file and the token's first characters only; null when none.
     */
    public static String tokenWarning(java.util.List<com.besome.sketch.beans.SrcCodeBean> sources) {
        return tokenWarning(sources, java.util.Collections.<java.io.File>emptyList());
    }

    /** v2.0.2 review: also the project's own Java files ({@code files/java}, up to 2 MB each). */
    public static String tokenWarning(java.util.List<com.besome.sketch.beans.SrcCodeBean> sources,
                                      java.util.List<java.io.File> customJavaFiles) {
        for (com.besome.sketch.beans.SrcCodeBean bean : sources) {
            if (bean.source == null || bean.srcFileName == null || !bean.srcFileName.endsWith(".java")) continue;
            String warning = tokenIn(bean.srcFileName, bean.source);
            if (warning != null) return warning;
        }
        for (java.io.File file : customJavaFiles) {
            if (file == null || !file.isFile() || !file.getName().endsWith(".java") || file.length() > 2_000_000) continue;
            try {
                String warning = tokenIn(file.getName(), new String(java.nio.file.Files.readAllBytes(file.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8));
                if (warning != null) return warning;
            } catch (java.io.IOException | RuntimeException ignored) {
                // unreadable: the compiler says so
            }
        }
        return null;
    }

    private static String tokenIn(String fileName, String source) {
        java.util.regex.Matcher m = TOKEN.matcher(source);
        if (!m.find()) return null;
        String token = m.group(1);
        return fileName + " holds a GitHub token (" + token.substring(0, Math.min(token.length(), 8))
                + "…): anyone who has the APK can read it. Use a fine-grained token limited to the repositories "
                + "and permissions the app needs (or none for public data), and revoke it on github.com if the "
                + "app is shared.";
    }

    /** The helper's source with the project's package (an IDE without the resource: a class saying so). */
    public static String helperClass(String packageName) {
        String template;
        try {
            template = template();
        } catch (IllegalStateException e) {
            return "package " + packageName + ";\n\n/** " + e.getMessage() + ": reinstall Sketchware AI. */\npublic class "
                    + CLASS_NAME + " {\n}\n";
        }
        return template.replace("package __PACKAGE__;", "package " + packageName + ";");
    }

    private static String template;

    private static synchronized String template() {
        if (template == null) {
            java.io.InputStream in = GitHubCode.class.getResourceAsStream(CLASS_NAME + ".java.txt");
            if (in == null) throw new IllegalStateException(CLASS_NAME + ".java.txt missing from the IDE");
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                template = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(CLASS_NAME + ".java.txt not readable", e);
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
