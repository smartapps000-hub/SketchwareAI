# Sketchware AI v1.8.11

versionCode 181, versionName `v1.8.11-COMMIT-minApi26`. Installs over v1.8.10 (same test key). A fix release.

## Download (read this if the last APK "appears to be invalid")

The APK is stored with Git LFS. A `raw.githubusercontent.com/…/apk/…` link gives the 134-byte LFS pointer text, not
the APK, and Android then says *App not installed as package appears to be invalid*. Use one of these, both checked
to give the full file:

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.8.11.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.8.11.apk (the *Download raw
  file* button on the file's GitHub page goes there too)

The file is SIZE bytes; anything a few hundred bytes long is the pointer.

## Library Resolver

- **Cards.** Each installed library is one card: its readable name (the POM's `<name>`, or the artifact made readable:
  "ESCPOS Thermal Printer Android", "Airbnb Lottie") with a version badge, the `group:artifact` line, and one row of
  actions: (i), Share (the GitHub page for JitPack libraries, else the project page from the POM or the Maven Central /
  Google Maven page), Latest / Update, Delete and the switch for this project. Names and coordinates longer than the card
  scroll instead of being cut.
- **Sort** (All tab): A to Z, or updates & newest installs first. Remembered on the phone.
- **Progress.** Installing shows the step (reading the POM files, fetching the .aar / .jar, unpacking, D8 dexing, with
  "(2/5)" when several libraries are installed) and a bar with the bytes of the download in progress.
- **+ Add takes links.** A GitHub link (`github.com/DantSu/ESCPOS-ThermalPrinter-Android`, `…/releases/tag/3.4.0`,
  `…/tree/v1.2`) or a JitPack page (`jitpack.io/#User/Repo/1.0`) becomes the JitPack coordinate: the tag in the link,
  or the newest version JitPack has built. `com.github.User:Repo` without a version works too. Typing words in the
  search field still searches Maven Central (JitPack has no search).
- **"Not found in the repositories"** now says why when a repository gave no answer (time-out, no connection,
  unknown host, HTTP 401: for JitPack that means no such GitHub repository), and for GitHub libraries gives the JitPack
  build log and Releases links. Repositories that redirect to another address (JCenter, Sonatype, Atlassian) are
  followed (never from https down to http); JitPack's "still building" is reported as such; a GitHub version is also
  tried with/without its leading `v` (`v3.4.0` / `3.4.0`). A repository in your list that can't be reached at all is
  asked once per search instead of once per POM (it cost 20 seconds each time).
- **Old version still used by this project:** its card says *Use v3.5.4* when a newer version is installed; one tap
  switches the project over.
- **Search** lists only libraries that can be installed (.aar / .jar): WebJars, npm packages, Kotlin/JS and native
  artifacts, BOMs and parent POMs are left out. Characters the search service treats as operators are ignored.
- **Update offered again after updating (fixed).** The old version stays installed for projects that still use it;
  it is now listed under *Updates → Older versions* (with Delete) instead of offering the same update again, and the
  update disappears as soon as a version at least as new is installed. Updating switches the project from the old
  version to the new one.
- **Archives.** Jars an .aar bundles in `libs/` are not part of the build (as before); the install now names them and
  says how to add them (+ Add → Import file, or their Maven coordinate). Merging them automatically was tried and left
  out of this release: their classes and resources clash with the libraries Sketchware AI has built in (Gson, OkHttp,
  Kotlin …). A release zip with several files installs its .aar; a POM-only coordinate (a BOM, the root of a
  multi-module build) says so and how JitPack names modules.

## Java editor: statements outside methods

A statement typed in the class body (outside every method), like `button1.setText("AiTechApp");`, was stored as a
custom variable, then generated back into the class body: the build failed and the editor showed *Found invalid data of
Custom Variable #1* (project #659). Saving now refuses it with the line and where it belongs (an event such as onCreate
or a click, or a MoreBlock). Several names in one line (`int a, b;`) are refused too: the logic editor shows one block
per custom variable, so write one field per line. **To repair a project that already has one:** open *Edit Java code*, delete that line from
the class body and tap *Save as blocks*: the custom variable is removed.

## Design editor: ID in the property bar

No crash log came with this report, so the cause is not confirmed. What the code showed and was changed: the ID dialog
checks the new name against the ids of the screen the property bar shows now (after switching screens it could check
another screen's), finds the Activity behind the bar's context instead of assuming it, and if there is still nothing to
check against it shows "Select the widget on the canvas again" instead of closing the app. **If the ID button still
closes the app, please send the crash report (Copy on the crash screen).**

## Crash screen

Known causes are named on the crash screen (rule-based, offline, no AI): the v1.8.10 canvas ClassCastException, a
dialog opened for a closing screen (BadTokenException), a property-dialog NullPointerException. All issues of this
release (LFS download, canvas crash, custom variables, ID dialog, WebJars, JitPack, update loop, bundled jars) are
recorded in `engineering/known-errors.json` with cause, repair and how they were checked.

## Checked

CHECKED

**Not run here:** the Library Resolver and design editor screens (Activities), downloads on the phone's network, the
minApi21 flavour. Please check on the phone: + Add with `https://github.com/DantSu/ESCPOS-ThermalPrinter-Android`,
the Updates tab after an update, and the ID button on a CardView.

## Files

`apk/Sketchware_AI_v1.8.11.apk` (SIZE bytes, sha256 `SHA`) on the repository's `binaries` branch (one file, Git LFS).
