# Sketchware AI v2.0.4

versionCode 205, `v2.0.4-208a80a-minApi26`, 142574278 bytes, sha256
`ab09287787427bca0dc21229f1476607e969a141dc566ec6e42e3d9e8868e004`. Installs over v2.0.3 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.0.4.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.0.4.apk

The next release takes versionCode 206. The full record is `engineering/v2.0.4-verification.md`.

## One GitHub repository per project, versions as releases

- **Back up → To GitHub** and the automatic backup after **Save & exit** now go into the project's own private
  repository `SketchwareAI-<ProjectName>` (made on the first backup; another project of the same name gets `-2`). The
  old shared repository `SketchwareAI-Projects` is no longer written to; its backups stay readable under
  **Restore projects → Older backups**.
- Every app version is a GitHub release tagged `v<versionName>-<versionCode>` with the project's `.swb` and, when you
  built it since the last backup, the APK as release files. Backing up the same version again updates that release
  (the APK stays when there is no newer one). Files go straight to GitHub's upload server; projects over 16 MB go as
  16 MB parts with a parts list, each checked.
- At most **5 versions** are kept: the oldest go first, except the version you pinned as **★ Standard**, which is never
  removed.
- **Public / Private** is one change on GitHub, no upload: it takes about a second.

## Publish to GitHub

- Tapping **Private** on a project card opens **Publish to GitHub**: the title, description, topics, a README (on/off,
  editable) and "What's New & Fixed" are filled in from the project itself. **Publish Public** writes README.md and
  CHANGELOG.md, saves the release notes and makes the repository public. A public project's README, CHANGELOG and
  description update with each later backup (your edits are kept).
- Tapping **Public** offers **Edit public details** and **Switch to Private**.
- The texts come from an on-phone comparison with the previous version (no network, no AI): new screens, widgets,
  components, events, libraries and permissions under ✨ New Features; changed blocks per event under 🛠 Logic & Bug
  Fixes; version / SDK changes under ⬆️ Upgrades. A snapshot of each version is kept in the project's data folder.
- The topic `#sketchware-ai` always stays: your other phones find the project's repository by it.

## Restore projects (full screen)

- **GitHub**: paste a link (a project repository, a folder or a .swb file); your projects on GitHub with Private/Public,
  Share and their versions (**Latest** first, **★ Standard** second), **Restore** and **★ Set as Standard**; the older
  backups. The list shows at once from the phone and refreshes from GitHub.
- **On this phone**: every .swb backup in the backups folder with its date and size, **Restore**; **Pick a .swb file**
  for any other folder. The app never deletes these files.
- A project restored from your own repository is linked to it again, so its next backups go into the same repository
  (never two projects into one repository).
- Shared links `https://github.com/<you>/SketchwareAI-…` and `sketchwareai://import?url=…` open this screen. On Android
  12+, github.com links open in the app only after you add them once: the screen's **Open shared GitHub links here**
  card goes to that setting. Nothing is restored without your tap.

## Signing in on a second phone

- Sign in as usual: nothing is locked to the first phone and the first phone is **not** signed out (the earlier "single
  device lock / auto-logout" request was replaced by your last directive). After signing in, the app looks for your
  `SketchwareAI-*` repositories in the background.

## Progress, Cancel and Retry

- Backups, publishing and imports run in a **foreground service** with a notification: the job, percentage, size and
  speed, and **Cancel**; at the end a notification says Done or Didn't work, with **Retry** and **Open on GitHub**.
  New permissions for that, which you asked for: `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` (no prompt;
  Android 13+ asks once for notifications when you tap **Hide**).
- The progress sheet has **Hide**, **Cancel** and **Open on GitHub**; after a failure **Retry** (only when retrying can
  help) and **Close**.
- The token is sent cleanly on every request and every file; a 401 on one file while GitHub still accepts the sign-in
  sends that file again; you are never signed out by an error. The cause of v2.0.3's 401 at 90% is still not known.

## Cards

- Project card: the name on one line up to the #number; the package and version on the left and Public/Private + Share
  on the right of the second row.
- Drawer card: "GitHub" and "Connected" on one line each (14sp / 11sp); copy buttons for @username and e-mail.

## GitHub component (your apps)

- User names work with or without "@" and as profile links (from an input field or a variable; a block's own text
  field starting with "@" is read by Sketchware as a link to another block, so type the name without "@" there).
- New blocks: **list files** (a repository folder), **list releases**, **download** (into the app's own Download folder,
  with progress), **show … in ListView** (readable rows: tap a repository for its files, a folder to open it, a file to
  download it) and **readable text of** (user, repositories, files, releases). New events: files listed, releases
  listed, download progress, file saved.
- The **GitHub API Demo** sample shows the profile as text and repositories / files in a list. Existing installs keep
  their old demo; make the new one from the GitHub blocks guide (**Create GitHub Sample Project**).

## Checked

Android-runtime suite on this APK, JVM tests against a fake GitHub, layoutlib renders of the new screens, release checks
(signature, 16 KB alignment, link checks, pinned resource ids) and three review passes: see
`engineering/v2.0.4-verification.md`. Not run: a phone, the real GitHub API, notifications and the foreground service,
deep links from other apps.
