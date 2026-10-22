package pz

import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import pz.render.SketchFonts
import java.io.File

/**
 * Telugu text with custom fonts on the Android text stack of layoutlib (Minikin + HarfBuzz + FreeType):
 * the font block as upstream writes it (Typeface.createFromAsset + setTypeface) vs SketchFonts.apply.
 * Measures, per line, the shaped ink against the view's edges and the neighbouring lines.
 */
class TeluguFontsTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3000))

    private val text = "అను ఫాంట్స్ ఈశ్వర్ నీ\nశ్రీ లక్ష్మీ స్త్రీ గ్రీష్మ\nరాష్ట్ర్యై ఖ్యై ఢ్ఢ్రూ ఙ్క్ష్వ"
    private val report = File(System.getProperty("pz.report", "/tmp/telugu-report.txt"))

    private fun cell(font: String, mode: String): TextView {
        val ctx = paparazzi.context
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 22f
        tv.setTextColor(Color.BLACK)
        tv.setBackgroundColor(0xFFFFF3C4.toInt())
        if (mode.contains("nopad")) tv.includeFontPadding = false
        if (mode.startsWith("upstream")) {
            tv.setTypeface(Typeface.createFromAsset(ctx.assets, "fonts/$font"), 0)
        } else {
            SketchFonts.apply(tv, ctx.assets, "fonts/$font", 0)
        }
        return tv
    }

    /** px of ink outside the view (top, bottom) and the worst overlap between neighbouring lines. */
    private fun measure(tv: TextView, width: Int): IntArray {
        tv.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        tv.layout(0, 0, tv.measuredWidth, tv.measuredHeight)
        val layout = tv.layout
        val r = Rect()
        val tops = IntArray(layout.lineCount); val bottoms = IntArray(layout.lineCount)
        for (i in 0 until layout.lineCount) {
            val s = layout.getLineStart(i); val e = layout.getLineEnd(i)
            tv.paint.getTextBounds(text, s, e, r)
            val base = tv.totalPaddingTop + layout.getLineBaseline(i)
            tops[i] = base + r.top; bottoms[i] = base + r.bottom
        }
        val clipTop = maxOf(0, -tops[0])
        val clipBottom = maxOf(0, bottoms[layout.lineCount - 1] - tv.measuredHeight)
        var overlap = 0
        for (i in 0 until layout.lineCount - 1) overlap = maxOf(overlap, bottoms[i] - tops[i + 1])
        return intArrayOf(clipTop, clipBottom, overlap, tv.measuredHeight)
    }

    private fun render(font: String) {
        val ctx = paparazzi.context
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Color.WHITE)
        col.setPadding(24, 24, 24, 24)
        val w = 1080 - 48
        for (mode in listOf("upstream", "upstream-nopad", "SketchFonts", "SketchFonts-nopad")) {
            val tv = cell(font, mode)
            val m = measure(tv, w)
            val line = "%-24s %-18s ink cut at top %3d px, at bottom %3d px, lines overlap %3d px, height %4d px".format(font, mode, m[0], m[1], m[2], m[3])
            report.appendText(line + "\n")
            val label = TextView(ctx)
            label.text = "$font · $mode — cut top ${m[0]}px · cut bottom ${m[1]}px · overlap ${m[2]}px"
            label.textSize = 11f
            label.setTextColor(if (m[0] + m[1] + maxOf(0, m[2]) > 0) 0xFFC62828.toInt() else 0xFF2E7D32.toInt())
            label.setPadding(0, 18, 0, 4)
            col.addView(label)
            col.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        col.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        col.layout(0, 0, col.measuredWidth, col.measuredHeight)
        val bmp = android.graphics.Bitmap.createBitmap(col.measuredWidth, col.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        col.draw(android.graphics.Canvas(bmp))
        File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/telugu_" + font.replace('.', '_') + ".png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        paparazzi.snapshot(col, font.replace('.', '_'))
    }

    @Test fun ramabhadraTtf() = render("ramabhadra.ttf")
    @Test fun ramabhadraTightMetrics() = render("ramabhadra_tight.ttf")
    @Test fun ramabhadraTightMetricsAndBox() = render("ramabhadra_tightbox.ttf")
    @Test fun tenaliOtf() = render("tenali.otf")
    @Test fun notoTeluguOtf() = render("noto_telugu.otf")
    @Test fun mandaliTtf() = render("mandali.ttf")
}
