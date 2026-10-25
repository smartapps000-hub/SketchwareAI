package pz

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import pz.render.FixedLinear

/** v1.9.0: the owner's crash (card column with FrameLayout params inside a LinearLayout-drawn card) and the fix. */
class CardParamsTest {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun build(card: LinearLayout): View {
        val ctx = p.context
        val scroll = ScrollView(ctx)
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(column, FrameLayout.LayoutParams(-1, -2))
        column.addView(card, LinearLayout.LayoutParams(-1, -2))
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(TextView(ctx).apply { text = "Card title" })
        card.addView(inner)
        // ViewPane.a(View, ViewBean) with parentType 36: FrameLayout params set on the child after it was added
        inner.layoutParams = FrameLayout.LayoutParams(-1, -2).apply { leftMargin = 24; gravity = Gravity.END }
        return scroll
    }

    @Test fun crashAndFix() {
        val out = StringBuilder()
        val spec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val h = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        if (System.getProperty("withPlain") != null) try {
            build(LinearLayout(p.context)).measure(spec, h)
            out.append("plain LinearLayout: measured (no crash)\n")
        } catch (e: Throwable) {
            out.append("plain LinearLayout: ").append(e.javaClass.simpleName).append(" at ")
                .append(e.stackTrace.firstOrNull()).append('\n')
        }
        val fixed = FixedLinear(p.context)
        val root = build(fixed)
        root.measure(spec, h)
        val lp = fixed.getChildAt(0).layoutParams as LinearLayout.LayoutParams
        out.append("FixedLinear: measured ").append(root.measuredWidth).append('x').append(root.measuredHeight)
            .append("; child params ").append(lp.javaClass.name).append(" leftMargin=").append(lp.leftMargin)
            .append(" gravity=").append(lp.gravity == Gravity.END).append('\n')
        java.io.File("/home/claude/pz/out/card_params.txt").writeText(out.toString())
    }
}
