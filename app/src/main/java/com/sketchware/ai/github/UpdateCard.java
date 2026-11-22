package com.sketchware.ai.github;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.remod.R;

/**
 * v2.1.2 (owner's 21 Nov directive "premium Obsidian glass update card"): the update notice as a card instead of the
 * plain "A newer Sketchware AI is ready." dialog: a glowing heading, the version step as a pill ("v2.1.1 → v2.1.2"), why
 * it is needed ({@code update_reason} of app-control.json, else the card's own words), up to three lines of what is new
 * ({@code update_notes}) and the orange Update button with a white label. A required update has no Later and the card
 * can't be dismissed; the download sheet that follows has Hide only (Retry after a failure, see {@link AppUpdater}).
 * Plain views on the glass dialog style, so layoutlib draws this code (V212Test).
 * <p>
 * v2.1.3 (owner's 22 Nov phone review, 11 screenshots): "UPDATE AVAILABLE" in capitals in #FF8A65, the version step and a
 * "REQUIRED" tag, then "KEY FEATURES" with a short line and numbered points ({@code update_notes}), "BUGS FIXED · MORE
 * EFFICIENT" with ticks ({@code update_fixes}), and glass pills like the projects' Public / Private pills: Later (glass)
 * and Update (amber glass with a download icon, white label). V213Test draws it.
 */
public final class UpdateCard {

    private UpdateCard() {
    }

    /** "v2.1.2" of "v2.1.2-491ab6c-minApi26" (the version the user knows). */
    static String shortVersion(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String n = name.trim();
        int dash = n.indexOf('-');
        if (dash > 0) n = n.substring(0, dash);
        return n.startsWith("v") ? n : "v" + n;
    }

    /** The card's words when app-control.json gives none. */
    static String reason(AppControl.Config config, boolean forced) {
        if (config != null && !config.updateReason.isEmpty()) return config.updateReason;
        return forced
                ? "This update is required: it fixes problems in the version you have, and that version is no longer "
                + "supported. It installs over it: your projects, settings and GitHub sign-in stay."
                : "A newer version is ready. It installs over this one: your projects, settings and GitHub sign-in stay.";
    }

    /** Builds the card (no dialog): what {@link #show} puts on the glass dialog, and what the renders draw. */
    public static LinearLayout view(Activity activity, AppControl.Config config, boolean forced, Runnable update,
                                    Runnable later) {
        GitHubViews v = new GitHubViews(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(v.dp(22), v.dp(20), v.dp(22), v.dp(18));

        // v2.1.3 (owner's 22 Nov: "UPDATE AVAILABLE in capital letters, bigger, in the colour the failed 15% had"): the
        // heading in ob_error (#FF8A65) with a soft glow of the same colour
        TextView title = new TextView(activity, null, 0, R.style.Ob_Text_Title);
        title.setText("UPDATE AVAILABLE");
        title.setTypeface(v.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setLetterSpacing(0.04f);
        title.setTextColor(v.color(R.color.ob_error));
        title.setShadowLayer(v.dp(8), 0, 0, 0x66FF8A65);
        title.setPadding(0, v.dp(2), 0, v.dp(2));
        title.setContentDescription(forced ? "Update available, required" : "Update available");
        root.addView(title, v.full(-1, 0));

        // the version step, and "Required" beside it when it can't wait
        String from = shortVersion(com.sketchware.remod.BuildConfig.VERSION_NAME);
        String to = config == null || config.latestVersionName == null || config.latestVersionName.isEmpty()
                ? "the newest version" : shortVersion(config.latestVersionName);
        LinearLayout tags = new LinearLayout(activity);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setGravity(Gravity.CENTER_VERTICAL);
        TextView step = tag(activity, v, from + "  →  " + to, R.drawable.ob_pill16, R.color.ob_accent);
        step.setContentDescription("From " + from + " to " + to);
        tags.addView(step, new LinearLayout.LayoutParams(-2, v.dp(30)));
        if (forced) {
            TextView required = tag(activity, v, "REQUIRED", R.drawable.ob_amber_pill, R.color.ob_text);
            required.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, v.dp(30));
            rp.leftMargin = v.dp(8);
            tags.addView(required, rp);
        }
        root.addView(tags, v.full(-1, 12));

        TextView why = new TextView(activity, null, 0, R.style.Ob_Text_Body);
        why.setText(reason(config, forced));
        why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        why.setTextColor(v.color(R.color.ob_text_2));
        why.setLineSpacing(v.dp(2), 1f);
        root.addView(why, v.full(-1, 12));

        // "Key features" with a short line under it and numbered points; then what it fixes
        if (config != null && !config.updateNotes.isEmpty()) {
            section(activity, v, root, "KEY FEATURES");
            int n = 1;
            for (String line : config.updateNotes) root.addView(point(activity, v, String.valueOf(n++), line), v.full(-1, 8));
        }
        if (config != null && !config.updateFixes.isEmpty()) {
            section(activity, v, root, "BUGS FIXED · MORE EFFICIENT");
            for (String line : config.updateFixes) root.addView(point(activity, v, null, line), v.full(-1, 8));
        }

        // glass pills like the projects' Public / Private pills: Later (glass) beside Update (amber glass, icon)
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        if (later != null) {
            TextView not = v.glassPill("Later", true, x -> later.run());
            not.setTextColor(v.color(R.color.ob_text_2));
            not.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            GitHubViews.oneLine(not);
            buttons.addView(not, new LinearLayout.LayoutParams(0, v.dp(48), 1f));
        }
        // built like the Public / Private pill: the icon and the label centred together on the glass
        LinearLayout go = new LinearLayout(activity);
        go.setOrientation(LinearLayout.HORIZONTAL);
        go.setGravity(Gravity.CENTER);
        go.setBackgroundResource(R.drawable.ob_amber_pill_state);
        go.setPadding(v.dp(14), 0, v.dp(14), 0);
        android.widget.ImageView icon = new android.widget.ImageView(activity);
        icon.setImageResource(R.drawable.ob_ic_download);
        icon.setColorFilter(0xFFFFFFFF);
        icon.setContentDescription(null);
        go.addView(icon, new LinearLayout.LayoutParams(v.dp(18), v.dp(18)));
        TextView label = new TextView(activity, null, 0, R.style.Ob_Text_Button);
        label.setText(forced ? "Update now" : "Update");
        label.setTypeface(v.bold());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        label.setTextColor(0xFFFFFFFF);
        label.setIncludeFontPadding(false);
        GitHubViews.oneLine(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = v.dp(8);
        go.addView(label, lp);
        go.setClickable(true);
        go.setFocusable(true);
        go.setOnClickListener(x -> update.run());
        go.setContentDescription((forced ? "Update now" : "Update") + ": download " + to + " in the app and install it");
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, v.dp(48), later != null ? 1.4f : 1f);
        if (later != null) gp.leftMargin = v.dp(10);
        buttons.addView(go, gp);
        root.addView(buttons, v.full(-1, 22));
        if (later == null) {
            TextView note = new TextView(activity, null, 0, R.style.Ob_Text_Caption);
            note.setText("Sketchware AI opens again after Android's installer has updated it.");
            note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            note.setGravity(Gravity.CENTER);
            root.addView(note, v.full(-1, 10));
        }
        return root;
    }

    /** A small one-line glass tag (the version step, "REQUIRED"). */
    private static TextView tag(Activity activity, GitHubViews v, String text, int background, int color) {
        TextView t = new TextView(activity, null, 0, R.style.Ob_Text_Label);
        t.setText(text);
        t.setTypeface(v.bold());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(v.color(color));
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(background);
        t.setPadding(v.dp(14), 0, v.dp(14), 0);
        GitHubViews.oneLine(t);
        return t;
    }

    /** A section heading in capitals with a short amber line under it. */
    private static void section(Activity activity, GitHubViews v, LinearLayout root, String text) {
        TextView head = new TextView(activity, null, 0, R.style.Ob_Text_Label);
        head.setText(text);
        head.setTypeface(v.bold());
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        head.setLetterSpacing(0.1f);
        head.setTextColor(v.color(R.color.ob_text));
        root.addView(head, v.full(-1, 18));
        View line = new View(activity);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{v.color(R.color.ob_amber), 0x00FF7A29});
        d.setCornerRadius(v.dp(1));
        line.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(v.dp(44), v.dp(2));
        lp.topMargin = v.dp(6);
        lp.bottomMargin = v.dp(2);
        root.addView(line, lp);
    }

