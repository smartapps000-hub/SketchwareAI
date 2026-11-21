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
        root.setPadding(v.dp(22), v.dp(18), v.dp(22), v.dp(16));

        // the heading with an orange glow behind it
        TextView title = new TextView(activity, null, 0, R.style.Ob_Text_Title);
        title.setText(forced ? "Update required" : "Update available");
        title.setTypeface(v.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(v.color(R.color.ob_text));
        title.setShadowLayer(v.dp(14), 0, 0, 0xCCFF6D24);
        title.setPadding(0, v.dp(4), 0, v.dp(4));
        root.addView(title, v.full(-1, 0));

        // the version step
        String from = shortVersion(com.sketchware.remod.BuildConfig.VERSION_NAME);
        String to = config == null || config.latestVersionName == null || config.latestVersionName.isEmpty()
                ? "the newest version" : shortVersion(config.latestVersionName);
        TextView step = new TextView(activity, null, 0, R.style.Ob_Text_Label);
        step.setText(from + "  →  " + to);
        step.setTypeface(v.bold());
        step.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        step.setTextColor(v.color(R.color.ob_accent));
        step.setGravity(Gravity.CENTER);
        step.setBackgroundResource(R.drawable.ob_pill16);
        step.setPadding(v.dp(14), v.dp(6), v.dp(14), v.dp(6));
        GitHubViews.oneLine(step);
        step.setContentDescription("From " + from + " to " + to);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = v.dp(10);
        root.addView(step, sp);

        TextView why = new TextView(activity, null, 0, R.style.Ob_Text_Body);
        why.setText(reason(config, forced));
        why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        why.setTextColor(v.color(R.color.ob_text_2));
        why.setLineSpacing(v.dp(2), 1f);
        root.addView(why, v.full(-1, 14));

        // what is new: up to three lines with an orange dot
        if (config != null && !config.updateNotes.isEmpty()) {
            TextView head = new TextView(activity, null, 0, R.style.Ob_Text_Label);
            head.setText("What's new");
            head.setTypeface(v.bold());
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            head.setTextColor(v.color(R.color.ob_text));
            root.addView(head, v.full(-1, 16));
            for (String line : config.updateNotes) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                View dot = new View(activity);
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(v.color(R.color.ob_amber));
                dot.setBackground(d);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(v.dp(7), v.dp(7));
                dp.topMargin = v.dp(7);
                dp.rightMargin = v.dp(10);
                row.addView(dot, dp);
                TextView text = new TextView(activity, null, 0, R.style.Ob_Text_Body);
                text.setText(line);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                text.setTextColor(v.color(R.color.ob_text));
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
                root.addView(row, v.full(-1, 8));
            }
        }

        // Update (orange, white label); Later only when it may wait
        TextView go = v.pill(forced ? "Update now" : "Update", false, true, x -> update.run());
        go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        go.setContentDescription((forced ? "Update now" : "Update") + ": download " + to + " in the app and install it");
        root.addView(go, v.full(50, 20));
        if (later != null) {
            TextView not = v.glassPill("Later", true, x -> later.run());
            root.addView(not, v.full(44, 8));
        } else {
            TextView note = new TextView(activity, null, 0, R.style.Ob_Text_Caption);
            note.setText("Sketchware AI opens again after Android's installer has updated it.");
            note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            note.setGravity(Gravity.CENTER);
            root.addView(note, v.full(-1, 10));
        }
        return root;
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
