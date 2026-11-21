# Sketchware AI v2.1.0

versionCode 207, `v2.1.0-ed691a0-minApi26`, 142693495 bytes, sha256
`e2330d1c2d73b6f13f7985c9a266ec5f775753fd7fa81ab265a96ac0f0b08edb`. Installs over v2.0.5 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.1.0.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.0.apk

The v2.0.5 phone fixes (16 Nov report) plus the new Home: bottom bar, full-screen tabs, Play Store, Community, one
phone per GitHub account and an update check at start. The full record is `engineering/v2.1.0-verification.md`.

## Home: the Honey-Drop bar

- The side menu (≡) and the floating + button are gone. A glass bar at the bottom has six tabs: **Home**, **Create**,
  **GitHub**, **Play Store**, **Community**, **Settings**. A tap drops a honey drop onto the tab, ripples the bar's
  edge and flows the orange pill over (a soft droplet sound plays unless the phone is on silent or vibrate; Settings
  has a switch). The bar slides away while the project list scrolls down and comes back on the way up.
- Every tab is a full screen. Back inside a tab goes back page by page; on a tab's first page Back asks "Do you want
  to close [tab]?" (Cancel / Close). Back on Home leaves the app.
- **Create** is the New Project screen (names up to 64 characters). **Settings** holds what the side menu had: the
  GitHub account card, Back up after saving, the sound switch, App updates, System Settings, Create Release Keystore,
  App Theme, Developer Tools, Changelog, About.

## Restore projects (the GitHub tab)

- Three equal tabs on one line: **GitHub | Demo | On this phone**. The Demo row left the projects list: demo projects
  are in the Demo tab, numbered #1, #2, #3 (newest first) with **Open**.
- Project cards are compact like the Home cards: name, Private/Public, Share, the version row, then "+N older →",
  **Restore** and **Pin** on one line.
- **Paste** in the link box pastes and imports at once (the front version: ★ pinned, else the newest). The separate
  "Import from link" button is gone.

## Repository viewer no longer hangs on "Reading … on GitHub…"

- Each request waits 10 seconds at most and is tried once. The page saved on the phone shows at once ("Saved on this
  phone · checking GitHub…"); if GitHub doesn't answer it says so ("GitHub didn't answer within 10 seconds") with
  **Try again**.

## Backups, imports and exports run together, in the notification

- Up to three backups run at the same time: two lanes for the ones you start, one for automatic backups (a project's
  own jobs never overlap). Imports have their own lane. Several running jobs share one notification ("3 GitHub jobs"
  with a line and percentage each); a single job keeps its notification with Cancel.

## "Sign-in not accepted" (HTTP 401)

- A 401 is now confirmed by asking GitHub again 3 seconds later before the card says the sign-in wasn't accepted.
  The note shows when this sign-in was made, with which version, how many sign-ins this phone has made, and when GitHub
  last accepted it, so the next report shows why GitHub dropped it. The card's state line wraps to two lines instead
  of overlapping. The saved sign-in is kept across app updates (it is never cleared by an update).

## Play Store and Community (on GitHub)

- **Play Store**: apps made with Sketchware AI that their owners published (public project repositories with the
  topic `sketchwareai-playstore`). The list is kept on the phone for offline use. An app's page shows its pictures (tap
  for full screen), **Download & Install APK** (with 0–100 %), **Import .SWB**, **Watch demo video**, **Open on GitHub**
  and its reviews.
- **Publish your app** picks one of your backed-up projects, takes a name, a description, a video link and up to 8
  screenshots, and, after you confirm, makes that repository public with the store topic and GitHub Discussions on (for
  the reviews). The private backup repository `SketchwareAI-Projects` is never published.
- **Community**: Sketchware AI's GitHub Discussions (questions, ideas, show and tell): read, post in a category,
  reply. GitHub shows Discussions only to signed-in accounts.

## One phone per GitHub account

- Signing in on a phone records that phone in your private `SketchwareAI-Projects` repository
  (`.device-session.json`: a hash of this install's random id and your login, the phone model and the app version; no
  token). When the account signs in on another phone, this phone signs out at its next check with "Signed out: this
  account was signed in on another device". Network errors never sign out. Your projects on the phone stay.

## Update check at start

- About 3.5 seconds after Home opens, Sketchware AI reads its release file. When a newer version is out, **Update**
  downloads it in the app (0–100 %), checks its SHA-256 and opens Android's installer (Android asks once to allow
  installs from Sketchware AI). When an update is required, **Later** is hidden and Create waits for it. Only after
  this comes the sign-in check. Settings › App updates shows the version and "Latest", or the update.

## Known limits

- Reviews and Community need GitHub Discussions switched on for the repository they read (Publish switches them on for
  your app; Sketchware AI's own Community needs its owner to switch them on).
- Nothing here was run on a phone: the bar animation and sound, the installer, notifications and the tabs were drawn
  with layoutlib from their own code or run on the Android 12 runtime harness and against a fake GitHub on the desktop
  JVM.
