# Sketchware AI v1.8.12

versionCode 182, versionName `v1.8.12-COMMIT-minApi26`. Installs over v1.8.11 (same test key). A fix release for the
Library Resolver.

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.8.12.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.8.12.apk

SIZE bytes. Not a `raw.githubusercontent.com` link: that one gives the 134-byte Git LFS pointer.

## Only Google Maven, Maven Central and JitPack

The Library Resolver used the old library downloader's repository list (`repositories.json`, shared with Sketchware
Pro): Hortonworks, Spring, Atlassian, JCenter, Sonatype … They answered with TLS errors ("Chain validation failed"),
HTTP 401 or not at all, each one with a time-out per POM, which is why Find seemed frozen. Now:

- Only **Google Maven, Maven Central and JitPack** are asked, by the Library Resolver and by the old downloader.
  `repositories.json` is no longer read.
- **GitHub libraries (`com.github.…`) go to JitPack first.** `com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0`
  and `3.4.0`: the first request goes to JitPack, 5 requests in all (its POM, then ZXing's from Maven Central).
- **No POM?** The `.aar` (or `.jar`) at its Maven path is installed on its own, the way v1.8.2-v1.8.5 did it. Without
  the POM, the libraries it needs can't be known.
- Time-outs are shorter: 15 s to connect, 30 s to read.

## Find: progress, Cancel, Try again, Copy log

- While Find works, the dialog shows the step ("Reading the POM (request 2) · jitpack.io · …pom"). The bar fills when
  the server gives the size, else it moves. **Cancel** stops the Find and keeps the dialog open; Cancel again closes it.
- When nothing is found, the button becomes **Try again**. One dialog says what happened, with the JitPack build log
  and Releases links, and **Copy log**: every request and its answer (`GET … → 200 / 404 / error`). Send that log if a
  library still isn't found.
- The installing dialog has **Cancel** too (it stops after the current download). Its report has **Copy log**.

## No more Boost / C++ dialogs

Searching "date time" showed `com.github.brunotl:boost_date_time`. That is Boost for the Android NDK: "Prefab" `.aar`
files with C++ headers and `.so` files, no Java classes. Installing it gave one long paragraph per dependency.

- Search hides results without an `.aar`/`.jar` file, Boost, Prefab, NDK/C++ packages and server artifacts (Vaadin,
  Spring Boot …), and lists Android libraries (`.aar`) first.
- An archive without Android code is left out of an install in **one line**: "Left out, no Android code in them (13):
  …". At most five failures are shown in full; all of them are in Copy log.

## Cards

- **GitHub** (or **Web**) opens the library's page in the browser (the GitHub repository for JitPack libraries).
- **✓ Up to date** in green when the last check found the newest stable version. **Update** only when a newer one exists.
- Libraries the check couldn't ask about are named in the Updates tab instead of a popup.

## Checked

CHECKED

**Not run here:** the Library Resolver screen itself (an Activity) and your phone's network. Please try on the phone:
+ Add with `com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0` (and Cancel once while it works). If it fails, tap
**Copy log** and send it.

## Files

`apk/Sketchware_AI_v1.8.12.apk` (SIZE bytes, sha256 `SHA`) on the repository's `binaries` branch (one file, Git LFS).
