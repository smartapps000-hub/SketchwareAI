package com.sketchware.ai.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v1.9.6: a three-step guide for the web and permission blocks (owner's 7 Nov directive): the block drawn in its colour,
 * then 1 where to drop it, 2 what to plug into it, 3 what the app does. Opened by a tap on such a block in the drawer
 * (a long press there drags the block) or by the drawer's "Guide" button. Android classes only (layoutlib renders
 * {@link #content}).
 */
public final class BlockGuide {

    public static final int WEB = 0xFF4A6CD4;
    public static final int PERMISSION = 0xFF009688;
    public static final int FILE = 0xFFA1887F;
    public static final int GITHUB = 0xFF24292F;

    /** One block's guide. */
    public static final class Entry {
        public final String block;
        /** " " command, "b" boolean, "s" value: the block's shape on the canvas. */
        public final String kind;
        public final int color;
        public final String where;
        public final String plug;
        public final String result;

        Entry(String block, String kind, int color, String where, String plug, String result) {
            this.block = block;
            this.kind = kind;
            this.color = color;
            this.where = where;
            this.plug = plug;
            this.result = result;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    /** The opCodes of the Web section, in the drawer's order. */
    public static final String[] WEB_BLOCKS = {"webAllowDownloads", "sketchDownloadFile", "webSetAdBlock", "webSetPopupBlock",
            "webSetMixedContent", "webSearchGoogle", "webGetHistory", "webSetUserAgent", "webSetDesktopMode",
            "webClearCookiesAndCache", "webEnableFileUpload"};
    public static final String[] PERMISSION_BLOCKS = {"requestAutoPermission", "requestCustomPermission",
            "hasAutoPermission", "getPermissionStatus"};
    /** v2.0.2: the GitHub component's blocks. */
    public static final String[] GITHUB_BLOCKS = {"gitHubSetToken", "gitHubGetUser", "gitHubListRepos", "gitHubGetFile",
            "gitHubUploadFile", "gitHubTriggerWorkflow", "gitHubHasToken"};

    static {
        put("webAllowDownloads", "webview1 allow downloads to folder [MyApp] notify [true] only types [ ]", WEB,
                "onCreate, once, after the WebView's page events.",
                "The WebView; a folder inside Downloads (empty: Downloads itself); notify true or false; types: empty "
                        + "or * for every file, else endings or MIME types separated by commas (.mp3, .pdf, audio/*).",
                "Files the page offers are saved by Android's download manager with their real name and ending (from "
                        + "the server's headers, else the link). A long press on an image offers "
                        + "\"Download image\". WebView events: On download start / complete.");
        put("sketchDownloadFile", "download file [link] to folder [MyApp] name [ ] notify [true] type [Default]", FILE,
                "Any event, e.g. a button's onClick. No WebView needed.",
                "The file's link (http or https); a folder inside Downloads; a name (empty: the server's or the "
                        + "link's name); notify true or false; type: Default, a category (Audio, Video, Images, "
                        + "Packages, Documents) or a custom ending / MIME type; it only names a file the link and "
                        + "the server left without an ending.",
                "Android's download manager saves the file in Downloads/folder (Android 10+; older Android without "
                        + "storage permission: the app's own Downloads folder) and shows a notification when done. "
                        + "The name keeps the right ending from the server or the link (never .bin).");
        put("webSetAdBlock", "webview1 block ads [true]", WEB,
                "onCreate, before \"loadUrl\" and after the WebView's page events (its own client is kept).",
                "The WebView; true to block, false to allow again.",
                "Two layers: requests to known ad / tracker hosts get an empty answer, and a style sheet hides ad "
                        + "boxes the page still drew; Back steps over ad pages. A built-in list, not a full filter "
                        + "list like EasyList. Android 8+ (on older Android nothing is blocked).");
        put("webSetPopupBlock", "webview1 block pop-ups [true]", WEB,
                "onCreate, before \"loadUrl\".",
                "The WebView; true to block, false to allow.",
                "Windows a page opens by itself are blocked; a window opened by a tap (download buttons, "
                        + "target=_blank links) opens in this WebView. With ads blocked too, a tap's window to an "
                        + "ad domain is dropped.");
        put("webSetMixedContent", "webview1 mixed content [COMPATIBILITY]", WEB,
                "onCreate, before \"loadUrl\".",
                "COMPATIBILITY (Chrome's rule, the default: http images on https pages load, http scripts don't), "
                        + "NEVER_ALLOW, or ALWAYS_ALLOW (also http scripts: unsafe, only for your own servers).",
                "The WebView loads http parts of https pages by that rule (Android 5+).");
        put("webSearchGoogle", "webview1 search Google for [text]", WEB,
                "A button's onClick or an EditText's action.",
                "The WebView and the text to look for (e.g. edittext1's getText).",
                "Google's results for the text load in the WebView.");
        put("webGetHistory", "webview1 history to list [list]", WEB,
                "Anywhere after pages have loaded, e.g. a History button.",
                "The WebView and a String list (create it under Variable > List).",
                "The list is filled with the addresses this WebView visited, oldest first (its back and forward "
                        + "list); show it with a ListView or Spinner.");
        put("webSetUserAgent", "webview1 user agent [text]", WEB,
                "onCreate, before \"loadUrl\".",
                "The WebView and a user agent text; empty for the WebView's own.",
                "Pages loaded afterwards see that browser name.");
        put("webSetDesktopMode", "webview1 desktop mode [true]", WEB,
                "A switch's onCheckedChange or a menu item.",
                "The WebView; true for desktop, false for mobile.",
                "The page reloads as a desktop browser sees it (desktop user agent, wide view port).");
        put("webClearCookiesAndCache", "webview1 clear cookies and cache", WEB,
                "A button's onClick (e.g. Sign out).",
                "The WebView.",
                "Cache, cookies, web storage, form data and history are cleared.");
        put("webEnableFileUpload", "webview1 enable file upload", WEB,
                "onCreate, before \"loadUrl\".",
                "The WebView.",
                "The page's file inputs open Android's file picker (Android 5+); the chosen files go to the page.");
        put("requestAutoPermission", "request permission [CAMERA]", PERMISSION,
                "onCreate, or a button's onClick before the feature that needs it.",
                "A group (STORAGE, CAMERA, LOCATION ...) or a permission from the list.",
                "Android's dialog for the permissions the group means on this phone's Android version (declared in "
                        + "the manifest at build time). The answer arrives in the activity events On permission "
                        + "granted / On permission denied (Add event).");
        put("requestCustomPermission", "request permission name [text]", PERMISSION,
                "Like \"request permission\".",
                "A permission name as text, e.g. android.permission.SEND_SMS.",
                "Android's dialog for that permission; the same answer events.");
        put("hasAutoPermission", "permission [CAMERA] granted", PERMISSION,
                "Inside an \"if\" block, before using the feature.",
                "A group or a permission.",
                "true when every permission the group needs on this phone is granted.");
        // v2.0.2: the GitHub component (Components > + > GitHub); every answer arrives in its events
        put("gitHubSetToken", "github1 set token [text]", GITHUB,
                "onCreate, before the other GitHub blocks (only for private data or writing).",
                "The GitHub component and a personal access token (github.com > Settings > Developer settings). The "
                        + "token is inside the app: anyone with the APK can read it, so use a fine-grained token with "
                        + "only the repositories and permissions the app needs. Public data needs no token.",
                "The next requests are sent with the token (5000 requests an hour instead of 60).");
        put("gitHubGetUser", "github1 get user [name]", GITHUB,
                "A button's onClick, or onCreate.",
                "A GitHub user name; empty = the token's own account.",
                "Event \"When github1 user loaded\": login, name, avatarUrl and the whole answer as json; or \"When "
                        + "github1 error\" with what went wrong and what to do.");
        put("gitHubListRepos", "github1 list repositories of [user]", GITHUB,
                "A button's onClick, or onCreate.",
                "A user name; empty = the token's own repositories (private ones included).",
                "Event \"repositories listed\": reposJson (name, fullName, description, url, isPrivate, stars, "
                        + "language for each; turn it into a list with the Json to List Map block) and count.");
        put("gitHubGetFile", "github1 get file owner [ ] repo [ ] path [ ]", GITHUB,
                "Any event.",
                "Owner, repository and the file's path (e.g. docs/notes.txt); a private repository needs a token.",
                "Event \"file downloaded\": path, content (the text) and sha.");
        put("gitHubUploadFile", "github1 upload file owner [ ] repo [ ] path [ ] text [ ] message [ ]", GITHUB,
                "A Save button's onClick.",
                "Owner, repository, path, the new text and a commit message; needs a token with Contents: read and "
                        + "write. An existing file is replaced, a new one created.",
                "Event \"file uploaded\": path, sha and htmlUrl (the file on github.com).");
        put("gitHubTriggerWorkflow", "github1 run workflow owner [ ] repo [ ] file [build.yml] branch [main]", GITHUB,
                "A button's onClick.",
                "Owner, repository, the workflow file in .github/workflows (it needs a workflow_dispatch trigger) "
                        + "and a branch; needs a token with Actions: read and write.",
                "GitHub Actions starts the workflow; event \"workflow started\".");
        put("gitHubHasToken", "github1 has token", GITHUB,
                "Inside an \"if\" block.",
                "The GitHub component.",
                "true when a token is set.");
        put("getPermissionStatus", "permission status [CAMERA]", PERMISSION,
                "A text slot, e.g. setText or an \"if equals\".",
                "A group or a permission.",
                "GRANTED, DENIED, or NEVER_ASK_AGAIN (Android won't show the dialog again: send the user to the "
                        + "app's settings).");
    }

    private static void put(String opCode, String block, int color, String where, String plug, String result) {
        // the two permission checks are a boolean and a value block; every other guided block is a command
        String kind = "hasAutoPermission".equals(opCode) || "gitHubHasToken".equals(opCode) ? "b"
                : "getPermissionStatus".equals(opCode) ? "s" : " ";
        ENTRIES.put(opCode, new Entry(block, kind, color, where, plug, result));
    }

    private BlockGuide() {
    }

    public static boolean has(Object opCode) {
        return opCode instanceof String && ENTRIES.containsKey(opCode);
    }

    public static Entry entry(String opCode) {
        return ENTRIES.get(opCode);
    }

    /** Shows the guide for these blocks (one or a whole section). */
    public static Dialog show(Context context, String title, String... opCodes) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content(context, title, opCodes));
        Window window = dialog.getWindow();
        final View content = dialog.findViewById(android.R.id.content);
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - PermissionPicker.dp(context, 32),
                    PermissionPicker.dp(context, 520));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        // v1.9.7: a short Material enter (rise + fade); layoutlib runs no animation, so the sheet still renders
        if (content != null) {
            content.setAlpha(0f);
            content.setTranslationY(PermissionPicker.dp(context, 24));
            content.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
        return dialog;
    }

    public static View content(Context context, String title, String... opCodes) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(PermissionPicker.SURFACE);
        background.setCornerRadius(PermissionPicker.dp(context, 16));
        root.setBackground(background);
        int pad = PermissionPicker.dp(context, 18);
        root.setPadding(pad, pad, pad, pad / 2);

        TextView heading = text(context, title, 18, PermissionPicker.NAME, true);
        root.addView(heading);

        ScrollView scroll = new ScrollView(context);
        LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cards);
        for (String opCode : opCodes) {
            Entry entry = ENTRIES.get(opCode);
            if (entry != null) cards.addView(card(context, entry));
        }
        int maxHeight = context.getResources().getDisplayMetrics().heightPixels * 70 / 100;
        root.addView(new PermissionPicker.Capped(context, scroll, maxHeight), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private static View card(Context context, Entry entry) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, PermissionPicker.dp(context, 14), 0, PermissionPicker.dp(context, 6));

        // v1.9.7: the block drawn the way it looks on the canvas (shape, connector notches, parameter slots)
        BlockShapeView block = new BlockShapeView(context, entry.block, entry.color, entry.kind);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blockParams.bottomMargin = PermissionPicker.dp(context, 10);
        card.addView(block, blockParams);

        card.addView(step(context, 1, "Where to drop", entry.where, entry.color));
        card.addView(step(context, 2, "What to plug", entry.plug, entry.color));
        card.addView(step(context, 3, "What you get", entry.result, entry.color));
        return card;
    }

    private static View step(Context context, int number, String label, String body, int color) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, PermissionPicker.dp(context, 4), 0, PermissionPicker.dp(context, 4));

        TextView badge = text(context, String.valueOf(number), 12, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(BlockShapeView.readable(color)); // white number: same deepened colour as the block
        badge.setBackground(circle);
        int size = PermissionPicker.dp(context, 22);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(size, size);
        badgeParams.rightMargin = PermissionPicker.dp(context, 10);
        badgeParams.topMargin = PermissionPicker.dp(context, 1);
        row.addView(badge, badgeParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(context, label, 13, PermissionPicker.NAME, true));
        texts.addView(text(context, body, 13, PermissionPicker.SUBTITLE, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLineSpacing(0, 1.1f);
        return view;
    }
}
