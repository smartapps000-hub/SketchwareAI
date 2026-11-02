# Sketchware AI v1.9.2

versionCode 192, `v1.9.2-07bd11c-minApi26`, 142283227 bytes, sha256 `d0a3cd128761b97c3db9944ce1e50116c8212c235667f6cd05bb08e8827a0412`. Installs over v1.9.1.

- **Toolbar colour**: the app bar of a built app is the project colour, as on the design canvas. With dynamic colour on,
  it took the wallpaper's purple before. Buttons and other roles still follow dynamic colour when it's on.
- **Logic editor drag bar** (Delete / Copy / Collection / Detail): dark surface colours, light text.
- **`</>` sheet**: a one-line hint instead of the long text, more room for the code, the keyboard opens with Edit.
- `int` locals stay custom variables: as Number (a double) `String.valueOf` would show `3.0` and `list.get(x)`
  would stop compiling. Tell me if you want them converted anyway.
- Checked on the APK's own classes in the Android 12 test runtime (no phone): 913 (Material 3 dark, dynamic colour) and
  919 (Java editor 95/95) build; apps are zip-aligned with `-P 16` and signed v2 + v3; release checks pass. Not run: the
  logic editor, the sheet and the built app on a phone.
