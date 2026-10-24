package pz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatViewInflater
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager.widget.ViewPager
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import org.junit.Rule
import org.junit.Test
import pz.render.R

/**
 * v1.8.7: the New Project templates as the harness built them (fixtures 914-918: NewProjectSetup.create, then the Run
 * build's generator). Each fixture's generated layouts, styles, colours and images were copied in by
 * import_generated.py under its own names (np<id>_*, AppTheme<id>); the template's onCreate code (the add-source
 * block, copied from the generated MainActivity) runs as PresetCode<id>.run on the inflated views. Inflated through
 * AppCompatViewInflater (viewInflaterClass of the generated theme), light and night.
 */
private fun inflate(paparazzi: Paparazzi, layout: Int): View {
    val context: Context = paparazzi.context
    val impl = AppCompatViewInflater()
    val inflater = LayoutInflater.from(context).cloneInContext(context)
    inflater.factory2 = object : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, n: String, c: Context, a: AttributeSet): View? =
            impl.createView(parent, n, c, a, false, false, true, false)
        override fun onCreateView(n: String, c: Context, a: AttributeSet): View? = onCreateView(null, n, c, a)
    }
    return inflater.inflate(layout, null)
}

private fun save(view: View, name: String, setup: (View) -> Unit = {}) {
    // the activity label on the toolbar, as setSupportActionBar shows it (the app name of each fixture)
    view.findViewById<androidx.appcompat.widget.Toolbar>(R.id._toolbar)?.title = "NP " + name.substringBefore('_').replace("np914", "npempty")
        .replace("np915", "npscaffold").replace("np916", "npbottom").replace("np917", "npdrawer").replace("np918", "nptabs")
    setup(view)
    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
    view.layout(0, 0, 1080, 2340)
    view.jumpDrawablesToCurrentState()
    val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // the window background of the generated theme (?android:colorBackground)
    val a = view.context.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
    canvas.drawColor(a.getColor(0, 0xFFFFFFFF.toInt()))
    a.recycle()
    view.draw(canvas)
    java.io.File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private val LIGHT = DeviceConfig.PIXEL_5
private val NIGHT = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT)

class Preset914Light { @get:Rule val p = Paparazzi(deviceConfig = LIGHT, theme = "AppTheme914"); @Test fun r() = save(inflate(p, R.layout.np914_main), "np914_light") }
class Preset914Night { @get:Rule val p = Paparazzi(deviceConfig = NIGHT, theme = "AppTheme914"); @Test fun r() = save(inflate(p, R.layout.np914_main), "np914_night") }
class Preset915Light { @get:Rule val p = Paparazzi(deviceConfig = LIGHT, theme = "AppTheme915"); @Test fun r() = save(inflate(p, R.layout.np915_main), "np915_light") }
class Preset915Night { @get:Rule val p = Paparazzi(deviceConfig = NIGHT, theme = "AppTheme915"); @Test fun r() = save(inflate(p, R.layout.np915_main), "np915_night") }

private fun bottom(v: View) = pz.render.PresetCode916.run(v.findViewById<BottomNavigationView>(R.id.bottomnavigation1), v.findViewById<TextView>(R.id.textview1))
class Preset916Light { @get:Rule val p = Paparazzi(deviceConfig = LIGHT, theme = "AppTheme916"); @Test fun r() = save(inflate(p, R.layout.np916_main), "np916_light", ::bottom) }
class Preset916Night { @get:Rule val p = Paparazzi(deviceConfig = NIGHT, theme = "AppTheme916"); @Test fun r() = save(inflate(p, R.layout.np916_main), "np916_night", ::bottom) }

private fun openDrawer(v: View) { (v as DrawerLayout).openDrawer(Gravity.START, false) }
class Preset917Closed { @get:Rule val p = Paparazzi(deviceConfig = LIGHT, theme = "AppTheme917"); @Test fun r() = save(inflate(p, R.layout.np917_main), "np917_closed") }
class Preset917Open { @get:Rule val p = Paparazzi(deviceConfig = LIGHT, theme = "AppTheme917"); @Test fun r() = save(inflate(p, R.layout.np917_main), "np917_open", ::openDrawer) }

private fun tabs(v: View) = pz.render.PresetCode918.run(v.findViewById<ViewPager>(R.id.viewpager1), v.findViewById<TabLayout>(R.id.tablayout1))
class Preset918Light { @get:Rule val p = Paparazzi(deviceConfig = LIGHT, theme = "AppTheme918"); @Test fun r() = save(inflate(p, R.layout.np918_main), "np918_light", ::tabs) }
class Preset918Night { @get:Rule val p = Paparazzi(deviceConfig = NIGHT, theme = "AppTheme918"); @Test fun r() = save(inflate(p, R.layout.np918_main), "np918_night", ::tabs) }
