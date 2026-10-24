package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.sketchware.ai.project.M3Palette
import com.sketchware.ai.project.ThemePresets
import com.sketchware.ai.ui.ThemePreviewView
import org.junit.Rule
import org.junit.Test
import pz.render.R

/**
 * v1.8.7: the New Project screen (the IDE's ob_new_project.xml with its item layouts; the glass drawables as their
 * plain-shape fallbacks) filled the way NewProjectActivity fills it: template cards (ProjectPresets.LIST), engine
 * segments, colour presets (ThemePresets) with their four tones, the five colour swatches, the live preview
 * (the real ThemePreviewView with M3Palette), the Material 3 rows and the SDK fields.
 */
private val TEMPLATES = arrayOf(
    arrayOf("empty", "Standard", "Empty Activity", "Toolbar and an empty screen", R.drawable.np_tpl_empty),
    arrayOf("scaffold", "Material 3", "Material 3 Scaffold", "Toolbar, elevated cards and a floating action button", R.drawable.np_tpl_scaffold),
    arrayOf("bottom_navigation", "Navigation", "Bottom Navigation", "Three-item navigation bar that switches the content", R.drawable.np_tpl_bottom_nav),
    arrayOf("drawer", "Classic", "Navigation Drawer", "Slide-out drawer with a header and menu rows", R.drawable.np_tpl_drawer),
    arrayOf("tabs", "Tabs", "Tabbed ViewPager", "Tabs under the toolbar with three swipeable pages", R.drawable.np_tpl_tabs),
)

