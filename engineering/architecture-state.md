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

## v1.9.0 additions

- **Java editor registers variables** (`editor.AutoVariables`, `JavaToBlocks.planAutoVariables`): among the statements
  that become blocks (the changed runs' code and new MoreBlocks' bodies, `ownStatements`), a local declaration
  `[final] T name [= value];` becomes a project variable: String / double / boolean a String / Number / Boolean variable
  whose set block writes `name = value;` (a decimal number as `2.5d`, the way the Number set block writes it); any other
  type (int, long, Intent, a library class ...) a custom variable (type 6) `private T name` with the assignment kept as
  code. An int is not made a Number (double): `String.valueOf(count)` would print "1.0" and `list.get(count)` would no
  longer compile. An assignment `name = value;` to a name declared nowhere registers a variable of the type its value
  shows (`AutoVariables.infer`: string literal / getText / String.valueOf / concatenation with a literal / toString,
  trim ...; number / arithmetic of numbers and Number variables / Double.parseDouble; true/false / comparison / ! /
  equals, isEmpty, contains, isChecked ...; a variable of that type); two values of different types: not registered.
  A local becomes a field only when it can't change what the code does: declared exactly once in the class
  (`AutoVariables.declarations`, also parameters, for-each, catch, lambdas), not taken by a field, method, inner class,
  variable, list or MoreBlock, not starting with `_`, not used before its declaration in its block, not used by a
  listener or lambda opened after it in its block (`hazard`: the field would hold a later value), not in a MoreBlock
  that calls itself. Otherwise it stays code and the save says why. Registrations are planned before any line becomes
  a block (`targetsFor` then `planAutoVariables` then `applyTargets`), so later lines in the same save use the variable
  blocks. Proof: the activity generated again must equal the edited text with each converted declaration rewritten as
  its assignment (or removed) and each new field after the class line (`expectedText`). Variables are appended to
  `eC.e` after MoreBlocks; the Snapshot covers them.
- **Logic editor source sheet** (`editor.EventCodeSheet`, `EventCode`, `ui.M3CodeScheme`): the "</>" sheet showed the
  TextMate Dracula theme with sora's `JavaLanguage`; a TextMate scheme only colours TextMate tokens, so JavaLanguage's
  keywords, names and literals kept `EditorColorScheme`'s light-theme defaults (#2196F3, #666666 on the dark sheet).
  `M3CodeScheme` sets every colour JavaLanguage uses on M3 dark surface #141218, each at least 5:1 (WCAG AA 4.5:1).
  **Edit** stores the canvas's blocks (`LogicEditorActivity.E()`), opens the activity in a `JavaEditSession` on the
  design editor's live data and shows the event's body (its lines without the generator's indentation; empty events
  with an empty body); **Apply** puts the edited body back in place of the event's lines and saves through
  `JavaEditSession.save` (statements → blocks, variables registered, the rest add-source, proof), writes the working
  copy (`jC.a(sc_id).k()`) and opens the logic editor again on the same event (a fresh intent without SINGLE_TOP and
  focus extras, `applied_report` shown after the load), so the old canvas is never saved over the new blocks; a
  refused apply keeps the sheet open with the problems on the sheet's lines.
- **Build cache for Run builds** (Build Settings → Build cache, `build_cache`, default on; never for exports, AABs,
  ProGuard or StringFog):
  - `project.RClassCache`: aapt2 writes one R.java per package of the project and of every library with resources,
    each with all ids (non-transitive R off): 28 files of 2.5 MB in a Material 3 project, compiled by ECJ and dexed by
    D8 at every build. Key = SHA-1 of every R.java (path, size, content) + Java level + min SDK + IDE version name;
    entry `cacheDir/build-cache/r/<sc_id>/<key>/{classes,dex}` (one entry per project). Hit: ECJ gets the kept classes on
    its classpath and no R sources; D8 gets the project's own classes; the kept R DEX is added as `bin/dex/r-classes*.dex`
    (merged or packaged like the project's DEX; `DexOrder` keeps code files first). Miss: compiled together as before,
    the R classes kept after a successful compile, dexed once separately. Fixture 926 on ART: Java 125 s → 4 s, DEX
    190 s → 5 s on the second build; every class of the cold, warm and cache-off APKs equal (dexdump, 9,545 classes),
    resources.arsc and all other entries byte-identical.
  - `project.ResourceCache`: a local library's compiled res archive kept by the folder's file listing (path, size,
    time) + aapt2 identity, with the PNG notes of its compile; the project's generated resources (written fresh into
    mysc each build) as one `.flat` per file keyed by content, changed files compiled one by one (more than 8 or all:
    the folder as before, then kept); the archive written from the flats (stored entries, sorted, 1980 times like
    aapt2's). aapt2 compile output is deterministic (checked: two runs byte-identical, a single-file compile equals the
    archive entry).
  - Not cached: the aapt2 link (it writes the R.java files the R key is computed from) and packaging/signing.
- **Target device** (`project.DeviceTargets`, Project Configuration → Target device, `target_device`): Phone (default),
  Foldable, Tablet, Wear OS, Android TV. Manifest only for Wear OS (`uses-feature android.hardware.type.watch`, meta-data
  `com.google.android.wearable.standalone`) and Android TV (`android.software.leanback`, `android.hardware.touchscreen`,
  `android.hardware.screen.portrait` not required; `LEANBACK_LAUNCHER` on the launcher activity; `android:banner` =
  app icon), never added twice (`hasChild`), never added for other targets. Fixtures 927/928: aapt2 badging shows the
  features and `leanback-launchable-activity`.
- **Design canvas viewports** (`editor.ViewportViewEditor`, `res/layout/fr_graphic_editor.xml`): a subclass of the
  precompiled `ViewEditor` whose layout method `a()` is upstream's (read from its bytecode) with the screen size as a
  parameter; Phone calls upstream's own `a()`. Foldable 700×840 dp, tablet 1280×800 dp, Wear OS 227×227 dp round (frame,
  strips and pane clipped to one circle), TV 960×540 dp, each scaled into the same space. Design editor header → Device
  (menu id 0x5D1E, not a resource); the canvas opens at the project's target device. Material 3 window size class shown
  with the size (Compact < 600 dp, Medium < 840 dp, Expanded).
- **Material Symbols** (`ui.SymbolIcons`, `assets/icons/material_symbols.zip`, Apache-2.0, npm
  `@material-symbols/svg-400` 0.48.2): 3,937 icons × Outlined / Rounded / Sharp, one SVG path each (viewBox 0 -960 960
  960; only M L H V Q T Z used); the Image Manager's icon screen (`ImportIconActivity`) has a row 2014 icons / Outlined
  / Rounded / Sharp; a symbol is drawn into a 48×48 PNG (the 2014 icons' size, 24 dp at xhdpi) in the chosen colour
  (black, grey #757575, white) and added like any icon (name `ms_<icon>_<style>_<colour>`).
- **Toolchain**: projects compile against the bundled **android-37.0** android.jar (Android 17; class files 52 like
  34; ECJ 3.26 reads it; aapt2 2.20 links against its resource table); `BuildWarnings.BUNDLED_ANDROID_JAR_API` 37.
  `tools/builtin-libs/android_jar.py` writes `assets/libs/android.jar.zip`: the platform jar with the public API Android
  17's SDK dropped that projects call from add-source blocks copied back from android-34 (`android.hardware.fingerprint`
  FingerprintManager + 3 nested classes, `Context.FINGERPRINT_SERVICE` added to Context.class as a String constant); every
  other entry byte-identical. Public members of android-34 missing in 37 (72 lines, by a class-file diff): the 6 classes
  above and MediaDrm's two annotation types; the rest moved to a supertype (SplittableRandom, Throwable.getCause,
  equals/hashCode, Layout) and still compile, except `Notification.WearableExtender.get/setBackground`,
  `Thread.suspend/resume/destroy/countStackFrames/stop(Throwable)`, `Deflater/Inflater/ZipFile.finalize`,
  `CarrierConfigManager.KEY_HIDE_ENABLE_2G`, `WifiInfo.makeCopy`, ICU calendar internals (not restored). Found by
  LoadAll: androidx core's `FingerprintManagerCompat$1` no longer linked against the dexed android-37 jar (fixture 929
  `fingerprint` builds the typical add-source fingerprint code).
  D8/R8/L8 **8.13.25** (the newest 8.x; 9.x dropped `D8Command.Builder.setDesugaredLibraryKeepRuleConsumer`, which core
  library desugaring uses: moving to TraceReferences is its own change). linkcheck 0 risky calls; api_level against
  android-26 the same members as v1.8.13. aapt2 stays 2.20 (android-16.0.0_r4): AOSP has android-17.0.0_r1, a rebuild
  for four ABIs was not done in this release.
- **IDE build on Gradle 9.8.2 / AGP 9.5.0 / Kotlin Gradle plugin 2.4.21** (owner, 1 Nov 2026: `./gradlew :wrapper
  --gradle-version=9.8.2`; compileSdk, target and the min 26/21 flavours unchanged). Gradle 9.6+ removed an internal API
  every AGP 8.x uses ("relies on InternalProblems"), and Gradle 9.8.2 is tested with AGP 9.0-9.5 and KGP 2.0-2.4; AGP 9.5.0
  needs KGP 2.2.10+. Changes: AGP 9's built-in Kotlin compiles the app's Kotlin files (no `kotlin-android` plugin; options in
  `kotlin { compilerOptions }`); `Project.exec` is gone in Gradle 9 (git through `providers.exec`); `packaging` instead of
  `packagingOptions`; `=` assignments where Gradle 10 drops the space syntax. Kotlin 2.4 compiles language/API version 2.0 at
  the oldest (1.7 is refused): the IDE keeps kotlin-stdlib 1.7.x at run time (`kotlin.stdlib.default.dependency=false` is
  honoured by built-in Kotlin; resolved 1.7.20 as before), and `tools/linkcheck/kotlin_stdlib.py` checks that every kotlin.*
  member the APK's code calls is defined in the APK (763 references, 0 unresolved, same count as the AGP 8.12 build).
  `android.nonFinalResIds=false` is deprecated in AGP 9 and removed in AGP 10: the precompiled jars and switch statements on
  R ids need final ids, so AGP 10 needs that code changed first. AGP 9's D8 adds empty synthetic stubs for framework classes
  newer than min SDK 26 that libraries reference (594 more `android/*` classes than the AGP 8.12 build; the phone's own
  class wins where it exists) and its own desugaring helpers (DesugarVarHandle, RecordTag, renamed `j$` classes); small Java
  resources are now stored deflated. Resources, assets, native libraries and the manifest are otherwise identical.
- **Export to Android Studio on Gradle 9.8.2 / AGP 9.5.0 / KGP 2.4.21 / compileSdk 37** (owner, 1 Nov 2026, "API 37
  directive"): `GradleExport` constants (`ANDROID_GRADLE_PLUGIN`, `GRADLE`, `KOTLIN_GRADLE_PLUGIN`, `COMPILE_SDK`,
  `ANDROID_STUDIO`); the top-level build.gradle puts `org.jetbrains.kotlin.android` 2.4.21 on the classpath (`apply
  false`) so AGP 9's built-in Kotlin compiles the project's Kotlin files with it (AGP 8.12 exports had no Kotlin plugin:
  a .kt file was left out); app/build.gradle with `=` assignments. targetSdk and minSdk stay the project's, so the export
  builds the app the phone builds (new projects target 37). AGP 9.5 needs Gradle 9.6+, JDK 17 and Android Studio Rabbit 2
  (2026.2.2) or newer (developer.android.com, Nov 2026); the Export screen says so under the card's title
  (`ExportProjectActivity.addExportStackNotice`: "Future-Proof Modern Pipeline" pill + versions; render
  `V190ExportNoticeTest`). The owner's text named Android Studio 2024.2 (Ladybug), which only opens AGP up to 8.7.
  Fixtures 904 (Kotlin), 908, 911, 920, 922 exported and built with Gradle 9.8.2.
- **IDE compileSdk 37, targetSdk 35** (same directive; target 35 at the owner's next directive the same day): the IDE
  compiles against android-37.0 without source changes. Target 35 with `android:windowOptOutEdgeToEdgeEnforcement` in
  AppTheme (every activity theme but one inherits it) and the MaterialComponents Bridge theme (Android 15 would draw
  every screen edge to edge; the opt-out holds on Android 15 and, for target 35, on 16+) and
  `<application android:enableOnBackInvokedCallback="false">` (onBackPressed stays the back path everywhere). Target-35
  OpenJDK changes checked in the APK's code: no `Arrays.asList(...).toArray()` cast to String[] (one jose4j ArrayList
  cast, unchanged behaviour); no Configuration screen dp use; no foreground services. Not targeting 36/37: for target 36+, Android 16+ draws every screen edge to edge with no opt-out
  (`windowOptOutEdgeToEdgeEnforcement` disabled) and no longer calls `onBackPressed` unless
  `android:enableOnBackInvokedCallback="false"` (a temporary opt-out): 26 source activities and ~30 precompiled classes
  override it, the design and logic editors save the project there; target 37 also makes changing a `static final`
  field by reflection throw, requires `System.load` files to be read-only and drops the large-screen orientation opt-out
  (developer.android.com behavior-changes-16 / -17). None of it can be run here (no device, no emulator).

## v1.9.1 additions

Owner's phone report, 1-2 Nov 2026 (18 screenshots, 2 recordings: project #663 "Package APK" failure and an IDE
restart during "Building APK...", three library warnings, an empty boolean slot, add source for operators / math / list /
file / Toast lines, the Export screen's Local Path row under the Send button).

- **`project.JavaResources`** (Dp.buildApk): sdklib 25.3.0's ApkBuilder throws DuplicateFileException for a Java-resource
  path it already packaged (resources.ap_, the DEX, native folders, an earlier jar; META-INF is skipped by
  SignedJarBuilder.writeZip). Before packaging, each jar's entries are checked with ApkBuilder's own static filters; a jar
  holding an already-packaged path is replaced by a copy in `bin/java-resources/` without those entries (first copy
  wins; the copy is deleted in `finally`). The build warning names up to 8 files and their two owners
  (`ownerOf`: the library folder). Exceptions from ApkBuilder now carry their causes in the By message.
- **`LibraryIntegrity.hasClasses`**: a library jar with no `.class` (AndroidX -ktx shells of 2025-2026) needs no DEX:
  no warning, no repair.
- **`BuildMap.Record.cause`**: the first two non-blank lines of a failed build's log, printed as `failed with:` under
  the Build map (the owner's screenshots show the log's end; the exception sat at the top).
- **`editor.StatementBlocks` templates**: `REPORTERS` (value blocks with their type b/d/s) and `STATEMENTS` (one-line
  statement blocks). A template is made per opcode by generating the block with marker values (`zqv<i>qz` getVar blocks in
  %s/%d/%b slots, the marker as the value of a dropdown slot) — a value block inside a setVarString/Int/Boolean carrier
  `zqcarrierqz = …;` — and finding the markers in the code: pieces of literal text with holes in the order the generator
  writes the slots (fileutilwrite writes slot 2 first). Slot kinds come from the spec (palette, English, or `lq.a`);
  templates whose code isn't one line, contains `{`, or has a marker missing / twice are not used. Matching splits the
  text at the pieces at bracket depth 0 outside string / char literals (up to 6 splits per template, a budget of 4,000
  split steps and depth 8 per statement); each slot text becomes a literal, a variable block, a list / map / view /
  component name of the right kind (`VIEW_KINDS`, `COMPONENT_KINDS`, lists from eC.f) or, recursively, a value block —
  also without one pair of outer brackets (the generator brackets an operator inside another: `(a + 7) + bb`). The whole
  statement is then generated and must equal the typed one. true / false are the palette's true / false blocks (a bare
  literal showed an empty slot); `literalBooleansToBlocks` repairs blocks saved that way when LogicEditorActivity loads an
  event. **Storage permissions**: a.a.a.yq adds READ (and WRITE) EXTERNAL_STORAGE plus an onCreate request for any
  fileutil* / bitmap-file block, so these are only used when the activity already has a block of that kind
  (`useExistingBlocks`); otherwise the line stays code and the save says so (`heldForPermission`). Other side-effect
  opcodes (Gson, Glide, webView, intentSetAction CALL) are not in the lists.
- **Toast rewrite** (`StatementBlocks.equivalent`, `JavaToBlocks.planEquivalents`): `[android.widget.]Toast.makeText(
  getApplicationContext() | getBaseContext() | [X.]this, text, [android.widget.]Toast.LENGTH_SHORT).show();` is stored
  as the Toast block when the Toast block takes `text` (a String: literal, String variable or String value block); the
  proof compares with the rewritten text (`expectedText` whenever `autoRewrites` is non-empty) and the save notes it.
  `SketchwareUtil.showMessage(Context, String)` is `Toast.makeText(_context, _s, Toast.LENGTH_SHORT).show()` (Lx).
