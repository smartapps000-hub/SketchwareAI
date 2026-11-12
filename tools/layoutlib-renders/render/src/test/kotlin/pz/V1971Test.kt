package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import pz.render.R
import org.junit.Rule
import org.junit.Test

/**
 * v1.9.7.1 contrast pass: the call-to-action with its white label on the original orange gradient (before) and on
 * the deepened one (after), normal and pressed (the plain-shape versions of the drawables). Writes out/v1971_cta.png.
 */
class V1971Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()

    private fun original(): android.graphics.drawable.Drawable {
        // ob_safe_cta as it was up to v1.9.7: #E8501C to #F97316, angle 315
        val g = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#E8501C"), Color.parseColor("#F97316")))
        g.cornerRadius = dp(16).toFloat()
        g.setStroke(dp(1), Color.parseColor("#80FFFFFF"))
        return g
    }

    private fun button(text: String, bg: android.graphics.drawable.Drawable, icon: Boolean): TextView {
        val c = paparazzi.context
        val b = TextView(c)
        b.text = text
        b.setTextColor(Color.WHITE)
        b.textSize = 16f
        b.typeface = c.resources.getFont(R.font.manrope_extrabold)
        b.gravity = Gravity.CENTER
        b.background = bg
        if (icon) {
            b.setCompoundDrawablesWithIntrinsicBounds(c.getDrawable(R.drawable.ob_ic_play_small), null, null, null)
            b.compoundDrawablePadding = dp(6)
        }
        b.setPadding(dp(22), 0, dp(22), 0)
        return b
    }

    private fun caption(text: String): TextView {
        val t = TextView(paparazzi.context)
        t.text = text
        t.setTextColor(Color.parseColor("#A1A1AA"))
        t.textSize = 13f
        t.setPadding(0, dp(14), 0, dp(6))
        return t
    }

    @Test fun cta() {
        val c = paparazzi.context
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Color.parseColor("#0B0B0D"))
        col.setPadding(dp(20), dp(12), dp(20), dp(20))
        fun row(label: String, bg: () -> android.graphics.drawable.Drawable) {
            col.addView(caption(label))
            val r = LinearLayout(c)
            r.orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(0, dp(48), 1f); lp.rightMargin = dp(10)
            r.addView(button("Create project", bg(), false), lp)
            r.addView(button("Run", bg(), true), LinearLayout.LayoutParams(dp(110), dp(48)))
            col.addView(r)
        }
        row("Before: white on #E8501C to #F97316 (3.75:1 to 2.80:1)") { original() }
        row("After: white on #D34115 to #C64F14 (4.6:1)") { c.getDrawable(R.drawable.ob_safe_cta)!! }
        row("After, pressed: #C83D13 to #BC4A12 (5.1:1)") { c.getDrawable(R.drawable.ob_safe_cta_pressed)!! }
        val w = dp(400)
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        col.layout(0, 0, w, col.measuredHeight)
        val bmp = Bitmap.createBitmap(w, col.measuredHeight, Bitmap.Config.ARGB_8888)
        col.draw(Canvas(bmp))
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/v1971_cta.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The selected segment pill (property panel): white on ob_tile_orange (before) and on ob_seg_pill_on (after). */
    @Test fun pills() {
        val c = paparazzi.context
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Color.parseColor("#0B0B0D"))
        col.setPadding(dp(20), dp(12), dp(20), dp(20))
        fun pill(text: String, selected: Boolean, on: () -> android.graphics.drawable.Drawable): TextView {
            val t = TextView(c)
            t.text = text
            t.textSize = 12f
            t.typeface = c.resources.getFont(R.font.manrope_bold)
            t.gravity = Gravity.CENTER
            t.setTextColor(if (selected) Color.WHITE else Color.parseColor("#D4D4D8"))
            t.background = if (selected) on() else c.getDrawable(R.drawable.ob_pill)!!
            t.setPadding(dp(14), 0, dp(14), 0)
            t.minWidth = dp(64)
            return t
        }
        fun row(label: String, on: () -> android.graphics.drawable.Drawable) {
            col.addView(caption(label))
            val r = LinearLayout(c)
            r.orientation = LinearLayout.HORIZONTAL
            for ((i, name) in listOf("Basic", "Recent", "Event").withIndex()) {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)); lp.rightMargin = dp(8)
                r.addView(pill(name, i == 0, on), lp)
            }
            col.addView(r)
        }
        row("Before: white on ob_tile_orange #F4511E (3.5:1 down to 2.9:1)") { c.getDrawable(R.drawable.ob_tile_orange)!! }
        row("After: white on ob_seg_pill_on #D34115 to #C64F14 (4.6:1)") { c.getDrawable(R.drawable.ob_seg_pill_on)!! }
        val w = dp(360)
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        col.layout(0, 0, w, col.measuredHeight)
        val bmp = Bitmap.createBitmap(w, col.measuredHeight, Bitmap.Config.ARGB_8888)
        col.draw(Canvas(bmp))
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/v1971_pills.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
