package pz

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Android's own shaping (TextRunShaper -> Minikin -> HarfBuzz) of Telugu with project fonts loaded the way SketchFonts
 * loads them (Typeface.createFromAsset). Writes glyph ids and positions; compared with HarfBuzz (uharfbuzz) outside.
 */
class ShapingTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val strings = listOf("అను ఫాంట్స్", "ఈశ్వర్ నీ", "శ్రీ లక్ష్మీ", "స్త్రీ గ్రీష్మ", "రాష్ట్ర్యై", "ఙ్క్ష్వ", "కృష్ణ", "స్వాతంత్ర్యం")

    @Test fun shape() {
        val out = StringBuilder()
        for (font in listOf("ramabhadra.ttf", "tenali.otf", "noto_telugu.otf", "mandali.ttf")) {
            val paint = Paint()
            paint.typeface = Typeface.createFromAsset(paparazzi.context.assets, "fonts/$font")
            paint.textSize = 1000f
            for (s in strings) {
                val g = TextRunShaper.shapeTextRun(s, 0, s.length, 0, s.length, 0f, 0f, false, paint)
                val ids = (0 until g.glyphCount()).joinToString(",") { "${g.getGlyphId(it)}@${Math.round(g.getGlyphX(it))}:${Math.round(g.getGlyphY(it))}" }
                out.append(font).append('\t').append(s).append('\t').append(ids).append('\n')
            }
        }
        File("/home/claude/pz/out/android-shaping.tsv").writeText(out.toString())
    }
}
