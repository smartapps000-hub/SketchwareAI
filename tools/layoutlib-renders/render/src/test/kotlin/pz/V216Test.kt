package pz

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.github.GitHubViews
import com.sketchware.ai.github.PlayStore
import com.sketchware.ai.project.Demos
import org.junit.Rule
import org.junit.Test

/** v2.1.6: Settings' follow-notification switch, the Demo tab (GitHub + JSON Live Demo first), the 8-digit code note. */
object V216Shots {
    /** Restore projects → Demo: the three GitHub API Demo versions, with or without the GitHub + JSON Live Demo. */
    fun demos(c: Context, withJson: Boolean): View {
        V210Shots.sampleData()
        Demos.KINDS.clear()
        if (withJson) {
            a.a.a.lC.LIST.add(0, hashMapOf("sc_id" to "670", "my_app_name" to "GitHub + JSON Live Demo",
                    "sc_ver_name" to "1.0", "sc_ver_code" to "1", "demo" to "1"))
            Demos.KINDS["670"] = "githubjson"
        }
        return V210Shots.restore(c, 1)
    }

    /** The sign-in window on GitHub's 8-digit e-mailed code page, as otpBar / pasteAndFill leave it. */
    fun signin(c: Context, afterSixDigits: Boolean): View {
        val v = GitHubViews(c)
        val a = v.authChrome()
        a.code.text = "4101-C456"
        a.otp.visibility = View.VISIBLE
        a.otpTitle.text = "Code sent to b***@gmail.com"
        // GitHubInAppAuthDialog.otpBar: the field's own length, said before anything is pasted
        a.otpNote.text = "This page takes an 8-digit code: copy the 8-digit code from Gmail, then tap Paste & fill."
        a.otpNote.visibility = View.VISIBLE
        a.sendMail.visibility = View.GONE
        a.openMail.text = "Open Gmail"
        a.openMail.visibility = View.VISIBLE
        a.pasteFill.visibility = View.VISIBLE
        val lp = a.pasteFill.layoutParams as LinearLayout.LayoutParams
        lp.leftMargin = V210Shots.dp(c, 8)
        a.pasteFill.layoutParams = lp
        a.otpAuto.visibility = View.GONE
        // GitHubInAppAuthDialog.lengthHint after a 6-digit code was copied (nothing typed into GitHub's field)
        a.status.text = if (afterSixDigits) "This GitHub page needs an 8-digit code (the copied one has 6 digits). Copy the " +
                "8-digit code from Gmail, then tap Paste & fill." else "Waiting for the code…"
        if (afterSixDigits) a.status.setTextColor(Color.parseColor("#FF8A65"))
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Color.parseColor("#0D1117"))
        page.setPadding(V210Shots.dp(c, 20), V210Shots.dp(c, 24), V210Shots.dp(c, 20), V210Shots.dp(c, 20))
        for ((text, big) in listOf("Device verification" to true,
                "Enter the verification code sent to b***@gmail.com" to false, "XXXXXXXX" to false, "Verify" to false)) {
            val t = TextView(c)
            t.text = text
            t.textSize = if (big) 22f else 14f
            t.setTextColor(Color.parseColor(if (big || text == "XXXXXXXX") "#F0F6FC" else if (text == "Verify") "#4493F8" else "#9198A1"))
            if (text == "XXXXXXXX") {
                t.setBackgroundColor(Color.parseColor("#151B23"))
                t.setPadding(V210Shots.dp(c, 12), V210Shots.dp(c, 10), V210Shots.dp(c, 12), V210Shots.dp(c, 10))
            }
            val p = LinearLayout.LayoutParams(-1, -2)
            p.topMargin = V210Shots.dp(c, if (big) 0 else 12)
            page.addView(t, p)
        }
        a.page.addView(page, FrameLayout.LayoutParams(-1, -1))
        val frame = FrameLayout(c)
        frame.setBackgroundColor(Color.parseColor("#0B0B0D"))
        frame.setPadding(V210Shots.dp(c, 14), V210Shots.dp(c, 10), V210Shots.dp(c, 14), V210Shots.dp(c, 14))
        frame.addView(a.root, FrameLayout.LayoutParams(-1, -1))
        return frame
    }
}

class V216Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    @Test fun settings() {
        val c = paparazzi.context
        V210Shots.sampleData()
        // the render's shared preferences (layoutlib's own keep nothing between reads); ticked = notices really come
        PlayStore.setNoticesSwitch(V211Shots.ctx(c), V210Shots.OWNER, true)
        val on = V211Shots.open(c, com.sketchware.ai.ui.SettingsActivity())
        PlayStore.setNoticesSwitch(V211Shots.ctx(c), V210Shots.OWNER, false)
        val off = V211Shots.open(c, com.sketchware.ai.ui.SettingsActivity())
        PlayStore.setNoticesSwitch(V211Shots.ctx(c), V210Shots.OWNER, true)
        V210Shots.save(c, "v216_settings.png", listOf(V210Shots.shot(c, on, 393, 851), V210Shots.shot(c, off, 393, 851)))
    }

    @Test fun demos() {
        val c = paparazzi.context
        val both = V216Shots.demos(c, true)
        val missing = V216Shots.demos(c, false)
        V210Shots.save(c, "v216_demo.png", listOf(V210Shots.shot(c, both, 393, 851), V210Shots.shot(c, missing, 393, 851)))
        Demos.KINDS.clear()
    }

    @Test fun signin() {
        val c = paparazzi.context
        V210Shots.save(c, "v216_signin.png", listOf(V210Shots.shot(c, V216Shots.signin(c, false), 393, 851),
                V210Shots.shot(c, V216Shots.signin(c, true), 393, 851)))
    }
}

/** v2.1.6: the same at 360 dp, font scale 1.3 (the Make pills, the switch's text, the code note). */
class V216LargeFontTest {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT, fontScale = 1.3f), theme = "android:Theme.Material.NoActionBar")

    @Test fun large() {
        val c = paparazzi.context
        V210Shots.sampleData()
        PlayStore.setNoticesSwitch(V211Shots.ctx(c), V210Shots.OWNER, true)
        val settings = V211Shots.open(c, com.sketchware.ai.ui.SettingsActivity())
        val missing = V216Shots.demos(c, false)
        V210Shots.save(c, "v216_large.png", listOf(V210Shots.shot(c, settings, 360, 760), V210Shots.shot(c, missing, 360, 760),
                V210Shots.shot(c, V216Shots.signin(c, true), 360, 760)))
        Demos.KINDS.clear()
    }
}
