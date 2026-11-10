# Sketchware AI v1.9.7

versionCode 197, `v1.9.7-34fabe7-minApi26`, 142331909 bytes, sha256
`067a92b3ccf56a48381d419115929444acc6b00aae2eccc0b683c8f2ab90c488`. Installs over v1.9.6 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.7.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.7.apk

## Your two v1.9.6 decisions

- **No request that could spend a one-time link.** Before a download whose name has no ending, the app now sends one
  HEAD request only (it reads the server's file name and type; it never fetches the file). The 64-byte GET is gone.
  When the server tells nothing, the file keeps its name without an ending (never `.bin`).
- **Library cache fix is in.** A Run build and an Export that rebuild the shared library resources at the same time
  can't read a half-written file any more (each writes its own file, then swaps it in).

## Ad and pop-up blocker, two layers

- **Network**: besides the 125 listed ad / tracker domains, requests whose host carries an ad brand word
  (`adservice.google.co.in`, `stats.g.doubleclick.net` ...) or an ad path (`/pagead/`) are blocked. The broader match
  only blocks parts of a page, never the page you open, so a wrong guess can't blank a site.
- **Cosmetic**: a style sheet hides ad boxes the page still drew (`.adsbygoogle`, Google ad slots, elements whose
  class starts with `ad-`, `ads-` or `advert`, ad iframes, pop-unders).
- **Back without bounce pages**: with *block ads* on, Back steps over pages on ad hosts. With it off, Back works as
  before.
- Still a built-in list, not a full filter list (EasyList); Android 8+ only. The pop-up block is unchanged.

## Download type

- **File → download file** has a new slot **type**. Tap it: *Default*, *Audio*, *Video*, *Images*, *Packages*,
  *Documents*, or type your own ending (`.flac`) or MIME type (`application/json`).
- It never stops a download. It only names a file that the link and the server left without an ending (Audio → `.mp3`).
- A project made with v1.9.6 keeps working (its block has no type = Default).

## Guide with real block shapes

- The guide sheet (tap a web or permission block in the drawer, or the *ⓘ guide* rows) now draws each block the way
  it looks on the canvas: its colour, the notch on top and the bump below where blocks stack, and a rounded slot for
  each value. The true/false block has pointed ends, the text block round ends. The sheet rises in with a short fade.

## Not in this release

- Multi-tab WebView blocks, the security settings sheet and the home-screen web app shortcut: v1.9.8, as agreed.
- GitHub sign-in: needs the PKCE flow without a client secret (a secret inside an APK can be read by anyone). Not built.
- Tamper / re-sign checks and R8 full mode: out of scope (the app is signed with the public test key; R8 broke the
  built-in Kotlin compiler in v1.8.9).

## Checked (Android 12 test runtime and layoutlib, no phone)

See `engineering/v1.9.7-verification.md`. Not run: the ad blocker, Back, downloads and the type dialog on a phone or a
real page; the guide and picker inside the logic editor.
