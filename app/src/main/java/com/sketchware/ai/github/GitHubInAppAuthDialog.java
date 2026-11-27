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
    /** Third review: how long a code waits for the device page asked for at the start before it is asked for again. */
    private static final long DEVICE_PAGE_WAIT = 15_000L;
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
    private final Runnable devicePageCheck = this::devicePageCheck;
    /** v2.1.4: the page is read for a code field / "Send a code via email" this often while the window is open. */
    private static final long PROBE_MILLIS = 1500L;
    private final Runnable probeTick = this::probeTick;
    private final OtpNotifications.Sink otpSink = this::codeFromNotification;
    /** v2.1.4: what the page itself showed at the last look ({@link OtpAssist#PROBE_SCRIPT}), else null. */
    private OtpAssist.Probe probe;
    /** v2.1.4: the code bar's last drawn state (the bar and the status line change only when it changes). */
    private String otpState;
    /** v2.1.4: when the window lost focus while the page had a code field (wall clock), else 0. */
    private long leftForCodeAt;
    /**
     * v2.1.5 (owner's 23 Nov report: "the code should come into the app without opening the e-mail"): Android tells the
     * window when something is copied (Gmail's notification "Copy code", a copy in split screen; Android 10+ only while
     * this window is in front, else the code is read on return as before).
     */
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    /** v2.1.4: the last code put in by itself (copied or from a notification) and when: never the same twice. */
    private String lastAutoCode;
    private long lastAutoAt;
    /** v2.1.4: a code that came (notification) before GitHub's page showed its field: filled when the field comes. */
    private String waitingCode;
    private String waitingFrom;
    private long waitingAt;
    /** Review (MEDIUM): every code this window put in or tried (by hand too): never filled by itself again. */
    private final java.util.Set<String> usedCodes = new java.util.HashSet<>();
    /**
     * Review (MEDIUM): codes from notifications count only from this time on (wall clock): when the page offered
     * "Send a code via email" / E-mail me a code was tapped, else 30 s before a code page first showed.
     */
    private long codeFloor;
    /** Review (LOW): notification access as the bar last drew it (a focus return redraws only when it changed). */
    private boolean drawnWithNotifications;
    /**
     * Third review: when the device page asked for at the start (before the code) was requested
     * ({@code SystemClock.elapsedRealtime()}), 0 once a page started or failed: a code arriving meanwhile waits for it
     * instead of loading it a second time.
     */
    private long devicePageAskedAt;
    private GitHubViews.AuthChrome chrome;
    private WebView web;
    private String loadedFor;
    /** v2.0.3: the main page is (being) GitHub's device page: a code that arrives is filled in without a reload. */
    private boolean onDevicePage;
    /**
     * Second review: the main page's last load failed (WebView then shows its error page at that address): a code that
     * arrives loads GitHub's device page again instead of being typed into the error page.
     */
    private boolean pageFailed;
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
        // v2.1.2: the verification-code bar
        chrome.openMail.setOnClickListener(v -> openMail());
        chrome.pasteFill.setOnClickListener(v -> pasteAndFill());
        chrome.sendMail.setOnClickListener(v -> sendMailCode());
        chrome.otpAuto.setOnClickListener(v -> explainAutoCodes());
        setOnDismissListener(d -> close());
        watchClipboard();
        if (!TokenStore.supported()) {
            status("GitHub sign-in needs Android 6.0 or newer.", true);
            AuthGuard.markUnavailable();
            return;
        }
        // no WebView (Android System WebView switched off): the code and "Sign in via browser" still work
        createWeb();
        signIn.addListener(listener);
        begin();
        // v2.1.4: GitHub's code pages are found by what they show (they come at many addresses)
        main.postDelayed(probeTick, PROBE_MILLIS);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            // v2.0.2 review: the page Back reaches is the user's choice (no move to the code page from it)
            backPending = true;
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
        if (closed) return;
        // v2.1.4: away to copy GitHub's code (Gmail, an authenticator app): a code copied meanwhile goes in by itself
        if (!hasFocus) {
            // review (LOW): also from "Send a code via email" (Open Gmail right after E-mail me a code)
            if (codePage() || "send".equals(otpKind)) leftForCodeAt = System.currentTimeMillis();
        } else {
            long left = leftForCodeAt;
            leftForCodeAt = 0;
            if (left > 0 && (codePage() || "send".equals(otpKind))) codeFromClipboard(left);
            // review (LOW): redrawn only when notification access changed in Android's settings (a redraw puts the
            // bar's hint over the status line: sign-in errors and "signing you in" stay)
            if (chrome != null && chrome.otp != null && chrome.otp.getVisibility() == View.VISIBLE
                    && OtpNotifications.enabled(getContext()) != drawnWithNotifications) {
                otpState = null;
                otpBar(currentUri());
            }
            probeNow();
        }
        if (!hasFocus || signIn.pending() == null) return;
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
        if (signIn.pending() == null) {
            signIn.start(clientId);
            // v2.0.3 (owner's 15 Nov phone test: a blank window under "Getting a sign-in code…"): GitHub's device page
            // opens at once (sign in there meanwhile); the code goes into it when GitHub sends it
            if (web != null && limitedUntil == 0) {
                devicePageAskedAt = SystemClock.elapsedRealtime();
                web.loadUrl(DEVICE_PAGE);
            }
        }
        onSignInChanged();
    }

    /**
     * v2.0.3 review: GitHub's sign-in pages (password, two-factor, sign-up): a code that arrives meanwhile must not reload
     * the window under the user; GitHub returns to the device page after signing in, and that page gets the code.
     */
    private boolean onSignInPage() {
        String url = web == null ? null : web.getUrl();
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        if (!"github.com".equals(uri.getHost())) return false;
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/login/device")) return false;
        return path.startsWith("/login") || path.startsWith("/session") || path.startsWith("/signup")
                || path.startsWith("/join") || path.startsWith("/password_reset");
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
            OtpNotifications.disarm(otpSink);
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
                if (onDevicePage && !pageFailed) {
                    // v2.0.3: the page opened before the code came: filled now (or when it has finished loading)
                    if (!loading) fill();
                } else if (onSignInPage() && !pageFailed) {
                    // review: the user is signing in: GitHub's way back to the device page fills the code (no reload
                    // under a half-typed password)
                    status("Sign in on GitHub's page: the code " + code.userCode + " goes in after it.", false);
                } else if (devicePageAskedAt > 0 && !pageFailed
                        && SystemClock.elapsedRealtime() - devicePageAskedAt < DEVICE_PAGE_WAIT) {
                    // third review: the page asked for at the start is on its way and fills the code when it opens;
                    // loaded again only if it hasn't started by then
                    status("Opening GitHub…", false);
                    main.removeCallbacks(devicePageCheck);
                    main.postDelayed(devicePageCheck,
                            DEVICE_PAGE_WAIT - (SystemClock.elapsedRealtime() - devicePageAskedAt));
                } else if (limitedUntil == 0) {
                    // second review: also when the early page failed or hasn't started (a reload costs nothing then)
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
            String note = signIn.startNote();
            status(note == null ? "Getting a sign-in code…" : "Getting a sign-in code… (" + note + ")", false);
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
        OtpNotifications.disarm(otpSink);
        unwatchClipboard();
        for (WebView popup : new java.util.ArrayList<>(popups)) destroyQuietly(popup);
        popups.clear();
        // v2.0.1: closed before GitHub's approval: the sign-in pauses and keeps its code for the next window
        // v2.0.3 review: also when a sign-in was kept from before ("Sign in again"): only a sign-in that finished
        // meanwhile in another window is left alone
        boolean finishedElsewhere = noLoginAtBegin && TokenStore.get(getContext()).login() != null;
        if (!signedIn && !keepSignIn && !finishedElsewhere) signIn.pause();
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
    public static Intent customTab(Context context, String url) {
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
                pageFailed = false;
                devicePageAskedAt = 0;
                main.removeCallbacks(devicePageCheck);
                String startedPath = uri.getPath() == null ? "" : uri.getPath();
                onDevicePage = "github.com".equals(uri.getHost())
                        && (startedPath.equals("/login/device") || startedPath.equals("/login/device/"));
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
                if (v != web || url == null) return;
                if (backPending) {
                    backPending = false;
                    // third review pass: a Back that never committed (refused redirect, failed load, a load of
                    // the app's own) doesn't hold over a page that comes much later
                    if (SystemClock.elapsedRealtime() - backAt < 10_000L) {
                        backUrl = url;
                        return;
                    }
                }
                if (!url.equals(backUrl)) backUrl = null;
                toDevicePageIfSignedIn(Uri.parse(url));
                otpBar(Uri.parse(url));
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
                pageFailed = true;
                onDevicePage = false;
                devicePageAskedAt = 0;
                main.removeCallbacks(devicePageCheck);
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
    private long toDeviceAt;
    /** v2.0.2 review: Back was pressed and its page hasn't committed yet; the page Back reached (it stays). */
    private boolean backPending;
    private long backAt;
    private String backUrl;

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
        if (limitedUntil > now || now - toDeviceAt < 3000 || uri.toString().equals(backUrl)) return;
        if (!allowAgain(toDevice, 3, 60_000L)) return;
        toDeviceAt = now;
        status(signedIn ? "Signed in to GitHub · opening the code page…" : "Opening GitHub's code page…", false);
        filled = false;
        fillTries = 0;
        main.post(() -> {
            if (!closed && web != null && limitedUntil <= SystemClock.elapsedRealtime()) web.loadUrl(DEVICE_PAGE);
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

    /** Third review: the device page asked for at the start never started: asked for again (the code waits). */
    private void devicePageCheck() {
        long now = SystemClock.elapsedRealtime();
        if (closed || web == null || devicePageAskedAt == 0 || limitedUntil > now) return;
        // fourth review: a load still on its way (slow network) isn't restarted, up to the "stalled" time
        int progress = web.getProgress();
        if (progress > 0 && progress < 100 && now - devicePageAskedAt < STALLED_MILLIS) {
            main.postDelayed(devicePageCheck, 5_000L);
            return;
        }
        devicePageAskedAt = 0;
        if (signIn.pending() != null) {
            status("Opening GitHub…", false);
            web.loadUrl(DEVICE_PAGE);
        }
    }

    private void pageFinished(String url) {
        // third review: WebView's error page (at the failed address) is never filled
        if (closed || web == null || pageFailed) return;
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
        probe = null;
        otpBar(uri);
        probeNow();
        if (path.equals("/login/device") || path.equals("/login/device/")) {
            fill();
        } else if (path.startsWith("/login/device/")) {
            // past the code (confirmation or "you're all set"): check with GitHub now
            status("Checking with GitHub…", false);
            signIn.pollNow();
        } else if ((path.startsWith("/login") || path.startsWith("/session")) && otpKind == null) {
            // v2.1.2 review (LOW): GitHub's code pages keep the code bar's hint
            status("Sign in below · the code goes in by itself.", false);
        }
    }

    /**
     * v2.1.2: what the verification-code bar is for on the page shown now ("email", "two-factor", "login"; v2.1.4 also
     * "code": a code field GitHub doesn't say more about, and "send": "Send a code via email" without a field yet),
     * else null.
     */
    private String otpKind;

    /** v2.1.4: the page shown now has a field for a code (the bar's Paste & fill and the automatic fill work there). */
    private boolean codePage() {
        return "email".equals(otpKind) || "code".equals(otpKind) || "two-factor".equals(otpKind);
    }

    private Uri currentUri() {
        String url = web == null ? null : web.getUrl();
        return url == null ? null : Uri.parse(url);
    }

    /** v2.1.4: reads the page again for a code field / "Send a code via email" (and keeps reading while open). */
    private void probeTick() {
        if (closed) return;
        // review (LOW): a screen re-created under the window without a dismiss: the window stops (no leak, no listener)
        if (activity.isFinishing() || activity.isDestroyed()) {
            // second review (LOW): as onSignInChanged's own case: the window goes, the sign-in itself goes on
            keepSignIn = true;
            try {
                dismiss();
            } catch (RuntimeException ignored) {
                // already gone from the window manager
            }
            close();
            return;
        }
        main.removeCallbacks(probeTick);
        main.postDelayed(probeTick, PROBE_MILLIS);
        // review (LOW): only while the window is in front (the page is read again on return)
        Window w = getWindow();
        if (w != null && !w.getDecorView().hasWindowFocus()) return;
        probeNow();
    }

    private void probeNow() {
        if (closed || web == null || chrome == null || signedIn) return;
        final WebView page = web;
        Uri uri = currentUri();
        if (uri == null || !"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())) {
            if (probe != null) {
                probe = null;
                otpBar(uri);
            }
            return;
        }
        try {
            page.evaluateJavascript(OtpAssist.PROBE_SCRIPT, answer -> {
                if (closed || page != web) return;
                OtpAssist.Probe seen = OtpAssist.Probe.parse(OtpAssist.unquote(answer));
                // a page between two loads answers nothing: the last look stands until the next one
                if (seen == null) return;
                probe = seen;
                otpBar(currentUri());
            });
        } catch (RuntimeException ignored) {
            // the WebView is going away
        }
    }

    /** v2.1.2: the bar shows on GitHub's verification pages only (an e-mailed code: with Open Gmail). */
    private void otpBar(Uri uri) {
        if (chrome == null || chrome.otp == null) return;
        String pathKind = uri == null ? null : OtpAssist.pageKind(uri.getScheme(), uri.getHost(), uri.getPath());
        boolean onGitHub = uri != null && "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost());
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath();
        // review (LOW): recovery-code and passkey pages are never code pages, whatever their fields are called
        boolean vetoed = path.contains("recovery") || path.contains("webauthn");
        OtpAssist.Probe p = onGitHub && !vetoed ? probe : null;
        // v2.1.4 (owner's 22 Nov report: no bar on "Confirm access" / "Enter the verification code sent to
        // b***@gmail.com"): the page itself decides first; the address only when the page hasn't been read yet
        String kind;
        if (p != null && p.codeField) {
            if (p.sentTo != null || "email".equals(pathKind)) kind = "email";
            else if ("two-factor".equals(pathKind)) kind = "two-factor";
            else kind = "code";
        } else if ((p != null && p.mobile) || "mobile".equals(pathKind)) {
            // v2.1.5: GitHub Mobile's request: answered only in that app (E-mail me a code when GitHub offers one)
            kind = "mobile";
        } else if (p != null && p.sendLink) {
            kind = "send";
        } else {
            kind = pathKind;
        }
        String before = otpKind;
        otpKind = kind;
        boolean notifications = OtpNotifications.enabled(getContext());
        String state = kind + "|" + (p == null ? "" : p.key()) + "|" + notifications;
        if (state.equals(otpState)) return;
        otpState = state;
        drawnWithNotifications = notifications;
        boolean waits = "email".equals(kind) || "code".equals(kind) || "send".equals(kind);
        long now = System.currentTimeMillis();
        if ("send".equals(kind) && !"send".equals(before)) {
            // review (MEDIUM): a code from before GitHub offered to send one is never this page's code; second review
            // (LOW): only when the page turns into "send" (a redraw doesn't move it past an e-mail that came)
            codeFloor = Math.max(codeFloor, now);
        } else if (waits && codeFloor == 0) {
            codeFloor = now - 30_000L;
        }
        if (waits) {
            OtpNotifications.arm(getContext(), otpSink, codeFloor);
        } else if (probe != null || !onGitHub || vetoed) {
            // review (MEDIUM): only on a page that was read (a page between loads keeps the listener as it was)
            OtpNotifications.disarm(otpSink);
        }
        if (kind == null) {
            chrome.otp.setVisibility(View.GONE);
            return;
        }
        if ("email".equals(kind) || "code".equals(kind)) fillWaitingCode();
        boolean login = "login".equals(kind);
        boolean send = "send".equals(kind);
        boolean mobile = "mobile".equals(kind);
        boolean offersMail = send || (mobile && p != null && p.sendLink);
        boolean email = "email".equals(kind) || "code".equals(kind) || login || send;
        String sentTo = p == null ? null : p.sentTo;
        boolean confirm = p != null && p.confirmAccess;
        // v2.1.3: on GitHub's sign-in page too, for the code it may e-mail after the password
        String title;
        if (mobile) title = "Approve in the GitHub Mobile app";
        else if (send) title = confirm ? "Confirm access with an e-mailed code" : "Get a code by e-mail";
        else if (login) title = "Code by e-mail?";
        else if ("two-factor".equals(kind)) title = "Two-factor code";
        else if (sentTo != null) title = "Code sent to " + sentTo;
        else if ("email".equals(kind)) title = "Email verification code";
        else title = "Verification code";
        chrome.otpTitle.setText(title);
        String note = null;
        if (mobile) note = "GitHub sent this request to its own GitHub Mobile app only: it can't be answered here. "
                + (offersMail ? "No GitHub Mobile? E-mail me a code." : "No GitHub Mobile? Choose another way on GitHub's "
                + "page below (authenticator app, SMS or a recovery code).");
        else if (send) note = "GitHub sends a code to your account's e-mail.";
        else if ("code".equals(kind)) note = "From GitHub's e-mail or your authenticator app.";
        chrome.otpNote.setText(note == null ? "" : note);
        chrome.otpNote.setVisibility(note == null ? View.GONE : View.VISIBLE);
        chrome.sendMail.setVisibility(offersMail ? View.VISIBLE : View.GONE);
        chrome.openMail.setText(mobile ? "GitHub Mobile" : "Open Gmail");
        chrome.openMail.setContentDescription(mobile ? "Open the GitHub Mobile app to approve this sign-in"
                : "Open Gmail: GitHub's e-mail has the code");
        chrome.openMail.setVisibility(email || mobile ? View.VISIBLE : View.GONE);
        chrome.pasteFill.setVisibility(send || mobile ? View.GONE : View.VISIBLE);
        // a pill beside another keeps an 8dp gap; the first one shown none
        gap(chrome.openMail, offersMail);
        gap(chrome.pasteFill, email);
        // v2.1.4: codes from e-mail notifications (the user's own switch in Android's settings)
        if (email && OtpNotifications.offered()) {
            chrome.otpAuto.setText(notifications ? "✓ Codes in GitHub's e-mail go in by themselves (notification access)"
                    : "⚡ Fill codes from e-mail notifications · Turn on ›");
            chrome.otpAuto.setTextColor(getContext().getResources().getColor(notifications ? R.color.ob_text_2
                    : R.color.ob_accent));
            chrome.otpAuto.setVisibility(View.VISIBLE);
        } else {
            chrome.otpAuto.setVisibility(View.GONE);
        }
        chrome.otp.setVisibility(View.VISIBLE);
        if (mobile) {
            status("Open GitHub Mobile and tap the number this page shows" + (offersMail ? ", or tap E-mail me a code."
                    : ". No GitHub Mobile: use another way on GitHub's page."), false);
            return;
        }
        if (login) {
            status("Sign in below · the code goes in by itself. If GitHub e-mails you a code: Open Gmail, copy it, "
                    + "Paste & fill.", false);
            return;
        }
        if (send) {
            status("Tap E-mail me a code (GitHub's \"Send a code via email\"), or choose another way on GitHub's page.",
                    false);
            return;
        }
        if ("two-factor".equals(kind)) {
            status("Copy the code from your authenticator app: it goes in by itself when you come back (or tap Paste & "
                    + "fill).", false);
            return;
        }
        status(notifications && OtpNotifications.offered()
                ? "GitHub's code goes in by itself when its e-mail arrives (or copy it and tap Paste & fill)."
                : "Pull down your notifications and tap Copy code on GitHub's e-mail (or open Gmail and copy it): "
                + "it goes in by itself.", false);
    }

    private void gap(View pill, boolean after) {
        android.view.ViewGroup.LayoutParams lp = pill.getLayoutParams();
        if (lp instanceof android.widget.LinearLayout.LayoutParams) {
            ((android.widget.LinearLayout.LayoutParams) lp).leftMargin = after ? (int) (8 * getContext().getResources()
                    .getDisplayMetrics().density) : 0;
            pill.setLayoutParams(lp);
        }
    }

    /** v2.1.4: presses GitHub's own "Send a code via email" on the page (the user's tap). */
    private void sendMailCode() {
        if (web == null) return;
        final WebView page = web;
        page.evaluateJavascript(OtpAssist.SEND_SCRIPT, answer -> {
            if (closed || page != web) return;
            if ("clicked".equals(OtpAssist.unquote(answer))) {
                codeFloor = Math.max(codeFloor, System.currentTimeMillis());
                waitingCode = null;
                OtpNotifications.arm(getContext(), otpSink, codeFloor);
                status(OtpNotifications.enabled(getContext()) && OtpNotifications.offered()
                        ? "GitHub is e-mailing you a code: it goes in here by itself "
                        + "when the e-mail arrives." : "GitHub is e-mailing you a code. Open Gmail, copy it and come back: "
                        + "it goes in by itself.", false);
                main.postDelayed(this::probeNow, 1200);
            } else {
                status("GitHub's page has no \"Send a code via email\" right now: choose a way on the page.", true);
                probeNow();
            }
        });
    }

    /** v2.1.4: what codes from e-mail notifications do, then Android's own switch for it. */
    private void explainAutoCodes() {
        if (OtpNotifications.enabled(getContext())) {
            status("Codes in GitHub's e-mail go in by themselves while this window waits for one. Turn it off in "
                    + "Android's Settings → Notification access.", false);
            return;
        }
        String text = "Sketchware AI can take GitHub's code from your e-mail app's notification and put it into GitHub's "
                + "field here; GitHub's own Verify is then pressed.\n\n"
                + "• Only while this sign-in window waits for a code.\n"
                + "• Only notifications that mention GitHub and hold a 6- or 8-digit code.\n"
                + "• Nothing is kept or sent anywhere; turn it off in the same place.\n\n"
                + "Next, Android shows Notification access: turn on Sketchware AI. If it says \"Restricted setting\" "
                + "(apps installed from a file), tap App info → ⋮ (top right) → Allow restricted settings, then turn it on.";
        try {
            new com.sketchware.ai.ui.ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                    .setTitle("Codes from e-mail notifications")
                    .setMessage(text)
                    .setPositiveButton("Open settings", (d, w) -> openListenerSettings())
                    .setNeutralButton("App info", (d, w) -> openAppInfo())
                    .setNegativeButton("Not now", null)
                    .show();
        } catch (RuntimeException e) {
            openListenerSettings();
        }
    }

    private void openListenerSettings() {
        Context context = getContext();
        Intent intent;
        if (Build.VERSION.SDK_INT >= 30) {
            intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            OtpNotifications.component(context).flattenToString());
        } else {
            intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (RuntimeException ignored) {
            // the detail page isn't on this phone: the list
        }
        try {
            context.startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            status("This phone has no Notification access page: copy the code from the e-mail and tap Paste & fill.",
                    true);
        }
    }

    private void openAppInfo() {
        Context context = getContext();
        try {
            context.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.getPackageName(), null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            status("App info didn't open: open Settings → Apps → Sketchware AI.", true);
        }
    }

    /** v2.1.4: a code in the e-mail app's notification (the user turned notification access on). */
    private void codeFromNotification(String code, String from) {
        main.post(() -> {
            if (closed || usedCodes.contains(code)) return;
            if ("email".equals(otpKind) || "code".equals(otpKind)) {
                autoFill(code, from);
            } else {
                // the e-mail came before GitHub's page showed its field: filled when the field comes
                waitingCode = code;
                waitingFrom = from;
                waitingAt = SystemClock.elapsedRealtime();
                status("Code from " + from + " ready: it goes in when GitHub's page shows its field.", false);
                probeNow();
            }
        });
    }

    private void fillWaitingCode() {
        String code = waitingCode;
        if (code == null) return;
        waitingCode = null;
        if (SystemClock.elapsedRealtime() - waitingAt > 10 * 60_000L) return;
        autoFill(code, waitingFrom);
    }

    /** v2.1.4: back from Gmail / an authenticator app: a code copied while away goes in by itself. */
    private void codeFromClipboard(long leftAt) {
        android.content.ClipboardManager clip = (android.content.ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clip == null) return;
        CharSequence copied;
        try {
            // review (MEDIUM): the description first (no "pasted from your clipboard" notice, nothing read): only
            // plain text copied while the window was away is read (Android keeps when a clip was set)
            if (!clip.hasPrimaryClip()) return;
            android.content.ClipDescription d = clip.getPrimaryClipDescription();
            // second review (MEDIUM): text of any kind (Gmail's and the browser's Copy give text/html with its text)
            if (d == null || !d.hasMimeType("text/*")) return;
            long at = d.getTimestamp();
            if (at <= 0) return;
            boolean wall = at > 1_000_000_000_000L;
            long since = wall ? leftAt : SystemClock.elapsedRealtime() - (System.currentTimeMillis() - leftAt);
            if (at < since - 2000) return;
            android.content.ClipData data = clip.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) return;
            copied = data.getItemAt(0).getText();
        } catch (RuntimeException e) {
            return;
        }
        if (OtpAssist.isDeviceCode(copied)) return;
        String code = OtpAssist.codeFrom(copied);
        if (code != null) autoFill(code, "the clipboard");
    }

    /** v2.1.4: a code that came by itself (never the same one twice in two minutes). */
    private void autoFill(String code, String from) {
        if (closed || web == null || code == null || usedCodes.contains(code)) return;
        long now = SystemClock.elapsedRealtime();
        if (code.equals(lastAutoCode) && now - lastAutoAt < 120_000L) return;
        lastAutoCode = code;
        lastAutoAt = now;
        fillCode(code, from);
    }

    private void fillCode(String code, String from) {
        final WebView page = web;
        page.evaluateJavascript(OtpAssist.fillScript(code), result -> {
            if (closed || page != web) return;
            String r = result == null ? "" : result.replace("\"", "");
            if ("none".equals(r)) {
                if (from != null) {
                    // the field isn't there yet: kept for the moment it comes
                    waitingCode = code;
                    waitingFrom = from;
                    waitingAt = SystemClock.elapsedRealtime();
                    lastAutoCode = null;
                    status("Code from " + from + " ready: it goes in when GitHub's page shows its field.", false);
                    return;
                }
                status("login".equals(otpKind) ? "No code field on this page: GitHub asks for an e-mailed code after "
                        + "your password. Sign in first." : "This GitHub page has no code field yet: wait for it to load, "
                        + "then tap Paste & fill again.", true);
            } else {
                usedCodes.add(code);
                if (code.equals(waitingCode)) waitingCode = null;
                status("Code " + code + (from == null ? "" : " from " + from) + " filled in: GitHub is checking it…",
                        false);
            }
        });
    }

    /** v2.1.2: Gmail (else the phone's e-mail app); the sign-in window stays as it is. */
    private void openMail() {
        Context context = getContext();
        if ("mobile".equals(otpKind)) {
            // v2.1.5: GitHub Mobile's page: that app answers GitHub's request
            try {
                context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setPackage("com.github.android").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                status("GitHub Mobile isn't on this phone. " + (probe != null && probe.sendLink
                        ? "Tap E-mail me a code." : "Choose another way on GitHub's page below."), true);
            }
            return;
        }
        Intent gmail = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage("com.google.android.gm").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(gmail);
            return;
        } catch (RuntimeException ignored) {
            // no Gmail: any e-mail app
        }
        try {
            context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            status("No e-mail app on this phone: open GitHub's e-mail where you read it and copy the code.", true);
        }
    }

    /** v2.1.2: the code the user copied into GitHub's field on this page (GitHub's own Verify is then pressed). */
    private void pasteAndFill() {
        if (web == null || otpKind == null) return;
        Uri now = currentUri();
        // v2.1.4: a page GitHub shows its code field on (read from the page), or one of its code addresses
        boolean field = probe != null && probe.codeField;
        if (!field && (now == null || OtpAssist.pageKind(now.getScheme(), now.getHost(), now.getPath()) == null)) {
            otpBar(now);
            return;
        }
        CharSequence copied = null;
        try {
            android.content.ClipboardManager clip = (android.content.ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clip != null && clip.hasPrimaryClip() && clip.getPrimaryClip() != null
                    && clip.getPrimaryClip().getItemCount() > 0) {
                copied = clip.getPrimaryClip().getItemAt(0).coerceToText(getContext());
            }
        } catch (RuntimeException ignored) {
            // nothing to read
        }
        // v2.1.5 (owner's 23 Nov report: Paste & fill tapped before copying showed an error): what to do next, and
        // the window's own sign-in code is never taken for GitHub's e-mailed one
        if (OtpAssist.isDeviceCode(copied)) {
            status("That's this window's sign-in code (" + copied.toString().trim() + "), not GitHub's e-mailed code. "
                    + nextStep(), false);
            return;
        }
        String code = OtpAssist.codeFrom(copied);
        if (code == null) {
            status((copied == null || copied.toString().trim().isEmpty() ? "Nothing copied yet. "
                    : "What's copied has no 6- or 8-digit code. ") + nextStep(), false);
            return;
        }
        fillCode(code, null);
    }

    /** v2.1.5: where GitHub's code comes from on this page, and that it then goes in by itself. */
    private String nextStep() {
        if ("two-factor".equals(otpKind)) {
            return "Copy the code from your authenticator app: it goes in by itself when you come back.";
        }
        return "Pull down your notifications and tap Copy code on GitHub's e-mail (or open Gmail and copy it): it "
                + "goes in by itself.";
    }

    /** v2.1.5: told by Android when something is copied (see {@link #clipListener}). */
    private void watchClipboard() {
        ClipboardManager clip = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clip == null || clipListener != null) return;
        clipListener = () -> main.post(this::clipChanged);
        try {
            clip.addPrimaryClipChangedListener(clipListener);
        } catch (RuntimeException e) {
            clipListener = null;
        }
    }

    private void unwatchClipboard() {
        ClipboardManager.OnPrimaryClipChangedListener listener = clipListener;
        clipListener = null;
        if (listener == null) return;
        try {
            ClipboardManager clip = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clip != null) clip.removePrimaryClipChangedListener(listener);
        } catch (RuntimeException ignored) {
            // already gone
        }
    }

    /**
     * v2.1.5: something was copied while a code page waits: the code goes in at once. Android 10+ tells only the app in
     * front, and the clipboard is read only then (in the background the code is read when the window comes back).
     */
    private void clipChanged() {
        if (closed || !(codePage() || "send".equals(otpKind))) return;
        Window w = getWindow();
        boolean front = w != null && w.getDecorView().hasWindowFocus();
        if (!front && Build.VERSION.SDK_INT >= 29) return;
        codeFromClipboard(System.currentTimeMillis() - 5000L);
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
