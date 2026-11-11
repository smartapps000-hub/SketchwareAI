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
 * v1.9.7.1 contrast pass: the call-to-action label before (white) and after (ob_on_cta #0B0B0D) on the normal and the
 * pressed orange surface (the plain-shape versions of the drawables). Writes out/v1971_cta.png.
 */
class V1971Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()

    private fun button(text: String, color: Int, bg: Int, icon: Boolean): TextView {
        val c = paparazzi.context
        val b = TextView(c)
        b.text = text
        b.setTextColor(color)
        b.textSize = 16f
        b.typeface = c.resources.getFont(R.font.manrope_extrabold)
        b.gravity = Gravity.CENTER
        b.setBackgroundResource(bg)
        if (icon) {
            val d = c.getDrawable(R.drawable.ob_ic_play_small)!!.mutate()
            d.setTint(color)
            b.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null)
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
        fun row(label: String, color: Int, bg: Int) {
            col.addView(caption(label))
            val r = LinearLayout(c)
            r.orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(0, dp(48), 1f); lp.rightMargin = dp(10)
            r.addView(button("Create project", color, bg, false), lp)
            r.addView(button("Run", color, bg, true), LinearLayout.LayoutParams(dp(110), dp(48)))
            col.addView(r)
        }
        row("Before: white label (2.8:1 to 3.75:1)", Color.WHITE, R.drawable.ob_safe_cta)
        row("After: #0B0B0D label (5.2:1 to 7.0:1)", Color.parseColor("#0B0B0D"), R.drawable.ob_safe_cta)
        row("After, pressed (4.8:1 to 5.9:1)", Color.parseColor("#0B0B0D"), R.drawable.ob_safe_cta_pressed)
        val w = dp(400)
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        col.layout(0, 0, w, col.measuredHeight)
        val bmp = Bitmap.createBitmap(w, col.measuredHeight, Bitmap.Config.ARGB_8888)
        col.draw(Canvas(bmp))
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/v1971_cta.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
