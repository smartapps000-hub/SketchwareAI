package pz

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.besome.sketch.beans.BlockBean
import com.besome.sketch.beans.ComponentBean
import com.besome.sketch.beans.ProjectFileBean
import com.besome.sketch.beans.ViewBean
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.sketchware.ai.github.CloudBackup
import com.sketchware.ai.github.DrawerAccountCard
import com.sketchware.ai.github.GitHubBackupsActivity
import com.sketchware.ai.github.GitHubHttp
import com.sketchware.ai.github.GitHubImportDialog
import com.sketchware.ai.github.GitHubSession
import com.sketchware.ai.github.GitHubViews
import com.sketchware.ai.github.ProjectRepo
import com.sketchware.ai.github.PublishActivity
import com.sketchware.ai.github.RepoCache
import com.sketchware.ai.github.RepoViewerActivity
import com.sketchware.ai.ui.DemoCards
import org.junit.Rule
import org.junit.Test
import pz.render.R

/**
 * v2.0.5: the owner's 16 Nov directive and phone report. The Public details screen (PublishActivity, editable fields,
 * topic chips with ✕ and the Add field, Save for a public repository); the Restore screen with the Paste link box and
 * the version carousel (★ Pinned in the front slot, "New", "+N older versions →"); the in-app repository viewer
 * (RepoViewerActivity on canned GitHub answers: details, topics, versions with Restore / APK, README, files); the drawer
 * account card (no name: @username once; long e-mail on one line; refused sign-in with Sign in again / Sign out under
 * the details); the progress sheet's two rows (Hide | Cancel, then Open on GitHub) with the notifications line; the
 * Demo row. V205LargeFontTest draws the cards and the Restore screen at font scale 1.3.
 * Writes out/v205_publish.png, v205_restore.png, v205_viewer.png, v205_cards.png (and v205_large_*.png).
 */
