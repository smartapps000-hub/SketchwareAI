# Sketchware AI v2.1.4

versionCode 211, `@@NAME@@`, @@SIZE@@ bytes, sha256
`@@SHA@@`. Installs over v2.1.3 (same test key). v2.1.3 asks for it by itself about 3.5 s after it opens (required,
no Later), downloads it in the app and checks its SHA-256.

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v2.1.4.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.4.apk

The owner's 22 Nov review of v2.1.3 (10 screenshots). The full record is `engineering/v2.1.4-verification.md`.

## GitHub sign-in: the e-mailed code

- The code bar now reads GitHub's page itself, so it also shows on GitHub's "Confirm access" page and on "Enter the
  verification code sent to b***@gmail.com" (it didn't in v2.1.3: those pages have other addresses).
- On "Confirm access": **E-mail me a code** presses GitHub's own "Send a code via email".
- Open Gmail, copy the code, come back: the code goes in by itself and GitHub's Verify is pressed (no Paste needed).
- Optional, your switch: **Fill codes from e-mail notifications · Turn on**. With Android's "Notification access" for
  Sketchware AI on, GitHub's code goes from Gmail's notification straight into the field. It is used only while the sign-in
  window waits for a code, only for notifications that mention GitHub and hold a 6- or 8-digit code; nothing is kept or
  sent anywhere. Apps installed from a file: Android first asks for App info → ⋮ → Allow restricted settings.

## Signed out after an update

- The sign-in isn't removed by an update: GitHub answered "Bad credentials" for the saved token, which means GitHub ended
  that token on its side (its 10-sign-ins-per-app limit, access removed in GitHub's settings, or the token found in a
  public place). From v2.1.4 the account card says whether the token sent was the very one made at sign-in, and a
  **Why? GitHub's security log** link opens GitHub's own record of when and why.

## Publish

- Step 1 is one line: the chosen project with **Change**; steps 2 and 3 come right under it.
- The description is written from the project itself (screens and how they are reached, the buttons to tap, what it does,
  the permissions, the Android versions) when the field is empty; **✨ From project** writes it again.
- After publishing, the sheet stays with **Done · See it in the store**: the store opens with your app first, lit and
  marked "Just published" (also before GitHub's search lists it), and reads the store again.
- "What's new" shows headings and points instead of Markdown marks.

## Follow and creators

- Follow is always on the app page with the creator's followers ("＋ Follow · 314"); on your own app it shows your
  followers and opens your page. The creator page has Follow too.
- Creator levels: ✔ Verified, ★ Trusted, Not verified (tap the badge for how they are worked out: from the public GitHub
  profile and the store; GitHub has no "verified person" mark for accounts).
- The creator's country with its flag, from their GitHub Location ("🇮🇳 India (Hyderabad, Telangana)"), and since when
  they are on GitHub. No Location: "Country not shared on GitHub" (nothing is guessed).

## Known limits

- Nothing here was run on a phone: screens were drawn with layoutlib from their own code (the sign-in bar's states as the
  window sets them; GitHub's page there is a drawn stand-in), engine parts ran on the Android 12 runtime harness, the page
  reader in jsdom on stand-ins of GitHub's pages (GitHub's real pages can't be opened from the build machine).
