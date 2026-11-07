package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.ui.BlockGuide
import com.sketchware.ai.ui.PermissionPicker
import org.junit.Rule
import org.junit.Test

/**
 * v1.9.6: the permission picker (Select-widget look) with the real rows (out/v196_items.tsv, written from the IDE's
 * PermissionCode and android-37's Manifest.permission by DumpItems), empty and searched for "blue"; the block guide
 * for three web blocks and the permission section. Picker width = the dialog's (screen - 32dp, at most 480dp).
 */
class V196Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun items(): List<PermissionPicker.Item> =
            java.io.File("/home/claude/pz/out/v196_items.tsv").readLines().filter { it.isNotBlank() }.map {
                val p = it.split("\t"); PermissionPicker.Item(p[0], p[1], p[2])
            }

    private fun save(view: View, name: String) {
        val density = paparazzi.context.resources.displayMetrics.density
        val screen = paparazzi.context.resources.displayMetrics.widthPixels
        val width = minOf(screen - (32 * density).toInt(), (480 * density).toInt())
        val frame = FrameLayout(paparazzi.context)
        frame.setBackgroundColor(Color.parseColor("#99000000"))
        val pad = (16 * density).toInt()
        frame.setPadding(pad, pad, pad, pad)
        frame.addView(view, FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT))
        val total = width + 2 * pad
        frame.measure(View.MeasureSpec.makeMeasureSpec(total, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(paparazzi.context.resources.displayMetrics.heightPixels, View.MeasureSpec.AT_MOST))
        frame.layout(0, 0, total, frame.measuredHeight)
        frame.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(total, frame.measuredHeight, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bmp))
        java.io.File("/home/claude/pz/out").mkdirs()
        java.io.FileOutputStream("/home/claude/pz/out/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun picker() {
        val ctx = paparazzi.context
        val custom = PermissionPicker.CustomRow { typed ->
            if (typed.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
                PermissionPicker.Item(typed, "Use \"$typed\"", "Custom permission (declared by a library or another app)") else null
        }
        save(PermissionPicker.content(ctx, "Select permission", items(), "CAMERA", custom) { }, "v196_picker")
        val searched = PermissionPicker.content(ctx, "Select permission", items(), "CAMERA", custom) { }
        val search = ((searched as android.view.ViewGroup).getChildAt(1) as android.widget.EditText)
        search.setText("blue")
        save(searched, "v196_picker_blue")
        val typed = PermissionPicker.content(ctx, "Select permission", items(), "", custom) { }
        ((typed as android.view.ViewGroup).getChildAt(1) as android.widget.EditText).setText("com.google.android.c2dm.permission.RECEIVE")
        save(typed, "v196_picker_custom")
    }

    @Test fun guide() {
        val ctx = paparazzi.context
        save(BlockGuide.content(ctx, "How to use this block", "webAllowDownloads"), "v196_guide_download")
        save(BlockGuide.content(ctx, "Web blocks", "sketchDownloadFile", "webSetAdBlock", "webSetPopupBlock"), "v196_guide_web")
        save(BlockGuide.content(ctx, "Permission blocks", *BlockGuide.PERMISSION_BLOCKS), "v196_guide_permission")
    }
}
