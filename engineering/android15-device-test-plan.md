# Android 15 device test plan

**Nothing in this plan has been run on a device yet.** It was written from the source code and from tests on a Linux machine (see android15-audit.md). Every "should happen" below is an expectation, not a result.

## Summary

- 12 main steps, about 20 minutes on the phone. 5 optional steps for the items the audit marked RISK or UNKNOWN.
- One step is **expected to fail**: step 13, installing an APK made with Export. The audit found that such APKs are signed in the old way only (android15-audit.md, B9). The other steps are expected to pass.
- For every failed step send: **Compile log → Copy** (paste the whole text), plus a screenshot when the problem is a system dialog and not a build error.
- Write the result of each step in the table at the end.

## How this was put together

- Screens, buttons and menu texts: `com/besome/sketch/design/DesignActivity.java:395-452` (Run button; options menu "Build Settings", "Clean temporary files", "Show last compile error", "Show source code", "Install last built APK"), `:183, 211` ("Show compile log", "Built with N warnings"), `com/besome/sketch/tools/CompileLogActivity.java:77, 157` (Copy).
- The three projects: `engineering/compatibility-matrix.md` (section 2, "User's phone"), `engineering/known-errors.json`, `engineering/test-fixtures.json` (fixtures `png`, `660`, `650-writeback`).
- Export screen texts: `res/values/strings.xml:1731-1735` ("Export Project", "Sign APK"), `mod/jbk/export/GetKeyStoreCredentialsDialog.java:267-268` ("Sign using key store", "Sign using testkey"). Debug screen title: `assets/debug/DebugActivity.java:64`.
- Risk steps: android15-audit.md rows A3, A8, B2, B5, B6, B9, B12.

## Before you start

Write down and send once:

- Phone model and Android version (Settings → About phone). You said Android 15.
- The version shown in Sketchware AI (About screen), for example `v1.8.1-…-minApi26`.

## Main steps (about 20 minutes)

| # | What to tap | What should happen | If it fails, send |
|---|---|---|---|
| 1 | Install the new APK **over** the installed app. Do not uninstall first. | "App updated". The app opens. Your projects are still in the list. | Screenshot of the install message. |
| 2 | Open the app. If it asks for storage: tap OK, turn on "Allow access to manage all files", go back. | The project list shows your projects. No second request. | Screenshot of the screen you are stuck on. |
| 3 | Tap project **#644** (Vehicle Parking). | The design editor opens. No crash. | If the crash screen opens: tap Copy there and send the text. |
| 4 | In #644: tap the options button next to **Run** → **Clean temporary files**. Then tap **Run**. | The steps run: "AAPT2 is running…", "Java is compiling…", "D8 is running…", "Building APK…", "Signing APK…". Then the Android install dialog opens. | Compile log → Copy. |
| 5 | In the install dialog tap Install (allow "Install unknown apps" if asked), then Open. | The app installs and starts. If the project's target SDK is 31 or higher and it uses Bluetooth, a "Nearby devices" permission dialog appears. | Screenshot. Also Compile log → Copy of that build (options → Show last compile error). |
| 6 | Back in the editor of #644: if "Built with N warnings" is shown, tap **Show compile log**. | The log lists `auto-fix:` lines for the Bluetooth MoreBlock. No line says "Java 11 couldn't be used". | Compile log → Copy. |
| 7 | Still #644: tap **Run** again without changing anything. | Same result as step 4. The same `auto-fix:` lines appear again unless you used "Apply to block" before (the build repairs generated code each time; see architecture-state.md). No new kind of error. | Compile log → Copy. |
| 8 | Open project **#623** → **Run**. (In v1.8.0 it failed in AAPT2 with "PNG chunk type 49444154 is too large" and "Exit code 139".) | The build finishes and offers to install. The log has a warning that names the damaged image and says a repaired copy was used. Your image file is not changed. | Compile log → Copy. |
| 9 | #623: tap **Run** again. | Builds again. The same image warning appears again (the image itself is never changed; replace it in Image Manager to make the warning go away). | Compile log → Copy. |
| 10 | Turn on mobile data or Wi-Fi. Open project **#606** → **Run**. (In v1.8.0 it failed in Java compile: a local library was enabled but its classes.jar was missing.) | Either: the build finishes, and the log has `auto-fix: local library … had no classes.jar; …`. Or: the build fails and the log says plainly which library is missing and why it could not be restored. | Compile log → Copy. |
| 11 | #606: tap **Run** again. | If step 10 restored the library: this build has **no** "had no classes.jar" line. The restore is kept between builds. | Compile log → Copy. |
| 12 | Create a **new** project (any name) → put one button on the screen → **Run** → Install → Open. Then **Run** a second time. | Both builds finish with no warnings. The app starts. The toolbar is below the status bar and nothing is hidden under the navigation bar. | Compile log → Copy. Screenshot of the running app if the layout is under the bars. |

