# Sketchware AI v2.0.1

versionCode 202, `v2.0.1-d450f80-minApi26`, 142434970 bytes, sha256
`c723881fefaa52c3f13bd2e0e416f652c0eaca4ca91711bb75bf37b4242408f5`. Installs over v2.0.0 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.0.1.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.0.1.apk

The next release takes versionCode 203. The full record is `engineering/v2.0.1-verification.md`.

## GitHub sign-in window

- Two rows at the top, nothing cut: **✕ · GitHub Sign-In · ⟳**, a thin orange loading line, then the code (tap to
  copy) and what is happening now.
- **⟳ Reload** reloads GitHub's page, or tries again after an error (with the same code while it is valid).
- **Sign in via browser** (bottom card): for accounts that use Google, Apple or a passkey. The code is copied, GitHub's
  device page opens in the browser; paste the code, approve, come back: the app checks at once and signs you in.
- No more blank page: when a sign-in leaves github.com (e.g. "Continue with Google"), the window goes back to GitHub's
  page and points at the browser card.
- "Too many requests": the window waits one minute with a countdown instead of loading again. Closing the window no
  longer throws the code away, so opening it again doesn't ask GitHub for a new one.
- Links that open a new window (create an account, forgot password) open in the same window.
- A slow network says so after 20 seconds; if Android stops the page's renderer, the window makes a new page instead of
  closing the app.

## Back up and restore

- A project's **Back up** sheet has the two classic boxes **Include used Local libraries** and **Include used Custom
  Blocks** (remembered), then **Save on phone** (.swb file) and **Back up to GitHub**: GitHub gets exactly the same
  .swb with the same boxes, in your private `SketchwareAI-Projects` repository.
- **Restore projects** shows two cards: **Restore local .swb file** and **GitHub import project**.
- Import from a link: a backup that brings local libraries now asks (Copy libraries / Without libraries); libraries
  already on your phone are never replaced. Custom blocks always come with the project.
- The import sheet's text is no longer cut; your list of GitHub backups is shown offline too (restoring still needs the
  internet).

## Slow and busy networks

- GitHub calls wait longer on 2G/3G, read again after a dropped connection or a passing server error (1, 3, 7 seconds),
  and stop asking while GitHub's limit is reached ("try again after HH:MM") instead of retrying.

## Not changed

- The sign-in stays bound to this phone (Android Keystore, AES-256-GCM, bound to the phone, the app's signing key and
  your GitHub account), checked online every 6 hours; the sign-in checks at startup, Library Manager, permissions,
  block editor, new / open project, backup / restore and builds are unchanged.
