# Sketchware AI v2.1.6

versionCode 213, `@VERSIONNAME@`, @BYTES@ bytes, sha256
`@SHA@`. Installs over v2.1.5 (same key). v2.1.5 asks for it by itself about 3.5 s after it opens (required, no Later),
downloads it in the app and checks its SHA-256.

## Download

- GitHub Release (permanent, once the release is made on github.com, see below):
  https://github.com/smartapps000-hub/SketchwareAI/releases/download/v2.1.6/Sketchware_AI_v2.1.6.apk
- Binaries branch (works now): https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.6.apk

The owner's 28 Nov directive "final public release" (four screenshots). The full record is
`engineering/v2.1.6-verification.md`.

## GitHub + JSON Live Demo (first in Restore projects → Demo)

- A new demo project made by the app itself, first in the Demo tab: type a GitHub user name (octocat is filled in) and
  tap **Load profile**: the app asks api.github.com, reads GitHub's answer with **JSON get text / get number**, builds
  a new JSON object with **JSON set** (`stats.followers`, `stats.public_repos`) and shows it with **JSON pretty**.
  **Repository names**: a **repeat** over **JSON count** reads `[i].name` of every repository into a JSON array, **JSON
  array to list** fills the list on the screen, and the newest repository's stars come from **JSON get number**.
- Made once by itself after the update; "Make the GitHub + JSON Live Demo" in the Demo tab makes it again. It opens,
  runs, exports and backs up like any project (Public / Private, Share, Publish).

## Sign-in

- **The sign-in window opens by itself** when GitHub has ended this phone's sign-in (the "GitHub didn't accept this
  phone's sign-in" card): at start after the update check, when it happens while Home, Settings or a GitHub screen is
  open, and when a backup or publish you tapped meets it. Once per ended sign-in (not again after you close it three
  times); never over the editors. After signing in, every waiting backup and publish goes on by itself.
- **8-digit codes**: the code bar reads how many digits GitHub's field takes. A 6-digit code is never typed into an
  8-digit field any more (GitHub's "Please match the format requested"); the bar says "Copy the 8-digit code from Gmail,
  then tap Paste & fill", and from a copied e-mail with several numbers the one of the right length is taken.
- **"Why? GitHub's security log ›"** first says which account is signed in here ("Signed in here as @…"), with Copy, so
  you read that account's log in the browser (switch accounts there if it shows another one).
- Why it said "didn't accept": GitHub answered 401 for the very token made at your sign-in (the card shows it unchanged),
  so GitHub ended it on its side; no update path of the app deletes or changes it. GitHub's security log says when and
  why (for example `oauth_authorization.destroy`). GitHub keeps 10 sign-ins per account, app and permission set and ends the
  oldest unused one when an eleventh is made.

## Settings

- **Notifications**: "New apps and versions from creators I follow" can be switched off (per GitHub account). Off stays
  off when you follow someone; notifications already shown go away.

## Security

- The app checks its own signing certificate. A copy signed with another key keeps working as an editor, but GitHub
  sign-in, backups to GitHub, Play Store publishing and in-app updates are off in it, with one message saying why.
- The GitHub token goes only over https to api.github.com and uploads.github.com; downloads GitHub sends elsewhere never
  carry it. An update installs only when its SHA-256 matches. The APK is no longer debuggable.
- Limit, said plainly: releases are signed with the repository's public test key, so a changed copy re-signed with that
  same key passes the check. Real protection needs a private release key kept off GitHub (your decision: phones would
  then need one reinstall or a key rotation).

## Known limits

- Nothing here was run on a phone: screens were drawn with layoutlib from their own code, the demo was made by the app's
  own code and built on the Android 12 runtime harness (and its JSON code run there on a hand-made answer in GitHub's
  shape: this machine can't reach api.github.com), the code bar's page reader in jsdom on stand-ins of GitHub's pages.
- The GitHub Release v2.1.6 has to be made on github.com by you (this session can't create releases); until then the app
  downloads the update from the binaries branch.
