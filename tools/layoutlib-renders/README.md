# layoutlib renders (v1.8.4, v1.8.5)

A standalone Gradle module (AGP 8.7, Paparazzi 1.3.5, Gradle 8.11, compileSdk 34, Material 1.13.0, AppCompat 1.7.1) that
renders with **layoutlib**: Android 14's own view, resource and text code (Minikin + HarfBuzz + FreeType) on the JVM.
It is not a device and not ART. Used for:

- `TeluguFontsTest`: 3 lines of Telugu with six fonts (TTF and OTF/CFF, normal and tight metrics), the upstream font block
  (`setTypeface(Typeface.createFromAsset(...))`) against `SketchFonts.apply`, includeFontPadding on/off. Measures, per line,
  the shaped ink against the view's edges and the next line; writes `out/telugu_*.png` and `/tmp/telugu-report.txt`.
- `ShapingTest`: Android's shaping (`TextRunShaper`) of Telugu strings with fonts loaded by `Typeface.createFromAsset`;
  `out/android-shaping.tsv` is compared glyph by glyph with HarfBuzz (`compare_shaping.py`, needs `pip install uharfbuzz`).
- `M3ThemeTest` / `OldThemeTest`: generated widgets under the v1.8.4 Material 3 AppTheme and the MaterialComponents (Bridge)
  AppTheme of older projects (`res/values/generated_styles.xml` is fixture 908's generated styles.xml plus the old theme).
- `SwitchM3Test` / `SwitchMdcTest` / `SwitchBridgeTest` (ThemeSwitchTest.kt): the palette's MaterialButton + TextInputLayout of a
  Material 3 project (`palette_m3_c` = explicit Widget.Material3 styles of the first v1.8.4 build, `palette_m3_d` = theme
  styles) inflated in one session per AppTheme; writes `out/theme_switch_<theme>.txt` and `out/switch_*.png`. A failed
  inflation shows as a MockView plus an InflateException in the test report (Paparazzi then fails the run; read the report).

- `M385LightTest` / `M385NightTest` (M385Test.kt, v1.8.5): fixture 908's generated Material 3 widgets (`screen_m385`: the
  tags the generator writes, SketchButton, outlined SketchTextField with and without text, MaterialCheckBox, MaterialSwitch,
  MaterialRadioButton) under its generated AppTheme (`m385_styles.xml`: Theme.Material3.Light, AppCompatViewInflater),
  inflated through AppCompatViewInflater as AppCompatActivity does, on a light and a night device setting, unchecked and
  checked (checked before the first layout, drawables jumped to their end state: layoutlib runs no animations). Also a
  rounded GradientDrawable set from code on a SketchButton and on a plain MaterialButton. Writes `out/m385_*.png` and
  `out/m385_<light|night>.txt` (the inflated classes).

Setup: `echo sdk.dir=/opt/android-sdk > local.properties`; fonts: `python3 ../art-harness/make-test-fonts.py
render/src/main/assets/fonts`; helpers: the generated `SketchFonts.java` (FontCode.helperClass("pz.render")), `SketchTextField.java` and
`SketchButton.java` (M3Widgets.textFieldClass / buttonClass("pz.render")) into `render/src/main/java/pz/render/`. Run: `./gradlew :render:recordPaparazziDebug` (JDK 17). Output in `out/` (paths in the
tests are absolute to /home/claude/pz; change them when the module lives elsewhere).
