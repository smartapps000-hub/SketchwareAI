package com.sketchware.ai.library;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.remod.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import mod.agus.jcoderz.lib.FileUtil;

/**
 * Library Resolver (v1.8.8): the local libraries in four tabs.
 * <ul>
 * <li>Required: the project's libraries, whether each is ready to build, what it needs and isn't switched on.</li>
 * <li>All: every installed library, switched on or off for the project; search the installed ones or Maven Central.</li>
 * <li>Updates: newer stable versions of libraries that came from Maven, installed with what they need.</li>
 * <li>Cache: the download cache and the build caches, with their sizes.</li>
 * </ul>
 * + Add: a Maven coordinate (with or without the libraries it needs; what Sketchware AI has built in is not downloaded),
 * or a .aar / .jar file. Started with extra sc_id (a project id, or "system" / none for all projects).
 */
public class LibraryResolverActivity extends BaseAppCompatActivity {

    private static final int REQUEST_IMPORT = 7302;
    private static final String[] TABS = {"Required", "All", "Updates", "Cache"};

    private String scId;
    private int tab;
    private LinearLayout list;
    private TextView action;
    private EditText search;
    private final List<TextView> tabViews = new ArrayList<>();
    private volatile boolean busy;
    /** Updates found by the last check: folder → newer version. */
    private final HashMap<String, String> updates = new HashMap<>();
    private boolean updatesChecked;
    private List<MavenSearch.Hit> hits;
    private String hitsQuery;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_library_resolver);
        ObsidianWindow.apply(this);
        if (!j()) {
            finish();
            return;
        }
        String id = getIntent().getStringExtra("sc_id");
        scId = id == null || id.equals("system") ? null : id;
        ((TextView) findViewById(R.id.lr_subtitle)).setText(scId == null ? "Every project's local libraries" : "Project #" + scId);
        list = findViewById(R.id.lr_list);
        action = findViewById(R.id.lr_action);
        search = findViewById(R.id.lr_search);
        findViewById(R.id.lr_back).setOnClickListener(v -> finish());
        findViewById(R.id.lr_add).setOnClickListener(v -> showAddDialog(""));
        LinearLayout tabs = findViewById(R.id.lr_tabs);
        for (int i = 0; i < TABS.length; i++) {
            if (scId == null && i == 0) {
                tabViews.add(null);
                continue;
            }
            TextView t = (TextView) LayoutInflater.from(this).inflate(R.layout.ob_lib_tab, tabs, false);
            t.setText(TABS[i]);
            final int index = i;
            t.setOnClickListener(v -> select(index));
            tabs.addView(t);
            tabViews.add(t);
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // filtered when typing pauses
                list.removeCallbacks(refreshSearch);
                list.removeCallbacks(liveSearch);
                if (tab == 1) {
                    list.postDelayed(refreshSearch, 250);
                    // v1.8.9: repositories are searched as you type (after a short pause)
                    if (search.getText().toString().trim().length() >= 3) list.postDelayed(liveSearch, 700);
                }
            }
        });
        select(scId == null ? 1 : 0);
    }

    private final Runnable refreshSearch = () -> {
        if (tab == 1 && !isFinishing()) show();
    };

    /** Live search: the newest query wins; an older answer that arrives late is dropped. */
    private int searchSeq;
    private String searching;
    private String searchFailed;

    private final Runnable liveSearch = () -> {
        if (tab != 1 || isFinishing()) return;
        String typed = search.getText().toString().trim();
        String key = typed.toLowerCase(Locale.ROOT);
        if (typed.length() < 3 || key.equals(hitsQuery) || key.equals(searching)) return;
        final int seq = ++searchSeq;
        searching = key;
        searchFailed = null;
        show();
        new Thread(() -> {
            List<MavenSearch.Hit> found = null;
            String failure = null;
            try {
                found = MavenSearch.search(typed, 40, null);
            } catch (Throwable t) {
                failure = t.getMessage() != null ? t.getMessage() : t.toString();
            }
            final List<MavenSearch.Hit> result = found;
            final String error = failure;
            runOnUiThread(() -> {
                if (seq != searchSeq || isFinishing()) return;
                searching = null;
                if (result != null) {
                    hits = result;
                    hitsQuery = key;
                } else {
                    searchFailed = key + "\u0000" + error;
                }
                if (tab == 1) show();
            });
        }, "LibraryLiveSearch").start();
    };

    private void select(int index) {
        tab = index;
        for (int i = 0; i < tabViews.size(); i++) {
            TextView t = tabViews.get(i);
            if (t == null) continue;
            boolean on = i == index;
            t.setBackgroundResource(on ? R.drawable.ob_amber_pill : R.drawable.ob_pill16_state);
            t.setTextColor(on ? 0xFFFFB877 : 0xFFF5F5F7);
        }
        search.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        show();
    }

    // ------------------------------------------------------------------------------------------- tabs

    private void show() {
        list.removeAllViews();
        action.setVisibility(View.GONE);
        switch (tab) {
            case 0:
                showRequired();
                break;
            case 1:
                showAll();
                break;
            case 2:
                showUpdates();
                break;
            default:
                showCache();
                break;
        }
    }

    private static final class Installed {
        String name;
        File dir;
        String coordinate;
        LibraryNeeds needs;
        boolean ready;
        long size;
        /** v1.8.11: the card's title and project link (LibraryInfo.summary), the version, the install time. */
        String title;
        String link;
        String version;
        long installedAt;

        /** group:artifact, or null for an imported file. */
        String key() {
            if (coordinate == null) return null;
            String[] gav = coordinate.split(":");
            return gav.length >= 3 ? gav[0] + ":" + gav[1] : null;
        }
    }

    /** Installed libraries with their sizes, read off the UI thread; null until read (and after anything changed). */
    private List<Installed> installedCache;
    private boolean loadingInstalled;

    /** The installed libraries, or null while they are being read off the UI thread (the tab is drawn again then). */
    private List<Installed> installedList() {
        if (installedCache != null) return installedCache;
        if (!loadingInstalled) {
            loadingInstalled = true;
            new Thread(() -> {
                List<Installed> libs;
                try {
                    libs = installed();
                    for (Installed l : libs) {
                        l.size = folderSize(l.dir);
                        String[] summary = LibraryInfo.summary(l.dir, l.coordinate, cacheFolder());
                        l.title = summary[0];
                        l.link = summary[1];
                    }
                } catch (RuntimeException e) {
                    libs = new ArrayList<>();
                }
                final List<Installed> read = libs;
                runOnUiThread(() -> {
                    installedCache = read;
                    loadingInstalled = false;
                    for (Installed l : read) {
                        String latest = updates.get(l.name);
                        Installed newer = latest == null ? null : newerInstalled(l, read);
                        if (newer != null && MavenResolver.compareVersions(newer.version, latest) >= 0) updates.remove(l.name);
                    }
                    if (!isFinishing()) show();
                });
            }, "LibraryList").start();
        }
        return null;
    }

    private static List<Installed> installed() {
        List<Installed> out = new ArrayList<>();
        File[] dirs = LibraryInstaller.root().listFiles();
        if (dirs == null) return out;
        Arrays.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File dir : dirs) {
            if (!dir.isDirectory() || dir.getName().startsWith(".")) continue;
            Installed lib = new Installed();
            lib.name = dir.getName();
            lib.dir = dir;
            lib.needs = LibraryNeeds.read(dir);
            lib.coordinate = lib.needs != null ? lib.needs.coordinate : null;
            if (lib.coordinate == null) {
                com.sketchware.ai.project.LibraryIntegrity.Source source = com.sketchware.ai.project.LibraryIntegrity.readSource(dir);
                if (source != null) lib.coordinate = source.coordinate;
            }
            if (lib.coordinate != null && lib.coordinate.split(":").length < 3) lib.coordinate = null;
            lib.version = lib.coordinate != null ? lib.coordinate.split(":")[2] : null;
            lib.ready = LibraryInstaller.usable(dir);
            File classes = new File(dir, "classes.jar");
            lib.installedAt = classes.isFile() ? classes.lastModified() : dir.lastModified();
            out.add(lib);
        }
        return out;
    }

    /**
     * v1.8.11: a library of which a newer version of the same group:artifact is installed too (after an update, the old
     * folder stays for projects that still use it): it is not offered as an update again.
     */
    private static Installed newerInstalled(Installed lib, List<Installed> all) {
        String key = lib.key();
        if (key == null) return null;
        Installed newest = null;
        for (Installed other : all) {
            if (other == lib || !key.equals(other.key()) || other.version == null) continue;
            if (MavenResolver.compareVersions(other.version, lib.version) > 0
                    && (newest == null || MavenResolver.compareVersions(other.version, newest.version) > 0)) newest = other;
        }
        return newest;
    }

    private List<String> enabledNames() {
        List<String> names = new ArrayList<>();
        if (scId == null) return names;
        ArrayList<HashMap<String, Object>> libraries = LibraryInstaller.enabled(scId);
        if (libraries == null) return names;
        for (HashMap<String, Object> library : libraries) names.add(String.valueOf(library.get("name")));
        return names;
    }

    // ------------------------------------------------------------------------------------------- cards

    /** v1.8.11 card of an installed library: name + version badge, coordinate, a status line and the action row. */
    private View card(Installed lib, String meta, boolean withCheck, boolean checked, boolean withDelete) {
        View card = LayoutInflater.from(this).inflate(R.layout.ob_lib_card, list, false);
        TextView name = card.findViewById(R.id.lc_name);
        name.setText(lib.title != null ? lib.title : lib.name);
        name.setSelected(true);   // starts the marquee when the name is longer than the card
        TextView version = card.findViewById(R.id.lc_version);
        if (lib.version != null) {
            version.setText("v" + lib.version.replaceFirst("^v", ""));
            version.setVisibility(View.VISIBLE);
        }
        TextView coordinate = card.findViewById(R.id.lc_coordinate);
        coordinate.setText(lib.coordinate != null ? lib.key() : "imported file · " + lib.name);
        coordinate.setSelected(true);
        ((TextView) card.findViewById(R.id.lc_meta)).setText(meta);
        card.findViewById(R.id.lc_info).setOnClickListener(v -> showInfo(lib.name, lib.dir, lib.coordinate));
        if (lib.link != null) {
            TextView share = card.findViewById(R.id.lc_share);
            share.setVisibility(View.VISIBLE);
            share.setOnClickListener(v -> shareLink(lib));
        }
        TextView status = card.findViewById(R.id.lc_status);
        String latest = updates.get(lib.name);
        Installed newer = installedCache == null ? null : newerInstalled(lib, installedCache);
        if (newer != null) {
            status.setText("Newer: v" + newer.version);
            status.setVisibility(View.VISIBLE);
        } else if (latest != null) {
            status.setText("Update");
            status.setBackgroundResource(R.drawable.ob_amber_pill);
            status.setVisibility(View.VISIBLE);
            String[] gav = lib.coordinate.split(":");
            status.setOnClickListener(v -> installCoordinates(Collections.singletonList(gav[0] + ":" + gav[1] + ":" + latest)));
        } else if (updatesChecked && lib.coordinate != null) {
            status.setText("Latest");
            status.setVisibility(View.VISIBLE);
        }
        if (withDelete) {
            TextView delete = card.findViewById(R.id.lc_delete);
            delete.setVisibility(View.VISIBLE);
            delete.setOnClickListener(v -> confirmDelete(lib));
        }
        if (withCheck && scId != null) {
            CheckBox check = card.findViewById(R.id.lc_check);
            check.setVisibility(View.VISIBLE);
            check.setChecked(checked);
            check.setOnClickListener(v -> setEnabled(lib.name, check.isChecked()));
        }
        return card;
    }

    private void shareLink(Installed lib) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, lib.title != null ? lib.title : lib.name);
        send.putExtra(Intent.EXTRA_TEXT, (lib.coordinate != null ? "implementation '" + lib.coordinate + "'\n" : "") + lib.link);
        try {
            startActivity(Intent.createChooser(send, "Share " + (lib.title != null ? lib.title : lib.name)));
        } catch (RuntimeException e) {
            Toast.makeText(this, lib.link, Toast.LENGTH_LONG).show();
        }
    }

    private Installed find(List<Installed> libs, String name) {
        if (libs != null) for (Installed l : libs) if (l.name.equals(name)) return l;
        return null;
    }

    // ------------------------------------------------------------------------------------------- tabs

    private void showRequired() {
        if (LibraryInstaller.enabled(scId) == null) {
            note("This project's local library list (data/" + scId + "/local_library) can't be read. Nothing here changes it.");
            return;
        }
        List<String> enabled = enabledNames();
        if (enabled.isEmpty()) {
            note("This project uses no local libraries. All shows the installed ones; + Add gets one from Maven or a file.");
            return;
        }
        List<Installed> libs = installedList();
        if (libs == null) {
            note("Reading the installed libraries…");
            return;
        }
        for (String name : enabled) {
            File dir = new File(LibraryInstaller.root(), name);
            LibraryNeeds needs = LibraryNeeds.read(dir);
            StringBuilder sub = new StringBuilder();
            String status;
            if (!dir.isDirectory()) status = "Missing: the folder is gone";
            else if (!new File(dir, "classes.jar").isFile()) status = "Missing classes.jar";
            else if (!new File(dir, "classes.dex").isFile()) status = "Not dexed yet";
            else status = "Ready";
            sub.append(status);
            List<String> missing = new ArrayList<>();
            if (needs != null) {
                if (!needs.builtIn.isEmpty()) sub.append(" · built in: ").append(builtInNames(needs.builtIn));
                for (String need : needs.requires) {
                    String folder = folderOf(need);
                    if (!enabled.contains(folder)) missing.add(need);
                }
            }
            if (!missing.isEmpty()) sub.append("\nNeeds, not switched on: ").append(join(missing));
            Installed lib = find(libs, name);
            if (lib == null) {
                // switched on but not installed: the old row, with what can be done
                View row = row(name, sub.toString());
                if (needs != null && needs.coordinate != null) {
                    actionButton(row, "Reinstall", v -> installCoordinates(Collections.singletonList(needs.coordinate)));
                }
                list.addView(row);
                continue;
            }
            View card = card(lib, sub.toString(), true, true, false);
            TextView status2 = card.findViewById(R.id.lc_status);
            if (!missing.isEmpty()) {
                status2.setText("Add " + missing.size());
                status2.setBackgroundResource(R.drawable.ob_amber_pill);
                status2.setVisibility(View.VISIBLE);
                status2.setOnClickListener(v -> installCoordinates(missing));
            } else if (status.startsWith("Not dexed")) {
                status2.setText("Dex");
                status2.setBackgroundResource(R.drawable.ob_amber_pill);
                status2.setVisibility(View.VISIBLE);
                status2.setOnClickListener(v -> dexNow(dir));
            }
            list.addView(card);
        }
    }

    private static String builtInNames(List<String> constants) {
        List<String> names = new ArrayList<>();
        for (String c : constants) names.add(c.toLowerCase(Locale.ROOT).replace('_', '-'));
        return join(names);
    }

    private static String folderOf(String coordinate) {
        String[] gav = coordinate.split(":");
        MavenResolver.Artifact a = new MavenResolver.Artifact();
        a.group = gav[0];
        a.name = gav[1];
        a.version = gav.length > 2 ? gav[2] : "";
        return LibraryInstaller.folderName(a);
    }

    /** v1.8.11: All tab order: A to Z by name, or updates first then the newest installs. Kept per phone. */
    private boolean sortNewest() {
        try {
            return getPreferences(MODE_PRIVATE).getBoolean("sort_newest", false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void setSortNewest(boolean newest) {
        try {
            getPreferences(MODE_PRIVATE).edit().putBoolean("sort_newest", newest).apply();
        } catch (RuntimeException ignored) {
            // this session only
        }
        show();
    }

    private void sortBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(2), 0, dp(2));
        TextView label = new TextView(this, null, 0, R.style.Ob_Text_Caption);
        label.setText("Sort");
        bar.addView(label);
        boolean newest = sortNewest();
        bar.addView(sortPill("A to Z", !newest, v -> setSortNewest(false)));
        bar.addView(sortPill("Updates & newest first", newest, v -> setSortNewest(true)));
        list.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView sortPill(String text, boolean selected, View.OnClickListener click) {
        TextView pill = new TextView(this, null, 0, R.style.Ob_Text_Button);
        pill.setText(text);
        pill.setTextSize(12);
        pill.setGravity(android.view.Gravity.CENTER);
        pill.setPadding(dp(12), 0, dp(12), 0);
        pill.setBackgroundResource(selected ? R.drawable.ob_amber_pill : R.drawable.ob_pill16_state);
        pill.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        lp.leftMargin = dp(8);
        pill.setLayoutParams(lp);
        return pill;
    }

    private void showAll() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<String> enabled = enabledNames();
        List<Installed> libs = installedList();
        if (libs == null) {
            note("Reading the installed libraries…");
            return;
        }
        List<Installed> shownLibs = new ArrayList<>();
        for (Installed lib : libs) {
            if (!query.isEmpty() && !lib.name.toLowerCase(Locale.ROOT).contains(query)
                    && (lib.coordinate == null || !lib.coordinate.toLowerCase(Locale.ROOT).contains(query))
                    && (lib.title == null || !lib.title.toLowerCase(Locale.ROOT).contains(query))) continue;
            shownLibs.add(lib);
        }
        if (sortNewest()) {
            Collections.sort(shownLibs, (a, b) -> {
                boolean ua = updates.containsKey(a.name), ub = updates.containsKey(b.name);
                if (ua != ub) return ua ? -1 : 1;
                return Long.compare(b.installedAt, a.installedAt);
            });
        } else {
            Collections.sort(shownLibs, (a, b) -> String.valueOf(a.title).compareToIgnoreCase(String.valueOf(b.title)));
        }
        if (shownLibs.size() > 1) sortBar();
        for (Installed lib : shownLibs) {
            String meta = size(lib.size) + (lib.ready ? "" : " · not ready to build");
            list.addView(card(lib, meta, true, enabled.contains(lib.name), true));
        }
        if (shownLibs.isEmpty()) note(query.isEmpty() ? "No local libraries installed yet: + Add gets one." : "None installed matches \"" + query + "\".");
        if (query.length() >= 2) {
            if (hits != null && query.equals(hitsQuery)) {
                header("Search results");
                if (hits.isEmpty()) {
                    note("Nothing found on Maven Central (web and POM-only artifacts are left out: only .aar / .jar libraries). "
                            + "JitPack has no search: paste the library's GitHub link in + Add.");
                }
                for (MavenSearch.Hit hit : hits) {
                    View row = row(hit.group + ":" + hit.name, hit.latestVersion + (hit.packaging == null ? "" : " · " + hit.packaging));
                    TextView title = row.findViewById(R.id.lib_title);
                    title.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                    title.setMarqueeRepeatLimit(-1);
                    title.setSelected(true);
                    actionButton(row, "Add", v -> showAddDialog(hit.coordinate()));
                    list.addView(row);
                }
            } else if (query.equals(searching)) {
                header("Search results");
                note("Searching…");
            } else {
                String failed = searchFailed != null && searchFailed.startsWith(query + "\u0000")
                        ? searchFailed.substring(query.length() + 1) : null;
                View row = row(failed != null ? "Search failed: " + failed : "Search for \"" + query + "\"",
                        "Maven Central by name; group:artifact also finds Google Maven and JitPack");
                actionButton(row, "Search", v -> searchMaven(search.getText().toString().trim()));
                list.addView(row);
            }
        }
    }

    private void showUpdates() {
        List<Installed> libs = installedList();
        if (libs == null) {
            note("Reading the installed libraries…");
            return;
        }
        List<Installed> fromMaven = new ArrayList<>();
        List<Installed> older = new ArrayList<>();
        for (Installed lib : libs) {
            if (lib.coordinate == null) continue;
            if (newerInstalled(lib, libs) != null) older.add(lib);   // v1.8.11: updated already, the old copy stays
            else fromMaven.add(lib);
        }
        if (fromMaven.isEmpty() && older.isEmpty()) {
            note("No installed library came from Maven (downloaded with + Add or the old downloader).");
            return;
        }
        if (!updatesChecked) note("Checks Google Maven, Maven Central and JitPack for newer stable versions.");
        int newer = 0;
        for (Installed lib : fromMaven) {
            String latest = updates.get(lib.name);
            if (updatesChecked && latest == null) continue;
            newer++;
            list.addView(card(lib, latest == null ? size(lib.size) : "v" + lib.version + " → v" + latest, false, false, false));
        }
        if (updatesChecked && newer == 0) note("Every library from Maven is at its newest stable version.");
        if (!older.isEmpty()) {
            header("Older versions (a newer one is installed)");
            note("Projects that still use them keep working; Delete removes the old copy.");
            List<String> enabled = enabledNames();
            for (Installed lib : older) {
                list.addView(card(lib, size(lib.size) + (enabled.contains(lib.name) ? " · used by this project" : ""),
                        false, false, true));
            }
        }
        action.setVisibility(View.VISIBLE);
        action.setText(updatesChecked ? "Check again" : "Check for updates");
        action.setOnClickListener(v -> checkUpdates(fromMaven));
    }

    private static final class CacheEntry {
        final String title, hint;
        final File[] files;

        CacheEntry(String title, String hint, File... files) {
            this.title = title;
            this.hint = hint;
            this.files = files;
        }
    }

    private List<CacheEntry> caches() {
        List<CacheEntry> entries = new ArrayList<>();
        entries.add(new CacheEntry("Maven downloads", "POM files and archives the Library Resolver fetched; fetched again when needed",
                cacheFolder()));
        entries.add(new CacheEntry("Built-in libraries", "unpacked from the app; unpacked again at the next build",
                mod.jbk.build.BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH));
        entries.add(new CacheEntry("Desugared Java library", "DEX files of desugar_jdk_libs per min SDK; made again when needed",
                new File(com.sketchware.ai.project.CoreLibDesugaring.directory(), "cache")));
        List<File> leftovers = new ArrayList<>();
        File[] dirs = LibraryInstaller.root().listFiles();
        if (dirs != null) for (File d : dirs) if (d.getName().startsWith(".") && d.getName().endsWith(".installing")) leftovers.add(d);
        entries.add(new CacheEntry("Unfinished installs", "left when an install stopped", leftovers.toArray(new File[0])));
        if (scId != null) {
            entries.add(new CacheEntry("This project's build files", "deleted at every build anyway",
                    new File(FileUtil.getExternalStorageDir(), ".sketchware/mysc/" + scId)));
        }
        return entries;
    }

    public static File cacheFolder() {
        return new File(FileUtil.getExternalStorageDir(), ".sketchware/libs/cache/maven");
    }

    private void showCache() {
        List<CacheEntry> entries = caches();
        for (CacheEntry entry : entries) {
            View row = row(entry.title, "Counting… · " + entry.hint);
            list.addView(row);
            new Thread(() -> {
                long bytes = 0;
                for (File f : entry.files) bytes += folderSize(f);
                final long total = bytes;
                runOnUiThread(() -> {
                    ((TextView) row.findViewById(R.id.lib_subtitle)).setText(size(total) + " · " + entry.hint);
                    if (total > 0) actionButton(row, "Clear", v -> clear(entry));
                });
            }, "LibraryCacheSize").start();
        }
    }

    private void clear(CacheEntry entry) {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            for (File f : entry.files) FileUtil.deleteFile(f.getAbsolutePath());
            if (entry.files.length > 0 && entry.files[0].equals(mod.jbk.build.BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH)) {
                com.sketchware.ai.project.CoreLibDesugaring.reset();
            }
            runOnUiThread(() -> {
                busy = false;
                Toast.makeText(this, entry.title + ": cleared", Toast.LENGTH_SHORT).show();
                show();
            });
        }, "LibraryCacheClear").start();
    }

    // ------------------------------------------------------------------------------------------- actions

    /** Switches a library on or off for the project (off the UI thread: switching on reads its classes). */
    private void setEnabled(String name, boolean on) {
        if (scId == null) return;
        setResult(RESULT_OK);
        new Thread(() -> {
            String message = null;
            ArrayList<HashMap<String, Object>> libraries = LibraryInstaller.enabled(scId);
            if (libraries == null) {
                message = "The project's local library list can't be read: nothing was changed.";
            } else {
                for (int i = libraries.size() - 1; i >= 0; i--) {
                    if (name.equals(String.valueOf(libraries.get(i).get("name")))) libraries.remove(i);
                }
                if (on) libraries.add(LibraryInstaller.entry(name));
                List<String> linked = new ArrayList<>();
                if (on) {
                    // what it needs and is installed goes on with it (as the local library manager does)
                    try {
                        linked.addAll(com.sketchware.ai.project.LibraryAutoResolve.linkDependencies(libraries,
                                Collections.singletonList(name),
                                new File(mod.jbk.build.BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar").getAbsolutePath(),
                                LibraryInstaller.root()));
                    } catch (RuntimeException ignored) {
                        // switched on alone
                    }
                    LibraryNeeds needs = LibraryNeeds.read(new File(LibraryInstaller.root(), name));
                    if (needs != null) {
                        for (String need : needs.requires) {
                            String folder = folderOf(need);
                            if (new File(LibraryInstaller.root(), folder).isDirectory() && !contains(libraries, folder)) {
                                libraries.add(LibraryInstaller.entry(folder));
                                linked.add(folder);
                            }
                        }
                    }
                }
                LibraryInstaller.save(scId, libraries);
                if (!linked.isEmpty()) message = "Also on (needed by " + name + "): " + join(linked);
            }
            final String text = message;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (text != null) Toast.makeText(this, text, Toast.LENGTH_LONG).show();
                if (tab == 0 || text != null) show();
            });
        }, "LibrarySwitch").start();
    }

    private static boolean contains(List<HashMap<String, Object>> libraries, String name) {
        for (HashMap<String, Object> l : libraries) if (name.equals(String.valueOf(l.get("name")))) return true;
        return false;
    }

    private void confirmDelete(Installed lib) {
        new ObAlertBuilder(this)
                .setTitle("Delete " + lib.name + "?")
                .setMessage("It is removed from this phone. Projects that use it can't build until it is installed again.")
                .setPositiveButton("Delete", (d, w) -> runBusy("Deleting " + lib.name + "…", () -> {
                    FileUtil.deleteFile(lib.dir.getAbsolutePath());
                    if (scId != null) {
                        ArrayList<HashMap<String, Object>> libraries = LibraryInstaller.enabled(scId);
                        if (libraries != null && contains(libraries, lib.name)) {
                            for (int i = libraries.size() - 1; i >= 0; i--) {
                                if (lib.name.equals(String.valueOf(libraries.get(i).get("name")))) libraries.remove(i);
                            }
                            LibraryInstaller.save(scId, libraries);
                            setResult(RESULT_OK);
                        }
                    }
                    return null;
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void dexNow(File dir) {
        runBusy("Dexing " + dir.getName() + "…", () -> {
            LibraryInstaller.dex(new File(dir, "classes.jar"));
            return dir.getName() + " is ready";
        });
    }

    private void searchMaven(String query) {
        runBusy("Searching…", () -> {
            // the typed case is kept: JitPack paths are case-sensitive (com.github.DantSu)
            List<MavenSearch.Hit> found = MavenSearch.search(query, 40, null);
            hits = found;
            hitsQuery = query.toLowerCase(Locale.ROOT);
            return null;
        });
    }

    private void checkUpdates(List<Installed> libs) {
        runBusy("Checking " + libs.size() + " libraries…", () -> {
            MavenResolver resolver = new MavenResolver(repositories(), cacheFolder(), null);
            updates.clear();
            for (Installed lib : libs) {
                String[] gav = lib.coordinate.split(":");
                String latest = resolver.latestStable(gav[0], gav[1]);
                if (latest != null && MavenResolver.compareVersions(latest, gav[2]) > 0) updates.put(lib.name, latest);
            }
            updatesChecked = true;
            return null;
        });
    }

    private void installCoordinates(List<String> coordinates) {
        runBusy("Resolving…", () -> {
            MavenResolver resolver = new MavenResolver(repositories(), cacheFolder(), null);
            StringBuilder report = new StringBuilder();
            for (String coordinate : coordinates) {
                status("Reading the POM files\n" + coordinate);
                MavenResolver.Plan plan = resolver.resolve(coordinate);
                if (!MavenResolver.found(plan)) {
                    report.append(coordinate).append(": ").append(join(plan.problems)).append(networkNotes(plan)).append('\n');
                    continue;
                }
                LibraryInstaller.Outcome outcome = LibraryInstaller.install(resolver, plan, scId, this::status);
                report.append(summary(coordinate, plan, outcome)).append('\n');
            }
            setResult(RESULT_OK);
            return report.toString().trim();
        });
    }

    private void status(String text) {
        runOnUiThread(() -> {
            if (busyStep == null) return;
            busyStep.setText(text);
            busyBar.setIndeterminate(true);
            busyBytes.setText("");
        });
    }

    /** MavenResolver.PROGRESS of the worker thread: at most five updates a second, and the last one. */
    private void bytes(String url, long done, long total) {
        long now = android.os.SystemClock.uptimeMillis();
        boolean last = total > 0 && done >= total;
        if (!last && now - busyShownAt < 200) return;
        busyShownAt = now;
        String file = url.substring(url.lastIndexOf('/') + 1);
        runOnUiThread(() -> {
            if (busyBar == null) return;
            if (total > 0) {
                busyBar.setIndeterminate(false);
                busyBar.setProgress((int) Math.min(1000, done * 1000 / total));
                busyBytes.setText(size(done) + " of " + size(total) + " · " + file);
            } else {
                busyBar.setIndeterminate(true);
                busyBytes.setText(size(done) + " · " + file);
            }
        });
    }

    static String summary(String coordinate, MavenResolver.Plan plan, LibraryInstaller.Outcome outcome) {
        StringBuilder s = new StringBuilder(coordinate).append('\n');
        if (!outcome.installed.isEmpty()) s.append("Installed: ").append(join(outcome.installed)).append('\n');
        if (!outcome.reused.isEmpty()) s.append("Already installed: ").append(join(outcome.reused)).append('\n');
        if (!plan.builtIn.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (MavenResolver.Artifact a : plan.builtIn) names.add(a.name + " " + a.builtInVersion);
            s.append("Built in (added to the build): ").append(join(names)).append('\n');
        }
        if (!outcome.enabled.isEmpty()) s.append("Switched on in this project: ").append(join(outcome.enabled)).append('\n');
        if (!outcome.replaced.isEmpty()) s.append("Switched off (other version): ").append(join(outcome.replaced)).append('\n');
        if (!outcome.failed.isEmpty()) s.append("Failed: ").append(join(outcome.failed)).append('\n');
        for (String note : plan.notes) if (!note.startsWith("No answer")) s.append(note).append('\n');
        for (String problem : plan.problems) s.append(problem).append('\n');
        return s.toString();
    }

    static List<String[]> repositories() {
        List<String[]> repos = new ArrayList<>();
        try {
            String json = FileUtil.readFile(mod.hey.studios.project.library.LibraryDownloader.CONFIGURED_REPOSITORIES_FILE.getAbsolutePath());
            ArrayList<HashMap<String, Object>> list = new com.google.gson.Gson().fromJson(json, mod.hey.studios.util.Helper.TYPE_MAP_LIST);
            if (list != null) {
                for (HashMap<String, Object> repo : list) {
                    Object url = repo.get("url"), name = repo.get("name");
                    if (url != null) repos.add(new String[]{String.valueOf(name), String.valueOf(url)});
                }
            }
        } catch (RuntimeException ignored) {
            // the default list
        }
        return repos.isEmpty() ? MavenResolver.defaultRepositories() : repos;
    }

    // ------------------------------------------------------------------------------------------- add

    private void showAddDialog(String prefill) {
        View view = LayoutInflater.from(this).inflate(R.layout.ob_lib_add, null);
        EditText coordinate = view.findViewById(R.id.la_coordinate);
        CheckBox withNeeds = view.findViewById(R.id.la_with_needs);
        TextView message = view.findViewById(R.id.la_message);
        coordinate.setText(prefill);
        AlertDialog dialog = new ObAlertBuilder(this)
                .setTitle("Add library")
                .setView(view)
                .setPositiveButton("Find", null)
                .setNeutralButton("Import file", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        final MavenResolver.Plan[] found = new MavenResolver.Plan[1];
        final MavenResolver[] resolver = new MavenResolver[1];
        coordinate.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                found[0] = null;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Find");
            }
        });
        withNeeds.setOnCheckedChangeListener((b, checked) -> {
            found[0] = null;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Find");
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            dialog.dismiss();
            pickFile();
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (busy) return;
            String typed = coordinate.getText().toString().trim();
            // v1.8.11: a GitHub / JitPack link is a JitPack coordinate (the newest version JitPack built, without a tag)
            final String[] link = MavenFetcher.parse(typed) == null ? MavenResolver.fromLink(typed) : null;
            if (MavenFetcher.parse(typed) == null && link == null) {
                message.setText("Write it as group:artifact:version, e.g. com.squareup.retrofit2:converter-gson:2.9.0 "
                        + "(a Gradle line like implementation 'g:a:v' or a GitHub link github.com/user/repo works too). "
                        + "To find a library by name, type in the search field instead.");
                return;
            }
            if (found[0] == null) {
                busy = true;
                message.setText("Reading the POM files…");
                new Thread(() -> {
                    MavenResolver r = new MavenResolver(repositories(), cacheFolder(), null);
                    final long[] shownAt = {0};
                    MavenResolver.PROGRESS.set((url, done, total) -> {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - shownAt[0] < 200) return;
                        shownAt[0] = now;
                        String file = url.substring(url.lastIndexOf('/') + 1);
                        String host = Uri.parse(url).getHost();
                        runOnUiThread(() -> {
                            if (dialog.isShowing() && found[0] == null) message.setText("Reading the POM files…\n" + file + " · " + host);
                        });
                    });
                    MavenResolver.Plan resolved;
                    String text = typed;
                    try {
                        String version = link == null ? null : link[2] != null ? link[2] : r.latestStable(link[0], link[1]);
                        if (link != null && version == null) {
                            resolved = new MavenResolver.Plan();
                            resolved.problems.add("JitPack has no built version of " + link[0] + ":" + link[1]
                                    + ". Add the version (a tag from the repository's Releases page), e.g. "
                                    + link[0] + ":" + link[1] + ":1.0.0" + LibraryInstaller.hostLinks(link[0], link[1], null));
                        } else {
                            if (link != null) text = link[0] + ":" + link[1] + ":" + version;
                            resolved = r.resolve(text);
                        }
                    } catch (Throwable t) {
                        resolved = new MavenResolver.Plan();
                        resolved.problems.add("Reading the POM files failed: " + t);
                    } finally {
                        MavenResolver.PROGRESS.remove();
                    }
                    final MavenResolver.Plan plan = resolved;
                    final String asked = text;
                    runOnUiThread(() -> {
                        busy = false;
                        if (!dialog.isShowing()) return;
                        // the link becomes the coordinate it stands for (resets found: set before it)
                        if (!asked.equals(coordinate.getText().toString().trim())) coordinate.setText(asked);
                        if (!MavenResolver.found(plan)) {
                            message.setText((plan.offline ? "No connection to the repositories.\n" : "") + join(plan.problems)
                                    + (plan.problems.isEmpty() ? "Not found." : "") + networkNotes(plan)
                                    + (plan.root == null ? "" : LibraryInstaller.hostLinks(plan.root.group, plan.root.name, plan.root.version)));
                            return;
                        }
                        if (!withNeeds.isChecked()) {
                            // only the library itself
                            List<MavenResolver.Artifact> only = new ArrayList<>();
                            for (MavenResolver.Artifact a : plan.artifacts) if (a == plan.root) only.add(a);
                            plan.artifacts.clear();
                            plan.artifacts.addAll(only);
                        }
                        found[0] = plan;
                        resolver[0] = r;
                        message.setText(describe(plan));
                        boolean installable = false;
                        for (MavenResolver.Artifact a : plan.artifacts) installable |= !"pom".equals(a.packaging);
                        if (!installable) plan.artifacts.clear();
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(installable ? "Install" : "OK");
                    });
                }, "LibraryResolve").start();
                return;
            }
            if (found[0].artifacts.isEmpty()) {
                dialog.dismiss();
                return;
            }
            MavenResolver.Plan plan = found[0];
            MavenResolver r = resolver[0];
            dialog.dismiss();
            runBusy("Installing…", () -> {
                LibraryInstaller.Outcome outcome = LibraryInstaller.install(r, plan, scId, this::status);
                setResult(RESULT_OK);
                return summary(plan.root.coordinate(), plan, outcome).trim();
            });
        });
    }

    /**
     * v1.8.11: why a repository gave no answer (time-out, HTTP 401, no connection), shown when nothing was found:
     * before, only "Not found in the repositories" was shown and the reason stayed hidden.
     */
    static String networkNotes(MavenResolver.Plan plan) {
        StringBuilder s = new StringBuilder();
        int shown = 0;
        for (String note : plan.notes) {
            if (!note.startsWith("No answer")) continue;
            if (shown++ == 6) {
                s.append("\n…");
                break;
            }
            s.append("\n• ").append(note);
        }
        return s.length() == 0 ? "" : "\n\nRepositories that didn't answer:" + s;
    }

    static String describe(MavenResolver.Plan plan) {
        StringBuilder s = new StringBuilder();
        if (plan.root != null && plan.root.builtIn != null) {
            s.append(plan.root.key()).append(" is built into Sketchware AI (").append(plan.root.builtInVersion)
                    .append("): the build adds it when the project uses it. Nothing to download.");
            return s.toString();
        }
        if (plan.root != null && "pom".equals(plan.root.packaging)) {
            // v1.8.11: a POM-only root (a BOM, the root of a multi-module build): nothing of its own to install
            s.append(plan.root.coordinate()).append(" is only a POM: it has no .aar or .jar of its own")
                    .append(plan.artifacts.size() > 1 ? ", only what it lists is installed" : "")
                    .append(". A multi-module JitPack build has one coordinate per module: com.github.User.Repo:module:version.")
                    .append(LibraryInstaller.hostLinks(plan.root.group, plan.root.name, plan.root.version)).append("\n\n");
        }
        List<String> install = new ArrayList<>();
        for (MavenResolver.Artifact a : plan.artifacts) {
            boolean there = LibraryInstaller.usable(new File(LibraryInstaller.root(), LibraryInstaller.folderName(a)));
            install.add(a.coordinate() + " (" + a.packaging + (there ? ", installed" : "") + ")");
        }
        s.append("To install:\n• ").append(join(install).replace(", ", "\n• "));
        if (!plan.builtIn.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (MavenResolver.Artifact a : plan.builtIn) names.add(a.key() + " " + a.builtInVersion);
            s.append("\n\nBuilt in, not downloaded:\n• ").append(join(names).replace(", ", "\n• "));
        }
        for (String note : plan.notes) if (!note.startsWith("No answer")) s.append("\n\n").append(note);
        for (String problem : plan.problems) s.append("\n\n").append(problem);
        return s.toString();
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker on this phone", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String display = "library";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) display = c.getString(0);
        } catch (Exception ignored) {
            // the default name
        }
        String base = display.replaceAll("\\.(zip|aar|jar)$", "").replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        if (base.isEmpty()) base = "library";
        // never over another library's folder
        String unique = base;
        for (int n = 2; new File(LibraryInstaller.root(), unique).exists(); n++) unique = base + "_" + n;
        final String name = unique;
        final String shown = display;
        runBusy("Importing " + display + "…", () -> {
            File copy = new File(getCacheDir(), "import_" + System.currentTimeMillis());
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(copy)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
            File dir = new File(LibraryInstaller.root(), name);
            File staging = new File(LibraryInstaller.root(), "." + name + ".installing");
            FileUtil.deleteFile(staging.getAbsolutePath());
            String reason = MavenFetcher.install(copy, staging);
            copy.delete();
            if (reason != null) {
                FileUtil.deleteFile(staging.getAbsolutePath());
                return shown + ": " + reason;
            }
            com.sketchware.ai.project.LibraryHeal.writeConfig(staging, null);
            try {
                LibraryInstaller.dex(new File(staging, "classes.jar"));
            } catch (Exception e) {
                FileUtil.deleteFile(staging.getAbsolutePath());
                return shown + ": couldn't be dexed: " + (e.getMessage() != null ? e.getMessage() : e.toString());
            }
            if (!staging.renameTo(dir)) {
                FileUtil.deleteFile(staging.getAbsolutePath());
                return shown + ": couldn't move it into " + dir;
            }
            if (scId != null) {
                ArrayList<HashMap<String, Object>> libraries = LibraryInstaller.enabled(scId);
                if (libraries == null) return name + " installed; the project's local library list can't be read, so it isn't switched on";
                if (!contains(libraries, name)) libraries.add(LibraryInstaller.entry(name));
                LibraryInstaller.save(scId, libraries);
                setResult(RESULT_OK);
                return name + " installed and switched on in this project";
            }
            return name + " installed";
        });
    }

    // ------------------------------------------------------------------------------------------- helpers

    private interface Work {
        String run() throws Exception;
    }

    private AlertDialog busyDialog;
    private TextView busyStep, busyBytes;
    private android.widget.ProgressBar busyBar;
    private long busyShownAt;

    /**
     * Runs work off the UI thread behind a dialog with the step it is on and (v1.8.11) a progress bar: the bytes of
     * the download in progress when the server gives its size, else a moving bar. Then shows the report (if any) and
     * the tab again.
     */
    private void runBusy(String title, Work work) {
        if (busy) return;
        busy = true;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(4));
        busyStep = new TextView(this, null, 0, R.style.Ob_Text_Body);
        busyStep.setText("…");
        box.addView(busyStep);
        busyBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        busyBar.setIndeterminate(true);
        busyBar.setMax(1000);
        android.content.res.ColorStateList amber = android.content.res.ColorStateList.valueOf(0xFFFF7A29);
        busyBar.setProgressTintList(amber);
        busyBar.setIndeterminateTintList(amber);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14));
        barParams.topMargin = dp(12);
        box.addView(busyBar, barParams);
        busyBytes = new TextView(this, null, 0, R.style.Ob_Text_Caption);
        box.addView(busyBytes);
        busyDialog = new ObAlertBuilder(this).setTitle(title).setView(box).setCancelable(false).create();
        busyDialog.show();
        new Thread(() -> {
            MavenResolver.PROGRESS.set(this::bytes);
            String report;
            try {
                report = work.run();
            } catch (Throwable t) {
                report = "Failed: " + t;
            } finally {
                MavenResolver.PROGRESS.remove();
            }
            final String text = report;
            runOnUiThread(() -> {
                busy = false;
                if (busyDialog != null) busyDialog.dismiss();
                busyDialog = null;
                busyStep = busyBytes = null;
                busyBar = null;
                if (isFinishing()) return;
                installedCache = null;
                show();
                if (text != null && !text.isEmpty()) {
                    new ObAlertBuilder(this).setTitle("Library Resolver").setMessage(text).setPositiveButton("OK", null).show();
                }
            });
        }, "LibraryResolver").start();
    }

    private View row(String title, String subtitle) {
        View row = LayoutInflater.from(this).inflate(R.layout.ob_lib_row, list, false);
        ((TextView) row.findViewById(R.id.lib_title)).setText(title);
        ((TextView) row.findViewById(R.id.lib_subtitle)).setText(breakable(subtitle));
        return row;
    }

    private void actionButton(View row, String text, View.OnClickListener listener) {
        TextView button = row.findViewById(R.id.lib_action);
        button.setText(text);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(listener);
    }

    /** (i): what is known about the library on this phone (LibraryInfo, offline). */
    private void showInfo(String name, File dir, String coordinate) {
        new Thread(() -> {
            String text;
            String importLines = null;
            try {
                LibraryInfo info = LibraryInfo.read(dir, coordinate, cacheFolder());
                text = info.describe(name, size(folderSize(dir)));
                if (!info.packages.isEmpty()) {
                    StringBuilder lines = new StringBuilder();
                    for (String p : info.packages) lines.append("import ").append(p).append(".*;\n");
                    importLines = lines.toString().trim();
                }
            } catch (Throwable t) {
                text = "Couldn't read the library's files: " + t;
            }
            final String message = text, imports = importLines;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                AlertDialog.Builder b = new ObAlertBuilder(this).setTitle(name).setMessage(message).setPositiveButton("OK", null);
                if (imports != null) {
                    b.setNeutralButton("Copy import", (d, w) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("import", imports));
                            Toast.makeText(this, "Import copied", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                b.show();
            });
        }, "LibraryInfo").start();
    }

    private void note(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setPadding(4, dp(12), 4, dp(4));
        list.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void header(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Label);
        t.setText(text);
        t.setPadding(4, dp(18), 4, dp(2));
        list.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    static long folderSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) total += folderSize(c);
        return total;
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Lets a long Maven coordinate wrap after its colons instead of inside a word. */
    static String breakable(String text) {
        return text == null ? "" : text.replace(":", ":\u200B");
    }

    static String join(List<String> items) {
        return android.text.TextUtils.join(", ", items);
    }
}
