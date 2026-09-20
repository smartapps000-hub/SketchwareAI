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
        adapt(view, true);
    }

    /**
     * @param onDark whether what shows behind this view is dark. Text is only lightened on a dark
     *               ground: under a background we can't read (a card, a 9-patch, a selector) the
     *               original dark text stays, so it never ends up light-on-light either.
     */
    private static void adapt(View view, boolean onDark) {
        if (view == null || TAG_NATIVE.equals(view.getTag())) return;
        onDark = resolveBackground(view, onDark);

        if (view instanceof TextView && onDark) {
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
        } else if (view instanceof ImageView && onDark) {
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
                adapt(group.getChildAt(i), onDark);
            }
        }
    }

    /**
     * Clears flat light-grey/white backgrounds (the glass shows through) and returns whether
     * the view's content sits on a dark ground afterwards.
     */
    private static boolean resolveBackground(View view, boolean onDark) {
        if (view instanceof androidx.cardview.widget.CardView) {
            // Cards that GlassScreens made dark carry dark content; other cards are left alone.
            ColorStateList card = ((androidx.cardview.widget.CardView) view).getCardBackgroundColor();
            return card != null && luminance(card.getDefaultColor()) < 0.3;
        }
        if (view.getClass().getName().contains("CardView")) return false;
        Drawable background = view.getBackground();
        if (background == null) return onDark;
        if (background instanceof android.graphics.drawable.InsetDrawable
                && ((android.graphics.drawable.InsetDrawable) background).getDrawable() != null) {
            background = ((android.graphics.drawable.InsetDrawable) background).getDrawable();
        }
        int color;
        if (background instanceof ColorDrawable) {
            color = ((ColorDrawable) background).getColor();
        } else if (Build.VERSION.SDK_INT >= 24 && background instanceof android.graphics.drawable.GradientDrawable) {
            android.graphics.drawable.GradientDrawable shape = (android.graphics.drawable.GradientDrawable) background;
            ColorStateList fill = shape.getColor();
            int[] gradient = shape.getColors();
            if (fill != null) {
                color = fill.getDefaultColor();
            } else if (gradient != null && gradient.length > 0) {
                color = gradient[0];
            } else {
                return onDark;
            }
        } else if (background instanceof GlassDrawable
                || background instanceof android.graphics.drawable.RippleDrawable
                || background instanceof android.graphics.drawable.StateListDrawable && isPressOverlay(view)) {
            return onDark;
        } else {
            // Unknown artwork (9-patch, card, image): assume it's light, leave text alone.
            return false;
        }
        if (Color.alpha(color) < 40) return onDark;
        if (isNeutral(color) && luminance(color) > 0.75) {
            if (background != view.getBackground()) return false; // light artwork we can't remove
            view.setBackground(null);
            return onDark;
        }
        return luminance(color) < 0.5;
    }

    /** Our own selectors (press overlays, glass pill states) are dark-surface artwork. */
    private static boolean isPressOverlay(View view) {
        return view.getBackground().getCurrent() instanceof GlassDrawable
                || view.getBackground().getCurrent() == null
                || view.getBackground().getCurrent() instanceof ColorDrawable
                && Color.alpha(((ColorDrawable) view.getBackground().getCurrent()).getColor()) < 40;
    }

    /** Greys and near-greys; saturated colours are meaningful and keep their hue. */
    private static boolean isNeutral(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 40;
    }

    private static double luminance(int color) {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0;
    }

    /**
     * Legacy menu icons that are plain black artwork (e.g. the "Tt" font icon) vanish on glass.
     * If every visible pixel of the icon is a dark grey, tint it light; coloured icons are kept.
     */
    public static void lightenIfDarkMono(ImageView image) {
        try {
            Drawable d = image.getDrawable();
            if (!(d instanceof android.graphics.drawable.BitmapDrawable) || image.getImageTintList() != null) return;
            android.graphics.Bitmap bmp = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            if (bmp == null) return;
            int w = bmp.getWidth(), h = bmp.getHeight(), seen = 0;
            int step = Math.max(1, Math.min(w, h) / 24);
            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    int c = bmp.getPixel(x, y);
                    if (Color.alpha(c) < 60) continue;
                    if (!isNeutral(c) || luminance(c) > 0.35) return; // coloured or light: leave it
                    seen++;
                }
            }
            if (seen > 0) image.setImageTintList(ColorStateList.valueOf(0xFFE4E4E7));
        } catch (RuntimeException ignored) {
        }
    }
}
