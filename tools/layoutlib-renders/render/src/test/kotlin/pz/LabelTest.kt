package pz

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.appcompat.app.AppCompatViewInflater
import org.junit.Rule
import org.junit.Test

/** v1.8.6: the outlined field's floating label of a wrap_content 12sp field (owner's project #659), font scale 1.0 and 1.3. */
private fun renderLabel(paparazzi: Paparazzi, name: String, weightAdjustment: Int = 0) {
    var context: Context = paparazzi.context
    if (weightAdjustment != 0) {
        // the phone's "Bold text" setting (Android 12+): Configuration.fontWeightAdjustment
        val config = android.content.res.Configuration(context.resources.configuration)
        config.fontWeightAdjustment = weightAdjustment
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
    val impl = AppCompatViewInflater()
    val inflater = LayoutInflater.from(context).cloneInContext(context)
    inflater.factory2 = object : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, n: String, c: Context, a: AttributeSet): View? =
            impl.createView(parent, n, c, a, false, false, true, false)
        override fun onCreateView(n: String, c: Context, a: AttributeSet): View? = onCreateView(null, n, c, a)
    }
    val root = inflater.inflate(pz.render.R.layout.screen_label, null)
    root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST))
    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    val bmp = android.graphics.Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
    root.draw(android.graphics.Canvas(bmp))
    java.io.File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/label_$name.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val f = root.findViewById<View>(pz.render.R.id.edittext1)
    val sb = StringBuilder("edittext1 width=${f.width} box=${(f.parent.parent as View).width}\n")
    for (id in intArrayOf(pz.render.R.id.edittext1, pz.render.R.id.edittext2)) {
        val box = root.findViewById<View>(id).parent.parent as com.google.android.material.textfield.TextInputLayout
        val helperField = com.google.android.material.textfield.TextInputLayout::class.java.getDeclaredField("collapsingTextHelper")
        helperField.isAccessible = true
        val helper = helperField.get(box)
        fun field(n: String): Any? { val fl = helper.javaClass.getDeclaredField(n); fl.isAccessible = true; return fl.get(helper) }
        val bounds = field("collapsedBounds") as android.graphics.Rect
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.textSize = field("collapsedTextSize") as Float
        paint.typeface = field("collapsedTypeface") as android.graphics.Typeface?
        paint.letterSpacing = field("collapsedLetterSpacing") as Float
        val hint = box.hint.toString()
        val edit = root.findViewById<android.widget.TextView>(id)
        sb.append("id=${root.resources.getResourceEntryName(id)} label width=${paint.measureText(hint)} collapsedBounds=${bounds.width()} size=${paint.textSize} ls=${paint.letterSpacing} tf=${paint.typeface?.weight}")
        sb.append(" | field textSize=${edit.textSize} ls=${edit.letterSpacing} tf=${edit.typeface?.weight} padL=${edit.compoundPaddingLeft} padR=${edit.compoundPaddingRight} fieldMeasure=${edit.paint.measureText(hint)}\n")
    }
    java.io.File("/home/claude/pz/out/label_$name.txt").writeText(sb.toString())
}

class LabelScale10Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "AppTheme85")
    @Test fun label() = renderLabel(paparazzi, "scale10")
}

class LabelScale13Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f), theme = "AppTheme85")
    @Test fun label() = renderLabel(paparazzi, "scale13")
}

class LabelBoldTextTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f), theme = "AppTheme85")
    @Test fun label() = renderLabel(paparazzi, "scale13_bold", 300)
}
