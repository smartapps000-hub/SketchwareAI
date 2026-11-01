package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.sketchware.ai.ui.M3CodeScheme
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import org.junit.Rule
import org.junit.Test
import pz.render.R

/**
 * v1.9.0: the logic editor's source sheet ("</>") as v1.8.13 showed it (the TextMate Dracula theme with sora's
 * JavaLanguage: its tokens keep EditorColorScheme's light-theme defaults) and with M3CodeScheme; the Target device row
 * of Project Configuration as ProjectSettingsDialog.addTargetDevice builds it (Wear OS chosen).
 */
class V190Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.NoActionBar.Bridge")

    private val code = """
        String userName = edittext1.getText().toString();
        if (userName.isEmpty()) {
            SketchwareUtil.showMessage(getApplicationContext(), "Enter a name");
        } else {
            textview1.setText("Hello " + userName);
        }
        // counted on every click
        clicks++;
        @SuppressWarnings("unused") double total = 2.5d;
        boolean shown = true;
    """.trimIndent()

    private fun save(root: View, width: Int, height: Int, name: String) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                if (height > 0) View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, width, root.measuredHeight)
        root.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(width, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sheet(scheme: EditorColorScheme, name: String) {
        val editor = CodeEditor(paparazzi.context)
        editor.colorScheme = scheme
        editor.setEditorLanguage(JavaLanguage())
        editor.setText(code)
        editor.setTextSize(12f)
        editor.typefaceText = Typeface.MONOSPACE
        editor.isWordwrap = false
        editor.isEditable = false
        val box = FrameLayout(paparazzi.context)
        box.setBackgroundColor(0xFF1C1B1F.toInt())
        box.setPadding(24, 24, 24, 24)
        box.addView(editor, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val width = paparazzi.context.resources.displayMetrics.widthPixels
        // JavaLanguage colours tokens on a background thread whose result layoutlib never delivers: the same analyzer
        // (JavaIncrementalAnalyzeManager) is run line by line here and its spans are set on the editor
        val manager = io.github.rosemoe.sora.langs.java.JavaIncrementalAnalyzeManager()
        val identifiers = io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete.SyncIdentifiers()
        val field = io.github.rosemoe.sora.langs.java.JavaIncrementalAnalyzeManager::class.java.getDeclaredField("identifiers")
        field.isAccessible = true
        field.set(manager, identifiers)
        val builder = io.github.rosemoe.sora.lang.styling.MappedSpans.Builder()
        var state: io.github.rosemoe.sora.langs.java.State = manager.initialState
        val lines = code.split("\n")
        for ((i, line) in lines.withIndex()) {
            val result = manager.tokenizeLine(line, state, i)
            for (span in manager.generateSpansForLine(result)) builder.add(i, span)
            state = result.state
        }
        builder.determine(lines.size - 1)
        editor.setStyles(io.github.rosemoe.sora.lang.styling.Styles(builder.build()))
        save(box, width, 760, name)
    }

    @Test fun sourceSheetBefore() {
        val dracula = TextMateColorScheme(IThemeSource.fromInputStream(
                paparazzi.context.assets.open("textmate/themes/dracula.json"), "dracula.json", null))
        sheet(dracula, "sheet190_before")
    }

    @Test fun sourceSheetAfter() {
        sheet(M3CodeScheme(), "sheet190_after")
    }

    @Test fun targetDeviceRow() {
        val context = paparazzi.context
        val density = context.resources.displayMetrics.density
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.background = context.getDrawable(R.drawable.ob_window_bg)
        column.setPadding((20 * density).toInt(), 30, (20 * density).toInt(), 40)
        val caption = TextView(context)
        caption.text = "Target device"
        caption.textSize = 12f
        caption.setTextColor(context.getColor(R.color.ob_text_2))
        column.addView(caption)
        val effect = TextView(context)
        effect.textSize = 12f
        effect.setTextColor(context.getColor(R.color.ob_text_3))
        val labels = listOf(listOf("Phone", "Foldable", "Tablet"), listOf("Wear OS", "Android TV"))
        for (row in labels) {
            val line = LinearLayout(context)
            for (label in row) {
                val pill = TextView(context)
                pill.text = label
                pill.textSize = 13f
                pill.maxLines = 1
                pill.gravity = Gravity.CENTER
                pill.minHeight = (40 * density).toInt()
                pill.setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                val chosen = label == "Wear OS"
                pill.setBackgroundResource(if (chosen) R.drawable.ob_amber_pill else R.drawable.ob_pill16_state)
                pill.setTextColor(context.getColor(if (chosen) R.color.ob_accent else R.color.ob_text))
                line.addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (6 * density).toInt() })
            }
            column.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * density).toInt() })
        }
        effect.text = "Manifest: android.hardware.type.watch + standalone Wear app"
        column.addView(effect, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * density).toInt() })
        // the glass dialog's content width on a 393dp phone (about 330dp)
        save(column, (330 * density).toInt(), 0, "cfg190_target_device")
    }
}

