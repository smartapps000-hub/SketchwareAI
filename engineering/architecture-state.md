# Architecture state

Verified facts about this fork, kept so they don't have to be rediscovered. Last updated for v1.8.5.

## Foundations (do not bypass)

- Fork of Sketchware Pro 6.4.0-rc05 (git base `966d905`); package `com.sketchware.ai`.
- Storage is the original layout: `/.sketchware/data/<sc_id>/{logic,view,file,library,resource}` (logic/view/file are AES/CBC, key = iv = `sketchwaresecure`), generated project in `/.sketchware/mysc/<sc_id>/`, local libraries in `/.sketchware/libs/local_libs/<name>/`.
- The block graph is the source of truth. Generated Java is derived and rewritten at every build (`mysc/<sc_id>` is deleted first). Build-time repairs therefore never persist in files; they are deterministic and re-applied at each build.
- Most of the core (`a.a.a.Fx` block-to-code, `eC` project data, `BlockPane`, `Rs` block view) exists only as compiled classes in `app/libs/*.jar`. They are used, not modified.
- Block ids: a block's id is the view tag it gets in the logic editor; it is kept across load/save of an event and is unique per event (not per project). A block is identified by activity + event key (`<targetId>_<eventName>`) + id.

## Phase 1 - Source Map Engine (done in v1.8.0)

`com.sketchware.ai.sourcemap`

- `SourceMapMarkers`: during a second, throw-away generation every statement block of every event/MoreBlock gets a marker block in front and behind (plain "add source directly" blocks whose code is a comment). The generator itself is not changed.
- `SourceMap.extract`: removes the markers and notes the lines. Hooked in `Jx.generateCode()` (activities) with `SourceMapMarkers.wrap` at the four block-to-code call sites (onCreate, events in `Hx`, adapters, MoreBlocks).
- Proof instead of trust: the map is kept only if the marked text minus markers equals the normal generation character for character, and (at `SourceMaps.finish`) only if the file on disk has the hash of that text. A lookup re-checks the hash (`SourceMaps.resolve`) or uses maps checked at the start of the compile step (`ErrorOrigins.session`). Anything else answers "generated source only"; a wrong block is never shown.
- Written per build by `yq.b()` to `mysc/<sc_id>/sourcemap.json`: per file `{file, sourceHash, generator, exact, events[{kind,targetId,eventName,start,end}], entries[{event, blockId, opCode, position, depth, start, end}]}`. Regenerated from the block graph each build, so it survives edits by construction; only activities whose code is generated are mapped.
- A saved log is tied to its map: the build's log ends with `(source map <file>@<hash prefix>)`; the compile log screen resolves a file only if the log names the map on disk, the file still has the map's hash, and the line on disk is the line the compiler quoted. An older log shown after a newer build gets no origins.
- Exports (`exportingProject`) make no source map and leave none in the exported folder.
- Consumers: failed build log gets "Where the errors come from"; auto-fix messages get the origin; the diagnostic sheet shows one card per Java error with Origin, **Open block** (logic editor opened on the event, block scrolled into view and ringed: extras `focus_block_id`, `focus_block_op`, `focus_line`) and **View source** (the file the compiler read, from disk, at the line: extras `path`, `line`).
- Not mapped: lines outside block code (fields, method headers, listener frames, imports), expression blocks on their own (they belong to their statement block), XML/layout lines, files replaced by custom Java, files rewritten by command blocks.

## Phase 2 - Build Map (first part in v1.8.0)

- `com.sketchware.ai.buildmap.BuildMap`: 13 tasks (prepare, generate, built-in libraries, AAPT2, Kotlin, Java, StringFog, ProGuard/R8, DEX, DEX merge, package, sign, install) with status and duration; errors and repair attempts for the failed task. `mysc/<sc_id>/buildmap.json`; printed at the end of a failed build's log.
- Missing: per-task inputs/outputs, warnings per task, cache (the build has none), AAB/zipalign/launch tasks, a dashboard screen.

## v1.8.1 additions

