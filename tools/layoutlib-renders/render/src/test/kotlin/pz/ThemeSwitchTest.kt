package pz

import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Widgets placed from the palette in a Material 3 project, inflated under each AppTheme the generator can write:
 * Material 3 (material3_theme on), MaterialComponents (off, bridgeless) and MaterialComponents Bridge (off).
 * palette_m3_c = the inject of the first v1.8.4 build (explicit Widget.Material3 styles), palette_m3_d = after the fix
 * (theme styles). One Paparazzi session per theme, so no other theme's attributes are present.
 */
private fun tryInflate(paparazzi: Paparazzi, theme: String) {
    val report = StringBuilder()
    for ((ln, lid) in listOf("palette_m3_c" to pz.render.R.layout.palette_m3_c, "palette_m3_d" to pz.render.R.layout.palette_m3_d)) {
        try {
            val v = paparazzi.inflate<View>(lid)
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val bmp = android.graphics.Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            v.draw(android.graphics.Canvas(bmp))
            File("/home/claude/pz/out").mkdirs()
            java.io.FileOutputStream("/home/claude/pz/out/switch_${ln}_$theme.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val g = v as android.view.ViewGroup
            val btn = g.getChildAt(1) as com.google.android.material.button.MaterialButton
            val til = g.getChildAt(2) as com.google.android.material.textfield.TextInputLayout
            val corner = btn.shapeAppearanceModel.topLeftCornerSize.getCornerSize(android.graphics.RectF(0f, 0f, btn.measuredWidth.toFloat(), btn.measuredHeight.toFloat()))
            report.append("$ln $theme: inflated; TextInputLayout boxBackgroundMode=${til.boxBackgroundMode} (2=outline); MaterialButton corner=${corner}px of height ${btn.measuredHeight}px\n")
        } catch (t: Throwable) {
            var c: Throwable? = t; var msg = ""
            while (c != null) { msg += " | " + c.javaClass.simpleName + ": " + (c.message ?: "").replace("\n", " ").take(200); c = c.cause }
            report.append("$ln $theme: FAILED$msg\n")
        }
    }
    File("/home/claude/pz/out/theme_switch_$theme.txt").writeText(report.toString())
}

class SwitchM3Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "AppTheme")
    @Test fun palette() = tryInflate(paparazzi, "AppTheme")
}

class SwitchMdcTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "AppThemeMdc")
    @Test fun palette() = tryInflate(paparazzi, "AppThemeMdc")
}

class SwitchBridgeTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "AppThemeOld")
    @Test fun palette() = tryInflate(paparazzi, "AppThemeOld")
}
