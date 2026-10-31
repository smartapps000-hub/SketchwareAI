package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import pz.render.R

/**
 * v1.8.13: the Library Resolver list after an install that needs a word (the notice row with Copy log and ×, filled as
 * LibraryResolverActivity.show() fills it) above the installed library's card with the green "✓ Installed" badge.
 */
class V1813Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.Light.NoActionBar.Bridge")

    private fun save(root: View, width: Int, name: String) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, width, root.measuredHeight)
        root.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(width, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun installedList() {
        val list = LinearLayout(paparazzi.context)
        list.orientation = LinearLayout.VERTICAL
        list.background = paparazzi.context.getDrawable(R.drawable.ob_window_bg)
        list.setPadding(40, 20, 40, 40)
        val row = LayoutInflater.from(list.context).inflate(R.layout.ob_lib_row, list, false)
        row.findViewById<TextView>(R.id.lib_title).apply {
            text = "ESCPOS Thermal Printer Android 3.3.0: 1 library couldn't be installed"
            isSingleLine = false; maxLines = 6; ellipsize = android.text.TextUtils.TruncateAt.END
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        row.findViewById<TextView>(R.id.lib_subtitle).text = "Copy log has the details"
        row.findViewById<TextView>(R.id.lib_action).apply { text = "Copy log"; visibility = View.VISIBLE }
        row.findViewById<TextView>(R.id.lib_info).apply { text = "×"; visibility = View.VISIBLE }
        list.addView(row)
        val card = LayoutInflater.from(list.context).inflate(R.layout.ob_lib_card, list, false)
        card.findViewById<TextView>(R.id.lc_name).apply { text = "ESCPOS Thermal Printer Android"; isSelected = true }
        card.findViewById<TextView>(R.id.lc_version).apply { text = "v3.3.0"; visibility = View.VISIBLE }
        card.findViewById<TextView>(R.id.lc_coordinate).apply { text = "com.github.DantSu:ESCPOS-ThermalPrinter-Android"; isSelected = true }
        card.findViewById<TextView>(R.id.lc_meta).text = "88 KB"
        card.findViewById<TextView>(R.id.lc_share).apply { text = "GitHub"; visibility = View.VISIBLE }
        card.findViewById<TextView>(R.id.lc_status).apply {
            text = "✓ Installed"; setTextColor(0xFF7BD88F.toInt()); setBackgroundResource(R.drawable.ob_pill16); visibility = View.VISIBLE
        }
        card.findViewById<View>(R.id.lc_delete).visibility = View.VISIBLE
        card.findViewById<CheckBox>(R.id.lc_check).apply { visibility = View.VISIBLE; isChecked = true }
        list.addView(card)
        save(list, paparazzi.context.resources.displayMetrics.widthPixels, "lr1813_installed")
    }
}
