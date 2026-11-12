package pz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * v1.9.8: the GitHub screen (ob_github.xml + GitHubScreen, the IDE's own code) in four states: signed out with an error,
 * the Device Flow code, signed in with backups, signed in while busy. Writes out/v198_github.png.
 */
class V198Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()

    private val noActions = object : GitHubScreen.Actions {
        override fun signIn(clientId: String) {}
        override fun openVerification() {}
        override fun cancelSignIn() {}
        override fun signOut() {}
        override fun backUp() {}
        override fun refreshBackups() {}
        override fun openBackup(index: Int) {}
    }

    private fun screen(subtitle: String, s: GitHubScreen.State): Bitmap {
        val c = paparazzi.context
        val root = LayoutInflater.from(c).inflate(R.layout.ob_github, null) as LinearLayout
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        (root.findViewById<android.widget.TextView>(R.id.gh_subtitle)).text = subtitle
        GitHubScreen.render(c, root.findViewById(R.id.gh_list), s, noActions)
        val w = dp(393); val h = dp(860)
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        root.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        return bmp
    }

    @Test fun github() {
        val out = java.io.File("/home/claude/pz/out"); out.mkdirs()
        val a = GitHubScreen.State().apply {
            clientId = "Ov23liExampleClientId"
            status = "Device sign-in is switched off for this OAuth app. On github.com open Settings > Developer settings > OAuth Apps > the app, tick \"Enable Device Flow\" and save."
            statusIsError = true
        }
        val b = GitHubScreen.State().apply { userCode = "WDJB-MJHT" }
        val c = GitHubScreen.State().apply {
            login = "smartapps000-hub"
            status = "Fixture_m3 was backed up (12 Nov 2026, 14:05)."
            backups = listOf(arrayOf("Vehicle Parking", "com.my.parking · v1.2 · 12 Nov 2026, 14:05 · 2318 KB"),
                    arrayOf("Fixture_m3", "com.fixture.m3 · v1.0 · 12 Nov 2026, 13:58 · 412 KB"))
        }
        val d = GitHubScreen.State().apply {
            login = "smartapps000-hub"; busy = true; status = "Uploading Vehicle Parking…"; loadingBackups = true
        }
        val shots = listOf(screen("Sign-in and cloud backup", a), screen("Sign-in and cloud backup", b),
                screen("Signed in as @smartapps000-hub", c), screen("Signed in as @smartapps000-hub", d))
        val gap = dp(12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots[0].height + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (shot in shots) { canvas.drawBitmap(shot, x.toFloat(), gap.toFloat(), null); x += shot.width + gap }
        java.io.FileOutputStream(java.io.File(out, "v198_github.png")).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
