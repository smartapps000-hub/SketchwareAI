package pz

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.sketchware.ai.github.CommunityActivity
import com.sketchware.ai.github.GitHubHttp
import com.sketchware.ai.github.PlayStore
import com.sketchware.ai.github.PlayStoreActivity
import com.sketchware.ai.github.ProjectRepo
import com.sketchware.ai.github.RepoIcons
import org.junit.Rule
import org.junit.Test

/**
 * v2.1.1: the owner's 21 Nov directive ("Google Play-grade Play Store, real app icons, slim Restore cards, native
 * Community"). Play Store: the list with the search box in the header, app icons and version lines; an app's page with
 * its icon, @creator, the version line, Downloads / Likes / Shares / Views, What's new, pictures, Get it and reviews; a
 * creator's page; Publish with its form and three pictures read back from the phone. Community: the feed kept on the
 * phone (posts from several repositories). Restore projects: GitHub cards with the Home tile or the app's icon, the
 * version selector, Demo and On this phone with icons. Real screen classes on the stand-ins of setup_v211.sh.
 * Writes out/v211_*.png.
 */
/** In-memory SharedPreferences (layoutlib's are stubs). */
class MapPrefs : android.content.SharedPreferences {
    val map = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(k: String, d: String?) = map[k] as String? ?: d
    override fun getStringSet(k: String, d: MutableSet<String>?) = d
    override fun getInt(k: String, d: Int) = map[k] as Int? ?: d
    override fun getLong(k: String, d: Long) = map[k] as Long? ?: d
    override fun getFloat(k: String, d: Float) = map[k] as Float? ?: d
    override fun getBoolean(k: String, d: Boolean) = map[k] as Boolean? ?: d
    override fun contains(k: String) = map.containsKey(k)
    override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
        override fun putString(k: String, v: String?) = apply { map[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { map[k] = v }
        override fun putInt(k: String, v: Int) = apply { map[k] = v }
        override fun putLong(k: String, v: Long) = apply { map[k] = v }
        override fun putFloat(k: String, v: Float) = apply { map[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { map[k] = v }
        override fun remove(k: String) = apply { map.remove(k) }
        override fun clear() = apply { map.clear() }
        override fun commit() = true
        override fun apply() { }
    }
    override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) { }
    override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) { }
}

object V211Shots {
    val prefs = HashMap<String, MapPrefs>()

    /** The app's folders (V210Shots.files) and working preferences. */
    fun ctx(c: Context): Context = object : android.content.ContextWrapper(V210Shots.files(c)) {
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences = prefs.getOrPut(name) { MapPrefs() }
        override fun getApplicationContext(): Context = this
    }

    fun <A : com.besome.sketch.lib.base.BaseAppCompatActivity> open(c: Context, act: A, intent: Intent = Intent()): View {
        act.attach(ctx(c), intent)
        act.onCreate(null)
        V210Shots.drain()
        return act.root()
    }

    val out get() = V210Shots.out
    const val OWNER = V210Shots.OWNER
    const val DAY = V210Shots.DAY
    const val NOW = V210Shots.NOW

    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    fun search(q: String) = "/search/repositories?q=" + enc(q) + "&sort=updated&order=desc&per_page=50"

    /** An app's own icon in the app's glass theme: an Obsidian tile with an orange glyph and a soft rim, written as PNG. */
    fun icon(name: String, color: String, glyph: String): java.io.File {
        val f = java.io.File(out, "icons/$name.png"); f.parentFile.mkdirs()
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, 192f, Color.parseColor("#26262C"), Color.parseColor("#0B0B0D"),
                android.graphics.Shader.TileMode.CLAMP)
        cv.drawRoundRect(0f, 0f, 192f, 192f, 46f, 46f, p)
        p.shader = android.graphics.RadialGradient(96f, 120f, 90f, Color.parseColor("#66FF6D24"), Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP)
        cv.drawRoundRect(0f, 0f, 192f, 192f, 46f, 46f, p)
        p.shader = null
        p.color = Color.parseColor(color); p.textSize = 104f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        cv.drawText(glyph, 96f, 132f, p)
        java.io.FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    /** The picture cache entry the store reads before it would download [url]. */
    fun seed(c: Context, url: String, file: java.io.File) {
        val dir = java.io.File(V210Shots.files(c).cacheDir, "playstore"); dir.mkdirs()
        file.copyTo(java.io.File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length + ".img"), overwrite = true)
    }

