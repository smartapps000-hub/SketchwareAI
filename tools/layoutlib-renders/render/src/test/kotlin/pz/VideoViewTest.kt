package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * v1.8.7: why the owner's app was black under the MaterialButton. Android's own VideoView.onMeasure with no video
 * loaded: an empty VideoView with wrap_content takes every pixel its parent offers (View.getDefaultSize(0, AT_MOST)).
 * On a phone it is a SurfaceView, black until a video plays; the WebView and Button after it get no height. layoutlib
 * doesn't draw a SurfaceView, so the render marks the VideoView's bounds in red and writes the measured positions.
 */
class VideoViewTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.Light.NoActionBar")

    @Test fun emptyVideoViewFillsTheRest() {
        val root = LayoutInflater.from(paparazzi.context).inflate(pz.render.R.layout.screen_video, null)
        val width = 1080
        val height = 2340
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        val video = root.findViewById<View>(pz.render.R.id.videoview1)
        val web = root.findViewById<View>(pz.render.R.id.webview1)
        val button = root.findViewById<View>(pz.render.R.id.button1)
        fun box(v: View): IntArray {
            var x = 0; var y = 0; var c: View? = v
            while (c != null && c !== root) { x += c.left; y += c.top; c = c.parent as? View }
            return intArrayOf(x, y, v.width, v.height)
        }
        val report = "screen ${width}x$height\nvideoview1 x,y,w,h = ${box(video).joinToString()}\nwebview1 x,y,w,h = ${box(web).joinToString()}\nbutton1 x,y,w,h = ${box(button).joinToString()}\n"
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFEF7FF.toInt())
        root.draw(canvas)
        val b = box(video)
        val fill = Paint().apply { color = 0xFF000000.toInt() }
        canvas.drawRect(b[0].toFloat(), b[1].toFloat(), (b[0] + b[2]).toFloat(), (b[1] + b[3]).toFloat(), fill)
        val stroke = Paint().apply { color = 0xFFFF1744.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f }
        canvas.drawRect(b[0] + 4f, b[1] + 4f, b[0] + b[2] - 4f, b[1] + b[3] - 4f, stroke)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 44f }
        canvas.drawText("VideoView (no video): ${b[2]} x ${b[3]} px", b[0] + 40f, b[1] + 100f, text)
        canvas.drawText("WebView height ${web.height} px, Button height ${button.height} px", b[0] + 40f, b[1] + 170f, text)
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/videoview_fill.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        java.io.File("/home/claude/pz/out/videoview_fill.txt").writeText(report)
    }
}
