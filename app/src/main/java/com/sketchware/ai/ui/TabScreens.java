package com.sketchware.ai.ui;

import android.app.Activity;
import android.content.Intent;

/**
 * v2.1.0 (owner's 16 Nov directive "tab-scoped back stack"): a bottom-bar tab (Create, GitHub, Play Store, Community,
 * Settings) opens a full-screen screen marked {@link #EXTRA_AS_TAB}. Its own pages are screens on top of it (Back
 * closes them one by one, Android's back stack).
 * <p>
 * v2.1.2 (owner's 21 Nov directive: "remove the 'Do you want to close [tab]?' popups", "keep each tab's scroll position
 * and navigation state alive"): Back on a tab's first page asks nothing: it brings Home to the front and the tab screen
 * stays as it is behind it (stopped, not closed), so the next tap on that tab brings the same screen back
 * ({@link #reopen}: {@code FLAG_ACTIVITY_REORDER_TO_FRONT}), scrolled where it was, with its inner page open. Create is a
 * form and closes on Back as its Cancel does. Android may still close a stopped screen when the phone needs the memory;
 * it then opens fresh.
 */
public final class TabScreens {

    public static final String EXTRA_AS_TAB = "sketchware_ai_as_tab";
    public static final String EXTRA_TAB_NAME = "sketchware_ai_tab_name";
    /** v2.1.2: a tab that closes on Back (Create's form) instead of staying behind Home. */
    public static final String EXTRA_CLOSES = "sketchware_ai_tab_closes";

    private TabScreens() {
    }

    /** {@code intent} as the tab named {@code name}. */
    public static Intent asTab(Intent intent, String name) {
        return intent.putExtra(EXTRA_AS_TAB, true).putExtra(EXTRA_TAB_NAME, name);
    }

    /** v2.1.2: {@code intent} as a tab that, when it is still open behind Home, comes back as it was. */
    public static Intent reopen(Intent intent) {
        return intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    public static boolean isTab(Activity activity) {
        return activity.getIntent() != null && activity.getIntent().getBooleanExtra(EXTRA_AS_TAB, false);
    }

    /**
     * Back on a tab's first page: Home comes to the front, the tab stays behind it (v2.1.2: no question); true when it
     * was a tab (the caller does nothing else).
     */
    public static boolean onBack(Activity activity) {
        if (!isTab(activity)) return false;
        if (activity.getIntent().getBooleanExtra(EXTRA_CLOSES, false)) {
            activity.finish();
            return true;
        }
        try {
            Intent home = new Intent(activity, com.besome.sketch.MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(home);
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (RuntimeException e) {
            activity.finish();
        }
        return true;
    }

    /**
     * v2.1.2: a tab brought back to the front with a new intent keeps its state; only the tab marks are taken over.
     * Second review (LOW): when the screen brought forward is an inner page of the same kind (a link or a notification
     * left one above the tab), it closes and the tab is asked for again, so the real tab comes forward; true then (the
     * caller does nothing else).
     */
    public static boolean onNewIntent(Activity activity, Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_AS_TAB, false)) return false;
        Intent current = activity.getIntent();
        if (current == null) {
            activity.setIntent(intent);
            return false;
        }
        if (!current.getBooleanExtra(EXTRA_AS_TAB, false)) {
            activity.finish();
            try {
                activity.startActivity(new Intent(intent));
            } catch (RuntimeException ignored) {
                // the inner page closed; Home stays
            }
            return true;
        }
        current.putExtra(EXTRA_AS_TAB, true).putExtra(EXTRA_TAB_NAME, intent.getStringExtra(EXTRA_TAB_NAME));
        return false;
    }
}
