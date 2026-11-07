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

    /** One block's guide. */
    public static final class Entry {
        public final String block;
        public final int color;
        public final String where;
        public final String plug;
        public final String result;

        Entry(String block, int color, String where, String plug, String result) {
            this.block = block;
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

    static {
        put("webAllowDownloads", "webview1 allow downloads to folder [MyApp] notify [true] only types [ ]", WEB,
                "onCreate, once, after the WebView's page events.",
                "The WebView; a folder inside Downloads (empty: Downloads itself); notify true or false; types: empty "
                        + "or * for every file, else endings or MIME types separated by commas (.mp3, .pdf, audio/*).",
                "Files the page offers are saved by Android's download manager with their real name and ending (from "
                        + "the server, else the link, else the file's first bytes). A long press on an image offers "
                        + "\"Download image\". WebView events: On download start / complete.");
        put("sketchDownloadFile", "download file [link] to folder [MyApp] name [ ] notify [true]", FILE,
                "Any event, e.g. a button's onClick. No WebView needed.",
                "The file's link (http or https); a folder inside Downloads; a name (empty: the server's or the "
                        + "link's name); notify true or false.",
                "Android's download manager saves the file in Downloads/folder (Android 10+; older Android without "
                        + "storage permission: the app's own Downloads folder) and shows a notification when done.");
        put("webSetAdBlock", "webview1 block ads [true]", WEB,
                "onCreate, before \"loadUrl\" and after the WebView's page events (its own client is kept).",
                "The WebView; true to block, false to allow again.",
                "Requests to 125 known ad and tracker domains (and their subdomains) get an empty answer, so their ads don't load. "
                        + "A built-in list, not a full filter list. Android 8+ (on older Android nothing is blocked).");
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
        put("getPermissionStatus", "permission status [CAMERA]", PERMISSION,
                "A text slot, e.g. setText or an \"if equals\".",
                "A group or a permission.",
                "GRANTED, DENIED, or NEVER_ASK_AGAIN (Android won't show the dialog again: send the user to the "
                        + "app's settings).");
    }

    private static void put(String opCode, String block, int color, String where, String plug, String result) {
        ENTRIES.put(opCode, new Entry(block, color, where, plug, result));
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
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - PermissionPicker.dp(context, 32),
                    PermissionPicker.dp(context, 520));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
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

        // the block as it looks in the drawer: its colour, white text, rounded like a block
        TextView block = text(context, entry.block, 13, Color.WHITE, true);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(entry.color);
        shape.setCornerRadius(PermissionPicker.dp(context, 6));
        block.setBackground(shape);
        int h = PermissionPicker.dp(context, 10);
        block.setPadding(h, h / 2 + 2, h, h / 2 + 2);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blockParams.bottomMargin = PermissionPicker.dp(context, 8);
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
        circle.setColor(color);
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
