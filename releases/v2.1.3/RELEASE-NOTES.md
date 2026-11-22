# Sketchware AI v2.1.3

versionCode 210, `v2.1.3-e1bcdcb-minApi26`, 142,870,987 bytes, sha256
`4ecff3afa25fa1737f68ee7a792c8d850e058691ed1150e59b4ba5824b5bbba4`. Installs over v2.1.2 (same test key). v2.1.2 asks for it by itself about 3.5 s after it opens (required,
no Later), downloads it in the app and checks its SHA-256.

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.1.3.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.3.apk

The owner's 22 Nov phone review of v2.1.2 (11 screenshots). The full record is `engineering/v2.1.3-verification.md`.

## Update card

- "UPDATE AVAILABLE" in capitals in the warm orange (#FF8A65), the version step and a REQUIRED tag, then KEY FEATURES with
  a short line and numbered points, then BUGS FIXED · MORE EFFICIENT with ticks. Later and Update are glass pills like
  the projects' Public / Private pill; Update is amber glass with a download icon. The step from v2.1.2 to v2.1.3 still
  shows v2.1.2's own card; this one starts with the next update.

## Backups to GitHub

- A backup from 3 MB goes up in 2 MB parts, four at a time (one connection can't fill a phone's upload over a long
  route; four can), and the APK beside them.
- A lost connection (for example "Unable to resolve host uploads.github.com") no longer ends the backup: it tries again
  by itself up to three times, waits for the network to come back, and goes on from where it stopped (parts already on
  GitHub aren't sent again). Only then does it end with Retry.
- When a backup runs again (Retry, or another tap), its old "Didn't work" notice goes at once; a later error replaces it.
- The backup, import and update notifications are the app's own dark glass card with an orange progress line.
- Sizes are in MB as Android counts them (the v2.1.2 APK is 142.8 MB; v2.1.2 showed "136.2 MB" for the same file).

## Restore

- On this phone: one card per project, its backups inside as versions (newest first, a pinned one in front, ★ Pin), a
  small Restore in the card's title row, the project's icon from its .swb (else its two letters).
- Demo: one card per demo with its versions inside, Open in the title row, Pin, and once a demo is backed up to GitHub its
  Public / Private pill and Share (else Delete).

## Play Store

- Publish is a button in the header (the Publish row is gone).
- The app page's hero is slim: the title in orange on one line, @creator and the version as pills, Follow / Following,
  and Downloads / Likes / Shares / Views inside the card. Install APK, .SWB, GitHub and Review in one row. "About this app"
  shows two lines (a tap shows all of it).
- Follow needs GitHub's "follow accounts" permission, which sign-ins from v2.1.3 on ask for; an older sign-in is asked to
  sign in again the first time Follow is tapped.

## Faster

- Tabs open about 0.3 s sooner (the drop no longer waits for its splash) and fade in over 0.15 s; Back to Home fades in
  0.15 s instead of Android's 0.5 s. Create reads the project list off the main thread.
- Gson 2.15.0 (2.9.0 before). The lag wasn't Gson's: it was the screen animations and Create's list read.

## Sign-in

- The e-mailed code helper (Open Gmail, Paste & fill) now shows from GitHub's first sign-in page on, not only on the code
  page.
- No update path signs you out (checked again); if a sign-out happens, the account card says when and why.

## Known limits

- Nothing here was run on a phone: screens were drawn with layoutlib from their own code (the notification cards through
  Android's RemoteViews), engine parts ran on the Android 12 runtime harness and against a fake GitHub on the desktop JVM.
- Demos stay on the phone unless you back them up; nothing is made public by itself.
