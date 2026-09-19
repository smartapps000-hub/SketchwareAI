package com.sketchware.ai.ui;

import android.app.Activity;
import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/**
 * App theme: System default, Light or Dark.
 * <p>
 * Sketchware's screens (much of them in precompiled code) hard-code light colours, so a
 * hand-made night palette would leave most screens half-converted. Instead the app themes allow
 * Android's automatic dark rendering (android:forceDarkAllowed, Android 10+), and this class
 * decides when it is on:
 * <ul>
 *     <li>System default — dark whenever the phone is in dark mode (Android 10+).</li>
 *     <li>Light / Dark — per-app night mode through UiModeManager.setApplicationNightMode
 *     (Android 12+). On Android 10–11 "Light" still works (dark rendering is switched off);
 *     "Dark" there follows the system setting.</li>
 * </ul>
 * The design canvas and the block editor are never darkened: they preview the user's own app
 * and block colours, which must stay true.
 */
public final class ThemeMode {

    public static final int SYSTEM = 0;
    public static final int LIGHT = 1;
    public static final int DARK = 2;

    public static final String[] LABELS = {"System default", "Light", "Dark"};

    private static final String PREFS = "sketchware_ai_ui";
    private static final String KEY = "theme_mode";

    private ThemeMode() {
    }

    public static int get(Context context) {
        return prefs(context).getInt(KEY, SYSTEM);
    }

    /** Stores and applies the mode. Returns false when this Android version can't honour it fully. */
    public static boolean set(Context context, int mode) {
        prefs(context).edit().putInt(KEY, mode).apply();
        return applyNightMode(context, mode);
    }

    /** Call once from Application#onCreate. */
    public static void install(Application application) {
        if (Build.VERSION.SDK_INT < 29) return;
        applyNightMode(application, get(application));
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                View decor = activity.getWindow().getDecorView();
                if (Build.VERSION.SDK_INT < 31 && get(activity) == LIGHT) {
                    decor.setForceDarkAllowed(false);
                }
                String name = activity.getClass().getName();
                if (name.endsWith(".DesignActivity") || name.endsWith(".LogicEditorActivity")) {
                    keepPreviewsTrueToColour(decor);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
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
        });
    }

    private static boolean applyNightMode(Context context, int mode) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                UiModeManager manager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
                int nightMode = mode == LIGHT ? UiModeManager.MODE_NIGHT_NO
                        : mode == DARK ? UiModeManager.MODE_NIGHT_YES
                        : UiModeManager.MODE_NIGHT_AUTO;
                // setApplicationNightMode is API 31; this build compiles against API 30.
                UiModeManager.class.getMethod("setApplicationNightMode", int.class).invoke(manager, nightMode);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return mode != DARK;
    }

    /** Excludes the design canvas and block editor from automatic dark rendering. */
    private static void keepPreviewsTrueToColour(final View decor) {
        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                exclude(decor);
            }
        });
    }

    private static void exclude(View view) {
        String name = view.getClass().getName();
        if (name.equals("com.besome.sketch.editor.view.ViewPane")
                || name.equals("com.besome.sketch.editor.view.ViewLogicEditor")) {
            if (view.isForceDarkAllowed()) view.setForceDarkAllowed(false);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) exclude(group.getChildAt(i));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
