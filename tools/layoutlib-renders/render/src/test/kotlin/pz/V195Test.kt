package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.google.android.material.progressindicator.BaseProgressIndicator
import org.junit.Rule
import org.junit.Test

/**
 * v1.9.5: fixture 931's Material 3 tags (Linear / CircularProgressIndicator, filled and Tonal SketchButton as
 * MaterialButton, plus Outlined / Elevated) under its generated DayNight AppTheme (import_generated.py: AppTheme931,
 * np931_* colours), light and night. Writes out/v195_widgets_<mode>.png and out/v195_widgets.txt (classes inflated).
 */
abstract class V195Base(night: Boolean) {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT),
            theme = "AppTheme931")

    fun render(mode: String) {
        val root = LayoutInflater.from(paparazzi.context).inflate(pz.render.R.layout.v195_widgets, null)
        // layoutlib runs no animations: the indicators draw their first frame
        root.jumpDrawablesToCurrentState()
        val width = 1080
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, width, root.measuredHeight)
        val bmp = Bitmap.createBitmap(width, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/v195_widgets_$mode.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val p1 = root.findViewById<View>(pz.render.R.id.progressbar1)
        val p2 = root.findViewById<View>(pz.render.R.id.progressbar2)
        val b2 = root.findViewById<View>(pz.render.R.id.button2)
        // attached to a window (Material's indicators draw only then); Paparazzi writes it under build/reports/paparazzi
        paparazzi.snapshot(LayoutInflater.from(paparazzi.context).inflate(pz.render.R.layout.v195_widgets, null), "v195_$mode")
        java.io.File("/home/claude/pz/out/v195_widgets.txt").appendText("$mode: ${p1.javaClass.name} progress=" +
                "${(p1 as BaseProgressIndicator<*>).progress} ${p2.javaClass.name} indeterminate=${(p2 as BaseProgressIndicator<*>).isIndeterminate} " +
                "${b2.javaClass.name} height=${p1.measuredHeight}px\n")
    }
}

class V195LightTest : V195Base(false) {
    @Test fun widgets() = render("light")
}

class V195NightTest : V195Base(true) {
    @Test fun widgets() = render("night")
}