- **Write-back to blocks** (`sourcemap.BlockWriteBack`): after a build that compiled with repairs, each repair whose changed lines lie inside one "add source directly" block becomes a proposal in `mysc/<sc_id>/autofix.json` (block code before/after, changed lines). The compile log's sheet shows it with **Apply to block** / **Undo**. Apply replaces the block's code in the open project's data, generates the activity again and requires the block's lines to equal the repaired lines that compiled; otherwise the old code is put back. History in `data/<sc_id>/autofix_history.json`. Not written back: repairs in ordinary blocks, added imports, repairs that span two blocks. The build still repairs those each time.
- **PNG sanitizer** (`project.PngSanitizer`), hooked in `ResourceCompiler.compileDirectory` for project, imported and local-library resources.
- **Library integrity** (`project.LibraryIntegrity`), run by `DesignActivity` before the sources are generated. Restores a missing classes.jar from an archive in the folder, or by downloading a coordinate that is certain (`source.json`, written at every download from now on, or the curated tables); never by searching a repository for the name. Adds only missing files. `LibraryHeal.writeConfig` takes the package from the AAR's manifest, never writes a Maven group that isn't a valid Java package, and never replaces an existing config.
- **Dialogs that reuse `R.layout.dialog`** (`AsdAll`, `AsdOrigin`, `SketchDialog`) now go through `ui.ObsidianDialogs.window/leftButton/finish`. Before, they added a bare "Code Editor" text in front of the pill row and never hid the unnamed left pill, so Cancel/Select were squeezed and unreadable, and their option lists kept dark text on the dark sheet.
- Sheets and dialogs are fully opaque (2-3% see-through showed the log's text behind them on OLED).
- The owner's "four tiers" are the existing order of the compile step, not new machinery: pre-compile pass (syntax/dead code) -> library integrity and library rounds (dependencies) -> AutoHealLoop (several attempts, each verified by a real compile) -> rollback and origin cards. No strategy ids or repair memory yet (Phase 3/5).
- Sign-in and telemetry: reviewed, not built. See `auth-telemetry-review.md`.

## v1.8.3 additions

- **Built-in libraries updated** (`assets/libs/libs.zip`, `dexs.zip`, `mod/jbk/build/BuiltInLibraries.java` in `overlay/`):
  AndroidX to the newest versions that build against API 33 (appcompat 1.6.1, material 1.9.0, core 1.10.1, activity 1.7.2,
  fragment 1.6.2, lifecycle 2.6.2, recyclerview 1.3.2, ...), Kotlin stdlib 1.8.22 (+ coroutines 1.7.3), Glide 4.16.0,
  Gson 2.14.0, OkHttp 3.14.9, Okio 1.17.6, Lottie 3.7.2. Firebase / Play services / OneSignal / FAN / YouTube are unchanged.
  Rules in `v1.8.3-verification.md`; tools in `tools/builtin-libs`.
- `BuiltInLibraries` dependency lists only grow: every dependency an entry had in 1.8.2 is kept, the new POM ones are added
  (code in a project may use a class that arrived transitively). An R package is listed only when the library's `res/` has a
  real resource (`androidx.activity`, `androidx.customview.poolingcontainer` are new).
- The extracted copy in `cacheDir/libs` is refreshed when the asset's **size** changes (`Dp.hasFileChanged`): a new libs.zip
  must differ in size from the old one (it does: 24.9 MB vs the 1.8.2 size).
- `Lx` (Android Studio export) build.gradle names the same versions.
- DEX merge (min SDK < 21, or a non-debug build): with min SDK < 21 the project's own DEX is merged right after
  MultiDex so its Application is in classes.dex; `DexMerger` reserves 3x the debug info (index growth 1 -> 3 bytes).
- **Android runtime harness kept in the repository** (`tools/art-harness`): installs an Android 12 ART into the workspace,
  writes fixture projects with the IDE's own project classes and runs the DesignActivity Run-build steps (generate,
  built-in libraries, AAPT2, Kotlin, ECJ + auto-fix, D8, merge, package, sign) with the classes and aapt2 of a built APK.

## v1.8.4 additions

- **Compile platform android-34**: `assets/libs/android.jar.zip` holds the Android 14 (API 34) platform android.jar (class files
  52, like 33). User code, AAPT2 links and D8 desugaring use it unless Build Settings names a custom android.jar. Its size differs
  from the API 33 archive, so `Dp.maybeExtractAndroidJar` extracts it again once. `BuildWarnings.BUNDLED_ANDROID_JAR_API = 34`.
- **aapt2 2.19 (android-14.0.0_r75) for all four ABIs, 16 KB LOAD alignment**: armeabi-v7a and x86 are now built by
  `tools/aapt2-static` like the 64-bit ones (upstream's 32-bit binaries: 4 KB, and can't compile `<macro>` resources, i.e.
  Material 1.11+). The same tag links android-35 resource tables too (checked with a values-v35 attribute).
  `tools/aapt2-static/compare-abis.sh`: all four ABIs give the same resource table (`aapt2 dump resources`) and R.java.
- **ClassFileSanitizer** (`com.sketchware.ai.project`): javac 21 writes `MethodParameters` entries with name_index 0 for
  mandated parameters; D8/R8 3.3.75 throws `NullPointerException ... String.length()` on them (root cause of the old
  concurrent-futures >= 1.2 / error_prone >= 2.48 crash). The sanitizer removes only those attributes (other bytes copied as they
  are). Run on the built-in jars when they are packaged (`tools/builtin-libs/package.py`), and on a library's classes.jar before
  D8 in `LibraryHeal.dex` and `LibraryDownloader._jar2dex`. Not run on local libraries imported before v1.8.4 (D8 reads them
  as library input in `DexCompiler`).
- **Built-in libraries for compileSdk 34**: Material 1.13.0, AppCompat 1.7.1, core 1.13.1, activity 1.9.3, fragment 1.8.9,
  lifecycle 2.8.7, constraintlayout 2.2.2 ... (component-library-inventory.md section 0a). Kotlin stays 1.8.22 (kotlinc 1.7.10
  reads metadata up to 1.8; lifecycle/core/activity at these versions use 1.8 metadata).
