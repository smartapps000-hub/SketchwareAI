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
import com.sketchware.ai.github.TokenStore
import org.junit.Rule
import org.junit.Test

/**
 * v2.1.5: the owner's 23 Nov review of v2.1.4 drawn from the release code: the app page's Follow pill (orange Follow,
 * glass Following kept from the phone, your own app's followers), the creator page (level on its own row, country from
 * GitHub's Location; your own page with this phone's country and "Show … on my store page"), a followers list, the store
 * list with "From creators you follow" first, the Publish page's country line, the sign-in window on GitHub Mobile's
 * page and after Paste & fill with nothing copied, and the JSON power pack's guide (every block in its shape). Writes
 * out/v215_*.png.
 */
class V215Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(c: Context, v: Int) = V210Shots.dp(c, v)

    private val viewer = "viewer-raj"

    private fun annProfile(followers: Int) = """{"login":"ann-dev","name":"Ann Devi","bio":"I make small, useful Android apps on my phone with Sketchware AI.","avatar_url":"https://avatars.githubusercontent.com/u/90210?v=4","followers":$followers,"public_repos":12,"location":"Hyderabad, Telangana","created_at":"2019-04-01T10:00:00Z"}"""

    /** Ann's Notes Pro page as [viewer] sees it; [following] = what the phone remembers for that account. */
    private fun appPage(c: Context, following: Boolean): View {
        val ctx = V211Shots.ctx(c)
        V211Shots.prefs.remove("playstore_follows")
        if (following) PlayStore.noteFollowing(ctx, viewer, "ann-dev", true)
        GitHubHttp.ANSWERS["/users/ann-dev"] = annProfile(314)
        // GitHub's answer: 404 = not following; "{}" = unknown (the phone's state stays)
        GitHubHttp.ANSWERS["/user/following/ann-dev"] = if (following) "{}" else ""
        return V211Shots.page(c, viewer)
    }

    @Test fun follow() {
        val c = paparazzi.context
        val saved = TokenStore.LOGIN
        TokenStore.LOGIN = viewer
        val notYet = appPage(c, false)
        val on = appPage(c, true)
        // your own app: your followers (a tap lists them)
        TokenStore.LOGIN = V211Shots.OWNER
        GitHubHttp.ANSWERS["/users/${V211Shots.OWNER}"] = """{"login":"${V211Shots.OWNER}","name":"Bammidi Eswara Rao","followers":27,"public_repos":9,"location":"","created_at":"2023-01-14T10:00:00Z"}"""
        val io = Intent(); io.putExtra("playstore_owner", V211Shots.OWNER); io.putExtra("playstore_repo", "SketchwareAI-Vehicle_Parking")
        val own = V211Shots.open(c, PlayStoreActivity(), io)
        TokenStore.LOGIN = saved
        V210Shots.save(c, "v215_follow.png", listOf(V210Shots.shot(c, notYet, 393, 851), V210Shots.shot(c, on, 393, 851),
                V210Shots.shot(c, own, 393, 851)))
        V211Shots.prefs.remove("playstore_follows")
    }

    @Test fun creator() {
        val c = paparazzi.context
        val saved = TokenStore.LOGIN
        TokenStore.LOGIN = viewer
        // ann-dev: since 2019, store apps, 314 followers: ★ Trusted; India from "Hyderabad, Telangana"
        V211Shots.developer(c)
        TokenStore.LOGIN = viewer
        GitHubHttp.ANSWERS["/users/ann-dev"] = annProfile(314)
        val i = Intent(); i.putExtra("playstore_developer", "ann-dev")
        val ann = V211Shots.open(c, PlayStoreActivity(), i)
        // your own page without a GitHub Location: this phone's country and the offer to show it
        val me = V211Shots.OWNER
        TokenStore.LOGIN = me
        GitHubHttp.ANSWERS["/users/$me"] = """{"login":"$me","name":"Bammidi Eswara Rao","bio":null,"followers":27,"public_repos":9,"location":null,"created_at":"2023-01-14T10:00:00Z"}"""
        GitHubHttp.ANSWERS[V211Shots.search("topic:sketchwareai-playstore user:$me")] = """{"items":[
            {"id":12,"name":"SketchwareAI-Vehicle_Parking","private":false,"owner":{"login":"$me"},"description":"Parking tickets.","stargazers_count":17,"pushed_at":"2026-11-19T10:00:00Z","default_branch":"main"},
            {"id":31,"name":"SketchwareAI-AnuFontsLabStudio","private":false,"owner":{"login":"$me"},"description":"Telugu fonts.","stargazers_count":4,"pushed_at":"2026-11-18T10:00:00Z","default_branch":"main"},
            {"id":32,"name":"SketchwareAI-GitHub_API_Demo","private":false,"owner":{"login":"$me"},"description":"GitHub blocks.","stargazers_count":2,"pushed_at":"2026-11-12T10:00:00Z","default_branch":"main"},
            {"id":33,"name":"SketchwareAI-Calculator","private":false,"owner":{"login":"$me"},"description":"A calculator.","stargazers_count":1,"pushed_at":"2026-11-02T10:00:00Z","default_branch":"main"}]}"""
        GitHubHttp.ANSWERS[V211Shots.search("topic:sketchware-ai user:$me")] = """{"items":[]}"""
        val oi = Intent(); oi.putExtra("playstore_developer", me)
        val own = V211Shots.open(c, PlayStoreActivity(), oi)
        // Ann's followers (one of them followed by the viewer)
        TokenStore.LOGIN = viewer
        V211Shots.prefs.remove("playstore_follows")
        PlayStore.noteFollowing(V211Shots.ctx(c), viewer, "ravi-k", true)
        // made-up accounts with drawn pictures (seeded: nothing is downloaded)
        val people = listOf("ravi-k" to "#FFB347", "zed-apps" to "#7CC4FF", "bhanu-telugu-apps-and-fonts" to "#FF8A3D",
                "cy" to "#9BE37A", "meena" to "#F28BD4")
        val rows = people.mapIndexed { n, (login, color) ->
            val url = "https://avatars.githubusercontent.com/u/9${n}0215?v=4"
            V211Shots.seed(c, url, V211Shots.icon("person$n", color, login.substring(0, 1).uppercase()))
            """{"login":"$login","avatar_url":"$url"}"""
        }
        GitHubHttp.ANSWERS["/users/ann-dev/followers?per_page=100&page=1"] = rows.joinToString(",", "[", "]")
        val pi = Intent(); pi.putExtra("playstore_people", "ann-dev"); pi.putExtra("playstore_people_followers", true)
        val list = V211Shots.open(c, PlayStoreActivity(), pi)
        TokenStore.LOGIN = saved
        V210Shots.save(c, "v215_creator.png", listOf(V210Shots.shot(c, ann, 393, 851), V210Shots.shot(c, own, 393, 851),
                V210Shots.shot(c, list, 393, 851)))
        V211Shots.prefs.remove("playstore_follows")
    }

    @Test fun store() {
        val c = paparazzi.context
        val saved = TokenStore.LOGIN
        TokenStore.LOGIN = viewer
        V211Shots.storeData(c)
        TokenStore.LOGIN = viewer
        V211Shots.prefs.remove("playstore_follows")
        PlayStore.saveFollowing(V211Shots.ctx(c), viewer, listOf("ann-dev", "ravi-k"))
        GitHubHttp.ANSWERS["/user/following?per_page=100&page=1"] = """[{"login":"ann-dev"},{"login":"ravi-k"}]"""
        val list = V211Shots.open(c, PlayStoreActivity(), Intent())
        TokenStore.LOGIN = saved
        V210Shots.save(c, "v215_store.png", listOf(V210Shots.shot(c, list, 393, 851)))
        V211Shots.prefs.remove("playstore_follows")
    }

    @Test fun publish() {
        val c = paparazzi.context
        V211Shots.storeData(c)
        val files = V210Shots.files(c)
        val staging = PlayStore.stagingDir(files, 103)
        staging.deleteRecursively(); staging.mkdirs()
        val form = PlayStore.Listing(); form.title = "Vehicle Parking"
        form.description = "Parking tickets, slots and a daily report for a parking stand."; form.video = ""
        form.saveForm(staging)
        V211Shots.ctx(c).getSharedPreferences("playstore_publish", 0).edit().putLong("chosen_${V211Shots.OWNER}", 103).commit()
        val i = Intent(); i.putExtra("playstore_publish", true)
        val view = V211Shots.open(c, PlayStoreActivity(), i)
        V210Shots.save(c, "v215_publish.png", listOf(V210Shots.shot(c, view, 393, 851)))
    }

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
            t.textSize = if (kind == 1) 22f else if (kind == 5) 40f else 14f
            if (kind == 3) {
                t.setTextColor(Color.parseColor("#F0F6FC"))
                t.setBackgroundColor(Color.parseColor("#151B23"))
                t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10))
            }
            if (kind == 5) {
                t.setTextColor(Color.parseColor("#F0F6FC"))
                t.gravity = Gravity.CENTER
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

    /** The window as otpBar / pasteAndFill leave it: "mobile" (GitHub Mobile's page) or "nothing" (Paste & fill first). */
    private fun signin(c: Context, state: String): View {
        val v = GitHubViews(c)
        val a = v.authChrome()
        a.code.text = "4101-C456"
        val mobile = state == "mobile"
        a.otp.visibility = View.VISIBLE
        a.otpTitle.text = if (mobile) "Approve in the GitHub Mobile app" else "Email verification code"
        a.otpNote.text = if (mobile) "GitHub sent this request to its own GitHub Mobile app only: it can't be answered here. " +
                "No GitHub Mobile? E-mail me a code." else ""
        a.otpNote.visibility = if (mobile) View.VISIBLE else View.GONE
        a.sendMail.visibility = if (mobile) View.VISIBLE else View.GONE
        a.openMail.text = if (mobile) "GitHub Mobile" else "Open Gmail"
        a.openMail.visibility = View.VISIBLE
        a.pasteFill.visibility = if (mobile) View.GONE else View.VISIBLE
        gap(c, a.openMail, mobile)
        gap(c, a.pasteFill, true)
        a.otpAuto.visibility = View.GONE
        a.status.text = if (mobile) "Open GitHub Mobile and tap the number this page shows, or tap E-mail me a code."
        else "Nothing copied yet. Pull down your notifications and tap Copy code on GitHub's e-mail (or open Gmail and " +
                "copy it): it goes in by itself."
        val page = if (mobile) githubPage(c, listOf("Two-factor authentication" to 1,
                "We sent you a verification request on your GitHub Mobile app. Enter the digits shown below to verify." to 0,
                "77" to 5, "Having problems?" to 0, "Use your authenticator app" to 2, "Send a code via email" to 2))
        else githubPage(c, listOf("Device verification" to 1,
                "Email: We just sent your authentication code via email to b***@gmail.com." to 0,
                "Device Verification Code" to 0, "XXXXXX" to 3, "Verify" to 2))
        a.page.addView(page, FrameLayout.LayoutParams(-1, -1))
        val frame = FrameLayout(c)
        frame.setBackgroundColor(Color.parseColor("#0B0B0D"))
        frame.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 14))
        frame.addView(a.root, FrameLayout.LayoutParams(-1, -1))
        return frame
    }

    @Test fun signin() {
        val c = paparazzi.context
        V210Shots.save(c, "v215_signin.png", listOf("mobile", "nothing").map { V210Shots.shot(c, signin(c, it), 393, 851) })
    }

    @Test fun jsonGuide() {
        val c = paparazzi.context
        // the sheet scrolls in the app; here three blocks a picture: readers, checks, setters, lists and maps
        val groups = listOf(arrayOf("sketchJsonGetText", "sketchJsonGetBoolean", "sketchJsonCount"),
                arrayOf("sketchJsonHas", "sketchJsonIsValid", "sketchJsonPretty"),
                arrayOf("sketchJsonSetText", "sketchJsonSetJson", "sketchJsonRemove"),
                arrayOf("sketchJsonKeys", "sketchJsonToMapList", "sketchJsonToMap"))
        V210Shots.save(c, "v215_json_guide.png", groups.map {
            V210Shots.shot(c, com.sketchware.ai.ui.BlockGuide.content(c, "JSON power pack", *it), 393, 851, wrap = true)
        })
    }
}