private fun fill(root: View, template: String, colors: IntArray, modern: Boolean, material3: Boolean, dark: Boolean, dynamic: Boolean) {
    val context = root.context
    val inflater = LayoutInflater.from(context)
    root.background = context.getDrawable(R.drawable.ob_window_bg)
    root.findViewById<TextView>(R.id.np_subtitle).text = "Project #605"
    root.findViewById<EditText>(R.id.np_package).setText("com.my.newproject5")
    root.findViewById<EditText>(R.id.np_project).setText("NewProject5")
    root.findViewById<EditText>(R.id.np_version_code).setText("1")
    root.findViewById<EditText>(R.id.np_version_name).setText("1.0")

    val templates = root.findViewById<LinearLayout>(R.id.np_templates)
    for (row in TEMPLATES) {
        val card = inflater.inflate(R.layout.ob_np_template, templates, false)
        card.findViewById<ImageView>(R.id.np_tpl_icon).setImageResource(row[4] as Int)
        card.findViewById<TextView>(R.id.np_tpl_badge).text = row[1] as String
        card.findViewById<TextView>(R.id.np_tpl_title).text = row[2] as String
        card.findViewById<TextView>(R.id.np_tpl_desc).text = row[3] as String
        card.isSelected = row[0] == template
        templates.addView(card)
    }

    // NewProjectActivity.refreshEngine on Android 8+: one engine (Dp uses D8 there), the second segment hidden
    val m = root.findViewById<TextView>(R.id.np_engine_modern)
    root.findViewById<TextView>(R.id.np_engine_legacy).visibility = View.GONE
    m.isSelected = true
    m.setTextColor(0xFFFFFFFF.toInt())
    m.text = "On the phone: D8 / R8 8.11"
    root.findViewById<TextView>(R.id.np_engine_caption).text = "Core library desugaring (java.time, streams on old Android versions), Java 7 to 11. Resources: aapt2 2.20 with 16 KB page alignment. A Gradle build doesn't run on the phone; Export to Android Studio writes the project as a Gradle project."

    val density = context.resources.displayMetrics.density
    val palettes = root.findViewById<LinearLayout>(R.id.np_palettes)
    val selected = ThemePresets.indexOf(colors)
    for (i in 0..ThemePresets.PRESETS.size) {
        val custom = i == ThemePresets.PRESETS.size
        val pill = inflater.inflate(R.layout.ob_np_palette, palettes, false)
        pill.findViewById<TextView>(R.id.np_pal_name).text = if (custom) "Custom" else ThemePresets.PRESETS[i].name
        val set = if (custom) colors else ThemePresets.PRESETS[i].colors
        val tones = pill.findViewById<LinearLayout>(R.id.np_pal_tones)
        tones.background = GradientDrawable().apply { cornerRadius = 10 * density; setColor(0xFF000000.toInt()) }
        tones.clipToOutline = true
        val palette = M3Palette.of(set[1], set[0])
        for (tone in intArrayOf(set[1], set[2], set[0], palette.get("sketch_m3_primary_container", false))) {
            tones.addView(View(context).apply { setBackgroundColor(tone or 0xFF000000.toInt()) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        pill.isSelected = if (custom) selected < 0 else i == selected
        palettes.addView(pill)
    }

    val colorViews = root.findViewById<LinearLayout>(R.id.np_colors)
    for (i in ThemePresets.KEYS.indices) {
        val swatch = inflater.inflate(R.layout.myproject_color, colorViews, false)
        swatch.findViewById<TextView>(R.id.name).text = ThemePresets.LABELS[i]
        (swatch.findViewById<View>(R.id.color).parent as View).clipToOutline = true
        swatch.findViewById<View>(R.id.color).setBackgroundColor(colors[i])
        colorViews.addView(swatch, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    root.findViewById<ThemePreviewView>(R.id.np_preview).set(colors, material3, dark, "My Telugu app")
    root.findViewById<TextView>(R.id.np_preview_caption).text = when {
        !material3 -> "MaterialComponents theme: light only, the project colours as they are."
        dynamic -> "On Android 12 and newer the wallpaper's colours replace the toolbar and window colours shown here; colours you set on a widget yourself stay."
        else -> "Toolbar, button and floating button use your colours in light and dark; the window, text and outline colours are made from the primary colour."
    }

    fun row(id: Int, title: String, subtitle: String, checked: Boolean, visible: Boolean) {
        val r = root.findViewById<View>(id)
        r.findViewById<TextView>(R.id.ob_row_title).text = title
        r.findViewById<TextView>(R.id.ob_row_subtitle).text = subtitle
        r.findViewById<CheckBox>(R.id.ob_row_check).isChecked = checked
        r.findViewById<CheckBox>(R.id.ob_row_check).jumpDrawablesToCurrentState()
        r.visibility = if (visible) View.VISIBLE else View.GONE
    }
    row(R.id.np_material3, "Material 3 theme", "Material 3 widgets and colour roles made from the project colours. Off: the MaterialComponents theme", material3, true)
    row(R.id.np_dark, "Dark theme", "The app follows the phone's dark mode with Material 3 dark colours", dark, material3)
    row(R.id.np_dynamic, "Dynamic colour", "Android 12 and newer use the wallpaper's colours instead of the project colours", dynamic, material3)

    root.findViewById<TextView>(R.id.np_min_sdk).text = "21 (Android 5.0 Lollipop)"
    root.findViewById<TextView>(R.id.np_target_sdk).text = "37 (Android 17)"
    root.findViewById<TextView>(R.id.np_java).text = if (modern) "Java 11 (recommended)" else "Java 7"
    root.findViewById<TextView>(R.id.np_java_caption).text =
        "Java 17 isn't available: the on-phone compiler supports up to Java 11. The bundled android.jar is API 34; a newer target SDK changes how Android treats the app, not which APIs you can call."
}

private fun save(root: View, width: Int, height: Int, name: String) {
    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
    root.layout(0, 0, width, height)
    // layoutlib runs no animations: the selectors' fade-in (enterFadeDuration) would stay at alpha 0
    root.jumpDrawablesToCurrentState()
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bmp))
    java.io.File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** The whole screen: header + every card + the bottom bar (the scroll view given the content's height). */
private fun render(paparazzi: Paparazzi, name: String, template: String, colors: IntArray, modern: Boolean,
                   material3: Boolean, dark: Boolean, dynamic: Boolean) {
    val context = paparazzi.context
    val root = LayoutInflater.from(context).inflate(R.layout.ob_new_project, null)
    fill(root, template, colors, modern, material3, dark, dynamic)
    val width = context.resources.displayMetrics.widthPixels
    val screen = context.resources.displayMetrics.heightPixels
    save(root, width, screen, "${name}_screen")
    // full height: measure the scroll content, then give the screen that much room
    val scroll = root.findViewById<ViewGroup>(R.id.np_scroll)
    val content = scroll.getChildAt(0)
    content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    save(root, width, screen - scroll.height + content.measuredHeight, "${name}_full")
}

class NewProjectScreenTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.Light.NoActionBar.Bridge")

    @Test fun defaults() = render(paparazzi, "np_default", "empty", ThemePresets.PRESETS[0].colors, true, true, true, false)
    @Test fun purpleScaffold() = render(paparazzi, "np_purple", "scaffold", ThemePresets.PRESETS[1].colors, true, true, true, true)
    @Test fun legacyNoM3() = render(paparazzi, "np_legacy", "tabs", ThemePresets.PRESETS[4].colors, false, false, false, false)
}
