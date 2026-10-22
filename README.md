# SketchwareAI
Open-source on-device Android IDE with AAPT2, D8/R8 compilation, Bundletool AAB, and Material 3 support. 
# SketchwareAI (Modern Android On-Device Engine)

An advanced, open-source mobile-native Android IDE engineered to compile and package modern Android applications natively on ARM64 mobile runtimes without desktop dependencies.

### Key Architecture & Features:
- **Modern Build Toolchain:** Built with AOSP AAPT2 (16 KB page-aligned), Google Bundletool 1.18.3, and D8/R8 byte-code compiler with core library desugaring (`desugar_jdk_libs_nio 2.1.5`).
- **Target Compatibility:** Full backward and forward compatibility spanning Android 7.0 (API 24) through modern Android 17 (API 34+ compile targets).
- **Material 3 Dynamic System:** Automatic runtime view inflation for MDC widgets (`MaterialButton`, `TextInputLayout`, `MaterialSwitch`) and DayNight color adaptations.
- **Complex Script Typography:** Native HarfBuzz text shaping integration resolving complex glyphs, ligatures, and conjuncts for Telugu typography.
- **Play Store Ready:** Native on-device Android App Bundle (AAB) export with v1 SHA-256 signing and 16 KB ELF constraints.

### License
Licensed under the GNU General Public License v3.0.

