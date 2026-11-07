# Sketchware AI v1.9.6

versionCode 196, `v1.9.6-a76e9dc-minApi26`, 142325713 bytes, sha256
`86af1443ad821b47bb9ab2ad78b3f7500ada22038c35808c2f9203976b3975a8`. Installs over v1.9.5 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.6.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.6.apk

## Downloads (your "Endhayya Saami.bin")

- **No more `.bin`**: the name comes from the server (Content-Disposition, `filename*=` first), else from the link (a
  `file=`, `name=`, `title=` … parameter that has an ending, else the last part of the path), with the ending of the
  MIME type. When the name still has no ending, the app asks the server once (HEAD, then the first 64 bytes) and takes
  the ending from its answer or from the file's first bytes (ID3 / MPEG → .mp3, ftyp → .mp4 / .m4a, %PDF, PK → .zip,
  PNG, JPEG, WebP, Ogg, FLAC, WAV, WebM, SVG). Your link would get `.mp3` from its ID3 header. If the server answers
  nothing useful, the name stays without an ending (never `.bin`).
- Cookies and the browser's user agent go with every download (also to that check, but only to the link's own host).
- *only types* empty, `*` or `*/*` = every file.
- **File → download file** `[link] to folder [ ] name [ ] notify [ ]`: downloads without a WebView (empty name = the
  server's name).
- **Long press on an image** in a WebView with *allow downloads*: *Download image*.

## Browser blocks (WebView)

- **block ads** `[true]`: requests to 125 ad and tracker domains (and their subdomains) get an empty answer, so their
  ads don't load. A built-in list, not a full filter list; works on Android 8+ (does nothing on older Android).
- **block pop-ups** `[true]`: windows a page opens by itself are blocked; a window opened by a tap (download buttons,
  `target=_blank`) opens in the same WebView, and a file it answers with is downloaded.
- **mixed content** `[COMPATIBILITY / NEVER_ALLOW / ALWAYS_ALLOW]`: COMPATIBILITY is Chrome's rule and the default;
  ALWAYS_ALLOW is there if you need it (it lets http scripts run on https pages).
- **search Google for** `[text]`, **history to list** `[list]` (addresses visited, oldest first), **user agent**
  `[text]` (empty = the WebView's own).
- Put *block ads* / *block pop-ups* in onCreate before *loadUrl*.

## Permission picker

- The permission slot's list now looks like *Select widget*: dark rounded box (#252528, 16dp), compact search field,
  radio bubble, bold white name, grey line under it. One tap picks and closes; no Save / Cancel (Back closes it).

## Guide for web and permission blocks

- **Tap** a web or permission block in the drawer: a sheet shows the block and three steps: 1 where to drop it,
  2 what to plug into it, 3 what the app does. Also the *ⓘ Web blocks guide* / *ⓘ Permission blocks guide* rows.
  A long press in the drawer drags the block (that's the editor's own gesture), so the guide is on a tap.

## Checked (Android 12 test runtime and layoutlib, no phone)

See `engineering/v1.9.6-verification.md`. Not run: the picker, the guide, ad / pop-up blocking, downloads, the image
long press on a phone and on real pages.
