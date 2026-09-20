package com.sketchware.ai.ui;

import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;

/**
 * Slims a Toolbar so its title gets the width: the back button shrinks from 56dp to 40dp and
 * each action icon from 48dp to 36dp. Menu items arrive late (the activity's and every
 * fragment's menus), so the sizes are re-applied on each layout pass; a pass that finds nothing
 * to change does nothing, so it can't loop.
 */
public final class CompactToolbar {

    private CompactToolbar() {
    }

    public static void apply(Toolbar toolbar, TextView... crispText) {
        final float d = toolbar.getResources().getDisplayMetrics().density;
        final int nav = (int) (40 * d), action = (int) (36 * d), pad = (int) (6 * d);
        for (TextView t : crispText) {
            if (t != null) {
                t.setPaintFlags(t.getPaintFlags() | Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            }
        }
        toolbar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> compact(toolbar, nav, action, pad));
        compact(toolbar, nav, action, pad);
    }

    private static void compact(ViewGroup toolbar, int nav, int action, int pad) {
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View c = toolbar.getChildAt(i);
            if (c instanceof ImageButton) {
                size(c, nav, pad);
            } else if (c instanceof ActionMenuView) {
                ViewGroup menu = (ViewGroup) c;
                for (int j = 0; j < menu.getChildCount(); j++) {
                    size(menu.getChildAt(j), action, pad);
                }
            }
        }
    }

    private static void size(View v, int minWidth, int pad) {
        if (v.getMinimumWidth() != minWidth) v.setMinimumWidth(minWidth);
        if (v instanceof TextView) {
            // ActionMenuItemView measures against its own private mMinWidth (48dp from the style).
            try {
                java.lang.reflect.Field f = v.getClass().getDeclaredField("mMinWidth");
                f.setAccessible(true);
                if (f.getInt(v) != minWidth) {
                    f.setInt(v, minWidth);
                    v.requestLayout();
                }
            } catch (Throwable ignored) {
            }
        }
        if (v.getPaddingLeft() != pad || v.getPaddingRight() != pad) {
            v.setPadding(pad, v.getPaddingTop(), pad, v.getPaddingBottom());
        }
    }
}
