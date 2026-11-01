package com.sketchware.ai.project;

import a.a.a.Nx;
import mod.hey.studios.project.ProjectSettings;

/**
 * v1.9.0: the kind of device a project is made for (Project Configuration → Target device) and what that changes.
 * <ul>
 * <li><b>Manifest</b> (only for the chosen target, never by itself): Wear OS gets
 * {@code <uses-feature android:name="android.hardware.type.watch"/>} and the standalone meta-data Wear apps need;
 * Android TV gets {@code android.software.leanback}, {@code android.hardware.touchscreen} and
 * {@code android.hardware.screen.portrait} as not required (the app still installs on phones; a portrait activity
 * would otherwise imply a portrait screen), the {@code LEANBACK_LAUNCHER} category on the launcher activity and the
 * app icon as its TV banner. Phone, foldable and tablet need nothing: apps are resizable by default since target 24.</li>
 * <li><b>Design canvas</b>: the size the design editor previews the screen at ({@link Viewport}).</li>
 * </ul>
 * A feature or attribute the manifest has already (injected in the manifest editor) is not added again.
 */
public final class DeviceTargets {

    public static final String SETTING = "target_device";

    public static final String PHONE = "phone";
    public static final String FOLDABLE = "foldable";
    public static final String TABLET = "tablet";
    public static final String WEAR = "wear";
    public static final String TV = "tv";

    public static final String[] ALL = {PHONE, FOLDABLE, TABLET, WEAR, TV};

    public static final String WATCH_FEATURE = "android.hardware.type.watch";
    public static final String LEANBACK_FEATURE = "android.software.leanback";
    public static final String TOUCHSCREEN_FEATURE = "android.hardware.touchscreen";
    public static final String PORTRAIT_FEATURE = "android.hardware.screen.portrait";
    public static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    public static final String WEAR_STANDALONE = "com.google.android.wearable.standalone";

    /** A design-canvas size: width and height in dp (0 = the phone's own screen), round for Wear OS. */
    public static final class Viewport {
        public final String target;
        public final int widthDp;
        public final int heightDp;
        public final boolean round;

        Viewport(String target, int widthDp, int heightDp, boolean round) {
            this.target = target;
            this.widthDp = widthDp;
            this.heightDp = heightDp;
            this.round = round;
        }

        /** Material 3 window size class of the width: Compact below 600dp, Medium below 840dp, else Expanded. */
        public String sizeClass() {
            if (widthDp <= 0 || widthDp < 600) return "Compact";
            return widthDp < 840 ? "Medium" : "Expanded";
        }

        public String describe() {
            if (widthDp <= 0) return label(target) + " · this phone's screen · Compact";
            return label(target) + " · " + widthDp + "×" + heightDp + " dp" + (round ? " round" : "") + " · " + sizeClass();
        }
    }

    private DeviceTargets() {
    }

    public static String label(String target) {
        switch (target) {
            case FOLDABLE:
                return "Foldable";
            case TABLET:
                return "Tablet";
            case WEAR:
                return "Wear OS";
            case TV:
                return "Android TV";
            default:
                return "Phone";
        }
    }

    /** One line on what choosing it changes in the built app. */
    public static String effect(String target) {
        switch (target) {
            case WEAR:
                return "Manifest: android.hardware.type.watch + standalone Wear app";
            case TV:
                return "Manifest: leanback (not required), TV launcher, icon as banner";
            case FOLDABLE:
            case TABLET:
                return "Canvas size only: apps are resizable by default";
            default:
                return "Canvas: this phone's screen";
        }
    }

    /**
     * The design canvas sizes: foldable unfolded 700×840 dp (Medium), tablet landscape 1280×800 dp (sw720dp,
     * Expanded), Wear OS round 227×227 dp (454×454 px at 2x), Android TV 960×540 dp (1920×1080 px at 2x).
     */
    public static Viewport viewport(String target) {
        switch (target) {
            case FOLDABLE:
                return new Viewport(FOLDABLE, 700, 840, false);
            case TABLET:
                return new Viewport(TABLET, 1280, 800, false);
            case WEAR:
                return new Viewport(WEAR, 227, 227, true);
            case TV:
                return new Viewport(TV, 960, 540, false);
            default:
                return new Viewport(PHONE, 0, 0, false);
        }
    }

    /** The project's target (phone when nothing or an unknown value is stored). */
    public static String of(ProjectSettings settings) {
        String value = settings == null ? null : settings.getValue(SETTING, PHONE);
        for (String target : ALL) if (target.equals(value)) return target;
        return PHONE;
    }

    // ------------------------------------------------------------------------------------------------ manifest

    /** The uses-feature tags of the target, in the manifest root (before the application tag). */
    public static void addFeatures(Nx manifest, ProjectSettings settings) {
        String target = of(settings);
        if (WEAR.equals(target)) {
            addFeature(manifest, WATCH_FEATURE, null);
        } else if (TV.equals(target)) {
            addFeature(manifest, LEANBACK_FEATURE, "false");
            addFeature(manifest, TOUCHSCREEN_FEATURE, "false");
            // Sketchware activities default to portrait, which implies a required portrait screen: TVs are landscape
            addFeature(manifest, PORTRAIT_FEATURE, "false");
        }
    }

    /** Application attributes and meta-data of the target. */
    public static void application(Nx application, ProjectSettings settings) {
        String target = of(settings);
        if (WEAR.equals(target) && !hasChild(application, "meta-data", WEAR_STANDALONE)) {
            Nx standalone = new Nx("meta-data");
            standalone.a("android", "name", WEAR_STANDALONE);
            standalone.a("android", "value", "true");
            application.a(standalone);
        } else if (TV.equals(target) && !application.hasAttributeContaining("banner")) {
            application.a("android", "banner", "@drawable/app_icon");
        }
    }

    /** The launcher activity's intent filter: Android TV's launcher lists apps with LEANBACK_LAUNCHER. */
    public static void launcher(Nx intentFilter, ProjectSettings settings) {
        if (TV.equals(of(settings)) && !hasChild(intentFilter, "category", LEANBACK_LAUNCHER)) {
            Nx category = new Nx("category");
            category.a("android", "name", LEANBACK_LAUNCHER);
            intentFilter.a(category);
        }
    }

    private static void addFeature(Nx manifest, String name, String required) {
        if (hasChild(manifest, "uses-feature", name)) return;
        Nx feature = new Nx("uses-feature");
        feature.a("android", "name", name);
        if (required != null) feature.a("android", "required", required);
        manifest.a(feature);
    }

    /** Whether {@code parent} has a {@code <tag android:name="name">} child already. */
    static boolean hasChild(Nx parent, String tag, String name) {
        if (parent == null || parent.f == null) return false;
        for (Nx child : parent.f) {
            if (child == null || !tag.equals(child.a)) continue;
            for (String[] attribute : child.attributes()) {
                if (attribute.length >= 3 && "name".equals(attribute[1]) && name.equals(attribute[2])) return true;
            }
        }
        return false;
    }
}
