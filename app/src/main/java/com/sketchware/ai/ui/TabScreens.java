package com.sketchware.ai.ui;

import android.app.Activity;
import android.content.Intent;

import com.sketchware.remod.R;

/**
 * v2.1.0 (owner's 16 Nov directive "tab-scoped back stack"): a bottom-bar tab (Create, GitHub, Play Store, Community,
 * Settings) opens a full-screen screen marked {@link #EXTRA_AS_TAB}. Its own pages are screens on top of it (Back
 * closes them one by one, Android's back stack); on the tab's first page Back asks "Do you want to close [Tab]?"
 * (Cancel / Close), and Close returns to Home, whose bar animates back. A screen opened another way (a project card, a
 * link) closes with Back as before.
 */
public final class TabScreens {

    public static final String EXTRA_AS_TAB = "sketchware_ai_as_tab";
    public static final String EXTRA_TAB_NAME = "sketchware_ai_tab_name";

    private TabScreens() {
    }

    /** {@code intent} as the tab named {@code name}. */
    public static Intent asTab(Intent intent, String name) {
        return intent.putExtra(EXTRA_AS_TAB, true).putExtra(EXTRA_TAB_NAME, name);
    }

    public static boolean isTab(Activity activity) {
        return activity.getIntent() != null && activity.getIntent().getBooleanExtra(EXTRA_AS_TAB, false);
    }

    /** Back on a tab's first page: the question; true when it was asked (the caller does nothing else). */
    public static boolean onBack(Activity activity) {
        if (!isTab(activity)) return false;
        String name = activity.getIntent().getStringExtra(EXTRA_TAB_NAME);
        new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Do you want to close " + (name == null ? "this tab" : name) + "?")
                .setPositiveButton("Close", (d, w) -> activity.finish())
                .setNegativeButton("Cancel", null)
                .show();
        return true;
    }
}
