# Sketchware AI v1.9.9

versionCode 200, `v1.9.9-6071ad5-minApi26`, 142398392 bytes, sha256
`85d8d75a8947351f0752f84b91167643d3a161c5ba5cddf7083e30428592d589`. Installs over v1.9.8 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.9.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.9.apk

The next release, v1.9.10, takes versionCode 201. The full record is `engineering/v1.9.9-verification.md`.

## GitHub sign-in works when you come back from Chrome

- v1.9.8 gave up as soon as one check with GitHub failed. While Chrome is open, Android 15 cuts the app's network, so
  after you approved the code the app never heard about it ("Sign-in didn't work"). Now the app keeps checking until
  the code expires, checks again the moment you come back, and continues after Android stopped the app.
- When GitHub says yes, the app closes the Chrome tab and shows your picture and **@username**. If Android doesn't let
  it, tap **X** or Back in Chrome: you are signed in at once.
- Sign in once more after this update: sign-ins of v1.9.8 are not carried over (they weren't tied to the phone).

## Cloud Sync & Account

- The drawer item is now **Cloud Sync & Account**, in the same card style as the drawer's Sketchware AI header.
- Every project on the phone is listed with **Back up** and, when it has a backup, **Restore**. Backups whose project
  isn't on this phone are listed under **Only on GitHub**. A toast says when a backup is done.

## Sign-in tied to this phone

- The saved sign-in only opens on the phone and app it was made on (Android ID, the app's signing certificate and your
  GitHub account). Copied app data or a re-signed copy of the app is signed out.

## Sign-in can be required (off for now)

- When `config/app-control.json` says `"require_login": true`, the app shows the sign-in screen first, and before a new
  project, opening a project, Run, export and restore. It can't be closed except by leaving the app. It stays **off**
  in this release until sign-in is confirmed on a phone.
