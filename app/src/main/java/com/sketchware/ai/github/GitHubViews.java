package com.sketchware.ai.github;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sketchware.remod.R;

/**
 * v2.0.0: the GitHub parts of the IDE in the Obsidian glass look of the drawer's "Sketchware AI / Android 17 ready" card
 * (design/README.md): {@code ob_glass} cards with 14dp padding, a 44dp tile on the left (the GitHub mark on a neutral
 * glass tile, or the account picture), a Sora title with one grey line, and pills without the orange outer glow
 * ({@code ob_cta_pill_soft_state}, {@code ob_pill16_state}). Plain views only, so layoutlib draws exactly this code
 * (tools/layoutlib-renders V200Test). Used by the sign-in gate, the in-app sign-in window, the GitHub import sheet and
 * the drawer's account card.
 */
public final class GitHubViews {

    final Context c;

    public GitHubViews(Context context) {
        this.c = context;
    }

    // ------------------------------------------------------------------ surfaces

    /** The drawer header's surface: ob_glass, 14dp padding. */
    public LinearLayout glassCard() {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_glass);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        return card;
    }

    /**
     * The drawer card's row: a 44dp tile (the picture when there is one, else the GitHub mark on a glass tile), a title
     * and one grey line. Children: [0] the tile, [1] the texts (title, subtitle).
     */
    public LinearLayout header(Drawable picture, String title, String subtitle) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView tile = new ImageView(c);
        showPicture(tile, picture);
        row.addView(tile, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c, null, 0, R.style.Ob_Text);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(c.getResources().getFont(R.font.sora_bold));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(t);
        TextView sub = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        sub.setText(subtitle);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setPadding(0, dp(3), 0, 0);
        text.addView(sub);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(12);
        row.addView(text, textParams);
        return row;
    }

    /** The account picture with 14dp corners, or the GitHub mark on a neutral glass tile. */
    public void showPicture(ImageView tile, Drawable picture) {
        if (picture != null) {
            tile.setBackground(null);
            tile.setImageDrawable(picture);
            tile.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.setPadding(0, 0, 0, 0);
            tile.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
                }
            });
            tile.setClipToOutline(true);
            tile.setContentDescription("GitHub account picture");
        } else {
            tile.setClipToOutline(false);
            tile.setBackgroundResource(R.drawable.ob_tile);
            tile.setImageResource(R.drawable.ob_ic_github);
            tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.setPadding(dp(10), dp(10), dp(10), dp(10));
            tile.setContentDescription("GitHub");
        }
    }

    // ------------------------------------------------------------------ buttons

    /** The orange pill without the outer glow, white label; with the GitHub mark when {@code mark}. */
    public TextView pill(String text, boolean mark, boolean enabled, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Button_Cta);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_cta_pill_soft_state);
        t.setPadding(dp(20), 0, dp(20), 0);
        if (mark) {
            Drawable icon = c.getDrawable(R.drawable.ob_ic_github);
            if (icon != null) {
                icon.setBounds(0, 0, dp(18), dp(18));
                t.setCompoundDrawables(icon, null, null, null);
                t.setCompoundDrawablePadding(dp(10));
            }
        }
        enable(t, enabled, click);
        return t;
    }

    /** The design's "glass pill · 16dp": light label on glass. */
    public TextView glassPill(String text, boolean enabled, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Button);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_pill16_state);
        t.setPadding(dp(14), 0, dp(14), 0);
        enable(t, enabled, click);
        return t;
    }

    /** A small pill on a row (36dp): orange without glow, or glass. */
    public TextView smallPill(String text, boolean orange, boolean enabled, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, orange ? R.style.Ob_Text_Button_Cta : R.style.Ob_Text_Button);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(orange ? R.drawable.ob_cta_pill_soft_state : R.drawable.ob_pill16_state);
        t.setPadding(dp(14), 0, dp(14), 0);
        enable(t, enabled, click);
        return t;
    }

    private void enable(TextView t, boolean enabled, View.OnClickListener click) {
        t.setEnabled(enabled);
        t.setAlpha(enabled ? 1f : 0.5f);
        if (enabled && click != null) {
            t.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.sw_press));
            t.setOnClickListener(click);
        }
    }

    // ------------------------------------------------------------------ text

    /** One short line: grey, or the error colour. */
    public TextView line(String text, boolean error) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (error) t.setTextColor(c.getResources().getColor(R.color.ob_error, c.getTheme()));
        return t;
    }

    /** A row on a glass pill: title, one grey line, then the given pills on the right. */
    public LinearLayout row(String title, String detail, View... actions) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.ob_pill16);
        box.setPadding(dp(14), dp(10), dp(10), dp(10));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(c, null, 0, R.style.Ob_Text_Label);
        name.setText(title);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(name);
        if (detail != null) {
            TextView d = line(detail, false);
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            d.setMaxLines(2);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            d.setPadding(0, dp(2), 0, 0);
            text.addView(d);
        }
        box.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        for (View action : actions) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            p.leftMargin = dp(6);
            box.addView(action, p);
        }
        return box;
    }

    // ------------------------------------------------------------------ the sign-in window

    /** The in-app sign-in window's views around the web page (GitHubInAppAuthDialog). */
    public static final class AuthChrome {
        public LinearLayout root;
        public View close;
        public TextView status;
        public TextView code;
        public FrameLayout page;
        public TextView retry;
    }

    /**
     * Top: a close button, "Sign in with GitHub" and a compact status pill with the code; then the page (a 22dp-rounded
     * frame the WebView goes into); a "Try again" pill shown on errors.
     */
    public AuthChrome authChrome() {
        AuthChrome a = new AuthChrome();
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c.getResources().getColor(R.color.ob_bg, c.getTheme()));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundResource(R.drawable.ob_glass);
        top.setPadding(dp(10), dp(10), dp(14), dp(10));
        ImageView close = new ImageView(c);
        close.setImageResource(R.drawable.ob_ic_close);
        close.setBackgroundResource(R.drawable.ob_pill16_state);
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setContentDescription("Close");
        top.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(c, null, 0, R.style.Ob_Text);
        title.setText("Sign in with GitHub");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(c.getResources().getFont(R.font.sora_bold));
        title.setSingleLine(true);
        titles.addView(title);
        TextView status = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setMaxLines(2);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(0, dp(2), 0, 0);
        titles.addView(status);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(12);
        top.addView(titles, tp);
        TextView code = new TextView(c, null, 0, R.style.Ob_Text_Label);
        code.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        code.setLetterSpacing(0.06f);
        code.setGravity(Gravity.CENTER);
        code.setBackgroundResource(R.drawable.ob_pill16);
        code.setPadding(dp(12), 0, dp(12), 0);
        code.setVisibility(View.GONE);
        code.setContentDescription("Sign-in code; tap to copy");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        cp.leftMargin = dp(8);
        top.addView(code, cp);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout page = new FrameLayout(c);
        page.setBackgroundColor(0xFF0D1117);
        page.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(22));
            }
        });
        page.setClipToOutline(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        pp.topMargin = dp(10);
        root.addView(page, pp);

        TextView retry = pill("Try again", false, true, null);
        retry.setVisibility(View.GONE);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rp.topMargin = dp(10);
        root.addView(retry, rp);

        a.root = root;
        a.close = close;
        a.status = status;
        a.code = code;
        a.page = page;
        a.retry = retry;
        return a;
    }

    // ------------------------------------------------------------------ layout parameters

    public LinearLayout.LayoutParams full(int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                heightDp < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(heightDp));
        params.topMargin = dp(topMarginDp);
        return params;
    }

    public int dp(int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