- **`ui.ExportCards`** (ExportProjectActivity): `addNotice` (pill + subtitle under a card's title, 12 dp right padding,
  the card's button / loading / result re-anchored below it with copied RelativeLayout params) for Sign APK, Android
  Studio and App Bundle; `fitResultRows` (result rows wrap their text, 24 dp minimum, 10 dp above Send). The App Bundle
  button had taken the Android Studio button's LayoutParams object (its margins moved that button too): own params now.

## v1.9.4 additions

- **Permission blocks** (`project.PermissionCode`, owner's directive 2 Nov 2026): Component palette section
  "Permissions": `requestAutoPermission %m.permissionGroup`, `requestCustomPermission %s` (a permission name),
  `hasAutoPermission` (b), `getPermissionStatus` (s: GRANTED / DENIED / NEVER_ASK_AGAIN); menu `permissionGroup`
  (ExtraMenuBean): STORAGE, CAMERA, MICROPHONE, LOCATION, BLUETOOTH, NOTIFICATIONS, PHONE, SMS. Block code uses
  `{host}`, which `ExtraBlockCode` replaces with `<Activity>.this` (Fx.c: the class the block is in) for these blocks
  and webEnableFileUpload only, so the blocks work inside listeners. Generated helper `SketchPermission` (Java 7, min SDK 19, `PermissionCode.helperClass`, written by
  `yq.addCodeHelper` when a generated or custom Java file calls `SketchPermission.`): `permissions(group, sdk, target)`
  decides at run time (STORAGE: READ_MEDIA_IMAGES/VIDEO/AUDIO + VISUAL_USER_SELECTED for API 34+/target 34+, the
  three for 33+/33+, READ (+WRITE up to API 29 or target < 30) otherwise; BLUETOOTH: CONNECT + SCAN for 31+/31+, else
  FINE_LOCATION; NOTIFICATIONS: POST_NOTIFICATIONS for 33+/33+, else nothing), `has` (LOCATION: fine or coarse;
  STORAGE 34+: selected photos count), `status` (NEVER_ASK_AGAIN = asked before + no rationale), `request` (request
  codes 7401-7408, a permission name 7500; one dialog at a time: later requests queue for up to 60 s; nothing to ask →
  the answer is posted to the activity's onRequestPermissionsResult, by reflection below API 23), `onResult`.
- **Permission events**: activity events `onPermissionGranted %s.permissionGroup` and `onPermissionDenied
  %s.permissionGroup %b.neverAskAgain` (EventsHandler; code `public void _onPermissionGranted(final String
  _permissionGroup)`). Jx writes `onRequestPermissionsResult` when PermissionManager needs it (File blocks, requestCode
  1000 → initializeLogic, unchanged) or the activity uses the blocks/events (`PermissionCode.usedBy`), with
  `PermissionCode.resultCode`: `SketchPermission.onResult` then the events the activity has; every activity of a
  project that uses a permission block anywhere gets it (`jq.permissionBlocksUsed`), so a block in a Fragment releases
  the helper's one-dialog lock through its activity. An answer with no permissions (interrupted dialog) is never
  reported as "never ask again". Without AppCompat the
  `super` call is guarded with `SDK_INT >= 23` (android.app.Activity has no such method below 23). Fragments: a block
  asks through the fragment's activity; the events are activity events.
- **Manifest** (`PermissionCode.manifestEntries` / `writeManifest`, from Ix after every other permission writer
  (built-ins, Permission manager, components, manifest injections: their names are read back from the manifest text)): every
  permission a group can need, `maxSdkVersion` where newer Android ignores it (STORAGE: READ 32 for target 33+, WRITE 29
  for target 30+; BLUETOOTH target 31+: BLUETOOTH / ADMIN / FINE_LOCATION 30, SCAN `neverForLocation`); a permission
  two groups need keeps the wider range; a name already written is not written twice (a duplicate fails Gradle's
  manifest merger) and keeps its own attributes. The File blocks' READ/WRITE stay as upstream writes them (no
  maxSdkVersion: their onCreate check would otherwise ask again at every start on Android 11-12, review finding). The
  web block "allow downloads" adds WRITE_EXTERNAL_STORAGE maxSdkVersion 22 when min SDK < 23 (the download manager of
  Android 4.4-5.1 needs it even for the app's own folder; AOSP DownloadProvider read). The added names go to the build
  log's notes (`jq.permissionNote`, noted in `BuildWarnings.begin`, which runs after generation).
- **Web blocks** (`project.WebCode`): View palette under WebView: `webAllowDownloads %m.webview %s %b %s` (folder,
  notification on completion, MIME filter), `webSetDesktopMode %b`, `webClearCookiesAndCache`, `webEnableFileUpload`
  ({host}); WebView events `onDownloadStart %s.url %s.mimeType %d.contentLength` and `onDownloadComplete %s.url
  %s.fileName %b.success` through the listener `webDownloadListener` (oq lists, Lx event + listener code
  `SketchWeb.setDownloadEvents(webview, new SketchWeb.DownloadEvents() {…})`). Hx emits a WebView's listeners even
  without events (upstream): the download listener is excluded from that, so WebView projects without the events
  generate what they did. Helper `SketchWeb`: one DownloadListener per WebView (WeakHashMap state); DownloadManager with
  cookies + user agent, Downloads/<folder> on Android 10+ or with WRITE permission, else the app's own Downloads folder
  (no permission added; INTERNET for the block, as webViewLoadUrl); completion through one receiver
  (ACTION_DOWNLOAD_COMPLETE, RECEIVER_EXPORTED on 33+); desktop mode = Chrome version from the default user agent in a
  desktop UA + wide view port, reload; file upload = a WebChromeClient whose onShowFileChooser opens the picker from an
  android.app.Fragment of its own (`SketchWeb.Chooser`), so the activity's onActivityResult is not involved. No block
  ignores SSL errors.
- **Safe int converter** (`AutoVariables.intUnsafe`, owner's decision 2 Nov 2026): an `int` local the Java editor
  registers becomes a Number variable (double field) only when every use is `n++` / `n--` / `n += k` / `n -= k` (k a
  whole number), a one-statement self-assignment of + and - only (no text, *, /, %, index, ternary, comparison,
  assignment or bit operator), a comparison operand, or under a primitive cast (`(int) n`,
  `String.valueOf((long)(n))`); shifts are read as shifts, not comparisons. Any other use keeps it a custom int and the
  save names the line. Left: + and - past 2^31 (int wraps, double doesn't).

## v1.9.5 additions

- Review findings applied before release: DayNight only where M3Widgets is on (replaced activity file / layout keeps
  literal colours: light theme); an app or activity theme from the manifest editor that isn't AppTheme* turns the
  Material widgets off (`AndroidManifestInjector.usesForeignTheme`); the default back code is not written when an
  add-source block declares `void onBackPressed()` (`Jx.declaresOnBackPressed`); every ProgressBar stays framework when
  the project's code uses RemoteViews or sets a progress / indeterminate drawable anywhere; download entries are stored
  under the receiver's lock; quoted / ISO-8859-1 `filename*=`; the server's own MIME type also passes the filter.
- **Material 3 only** (owner's directive 3 Nov "Deprecate Material 1 & 2"): `ModernProjectDefaults.material3()` is
  always true, so every project with AppCompat + Design gets the Material 3 AppTheme, M3Palette colour roles and the
  M3Widgets tags; the MaterialComponents (Bridge) theme is no longer generated whatever `material3_theme` says.
  `m3DarkTheme()` is true unless the Dark theme switch stored "false" (DayNight by default, also for projects that never
  stored it). Projects without AppCompat keep the framework theme: Material 3 needs those libraries (Project Configuration
  → Convert). The New Project screen hides its Material 3 row; the Library Manager's Material 3 row shows ON and is not
  clickable while AppCompat is on (OFF: tap offers to turn AppCompat and Design on).
- **ProgressBar → Material progress indicators** (`M3Widgets.progressIndicator`, Ox): horizontal style →
  LinearProgressIndicator (stored indeterminate, max, progress), circle styles → CircularProgressIndicator with
  `indeterminate="true"` (the framework circle styles always spin); no `style` attribute (theme defaults). Both extend
  ProgressBar, so the Java field type and ProgressBar blocks are unchanged. Kept a framework ProgressBar when converted,
  with an injected style / drawable / tint, or when code sets or reads its drawables, tints, background or secondary
  progress (`scanCode` → `touchesProgressDrawables`; Material throws on a framework progress drawable).
- **Material 3 button styles** (`M3Widgets.styledButton`): a Button whose Custom attributes hold
  `style="@style/Widget.Material3.Button…"` (TonalButton, OutlinedButton, ElevatedButton, TextButton …) is generated as
  SketchButton with that style, and the editor's default black label colour is not written over the style's.
- **Field types**: generated fields keep the type the blocks are written for (Button, CardView, ProgressBar): the Material
  classes extend them, so every call compiles and the object is the Material class at run time; MaterialSwitch (not a
  Switch subclass) has been typed since v1.8.5.
- **Permission category**: the drawer's 10th built-in category "Permission" (`PermissionCode.PALETTE_ID` 100, teal
  #009688; custom palettes count from 9), rows moved out of Component. The `%m.permissionGroup` slot opens
  `ExtraMenuBean.permissionPicker`: groups first (with one line each), then every `android.permission.*` of the phone's
  Manifest.permission, filtered as you type; a typed name of the form `[A-Za-z0-9_.]+` that isn't listed is offered as a
  custom permission (it needs a dot; Settings-only and system permissions such as SYSTEM_ALERT_WINDOW,
  MANAGE_EXTERNAL_STORAGE, BIND_* are left out). Only names whose meaning moved between Android versions are aliased to a
  group (`PermissionCode.groupOf` for the manifest, `SketchPermission.group` at run time): READ/WRITE_EXTERNAL_STORAGE →
  STORAGE, BLUETOOTH / ADMIN / CONNECT / SCAN → BLUETOOTH, ACCESS_FINE_LOCATION → LOCATION (Android 12+ ignores FINE
  without COARSE), POST_NOTIFICATIONS, CAMERA, RECORD_AUDIO. Other names (SEND_SMS, READ_PHONE_STATE,
  ACCESS_COARSE_LOCATION ...) stay one permission (review: widening would declare and ask for more than picked);
  READ_MEDIA_* names mean READ_EXTERNAL_STORAGE below Android 13 (declared with maxSdkVersion 32 for target 33+).
- **WebView**: the generated initializer turns on DOM storage, the database, JavaScript windows (window.open loads in the
  same view) and mixed content COMPATIBILITY mode on 21+ (Chrome's behaviour; ALWAYS_ALLOW would let an http script
  run on an https page). Activities with a WebView get a default `onBackPressed` (Hx default lifecycle code, so an
  onBackPressed event of the user's own wins): open drawer closes → each WebView that can go back goes back →
  `super.onBackPressed()`; generated apps keep `enableOnBackInvokedCallback="false"` (v1.9.0) so it is called on
  Android 16+. SketchWeb downloads: the filter takes file endings (`.mp3`, `mp3`) and `*` besides MIME types, empty =
  every file; the file name from Content-Disposition (`filename*=` UTF-8 first, then `filename=`, no folders), the MIME
  type from the ending when the server sends none or octet-stream; DownloadManager is called on a background thread
  (the page never waits), completion still on the main thread.

## v1.9.6 additions

- **SketchWeb is a real Java file** (`app/src/main/resources/com/sketchware/ai/project/SketchWeb.java.txt`, packaged as
  a Java resource; `WebCode.helperClass(package, appCompat)` fills in `package __PACKAGE__;` and, with AppCompat, makes
  the image dialog a `MaterialAlertDialogBuilder`). Compile the template on the host before a harness run (javac
  `-source 8 -Xlint:all` against the android-37 jar; it compiles without a warning).
- **Download names, never ".bin"** (owner's 7 Nov report "Endhayya Saami.bin": no Content-Disposition, an extension-less
  link and `application/octet-stream` made `URLUtil.guessFileName` append ".bin"). `fileName(url, disposition, mime)`:
  (1) Content-Disposition, `filename*=` (UTF-8 / ISO-8859-1, quoted or not) before `filename=`; (2) the link: a query
  parameter named filename, file_name, file, name, title, fn, f, download, dl, attachment, media, song or video whose
  value has an ending, else the path's last part (decoded, `;` parameters and `#` dropped); (3) "download". Then
  `withMimeExtension`: an ending from the MIME type unless it is octet-stream or would be "bin". URLUtil is no longer
  used. A name still without an ending is checked at enqueue time on the download thread: a HEAD request (same Cookie
  and User-Agent), and only if it tells nothing, a GET of bytes 0-63 (`Range`), 6 s timeouts each; the server's
  Content-Disposition, its MIME type, or the first bytes (`extensionFromBytes`: ID3 / MPEG frame → mp3, ADTS → aac,
  ftyp brands → mp4 / m4a / mov / 3gp, %PDF, PK → zip, PNG, JPEG, GIF, OggS, fLaC, WebM, RIFF WAVE / WEBP / AVI, `<svg`,
  html) give the ending. No answer: the name stays without an ending (still not ".bin"). A single-use link could be
  spent by that probe (release notes).
- Downloads forward the WebView's cookies and user agent to DownloadManager (as before) and the probe; empty, `*` and
  `*/*` filters pass every file. **Image long press** (`allowDownloads` sets the WebView's OnLongClickListener):
  IMAGE_TYPE uses the hit test's extra, SRC_IMAGE_ANCHOR_TYPE asks `requestImageRef` (Bundle key "url"); http(s) images
  only; a one-item dialog "Download image" enqueues with the allowDownloads folder / notify. Other long presses keep the
  WebView's own behaviour (return false).
- **`downloadFile({host}, url, folder, name, notify)`** (block opcode `sketchDownloadFile`: user block packs often use
  "downloadFile", and a built-in of the same name would win over them; File drawer → "Download"): no WebView;
  empty name = server / link name; INTERNET and the < 23 WRITE entry like webAllowDownloads (`yq`: `webDownloadsUsed`).
  `{host}` replaced for it (`WebCode.usesHost`).
- **Ad block** (`setAdBlockEnabled`, block `webSetAdBlock`): 125 ad / tracker domains, matched with their subdomains
  (`isAdHost` walks the host's suffixes), answered with an empty `WebResourceResponse("text/plain", "UTF-8")`, and
  navigation to them is refused. Android 8+ only: the WebView's current client (the generated page-event client) is read
  with `getWebViewClient()` and wrapped by `SketchWeb.Client`, which forwards every WebViewClient callback the platform
  has (two shouldOverrideUrlLoading, two shouldInterceptRequest, page started / finished / commit visible, load resource,
  both onReceivedError, HTTP error, form resubmission, visited history, SSL error, client cert, HTTP auth, key events,
  scale, login, render process gone). Below Android 8 the block does nothing (documented in the guide). A client set
  by code after the block replaces the wrapper.
- **Pop-up block** (`setPopupBlockEnabled`, `webSetPopupBlock`): on = `setJavaScriptCanOpenWindowsAutomatically(false)`
  + `setSupportMultipleWindows(true)` and the shared `SketchWeb.Chrome`: `onCreateWindow` refuses windows without a user
  gesture; a tapped window's first address is loaded in the same WebView through a throw-away WebView (ad domains
  dropped when ad block is on). Off = v1.9.5's defaults (JavaScript windows load in the same view). `Chrome` also
  carries file upload: `enableFileUpload` and the pop-up block share one WebChromeClient (either replaces one set by
  code before it).
- `setMixedContent(webView, mode)` (`webSetMixedContent`, menu `mixedContentMode`: COMPATIBILITY default / NEVER_ALLOW /
  ALWAYS_ALLOW, API 21+), `searchGoogle` (`https://www.google.com/search?q=` + URL-encoded text; INTERNET and
  ACCESS_NETWORK_STATE), `history(webView, list)` (`webGetHistory`, `%m.listStr`: the back/forward list's URLs, oldest
  first), `setUserAgent` (`webSetUserAgent`; empty = the WebView's own). The stock `webViewClearHistory` block stays.
- **Permission picker** (`ui.PermissionPicker`, owner's "Select widget" screenshot): Dialog with a #252528 container,
  16dp corners, compact search field (#1C1C1F, 12dp corners, hint #8E8E93), rows with a radio bubble (checked = the
  slot's current value, accent #FF7A29), bold white name, #B0B0B0 subtitle; one tap picks and dismisses (no Save /
  Cancel; Back or outside closes unchanged). Groups first ("Group · …"), then `android.permission.*` names (short name
  bold, full name and alias in the subtitle); a typed dotted name gets a "Use …" row. Android classes only.
- **Block guide** (`ui.BlockGuide`): three steps per block (Where to drop / What to plug / What you get) under the
  block drawn in its colour, for the 11 web blocks of the WebView section + downloadFile and the 4 permission blocks.
  Opened by a tap on such a block in the drawer (`LogicEditorActivity.onTouch` ACTION_UP without a drag on a palette
  block, `getBlockType() == 1`; a long press there is the drag) or the "ⓘ … guide" button rows in the WebView section
  and the Permission category.
- Review findings applied before the release build (separate agent): opcode renamed (above); the pop-up / upload
  client wraps the app's own WebChromeClient on Android 8+ (`Chrome(state, original)` forwards progress, title, icons,
  full-screen video, JS dialogs, geolocation, permission requests, console, video poster, visited history; below 8 a
  client set by code is replaced as in v1.9.5); the throw-away pop-up WebView is destroyed after its first address,
  which loads with the page as Referer (`loadUrl(url, headers)`), also when only `onPageStarted` sees it; non-http
  links from a pop-up go to Android (`intent:` parsed browser-style: component and selector cleared, BROWSABLE); an
  empty or dot-only server name falls back to the link; `hasExtension` = an ending Android's MimeTypeMap knows, or 1-8
  ASCII letters / digits with a letter in one case (xapk, MKV), so "Mr.Bean" and "Invoice 12.2026" get the MIME ending;
  a script ending (php, asp, aspx, jsp, cgi, pl, do, action, htm, html) is replaced by the MIME type's and makes the
  probe run; names lose control characters and `/ \ : * ? " < > | % #`, leading dots, and are cut to 150 characters
  (ending kept); the probe follows redirects itself (Cookie only to the link's host), 4 s timeouts, a HEAD answer of
  text/html is ignored; the image dialog falls back to the framework AlertDialog when the Material one throws (a theme
  without Material colours); an androidx Fragment host is resolved directly with AppCompat (`/*@ANDROIDX_FRAGMENT*/`);
  the exported download-complete receiver ends an entry only when DownloadManager reports SUCCESSFUL or FAILED (a row
  that is gone = cancelled = failed); `onSafeBrowsingHit` forwarded; ad host set built once (static final), flags
  volatile; the picker window is anchored at the top so the search field stays put while the list changes height;
  a missing template resource makes a stub class (only projects using SketchWeb fail, with a reinstall note).
- Second review pass (before the release build): links a pop-up hands to Android lose every flag (no URI permission
  grants) and are refused with `content:` / `file:` / `javascript:` data; the probe sends the Cookie only while host and
  protocol stay the link's (no Secure cookie after an https → http redirect); a pop-up whose answer is a file goes to
  the page's download handling (`download()`, shared with the page's DownloadListener); the throw-away pop-up WebView is
  destroyed through the main looper (a detached view's `post` never runs) after its address, its download, its
  `onCloseWindow`, or 30 s; endings Android doesn't know need 2+ characters ("Still D.R.E" has none), and an unknown
  ending gets the server's audio / video / image / PDF type's ending added (`song.feat` → `song.feat.mp3`); an
  unreadable download status counts as failed; a cut name never splits a surrogate pair. Known limit: a POST form with
  `target=_blank` reopens its address as a GET (the form data is not resent).

## v1.9.7 additions

Scope agreed with the owner (8 Nov 2026): HEAD-only download probe + cache-race fix, 2-layer ad / pop-up blocker with
redirect-free Back, download-type selector, visual block shapes in the guide. Deferred to v1.9.8: multi-tab WebView
blocks, the security settings sheet, the PWA shortcut. Declined (standing rules): an OAuth client secret in the APK
(GitHub sign-in needs PKCE without a secret: later milestone), anti-tamper / re-sign kill switch, R8 full mode.

- **Download probe is HEAD only** (owner: never spend a one-time link with a pre-flight GET). `SketchWeb.probe` sends
  one HEAD (own redirects, Cookie only while host and protocol stay the link's, 4 s timeouts) and reads
  Content-Disposition and Content-Type; a HEAD answer of text/html or octet-stream tells nothing. The ranged GET and the
  byte sniff on the network path are gone. A name the server and the link give no ending keeps none (never ".bin").
  `extensionFromBytes` stays as a public utility with no internal caller. A post-download rename by the file's bytes
  was written and removed after review: DownloadManager keeps the old path (its notification would open a missing
  file) and there is no API to update it.
- **Built-in library resource cache** (`ResourceCompiler.compileBuiltInLibraryResources`): aapt2 compiles into
  `<library>.tmp-<thread>-<nanoTime>.zip` in the same folder, then `renameTo` the cached name (copy as fallback), temp
  deleted in `finally`. A Run build and an Export that rebuild the cache at once can no longer read a half-written
  archive (v1.9.6 harness collision, known-errors).
- **Ad blocker, two layers** (`SketchWeb.Client`, Android 8+ as before): (1) network: `isAdHost` (125 domains and
  their subdomains) blocks any request and stops a navigation; the broader `isAdUrl` (also an ad brand word at the
  start of a host label: `adservice.google.co.in`, `stats.g.doubleclick.net`, 21 words in `AD_HOST_WORDS`; or the
  paths `/pagead/`, `/adx/`) blocks **sub-resources only** (`!request.isForMainFrame()`), so a loose match can never
  blank a page. Words are matched in the host, never anywhere in the URL (review: `adservice` matched
  `/FileUploadService/`, `adroll` matched `breadrolls.jpg`); googletagmanager is not blocked (sites ship consent
  banners and widgets through it). (2) cosmetic: `injectHidingCss` on page start and finish adds one style element
  (id `sketch_adblock_css`) through `evaluateJavascript` that hides `.adsbygoogle`, `[id^="google_ads"]`,
  `[id^="div-gpt-ad"]`, classes that start with `ad-`, `ads-` or `advert` (anchored on whole class names, so
  `download-box` and `uploads-list` stay), DoubleClick / AdSense iframes and `.popunder`, with
  `display:none !important`. Needs JavaScript (on by the WebView initializer).
- **Redirect-free Back**: `SketchWeb.goBack(webView)`: while that WebView's ad blocking is on, it reads
  `copyBackForwardList()` and steps back past pages on a listed ad host (`goBackOrForward(delta)`; when every earlier
  page is one, a single step); with ad blocking off it is the WebView's plain back step, so projects without the block
  behave as in v1.9.6. Host list only (review: a fragment match skipped `google.com/search?q=adsense`). The generated
  default `onBackPressed` (`Lx.getDefaultActivityLifecycleCode`) calls `SketchWeb.goBack(id)` inside
  `if (id != null && id.canGoBack())`, so every project with a WebView now gets the SketchWeb helper (inert until
  called).
- **Download type** (`sketchDownloadFile` has a fifth slot `type %m.downloadType`; code
  `SketchWeb.downloadFile({host}, url, folder, name, notify, "<type>")`): `ExtraMenuBean.downloadTypePicker` shows
  Default, Audio, Video, Images, Packages, Documents in the Select-widget picker, or a typed ending / MIME (strict
  characters only, so the generated string literal stays valid). `SketchWeb.categoryExtensions` maps the token to
  endings; a name the link **and** the server's HEAD answer leave without an ending gets the category's first ending
  (a MIME: its registered ending; `enqueue(..., fallbackEnding)`), and the download is never blocked. A v1.9.6 block with four slots still generates (empty type = Default).
- **Visual block guide**: `ui.BlockShapeView` draws the block on a Canvas: rounded body in the category colour, a notch
  in the top edge and a bump under the bottom edge (where blocks stack), white label, one rounded slot per `[hint]` in
  the block text, wrapped over lines; a boolean block (`permission … granted`) has pointed ends and a value block
  (`permission status`) round ends, as on the canvas (`Entry.kind`). `BlockGuide.card` uses it instead of a coloured TextView; `BlockGuide.show` adds
  a 220 ms rise-and-fade. The shared picker's search hint is "Search".
- Review (separate agent, two passes, before the final build), applied: no post-download rename; ad words in the
  host only and never for a navigation; anchored hiding CSS; Back by ad host and only with ad blocking on; the category
  ending after the HEAD answer; strict custom type characters (valid ICU regex, checked on ART); a `[` without `]`
  in a block text; guide texts that promise only what runs. Clean in both passes: receiver, cache rename, generated
  back chain for several WebViews and a drawer, String.format slots, API levels.

## v1.9.7.1 (11 Nov 2026): toolchain / Material 3 / Play audit with small fixes

Record and test results: `v1.9.7.1-toolchain-m3-play-audit.md`.

- **Call-to-action colour** (owner's decision, 11 Nov): button labels stay pure white; the surfaces `ob_cta`,
  `ob_cta_pressed` (both resource folders), `ob_safe_cta`, `ob_safe_cta_pressed` are #D34115 → #C64F14 (pressed
  #C83D13 → #BC4A12) instead of #E8501C → #F97316, so white reaches 4.6:1. Every other orange is unchanged. A dark label
  on orange is not to be proposed again.
- **Block guide**: `BlockShapeView.readable(color)` deepens a block colour until white reaches 4.6:1 (teal → #008377,
  brown → #857169); `BlockGuide` uses it for the step badge.
- **Generated manifest** (`Ix`): `legacyStorageMaxSdk()` caps READ / WRITE_EXTERNAL_STORAGE of File blocks and
  components at `maxSdkVersion="32"` for target 33+; `.DebugActivity` is declared for Run builds or when the project has
  its own `DebugActivity.java` (exports no longer name a missing class).
- **Network security configuration**: `Ix` adds `android:networkSecurityConfig="@xml/sketch_network_security_config"`
  wherever `usesCleartextTraffic="true"` is written and nothing else set one; `jq.networkSecurityConfig` carries that
  to `yq`, which adds the `SrcCodeBean` `Ix.NETWORK_SECURITY_CONFIG` (routed to `res/xml` by `yq.a(String, String)`).
  `Ix.settleNetworkSecurityConfig` runs after `CommandBlock.applyCommands`, on both manifest paths (generation and
  `getFileSrc`), and removes the generated attribute and file when a command block set the attribute too.
- **Helper sources**: `SketchWeb.Client` carries `@TargetApi(27)`, `SketchWeb.register` and
  `SketchButton.setBackgroundDrawable` a `@SuppressLint`; `SketchwareUtil.hideKeyboard` passes `SHOW_IMPLICIT`. No
  behaviour change; they remove ten Android Studio lint errors from exported projects.
- **Selected segment pills** (12 Nov, owner's go): `ob_seg_pill`'s selected state is `ob_seg_pill_on` (glass in
  `drawable-v24`, gradient shape below), #D34115 → #C64F14; `ob_tile_orange` keeps its other users.
- **Camera feature tag** (12 Nov, owner's go): `Ix.settleCameraFeature(String)` runs in `yq` after
  `settleNetworkSecurityConfig`, on the final manifest text of both paths: CAMERA declared (either quote style) and no
  `uses-feature` for `android.hardware.camera` → `<uses-feature android:name="android.hardware.camera"
  android:required="false" />` before `<application`, same indent and line ending. An existing camera tag is never
  doubled (Gradle's manifest merger rejects two differing ones; aapt2 accepts them).
- **Export check**: `gradle-export-check.sh` can run `lintDebug` (`LINT=1`). Fixture 908's export: 3 lint errors left
  (two from the fixture's layout, `SuspiciousIndentation` in the activity template's helper).
- Measured for the record: a Run build's APK verifies with v1, v2, v3 and v4 (`.idsig` beside it); an exported APK has
  v1 + v2 + v3 and no v4 file; D8 8.13.25 never writes a DEX version above 039.

## v1.9.8 additions (12 Nov 2026): self-healing built-in library cache, Build Doctor rules, GitHub sign-in and cloud backup

Owner's 12 Nov directives ("Self-healing cache, Diagnose AI training, GitHub PKCE login & database engine", then the
same day "no Client ID box, one-tap login, remote control & integrity gate"). Record with results:
`v1.9.8-verification.md`; project data audit: `v1.9.8-project-data-audit.md`.

- **`project.BuiltInCache`** (the "video build crash": `NoSuchFileException .../cache/libs/libs/graphics-shapes-1.0.1/
  classes.jar` until a reinstall). `onAppStart(context, versionCode|versionName)` from `SketchApplication.onCreate`: a new
  app version moves `cache/libs/{libs,dexs,libs.zip,dexs.zip,desugar,desugar.zip}` and `cache/compiledLibs` into
  `cache/builtin-stale-<n>` (renames only), lists staged leftovers (`*.tmp-<n>*`, `*.old-<n>*` in `libs`, `libs/libs`,
  `libs/dexs`) before any build of the process runs, and deletes both on a guarded daemon thread. android.jar, testkey and
  the lambda stubs stay (each checked by its own size; the generator's automatic imports read android.jar).
  `extractStaged` (whole archives: temp folder, every entry checked by size, renamed into place) replaces `KB().a` in
  `Dp.getBuiltInLibrariesReady` (in-place extraction remains the fallback). `Dp.verifyBuiltInLibraries` (end of
  `getBuiltInLibrariesReady`: Run, Export APK, Export AAB) compares every used library's files with the `libs.zip` /
  `dexs.zip` entry sizes and unpacks a damaged one again through `<name>.tmp-<n>` (its `compiledLibs/<name>.zip` is
  dropped); a repair is a **note**, not a warning. android.jar and testkey are also re-extracted when the unpacked file
  is missing or cut off (`unpackedFileIntact`, `extractFile`: temp file + rename). After a failed step,
  `recover(compileAssets, failure)` takes the library `BuildDoctor.builtInLibraryOf` names (error and
  `/cache/libs/(libs|dexs)/<name>` on one line; only names that are folders of `libs.zip` or `.dex` of `dexs.zip`) and
  unpacks it again with `verify(..., force=true)` (staged; the old copy goes only once the new one is complete);
  DesignActivity's `buildFailed` (all of it guarded; the snackbar posted to the UI thread) restarts the build once per
  user click (`userStartedBuild` / `retryAvailable` / `claimRetry`; `BuildWarnings.carryNote` only into the build that
  really starts), ExportProjectActivity runs `b()` once more (`retriedAfterRepair`). Export to Android Studio reads the
  APK's own `assets/libs/libs.zip`, not the cache.
- **Build Doctor**: code `BUILT_IN_CACHE` (retry can help, generic advice suppressed); `DUPLICATE_CLASS` names local and
  built-in libraries and, for two versions of one library, which to switch off (`duplicateAdvice`, `compareVersions`;
  an older built-in one stays, the local one goes); `CompileLogDiagnostics` rules for Material 3 attributes / dotted
  `Widget.Material3.*` styles without the Material library, invalid resource file names, ECJ class-file versions (two
  rules), and the stale "Android 14 android.jar" texts now say API 37. `BuildWarnings` note for storage permissions with
  target 33+ (never granted on Android 13+). `Ix.settleExported` (generated manifest, target 31+, after the command
  blocks, in both `yq` paths): `android:exported="true"` on an activity / alias / service / receiver with an intent
  filter and no exported (quote-aware start tags; same line count); noted through `jq.exportedNote`.
- **`com.sketchware.ai.github`**: `GitHubHttp` (HttpURLConnection; token only to the API host; no redirects; streamed
  base64 blob upload; raw downloads with a size cap; PATCH falls back to POST + X-HTTP-Method-Override only on Java SE,
  Android's HttpURLConnection sends PATCH), `DeviceFlow` (OAuth Device Flow with the public Client ID in
  `strings.xml` `github_client_id` = `Ov23liRZYoX7Y9YTJmfJ`, scope `repo`; `authorization_pending`, `slow_down` +5 s,
  errors mapped), `TokenStore` (AES-256-GCM key in the Android Keystore, alias `sketchware_ai_github_token`; prefs
  `sketchware_ai_github` hold `v1:iv:ciphertext` and the login; signed out only on a bad tag / missing key / unknown
  format, not on a passing Keystore error; API 23+), `CloudBackup` (private repo `<login>/SketchwareAI-Projects`,
  created with `auto_init`, a public one refused; one commit per backup through blob / tree with `base_tree` / commit /
  PATCH ref, retried once on 422; `projects/<package>/<name>.swb` + `index.json` read raw; history by
  `commits?path=` (100); download raw; files over 95 MB refused), `GitHubScreen` (the cards; compact: GitHub mark,
  "GitHub", one line, orange pill; code card; account + cloud backup), `GitHubActivity` (main drawer "GitHub & Cloud
  Backup"; one tap: Device Flow, code copied, toast, github.com/login/device in a Custom Tab through raw intent extras;
  single-thread executor shut down without interrupt; token read on the worker; restore through
  `BackupRestoreManager.doRestore` = a new project), `AppControl` (MainActivity.onCreate with storage access:
  `config/app-control.json` of `main` on raw.githubusercontent.com, read once per process on a daemon thread, cached;
  at most one dialog: update when `min_version_code` > this version, `status_message` once, with `app_enabled=false` at
  every start, a sign-in suggestion with `require_login`. Notices only: nothing locks the IDE, no build expiry).
  PKCE (browser redirect) not built: GitHub's token endpoint requires the client secret for it.
- **Project data**: `project.ProjectIds.free` (no folder in `mysc/list`, `data`, `bak`, `mysc`, `resources/*`) for
  restore (`BackupRestoreManager`), cloud restore, `NewProjectActivity` and `MyProjectSettingActivity`;
  `BackupFactory.unzip` refuses entries outside the folder (skips the folder itself), `getNewScId` numeric, a failed
  backup leaves no partial `.swb`, `RandomAccessFile`s closed.
- Harness: `cachetest <id>` (`h.CacheTest`, four builds), `githubtest <id>` (`h.GitHubTest`: offline parts + real `.swb`
  restore + app-control.json), `doctortest` (`h.DoctorTest`); `Main.buildWithRecovery` mirrors DesignActivity's restart;
  `Main.AFTER_LIBRARIES` hook. `tools/jvm-tests/GitHubClientTest.java` (network side against a fake GitHub on the JVM:
  ART here can't open sockets). Render: `tools/layoutlib-renders` `V198Test` + `setup_v198.sh`.
- Not built (owner's 12 Nov asks, reasons in the verification record): native/Java APK integrity gate, remote kill switch
  that locks the IDE, 45-day build expiry, workspace locked until sign-in, Google One-Tap / e-mail code sign-in, GitHub
  Discussions store.

## v1.9.9 additions (12 Nov 2026): sign-in that survives the browser, Cloud Sync & Account, sign-in gate, device-bound session

Owner's 12 Nov evening directive after the v1.9.8 phone test (GitHub: "Congratulations, you're all set!", the app:
"Sign-in didn't work"). Record with results: `v1.9.9-verification.md`.

- **Cause of the phone failure**: v1.9.8 polled the token endpoint from `GitHubActivity`'s executor and ended the
  sign-in on the first `IOException`. While the Custom Tab is in front the app is in the background, and Android 15
  cuts a background app's network (and freezes cached apps): the first poll after the user approved failed, so the
  approval was never picked up.
- **`github.SignIn`** (process singleton, application context): `start(clientId)` / `resumeSaved(clientId)` /
  `pollNow()` / `cancel()`; its own daemon thread per sign-in, a `generation` counter so a cancelled or replaced run
  never touches state; the code (`device_code`, `user_code`, URIs, interval, `expires_at`) saved in prefs
  `sketchware_ai_github_signin` and resumed by the screen's `onCreate` (a run the system ended by stopping the
  process continues until the code expires); `pollNow` wakes the wait (`onResume`: back from the browser = an
  immediate check, but never two checks closer than the interval: GitHub answers `slow_down`, +5 s for the rest of
  the code; `run()` clears a stale wake flag); after the token, `CloudBackup.account` (login, numeric id, avatar from
  `avatars.githubusercontent.com` only) is retried up to 10 minutes on network errors and on HTTP 5xx / 429 / 403
  (`isPassing`; the "Almost done" card has Cancel), then `TokenStore.save`; the avatar is downloaded by `SignIn` itself
  afterwards (`files/github_avatar.png`, temp file + rename) and every screen reads it from the file;
  `takeSignedIn` / `takeOpenBrowser` (each once); listeners on the main thread. Auto-return: the screen opens the
  Custom Tab with `startActivityForResult(…, 7311)` and registers itself (`browserOpenedBy`, a WeakReference; dropped in
  `onDestroy`); when the token arrives and no screen listens, `bringToFront()` calls `finishActivity(7311)` on it: the
  tab (in the app's task, above the screen) is removed and the screen comes back. Closing an activity is not an
  activity start, so the background-start rules don't apply (Android 15 dropped the "activity in the back stack of the
  foreground task" exception and blocks non-visible windows; developer.android.com background-starts, read 12 Nov
  2026). If 500 ms later no screen listens and no screen of the app is resumed (`ResumedCounter`, ActivityLifecycleCallbacks
  registered when `SignIn` is created from the screen's `onCreate`; so a gate that finished itself, or a user elsewhere
  in the app, gets no extra screen), `GitHubActivity` is started with `NEW_TASK | CLEAR_TOP | SINGLE_TOP`, which
  Android may refuse; then the user's own return (Back / X) shows the signed-in
  screen at once (`pollNow` + `takeSignedIn`). Errors: one short line
  (`shortMessage`: cancelled / expired / not available / no internet / Keystore / "Sign-in didn't work"), the cause
  plus the last network error while waiting in `errorDetail` (long press copies it).
- **`DeviceFlow.waitForToken`**: deadline from `Code.expiresAt` (= start + `expires_in`); a poll that throws
  `IOException` or answers 5xx without an `error` calls `Waiting.networkTrouble` and waiting goes on; `slow_down` =
  max(interval + 5, GitHub's interval); `Poll` (one answer) and `poll()` split out.
- **Custom Tab**: raw extras as before plus `android.support.customtabs.extra.KEEP_ALIVE` = an Intent naming
  `github.KeepAliveService` (exported, empty Binder): a browser that honours it binds the service while the tab is open,
  which raises the app's process importance. Undocumented, best effort.
- **`TokenStore` v2**: `"v2:" + iv + ":" + ciphertext`, AES-256-GCM associated data = SHA-256(`ANDROID_ID` + `|` +
  SHA-256 of the signing certificate (`GET_SIGNING_CERTIFICATES` on 28+, `GET_SIGNATURES` below) + `|` + GitHub user id);
  prefs also hold `user_id`. Another phone (copied app data), a re-signed copy or another user id: the tag fails =
  signed out (token, login, id removed). v1.9.8's `v1:` tokens read as signed out (one new sign-in). The Keystore key
  itself never leaves the phone.
- **`github.AuthGuard`**: `required(ctx)` = `AppControl.cached(ctx).requireLogin` (the last `config/app-control.json`
  read on this phone; never read = not required) **and** `AppControl.fetchedOnline()` (the file came from GitHub in
  this process: an offline start never asks, the IDE stays offline-first) **and** not `markUnavailable()` (set for the
  run by a sign-in that can't work here: `device_flow_disabled`, `incorrect_client_credentials`, `http_404`,
  `no_client_id`, `keystore`, `unsupported_grant_type` (`SignIn.cannotWork`), or no browser). Review finding of
  v1.9.9: without these, a cached `true` locked an offline or Keystore-broken phone out of its projects; `verifyHardwareBoundSession(ctx)` = a login and a token that decrypts
  (cached per process while true; `reset()` on sign-in / sign-out); `check(activity)`: true when not required or
  signed in, a `RuntimeException` fails open; otherwise opens `GitHubActivity` with `EXTRA_GATE` (`SINGLE_TOP |
  REORDER_TO_FRONT`) and the caller stops. Call sites: `MainActivity.onResume`, `NewProjectActivity.onCreate`,
  `MyProjectSettingActivity.onCreate` (new projects only), `DesignActivity.onCreate` (open project) and the Run button,
  `ExportProjectActivity.onCreate` (after its views: its `onDestroy` reads them), `BackupRestoreManager.restore` and
  `doRestore` (`.sw` / `.swb` and cloud restores). `AppControl.check`: a `login` notice now calls `AuthGuard.check`
  instead of a dialog. The gate: no Back arrow while signed out, Back = `finishAffinity`; finishes itself once signed
  in. No account-name bypass (it would be a backdoor anyone can read in the source). The repository keeps
  `"require_login": false` until the owner confirms sign-in on his phone.
- **"Cloud Sync & Account"** (drawer item and screen title; was "GitHub & Cloud Backup" / "Account"): `GitHubScreen`
  rebuilt on the drawer header card (`ob_glass`, 14dp padding, 44dp `ob_tile_orange` badge with the GitHub mark or the
  rounded avatar, Sora bold title, caption subtitle, deep-orange `ob_cta_pill_state` with white label): signed out /
  gate ("Sign in to continue"), code card (code, "Open GitHub", Cancel), "Almost done…", signed in (`@login`, Sign out
  pill unless busy, 16sp title, ellipsized), "Your projects" (every project of `lC.a()` with Back up (orange) and Restore
  (glass, only when a backup exists); detail = "Backed up 12 Nov, 14:05" / "Not backed up yet" / "Backing up…") and
  "Only on GitHub" (index entries whose path no local project has). Back up = `BackupFactory` without local libraries +
  `CloudBackup.upload`, toast "<name> backed up to GitHub"; Restore = the version list, then the app's own restore as a
  new project, the list re-read until the new project appears (up to a minute; on a main-looper Handler whose
  callbacks `onDestroy` removes: a View's `postDelayed` outlived the screen and handed work to the shut-down executor,
  a crash found in review). Avatar in `files/github_avatar.png` (deleted at sign-out). Known limit: two local projects
  with the same name and package share one backup path (`projects/<package>/<name>.swb`, so a backup made on another
  phone matches); both rows show the same backup.
- `config/app-control.json`: `min_version_code` 200, `update_url` the v1.9.9 APK.
- Tests: `GitHubClientTest` (dropped connection + HTTP 502 mid-wait, `access_denied`, expired saved code, `expires_at`,
  account id / avatar host), `h.GitHubTest` (v2 format, other ANDROID_ID / certificate / user id, v1 token, gate rules,
  short messages), render `V199Test` + `setup_v199.sh`.

## v2.0.0 additions (13 Nov 2026): in-app GitHub sign-in, drawer account card, per-project backup and import

Owner's 13 Nov directive "in-app zero-Chrome GitHub auth, individual project sync/import, unified glass pill UI & deep
hardware-bound auth guard (v2.0.0)", with the Liquid Glass design files. Record: `v2.0.0-verification.md`.

- **Design reference** `design/`: the owner's PDF (mockups + token sheet) and `Tokens.dc.html` (values), with
  `design/README.md` mapping every token to its resource (`colors_ob.xml`, `ob_glass`, `ob_pill16*`, `ob_cta*`), the
  rules of CLAUDE.md sections 1 and 8, and the recorded deviations (CTA deepened in v1.9.7.1; GitHub cards without the
  glow in v2.0.0). The export's React runtime scripts are not kept.
- **`github.GitHubInAppAuthDialog`** (full-screen `Dialog`, `Theme_Material_NoActionBar`, ob_bg window, adjustResize):
  GitHub's device page in a `WebView` inside the app (no Custom Tab, no external browser; `KeepAliveService`, the
  `startActivityForResult` / `finishActivity` route and the `ResumedCounter` of v1.9.9 are gone). The app stays in front,
  so the polling of `SignIn` keeps its network. Main-frame navigations only to `https://github.com` and its subdomains
  (`allowed`); frames inside GitHub's page are not limited (a captcha may come from another host); Google sign-in pages
  refused with a line (Google blocks embedded sign-in). JavaScript and DOM storage on, file / content access off, no
  `addJavascriptInterface`, mixed content never, Safe Browsing on 8+, SSL errors cancelled, cookies kept (flushed on
  close: the next sign-in skips the password). `fillScript(code)` (code validated `[A-Za-z0-9]{4}-?[A-Za-z0-9]{4}`,
  stripped to `[A-Za-z0-9-]`): on `/login/device` only, the visible text inputs; eight one-letter boxes get the eight
  characters, fewer one-letter boxes = "partial" (retried, never half-filled), otherwise one box named like "code" (or
  the only box) gets the code; values through the input's own setter + `input` / `change` / `keyup` events; never
  presses Continue or Authorize. Retried 8 times every 600 ms while the page builds; then "Type the code …" (the code
  chip copies it). Pages under `/login/device/…` (after Continue / Authorize) call `pollNow`. The window closes when
  `takeSignedIn` returns the login (`Done.signedIn`, toast "Signed in as @x"); closing it before that cancels the sign-in.
  One window at a time (`current`; a window on another / recreated screen is closed quietly, its sign-in goes on); a
  destroyed host = the WebView freed quietly; success is also seen when another window took the result (a code shown,
  none pending, a login saved, and none stored when this window began). Sign-out removes the WebView cookies.
  No WebView on the phone / Android < 6: a line, and `AuthGuard.markUnavailable()`.
- **`SignIn`** (v1.9.9 polling kept: errors while waiting = keep waiting, saved code resumed, checks never closer than
  the interval) minus the browser hand-off; after the token also `CloudBackup.primaryEmail` (GET /user/emails, scope
  `user:email` added: `DeviceFlow.SCOPE = "repo user:email"`; a token without it gives no e-mail, no error) and
  `GitHubSession.saveProfile`.
- **`github.GitHubSession`**: profile prefs `sketchware_ai_github_profile` (name, e-mail, avatar URL, `checked_at`),
  picture `files/github_avatar.png`, `signOut` (TokenStore + Keystore key + profile + picture), listeners on the main
  thread, `recheckOnline` (MainActivity.onResume, at most every 6 h, background: GET /user; 401 = signed out, a new
  picture / name / e-mail saved; offline nothing changes).
- **Drawer**: `main_drawer_header.xml` has a second `ob_glass` card `drawer_account*` under the Sketchware AI card,
  bound by `github.DrawerAccountCard` (signed out: GitHub mark on `ob_tile`, "Sign in with GitHub", "Sync & back up
  projects", tap = sign-in window; signed in: picture (14dp corners) or the mark, name or @login, @login, primary e-mail,
  "Sign out" glass pill with a confirmation). MainDrawer rebinds the header on `GitHubSession` changes
  (`onAttachedToWindow`). `DrawerItem.MENU_GITHUB` ("Cloud Sync & Account") removed.
- **`github.CloudSync`** (one daemon thread for the app): `upload(scId)` = the token and `CloudBackup.ensureRepository`
  first (offline nothing is zipped), then `BackupFactory` (no local libraries, custom blocks on,
  `setReadSavedDataOnly(true)`: `CustomBlocksManager(sc_id, true)` reads the saved project into private `hC.i()` /
  `eC.g()+e()` instances, never `jC`, whose getters empty the editor's live project in place when another project is
  asked for: review finding), `upload`, then `BackupFactory.deleteUploadedBackup` (the .swb and its empty folder, under
  `BACKUP_LOCK`: `BackupFactory.backup` runs one at a time, background runs use `<name>_cloud_temp`); `backUpFromMenu` (project card Back up >
  "To GitHub"; signed out: the sign-in window first; toasts at start and end); `projectCreated` (GC's new-project result:
  signed in = backed up by itself); IOException or a Keystore hiccup = `pending` (prefs `sketchware_ai_cloud_sync`,
  string set copied before changing); `syncPending` after a sign-in, `syncPendingSoon` from MainActivity.onResume (every
  10 min at most, only when something waits; `SignIn` also calls `syncPending` after every sign-in); waiting also on
  HTTP 5xx / 429 / 403 and "not signed in"; HTTP 401 = signed out only when it is still the same token, the project
  waits; failures of the automatic backup that won't be retried are shown.
- **`github.GitHubImport`** + **`GitHubImportDialog`** (Restore card > "Import from GitHub"): own backups (signed in:
  `CloudBackup.list`, a version list, download) and a pasted link: `github.com/<o>/<r>` (every .swb of the default
  branch's tree), `…/tree/<ref>/<dir>`, `…/blob|raw/<ref>/<path>.swb`, `raw.githubusercontent.com/<o>/<r>/[refs/heads/]<ref>/<path>`;
  path segments decoded, `.` / `..` / slashes in a segment refused, other hosts refused; downloads through the API
  contents endpoint (raw), the token only to the API host and only when signed in; 404 signed out = "a private
  repository needs you to sign in". The file goes to `BackupRestoreManager.doRestore` with the projects list's callback;
  an own backup holding local libraries asks Copy / Don't copy (as the .swb restore does), a link's backup never copies
  local libraries (a planted `<artifact>_V_<version>` folder would serve other projects: review finding); the zip check
  runs on the worker. Looking at own backups never creates the repository (`CloudBackup.openIfExists`).
- **Project list (`a.a.a.GC`)**: Back up = choice "On this phone (.swb file)" / "To GitHub"; Restore card = choice
  "Restore a .swb file" / "Import from GitHub" (the latter behind `AuthGuard.check`).
- **`GitHubActivity`** = the sign-in gate only (one card, "Sign in with GitHub" opens the window; finishes once
  signed in); its v1.9.9 project list is gone. `GitHubScreen` replaced by `GitHubViews` (shared view kit: glass card,
  header row, no-glow pills, rows, the sign-in window's chrome).
- **AuthGuard** also at `LogicEditorActivity.onCreate` (blocks, palette, permissions), `ManageLibraryActivity.onCreate`,
  `LibraryResolverActivity.onCreate` (`LogicEditorActivity.finish()` skips its event-history call while the project
  fields are unset: finishing from onCreate crashed it), and every export button (APK, AAB, Android Studio) on top of the
  screen's own check; `AppControl.refresh` (MainActivity.onResume, every 6 h, background, no dialogs) re-reads app-control.json and
  opens the gate when sign-in became required. `BuiltInCache`'s repair at app start is not gated (blocking it would
  leave a broken cache for the next build; builds themselves are gated).
- **Drawables** `ob_cta_pill_soft*` (`GlassDrawable` without `swGlow*`, 16dp; plain shapes below 7.0) for the GitHub UI;
  `ob_cta_pill*` and the other CTAs keep their glow.
- Tests: `GitHubClientTest` (profile name, primary e-mail with and without the scope, import listing / download public
  signed out and private with the token), `h.GitHubTest` (import links, allowed pages, the script's code argument),
  `tools/jvm-tests/fill-script-test.js` (jsdom stand-ins of the device page, `FillScriptPrint` prints the app's script),
  render `V200Test` + `setup_v200.sh`.

## v2.0.1 additions (13 Nov 2026): sign-in window fixes, API retries and rate limits, glass Back up / Restore sheets

Owner's 13 Nov directive after the v2.0.0 phone test (screenshots: "Sign in with G…" and the status cut, blank pages,
GitHub's "Too many requests", the import hint and a row title cut, plain backup lists). Record:
`engineering/v2.0.1-verification.md`.

- `GitHubViews.authChrome` (two rows in one `ob_glass` card): close, "GitHub Sign-In" (centred, up to two lines, never
  cut) and Reload (`iconButton`, `ob_ic_refresh`); a 3dp horizontal `ProgressBar` (orange on `ob_divider`, INVISIBLE
  when idle so nothing moves); the code pill (`setMaxLines(1)`, not `setSingleLine`: single-line + centre gravity drew
  the text outside the pill in layoutlib) with the copy icon, and the status on up to three lines; then the page and the
  "Sign in via browser" action card.
- `GitHubInAppAuthDialog`: `setSupportMultipleWindows(true)` + `onCreateWindow` (user gesture only): a throw-away
  WebView takes the first address; GitHub's loads in the main view, anything else is refused; the throw-away view is
  destroyed on the main looper (10 s cap). A refused main-frame navigation calls `recoverSoon()` (300 ms later: a
  foreign or empty page, `document.body.innerText` length 0, is replaced by `lastGoodUrl` or the device page).
  `onPageStarted` → 20 s "slow network" line, 60 s "not answering" line; `onReceivedHttpError` 429 or a page title
  "Too many requests" → one-minute pause with a countdown (Reload waits). `onRenderProcessGone` (Android 8+) → the
  WebView is destroyed and a new one made (before: the app process ended). "Sign in via browser" copies the code and
  starts a Custom Tab by intent extras (no androidx.browser); `onWindowFocusChanged(true)` with a pending code →
  `SignIn.pollNow()`. No WebView on the phone: the window still asks for a code, the browser card works;
  `AuthGuard.markUnavailable()` only when there is neither a WebView nor a browser. User agent unchanged (removing
  "; wv" to pass Google's embedded-sign-in block is against Google's policy; Google/Apple/passkey go through the browser).
- `SignIn.pause()` (window closed before approval): generation++, pending cleared, the saved code kept, so the next
  window resumes it (`resumeSaved`) instead of asking for a new one; not while `finishing`. `start()`: at most one new
  code per `MIN_START_GAP` 10 s (a second ask sleeps on its worker thread for the rest, generation-checked), none for
  60 s after `http_429` / `rate_limited` (`waitSeconds()`); `errorCode()`; an expired code is renewed once by itself
  while the window is open. `isPassing` includes `rate_limited`.
- `GitHubHttp`: connect 30 s / read 120 s; GET (`api` without body, `apiRaw`, `download`) tried again after an
  IOException (not UnknownHost / SSL handshake / over-size) or 502/503/504 with pauses `backoff` 1/3/7 s + jitter; a 429/403
  rate limit with Retry-After ≤ 10 s is waited out once; `note()` records GitHub's limit (`Retry-After`, or
  `X-RateLimit-Remaining: 0` + `X-RateLimit-Reset`, or 60 s) in `blockedUntil` (capped at one hour against wrong
  clocks) and every API call answers a synthetic 429 until then without a request; writes never retried. `Response`
  carries `retryAfter`, `rateRemaining`, `rateReset`, `rateLimited()`. `CloudBackup.http()` turns a rate limit into
  `rate_limited` ("try again after HH:MM"). `DeviceFlow.waitForToken`: a 429 while waiting = interval +5 s (or
  Retry-After, at most 60 s), the code kept; `failure("http_429")` text.
- `CloudSync.Options(localLibraries, customBlocks)`; `AUTOMATIC` = (false, true) for new and waiting projects;
  `backUpFromMenu(..., Options)` and `upload(scId, Options)` set `BackupFactory.setBackupLocalLibs/CustomBlocks`.
  `ProjectBackupSheets.backup` (`GitHubViews.backupSheet`: header, "Include used Local libraries" / "Include used Custom
  Blocks" remembered in `sketchware_ai_cloud_sync` `option_*` (both on by default), "Save on phone" →
  `BackupRestoreManager.backupNow`, "Back up to GitHub" → the same boxes) and `ProjectBackupSheets.restore`
  (`restoreSheet`: "Restore local .swb file", "GitHub import project"); `GC.backupProject` / `chooseRestore` use them.
- `GitHubImportDialog`: hint "Paste a GitHub link" + a grey line under it, `GitHubViews.row` titles on up to two lines;
  own backups list saved (Gson of `CloudBackup.Entry[]`, login-keyed) and shown offline with "Offline · the list from …";
  a linked backup with `local_libs` asks "Copy libraries" / "Without libraries" with a trust note (BackupFactory copies
  only libraries not on the phone; custom blocks come with the project's data).
- Harness: `stubs/android/os/{Handler,Looper,SystemClock}` (post does nothing: no main thread on the harness).
  Tests: `GitHubClientTest` +10 (retries, Retry-After, the gate, POST once, download resume, poll 429, code 429),
  `h.GitHubTest` +6 (pause keeps the code, rate-limit line, automatic options, both boxes in the .swb, restore copies a
  missing library and keeps an existing one, custom blocks restored), render `V201Test` + `setup_v201.sh`.

## v2.0.2 additions (14 Nov 2026): sign-in that lands on the code page, live progress, share links, import guide, GitHub component

Owner's 14 Nov directive after the v2.0.1 phone test (screenshots: GitHub's home feed after signing in, a blank page
after "Continue with Google", "Sign in with Gi…" / "@cr…" in the drawer, no visible Back up to GitHub). Record:
`engineering/v2.0.2-verification.md`.

- `GitHubInAppAuthDialog`: `toDevicePageIfSignedIn` (from `pageFinished` and `doUpdateVisitedHistory`): on
  github.com "/", `/dashboard*`, `/home`, `/feed*`, `/explore*`, `/welcome*`, `/onboarding*`, `/copilot*`,
  `/notifications*` (all but "/" only with GitHub's `logged_in=yes` cookie) the device page loads again, at most 3 times a
  minute, only while a code waits. "Continue with Google / Apple": `isSocialSignIn` (github.com `/sessions/social…`,
  `/session/social…`, `/login/social…`, `…/social/google|apple`; accounts.google.com, appleid.apple.com,
  login.microsoftonline.com, login.live.com) in `shouldOverrideUrlLoading` → `intercept()` and in `onPageStarted`;
  `SOCIAL_SCRIPT` (injected on `/login*`, `/session*`, `/signup*`, `/join*`, "/") cancels a click on a social button
  (link / form / formaction with `/social/`, `social_provider`, `/auth/google|apple`, or the text "Continue / Sign in /
  Sign up with Google / Apple") and a social form's submit, and navigates to `sketchware-ai://browser-sign-in`
  (`HANDOFF_URL`), which `intercept()` answers with `socialHandoff()` (≥ 3 s apart) → `openBrowser()`. The Custom Tab:
  `startActivityForResult(…, REQUEST_BROWSER 7311)`, the keep-alive extra naming `KeepAliveService` again (exported, an
  empty Binder; manifest), and `activity.finishActivity(7311)` when the token arrives. `MainDrawer` closes the drawer after
  a sign-in from the account card (`DrawerLayout.b()` = closeDrawers in the bundled obfuscated copy).
- Drawer card (`main_drawer_header.xml`, `DrawerAccountCard`): texts take the whole width; signed out the title and
  line wrap (2 lines); signed in name, @login and e-mail are single-line marquee (`setSelected(true)`, repeat forever);
  "Sign out" sits under them.
- `GitHubProgress` (opaque glass sheet from `GitHubViews.progressSheet`: icon + title, percentage in Sora 34sp, 6dp
  orange bar 0-1000, step line "N% • …"; "Hide" keeps the work running with a toast at the end; "Done" closes after
  1.6 s; failures stay in the error colour). Backup: `CloudSync.backUpFromMenu` shows it; `upload(scId, options,
  progress)`: 5% repository, 15% packing (what the boxes include), 30% packed size, 30-90% upload bytes
  (`CloudBackup.UploadProgress`, `GitHubHttp.postBlob(..., Progress)` through `CountingStream`, reports every 32 KB),
  92% saving, then done. Import: the sheet closes, `startImport` shows the progress, downloads run on their own threads
  (`background`: the sheet's executor stops with the sheet), 5-80% bytes (`GitHubHttp.download(..., Progress)`:
  Content-Length via `getContentLength()`, else the index size, else a slow creep), 82% downloaded, the libraries
  question (not cancelable), 88% restoring, `BackupRestoreManager.doRestore(file, libs, origin, RestoreDone)` (no
  ProgressDialog of its own; refused by AuthGuard → done(false)).
- `ProjectLinks`: `data/<sc_id>/github_backup.json` {url, path, time} written atomically after each upload to GitHub
  (`CloudBackup.link(path)` = github.com/<owner>/SketchwareAI-Projects/blob/<branch>/<path>); only github.com links read
  back, numeric ids only, never written for a missing project folder. `myprojects_item.xml`: `ob_share_link` (34dp
  glass pill, `ob_ic_share`) beside `tv_published` (#id); `GC` shows it when a link exists; a tap copies the link and
  opens Android's share sheet.
- `GitHubImportDialog`: ⓘ in the header → `showGuide` (`GitHubViews.importGuide`: three step cards with an orange number
  disc and diagrams built from the app's own views: the share button next to "#663", the link field with Import, a found
  backup with Import; the tap targets ringed in orange); backups a repository / folder link holds are rows with Import in
  the sheet (`found`); an own backup's versions are a glass sheet of rows ("Newest backup", "Backup N").
- GitHub component (`project.GitHubCode`): type **2026** (above 36, so `%m.GitHub` menus list it like a custom
  component), type name "GitHub", class `SketchGitHub` (class info `Component.SketchGitHub`), built in like AsyncTask in
  `ComponentsHandler` (id, typeName, name, icon `ob_c_github`, description, docs, buildClass, add, var, class);
  `Lx` field `private SketchGitHub <name>;`, initializer `<name> = new SketchGitHub(this);`, listener
  `<name>.setListener(new SketchGitHub.Listener() {…});` (`gitHubListener`), events `onGitHubUserLoaded`,
  `onGitHubReposListed`, `onGitHubFileDownloaded`, `onGitHubFileUploaded`, `onGitHubWorkflowTriggered`, `onGitHubError`
  (`oq` lists, `EventsHandler` icon / description / parameters / spec); `yq`: INTERNET for the component, `SketchGitHub.java`
  from `GitHubCode.helperClass` when any code mentions the class (`addCodeHelper` with a marker: the field and the
  initializer have no "SketchGitHub."). Blocks (`ExtraBlocks`, palette 101 "GitHub", `ExtraPaletteBlock` shows a hint and
  the guide when the screen has no GitHub component): gitHubSetToken, gitHubHasToken, gitHubGetUser, gitHubListRepos,
  gitHubGetFile, gitHubUploadFile, gitHubTriggerWorkflow; `BlockGuide.GITHUB_BLOCKS`. The helper
  (`resources/com/sketchware/ai/project/SketchGitHub.java.txt`, Java 7, min SDK 19, ASCII, framework + org.json only):
  each call on its own thread, answers on the main thread unless the Activity is finishing / destroyed, abstract
  `Listener` with six methods, `explain()` turns GitHub's answer into a sentence (no network, 401 token, 403/429 rate
  limit with the reset time vs missing permission per action, 404, 409, 422 with the workflow_dispatch hint, 5xx).
  `GitHubCode.tokenWarning` → `jq.gitHubTokenWarning` → `BuildWarnings.begin` warns when generated code holds
  `gh[pousr]_…` or `github_pat_…`; `BuildDoctor.gitHub` adds advice for compile errors around SketchGitHub.
- Tests: fixture 934 `github` (`Fixtures.gitHubBlocks`), `h.CallGitHub` (GITHUBCALL), lane item `ghcall`;
  `GitHubTest` (social addresses, SOCIAL_SCRIPT has no field reads, ProjectLinks), `GitHubClientTest` (upload / download
  progress, link), `tools/jvm-tests/social-script-test.js` + `SocialScriptPrint`, render `V202Test` + `setup_v202.sh`.
- Review fixes (three passes, all before the final build): `GitHubProgress.hidden()` also counts a gone screen (the
  result is then a toast through the application context) and `done()` closes by itself only while the sheet's window has
  focus (after Home it stays at 100% / "Done"); import download ends go through `downloaded()` / `failed()` (a toast after
  "Hide" or when the screen went away, with the .swb's folder); `ProjectLinks` stores `sc_id` and `login`: a link counts
  only for the project id it was written for (the file travels inside .swb backups, so restored or duplicated copies,
  which get new ids, show no foreign link) and not while another account is signed in (`get(scId, login)`), and
  `onChanged` (one weakly held Runnable, `GC`'s adapter refresh) shows the button right after a backup;
  `toDevicePageIfSignedIn` never runs during GitHub's 429 pause (also re-checked in the posted load), once per landing
  (3 s) and never on the page Back reached (`backPending` → `backUrl` at the next commit, within 10 s);
  `socialHandoff` opens no second tab while the browser is in front; `KeepAliveService.onStartCommand` stops itself;
  the token warning also scans `files/java` and is a two-line toast on every export (Run builds list the full advice);
  the helper drops events into a fragment that is no longer added (`isAdded` by reflection; R8 renaming it = delivered as
  before), `token` volatile, a TLS 1.2 note for Android 4.4.

## v2.0.3 additions (15 Nov 2026): a session that stays, backups that finish, Public / Private, sample project

Owner's 15 Nov directive "CRITICAL STABILITY & UX OVERHAUL (v2.0.3 / versionCode 204)" after the v2.0.2 phone test (12
screenshots: signed out by itself, "Getting a sign-in code…" with a blank page, a backup stuck at 1%, a large one failing
at 90%, the import list on "Loading…", drawer text drawn over itself, a black recents card). Record:
`engineering/v2.0.3-verification.md`.

- Session (never removed by itself): `TokenStore.token()` never deletes; it tries the binding as read now and the same
  with ANDROID_ID or the certificate unreadable ("?"), re-seals with the full binding (compare-and-set under the shared
  `LOCK`: a new sign-in or a sign-out wins), and `problem()` says why a token can't be read. Writes (`save`, re-seal,
  `clear`) take `LOCK`; reads don't, except the token + user id pair. `clear()` only from the card's Sign out and a
  cancelled sign-in. `GitHubSession.markRefused(context, note, sealedKey)`: GitHub's 401 marks the sign-in that was used
  (`sealedKey` = hash of the stored value), never signs out; `recheckOnline` (6 h) marks too; `Profile.problem`,
  `repositories` (public_repos + total_private_repos), `checkedAt`. `SignIn`: `DeviceFlow.start(clientId, scope, Tries)`
  with 10 s / 15 s timeouts and pauses {1.5 s, 4 s} (three tries on IOException or 5xx), `startNote`; the generation
  check and the token save under one lock; `TokenStore.prepareKey()` before it. FLAG_SECURE: never set in the app.
- Sign-in window: GitHub's device page loads at once (`devicePageAskedAt`); a code arriving within 15 s of that request
  waits for it (`devicePageCheck` asks again if no page started); fills only on the device page, not on GitHub's sign-in
  pages (no reload under a password) and never on WebView's error page (`pageFailed`: set in `onReceivedError`, cleared
  in `onPageStarted`; a code then loads the page again).
- Drawer card (`main_drawer_header.xml`, `DrawerAccountCard`): top row picture · "GitHub" + state (Connected / the problem
  in `ob_error`) · Sign out; details (name, @login, e-mail, "N repositories · checked HH:MM" or the problem, "Sign in
  again") wrap (`BREAK_STRATEGY_SIMPLE`); the v2.0.2 marquee is gone.
- `CloudSync` job queue: `Job` (scId, kind BACKUP / MAKE_PUBLIC / MAKE_PRIVATE, options, user, afterRunning, watchers,
  results); taps before automatic jobs, a second tap joins (and promotes) a waiting job, `afterRunning` jobs never join a
  running one; one worker thread, `finally` frees `current`, `Throwable` caught (an Error ends the job, not the queue);
  IOException / passing errors / sign-in problems keep a backup pending. `projectSaved(scId, leaving)` (DesignActivity's
  `ProjectSaver` and `SaveChangesProjectCloser`): signed in + System Settings key 2 "Back up to GitHub after saving"
  (`P12I_github_backup`, default on) + the content `fingerprint` (SHA-256 of names and contents of `data/<sc_id>`, files
  over 4 MiB by size, `github_backup.json` left out) differs from the account's last backup; a Save without leaving
  waits 5 minutes after the last backup; a follow-up with nothing new is skipped in `run`. `upload` packs with its own
  `hC`/`eC` (`setReadSavedDataOnly`) and packs again (≤ 3) when the fingerprint or `stamp` (sizes + times) changed while
  packing. A 401: `signInWorks()` (GET /user) → "refused once, try again", else the mark and the backup waits.
- Parts (`SwbParts`, `CloudBackup.upload`): over 4 MiB = 4 MiB parts `<swb>.parts/NNN`, each `POST /git/blobs` streamed
  from the file (`GitHubHttp.postBlob(path, token, file, offset, length, progress)`), its returned sha compared with the
  git blob id computed here, three tries (IOException, 5xx/408, a 401 while GET /user works; pauses 2 s / 6 s); one tree
  (parts, `<swb>.parts.json` manifest {kind, format 1, name, size, sha256, partBytes, parts[path,size,sha]}, index.json,
  `sha: null` deletions of paths the backup no longer uses), one commit, the ref PATCHed (two retries on 422). One .swb
  ≤ 4 MiB: the file itself and a one-part manifest; the share link names the .swb (parts: the manifest). Download: the
  .swb at that commit first (a backup in parts removes it, so a .swb beside a manifest is newer), else the manifest's
  parts by blob id (`Accept: application/vnd.github.raw`, checked against the real API; a JSON answer up to 4/3 + 64 KiB
  is decoded), joined, size + SHA-256 checked before the rename. Import: manifests listed, a .swb and its manifest once
  (by the .swb), a .swb link falls back to its manifest, `history` merges both paths. Manifests from links are capped
  (parts ≤ size / 1 MiB + 1, each ≤ 16 MiB, total = size ≤ 95 MB).
- Import list (`GitHubImportDialog`): the list saved on the phone shows first (`cachedList`, `LIST_LOCK`), GitHub's answer
  replaces it in the background (newest first); a backup from this phone is added at once (`rememberInList`).
- Public / Private (`PublicCopy`, `ProjectLinks`, `GC` pills `ob_visibility` + `ob_share_link` in `ob_github_pills`):
  a project's own repository `SketchwareAI-Public-<name>[-2…-9]` created private with `auto_init` and the description
  "Sketchware AI public copy <12-hex id>: …", the newest backup uploaded, the record set public, then `PATCH private:false`
  (a refused PATCH sets the record back). The record (`github_backup.json`: visibility, public_repo, public_url = the
  repository page, public_path, public_id, public_number = GitHub's repository id) counts only for its own sc_id and the
  account that wrote it; a restored / imported .swb drops it. The copy is found by `GET /repositories/{number}` (owner
  must be the signed-in login; renames followed; the old name's 301 is "taken" for a new copy), records without a number
  by name + description id. Later backups of a public project update the copy; a copy gone or private on GitHub sets the
  record private with a remark. Private = `PATCH private:true` on the copy (nothing when it is gone or already private).
  `SketchwareAI-Projects` is refused by name in every letter case by every PATCH.
- GitHub blocks: `ExtraBlocks` / `GitHubCode.PALETTE_COLOR` #1F6FEB (`gitHubHasToken` #2EA043); `BlockGuide`
  `gitHubActions`: "Open GitHub token page" (`TOKEN_PAGE` github.com/settings/tokens/new?description=…) and "Create
  GitHub sample project". `project.GitHubSample`: "GitHub API Demo" (`GitHub_API_Demo`, com.my.githubdemo) through
  `NewProjectSetup.create`, then views, `ComponentBean(2026, "github1")`, String variable `userName`, events and blocks
  (own `hC`/`eC`, saved with `m()`/`j()`); `seedOnce` from `GC.onCreateView` (preference `github_sample_seeded`, only when
  no project of that name exists); fixture spec `ghsample`.

## v2.0.4 additions (16 Nov 2026): one repository per project, versions as releases, publish, restore screen, service

Owner's 15 Nov directive "CRITICAL ARCHITECTURE, UI & PERFORMANCE DIRECTIVE (v2.0.4 / versionCode 205)" with 12 phone
screenshots of v2.0.3, re-sent with additions after worker restarts; the last version governs (it replaced a
"single-device session lock / auto-logout of the old phone" with multi-device discovery, so no lock was built). Record:
`engineering/v2.0.4-verification.md`.

- `github.ProjectRepo`: each project backs up into its own private repository `SketchwareAI-<name>` (`repositoryName`:
  GitHub's name characters; "Projects" and names starting "Public-" get "-App"; taken names → `-2` … `-20`), created
  with `auto_init`, the description "Sketchware AI project: <name> (<package>). …" (`MARK`) and the topic
  `sketchware-ai` (`TOPIC`, always first in `setDetails`, so discovery survives a rewritten description). Found again by
  GitHub's repository number (`GET /repositories/{n}`, owner = signed-in login, never `SketchwareAI-Projects`; renames
  followed and written back). A version = a release tagged `tagFor(versionName, versionCode)` = "v<name>-<code>"
  (`[A-Za-z0-9._]`); its body = the notes + a hidden facts line `<!-- sketchware-ai {kind, format 1, time, versionName,
  versionCode, size, sha256, standard, swb, apk} -->` (`facts`, `body`, `parse`; releases without it are ignored).
  `upload(Backup, UploadProgress)`: drafts left by a cut-off backup deleted; the same tag again = that release updated
  (new assets, then the old ones deleted; the Standard flag kept; review M1: without a newer APK the version keeps its
  APK); else a draft release, the assets, then `PATCH draft:false`. Assets go raw to the upload host
  (`GitHubHttp.uploadAsset`: `POST uploads.github.com/repos/o/r/releases/{id}/assets?name=`), ≤ 16 MiB as one
  `<name>_<yyyyMMdd-HHmmss>.swb`, larger as `.swb.partNNN` (16 MiB, git blob id each) + `.swb.parts.json` (`SwbParts`
  manifest), the last built APK (`mysc/<id>/bin/*.apk`, newer than the recorded `apk_time`) as
  `<name>_<tag>_<stamp>.apk`; each asset checked by size, three tries (IOException, 5xx/408, 422 = a lost answer: the
  half asset removed first, 401 while GET /user works). `prune()`/`keepAtMost`: at most 5 versions (`MAX_VERSIONS`), the
  oldest non-Standard go with their tags (`DELETE /git/refs/tags/…`). `setStandard(id, on)`: one Standard per repository
  (facts edited). `setPrivate` = one `PATCH {private}`; `setDetails` = PATCH description (≤ 350, one line) + `PUT /topics`;
  `putFile` = contents API (unchanged text = no commit). `download(token, owner, repo, Version, target, progress)`:
  `GET /releases/assets/{id}` with `Accept: application/octet-stream`, redirects followed by hand
  (`GitHubHttp.downloadAsset`, the token only to api()/uploads() hosts, never to the file host), parts joined and checked
  (blob id per part, size + SHA-256 of the whole) before the rename; a public repository works without a token.
  `listOwn(token)`: `/user/repos?affiliation=owner` (≤ 10 pages), names with the prefix and the topic, the mark or a
  v2.0.3 `SketchwareAI-Public-` name; never the backup repository. `gone(token, number)` (404 vs moved).
- `github.ProjectLinks` (`data/<sc_id>/github_backup.json`): repo, repo_number, visibility, url (= the repository page),
  login, sc_id, time, tag, options, fingerprint, apk_time, standard_tag; v2.0.3 public_repo / public_number adopted as
  the project's repository. Fields count only for their own sc_id and the account that wrote them. `repoFound`,
  `saved(scId, login, tag, options, fingerprint, apkTime)` (only on an existing record), `visibilityNow`, `standardNow`,
  `linkRestored` (refused while another project on the phone links that number: two projects never share a repository).
- `project.ProjectDiffAnalyzer` (offline, no network, no AI): `snapshot(scId)` from the saved files with own `hC`/`eC`/`iC`
  instances (screens → widgets id→type, components id→type, events → block signatures (opCode|spec|parameters hash),
  libraries, local libraries, permissions, resource counts, version, min/target SDK, Material 3) kept as
  `data/<sc_id>/snapshots/v<name>_<code>_snapshot.json`; `previous` = the newest kept one of another version;
  `compare` → ✨ New Features (screens, widgets, components, events, libraries, permissions, resources), 🛠 Logic & Bug
  Fixes (blocks changed per event: the larger of gone / new signatures), ⬆️ Upgrades (version, min/target SDK, Material 3),
  🧹 Removed; `releaseNotes` (first version: a summary), `commitMessage`, `changelogEntry`/`changelog`, `description`,
  `topics`, `topic` (GitHub's form), `readme`, `title`.
- `github.PublishDetails` (`data/<sc_id>/github_publish.json`): title, description, topics, README (on/off, text), notes,
  CHANGELOG entries; generated values replaced on refresh unless edited (`*Custom`).
- `github.PublishActivity` (the Private pill; "Edit public details" from the Public pill): analyzer-filled title,
  description, topic chips (`sketchware-ai` kept, no ✕), Include README + editable preview, "What's New & Fixed"; Publish
  Public = `CloudSync.publish` (first version backed up when there is none, description + topics, README.md +
  CHANGELOG.md, the newest version's notes, then `PATCH private:false`). Public projects update README / CHANGELOG /
  description on later backups. The Public pill's sheet: "Edit public details", "Switch to Private" (`makePrivate`: one
  PATCH on its own thread; refused while a backup or Publish of that project runs; "Private" only when GitHub changed or
  the repository is gone).
- `github.CloudSync` rewritten: kinds BACKUP / PUBLISH / IMPORT; two lanes (work: backups and publish; imports), job key,
  join / promote as in v2.0.3, `cancel(key)` (queued: removed; running: `GitHubHttp.cancel(thread)` = interrupt +
  disconnect; `apiOnce` and uploads stop on the interrupt flag, cleared under the lock before a job's thread is
  published), `retry(key)` / `canRetry(key)` (only jobs that failed in a retryable way, stored without their screens),
  per-job remark, `visibilityChanged` / `visibilityChangedSince` (a backup never writes an older visibility over a
  Switch to Private or Publish that happened while it ran). `upload` = analyzer snapshot + notes → `ProjectRepo.ensure`
  → `PublishDetails.refresh` → pack (as v2.0.3) → APK when newer → `repo.upload` → record → docs when public.
  `runImport`: staged download into `backups/staging/<hex>`, restore under `ProjectIds.RESTORE_LOCK` (one restore picks
  its id and unpacks at a time; `BackupRestoreManager` takes it too, on `THREAD_POOL_EXECUTOR`), `linkRestored` for the
  account's own repositories.
- `github.CloudSyncService`: foreground service `foregroundServiceType="dataSync"` (manifest: FOREGROUND_SERVICE +
  FOREGROUND_SERVICE_DATA_SYNC, asked for by the owner), notification channel `github_sync` (low importance): job,
  percentage, bytes and speed, Cancel; at the end its own notification (Done / Didn't work + Retry, Open on GitHub) via
  the non-exported receiver `CloudSyncService$Actions`. State under `LOCK`: `started`, `foreground`, `instance`,
  `generation` (reviews: never stopped before `startForeground`, a stopping instance never clears the next start's
  flags, a stale idle() does nothing); `onTimeout` (Android 15's dataSync limit) detaches and stops; a start refused in
  the background = the notification alone.
- `github.GitHubProgress`: Hide (asks POST_NOTIFICATIONS once on Android 13+), Cancel, Open on GitHub while running; Done,
  or Retry + Close after a failure (Retry only when `canRetry`); pills one line with an ellipsis (`GitHubViews.oneLine`:
  never `setSingleLine` on a centred pill), progress pills at their own width.
- `github.GitHubBackupsActivity` ("Restore projects", full screen, `ob_github_screen.xml`): tabs GitHub / On this phone;
  link import (a project repository: its versions; a folder or .swb: v2.0.3 import; another person's: libraries only on
  request; a private one signed out: 404 → sign-in), the account's projects from `RepoCache` at once then GitHub, each
  with Private/Public badge, Share, version chips (`ordered`: Latest #1, ★ Standard #2, then newest; ≤ 5), Restore and
  ★ Set as Standard / Unpin; older backups of `SketchwareAI-Projects` (read only); "Open shared GitHub links here"
  (Android 12+ Open-by-default settings). On this phone: Pick a .swb, every .swb in the backups folder (≤ 2 levels, no
  staging), Restore; nothing deleted by the app. `github.RepoCache` (SharedPreferences: repositories and versions per
  account) + `discover(context)` after sign-in (`SignIn`).
- `github.GitHubLinkActivity` (exported, singleTask): `https://github.com/<owner>/SketchwareAI-*` (and www.) and
  `sketchwareai://import?url=…` / `sketchwareai://import/<owner>/<repo>` → the restore screen with the link (reads only;
  nothing restored without a tap). Android 12+ opens github.com links in the app only after the user adds them under
  "Open by default".
- Cards: `myprojects_item.xml` row A = name (one line, marquee) · #id · target pill; row B = package · version (weight 1,
  middle ellipsis) · Public/Private + Share. Drawer card: signed in = "GitHub" 14sp / "Connected" 11sp single lines,
  @login and e-mail rows with copy buttons (`ob_ic_copy`).
- GitHub component (generated `SketchGitHub`, Java 7, ASCII, min SDK 19): `name()` strips "@" and profile / repository
  links; `listFiles(owner, repo, folder)` (contents API → name, path, type, size, downloadUrl, owner, repo),
  `listReleases(owner, repo)` (tag, name, published, files with url / apiUrl), `downloadFile(url, nameOrPath)` (redirects
  by hand, token only to api.github.com / raw.githubusercontent.com, into the app's own Download folder, `.part` then
  rename), `showList(ListView, json)` (rows per list; a repository lists its files, a folder opens, a file or release
  file downloads under its own name), `describe(json)`; events `onGitHubFilesListed`, `onGitHubReleasesListed`,
  `onGitHubDownloadProgress`, `onGitHubFileSaved`; blocks gitHubListFiles, gitHubListReleases, gitHubDownloadFile,
  gitHubShowList, gitHubDescribe (#2EA043). "GitHub API Demo" (`GitHubSample`): ListView `listview1`, user text through
  `describe`, repositories and files in the list, download progress and saved file shown. A block text field starting
  with "@" is a link to another block in Sketchware (upstream generator, `Fx.a(String, int, String)`): typed "@user"
  becomes ""; from an input field or a variable the "@" is removed at run time.
- Tests: `tools/jvm-tests/GitHubClientTest` fake GitHub gained releases, uploads (`/api/uploads/…`), the file host
  (`/objects/{id}`, records an Authorization header), topics, contents JSON, `/user/repos`, signed-out reads of public
  repositories (404 for private ones); `h.GitHubTest` (records, analyzer, deep links), `h.CallGitHub` (helper),
  fixture 934 (new blocks and events, a ListView); renders `tools/layoutlib-renders/setup_v204.sh` + `V204Test` run the
  real Restore and Publish screens on a window-less stand-in base.

## v2.0.5 additions (16 Nov 2026): stability release for the v2.0.4 phone report

Owner's 16 Nov directive "CRITICAL BUG-FIX, DIAGNOSTICS & UX HARDENING (v2.0.5 / versionCode 206)" with 9 phone
screenshots of v2.0.4, sent together with a "v2.1.0 / versionCode 206" directive (HoneyDrop bottom bar, full-screen tabs,
Play Store / Community, single-device lock, startup update gate). The v2.0.5 text says the stability release comes
before any navigation redesign, so v2.0.5 holds the bug fixes of both and the v2.1.0 overhaul is the next release
(versionCode 207). Record: `engineering/v2.0.5-verification.md`.

- Text fields (`GitHubViews.input(hint, multi, lines)`): built with `android.R.attr.editTextStyle` (an `EditText` made
  with defStyleAttr 0 isn't focusable in touch mode: no keyboard on a tap, the "Public details not working" report),
  `Ob.Text` appearance, `ob_field`; used by `PublishActivity` (title, description, README, topic) and the import sheet.
  `GitHubViews.linkBox(out, go)`: the link field (URI keyboard, Enter = go) with a 34dp orange **Paste** pill inside its
  right edge: clipboard → field → `go` (`GitHubBackupsActivity.importLink` on its own `lookups` executor).
- Project names: `project.ProjectNameValidator` (extends `a.a.a.MB` like upstream's `VB`; same rules and messages: a
  letter first, letters / digits / `_`) with `MAX = 64` (VB: 20) in `MyProjectSettingActivity` and `ui.NewProjectActivity`.
  The name is the workspace name (`my_ws_name`: .swb file names, the project's repository name
  `SketchwareAI-<name>[-n]` = at most 13 + 64 + 3 characters, under GitHub's 100).
- `CloudSync` queue: a tap's job (`user`) that finds an automatic **BACKUP** running sets `deferred` on it and stops its
  transfer (`GitHubHttp.cancel`); the loop puts a deferred job that ended unfinished back right after the taps' jobs
  (`requeueDeferred`, same watchers and results). Waiting text names the running job and its percentage ("Waiting:
  Backing up X (34%) pauses for this one…"). Watchdog (`watch`, one daemon thread while a lane has a job): no step and
  no byte for `STALL_MS` (5 min; not while packing) → `stalled`, transfer stopped, "Stopped: nothing moved …", Retry
  kept, a backup goes to pending; a stopped job whose thread hasn't returned after `ABANDON_MS` (20 s) is left behind
  (`abandoned`, its end told by the watchdog, a deferred one re-queued as a copy) and the lane gets a new worker; a left
  worker ends at its next loop (`lane.worker != me`). `job.finished` guards a second end.
- Checkpoint resume: the packed backup is moved to `<backups>/staging/resume/<scId>.swb` with `<scId>.json`
  (fingerprint, boxes, version name / code, size, time) and reused while all match and it is < 7 days old; removed when
  the version is on GitHub. `ProjectRepo.upload`: asset names from the file's time (stable across tries), a draft with
  the same tag and SHA-256 is kept and reused (other drafts deleted; a published release of that tag wins), assets
  already there with the right size and state `uploaded` are not sent again (`present`, `skippedLast`); `PART_BYTES`
  8 MiB (16 before).
- 401: `GitHubSession.isRefused(context)` (the refusal mark belongs to the saved sign-in); `CloudSync.run` fails a
  backup / publish at once while refused (no request, the job waits as pending), imports read without the token;
  `signInNeeded` → `GitHubProgress.failedSignIn` ("Sign in again": `GitHubInAppAuthDialog`, then the job's Retry).
  `refusedNote` explains GitHub's limit of 10 tokens per user / app / scope (an 11th revokes the oldest unused one, else
  the least recently used), the likely cause after
  repeated sign-ins.
- `github.SyncDoctor`: one "What to do: …" sentence per failure code (401 / refused, 403, 404, 409, 422, 429 / rate,
  5xx, too_large, bad_* (incomplete or newer-format backup), keystore) unless the message already says what to do; out
  of memory and a full phone (`isStorageFull`, ENOSPC: no longer reported as "no connection").
- `GitHubProgress`: Hide | Cancel in one row, Open on GitHub (full width) under them; Android 13+: POST_NOTIFICATIONS
  asked once when the sheet opens (pref `notifications_asked`), otherwise a line "Notifications are off …" with Turn on
  (`ACTION_APP_NOTIFICATION_SETTINGS`, fallback app details). Open on GitHub → `RepoViewerActivity`.
- `github.RepoViewerActivity` (new, manifest): `open(activity, link)` (a GitHub link parsed by `GitHubImport.parse`, else
  the browser), `intent(context, owner, repo)` (notification, `GitHubLinkActivity` for repository links). Reads `GET
  /repos/{o}/{r}` (404: private / wrong link, Sign in), the info card (badge, description, updated, stars, owner, topics
  in `PublishActivity.FlowLayout`), Open in browser / Share, versions (`ProjectRepo.versions`, ordered as the carousel)
  with Restore (own: with libraries; another account's: the libraries question) and APK (`GitHubHttp.downloadAsset`
  into `.sketchware/downloads`, then the installer through the app's FileProvider `<pkg>.provider`), README.md (contents
  API, base64, monospace, first 400 lines), the files at the top (`fileSize`). The token only when signed in and not
  refused.
- `GitHubBackupsActivity`: versions in one horizontal row, `ordered` = the pinned (★ Standard) version first, then
  newest first, ≤ 5; `badgeOf` "★ Pinned" / "New" / both; "+N older versions →" scrolls the row; Pin / Unpin pill.
- `PublishActivity`: editable fields; for a public repository **Save & update** = `CloudSync.saveDetails` (own thread,
  progress sheet steps: description + topics, README, CHANGELOG, notes), otherwise Publish as in v2.0.4.
- `ProjectDiffAnalyzer` format 2: per screen `moreBlocks` (eC.g names), `variables` (eC.e: name → Boolean / Number /
  String / Map or the custom type's class), `lists` (eC.f), `parts` (toolbar, navigation drawer, floating button, full
  screen, landscape / rotates from `ProjectFileBean`); project `customViewNames`, `files` (java, assets, resource,
  native counts under `data/<id>/files`). `compare` reports the new parts only when both snapshots are format 2.
  README: `## Screens` table + `### MoreBlocks`; description names the screens and what the app uses (components,
  notable widgets, libraries), Android min–target, Material 3.
- Drawer card: the top row keeps "GitHub" and its state (Sign out moved to a bottom row beside Sign in again); the name
  line only when the profile has a name; @login and e-mail one line each with a middle ellipsis (copy buttons copy
  them whole).
- Demo row: `project.Demos` (`isDemo`: package `com.my.githubdemo` or `data/<id>/demo.json`; `version`, `mark`, `sort`
  newest demo version first), `GitHubSample.DEMO_VERSION` 3 marked at creation; `GC.a(boolean)` moves demos out of the
  list into `ob_demo_section` / `ob_demo_row` (`myprojects.xml`) with `ui.DemoCards` (tap opens, long press → Delete
  with `lC.a` on a thread).

## v2.1.0 additions (20-21 Nov 2026): the v2.0.5 phone fixes and the Home overhaul

Owner's 16 Nov "MASTER DIRECTIVE (v2.1.0 / versionCode 207)" with 7 v2.0.5 phone screenshots: part A (six phone bugs),
part B (bottom bar, full-screen tabs, Play Store / Community on GitHub, single-device lock, sequential update gate),
part C (audit, suites, main, binaries). Record: `engineering/v2.1.0-verification.md`.

- Home (`com.besome.sketch.MainActivity`, `res/layout/main.xml`): no `DrawerLayout` / `MainDrawer` / FAB (the FAB view
  stays in the layout, 0dp and GONE; `MainDrawer.java` is left unused); root `CoordinatorLayout` with
  `clipChildren=false` (the drop falls above the bar); `ui.HoneyDropBar` (`ob_honey_bar`, 62dp, margins 12/12/10).
  Back on Home = `finish()`. `openTab`: Create → `GC.openCreate()` (storage access and `AppControl.updateRequired()`
  first; `NewProjectActivity` as a tab), GitHub → `GitHubBackupsActivity`, Play Store → `PlayStoreActivity.tab`,
  Community → `CommunityActivity.tab`, Settings → `ui.SettingsActivity`, each through `ui.TabScreens.asTab` (extras
  `sketchware_ai_as_tab`, `sketchware_ai_tab_name`); `onBackPressed` of a tab screen → `TabScreens.onBack` ("Do you
  want to close X?" Cancel / Close). `onResume`: the bar flows back to Home, `setScrolledAway(false)`, AuthGuard only
  once the gate passed, `SingleDeviceGuard.showSignedOut` + `check`. `GC` scroll listener → `projectsScrolled(down)`.
- `ui.HoneyDropBar`: six tab views (30dp icon + Sora bold label, `GitHubViews.fitOneLine(label, 6, 9.5)`); `onDraw`
  glass rounded rect + honey pill (`LinearGradient` #FFD166 → #FFB347 → #FF6D24; head eased, tail spring-settled);
  `dispatchDraw` waves (damped cosine on the top edge from t 0.34) and the teardrop (falls 0-0.34, squashes to 0.46,
  melts); `ValueAnimator` 760 ms; listener fires at 0.34 (one tap at a time); `SoundPool` (`res/raw/honey_drop.wav`,
  22.05 kHz mono, 9,746 bytes, made here) only with `RINGER_MODE_NORMAL` and the switch on (prefs `sketchware_ai_ui` /
  `honey_drop_sound`); `frameForTests(from, to, fraction)` for renders.
- `ui.SettingsActivity` (manifest): the account card (`drawer_account` out of `main_drawer_header`, `DrawerAccountCard`),
  Back up after saving (`P12` / `CloudSync.AUTO_BACKUP_KEY`), Restore projects, the sound switch, App updates (version
  without hash; Update to vX with `AppUpdater`, else a "Latest" / "Not checked yet" badge; "Coming soon" badge with
  `coming_soon`), and the drawer's items.
- `CloudSync` lanes: `work`, `work2` (taps), `auto` (automatic backups), `imports`. `laneFor`: an import → imports; a
  project with a job in a lane → that lane; a tap → the less loaded tap lane; else auto. A running automatic BACKUP
  pauses only for a tap's job in its own lane (same project). A deferred job is requeued in the `finally`'s lock that
  frees the lane. Several running jobs → one notification (`combined`: "N GitHub jobs", "title · P%" per line, the least
  percent; key null → no Cancel, `BigTextStyle`); refreshed when one of them ends.
- `GitHubHttp.quick(millis, call)`: a ThreadLocal timeout for connect + read and no read retries (one try) on that
  thread; `RepoViewerActivity` (10 s, page saved in `files/repo_viewer/<owner>__<repo>.json` shown first, "Saved on this
  phone · checking GitHub…", `whyFailed` → "GitHub didn't answer within 10 seconds"), Play Store, Community.
- 401: `CloudBackup.refusesSignIn` and `GitHubSession.recheckOnline` ask GET /user again after 3 s; refused only on two
  401s. `GitHubSession.noteSignedIn` (signed_in_at, accepted_at, signed_in_version, signin_count; the count survives a
  sign-out) → `refusedNote` adds "This sign-in: made … with … (sign-in N on this phone), last accepted …".
- `GitHubBackupsActivity`: tabs GitHub | Demo | On this phone (`EXTRA_TAB`, 12sp, `fitOneLine` 9-12sp, 38dp); compact
  cards; Paste → `importLink(true)` (front version: pinned, else newest; or the single .swb); Demo tab = `lC.a()`
  filtered by `Demos.isDemo`, `Demos.sort`, "#i Name", Open → DesignActivity, long press Delete, "Make the GitHub API
  Demo" when none. `GC` no longer shows the Demo row. `ProjectRepo.ordered` (moved from the activity).
- `AppControl` gate: `scheduleGate` (3.5 s after Home, once per process; from onCreate and onResume with storage
  access) → `check` → update notice (`force_update` only with a link → `updateRequired`, no Later, not cancelable) or
  `passGate` (sets `gatePassed`; AuthGuard when the screen has focus; `SingleDeviceGuard.check`). New fields
  `force_update`, `update_sha256` (64 hex), `latest_version_code`, `latest_version_name`, `coming_soon`.
  `showUpdateAgain` for Create while an update is required. `AppUpdater`: GitHub hosts over https only, redirects by
  hand, into `cacheDir/updates` (`provider_paths.xml` `cache-path name="updates"`), SHA-256 compared, reused after the
  "Install unknown apps" detour; no SHA-256 → the link in the browser (v2.0.5 behaviour).
- `SingleDeviceGuard`: `.device-session.json` in `SketchwareAI-Projects` {active_device_hash = SHA-256(install id |
  login), device, app_version, time}; install id in `getNoBackupFilesDir()/device_install_id`. `claim` (at sign-in)
  writes `claim_pending` + `signed_in_at` first; `check` (gate, then every 6 h) under one lock: `decide(there, mine,
  pending, signedInAt)` → claim / keep / signout (another phone's mark and no pending claim, or a pending claim older
  than that mark). Errors change nothing. `forget` at sign-out. This reverses v2.0.3's "never sign out automatically"
  only for this case (the owner's directive).
- `GitHubGraphQL.run` (POST /graphql; signed_out / http_401 / graphql / bad_answer); `PlayStore` (topic
  `sketchwareai-playstore`; search; `playstore/listing.json` + `playstore/screenshots` (raw.githubusercontent.com only,
  png/jpg/webp, ≤ 8); cache `files/playstore/apps.json`, `merge`; `publish`: refuses `SketchwareAI-Projects`, PATCH
  private:false + has_discussions:true, topics kept + store topic, screenshots `NN.jpg` and the page through the
  contents API, note when the front version has no APK; Discussions: `posts`, `thread`, `newPost`, `reply`,
  `reviewCategory`). `PlayStoreActivity` (list / app page / publish; app-wide `WRITES` executor for publish and posts;
  pictures cached in `cacheDir/playstore`, decoded with inSampleSize; full-screen viewer), `CommunityActivity`
  (smartapps000-hub/SketchwareAI's Discussions; thread page with reply). `RepoViewerActivity.restore/downloadApk/install`
  are static helpers the Play Store uses.
- `ProjectRepo.create` returns null on 422 "name already exists" (two lanes, same-name projects): `ensure` tries the
  next name.

## v2.1.1 additions (21 Nov 2026): app icons, a Play Store with counters, a native Community, glass bar, default icon

Owner's 21 Nov directive (v2.1.1 / versionCode 208) with 10 v2.1.0 screenshots, then two corrections the same day:
"this APK default icon, same as it is" (the stock green icon he photographed) and "original Android robot from image 3
on our Obsidian / orange glass card; the card keeps its monogram", plus "transparent glass droplet, splash and waves, no
gold; 4-stage physics; new honey_drop.wav". Record: `engineering/v2.1.1-verification.md`.

- **Default launcher icon** (`assets/resource/600/res.zip` → `drawable-xhdpi/app_icon.png`, 144 px, drawn by
  `tools/icons/make_default_app_icon.py`): Android Studio's round launcher icon element for element in the stock
  template's geometry (132 px circle, 108-unit viewport at 132/74 px per unit, centred; the background's interior grid
  lines 29-79 with their overlapping inner lines (the 19 / 89 lines at the circle's edge left out, owner), the 45° long
  shadow path, the robot path with eye holes, the soft drop shadow), recoloured: frosted slate-obsidian base
  (`#23262E → #16181E`, a faint `#FF6D24` ambient tint), hairline grid (0.5 unit, warm white at 9 %), the long shadow as
  a dark cast shadow (`#040406`, 58 % → clear), the robot as frosted white glass (the layers behind it blurred, a 30 % →
  18 % white tint, a trace of a brighter edge), a top gloss; no border (owner). The same image is the IDE's preview
  (`res/drawable-xxhdpi|xxxhdpi/ob_app_icon_placeholder.png`: New Project, project settings, Play Store tiles of apps
  without their own icon; the old vector is gone). The themed robot written as a project's own icon (first draft), a
  squircle and two round versions came before (the owner's corrections, 21 Nov).
- **Project icons everywhere** (`github.RepoIcons`): the phone's `resources/icons/<sc_id>/icon.png` (custom_icon), a
  .swb's `resources/icons/icon.png`, and GitHub's `playstore/icon.png` (contents API; written after every backup by
  `CloudSync` and by Publish; skipped when the git blob id is the same; read with the token for private repositories;
  `files/repo_icons/<number>.png`, a 404 remembered a day as `.none`, a saved icon asked again after a week; one quick
  request per card on the Restore screen's own `iconLoads` worker, none after a network error). Restore cards: the Home monogram tile
  (`GC.monogram`, now public) with the icon laid over it when found (`GitHubBackupsActivity.iconTile`).
- **Play Store** (`PlayStore`, `PlayStoreActivity`): app page order (owner's architecture directive): hero (`appTile`
  72 dp, name, @developer, version pill, `publishLine`) → `statsRow` → `actionsBox`, one row of capsules from
  `renderActions` / `addAction` (Install APK primary when the newest version has an APK, else .SWB; Demo with a video
  link; Try again after a failed read; GitHub) with `actionNote` under it → description → What's new → pictures →
  the counters' caption → reviews; `appTile` = the default APK icon under the app's own; search box in the header (`gs_tabs`; text filters the phone's list,
  IME Search = `PlayStore.search(token, text, user)` with an URL-encoded `q`, `@login` opens a creator); `App` version
  fields from `listing.json` (`version_name`, `version_code`, `action` published / republished / updated,
  `publish_number`, `changelog`, `published_at`, `stats_discussion`) → `status()`; app page with the icon
  (`iconUrl()` = raw.githubusercontent.com/…/playstore/icon.png through `picture()`, which also accepts GitHub's avatar
  host and remembers a 404 for a day), @creator, Downloads (`downloadsOf`: .apk/.swb `download_count`), Likes / Shares /
  Views (`Stats`, `reactions`: ❤️ / 🚀 / 👀 reactions on the app's "📊 <title> · Play Store" discussion, made at publish
  in General by `ensureStatsDiscussion`; `react` add/remove; a view counted once per account; signed out = "–"),
  What's new; creator page (`developer`: GET /users/{login} + two searches); publish form kept per repository in
  `files/publish_staging/<number>` (`Listing.load` / `saveForm`, `stagePicture` / `unstagePicture` / `renumber`, saved
  on every keystroke and in onPause on its own thread `STAGE`), the last chosen repository in prefs
  `playstore_publish/chosen_<login>`; `publish()` = PATCH public + Discussions, topics, icon, pictures, `removeExtraShots`,
  versions, `readPage`, stats post, store page (steps 5 → 97).
- **Publish as a job** (`CloudSync.STORE`, `Store`, `publishToStore`, `runStore`): progress sheet + dataSync
  notification; a 401 is confirmed with GET /user (as backups) and marks the sign-in refused; `resumeAfterSignIn()`
  (called by `SignIn` after a successful sign-in) re-runs every job in `signInNeeded` from `failed`, then the pending
  backups; the sheet's Sign in again `join(key, …)`s the run the sign-in started. Staged pictures are deleted only after
  a successful publish.
- **Community** (`CommunityActivity` rewritten, `PlayStore.feed`): repositories = smartapps000-hub/SketchwareAI, topic
  `sketchwareai-community`, the store's apps, the viewer's own repositories with Discussions on; one aliased GraphQL
  query for up to 15 (`GitHubGraphQL.runLenient`: data kept when another slot errs); stats posts left out; newest first;
  `files/community/feed.json` offline; New post → repository (only those with Discussions on) → category; thread authors
  open the creator page; "Open on GitHub" only on a discussion just read (never a Discussions page that is off).
- **Bar** (`ui.HoneyDropBar` drawing rewritten): the body path's top edge = crest (12 dp bump under the selected tab) +
  crater (Gaussian, 8 dp) + travelling damped waves (k = 2π/36 dp, front to both ends), corners windowed; glass
  teardrop / micro droplets / crown spikes / jet / beads (radial white fills 0x10 → 0x5E, white rims, specular dots,
  faint amber caustic); icon translation = dip then lift (9 dp) on the crest; warm pool under the lifted icon; DURATION
  1150 ms, LAND 0.26, CRATER_END 0.38, listener at 0.40. `res/raw/honey_drop.wav` from `tools/sound/make_honey_drop.py`
  (44.1 kHz 16-bit mono, 0.31 s, −3 dBFS).
- **Backups of the app**: `android:fullBackupContent` / `android:dataExtractionRules` exclude the shared preferences
  `sketchware_ai_github`, `_github_signin`, `_github_profile`, `sketchware_ai_device` (a token sealed by this phone's
  Keystore key can't be read elsewhere).
- `GitHubSession.answerOf` (the refusal note quotes "HTTP 401 …" once).

## v2.1.2 additions (21 Nov 2026): tabs kept, update card, sign-in record, code helper, honest uploads, letters in the app

Owner's 21 Nov directive "CRITICAL PERFORMANCE, UX & BUG-FIX (v2.1.2 / versionCode 209)" with 17 v2.1.1 phone
screenshots (9 points). Record: `engineering/v2.1.2-verification.md`.

- **Tabs** (`ui.TabScreens`): no "Do you want to close …?"; Back on a tab's first page starts Home with
  `FLAG_ACTIVITY_REORDER_TO_FRONT` and the tab stays behind it; Home re-opens a tab with the same flag (`TabScreens.reopen`),
  so it comes back scrolled where it was with its inner page; the tab screens take only the tab marks in `onNewIntent`.
  Create (`EXTRA_CLOSES`) is a form and closes on Back. Back on Home = `moveTaskToBack(true)` (Android 12+ behaviour; the
  kept tabs would otherwise surface).
- **Zero-lag reads**: Home's project list (`GC.a`) is read on a single-thread executor and applied with a generation
  number; the list read last in the process shows at once meanwhile. `ProjectLinks.read` (Home cards) keeps a record per
  file stamp + size (cleared on every write and `listChanged`); `PlayStore.cached` parses once per file version (copies
  handed out); `GitHubSession.profile` keeps the Keystore read per sealed sign-in and `warmUp` does it at start off the main
  thread; `AppControl.cached` parses once per text.
- **Update**: `UpdateCard` (glow heading, "vA → vB" pill, `update_reason`, up to three `update_notes`, orange Update; no
  Later and not dismissible when required); `AppUpdater` has no Cancel (Hide only; after an error Hide + Retry,
  `GitHubProgress.failedHideRetry`); `UpdateNotice` (channel "App updates": progress while hidden, "Tap to install" with
  the FileProvider URI, the error). app-control.json: `status_message_max_version_code` (an older version's plain dialog
  gets the text, the updated one doesn't show it again), `update_reason`, `update_notes`.
- **Sign-in across updates**: nothing in an update removes a sign-in; the only automatic sign-out is the one-phone rule.
  `SingleDeviceGuard` marks carry `device_key` = SHA-256("device:" + ANDROID_ID | login); a mark with this phone's key (or,
  written before v2.1.2, this phone's model) is this phone: "keep" / "claim", never "signout". `SessionLog`
  (`no_backup/session_log.json`, last 30: signed in, signed out with its reason, installed / updated with the sign-in's
  state) and the signed-out account card's line (`SessionLog.signedOutLine`).
- **Code helper** (`OtpAssist`): on https github.com `/sessions/verified-device` (Open Gmail + Paste & fill) and
  `/sessions/two-factor…` (not webauthn / recovery; Paste & fill): the copied 6- or 8-digit code (also "123 456") goes
  into GitHub's field the way typing does, then GitHub's own submit is pressed after 0.5 s unless the page sent the form
  itself; digits only (anything else refused before a script is made).
- **Upload**: `ProjectRepo.SINGLE_MAX_BYTES` 25 MiB (one asset; above it 8 MiB parts), `partsFor`; `CloudSync.UploadSpeed`
  (speed of the last ~8 s; "All N handed to the network · waiting for GitHub to confirm" when every byte is written; a note
  after 20 s under 30 KB/s while no screen of the app is in front). The stream was already fixed-length with 64 KB writes.
- **Letters in the app**: Play Store tiles (`PlayStoreActivity.monogramTile`: the app's letters under its real icon), New
  Project and project settings (`ui.MonogramDrawable`); the round Android icon is only the APK's default (template).
- **Screens**: Play Store title without the cut subtitle, a slim "Publish your app" row, @creator and the version pill on
  their own rows, four counters on one glass (Like / Share with a press foreground; Like always answers); Restore's Demo and
  On this phone cards in the GitHub card's shape (`GitHubBackupsActivity.slimCard`), no Demo paragraph; the bar's top
  corners sampled along their arcs with the edge offset added (`HoneyDropBar.onDraw`, window 0 only at the bar's ends):
  an edge tab's crest grows out of the corner (v2.1.1 had a 4-5 dp step where the arc ended).


## v2.1.3 additions (22 Nov 2026): parallel parts, retries by themselves, glass notifications, grouped Restore, Play Store hero, Follow

Owner's 22 Nov phone review of v2.1.2 (11 screenshots, in Telugu). Record: `engineering/v2.1.3-verification.md`.

- **Update card** (`UpdateCard`): "UPDATE AVAILABLE" Sora bold 26sp in `ob_error` (#FF8A65, the colour of a failed
  percentage), the version step and a REQUIRED tag (`ob_amber_pill`), the reason, "KEY FEATURES" with a 44dp amber line and
  numbered points (`update_notes`), "BUGS FIXED · MORE EFFICIENT" with ticks (`update_fixes`, new in app-control.json, up
  to three lines), Later (glass) beside Update (`ob_amber_pill_state`, `ob_ic_download` and the label centred together).
- **Uploads** (`ProjectRepo.sendAll`): a backup from `PARALLEL_MIN_BYTES` (3 MiB) goes as `PART_BYTES` (2 MiB) parts and the
  APK, `PARALLEL` (4) at a time on helper threads of the job (`GitHubHttp.adopt` / `disown`: a cancel, pause or watchdog stop
  of the job reaches them; `isCancelled` reads the parent's cancel); one rising sum of bytes (`Sent`); a part that fails for
  good stops the parts not started, the ones on their way finish (they count for the next try's resume). The parts list
  allows any part size up to 16 MiB since v2.0.4, so older versions restore these. A cancel that closed the connection is
  reported as a cancel (`GitHubHttp.uploadAsset`).
- **Retries by themselves** (`CloudSync.runWithRetries`): a BACKUP or IMPORT job that ends with an IOException other than a
  stop, `NoRetry` or a full phone runs again after 5 / 15 / 30 s and once Android has a network with internet again (up to
  2 more minutes each); "Connection lost (…). Trying again in N s (1 of 3); the backup goes on from where it stopped…". The
  packed .swb is the checkpoint (v2.0.5) and the draft keeps the parts already sent.
- **Notices** (`CloudSyncService`): the end notice of a job goes when that job runs again (`clearFinished`, at a tap and
  when a job starts; also the old v2.0.4-v2.1.2 id); end notices have their own id range (`finishedId`, 21000 + 12 bits).
  `GlassNotice`: the running / done / failed and the update notifications as a dark glass card (RemoteViews,
  `DecoratedCustomViewStyle`, `ob_notification_glass(_big).xml`: LinearLayout, ImageView, TextView, ProgressBar, plain
  shapes `ob_notif_card` / `ob_notif_tile` / `ob_notif_progress` only).
- **Restore** (`GitHubBackupsActivity.groupCard`): On this phone groups .swb files by project (`backups/<project>/`, else the
  name before " v1…" / "_v1…"; `phoneGroups`, `projectPart`, `versionPart`), Demo groups demos by name; each card: tile,
  name, the shown version's line, Restore / Open at the right of the title row; with more than one version a selector
  (`orderedItems`: a pinned one in front, else the newest; `markOf`: ★ Pinned / New) and ★ Pin (SharedPreferences
  `sketchware_ai_restore_pins`); Demo extras: Public / Private (opens Publish) and Share once the demo has a GitHub record of
  this account, else Delete. `slimCard` removed.
- **Play Store**: Publish pill in the header (`addPublishButton`, the row removed); hero = 52dp tile, title in `ob_orange`
  one line, publish line, pills @creator (opens the creator page) / version / Follow (`heroPill`), a hairline, the four
  counters inside (Like / Share press on `ob_pill16_state_clear`); actions Install APK, .SWB, (Demo when there is a video),
  GitHub, Review (`reviewTapped`); "About this app" with two lines (tap: all). Follow: `PlayStore.following` (GET
  /user/following/{login}), `follow` (PUT / DELETE), `lacksFollowScope` (403 / 404 without `user:follow` in X-OAuth-Scopes:
  "Sign in again to follow"); `DeviceFlow.SCOPE` "repo user:email user:follow". `downloadsOf` counts a version's parts list
  when it has no .swb.
- **Speed**: `HoneyDropBar.OPEN_AT` 0.14 (~160 ms after the tap; 0.40 = 460 ms before); tab fades `ob_tab_in` / `ob_tab_hold` /
  `ob_tab_out` (150 ms; Android's fade_in / fade_out are 500 ms) on open (MainActivity, GC's Create), Back to Home and Create's
  close (TabScreens); `NewProjectActivity` reads `lC.b()` / `lC.c()` (each decrypts every project's list file) on its own
  thread, Create and the icon picker wait for the id. `CloudSync.megabytes` and the viewer's sizes in decimal units.
- **Code helper**: `OtpAssist.pageKind` "login" on github.com `/login…` and `/session(s)…` (not the device-code, OAuth,
  passkey or recovery pages): the bar shows there too ("Code by e-mail?").
- **Gson 2.15.0** (2.9.0 before); the precompiled jars use `Gson.fromJson / toJson`, `GsonBuilder.create /
  excludeFieldsWithoutExposeAnnotation`, `TypeToken.getType` only.

## v2.1.4 additions (22 Nov 2026): the code bar reads GitHub's page, codes from e-mail notifications, Publish in one line, Follow everywhere, creator levels and country

Owner's 22 Nov review of v2.1.3 (10 screenshots, in Telugu). Record: `engineering/v2.1.4-verification.md`.

- **Code bar by page content** (`GitHubInAppAuthDialog`, `OtpAssist`): GitHub's "Confirm access" (sudo) page and the
  e-mailed sudo code page come at addresses `OtpAssist.pageKind` doesn't know (`/login/device/...`, `/login/oauth/...`), so
  v2.1.3 showed no bar there. `OtpAssist.PROBE_SCRIPT` (reads only) answers `"<field>|<send>|<sent to>|<confirm access>"`:
  a code field (`FIELD_JS`: autocomplete one-time-code, name/id with otp / totp / code / sudo / verification, placeholder
  XXXXXX / 000000, numeric 6 / 8; never `user-code-*`, login, password, e-mail, search), six / eight one-digit boxes, a
  visible "Send a code via email" control, the masked address after "sent to". It runs on every main-frame finish and every
  `PROBE_MILLIS` (1.5 s) while the window is open (`probeTick` on the dialog's `main` Handler, removed in `close()`); a page
  between loads (null answer) keeps the last look. `otpBar` decides: field → "email" (address known or e-mail address
  path), "two-factor" (that path), else "code"; no field but the send link → "send"; else the address kind. The bar redraws
  only when its state key changes (no status overwrite every tick). "E-mail me a code" = `SEND_SCRIPT` (clicks GitHub's own
  control). `fillScript` now finds the field the same way (named fields first).
- **Codes by themselves**: (a) the clipboard: focus lost while a code page showed (`leftForCodeAt`), back with focus → a
  6/8-digit code copied since then (ClipDescription timestamp, wall or uptime clock) is filled (`autoFill`; never the same
  code twice in two minutes); (b) `OtpNotifications` (NotificationListenerService; manifest service protected by
  `BIND_NOTIFICATION_LISTENER_SERVICE`, no uses-permission): only after the user turns on Android's Notification access
  (explained first: `explainAutoCodes`, Settings detail page on 11+, App info for Android 13+ restricted settings); armed
  only while a window shows a send / code page (`arm` / `disarm`), `requestUnbind()` the rest of the time, `requestRebind`
  when armed; only notifications that mention GitHub, posted at most 2 min before arming, code after "code" /
  "verification" (`OtpAssist.codeFromGitHubMail`); a code before the field exists waits (`waitingCode`, 10 min).
- **Refusal evidence** (`GitHubSession`): `token_print` = 12 hex of SHA-256(token) at sign-in (`noteSignedIn(app, token)`);
  a refusal note says whether the token sent is that one (GitHub ended it) or not (a fault here). `DrawerAccountCard`:
  "Why? GitHub's security log ›" (ClickableSpan → github.com/settings/security-log?q=action:oauth_authorization).
- **Publish** (`PlayStoreActivity`): step 1 = heading + the chosen project as one row with Change (`changingProject`,
  `lastRepos`); the list's explanation only while choosing; "✨ From project" beside step 2; `describe(info, asked)` writes
  `com.sketchware.ai.project.StoreDescription.of(scId)` (the snapshot of `ProjectDiffAnalyzer` + button / check labels read
  with own hC / eC instances) into an empty description (never over typed words; asked → confirm first) for the project
  linked to the repository (`linkedProject`); an unreadable project (no screens) is not described. Done: `CloudSync.
  publishToStore(activity, store, onDone)` → `GitHubProgress.doneOpens("Done · See it in the store", …)` (no auto-close) →
  `openStoreAfterPublish`: `PlayStore.highlight`, back to the list behind (`liveList` WeakReference) or a new store tab.
  `CloudSync.runStore` calls `PlayStore.notePublished` (the app first in the phone's copy, `playstore_recent` marks for 24 h);
  `withRecent` keeps marked apps first and listed before GitHub's search has them; the card says "● Just published", the
  lit one sits on `ob_accent_box`. "What's new" through `plainMarkdown` (# headings bold, - points as •, **bold**, links as
  text).
- **Follow and creators**: `followTarget` (the app's creator or the creator page's login) for `showFollow` / `loadFollow` /
  `loadFollowing` / `toggleFollow`; the pill always shows: "＋ Follow · N" / "✓ Following · N" (N from `PlayStore.profile`,
  GET /users/{login}; ±1 after a tap that worked), own app "👥 N" (opens your page); the creator page has a full-width
  Follow and its Followers number follows the pill. The @creator pill shrinks first (weight 1 in a wrap-width row). Levels
  `PlayStore.badge`: Official (smartapps000-hub), ✔ Verified (account ≥ 30 days, a public name or bio, ≥ 1 store app),
  ★ Trusted (≥ 365 days, ≥ 2 apps, ≥ 10 followers or ≥ 100 downloads), else Not verified with the reason; `BADGE_RULES`
  on tap. Country: `Countries.fromLocation` (the profile's Location: Locale country names in English, short forms, Indian
  states and cities, big cities, a flag emoji; else none: "Country not shared on GitHub"), with "on GitHub since".
