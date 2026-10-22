package pz

import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.io.File

private fun save(view: View, name: String) {
    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST))
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    val bmp = android.graphics.Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.WHITE)
    view.draw(android.graphics.Canvas(bmp))
    File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
}

/** The AppTheme that v1.8.4 generates for new projects (Material 3), inflated by layoutlib with Material 1.13.0. */
class M3ThemeTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "AppTheme")

    @Test fun generatedScreen() {
        val v = paparazzi.inflate<View>(pz.render.R.layout.screen_m3)
        save(v, "theme_m3")
        paparazzi.snapshot(v)
    }
}

/** The MaterialComponents (Bridge) AppTheme of projects made before v1.8.4, same colours, same widgets. */
class OldThemeTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "AppThemeOld")

    @Test fun generatedScreen() {
        val v = paparazzi.inflate<View>(pz.render.R.layout.screen_old)
        save(v, "theme_old")
        paparazzi.snapshot(v)
    }
}
