package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.github.DrawerAccountCard
import com.sketchware.ai.github.GitHubSession
import com.sketchware.ai.github.GitHubViews
import pz.render.R
import org.junit.Rule
import org.junit.Test

/**
 * v2.0.2: the owner's 14 Nov phone report. The drawer account card (main_drawer_header.xml + DrawerAccountCard) signed
 * out on a 300dp drawer ("Sign in with GitHub" whole) and signed in with a long name, login and e-mail (marquee: one
 * line each, scrolling on the phone; layoutlib shows the first frame) with "Sign out" under them; the live progress sheet
 * (GitHubViews.progressSheet) at 65% of a backup, finished, and failed; the import guide (GitHubViews.importGuide); a
 * project card (myprojects_item.xml) with the share button next to "#663". Writes out/v202_github.png.
 */
class V202Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()

    private val noActions = object : DrawerAccountCard.Actions {
        override fun signIn() {}
        override fun signOut() {}
    }

    private fun shot(view: View, widthDp: Int = 393, heightDp: Int = 860): Bitmap {
        val w = dp(widthDp); val h = dp(heightDp)
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        view.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun drawer(profile: GitHubSession.Profile?): View {
        val c = paparazzi.context
        val panel = LinearLayout(c)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.parseColor("#141416"))
        val header = LayoutInflater.from(c).inflate(R.layout.main_drawer_header, panel, false)
        DrawerAccountCard.bind(header.findViewById(R.id.drawer_account), profile, noActions)
        panel.addView(header, LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT))
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        root.addView(panel, FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT))
        return root
    }

    private fun dialog(content: View): View {
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
        lp.leftMargin = dp(20); lp.rightMargin = dp(20)
        root.addView(frame, lp)
        return root
    }

    private fun progress(percent: Int, text: String, failed: Boolean, button: String): View {
        val c = paparazzi.context
        val v = GitHubViews(c)
        val p = v.progressSheet(R.drawable.ob_ic_github, "Back up Vehicle Parking")
        p.percent.text = "$percent%"
        p.bar.progress = percent * 10
        p.status.text = if (failed) text else "$percent% • $text"
        if (failed) { p.status.setTextColor(Color.parseColor("#FF8A65")); p.percent.setTextColor(Color.parseColor("#FF8A65")) }
        val col = LinearLayout(c); col.orientation = LinearLayout.VERTICAL
        col.addView(p.root)
        val b = TextView(c, null, 0, R.style.Ob_Text_Label)
        b.text = button; b.setTextColor(Color.parseColor("#FFB877")); b.gravity = Gravity.END
        b.setPadding(0, dp(14), dp(24), dp(4))
        col.addView(b)
        return dialog(col)
    }

    private fun projectCard(): View {
        val c = paparazzi.context
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        val card = LayoutInflater.from(c).inflate(R.layout.myprojects_item, root, false)
        card.findViewById<TextView>(R.id.app_name).text = "Vehicle_Parking"
        card.findViewById<TextView>(R.id.project_name).text = "Vehicle Parking"
        card.findViewById<TextView>(R.id.package_name).text = "com.my.parking · 1.0"
        card.findViewById<TextView>(R.id.ob_monogram).text = "VP"
        card.findViewById<TextView>(R.id.tv_published).text = "#663"
        card.findViewById<TextView>(R.id.tv_published).visibility = View.VISIBLE
        card.findViewById<ImageView>(R.id.ob_share_link).visibility = View.VISIBLE
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(40); lp.leftMargin = dp(8); lp.rightMargin = dp(8)
        root.addView(card, lp)
        return root
    }

    @Test fun github() {
        val out = java.io.File("/tmp/claude-0/-home-user-SketchwareAI/7396650f-8b3a-5447-a67d-6edecd23d850/scratchpad/pz/out"); out.mkdirs()
        val long = GitHubSession.Profile().apply {
            login = "createnewappswithsketchwareai"; name = "Bammidi Eswara Rao (Sketchware AI)"
            email = "createnewappswithsketchwareai.owner@example.com"; avatar = null
        }
        val v = GitHubViews(paparazzi.context)
        val shots = listOf(
                shot(drawer(null), 320, 420), shot(drawer(long), 320, 420),
                shot(progress(65, "Uploading to GitHub… 1.3 MB of 2.0 MB", false, "Hide")),
                shot(progress(100, "Vehicle Parking backed up to GitHub · the share link is on its card", false, "Done")),
                shot(progress(30, "Reading your GitHub account has to wait: GitHub's request limit is used up for now. Nothing is lost; try again after 17:56.", true, "Close")),
                shot(dialog(v.importGuide())),
                shot(projectCard(), 393, 300))
        val gap = dp(12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots.maxOf { it.height } + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (s in shots) { canvas.drawBitmap(s, x.toFloat(), gap.toFloat(), null); x += s.width + gap }
        java.io.FileOutputStream(java.io.File(out, "v202_github.png")).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
