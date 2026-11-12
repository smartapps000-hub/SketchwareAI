# Sketchware AI v1.9.8

versionCode 199, `v1.9.8-90559c6-minApi26`, 142386084 bytes, sha256
`42dd8414700feeb9da4aa4367255ec622d3618f6e76502ee3d2e783d9605195c`. Installs over v1.9.7.1 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.8.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.8.apk

The next release, v1.9.9, takes versionCode 200. The full record is `engineering/v1.9.8-verification.md`.

## No more reinstalling after an update

- A build that stopped with `NoSuchFileException ... cache/libs/libs/graphics-shapes-1.0.1/classes.jar` (or another
  built-in library) kept stopping until the app was reinstalled. Now:
  - after an update the app unpacks its built-in libraries again by itself;
  - every build checks the built-in libraries it uses and unpacks a missing or cut-off one again first;
  - if a file still goes missing during a build, the library is unpacked again and the build starts over once
    (Run, Export APK and Export AAB).
- Unpacking goes through a temporary folder, so a killed app can't leave half a library behind.

## Build Doctor

- Explains the cache case, and names both libraries of a "class defined twice" error (and which one to switch off).
- New explanations: Material 3 names without the Material library, bad resource file names, libraries made for a
  newer Java.
- Generated apps: an activity, service or receiver with an intent filter and no `android:exported` gets
  `android:exported="true"` (Android 12+ refused to install such an app). The build log says which.
- A note when a project targets Android 13+ and asks for the old storage permissions.

## GitHub sign-in and cloud backup

- Main menu > **GitHub & Cloud Backup**. One tap on **Sign in**: the code is copied, GitHub opens; paste, approve,
  done. No password in the app; the sign-in is kept encrypted with a key in the phone's Android Keystore.
- **Back up a project** to your own private GitHub repository `SketchwareAI-Projects` (made for you). Every backup is
  kept; tap a project to restore any of them as a new project.
- Release notices from `config/app-control.json` in this repository (update available, a message, a sign-in
  suggestion). They never lock the app.

## Your projects are safer

- A restore never lands on top of a damaged project's leftover files; creating a project never deletes them.
- A backup file can't write outside its folder; a failed backup leaves no broken `.swb`.
