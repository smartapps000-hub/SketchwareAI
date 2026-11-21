# Sketchware AI v2.1.1

versionCode 208, `@@VERSIONNAME@@`, @@SIZE@@ bytes, sha256
`@@SHA256@@`. Installs over v2.1.0 (same test key); v2.1.0 offers it by itself at start (Update → in-app download →
SHA-256 check → Android's installer).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.1.1.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.1.apk

The owner's 21 Nov directive (v2.1.1) and his two corrections the same day (default icon, glass drop). The full record
is `engineering/v2.1.1-verification.md`.

## The default app icon

- An app built from a project **without its own icon** now gets the original Android robot (the white head of Android
  Studio's own default icon, unchanged) on an Obsidian glass squircle with a warm #FF6D24 / #FFB347 glow and a glass
  rim, instead of the green grid icon. Existing projects without an icon get it at their next Run or export.
- In Sketchware AI's own lists such a project keeps its two letters (GO, NV …). A project with its own icon shows that
  icon everywhere and its app gets it, as before.

## The bottom bar: clear glass

- No gold anymore. The selected tab sits on a raised glass crest of the bar's own top edge, its icon lifted onto it with
  a soft orange light under it.
- A tap, in slow motion: a clear glass teardrop (narrow neck, heavy belly) falls with three tiny droplets; the bar's
  edge dips into a crater under it and the icon dips; a crown of glass spikes and a jet throw five beads up; the icon
  rises onto the new crest; damped glass waves run to both ends of the bar and settle.
- New sound (made for the bar): a water-drop "plink" (520 → 1180 Hz), a soft honey body (180 → 95 Hz) and a short
  ripple tail; silent on Silent / Vibrate and switchable in Settings as before.

## Play Store

- The search box is at the top. Typing filters the apps on the phone; the keyboard's Search asks GitHub; "@name" opens
  that creator.
- Cards and app pages show the app's real icon (written to its repository as `playstore/icon.png` at every backup or
  publish) and its version line: "Updated to v4.7.7 (85)", "Re-published v4.7.6 (84) • Update #3".
- An app's page: icon, name, **@creator** (tap: the creator's page), a row of **Downloads** (GitHub's download counts),
  **Likes** (tap to like or take it back), **Shares** (Share opens Android's share sheet) and **Views** (counted once per
  signed-in account), **What's new** (the version's changelog), pictures, Get it, reviews.
- A **creator's page**: picture, name, @login, bio, apps / projects / followers / downloads, their store apps and their
  other projects.
- **Publish**: the project you chose last time is chosen again, and its words and pictures are kept on the phone as you
  type and pick (`publish_staging`), so a sign-in, a turned screen or leaving the screen loses nothing. Publishing runs
  in the background with its notification; a refused sign-in pauses it and Sign in again continues it by itself. The
  question is now "Make it public?" with the repository's name in the text.

## Community

- A feed built in the app: discussions from Sketchware AI's own repository (when its Discussions are on), from the Play
  Store's apps, from repositories with the topic `sketchwareai-community` and from your own repositories with
  Discussions on; newest first, kept on the phone for offline reading. New post asks where, then the category. A post's
  author opens their creator page. Nothing links to a Discussions page that is switched off (the old "Open on GitHub"
  404).

## Restore projects

- Slimmer cards in all three tabs, each with the project's icon (or its two letters): the GitHub cards have one version
  button ("v4.7.6 (84) · ★ Pinned · New +4 ▾", a tap lists the versions), Restore and Pin.

## Sign-in

- "Sign-in not accepted" quotes GitHub's answer once ("HTTP 401 …").
- A backup or Play Store publish stopped by a refused sign-in runs again by itself after Sign in again, from where it
  was.
- Android's own backup and phone-to-phone transfer leave the GitHub sign-in out (its key never leaves the phone: a copy
  could only arrive unreadable); after such a restore the app simply starts signed out.

## Known limits

- Likes, Shares and Views need a sign-in to read and count (GitHub shows reactions to signed-in accounts only); an app
  gets its counters at its first publish with v2.1.1. Downloads work signed out.
- Sketchware AI's own Discussions are still switched off on GitHub; Community shows the other repositories until its
  owner switches them on (Settings › Features › Discussions).
- Nothing here was run on a phone: the screens were drawn with layoutlib from their own code, the engine parts ran on the
  Android 12 runtime harness and against a fake GitHub on the desktop JVM.
