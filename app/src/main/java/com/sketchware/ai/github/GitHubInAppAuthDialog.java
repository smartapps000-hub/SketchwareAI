package com.sketchware.ai.github;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.sketchware.remod.R;

import mod.jbk.util.LogUtil;

/**
 * v2.0.0 (owner's 13 Nov directive "100% in-app GitHub sign-in"): GitHub's device page in the app's own window instead
 * of Chrome. The app stays in front, so Android 15 never cuts its network while {@link SignIn} checks the code, and the
 * window closes the moment the token arrives.
 * <ul>
 * <li>Only {@code https://github.com} (and its subdomains) opens here; any other address is refused with a short line
 * (Google sign-in refuses embedded pages anyway: GitHub password, passkey or 2FA app instead).</li>
 * <li>No bridge from the page into the app ({@code addJavascriptInterface} is never used); no file or content access;
 * Safe Browsing on (Android 8+); cookies kept, so the next sign-in skips the GitHub password.</li>
 * <li>On the device page a small script fills in the code (the boxes GitHub shows: eight one-letter boxes or one box),
 * with the page's own input events. It never presses Continue or Authorize: the user approves on GitHub's page.</li>
 * <li>Closing the window before the approval cancels the sign-in.</li>
 * </ul>
 */
public final class GitHubInAppAuthDialog extends Dialog {

    /** Called on the main thread. */
    public interface Done {
        /** The sign-in is saved; the window has closed. */
        void signedIn(String login);

        /** The window closed (after {@link #signedIn}, or without a sign-in). */
        default void closed() {
        }
    }

    static final String DEVICE_PAGE = "https://github.com/login/device";
    private static final String CODE_PATTERN = "[A-Za-z0-9]{4}-?[A-Za-z0-9]{4}";

    private final Activity activity;
    private final Done done;
    private final SignIn signIn;
    private final String clientId;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SignIn.Listener listener = this::onSignInChanged;
    private GitHubViews.AuthChrome chrome;
    private WebView web;
    private String loadedFor;
    private boolean filled;
    private int fillTries;
    private boolean signedIn;
    private boolean closed;
    /** Closed only to make room for a new window: the shared sign-in goes on. */
    private boolean keepSignIn;
    /** This window showed a code (so a saved login means this sign-in succeeded). */
    private boolean sawCode;
    /** No login was stored when this window began (a login kept from before proves nothing about this sign-in). */
    private boolean noLoginAtBegin;

    /** The window on screen, if any: one at a time (they share the one sign-in of {@link SignIn}). */
    private static GitHubInAppAuthDialog current;

    /** Opens the window and starts (or continues) a sign-in; a window already open on this screen is reused. */
    public static GitHubInAppAuthDialog show(Activity activity, Done done) {
        GitHubInAppAuthDialog open = current;
        if (open != null && !open.closed && open.isShowing() && open.activity == activity && !activity.isFinishing()) {
            return open;
        }
        if (open != null && !open.closed) {
            // a window left on another (or a recreated) screen: closed quietly, its sign-in carries on here
            open.keepSignIn = true;
            try {
                open.dismiss();
            } catch (RuntimeException ignored) {
                open.close();
            }
        }
        GitHubInAppAuthDialog dialog = new GitHubInAppAuthDialog(activity, done);
        current = dialog;
        dialog.show();
        return dialog;
    }