    fun app(owner: String, repo: String, number: Long, title: String?, desc: String?, stars: Long, updated: Long,
            vn: String?, vc: String?, action: String?, publishes: Int) = PlayStore.App().apply {
        this.owner = owner; this.repo = repo; this.number = number; this.title = title; description = desc; this.stars = stars
        this.updated = updated; versionName = vn; versionCode = vc; this.action = action; publishNumber = publishes
    }

    fun storeData(c: Context) {
        V210Shots.sampleData()
        val apps = listOf(
                app("ann-dev", "SketchwareAI-Notes", 11, "Notes Pro", "Notes with folders, search and a dark theme.", 42, NOW, "2.3", "9", "updated", 4),
                app(OWNER, "SketchwareAI-Vehicle_Parking", 12, "Vehicle Parking", "Parking tickets, slots and a daily report for a parking stand.", 17, NOW - DAY, "4.7.6", "84", "republished", 3),
                app("cy", "SketchwareAI-Public-Timer", 13, null, null, 3, NOW - 4 * DAY, "1.0", "1", "published", 1),
                app("ravi-k", "SketchwareAI-Telugu_Calendar", 14, "Telugu Calendar", "Panchangam, festivals and reminders in Telugu.", 128, NOW - 7 * DAY, null, null, null, 0))
        PlayStore.save(V210Shots.files(c), apps)
        // Notes Pro has no icon of its own (its APK's default icon shows); the others have theirs
        java.io.File(V210Shots.files(c).cacheDir, "playstore").listFiles()?.forEach { it.delete() }
        seed(c, apps[1].iconUrl(), icon("parking", "#FF8A3D", "P"))
        seed(c, apps[3].iconUrl(), icon("calendar", "#FFB347", "త"))
    }

    fun list(c: Context): View {
        storeData(c)
        return open(c, PlayStoreActivity(), Intent())
    }

    const val GRAPHQL = """{"data":{"repository":{"id":"R_1","hasDiscussionsEnabled":true,"discussionCategories":{"nodes":[{"id":"C1","name":"General"},{"id":"C2","name":"Reviews"}]},
        "discussion":{"id":"D_77","reactionGroups":[{"content":"HEART","viewerHasReacted":true,"reactors":{"totalCount":248}},{"content":"EYES","viewerHasReacted":true,"reactors":{"totalCount":1532}},{"content":"ROCKET","viewerHasReacted":false,"reactors":{"totalCount":36}}]},
        "discussions":{"nodes":[
        {"id":"D_9","number":9,"title":"📊 Notes Pro · Play Store","bodyText":"counters","author":{"login":"ann-dev"},"createdAt":"2026-11-18T09:00:00Z","comments":{"totalCount":0},"category":{"name":"General"},"url":"u"},
        {"id":"D_7","number":7,"title":"★★★★★ Best notes app I built from","bodyText":"Imported the .swb, changed the colours and it ran on my phone the first time.","author":{"login":"zed-apps"},"createdAt":"2026-11-18T09:00:00Z","comments":{"totalCount":2},"category":{"name":"Reviews"},"url":"u"}]}}}}"""

