package com.sketchware.ai.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Makes a classic (light) Sketchware layout readable on an Obsidian glass surface.
 * <p>
 * Dozens of dialogs pass their own content views into the glass dialog, many of them from
 * precompiled code with hard-coded dark text on white. Rather than leave that text invisible
 * on the dark sheet, this walks the content once, before it is shown, and:
 * <ul>
 *     <li>turns dark and grey text light (coloured text such as orange or red keeps its hue),
 *     and dark hints into the muted hint grey;</li>
 *     <li>clears flat white / light-grey backgrounds so the glass shows through;</li>
 *     <li>tints edit-text underlines, check boxes, radio buttons, switches and progress bars
 *     with the amber accent;</li>
 *     <li>lightens dark monochrome icons.</li>
 * </ul>
 * Views tagged {@link #TAG_NATIVE} (layouts already designed for Obsidian) are left alone.
 */
public final class ObsidianAdapter {

    public static final String TAG_NATIVE = "obsidian";

    private static final int TEXT = 0xFFF5F5F7;
    private static final int TEXT_2 = 0xFFA1A1AA;
    private static final int HINT = 0xFF71717A;
    private static final int ACCENT = 0xFFFF7A29;

    private ObsidianAdapter() {
    }

    /**
     * Adapts now and again whenever the content lays out, so rows that lists and adapters add
     * later (after the dialog is shown) are re-coloured too. Idempotent: adapted views are
     * already light and are left as they are.
     */
    public static void adaptLive(View view) {
        adapt(view);
        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> adapt(view));
    }

    public static void adapt(View view) {
        if (view == null || TAG_NATIVE.equals(view.getTag())) return;
        clearLightBackground(view);

        if (view instanceof TextView) {
            TextView text = (TextView) view;
            ColorStateList colors = text.getTextColors();
            if (colors != null) {
                int c = colors.getDefaultColor();
                if (isNeutral(c) && luminance(c) < 0.62) {
                    text.setTextColor(luminance(c) < 0.3 ? TEXT : TEXT_2);
                }
            }
            ColorStateList hints = text.getHintTextColors();
            if (hints != null && luminance(hints.getDefaultColor()) < 0.62) {
                text.setHintTextColor(HINT);
            }
            text.setLinkTextColor(0xFFFF9A4D);
            if (view instanceof EditText) {
                view.setBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
            }
            if (view instanceof CompoundButton) {
                ((CompoundButton) view).setButtonTintList(new ColorStateList(
                        new int[][]{{android.R.attr.state_checked}, {}},
                        new int[]{ACCENT, 0x99FFFFFF}));
            }
        } else if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            ColorStateList tint = image.getImageTintList();
            if (tint != null && isNeutral(tint.getDefaultColor()) && luminance(tint.getDefaultColor()) < 0.5) {
                image.setImageTintList(ColorStateList.valueOf(0xFFE4E4E7));
            }
        } else if (view instanceof ProgressBar) {
            ((ProgressBar) view).setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
            ((ProgressBar) view).setProgressTintList(ColorStateList.valueOf(ACCENT));
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                adapt(group.getChildAt(i));
            }
        }
    }

    private static void clearLightBackground(View view) {
        Drawable background = view.getBackground();
        int color;
        if (background instanceof ColorDrawable) {
            color = ((ColorDrawable) background).getColor();
        } else if (Build.VERSION.SDK_INT >= 24 && background instanceof android.graphics.drawable.GradientDrawable) {
            ColorStateList fill = ((android.graphics.drawable.GradientDrawable) background).getColor();
            if (fill == null) return;
            color = fill.getDefaultColor();
        } else {
            return;
        }
        if (Color.alpha(color) > 40 && isNeutral(color) && luminance(color) > 0.75) {
            view.setBackground(null);
        }
    }

    /** Greys and near-greys; saturated colours are meaningful and keep their hue. */
    private static boolean isNeutral(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 40;
    }

    private static double luminance(int color) {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0;
    }
}
