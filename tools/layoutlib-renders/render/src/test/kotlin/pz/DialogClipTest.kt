package pz

import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * v1.8.6: the Build Settings sheet (project_config_layout) when its options are taller than the space the dialog gets:
 * the options scroll inside the ScrollView; with the root's clipChildren=false the ScrollView's content is drawn
 * outside its bounds, under the Cancel / Save row (owner's screenshot). Font scale 1.3 like the owner's phone.
 */
private fun renderDialog(paparazzi: Paparazzi, variant: String) {
    val context = paparazzi.context
    val layout = if (variant == "before") pz.render.R.layout.dialog_cfg_before else pz.render.R.layout.dialog_cfg_after
    val root = LayoutInflater.from(context).inflate(layout, null)
    val content = root.findViewById<LinearLayout>(pz.render.R.id.cfg_content)
    fun text(t: String, size: Float) = TextView(context).apply { text = t; textSize = size; setTextColor(0xFFF5F5F7.toInt()); setPadding(0, 12, 0, 12) }
    content.addView(text("Custom android.jar", 18f))
    content.addView(text("Classpath (separated by :)", 18f))
    content.addView(text("Dexer   ( ) D8   ( ) Dx (legacy)", 18f))
    content.addView(text("D8 is the default. Dx is kept for old projects only: it cannot read Java 8+ code.", 15f))
    content.addView(text("Java version   1.7  1.8  1.9  10  11", 18f))
    content.addView(text("Language level for the ECJ compiler. Default: 11. If the phone cannot use 1.9, 10 or 11, the build compiles as 1.8 and the log says so.", 15f))
    for (label in arrayOf("Hide warnings in error log", "Don't include http-legacy-28.dex",
            "Enable debug logcat logs viewable in Logcat Reader. Not enabled in exported AABs/APKs.")) {
        content.addView(CheckBox(context).apply { text = label; textSize = 18f; setTextColor(0xFFF5F5F7.toInt()); isChecked = true })
    }
    // the height the dialog window leaves for the sheet
    root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY))
    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    val bmp = android.graphics.Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
    root.draw(android.graphics.Canvas(bmp))
    java.io.File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/dialog_$variant.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
}

class DialogClipTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f))

    @Test fun before() = renderDialog(paparazzi, "before")
    @Test fun after() = renderDialog(paparazzi, "after")
}
