# Sketchware AI v2.1.2

versionCode 209, `v2.1.2-2dbb401-minApi26`, 142,824,153 bytes, sha256
`ef44b98efd9b1ea538322335748e6b4c77847001f931628729a29ba07566418c`. Installs over v2.1.1 (same test key). v2.1.1 asks for it by itself about 3.5 s after it opens (required,
no Later), downloads it in the app and checks its SHA-256.

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.1.2.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.2.apk

The owner's 21 Nov directive (v2.1.2). The full record is `engineering/v2.1.2-verification.md`.

## Faster tabs, no questions

- Back on Play Store, GitHub, Community or Settings goes straight to Home, and the tab stays as it was: the next tap
  brings it back scrolled to the same place, with the same page open. Create is a form: Back closes it.
- Home reads the projects in the background (the list shown last appears at once); the cards, the Play Store list and the
  account card no longer read files or the Keystore while they are drawn.

## Updates

- A required update now shows a card: the version step (for example v2.1.2 → v2.1.3), why it is needed and what is new.
  The download has no Cancel: Hide moves it to its own notification, and after an error Retry appears.
- This first step from v2.1.1 still uses v2.1.1's own dialog, with the new text.

## Your GitHub sign-in

- Nothing in an update signs you out. Another install of Sketchware AI on the same phone no longer counts as another phone.
  If you are ever signed out, the account card says when and why.
- GitHub's e-mailed sign-in code: the sign-in window shows Open Gmail and Paste & fill; copy the code, tap Paste & fill,
  and it goes into GitHub's own field and is verified. The same for a two-factor code.

## Backups

- The upload shows its real current speed and, once everything is sent, "waiting for GitHub to confirm" instead of a silent
  90%. If the phone slows the app's network in the background, the line says so. A backup up to 25 MB goes up as one file.

## The app's own look

- Projects without their own icon show their two letters in New Project, project settings and the Play Store, like on
  Home. The round Android icon is only what their APK gets.
- Restore: one slim card for GitHub, Demo and On this phone; no paragraph on Demo. Play Store: no cut subtitle, a slim
  Publish row, the creator and the version on their own lines, all four counters in glass, Like always answers.
- Bottom bar: no cut bump above Home (or Settings); the crest grows out of the bar's corner.

## Known limits

- Nothing here was run on a phone: screens were drawn with layoutlib from their own code, engine parts ran on the Android
  12 runtime harness and against a fake GitHub on the desktop JVM, the code helper in jsdom.
