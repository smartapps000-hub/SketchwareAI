package com.sketchware.ai.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;


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
    private static final String VIEW_SELECTOR = "com.besome.sketch.editor.manage.ViewSelectorActivity";

    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new LegacyScreens());
    }

    @Override
    public void onActivityStarted(Activity activity) {
        String name = activity.getClass().getName();
        if (GlassScreens.handles(name)) {
            View content = activity.findViewById(android.R.id.content);
            if (content != null && content.getTag(R.id.ob_glassed) == null) {
                content.setTag(R.id.ob_glassed, Boolean.TRUE);
                try {
                    GlassScreens.apply(activity);
                } catch (Throwable t) {
                    android.util.Log.w("LegacyScreens", "Couldn't restyle " + name, t);
                }
            }
            return;
        }
        if (!ADD_EVENT.equals(name) && !ADD_VIEW.equals(name) && !VIEW_SELECTOR.equals(name)) return;
        try {
            View root = activity.findViewById(R.id.container);
            if (root == null || root.getTag(R.id.container) != null) return;
            root.setTag(R.id.container, Boolean.TRUE);
            ObsidianAdapter.adaptLive(root);
        } catch (Throwable t) {
            android.util.Log.w("LegacyScreens", "Couldn't restyle " + name, t);
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
