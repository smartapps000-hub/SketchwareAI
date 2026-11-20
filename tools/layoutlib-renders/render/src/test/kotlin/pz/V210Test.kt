package pz

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.sketchware.ai.github.AppControl
import com.sketchware.ai.github.CommunityActivity
import com.sketchware.ai.github.GitHubBackupsActivity
import com.sketchware.ai.github.GitHubHttp
import com.sketchware.ai.github.GitHubImportDialog
import com.sketchware.ai.github.GitHubSession
import com.sketchware.ai.github.PlayStore
import com.sketchware.ai.github.PlayStoreActivity
import com.sketchware.ai.github.ProjectRepo
import com.sketchware.ai.github.RepoCache
import com.sketchware.ai.github.RepoViewerActivity
import com.sketchware.ai.github.TokenStore
import com.sketchware.ai.ui.HoneyDropBar
import com.sketchware.ai.ui.SettingsActivity
import org.junit.Rule
import org.junit.Test
import pz.render.R

/**
 * v2.1.0: the owner's 16 Nov directive ("Honey-Drop bottom bar, full-screen tabs, Play Store, Community") and his v2.0.5
 * phone report. Home without drawer and floating button, the bar at four moments of a tap (settled on Home; the drop
 * falling toward GitHub; landing with the waves; settled on Play Store); Restore projects with its three tabs (GitHub
 * with compact cards, Demo numbered #1 #2 #3, On this phone); Settings (an update offered with a Coming soon badge;
 * a refused sign-in with its two-line state); the Play Store list, an app's page (pictures, Get it, reviews) and
 * Publish; Community (discussions, one discussion with replies, signed out, Discussions off); the repository viewer
 * with its saved page and the 10-second timeout line. Real screen classes on the stand-ins of setup_v210.sh.
 * Writes out/v210_*.png (V210LargeFontTest: out/v210_large_*.png at font scale 1.3).
 */
object V210Shots {
    val out = java.io.File("/tmp/claude-0/-home-user-SketchwareAI/7396650f-8b3a-5447-a67d-6edecd23d850/scratchpad/pz/out")
    const val OWNER = "bammidieswararao"
    const val DAY = 86_400_000L
    const val NOW = 1_795_100_000_000L

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    /** The app's files and cache folders in out/ (layoutlib's context has none). */
    fun files(c: Context): Context = object : ContextWrapper(c) {
        override fun getFilesDir() = java.io.File(out, "files").also { it.mkdirs() }
        override fun getCacheDir() = java.io.File(out, "cache").also { it.mkdirs() }
        override fun getApplicationContext(): Context = this
    }

    fun shot(c: Context, view: View, widthDp: Int, heightDp: Int, wrap: Boolean = false, before: (() -> Unit)? = null): Bitmap {
        val w = dp(c, widthDp); var h = dp(c, heightDp)
        if (wrap) {
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            h = maxOf(h, view.measuredHeight)
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        before?.invoke()
        view.jumpDrawablesToCurrentState()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.parseColor("#0B0B0D"))
        view.draw(Canvas(bmp))
        return bmp
    }

