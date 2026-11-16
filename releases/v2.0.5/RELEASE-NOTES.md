# Sketchware AI v2.0.5

versionCode 206, `v2.0.5-1bc8cc1-minApi26`, 142612018 bytes, sha256
`9c68de5ffb6e6539cd1ba3cd242f14989e16c9152c787d6d7dc7e18506f194ea`. Installs over v2.0.4 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.0.5.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.0.5.apk

A stability release for the v2.0.4 phone report (16 Nov). The next release takes versionCode 207 (the bottom bar /
full-screen tabs overhaul of the v2.1.0 directive). The full record is `engineering/v2.0.5-verification.md`.

## Backups never sit at "1% • Waiting" again

- A backup you start with a tap no longer waits behind an automatic one: the automatic backup **pauses** ("Waiting:
  Vehicle Parking (34%) pauses for this one…"), your backup runs at once, and the paused one goes on afterwards from
  where it stopped. Several projects queued with taps run one after the other.
- A **watchdog** stops a job when nothing has moved for 5 minutes ("Stopped: nothing moved… the backup waits for the
  next chance; its finished parts are kept") and, if its connection doesn't close, lets the next job start anyway.
- **Resume**: a backup that stops halfway keeps its packed file for up to 7 days; the next try reuses the half-made
  GitHub release and skips the parts already there (parts are now 8 MB, so less is sent again).
- Every failure says **what to do** (for example "tap Sign in again", "Retry in a minute", "free some space").

## Notifications on Android 13–15

- The progress sheet asks for the notification permission once when a transfer starts. If notifications stay off it
  says so ("Notifications are off: the progress shows only here") with **Turn on**, which opens the app's notification
  settings.
- The sheet's buttons are on two rows (**Hide | Cancel**, then **Open on GitHub**), so no label is cut at large font
  sizes.

## "HTTP 401: Bad credentials"

- GitHub keeps 10 sign-ins per app for each account and ends the least used one when an 11th is made, so signing in again many
  times (or on several phones) can make GitHub refuse an older saved sign-in. The card now says this, backups wait
  without sending more requests, and **Sign in again** in the progress sheet renews the sign-in and runs the job again.
  You are never signed out by the app.

## Public details (fields can be typed in again)

- Title, description, topics (type a topic, **Add**; ✕ removes it; `#sketchware-ai` stays) and the README are editable.
  For a public project **Save & update** writes the description, topics, README.md and CHANGELOG.md straight away
  (on its own, not behind backups) and shows each step.
- The README now has a **Screens** table and the **MoreBlocks**; release notes also list new MoreBlocks, variables,
  screen parts (toolbar, drawer, floating button…), custom views and Java files. Versions backed up by v2.0.4 are still
  compared, without these new parts.

## Restore projects

- **Paste** sits inside the link box: it pastes the link and looks it up at once. The keyboard opens again on a tap.
- Versions are one horizontal row: the version you **★ Pin** takes the front slot, the newest is marked **New**, and
  "+N older versions →" scrolls to the rest.
- **Open on GitHub** (progress sheet, notification and shared repository links) now opens an **in-app repository
  viewer**: description, topics, stars, versions with **Restore** and **APK** (downloads to `.sketchware/downloads` and
  opens Android's installer), the README and the files, plus **Open in browser** and **Share**.

## Projects

- Project names may have up to **64 characters** (letters, digits and `_`, a letter first; before: 20).
- Demo projects (GitHub API Demo) are in their own **Demo** row above your projects: the newest demo first, older ones
  beside it; a long press deletes one.
- Drawer account card: "GitHub" and its state are never cut; @username is shown once; the e-mail stays on one line
  (copy buttons copy it whole); **Sign in again** and **Sign out** sit under the details.

## Known limits

- A block text field starting with "@" is read by Sketchware as a link to another block (upstream behaviour): type
  GitHub user names without "@".
- Nothing here was run on a phone. The new screens were drawn with layoutlib from their own code; the queue, watchdog,
  resume and texts were run on the Android 12 runtime harness and against a fake GitHub on the desktop JVM.
