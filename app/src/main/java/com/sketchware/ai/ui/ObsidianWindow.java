package com.sketchware.ai.ui;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;

import com.sketchware.remod.R;

/**
 * Puts an activity on the Obsidian liquid-glass ground: the #0B0B0D window with its two ambient
 * glows, and matching dark status and navigation bars with light icons.
 */
public final class ObsidianWindow {

    public static final int GROUND = 0xFF0B0B0D;

    private ObsidianWindow() {
    }

    public static void apply(Activity activity) {
        Window window = activity.getWindow();
        window.setBackgroundDrawableResource(R.drawable.ob_window_bg);
        window.setStatusBarColor(GROUND);
        window.setNavigationBarColor(GROUND);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
        // Converted layouts opt out of force-dark on their own roots (android:forceDarkAllowed),
        // so screens that still have classic parts (the drawer, precompiled views) keep it.
    }
}
