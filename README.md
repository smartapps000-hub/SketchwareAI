# Sketchware AI: release files

This branch holds only built files, no source. The source is the `main` branch.

## v1.8.6 (versionCode 176, `v1.8.6-9bc690c-minApi26`)

| File | sha256 |
|---|---|
| `v1.8.6/SketchwareAI-v1.8.6.apk.7z.001` | `ca50b9ac5fa2dd78a3cf443eef9864335fbd738a149440e51f33ad7469d788e6` |
| `v1.8.6/SketchwareAI-v1.8.6.apk.7z.002` | `932f77f9f3ad4a7a7d4d5b2804d385ac5e1ec8c8137a1e4517be01236a705477` |
| the APK inside, `SketchwareAI-v1.8.6.apk` (138,320,721 bytes) | `8cf18893a303293fd78f44f9f36b5b5ff5436eda82baddc5135a94c45e67382b` |

| `v1.8.6/SketchwareAI-v1.8.6-patches.zip.7z.001` | `c960e5633f80aece72d64c938f38d2d67eb7b192ea791cb2c55c286c34d1573d` |
| `v1.8.6/SketchwareAI-v1.8.6-patches.zip.7z.002` | `542c09f1822fe6a1340dfa0e9da03e0853ae9ead000ea63bcc8008b0ed6d650f` |
| the patches zip inside, `SketchwareAI-v1.8.6-patches.zip` (139,350,979 bytes; 53 patches against the upstream zip, `main` at 49b245b) | `38319c54fcaea5ef888a3ba41c670e47da6e0498784c17885b1a184aca1e8758` |

The files are split because GitHub refuses files over 100 MB. The patches zip is only needed to rebuild the source
from the upstream Sketchware Pro zip without GitHub; a clone of `main` has the same history. Download both parts, open `.7z.001` in ZArchiver (or
`7z x SketchwareAI-v1.8.6.apk.7z.001`). Signed with the public test key, like every release, so it installs over v1.8.5.
Release notes: `releases/v1.8.6/RELEASE-NOTES.md` on `main`.
