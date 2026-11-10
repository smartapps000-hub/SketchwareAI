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
 * v1.9.7: the guide sheet now draws each block with its real shape (connector notches, parameter slots) via
 * BlockShapeView; the download-file block gains a type slot. Renders the web-block guide, the permission guide and the
 * download-type picker. Writes out/v197_*.png.
 */
class V197Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun save(view: View, name: String, wDp: Int = 480) {
        val d = paparazzi.context.resources.displayMetrics.density
        val width = (wDp * d).toInt()
        val frame = FrameLayout(paparazzi.context)
        frame.setBackgroundColor(Color.parseColor("#99000000"))
        val pad = (16 * d).toInt()
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

    @Test fun guideWeb() {
        save(BlockGuide.content(paparazzi.context, "Web blocks",
                "webAllowDownloads", "sketchDownloadFile", "webSetAdBlock", "webSetPopupBlock"), "v197_guide_web")
    }

    @Test fun guidePermission() {
        save(BlockGuide.content(paparazzi.context, "Permission blocks", *BlockGuide.PERMISSION_BLOCKS), "v197_guide_permission")
    }

    @Test fun guideShapes() {
        // a boolean block (pointed ends) and a value block (round ends) next to a command block
        save(BlockGuide.content(paparazzi.context, "Block shapes",
                "hasAutoPermission", "getPermissionStatus", "webSetAdBlock"), "v197_guide_shapes")
    }

    @Test fun downloadType() {
        val items = listOf(
                PermissionPicker.Item("Default (*/*)", "Default (*/*)", "Chrome-grade name: server, link or type; never .bin"),
                PermissionPicker.Item("Audio", "Audio", "mp3, m4a, wav, flac, ogg, aac"),
                PermissionPicker.Item("Video", "Video", "mp4, mkv, webm, avi, mov"),
                PermissionPicker.Item("Images", "Images", "png, jpg, webp, gif"),
                PermissionPicker.Item("Packages", "Packages", "apk, aab, zip, rar, 7z"),
                PermissionPicker.Item("Documents", "Documents", "pdf, docx, doc, txt"))
        save(PermissionPicker.content(paparazzi.context, "Download type", items, "Audio", null) { }, "v197_download_type")
    }
}
