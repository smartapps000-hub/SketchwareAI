# Sketchware AI v2.0.2

versionCode 203, `v2.0.2-4d98560-minApi26`, 142463769 bytes, sha256
`018a34b789fc10b09db706e7e02d56d07c183498518223bb46fb1a2e4ed537e7`. Installs over v2.0.1 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.0.2.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.0.2.apk

The next release takes versionCode 204. The full record is `engineering/v2.0.2-verification.md`.

## GitHub sign-in

- After you sign in on GitHub's page, GitHub shows its home feed ("Good evening, …"). The window now goes from there
  (or the dashboard) back to the code page by itself, so you only tap **Authorize**. It never does this while GitHub's
  "Too many requests" pause lasts, or on a page you went Back to.
- **Continue with Google / Apple** no longer ends on a blank page: the tap starts the browser sign-in before anything
  loads (the code is copied; paste it on GitHub's page in the browser and approve).
- When GitHub says yes, the browser tab closes (browsers that open it in the app's own task, such as Chrome's tab), the
  window closes, the drawer closes and you are on your projects list.

## Drawer account card

- "Sign in with GitHub" is shown in full. Signed in, a long name, @username or e-mail scrolls sideways instead of being
  cut; **Sign out** sits under them.

## Back up and import with progress

- **Back up to GitHub** shows a sheet from 0 to 100%: checking the repository, packing (with the libraries / custom
  blocks you ticked), uploading ("1.3 MB of 2.0 MB"), saving. **Hide** keeps it running and a message says when it
  ends; if you leave the app, the sheet waits at 100% with **Done**.
- **GitHub import** shows the same sheet while it downloads and restores.

## Share button on project cards

- After a backup to GitHub, a share button appears next to the project's #number. It copies the backup's link and opens
  Android's share sheet. The link is kept with the project and works offline. The backup repository is private, so the
  link opens for your GitHub account (on any phone signed in to it).

## Import guide

- **ⓘ** in Import from GitHub: three steps with pictures (copy the link, paste and Import, pick a backup and choose
  libraries); drawn by the app, so it works offline.
- A link to a repository or folder lists every backup (.swb) in it, each with Import.

## GitHub component for your apps

- **Components → + → GitHub**: a GitHub component for apps you make.
  - Blocks (drawer category **GitHub**): set token, has token, get user, list repositories, get file, upload file, run
    workflow.
  - Events: user loaded, repositories listed, file downloaded, file uploaded, workflow started, error. The error says in
    plain words what went wrong: token, missing permission, rate limit and when to try again, not found, the workflow
    trigger, no network.
- A helper file (`SketchGitHub.java`) is written into the project, so nothing is downloaded, and INTERNET is added to
  the manifest.
- A token typed into an app can be read by anyone who has that APK. The build warns when it finds one, on Run and on
  every export. Use a fine-grained token limited to what the app needs.

## Not changed

- The sign-in stays bound to this phone (Android Keystore, AES-256-GCM), checked online every 6 hours; the sign-in
  checks across the app are unchanged.
