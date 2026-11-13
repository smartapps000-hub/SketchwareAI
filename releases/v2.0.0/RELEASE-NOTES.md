# Sketchware AI v2.0.0

versionCode 201, `v2.0.0-5148377-minApi26`, 142414639 bytes, sha256
`d4ee6d84e7dd268af13aa5f00402d7c467fddd90d64441b2fdcbdef30cb302c0`. Installs over v1.9.9 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.0.0.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.0.0.apk

The next release takes versionCode 202. The full record is `engineering/v2.0.0-verification.md`.

## Sign in to GitHub without leaving the app

- Drawer > **Sign in with GitHub**: GitHub's page opens inside Sketchware AI, not in Chrome. Sign in to GitHub (the
  first time), the app fills in the code, tap **Continue** and **Authorize**: the window closes and you're signed in.
- Because the app stays in front, Android 15 no longer cuts the connection while the app waits for GitHub.
- Only github.com opens in that window. Sign in with your GitHub password (and 2FA code); "Continue with Google" and
  passkeys don't work inside apps.
- A v1.9.9 sign-in keeps working. GitHub now also gives a new sign-in your primary e-mail (read-only) for the drawer:
  sign out and in once to see it.

## Your GitHub account in the drawer

- Under the Sketchware AI card: your picture, name, @username, e-mail and **Sign out**.
- The "Cloud Sync & Account" item and its list of all projects are gone: backups are per project now.

## Back up and import one project at a time

- A project's **Back up** asks: **On this phone (.swb file)** or **To GitHub** (only that project).
- New projects are backed up to GitHub by themselves when you're signed in; without internet they wait and go up later.
- The **Restore projects** card asks: **Restore a .swb file** or **Import from GitHub**: your own backups, or paste a
  GitHub link to a repository, a folder or a .swb file (public repositories work without signing in).

## Sign-in requirement (still off)

- When `config/app-control.json` says `"require_login": true`, sign-in is also needed for the block editor, the Library
  Manager and each export, and the sign-in and the file are checked online every 6 hours. It is off in this release.

## Look

- The GitHub cards and buttons use the subtle glass look of the drawer's header card, without the orange glow. The
  owner's Liquid Glass design files are in `design/` of the repository.
