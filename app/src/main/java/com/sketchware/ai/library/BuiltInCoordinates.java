package com.sketchware.ai.library;

import java.util.HashMap;
import java.util.Map;

/**
 * Maven coordinates of the libraries built into Sketchware AI (mod.jbk.build.BuiltInLibraries, assets/libs): the
 * Library Resolver doesn't download these again, the build adds the built-in copy ({@link LibraryNeeds}).
 * Generated from tools/builtin-libs/coords.py and the BuiltInLibraries constants (v1.8.8); the kotlin-stdlib-jdk7 /
 * -jdk8 / -common jars are empty since Kotlin 1.8 and count as the built-in kotlin-stdlib.
 */
public final class BuiltInCoordinates {

    private static final Map<String, String[]> TABLE = new HashMap<>();

    private BuiltInCoordinates() {
    }

    private static void add(String key, String version, String name) {
        TABLE.put(key, new String[]{version, name});
    }

    /** Version built in for "group:artifact", or null when it isn't built in. */
    public static String version(String key) {
        String[] row = TABLE.get(key);
        return row == null ? null : row[0];
    }

    /** BuiltInLibraries constant name ("GSON", "ANDROIDX_CORE" ...) for "group:artifact", or null. */
    public static String name(String key) {
        String[] row = TABLE.get(key);
        return row == null ? null : row[1];
    }

    /** "group:artifact:version" of a built-in library constant name ("GSON" → com.google.code.gson:gson:2.14.0), or null. */
    public static String coordinateOf(String constant) {
        for (Map.Entry<String, String[]> entry : TABLE.entrySet()) {
            if (entry.getValue()[1].equals(constant) && !entry.getKey().startsWith("org.jetbrains.kotlin:kotlin-stdlib-")) {
                return entry.getKey() + ":" + entry.getValue()[0];
            }
        }
        return null;
    }

