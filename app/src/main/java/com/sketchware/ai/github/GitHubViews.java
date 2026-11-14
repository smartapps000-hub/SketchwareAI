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
        t.setTypeface(bold());
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

    /**
     * v2.0.1: the same row with a line icon on the glass tile (backup and restore sheets); the title may take two
     * lines so it is never cut ("Back up" + a long project name).
     */
    public LinearLayout header(int icon, String title, String subtitle) {
        LinearLayout row = header((Drawable) null, title, subtitle);
        ImageView tile = (ImageView) row.getChildAt(0);
        tile.setImageResource(icon);
        tile.setContentDescription(null);
        TextView t = (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(0);
        t.setSingleLine(false);
        t.setMaxLines(3);
        TextView sub = (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(1);
        sub.setSingleLine(false);
        sub.setMaxLines(2);
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

    /**
     * v2.0.1 (owner's 13 Nov directive "glass cards, no plain text lists"): a pressable glass card: a 40dp tile with a
     * line icon, a bold title and one grey line (both may wrap to a second line, never cut), the whole card is the
     * button.
     */
    public LinearLayout actionCard(int icon, String title, String subtitle, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.ob_pill16_state);
        card.setPadding(dp(12), dp(12), dp(14), dp(12));
        card.setMinimumHeight(dp(64));
        ImageView tile = new ImageView(c);
        tile.setBackgroundResource(R.drawable.ob_tile);
        tile.setImageResource(icon);
        tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.setPadding(dp(9), dp(9), dp(9), dp(9));
        card.addView(tile, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c, null, 0, R.style.Ob_Text);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(bold());
        t.setMaxLines(2);
        text.addView(t);
        TextView sub = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        sub.setText(subtitle);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setMaxLines(3);
        sub.setPadding(0, dp(2), 0, 0);
        text.addView(sub);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(12);
        card.addView(text, tp);
        card.setContentDescription(title + ". " + subtitle);
        card.setClickable(true);
        card.setFocusable(true);
        card.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.sw_press));
        if (click != null) card.setOnClickListener(click);
        return card;
    }

    /** v2.0.1: a classic check box on the dark glass (orange when ticked, light label, 48dp touch height). */
    public android.widget.CheckBox checkBox(String text, boolean checked) {
        android.widget.CheckBox box = new android.widget.CheckBox(c);
        box.setText(text);
        box.setTextColor(color(R.color.ob_text));
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        box.setMinHeight(dp(48));
        box.setPadding(dp(6), 0, 0, 0);
        box.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color(R.color.ob_orange), color(R.color.ob_text_2)}));
        box.setChecked(checked);
        return box;
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
        if (error) t.setTextColor(color(R.color.ob_error));
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
        // v2.0.1: two lines, never cut ("Sign in to see your backups" next to its pill on a small phone)
        name.setMaxLines(2);
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
        public TextView title;
        public View reload;
        /** The thin orange loading line under the first row (kept in the layout while hidden: nothing jumps). */
        public android.widget.ProgressBar progress;
        public TextView code;
        public TextView status;
        public FrameLayout page;
        /** "Sign in via browser" (Google, Apple, passkey: GitHub's page in the browser, the code copied). */
        public LinearLayout browser;
    }

    /**
     * v2.0.1 (owner's 13 Nov phone report: "Sign in with G…" and the status were cut): a two-row glass header.
     * Row 1: close, "GitHub Sign-In" (the whole width between the two buttons, never cut) and Reload, with the thin
     * orange loading line below. Row 2: the code pill (tap to copy) and the status on up to two lines. Then the page
     * (a 22dp-rounded frame the WebView goes into) and the "Sign in via browser" card.
     */
    public AuthChrome authChrome() {
        AuthChrome a = new AuthChrome();
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.ob_bg));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundResource(R.drawable.ob_glass);
        top.setPadding(dp(10), dp(10), dp(10), dp(10));

        LinearLayout row1 = new LinearLayout(c);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        ImageView close = iconButton(R.drawable.ob_ic_close, "Close");
        row1.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = new TextView(c, null, 0, R.style.Ob_Text);
        title.setText("GitHub Sign-In");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(bold());
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(8);
        tp.rightMargin = dp(8);
        row1.addView(title, tp);
        ImageView reload = iconButton(R.drawable.ob_ic_refresh, "Reload");
        row1.addView(reload, new LinearLayout.LayoutParams(dp(40), dp(40)));
        top.addView(row1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.ProgressBar progress = new android.widget.ProgressBar(c, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(false);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(color(R.color.ob_orange)));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(color(R.color.ob_divider)));
        progress.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        pb.topMargin = dp(8);
        top.addView(progress, pb);

        // v2.0.1: with a large font (or a very narrow phone) the status goes under the code, full width, never cut
        android.util.DisplayMetrics metrics = c.getResources().getDisplayMetrics();
        boolean stacked = c.getResources().getConfiguration().fontScale >= 1.15f
                || metrics.widthPixels / metrics.density < 330;
        LinearLayout row2 = new LinearLayout(c);
        row2.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row2.setGravity(stacked ? Gravity.START : Gravity.CENTER_VERTICAL);
        TextView code = new TextView(c, null, 0, R.style.Ob_Text_Label);
        code.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        code.setLetterSpacing(0.06f);
        code.setGravity(Gravity.CENTER);
        // one line without setSingleLine: single-line + centre gravity lays the text out on a very wide line and
        // draws it outside the pill (seen in the V201 render)
        code.setMaxLines(1);
        code.setBackgroundResource(R.drawable.ob_pill16_state);
        code.setPadding(dp(12), 0, dp(10), 0);
        Drawable copy = c.getDrawable(R.drawable.ob_ic_copy);
        if (copy != null) {
            copy.setBounds(0, 0, dp(15), dp(15));
            code.setCompoundDrawables(null, null, copy, null);
            code.setCompoundDrawablePadding(dp(8));
        }
        code.setVisibility(View.GONE);
        code.setContentDescription("Sign-in code; tap to copy");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                stacked ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(38));
        if (stacked) {
            code.setMinHeight(dp(38));
            code.setPadding(dp(12), dp(6), dp(10), dp(6));
        }
        cp.rightMargin = dp(10);
        row2.addView(code, cp);
        TextView status = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        // up to five lines beside the code (or under it): every status text fits whole
        status.setMaxLines(5);
        status.setMinHeight(dp(38));
        status.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sp = stacked
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (stacked) sp.topMargin = dp(6);
        row2.addView(status, sp);
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        r2.topMargin = dp(6);
        top.addView(row2, r2);
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

        LinearLayout browser = actionCard(R.drawable.ob_ic_open_browser, "Sign in via browser",
                "Google, Apple or passkey · the code is copied for you", null);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(10);
        root.addView(browser, bp);

        a.root = root;
        a.close = close;
        a.title = title;
        a.reload = reload;
        a.progress = progress;
        a.code = code;
        a.status = status;
        a.page = page;
        a.browser = browser;
        return a;
    }

    /** A 40dp glass pill with one line icon. */
    public ImageView iconButton(int icon, String description) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.ob_pill16_state);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setContentDescription(description);
        b.setClickable(true);
        b.setFocusable(true);
        b.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.sw_press));
        return b;
    }

    // ------------------------------------------------------------------ v2.0.1: the backup and restore sheets

    /** The Back up sheet's content (ProjectBackupSheets wires the clicks). */
    public static final class BackupSheet {
        public android.widget.ScrollView root;
        public android.widget.CheckBox libraries;
        public android.widget.CheckBox blocks;
        public LinearLayout phone;
        public LinearLayout github;
    }

    /** Header, the two classic boxes, "Save on phone" and "Back up to GitHub". */
    public BackupSheet backupSheet(String name, boolean signedIn, boolean libraries, boolean blocks) {
        BackupSheet b = new BackupSheet();
        LinearLayout content = sheet();
        content.addView(header(R.drawable.ob_ic_backup, "Back up " + name, "Choose what goes into the backup"));
        b.libraries = checkBox("Include used Local libraries", libraries);
        content.addView(b.libraries, full(-1, 12));
        b.blocks = checkBox("Include used Custom Blocks", blocks);
        content.addView(b.blocks, full(-1, 0));
        b.phone = actionCard(R.drawable.ob_ic_phone, "Save on phone", ".swb file in the backups folder", null);
        content.addView(b.phone, full(-1, 14));
        b.github = actionCard(R.drawable.ob_ic_github, "Back up to GitHub",
                (signedIn ? "The same .swb" : "Sign in first") + " · private repo SketchwareAI-Projects", null);
        content.addView(b.github, full(-1, 10));
        b.root = (android.widget.ScrollView) content.getParent();
        return b;
    }

    /** The Restore card's sheet content. */
    public static final class RestoreSheet {
        public android.widget.ScrollView root;
        public LinearLayout local;
        public LinearLayout github;
    }

    public RestoreSheet restoreSheet() {
        RestoreSheet r = new RestoreSheet();
        LinearLayout content = sheet();
        content.addView(header(R.drawable.ob_ic_restore, "Restore a project", "Always as a new project: nothing is replaced"));
        r.local = actionCard(R.drawable.ob_ic_phone, "Restore local .swb file", "Pick backups saved on this phone", null);
        content.addView(r.local, full(-1, 16));
        r.github = actionCard(R.drawable.ob_ic_github, "GitHub import project", "Your backups, or a .swb / repository link",
                null);
        content.addView(r.github, full(-1, 10));
        r.root = (android.widget.ScrollView) content.getParent();
        return r;
    }

    /** v2.0.2: the live progress sheet of "Back up to GitHub" and "GitHub import project". */
    public static final class ProgressSheet {
        public LinearLayout root;
        public TextView title;
        public TextView percent;
        public android.widget.ProgressBar bar;
        public TextView status;
    }

    /**
     * v2.0.2 (owner's 14 Nov directive "live 0%-100% progress"): header (line icon, title), the percentage in large Sora,
     * a 6dp orange bar on the divider track, and the step ("25% • Packing libraries & blocks…") on up to three lines.
     */
    public ProgressSheet progressSheet(int icon, String title) {
        ProgressSheet p = new ProgressSheet();
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));
        LinearLayout head = header(icon, title, "Keep the app open; you can hide this");
        content.addView(head);
        p.title = (TextView) ((LinearLayout) head.getChildAt(1)).getChildAt(0);
        TextView percent = new TextView(c, null, 0, R.style.Ob_Text);
        percent.setTypeface(bold());
        percent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        percent.setText("0%");
        content.addView(percent, full(-1, 18));
        android.widget.ProgressBar bar = new android.widget.ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(false);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color(R.color.ob_orange)));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(color(R.color.ob_divider)));
        content.addView(bar, full(6, 10));
        TextView status = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setMaxLines(3);
        content.addView(status, full(-1, 12));
        p.root = content;
        p.percent = percent;
        p.bar = bar;
        p.status = status;
        return p;
    }

    // ------------------------------------------------------------------ v2.0.2: the import guide

    /**
     * v2.0.2 (owner's 14 Nov directive "ⓘ step-by-step visual guide, bundled offline"): three glass cards, each with a
     * number, a title, a line and a small diagram drawn from the app's own views (the share button next to "#663", the
     * link field with Import, a found backup with the libraries question); the part to tap has an orange ring.
     */
    public android.widget.ScrollView importGuide() {
        LinearLayout content = sheet();
        content.addView(header(R.drawable.ob_ic_help, "Import from GitHub", "Three steps, also offline"));

        // step 1: where a link comes from
        LinearLayout card1 = miniCard();
        card1.addView(miniTile("MA"));
        LinearLayout texts = miniTexts("My App", "com.my.app · 1.0");
        card1.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView share = new ImageView(c);
        share.setImageResource(R.drawable.ob_ic_share);
        share.setPadding(dp(6), dp(6), dp(6), dp(6));
        share.setBackground(ring(R.drawable.ob_pill16));
        card1.addView(share, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView id = new TextView(c, null, 0, R.style.Ob_Text_Label);
        id.setText("#663");
        id.setTextColor(color(R.color.ob_accent));
        id.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        id.setPadding(dp(8), 0, 0, 0);
        card1.addView(id);
        LinearLayout diagram1 = new LinearLayout(c);
        diagram1.setOrientation(LinearLayout.VERTICAL);
        diagram1.addView(card1);
        TextView or = miniCaption("or any address like  github.com/user/repo");
        diagram1.addView(or, full(-1, 6));
        content.addView(step(1, "Copy a link", "Tap the share button next to a project's #number (it appears after "
                + "Back up to GitHub), or copy a GitHub repository, folder or .swb address.", diagram1), full(-1, 14));

        // step 2: paste and import
        LinearLayout diagram2 = new LinearLayout(c);
        diagram2.setOrientation(LinearLayout.VERTICAL);
        TextView field = new TextView(c, null, 0, R.style.Ob_Text);
        field.setText("github.com/user/repo");
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackgroundResource(R.drawable.ob_field);
        field.setPadding(dp(10), 0, dp(10), 0);
        diagram2.addView(field, full(34, 0));
        TextView importPill = new TextView(c, null, 0, R.style.Ob_Text_Button_Cta);
        importPill.setText("Import from link");
        importPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        importPill.setGravity(Gravity.CENTER);
        importPill.setBackground(ring(R.drawable.ob_cta_pill_soft));
        diagram2.addView(importPill, full(32, 6));
        content.addView(step(2, "Paste and import", "Open Restore projects > GitHub import project, paste the link "
                + "and tap Import from link.", diagram2), full(-1, 10));

        // step 3: pick and restore
        LinearLayout diagram3 = new LinearLayout(c);
        diagram3.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = miniCard();
        row.addView(miniTexts("App One.swb", "backups"), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView importSmall = new TextView(c, null, 0, R.style.Ob_Text_Button_Cta);
        importSmall.setText("Import");
        importSmall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        importSmall.setGravity(Gravity.CENTER);
        importSmall.setPadding(dp(10), 0, dp(10), 0);
        importSmall.setBackground(ring(R.drawable.ob_cta_pill_soft));
        row.addView(importSmall, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)));
        diagram3.addView(row);
        diagram3.addView(miniCaption("0% → 100% · local libraries: Without / Copy · custom blocks come along"),
                full(-1, 6));
        content.addView(step(3, "Pick and restore", "Pick a backup. It is restored as a new project; local libraries "
                + "only if you choose Copy.", diagram3), full(-1, 10));
        return (android.widget.ScrollView) content.getParent();
    }

    /** One step card: the number in an orange disc, title, line, then the diagram on a darker panel. */
    private LinearLayout step(int number, String title, String text, View diagram) {
        LinearLayout card = glassCard();
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = new TextView(c, null, 0, R.style.Ob_Text_Button_Cta);
        n.setText(String.valueOf(number));
        n.setGravity(Gravity.CENTER);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        android.graphics.drawable.GradientDrawable disc = new android.graphics.drawable.GradientDrawable();
        disc.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        disc.setColor(color(R.color.ob_orange));
        n.setBackground(disc);
        top.addView(n, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView t = new TextView(c, null, 0, R.style.Ob_Text);
        t.setText(title);
        t.setTypeface(bold());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(10);
        top.addView(t, tp);
        card.addView(top);
        TextView line = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        line.setText(text);
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        card.addView(line, full(-1, 8));
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF0F0F12);
        bg.setCornerRadius(dp(14));
        panel.setBackground(bg);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.addView(diagram);
        card.addView(panel, full(-1, 10));
        card.setContentDescription("Step " + number + ": " + title + ". " + text);
        return card;
    }

    private LinearLayout miniCard() {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.ob_pill16);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        return row;
    }

    private TextView miniTile(String letters) {
        TextView tile = new TextView(c, null, 0, R.style.Ob_Text_Label);
        tile.setText(letters);
        tile.setGravity(Gravity.CENTER);
        tile.setTextColor(color(R.color.ob_accent));
        tile.setBackgroundResource(R.drawable.ob_tile);
        tile.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        return tile;
    }

    private LinearLayout miniTexts(String title, String detail) {
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(8), 0, dp(6), 0);
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Label);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setSingleLine(true);
        texts.addView(t);
        TextView d = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        d.setText(detail);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        d.setSingleLine(true);
        texts.addView(d);
        return texts;
    }

    private TextView miniCaption(String text) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        return t;
    }

    /** The diagram's "tap here" ring: an orange 2dp outline drawn over {@code base}. */
    private Drawable ring(int base) {
        android.graphics.drawable.GradientDrawable outline = new android.graphics.drawable.GradientDrawable();
        outline.setColor(0x00000000);
        outline.setCornerRadius(dp(16));
        outline.setStroke(dp(2), color(R.color.ob_orange));
        Drawable inner = c.getDrawable(base);
        return new android.graphics.drawable.LayerDrawable(inner == null ? new Drawable[]{outline}
                : new Drawable[]{inner, outline});
    }

    /** A sheet's scrolling column (20dp sides) inside a ScrollView. */
    private LinearLayout sheet() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(c);
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));
        scroll.addView(content);
        return content;
    }

    // ------------------------------------------------------------------ layout parameters

    public LinearLayout.LayoutParams full(int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                heightDp < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(heightDp));
        params.topMargin = dp(topMarginDp);
        return params;
    }

    /** A colour resource on every API level (the minApi21 flavour has no getColor(int, Theme) below 23). */
    @SuppressWarnings("deprecation")
    public int color(int id) {
        return android.os.Build.VERSION.SDK_INT >= 23 ? c.getResources().getColor(id, c.getTheme()) : c.getResources().getColor(id);
    }

    /** Sora bold (font resources need API 26; bold system type below, minApi21 flavour). */
    public android.graphics.Typeface bold() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                return c.getResources().getFont(R.font.sora_bold);
            } catch (RuntimeException ignored) {
                // a missing font file must not stop the screen
            }
        }
        return android.graphics.Typeface.DEFAULT_BOLD;
    }

    public int dp(int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
