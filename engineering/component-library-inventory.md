# Component and library modernization inventory

Audited: commit `c82aba4` (v1.8.1). Date: 2026-10-04. Nothing here was run on a device.

## Summary

- The IDE bundles **113 libraries** for user apps (`libs.zip`: 113 folders; `dexs.zip`: 112 dex files). For 82 of them the version is confirmed by a marker inside the library; for the rest only the folder name says it.
- **12** are at the newest stable version listed upstream; **95** differ from it (older, or an alpha/beta/milestone build); for **6** no upstream version could be established.
- **No bundled library contains a native `.so`**, so none of them raises a 16 KB page-size question.
- Two bundled libraries are tied to services with a published end: **Google Mobile Ads SDK 20.1.0** (sunset since 30 June 2024) and **Firebase Dynamic Links** (service shut down 25 August 2025).
- In generated app code, **Bluetooth, system bars / edge-to-edge, back handling, speech and the camera capture path are handled** for new targets. **Notification permission, media/storage permissions and foreground-service types are not.**
- Separate from libraries: the **Export** signing path produces v1-only APKs (android15-audit.md, B9). That is the first item in "modernize next".

## How this was checked

Labels: VERIFIED (source), VERIFIED (experiment here), DOCUMENTED (URL), NOT TESTED, UNKNOWN.

| What | How |
|---|---|
| List of libraries | constants in `app/src/main/java/mod/jbk/build/BuiltInLibraries.java:20-131`; `unzip -l app/src/main/assets/libs/libs.zip` (3255 files, 113 top-level folders) and `dexs.zip` (112 files). The 113 names in source equal the 113 folders. Only `legacy-support-v4-1.0.0` has no dex. |
| Bundled version | `META-INF/<group>_<artifact>.version` inside each `classes.jar` (AndroidX, Material), or `<artifact>.properties` (`version=…`, Play services and Firebase). Where neither exists, the folder name. |
| Upstream version | `maven-metadata.xml` of the artifact, read on 2026-10-04 with `curl`: **GM** = `https://dl.google.com/android/maven2/<group path>/<artifact>/maven-metadata.xml` (Google Maven), **MC** = `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml` (Maven Central). "Latest stable" = the last listed version without alpha / beta / rc / dev / milestone in its name. |
| Native code | `unzip -l libs.zip | grep -E "\.so$|jni/"` → no match |
| Generated code | `a/a/a/Ix.java` (manifest), `a/a/a/Jx.java` (activity), `a/a/a/Lx.java` (component code), `a/a/a/yq.java` (which component adds which permission), `mod/hasrat/control/logic/PermissionManager.java`, `mod/agus/jcoderz/editor/manifest/EditorManifest.java`, `mod/agus/jcoderz/handle/component/ConstVarManifest.java`, `assets/debug/*.java` |
| Scratch (does not survive a new workspace) | `/tmp/claude-0/-home-claude/45d69778-7163-56e9-aa30-4ad415926e33/scratchpad/audit182/upstream/` (`inventory2.json`, `versions.tsv`), `logs/libs_meta.json` |

Other URLs:

- [admob-dep] https://developers.google.com/admob/android/deprecation
- [fdl] https://firebase.google.com/support/dynamic-links-faq
- [notif] https://developer.android.com/develop/ui/views/notifications/notification-permission
- [13-target] https://developer.android.com/about/versions/13/behavior-changes-13
- [14-target] https://developer.android.com/about/versions/14/behavior-changes-14
- [15-target] https://developer.android.com/about/versions/15/behavior-changes-15
- [bt] https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- [bal] https://developer.android.com/guide/components/activities/background-starts

Limits of table 1: a newer upstream version is not proof that the bundled one fails on Android 15. What each newer version needs (minSdk, compileSdk, Kotlin, other libraries) was not checked: UNKNOWN. Only the notes with a URL or a file reference are verified statements.

## 1. Built-in libraries

