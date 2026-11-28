# Sketchware AI v2.1.5

versionCode 212, `v2.1.5-c9bb9ae-minApi26`, 142,931,648 bytes, sha256
`392da6f2e1262ac2c214ee0474b3a27032a962c12aff8ab94ca085c834789c38`. Installs over v2.1.4 (same test key). v2.1.4 asks for it by itself about 3.5 s after it opens (required, no
Later), downloads it in the app and checks its SHA-256.

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.1.5.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.5.apk

The owner's 23 Nov review of v2.1.4 (12 screenshots). The full record is `engineering/v2.1.5-verification.md`.

## Country and creator levels by themselves

- The country comes by itself: from the GitHub Location when there is one, else from your store pages. The Publish page
  shows "🌍 Country on your store page: 🇮🇳 India (from this phone) · Change" (this phone's, another country, or none);
  "Make it public?" names it before anything is published. On your own creator page without a Location: "Show 🇮🇳 India
  on my store page" puts it on your published apps in one tap.
- Levels are worked out by themselves, with nothing to fill in: **✔ Verified** = a GitHub account older than 30 days with
  an app in the store; **★ Trusted** = on GitHub for a year with 3 apps, or 10 followers, or 50 downloads. Your account
  (January 2023, four apps) is ★ Trusted. Tap the badge for the rules.

## Follow like Subscribe

- One pill, always the same size and place: orange **＋ Follow** → glass **✓ Following** with a small bounce, and it stays
  Following for your account (also after closing the app).
- A long @name or version scrolls sideways; the followers count is under the title.
- Followers and Following lists of any creator (tap Followers / "Following N ›" on a creator page; on your own app, the
  "👥" pill); people you follow are marked.
- The store shows **From creators you follow** first.
- After your first Follow, a notification tells you when a creator you follow publishes an app or a new version to the
  store (checked every few hours; backups never notify). Android 13+ asks for notifications when you follow.

## GitHub sign-in codes

- A code you copy while the sign-in window is open goes in at once. Easiest with Gmail: pull down your notifications and
  tap **Copy code** on GitHub's e-mail; the code goes in when the window is back (no need to open the e-mail).
- **Paste & fill** before copying now says what to do instead of an error; the window's own sign-in code (like 4101-C456)
  is never taken for GitHub's e-mailed code.
- GitHub Mobile's page ("We sent a verification request… 77"): only the GitHub Mobile app can answer it (GitHub sends the
  request there and nowhere else). The bar says so and offers **E-mail me a code** (when GitHub offers it) and
  **GitHub Mobile** (opens that app).

## JSON power pack (blocks)

- 19 blocks under "JSON power pack" in the operators drawer: read text / number / boolean / a part at a path
  (`user.name`, `items[0].title`, `items[-1]`), has, count, type, is valid, pretty; set text / number / boolean / JSON and
  remove on a String variable; keys and arrays into lists, an object into a map. Tap a block for its guide.
- Apps get a small generated `SketchJson` on Android's own JSON code: no library, no permission. Wrong or missing parts
  read as empty / 0 / false; text that isn't JSON is never overwritten.

## Known limits

- Nothing here was run on a phone: screens were drawn with layoutlib from their own code, engine parts and a project with
  every JSON block ran on the Android 12 runtime harness, the page reader in jsdom on stand-ins of GitHub's pages.
- Android 15 hides one-time codes from notification readers, so on Android 15 the code comes through Copy code (or
  copying it in Gmail), not by itself from the notification.
- Notifications for followed creators run when Android lets background work run (every few hours, later for a rarely used
  app); after a phone restart they start again once the app is opened.
