package pz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.github.AppControl
import com.sketchware.ai.github.GitHubSession
import com.sketchware.ai.github.GitHubViews
import com.sketchware.ai.github.SessionLog
import com.sketchware.ai.github.UpdateCard
import com.sketchware.ai.ui.HoneyDropBar
import com.sketchware.ai.ui.MonogramDrawable
import com.sketchware.ai.ui.SettingsActivity
import org.junit.Rule
import org.junit.Test

/**
 * v2.1.2: the owner's 21 Nov directive (9 points) drawn from the release code: the bar with Home and Settings selected
 * (the left / right end where the crest met the corner) and a tap into Home; the Play Store list and app page
 * (monogram tiles, slim Publish row, @creator and the version pill on their own rows, four glass counters); the update
 * card (required and not) and the download sheet's buttons (Hide; Hide + Retry after an error); Restore's three tabs in
 * one card shape; the sign-in window's e-mail code bar; Settings signed out with the reason line; the New Project and
 * project settings tiles with the app's letters. Writes out/v212_*.png.
 */
class V212Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun crop(c: Context, full: Bitmap, heightDp: Int): Bitmap {
        val top = full.height - V210Shots.dp(c, heightDp)
        return Bitmap.createBitmap(full, 0, top, full.width, full.height - top)
    }

    @Test fun bar() {
        val c = paparazzi.context
        V210Shots.sampleData()
        val frames = listOf<(HoneyDropBar) -> Unit>(
                { it.select(HoneyDropBar.HOME, false) },
                { it.select(HoneyDropBar.SETTINGS, false) },
                { it.frameForTests(HoneyDropBar.GITHUB, HoneyDropBar.HOME, 0.34f) },
                { it.frameForTests(HoneyDropBar.GITHUB, HoneyDropBar.HOME, 0.55f) },
                { it.frameForTests(HoneyDropBar.GITHUB, HoneyDropBar.HOME, 0.80f) },
                { it.select(HoneyDropBar.CREATE, false) })
        val crops = frames.map { f ->
            val home = V210Shots.home(c)
            crop(c, V210Shots.shot(c, home, 393, 851) { f(V210Shots.bar(home)) }, 130)
        }
        V210Shots.save(c, "v212_bar.png", crops)
        // 3x the left and right ends (Home at rest, Settings at rest)
        val w = V210Shots.dp(c, 110); val h = V210Shots.dp(c, 112)
        val zoom = Bitmap.createBitmap(w * 3 * 2 + 24, h * 3, Bitmap.Config.ARGB_8888)
        val zc = Canvas(zoom); zc.drawColor(Color.parseColor("#3A3A44"))
        val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        val left = crops[0]; val right = crops[1]
        zc.drawBitmap(left, android.graphics.Rect(0, left.height - h, w, left.height), android.graphics.Rect(0, 0, w * 3, h * 3), p)
        zc.drawBitmap(right, android.graphics.Rect(right.width - w, right.height - h, right.width, right.height),
                android.graphics.Rect(w * 3 + 24, 0, w * 6 + 24, h * 3), p)
        java.io.FileOutputStream(java.io.File(V210Shots.out, "v212_bar_ends.png")).use { zoom.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun store() {
        val c = paparazzi.context
        V210Shots.save(c, "v212_store.png", listOf(V210Shots.shot(c, V211Shots.list(c), 393, 851),
                V210Shots.shot(c, V211Shots.page(c), 393, 851, wrap = true)))
    }

    @Test fun restore() {
        val c = paparazzi.context
        V210Shots.save(c, "v212_restore.png", listOf(V210Shots.shot(c, V211Shots.restore(c, 0), 393, 851, wrap = true),
                V210Shots.shot(c, V211Shots.restore(c, 1), 393, 851), V210Shots.shot(c, V211Shots.restore(c, 2), 393, 851)))
    }

    private fun sheet(c: Context, content: View): View {
        val frame = FrameLayout(c)
        frame.setBackgroundColor(Color.parseColor("#B3000000"))
        val card = FrameLayout(c)
        card.setBackgroundResource(pz.render.R.drawable.ob_glass)
        card.addView(content)
        val lp = FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER)
        lp.leftMargin = V210Shots.dp(c, 18); lp.rightMargin = V210Shots.dp(c, 18)
        frame.addView(card, lp)
        return frame
    }

    @Test fun update() {
        val c = paparazzi.context
        val act = com.besome.sketch.lib.base.BaseAppCompatActivity()
        act.attach(V211Shots.ctx(c), android.content.Intent())
        val config = AppControl.Config().apply {
            minVersionCode = 210; latestVersionCode = 210; latestVersionName = "v2.1.3"; forceUpdate = true
            updateReason = "This update is required: it fixes problems in the version you have, and that version is no " +
                    "longer supported. It installs over it: your projects, settings and GitHub sign-in stay."
            updateNotes.addAll(listOf("Tabs open at once and keep their place; no close questions",
                    "Your GitHub sign-in stays across updates; e-mailed codes fill with one tap",
                    "Backups show their real speed and finish without a silent 90%"))
        }
        val forced = UpdateCard.view(act, config, true, Runnable { }, null)
        val optional = UpdateCard.view(act, AppControl.Config().apply { latestVersionName = "v2.1.3" }, false, Runnable { }, Runnable { })
        // the download sheet: Hide only while it runs; Hide and Retry after an error (GitHubProgress's buttons)
        val v = GitHubViews(act)
        fun progress(failed: Boolean): View {
            val s = v.progressSheet(pz.render.R.drawable.ob_ic_export, "Updating Sketchware AI")
            s.percent.text = if (failed) "41%" else "41%"
            s.bar.progress = 410
            if (failed) {
                s.status.text = "Connection reset by peer"
                s.status.setTextColor(v.color(pz.render.R.color.ob_error)); s.percent.setTextColor(v.color(pz.render.R.color.ob_error))
                s.buttons.addView(v.smallPill("Hide", false, true, null), LinearLayout.LayoutParams(0, v.dp(40), 1f))
                val r = v.smallPill("Retry", true, true, null)
                val rp = LinearLayout.LayoutParams(0, v.dp(40), 1f); rp.leftMargin = v.dp(8)
                s.buttons.addView(r, rp)
            } else {
                s.status.text = "41% • Downloading… 56.0 MB of 136.2 MB"
                s.buttons.addView(v.smallPill("Hide", false, true, null), LinearLayout.LayoutParams(0, v.dp(40), 1f))
            }
            return s.root
        }
        V210Shots.save(c, "v212_update.png", listOf(
                V210Shots.shot(c, sheet(c, forced), 393, 851),
                V210Shots.shot(c, sheet(c, optional), 393, 851),
                V210Shots.shot(c, sheet(c, progress(false)), 393, 600),
                V210Shots.shot(c, sheet(c, progress(true)), 393, 600)))
    }

    @Test fun signIn() {
        val c = paparazzi.context
        val act = com.besome.sketch.lib.base.BaseAppCompatActivity()
        act.attach(V211Shots.ctx(c), android.content.Intent())
        val v = GitHubViews(act)
        fun chrome(email: Boolean): View {
            val a = v.authChrome()
            a.code.text = "WDJB-MJHT"; a.code.visibility = View.VISIBLE
            a.status.text = if (email) "GitHub e-mailed you a code: copy it, then tap Paste & fill." else
                "Copy the code from your authenticator app, then tap Paste & fill."
            a.otp.visibility = View.VISIBLE
            a.otpTitle.text = if (email) "Email verification code" else "Two-factor code"
            a.openMail.visibility = if (email) View.VISIBLE else View.GONE
            (a.pasteFill.layoutParams as LinearLayout.LayoutParams).leftMargin = if (email) v.dp(8) else 0
            // GitHub's page stands in as a plain panel (WebView doesn't draw in layoutlib)
            val page = TextView(act); page.text = if (email) "github.com/sessions/verified-device\n\nDevice verification\n\n" +
                    "Device Verification Code  [ ______ ]\n\n[ Verify ]" else "github.com/sessions/two-factor/app\n\nTwo-factor " +
                    "authentication\n\nAuthentication code  [ ______ ]\n\n[ Verify ]"
            page.setTextColor(Color.parseColor("#C9D1D9")); page.textSize = 14f; page.setPadding(48, 48, 48, 48)
            a.page.addView(page)
            return a.root
        }
        V210Shots.save(c, "v212_signin.png", listOf(V210Shots.shot(c, chrome(true), 393, 851), V210Shots.shot(c, chrome(false), 393, 851)))
    }

    @Test fun settingsAndTiles() {
        val c = paparazzi.context
        V210Shots.sampleData()
        GitHubSession.PROFILE = null
        AppControl.CONFIG = AppControl.Config().apply { minVersionCode = 209; latestVersionCode = 209; latestVersionName = "v2.1.2" }
        SessionLog.LINE = "Signed out 21 Nov, 18:05: @bammidieswararao: the account signed in on another phone (Pixel 9 · v2.1.2)."
        val settings = V211Shots.open(c, SettingsActivity())
        SessionLog.LINE = null
        // the New Project / project settings tile (64dp glass, 48dp picture) with the app's letters
        val act = com.besome.sketch.lib.base.BaseAppCompatActivity()
        act.attach(V211Shots.ctx(c), android.content.Intent())
        val row = LinearLayout(act); row.orientation = LinearLayout.HORIZONTAL; row.setPadding(40, 40, 40, 40)
        row.setBackgroundColor(Color.parseColor("#0B0B0D"))
        for (name in listOf("NewProject11", "Sri Durga Varahi Parking Stand", "GitHub API Demo", "AI Keyboard")) {
            val col = LinearLayout(act); col.orientation = LinearLayout.VERTICAL; col.gravity = android.view.Gravity.CENTER_HORIZONTAL
            val tile = FrameLayout(act); tile.setBackgroundResource(pz.render.R.drawable.ob_avatar); tile.clipToOutline = true
            val img = ImageView(act); img.setImageDrawable(MonogramDrawable(act, name))
            tile.addView(img, FrameLayout.LayoutParams(V210Shots.dp(c, 48), V210Shots.dp(c, 48), android.view.Gravity.CENTER))
            col.addView(tile, LinearLayout.LayoutParams(V210Shots.dp(c, 64), V210Shots.dp(c, 64)))
            val label = TextView(act); label.text = name; label.textSize = 10f; label.setTextColor(Color.parseColor("#A1A1AA"))
            label.maxLines = 2; label.gravity = android.view.Gravity.CENTER
            col.addView(label, LinearLayout.LayoutParams(V210Shots.dp(c, 84), -2))
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        }
        V210Shots.save(c, "v212_settings_tiles.png", listOf(V210Shots.shot(c, settings, 393, 851),
                V210Shots.shot(c, row, 393, 140)))
    }
}

/** v2.1.2: the Play Store page and list at 360 dp, font scale 1.3 (hero rows, counters, slim Publish row). */
class V212LargeFontTest {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT, fontScale = 1.3f), theme = "android:Theme.Material.NoActionBar")

    @Test fun large() {
        val c = paparazzi.context
        V210Shots.save(c, "v212_large_store.png", listOf(V210Shots.shot(c, V211Shots.page(c), 360, 760),
                V210Shots.shot(c, V211Shots.list(c), 360, 760)))
    }
}