| Library (folder in libs.zip) | Bundled version | Version evidence | Maven coordinate | Latest stable upstream, 2026-10-04 | Note |
|---|---|---|---|---|---|
| activity-1.3.0 | 1.3.0 | marker inside classes.jar | androidx.activity:activity | 1.13.0 (GM) |  |
| android-youtube-player-10.0.5 | 10.0.5 | folder name only | com.pierfrancescosoffritti.androidyoutubeplayer:core (assumed from the name) | 13.0.0 (MC) |  |
| annotation-1.3.0 | 1.3.0 | folder name only | androidx.annotation:annotation (assumed from the name) | 1.11.0 (GM) |  |
| annotation-experimental-1.2.0 | 1.2.0 | marker inside classes.jar | androidx.annotation:annotation-experimental | 1.6.0 (GM) |  |
| annotations-13.0 | 13.0 | folder name only | org.jetbrains:annotations (assumed from the name) | 26.1.0 (MC) |  |
| annotations-4.11.0 | 4.11.0 | folder name only | com.github.bumptech.glide:annotations (assumed from the name) | 5.0.9 (MC) |  |
| appcompat-1.4.0 | 1.4.0 | marker inside classes.jar | androidx.appcompat:appcompat | 1.8.0 (GM) | Base of every AppCompat project. Behaviour under Android 15 edge-to-edge: UNKNOWN (release notes not checked). |
| appcompat-resources-1.4.0 | 1.4.0 | marker inside classes.jar | androidx.appcompat:appcompat-resources | 1.8.0 (GM) |  |
| asynclayoutinflater-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.asynclayoutinflater:asynclayoutinflater | 1.1.0 (GM) |  |
| audience-network-sdk-5.9.0 | 5.9.0 | folder name only | com.facebook.android:audience-network-sdk (assumed from the name) | 6.22.0 (MC) | Facebook Audience Network component. |
| auto-value-annotations-1.6.5 | 1.6.5 | folder name only | com.google.auto.value:auto-value-annotations (assumed from the name) | 1.11.1 (MC) |  |
| browser-1.3.0 | 1.3.0 | marker inside classes.jar | androidx.browser:browser | 1.10.0 (GM) |  |
| cardview-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.cardview:cardview | 1.0.0 (GM) |  |
| circle-imageview-v3.1.0 | 3.1.0 | folder name only | de.hdodenhof:circleimageview (assumed from the name) | 3.1.0 (MC) |  |
| code-view | (none in name) | folder name only | UNKNOWN | UNKNOWN | No version in the folder name; origin not identified. |
| collection-1.2.0 | 1.2.0 | folder name only | androidx.collection:collection (assumed from the name) | 1.6.0 (GM) |  |
| concurrent-futures-1.1.0 | 1.1.0 | folder name only | androidx.concurrent:concurrent-futures (assumed from the name) | 1.3.0 (GM) |  |
| constraintlayout-2.1.2 | 2.1.2 | folder name only | androidx.constraintlayout:constraintlayout (assumed from the name) | 2.2.2 (GM) |  |
| constraintlayout-core-1.0.2 | 1.0.2 | folder name only | androidx.constraintlayout:constraintlayout-core (assumed from the name) | 1.1.2 (GM) |  |
| coordinatorlayout-1.1.0 | 1.1.0 | marker inside classes.jar | androidx.coordinatorlayout:coordinatorlayout | 1.3.0 (GM) |  |
| core-1.7.0 | 1.7.0 | marker inside classes.jar | androidx.core:core | 1.19.1 (GM) | Provides `ContextCompat`, `ActivityCompat`, `FileProvider` used by generated code. |
| core-common-2.1.0 | 2.1.0 | folder name only | androidx.arch.core:core-common (assumed from the name) | 2.2.0 (GM) |  |
| core-ktx-1.5.0-beta01 | 1.5.0-beta01 | marker inside classes.jar | androidx.core:core-ktx | 1.19.1 (GM) | A beta build is bundled. |
| core-runtime-2.1.0 | 2.1.0 | marker inside classes.jar | androidx.arch.core:core-runtime | 2.2.0 (GM) |  |
| cursoradapter-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.cursoradapter:cursoradapter | 1.0.0 (GM) |  |
| customview-1.1.0 | 1.1.0 | marker inside classes.jar | androidx.customview:customview | 1.2.0 (GM) |  |
| disklrucache-4.11.0 | 4.11.0 | folder name only | com.github.bumptech.glide:disklrucache (assumed from the name) | 5.0.9 (MC) |  |
| documentfile-1.0.1 | 1.0.1 | marker inside classes.jar | androidx.documentfile:documentfile | 1.1.0 (GM) |  |
| drawerlayout-1.1.1 | 1.1.1 | marker inside classes.jar | androidx.drawerlayout:drawerlayout | 1.2.0 (GM) |  |
| dynamic-animation-1.1.0-alpha03 | 1.1.0-alpha03 | marker inside classes.jar | androidx.dynamicanimation:dynamicanimation | 1.1.0 (GM) | An alpha build is bundled. |
| emoji2-1.0.1 | 1.0.1 | marker inside classes.jar | androidx.emoji2:emoji2 | 1.7.0 (GM) |  |
| emoji2-views-helper-1.0.1 | 1.0.1 | marker inside classes.jar | androidx.emoji2:emoji2-views-helper | 1.7.0 (GM) |  |
| exifinterface-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.exifinterface:exifinterface | 1.4.2 (GM) |  |
| firebase-auth-19.0.0 | 19.0.0 | marker inside classes.jar | com.google.firebase:firebase-auth | 24.2.0 (GM) | Firebase Auth component. |
| firebase-auth-interop-18.0.0 | 18.0.0 | marker inside classes.jar | com.google.firebase:firebase-auth-interop | 20.0.0 (GM) |  |
| firebase-common-19.3.1 | 19.3.1 | marker inside classes.jar | com.google.firebase:firebase-common | 23.0.0 (GM) |  |
| firebase-components-16.0.0 | 16.0.0 | marker inside classes.jar | com.google.firebase:firebase-components | 20.0.0 (GM) |  |
| firebase-database-19.3.1 | 19.3.1 | marker inside classes.jar | com.google.firebase:firebase-database | 22.0.2 (GM) | Firebase DB component. |
| firebase-database-collection-17.0.0 | 17.0.0 | marker inside classes.jar | com.google.firebase:firebase-database-collection | 18.0.1 (GM) |  |
| firebase-dynamic-links-19.0.0 | 19.0.0 | marker inside classes.jar | com.google.firebase:firebase-dynamic-links | 22.1.0 (GM) | The Firebase Dynamic Links service was shut down on 25 August 2025; links return HTTP 404 [fdl]. |
| firebase-iid-19.0.0 | 19.0.0 | marker inside classes.jar | com.google.firebase:firebase-iid | 21.1.0 (GM) | Used with firebase-messaging 19. |
| firebase-iid-interop-17.0.0 | 17.0.0 | marker inside classes.jar | com.google.firebase:firebase-iid-interop | 17.1.0 (GM) |  |
| firebase-measurement-connector-18.0.0 | 18.0.0 | marker inside classes.jar | com.google.firebase:firebase-measurement-connector | 20.0.1 (GM) |  |
| firebase-messaging-19.0.0 | 19.0.0 | marker inside classes.jar | com.google.firebase:firebase-messaging | 25.1.3 (GM) | Used by the FCM component. The generated app does not request `POST_NOTIFICATIONS` (table 2). |
| firebase-storage-19.0.0 | 19.0.0 | marker inside classes.jar | com.google.firebase:firebase-storage | 22.0.2 (GM) | Firebase Storage component. |
| fragment-1.3.6 | 1.3.6 | marker inside classes.jar | androidx.fragment:fragment | 1.9.1 (GM) |  |
| gifdecoder-4.11.0 | 4.11.0 | folder name only | com.github.bumptech.glide:gifdecoder (assumed from the name) | 5.0.9 (MC) |  |
| glide-4.11.0 | 4.11.0 | folder name only | com.github.bumptech.glide:glide (assumed from the name) | 5.0.9 (MC) | Added when a project uses Glide (`Dp.java:1053-1055`). |
| gson-2.8.7 | 2.8.7 | folder name only | com.google.code.gson:gson (assumed from the name) | 2.14.0 (MC) | Added with Firebase DB and RequestNetwork (`yq.java:487, 509`). |
| http-legacy-android-28 | (platform library, API 28) | folder name only | UNKNOWN | UNKNOWN | Apache HTTP classes for compiling; the manifest gets `uses-library org.apache.http.legacy` (`Ix.java:171-176`). Not a Maven artifact. |
| interpolator-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.interpolator:interpolator | 1.0.0 (GM) |  |
| kotlin-stdlib-1.4.30-M1-release-152 | 1.4.30-M1-release-152 | folder name only | org.jetbrains.kotlin:kotlin-stdlib (assumed from the name) | 2.4.20 (MC) | A milestone build is bundled. |
| kotlin-stdlib-jdk7-1.4.30-M1-release-152 | 1.4.30-M1-release-152 | folder name only | org.jetbrains.kotlin:kotlin-stdlib-jdk7 (assumed from the name) | 2.4.20 (MC) | A milestone build is bundled. |
| legacy-support-core-ui-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.legacy:legacy-support-core-ui | 1.0.0 (GM) |  |
| legacy-support-core-utils-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.legacy:legacy-support-core-utils | 1.0.0 (GM) |  |
| legacy-support-v13-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.legacy:legacy-support-v13 | 1.0.0 (GM) |  |
| legacy-support-v4-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.legacy:legacy-support-v4 | 1.0.0 (GM) | No classes.dex in dexs.zip for this one (112 dex files for 113 libraries). |
| lifecycle-common-2.4.0 | 2.4.0 | folder name only | androidx.lifecycle:lifecycle-common (assumed from the name) | 2.11.0 (GM) |  |
| lifecycle-livedata-2.4.0 | 2.4.0 | marker inside classes.jar | androidx.lifecycle:lifecycle-livedata | 2.11.0 (GM) |  |
| lifecycle-livedata-core-2.4.0 | 2.4.0 | marker inside classes.jar | androidx.lifecycle:lifecycle-livedata-core | 2.11.0 (GM) |  |
| lifecycle-process-2.4.0 | 2.4.0 | marker inside classes.jar | androidx.lifecycle:lifecycle-process | 2.11.0 (GM) |  |
| lifecycle-runtime-2.4.0 | 2.4.0 | marker inside classes.jar | androidx.lifecycle:lifecycle-runtime | 2.11.0 (GM) |  |
| lifecycle-viewmodel-2.3.1 | 2.3.1 | marker inside classes.jar | androidx.lifecycle:lifecycle-viewmodel | 2.11.0 (GM) |  |
| lifecycle-viewmodel-savedstate-2.3.1 | 2.3.1 | marker inside classes.jar | androidx.lifecycle:lifecycle-viewmodel-savedstate | 2.11.0 (GM) |  |
| listenablefuture-1.0.0 | 1.0.0 | folder name only | com.google.guava:listenablefuture (assumed from the name) | UNKNOWN | Upstream lists only 1.0 and a placeholder version. |
| loader-1.1.0 | 1.1.0 | marker inside classes.jar | androidx.loader:loader | 1.2.0 (GM) |  |
| localbroadcastmanager-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.localbroadcastmanager:localbroadcastmanager | 1.1.0 (GM) |  |
| Lottie-3.4.0 | 3.4.0 | folder name only | com.airbnb.android:lottie (assumed from the name) | 6.7.1 (MC) |  |
| material-1.6.1 | 1.6.1 | marker inside classes.jar | com.google.android.material:material | 1.14.0 (GM) | Added with AppCompat for every AppCompat project (`Dp.java:1023-1027`). Behaviour under Android 15 edge-to-edge: UNKNOWN (release notes not checked). |
| media-1.2.1 | 1.2.1 | marker inside classes.jar | androidx.media:media | 1.8.0 (GM) |  |
| multidex-2.0.1 | 2.0.1 | folder name only | androidx.multidex:multidex (assumed from the name) | 2.0.1 (GM) | Only added when a project's minSdk is below 21 (`Dp.java:281-284, 873-876`). |
| okhttp-3.9.1 | 3.9.1 | folder name only | com.squareup.okhttp3:okhttp (assumed from the name) | 5.5.0 (MC) | Used by the RequestNetwork component. |
| Okio-1.17.4 | 1.17.4 | folder name only | com.squareup.okio:okio (assumed from the name) | 3.18.2 (MC) | Used by okhttp 3.9.1. |
| OneSignal-3.14.0 | 3.14.0 | folder name only | com.onesignal:OneSignal (assumed from the name) | 5.10.2 (MC) | Two major versions behind. The generated manifest lists SDK 3 class names (`EditorManifest.java:37-118`), so a newer SDK cannot simply be dropped in. |
| OTPView-0.1.0 | 0.1.0 | folder name only | UNKNOWN | UNKNOWN | Origin not identified. |
| pattern-lock-view | (none in name) | folder name only | UNKNOWN | UNKNOWN | No version in the folder name; origin not identified. |
| play-services-ads-20.1.0 | 20.1.0 | marker inside classes.jar | com.google.android.gms:play-services-ads | 25.5.0 (GM) | Google Mobile Ads SDK 20.x is listed as sunset since 30 June 2024: ads at risk of not serving [admob-dep]. Used when AdMob is enabled. |
| play-services-ads-base-20.1.0 | 20.1.0 | marker inside classes.jar | com.google.android.gms:play-services-ads-base | 24.1.0 (GM) | Part of the Ads SDK 20.x set, see play-services-ads. |
| play-services-ads-identifier-17.0.0 | 17.0.0 | marker inside classes.jar | com.google.android.gms:play-services-ads-identifier | 18.3.0 (GM) |  |
| play-services-ads-lite-20.1.0 | 20.1.0 | marker inside classes.jar | com.google.android.gms:play-services-ads-lite | 25.0.0 (GM) | Part of the Ads SDK 20.x set, see play-services-ads. |
| play-services-auth-19.0.0 | 19.0.0 | marker inside classes.jar | com.google.android.gms:play-services-auth | 22.0.0 (GM) |  |
| play-services-auth-api-phone-17.0.5 | 17.5.0 | marker inside classes.jar | com.google.android.gms:play-services-auth-api-phone | 18.3.1 (GM) | Folder name says 17.0.5; the properties file inside says `version=17.5.0`. |
| play-services-auth-base-17.1.2 | 17.1.2 | marker inside classes.jar | com.google.android.gms:play-services-auth-base | 18.3.4 (GM) |  |
| play-services-base-17.6.0 | 17.6.0 | marker inside classes.jar | com.google.android.gms:play-services-base | 18.11.0 (GM) |  |
| play-services-basement-17.6.0 | 17.6.0 | marker inside classes.jar | com.google.android.gms:play-services-basement | 18.12.0 (GM) |  |
| play-services-gass-20.0.0 | 20.0.0 | marker inside classes.jar | com.google.android.gms:play-services-gass | 20.0.0 (GM) | Same as the newest listed version. Used by the Ads SDK. |
| play-services-gcm-17.0.0 | 17.0.0 | marker inside classes.jar | com.google.android.gms:play-services-gcm | 17.0.0 (GM) | No newer version is listed upstream. |
| play-services-iid-17.0.0 | 17.0.0 | marker inside classes.jar | com.google.android.gms:play-services-iid | 17.0.0 (GM) | No newer version is listed upstream. |
| play-services-location-18.0.0 | 18.0.0 | marker inside classes.jar | com.google.android.gms:play-services-location | 21.4.0 (GM) |  |
| play-services-maps-17.0.1 | 17.0.1 | marker inside classes.jar | com.google.android.gms:play-services-maps | 20.0.0 (GM) | Google Maps component. |
| play-services-measurement-18.0.3 | 18.0.3 | marker inside classes.jar | com.google.android.gms:play-services-measurement | 23.2.0 (GM) |  |
| play-services-measurement-base-18.0.3 | 18.0.3 | marker inside classes.jar | com.google.android.gms:play-services-measurement-base | 23.2.0 (GM) |  |
| play-services-measurement-impl-18.0.3 | 18.0.3 | marker inside classes.jar | com.google.android.gms:play-services-measurement-impl | 23.2.0 (GM) |  |
| play-services-measurement-sdk-18.0.3 | 18.0.3 | marker inside classes.jar | com.google.android.gms:play-services-measurement-sdk | 23.2.0 (GM) |  |
| play-services-measurement-sdk-api-18.0.3 | 18.0.3 | marker inside classes.jar | com.google.android.gms:play-services-measurement-sdk-api | 23.2.0 (GM) |  |
| play-services-places-placereport-17.0.0 | 17.0.0 | marker inside classes.jar | com.google.android.gms:play-services-places-placereport | 17.1.0 (GM) |  |
| play-services-stats-17.0.0 | 17.0.0 | marker inside classes.jar | com.google.android.gms:play-services-stats | 17.1.0 (GM) |  |
| play-services-tasks-17.2.1 | 17.2.1 | marker inside classes.jar | com.google.android.gms:play-services-tasks | 18.4.1 (GM) |  |
| print-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.print:print | 1.1.0 (GM) |  |
| recyclerview-1.2.1 | 1.2.1 | marker inside classes.jar | androidx.recyclerview:recyclerview | 1.4.0 (GM) |  |
| resourceinspection-annotation-1.0.0 | 1.0.0 | folder name only | androidx.resourceinspection:resourceinspection-annotation (assumed from the name) | 1.0.1 (GM) |  |
| savedstate-1.1.0 | 1.1.0 | marker inside classes.jar | androidx.savedstate:savedstate | 1.5.0 (GM) |  |
| slidingpanelayout-1.1.0 | 1.1.0 | marker inside classes.jar | androidx.slidingpanelayout:slidingpanelayout | 1.2.0 (GM) |  |
| startup-runtime-1.1.0 | 1.1.0 | marker inside classes.jar | androidx.startup:startup-runtime | 1.2.0 (GM) |  |
| swiperefreshlayout-1.2.0-alpha01 | 1.2.0-alpha01 | marker inside classes.jar | androidx.swiperefreshlayout:swiperefreshlayout | 1.2.0 (GM) | An alpha build is bundled. |
| tracing-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.tracing:tracing | 2.0.3 (GM) |  |
| transition-1.4.1 | 1.4.1 | marker inside classes.jar | androidx.transition:transition | 1.7.2 (GM) |  |
| vectordrawable-1.1.0 | 1.1.0 | marker inside classes.jar | androidx.vectordrawable:vectordrawable | 1.2.0 (GM) |  |
| vectordrawable-animated-1.1.0 | 1.1.0 | marker inside classes.jar | androidx.vectordrawable:vectordrawable-animated | 1.2.0 (GM) |  |
| versionedparcelable-1.1.1 | 1.1.1 | marker inside classes.jar | androidx.versionedparcelable:versionedparcelable | 1.2.1 (GM) |  |
| viewpager-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.viewpager:viewpager | 1.1.0 (GM) |  |
| viewpager2-1.0.0 | 1.0.0 | marker inside classes.jar | androidx.viewpager2:viewpager2 | 1.1.0 (GM) |  |
| wave-side-bar | (none in name) | folder name only | UNKNOWN | UNKNOWN | No version in the folder name; origin not identified. |

