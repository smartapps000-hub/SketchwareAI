# Component and library modernization inventory

Audited: commit `c82aba4` (v1.8.1). Date: 2026-10-04. Section 0 added for v1.8.3 (2026-10-21), section 0a for v1.8.4 (2026-10-22). Nothing here was run on a device.

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

## 0a. v1.8.4 update of the built-in libraries (2026-10-22)

Projects now compile against the bundled **android-34** android.jar, so the minCompileSdk gate is 34 (other gates as in v1.8.3:
minSdk <= 21, Kotlin metadata <= 1.8, class files <= 55, same major for OkHttp/Okio/Glide/Lottie, every dependency inside the set).
New in the packaging: `ClassFileSanitizer` removes the nameless `MethodParameters` entries that javac 21 writes (AndroidX built since
2024), which crashed the bundled D8/R8 3.3.75; the jars in `libs.zip` are the fixed ones, so D8 on the phone reads them too.
Result: 38 updated, 2 new, 4 kept on purpose, 27 unchanged. `libs.zip`: 118 folders, `dexs.zip`: 117 dex files.
Licences (POM, read 2026-10-22): every updated/new library is Apache-2.0 (AndroidX, Material, graphics-shapes, atomicfu); no other licence family was added.

| Constant | Maven | v1.8.3 | v1.8.4 | Gates read from the artifact / reason |
|---|---|---|---|---|
| `ANDROIDX_ACTIVITY` | androidx.activity:activity | activity-1.7.2 | activity-1.9.3 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_ANNOTATION_EXPERIMENTAL` | androidx.annotation:annotation-experimental | annotation-experimental-1.3.1 | annotation-experimental-1.4.1 | minCompileSdk 34, minSdk 19, Kotlin [1, 7, 0], class 52 |
| `ANDROIDX_APPCOMPAT` | androidx.appcompat:appcompat | appcompat-1.6.1 | appcompat-1.7.1 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 166 of 400 classes changed |
| `ANDROIDX_APPCOMPAT_RESOURCES` | androidx.appcompat:appcompat-resources | appcompat-resources-1.6.1 | appcompat-resources-1.7.1 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 2 of 35 classes changed |
| `ANDROIDX_ASYNCLAYOUTINFLATER` | androidx.asynclayoutinflater:asynclayoutinflater | asynclayoutinflater-1.0.0 | asynclayoutinflater-1.1.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 1 of 7 classes changed |
| `ANDROIDX_BROWSER` | androidx.browser:browser | browser-1.5.0 | browser-1.8.0 | minCompileSdk 34, minSdk 19, Kotlin None, class 52 |
| `ANDROIDX_CONCURRENT_FUTURES` | androidx.concurrent:concurrent-futures | concurrent-futures-1.1.0 | concurrent-futures-1.3.0 | minCompileSdk 1, minSdk 1, Kotlin None, class 52; D8 fix: 2 of 19 classes changed |
| `ANDROIDX_CONSTRAINTLAYOUT` | androidx.constraintlayout:constraintlayout | constraintlayout-2.1.4 | constraintlayout-2.2.2 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 32 of 163 classes changed |
| `ANDROIDX_CONSTRAINTLAYOUT_CORE` | androidx.constraintlayout:constraintlayout-core | constraintlayout-core-1.0.4 | constraintlayout-core-1.1.2 | minCompileSdk 1, minSdk 1, Kotlin None, class 52; D8 fix: 51 of 247 classes changed |
| `ANDROIDX_COORDINATORLAYOUT` | androidx.coordinatorlayout:coordinatorlayout | coordinatorlayout-1.2.0 | coordinatorlayout-1.3.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 6 of 14 classes changed |
| `ANDROIDX_CORE` | androidx.core:core | core-1.10.1 | core-1.13.1 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_CORE_KTX` | androidx.core:core-ktx | core-ktx-1.10.1 | core-ktx-1.13.1 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_CUSTOMVIEW` | androidx.customview:customview | customview-1.1.0 | customview-1.2.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 6 of 17 classes changed |
| `ANDROIDX_CUSTOMVIEW_POOLINGCONTAINER` | androidx.customview:customview-poolingcontainer | customview-poolingcontainer-1.0.0 | customview-poolingcontainer-1.1.0 | minCompileSdk 34, minSdk 21, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_DOCUMENTFILE` | androidx.documentfile:documentfile | documentfile-1.0.1 | documentfile-1.1.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52 |
| `ANDROIDX_DYNAMIC_ANIMATION` | androidx.dynamicanimation:dynamicanimation | dynamic-animation-1.1.0-alpha03 | dynamic-animation-1.1.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 18 of 36 classes changed |
| `ANDROIDX_EMOJI2` | androidx.emoji2:emoji2 | emoji2-1.3.0 | emoji2-1.5.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 6 of 110 classes changed |
| `ANDROIDX_EMOJI2_VIEWS_HELPER` | androidx.emoji2:emoji2-views-helper | emoji2-views-helper-1.3.0 | emoji2-views-helper-1.5.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52 |
| `ANDROIDX_EXIFINTERFACE` | androidx.exifinterface:exifinterface | exifinterface-1.3.7 | exifinterface-1.4.2 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 1 of 12 classes changed |
| `ANDROIDX_FRAGMENT` | androidx.fragment:fragment | fragment-1.6.2 | fragment-1.8.9 | minCompileSdk 34, minSdk 21, Kotlin [1, 8, 0], class 52; D8 fix: 49 of 152 classes changed |
| `ANDROIDX_GRAPHICS_SHAPES` | androidx.graphics:graphics-shapes | (new) | graphics-shapes-1.0.1 | minCompileSdk 34, minSdk 21, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_COMMON` | androidx.lifecycle:lifecycle-common | lifecycle-common-2.6.2 | lifecycle-common-2.8.7 | minCompileSdk 1, minSdk 1, Kotlin (1, 8, 0), class 52 |
| `ANDROIDX_LIFECYCLE_LIVEDATA` | androidx.lifecycle:lifecycle-livedata | lifecycle-livedata-2.6.2 | lifecycle-livedata-2.8.7 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_LIVEDATA_CORE` | androidx.lifecycle:lifecycle-livedata-core | lifecycle-livedata-core-2.6.2 | lifecycle-livedata-core-2.8.7 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52; D8 fix: 4 of 9 classes changed |
| `ANDROIDX_LIFECYCLE_PROCESS` | androidx.lifecycle:lifecycle-process | lifecycle-process-2.6.2 | lifecycle-process-2.8.7 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_RUNTIME` | androidx.lifecycle:lifecycle-runtime | lifecycle-runtime-2.6.2 | lifecycle-runtime-2.8.7 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_VIEWMODEL` | androidx.lifecycle:lifecycle-viewmodel | lifecycle-viewmodel-2.6.2 | lifecycle-viewmodel-2.8.7 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE` | androidx.lifecycle:lifecycle-viewmodel-savedstate | lifecycle-viewmodel-savedstate-2.6.2 | lifecycle-viewmodel-savedstate-2.8.7 | minCompileSdk 34, minSdk 19, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_MEDIA` | androidx.media:media | media-1.6.0 | media-1.7.1 | minCompileSdk 34, minSdk 19, Kotlin None, class 52 |
| `ANDROIDX_PRINT` | androidx.print:print | print-1.0.0 | print-1.1.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 5 of 9 classes changed |
| `ANDROIDX_PROFILEINSTALLER` | androidx.profileinstaller:profileinstaller | profileinstaller-1.3.1 | profileinstaller-1.4.1 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 3 of 26 classes changed |
| `ANDROIDX_SLIDINGPANELAYOUT` | androidx.slidingpanelayout:slidingpanelayout | slidingpanelayout-1.1.0 | slidingpanelayout-1.1.0 | kept: 1.2.0 adds androidx.window + Kotlin coroutines to every project that uses legacy-support-core-ui; nothing generated uses it |
| `ANDROIDX_STARTUP_RUNTIME` | androidx.startup:startup-runtime | startup-runtime-1.1.1 | startup-runtime-1.2.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52 |
| `ANDROIDX_SWIPEREFRESHLAYOUT` | androidx.swiperefreshlayout:swiperefreshlayout | swiperefreshlayout-1.2.0-alpha01 | swiperefreshlayout-1.2.0-alpha01 | kept: bundled 1.2.0-alpha01 is newer than the usable stable 1.1.0 |
| `ANDROIDX_TRANSITION` | androidx.transition:transition | transition-1.4.1 | transition-1.5.1 | minCompileSdk 34, minSdk 19, Kotlin None, class 52 |
| `ANDROIDX_VECTORDRAWABLE` | androidx.vectordrawable:vectordrawable | vectordrawable-1.1.0 | vectordrawable-1.2.0 | minCompileSdk 34, minSdk 19, Kotlin None, class 52; D8 fix: 1 of 12 classes changed |
| `ANDROIDX_VECTORDRAWABLE_ANIMATED` | androidx.vectordrawable:vectordrawable-animated | vectordrawable-animated-1.1.0 | vectordrawable-animated-1.2.0 | minCompileSdk 34, minSdk 19, Kotlin None, class 52; D8 fix: 5 of 14 classes changed |
| `ANDROIDX_VERSIONEDPARCELABLE` | androidx.versionedparcelable:versionedparcelable | versionedparcelable-1.1.1 | versionedparcelable-1.2.1 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 3 of 16 classes changed |
| `ANDROIDX_VIEWPAGER2` | androidx.viewpager2:viewpager2 | viewpager2-1.0.0 | viewpager2-1.1.0 | minCompileSdk 34, minSdk 19, Kotlin None, class 52; D8 fix: 24 of 50 classes changed |
| `ANDROIDX_VIEWPAGER` | androidx.viewpager:viewpager | viewpager-1.0.0 | viewpager-1.1.0 | minCompileSdk 34, minSdk 21, Kotlin None, class 52; D8 fix: 10 of 24 classes changed |
| `MATERIAL` | com.google.android.material:material | material-1.9.0 | material-1.13.0 | minCompileSdk 1, minSdk 21, Kotlin None, class 52; D8 fix: 352 of 1070 classes changed |
| `ANDROIDX_LISTENABLEFUTURE` | com.google.guava:listenablefuture | listenablefuture-1.0.0 | listenablefuture-1.0.0 | kept: same artifact as bundled (1.0 = folder listenablefuture-1.0.0) |
| `CIRCLE_IMAGEVIEW` | de.hdodenhof:circleimageview | circle-imageview-v3.1.0 | circle-imageview-v3.1.0 | kept: same version as bundled (folder circle-imageview-v3.1.0) |
| `KOTLINX_ATOMICFU` | org.jetbrains.kotlinx:atomicfu | (new) | atomicfu-0.21.0 | minCompileSdk 1, minSdk 1, Kotlin [1, 4, 3], class 52 |