    fun save(c: Context, name: String, shots: List<Bitmap>) {
        out.mkdirs()
        val gap = dp(c, 12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots.maxOf { it.height } + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (s in shots) { canvas.drawBitmap(s, x.toFloat(), gap.toFloat(), null); x += s.width + gap }
        java.io.FileOutputStream(java.io.File(out, name)).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun drain() {
        repeat(4) {
            Thread.sleep(500)
            while (BaseAppCompatActivity.QUEUE.isNotEmpty()) BaseAppCompatActivity.QUEUE.removeAt(0).run()
        }
    }

    fun <A : BaseAppCompatActivity> open(c: Context, act: A, intent: Intent = Intent()): View {
        act.attach(files(c), intent)
        act.onCreate(null)
        drain()
        return act.root()
    }

    private fun info(name: String, number: Long, private: Boolean, updated: Long) = ProjectRepo.Info().apply {
        this.name = name; this.number = number; isPrivate = private; this.updated = updated; owner = OWNER
    }

    fun version(id: Long, vn: String, vc: String, time: Long, standard: Boolean = false, apk: Boolean = false) = ProjectRepo.Version().apply {
        this.id = id; versionName = vn; versionCode = vc; tag = "v$vn-$vc"; this.time = time; this.standard = standard; size = 2_400_000
        swb = "Project_v$vn-$vc.swb"
        if (apk) { this.apk = "Project_v$vn-$vc.apk"; assets.add(ProjectRepo.Asset().also { it.name = "Project_v$vn-$vc.apk"; it.size = 9_800_000 }) }
    }

    fun sampleData() {
        TokenStore.LOGIN = OWNER
        GitHubSession.PROFILE = GitHubSession.Profile().apply {
            login = OWNER; name = "Bammidi Eswara Rao"; email = "bammidieswararao@gmail.com"; repositories = 14; checkedAt = NOW
        }
        RepoCache.REPOS = listOf(info("SketchwareAI-Vehicle_Parking", 101, true, NOW), info("SketchwareAI-GitHub_API_Demo-2", 102, false, NOW - DAY),
                info("SketchwareAI-Notes", 103, false, NOW - 3 * DAY))
        RepoCache.VERSIONS[101L] = listOf(version(1, "4.7.6", "84", NOW, apk = true), version(2, "4.7.5", "83", NOW - DAY),
                version(3, "4.7.4", "82", NOW - 2 * DAY), version(4, "4.7.0", "80", NOW - 9 * DAY, true), version(5, "4.6.9", "79", NOW - 12 * DAY))
        RepoCache.VERSIONS[102L] = listOf(version(6, "1.0", "1", NOW - DAY))
        RepoCache.VERSIONS[103L] = listOf(version(7, "2.3", "9", NOW - 3 * DAY, apk = true), version(8, "2.2", "8", NOW - 5 * DAY))
        GitHubImportDialog.OLDER = listOf()
        val dir = java.io.File(out, "swb-sample"); dir.mkdirs()
        mod.hey.studios.project.backup.BackupFactory.DIR = dir.absolutePath
        java.io.File(System.getProperty("java.io.tmpdir"), "render-storage/.sketchware/data").mkdirs()
        a.a.a.lC.LIST.clear()
        for ((id, v) in listOf("651" to 3, "612" to 2, "604" to 1)) {
            a.a.a.lC.LIST.add(hashMapOf("sc_id" to id, "my_app_name" to "GitHub API Demo", "sc_ver_name" to "$v.0", "sc_ver_code" to "$v", "demo" to "$v"))
        }
        a.a.a.lC.LIST.add(hashMapOf("sc_id" to "663", "my_app_name" to "Vehicle Parking", "sc_ver_name" to "4.7.6", "sc_ver_code" to "84"))
    }

    // ------------------------------------------------------------------ Home and the bar

    private fun card(c: Context, name: String, id: String, pkg: String, isPublic: Boolean): View {
        val card = LayoutInflater.from(c).inflate(R.layout.myprojects_item, null, false)
        card.findViewById<TextView>(R.id.app_name).text = name.replace(' ', '_')
        card.findViewById<TextView>(R.id.project_name).text = name
        card.findViewById<TextView>(R.id.package_name).text = pkg
        card.findViewById<TextView>(R.id.ob_monogram).text = name.take(2).uppercase()
        val tid = card.findViewById<TextView>(R.id.tv_published)
        tid.text = "#$id"; tid.visibility = View.VISIBLE
        card.findViewById<View>(R.id.ob_target_pill).visibility = View.GONE
        card.findViewById<View>(R.id.ob_github_pills).visibility = View.VISIBLE
        card.findViewById<TextView>(R.id.ob_visibility_text).text = if (isPublic) "Public" else "Private"
        card.findViewById<ImageView>(R.id.ob_visibility_icon).setImageResource(if (isPublic) R.drawable.ob_ic_public else R.drawable.ob_ic_lock)
        return card
    }

    /** main.xml as it is; three project cards where the pager shows the projects list. */
    fun home(c: Context): ViewGroup {
        val themed = ContextThemeWrapper(c, com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar)
        val root = LayoutInflater.from(themed).inflate(R.layout.main, null) as ViewGroup
        val pager = root.findViewById<View>(R.id.viewpager)
        val at = root.indexOfChild(pager)
        val lp = pager.layoutParams
        root.removeView(pager)
        val list = LinearLayout(themed)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(c, 8), dp(c, 84), dp(c, 8), 0)
        for ((n, i, p, pub) in listOf(Quad("Vehicle Parking", "663", "com.my.vehicleparking · 4.7.6 (84)", false),
                Quad("GitHub API Demo", "651", "com.my.githubdemo · 3.0 (3)", true),
                Quad("Sri Durga Varahi Parking Stand", "644", "com.my.varahi · 1.2 (5)", true),
                Quad("Notes", "623", "com.my.notes · 2.3 (9)", false))) {
            list.addView(card(themed, n, i, p, pub), LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(list, at, lp)
        return root
    }

    data class Quad(val a: String, val b: String, val c: String, val d: Boolean)

    fun bar(root: View): HoneyDropBar = root.findViewById(R.id.ob_honey_bar)

    // ------------------------------------------------------------------ screens

    fun restore(c: Context, tab: Int): View {
        val i = Intent(); i.putExtra(GitHubBackupsActivity.EXTRA_TAB, tab)
        return open(c, GitHubBackupsActivity(), i)
    }

    fun settings(c: Context, refused: Boolean, newer: Boolean): View {
        GitHubSession.PROFILE!!.problem = if (refused) "GitHub didn't accept the saved sign-in (HTTP 401: Bad credentials). " +
                "This sign-in: made 14 Nov 2026 with v2.0.4 (sign-in 3 on this phone), last accepted 16 Nov 2026." else null
        AppControl.CONFIG = AppControl.Config().apply {
            minVersionCode = 207
            updateUrl = "https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v2.1.1.apk"
            latestVersionCode = if (newer) 208 else 207
            latestVersionName = if (newer) "v2.1.1" else "v2.1.0"
            comingSoon = if (newer) "Wear OS and TV previews in the design editor" else ""
        }
        val v = open(c, SettingsActivity())
        GitHubSession.PROFILE!!.problem = null
        return v
    }

    private const val SEARCH = "/search/repositories?q=topic:sketchwareai-playstore&sort=updated&order=desc&per_page=50"

    fun storeAnswers() {
        GitHubHttp.ANSWERS[SEARCH] = """{"items":[
            {"id":11,"name":"SketchwareAI-Notes","private":false,"owner":{"login":"ann-dev"},"description":"Notes with folders, search and a dark theme.","stargazers_count":42,"pushed_at":"2026-11-19T10:00:00Z","default_branch":"main"},
            {"id":12,"name":"SketchwareAI-Vehicle_Parking","private":false,"owner":{"login":"$OWNER"},"description":"Parking tickets, slots and a daily report for a parking stand.","stargazers_count":17,"pushed_at":"2026-11-18T10:00:00Z","default_branch":"main"},
            {"id":13,"name":"SketchwareAI-Public-Timer","private":false,"owner":{"login":"cy"},"description":null,"stargazers_count":3,"pushed_at":"2026-11-15T10:00:00Z","default_branch":"main"},
            {"id":14,"name":"SketchwareAI-Telugu_Calendar","private":false,"owner":{"login":"ravi.k"},"description":"Panchangam, festivals and reminders in Telugu.","stargazers_count":128,"pushed_at":"2026-11-12T10:00:00Z","default_branch":"main"}]}"""
        val base = "/repos/ann-dev/SketchwareAI-Notes/contents/playstore"
        val listing = """{"title":"Notes Pro","description":"Write things down fast: folders, search, pinned notes, a dark theme and a home-screen widget. Made with Sketchware AI on a phone.","video":"https://youtu.be/dQw4w9WgXcQ"}"""
        GitHubHttp.ANSWERS["$base/listing.json?ref=main"] = """{"content":"${java.util.Base64.getEncoder().encodeToString(listing.toByteArray())}"}"""
        val shots = (1..4).joinToString(",") { """{"name":"0$it.jpg","download_url":"${shotUrl(it)}"}""" }
        GitHubHttp.ANSWERS["$base/screenshots?ref=main"] = "[$shots]"
        ProjectRepo.VIEWER = listOf(version(21, "2.3", "9", NOW - DAY, apk = true), version(22, "2.2", "8", NOW - 5 * DAY))
    }

    fun shotUrl(i: Int) = "https://raw.githubusercontent.com/ann-dev/SketchwareAI-Notes/main/playstore/screenshots/0$i.jpg"

    /** Stand-in screenshots in the picture cache (the store reads them there before it would download them). */
    fun seedPictures(c: Context) {
        val dir = java.io.File(files(c).cacheDir, "playstore"); dir.mkdirs()
        val tints = listOf("#F4511E", "#1F6FEB", "#2EA043", "#8E44AD")
        for (i in 1..4) {
            val url = shotUrl(i)
            val bmp = Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            cv.drawColor(Color.parseColor("#141418"))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.parseColor(tints[i - 1]); cv.drawRect(0f, 0f, 360f, 110f, p)
            p.color = Color.WHITE; p.textSize = 34f; cv.drawText("Notes Pro", 24f, 70f, p)
            p.color = Color.parseColor("#2A2A30")
            for (r in 0 until 5) cv.drawRoundRect(24f, 140f + r * 110f, 336f, 230f + r * 110f, 18f, 18f, p)
            p.color = Color.parseColor("#A1A1AA"); p.textSize = 22f
            for (r in 0 until 5) cv.drawText("Note ${r + 1} · screen $i", 44f, 192f + r * 110f, p)
            java.io.FileOutputStream(java.io.File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length + ".img")).use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    const val REVIEWS = """{"data":{"repository":{"id":"R_1","hasDiscussionsEnabled":true,"discussionCategories":{"nodes":[{"id":"C1","name":"General"},{"id":"C2","name":"Reviews"}]},"discussions":{"nodes":[
        {"id":"D_7","number":7,"title":"★★★★★ Best notes app I built from","bodyText":"Imported the .swb, changed the colours and it ran on my phone the first time.","author":{"login":"zed-apps"},"createdAt":"2026-11-18T09:00:00Z","comments":{"totalCount":2},"category":{"name":"Reviews"},"url":"https://github.com/ann-dev/SketchwareAI-Notes/discussions/7"},
        {"id":"D_5","number":5,"title":"★★★★☆ Widget please","bodyText":"Works well. A bigger widget would be nice.","author":{"login":"kiran"},"createdAt":"2026-11-16T09:00:00Z","comments":{"totalCount":0},"category":{"name":"Reviews"},"url":"https://github.com/ann-dev/SketchwareAI-Notes/discussions/5"}]}}}}"""

    fun storeList(c: Context): View { storeAnswers(); return open(c, PlayStoreActivity(), Intent()) }

    fun storeApp(c: Context): View {
        storeAnswers(); seedPictures(c)
        GitHubHttp.ANSWERS["/graphql"] = REVIEWS
        val i = Intent(); i.putExtra("playstore_owner", "ann-dev"); i.putExtra("playstore_repo", "SketchwareAI-Notes")
        return open(c, PlayStoreActivity(), i)
    }

    fun storePublish(c: Context): View {
        val i = Intent(); i.putExtra("playstore_publish", true)
        return open(c, PlayStoreActivity(), i)
    }

    const val COMMUNITY = """{"data":{"repository":{"id":"R_SW","hasDiscussionsEnabled":true,"discussionCategories":{"nodes":[{"id":"G","name":"General"},{"id":"I","name":"Ideas"},{"id":"Q","name":"Q&A"},{"id":"S","name":"Show and tell"}]},"discussions":{"nodes":[
        {"id":"D1","number":31,"title":"How do I add a RecyclerView with blocks?","bodyText":"","author":{"login":"kiran"},"createdAt":"2026-11-19T08:00:00Z","comments":{"totalCount":4},"category":{"name":"Q&A"},"url":"u"},
        {"id":"D2","number":30,"title":"My parking app is in the Play Store tab now","bodyText":"","author":{"login":"$OWNER"},"createdAt":"2026-11-18T08:00:00Z","comments":{"totalCount":9},"category":{"name":"Show and tell"},"url":"u"},
        {"id":"D3","number":28,"title":"Idea: Wear OS preview on the canvas","bodyText":"","author":{"login":"zed-apps"},"createdAt":"2026-11-16T08:00:00Z","comments":{"totalCount":1},"category":{"name":"Ideas"},"url":"u"},
        {"id":"D4","number":25,"title":"Welcome to the Sketchware AI community","bodyText":"","author":{"login":"smartapps000-hub"},"createdAt":"2026-11-10T08:00:00Z","comments":{"totalCount":0},"category":{"name":"General"},"url":"u"}]}}}}"""

    const val THREAD = """{"data":{"repository":{"discussion":{"id":"D1","title":"How do I add a RecyclerView with blocks?","bodyText":"I want a list of cards with an image and two lines. Which blocks do I use, and does it work in the Java editor too?","author":{"login":"kiran"},"createdAt":"2026-11-19T08:00:00Z","url":"u","comments":{"nodes":[
        {"author":{"login":"ann-dev"},"bodyText":"Use a ListView with a custom view first; RecyclerView needs the add-source block. The Java editor turns setText lines into blocks.","createdAt":"2026-11-19T09:10:00Z"},
        {"author":{"login":"zed-apps"},"bodyText":"The GitHub API Demo project shows a list with a custom row.","createdAt":"2026-11-19T10:30:00Z"}]}}}}}"""

    fun community(c: Context, json: String): View {
        GitHubHttp.ANSWERS["/graphql"] = json
        return open(c, CommunityActivity())
    }

    fun communityThread(c: Context): View {
        GitHubHttp.ANSWERS["/graphql"] = THREAD
        val i = Intent(); i.putExtra("community_owner", "smartapps000-hub"); i.putExtra("community_repo", "SketchwareAI")
        i.putExtra("community_number", 31); i.putExtra("community_title", "How do I add a RecyclerView with blocks?")
        return open(c, CommunityActivity(), i)
    }

    /** The viewer with the page saved on this phone and GitHub not answering within 10 seconds. */
    fun viewerTimeout(c: Context): View {
        val repo = "SketchwareAI-GitHub_API_Demo-2"
        val info = """{"id":102,"name":"$repo","private":false,"description":"GitHub API Demo: an Android app made with Sketchware AI with 1 screen. Uses GitHub.","stargazers_count":1,"default_branch":"main","updated_at":"2026-11-15T05:20:00Z","owner":{"login":"$OWNER"},"topics":["sketchware-ai","android","github-api"],"html_url":"https://github.com/$OWNER/$repo"}"""
        val readme = "# GitHub API Demo\n\nAn Android app made with Sketchware AI.\n\n- 1 screen: MainActivity\n- Components: GitHub\n"
        val f = java.io.File(files(c).filesDir, "repo_viewer/${OWNER.lowercase()}__${repo.lowercase()}.json")
        f.parentFile.mkdirs()
        val o = com.google.gson.JsonObject()
        o.add("info", com.google.gson.JsonParser.parseString(info))
        o.addProperty("readme", readme)
        f.writeText(o.toString())
        GitHubHttp.ERRORS["/repos/$OWNER/$repo"] = java.net.SocketTimeoutException("timeout")
        val i = Intent(); i.putExtra("owner", OWNER); i.putExtra("repo", repo)
        return open(c, RepoViewerActivity(), i)
    }
}

class V210Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    @Test fun v210() {
        val c = paparazzi.context
        V210Shots.sampleData()
        // Home and the bar at four moments of a tap (Home → GitHub, then settled on Play Store)
        val frames = listOf<(HoneyDropBar) -> Unit>(
                { it.select(HoneyDropBar.HOME, false) },
                { it.frameForTests(HoneyDropBar.HOME, HoneyDropBar.GITHUB, 0.22f) },
                { it.frameForTests(HoneyDropBar.HOME, HoneyDropBar.GITHUB, 0.5f) },
                { it.frameForTests(HoneyDropBar.HOME, HoneyDropBar.GITHUB, 0.78f) },
                { it.select(HoneyDropBar.STORE, false) })
        V210Shots.save(c, "v210_home_bar.png", frames.map { f ->
            val home = V210Shots.home(c)
            V210Shots.shot(c, home, 393, 851) { f(V210Shots.bar(home)) }
        })
        V210Shots.save(c, "v210_restore.png", listOf(V210Shots.shot(c, V210Shots.restore(c, 0), 393, 851, wrap = true),
                V210Shots.shot(c, V210Shots.restore(c, 1), 393, 851), V210Shots.shot(c, V210Shots.restore(c, 2), 393, 851)))
        V210Shots.save(c, "v210_settings.png", listOf(V210Shots.shot(c, V210Shots.settings(c, false, true), 393, 851, wrap = true),
                V210Shots.shot(c, V210Shots.settings(c, true, false), 393, 851, wrap = true)))
        V210Shots.save(c, "v210_store.png", listOf(V210Shots.shot(c, V210Shots.storeList(c), 393, 851),
                V210Shots.shot(c, V210Shots.storeApp(c), 393, 851, wrap = true), V210Shots.shot(c, V210Shots.storePublish(c), 393, 851, wrap = true)))
        val signedIn = TokenStore.LOGIN
        val offJson = V210Shots.COMMUNITY.replace("\"hasDiscussionsEnabled\":true", "\"hasDiscussionsEnabled\":false")
        val list = V210Shots.shot(c, V210Shots.community(c, V210Shots.COMMUNITY), 393, 851)
        val thread = V210Shots.shot(c, V210Shots.communityThread(c), 393, 851, wrap = true)
        val off = V210Shots.shot(c, V210Shots.community(c, offJson), 393, 600)
        TokenStore.LOGIN = null
        val out = V210Shots.shot(c, V210Shots.community(c, V210Shots.COMMUNITY), 393, 600)
        TokenStore.LOGIN = signedIn
        V210Shots.save(c, "v210_community.png", listOf(list, thread, off, out))
        V210Shots.save(c, "v210_viewer.png", listOf(V210Shots.shot(c, V210Shots.viewerTimeout(c), 393, 851, wrap = true)))
    }
}

class V210LargeFontTest {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT, fontScale = 1.3f), theme = "android:Theme.Material.NoActionBar")

    @Test fun v210Large() {
        val c = paparazzi.context
        V210Shots.sampleData()
        val home = V210Shots.home(c)
        val homeShot = V210Shots.shot(c, home, 360, 780) { V210Shots.bar(home).select(HoneyDropBar.COMMUNITY, false) }
        V210Shots.save(c, "v210_large_home_restore.png", listOf(homeShot, V210Shots.shot(c, V210Shots.restore(c, 1), 360, 780)))
        V210Shots.save(c, "v210_large_settings.png", listOf(V210Shots.shot(c, V210Shots.settings(c, true, true), 360, 780, wrap = true)))
    }
}