    /** One point: a small glass circle with its number (or a tick for a fix) and the line beside it. */
    private static LinearLayout point(Activity activity, GitHubViews v, String number, String text) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView badge = new TextView(activity, null, 0, R.style.Ob_Text_Label);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x1AFFFFFF);
        circle.setStroke(Math.max(1, v.dp(1)), 0x66FF8A65);
        badge.setBackground(circle);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        if (number != null) {
            badge.setText(number);
            badge.setTypeface(v.bold());
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            badge.setTextColor(v.color(R.color.ob_accent));
        } else {
            android.graphics.drawable.Drawable tick = activity.getDrawable(R.drawable.ob_ic_check);
            if (tick != null) {
                tick = tick.mutate();
                tick.setTint(v.color(R.color.ob_accent));
                tick.setBounds(0, 0, v.dp(12), v.dp(12));
                badge.setCompoundDrawables(tick, null, null, null);
                badge.setPadding(v.dp(5), 0, 0, 0);
                badge.setGravity(Gravity.CENTER_VERTICAL);
            }
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(v.dp(22), v.dp(22));
        bp.rightMargin = v.dp(10);
        row.addView(badge, bp);
        TextView line = new TextView(activity, null, 0, R.style.Ob_Text_Body);
        line.setText(text);
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        line.setTextColor(v.color(R.color.ob_text));
        line.setLineSpacing(v.dp(1), 1f);
        line.setPadding(0, v.dp(1), 0, 0);
        row.addView(line, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    /** The card on the glass dialog; a required one can't be dismissed. */
    public static void show(Activity activity, AppControl.Config config, boolean forced, Runnable update, Runnable later) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        AlertDialog[] dialog = new AlertDialog[1];
        LinearLayout card = view(activity, config, forced, () -> {
            if (dialog[0] != null) dialog[0].dismiss();
            update.run();
        }, later == null ? null : () -> {
            if (dialog[0] != null) dialog[0].dismiss();
            later.run();
        });
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(card);
        ObAlertBuilder b = new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog);
        b.setView(scroll);
        if (later == null) {
            b.setCancelable(false);
        } else {
            b.setOnCancelListener(d -> later.run());
        }
        dialog[0] = b.create();
        dialog[0].show();
    }
}
