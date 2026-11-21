# Sketchware AI v2.1.1

versionCode 208, `@@VERSIONNAME@@`, @@SIZE@@ bytes, sha256
`@@SHA256@@`. Installs over v2.1.0 (same test key). v2.1.0 asks for it by itself about 3.5 s after it opens: the
update is required (no Later), downloads in the app with its progress, is checked by its SHA-256 and opens Android's
installer (Android asks once to allow installs from Sketchware AI).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.1.1.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.1.apk

The owner's 21 Nov directive (v2.1.1) and his two corrections the same day (default icon, glass drop). The full record
is `engineering/v2.1.1-verification.md`.

## The default app icon

- An app built from a project **without its own icon** now gets the original round Android icon with every element in
  the same place (the circle, the grid lines, the 45° shadow, the robot head with its antennae and eyes, the soft
  shadow under the circle), in Sketchware AI's glass: a frosted slate-obsidian base, hairline grid lines, the shadow as a
  real dark cast shadow, the robot as light frosted white glass, a soft top gloss; no border. Existing projects without
  an icon get it at their next Run or export. New Project and a project's settings show this icon until you pick one,
  and Play Store cards show it for apps without an icon of their own.
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
- Cards show the app's real icon (written to its repository as `playstore/icon.png` at every backup or publish), or the
  default app icon above; and its version line: "Updated to v4.7.7 (85)", "Re-published v4.7.6 (84) • Update #3".
- An app's page, everything to act on at the first look: the icon, name, **@creator** (tap: the creator's page) and a
  version pill; a row of **Downloads**, **Likes** (tap to like or take it back), **Shares** and **Views**; right under
  it one row of capsules: **Install APK** (orange), **.SWB**, **Demo**, **GitHub**; then the description, **What's new**,
  the pictures (tap for full screen) and the reviews.
- A **creator's page**: picture, name, @login, bio, apps / projects / followers / downloads, their store apps and their
  other projects.
- **Publish**: the project you chose last time is chosen again, and its words and pictures are kept on the phone as you
  type and pick, so a sign-in, a turned screen or leaving the screen loses nothing, and they stay for the next publish
  (publishing without pictures keeps the store's pictures). Publishing runs in the background with its notification; a
  refused sign-in pauses it and Sign in again continues it by itself. The question is now "Make it public?" with the
  repository's name in the text.

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
  was, but only when the same account signs in (a publish never runs under another account); Sign out forgets them.
- Android's own backup and phone-to-phone transfer leave the GitHub sign-in out (its key never leaves the phone: a copy
  could only arrive unreadable); after such a restore the app simply starts signed out.

## Known limits

- Likes, Shares and Views are GitHub reactions (❤️ 🚀 👀) on the app's post: a like, a share or a view shows there under
  the account's name (the app page says so). They need a sign-in to read and count (GitHub shows reactions to signed-in accounts only); an app
  gets its counters at its first publish with v2.1.1. Downloads work signed out.
- Sketchware AI's own Discussions are still switched off on GitHub; Community shows the other repositories until its
  owner switches them on (Settings › Features › Discussions).
- Nothing here was run on a phone: the screens were drawn with layoutlib from their own code, the engine parts ran on the
  Android 12 runtime harness and against a fake GitHub on the desktop JVM.
