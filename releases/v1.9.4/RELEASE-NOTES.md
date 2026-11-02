# Sketchware AI v1.9.4

versionCode 194, `v1.9.4-e33064d-minApi26`, 142299995 bytes, sha256
`7cf2625edb567834c64ff1dabd98ded7ed121569fb2119a9c0cba33e18dd3a9d`. Installs over v1.9.3 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.4.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.4.apk

## Runtime permissions (Component palette → Permissions)

- **request permission [group]**: Storage/Media, Camera, Microphone, Location, Bluetooth, Notifications, Phone, SMS.
  The app asks for what the group means on the phone it runs on: Storage on Android 14+ = photos, videos, music (choosing
  "Select photos" counts as granted), Android 13 = the three media permissions, older = storage; Bluetooth on 12+ =
  connect + scan, older = location; Notifications on 13+ = the notification permission, older = nothing to ask.
- **request permission name [text]** for any other permission (e.g. `android.permission.READ_CONTACTS`).
- **permission [group] granted** (true/false) and **permission status [group]** (GRANTED / DENIED / NEVER_ASK_AGAIN).
- **Events**: Add event → *When permission granted* (group) and *When permission denied* (group, never ask again).
  Several requests in a row are asked one after the other, not all at once.
- **Manifest**: the group's permissions are added, with `maxSdkVersion` where newer Android ignores them (storage READ up
  to Android 12, WRITE up to Android 10 for new targets; old Bluetooth permissions up to Android 11). The Compile log's
  notes list every permission the blocks added. Nothing is asked unless a block asks.

## WebView blocks (View palette, when the screen has a WebView)

- **allow downloads to folder [text] notify [yes/no] only types [text]**: files a page offers download with Android's
  download manager into Downloads/folder (empty types = every file; `application/pdf, image/*` = PDFs and images).
- **desktop mode [yes/no]**, **clear cookies and cache**, **enable file upload** (a page's "choose file" opens the
  phone's picker).
- **Events**: *download starts* (URL, type, size) and *download completes* (URL, file name, success).
- There is no block that ignores SSL errors (it would let anyone on the network read and change the page).

## Java editor: safe int → Number

`int count = 0;` becomes a Number variable (set / increase blocks) when every use works the same with a decimal number:
`count++`, `count += 2`, `count = count + 1`, comparisons, `String.valueOf((long)(count))`. If any line uses it as text
(`"Score: " + count`), divides, multiplies, takes a remainder or passes it to a method, it stays an int variable, and the
save names that line.

## For you to decide

- *Storage/Media* on target 33+ declares READ_MEDIA_IMAGES / VIDEO. Google Play accepts those only for apps whose main
  feature needs broad photo access; other apps should use the photo picker. Keep as is, or a separate Photos group?

## Checked (Android 12 test runtime, no phone)

See `engineering/v1.9.4-verification.md`: new fixtures 931 (web), 932 (permissions), 933 (permissions without
AppCompat) and the 919 Java editor fixture (101/101) built with this APK; the permission table for Android 6-17; the
web helper's filters; every class of the built apps loads; Export AAB and Export to Android Studio (Gradle) of 931/932.
Not run: the dialogs, downloads and file picker on a phone, the new palette rows, drop-down and events in the logic
editor.
