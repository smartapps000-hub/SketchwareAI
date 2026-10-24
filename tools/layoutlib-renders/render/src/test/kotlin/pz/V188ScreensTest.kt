package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import pz.render.R

/**
 * v1.8.8: the Library Resolver (ob_library_resolver + ob_lib_tab + ob_lib_row, the All and Required tabs and the Add
 * dialog's content), the Java editor screen (ob_java_editor; a monospace TextView stands for the code editor view) and
 * the design editor's Code tab (rows as CodeTabFragment builds them), filled the way the screens fill them. The glass
 * drawables are their plain-shape fallbacks (drawable/).
 */
private fun save(root: View, width: Int, height: Int, name: String) {
    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
    root.layout(0, 0, width, height)
    root.jumpDrawablesToCurrentState()
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bmp))
    java.io.File("/home/claude/pz/out").mkdirs()
    java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private fun row(parent: LinearLayout, title: String, subtitle: String, checked: Boolean?, action: String?) {
    val row = LayoutInflater.from(parent.context).inflate(R.layout.ob_lib_row, parent, false)
    row.findViewById<TextView>(R.id.lib_title).text = title
    row.findViewById<TextView>(R.id.lib_subtitle).text = subtitle.replace(":", ":\u200B")
    if (checked != null) {
        val check = row.findViewById<CheckBox>(R.id.lib_check)
        check.visibility = View.VISIBLE
        check.isChecked = checked
    }
    if (action != null) {
        val button = row.findViewById<TextView>(R.id.lib_action)
        button.text = action
        button.visibility = View.VISIBLE
    }
    parent.addView(row)
}