- **Material 3 AppTheme** (`ModernProjectDefaults.SETTING_MATERIAL3` = `material3_theme`): new projects "true"; a project
  without the key keeps the MaterialComponents (Bridge) theme. `yq` writes `Theme.Material3.Light.NoActionBar` with the
  project colours in colorPrimary/colorPrimaryVariant/colorSecondary(=accent)/status bar, `viewInflaterClass` =
  `androidx.appcompat.app.AppCompatViewInflater` (plain `<Button>`/`<CheckBox>`... stay AppCompat widgets, as designed),
  FAB style in the accent colour. Toggle in Project Configuration (row `ob_cfg_row_m3`). Design editor: `editor.DesignTheme`
  (loaded in `DesignActivity.onCreate` and after Project Configuration is saved). In M3 projects the palette gives a new
  TextInputLayout `style="?attr/textInputOutlinedStyle"` and a new MaterialButton no style (the theme's button style), never
  an explicit `Widget.Material3.*` style: those need M3 theme attributes and fail to inflate (InflateException) if the project
  is switched back to MaterialComponents. `ItemMaterialButton` draws the fill and corner radius: injected cornerRadius, else a
  pill for Widget.Material3 or for no explicit style in an M3 project, else 4dp.
  Container colours (primaryContainer, secondaryContainer ...) stay Material 3 baseline.
- **Fonts (`project.FontCode`)**: `Jx.formattedCode` rewrites `X.setTypeface(Typeface.createFromAsset(<assets>,"fonts/N.ttf"), S);`
  to `SketchFonts.apply(X, <assets>, "fonts/N.ttf", S);` (same line; X must be a whole name chain, so `(cast) x).view` and
  `a[i].view` lines stay as written, and a match never crosses a line end). `yq` adds `<package>.SketchFonts` when a generated
  activity or a custom Java file of the project calls it (a custom SketchFonts.java in the Java manager wins). The helper caches Typefaces, falls back to
  Typeface.DEFAULT with a log line instead of crashing on a missing asset (also tries the lower-case path the font copy uses),
  reads the font's cmap to build a probe of characters the font itself maps (Latin letters + Telugu worst-case clusters when it
  maps KA + virama), measures the probe's shaped ink with `Paint.getTextBounds` (Minikin/HarfBuzz) and raises the line-spacing
  multiplier and top/bottom padding only where the ink exceeds the font metrics (remembered per view, so applying twice
  doesn't add twice); API 35+: `setUseBoundsForWidth` + `setShiftDrawingOffsetForStartOverhang` by reflection.
  The font manager still stores every font as `<name>.ttf`; Android reads TTF and OTF/CFF by content.
- **AAB export** (minApi26 `AppBundleCompiler`, overlay): local libraries' `jni/` native libraries are added (the APK build
  always did; AABs missed them), duplicate entries and library jar signature files are skipped. Signing of the .aab was
  unchanged in v1.8.4 (kellinwood ZipSigner, SHA-1); v1.8.5 replaced it with BundleSigner (SHA-256).
- **NativeAlignment**: build warning for a 64-bit `.so` (project or local library) whose LOAD alignment is below 16 KB.
- **DEX merge with the bigger libraries**: for min SDK < 21 the project's own D8 output is now several files (the R classes of
  the libraries pass 64K fields; the activities and the Application land in the last one). `project.DexOrder.codeFirst` merges
  the files that hold code right after MultiDex. `DexMerger.WriterSizes` reserves 3x the debug info written per code item
  (D8 shares identical debug_info_items; the merger writes one per use) instead of 3x the section, which overflowed (fixture 907).

## v1.8.5 additions

- **IDE build on AGP 8.12.0 / Gradle 8.13 / compileSdk 34** (target 34, min 26/21 flavours unchanged): KGP 1.9.25 with
  kotlin-stdlib pinned to 1.7.10 (`kotlin.stdlib.default.dependency=false`, language/api 1.7) so kotlinc stays the embedded
  1.7.10; `android.nonTransitiveRClass=false` and `android.nonFinalResIds=false` (the precompiled jars use R ids as
  constants); `buildConfig true`; `--stable-ids public-stable-ids.txt` still pins 7,860 IDs with 0 mismatches. The IDE itself
  uses coreLibraryDesugaring (desugar_jdk_libs_nio 2.1.5). compileSdk 34 changed one API: `MediaMetadataRetriever.release()`
  declares IOException (API 33), caught in AddSoundActivity and ManageCollectionActivity.
- **D8/R8 8.11.18 for user projects + core library desugaring** (`DexCompiler`, overlay; `project.CoreLibDesugaring`): D8 gets
  desugar_jdk_libs_configuration_nio 2.1.5 (`assets/libs/desugar.zip` -> cacheDir/libs/desugar, checked by exact file sizes) and
  a keep-rule consumer. When the project uses any `j$` class, L8 compiles desugar_jdk_libs 2.1.5 for the min SDK, shrunk to
  those classes, cached per min SDK + keep rules + android.jar size (per-run temp names, deleted in `finally`, also when L8
  throws; two concurrent calls for one key both return the same DEX), and writes `bin/dex/desugar_jdk_libs.dex`; it is
  packaged/merged like the project's DEX. If D8 with the configuration or L8 fails (L8 also on OOM) the project is dexed
  again without the configuration, a build warning says so, and `CoreLibDesugaring.reset()` deletes the extracted
  `desugar.zip` copy so the next build extracts the files again (`available()` checks sizes only). Library jars are D8 *library* input: calls into them with
  desugared types get API conversions (checked: `connectTimeout(Duration)` -> `TimeConversions.convert` + original
  signature), so library methods keep working as before. R8 8.x has no public `CompilationFailedException(String, Throwable)`
  (DexCompiler.failure uses initCause). R8 8.11 calls Java 9+ `InputStream.readAllBytes` only on R8/resource-shrinker paths,
  which the IDE doesn't use (shrinking is ProGuard); the D8/L8 path runs on the API 31 harness runtime, which lacks it.
