package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.github.GitHubScreen
import pz.render.R
import org.junit.Rule
import org.junit.Test

/**
 * v1.9.9: the "Cloud Sync & Account" screen (ob_github.xml + GitHubScreen, the IDE's own code) in six states: the
 * sign-in gate, the Device Flow code, "Almost done" after GitHub's approval, an expired code, signed in with the
 * phone's projects (one backed up, one not yet, one backing up) and a backup that is only on GitHub, and signed in
 * while the list loads. Writes out/v199_cloud_sync.png.
 */
class V199Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()

    private val noActions = object : GitHubScreen.Actions {
        override fun signIn() {}
        override fun openVerification() {}
        override fun cancelSignIn() {}
        override fun signOut() {}
        override fun backUp(index: Int) {}
        override fun restore(index: Int) {}
        override fun restoreCloudOnly(index: Int) {}
        override fun refresh() {}
        override fun copyDetails() {}
    }

    /** A stand-in for the downloaded GitHub picture. */
    private fun avatar(): BitmapDrawable {
        val size = 128
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor("#5B8DEF"), Color.parseColor("#8E5BEF"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null; p.color = Color.WHITE; p.textSize = 64f; p.textAlign = Paint.Align.CENTER
        c.drawText("S", size / 2f, size / 2f + 22f, p)
        return BitmapDrawable(paparazzi.context.resources, b)
    }

    private fun row(title: String, detail: String?, canRestore: Boolean, working: String? = null) = GitHubScreen.Row().apply {
        this.title = title; this.detail = detail; this.canBackUp = true; this.canRestore = canRestore; this.working = working
    }

    private fun screen(subtitle: String, s: GitHubScreen.State, backHidden: Boolean = false): Bitmap {
        val c = paparazzi.context
        val root = LayoutInflater.from(c).inflate(R.layout.ob_github, null) as LinearLayout
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        (root.findViewById<android.widget.TextView>(R.id.gh_subtitle)).text = subtitle
        if (backHidden) root.findViewById<View>(R.id.gh_back).visibility = View.INVISIBLE
        GitHubScreen.render(c, root.findViewById(R.id.gh_list), s, noActions)
        val w = dp(393); val h = dp(860)
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        root.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        return bmp
    }

    @Test fun cloudSync() {
        val out = java.io.File("/home/claude/pz/out"); out.mkdirs()
        val sGate = GitHubScreen.State().apply { gate = true }
        val sCode = GitHubScreen.State().apply { gate = true; userCode = "FAB4-3204" }
        val sFinishing = GitHubScreen.State().apply { gate = true; finishing = true; busy = true }
        val expired = GitHubScreen.State().apply { status = "The code expired. Tap Sign in again."; statusIsError = true }
        val signedIn = GitHubScreen.State().apply {
            login = "smartapps000-hub"; avatar = avatar()
            projects = listOf(row("Vehicle Parking", "Backed up 12 Nov, 14:05", true),
                    row("Fixture_m3", "Not backed up yet", false),
                    row("Telugu News", null, false, "Backing up…"))
            cloudOnly = listOf(GitHubScreen.Row().apply { title = "Old Calculator"; detail = "com.my.calc · 3 Nov, 09:12"; canRestore = true })
            busy = true
        }
        val loading = GitHubScreen.State().apply {
            login = "smartapps000-hub"; avatar = avatar(); loadingProjects = true
            projects = listOf(row("Vehicle Parking", "com.my.parking", false), row("Fixture_m3", "com.fixture.m3", false))
            cloudOnly = listOf()
        }
        val shots = listOf(screen("Sign in to continue", sGate, true), screen("Sign in to continue", sCode, true),
                screen("Sign in to continue", sFinishing, true), screen("Not signed in", expired),
                screen("@smartapps000-hub", signedIn), screen("@smartapps000-hub", loading))
        val gap = dp(12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots[0].height + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (shot in shots) { canvas.drawBitmap(shot, x.toFloat(), gap.toFloat(), null); x += shot.width + gap }
        java.io.FileOutputStream(java.io.File(out, "v199_cloud_sync.png")).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