private fun label(parent: LinearLayout, text: String, style: Int, top: Int) {
    val t = TextView(parent.context, null, 0, style)
    t.text = text
    val d = parent.context.resources.displayMetrics.density
    t.setPadding((4 * d).toInt(), (top * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
    parent.addView(t)
}

private fun resolver(paparazzi: Paparazzi, selected: Int, fillList: (LinearLayout) -> Unit, search: Boolean, action: String?, name: String) {
    val context = paparazzi.context
    val root = LayoutInflater.from(context).inflate(R.layout.ob_library_resolver, null)
    root.background = context.getDrawable(R.drawable.ob_window_bg)
    root.findViewById<TextView>(R.id.lr_subtitle).text = "Project #660"
    val tabs = root.findViewById<LinearLayout>(R.id.lr_tabs)
    for ((i, title) in arrayOf("Required", "All", "Updates", "Cache").withIndex()) {
        val t = LayoutInflater.from(context).inflate(R.layout.ob_lib_tab, tabs, false) as TextView
        t.text = title
        val on = i == selected
        t.setBackgroundResource(if (on) R.drawable.ob_amber_pill else R.drawable.ob_pill16_state)
        t.setTextColor(if (on) 0xFFFFB877.toInt() else 0xFFF5F5F7.toInt())
        tabs.addView(t)
    }
    val searchField = root.findViewById<TextView>(R.id.lr_search)
    searchField.visibility = if (search) View.VISIBLE else View.GONE
    if (search) searchField.text = "retro"
    fillList(root.findViewById(R.id.lr_list))
    val a = root.findViewById<TextView>(R.id.lr_action)
    if (action != null) {
        a.visibility = View.VISIBLE
        a.text = action
    }
    val m = context.resources.displayMetrics
    save(root, m.widthPixels, m.heightPixels, name)
}

class V188ScreensTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.MaterialComponents.Light.NoActionBar.Bridge")

    @Test fun resolverAll() = resolver(paparazzi, 1, { list ->
        row(list, "converter-gson_V_2.9.0", "com.squareup.retrofit2:converter-gson:2.9.0 · 5 KB", true, "Delete")
        row(list, "retrofit_V_2.9.0", "com.squareup.retrofit2:retrofit:2.9.0 · 245 KB", true, "Delete")
        label(list, "Maven Central", R.style.Ob_Text_Label, 18)
        row(list, "com.squareup.retrofit2:retrofit", "3.0.0 · jar", null, "Add")
        row(list, "com.squareup.retrofit2:converter-moshi", "3.0.0 · jar", null, "Add")
        row(list, "com.squareup.retrofit2:adapter-rxjava3", "3.0.0 · jar", null, "Add")
    }, true, null, "lr_all")

    @Test fun resolverRequired() = resolver(paparazzi, 0, { list ->
        row(list, "converter-gson_V_2.9.0", "Ready · built in: gson\nNeeds, not switched on: com.squareup.retrofit2:retrofit:2.9.0", true, "Add 1")
        row(list, "MPAndroidChart_V_v3.1.0", "Not dexed yet · built in: androidx-annotation", true, "Dex")
        row(list, "room-runtime_V_2.6.1", "Ready · built in: androidx-core, androidx-lifecycle-livedata", true, null)
    }, false, null, "lr_required")

    @Test fun resolverUpdates() = resolver(paparazzi, 2, { list ->
        row(list, "retrofit_V_2.9.0", "2.9.0 → 3.0.0", null, "Update")
        row(list, "rxandroid_V_3.0.2", "3.0.2 → 3.0.3", null, "Update")
    }, false, "Check again", "lr_updates")

    @Test fun resolverCache() = resolver(paparazzi, 3, { list ->
        row(list, "Maven downloads", "932 KB · POM files and archives the Library Resolver fetched; fetched again when needed", null, "Clear")
        row(list, "Built-in libraries", "212.4 MB · unpacked from the app; unpacked again at the next build", null, "Clear")
        row(list, "Desugared Java library", "1.9 MB · DEX files of desugar_jdk_libs per min SDK; made again when needed", null, "Clear")
        row(list, "Unfinished installs", "0 B · left when an install stopped", null, null)
        row(list, "This project's build files", "18.2 MB · deleted at every build anyway", null, "Clear")
    }, false, null, "lr_cache")

    @Test fun addDialog() {
        val context = paparazzi.context
        val view = LayoutInflater.from(context).inflate(R.layout.ob_lib_add, null)
        view.background = context.getDrawable(R.drawable.ob_window_bg)
        view.findViewById<TextView>(R.id.la_coordinate).text = "com.squareup.retrofit2:converter-gson:2.9.0"
        view.findViewById<TextView>(R.id.la_message).text = "To install:\n• com.squareup.retrofit2:converter-gson:2.9.0 (jar)\n" +
            "• com.squareup.retrofit2:retrofit:2.9.0 (jar)\n\nBuilt in, not downloaded:\n• com.google.code.gson:gson 2.14.0\n• com.squareup.okhttp3:okhttp 3.14.9"
        val m = context.resources.displayMetrics
        view.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        save(view, m.widthPixels, view.measuredHeight + 40, "lr_add")
    }

    @Test fun javaEditor() {
        val context = paparazzi.context
        val root = LayoutInflater.from(context).inflate(R.layout.ob_java_editor, null)
        root.background = context.getDrawable(R.drawable.ob_window_bg)
        root.findViewById<TextView>(R.id.je_title).text = "MainActivity.java"
        root.findViewById<TextView>(R.id.je_subtitle).text = "Save turns your changes into blocks"
        root.findViewById<View>(R.id.je_progress).visibility = View.GONE
        val code = TextView(context)
        code.typeface = Typeface.MONOSPACE
        code.textSize = 12f
        code.setTextColor(0xFFD4D4D4.toInt())
        code.setPadding(24, 16, 16, 16)
        code.text = "\tprivate void initializeLogic() {\n\t\tandroid.util.Log.d(\"create\", \"start\");\n\t\tedittext1.setText(\"x\");\n\t}\n\n" +
            "\tpublic void _sayHi(final String _who, final double _times) {\n\t\tfor (int i = 0; i < (int) _times; i++) {\n" +
            "\t\t\tandroid.util.Log.d(\"hi\", _who + i);\n\t\t}\n\t}"
        root.findViewById<FrameLayout>(R.id.je_editor_box).addView(code, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val m = context.resources.displayMetrics
        save(root, m.widthPixels, m.heightPixels, "je_screen")
        val banner = root.findViewById<TextView>(R.id.je_banner)
        banner.visibility = View.VISIBLE
        banner.text = "Changes here can't become blocks: command blocks rewrite this file when it is built; change those blocks in the logic editor."
        root.findViewById<View>(R.id.je_save).visibility = View.GONE
        root.findViewById<View>(R.id.je_reset).visibility = View.GONE
        root.findViewById<TextView>(R.id.je_subtitle).text = "Read only"
        save(root, m.widthPixels, m.heightPixels, "je_readonly")
    }

    @Test fun codeTab() {
        val context = paparazzi.context
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        val d = context.resources.displayMetrics.density
        list.setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        list.background = context.getDrawable(R.drawable.ob_window_bg)
        val create = LayoutInflater.from(context).inflate(R.layout.ob_lib_tab, list, false) as TextView
        create.text = "+ New class"
        create.setBackgroundResource(R.drawable.ob_cta_state)
        create.setTextColor(0xFFFFFFFF.toInt())
        list.addView(create, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (40 * d).toInt()))
        label(list, "Activities", R.style.Ob_Text_Label, 18)
        row(list, "MainActivity.java", "From the blocks · Edit turns code into blocks", null, "Edit")
        row(list, "SettingsActivity.java", "Your own file (Java Manager) instead of the blocks", null, "Edit")
        label(list, "Classes", R.style.Ob_Text_Label, 18)
        row(list, "ApiClient.java", "Java · 412 bytes", null, "Open")
        row(list, "PrintHelper.kt", "Kotlin · 230 bytes", null, "Open")
        val m = context.resources.displayMetrics
        save(list, m.widthPixels, (m.heightPixels * 0.6).toInt(), "code_tab")
    }
}
