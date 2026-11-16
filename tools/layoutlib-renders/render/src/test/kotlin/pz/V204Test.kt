package pz

import android.content.Intent
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
import com.besome.sketch.beans.BlockBean
import com.besome.sketch.beans.ComponentBean
import com.besome.sketch.beans.ProjectFileBean
import com.besome.sketch.beans.ViewBean
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.sketchware.ai.github.CloudBackup
import com.sketchware.ai.github.DrawerAccountCard
import com.sketchware.ai.github.GitHubBackupsActivity
import com.sketchware.ai.github.GitHubImportDialog
import com.sketchware.ai.github.GitHubSession
import com.sketchware.ai.github.GitHubViews
import com.sketchware.ai.github.ProjectRepo
import com.sketchware.ai.github.PublishActivity
import com.sketchware.ai.github.RepoCache
import pz.render.R
import org.junit.Rule
import org.junit.Test

/**
 * v2.0.4: the owner's 15 Nov directive. The Restore projects screen (GitHubBackupsActivity, its own code on a
 * window-less activity base) with three project repositories from the phone's copy (Private / Public badges, Share,
 * versions Latest #1 and ★ Standard #2, Restore and Set as Standard) and the older backups; its "On this phone" tab with
 * three .swb files; the Publish to GitHub screen (PublishActivity with the change analyzer on a sample project); the
 * drawer account card with the copy buttons (14sp / 11sp lines); project cards with the name on one line up to the id
 * and package + Public / Private + Share on the bottom row (393dp and 360dp); the progress sheet running (Hide, Cancel,
 * Open on GitHub) and failed (Retry, Close). Writes out/v204_restore.png, out/v204_publish.png, out/v204_cards.png.
 */
