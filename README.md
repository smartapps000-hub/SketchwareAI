# Sketchware AI: release files

This branch holds only built files, no source. The source is the `main` branch.

## v1.8.8 (versionCode 178, `v1.8.8-f6b8e9f-minApi26`)

| File | sha256 |
|---|---|
| `v1.8.8/SketchwareAI-v1.8.8.apk.7z.001` | `e2b332e041362bb59b8fb7eea2ebbea7101b97c0857def7123533032e2dd3b60` |
| `v1.8.8/SketchwareAI-v1.8.8.apk.7z.002` | `579b939bdcfa425449ff69867bffd3a009d216b889ecdbe6654cd039a2594c51` |
| the APK inside, `SketchwareAI-v1.8.8.apk` (138,448,009 bytes) | `0f3ca85d2bf3041b808c454c310c76faaf6d72f5db66a42b8f164cf98a1ed56d` |
| `v1.8.8/SketchwareAI-v1.8.8-patches.zip.7z.001` | `c7f8ab58638786369861348a6d1ee09bdd5c67b3b5f36b9efd3db64acd094612` |
| `v1.8.8/SketchwareAI-v1.8.8-patches.zip.7z.002` | `93ba8661309651bd042ac97f90d1d79cd39dde850d71a710f0a24b243d9f701f` |
| the patches zip inside, `SketchwareAI-v1.8.8-patches.zip` (139,564,473 bytes; 59 patches against the upstream zip, `main` at 6f2c8de) | `dae256be8bffb78eef5129bdc7647ae9b94846fca4c95a653992a34dab1b0fd0` |

Signed with the public test key, like every release, so it installs over v1.8.7. Release notes:
`releases/v1.8.8/RELEASE-NOTES.md` on `main`.

## v1.8.7 (versionCode 177, `v1.8.7-b2bdd42-minApi26`)

| File | sha256 |
|---|---|
| `v1.8.7/SketchwareAI-v1.8.7.apk.7z.001` | `bf461ee37c0ec71c34da7225ad1b73ecfe15d22ae1e81019c27d5babbb0c7025` |
| `v1.8.7/SketchwareAI-v1.8.7.apk.7z.002` | `1d8af386a86edc324ac2892e4e3b5b3d4172b9ac682cc05af677dcd492bb55ae` |
| the APK inside, `SketchwareAI-v1.8.7.apk` (138,357,541 bytes) | `0f616950c61f90f74ef2e480df2244ddc2a7163ef2dbafc34ec863013a04c35d` |
| `v1.8.7/SketchwareAI-v1.8.7-patches.zip.7z.001` | `55dd5dbf395128ba77e1cf3a725886e0f655e7059e99830a16b83a76019278cc` |
| `v1.8.7/SketchwareAI-v1.8.7-patches.zip.7z.002` | `7dfe9bd17bb87c40ff4845d8cc1475ffbb9303aa19dcde54a387947291e5b04b` |
| the patches zip inside, `SketchwareAI-v1.8.7-patches.zip` (139,443,565 bytes; 55 patches against the upstream zip, `main` at 1b3bd75) | `089a0ea2d86003edc5b599cf0102e3e74ab64634545e95d0307b30fe19340567` |

Signed with the public test key, like every release, so it installs over v1.8.6. Release notes:
`releases/v1.8.7/RELEASE-NOTES.md` on `main`.

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
