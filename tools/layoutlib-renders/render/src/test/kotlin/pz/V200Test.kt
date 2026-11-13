package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
 * v2.0.0: the IDE's own GitHub views. The drawer header with the GitHub account card (main_drawer_header.xml bound by
 * DrawerAccountCard) signed out and signed in (picture, name, @login, e-mail, Sign out); the in-app sign-in window
 * (GitHubViews.authChrome) with the code filled in and after a network error (GitHub's page itself is a WebView and
 * isn't drawn by layoutlib: a grey stand-in marks where it loads); the sign-in gate (ob_github.xml + the card).
 * Writes out/v200_github.png.
 */
class V200Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()

    private val noActions = object : DrawerAccountCard.Actions {
        override fun signIn() {}
        override fun signOut() {}
    }

    private fun avatarFile(): java.io.File {
        val size = 128
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor("#5B8DEF"), Color.parseColor("#8E5BEF"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null; p.color = Color.WHITE; p.textSize = 64f; p.textAlign = Paint.Align.CENTER
        c.drawText("S", size / 2f, size / 2f + 22f, p)
        val f = java.io.File.createTempFile("avatar", ".png")
        java.io.FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    private fun shot(view: View): Bitmap {
        val w = dp(393); val h = dp(860)
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
        panel.addView(header, LinearLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT))
        for (label in listOf("About Modders", "Changelog", "System Settings", "Program information", "Developer Tools", "Create Release Keystore", "App Theme: Pure Dark")) {
            val t = TextView(c, null, 0, R.style.Ob_Text)
            t.text = label; t.textSize = 17f; t.setPadding(dp(28), dp(14), 0, dp(14))
            panel.addView(t)
        }
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        root.addView(panel, FrameLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.MATCH_PARENT))
        return root
    }

    private fun window(status: String, error: Boolean, code: String?): View {
        val c = paparazzi.context
        val chrome = GitHubViews(c).authChrome()
        chrome.status.text = status
        if (error) { chrome.status.setTextColor(Color.parseColor("#FF8A65")); chrome.retry.visibility = View.VISIBLE }
        if (code != null) { chrome.code.text = code; chrome.code.visibility = View.VISIBLE }
        val stand = TextView(c, null, 0, R.style.Ob_Text_Caption)
        stand.text = "GitHub's own page (github.com/login/device)\nloads here in the app"
        stand.gravity = Gravity.CENTER
        chrome.page.addView(stand, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return chrome.root
    }

    private fun gate(): View {
        val c = paparazzi.context
        val root = LayoutInflater.from(c).inflate(R.layout.ob_github, null) as LinearLayout
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        root.findViewById<TextView>(R.id.gh_subtitle).text = "Sign in to continue"
        root.findViewById<View>(R.id.gh_back).visibility = View.INVISIBLE
        val v = GitHubViews(c)
        val card = v.glassCard()
        card.addView(v.header(null, "Sign in with GitHub", "Sync & back up projects"))
        card.addView(v.pill("Sign in with GitHub", true, true, null), v.full(48, 14))
        root.findViewById<LinearLayout>(R.id.gh_list).addView(card, v.full(-1, 8))
        return root
    }

    @Test fun github() {
        val out = java.io.File("/home/claude/pz/out"); out.mkdirs()
        val signedIn = GitHubSession.Profile().apply {
            login = "smartapps000-hub"; name = "Smart Apps"; email = "owner@example.com"; avatar = avatarFile()
        }
        val noName = GitHubSession.Profile().apply { login = "octo-dev-with-a-long-login-name"; name = null; email = null; avatar = null }
        val shots = listOf(shot(drawer(null)), shot(drawer(signedIn)), shot(drawer(noName)),
                shot(window("Code filled in. Tap Continue, then Authorize.", false, "FAB4-3204")),
                shot(window("GitHub can't be reached. Check the internet and try again.", true, "FAB4-3204")),
                shot(gate()))
        val gap = dp(12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots[0].height + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (s in shots) { canvas.drawBitmap(s, x.toFloat(), gap.toFloat(), null); x += s.width + gap }
        java.io.FileOutputStream(java.io.File(out, "v200_github.png")).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