## 2. Components and blocks that touch platform behaviour changed in Android 12-15

Status words: **handled** = the generator has code for the new rule. **not handled** = the generator still produces the old behaviour. **unknown** = the code that decides it is in a precompiled jar or was not read. Runtime behaviour on a phone is NOT TESTED for every row.

| Component / area | Where the generated code is | What it declares or requests | Platform rule | Status |
|---|---|---|---|---|
| Bluetooth (BluetoothConnect component) | `Lx.java:948-1107` (`BluetoothConnect.java`), `Lx.java:1299-1675` (`BluetoothController.java`), permissions `yq.java:519-522`, manifest `Ix.java:301-325`, runtime request `PermissionManager.java:75-89` | Target ≥ 31: `BLUETOOTH` and `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` with `neverForLocation`; both requested at runtime on API 31+. Target ≤ 30: legacy permissions only. `BLUETOOTH_ADVERTISE` is not generated; the generated code has no "make discoverable" call. | Target 31+: scan / connect / advertise are runtime permissions; legacy ones with `maxSdkVersion="30"` [bt] | **handled** |
| Camera | intent `Lx.java:928-929`, output file and FileProvider URI `Lx.java:1243-1252`, file in the app's external files dir `Lx.java:2525-2528`, provider `Ix.java:53-64`, `yq.java:658-666`, permissions `yq.java:473-480` | `CAMERA`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`; all three requested at start | Target 33+: `READ_MEDIA_*` replace `READ_EXTERNAL_STORAGE` [13-target] | Capture path: **handled** (own directory + FileProvider, no storage permission needed). Storage requests at target 33+: **not handled** (asked, cannot be granted). |
| File picker | `Intent.ACTION_GET_CONTENT`, `Lx.java:931-932, 1233-1241`; result turned into a file path with `FileUtil.convertUriToFilePath`, `Lx.java:111-122, 2178-2240`; permission `yq.java:482-484` | `READ_EXTERNAL_STORAGE` | as above [13-target] | Picking: no permission needed. Reading the picked file by path at target 33+: **not handled** |
| File blocks (generated `FileUtil`) | `Lx.java:1935-2530`, for example `getExternalStorageDir()` at `:2166-2168` | raw paths on shared storage; `requestLegacyExternalStorage="true"` when target ≥ 28 (`Ix.java:286-297, 351-353`) | as above [13-target] | **not handled** for shared storage at target 33+. App-specific directories (`getPackageDataDir`, `Lx.java:2170-2172`) need no permission. |
| Firebase Storage | permissions `yq.java:493-497`; library `firebase-storage-19.0.0` | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | as above | Same as File blocks: **not handled** at target 33+ |
| Notifications: Firebase Cloud Messaging | `ConstVarManifest.java:14-17`, `EditorManifest.java:14-35`; libraries `firebase-messaging-19.0.0`, `firebase-iid-19.0.0` | `WAKE_LOCK`, `com.google.android.c2dm.permission.RECEIVE`, messaging service (`exported="false"`), instance-id receiver (`exported="true"`) | Android 13+: apps targeting 33+ must request `POST_NOTIFICATIONS` or notifications stay off [notif] | **not handled**: no `POST_NOTIFICATIONS` in any generated manifest or code |
| Notifications: OneSignal | `ConstVarManifest.java:18-42`, `EditorManifest.java:37-118`; library `OneSignal-3.14.0` | C2D permissions, badge permissions, `RECEIVE_BOOT_COMPLETED`, SDK 3 receivers and services | as above [notif] | **not handled** |
| Location (LocationManager component) | init `Lx.java:1266-1267`; permission `yq.java:524-526`; manifest adds COARSE next to FINE `Ix.java:326-330`; runtime request of both `PermissionManager.java:123-128` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | — | Permission part: **handled**. The block code that requests updates is in the precompiled `a.a.a.Fx`: **unknown**. Background location is not generated. |
| Speech to text | init `Lx.java:1260-1261`; listener `Lx.java:500-540`; permission `yq.java:515-517`; `<queries>` for `android.speech.RecognitionService` when target ≥ 30, `Ix.java:76-93` | `RECORD_AUDIO` | — | **handled** (source) |
| Text to speech | init `Lx.java:1257-1258`; `<queries>` for `android.intent.action.TTS_SERVICE` when target ≥ 30, `Ix.java:76-93` | none | — | **handled** (source) |
| Ads: AdMob | library flags `yq.java:453-458`, `Dp.java:1047-1049`; manifest `AdActivity` and app id `Ix.java:436-442, 452-454, 211-216`; fields and callbacks `Lx.java:630-685` | `INTERNET`, `ACCESS_NETWORK_STATE` | — | Library `play-services-ads 20.1.0` is sunset [admob-dep]: **not handled**. Whether the generated calls compile against a newer SDK: UNKNOWN. |
| Ads: Facebook Audience Network | `EditorManifest.java:120-134`; library `audience-network-sdk-5.9.0` | `AudienceNetworkActivity` and provider, `exported="false"` | — | **unknown** |
| Firebase Auth / Realtime Database | manifest `Ix.java:111-152`; libraries `firebase-auth-19.0.0`, `firebase-database-19.3.1`, `firebase-common-19.3.1` | init provider and `ComponentDiscoveryService`, `exported="false"`; `INTERNET`, `ACCESS_NETWORK_STATE` (`yq.java:444-448, 486-491`) | — | **unknown** (old versions; not run) |
| Firebase Dynamic Links | intent filter `Ix.java:231-253`; activity flags `Ix.java:421-426`; library `firebase-dynamic-links-19.0.0` | VIEW / BROWSABLE filter on the chosen activity; at target ≥ 31 that activity gets `exported="false"` | The service was shut down on 25 August 2025 [fdl] | **not usable**. Also: an activity with `exported="false"` cannot be opened by a link from another app. |
| Google Maps | `Ix.java:159-164`, `Lx.java:2536-2670` (`GoogleMapController`); library `play-services-maps-17.0.1` | API key meta-data; `INTERNET`, `ACCESS_NETWORK_STATE` (`yq.java:459-464`) | — | **unknown** |
| Network (RequestNetwork component) | `yq.java:508-513`; library `okhttp-3.9.1`; `usesCleartextTraffic="true"` unless "no HTTP legacy" is set, `Ix.java:354-357`; `uses-library org.apache.http.legacy`, `Ix.java:171-176, 465-468` | `INTERNET`, `ACCESS_NETWORK_STATE` | — | **handled** for cleartext. TLS behaviour of OkHttp 3.9.1 on Android 15: **unknown** |
| User-defined services and broadcast receivers | `Ix.java:197-209` (receiver, `exported="true"` at target ≥ 31), `Ix.java:224-229` (service: name and `enabled` only) | no `foregroundServiceType`, no `FOREGROUND_SERVICE*` permission | Target 34+: each foreground service must declare a type [14-target] | **not handled**: a project that starts a foreground service must inject the type and permission by hand |
| System bars / edge-to-edge | `Jx.java:345-346, 554-556`, `SystemBarsCode.java` | no permission | Target 35: edge-to-edge enforced [15-target] | **handled** (compiles: VERIFIED by experiment; look on a phone: NOT TESTED) |
| Back button event | `Ix.java:359-366` | `enableOnBackInvokedCallback="false"` at target ≥ 33 | — | **handled** |
| Runtime permission flow | `PermissionManager.java:95-216`, `Jx.java:404-411` | requests at activity start, then `initializeLogic()` whatever the answer | — | **handled** for CALL_PHONE, CAMERA, RECORD_AUDIO, location, Bluetooth. Storage: see above. |
| Debug build: crash screen | `assets/debug/SketchApplication.java:26-45`, `DebugActivity.java` | alarm + immutable `PendingIntent.getActivity` | Target 35+: PendingIntent creators no longer grant background-launch privilege by default [bal], [15-target] | **not handled** for target 35+ (RISK, NOT TESTED) |
| Debug build: log to the IDE | `assets/debug/SketchLogger.java:65-69`; IDE side `LogReaderActivity.java:178-184` | implicit broadcast to an exported, runtime-registered receiver | Target 34+: implicit intents reach exported components only [14-target] | **handled** |