    private GitHubInAppAuthDialog(Activity activity, Done done) {
        super(activity, android.R.style.Theme_Material_NoActionBar);
        this.activity = activity;
        this.done = done;
        this.signIn = SignIn.get(activity);
        this.clientId = activity.getString(R.string.github_client_id);
        GitHubHttp.userAgent = "SketchwareAI/" + com.sketchware.remod.BuildConfig.VERSION_NAME;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        chrome = new GitHubViews(getContext()).authChrome();
        setContentView(chrome.root);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xFF0B0B0D));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setStatusBarColor(0xFF0B0B0D);
        }
        chrome.close.setOnClickListener(v -> dismiss());
        chrome.code.setOnClickListener(v -> copyCode());
        chrome.retry.setOnClickListener(v -> {
            chrome.retry.setVisibility(View.GONE);
            loadedFor = null;
            filled = false;
            if (web != null) begin();
        });
        setOnDismissListener(d -> close());
        if (!TokenStore.supported()) {
            status("GitHub sign-in needs Android 6.0 or newer.", true);
            AuthGuard.markUnavailable();
            return;
        }
        try {
            web = new WebView(getContext());
        } catch (RuntimeException e) {
            // no WebView on the phone (Android System WebView switched off or missing)
            LogUtil.e("GitHubInAppAuthDialog", "No WebView", e);
            status("Sign-in needs Android System WebView: turn it on in Settings > Apps.", true);
            AuthGuard.markUnavailable();
            return;
        }
        setUpWeb();
        chrome.page.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        signIn.addListener(listener);
        begin();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ sign-in

    private void begin() {
        noLoginAtBegin = TokenStore.get(getContext()).login() == null;
        // a result nobody showed (an older window was closed at that moment) is not this window's
        signIn.takeSignedIn();
        // a code the system interrupted (the app was stopped) continues while it is valid; else a new one
        signIn.resumeSaved(clientId);
        if (signIn.pending() == null) signIn.start(clientId);
        onSignInChanged();
    }

    private void onSignInChanged() {
        if (closed) return;
        if (activity.isDestroyed()) {
            // the screen under the window is gone (recreated): free the WebView; the sign-in itself goes on
            keepSignIn = true;
            close();
            return;
        }
        String login = signIn.takeSignedIn();
        if (login == null && sawCode && noLoginAtBegin && signIn.pending() == null && !signIn.busy()) {
            // another window took the result: the saved login says the same
            login = TokenStore.get(getContext()).login();
        }
        if (login != null) {
            signedIn = true;
            Toast.makeText(activity, "Signed in as @" + login, Toast.LENGTH_SHORT).show();
            dismiss();
            if (done != null) done.signedIn(login);
            return;
        }
        DeviceFlow.Code code = signIn.pending();
        if (code != null) {
            sawCode = true;
            chrome.code.setText(code.userCode);
            chrome.code.setVisibility(View.VISIBLE);
            if (!code.deviceCode.equals(loadedFor)) {
                loadedFor = code.deviceCode;
                filled = false;
                fillTries = 0;
                status("Opening GitHub…", false);
                web.loadUrl(DEVICE_PAGE);
            }
        } else {
            chrome.code.setVisibility(View.GONE);
        }
        if (signIn.finishing()) {
            status("Approved. Signing you in… (keep this open)", false);
        } else if (code == null && signIn.busy()) {
            status("Asking GitHub for a code…", false);
        } else if (code == null && signIn.error() != null) {
            status(signIn.error(), true);
            chrome.retry.setVisibility(View.VISIBLE);
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        if (current == this) current = null;
        signIn.removeListener(listener);
        main.removeCallbacksAndMessages(null);
        // the window was closed before GitHub's approval: the sign-in stops
        if (!signedIn && !keepSignIn && TokenStore.get(getContext()).login() == null) signIn.cancel();
        if (web != null) {
            try {
                CookieManager.getInstance().flush();
                web.stopLoading();
                ((ViewGroup) web.getParent()).removeView(web);
                web.destroy();
            } catch (RuntimeException e) {
                LogUtil.e("GitHubInAppAuthDialog", "WebView cleanup", e);
            }
            web = null;
        }
        if (done != null) done.closed();
    }

    // ------------------------------------------------------------------ the page

    private void setUpWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setBackgroundColor(0xFF0D1117);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // frames inside GitHub's page (a captcha from another host) stay; only the page itself is limited
                return request.isForMainFrame() && refuse(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return refuse(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!allowed(Uri.parse(url))) {
                    // a redirect we didn't get to see: stop and go back to the device page
                    view.stopLoading();
                    refuse(Uri.parse(url));
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageFinished(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    status("GitHub can't be reached. Check the internet and try again.", true);
                    chrome.retry.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                status("The connection to GitHub isn't secure here (check the date, time and network).", true);
                chrome.retry.setVisibility(View.VISIBLE);
            }
        });
    }

    /** https://github.com and its subdomains only. */
    static boolean allowed(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme())) return false;
        String host = uri.getHost();
        return host != null && (host.equals("github.com") || host.endsWith(".github.com"));
    }

    /** True (the page doesn't go there) for any address but GitHub's, with a short line why. */
    private boolean refuse(Uri uri) {
        if (allowed(uri)) return false;
        String host = uri == null ? null : uri.getHost();
        DeviceFlow.Code code = signIn.pending();
        String other = code == null ? "" : " Or enter " + code.userCode + " at github.com/login/device on another device.";
        if (host != null && (host.contains("google") || host.contains("apple"))) {
            status("Google and Apple sign-in don't work inside apps: use your GitHub password." + other, true);
        } else {
            status("Only github.com opens here." + other, true);
        }
        return true;
    }

    private void pageFinished(String url) {
        if (closed || web == null) return;
        Uri uri = Uri.parse(url);
        if (!allowed(uri)) return;
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.equals("/login/device") || path.equals("/login/device/")) {
            fill();
        } else if (path.startsWith("/login/device/")) {
            // past the code (confirmation or "you're all set"): check with GitHub now
            status("Waiting for GitHub…", false);
            signIn.pollNow();
        } else if (path.startsWith("/login") || path.startsWith("/session")) {
            status("Sign in to GitHub. The code goes in by itself.", false);
        }
    }

    /** Puts the code into GitHub's boxes; tries again while the page is still building them. */
    private void fill() {
        DeviceFlow.Code code = signIn.pending();
        if (code == null || filled || web == null || !code.userCode.matches(CODE_PATTERN)) return;
        web.evaluateJavascript(fillScript(code.userCode), result -> {
            if (closed) return;
            String r = result == null ? "" : result.replace("\"", "");
            if (r.startsWith("filled")) {
                filled = true;
                status("Code filled in. Tap Continue, then Authorize.", false);
            } else if (++fillTries < 8) {
                main.postDelayed(this::fill, 600);
            } else {
                status("Type the code " + code.userCode + " (tap it to copy).", false);
            }
        });
    }

    /**
     * The fill script. {@code code} is checked against {@link #CODE_PATTERN} first (letters, digits, one hyphen), so
     * nothing else is ever written into the page. Fills eight one-letter boxes, or one box meant for the code; sets
     * the value through the input's own setter and fires input / change / keyup, which page scripts listen to.
     */
    static String fillScript(String code) {
        return "(function(code){try{"
                + "if(location.hostname!=='github.com'||location.pathname.indexOf('/login/device')!==0)return 'skip';"
                + "var chars=code.replace(/[^A-Za-z0-9]/g,'').toUpperCase();"
                + "var boxes=Array.prototype.slice.call(document.querySelectorAll('input')).filter(function(i){"
                + "var t=(i.getAttribute('type')||'text').toLowerCase();"
                + "if(t!=='text'&&t!=='tel')return false;if(i.disabled||i.readOnly)return false;"
                + "var r=i.getBoundingClientRect();return r.width>0&&r.height>0;});"
                + "var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "function put(el,v){el.focus();setter.call(el,v);"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "el.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true}));}"
                + "var singles=boxes.filter(function(i){return i.maxLength===1;});"
                + "if(singles.length>=8){for(var k=0;k<8;k++)put(singles[k],chars.charAt(k));singles[7].blur();return 'filled8';}"
                + "if(singles.length>0)return 'partial:'+singles.length;"
                + "var named=boxes.filter(function(i){return /code/i.test((i.name||'')+' '+(i.id||''));});"
                + "var target=named.length?named[0]:(boxes.length===1?boxes[0]:null);"
                + "if(target){put(target,target.maxLength>0&&target.maxLength<9?chars:code);target.blur();return 'filled1';}"
                + "return 'none:'+boxes.length;"
                + "}catch(e){return 'error:'+e;}})('" + code.replaceAll("[^A-Za-z0-9-]", "") + "')";
    }

    // ------------------------------------------------------------------ helpers

    private void status(String text, boolean error) {
        if (chrome == null) return;
        chrome.status.setText(text);
        chrome.status.setTextColor(new GitHubViews(getContext()).color(error ? R.color.ob_error : R.color.ob_text_2));
    }

    private void copyCode() {
        DeviceFlow.Code code = signIn.pending();
        if (code == null) return;
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", code.userCode));
        Toast.makeText(activity, "Code copied", Toast.LENGTH_SHORT).show();
    }
}