object V205Shots {
    val out = java.io.File("/tmp/claude-0/-home-user-SketchwareAI/7396650f-8b3a-5447-a67d-6edecd23d850/scratchpad/pz/out")
    const val OWNER = "createnewappswithsketchwareai"
    const val DAY = 86_400_000L
    const val NOW = 1_794_732_000_000L

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun shot(c: Context, view: View, widthDp: Int, heightDp: Int, wrap: Boolean = false): Bitmap {
        val w = dp(c, widthDp); var h = dp(c, heightDp)
        if (wrap) {
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            h = maxOf(h, view.measuredHeight)
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
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
        repeat(3) {
            Thread.sleep(500)
            while (BaseAppCompatActivity.QUEUE.isNotEmpty()) BaseAppCompatActivity.QUEUE.removeAt(0).run()
        }
    }

    private fun info(name: String, number: Long, private: Boolean, updated: Long) = ProjectRepo.Info().apply {
        this.name = name; this.number = number; isPrivate = private; this.updated = updated; owner = OWNER
    }

    fun version(id: Long, vn: String, vc: String, time: Long, standard: Boolean = false, apk: Boolean = false) = ProjectRepo.Version().apply {
        this.id = id; versionName = vn; versionCode = vc; tag = "v$vn-$vc"; this.time = time; this.standard = standard; size = 2_400_000
        if (apk) { this.apk = "Vehicle_Parking_v$vn-$vc.apk"; assets.add(ProjectRepo.Asset().also { it.name = "Vehicle_Parking_v$vn-$vc.apk"; it.size = 9_800_000 }) }
    }

    fun sampleData() {
        RepoCache.REPOS = listOf(info("SketchwareAI-Vehicle_Parking", 101, true, NOW), info("SketchwareAI-GitHub_API_Demo", 102, false, NOW - DAY))
        RepoCache.VERSIONS[101L] = listOf(version(1, "4.7.6", "84", NOW), version(2, "4.7.5", "83", NOW - DAY),
                version(3, "4.7.4", "82", NOW - 2 * DAY), version(4, "4.7.0", "80", NOW - 9 * DAY, true), version(5, "4.6.9", "79", NOW - 12 * DAY))
        RepoCache.VERSIONS[102L] = listOf(version(6, "1.0", "1", NOW - DAY))
        GitHubImportDialog.OLDER = listOf()
        val dir = java.io.File(out, "swb-sample"); dir.mkdirs()
        mod.hey.studios.project.backup.BackupFactory.DIR = dir.absolutePath
        java.io.File(System.getProperty("java.io.tmpdir"), "render-storage/.sketchware/data").mkdirs()
    }

    fun restoreScreen(c: Context): View {
        val act = GitHubBackupsActivity()
        act.attach(c, Intent())
        act.onCreate(null)
        drain()
        return act.root()
    }

    fun publishScreen(c: Context): View {
        a.a.a.lC.PROJECT["my_ws_name"] = "Sri_D_Varahi_Parking_Stand"
        a.a.a.lC.PROJECT["my_app_name"] = "Sri D Varahi Parking Stand"
        a.a.a.lC.PROJECT["my_sc_pkg_name"] = "com.my.varahiparking"
        a.a.a.lC.PROJECT["sc_ver_name"] = "4.7.6"
        a.a.a.lC.PROJECT["sc_ver_code"] = "84"
        a.a.a.hC.SCREENS.clear()
        a.a.a.hC.SCREENS.add(ProjectFileBean("MainActivity", "main").apply { options = 1 or 8 })
        a.a.a.hC.SCREENS.add(ProjectFileBean("SettingsActivity", "settings"))
        a.a.a.eC.C["main"] = arrayListOf(ViewBean("linear1", 0), ViewBean("textview1", 4), ViewBean("button1", 3), ViewBean("listview1", 9))
        a.a.a.eC.C["settings"] = arrayListOf(ViewBean("switch1", 0), ViewBean("edittext1", 5))
        a.a.a.eC.D["MainActivity"] = hashMapOf("button1_onClick" to arrayListOf(BlockBean("doToast", "Toast %s", "Parked")),
                "onCreate_initializeLogic" to arrayListOf(BlockBean("setText", "%m.textview setText %s", "textview1", "Ready")))
        a.a.a.eC.E["MainActivity"] = arrayListOf(Pair(1, "slots"), Pair(2, "vehicleNumber"))
        a.a.a.eC.F["MainActivity"] = arrayListOf(Pair(3, "parkedList"))
        a.a.a.eC.G["MainActivity"] = arrayListOf(Pair("refreshList", "refreshList"), Pair("saveTicket", "saveTicket %s.number"))
        a.a.a.eC.H["MainActivity"] = arrayListOf(ComponentBean(2026, "github1"), ComponentBean(5, "timer1"))
        com.sketchware.ai.github.ProjectLinks.REPO_NAME = "SketchwareAI-Sri_D_Varahi_Parking_Stand"
        com.sketchware.ai.github.ProjectLinks.VISIBILITY = "public"
        val act = PublishActivity()
        val i = Intent()
        i.putExtra("sc_id", "663")
        i.putExtra("edit", true)
        act.attach(c, i)
        act.onCreate(null)
        drain()
        return act.root()
    }

    fun viewerScreen(c: Context): View {
        val repo = "SketchwareAI-Vehicle_Parking"
        val base = "/repos/$OWNER/$repo"
        GitHubHttp.ANSWERS[base] = """{"id":101,"name":"$repo","private":false,"description":"Vehicle Parking: an Android app made with Sketchware AI with 2 screens (MainActivity, SettingsActivity). Uses GitHub, Timer. Android 5.0 to 17, Material 3.","stargazers_count":3,"default_branch":"main","updated_at":"2026-11-16T05:20:00Z","owner":{"login":"$OWNER"},"topics":["sketchware-ai","sketchware","android","java","material3","github-api"],"html_url":"https://github.com/$OWNER/$repo"}"""
        val readme = "# Vehicle Parking\n\nAn Android app made with Sketchware AI.\n\n## Features\n\n- **2 screens**: MainActivity, SettingsActivity\n- 6 widgets, 2 logic blocks, 2 MoreBlocks, 3 variables\n- Components: GitHub, Timer\n\n## Screens\n\n| Screen | Widgets | Logic blocks |\n| --- | ---: | ---: |\n| MainActivity | 4 | 2 |\n"
        GitHubHttp.ANSWERS["$base/contents/README.md?ref=main"] = """{"content":"${java.util.Base64.getEncoder().encodeToString(readme.toByteArray())}"}"""
        GitHubHttp.ANSWERS["$base/contents?ref=main"] = """[{"name":"README.md","type":"file","size":1840},{"name":"CHANGELOG.md","type":"file","size":920},{"name":"screenshots","type":"dir","size":0},{"name":".gitattributes","type":"file","size":66}]"""
        ProjectRepo.VIEWER = listOf(version(1, "4.7.6", "84", NOW, apk = true), version(2, "4.7.5", "83", NOW - DAY),
                version(4, "4.7.0", "80", NOW - 9 * DAY, true))
        val act = RepoViewerActivity()
        val i = Intent()
        i.putExtra("owner", OWNER)
        i.putExtra("repo", repo)
        act.attach(c, i)
        act.onCreate(null)
        drain()
        return act.root()
    }

    private val noActions = object : DrawerAccountCard.Actions {
        override fun signIn() {}
        override fun signOut() {}
    }

    fun drawer(c: Context, profile: GitHubSession.Profile?): View {
        val panel = LinearLayout(c)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.parseColor("#141416"))
        val header = LayoutInflater.from(c).inflate(R.layout.main_drawer_header, panel, false)
        DrawerAccountCard.bind(header.findViewById(R.id.drawer_account), profile, noActions)
        panel.addView(header, LinearLayout.LayoutParams(dp(c, 300), ViewGroup.LayoutParams.WRAP_CONTENT))
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        root.addView(panel, FrameLayout.LayoutParams(dp(c, 300), ViewGroup.LayoutParams.MATCH_PARENT))
        return root
    }