    static {
        add("androidx.activity:activity", "1.9.3", "ANDROIDX_ACTIVITY");
        add("androidx.annotation:annotation", "1.9.0", "ANDROIDX_ANNOTATION");
        add("androidx.annotation:annotation-experimental", "1.4.1", "ANDROIDX_ANNOTATION_EXPERIMENTAL");
        add("androidx.appcompat:appcompat", "1.7.1", "ANDROIDX_APPCOMPAT");
        add("androidx.appcompat:appcompat-resources", "1.7.1", "ANDROIDX_APPCOMPAT_RESOURCES");
        add("androidx.arch.core:core-common", "2.2.0", "ANDROIDX_CORE_COMMON");
        add("androidx.arch.core:core-runtime", "2.2.0", "ANDROIDX_CORE_RUNTIME");
        add("androidx.asynclayoutinflater:asynclayoutinflater", "1.1.0", "ANDROIDX_ASYNCLAYOUTINFLATER");
        add("androidx.browser:browser", "1.8.0", "ANDROIDX_BROWSER");
        add("androidx.cardview:cardview", "1.0.0", "ANDROIDX_CARDVIEW");
        add("androidx.collection:collection", "1.4.5", "ANDROIDX_COLLECTION");
        add("androidx.concurrent:concurrent-futures", "1.3.0", "ANDROIDX_CONCURRENT_FUTURES");
        add("androidx.constraintlayout:constraintlayout", "2.2.2", "ANDROIDX_CONSTRAINTLAYOUT");
        add("androidx.constraintlayout:constraintlayout-core", "1.1.2", "ANDROIDX_CONSTRAINTLAYOUT_CORE");
        add("androidx.coordinatorlayout:coordinatorlayout", "1.3.0", "ANDROIDX_COORDINATORLAYOUT");
        add("androidx.core:core", "1.13.1", "ANDROIDX_CORE");
        add("androidx.core:core-ktx", "1.13.1", "ANDROIDX_CORE_KTX");
        add("androidx.cursoradapter:cursoradapter", "1.0.0", "ANDROIDX_CURSORADAPTER");
        add("androidx.customview:customview", "1.2.0", "ANDROIDX_CUSTOMVIEW");
        add("androidx.customview:customview-poolingcontainer", "1.1.0", "ANDROIDX_CUSTOMVIEW_POOLINGCONTAINER");
        add("androidx.documentfile:documentfile", "1.1.0", "ANDROIDX_DOCUMENTFILE");
        add("androidx.drawerlayout:drawerlayout", "1.2.0", "ANDROIDX_DRAWERLAYOUT");
        add("androidx.dynamicanimation:dynamicanimation", "1.1.0", "ANDROIDX_DYNAMIC_ANIMATION");
        add("androidx.emoji2:emoji2", "1.5.0", "ANDROIDX_EMOJI2");
        add("androidx.emoji2:emoji2-views-helper", "1.5.0", "ANDROIDX_EMOJI2_VIEWS_HELPER");
        add("androidx.exifinterface:exifinterface", "1.4.2", "ANDROIDX_EXIFINTERFACE");
        add("androidx.fragment:fragment", "1.8.9", "ANDROIDX_FRAGMENT");
        add("androidx.graphics:graphics-shapes", "1.0.1", "ANDROIDX_GRAPHICS_SHAPES");
        add("androidx.interpolator:interpolator", "1.0.0", "ANDROIDX_INTERPOLATOR");
        add("androidx.legacy:legacy-support-core-ui", "1.0.0", "ANDROIDX_LEGACY_SUPPORT_CORE_UI");
        add("androidx.legacy:legacy-support-core-utils", "1.0.0", "ANDROIDX_LEGACY_SUPPORT_CORE_UTILS");
        add("androidx.legacy:legacy-support-v13", "1.0.0", "ANDROIDX_LEGACY_SUPPORT_V13");
        add("androidx.legacy:legacy-support-v4", "1.0.0", "ANDROIDX_LEGACY_SUPPORT_V4");
        add("androidx.lifecycle:lifecycle-common", "2.8.7", "ANDROIDX_LIFECYCLE_COMMON");
        add("androidx.lifecycle:lifecycle-livedata", "2.8.7", "ANDROIDX_LIFECYCLE_LIVEDATA");
        add("androidx.lifecycle:lifecycle-livedata-core", "2.8.7", "ANDROIDX_LIFECYCLE_LIVEDATA_CORE");
        add("androidx.lifecycle:lifecycle-process", "2.8.7", "ANDROIDX_LIFECYCLE_PROCESS");
        add("androidx.lifecycle:lifecycle-runtime", "2.8.7", "ANDROIDX_LIFECYCLE_RUNTIME");
        add("androidx.lifecycle:lifecycle-viewmodel", "2.8.7", "ANDROIDX_LIFECYCLE_VIEWMODEL");
        add("androidx.lifecycle:lifecycle-viewmodel-savedstate", "2.8.7", "ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE");
        add("androidx.loader:loader", "1.1.0", "ANDROIDX_LOADER");
        add("androidx.localbroadcastmanager:localbroadcastmanager", "1.1.0", "ANDROIDX_LOCALBROADCASTMANAGER");
        add("androidx.media:media", "1.7.1", "ANDROIDX_MEDIA");
        add("androidx.multidex:multidex", "2.0.1", "ANDROIDX_MULTIDEX");
        add("androidx.print:print", "1.1.0", "ANDROIDX_PRINT");
        add("androidx.profileinstaller:profileinstaller", "1.4.1", "ANDROIDX_PROFILEINSTALLER");
        add("androidx.recyclerview:recyclerview", "1.3.2", "ANDROIDX_RECYCLERVIEW");
        add("androidx.resourceinspection:resourceinspection-annotation", "1.0.1", "ANDROIDX_RESOURCEINSPECTION_ANNOTATION");
        add("androidx.savedstate:savedstate", "1.2.1", "ANDROIDX_SAVEDSTATE");
        add("androidx.slidingpanelayout:slidingpanelayout", "1.1.0", "ANDROIDX_SLIDINGPANELAYOUT");
        add("androidx.startup:startup-runtime", "1.2.0", "ANDROIDX_STARTUP_RUNTIME");
        add("androidx.swiperefreshlayout:swiperefreshlayout", "1.2.0-alpha01", "ANDROIDX_SWIPEREFRESHLAYOUT");
        add("androidx.tracing:tracing", "1.2.0", "ANDROIDX_TRACING");
        add("androidx.transition:transition", "1.5.1", "ANDROIDX_TRANSITION");
        add("androidx.vectordrawable:vectordrawable", "1.2.0", "ANDROIDX_VECTORDRAWABLE");
        add("androidx.vectordrawable:vectordrawable-animated", "1.2.0", "ANDROIDX_VECTORDRAWABLE_ANIMATED");
        add("androidx.versionedparcelable:versionedparcelable", "1.2.1", "ANDROIDX_VERSIONEDPARCELABLE");
        add("androidx.viewpager:viewpager", "1.1.0", "ANDROIDX_VIEWPAGER");
        add("androidx.viewpager2:viewpager2", "1.1.0", "ANDROIDX_VIEWPAGER2");
        add("com.airbnb.android:lottie", "3.7.2", "LOTTIE");
        add("com.facebook.android:audience-network-sdk", "5.9.0", "FACEBOOK_ADS_AUDIENCE_NETWORK_SDK");
        add("com.github.bumptech.glide:annotations", "4.16.0", "GLIDE_ANNOTATIONS");
        add("com.github.bumptech.glide:disklrucache", "4.16.0", "GLIDE_DISKLRUCACHE");
        add("com.github.bumptech.glide:gifdecoder", "4.16.0", "GLIDE_GIFDECODER");
        add("com.github.bumptech.glide:glide", "4.16.0", "GLIDE");
        add("com.google.android.gms:play-services-ads", "20.1.0", "PLAY_SERVICES_ADS");
        add("com.google.android.gms:play-services-ads-base", "20.1.0", "PLAY_SERVICES_ADS_BASE");
        add("com.google.android.gms:play-services-ads-identifier", "17.0.0", "PLAY_SERVICES_ADS_IDENTIFIER");
        add("com.google.android.gms:play-services-ads-lite", "20.1.0", "PLAY_SERVICES_ADS_LITE");
        add("com.google.android.gms:play-services-auth", "19.0.0", "PLAY_SERVICES_AUTH");
        add("com.google.android.gms:play-services-auth-api-phone", "17.0.5", "PLAY_SERVICES_AUTH_API_PHONE");
        add("com.google.android.gms:play-services-auth-base", "17.1.2", "PLAY_SERVICES_AUTH_BASE");
        add("com.google.android.gms:play-services-base", "17.6.0", "PLAY_SERVICES_BASE");
        add("com.google.android.gms:play-services-basement", "17.6.0", "PLAY_SERVICES_BASEMENT");
        add("com.google.android.gms:play-services-gass", "20.0.0", "PLAY_SERVICES_GASS");
        add("com.google.android.gms:play-services-gcm", "17.0.0", "PLAY_SERVICES_GCM");
        add("com.google.android.gms:play-services-iid", "17.0.0", "PLAY_SERVICES_IID");
        add("com.google.android.gms:play-services-location", "18.0.0", "PLAY_SERVICES_LOCATION");
        add("com.google.android.gms:play-services-maps", "17.0.1", "PLAY_SERVICES_MAPS");
        add("com.google.android.gms:play-services-measurement", "18.0.3", "PLAY_SERVICES_MEASUREMENT");
        add("com.google.android.gms:play-services-measurement-base", "18.0.3", "PLAY_SERVICES_MEASUREMENT_BASE");
        add("com.google.android.gms:play-services-measurement-impl", "18.0.3", "PLAY_SERVICES_MEASUREMENT_IMPL");
        add("com.google.android.gms:play-services-measurement-sdk", "18.0.3", "PLAY_SERVICES_MEASUREMENT_SDK");
        add("com.google.android.gms:play-services-measurement-sdk-api", "18.0.3", "PLAY_SERVICES_MEASUREMENT_SDK_API");
        add("com.google.android.gms:play-services-places-placereport", "17.0.0", "PLAY_SERVICES_PLACES_PLACEREPORT");
        add("com.google.android.gms:play-services-stats", "17.0.0", "PLAY_SERVICES_STATS");
        add("com.google.android.gms:play-services-tasks", "17.2.1", "PLAY_SERVICES_TASKS");
        add("com.google.android.material:material", "1.13.0", "MATERIAL");
        add("com.google.auto.value:auto-value-annotations", "1.11.1", "GOOGLE_AUTO_VALUE_ANNOTATIONS");
        add("com.google.code.gson:gson", "2.14.0", "GSON");
        add("com.google.firebase:firebase-auth", "19.0.0", "FIREBASE_AUTH");
        add("com.google.firebase:firebase-auth-interop", "18.0.0", "FIREBASE_AUTH_INTEROP");
        add("com.google.firebase:firebase-common", "19.3.1", "FIREBASE_COMMON");
        add("com.google.firebase:firebase-components", "16.0.0", "FIREBASE_COMPONENTS");
        add("com.google.firebase:firebase-database", "19.3.1", "FIREBASE_DATABASE");
        add("com.google.firebase:firebase-database-collection", "17.0.0", "FIREBASE_DATABASE_COLLECTION");
        add("com.google.firebase:firebase-dynamic-links", "19.0.0", "FIREBASE_DYNAMIC_LINKS");
        add("com.google.firebase:firebase-iid", "19.0.0", "FIREBASE_IID");
        add("com.google.firebase:firebase-iid-interop", "17.0.0", "FIREBASE_IID_INTEROP");
        add("com.google.firebase:firebase-measurement-connector", "18.0.0", "FIREBASE_MEASUREMENT_CONNECTOR");
        add("com.google.firebase:firebase-messaging", "19.0.0", "FIREBASE_MESSAGING");
        add("com.google.firebase:firebase-storage", "19.0.0", "FIREBASE_STORAGE");
        add("com.google.guava:listenablefuture", "1.0", "ANDROIDX_LISTENABLEFUTURE");
        add("com.onesignal:OneSignal", "3.14.0", "ONESIGNAL");
        add("com.pierfrancescosoffritti.androidyoutubeplayer:core", "10.0.5", "YOUTUBE_PLAYER");
        add("com.squareup.okhttp3:okhttp", "3.14.9", "OKHTTP");
        add("com.squareup.okio:okio", "1.17.6", "OKIO");
        add("de.hdodenhof:circleimageview", "3.1.0", "CIRCLE_IMAGEVIEW");
        add("org.jetbrains:annotations", "24.1.0", "JETBRAINS_ANNOTATIONS");
        add("org.jetbrains.kotlin:kotlin-stdlib", "1.8.22", "KOTLIN_STDLIB");
        add("org.jetbrains.kotlinx:atomicfu", "0.21.0", "KOTLINX_ATOMICFU");
        add("org.jetbrains.kotlinx:kotlinx-coroutines-android", "1.7.3", "KOTLINX_COROUTINES_ANDROID");
        add("org.jetbrains.kotlinx:kotlinx-coroutines-core", "1.7.3", "KOTLINX_COROUTINES_CORE");
        add("org.jetbrains.kotlin:kotlin-stdlib-jdk7", "1.8.22", "KOTLIN_STDLIB");
        add("org.jetbrains.kotlin:kotlin-stdlib-jdk8", "1.8.22", "KOTLIN_STDLIB");
        add("org.jetbrains.kotlin:kotlin-stdlib-common", "1.8.22", "KOTLIN_STDLIB");
    }
}
