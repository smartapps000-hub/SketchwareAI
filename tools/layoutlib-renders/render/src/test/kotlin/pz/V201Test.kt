package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.github.GitHubViews
import pz.render.R
import org.junit.Rule
import org.junit.Test

/**
 * v2.0.1: the owner's 13 Nov phone report (header "Sign in with G…" and the status cut, plain backup list, import hint
 * and row cut). The in-app sign-in window (GitHubViews.authChrome) in four states, also on a 320dp phone; the Back up
 * sheet and the Restore sheet exactly as the app builds them (GitHubViews.backupSheet / restoreSheet) in a dialog frame;
 * the import sheet's top part built with the same calls as GitHubImportDialog (signed out). GitHub's page is a WebView
 * and isn't drawn by layoutlib: a grey stand-in marks where it loads. Writes out/v201_github.png.
 */
class V201Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()

    private fun shot(view: View, widthDp: Int = 393, heightDp: Int = 860): Bitmap {
        val w = dp(widthDp); val h = dp(heightDp)
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        view.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun window(status: String, error: Boolean, code: String?, progress: Int): View {
        val c = paparazzi.context
        val chrome = GitHubViews(c).authChrome()
        chrome.status.text = status
        if (error) chrome.status.setTextColor(Color.parseColor("#FF8A65"))
        if (code != null) { chrome.code.text = code; chrome.code.visibility = View.VISIBLE }
        if (progress in 1..99) { chrome.progress.visibility = View.VISIBLE; chrome.progress.progress = progress }
        val stand = TextView(c, null, 0, R.style.Ob_Text_Caption)
        stand.text = "GitHub's own page (github.com)\nloads here in the app"
        stand.gravity = Gravity.CENTER
        chrome.page.addView(stand, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return chrome.root
    }

    /** A stand-in for the glass alert dialog's window: dark, 28dp corners, Cancel at the bottom right. */
    private fun dialog(content: View): View {
        val c = paparazzi.context
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        val frame = LinearLayout(c)
        frame.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#1C1C20")); bg.cornerRadius = dp(28).toFloat()
        frame.background = bg
        frame.setPadding(0, dp(16), 0, dp(12))
        frame.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val cancel = TextView(c, null, 0, R.style.Ob_Text_Label)
        cancel.text = "Cancel"; cancel.setTextColor(Color.parseColor("#FFB877")); cancel.gravity = Gravity.END
        cancel.setPadding(0, dp(14), dp(24), dp(4))
        frame.addView(cancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.leftMargin = dp(20); lp.rightMargin = dp(20)
        root.addView(frame, lp)
        return root
    }

    /** The import sheet's top part, with the calls GitHubImportDialog.open/loadOwnBackups make (signed out). */
    private fun importSheet(): View {
        val c = paparazzi.context
        val v = GitHubViews(c)
        val content = LinearLayout(c)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20), dp(8), dp(20), dp(4))
        content.addView(v.header(R.drawable.ob_ic_github, "GitHub import project", "Your backups or a GitHub link"))
        val link = EditText(c, null, 0, R.style.Ob_Text)
        link.hint = "Paste a GitHub link"; link.setHintTextColor(Color.parseColor("#A1A1AA"))
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); link.isSingleLine = true; link.gravity = Gravity.CENTER_VERTICAL
        link.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        link.setBackgroundResource(R.drawable.ob_field); link.setPadding(dp(14), 0, dp(14), 0)
        content.addView(link, v.full(48, 14))
        val explain = v.line("A repository, a folder or a .swb file on github.com", false)
        explain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        content.addView(explain, v.full(-1, 6))
        content.addView(v.pill("Import from link", false, true, null), v.full(46, 10))
        val title = TextView(c, null, 0, R.style.Ob_Text_Title)
        title.text = "Your backups"
        content.addView(title, v.full(-1, 18))
        content.addView(v.row("Sign in to see your backups", "Projects you backed up from any phone",
                v.smallPill("Sign in", true, true, null)), v.full(-1, 8))
        return content
    }

    @Test fun github() {
        val out = java.io.File("/tmp/claude-0/-home-user-SketchwareAI/7396650f-8b3a-5447-a67d-6edecd23d850/scratchpad/pz/out"); out.mkdirs()
        val v = GitHubViews(paparazzi.context)
        val backup = v.backupSheet("Vehicle Parking Manager Pro", true, true, true)
        val backupOut = v.backupSheet("My App", false, false, true)
        val shots = listOf(
                shot(window("Sign in below · the code goes in by itself.", false, "WDJB-MJHT", 62)),
                shot(window("Code auto-filled · tap Continue, then Authorize.", false, "WDJB-MJHT", 100)),
                shot(window("Too many requests from this network. Try again in 42 s, or use the browser.", true, "WDJB-MJHT", 100), 360, 860),
                shot(window("Google, Apple, passkeys: use “Sign in via browser” below.", false, "WDJB-MJHT", 100), 320, 700),
                shot(window("Sign-in didn't work. Please try again. Tap ⟳ to try again.", true, null, 100), 320, 700),
                shot(dialog(backup.root)),
                shot(dialog(backupOut.root), 320, 860),
                shot(dialog(v.restoreSheet().root)),
                shot(dialog(importSheet())))
        val gap = dp(12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots.maxOf { it.height } + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (s in shots) { canvas.drawBitmap(s, x.toFloat(), gap.toFloat(), null); x += s.width + gap }
        java.io.FileOutputStream(java.io.File(out, "v201_github.png")).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** v2.0.1 (review): the same sheets and window with Android's largest common font size (1.3) on a 320dp phone. */
class V201LargeFontTest {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT, fontScale = 1.3f), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()

    private fun shot(view: View, widthDp: Int, heightDp: Int): Bitmap {
        val w = dp(widthDp); val h = dp(heightDp)
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        view.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun framed(content: View): View {
        val c = paparazzi.context
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        val frame = LinearLayout(c)
        frame.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(); bg.setColor(Color.parseColor("#1C1C20")); bg.cornerRadius = dp(28).toFloat()
        frame.background = bg
        frame.setPadding(0, dp(16), 0, dp(12))
        frame.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.leftMargin = dp(16); lp.rightMargin = dp(16)
        root.addView(frame, lp)
        return root
    }

    @Test fun largeFont() {
        val out = java.io.File("/tmp/claude-0/-home-user-SketchwareAI/7396650f-8b3a-5447-a67d-6edecd23d850/scratchpad/pz/out"); out.mkdirs()
        val c = paparazzi.context
        val v = GitHubViews(c)
        val chrome = v.authChrome()
        chrome.status.text = "Too many requests from this network. Try again in 42 s, or use the browser."
        chrome.status.setTextColor(Color.parseColor("#FF8A65"))
        chrome.code.text = "WDJB-MJHT"; chrome.code.visibility = View.VISIBLE
        val chrome2 = v.authChrome()
        chrome2.status.text = "Google, Apple, passkeys: use “Sign in via browser” below."
        chrome2.code.text = "WDJB-MJHT"; chrome2.code.visibility = View.VISIBLE
        val shots = listOf(shot(chrome.root, 320, 700), shot(chrome2.root, 393, 700),
                shot(framed(v.backupSheet("Vehicle Parking Manager Pro", false, true, true).root), 320, 860),
                shot(framed(v.restoreSheet().root), 320, 860))
        val gap = dp(12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots.maxOf { it.height } + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (s in shots) { canvas.drawBitmap(s, x.toFloat(), gap.toFloat(), null); x += s.width + gap }
        java.io.FileOutputStream(java.io.File(out, "v201_large_font.png")).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