    fun profiles(): List<GitHubSession.Profile> = listOf(
            GitHubSession.Profile().apply {
                login = OWNER; name = null; email = "createnewappswithsketchwareai.bammidieswararao@gmail.com"
                repositories = 12; checkedAt = NOW
            },
            GitHubSession.Profile().apply {
                login = OWNER; name = "Bammidi Eswararao"; email = "bammidieswararao@gmail.com"
                problem = "GitHub didn't accept the saved sign-in at 08:24 (HTTP 401: Bad credentials). GitHub keeps 10 sign-ins " +
                        "per app and ends the least used one: tap Sign in again. Backups wait; nothing is lost."
            })

    /** The sheet as GitHubProgress.runningButtons lays it out: Hide | Cancel, Open on GitHub under them, then the line. */
    fun progress(c: Context): View {
        val v = GitHubViews(c)
        val sheet = v.progressSheet(R.drawable.ob_ic_github, "Backing up NewProject9")
        val buttons2 = LinearLayout(c)
        val notice = LinearLayout(c)
        notice.gravity = android.view.Gravity.CENTER_VERTICAL
        val at = sheet.root.indexOfChild(sheet.buttons)
        sheet.root.addView(buttons2, at + 1, v.full(-1, 8))
        sheet.root.addView(notice, at + 2, v.full(-1, 10))
        fun pill(t: String, orange: Boolean) = v.smallPill(t, orange, true, null).also { GitHubViews.oneLine(it) }
        for (t in listOf(pill("Hide", false), pill("Cancel", false))) {
            val p = LinearLayout.LayoutParams(0, v.dp(40), 1f)
            if (sheet.buttons.childCount > 0) p.leftMargin = v.dp(8)
            sheet.buttons.addView(t, p)
        }
        buttons2.addView(pill("Open on GitHub", true), LinearLayout.LayoutParams(-1, v.dp(40)))
        sheet.percent.text = "1%"
        sheet.bar.progress = 10
        sheet.status.text = "1% • Waiting: Vehicle Parking (34%) pauses for this one…"
        sheet.note.visibility = View.GONE
        val line = v.line("Notifications are off: the progress shows only here.", false)
        line.textSize = 12f
        notice.addView(line, LinearLayout.LayoutParams(0, -2, 1f))
        val on = pill("Turn on", true)
        val lp = LinearLayout.LayoutParams(-2, v.dp(36)); lp.leftMargin = v.dp(8)
        notice.addView(on, lp)
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        val flp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        flp.leftMargin = dp(c, 16); flp.rightMargin = dp(c, 16); flp.topMargin = dp(c, 40)
        val card = FrameLayout(c)
        card.setBackgroundResource(R.drawable.ob_glass)
        card.addView(sheet.root)
        root.addView(card, flp)
        return root
    }

