package pz

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.github.AppControl
import com.sketchware.ai.github.GitHubViews
import com.sketchware.ai.github.PlayStoreActivity
import com.sketchware.ai.github.UpdateCard
import org.junit.Rule
import org.junit.Test

/**
 * v2.1.3: the owner's 22 Nov phone review drawn from the release code: the update card (UPDATE AVAILABLE in #FF8A65,
 * Key features numbered with a short line, Bugs fixed with ticks, Later / Update as glass pills; required and not), the
 * backup and update notifications as the glass card (applied through RemoteViews as Android's shade does: running,
 * done, failed), Restore's GitHub / Demo / On this phone tabs with one card per project (versions inside, Restore / Open
 * in the title row), the Play Store list (Publish in the header) and app page (slim hero, @creator / version / Follow
 * pills, counters inside, four buttons, About this app in two lines), Follow and Following. Writes out/v213_*.png.
 */
class V213Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

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

    private fun config(forced: Boolean) = AppControl.Config().apply {
        minVersionCode = 210; latestVersionCode = 210; latestVersionName = "v2.1.3"; forceUpdate = forced
        updateReason = "This update is required: it fixes problems in the version you have, and that version is no " +
                "longer supported. It installs over it: your projects, settings and GitHub sign-in stay."
        updateNotes.addAll(listOf("Backups upload in parallel parts and resume by themselves",
                "Restore: one card per project with its versions; Pin keeps one in front",
                "Play Store: Follow creators, Review, Publish from the header"))
        updateFixes.addAll(listOf("A finished Retry no longer leaves the old \"Didn't work\" notice",
                "Tabs open faster; sizes in MB as Android counts them",
                "The e-mailed sign-in code helper shows on GitHub's sign-in pages"))
    }

    @Test fun update() {
        val c = paparazzi.context
        val act = com.besome.sketch.lib.base.BaseAppCompatActivity()
        act.attach(V211Shots.ctx(c), Intent())
        val forced = UpdateCard.view(act, config(true), true, Runnable { }, null)
        val optional = UpdateCard.view(act, config(false), false, Runnable { }, Runnable { })
        V210Shots.save(c, "v213_update.png", listOf(
                V210Shots.shot(c, sheet(c, forced), 393, 851),
                V210Shots.shot(c, sheet(c, optional), 393, 851)))
    }

    /** The notification card as Android's shade builds it: the RemoteViews applied into a frame (else inflated). */
    private fun notice(c: Context, title: String, text: String, percent: Int, state: Int, big: Boolean, icon: Int): View {
        val layout = if (big) pz.render.R.layout.ob_notification_glass_big else pz.render.R.layout.ob_notification_glass
        val card = Class.forName("com.sketchware.ai.github.GlassNotice").getDeclaredMethod("card", Context::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java, String::class.java,
                Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        card.isAccessible = true
        val holder = FrameLayout(c)
        holder.setBackgroundColor(Color.parseColor("#2B2930"))
        holder.setPadding(V210Shots.dp(c, 12), V210Shots.dp(c, 12), V210Shots.dp(c, 12), V210Shots.dp(c, 12))
        val header = TextView(c); header.text = "Sketchware AI · now"; header.setTextColor(Color.parseColor("#C9C5D0")); header.textSize = 12f
        val column = LinearLayout(c); column.orientation = LinearLayout.VERTICAL
        column.addView(header)
        var applied: View? = null
        try {
            val rv = card.invoke(null, c, layout, icon, title, text, percent, false, state) as android.widget.RemoteViews
            // Android's shade inflates with a plain inflater (no AppCompat factory): the same here
            val plain = object : android.content.ContextWrapper(c) {
                private var inf: android.view.LayoutInflater? = null
                override fun getSystemService(name: String): Any? {
                    if (name == Context.LAYOUT_INFLATER_SERVICE) {
                        if (inf == null) {
                            inf = android.view.LayoutInflater.from(baseContext).cloneInContext(this)
                            for (f in listOf("mFactory", "mFactory2", "mPrivateFactory")) {
                                try {
                                    val fd = android.view.LayoutInflater::class.java.getDeclaredField(f)
                                    fd.isAccessible = true; fd.set(inf, null)
                                } catch (e: Throwable) { }
                            }
                        }
                        return inf
                    }
                    return super.getSystemService(name)
                }
            }
            applied = rv.apply(plain, column)
            System.err.println("RemoteViews.apply in layoutlib: applied (" + applied.javaClass.simpleName + ")")
        } catch (e: Throwable) {
            System.err.println("RemoteViews.apply in layoutlib: $e (inflated instead)")
        }
        if (applied == null) {
            val v = android.view.LayoutInflater.from(c).inflate(layout, column, false)
            v.findViewById<TextView>(pz.render.R.id.ob_notif_title).text = title
            v.findViewById<TextView>(pz.render.R.id.ob_notif_text).text = text
            v.findViewById<android.widget.ImageView>(pz.render.R.id.ob_notif_icon).setImageResource(icon)
            if (state == 2) v.findViewById<TextView>(pz.render.R.id.ob_notif_title).setTextColor(Color.parseColor("#FF8A65"))
            val bar = v.findViewById<android.widget.ProgressBar>(pz.render.R.id.ob_notif_bar)
            if (percent >= 0) bar.progress = percent else bar.visibility = View.GONE
            applied = v
        }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.topMargin = V210Shots.dp(c, 8)
        column.addView(applied, lp)
        holder.addView(column)
        return holder
    }

    @Test fun notifications() {
        val c = paparazzi.context
        val gh = pz.render.R.drawable.ob_ic_github
        val shots = listOf(
                notice(c, "Backing up Vehicle_Parking · 46%", "Uploading… 9.4 MB of 20.4 MB · 410 KB/s", 46, 0, false, gh),
                notice(c, "Backing up Vehicle_Parking · 46%", "Connection lost (UnknownHostException: uploads.github.com). " +
                        "Trying again in 12 s (1 of 3); the backup goes on from where it stopped…", 46, 0, true, gh),
                notice(c, "Done: Vehicle_Parking", "Vehicle_Parking backed up to its GitHub repository · the share link is on " +
                        "its card", -1, 1, true, pz.render.R.drawable.ob_ic_check),
                notice(c, "Didn't work: Vehicle_Parking", "No connection to GitHub (UnknownHostException: Unable to resolve " +
                        "host \"uploads.github.com\"). You stay signed in; the backup waits for the next chance.", -1, 2, true, gh),
                notice(c, "Updating Sketchware AI · 62%", "Downloading… 88.6 MB of 142.8 MB", 62, 0, false,
                        pz.render.R.drawable.ob_ic_download))
        V210Shots.save(c, "v213_notifications.png", shots.map { V210Shots.shot(c, it, 393, 0, wrap = true) })
    }

    @Test fun restore() {
        val c = paparazzi.context
        V210Shots.sampleData()
        val root = java.io.File(mod.hey.studios.project.backup.BackupFactory.DIR)
        root.deleteRecursively(); root.mkdirs()
        fun swb(folder: String, name: String, ago: Long, iconColor: String?) {
            val dir = java.io.File(root, folder); dir.mkdirs()
            val f = java.io.File(dir, "$name.swb")
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(f)).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("data/x")); z.write(ByteArray(2_000_000) { (it % 251).toByte() }); z.closeEntry()
                if (iconColor != null) {
                    z.putNextEntry(java.util.zip.ZipEntry("resources/icons/icon.png"))
                    z.write(V211Shots.icon(folder, iconColor, folder.substring(0, 1)).readBytes()); z.closeEntry()
                }
            }
            f.setLastModified(V210Shots.NOW - ago)
        }
        swb("Vehicle_Parking", "Vehicle_Parking v4.7.6 (com.my.parking, 84) 2026-11-21T101500", 0, "#FF8A3D")
        swb("Vehicle_Parking", "Vehicle_Parking v4.7.5 (com.my.parking, 83) 2026-11-20T090000", V210Shots.DAY, "#FF8A3D")
        swb("Vehicle_Parking", "Vehicle_Parking v4.7.4 (com.my.parking, 82) 2026-11-18T090000", 3 * V210Shots.DAY, "#FF8A3D")
        swb("Sri_Durga", "Sri_Durga v1.2 (com.sri.durga, 3) 2026-11-19T080000", 2 * V210Shots.DAY, null)
        swb("Notes", "Notes v2.3 (com.notes, 9) 2026-11-15T080000", 6 * V210Shots.DAY, "#FFB347")
        V210Shots.save(c, "v213_restore.png", listOf(
                V210Shots.shot(c, V211Shots.restore(c, 0), 393, 851, wrap = true),
                V210Shots.shot(c, V210Shots.restore(c, 1), 393, 851),
                V210Shots.shot(c, V210Shots.restore(c, 2), 393, 851)))
    }

    @Test fun store() {
        val c = paparazzi.context
        val list = V211Shots.list(c)
        val page = V211Shots.page(c)
        // the same page after a Follow tap that worked: ✓ Following
        val act = PlayStoreActivity()
        val i = Intent(); i.putExtra("playstore_owner", "ann-dev"); i.putExtra("playstore_repo", "SketchwareAI-Notes")
        val following = V211Shots.open(c, act, i)
        val f = PlayStoreActivity::class.java.getDeclaredField("follows"); f.isAccessible = true; f.set(act, true)
        val m = PlayStoreActivity::class.java.getDeclaredMethod("showFollow"); m.isAccessible = true; m.invoke(act)
        V210Shots.save(c, "v213_store.png", listOf(V210Shots.shot(c, list, 393, 851),
                V210Shots.shot(c, page, 393, 851), V210Shots.shot(c, following, 393, 851)))
    }
}