    fun page(c: Context): View {
        storeData(c)
        V210Shots.storeAnswers(); V210Shots.seedPictures(c)
        val base = "/repos/ann-dev/SketchwareAI-Notes/contents/playstore"
        val listing = """{"title":"Notes Pro","description":"Write things down fast: folders, search, pinned notes, a dark theme and a home-screen widget. Made with Sketchware AI on a phone.","video":"https://youtu.be/dQw4w9WgXcQ","version_name":"2.3","version_code":"9","action":"updated","publish_number":4,"published_at":$NOW,"stats_discussion":9,"changelog":"✨ New: a home-screen widget\n🛠 Fixed: search kept old results\n⬆️ Material 3 colours from the app's colour"}"""
        GitHubHttp.ANSWERS["$base/listing.json?ref=main"] = """{"content":"${java.util.Base64.getEncoder().encodeToString(listing.toByteArray())}"}"""
        GitHubHttp.ANSWERS["/graphql"] = GRAPHQL
        ProjectRepo.VIEWER = listOf(V210Shots.version(21, "2.3", "9", NOW - DAY, apk = true).also { v ->
            v.assets[0].downloads = 1840; v.assets.add(ProjectRepo.Asset().also { it.name = "Project_v2.3-9.swb"; it.downloads = 312 }) },
                V210Shots.version(22, "2.2", "8", NOW - 5 * DAY, apk = true).also { it.assets[0].downloads = 975 })
        val i = Intent(); i.putExtra("playstore_owner", "ann-dev"); i.putExtra("playstore_repo", "SketchwareAI-Notes")
        return open(c, PlayStoreActivity(), i)
    }

    fun developer(c: Context): View {
        storeData(c)
        GitHubHttp.ANSWERS["/users/ann-dev"] = """{"login":"ann-dev","name":"Ann Devi","bio":"I make small, useful Android apps on my phone with Sketchware AI.","avatar_url":"https://avatars.githubusercontent.com/u/90210?v=4","followers":314,"public_repos":12}"""
        GitHubHttp.ANSWERS[search("topic:sketchwareai-playstore user:ann-dev")] = """{"items":[
            {"id":11,"name":"SketchwareAI-Notes","private":false,"owner":{"login":"ann-dev"},"description":"Notes with folders, search and a dark theme.","stargazers_count":42,"pushed_at":"2026-11-19T10:00:00Z","default_branch":"main"},
            {"id":15,"name":"SketchwareAI-Budget","private":false,"owner":{"login":"ann-dev"},"description":"Monthly budget with charts.","stargazers_count":9,"pushed_at":"2026-11-10T10:00:00Z","default_branch":"main"}]}"""
        GitHubHttp.ANSWERS[search("topic:sketchware-ai user:ann-dev")] = """{"items":[
            {"id":11,"name":"SketchwareAI-Notes","private":false,"owner":{"login":"ann-dev"},"description":"x","stargazers_count":42,"pushed_at":"2026-11-19T10:00:00Z","default_branch":"main"},
            {"id":16,"name":"SketchwareAI-Button_Demo","private":false,"owner":{"login":"ann-dev"},"description":"Material 3 buttons, one of each.","stargazers_count":1,"pushed_at":"2026-11-08T10:00:00Z","default_branch":"main"}]}"""
        ProjectRepo.VIEWER = listOf(V210Shots.version(21, "2.3", "9", NOW - DAY, apk = true).also { it.assets[0].downloads = 1840 })
        seed(c, "https://avatars.githubusercontent.com/u/90210?v=4", icon("avatar", "#FFB347", "A"))
        val i = Intent(); i.putExtra("playstore_developer", "ann-dev")
        return open(c, PlayStoreActivity(), i)
    }

