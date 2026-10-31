# Sketchware AI - Core Engine Skill & Architecture Guidelines

## Project Identity & Target Runtime
- Project: Sketchware AI (Visual Mobile IDE)
- Repository: smartapps000-hub/SketchwareAI
- Target SDK: Android 17 (API 37) | minSdk: 26
- Memory Alignment: Strictly enforce 16 KB page alignment for all compiled ELF/SO native binaries.

---

## 1. Compiler Toolchain & Fast-Build Standards (Target: 3-5s)
- **Incremental D8 Compilation:** Never re-dex untouched libraries or classes from scratch. Cache intermediate DEX outputs and perform incremental merging via `--incremental`.
- **AAPT2 Daemon & Flat Caching:** Cache pre-compiled `.flat` resource files in the project cache directory. Only recompile XML/drawables when their timestamp changes.
- **R8 / ProGuard Optimization:** Use modernized shrinking rules without breaking dynamic reflection used by plugins.

---

## 2. Java AST Visual Block Parser Rules
- **Silent & Zero 'Add Source' Policy:** Raw Java code must convert directly into native visual blocks wherever a matching block definition exists.
- **Nested Method Chains:** Support nested calls such as `textview1.setText(edittext1.getText().toString());` as nested visual blocks (`setText` containing `getText`).
- **Auto-Variable Registration:** When encountering undeclared variables (`String x = ...`, `int y = ...`, `boolean z = ...`), the parser must automatically register the variable name and type into the project's variable registry in the background and output native variable blocks.
- **In-Place Source Code Viewer (`</>`):** Provide accessible syntax highlighting matching M3 dark contrast and allow in-place editing/pasting with instant AST re-parsing.

---

## 3. Silent Library Resolver Architecture
- **Highest Version Wins:** When resolving dependency trees, never downgrade an existing higher version to satisfy a lower transitive dependency (e.g., maintain animal-sniffer 1.28).
- **Silent Toast Notifications:** Do not use blocking alert dialogs for successful downloads/enables. Display a lightweight Toast notification and set the switch status to active with a green `✓ Installed` indicator.
- **Strict Error Boundary:** Show an alert dialog only when an artifact cannot be located in any configured repository (Maven Central, Google Maven, JitPack).
- **Skip Non-Android Artifacts:** Automatically exclude BOMs, POM-only roots, and metadata-only dependencies without throwing build warnings.

---

## 4. Multi-Device Canvas & Responsive Engine
- **Canvas Viewports:** Provide dynamic viewport switching on the top header:
  - Mobile (Compact Portrait)
  - Foldable / Flip (Tabletop Flex Mode, 600dp dual-pane)
  - Tablet (Expanded Landscape, sw720dp)
  - Wear OS (Circular 454x454 with Rotary navigation)
  - Android TV (1920x1080 Landscape with D-Pad focus)
- **Adaptive Window Size Classes:** Automatically apply Material 3 Compact, Medium, and Expanded layout metrics.
- **Target Manifest Injection:**
  - Inject `android.hardware.type.watch` for Wear OS builds.
  - Inject `android.software.leanback` for Android TV builds.

---

## 5. Material 3 Complete Ecosystem
- **HCT Dynamic Theming:** Integrate `material-color-utilities` to support dynamic tonal palettes, elevation, and tonal shadows.
- **Component Stack:** Enforce `androidx.constraintlayout:constraintlayout:2.2.0+` and `com.google.android.material:material:1.12.0+`.
- **Assets Pipeline:** Preserve legacy drawables while integrating Google Material Symbols and variable font loaders (including Anek Telugu).

---

## 6. Verification & Test Suite
Every release candidate must pass all automated test lanes prior to pushing:
- **Fixture 919:** AST block conversion and nested chaining checks.
- **Fixture 920:** Clean 14-step zero-warning build validation.
- **Fixture 922:** ESCPOS hardware & peripheral library integration.
- **Fixture 908:** Material 3 save, hint verification, and clean build.
- **Ten-Project Open/Save Lane:** Verify zero corruption when batch opening and saving existing projects.