## Look and feel (5 minutes, can be done during the steps above)

| # | What to do | What should happen | If it fails, send |
|---|---|---|---|
| L1 | In any project: options → **Build Settings**. Rotate the phone while the dialog is open. Rotate back. | The dialog stays readable. Its buttons are visible and not squeezed. | Screenshot. |
| L2 | Phone Settings → Display → turn **Dark theme** on, then off. Reopen Build Settings, Project Configuration and the compile log. | Text is readable in both modes: no dark text on a dark sheet, no white text on white. | Screenshot. |
| L3 | Phone Settings → Display → **Font size** to the largest. Reopen the same three dialogs. | Text may wrap, but buttons are still visible and can be tapped. | Screenshot. |

## Optional: items the audit marked RISK or UNKNOWN

| # | What to tap | What should happen | If it fails, send |
|---|---|---|---|
| 13 | The new project from step 12: project menu → **Export Project** → **Sign APK** → choose **Sign using testkey** → wait for "Aligning APK…". Install the exported file from the file manager. | **Expected to fail** ("App not installed" or "package appears to be invalid"). The audit found the exported APK has only a v1 signature, which Android 11+ refuses for target 30+. If it installs, tell us: then the audit is wrong on this point. | Screenshot of the message, and the name of the exported file. |
| 14 | New project: add blocks so that the app crashes on start (for example divide a number by 0 in onCreate) → Run → Open. | The debug screen "An error occurred" with the stack trace should open. The audit marks this RISK on Android 15 for new projects (target 37). | Say what happened: debug screen, or the app just closed. |
| 15 | A project with **Camera** and **File picker** at target 33 or higher: take a picture, pick a file. | The camera opens and returns a picture. Picking a file works. Reading the picked file by path may fail (audit B6). | Screenshot, and Compile log → Copy of the build. |
| 16 | A project with **Firebase Cloud Messaging** or **OneSignal** at target 33 or higher. | The audit expects **no** notification permission dialog and no notifications until the permission is added by hand (audit B5). | Say whether a notification appeared. |
| 17 | Only if Sketchware AI itself crashes at any time. | The crash report screen with Copy should appear. On Android 15 this is UNKNOWN (audit A8). | The copied report, or "the app just closed". |

Page size (audit A3): there is no step for it. If your phone has a developer option named "Boot with 16KB page size", tell us whether it is on. Otherwise nothing to do.

## Results

Fill in and send back.

| Step | PASS/FAIL | Note |
|---|---|---|
| 1 Install over old version | | |
| 2 Storage permission | | |
| 3 Open #644 | | |
| 4 #644 clean build | | |
| 5 #644 install and start | | |
| 6 #644 log check | | |
| 7 #644 second build | | |
| 8 #623 build | | |
| 9 #623 second build | | |
| 10 #606 build | | |
| 11 #606 second build | | |
| 12 New project, two builds | | |
| L1 Rotation | | |
| L2 Dark mode | | |
| L3 Font size | | |
| 13 Export APK install (expected FAIL) | | |
| 14 Debug crash screen | | |
| 15 Camera / File picker | | |
| 16 Notifications | | |
| 17 IDE crash screen | | |

## What a PASS will and will not prove

- A PASS on steps 1 to 12 proves those paths work on that one phone with that Android version. It can then be written into compatibility-matrix.md with the phone model and API level.
- It proves nothing about other Android versions, other phones, or 16 KB devices.
