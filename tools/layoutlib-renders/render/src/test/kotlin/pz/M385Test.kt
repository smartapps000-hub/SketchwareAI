package pz

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import androidx.appcompat.app.AppCompatViewInflater
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * v1.8.5: fixture 908's generated widgets under its generated AppTheme (Theme.Material3.Light), on a light and a night device.
 * The layout is inflated through AppCompatViewInflater, as AppCompatActivity does with the theme's viewInflaterClass;
 * the Material widgets are the tags the generator writes (SketchButton, MaterialCheckBox, MaterialSwitch, outlined field). Then what onCreate does:
 * SketchTextField.hint(edittext1, "Your name") (the setHint block), switch1 checked for the second frame.
 */
private fun inflate(paparazzi: Paparazzi): View {
    val context: Context = paparazzi.context
    val impl = AppCompatViewInflater()
    val inflater = LayoutInflater.from(context).cloneInContext(context)
    inflater.factory2 = object : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, n: String, c: Context, a: AttributeSet): View? =
            impl.createView(parent, n, c, a, false, false, true, false)
        override fun onCreateView(n: String, c: Context, a: AttributeSet): View? = onCreateView(null, n, c, a)
    }
    val root = inflater.inflate(pz.render.R.layout.screen_m385, null)
    pz.render.SketchTextField.hint(root.findViewById<TextView>(pz.render.R.id.edittext1), "Your name")
    // what a rounded-corner block does: a GradientDrawable set from code
    for (id in intArrayOf(pz.render.R.id.button2, pz.render.R.id.button3)) {
        val gd = android.graphics.drawable.GradientDrawable()
        gd.setColor(0xFF2196F3.toInt())
        gd.cornerRadius = 12f * context.resources.displayMetrics.density
        root.findViewById<View>(id).background = gd
    }
    return root
}

private fun render(paparazzi: Paparazzi, name: String) {
    val root = inflate(paparazzi)
    val types = StringBuilder()
    for (id in intArrayOf(pz.render.R.id.button1, pz.render.R.id.textview1, pz.render.R.id.edittext1,
            pz.render.R.id.edittext2, pz.render.R.id.button2, pz.render.R.id.button3, pz.render.R.id.checkbox1, pz.render.R.id.switch1, pz.render.R.id.radiobutton1)) {
        types.append(root.resources.getResourceEntryName(id)).append(" = ").append(root.findViewById<View>(id).javaClass.name).append('\n')
    }
    File("/home/claude/pz/out").mkdirs()
    File("/home/claude/pz/out/m385_$name.txt").writeText(types.toString())
    save(root, "m385_${name}_off")
    // checked before the first layout (no thumb animation), animated selectors jumped to their end state:
    // layoutlib does not run animations, so a toggle after layout would draw the transition's first frame
    val on = inflate(paparazzi)
    (on.findViewById<View>(pz.render.R.id.switch1) as CompoundButton).isChecked = true
    (on.findViewById<View>(pz.render.R.id.checkbox1) as CompoundButton).isChecked = true
    (on.findViewById<View>(pz.render.R.id.radiobutton1) as CompoundButton).isChecked = true
    on.jumpDrawablesToCurrentState()
    save(on, "m385_${name}_on")
    paparazzi.snapshot(root)
}

private fun save(view: View, name: String) {
    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST))
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    val bmp = android.graphics.Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
    view.draw(android.graphics.Canvas(bmp))
    java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
}

class M385LightTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "AppTheme85")

    @Test fun generated() = render(paparazzi, "light")
}

class M385NightTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "AppTheme85")

    @Test fun generated() = render(paparazzi, "night")
}
