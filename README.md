# Sketchware AI

A fork of **Sketchware Pro v6.4.0-rc05** (package `com.sketchware.ai`) for building Android apps from blocks on a phone.
Current release: **v1.9.7.1** (versionCode 198). Maintained by AiTechApps. Not affiliated with Sketchware
(besome) or with the Sketchware Pro team.

## Licence status (read this first)

- **This project is not open source.** It is built on Sketchware Pro, which its own [LICENSE](LICENSE) calls
  *source-available, not open-source*: most of its code is decompiled from the original Sketchware app, and the licence
  allows forks for personal use. Every file this fork inherits keeps that status.
- **GPL-3.0 parts** (as stated in the upstream LICENSE): the `kotlinc` module, taken from
  [CodeAssist](https://github.com/tyron12233/CodeAssist), and the `minApi26` build flavour, a derivative of it.
  The APK this fork ships is the `minApi26` flavour, so those parts are GPL-3.0.
- **Third-party components** (AOSP aapt2 and libraries, R8/D8, desugar_jdk_libs, bundletool, apksig, ECJ, kotlinc,
  fonts, and the libraries generated apps can use) keep their own licences: Apache-2.0, BSD-3-Clause, EPL-2.0,
  GPL-2.0 with Classpath Exception, OFL-1.1 and others. Each one is listed in [CREDITS.md](CREDITS.md).
- **The code this fork added** (`com.sketchware.ai.*`, `tools/`, `engineering/` and its changes to upstream files) is
  licensed by its author, AiTechApps, under the **GPL-3.0** ([COPYING](COPYING)). That covers only this fork's own
  work: upstream files keep upstream's terms, so the repository as a whole is still not open source.

This is a summary of what the files say, not legal advice.

## What "AI" means here

The name is the product name. **There is no AI model, LLM or cloud service in the app or in its builds.** The build
auto-fix and Build Doctor are rule-based and run offline on the phone. Network is used to download Maven libraries
(Google Maven, Maven Central, JitPack), for GitHub sign-in and cloud backup when the user uses them (github.com,
api.github.com), and since v1.9.8 for one read of `config/app-control.json` at start (raw.githubusercontent.com; the app
version goes in the User-Agent; offline, the last copy is used and nothing else happens).

## Shipped (v1.0 – v2.0.2)

Everything in this section is in the released APK and was exercised as described under *How it is tested*.

| Area | What it does | Since |
|---|---|---|
| On-phone toolchain | aapt2 2.20 built from AOSP `android-16.0.0_r4`, static, for arm64-v8a, armeabi-v7a, x86_64, x86, 16 KB-aligned | v1.0 (64-bit), v1.8.4 (all ABIs), v1.8.5 (android-16) |
| | Projects compile against the bundled Android 17 (API 37) `android.jar` (platform android-37.0); ECJ 3.26 (Java 7 to 11) | v1.8.4 (API 34), v1.9.0 (API 37) |
| | D8 / R8 8.13.25 with core library desugaring (`desugar_jdk_libs_nio` 2.1.5): `java.time`, streams and `java.nio.file` on older Android versions. If that step fails, the build continues without it and says so | v1.8.5, v1.9.0 (8.13.25) |
| | Embedded Kotlin compiler 1.7.10; bundletool 1.18.3 (AAB export, 16 KB page alignment for native libraries) | upstream / v1.8.5 |
| Build repair (offline, rule-based) | Unreachable code, missing imports, missing or broken libraries (restore, exact Maven download), damaged PNGs, missing images. Repairs never move a line. Repairs inside *add source directly* blocks can be stored back into the block | v1.7.0 – v1.8.2 |
| Diagnostics | Source map (a compile error points to its block, with *Open block*), build map (time and status per step), Build Doctor diagnosis, build history | v1.8.0 – v1.8.2 |
| New Project screen | Full screen in cards: names with icons, app icon, version; templates (Empty Activity, Material 3 Scaffold, Bottom Navigation, Navigation Drawer, Tabbed ViewPager) written as ordinary views and blocks you can edit; colour presets and a random harmonious set (Material Color Utilities) with a live light/dark preview; Material 3, dark theme and dynamic colour; minimum / target SDK and Java level. Shows the phone's build engine (D8 / R8 on Android 8+) | v1.8.7 |
| New-project defaults | AppCompat and Design on (Material 3 needs it), target API 37, min API 21, D8, Java 11 (core library desugaring and the 16 KB aapt2 are always on), Material 3 theme with dark theme. Existing projects keep their stored settings | v1.2 – v1.8.7 |
| Material 3 | Material 3 AppTheme whose colour roles (containers, tonal surfaces, outlines, tertiary, error) are computed from the project's primary colour with Material Color Utilities; the project's own colours stay exact. Light and dark (follows the phone's dark mode; on for new projects, a Library Manager switch for older ones). Dynamic colour from the wallpaper on Android 12+ as an opt-in switch. Plain Buttons become filled Material 3 buttons (also in a background colour of their own, keeping the pill shape), Switches `MaterialSwitch`, CheckBoxes and RadioButtons their Material versions, plain EditTexts outlined fields with 8dp corners, at least as wide as their label; default text colours follow the theme. Widgets the project's own code treats as framework widgets stay as they are. The design canvas draws the light look with the project's colours | v1.8.4 – v1.8.6 |
| Java editor | Design editor → *Edit Java code* or the Code tab: an activity's generated Java in a code editor. Saving turns the changes back into the project: changed lines inside an event become *add source directly* blocks (only the blocks whose lines changed are replaced; changes inside if/else/loops stay there), new fields custom variables, new imports custom imports, methods written as `public void _name(final String _x, …)` MoreBlocks. What the designer generates itself is refused with its line. Nothing is kept unless the code generated again from the blocks is the code you wrote | v1.8.8 |
| Library Resolver | Replaces the local library manager. Required / All / Updates / Cache tabs; Maven Central search; + Add by Maven coordinate (POMs read like Gradle: parents, BOMs, properties, exclusions, version ranges) or AAR/JAR/ZIP import; libraries Sketchware AI has built in are not downloaded, and a build adds the built-in libraries a local library needs (Retrofit → OkHttp, Gson) | v1.8.8 |
| Export of local libraries | Export to Android Studio includes the project's local libraries: by Maven coordinate in `build.gradle` when known, else as `.jar` / `.aar` files in `app/libs` | v1.8.8 |
| Java statements to blocks | Saving the Java editor turns statements a block writes (setText, setVisible, setEnabled/Clickable/Checked, requestFocus, Toast, setTitle, set/increase/decrease variables, Intent setScreen/putExtra, StartActivity, Finish) into those blocks instead of add-source blocks, when the block generates exactly the typed line; `X.getText().toString()` in a text slot becomes the getText block (v1.8.13); a setHint on a Material 3 outlined field is saved (v1.8.13) | v1.8.9 |
| Library Resolver 2 | Search as you type; `group:artifact` queries Maven Central by field (no HTTP 400) and Google Maven / JitPack by coordinate; `import pkg.*;` added to generated activities for local-library classes the code uses (skipped when it would make a name ambiguous); (i) per library: POM description, licence, project page, manifest permissions, packages, Copy import (offline) | v1.8.9 |
| Library Resolver 3 | One card per library: readable name (POM name, or the artifact made readable) with a version badge, the coordinate, (i), Share (GitHub / project page), Latest / Update, Delete, switched on; long names scroll. Sort A to Z or updates & newest first. + Add also takes a GitHub or JitPack link (newest version JitPack built, or the tag in the link); a com.github version is tried with and without a leading `v`; repositories that redirect (301/302 to another host) are followed; when nothing is found the dialog lists the repositories that didn't answer and why. Progress dialog with the step (POM, archive, D8) and the bytes of the download. Search shows only .aar / .jar libraries (no WebJars, npm, Kotlin/JS or native, BOMs). Jars an .aar bundles in `libs/` (not part of the build) are named after the install, with how to add them. After an update, the old version is listed under *Older versions* instead of offering the same update again | v1.8.11 |
| Library Resolver 4 | Only Google Maven, Maven Central and JitPack (JitPack first for GitHub groups); `repositories.json` is no longer read. Find shows the step and a bar, Cancel stops it, Try again; one failure dialog with Copy log (every request and its answer); without a POM the .aar/.jar is installed alone. Search hides Boost / C++ Prefab packages and server artifacts; archives without Android code are left out in one line. GitHub/Web button opens the browser; green Up to date | v1.8.12 |
| Library Resolver 5 | No popup when an install worked: a Toast, the switch on and a green ✓ Installed on the card; when something needs a word, one line above the list with Copy log; a dialog only when the library itself couldn't be installed. Readable names instead of folder names. The highest version of a library wins: a lower one is never switched on over a higher one, a needed library uses the higher version that is on | v1.8.13 |
| Java editor variables | Saving the Java editor (or Apply in the logic editor's source sheet) turns a local `String` / `double` / `boolean` declaration into a project variable with set blocks (`String name = "x";` → String variable + set block); an assignment to an undeclared name becomes a variable of the type its value shows; other types (int, Intent, library classes) become custom variables with the assignment kept as code. A local whose meaning would change as a field (declared twice, used by a listener opened after it, recursive MoreBlock) stays code, and the save says why | v1.9.0 |
| Java values to blocks | Values in the Java editor (and the source sheet's Apply) become the blocks that write them: Operator (`+ - * / %`, `< > ==`, `&&`, `||`, `!`, true / false), Math (`Math.pow`, `Math.sqrt`, min / max …), text (`String.valueOf`, `.length()`, `.concat`, `.contains`, `.equals`, `.substring`, `.replace`, `.trim` …), List (`add`, `get`, `size`, `contains`, `indexOf`, `clear`), Map (`put`, `get`, `containsKey` …) and widget getters, nested as deep as the code; `Toast.makeText(…, Toast.LENGTH_SHORT).show()` becomes the Toast block. A block is used only when it writes back exactly the typed line; FileUtil lines become File blocks only when the activity already has one (File blocks add storage permissions) | v1.9.1 |
| Material 3 only | Every project with AppCompat gets the Material 3 theme (DayNight unless its Dark theme switch is off) and widgets; the MaterialComponents theme is no longer generated. ProgressBar → LinearProgressIndicator / CircularProgressIndicator; a Button with a Material 3 style in its Custom attributes (`Widget.Material3.Button.TonalButton`, `.OutlinedButton`, `.ElevatedButton`, `.TextButton`) becomes a MaterialButton with that style. Projects without AppCompat keep the framework theme (Project Configuration → Convert) | v1.9.5 |
| Ad blocker 2, download type, block shapes | *block ads* in two layers: listed ad hosts, ad brand words in a host (`adservice.…`) and ad paths for parts of a page, plus a style sheet that hides ad boxes; Back steps over ad-host pages while it is on. *download file* has a **type** slot (Default, Audio, Video, Images, Packages, Documents, or your own ending) that names a file the link and server left without an ending. Before a download only a HEAD request is sent (a one-time link is never spent). The guide sheet draws blocks in their real shape (stacking notch, value slots; pointed true/false and round text blocks) | v1.9.7 |
| Browser blocks | WebView *block ads* (125 ad / tracker domains and their subdomains get an empty answer; Android 8+), *block pop-ups* (a window opens only after a tap, in the same WebView; a file it answers with is downloaded), *mixed content* (COMPATIBILITY = Chrome's rule, NEVER_ALLOW, ALWAYS_ALLOW), *search Google*, *history to list*, *user agent*; File → *download file* (link, folder, name, notify) without a WebView. Download names come from the server, the link (path or a `file=` / `name=` / `title=` parameter) or the MIME type, else the file's first bytes: never `.bin`. A long press on an image offers *Download image*. Tap a web or permission block in the drawer (or *ⓘ guide*) for three steps: where to drop it, what to plug, what you get | v1.9.6 |
| Permission picker | The permission slot's list looks like *Select widget*: dark rounded box, search field, radio bubbles, bold name and grey line; one tap picks and closes | v1.9.6 |
| Self-healing built-in libraries | No reinstall after an update or an interrupted build: a new app version unpacks the built-in libraries again; every build (Run, Export APK, Export AAB) checks the files of the libraries it uses against the app's own archive and unpacks a missing or cut-off one again (through a temporary folder); a step that still fails on such a file has the library unpacked again and the build starts over once. Build Doctor explains it | v1.9.8 |
| GitHub sign-in and cloud backup | Main menu → *GitHub & Cloud Backup* (*Cloud Sync & Account* since v1.9.9): one tap signs in with GitHub (Device Flow: the code is copied and GitHub opens; no password, no client secret in the app; the token encrypted with an Android Keystore key, Android 6+). *Back up a project* uploads its `.swb` to the user's own private repository `SketchwareAI-Projects`; every backup is a commit, any of them restores as a new project | v1.9.8 |
| Release notices | `config/app-control.json` in this repository (read at start, cached): update available (`min_version_code`, `update_url`), a message (`status_message`, `message_url`), a sign-in suggestion (`require_login`). Notices only: nothing locks the app; links only into this repository | v1.9.8 |
| Sign-in that survives the browser | GitHub sign-in keeps checking while Chrome is in front (Android 15 cuts a background app's network) and after Android stopped the app, until the code expires; checks again the moment the user comes back; closes the Chrome tab when GitHub answers (the screen opened it for a result). Errors are one short line; a long press copies the cause | v1.9.9 |
| Cloud Sync & Account | Drawer item and screen in the drawer header's card style: avatar and @username, every project on the phone with *Back up* and *Restore*, backups only on GitHub listed apart; toasts when done (replaced in v2.0.0 by the drawer card and per-project actions) | v1.9.9 |
| Device-bound sign-in | The saved GitHub sign-in (AES-GCM, Android Keystore key) is bound to this phone's Android ID, the app's signing certificate and the GitHub user id: copied app data or a re-signed app is signed out | v1.9.9 |
| Sign-in gate (off) | `"require_login": true` in `config/app-control.json` makes sign-in required at the main screen, new project, opening a project, Run, export and restore (v2.0.0 also: block editor, Library Manager / Resolver, each export button; the file and the token re-checked online every 6 hours); only when the file was read online in that run and sign-in can work on the phone; the repository keeps it `false` until sign-in is confirmed on a phone | v1.9.9 |
| In-app GitHub sign-in | GitHub's own page opens inside the app (no Chrome): the app fills in the code, you sign in and tap Authorize, the window closes and you're signed in. Only github.com opens there; no bridge from the page into the app | v2.0.0 |
| GitHub account card | In the drawer under the Sketchware AI card: "Sign in with GitHub", or your picture, name, @username, primary e-mail and Sign out | v2.0.0 |
| Per-project backup and import | A project's Back up offers "On this phone" or "To GitHub" (that project only); new projects are backed up by themselves when signed in (offline: later); the Restore card offers a .swb file or "Import from GitHub" (your backups, or a GitHub link to a repository, folder or .swb file) | v2.0.0 |
| Design reference | `design/`: the Liquid Glass mockups and token sheet with a map to the app's resources | v2.0.0 |
| Sign-in window, steadier | Two-row header (✕ · GitHub Sign-In · ⟳ Reload, thin orange loading line, the code and a status that is never cut); "Sign in via browser" for Google, Apple or passkey accounts (code copied, GitHub's device page in the browser, checked when you come back); no blank page after a refused redirect; GitHub's "Too many requests" waited out with a countdown; closing the window keeps the code; new-window links stay in the window; a crashed page renderer is replaced (Android 8+) | v2.0.1 |
| Backup and restore sheets | Back up: "Include used Local libraries" / "Include used Custom Blocks", then "Save on phone" or "Back up to GitHub" (the same .swb); Restore: "Restore local .swb file" or "GitHub import project"; a linked backup's local libraries only after asking, marked so builds never switch them on elsewhere by themselves | v2.0.1 |
| GitHub on slow networks | Longer timeouts for 2G/3G, reads tried again after a dropped connection or a passing server error, GitHub's Retry-After and rate limits respected (no requests while a limit lasts; signed-out limits never block the account); your list of GitHub backups shown offline | v2.0.1 |
| Sign-in lands on the code page | After you sign in on GitHub's page, the window goes from GitHub's home feed or dashboard back to the code page by itself (never during GitHub's "Too many requests" pause, never on a page you went Back to); "Continue with Google / Apple" starts the browser sign-in before a blank page can load (code copied); the browser tab closes when GitHub says yes (browsers that open it in the app's task), the drawer closes and you are on the projects list | v2.0.2 |
| Drawer account card without cuts | "Sign in with GitHub" in full; signed in, name, @username and e-mail scroll sideways when long; Sign out under them | v2.0.2 |
| Live 0-100% progress | Back up to GitHub and GitHub import show a sheet with the percentage, an orange bar and the step ("65% • Uploading to GitHub… 1.3 MB of 2.0 MB"); "Hide" keeps it running (a toast says when it ends); after Home the sheet waits at 100% | v2.0.2 |
| Share link on project cards | After a backup to GitHub a share button sits next to the project's #id: it copies the backup's link and opens Android's share sheet; stored with the project, works offline; shown only for that project and the account that made it (the backup repository is private: the link opens for that account) | v2.0.2 |
| Import guide | ⓘ in Import from GitHub: three steps with diagrams drawn by the app (copy the link with the share button, paste and Import, pick a backup and choose libraries); a repository or folder link lists the backups it holds as rows with Import | v2.0.2 |
| GitHub component for apps | Components → + → GitHub; blocks *set token*, *has token*, *get user*, *list repositories*, *get file*, *upload file*, *run workflow* (drawer category "GitHub"); events *user loaded*, *repositories listed*, *file downloaded*, *file uploaded*, *workflow started*, *error* (GitHub's answer explained: token, permission, rate limit, not found, workflow trigger, no network). A helper file is written into the project (nothing downloaded), INTERNET added; the build warns when a GitHub token is written into the code | v2.0.2 |
| Build Doctor rules | Duplicate classes name both libraries and which to switch off; Material 3 names without the Material library, invalid resource file names, libraries for a newer Java explained; generated manifests get `android:exported="true"` on components with an intent filter and none set (target 31+); storage note for target 33+ | v1.9.8 |
| Project data safety | Restores and new projects take an id with no leftover folders (a damaged project's files are never merged into or deleted); `.swb` entries can't write outside their folder; a failed backup leaves no partial file | v1.9.8 |
| Contrast and Play readiness | Orange buttons and selected pills keep their white label on a deeper orange (#D34115 to #C64F14, 4.6:1); guide blocks keep white on a deeper teal and brown. Generated apps: storage permissions of File blocks declared for Android 12L and older only when the project targets 33+; a network security configuration beside `usesCleartextTraffic`; the camera declared not required when the app asks for CAMERA; exported projects lose eleven Android Studio lint errors | v1.9.7.1 |
| WebView like a browser | Back goes through the pages visited before it closes the screen (an *onBackPressed* event of your own wins); DOM storage, `window.open` and Chrome's mixed-content rule on; downloads take file endings (`.mp3`) or `*`, get the file name from the server, and never hold up the page | v1.9.5 |
| Runtime permissions | Drawer → *Permission* (v1.9.4: Component palette → *Permissions*); the slot opens a searchable list of groups and every Android permission, or a typed name: request a group (Storage/Media, Camera, Microphone, Location, Bluetooth, Notifications, Phone, SMS) or a permission by name, *permission granted*, *permission status* (GRANTED / DENIED / NEVER_ASK_AGAIN). The app asks for what the group means on the phone's Android version (Android 14+: photos, videos and music, partial photo access counts; 13: the three media permissions; older: storage), one dialog at a time. Activity events *When permission granted* / *When permission denied* (with *never ask again*). The manifest gets the group's permissions, with `maxSdkVersion` where newer Android ignores them; the build log lists them | v1.9.4 |
| WebView blocks | *allow downloads* (Android's download manager into Downloads/folder, notification on completion, file types filter), *desktop mode*, *clear cookies and cache*, *enable file upload* (a page's file input opens the file picker); WebView events *download starts* (URL, type, size) and *download completes* (URL, file name, success) | v1.9.4 |
| Safe int → Number | The Java editor turns an `int` local into a Number variable when every use works the same with a decimal number (increments, + and - on itself, comparisons, casts such as `String.valueOf((long)(n))`); otherwise it stays an int variable and the save names the line | v1.9.4 |
| Source sheet | The logic editor's `</>` sheet in Material 3 dark colours (every token at least 5:1 contrast); *Edit* shows the event's code, *Apply* turns it into blocks (same rules as the Java editor) and reopens the event, without leaving the logic editor | v1.9.0 |
| Build cache | Build Settings → *Build cache* (on by default): a Run build reuses the last build's compiled and dexed R classes and the compiled resources (project and local libraries) while they are unchanged. On the test harness a repeat build of a Material 3 project went from 324 s to 36 s with identical output. Exports always build everything | v1.9.0 |
| Target device | Project Configuration → *Target device*: Phone, Foldable, Tablet, Wear OS, Android TV. Wear OS adds the watch feature and standalone meta-data to the manifest; Android TV the leanback feature (not required), the TV launcher and a banner. The design editor's *Device* button previews the screen at foldable 700×840 dp, tablet 1280×800 dp, Wear OS round 227×227 dp or TV 960×540 dp with its Material 3 size class | v1.9.0 |
| Material Symbols | Image Manager → icons: Google's Material Symbols (3,937 icons, Outlined / Rounded / Sharp) next to the 2014 Material icons, added as PNG images in black, grey or white | v1.9.0 |
| Material 3 tags | MaterialCardView (elevated) and MaterialToolbar in Material 3 projects; Project Configuration → *Convert to Material 3 & Modern Stack* for older projects (target 37, min 21, AppCompat, Material 3, D8 + Java 11) | v1.8.9 |
| Code tab | Fourth design editor tab: the project's activities (→ Java editor) and its own Java/Kotlin files; + New class | v1.8.8 |
| Fonts | `SketchFonts`: custom TTF/OTF fonts with room for Telugu (and other) stacked glyphs | v1.8.4 |
| Export | APK signed v1+v2+v3; AAB with a SHA-256 JAR signature; "Export to Android Studio" writes a Gradle 9.8.2 / Android Gradle plugin 9.5.0 / Kotlin Gradle plugin 2.4.21 project with compileSdk 37 (Kotlin files compiled too); it opens in Android Studio Rabbit 2 (2026.2.2) or newer with JDK 17+ | v1.8.2, v1.8.5, v1.9.0 (AGP 9.5) |
| Built-in libraries | Material 1.13.0, AppCompat 1.7.1, AndroidX core 1.13.1 and the rest of the set listed in `engineering/component-library-inventory.md` | v1.8.4 |
| Native libraries | Warning for a 64-bit `.so` with 4 KB page alignment; local libraries' `.so` files in APK and AAB | v1.8.4 |
| Packaging | A file two libraries both hold (a newer kotlinx-coroutines or kotlin-stdlib next to the built-in one) no longer stops the build at *Package APK*: the APK gets the first copy and the log names the files; AndroidX -ktx libraries without classes no longer get a "no classes.dex" warning; a failed build repeats its error under the Build map | v1.9.1 |
| UI | "Obsidian liquid glass" visual layer over upstream's screens. The logic editor, block drawer and view editor are upstream's own (precompiled) classes | v1.6 |

## How it is tested, and what is not

- **Android runtime harness** (`tools/art-harness`): the APK's own classes run on an Android 12 (API 31) ART runtime
  **without the Android framework**: whole Run builds of fixture projects 901–934 (914–918 created by the New Project
  screen's own code; 919 edited through the Java editor's save; 920 with libraries installed by the Library Resolver) and
  AAB exports (aapt2,
  ECJ, Kotlin, D8/L8, DEX merge, packaging, signing); the generated permission helper's group table for API 23-37 and the
  web helper's filters, download names, file-type sniffing and ad-host list run there from built apps (931-933), and the GitHub component's helper (path encoding, Base64, error explanations) from app 934. This is not a device test and says nothing about other
  Android versions.
- **layoutlib** (`tools/layoutlib-renders`): Android 14's view, theme and text code on a desktop JVM, for generated
  layouts and themes (on light and night-mode device settings, large font and bold-text settings), Telugu text and IDE
  dialog layouts. No Android emulator: the build machine has no hardware virtualisation.
- **Host checks**: resource-ID pinning, API-level and linkage scans of the APK, signatures, 16 KB alignment.
- **Not run here**: the IDE's screens (logic editor, design editor, compile log, Java editor, Library Resolver, GitHub
  sign-in window, drawer account card, sign-in gate, backup and restore sheets, progress sheets, share button, import guide), GitHub itself and its pages inside the app's WebView (the
  sign-in, backup and import code runs against a fake GitHub on a desktop JVM; the code fill and the Google / Apple interception against stand-in pages), a generated app's GitHub calls, Maven downloads on a phone's network, aapt2 on a real phone, installing or
  starting generated apps. Device testing is done by the maintainer on his phone (Android 15) after each build; its
  results go to `engineering/compatibility-matrix.md`.

Per-release evidence: `engineering/v<version>-verification.md`; device and API status:
`engineering/compatibility-matrix.md`.

## Known limits

- Project code compiles against Android 17 (API 37). That says nothing about running on older Android versions: a call
  above the project's min SDK needs a `Build.VERSION` check, and the Material 3 widgets still come from Material 1.13.
  Android 17's SDK dropped `FingerprintManager`; the bundled android.jar keeps it (and `Context.FINGERPRINT_SERVICE`) so
  fingerprint code in add-source blocks still compiles. A few other long-deprecated calls are gone (`Thread.suspend/resume`,
  `Notification.WearableExtender.setBackground`): set an older android.jar in Build Settings for code that needs them.
- Java 17 language level is not possible with ECJ 3.26 and the on-phone R8 (`engineering/java17-path.md`).
- Kotlin code is compiled by kotlinc 1.7.10, which reads Kotlin metadata up to 1.8: Kotlin sources can't use libraries built with newer Kotlin.
- Material 3 Expressive: Material 1.13 (bundled) contains some Expressive components (button groups, split button,
  loading indicator, docked and floating toolbars) but not the Expressive theme, and the palette doesn't offer them yet.
  Material Components 1.14 needs min SDK 23 (and androidx.core 1.16): not shipped.
- Dark theme: colours, icons and `setTextColor` blocks the user chose keep their values at night. A dark icon or text
  colour on the theme background can be hard to read in dark mode; the Library Manager's *Dark theme* switch turns it
  off per project. Dynamic colour replaces the project colours on Android 12+ by design.
- The design canvas shows the light look only. Its foldable, tablet, Wear OS and TV sizes are a preview of the same
  layout: there are no window-size-class layouts (sw600dp / sw720dp folders), no Wear OS rotary input and no TV D-pad
  focus handling yet.
- Build cache: the first Run build after an IDE update, a change in any resource or library, or a different Java level /
  min SDK compiles everything again. The aapt2 link step and packaging are not cached. Build times on a phone were not
  measured here.
- Java editor: only code inside events, fields, imports and MoreBlock-shaped methods becomes blocks; other methods and
  classes belong in a Java file of their own (Code tab → + New class). Statements a block writes exactly, getText in a
  text slot and local String / double / boolean variables become blocks; everything else is stored as *add source
  directly* blocks. An `int` local becomes a Number variable (a double) only when every use works the same with one;
  otherwise a custom int variable (+ and - past 2^31: an int wraps, a double doesn't). Decimal numbers are written with
  a `d` (`2.5d`), the way the Number blocks write them.
- v1.9.5 Material 3 for every AppCompat project: an older project switches to the Material 3 theme and widgets at its
  next build and follows the phone's dark mode; colours picked for a light background may need the Library Manager's
  *Dark theme* switch turned off. The design canvas still draws ProgressBars the old way (its item is a precompiled
  class); the app shows Material's indicators. Mixed content uses Chrome's rule (images over http load on an https page,
  scripts don't), not "always allow".
- Permission blocks: the result events are activity events (a block in a Fragment asks through its activity, whose
  events answer). *Storage/Media* on target 33+ declares READ_MEDIA_IMAGES / VIDEO, which Google Play accepts only for
  apps whose core feature needs broad photo access (otherwise the photo picker). A custom event or MoreBlock named
  onPermissionGranted / onPermissionDenied clashes with the new events.
- WebView blocks: *enable file upload* and *block pop-ups* share one WebChromeClient; on Android 8+ a client set by
  code before them keeps working inside it, on older Android it is replaced; a WebViewClient set by code after *block
  ads* replaces the ad blocker. *block ads* is a built-in list (125 domains, 21 host words, two paths and a style
  sheet; not a full filter list such as EasyList), does nothing below Android 8, and its style sheet needs JavaScript.
  A link whose name has no ending is asked once with a HEAD request before the download; when the server tells
  nothing the file keeps no ending. Every project with a WebView now includes the SketchWeb helper (its Back code
  calls it). Downloads need http(s) links
  (blob: / data: links report a failed download). A form with `target=_blank` sent as POST reopens as a plain page
  (GET) when pop-ups are blocked. There is no block that ignores SSL errors.
- Library Resolver: a library that needs a different major version of a built-in library (Retrofit 3 needs OkHttp 4;
  OkHttp 3.14.9 is built in) may not work; the install plan says so. Libraries compiled for Java 8+ can't be dexed on
  Android 7.1 and older (Dx).
- A drawer's panel keeps the AppCompat Manager's light grey (#EEEEEE) in dark mode; its default black text stays readable there.
- Not on the phone: a Gradle build (it needs a JDK and Gradle runtime of about 1.5 GB on the device), Kotlin as the
  language of the blocks (blocks generate Java; Kotlin files can be added in the Java Manager), Jetpack Compose.
- Firebase, Play services (Ads 20.1.0, Maps, Auth), OneSignal and Audience Network are held at their old versions.
- The debug APK is signed with the public test key, so it installs over earlier releases. It is not a release key.
- Sketchware AI itself compiles against API 37 and targets API 35 (Android 15), with the edge-to-edge opt-out in its
  themes and `enableOnBackInvokedCallback="false"` so every screen and the back button (the design and logic editors
  save there) behave as before. Targeting 36 or higher makes Android 16+ draw every screen edge to edge with no opt-out
  and drop `onBackPressed`; targeting 37 also blocks changing `static final` fields by reflection and ignores
  orientation locks on tablets. That move needs every screen and back handler checked on Android 16/17 phones first
  (roadmap). Apps made with it target 37.

## Advanced Roadmap (v1.9 – v2.0): not built

**None of the following exists in the code yet.** Order as planned; one release does not attempt everything.

1. Build Doctor strategies with IDs, skipping fixes that already failed for the same fingerprint.
2. Error dashboard.
3. Verified repair memory kept on disk.
4. Dependency and library intelligence beyond the Library Resolver.
5. Developer-tools controller, Project Brain, Git/GitHub beyond sign-in and project backup (commits of project
   source, pull requests), extensions, Android Studio import (export exists). A community store on GitHub Discussions
   and Releases (owner's 12 Nov directive) is not built.
6. Google / e-mail sign-in and telemetry: reviewed only (`engineering/auth-telemetry-review.md`). Google One-Tap
   (Credential Manager) needs a Google Cloud OAuth client for `com.sketchware.ai` registered with a private release key
   (never the public test key) and current AndroidX; an e-mail code needs a server that sends mail. GitHub sign-in
   (v1.9.8) is optional. Telemetry would ship switched off.
7. Material 3 Expressive: the Expressive components of Material 1.13 in the palette, then the Material Components 1.14
   migration (Expressive theme; needs min SDK 23; the bundled `android.jar` is API 37 since v1.9.0). Material 3
   typography roles and variable fonts (Anek Telugu) in the theme.
8. A dark-mode preview on the design canvas.
9. Toolchain: aapt2 rebuilt from `android-17.0.0_r1` for the four ABIs; D8/R8 9.x (core library desugaring through
   TraceReferences); caching the aapt2 link step.
10. Adaptive layouts: window-size-class layout folders from the canvas sizes, Wear OS rotary input, TV D-pad focus.
11. Sketchware AI itself targeting API 37: insets on every screen (edge to edge), back handling through
    OnBackPressedDispatcher instead of `onBackPressed`, the embedded compilers checked for `static final` reflection,
    layouts for any orientation on tablets. ECJ newer than 3.26 for Java 17/21 (3.28 and 3.33 crashed on ART in Oct 2026).
12. Any AI assistance (on-device or cloud model): not designed. Today's rules keep models out of the build.

## Building from source

The tree is the upstream source zip `Sketchware-Pro-6_4_0-rc05.zip` (sha256
`ac9c63106dd81879ece6178fc83bb0356dfb0aef6a5493eebf338008d2b1f0b8`) plus this fork's commits. Since v1.8.6 the repository
is complete: the source packages that upstream's unanchored `.gitignore` rule `build/` used to hide (`mod/hey/studios/build`,
`mod/jbk/build`) and the public test key are tracked.

Needs JDK 17 and the Android SDK (platform android-37.0: the IDE compiles against API 37 and targets API 35; Gradle 9.8.2,
the Android Gradle plugin 9.5.0 and the Kotlin Gradle plugin 2.4.21 are fetched by the wrapper, and AGP installs
build-tools 36.0.0 when it is missing). Then:

```
git clone https://github.com/smartapps000-hub/SketchwareAI.git && cd SketchwareAI
echo sdk.dir=/path/to/android-sdk > local.properties
./gradlew assembleMinApi26Debug
```

Output: `app/build/outputs/apk/minApi26/debug/app-minApi26-debug.apk`.

## Repository layout

| Path | Contents |
|---|---|
| `app/` | the IDE (upstream sources + this fork's changes; most of the core is precompiled in `app/libs/*.jar`) |
| `kotlinc/` | the embedded Kotlin compiler module (GPL-3.0, from CodeAssist) |
| `engineering/` | verified state of the fork, per-release verification records, test fixtures, compatibility matrix, known errors |
| `tools/` | Android runtime harness, layoutlib renders, static aapt2 build, built-in library packaging, APK scans, JVM tests |
| `releases/` | release notes and SHA-256 sums per release. The APKs (about 138 MB) are not in this branch: they are on the `binaries` branch (up to v1.8.8 as 7z parts; since v1.8.9 one full APK in `apk/`, stored with Git LFS because GitHub refuses normal files over 100 MB). Download them with the *Download raw file* button or `https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/<file>`: a `raw.githubusercontent.com` link gives the 134-byte LFS pointer text, which Android reports as "package appears to be invalid" |

## Credits

Sketchware (besome), the Sketchware Pro team and contributors, CodeAssist (tyron12233), the Android Open Source Project,
and the authors of every component in [CREDITS.md](CREDITS.md).
