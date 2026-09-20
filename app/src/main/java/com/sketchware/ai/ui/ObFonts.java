package com.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;

import com.sketchware.remod.R;

/**
 * Loads the bundled Obsidian fonts from code without AndroidX.
 * <p>
 * The app's AndroidX copy is a precompiled, stripped jar: {@code androidx.core.content.res
 * .ResourcesCompat} is there but {@code androidx.core.graphics.TypefaceCompat} is not, so
 * {@code ResourcesCompat.getFont()} dies with NoClassDefFoundError (v1.6.0 crashed opening the
 * Design editor that way). Android 8.0+ reads font resources itself
 * ({@link android.content.res.Resources#getFont}); older phones, or any failure, get the
 * closest system font instead. Never throws.
 */
public final class ObFonts {

    private ObFonts() {
    }

    public static Typeface get(Context context, int fontRes, Typeface fallback) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Typeface typeface = context.getResources().getFont(fontRes);
                if (typeface != null) return typeface;
            } catch (Throwable ignored) {
                // missing or unreadable font: fall back below
            }
        }
        return fallback;
    }

    public static Typeface bold(Context context) {
        return get(context, R.font.manrope_bold, Typeface.DEFAULT_BOLD);
    }

    public static Typeface mono(Context context) {
        return get(context, R.font.jetbrains_mono, Typeface.MONOSPACE);
    }
}
