package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import com.sketchware.ai.ui.BlockGuide
import pz.render.R
import org.junit.Rule
import org.junit.Test

/**
 * v2.0.3: the owner's 15 Nov phone report. The drawer account card (main_drawer_header.xml + DrawerAccountCard) on a
 * 300dp drawer: signed out; signed in with a long name and e-mail (a top row with the picture, "GitHub" and its state,
 * Sign out; name, @login, e-mail and the account line wrapped below, nothing drawn over); GitHub not accepting the saved
 * sign-in (the line in the error colour, Sign in again); project cards (myprojects_item.xml) with the Private and the
 * Public pill and the share pill under "#663" (393dp and 360dp); the GitHub blocks' guide (BlockGuide) in #1F6FEB with
 * "Open GitHub Token Page" and "Create GitHub Sample Project". Writes out/v203_github.png.
 */
class V203Test {
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

    private fun card(c: android.content.Context, name: String, isPublic: Boolean, target: String?): View {
        val card = LayoutInflater.from(c).inflate(R.layout.myprojects_item, null, false)
        card.findViewById<TextView>(R.id.app_name).text = name.replace(' ', '_')
        card.findViewById<TextView>(R.id.project_name).text = name
        card.findViewById<TextView>(R.id.package_name).text = "com.my.vehi… · 4.7.6 (84)"
        card.findViewById<TextView>(R.id.ob_monogram).text = name.take(2).uppercase()
        val id = card.findViewById<TextView>(R.id.tv_published)
        val pill = card.findViewById<TextView>(R.id.ob_target_pill)
        if (target == null) {
            id.text = "#663"; id.visibility = View.VISIBLE; pill.visibility = View.GONE
        } else {
            id.visibility = View.GONE; pill.text = target; pill.visibility = View.VISIBLE
        }
        card.findViewById<View>(R.id.ob_github_pills).visibility = View.VISIBLE
        card.findViewById<TextView>(R.id.ob_visibility_text).text = if (isPublic) "Public" else "Private"
        card.findViewById<ImageView>(R.id.ob_visibility_icon).setImageResource(if (isPublic) R.drawable.ob_ic_public else R.drawable.ob_ic_lock)
        return card
    }

    private fun projectCards(): View {
        val c = paparazzi.context
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        root.setPadding(dp(8), dp(24), dp(8), 0)
        for ((name, pub, target) in listOf(Triple("New video", false, null), Triple("Sri Durga Varahi Parking Stand", true, "Target 28"),
                Triple("GitHub API Demo", false, null))) {
            root.addView(card(c, name, pub, target), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return root
    }

    private fun guide(): View {
        val c = paparazzi.context
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        val sheet = BlockGuide.content(c, "GitHub blocks", *BlockGuide.GITHUB_BLOCKS)
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = dp(16); lp.rightMargin = dp(16); lp.topMargin = dp(24)
        root.addView(sheet, lp)
        return root
    }

    @Test fun github() {
        val out = java.io.File("/tmp/claude-0/-home-user-SketchwareAI/7396650f-8b3a-5447-a67d-6edecd23d850/scratchpad/pz/out"); out.mkdirs()
        val long = GitHubSession.Profile().apply {
            login = "createnewappswithsketchwareai"; name = "Bammidi Eswararao"
            email = "createnewappswithsketchwareai.bammidieswararao@gmail.com"; avatar = null
            repositories = 12; checkedAt = 1_794_732_000_000L
        }
        val refused = GitHubSession.Profile().apply {
            login = "createnewappswithsketchwareai"; name = "Bammidi Eswararao"
            email = "createnewappswithsketchwareai.bammidieswararao@gmail.com"; avatar = null
            problem = "GitHub didn't accept the saved sign-in at 22:17 (Bad credentials). You're still signed in here: tap Sign in again to renew it."
        }
        val shots = listOf(
                shot(drawer(null), 320, 520), shot(drawer(long), 320, 520), shot(drawer(refused), 320, 520),
                shot(projectCards(), 393, 520), shot(projectCards(), 360, 520), shot(guide(), 393, 860))
        val gap = dp(12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots.maxOf { it.height } + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (s in shots) { canvas.drawBitmap(s, x.toFloat(), gap.toFloat(), null); x += s.width + gap }
        java.io.FileOutputStream(java.io.File(out, "v203_github.png")).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