- **bundletool 1.18.3** (minApi26): `AppBundleCompiler` BundleConfig adds `uncompressNativeLibraries { enabled, alignment
  PAGE_ALIGNMENT_16K }`. **AAB signing** = `project.BundleSigner` (apksig, JAR signature only, minSdk 24 for apksig so the
  digests are SHA-256: `SHA-256-Digest` + SHA256withRSA, no v2/v3 block). The test key comes from cacheDir/libs/testkey
  (extracted with the built-in libraries); a user keystore through `KeyStoreFileManager`. APK signing (ReleaseSigner,
  ApkSigner) is untouched.
- **aapt2 2.20 (android-16.0.0_r4)** for all four ABIs (`tools/aapt2-static`: protobuf 25.8 needs abseil-cpp, built with
  protobuf's own CMake; one aconfig header stub; `-llog` from abseil resolved to our static liblog). Same resource table and
  R.java as 2.19 on the 52 library resource sets (all ABIs).
- **Material 3 widgets** (`project.M3Widgets`, decided once per generation in `yq` set-up -> `jq.m3Widgets`): AppCompat on,
  `material3_theme` true, no user `values/styles.xml`, no activity Java file replaced in the Java manager. Per layout file,
  not when the user replaced that layout. The theme stays `Theme.Material3.Light.NoActionBar` with
  `viewInflaterClass` = AppCompatViewInflater (as v1.8.4): MaterialComponentsViewInflater tinted every Button background
  (property colour, image, `setBackground(GradientDrawable)` from code) with the primary colour, and DayNight hid
  user-chosen dark colours, black icons and `setTextColor` results at night (both found by the review, shown in layoutlib).
  Tags written instead: plain Button without own background/style -> `<package>.SketchButton` (MaterialButton whose
  `setBackgroundDrawable` drops the tint for a background set from code); plain CheckBox/RadioButton without injected
  button/style -> MaterialCheckBox/MaterialRadioButton; plain `<Switch>` (no injected style/thumb/track) ->
  `MaterialSwitch` and its Java fields (activity, drawer `_drawer_x`, adapter locals via `M3Widgets.enter/exit`); plain
  EditText (no own background incl. transparent, no injected style/background/layout_/visibility, parent not converted,
  not inside a TextInputLayout) -> outlined `TextInputLayout` (`Widget.Material3.TextInputLayout.OutlinedBox`) around
  `<package>.SketchTextField` (layout_*, alpha, rotation, translation, scale move to the box; SketchTextField.setVisibility
  forwards to the box; a wrap_content field is at least as wide as its hint); `x.setHint(` of such a field ->
  `SketchTextField.hint(x, ` (same line; CharSequence and string-resource overloads). **`scanCode`** reads the project's
  blocks as `opCode(params)`, add-source code, custom variables and more blocks first: any `Switch` type or
  `set/getThumb|Track` keeps every Switch a Switch (`jq.m3Switches`); an EditText/CheckBox/RadioButton id that code moves
  (`addView/removeView...(id`), re-lays out (`id.get/setLayoutParams`, `getParent`) or restyles (`setButtonDrawable/Tint`)
  stays plain (`jq.m3KeepPlain`). Default text colour #000000 -> `?attr/colorOnPrimary` on a SketchButton, else
  `?attr/colorOnSurface` when nothing opaque is behind it; default hint #607D8B -> `?attr/colorOnSurfaceVariant`.
  **"Plain"** = convert empty or equal to the widget's own class name: upstream's palette sets convert "Button",
  "EditText", "Switch" ...
- **Design canvas**: `ItemButton`, `ItemEditText`, `ItemSwitch`, `ItemCheckBox` are source classes now (removed from
  com.besome.sketch-classes.jar; same public fields). `M3Canvas.widgets()` = DesignTheme.widgets (the generator's decision):
  pill filled button, checkbox/radio tint, MaterialSwitch track/thumb, outlined field with label notch, theme text colours;
  `M3Canvas.active()` (the M3 theme) only for the palette's own MaterialButton/TextInputLayout. The canvas doesn't run
  `scanCode` (it would need the logic data): a widget kept plain because of code still draws the M3 look. ItemEditText
  changes its background only when it enters or leaves the outlined look (ViewPane sets image backgrounds before the
  item is attached). TextView.setHintTextColor is final, so ItemEditText applies the hint colour in setVisibility
  (ViewPane ends every property update with setVisibility(VISIBLE)). `DesignTheme.load` reads libraries and the file list
  with its own iC/hC instances, never jC's editor cache. Library Manager has a "Material 3 (M3)" row.
- **Export to Android Studio** (`project.GradleExport`): AGP 8.12.0 / Gradle 8.13 projects (settings.gradle with
  pluginManagement/dependencyResolutionManagement google()+mavenCentral(), plugins DSL, `namespace`, compileSdk 36 (AGP 8.12's
  maximum; targetSdk as in the project), Java version from Build Settings, coreLibraryDesugaring from min SDK 21,
  gradle.properties, gradle-wrapper.properties); the exported manifest loses its package attribute. Widget libraries: Lottie
  and CircleImageView from Maven Central; YouTube player 10.0.5, OTPView, CodeView, PatternLockView, WaveSideBar (no Maven
  artifact at these versions) as `app/libs/<name>.aar` made from assets/libs/libs.zip, with an `R.txt` from
  `GradleExport.textSymbols` when the built-in copy has none (without it AGP makes no R classes for the library:
  NoClassDefFoundError `R$styleable` at run time); SwipeRefreshLayout 1.1.0 with AppCompat; firebase-messaging +
  firebase-iid 19.0.0 (FCM), play-services-auth 19.0.0 (Google sign-in), OneSignal 3.14.0, audience-network-sdk 5.9.0
  (upstream's `Lx.getBuildGradleString` never listed them).
- Rewarded-ad component imports `OnUserEarnedRewardListener` itself (was an auto-fix at every build).

## v1.8.6 additions

- **Material 3 colour roles** (`project.M3Palette`, Material Color Utilities = the `color.utilities` classes of Material
  1.13.0 in `app/libs/material-color-utilities-1.13.0.jar`, Apache-2.0): `SchemeContent` from the project's primary colour,
  light and dark. yq writes `values/m3_colors.xml` (colour resources `sketch_m3_*`; the `m3_` prefix is Material's own) and
  points every M3 role attribute of AppTheme at them (on-primary, primary/secondary/tertiary containers, error, background,
  surface, surface variant, inverse, surface bright/dim/container x5, outline, outline variant). colorPrimary, colorAccent
  (= colorSecondary), colorPrimaryDark (status bar) stay the user's exact colours in both modes; on-primary / on-secondary
  are white when its WCAG contrast on the user colour is >= 3, else tone 10 of that colour's palette. The FAB icon uses
  on-secondary.
- **Dark theme** (`ModernProjectDefaults.SETTING_M3_DARK_THEME`, written true for new projects; absent/false = light, so
  v1.8.4/v1.8.5 Material 3 projects keep their look): AppTheme parent `Theme.Material3.DayNight.NoActionBar`,
  `values-night/m3_colors.xml` (source-list name `m3_colors-night.xml`, mapped by `yq.a(fileName, content)`), PopupOverlay
  `ThemeOverlay.Material3`. **Dynamic colour** (`SETTING_M3_DYNAMIC_COLOR`, default false; `jq.m3DynamicColor` = AppCompat +
  Material 3 + setting + generated styles.xml): Jx writes
  `com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);` before `super.onCreate` in every
  generated activity (no-op below Android 12). Library Manager: "Dark theme" and "Dynamic colour" rows under Material 3,
  shown while it is on (they write the project settings at once, like the Material 3 row).
- **Outlined field**: generated style `AppTheme.TextField` (OutlinedBox with 8dp box corners) replaces the direct OutlinedBox
  style. `SketchTextField.onMeasure` measures the hint as the collapsed label is drawn: bodySmall 12sp, letter spacing
  0.4sp (the field's own is 0.5/16), the box typeface with the phone's bold-text weight adjustment (API 31+), and keeps
  2dp spare. v1.8.5 measured with the field's paint + 1px: a 12sp field with 8dp padding cut the label by 1px
  ("Telugu language title crea…"), reproduced in layoutlib.
- **Buttons with a background colour** (`M3Widgets.tintedButton`): a plain Button whose own background is an opaque colour
  (no image, no injected background/tint/style) is a SketchButton with `app:backgroundTint` (pill kept); a label in the
  editor's default black gets white or #1D1B20 by contrast. `scanCode` keeps a Button plain when code calls
  `<id>.getBackground(` (it would no longer be a ColorDrawable).
- **Design canvas**: `DesignTheme` computes the project's light palette (`DesignTheme.role`); `M3Canvas` reads outline,
  on-surface(-variant), surface-container-highest, on-primary from it (baseline values as fallback); ItemEditText draws
  8dp corners; ItemButton draws tinted pills; `ItemLinearLayout` is a source class now (removed from
  com.besome.sketch-classes.jar, same fields and behaviour) so the screen root shows the app's window background instead
  of the editor grey #EEEEEE in Material 3 projects.
- **Build Settings dialog** (`project_config_layout.xml`): the root clips its children. The ScrollView has no padding, so
  only the parent's clip bounds it; with `clipChildren="false"` scrolled options were drawn under the Cancel / Save row.
- **Repository**: `.gitignore` anchors the Gradle build folders (`/build/`, `/app/build/` ...), so the source packages
  `mod/hey/studios/build` and `mod/jbk/build` are tracked; the public AOSP test key `testkey.keystore` is tracked. A clone
  of the repository builds without the patches zip's `overlay/`.

## v1.8.7 additions

- **New Project screen** (`ui.NewProjectActivity`, `res/layout/ob_new_project.xml`, `ob_np_template.xml`,
  `ob_np_palette.xml`): full screen instead of the project setting dialog for *new* projects (GC.toProjectSettingsActivity;
  the dialog `MyProjectSettingActivity` stays for the settings of existing projects). Obsidian glass cards: names with
  icons, app icon, version code/name (validated: a whole number from 1, "x.y"); template carousel; build engine (one per
  phone, shown, not chosen: `Dp.isD8Enabled()` uses D8 on Android 8+ even for a project that says Dx, and D8 can't run
  below 8, where Dx is the only dexer); colour presets (`project.ThemePresets`: Solar Orange, Material Purple / Blue /
  Green, Rose Wine, Custom; "Generate random" = one random HCT hue: primary tone 40, dark primary tone 30, accent hue+60);
  the five project colours (labelled by what they colour: Accent, Primary, Status bar, Ripple, Controls); a live preview
  (`ui.ThemePreviewView`: light and dark phone with the colours the build writes, M3Palette roles, white toolbar title of
  the generated AppBarOverlay; MaterialComponents look when Material 3 is off); Material 3 / dark theme / dynamic colour
  rows; min SDK (21-34), target SDK (34-37), Java level (the flavour's `BuildSettingsDialogBridge` list without 9/10).
  Result extras `sc_id` + `is_new` as before, so the projects list opens the design editor. A failed create deletes
  what was written for the id (`lC.a(context, id)`).
- **One creation path** (`project.NewProjectSetup.create(Context, scId, Choices)`): the project list entry (lC), `wq.a`,
  the emptied data folder, `ModernProjectDefaults.applyToNewProject`, then the screen's choices over the defaults
  (project settings, build settings: dexer + Java level) and the template. The harness fixtures 914-918 call the same
  method (`Fixtures.createLikeNewProjectScreen`).
- **Templates** (`project.ProjectPresets`): ordinary project content written with the managers' own load/save (`hC`
  files, `eC` views/logic, `kC` resources; data folder): Empty (toolbar); Material 3 Scaffold (ScrollView, two CardViews,
  FAB with a "+" project image `fab_add` from the Java resource `preset_fab_add.png`); Bottom Navigation (content +
  BottomNavigationView, one add-source block in onCreate adding three items and a selection listener); Navigation Drawer
  (activity drawer option + `_drawer_main` with a header in the primary colour and three rows); Tabbed ViewPager
  (TabLayout in the primary colour + ViewPager, one add-source block with a PagerAdapter of three text pages and
  setupWithViewPager). Block code is Java 7 (anonymous classes, final captures, fully qualified names).
- **AppCompat on for new projects** (`ModernProjectDefaults.enableAppCompat`, only when the project has no library file
  yet): Material 3 applies only to AppCompat projects and a project's libraries start "N" (`iC.f()`), so projects created
  on the phone with v1.8.4-v1.8.6 kept the framework theme although `material3_theme` was stored (the owner's NewProject8
  / NewProject9). The harness fixtures had always switched AppCompat on themselves.
- **Canvas VideoView** (`ItemVideoView`, source): measures like android's VideoView with no video
  (`View.getDefaultSize(0, spec)`: wrap_content takes all the space an AT_MOST parent offers) and draws black where no
  background is set. On the phone such a VideoView is a black SurfaceView that pushes later widgets off the screen (the
  owner's "black area below the widgets"); the canvas showed a fixed preview. A ScrollView parent keeps the preview size
  in the canvas (on the phone the empty VideoView is 0 high there).
- **colors.xml**: `colorControlHighlight` keeps its alpha (`yq.highlightColor`); every colour used to be written #RRGGBB,
  so the translucent ripple colour became an opaque flash. Opaque or fully transparent stored values are written as before.
- **Project setting dialog**: the old version dialog reads "x" as "x.0" instead of throwing on a version name without a dot.

## v1.8.8 additions

- **Java editor, v1.8.13**: a `%s` slot of a mapped statement takes `X.getText().toString()` as a nested getText block
  (`@id`, like getVar). In Material 3 projects the edited text is routed (`M3Widgets.routeHints`) before comparing, the
  statement generator routes too, `SketchTextField.hint(field, text)` maps to the setHint block, and add-source text is
  stored un-routed (`JavaToBlocks.useStoredText` + `M3Widgets.unrouteHints`). `BlockWriteBack` compares a block's
  setHint lines routed the way the generated line shows them.
- **Java editor** (`editor.JavaEditorActivity`, `res/layout/ob_java_editor.xml`; design editor build options menu item 6
  "Edit Java code" and the Code tab, request code 241, RESULT_OK when blocks changed): one activity's generated Java in
  sora-editor (JavaLanguage, the user's code editor settings). Opened with the design editor's live project data
  (`jC.b/a/c(sc_id)`: only started from DesignActivity once its project is loaded, so these are the editor's own copies).
  Save runs on a background thread; the editor is locked while it runs.
- **Edit map** (`editor.EditMarkers`, `EditMap`): a second, throw-away generation with comment markers (`/*@SWED`) around
  every event and every block, through the source map's hook (`SourceMapMarkers.wrap` delegates while
  `EditMarkers.active()`) and `Hx` (an event without blocks gets a marker for its place; `""` as before otherwise). The
  map is used only when the marked text minus markers equals the normal generation line for line; an empty event is
  matched against the classic text (with or without its blank line). Otherwise the editor is read-only and says why.
  Activities whose generated file differs after command-block processing are read-only too; an activity replaced by a Java
  Manager file is that file, written as it is.
- **Save** (`editor.JavaToBlocks`, `JavaMembers`, `LineDiff`): members (fields, methods, imports) of the shown and the
  edited text are compared by signature; inside an event a Myers line diff (`LineDiff`, O(D²) trace memory, one hunk over
  everything past D 1500) finds the changed runs, and `LineDiff.slide` moves a pure insert/delete run to the equal
  neighbouring lines inside the event. Each changed run replaces the blocks whose lines it touches with one add-source
  block in the same stack (a change inside if/else/repeat stays in that stack: `BlockInfo(parentId, stack)`); unchanged
  blocks stay. New field → custom variable (type 6, one line; a multi-line declaration is joined, one with comments or
  annotations is refused); changed field → custom variable with the new text (removed + added of one name pair up as a
  type change); removed variable → removed (refused while a block uses it); new import → type 9; `public void
  _name(final T _p, ...)` with boolean/double/String/Map/List/View parameters → MoreBlock with its body in one add-source
  block; removed MoreBlock → removed (refused while called). Lines outside any member (stray text) and everything the
  generator writes by itself (view fields, `initialize()`, onCreate's frame, listener frames, adapters, the class and
  package lines, generated imports) are refused with the line and what generates it. **Proof**: the activity is generated again from the changed data and
  compared with the edited text (imports and members as sets, trimmed non-blank lines, `final` ignored in method headers,
  multi-line fields joined); on any difference or any Throwable the `Snapshot` of the touched data (blocks, variables,
  MoreBlocks) is put back. On success the design editor's working copy is written (`jC.a(sc_id).k()`).
- **Code tab** (`editor.CodeTabFragment`): fourth design editor tab (View / Event / Component / Code): the project's
  activities (→ Java editor) and its own Java/Kotlin files (`data/<sc_id>/files/java`, → code editor); + New class writes
  a Java or Kotlin class or activity in the project's package (names of the project's activities and of generated
  classes, and existing files, refused; an activity still needs its manifest entry from the Java Manager, as before;
  Kotlin only on Android 8+). Waits for the
  design editor's project load (`DesignActivity.isProjectReady`).
- **Library Resolver** (`library.LibraryResolverActivity`, `ob_library_resolver.xml`, `ob_lib_*.xml`): replaces the
  local library manager (design editor drawer, Tools, Compile log → Local libraries). Tabs Required / All / Updates /
  Cache; + Add (Maven coordinate or Gradle line → resolve → plan with what's downloaded, what's built in and what's
  already installed → Install; or Import .aar/.jar/.zip). Listing, search (debounced), enable, delete and dex run in the
  background. Maven Central search: `search.maven.org/solrsearch` (`MavenSearch`).
- **Resolver** (`library.MavenResolver`): repositories Google Maven, Maven Central, JitPack only. Reads POMs like Gradle:
  compile + runtime scopes, optional skipped, parents, `${...}` properties, dependencyManagement and BOM imports,
  exclusions (also `*:*`), version ranges (highest published version in range from `maven-metadata.xml`), conflicts →
  highest version. What `BuiltInCoordinates` maps to a `BuiltInLibraries` constant (generated from tools/builtin-libs
  coords.py, plus manual entries) is never downloaded. Cache `/.sketchware/libs/cache/maven/<group path>/<artifact>/<version>`.
  Downloads and installs nothing but POM/AAR/JAR/metadata files; no Gradle scripts or plugins.
- **Install** (`library.LibraryInstaller`): `<artifact>_V_<version>` folders in `local_libs` as the old downloader named
  them, staged in `.<name>.installing` and renamed; D8 on Android 8+, Dx (class files up to Java 7) below; `config`,
  `source.json`, and `needs.json` (`library.LibraryNeeds`: coordinate, required local libraries, built-in libraries).
  Enabling replaces another version of the same artifact only when both groups are known and equal; an unreadable
  `local_library` list is never overwritten. v1.8.13: one version per group:artifact and the highest wins (`enable`):
  a lower version is never switched on over a higher one that is on (`Outcome.keptNewer`); a root lower than the
  version on switches nothing on (`rootKeptBack`); a needed library reuses the higher version on in the project
  (`highestOn`, read once per install) instead of downloading the lower one; an unreadable list sets `notSwitchedOn`.
- **Resolver reports** (v1.8.13, `LibraryResolverActivity.Report`): a finished task is a Toast (`quiet`), a Toast plus a
  notice line above the list with Copy log and × (`warning`), or the only dialog (`fatal`: the library itself not
  installed). Installed libraries get a green "✓ Installed" on their card for the session. User-facing names come from
  `readableName` / `pretty` (coordinate made readable), not folder names.
- **Build**: `Dp` adds the built-in libraries every enabled local library's `needs.json` names
  (`LibraryNeeds.addBuiltIns`), so e.g. Retrofit gets the built-in OkHttp and Gson with no block using them;
  `GradleExport` writes those libraries' Maven coordinates (`LibraryNeeds.exportCoordinates`).
- **Export to Android Studio and local libraries** (`GradleExport.localLibraries`, `writeLocalLibraries`,
  `blankUnresolvableImports`; called by ExportProjectActivity after `writeWidgetLibraries`): the project's enabled local
  libraries were never exported (upstream gap). A library whose coordinate is recorded (needs.json, else source.json; a
  valid `g:a:v` only, nothing guessed from a name) becomes an `implementation` line in `extraDependencies`; JitPack is
  added to settings.gradle when such a library's source URL is jitpack.io. Others go to `app/libs`: classes.jar as
  `local-<name>.jar`, or `local-<name>.aar` (classes.jar, res, assets, jni, proguard.txt, the folder's manifest or one
  with the `config` package, R.txt from the folder or `textSymbols`) when the folder has Android parts. A local library's
  "packageName" is its R package (ManageLocalLibrary: `import <pkg>.*` in generated code, an R class in the on-phone
  build): the Library Resolver writes it only from an .aar manifest; in an export, `import <pkg>.*;` for a package with no
  class in the library and no res/ is left as an empty line.
- **Android ICU regex**: an unescaped `}` in a `java.util.regex.Pattern` is a syntax error on Android (ICU) and fine on the
  desktop JVM: `MavenResolver`'s property pattern threw in `<clinit>` on ART. Known-errors KE-ICU-REGEX.

## Existing repair engine (v1.7.x)

- `AutoHeal` (problem parser, located edits, never changes line count, rollback), `JavaTokens` (dead-code detection after JLS 14.22), `AutoHealLoop` (attempts, per-site escalation keep -> remove, report), `ClassIndex` (imports from the classpath), `LibraryAutoResolve`/`LibraryHeal` (enable installed library, known Maven coordinates, sub-dependency linking, D8), `BuildWarnings`.
- Order in `Dp.compileJavaCode()`: origin session -> pre-compile dead-code pass -> ECJ -> library rounds -> AutoHealLoop -> report.

## Not built

Phase 3 Build Doctor as a strategy-ID pipeline with result fingerprints, Phase 4 dashboard screen, Phase 5 repair history/memory on disk, Phase 6 Local Library Manager status display, Phase 7 Developer Tools controller, Project Brain, Git/GitHub, extensions, Android Studio import/export. A tamper/licence "security kit" is out of scope and will not be added.

## Rules carried from the directives

- Offline first; no cloud model in the build. Network only for Maven artifacts from Google Maven, Maven Central, JitPack. No scripts, Gradle hooks or unsigned plugins are ever downloaded or executed.
- No GitHub tokens/secrets in the APK, resources or project files.
- Permissions are never added silently.
- Project #639 is a regression fixture; no project id, block name or Bluetooth class is special-cased.
- A release note says "tested" only for what ran, and where (see compatibility-matrix.md).

## Repository notes

- Until v1.8.5, `.gitignore` had an unanchored `build/` rule, so source folders named `build` were not tracked
  (`mod/hey/studios/build/`, `mod/jbk/build/` and the flavours' copies) and their changed files shipped as whole files in
  the patches zip's `overlay/`. Since v1.8.6 the rule is anchored and those folders are tracked; a round trip from the
  upstream zip runs `git clean -fdX` after the base commit so the patches can add them. Still: never put new code in a
  package folder named `build`.

## v1.8.9 additions

- `com.sketchware.ai.editor.StatementBlocks`: maps one statement line (outside braces and comments, not the body of a
  braceless if/else/loop) to the block that writes it (setText, setHint, setVisible, setEnable, setClickable, setChecked,
  requestFocus, doToast, setTitle, setVarString/Int/Boolean, increaseInt/decreaseInt, intentSetScreen, intentPutExtra,
  startActivity, finishActivity only as the last statement); accepted only when `Fx` writes exactly that line. Used by
  `JavaToBlocks.chainFor` for event bodies and new MoreBlocks; everything else stays add-source.
- `com.sketchware.ai.library.LibraryImports` (called from `Jx.formattedCode`): `import pkg.*;` for local-library packages
  whose public classes the code uses by simple name; never when a used name would become ambiguous against the activity's
  other on-demand imports (android.jar, built-in libraries under `EXTRACTED_BUILT_IN_LIBRARIES_PATH`, java.lang,
  androidx.annotation, local libraries); nothing when one of those packages can't be indexed or android.jar isn't unpacked
  (the generator never unpacks it). The Java editor treats these lines as generated.
- `LibraryInfo` (offline (i)): pom.xml kept in the library folder at install (`LibraryInstaller.keepPom`), else the
  resolver cache; AndroidManifest.xml permissions; classes.jar packages.
- `MavenSearch.solrQuery`/`normalize`: no raw colon reaches search.maven.org; coordinate fallback to Google Maven / JitPack
  maven-metadata.xml. Live search in `LibraryResolverActivity` (debounced, newest query wins).
- `Ox`: MaterialCardView (`Widget.Material3.CardView.Elevated`, unless the inject sets a style) and MaterialToolbar in
  Material 3 layouts (`M3Widgets.materialCard`, `MATERIAL_TOOLBAR`).
- `ModernProjectDefaults.convertToModern` (Project Configuration → Convert to Material 3 & Modern Stack): AppCompat on in
  the saved data and, separately, in the unsaved working copy; D8 + Java 11; the dialog's fields set target 37, min 21,
  Material 3. Runs off the UI thread.
- Release packaging: the APK is one file in `binaries:apk/` stored with Git LFS (not shrunk: the embedded Kotlin compiler
  is reflective; see v1.8.9-verification.md section 5).