    /** The Demo row as myprojects.xml holds it (title + one horizontal row), with DemoCards' cards. */
    fun demoRow(c: Context): View {
        val section = LinearLayout(c)
        section.orientation = LinearLayout.VERTICAL
        section.setBackgroundColor(Color.parseColor("#0B0B0D"))
        section.setPadding(dp(c, 16), dp(c, 16), 0, dp(c, 8))
        val title = TextView(c, null, 0, R.style.Ob_Text_Label)
        title.text = "Demo"
        section.addView(title)
        val scroll = HorizontalScrollView(c)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(c, 8), dp(c, 16), 0)
        scroll.addView(row)
        section.addView(scroll)
        val demos = listOf(hashMapOf<String, Any>("sc_id" to "651", "my_app_name" to "GitHub API Demo", "sc_ver_name" to "3.0", "sc_ver_code" to "3"),
                hashMapOf<String, Any>("sc_id" to "612", "my_app_name" to "GitHub API Demo", "sc_ver_name" to "2.0", "sc_ver_code" to "2"),
                hashMapOf<String, Any>("sc_id" to "604", "my_app_name" to "GitHub API Demo", "sc_ver_name" to "1.0", "sc_ver_code" to "1"))
        for ((i, p) in demos.withIndex()) row.addView(DemoCards.card(c, java.util.HashMap(p), i, {}, {}), DemoCards.params(c, i))
        return section
    }
}

class V205Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    @Test fun v205() {
        val c = paparazzi.context
        V205Shots.sampleData()
        V205Shots.save(c, "v205_publish.png", listOf(V205Shots.shot(c, V205Shots.publishScreen(c), 393, 860, wrap = true)))
        V205Shots.save(c, "v205_restore.png", listOf(V205Shots.shot(c, V205Shots.restoreScreen(c), 393, 860, wrap = true)))
        V205Shots.save(c, "v205_viewer.png", listOf(V205Shots.shot(c, V205Shots.viewerScreen(c), 393, 860, wrap = true)))
        val ps = V205Shots.profiles()
        V205Shots.save(c, "v205_cards.png", listOf(V205Shots.shot(c, V205Shots.drawer(c, ps[0]), 320, 560),
                V205Shots.shot(c, V205Shots.drawer(c, ps[1]), 320, 560), V205Shots.shot(c, V205Shots.progress(c), 393, 560),
                V205Shots.shot(c, V205Shots.progress(c), 360, 560), V205Shots.shot(c, V205Shots.demoRow(c), 393, 200)))
    }
}

class V205LargeFontTest {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT, fontScale = 1.3f), theme = "android:Theme.Material.NoActionBar")

    @Test fun v205Large() {
        val c = paparazzi.context
        V205Shots.sampleData()
        val ps = V205Shots.profiles()
        V205Shots.save(c, "v205_large_cards.png", listOf(V205Shots.shot(c, V205Shots.drawer(c, ps[0]), 320, 600),
                V205Shots.shot(c, V205Shots.drawer(c, ps[1]), 320, 600), V205Shots.shot(c, V205Shots.progress(c), 360, 600),
                V205Shots.shot(c, V205Shots.demoRow(c), 360, 220)))
        V205Shots.save(c, "v205_large_restore.png", listOf(V205Shots.shot(c, V205Shots.restoreScreen(c), 360, 860, wrap = true)))
    }
}
