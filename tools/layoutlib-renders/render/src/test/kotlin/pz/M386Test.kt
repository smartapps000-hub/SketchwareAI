package pz

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import androidx.appcompat.app.AppCompatViewInflater
import org.junit.Rule
import org.junit.Test

/**
 * v1.8.6: fixture 913's generated widgets under its generated AppTheme (Theme.Material3.DayNight, colour roles from the
 * project colour #F4511E by M3Palette), on a light and a night device; checked states in the second frame.
 */
private fun inflate86(paparazzi: Paparazzi): View {
    val context: Context = paparazzi.context
    val impl = AppCompatViewInflater()
    val inflater = LayoutInflater.from(context).cloneInContext(context)
    inflater.factory2 = object : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, n: String, c: Context, a: AttributeSet): View? =
            impl.createView(parent, n, c, a, false, false, true, false)
        override fun onCreateView(n: String, c: Context, a: AttributeSet): View? = onCreateView(null, n, c, a)
    }
    return inflater.inflate(pz.render.R.layout.screen_m386, null)
}

private fun save86(view: View, name: String) {
    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST))
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    val bmp = android.graphics.Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
    view.draw(android.graphics.Canvas(bmp))
    java.io.File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
}

private fun render86(paparazzi: Paparazzi, name: String) {
    save86(inflate86(paparazzi), "m386_${name}_off")
    val on = inflate86(paparazzi)
    for (id in intArrayOf(pz.render.R.id.switch1, pz.render.R.id.checkbox1, pz.render.R.id.radiobutton1)) {
        (on.findViewById<View>(id) as CompoundButton).isChecked = true
    }
    on.jumpDrawablesToCurrentState()
    save86(on, "m386_${name}_on")
}

class M386LightTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "AppTheme86")
    @Test fun generated() = render86(paparazzi, "light")
}

class M386NightTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "AppTheme86")
    @Test fun generated() = render86(paparazzi, "night")
}
