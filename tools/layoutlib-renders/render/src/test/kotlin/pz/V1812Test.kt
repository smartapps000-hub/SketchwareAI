package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import pz.render.R

/**
 * v1.8.12: the Add library dialog's content while Find works (step line + bar) and after a failure (Try again), and a
 * library card with the green "Up to date" badge and the GitHub button, filled as LibraryResolverActivity fills them.
 */
private fun save1812(root: View, width: Int, name: String) {
    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val height = root.measuredHeight
    root.layout(0, 0, width, height)
    root.jumpDrawablesToCurrentState()
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bmp))
    java.io.File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

class V1812Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.Light.NoActionBar.Bridge")

    private fun dialog(): View {
        val view = LayoutInflater.from(paparazzi.context).inflate(R.layout.ob_lib_add, null)
        view.background = paparazzi.context.getDrawable(R.drawable.ob_window_bg)
        view.findViewById<TextView>(R.id.la_coordinate).text = "com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0"
        return view
    }

    @Test fun finding() {
        val view = dialog()
        view.findViewById<TextView>(R.id.la_message).text = ""
        view.findViewById<View>(R.id.la_progress_box).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.la_state).text = "Reading the POM (request 1)\njitpack.io · ESCPOS-ThermalPrinter-Android-3.3.0.pom"
        val bar = view.findViewById<ProgressBar>(R.id.la_progress)
        bar.isIndeterminate = false
        bar.progress = 450
        save1812(view, paparazzi.context.resources.displayMetrics.widthPixels, "lr1812_finding")
    }

    @Test fun failed() {
        val view = dialog()
        view.findViewById<TextView>(R.id.la_message).text = "Not found in the repositories: com.github.Someone:Gone:1.0"
        save1812(view, paparazzi.context.resources.displayMetrics.widthPixels, "lr1812_failed")
    }

    @Test fun upToDateCard() {
        val list = LinearLayout(paparazzi.context)
        list.orientation = LinearLayout.VERTICAL
        list.background = paparazzi.context.getDrawable(R.drawable.ob_window_bg)
        list.setPadding(40, 20, 40, 40)
        val card = LayoutInflater.from(list.context).inflate(R.layout.ob_lib_card, list, false)
        card.findViewById<TextView>(R.id.lc_name).apply { text = "ESCPOS Thermal Printer Android"; isSelected = true }
        card.findViewById<TextView>(R.id.lc_version).apply { text = "v3.4.0"; visibility = View.VISIBLE }
        card.findViewById<TextView>(R.id.lc_coordinate).apply { text = "com.github.DantSu:ESCPOS-ThermalPrinter-Android"; isSelected = true }
        card.findViewById<TextView>(R.id.lc_meta).text = "88 KB"
        card.findViewById<TextView>(R.id.lc_share).apply { text = "GitHub"; visibility = View.VISIBLE }
        card.findViewById<TextView>(R.id.lc_status).apply {
            text = "✓ Up to date"; setTextColor(0xFF7BD88F.toInt()); setBackgroundResource(R.drawable.ob_pill16); visibility = View.VISIBLE
        }
        card.findViewById<View>(R.id.lc_delete).visibility = View.VISIBLE
        card.findViewById<CheckBox>(R.id.lc_check).apply { visibility = View.VISIBLE; isChecked = true }
        list.addView(card)
        save1812(list, paparazzi.context.resources.displayMetrics.widthPixels, "lr1812_card")
    }
}