## 3. Modernize next (in this order)

| # | Item | Why | Evidence |
|---|---|---|---|
| 1 | **Export signing: use the bundled apksigner** (v2/v3) instead of the v1-only `ZipSigner`, and sign after aligning | Exported APKs of every project with target ≥ 30 are refused by Android 11+. New projects target 37. The Run build already uses apksigner. | android15-audit.md B9, B10: VERIFIED (experiment here). On a phone: NOT TESTED (device test plan, step 13). |
| 2 | **`POST_NOTIFICATIONS`** for projects that use FCM or OneSignal: declare it and request it at runtime, gated on API 33+ | At target 33+ notifications are off until the permission is requested [notif]. The generator already has the same pattern for Bluetooth. | Table 2. VERIFIED (source). |
| 3 | **Storage permissions at target 33+**: stop requesting `READ/WRITE_EXTERNAL_STORAGE` where they can no longer be granted; request `READ_MEDIA_*` where shared media is read; give File picker results as a stream or a copied file | The current request is asked and denied on every start; path-based reads of shared files fail [13-target]. | Table 2. VERIFIED (source). |
| 4 | **Debug crash screen** for target 35+ projects | It is how the owner sees why a built app crashed. It depends on a background activity start that target 35 restricts [bal]. | android15-audit.md B12. NOT TESTED. |
| 5 | **Google Mobile Ads SDK** (20.1.0 → a supported version) | 20.x is sunset; ads are at risk of not serving [admob-dep]. Needs a check of the generated ad code against the new API and of the SDK's minSdk and library needs. | Table 1. DOCUMENTED. |
| 6 | **Firebase Dynamic Links component**: remove or mark as ended | The service no longer exists [fdl]. | Table 1 and 2. DOCUMENTED. |
| 7 | **Foreground-service type** for user services | Required at target 34+ [14-target]; today it must be injected by hand. | Table 2. VERIFIED (source). |
| 8 | **AndroidX / Material set** (appcompat 1.4.0, material 1.6.1, core 1.7.0, activity 1.3.0, fragment 1.3.6 → current) | All from before Android 14/15. No verified failure. Newer versions may need compileSdk 34+ and a newer android.jar, which ties this to the toolchain work in java17-path.md (stage 2). | Table 1. Need for it on Android 15: UNKNOWN. |
| 9 | **Firebase and Play services base libraries**, OneSignal, OkHttp | Several major versions behind. No verified failure on Android 15. Each needs its own compile test of the generated code. | Table 1. UNKNOWN. |
| 10 | **16 KB check for user native libraries**: read the ELF LOAD alignment of each `.so` a project adds and warn | The IDE packs whatever `.so` it is given; a 4 KB-aligned 64-bit library is a risk on 16 KB devices. | android15-audit.md B7. VERIFIED (source: no such check). |

Items 1 to 4 need no new library and no toolchain change.

## Could not be determined here

- Whether any bundled library version fails on Android 15: no library was run.
- The origin and upstream of `code-view`, `pattern-lock-view`, `wave-side-bar`, `OTPView-0.1.0`.
- Block code generated by the precompiled `a.a.a.Fx` (location updates, most component actions) was not read.
- What newer library versions require (minSdk, compileSdk, Kotlin, transitive libraries).
