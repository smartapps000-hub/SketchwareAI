package pz

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.ui.HoneyDropBar
import org.junit.Rule
import org.junit.Test

/**
 * v2.1.1 (owner's 21 Nov correction "transparent glass droplet, splash and full-bar waves, no gold"): the bar at rest on
 * Home, then a tap Home → GitHub at the moments of its four stages (descent with micro droplets, crater with the icon's
 * dip, crown / jet / beads with the icon rising, waves running to both ends), and at rest on GitHub. The bottom 150 dp
 * of the Home screen, each frame cropped; writes out/v211_bar.png (one frame under the other) and the frames 4x
 * enlarged around the impact (out/v211_bar_zoom.png).
 */
class V211BarTest {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    @Test fun bar() {
        val c = paparazzi.context
        V210Shots.sampleData()
        val moments = listOf(Float.NaN, 0.10f, 0.20f, 0.29f, 0.34f, 0.41f, 0.47f, 0.55f, 0.66f, 0.80f, 0.93f, -1f)
        val crops = moments.map { m ->
            val home = V210Shots.home(c)
            val full = V210Shots.shot(c, home, 393, 851) {
                val bar = V210Shots.bar(home)
                when {
                    m.isNaN() -> bar.select(HoneyDropBar.HOME, false)
                    m < 0 -> bar.select(HoneyDropBar.GITHUB, false)
                    else -> bar.frameForTests(HoneyDropBar.HOME, HoneyDropBar.GITHUB, m)
                }
            }
            val top = full.height - V210Shots.dp(c, 150)
            android.graphics.Bitmap.createBitmap(full, 0, top, full.width, full.height - top)
        }
        val w = crops[0].width
        val h = crops.sumOf { it.height + 8 }
        val sheet = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(android.graphics.Color.parseColor("#3A3A44"))
        var y = 0f
        for (b in crops) {
            canvas.drawBitmap(b, 0f, y, null)
            y += b.height + 8
        }
        V210Shots.out.mkdirs()
        java.io.FileOutputStream(java.io.File(V210Shots.out, "v211_bar.png")).use { sheet.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        // zoom: 4x around the GitHub tab, stages 1-4
        val zoomFrames = crops.subList(1, 11)
        val zw = V210Shots.dp(c, 120)
        val cx = (w / 2.0).toInt() - V210Shots.dp(c, 20)
        val zh = V210Shots.dp(c, 110)
        val zoom = android.graphics.Bitmap.createBitmap(zw * 2 * 5, zh * 2 * 2, android.graphics.Bitmap.Config.ARGB_8888)
        val zc = android.graphics.Canvas(zoom)
        val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        zoomFrames.forEachIndexed { i, b ->
            val src = android.graphics.Rect(cx - zw / 2 - V210Shots.dp(c, 40), b.height - zh, cx + zw / 2 - V210Shots.dp(c, 40), b.height)
            val col = i % 5; val row = i / 5
            zc.drawBitmap(b, src, android.graphics.Rect(col * zw * 2, row * zh * 2, col * zw * 2 + zw * 2, row * zh * 2 + zh * 2), p)
        }
        java.io.FileOutputStream(java.io.File(V210Shots.out, "v211_bar_zoom.png")).use { zoom.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
