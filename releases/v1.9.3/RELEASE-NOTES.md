# Sketchware AI v1.9.3

versionCode 193, `v1.9.3-b77041f-minApi26`, 142283355 bytes, sha256 `f0206d3556afdce1389dde0f9c280b4c600456aea3c3c5b9291108feaf063dac`. Installs over v1.9.2.

- **Logic editor drag bar**: Copy is dark like Delete and Collection. v1.9.2 recoloured the wrong item after each
  drag; the precompiled bar's methods were read from its bytecode and each one now gets its own item.
- **Java → blocks**: parse int, toHashCode, matches RegExp and reverse join the Operator blocks that typed code turns
  into (substring-from and `? :` too, when the generator gives back the exact line).
- **Audits**: `engineering/v1.9.3-deep-audit.md` (Copy root cause, what maps to which palette block and what doesn't,
  XML editor plan, managers) and `engineering/v1.9.3-ecosystem-audit.md` (components, web engine, (i) docs, new-project
  defaults, suggested order). The component suite, web blocks and (i) docs are planned there, not built.
- Checked in the Android 12 test runtime (no phone): fixture 919 95/95 and its build; release checks pass. Not run: the
  logic editor bar on a phone.