/**
 * v1.9.0: the Export screen's "Export to Android Studio" card (a glass card, #1E1E22, light text) with the notice
 * ExportProjectActivity.addExportStackNotice adds under its title, built the same way (pill + subtitle, button below).
 */
class V190ExportNoticeTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.NoActionBar.Bridge")

    @Test fun exportNotice() {
        val context = paparazzi.context
        val d = context.resources.displayMetrics.density
        val card = android.widget.RelativeLayout(context)
        card.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        card.background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF1E1E22.toInt()); cornerRadius = 12 * d }
        val icon = android.view.View(context).apply { id = View.generateViewId(); setBackgroundColor(0xFF3DDC84.toInt()) }
        card.addView(icon, android.widget.RelativeLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt()))
        val title = TextView(context).apply {
            id = View.generateViewId(); text = "Export to Android Studio"; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(R.color.ob_text))
        }
        card.addView(title, android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            addRule(android.widget.RelativeLayout.RIGHT_OF, icon.id); leftMargin = (8 * d).toInt() })
        val notice = LinearLayout(context).apply { id = View.generateViewId(); orientation = LinearLayout.VERTICAL }
        val badge = TextView(context).apply {
            text = "Future-Proof Modern Pipeline"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(context.getColor(R.color.ob_accent)); setBackgroundResource(R.drawable.ob_amber_pill)
            setPadding((10 * d).toInt(), (3 * d).toInt(), (10 * d).toInt(), (3 * d).toInt())
        }
        notice.addView(badge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val subtitle = TextView(context).apply {
            text = "Gradle 9.8.2 \u00b7 AGP 9.5.0 \u00b7 Kotlin 2.4.21 \u00b7 compileSdk\u00a037\nRequires Android Studio Rabbit 2 (2026.2.2)+ & JDK\u00a017/21"
            textSize = 12f; setTextColor(context.getColor(R.color.ob_text_2))
        }
        notice.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * d).toInt() })
        card.addView(notice, android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            addRule(android.widget.RelativeLayout.BELOW, title.id); addRule(android.widget.RelativeLayout.ALIGN_LEFT, title.id); topMargin = (8 * d).toInt() })
        val button = TextView(context).apply {
            text = "EXPORT"; textSize = 14f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.ob_cta); setPadding((24 * d).toInt(), (10 * d).toInt(), (24 * d).toInt(), (10 * d).toInt())
        }
        card.addView(button, android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            addRule(android.widget.RelativeLayout.BELOW, notice.id); addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL)
            topMargin = (12 * d).toInt(); bottomMargin = (16 * d).toInt() })
        val screen = FrameLayout(context).apply { setBackgroundColor(0xFF0B0B0D.toInt()); setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt()) }
        screen.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val width = context.resources.displayMetrics.widthPixels
        screen.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        screen.layout(0, 0, width, screen.measuredHeight)
        val bmp = Bitmap.createBitmap(width, screen.measuredHeight, Bitmap.Config.ARGB_8888)
        screen.draw(Canvas(bmp))
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/export190_notice.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** v1.9.0: Material Symbols drawn by the IDE's SymbolIcons.render (android.graphics.Path), as the icon screen shows them. */
class V190SymbolsTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.Light.NoActionBar.Bridge")

    @Test fun symbols() {
        val icons = com.sketchware.ai.ui.SymbolIcons.read(paparazzi.context.assets.open(com.sketchware.ai.ui.SymbolIcons.ASSET))
        val names = listOf("home", "search", "settings", "favorite", "delete", "add_circle", "arrow_back", "notifications", "account_circle", "shopping_cart")
        val cell = 104; val size = 96
        val colors = listOf(0xFF000000.toInt(), 0xFF757575.toInt(), 0xFFFFFFFF.toInt())
        val bmp = Bitmap.createBitmap(cell * names.size, cell * 9, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val dark = android.graphics.Paint().apply { color = 0xFFBDBDBD.toInt() }
        var row = 0
        for (style in com.sketchware.ai.ui.SymbolIcons.STYLES) {
            for (color in colors) {
                if (color == 0xFFFFFFFF.toInt()) canvas.drawRect(0f, (row * cell).toFloat(), (cell * names.size).toFloat(), ((row + 1) * cell).toFloat(), dark)
                for ((c, name) in names.withIndex()) {
                    val icon = icons[style]!!.first { it.name == name }
                    val drawn = com.sketchware.ai.ui.SymbolIcons.render(icon.path, size, color)
                    canvas.drawBitmap(drawn, (c * cell + 4).toFloat(), (row * cell + 4).toFloat(), null)
                }
                row++
            }
        }
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/symbols190.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