class V204Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    private fun dp(v: Int) = (v * paparazzi.context.resources.displayMetrics.density).toInt()
    private val out = java.io.File("/tmp/claude-0/-home-user-SketchwareAI/7396650f-8b3a-5447-a67d-6edecd23d850/scratchpad/pz/out")

    private fun shot(view: View, widthDp: Int, heightDp: Int, wrap: Boolean = false): Bitmap {
        val w = dp(widthDp); var h = dp(heightDp)
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

    private fun save(name: String, shots: List<Bitmap>) {
        out.mkdirs()
        val gap = dp(12)
        val all = Bitmap.createBitmap(shots.sumOf { it.width } + gap * (shots.size + 1), shots.maxOf { it.height } + 2 * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(all)
        canvas.drawColor(Color.parseColor("#3A3A40"))
        var x = gap
        for (s in shots) { canvas.drawBitmap(s, x.toFloat(), gap.toFloat(), null); x += s.width + gap }
        java.io.FileOutputStream(java.io.File(out, name)).use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun drain() {
        Thread.sleep(700)
        while (BaseAppCompatActivity.QUEUE.isNotEmpty()) BaseAppCompatActivity.QUEUE.removeAt(0).run()
    }

    private fun info(name: String, number: Long, private: Boolean, updated: Long) = ProjectRepo.Info().apply {
        this.name = name; this.number = number; isPrivate = private; this.updated = updated; owner = "createnewappswithsketchwareai"
    }

    private fun version(id: Long, vn: String, vc: String, time: Long, standard: Boolean = false) = ProjectRepo.Version().apply {
        this.id = id; versionName = vn; versionCode = vc; tag = "v$vn-$vc"; this.time = time; this.standard = standard; size = 2_400_000
    }

    private fun sampleData() {
        val day = 86_400_000L
        val now = 1_794_732_000_000L
        RepoCache.REPOS = listOf(info("SketchwareAI-Vehicle_Parking", 101, true, now), info("SketchwareAI-GitHub_API_Demo", 102, false, now - day),
                info("SketchwareAI-Sri_Durga_Varahi_Parking_Stand_Management", 103, true, now - 3 * day))
        RepoCache.VERSIONS[101L] = listOf(version(1, "4.7.6", "84", now), version(2, "4.7.5", "83", now - day),
                version(3, "4.7.4", "82", now - 2 * day), version(4, "4.7.0", "80", now - 9 * day, true), version(5, "4.6.9", "79", now - 12 * day))
        RepoCache.VERSIONS[102L] = listOf(version(6, "1.0", "1", now - day))
        GitHubImportDialog.OLDER = listOf(CloudBackup.Entry().apply { name = "My_App"; packageName = "com.my.app"; time = now - 30 * day; size = 1_300_000 },
                CloudBackup.Entry().apply { name = "New_video"; packageName = "com.my.newvideo"; time = now - 40 * day; size = 800_000 })
    }

    private fun restoreScreen(phone: Boolean): View {
        val act = GitHubBackupsActivity()
        val i = Intent()
        i.putExtra(GitHubBackupsActivity.EXTRA_PHONE, phone)
        act.attach(paparazzi.context, i)
        act.onCreate(null)
        drain()
        return act.root()
    }

    private fun publishScreen(): View {
        a.a.a.lC.PROJECT["my_ws_name"] = "Vehicle Parking"
        a.a.a.lC.PROJECT["my_app_name"] = "Vehicle Parking"
        a.a.a.lC.PROJECT["my_sc_pkg_name"] = "com.my.vehicleparking"
        a.a.a.lC.PROJECT["sc_ver_name"] = "4.7.6"
        a.a.a.lC.PROJECT["sc_ver_code"] = "84"
        a.a.a.hC.SCREENS.clear()
        a.a.a.hC.SCREENS.add(ProjectFileBean("MainActivity", "main"))
        a.a.a.hC.SCREENS.add(ProjectFileBean("SettingsActivity", "settings"))
        a.a.a.eC.C["main"] = arrayListOf(ViewBean("linear1", 0), ViewBean("textview1", 4), ViewBean("button1", 3), ViewBean("listview1", 9))
        a.a.a.eC.C["settings"] = arrayListOf(ViewBean("switch1", 0), ViewBean("edittext1", 5))
        a.a.a.eC.D["MainActivity"] = hashMapOf("button1_onClick" to arrayListOf(BlockBean("doToast", "Toast %s", "Parked")),
                "onCreate_initializeLogic" to arrayListOf(BlockBean("setText", "%m.textview setText %s", "textview1", "Ready")))
        a.a.a.eC.H["MainActivity"] = arrayListOf(ComponentBean(2026, "github1"), ComponentBean(5, "timer1"))
        com.sketchware.ai.github.ProjectLinks.REPO_NAME = "SketchwareAI-Vehicle_Parking"
        val act = PublishActivity()
        val i = Intent()
        i.putExtra("sc_id", "663")
        act.attach(paparazzi.context, i)
        act.onCreate(null)
        drain()
        return act.root()
    }

    private val noActions = object : DrawerAccountCard.Actions {
        override fun signIn() {}
        override fun signOut() {}
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

    private fun card(name: String, isPublic: Boolean, target: String?): View {
        val c = paparazzi.context
        val card = LayoutInflater.from(c).inflate(R.layout.myprojects_item, null, false)
        card.findViewById<TextView>(R.id.app_name).text = name.replace(' ', '_')
        val title = card.findViewById<TextView>(R.id.project_name)
        title.text = name
        title.isSelected = true
        card.findViewById<TextView>(R.id.package_name).text = "com.my.vehicleparking · 4.7.6 (84)"
        card.findViewById<TextView>(R.id.ob_monogram).text = name.take(2).uppercase()
        val id = card.findViewById<TextView>(R.id.tv_published)
        val pill = card.findViewById<TextView>(R.id.ob_target_pill)
        id.text = "#663"; id.visibility = View.VISIBLE
        if (target == null) pill.visibility = View.GONE else { pill.text = target; pill.visibility = View.VISIBLE }
        card.findViewById<View>(R.id.ob_github_pills).visibility = View.VISIBLE
        card.findViewById<TextView>(R.id.ob_visibility_text).text = if (isPublic) "Public" else "Private"
        card.findViewById<ImageView>(R.id.ob_visibility_icon).setImageResource(if (isPublic) R.drawable.ob_ic_public else R.drawable.ob_ic_lock)
        return card
    }

    private fun projectCards(): View {
        val root = LinearLayout(paparazzi.context)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        root.setPadding(dp(8), dp(24), dp(8), 0)
        for ((name, pub, target) in listOf(Triple("New video", false, null), Triple("Sri Durga Varahi Parking Stand Management", true, "Target 28"),
                Triple("GitHub API Demo", true, null))) {
            root.addView(card(name, pub, target), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return root
    }

    /** The sheet as GitHubProgress fills it: running (Hide, Cancel, Open on GitHub) or failed (Retry, Close). */
    private fun progress(failed: Boolean): View {
        val c = paparazzi.context
        val v = GitHubViews(c)
        val sheet = v.progressSheet(R.drawable.ob_ic_github, "Backing up Vehicle Parking")
        fun pills(vararg p: TextView) {
            sheet.buttons.removeAllViews()
            for (t in p) {
                GitHubViews.oneLine(t)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, v.dp(40), 1f)
                if (sheet.buttons.childCount > 0) lp.leftMargin = v.dp(8)
                sheet.buttons.addView(t, lp)
            }
        }
        if (!failed) {
            sheet.percent.text = "62%"
            sheet.bar.progress = 620
            sheet.status.text = "62% • Uploading to GitHub… 9.4 of 15.2 MB · 1.2 MB/s"
            pills(v.smallPill("Hide", false, true, null), v.smallPill("Cancel", false, true, null), v.smallPill("Open on GitHub", true, true, null))
            sheet.note.text = "Open on GitHub: view the repository and its files; this goes on in the notification."
            sheet.note.visibility = View.VISIBLE
        } else {
            sheet.percent.text = "90%"
            sheet.bar.progress = 900
            sheet.status.text = "Uploading the backup failed: the network dropped it three times. Nothing on GitHub changed; tap Retry."
            sheet.status.maxLines = 8
            sheet.status.setTextColor(v.color(R.color.ob_error))
            sheet.percent.setTextColor(v.color(R.color.ob_error))
            pills(v.smallPill("Retry", true, true, null), v.smallPill("Close", false, true, null))
            sheet.note.visibility = View.GONE
        }
        val root = FrameLayout(c)
        root.setBackgroundColor(Color.parseColor("#0B0B0D"))
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = dp(16); lp.rightMargin = dp(16); lp.topMargin = dp(40)
        val card = FrameLayout(c)
        card.setBackgroundResource(R.drawable.ob_glass)
        card.addView(sheet.root)
        root.addView(card, lp)
        return root
    }

    @Test fun v204() {
        java.io.File(System.getProperty("java.io.tmpdir"), "render-storage/.sketchware/data").mkdirs()
        sampleData()
        val dir = java.io.File(out, "swb-sample"); dir.mkdirs()
        for ((n, t) in listOf("Vehicle_Parking_20261115.swb" to 0L, "GitHub_API_Demo.swb" to 1L, "New_video.swb" to 2L)) {
            val f = java.io.File(dir, n); f.writeBytes(ByteArray(150_000 + (t * 90_000).toInt())); f.setLastModified(1_794_732_000_000L - t * 86_400_000L)
        }
        mod.hey.studios.project.backup.BackupFactory.DIR = dir.absolutePath
        save("v204_restore.png", listOf(shot(restoreScreen(false), 393, 860, wrap = true), shot(restoreScreen(true), 393, 860)))
        val publish = publishScreen()
        save("v204_publish.png", listOf(shot(publish, 393, 860, wrap = true)))
        val long = GitHubSession.Profile().apply {
            login = "createnewappswithsketchwareai"; name = "Bammidi Eswararao"
            email = "createnewappswithsketchwareai.bammidieswararao@gmail.com"; avatar = null
            repositories = 12; checkedAt = 1_794_732_000_000L
        }
        save("v204_cards.png", listOf(shot(drawer(null), 320, 520), shot(drawer(long), 320, 520), shot(projectCards(), 393, 520),
                shot(projectCards(), 360, 520), shot(progress(false), 393, 520), shot(progress(false), 360, 520),
                shot(progress(true), 393, 520)))
    }
}
