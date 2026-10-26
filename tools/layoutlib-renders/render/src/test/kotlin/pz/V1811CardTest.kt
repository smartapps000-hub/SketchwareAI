package pz

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * v1.8.11: the Library Resolver's library card (ob_lib_card) filled the way LibraryResolverActivity.card() fills it,
 * the sort bar of the All tab, the Updates tab with its "Older versions" section, and the progress dialog's content.
 */
private fun save1811(root: View, width: Int, height: Int, name: String) {
    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
    root.layout(0, 0, width, height)
    root.jumpDrawablesToCurrentState()
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bmp))
    java.io.File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private fun dp(v: View, value: Int) = (value * v.resources.displayMetrics.density).toInt()

/** status: null, "Latest", "Update" (amber), "Newer: v3.5.4"; check: null = gone */
private fun card(list: LinearLayout, title: String, version: String?, coordinate: String, meta: String, share: Boolean,
                 status: String?, delete: Boolean, check: Boolean?) {
    val card = LayoutInflater.from(list.context).inflate(R.layout.ob_lib_card, list, false)
    card.findViewById<TextView>(R.id.lc_name).apply { text = title; isSelected = true }
    if (version != null) card.findViewById<TextView>(R.id.lc_version).apply { text = "v$version"; visibility = View.VISIBLE }
    card.findViewById<TextView>(R.id.lc_coordinate).apply { text = coordinate; isSelected = true }
    card.findViewById<TextView>(R.id.lc_meta).text = meta
    if (share) card.findViewById<View>(R.id.lc_share).visibility = View.VISIBLE
    if (status != null) card.findViewById<TextView>(R.id.lc_status).apply {
        text = status
        visibility = View.VISIBLE
        if (status == "Update" || status.startsWith("Add") || status == "Dex") setBackgroundResource(R.drawable.ob_amber_pill)
    }
    if (delete) card.findViewById<View>(R.id.lc_delete).visibility = View.VISIBLE
    if (check != null) card.findViewById<CheckBox>(R.id.lc_check).apply { visibility = View.VISIBLE; isChecked = check }
    list.addView(card)
}

private fun sortBar(list: LinearLayout, newest: Boolean) {
    val bar = LinearLayout(list.context)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setPadding(0, dp(list, 2), 0, dp(list, 2))
    bar.addView(TextView(list.context, null, 0, R.style.Ob_Text_Caption).apply { text = "Sort" })
    for ((text, on) in listOf("A to Z" to !newest, "Updates & newest first" to newest)) {
        val pill = TextView(list.context, null, 0, R.style.Ob_Text_Button)
        pill.text = text
        pill.textSize = 12f
        pill.gravity = Gravity.CENTER
        pill.setPadding(dp(list, 12), 0, dp(list, 12), 0)
        pill.setBackgroundResource(if (on) R.drawable.ob_amber_pill else R.drawable.ob_pill16_state)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(list, 32))
        lp.leftMargin = dp(list, 8)
        bar.addView(pill, lp)
    }
    list.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}

private fun note(list: LinearLayout, text: String, style: Int, top: Int) {
    val t = TextView(list.context, null, 0, style)
    t.text = text
    t.setPadding(4, dp(list, top), 4, dp(list, 4))
    list.addView(t)
}

private fun screen(paparazzi: Paparazzi, selected: Int, search: Boolean, action: String?, name: String, fill: (LinearLayout) -> Unit) {
    val context = paparazzi.context
    val root = LayoutInflater.from(context).inflate(R.layout.ob_library_resolver, null)
    root.background = context.getDrawable(R.drawable.ob_window_bg)
    root.findViewById<TextView>(R.id.lr_subtitle).text = "Project #659"
    val tabs = root.findViewById<LinearLayout>(R.id.lr_tabs)
    for ((i, title) in arrayOf("Required", "All", "Updates", "Cache").withIndex()) {
        val t = LayoutInflater.from(context).inflate(R.layout.ob_lib_tab, tabs, false) as TextView
        t.text = title
        t.setBackgroundResource(if (i == selected) R.drawable.ob_amber_pill else R.drawable.ob_pill16_state)
        t.setTextColor(if (i == selected) 0xFFFFB877.toInt() else 0xFFF5F5F7.toInt())
        tabs.addView(t)
    }
    root.findViewById<View>(R.id.lr_search).visibility = if (search) View.VISIBLE else View.GONE
    fill(root.findViewById(R.id.lr_list))
    if (action != null) root.findViewById<TextView>(R.id.lr_action).apply { visibility = View.VISIBLE; text = action }
    val m = context.resources.displayMetrics
    save1811(root, m.widthPixels, m.heightPixels, name)
}

class V1811CardTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.Light.NoActionBar.Bridge")

    @Test fun allTab() = screen(paparazzi, 1, true, null, "lr1811_all") { list ->
        sortBar(list, false)
        card(list, "Airbnb Lottie", "6.6.0", "com.airbnb.android:lottie", "612 KB", true, "Update", true, true)
        card(list, "ESCPOS Thermal Printer Android", "3.4.0", "com.github.DantSu:ESCPOS-ThermalPrinter-Android", "88 KB", true, "Latest", true, true)
        card(list, "Zxing Core", "3.4.1", "com.google.zxing:core", "540 KB", true, "Newer: v3.5.4", true, false)
        card(list, "Zxing Core", "3.5.4", "com.google.zxing:core", "556 KB", true, "Latest", true, true)
        card(list, "my_widgets", null, "imported file · my_widgets", "41 KB · not ready to build", false, null, true, false)
    }

    @Test fun updatesTab() = screen(paparazzi, 2, false, "Check again", "lr1811_updates") { list ->
        card(list, "Airbnb Lottie", "6.6.0", "com.airbnb.android:lottie", "v6.6.0 → v6.7.1", true, "Update", false, null)
        note(list, "Older versions (a newer one is installed)", R.style.Ob_Text_Label, 18)
        note(list, "Projects that still use them keep working; Delete removes the old copy.", R.style.Ob_Text_Caption, 12)
        card(list, "Zxing Core", "3.4.1", "com.google.zxing:core", "540 KB · used by this project", true, "Newer: v3.5.4", true, null)
    }

    @Test fun progressDialog() {
        val context = paparazzi.context
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.background = context.getDrawable(R.drawable.ob_window_bg)
        box.setPadding(dp(box, 24), dp(box, 8), dp(box, 24), dp(box, 12))
        box.addView(TextView(context, null, 0, R.style.Ob_Text_Title).apply { text = "Resolving…" })
        box.addView(TextView(context, null, 0, R.style.Ob_Text_Body).apply {
            text = "Fetching the .aar (1/2)\ncom.github.DantSu:ESCPOS-ThermalPrinter-Android:3.4.0".replace(":", ":\u200B")
        })
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
        bar.isIndeterminate = false
        bar.max = 1000
        bar.progress = 620
        val amber = ColorStateList.valueOf(0xFFFF7A29.toInt())
        bar.progressTintList = amber
        bar.indeterminateTintList = amber
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(box, 14))
        lp.topMargin = dp(box, 12)
        box.addView(bar, lp)
        box.addView(TextView(context, null, 0, R.style.Ob_Text_Caption).apply { text = "38 KB of 62 KB · ESCPOS-ThermalPrinter-Android-3.4.0.aar" })
        val m = context.resources.displayMetrics
        box.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        save1811(box, m.widthPixels, box.measuredHeight, "lr1811_progress")
    }
}
