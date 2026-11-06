# Sketchware AI v1.9.5

versionCode 195, `v1.9.5-bfa2ed6-minApi26`, 142306831 bytes, sha256
`bad13e48a1e21bfe2edf270b813e87ae63391ae745164702b42c23d6ac42adb1`. Installs over v1.9.4 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.5.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.5.apk

## Fixes from your v1.9.4 phone test

- **Permission blocks visible**: the drawer has a new category **Permission** (after More Block). v1.9.4 had put the
  blocks at the end of Component's first part.
- **Searchable permission list**: tap the block's slot. Groups come first (Storage/Media, Camera, Microphone, Location,
  Bluetooth, Notifications, Phone, SMS), then every Android permission; typing "blue", "notif", "media" narrows the
  list; a name that isn't listed (a library's own permission) can be typed and used. Storage and Bluetooth names are
  bridged by Android version (READ_EXTERNAL_STORAGE = photos/videos/music on 13+, BLUETOOTH = connect + scan on 12+);
  SMS, phone and approximate-location names stay exactly what you picked.
- **Downloads like a browser**: *only types* takes file endings (`.mp3`, `mp3`, `.apk`), MIME types (`audio/*`) or `*`;
  empty = every file. The file name comes from the server (Content-Disposition), and the download manager is called off
  the page's thread, so the page doesn't wait. With *notify* on, Android's notification shows when it's done.
  Your `.mp3` filter in v1.9.4 compared against MIME types only, so nothing matched.
- **Back button**: in an app with a WebView, Back goes to the previous page first and closes the screen only when there
  is none (an open drawer closes first). Your own *onBackPressed* event, or one written in an add-source block, still
  decides.
- **WebView settings**: DOM storage, the database and `window.open` (opens in the same WebView) are on. Mixed content
  uses Chrome's rule: images over http load on an https page, scripts don't (see below).

## Material 3 only

- Every project with AppCompat gets the Material 3 theme and widgets; the old MaterialComponents theme is not generated
  any more. The theme follows the phone's dark mode unless the Library Manager's *Dark theme* switch is off.
- ProgressBar → Material's LinearProgressIndicator (horizontal) / CircularProgressIndicator (circle).
- Button styles: put `style="@style/Widget.Material3.Button.TonalButton"` (or `.OutlinedButton`, `.ElevatedButton`,
  `.TextButton`) in a Button's Custom attributes and it becomes a Material button with that style.
- Already Material 3 since v1.8.5–v1.8.9: Button, EditText (outlined), Switch, CheckBox, RadioButton, CardView, Toolbar.
- Kept as they are, so nothing stops compiling: a widget your code styles or moves itself, an app or screen with a
  theme of its own from the manifest editor, and projects without AppCompat (Project Configuration → Convert).

## For you to decide

- Mixed content: you asked for "always allow"; it lets a script loaded over plain http run inside an https page (anyone on
  the same Wi-Fi can change it). v1.9.5 uses Chrome's rule instead. Say if you want "always allow" anyway.
- `window.open` now opens in the same WebView, so a pop-up ad can take over the page. Keep, or only for downloads?

## Checked (Android 12 test runtime and layoutlib, no phone)

See `engineering/v1.9.5-verification.md`. Not run: the drawer category and picker dialog, the Library Manager and New
Project rows, back navigation, downloads and the WebView settings on a phone.
