package pz

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.github.GitHubHttp
import com.sketchware.ai.github.GitHubViews
import com.sketchware.ai.github.PlayStore
import com.sketchware.ai.github.PlayStoreActivity
import org.junit.Rule
import org.junit.Test

/**
 * v2.1.4: the owner's 22 Nov review of v2.1.3 drawn from the release code: the sign-in window's code bar on GitHub's
 * "Confirm access" page (E-mail me a code) and on the e-mailed code page ("Code sent to b***@gmail.com", notification
 * access off / on; the bar's states set as GitHubInAppAuthDialog.otpBar sets them: the dialog's WebView can't run in
 * layoutlib, so GitHub's page is a drawn stand-in), the Publish page with the chosen project as one row (Change) and steps
 * 2-3 right under it, the description written from the project, the store list after a publish's Done (the app first and
 * lit, "Just published"), the app page with Follow · followers and the creator's mark, your own app's followers pill,
 * and the creator page (level, country, Follow). Writes out/v214_*.png.
 */
class V214Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(c: Context, v: Int) = V210Shots.dp(c, v)

    /** GitHub's page in the window (a drawn stand-in: GitHub's dark page with its words). */
    private fun githubPage(c: Context, lines: List<Pair<String, Int>>): View {
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Color.parseColor("#0D1117"))
        page.setPadding(dp(c, 20), dp(c, 24), dp(c, 20), dp(c, 20))
        for ((text, kind) in lines) {
            val t = TextView(c)
            t.text = text
            t.setTextColor(Color.parseColor(if (kind == 2) "#4493F8" else if (kind == 1) "#F0F6FC" else "#9198A1"))
            t.textSize = if (kind == 1) 22f else 14f
            if (kind == 3) {
                t.setTextColor(Color.parseColor("#F0F6FC"))
                t.setBackgroundColor(Color.parseColor("#151B23"))
                t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10))
            }
            if (kind == 4) {
                t.setTextColor(Color.WHITE)
                t.gravity = Gravity.CENTER
                t.setBackgroundColor(Color.parseColor("#238636"))
                t.setPadding(0, dp(c, 10), 0, dp(c, 10))
            }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(c, if (kind == 1) 0 else 12)
            page.addView(t, lp)
        }
        return page
    }

    private fun gap(c: Context, pill: View, after: Boolean) {
        val lp = pill.layoutParams as LinearLayout.LayoutParams
        lp.leftMargin = if (after) dp(c, 8) else 0
        pill.layoutParams = lp
    }

    /** The window as otpBar leaves it for [state]: "send", "code" (notification access off), "auto" (on), "filled". */
    private fun signin(c: Context, state: String): View {
        val v = GitHubViews(c)
        val a = v.authChrome()
        a.code.text = "8F2C-41D7"
        val send = state == "send"
        a.otp.visibility = View.VISIBLE
        a.otpTitle.text = if (send) "Confirm access with an e-mailed code" else "Code sent to b***@gmail.com"
        a.otpNote.text = if (send) "GitHub sends a code to your account's e-mail." else ""
        a.otpNote.visibility = if (send) View.VISIBLE else View.GONE
        a.sendMail.visibility = if (send) View.VISIBLE else View.GONE
        a.openMail.visibility = View.VISIBLE
        a.pasteFill.visibility = if (send) View.GONE else View.VISIBLE
        gap(c, a.openMail, send)
        gap(c, a.pasteFill, true)
        val on = state == "auto" || state == "filled"
        a.otpAuto.text = if (on) "✓ Codes in GitHub's e-mail go in by themselves (notification access)"
        else "⚡ Fill codes from e-mail notifications · Turn on ›"
        a.otpAuto.setTextColor(v.color(if (on) pz.render.R.color.ob_text_2 else pz.render.R.color.ob_accent))
        a.otpAuto.visibility = View.VISIBLE
        a.status.text = when (state) {
            "send" -> "Tap E-mail me a code (GitHub's \"Send a code via email\"), or choose another way on GitHub's page."
            "code" -> "Open Gmail and copy GitHub's code: it goes in by itself when you come back (or tap Paste & fill)."
            "auto" -> "GitHub's code goes in by itself when its e-mail arrives (or copy it and tap Paste & fill)."
            else -> "Code 81234567 from Gmail filled in: GitHub is checking it…"
        }
        val page = if (send) githubPage(c, listOf("Confirm access" to 1, "Signed in as @bammidieswararao" to 0,
                "Use your passkey" to 3, "GitHub Mobile" to 3, "Having problems?" to 0, "Send a code via email" to 2))
        else githubPage(c, listOf("Confirm access" to 1, "Enter the verification code sent to b***@gmail.com" to 0,
                (if (state == "filled") "81234567" else "XXXXXXXX") to 3, "Verify" to 4, "Resend code" to 2))
        a.page.addView(page, FrameLayout.LayoutParams(-1, -1))
        val frame = FrameLayout(c)
        frame.setBackgroundColor(Color.parseColor("#0B0B0D"))
        frame.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 14))
        frame.addView(a.root, FrameLayout.LayoutParams(-1, -1))
        return frame
    }

    @Test fun signin() {
        val c = paparazzi.context
        V210Shots.save(c, "v214_signin.png", listOf("send", "code", "auto", "filled").map {
            V210Shots.shot(c, signin(c, it), 393, 851)
        })
    }

    /** The Publish page: the chosen project as one row (Change), its description written from the project. */
    private fun publish(c: Context, emptyDescription: Boolean, change: Boolean): View {
        V211Shots.storeData(c)
        val files = V210Shots.files(c)
        val staging = PlayStore.stagingDir(files, 103)
        staging.deleteRecursively(); staging.mkdirs()
        val form = PlayStore.Listing(); form.title = "Vehicle Parking"
        form.description = if (emptyDescription) "" else "Quick notes with folders and a widget."; form.video = ""
        form.saveForm(staging)
        V211Shots.ctx(c).getSharedPreferences("playstore_publish", 0).edit().putLong("chosen_${V211Shots.OWNER}", 103).commit()
        // the phone's project linked to the chosen repository (by name), so its description can be written: two
        // screens (Main with a side menu), its buttons, a list, a text field, camera, internet and saved settings
        val chosen = com.sketchware.ai.github.RepoCache.REPOS?.firstOrNull { it.number == 103L }
        com.sketchware.ai.github.ProjectLinks.REPO_NAME = chosen?.name
        a.a.a.lC.PROJECT["my_ws_name"] = "Vehicle_Parking"; a.a.a.lC.PROJECT["my_app_name"] = "Vehicle Parking"
        a.a.a.hC.SCREENS.clear()
        a.a.a.hC.SCREENS.add(com.besome.sketch.beans.ProjectFileBean("MainActivity.java", "main").also { it.options = 1 or 4 })
        a.a.a.hC.SCREENS.add(com.besome.sketch.beans.ProjectFileBean("ParkingHistoryActivity.java", "parking_history"))
        fun button(id: String, words: String) = com.besome.sketch.beans.ViewBean(id, 3).also {
            it.text = com.besome.sketch.beans.TextBean().also { t -> t.text = words } }
        a.a.a.eC.C["main"] = arrayListOf(button("park", "Park vehicle"), button("exit", "Exit"), button("b3", "Button"),
                com.besome.sketch.beans.ViewBean("plate", 5), com.besome.sketch.beans.ViewBean("slots", 9))
        a.a.a.eC.C["parking_history"] = arrayListOf(button("clear", "Clear history"), com.besome.sketch.beans.ViewBean("list", 9))
        a.a.a.eC.H["MainActivity.java"] = arrayListOf(com.besome.sketch.beans.ComponentBean(15, "camera"),
                com.besome.sketch.beans.ComponentBean(17, "net"), com.besome.sketch.beans.ComponentBean(2, "saved"))
        val act = PlayStoreActivity()
        val i = Intent(); i.putExtra("playstore_publish", true)
        val view = V211Shots.open(c, act, i)
        if (change) {
            val f = PlayStoreActivity::class.java.getDeclaredField("changingProject"); f.isAccessible = true; f.set(act, true)
            val repos = PlayStoreActivity::class.java.getDeclaredField("lastRepos"); repos.isAccessible = true
            val m = PlayStoreActivity::class.java.getDeclaredMethod("showRepos", java.util.List::class.java); m.isAccessible = true
            m.invoke(act, repos.get(act))
        }
        V210Shots.drain()
        com.sketchware.ai.github.ProjectLinks.REPO_NAME = null
        a.a.a.hC.SCREENS.clear(); a.a.a.eC.C.clear(); a.a.a.eC.H.clear()
        return view
    }

    @Test fun publish() {
        val c = paparazzi.context
        V210Shots.save(c, "v214_publish.png", listOf(
                V210Shots.shot(c, publish(c, true, false), 393, 851, wrap = true),
                V210Shots.shot(c, publish(c, false, true), 393, 851)))
    }

    @Test fun store() {
        val c = paparazzi.context
        // the store list after a publish's Done: the app first and lit, "Just published" (also before GitHub's search has it)
        V211Shots.storeData(c)
        val ctx = V211Shots.ctx(c)
        PlayStore.notePublished(ctx, V211Shots.OWNER, "SketchwareAI-Vehicle_Parking", 12, "Vehicle Parking",
                "Parking tickets, slots and a daily report for a parking stand.")
        val hl = PlayStore::class.java.getDeclaredMethod("highlight", String::class.java, String::class.java)
        hl.isAccessible = true
        hl.invoke(null, V211Shots.OWNER, "SketchwareAI-Vehicle_Parking")
        val list = V211Shots.open(c, PlayStoreActivity(), Intent())
        // an app page: Follow with the creator's followers, the creator's mark (✔ Verified)
        GitHubHttp.ANSWERS["/users/ann-dev"] = """{"login":"ann-dev","name":"Ann Devi","bio":"I make small, useful Android apps.","followers":314,"public_repos":12,"location":"Hyderabad, Telangana","created_at":"2019-04-01T10:00:00Z"}"""
        val page = V211Shots.page(c)
        // your own app: your followers (a tap opens your page)
        GitHubHttp.ANSWERS["/users/${V211Shots.OWNER}"] = """{"login":"${V211Shots.OWNER}","name":"Bammidi Eswara Rao","followers":27,"public_repos":9,"location":"","created_at":"2025-06-01T10:00:00Z"}"""
        val own = PlayStoreActivity()
        val io = Intent(); io.putExtra("playstore_owner", V211Shots.OWNER); io.putExtra("playstore_repo", "SketchwareAI-Vehicle_Parking")
        val saved = com.sketchware.ai.github.TokenStore.LOGIN
        com.sketchware.ai.github.TokenStore.LOGIN = V211Shots.OWNER
        val ownPage = V211Shots.open(c, own, io)
        com.sketchware.ai.github.TokenStore.LOGIN = saved
        V210Shots.save(c, "v214_store.png", listOf(V210Shots.shot(c, list, 393, 851), V210Shots.shot(c, page, 393, 851),
                V210Shots.shot(c, ownPage, 393, 851)))
        V211Shots.prefs.remove("playstore_recent")
    }

    /** The creator page with GitHub's profile answer [user] (the store answers from V211Shots.developer). */
    private fun creatorPage(c: Context, user: String?): View {
        val seeded = V211Shots.developer(c)
        if (user == null) return seeded
        GitHubHttp.ANSWERS["/users/ann-dev"] = user
        val i = Intent(); i.putExtra("playstore_developer", "ann-dev")
        return V211Shots.open(c, PlayStoreActivity(), i)
    }

    @Test fun creator() {
        val c = paparazzi.context
        // ann-dev: on GitHub since 2019, two store apps, 314 followers: ★ Trusted; India from "Hyderabad, Telangana"
        val trusted = creatorPage(c, """{"login":"ann-dev","name":"Ann Devi","bio":"I make small, useful Android apps on my phone with Sketchware AI.","avatar_url":"https://avatars.githubusercontent.com/u/90210?v=4","followers":314,"public_repos":12,"location":"Hyderabad, Telangana","created_at":"2019-04-01T10:00:00Z"}""")
        // 60 days on GitHub, a name, apps: ✔ Verified; "Berlin, Germany"
        val verified = creatorPage(c, """{"login":"ann-dev","name":"Ann Devi","bio":null,"avatar_url":"https://avatars.githubusercontent.com/u/90210?v=4","followers":3,"public_repos":4,"location":"Berlin, Germany","created_at":"2026-09-20T10:00:00Z"}""")
        // a new account without a name or Location: Not verified, "Country not shared"
        val newcomer = creatorPage(c, """{"login":"ann-dev","name":null,"bio":null,"followers":2,"public_repos":1,"location":null,"created_at":"2026-11-10T10:00:00Z"}""")
        V210Shots.save(c, "v214_creator.png", listOf(V210Shots.shot(c, trusted, 393, 851),
                V210Shots.shot(c, verified, 393, 851), V210Shots.shot(c, newcomer, 393, 851)))
    }
}
