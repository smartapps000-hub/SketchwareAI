package com.sketchware.ai.github;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.sketchware.remod.R;

import java.util.Locale;

import mod.jbk.util.LogUtil;

/**
 * v2.0.0 (owner's 13 Nov directive "100% in-app GitHub sign-in"): GitHub's device page in the app's own window instead
 * of Chrome. The app stays in front, so Android 15 never cuts its network while {@link SignIn} checks the code, and the
 * window closes the moment the token arrives.
 * <ul>
 * <li>Only {@code https://github.com} (and its subdomains) opens here; any other address is refused with a short line.
 * Google, Apple and passkey sign-ins refuse embedded pages: they go through "Sign in via browser" (v2.0.1), which
 * copies the code and opens GitHub's device page in a Custom Tab; coming back to the app checks with GitHub at once.</li>
 * <li>No bridge from the page into the app ({@code addJavascriptInterface} is never used); no file or content access;
 * Safe Browsing on (Android 8+); cookies kept, so the next sign-in skips the GitHub password.</li>
 * <li>On the device page a small script fills in the code (the boxes GitHub shows: eight one-letter boxes or one box),
 * with the page's own input events. It never presses Continue or Authorize: the user approves on GitHub's page.</li>
 * </ul>
 * v2.0.1 (owner's 13 Nov phone report: blank page, cut header, "Too many requests"): two-row header with the thin
 * orange loading line and Reload ({@link GitHubViews#authChrome}); links that open a new window (target=_blank,
 * window.open after a tap) load in this view when they are GitHub's; a refused redirect goes back to the last GitHub
 * page instead of leaving a blank one; a slow page says so after 20 s; a crashed page renderer gets a new WebView
 * (Android 8+; before, the whole app would close); GitHub's "too many requests" page or answer starts a one-minute
 * pause with a countdown (Reload waits too); closing the window pauses the sign-in and keeps its code
 * ({@link SignIn#pause}), so opening it again never asks GitHub for a new code. The user agent stays WebView's own:
 * removing its "wv" mark to get Google's sign-in into an embedded page is what Google's policy forbids.
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
    /** A page that hasn't finished after this long gets a "slow network" line; after the second, a hint to reload. */
    private static final long SLOW_MILLIS = 20_000L;
    private static final long STALLED_MILLIS = 60_000L;
    /** GitHub's "too many requests": Reload waits this long. */
    private static final long LIMIT_PAUSE_MILLIS = 60_000L;
    /** v2.0.2: the Custom Tab of "Sign in via browser", closed with finishActivity when GitHub says yes. */
    static final int REQUEST_BROWSER = 7311;
    /**
     * v2.0.2: the address the page's own click handler goes to when a "Continue with Google / Apple" button is tapped
     * (see {@link #SOCIAL_SCRIPT}): not a bridge into the app, only a navigation the window refuses and answers with
     * the browser hand-off.
     */
    static final String HANDOFF_URL = "sketchware-ai://browser-sign-in";

    private final Activity activity;
    private final Done done;
    private final SignIn signIn;
    private final String clientId;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SignIn.Listener listener = this::onSignInChanged;
    private final Runnable slow = this::pageSlow;
    private final Runnable stalled = this::pageStalled;
    private final Runnable countdown = this::countDown;
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
    /** v2.0.1: the last GitHub page that finished loading (where a refused redirect goes back to). */
    private String lastGoodUrl;
    /** v2.0.1: "Sign in via browser" was tapped before a code was there: open the browser when it comes. */
    private boolean browserWanted;
    /** v2.0.1: the browser was opened for this code: coming back checks with GitHub at once. */
    private boolean openedBrowser;
    /** v2.0.1: GitHub's "too many requests" pause ends at this time (uptime clock), or 0. */
    private long limitedUntil;
    private long lastReload;
    /** v2.0.1: an expired code was renewed once by itself (a second expiry waits for Reload). */
    private boolean renewed;
    private boolean loading;
    /** v2.0.2: uptimes of recent "signed in, now to the device page" moves (no loop with a page GitHub insists on). */
    private final java.util.ArrayDeque<Long> toDevice = new java.util.ArrayDeque<>();
    /** The page renderer kept ending and no new WebView is made (the status says so; not "no WebView"). */
    private boolean webGaveUp;
    /** v2.0.1 (review): uptimes of recent page recoveries and WebView re-creations (loop guards). */
    private final java.util.ArrayDeque<Long> recoveries = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Long> recreations = new java.util.ArrayDeque<>();
    /** Throw-away WebViews of new-window links not yet destroyed (destroyed at close as well). */
    private final java.util.List<WebView> popups = new java.util.ArrayList<>();

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        chrome = new GitHubViews(getContext()).authChrome();
        setContentView(chrome.root);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xFF0B0B0D));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            window.setStatusBarColor(0xFF0B0B0D);
        }
        chrome.close.setOnClickListener(v -> dismiss());
        chrome.code.setOnClickListener(v -> copyCode(true));
        chrome.reload.setOnClickListener(v -> reload());
        chrome.browser.setOnClickListener(v -> openBrowser());
        setOnDismissListener(d -> close());
        if (!TokenStore.supported()) {
            status("GitHub sign-in needs Android 6.0 or newer.", true);
            AuthGuard.markUnavailable();
            return;
        }
        // no WebView (Android System WebView switched off): the code and "Sign in via browser" still work
        createWeb();
        signIn.addListener(listener);
        begin();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            // v2.0.2 review: going back to a landing page is the user's choice (no move to the code page right away)
            backAt = SystemClock.elapsedRealtime();
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    /** v2.0.1: back from the browser (or any other app): check with GitHub now instead of at the next interval. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || closed || signIn.pending() == null) return;
        signIn.pollNow();
        if (openedBrowser) status("Checking with GitHub…", false);
    }

    // ------------------------------------------------------------------ sign-in

    private void begin() {
        noLoginAtBegin = TokenStore.get(getContext()).login() == null;
        // a result nobody showed (an older window was closed at that moment) is not this window's
        signIn.takeSignedIn();
        // a code from before (this window closed, or the app was stopped) continues while it is valid; else a new one
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
            // v2.0.2: the browser tab of "Sign in via browser" closes too, back to the screen the window was opened on
            try {
                activity.finishActivity(REQUEST_BROWSER);
            } catch (RuntimeException ignored) {
                // no tab open
            }
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
            if (web != null && !code.deviceCode.equals(loadedFor)) {
                loadedFor = code.deviceCode;
                filled = false;
                fillTries = 0;
                if (limitedUntil == 0) {
                    status("Opening GitHub…", false);
                    web.loadUrl(DEVICE_PAGE);
                }
            }
            if (browserWanted) {
                browserWanted = false;
                openBrowser();
            } else if (web == null && !webGaveUp && !openedBrowser && !signIn.finishing()) {
                status("This phone has no WebView: use “Sign in via browser” below.", false);
            }
        } else {
            chrome.code.setVisibility(View.GONE);
        }
        if (signIn.finishing()) {
            status("Approved · signing you in… (keep this open)", false);
        } else if (code == null && signIn.busy()) {
            status("Getting a sign-in code…", false);
        } else if (code == null && signIn.error() != null) {
            String errorCode = signIn.errorCode();
            if (("expired_token".equals(errorCode) || "token_expired".equals(errorCode)) && !renewed && isShowing()) {
                // the code ran out while the window was open: one new code by itself
                renewed = true;
                status("The code expired · getting a new one…", false);
                signIn.start(clientId);
                return;
            }
            browserWanted = false;
            if (signIn.waitSeconds() > 0) {
                startLimitPause(signIn.waitSeconds() * 1000L);
            } else {
                status(signIn.error() + " Tap ⟳ to try again.", true);
            }
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        if (current == this) current = null;
        signIn.removeListener(listener);
        main.removeCallbacksAndMessages(null);
        for (WebView popup : new java.util.ArrayList<>(popups)) destroyQuietly(popup);
        popups.clear();
        // v2.0.1: closed before GitHub's approval: the sign-in pauses and keeps its code for the next window
        if (!signedIn && !keepSignIn && TokenStore.get(getContext()).login() == null) signIn.pause();
        destroyWeb();
        if (done != null) done.closed();
    }

    // ------------------------------------------------------------------ the buttons

    /** ⟳: a new try after an error (the same code while it is valid), else the page again. */
    private void reload() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastReload < 1500) return;
        if (limitedUntil > now) {
            countDown();
            return;
        }
        lastReload = now;
        if (signIn.pending() == null) {
            if (signIn.busy()) return;
            renewed = false;
            loadedFor = null;
            begin();
            return;
        }
        if (web == null && !createWeb()) return;
        filled = false;
        fillTries = 0;
        recoveries.clear();
        String url = web.getUrl();
        // a fresh GET of the page (reload() of a page that came from a form would ask to send the form again, and
        // WebView refuses that silently)
        web.loadUrl(url != null && allowed(Uri.parse(url)) ? url : lastGoodUrl != null ? lastGoodUrl : DEVICE_PAGE);
    }

    /**
     * "Sign in via browser" (owner's 13 Nov directive: Google, Apple or passkey accounts): copies the code and opens
     * GitHub's device page in a Custom Tab (a normal browser page when the browser has none). The sign-in goes on in
     * the app: coming back checks with GitHub at once and the window closes when GitHub says yes.
     */
    private void openBrowser() {
        DeviceFlow.Code code = signIn.pending();
        if (code == null) {
            browserWanted = true;
            if (!signIn.busy()) {
                renewed = false;
                begin();
            }
            if (signIn.pending() == null && signIn.busy()) status("Getting a code for the browser…", false);
            return;
        }
        copyCode(false);
        try {
            // v2.0.2: started for a result so the tab can be closed with finishActivity when GitHub says yes
            activity.startActivityForResult(customTab(activity, code.verificationUri != null
                    && code.verificationUri.startsWith("https://github.com/") ? code.verificationUri : DEVICE_PAGE),
                    REQUEST_BROWSER);
            openedBrowser = true;
            status("Code copied: paste " + code.userCode + " in the browser and approve. This closes by itself.", false);
        } catch (ActivityNotFoundException e) {
            if (web == null) {
                // neither a WebView nor a browser: sign-in can't work here, a required sign-in is lifted for this run
                AuthGuard.markUnavailable();
                status("Sign-in needs Android System WebView or a browser on this phone.", true);
            } else {
                status("No browser here: sign in on the page above.", true);
            }
        }
    }

    /**
     * A Custom Tab without the androidx.browser library (the protocol's extras); a plain browser page otherwise.
     * v2.0.2: with the keep-alive extra naming {@link KeepAliveService} (the browser binds it while the tab is open, so
     * the app keeps its network and sees GitHub's "yes" while the tab is still in front).
     */
    static Intent customTab(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        Bundle extras = new Bundle();
        extras.putBinder("android.support.customtabs.extra.SESSION", null);
        intent.putExtras(extras);
        intent.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF0B0B0D);
        intent.putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2);
        intent.putExtra("android.support.customtabs.extra.KEEP_ALIVE",
                new Intent().setClassName(context.getPackageName(), KeepAliveService.class.getName()));
        return intent;
    }

    // ------------------------------------------------------------------ the page

    private boolean createWeb() {
        try {
            web = new WebView(getContext());
        } catch (RuntimeException e) {
            // no WebView on the phone (Android System WebView switched off or missing)
            LogUtil.e("GitHubInAppAuthDialog", "No WebView", e);
            web = null;
            return false;
        }
        setUpWeb(web);
        chrome.page.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return true;
    }

    private void destroyWeb() {
        if (web == null) return;
        WebView w = web;
        web = null;
        try {
            CookieManager.getInstance().flush();
        } catch (RuntimeException e) {
            LogUtil.e("GitHubInAppAuthDialog", "Cookie flush", e);
        }
        destroyQuietly(w);
    }

    /** Stops (when it still can), detaches and destroys a WebView; each step on its own. */
    private static void destroyQuietly(WebView w) {
        try {
            w.stopLoading();
        } catch (RuntimeException ignored) {
            // a WebView whose renderer is gone
        }
        try {
            ViewGroup parent = (ViewGroup) w.getParent();
            if (parent != null) parent.removeView(w);
        } catch (RuntimeException ignored) {
        }
        try {
            w.destroy();
        } catch (RuntimeException ignored) {
        }
    }

    /** True when fewer than {@code max} events happened in the last {@code windowMillis} (and records this one). */
    private static boolean allowAgain(java.util.ArrayDeque<Long> times, int max, long windowMillis) {
        long now = SystemClock.elapsedRealtime();
        while (!times.isEmpty() && now - times.peekFirst() > windowMillis) times.pollFirst();
        if (times.size() >= max) return false;
        times.addLast(now);
        return true;
    }

    @SuppressWarnings("deprecation")
    private void setUpWeb(WebView view) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        // v2.0.1: links that open a new window come to onCreateWindow (GitHub's only, after a tap)
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        view.setBackgroundColor(0xFF0D1117);
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int newProgress) {
                if (v != web) return;
                progress(newProgress);
            }

            @Override
            public boolean onCreateWindow(WebView v, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                return v == web && isUserGesture && openWindow(resultMsg);
            }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                // frames inside GitHub's page (a captcha from another host) stay; only the page itself is limited
                if (!request.isForMainFrame()) return false;
                return intercept(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return intercept(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                if (v != web) return;
                Uri uri = Uri.parse(url);
                if (!allowed(uri) || isSocialSignIn(uri)) {
                    // a redirect or form we didn't get to see: stop it and go back to GitHub (never a blank page)
                    v.stopLoading();
                    if (isSocialSignIn(uri)) {
                        socialHandoff();
                    } else {
                        refuse(uri);
                    }
                    recoverSoon();
                    return;
                }
                loading = true;
                main.removeCallbacks(slow);
                main.removeCallbacks(stalled);
                main.postDelayed(slow, SLOW_MILLIS);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (v != web) return;
                loading = false;
                main.removeCallbacks(slow);
                main.removeCallbacks(stalled);
                progress(100);
                String title = v.getTitle();
                if (title != null && title.toLowerCase(Locale.ROOT).contains("too many requests")) {
                    pageLimited();
                    return;
                }
                pageFinished(url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) {
                // v2.0.2: GitHub moves between its pages without a new load (Turbo): the landing check runs here too
                if (v == web && url != null) toDevicePageIfSignedIn(Uri.parse(url));
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest request, WebResourceResponse response) {
                if (v != web || !request.isForMainFrame()) return;
                if (response.getStatusCode() == 429) pageLimited();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                if (v != web || !request.isForMainFrame()) return;
                loading = false;
                main.removeCallbacks(slow);
                main.removeCallbacks(stalled);
                status("GitHub can't be reached. Check the internet, then tap ⟳.", true);
            }

            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                status("The connection to GitHub isn't secure here (check the date, time and network).", true);
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                // v2.0.1: the page's renderer crashed or was stopped for memory: a new WebView, the same code
                if (v != web) {
                    try {
                        v.destroy();
                    } catch (RuntimeException ignored) {
                    }
                    return true;
                }
                LogUtil.d("GitHubInAppAuthDialog", "WebView renderer gone; a new one is made");
                // the dead WebView leaves the layout at once (the documented way), a new one comes next
                web = null;
                try {
                    ViewGroup parent = (ViewGroup) v.getParent();
                    if (parent != null) parent.removeView(v);
                    v.destroy();
                } catch (RuntimeException ignored) {
                }
                if (!allowAgain(recreations, 2, 60_000L)) {
                    webGaveUp = true;
                    status("GitHub's page keeps closing on this phone: use “Sign in via browser” below.", true);
                    pulse(chrome.browser);
                    return true;
                }
                main.post(() -> {
                    if (closed || web != null || !createWeb()) return;
                    loadedFor = null;
                    status("The page was reloaded.", false);
                    onSignInChanged();
                });
                return true;
            }
        });
    }

    /**
     * A link that opens a new window: a throw-away WebView takes its first address. GitHub's opens here; any other is
     * refused with the usual line (no second window ever shows).
     */
    private boolean openWindow(Message resultMsg) {
        WebView popup;
        try {
            popup = new WebView(getContext());
        } catch (RuntimeException e) {
            return false;
        }
        boolean[] taken = {false};
        popups.add(popup);
        Runnable drop = () -> {
            if (popups.remove(popup)) destroyQuietly(popup);
        };
        popup.getSettings().setJavaScriptEnabled(false);
        popup.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                take(request.getUrl());
                return true;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                take(Uri.parse(url));
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                if (url == null || url.startsWith("about:")) return;
                v.stopLoading();
                take(Uri.parse(url));
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                // review: every WebView must handle its renderer's end, or Android ends the app
                taken[0] = true;
                popups.remove(v);
                destroyQuietly(v);
                return true;
            }

            private void take(Uri uri) {
                if (taken[0]) return;
                taken[0] = true;
                // the throw-away view is destroyed on the main looper (a detached view's post never runs)
                main.post(() -> {
                    drop.run();
                    if (closed || web == null) return;
                    if (allowed(uri)) {
                        web.loadUrl(uri.toString());
                    } else {
                        refuse(uri);
                    }
                });
            }
        });
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(popup);
        resultMsg.sendToTarget();
        // a window that never goes anywhere is dropped
        main.postDelayed(() -> {
            if (!taken[0]) {
                taken[0] = true;
                drop.run();
            }
        }, 10_000);
        return true;
    }

    /** https://github.com and its subdomains only. */
    static boolean allowed(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme())) return false;
        String host = uri.getHost();
        return host != null && (host.equals("github.com") || host.endsWith(".github.com"));
    }

    /**
     * v2.0.2: the navigation decision for the page itself: the hand-off address and GitHub's own "Continue with Google /
     * Apple" addresses start the browser sign-in at once (nothing loads, so no blank page); other hosts are refused.
     */
    private boolean intercept(Uri uri) {
        if (uri != null && HANDOFF_URL.equals(uri.toString())) {
            socialHandoff();
            return true;
        }
        if (isSocialSignIn(uri)) {
            socialHandoff();
            return true;
        }
        if (!refuse(uri)) return false;
        recoverSoon();
        return true;
    }

    /**
     * GitHub's sign-in with another account (Google, Apple, Microsoft): GitHub's own {@code /sessions/social/…}
     * addresses and the providers' sign-in hosts. Those providers refuse sign-in inside an app's WebView.
     */
    static boolean isSocialSignIn(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        if (allowed(uri)) {
            String path = uri.getPath() == null ? "" : uri.getPath();
            return path.startsWith("/sessions/social") || path.startsWith("/session/social")
                    || path.startsWith("/login/social") || path.contains("/social/google") || path.contains("/social/apple");
        }
        return host.equals("accounts.google.com") || host.endsWith(".google.com") && host.startsWith("accounts")
                || host.equals("appleid.apple.com") || host.equals("login.microsoftonline.com") || host.equals("login.live.com");
    }

    private long lastHandoff;
    /** v2.0.2 review: the last Back inside the page, and the last move to the code page (uptime clock). */
    private long backAt, toDeviceAt;

    /** Google, Apple, Microsoft: the browser sign-in starts by itself (code copied, GitHub's device page in a tab). */
    private void socialHandoff() {
        long now = SystemClock.elapsedRealtime();
        // one hand-off for one tap (a form and its redirect both arrive here)
        if (now - lastHandoff < 3000) return;
        // v2.0.2 review: a late redirect of the same tap while the browser tab is in front opens no second tab
        if (openedBrowser && !focused() && signIn.pending() != null) return;
        lastHandoff = now;
        status("Google / Apple sign-in opens in the browser: the code is copied.", false);
        openBrowser();
    }

    /**
     * v2.0.2 (owner's 13 Nov phone test: after signing in, GitHub's home feed stayed on screen): when GitHub lands on
     * its home page, dashboard or a welcome page, the device page opens again so the code goes in and Authorize comes
     * next. Signed out, only GitHub's front page is moved. At most three times a minute (a page GitHub insists on
     * stays).
     */
    private void toDevicePageIfSignedIn(Uri uri) {
        if (closed || web == null || uri == null || !allowed(uri) || !"github.com".equals(uri.getHost())) return;
        if (signIn.pending() == null || signIn.finishing()) return;
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        boolean front = path.equals("/");
        boolean landing = front || path.startsWith("/dashboard") || path.equals("/home") || path.startsWith("/feed")
                || path.startsWith("/explore") || path.startsWith("/welcome") || path.startsWith("/onboarding")
                || path.startsWith("/copilot") || path.startsWith("/notifications");
        if (!landing) return;
        boolean signedIn = signedInOnGitHub();
        if (!front && !signedIn) return;
        long now = SystemClock.elapsedRealtime();
        // v2.0.2 review: never during GitHub's "too many requests" pause; one move per landing (history update and
        // page end both arrive here); a page reached with Back stays
        if (limitedUntil > now || now - toDeviceAt < 3000 || now - backAt < 3000) return;
        if (!allowAgain(toDevice, 3, 60_000L)) return;
        toDeviceAt = now;
        status(signedIn ? "Signed in to GitHub · opening the code page…" : "Opening GitHub's code page…", false);
        filled = false;
        fillTries = 0;
        main.post(() -> {
            if (!closed && web != null) web.loadUrl(DEVICE_PAGE);
        });
    }

    /** This window is in front (not the browser tab or another app). */
    private boolean focused() {
        android.view.Window w = getWindow();
        return w != null && w.getDecorView().hasWindowFocus();
    }

    /** GitHub's own "signed in" cookie in the window's cookie store. */
    static boolean signedInOnGitHub() {
        try {
            String cookies = CookieManager.getInstance().getCookie("https://github.com");
            return cookies != null && cookies.contains("logged_in=yes");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * v2.0.2: on GitHub's sign-in and sign-up pages, a tap on "Continue with Google / Apple" goes to
     * {@link #HANDOFF_URL} instead (the window answers with the browser sign-in before anything loads). Checks only the
     * button's own link, form address or text; touches nothing else on the page and reads no field.
     */
    static final String SOCIAL_SCRIPT = "(function(){try{if(window.__swSocial)return 'again';window.__swSocial=1;"
            + "function social(el){if(!el)return false;var a=el.closest('a,button,form');if(!a)return false;"
            + "var target=(a.getAttribute('href')||a.getAttribute('action')||a.getAttribute('formaction')||'');"
            + "var form=a.form||a.closest('form');if(form)target+=' '+(form.getAttribute('action')||'');"
            + "if(/\\/social\\/|social_provider|\\/auth\\/(google|apple)/i.test(target))return true;"
            + "var text=(a.textContent||'').replace(/\\s+/g,' ').trim();"
            + "return /^(continue|sign (in|up)) with (google|apple)$/i.test(text);}"
            + "function go(e){if(social(e.target)){e.preventDefault();e.stopPropagation();"
            + "location.href='" + HANDOFF_URL + "';}}"
            + "document.addEventListener('click',go,true);"
            + "document.addEventListener('submit',function(e){var f=e.target;var t=(f&&f.getAttribute('action'))||'';"
            + "if(/\\/social\\/|social_provider/i.test(t)){e.preventDefault();e.stopPropagation();"
            + "location.href='" + HANDOFF_URL + "';}},true);return 'ok';}catch(e){return 'error:'+e;}})()";

    /** True (the page doesn't go there) for any address but GitHub's, with a short line why. */
    private boolean refuse(Uri uri) {
        if (allowed(uri)) return false;
        String host = uri == null ? null : uri.getHost();
        String scheme = uri == null ? null : uri.getScheme();
        if (scheme != null && (scheme.equals("mailto") || scheme.equals("tel") || scheme.equals("intent"))) {
            // GitHub's help links: nothing to open from a sign-in window
            return true;
        }
        if (host != null && (host.contains("google") || host.contains("apple") || host.contains("microsoft")
                || host.contains("live.com"))) {
            status("Google, Apple, passkeys: use “Sign in via browser” below.", false);
            pulse(chrome.browser);
        } else {
            status("Only github.com opens here. Other sign-ins: “Sign in via browser” below.", true);
        }
        return true;
    }

    /**
     * v2.0.1 (owner's 13 Nov report: blank page after "Continue with Google"): a refused navigation can leave an empty
     * page behind. Shortly after, an empty or foreign page is replaced by the last GitHub page (or the device page).
     */
    private void recoverSoon() {
        main.postDelayed(() -> {
            if (closed || web == null) return;
            String url = web.getUrl();
            if (url == null || !allowed(Uri.parse(url))) {
                recover();
                return;
            }
            web.evaluateJavascript("(function(){return document.body?document.body.innerText.trim().length:0;})()",
                    result -> {
                        if (closed || web == null) return;
                        if (result == null || "0".equals(result) || "null".equals(result)) recover();
                    });
        }, 300);
    }

    /** Loads the last GitHub page again: at most twice in 30 s (review: no loop with a page that leaves by itself). */
    private void recover() {
        if (!allowAgain(recoveries, 2, 30_000L)) {
            status("This sign-in leaves github.com: use “Sign in via browser” below.", true);
            pulse(chrome.browser);
            return;
        }
        web.loadUrl(lastGoodUrl != null ? lastGoodUrl : DEVICE_PAGE);
    }

    private void pageFinished(String url) {
        if (closed || web == null) return;
        Uri uri = Uri.parse(url);
        if (!allowed(uri)) return;
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.contains("/social") && !path.contains("/oauth")) lastGoodUrl = url;
        if (path.startsWith("/login") || path.startsWith("/session") || path.startsWith("/signup")
                || path.startsWith("/join") || path.equals("/")) {
            // v2.0.2: "Continue with Google / Apple" goes to the browser sign-in before anything loads
            web.evaluateJavascript(SOCIAL_SCRIPT, null);
        }
        toDevicePageIfSignedIn(uri);
        if (path.equals("/login/device") || path.equals("/login/device/")) {
            fill();
        } else if (path.startsWith("/login/device/")) {
            // past the code (confirmation or "you're all set"): check with GitHub now
            status("Checking with GitHub…", false);
            signIn.pollNow();
        } else if (path.startsWith("/login") || path.startsWith("/session")) {
            status("Sign in below · the code goes in by itself.", false);
        }
    }

    private void pageSlow() {
        if (closed || !loading) return;
        status("Slow network: GitHub is still loading…", false);
        main.postDelayed(stalled, STALLED_MILLIS - SLOW_MILLIS);
    }

    private void pageStalled() {
        if (closed || !loading) return;
        status("GitHub isn't answering. Tap ⟳, or use the browser below.", true);
    }

    /** GitHub showed "Too many requests": a one-minute pause with a countdown; nothing is loaded meanwhile. */
    private void pageLimited() {
        if (closed) return;
        startLimitPause(LIMIT_PAUSE_MILLIS);
        pulse(chrome.browser);
    }

    private void startLimitPause(long millis) {
        long until = SystemClock.elapsedRealtime() + millis;
        if (until > limitedUntil) limitedUntil = until;
        countDown();
    }

    private void countDown() {
        main.removeCallbacks(countdown);
        if (closed) return;
        long rest = limitedUntil - SystemClock.elapsedRealtime();
        if (rest <= 0) {
            limitedUntil = 0;
            status("Ready again: tap ⟳ to reload GitHub.", false);
            return;
        }
        status("Too many requests from this network. Try again in " + ((rest + 999) / 1000)
                + " s, or use the browser.", true);
        main.postDelayed(countdown, 1000);
    }

    private void progress(int value) {
        if (chrome == null) return;
        if (value >= 100) {
            chrome.progress.setProgress(100);
            main.postDelayed(() -> {
                if (!loading && chrome != null) chrome.progress.setVisibility(View.INVISIBLE);
            }, 250);
            return;
        }
        chrome.progress.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 24) {
            chrome.progress.setProgress(Math.max(5, value), true);
        } else {
            chrome.progress.setProgress(Math.max(5, value));
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
                status("Code auto-filled · tap Continue, then Authorize.", false);
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

    /** A short nudge on a view the status line points at. */
    private static void pulse(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.animate().scaleX(1.03f).scaleY(1.03f).setDuration(140)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
    }

    private void copyCode(boolean toast) {
        DeviceFlow.Code code = signIn.pending();
        if (code == null) return;
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", code.userCode));
        if (toast) Toast.makeText(activity, "Code copied", Toast.LENGTH_SHORT).show();
    }
}