## 0. v1.8.3 update of the built-in libraries (2026-10-21)

Sections 1-4 below are the v1.8.1 audit and keep its numbers. In v1.8.3 the libraries the generated code and their
dependencies use were moved to the newest version that passes the gates in `v1.8.3-verification.md` (minCompileSdk <= 33,
minSdk <= 21, Kotlin metadata <= 1.8, class files <= 55, dexable by the bundled D8 3.3.75, same major for OkHttp/Okio/Glide/Lottie).
Made with `tools/builtin-libs` (coords.py -> solve.py -> package.py -> genjava.py -> check_graph.py). Firebase, Play services,
OneSignal, Audience Network, YouTube player and the small view libraries were **not** changed (see the verification file).
`libs.zip`: 116 folders, `dexs.zip`: 115 dex files.

| Constant | Maven | Before | v1.8.3 | Gates read from the artifact / reason |
|---|---|---|---|---|
| `ANDROIDX_ACTIVITY` | androidx.activity:activity | activity-1.3.0 | activity-1.7.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_ANNOTATION` | androidx.annotation:annotation | annotation-1.3.0 | annotation-1.9.0 | minCompileSdk 1, minSdk 1, Kotlin [1, 7, 0], class 52 |
| `ANDROIDX_ANNOTATION_EXPERIMENTAL` | androidx.annotation:annotation-experimental | annotation-experimental-1.2.0 | annotation-experimental-1.3.1 | minCompileSdk 33, minSdk 14, Kotlin [1, 7, 1], class 52 |
| `ANDROIDX_APPCOMPAT` | androidx.appcompat:appcompat | appcompat-1.4.0 | appcompat-1.6.1 | minCompileSdk 33, minSdk 14, Kotlin [1, 7, 1], class 52 |
| `ANDROIDX_APPCOMPAT_RESOURCES` | androidx.appcompat:appcompat-resources | appcompat-resources-1.4.0 | appcompat-resources-1.6.1 | minCompileSdk 33, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_CORE_COMMON` | androidx.arch.core:core-common | core-common-2.1.0 | core-common-2.2.0 | minCompileSdk 1, minSdk 1, Kotlin None, class 52 |
| `ANDROIDX_CORE_RUNTIME` | androidx.arch.core:core-runtime | core-runtime-2.1.0 | core-runtime-2.2.0 | minCompileSdk 33, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_ASYNCLAYOUTINFLATER` | androidx.asynclayoutinflater:asynclayoutinflater | asynclayoutinflater-1.0.0 | asynclayoutinflater-1.0.0 | same version |
| `ANDROIDX_BROWSER` | androidx.browser:browser | browser-1.3.0 | browser-1.5.0 | minCompileSdk 33, minSdk 16, Kotlin None, class 52 |
| `ANDROIDX_CARDVIEW` | androidx.cardview:cardview | cardview-1.0.0 | cardview-1.0.0 | same version |
| `ANDROIDX_COLLECTION` | androidx.collection:collection | collection-1.2.0 | collection-1.4.5 | minCompileSdk 1, minSdk 1, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_CONCURRENT_FUTURES` | androidx.concurrent:concurrent-futures | concurrent-futures-1.1.0 | concurrent-futures-1.1.0 | kept: 1.2.0+ class files crash the IDE's D8 3.3.75 (NullPointerException in CallbackToFutureAdapter$SafeFuture$1); bundled 1.1.0 is what profileinstaller needs |
| `ANDROIDX_CONSTRAINTLAYOUT` | androidx.constraintlayout:constraintlayout | constraintlayout-2.1.2 | constraintlayout-2.1.4 | minCompileSdk 1, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_CONSTRAINTLAYOUT_CORE` | androidx.constraintlayout:constraintlayout-core | constraintlayout-core-1.0.2 | constraintlayout-core-1.0.4 | minCompileSdk 1, minSdk 1, Kotlin None, class 52 |
| `ANDROIDX_COORDINATORLAYOUT` | androidx.coordinatorlayout:coordinatorlayout | coordinatorlayout-1.1.0 | coordinatorlayout-1.2.0 | minCompileSdk 31, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_CORE` | androidx.core:core | core-1.7.0 | core-1.10.1 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_CORE_KTX` | androidx.core:core-ktx | core-ktx-1.5.0-beta01 | core-ktx-1.10.1 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_CURSORADAPTER` | androidx.cursoradapter:cursoradapter | cursoradapter-1.0.0 | cursoradapter-1.0.0 | same version |
| `ANDROIDX_CUSTOMVIEW` | androidx.customview:customview | customview-1.1.0 | customview-1.1.0 | same version |
| `ANDROIDX_CUSTOMVIEW_POOLINGCONTAINER` | androidx.customview:customview-poolingcontainer | (new) | customview-poolingcontainer-1.0.0 | minCompileSdk 32, minSdk 14, Kotlin [1, 6, 0], class 52 |
| `ANDROIDX_DOCUMENTFILE` | androidx.documentfile:documentfile | documentfile-1.0.1 | documentfile-1.0.1 | same version |
| `ANDROIDX_DRAWERLAYOUT` | androidx.drawerlayout:drawerlayout | drawerlayout-1.1.1 | drawerlayout-1.2.0 | minCompileSdk 33, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_DYNAMIC_ANIMATION` | androidx.dynamicanimation:dynamicanimation | dynamic-animation-1.1.0-alpha03 | dynamic-animation-1.1.0-alpha03 | kept: bundled 1.1.0-alpha03 is newer than the usable stable 1.0.0 |
| `ANDROIDX_EMOJI2` | androidx.emoji2:emoji2 | emoji2-1.0.1 | emoji2-1.3.0 | minCompileSdk 33, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_EMOJI2_VIEWS_HELPER` | androidx.emoji2:emoji2-views-helper | emoji2-views-helper-1.0.1 | emoji2-views-helper-1.3.0 | minCompileSdk 33, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_EXIFINTERFACE` | androidx.exifinterface:exifinterface | exifinterface-1.0.0 | exifinterface-1.3.7 | minCompileSdk 1, minSdk 14, Kotlin None, class 51 |
| `ANDROIDX_FRAGMENT` | androidx.fragment:fragment | fragment-1.3.6 | fragment-1.6.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_INTERPOLATOR` | androidx.interpolator:interpolator | interpolator-1.0.0 | interpolator-1.0.0 | same version |
| `ANDROIDX_LIFECYCLE_COMMON` | androidx.lifecycle:lifecycle-common | lifecycle-common-2.4.0 | lifecycle-common-2.6.2 | minCompileSdk 1, minSdk 1, Kotlin (1, 8, 0), class 52 |
| `ANDROIDX_LIFECYCLE_LIVEDATA` | androidx.lifecycle:lifecycle-livedata | lifecycle-livedata-2.4.0 | lifecycle-livedata-2.6.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_LIVEDATA_CORE` | androidx.lifecycle:lifecycle-livedata-core | lifecycle-livedata-core-2.4.0 | lifecycle-livedata-core-2.6.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_PROCESS` | androidx.lifecycle:lifecycle-process | lifecycle-process-2.4.0 | lifecycle-process-2.6.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_RUNTIME` | androidx.lifecycle:lifecycle-runtime | lifecycle-runtime-2.4.0 | lifecycle-runtime-2.6.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_VIEWMODEL` | androidx.lifecycle:lifecycle-viewmodel | lifecycle-viewmodel-2.3.1 | lifecycle-viewmodel-2.6.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE` | androidx.lifecycle:lifecycle-viewmodel-savedstate | lifecycle-viewmodel-savedstate-2.3.1 | lifecycle-viewmodel-savedstate-2.6.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_LOADER` | androidx.loader:loader | loader-1.1.0 | loader-1.1.0 | same version |
| `ANDROIDX_LOCALBROADCASTMANAGER` | androidx.localbroadcastmanager:localbroadcastmanager | localbroadcastmanager-1.0.0 | localbroadcastmanager-1.1.0 | minCompileSdk 31, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_MEDIA` | androidx.media:media | media-1.2.1 | media-1.6.0 | minCompileSdk 31, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_PRINT` | androidx.print:print | print-1.0.0 | print-1.0.0 | same version |
| `ANDROIDX_PROFILEINSTALLER` | androidx.profileinstaller:profileinstaller | (new) | profileinstaller-1.3.1 | minCompileSdk 33, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_RECYCLERVIEW` | androidx.recyclerview:recyclerview | recyclerview-1.2.1 | recyclerview-1.3.2 | minCompileSdk 33, minSdk 14, Kotlin [1, 7, 1], class 52 |
| `ANDROIDX_RESOURCEINSPECTION_ANNOTATION` | androidx.resourceinspection:resourceinspection-annotation | resourceinspection-annotation-1.0.0 | resourceinspection-annotation-1.0.1 | minCompileSdk 1, minSdk 1, Kotlin None, class 52 |
| `ANDROIDX_SAVEDSTATE` | androidx.savedstate:savedstate | savedstate-1.1.0 | savedstate-1.2.1 | minCompileSdk 33, minSdk 14, Kotlin [1, 8, 0], class 52 |
| `ANDROIDX_SLIDINGPANELAYOUT` | androidx.slidingpanelayout:slidingpanelayout | slidingpanelayout-1.1.0 | slidingpanelayout-1.1.0 | kept: 1.2.0 adds androidx.window + Kotlin coroutines to every project that uses legacy-support-core-ui; nothing generated uses it |
| `ANDROIDX_STARTUP_RUNTIME` | androidx.startup:startup-runtime | startup-runtime-1.1.0 | startup-runtime-1.1.1 | minCompileSdk 31, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_SWIPEREFRESHLAYOUT` | androidx.swiperefreshlayout:swiperefreshlayout | swiperefreshlayout-1.2.0-alpha01 | swiperefreshlayout-1.2.0-alpha01 | kept: bundled 1.2.0-alpha01 is newer than the usable stable 1.1.0 |
| `ANDROIDX_TRACING` | androidx.tracing:tracing | tracing-1.0.0 | tracing-1.2.0 | minCompileSdk 33, minSdk 14, Kotlin None, class 52 |
| `ANDROIDX_TRANSITION` | androidx.transition:transition | transition-1.4.1 | transition-1.4.1 | same version |
| `ANDROIDX_VECTORDRAWABLE` | androidx.vectordrawable:vectordrawable | vectordrawable-1.1.0 | vectordrawable-1.1.0 | same version |
| `ANDROIDX_VECTORDRAWABLE_ANIMATED` | androidx.vectordrawable:vectordrawable-animated | vectordrawable-animated-1.1.0 | vectordrawable-animated-1.1.0 | same version |
| `ANDROIDX_VERSIONEDPARCELABLE` | androidx.versionedparcelable:versionedparcelable | versionedparcelable-1.1.1 | versionedparcelable-1.1.1 | same version |
| `ANDROIDX_VIEWPAGER2` | androidx.viewpager2:viewpager2 | viewpager2-1.0.0 | viewpager2-1.0.0 | same version |
| `ANDROIDX_VIEWPAGER` | androidx.viewpager:viewpager | viewpager-1.0.0 | viewpager-1.0.0 | same version |
| `LOTTIE` | com.airbnb.android:lottie | Lottie-3.4.0 | Lottie-3.7.2 | minCompileSdk 1, minSdk 16, Kotlin None, class 51 |
| `GLIDE_ANNOTATIONS` | com.github.bumptech.glide:annotations | annotations-4.11.0 | annotations-4.16.0 | minCompileSdk 1, minSdk 1, Kotlin None, class 51 |
| `GLIDE_DISKLRUCACHE` | com.github.bumptech.glide:disklrucache | disklrucache-4.11.0 | disklrucache-4.16.0 | minCompileSdk 1, minSdk 14, Kotlin None, class 51 |
| `GLIDE_GIFDECODER` | com.github.bumptech.glide:gifdecoder | gifdecoder-4.11.0 | gifdecoder-4.16.0 | minCompileSdk 1, minSdk 14, Kotlin None, class 52 |
| `GLIDE` | com.github.bumptech.glide:glide | glide-4.11.0 | glide-4.16.0 | minCompileSdk 1, minSdk 14, Kotlin None, class 51 |
| `MATERIAL` | com.google.android.material:material | material-1.6.1 | material-1.9.0 | minCompileSdk 1, minSdk 14, Kotlin None, class 52 |
| `GOOGLE_AUTO_VALUE_ANNOTATIONS` | com.google.auto.value:auto-value-annotations | auto-value-annotations-1.6.5 | auto-value-annotations-1.11.1 | minCompileSdk 1, minSdk 1, Kotlin None, class 52 |
| `GSON` | com.google.code.gson:gson | gson-2.8.7 | gson-2.14.0 | minCompileSdk 1, minSdk 1, Kotlin None, class 52 |
| `ANDROIDX_LISTENABLEFUTURE` | com.google.guava:listenablefuture | listenablefuture-1.0.0 | listenablefuture-1.0.0 | kept: same artifact as bundled (1.0 = folder listenablefuture-1.0.0) |
| `OKHTTP` | com.squareup.okhttp3:okhttp | okhttp-3.9.1 | okhttp-3.14.9 | minCompileSdk 1, minSdk 1, Kotlin None, class 52 |
| `OKIO` | com.squareup.okio:okio | Okio-1.17.4 | Okio-1.17.6 | minCompileSdk 1, minSdk 1, Kotlin None, class 51 |
| `CIRCLE_IMAGEVIEW` | de.hdodenhof:circleimageview | circle-imageview-v3.1.0 | circle-imageview-v3.1.0 | kept: same version as bundled (folder circle-imageview-v3.1.0) |
| `KOTLIN_STDLIB` | org.jetbrains.kotlin:kotlin-stdlib | kotlin-stdlib-1.4.30-M1-release-152 | kotlin-stdlib-1.8.22 | minCompileSdk 1, minSdk 1, Kotlin [1, 8, 0], class 52 |
| `KOTLINX_COROUTINES_ANDROID` | org.jetbrains.kotlinx:kotlinx-coroutines-android | (new) | kotlinx-coroutines-android-1.7.3 | minCompileSdk 1, minSdk 1, Kotlin [1, 8, 0], class 52 |
| `KOTLINX_COROUTINES_CORE` | org.jetbrains.kotlinx:kotlinx-coroutines-core | (new) | kotlinx-coroutines-core-1.7.3 | minCompileSdk 1, minSdk 1, Kotlin [1, 8, 0], class 52 |
| `JETBRAINS_ANNOTATIONS` | org.jetbrains:annotations | annotations-13.0 | annotations-24.1.0 | minCompileSdk 1, minSdk 1, Kotlin None, class 52 |
| `KOTLIN_STDLIB_JDK7` | org.jetbrains.kotlin:kotlin-stdlib-jdk7 | kotlin-stdlib-jdk7-1.4.30-M1-release-152 | (removed) | empty since Kotlin 1.8; the constant now names kotlin-stdlib |

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

