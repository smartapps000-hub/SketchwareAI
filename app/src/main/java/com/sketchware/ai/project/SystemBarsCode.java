package com.sketchware.ai.project;

/**
 * Java source added to generated activities so they never sit under the status bar.
 * <p>
 * Android 15 forces edge-to-edge on apps targeting API 35+: the window draws behind the status
 * and navigation bars and setStatusBarColor() is ignored, so a classic Sketchware layout ends up
 * with its Toolbar under the clock. The generated helper:
 * <ul>
 *     <li>Edge-to-edge OFF (default): pads the content below/above the bars and paints two small
 *     scrim views with the theme's status/navigation bar colours — the layout looks exactly like
 *     it did at targetSdk 28.</li>
 *     <li>Edge-to-edge ON: the window draws under transparent bars (also on Android 11–14) and the
 *     layout's root view gets the bar insets added to its own padding.</li>
 * </ul>
 * Uses only framework APIs present in the bundled android.jar (API 33) and no lambdas, so it
 * compiles with any Java version setting and without AndroidX.
 */
public final class SystemBarsCode {

    public static final String METHOD_NAME = "_sketchwareApplySystemBars";

    private SystemBarsCode() {
    }

    public static String call() {
        return METHOD_NAME + "();";
    }

    public static String method(boolean edgeToEdge, String eol) {
        StringBuilder b = new StringBuilder();
        String n = eol;
        b.append(n);
        b.append("private void ").append(METHOD_NAME).append("() {").append(n);
        if (edgeToEdge) {
            b.append("if (android.os.Build.VERSION.SDK_INT < 30) return;").append(n);
            b.append("getWindow().setDecorFitsSystemWindows(false);").append(n);
            b.append("getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);").append(n);
            b.append("getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);").append(n);
            b.append("final android.view.ViewGroup _content = (android.view.ViewGroup) findViewById(android.R.id.content);").append(n);
            b.append("if (_content == null || _content.getChildCount() == 0) return;").append(n);
            b.append("final android.view.View _root = _content.getChildAt(0);").append(n);
            b.append("final int _l = _root.getPaddingLeft(), _t = _root.getPaddingTop(), _r = _root.getPaddingRight(), _b = _root.getPaddingBottom();").append(n);
            b.append("_root.setOnApplyWindowInsetsListener(new android.view.View.OnApplyWindowInsetsListener() {").append(n);
            b.append("@Override").append(n);
            b.append("public android.view.WindowInsets onApplyWindowInsets(android.view.View _v, android.view.WindowInsets _insets) {").append(n);
            b.append("android.graphics.Insets _bars = _insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());").append(n);
            b.append("android.graphics.Insets _ime = _insets.getInsets(android.view.WindowInsets.Type.ime());").append(n);
            b.append("_v.setPadding(_l + _bars.left, _t + _bars.top, _r + _bars.right, _b + Math.max(_bars.bottom, _ime.bottom));").append(n);
            b.append("return android.view.WindowInsets.CONSUMED;").append(n);
            b.append("}").append(n);
            b.append("});").append(n);
            b.append("_root.requestApplyInsets();").append(n);
        } else {
            // Only Android 15+ forces edge-to-edge; below that the system already keeps content
            // out from under the bars.
            b.append("if (android.os.Build.VERSION.SDK_INT < 35) return;").append(n);
            b.append("final android.view.ViewGroup _decor = (android.view.ViewGroup) getWindow().getDecorView();").append(n);
            b.append("final android.view.View _content = findViewById(android.R.id.content);").append(n);
            b.append("if (_content == null || _decor.findViewWithTag(\"sketchware_status_scrim\") != null) return;").append(n);
            b.append("android.util.TypedValue _tv = new android.util.TypedValue();").append(n);
            b.append("int _statusColor = 0xFF000000;").append(n);
            b.append("int _navColor = 0xFF000000;").append(n);
            b.append("if (getTheme().resolveAttribute(android.R.attr.statusBarColor, _tv, true) && _tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && _tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT && android.graphics.Color.alpha(_tv.data) != 0) _statusColor = _tv.data;").append(n);
            b.append("else if (getTheme().resolveAttribute(android.R.attr.colorPrimaryDark, _tv, true) && _tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && _tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) _statusColor = _tv.data;").append(n);
            b.append("if (getTheme().resolveAttribute(android.R.attr.navigationBarColor, _tv, true) && _tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && _tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT && android.graphics.Color.alpha(_tv.data) != 0) _navColor = _tv.data;").append(n);
            b.append("final android.view.View _statusScrim = new android.view.View(this);").append(n);
            b.append("_statusScrim.setTag(\"sketchware_status_scrim\");").append(n);
            b.append("_statusScrim.setBackgroundColor(_statusColor);").append(n);
            b.append("final android.view.View _navScrim = new android.view.View(this);").append(n);
            b.append("_navScrim.setBackgroundColor(_navColor);").append(n);
            b.append("_decor.addView(_statusScrim, new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, android.view.Gravity.TOP));").append(n);
            b.append("_decor.addView(_navScrim, new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, android.view.Gravity.BOTTOM));").append(n);
            b.append("final boolean _resize = (getWindow().getAttributes().softInputMode & android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) != android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;").append(n);
            b.append("_content.setOnApplyWindowInsetsListener(new android.view.View.OnApplyWindowInsetsListener() {").append(n);
            b.append("@Override").append(n);
            b.append("public android.view.WindowInsets onApplyWindowInsets(android.view.View _v, android.view.WindowInsets _insets) {").append(n);
            b.append("android.graphics.Insets _bars = _insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());").append(n);
            b.append("int _bottom = _bars.bottom;").append(n);
            b.append("if (_resize) _bottom = Math.max(_bottom, _insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);").append(n);
            b.append("_v.setPadding(_bars.left, _bars.top, _bars.right, _bottom);").append(n);
            b.append("android.view.ViewGroup.LayoutParams _sp = _statusScrim.getLayoutParams();").append(n);
            b.append("_sp.height = _bars.top;").append(n);
            b.append("_statusScrim.setLayoutParams(_sp);").append(n);
            b.append("android.view.ViewGroup.LayoutParams _np = _navScrim.getLayoutParams();").append(n);
            b.append("_np.height = _bars.bottom;").append(n);
            b.append("_navScrim.setLayoutParams(_np);").append(n);
            b.append("return android.view.WindowInsets.CONSUMED;").append(n);
            b.append("}").append(n);
            b.append("});").append(n);
            b.append("_content.requestApplyInsets();").append(n);
        }
        b.append("}").append(n);
        return b.toString();
    }
}
