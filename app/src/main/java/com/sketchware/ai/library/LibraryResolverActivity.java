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
                if (tab == 1) list.postDelayed(refreshSearch, 250);
            }
        });
        select(scId == null ? 1 : 0);
    }

    private final Runnable refreshSearch = () -> {
        if (tab == 1 && !isFinishing()) show();
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
                    for (Installed l : libs) l.size = folderSize(l.dir);
                } catch (RuntimeException e) {
                    libs = new ArrayList<>();
                }
                final List<Installed> read = libs;
                runOnUiThread(() -> {
                    installedCache = read;
                    loadingInstalled = false;
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
            lib.ready = LibraryInstaller.usable(dir);
            out.add(lib);
        }
        return out;
    }

    private List<String> enabledNames() {
        List<String> names = new ArrayList<>();
        if (scId == null) return names;
        ArrayList<HashMap<String, Object>> libraries = LibraryInstaller.enabled(scId);
        if (libraries == null) return names;
        for (HashMap<String, Object> library : libraries) names.add(String.valueOf(library.get("name")));
        return names;
    }

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
            View row = row(name, sub.toString());
            CheckBox check = row.findViewById(R.id.lib_check);
            check.setVisibility(View.VISIBLE);
            check.setChecked(true);
            check.setOnClickListener(v -> {
                setEnabled(name, check.isChecked());
                show();
            });
            if (!missing.isEmpty()) {
                actionButton(row, "Add " + missing.size(), v -> installCoordinates(missing));
            } else if (status.startsWith("Not dexed")) {
                actionButton(row, "Dex", v -> dexNow(dir));
            } else if (status.startsWith("Missing") && needs != null && needs.coordinate != null) {
                actionButton(row, "Reinstall", v -> installCoordinates(Collections.singletonList(needs.coordinate)));
            }
            list.addView(row);
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

    private void showAll() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<String> enabled = enabledNames();
        List<Installed> libs = installedList();
        if (libs == null) {
            note("Reading the installed libraries…");
            return;
        }
        int shown = 0;
        for (Installed lib : libs) {
            if (!query.isEmpty() && !lib.name.toLowerCase(Locale.ROOT).contains(query)
                    && (lib.coordinate == null || !lib.coordinate.toLowerCase(Locale.ROOT).contains(query))) continue;
            shown++;
            String sub = (lib.coordinate != null ? lib.coordinate : "imported file") + " · " + size(lib.size)
                    + (lib.ready ? "" : " · not ready to build");
            View row = row(lib.name, sub);
            if (scId != null) {
                CheckBox check = row.findViewById(R.id.lib_check);
                check.setVisibility(View.VISIBLE);
                check.setChecked(enabled.contains(lib.name));
                check.setOnClickListener(v -> setEnabled(lib.name, check.isChecked()));
            }
            actionButton(row, "Delete", v -> confirmDelete(lib));
            list.addView(row);
        }
        if (shown == 0) note(query.isEmpty() ? "No local libraries installed yet: + Add gets one." : "None installed matches \"" + query + "\".");
        if (query.length() >= 2) {
            if (hits != null && query.equals(hitsQuery)) {
                header("Maven Central");
                if (hits.isEmpty()) note("Nothing found.");
                for (MavenSearch.Hit hit : hits) {
                    View row = row(hit.group + ":" + hit.name, hit.latestVersion + " · " + (hit.packaging == null ? "" : hit.packaging));
                    actionButton(row, "Add", v -> showAddDialog(hit.coordinate()));
                    list.addView(row);
                }
            } else {
                View row = row("Search Maven Central for \"" + query + "\"", "Google Maven libraries (androidx…) are added by coordinate");
                actionButton(row, "Search", v -> searchMaven(query));
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
        for (Installed lib : libs) if (lib.coordinate != null && lib.coordinate.split(":").length >= 3) fromMaven.add(lib);
        if (fromMaven.isEmpty()) {
            note("No installed library came from Maven (downloaded with + Add or the old downloader).");
            return;
        }
        if (!updatesChecked) note("Checks Google Maven, Maven Central and JitPack for newer stable versions.");
        int newer = 0;
        for (Installed lib : fromMaven) {
            String latest = updates.get(lib.name);
            String version = lib.coordinate.split(":")[2];
            if (updatesChecked && latest == null) continue;
            newer++;
            View row = row(lib.name, latest == null ? lib.coordinate : version + " → " + latest);
            if (latest != null) {
                String[] gav = lib.coordinate.split(":");
                actionButton(row, "Update", v -> installCoordinates(Collections.singletonList(gav[0] + ":" + gav[1] + ":" + latest)));
            }
            list.addView(row);
        }
        if (updatesChecked && newer == 0) note("Every library from Maven is at its newest stable version.");
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
        runBusy("Searching Maven Central…", () -> {
            List<MavenSearch.Hit> found = MavenSearch.search(query, 25, null);
            hits = found;
            hitsQuery = query;
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
                MavenResolver.Plan plan = resolver.resolve(coordinate);
                if (!plan.problems.isEmpty() && plan.root == null) {
                    report.append(coordinate).append(": ").append(join(plan.problems)).append('\n');
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
            if (busyDialog != null) busyDialog.setMessage(text);
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
            String text = coordinate.getText().toString().trim();
            if (MavenFetcher.parse(text) == null) {
                message.setText("Write it as group:artifact:version, e.g. com.squareup.retrofit2:converter-gson:2.9.0 "
                        + "(a Gradle line like implementation 'g:a:v' works too).");
                return;
            }
            if (found[0] == null) {
                busy = true;
                message.setText("Reading the POM files…");
                new Thread(() -> {
                    MavenResolver r = new MavenResolver(repositories(), cacheFolder(), null);
                    MavenResolver.Plan resolved;
                    try {
                        resolved = r.resolve(text);
                    } catch (Throwable t) {
                        resolved = new MavenResolver.Plan();
                        resolved.problems.add("Reading the POM files failed: " + t);
                    }
                    final MavenResolver.Plan plan = resolved;
                    runOnUiThread(() -> {
                        busy = false;
                        if (!dialog.isShowing()) return;
                        if (plan.root == null || plan.root.builtIn == null && plan.artifacts.isEmpty()) {
                            message.setText((plan.offline ? "No connection to the repositories.\n" : "") + join(plan.problems)
                                    + (plan.problems.isEmpty() ? "Not found." : ""));
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
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(plan.artifacts.isEmpty() ? "OK" : "Install");
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

    static String describe(MavenResolver.Plan plan) {
        StringBuilder s = new StringBuilder();
        if (plan.root != null && plan.root.builtIn != null) {
            s.append(plan.root.key()).append(" is built into Sketchware AI (").append(plan.root.builtInVersion)
                    .append("): the build adds it when the project uses it. Nothing to download.");
            return s.toString();
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

    /** Runs work off the UI thread behind a small dialog, then shows its report (if any) and the tab again. */
    private void runBusy(String title, Work work) {
        if (busy) return;
        busy = true;
        busyDialog = new ObAlertBuilder(this).setTitle(title).setMessage("…").setCancelable(false).create();
        busyDialog.show();
        new Thread(() -> {
            String report;
            try {
                report = work.run();
            } catch (Throwable t) {
                report = "Failed: " + t;
            }
            final String text = report;
            runOnUiThread(() -> {
                busy = false;
                if (busyDialog != null) busyDialog.dismiss();
                busyDialog = null;
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
