package com.sketchware.ai.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.sketchware.remod.R;

/** Finishing touches for framework AlertDialogs shown as Obsidian glass sheets. */
public final class ObsidianDialogs {

    private ObsidianDialogs() {
    }

    public static void style(AlertDialog dialog) {
        try {
            if (dialog.getWindow() != null) {
                ObsidianAdapter.adaptLive(dialog.getWindow().getDecorView());
            }
            float density = dialog.getContext().getResources().getDisplayMetrics().density;
            styleButton(dialog.getButton(DialogInterface.BUTTON_NEUTRAL), false, density);
            styleButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), false, density);
            styleButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), true, density);
        } catch (RuntimeException ignored) {
            // Styling is cosmetic: never let it break a dialog.
        }
    }

    private static void styleButton(Button button, boolean primary, float density) {
        if (button == null || button.getVisibility() != View.VISIBLE) return;
        button.setBackgroundResource(primary ? R.drawable.ob_safe_cta_state : R.drawable.ob_safe_pill_state);
        button.setTextColor(primary ? 0xFFFFFFFF : 0xFFF5F5F7);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight((int) (44 * density));
        button.setMinimumHeight((int) (44 * density));
        int h = (int) (16 * density);
        button.setPadding(h, 0, h, 0);
        ViewGroup.LayoutParams lp = button.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).leftMargin = (int) (8 * density);
            button.setLayoutParams(lp);
        }
    }
}
