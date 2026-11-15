# Sketchware AI v2.0.3

versionCode 204, `v2.0.3-0d1ab27-minApi26`, 142497857 bytes, sha256
`b70a700d7928f64bb5dd7136b5173f0a892480b4520070743475cb7c7d3c6406`. Installs over v2.0.2 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.0.3.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.0.3.apk

The next release takes versionCode 205. The full record is `engineering/v2.0.3-verification.md`.

## You stay signed in

- Only **Sign out** on the drawer card ends the GitHub session. Upload errors, time-outs, rate limits, GitHub refusing a
  request and the 6-hourly background check no longer sign you out.
- When GitHub refuses the saved sign-in (HTTP 401) the card says **Sign-in not accepted** in red with GitHub's reason and
  a **Sign in again** button; your backups wait and continue after you sign in again. A sign-in the phone can't read
  (Keystore, another app signature) is kept and explained the same way, never deleted.
- **Getting a sign-in code…** no longer hangs: the request has short time-outs (10 s connect, 15 s read) and is tried
  three times; GitHub's page opens at once so you can sign in while the code comes, and the code goes into the page when
  it arrives. If GitHub's page fails to load, it loads again when the code is there.
- The app never sets FLAG_SECURE. A black card in the recent-apps screen comes from the browser tab or WebView
  surface Android shows there, not from the app blocking screenshots.

## Drawer account card

- Top row: your GitHub picture, **GitHub** with **Connected** (or the problem in red) and a compact **Sign out**.
- Below: name, @username, primary e-mail and "N repositories · checked HH:MM", each wrapping to the next line when long
  (no sideways scrolling, nothing cut or drawn over).

## Backups that finish

- No more "1%" hang: backups run one after another in a queue. A tap goes before automatic backups; a second tap on a
  project already waiting joins it; the lock is always released, also after an out-of-memory error, so the next tap runs.
- Projects over 4 MiB go up in 4 MiB parts, each its own GitHub blob (Git Data API: blobs, a tree, a commit, the branch
  moved), each checked against its git id and sent again on its own after a dropped connection, a server error or a
  one-off refusal. A parts list (`<backup>.swb.parts.json`) holds the sizes and the SHA-256 of the whole backup;
  restoring joins and checks the parts before anything is restored. Files a backup no longer uses are removed in the same
  commit. Errors name the part and GitHub's request id.
- The import list opens at once with the backups saved on the phone and refreshes from GitHub in the background
  ("No connection" only when there is nothing saved yet), with **Try again**.

## Back up after saving

- When you save a project in the editor (Save, Save & exit, or leaving with *Automatically save* on) and you are signed
  in, it is backed up to GitHub silently, with the boxes of its last backup, only when its saved files changed; a Save
  without leaving at most every five minutes. Settings → **Back up to GitHub after saving** turns it off.

## Public / Private and Share next to the #number

- After a project's first backup to GitHub, its card shows **Private** and a **Share** pill next to #number.
- **Public** makes the project's own public repository `SketchwareAI-Public-<name>` (created private, the newest backup
  uploaded, then made public; your backup repository `SketchwareAI-Projects` always stays private). Later backups update
  it. **Private** makes that repository private again (PATCH /repos/{owner}/{repo}). Public asks first (anyone with the
  link can download the project, keys written in it included); Private acts at once.
- **Share** shares the public repository's page while the project is public, otherwise the private backup's link (a
  `.swb` file while the backup is one file, its parts list when it is in parts). Stored with the project, works offline.
- A copy renamed on github.com is still found (by GitHub's repository number); one made private there shows as Private.
  A project restored from a `.swb` starts without the old project's link or public copy.

## GitHub blocks, token page, sample project

- GitHub blocks are Royal Blue (#1F6FEB), *has token* emerald (#2EA043).
- The GitHub blocks guide has **Open GitHub token page** (github.com/settings/tokens/new) and **Create GitHub sample
  project**.
- **GitHub API Demo** (made once by itself when no project of that name exists, or from the guide): a user-name field,
  *Load profile* and *List repositories* buttons, the GitHub component, a `userName` String variable, the events
  *user loaded*, *repositories listed* and *error* writing to a text and a Toast, INTERNET. Public data only, no token.

## Not run here

The phone screens (sign-in window, drawer card, project cards, progress sheets, settings), GitHub itself (the sign-in,
backup, public/private and import code ran against a fake GitHub that follows GitHub's documented answers; the raw blob
download was checked once against the real API), and a backup of a large project on a mobile network. Device checks:
projects #644, #623, #606, a large project backed up to GitHub, Public then Private on one project, sign-in after a
cancelled or failed code request.
