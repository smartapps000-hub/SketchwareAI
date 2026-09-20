package com.sketchware.ai.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sketchware.remod.R;

/**
 * Obsidian styling for screens that only exist as precompiled classes (no source to edit):
 * their layouts are restyled in XML, and this finishes the job once they are on screen.
 * <ul>
 *     <li>Add Event: the event list becomes a grid of tiles, up to 6 per row (fewer when the
 *     screen is narrow, so target ids and event names stay readable), and dark legacy text is
 *     lightened;</li>
 *     <li>Add View (new screen): dark legacy text is lightened on the glass panel.</li>
 * </ul>
 * Everything here is best-effort: a failure leaves the screen as the XML made it.
 */
public final class LegacyScreens implements Application.ActivityLifecycleCallbacks {

    private static final String ADD_EVENT = "com.besome.sketch.editor.event.AddEventActivity";
    private static final String ADD_VIEW = "com.besome.sketch.editor.manage.view.AddViewActivity";
    private static final int MAX_COLUMNS = 6;
    private static final int MIN_COLUMNS = 3;
    /** Narrowest tile that still shows an id like "button12" and "onTextChanged" at 9.5sp. */
    private static final int MIN_TILE_DP = 76;

    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new LegacyScreens());
    }

    @Override
    public void onActivityStarted(Activity activity) {
        String name = activity.getClass().getName();
        if (!ADD_EVENT.equals(name) && !ADD_VIEW.equals(name)) return;
        try {
            View root = activity.findViewById(R.id.container);
            if (root == null || root.getTag(R.id.container) != null) return;
            root.setTag(R.id.container, Boolean.TRUE);
            ObsidianAdapter.adaptLive(root);
            if (ADD_EVENT.equals(name)) {
                RecyclerView list = activity.findViewById(R.id.event_list);
                if (list != null) list.post(() -> useGrid(activity, list));
            }
        } catch (Throwable t) {
            android.util.Log.w("LegacyScreens", "Couldn't restyle " + name, t);
        }
    }

    private static void useGrid(Activity activity, RecyclerView list) {
        try {
            float density = activity.getResources().getDisplayMetrics().density;
            int width = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
            if (width <= 0) width = activity.getResources().getDisplayMetrics().widthPixels - (int) (40 * density);
            int columns = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, (int) (width / (MIN_TILE_DP * density))));
            list.setLayoutManager(new GridLayoutManager(activity, columns));
        } catch (Throwable t) {
            android.util.Log.w("LegacyScreens", "Event grid unavailable, keeping the list", t);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