    fun publish(c: Context): View {
        storeData(c)
        val files = V210Shots.files(c)
        val staging = PlayStore.stagingDir(files, 103)
        staging.deleteRecursively(); staging.mkdirs()
        val form = PlayStore.Listing(); form.title = "Notes"; form.description = "Quick notes with folders and a widget."; form.video = ""
        form.saveForm(staging)
        val tints = listOf("#F4511E", "#1F6FEB", "#2EA043")
        for (i in 1..3) {
            val bmp = Bitmap.createBitmap(270, 540, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp); cv.drawColor(Color.parseColor("#141418"))
            val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = Color.parseColor(tints[i - 1]); cv.drawRect(0f, 0f, 270f, 90f, p)
            java.io.FileOutputStream(java.io.File(staging, "0$i.jpg")).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        ctx(c).getSharedPreferences("playstore_publish", 0).edit().putLong("chosen_$OWNER", 103).commit()
        val i = Intent(); i.putExtra("playstore_publish", true)
        return open(c, PlayStoreActivity(), i)
    }

    fun community(c: Context): View {
        V210Shots.sampleData()
        val feed = PlayStore.Feed()
        fun post(o: String, r: String, n: Int, t: String, a: String, cat: String, ago: Long, replies: Int) = PlayStore.Post().apply {
            owner = o; repo = r; number = n; title = t; author = a; category = cat; updated = NOW - ago; time = updated; comments = replies; id = "D$n"
        }
        feed.posts.add(post("ann-dev", "SketchwareAI-Notes", 7, "★★★★★ Best notes app I built from", "zed-apps", "Reviews", 3_600_000, 2))
        feed.posts.add(post("x-forum", "SketchwareAI-Community", 31, "How do I add a RecyclerView with blocks?", "kiran", "Q&A", 2 * 3_600_000, 4))
        feed.posts.add(post(OWNER, "SketchwareAI-Vehicle_Parking", 4, "Show and tell: the parking app is in the Play Store", OWNER, "General", DAY, 9))
        feed.posts.add(post("ravi-k", "SketchwareAI-Telugu_Calendar", 2, "Idea: festival reminders in Hindi too", "meena", "Ideas", 3 * DAY, 1))
        feed.open.add("ann-dev/SketchwareAI-Notes"); feed.open.add("x-forum/SketchwareAI-Community")
        PlayStore.saveFeed(V210Shots.files(c), feed)
        GitHubHttp.ANSWERS.remove("/graphql")
        return open(c, CommunityActivity())
    }

    fun restore(c: Context, tab: Int): View {
        V210Shots.sampleData()
        RepoIcons.ICONS[101L] = icon("parking", "#FF8A3D", "P").path
        RepoIcons.ICONS[103L] = icon("notes", "#FFB347", "N").path
        RepoIcons.LOCAL["651"] = icon("ghdemo", "#F5F5F7", "G").path
        val swb = java.io.File(mod.hey.studios.project.backup.BackupFactory.DIR, "Vehicle_Parking_v4.7.6.swb")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(swb)).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("resources/icons/icon.png")); z.write(icon("parking", "#FF8A3D", "P").readBytes()); z.closeEntry()
        }
        java.io.File(mod.hey.studios.project.backup.BackupFactory.DIR, "Notes_v2.3.swb").writeBytes(ByteArray(10))
        return V210Shots.restore(c, tab)
    }
}

class V211Test {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    @Test fun v211() {
        val c = paparazzi.context
        V210Shots.save(c, "v211_store.png", listOf(V210Shots.shot(c, V211Shots.list(c), 393, 851),
                V210Shots.shot(c, V211Shots.page(c), 393, 851, wrap = true)))
        V210Shots.save(c, "v211_store2.png", listOf(V210Shots.shot(c, V211Shots.developer(c), 393, 851, wrap = true),
                V210Shots.shot(c, V211Shots.publish(c), 393, 851, wrap = true)))
        V210Shots.save(c, "v211_community.png", listOf(V210Shots.shot(c, V211Shots.community(c), 393, 851)))
        V210Shots.save(c, "v211_restore.png", listOf(V210Shots.shot(c, V211Shots.restore(c, 0), 393, 851, wrap = true),
                V210Shots.shot(c, V211Shots.restore(c, 1), 393, 851), V210Shots.shot(c, V211Shots.restore(c, 2), 393, 851)))
    }
}

/** v2.1.1 third review: the app page's hero and capsule row at 360 dp with font scale 1.3 (out/v211_large_store.png). */
class V211LargeFontTest {
    @get:Rule val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT, fontScale = 1.3f), theme = "android:Theme.Material.NoActionBar")

    @Test fun v211Large() {
        val c = paparazzi.context
        V210Shots.save(c, "v211_large_store.png", listOf(V210Shots.shot(c, V211Shots.page(c), 360, 760),
                V210Shots.shot(c, V211Shots.list(c), 360, 760)))
    }
}