v1.8.5 (22-23 Oct 2026): the built-in libraries are unchanged (Material 1.13.0 ...). Checked for the next step:
Material Components for Android 1.14.0 (13 May 2026, the last planned View release: the repository is in maintenance
mode since Google I/O 2026) needs min SDK 23 for Material and AndroidX and pulls androidx.core 1.16.0 (minCompileSdk 35):
it needs a bundled API 35 android.jar and generated apps at min SDK 23, so it is its own migration. Compose Material
(1.12.1 / 1.13.0-beta) and Material 3 for Compose can't be built on the phone at all (Compose compiler plugin with Kotlin
2.x, compileSdk 37+, min SDK 23). Source: release notes read 22 Oct 2026 (reference-index.json `mdc-android-1.14.0`,
`compose-material`); not verified from the AAR metadata.

## Could not be determined here

- Whether any bundled library version fails on Android 15: no library was run.
- The origin and upstream of `code-view`, `pattern-lock-view`, `wave-side-bar`, `OTPView-0.1.0`.
- Block code generated by the precompiled `a.a.a.Fx` (location updates, most component actions) was not read.
- What newer library versions require (minSdk, compileSdk, Kotlin, transitive libraries).

## v1.8.6

- IDE: `app/libs/material-color-utilities-1.13.0.jar` (the `com.google.android.material.color.utilities` classes of
  Material 1.13.0, Apache-2.0) for M3Palette; generated apps already have them inside the built-in Material 1.13.0.
- Material 1.13.0 (built-in) already contains Material 3 Expressive components: MaterialButtonGroup, MaterialSplitButton,
  LoadingIndicator, DockedToolbarLayout, FloatingToolbarLayout (no Expressive theme). The palette does not offer them yet
  (roadmap); calling them from project code was not tested.
