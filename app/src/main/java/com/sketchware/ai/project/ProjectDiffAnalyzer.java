package com.sketchware.ai.project;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.lC;
import a.a.a.yB;

/**
 * v2.0.4 (owner's 16 Nov directive "on-device project diff engine, auto-changelog and GitHub docs generator"): what a
 * project is, read offline from its saved files (never the editor's open copy: own {@code hC}/{@code eC}/{@code iC}
 * instances, as the background backup reads them), as a small JSON snapshot: name, package, version, min / target SDK,
 * screens with their widgets, components and a fingerprint of every event's blocks, libraries, local libraries,
 * permissions and resource counts. A snapshot is kept per version in {@code data/<sc_id>/snapshots/
 * v<versionName>_<versionCode>_snapshot.json}; comparing it with the previous version's gives the release notes
 * ("✨ New Features", "🛠 Logic & Bug Fixes", "⬆️ Upgrades"), a CHANGELOG entry and a commit message. The first version
 * gets a summary instead. The same snapshot writes the repository's description, topics and README. No network, no AI.
 * <p>
 * v2.0.5 (owner's 16 Nov directive "upgrade ProjectDiffAnalyzer to read all screens, widgets, blocks, components,
 * libraries and permissions"): snapshot format 2 adds per screen the MoreBlocks, variables, lists and the screen's
 * parts (toolbar, drawer, floating button, full screen, orientation), and for the project its custom views, Java /
 * asset / resource / native-library files. A format-1 snapshot (v2.0.4) is still compared: the new parts are compared
 * only when both sides have them (else every MoreBlock would be reported as new). The README gets a screen table and
 * the MoreBlocks; the description names the screens and what the app uses.
 */
public final class ProjectDiffAnalyzer {

    public static final String FOLDER = "snapshots";
    static final int FORMAT = 2;

    private ProjectDiffAnalyzer() {
    }

    // ------------------------------------------------------------------ snapshot

    /** The project's snapshot now (its saved files). Never throws for a missing part: that part is empty. */
    public static JsonObject snapshot(String scId) {
        JsonObject s = new JsonObject();
        s.addProperty("format", FORMAT);
        s.addProperty("time", System.currentTimeMillis());
        HashMap<String, Object> project = null;
        try {
            project = lC.b(scId);
        } catch (RuntimeException ignored) {
        }
        s.addProperty("name", project == null ? "" : text(yB.c(project, "my_ws_name")));
        s.addProperty("appName", project == null ? "" : text(yB.c(project, "my_app_name")));
        s.addProperty("package", project == null ? "" : text(yB.c(project, "my_sc_pkg_name")));
        s.addProperty("versionName", project == null ? "1.0" : orDefault(yB.c(project, "sc_ver_name"), "1.0"));
        s.addProperty("versionCode", project == null ? "1" : orDefault(yB.c(project, "sc_ver_code"), "1"));
        JsonObject config = readJson(dataFile(scId, "project_config"));
        s.addProperty("minSdk", config == null ? "" : text(string(config, "min_sdk")));
        s.addProperty("targetSdk", config == null ? "" : text(string(config, "target_sdk")));
        s.addProperty("material3", config != null && "true".equals(string(config, "material3_theme")));

        JsonObject screens = new JsonObject();
        try {
            hC files = new hC(scId);
            files.i();
            eC data = new eC(scId);
            data.g();
            data.e();
            ArrayList<ProjectFileBean> activities = files.b();
            if (activities != null) {
                for (ProjectFileBean file : activities) {
                    JsonObject screen = screen(data, file.getJavaName(), file.getXmlName());
                    screen.add("parts", parts(file));
                    screens.add(file.getJavaName(), screen);
                }
            }
            ArrayList<ProjectFileBean> custom = files.c();
            s.addProperty("customViews", custom == null ? 0 : custom.size());
            JsonArray customNames = new JsonArray();
            if (custom != null) {
                for (ProjectFileBean file : custom) {
                    if (file != null && file.getXmlName() != null) customNames.add(file.getXmlName());
                }
            }
            s.add("customViewNames", customNames);
        } catch (RuntimeException e) {
            s.addProperty("readError", String.valueOf(e));
        }
        s.add("screens", screens);

        JsonArray libraries = new JsonArray();
        try {
            iC lib = new iC(scId);
            lib.i();
            for (ProjectLibraryBean bean : new ProjectLibraryBean[]{lib.b(), lib.c(), lib.d(), lib.e()}) {
                if (bean != null && "Y".equals(bean.useYn)) libraries.add(libraryName(bean.libType));
            }
        } catch (RuntimeException ignored) {
            // no library file: none
        }
        s.add("libraries", libraries);
        s.add("localLibraries", namesOf(readArray(dataFile(scId, "local_library")), "name"));
        JsonArray permissions = new JsonArray();
        JsonArray stored = readArray(dataFile(scId, "permission"));
        if (stored != null) {
            for (JsonElement e : stored) {
                if (e.isJsonPrimitive()) permissions.add(shortPermission(e.getAsString()));
            }
        }
        s.add("permissions", permissions);
        JsonObject resources = new JsonObject();
        File root = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/resources");
        for (String kind : new String[]{"images", "sounds", "fonts"}) {
            File[] list = new File(root, kind + "/" + scId).listFiles();
            resources.addProperty(kind, list == null ? 0 : list.length);
        }
        s.add("resources", resources);
        // v2.0.5: the project's own files (Java classes, assets, resources, native libraries), counted
        JsonObject own = new JsonObject();
        File filesRoot = new File(dataDir(scId), "files");
        own.addProperty("java", countFiles(new File(filesRoot, "java"), ".java", 0));
        own.addProperty("assets", countFiles(new File(filesRoot, "assets"), null, 0));
        own.addProperty("resource", countFiles(new File(filesRoot, "resource"), null, 0));
        own.addProperty("native", countFiles(new File(filesRoot, "native_libs"), ".so", 0));
        s.add("files", own);
        return s;
    }

    /** Files under {@code dir} (four levels at most), ending in {@code ending} when given. */
    static int countFiles(File dir, String ending, int depth) {
        File[] list = dir.listFiles();
        if (list == null || depth > 4) return 0;
        int n = 0;
        for (File f : list) {
            if (f.isDirectory()) n += countFiles(f, ending, depth + 1);
            else if (ending == null || f.getName().endsWith(ending)) n++;
        }
        return n;
    }

    /** v2.0.5: a screen's parts from its settings: toolbar, drawer, floating button, full screen, orientation. */
    static JsonArray parts(ProjectFileBean file) {
        JsonArray parts = new JsonArray();
        if (file == null) return parts;
        if (file.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_TOOLBAR)) parts.add("toolbar");
        if (file.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) parts.add("navigation drawer");
        if (file.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB)) parts.add("floating button");
        if (file.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FULLSCREEN)) parts.add("full screen");
        if (file.orientation == ProjectFileBean.ORIENTATION_LANDSCAPE) parts.add("landscape");
        else if (file.orientation == ProjectFileBean.ORIENTATION_BOTH) parts.add("rotates");
        return parts;
    }

    private static JsonObject screen(eC data, String javaName, String xmlName) {
        JsonObject screen = new JsonObject();
        screen.addProperty("xml", xmlName);
        JsonObject views = new JsonObject();
        ArrayList<ViewBean> beans = data.c == null ? null : data.c.get(xmlName);
        if (beans != null) {
            for (ViewBean bean : beans) {
                if (bean == null || bean.id == null) continue;
                String type = bean.convert != null && !bean.convert.isEmpty() ? simpleName(bean.convert)
                        : ViewBean.getViewTypeName(bean.type);
                views.addProperty(bean.id, type == null ? "View" : type);
            }
        }
        screen.add("views", views);
        JsonObject components = new JsonObject();
        ArrayList<ComponentBean> list = data.h == null ? null : data.h.get(javaName);
        if (list != null) {
            for (ComponentBean bean : list) {
                if (bean != null && bean.componentId != null) components.addProperty(bean.componentId, componentName(bean.type));
            }
        }
        screen.add("components", components);
        JsonObject events = new JsonObject();
        int blocks = 0;
        HashMap<String, ArrayList<BlockBean>> logic = data.d == null ? null : data.d.get(javaName);
        if (logic != null) {
            for (Map.Entry<String, ArrayList<BlockBean>> e : new TreeMap<>(logic).entrySet()) {
                ArrayList<BlockBean> eventBlocks = e.getValue();
                if (eventBlocks == null || eventBlocks.isEmpty()) continue;
                JsonArray signatures = new JsonArray();
                for (BlockBean b : eventBlocks) signatures.add(signature(b));
                blocks += eventBlocks.size();
                events.add(e.getKey(), signatures);
            }
        }
        screen.add("events", events);
        screen.addProperty("blocks", blocks);
        // v2.0.5: MoreBlocks, variables and lists of the screen
        JsonArray moreBlocks = new JsonArray();
        ArrayList<android.util.Pair<String, String>> more = data.g == null ? null : data.g.get(javaName);
        if (more != null) {
            for (android.util.Pair<String, String> p : more) if (p != null && p.first != null) moreBlocks.add(p.first);
        }
        screen.add("moreBlocks", moreBlocks);
        JsonObject variables = new JsonObject();
        ArrayList<android.util.Pair<Integer, String>> vars = data.e == null ? null : data.e.get(javaName);
        if (vars != null) {
            for (android.util.Pair<Integer, String> p : vars) {
                if (p == null || p.first == null || p.second == null) continue;
                String name = variableName(p.first, p.second);
                if (name != null) variables.addProperty(name, variableType(p.first, p.second));
            }
        }
        screen.add("variables", variables);
        JsonObject lists = new JsonObject();
        ArrayList<android.util.Pair<Integer, String>> listVars = data.f == null ? null : data.f.get(javaName);
        if (listVars != null) {
            for (android.util.Pair<Integer, String> p : listVars) {
                if (p != null && p.first != null && p.second != null) lists.addProperty(p.second, listType(p.first));
            }
        }
        screen.add("lists", lists);
        return screen;
    }

    /** A variable's name: the stored value for the basic types, the last word before "=" / ";" for a custom one. */
    static String variableName(int type, String value) {
        if (type >= 0 && type <= 3) return value.trim().isEmpty() ? null : value.trim();
        if (type == 9) return null; // a custom import, not a variable
        String v = value;
        int eq = v.indexOf('=');
        if (eq >= 0) v = v.substring(0, eq);
        v = v.replace(";", " ").trim();
        int space = v.lastIndexOf(' ');
        String name = space >= 0 ? v.substring(space + 1) : v;
        return name.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? name : null;
    }

    static String variableType(int type, String value) {
        switch (type) {
            case 0: return "Boolean";
            case 1: return "Number";
            case 2: return "String";
            case 3: return "Map";
            default:
                String v = value.trim();
                int eq = v.indexOf('=');
                if (eq >= 0) v = v.substring(0, eq).trim();
                String[] words = v.split("\\s+");
                for (String w : words) {
                    if (!w.isEmpty() && Character.isUpperCase(w.charAt(0))) return w;
                }
                return "custom";
        }
    }

    static String listType(int type) {
        switch (type) {
            case 1: return "List Number";
            case 2: return "List String";
            case 3: return "List Map";
            default: return "List";
        }
    }

    /** A block's fingerprint: what it does and its inputs (its id and place are not part of it). */
    static int signature(BlockBean b) {
        StringBuilder sb = new StringBuilder();
        sb.append(b.opCode).append('|').append(b.spec);
        if (b.parameters != null) for (String p : b.parameters) sb.append('|').append(p);
        return sb.toString().hashCode();
    }

    /** Keeps the snapshot of this version ({@code snapshots/v1.0_1_snapshot.json}). */
    public static File save(String scId, JsonObject snapshot) {
        File dir = new File(dataDir(scId), FOLDER);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File file = new File(dir, fileName(string(snapshot, "versionName"), string(snapshot, "versionCode")));
        mod.agus.jcoderz.lib.FileUtil.writeFile(file.getAbsolutePath(),
                new GsonBuilder().create().toJson(snapshot));
        return file;
    }

    public static String fileName(String versionName, String versionCode) {
        return "v" + safe(versionName) + "_" + safe(versionCode) + "_snapshot.json";
    }

    /** The newest kept snapshot of another version than {@code current}'s, or null (the first version). */
    public static JsonObject previous(String scId, JsonObject current) {
        File[] files = new File(dataDir(scId), FOLDER).listFiles();
        if (files == null) return null;
        String own = fileName(string(current, "versionName"), string(current, "versionCode"));
        JsonObject best = null;
        long bestTime = Long.MIN_VALUE;
        for (File f : files) {
            if (!f.getName().endsWith("_snapshot.json") || f.getName().equals(own) || f.length() > 4L * 1024 * 1024) continue;
            JsonObject s = readJson(f);
            if (s == null) continue;
            long time = s.has("time") ? s.get("time").getAsLong() : f.lastModified();
            if (time > bestTime) {
                bestTime = time;
                best = s;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the comparison

    /** What changed from {@code before} (null = first version) to {@code now}. */
    public static final class Changes {
        public final List<String> features = new ArrayList<>();
        public final List<String> fixes = new ArrayList<>();
        public final List<String> upgrades = new ArrayList<>();
        public final List<String> removed = new ArrayList<>();
        public boolean first;
        public int blocksChanged;
        public int screensAdded;
        public int widgetsAdded;
        public int moreBlocksAdded;

        public boolean isEmpty() {
            return features.isEmpty() && fixes.isEmpty() && upgrades.isEmpty() && removed.isEmpty();
        }
    }

    public static Changes compare(JsonObject before, JsonObject now) {
        Changes c = new Changes();
        if (before == null) {
            c.first = true;
            return c;
        }
        JsonObject oldScreens = object(before, "screens");
        JsonObject newScreens = object(now, "screens");
        // v2.0.5: the parts format 2 added are compared only when the older snapshot has them too
        boolean detailed = count(before, "format") >= 2 && count(now, "format") >= 2;
        for (String screen : new TreeSet<>(newScreens.keySet())) {
            JsonObject n = newScreens.getAsJsonObject(screen);
            if (!oldScreens.has(screen)) {
                c.features.add("New screen **" + screen + "** (" + object(n, "views").size() + " widgets, "
                        + count(n, "blocks") + " blocks)");
                c.screensAdded++;
                continue;
            }
            JsonObject o = oldScreens.getAsJsonObject(screen);
            List<String> addedViews = added(object(o, "views"), object(n, "views"));
            if (!addedViews.isEmpty()) {
                c.features.add("Added to " + screen + ": " + join(addedViews, 6));
                c.widgetsAdded += addedViews.size();
            }
            List<String> removedViews = added(object(n, "views"), object(o, "views"));
            if (!removedViews.isEmpty()) c.removed.add("Removed from " + screen + ": " + join(removedViews, 6));
            List<String> addedComponents = added(object(o, "components"), object(n, "components"));
            for (String component : addedComponents) c.features.add("New component in " + screen + ": " + component);
            List<String> removedComponents = added(object(n, "components"), object(o, "components"));
            for (String component : removedComponents) c.removed.add("Component removed from " + screen + ": " + component);
            // logic: events added, changed (block by block) or removed
            JsonObject oe = object(o, "events");
            JsonObject ne = object(n, "events");
            List<String> changedEvents = new ArrayList<>();
            List<String> newEvents = new ArrayList<>();
            int changedBlocks = 0;
            for (String event : new TreeSet<>(ne.keySet())) {
                JsonArray now2 = ne.getAsJsonArray(event);
                if (!oe.has(event)) {
                    newEvents.add(readable(event));
                    changedBlocks += now2.size();
                    continue;
                }
                int diff = blockDifference(oe.getAsJsonArray(event), now2);
                if (diff > 0) {
                    changedEvents.add(readable(event));
                    changedBlocks += diff;
                }
            }
            for (String event : oe.keySet()) {
                if (!ne.has(event)) {
                    changedEvents.add(readable(event) + " (removed)");
                    changedBlocks += oe.getAsJsonArray(event).size();
                }
            }
            if (!newEvents.isEmpty()) {
                c.features.add("New logic in " + screen + ": " + join(newEvents, 5));
            }
            if (!changedEvents.isEmpty()) {
                c.fixes.add("Updated & fixed logic in " + screen + " (" + changedBlocks + " block"
                        + (changedBlocks == 1 ? "" : "s") + " modified): " + join(changedEvents, 5));
            }
            c.blocksChanged += changedBlocks;
            if (detailed) {
                // v2.0.5: MoreBlocks, variables, lists and the screen's parts (only when both snapshots have them)
                List<String> newMore = addedItems(array(o, "moreBlocks"), array(n, "moreBlocks"));
                if (!newMore.isEmpty()) c.features.add("New MoreBlock" + (newMore.size() == 1 ? "" : "s") + " in "
                        + screen + ": " + join(newMore, 5));
                List<String> goneMore = addedItems(array(n, "moreBlocks"), array(o, "moreBlocks"));
                if (!goneMore.isEmpty()) c.removed.add("MoreBlock" + (goneMore.size() == 1 ? "" : "s") + " removed from "
                        + screen + ": " + join(goneMore, 5));
                List<String> newVars = added(object(o, "variables"), object(n, "variables"));
                newVars.addAll(added(object(o, "lists"), object(n, "lists")));
                if (!newVars.isEmpty()) c.features.add("New variables in " + screen + ": " + join(newVars, 6));
                List<String> goneVars = added(object(n, "variables"), object(o, "variables"));
                goneVars.addAll(added(object(n, "lists"), object(o, "lists")));
                if (!goneVars.isEmpty()) c.removed.add("Variables removed from " + screen + ": " + join(goneVars, 6));
                List<String> newParts = addedItems(array(o, "parts"), array(n, "parts"));
                if (!newParts.isEmpty()) c.features.add("Screen parts added in " + screen + ": " + join(newParts, 6));
                List<String> goneParts = addedItems(array(n, "parts"), array(o, "parts"));
                if (!goneParts.isEmpty()) c.removed.add("Screen parts removed from " + screen + ": " + join(goneParts, 6));
                c.moreBlocksAdded += newMore.size();
            }
        }
        for (String screen : oldScreens.keySet()) {
            if (!newScreens.has(screen)) c.removed.add("Screen removed: " + screen);
        }
        for (String lib : addedItems(array(before, "libraries"), array(now, "libraries"))) c.features.add("Library: " + lib);
        for (String lib : addedItems(array(before, "localLibraries"), array(now, "localLibraries"))) {
            c.features.add("Local library: " + lib);
        }
        for (String p : addedItems(array(before, "permissions"), array(now, "permissions"))) c.features.add("Permission: " + p);
        for (String lib : addedItems(array(now, "libraries"), array(before, "libraries"))) c.removed.add("Library removed: " + lib);
        for (String p : addedItems(array(now, "permissions"), array(before, "permissions"))) c.removed.add("Permission removed: " + p);
        JsonObject oldRes = object(before, "resources");
        JsonObject newRes = object(now, "resources");
        for (String kind : new String[]{"images", "sounds", "fonts"}) {
            int more = count(newRes, kind) - count(oldRes, kind);
            if (more > 0) c.features.add(more + " new " + (more == 1 ? kind.substring(0, kind.length() - 1) : kind));
        }
        if (detailed) {
            for (String view : addedItems(array(before, "customViewNames"), array(now, "customViewNames"))) {
                c.features.add("New custom view: " + view);
            }
            JsonObject oldFiles = object(before, "files");
            JsonObject newFiles = object(now, "files");
            String[][] kinds = {{"java", "Java file"}, {"assets", "asset"}, {"resource", "resource file"},
                    {"native", "native library"}};
            for (String[] kind : kinds) {
                int more = count(newFiles, kind[0]) - count(oldFiles, kind[0]);
                if (more > 0) c.features.add(more + " new " + kind[1] + (more == 1 ? "" : "s"));
                else if (more < 0) c.removed.add((-more) + " " + kind[1] + (more == -1 ? "" : "s") + " removed");
            }
        }
        String oldVersion = version(before);
        String newVersion = version(now);
        if (!oldVersion.equals(newVersion)) c.upgrades.add("Version " + oldVersion + " → " + newVersion);
        upgrade(c, "Min SDK", string(before, "minSdk"), string(now, "minSdk"));
        upgrade(c, "Target SDK", string(before, "targetSdk"), string(now, "targetSdk"));
        if (!before.has("material3") || before.get("material3").getAsBoolean() != now.get("material3").getAsBoolean()) {
            if (now.get("material3").getAsBoolean() && before.has("material3")) c.upgrades.add("Material 3 theme switched on");
        }
        return c;
    }

    private static void upgrade(Changes c, String what, String before, String now) {
        if (before == null || now == null || before.isEmpty() || now.isEmpty() || before.equals(now)) return;
        c.upgrades.add(what + " " + before + " → " + now);
    }

    /** How many blocks of an event changed: the larger of blocks gone and blocks new (by fingerprint). */
    static int blockDifference(JsonArray before, JsonArray now) {
        HashMap<Integer, Integer> counts = new HashMap<>();
        for (JsonElement e : before) counts.merge(e.getAsInt(), 1, Integer::sum);
        int added = 0;
        for (JsonElement e : now) {
            Integer left = counts.get(e.getAsInt());
            if (left != null && left > 0) {
                counts.put(e.getAsInt(), left - 1);
            } else {
                added++;
            }
        }
        int gone = 0;
        for (int left : counts.values()) gone += left;
        return Math.max(added, gone);
    }

    // ------------------------------------------------------------------ texts

    /** "1.0 (1)". */
    public static String version(JsonObject s) {
        return orDefault(string(s, "versionName"), "1.0") + " (" + orDefault(string(s, "versionCode"), "1") + ")";
    }

    /** The release notes of {@code now} ("What's New & Fixed"), Markdown. */
    public static String releaseNotes(JsonObject now, Changes c) {
        StringBuilder sb = new StringBuilder();
        if (c.first) {
            sb.append("## ").append(title(now)).append(" v").append(version(now)).append(": first release\n\n");
            sb.append(summary(now)).append('\n');
            return sb.toString();
        }
        sb.append("## What's New & Fixed in v").append(version(now)).append("\n\n");
        if (c.isEmpty()) {
            sb.append("Maintenance update: no structural changes since the previous version.\n");
            return sb.toString();
        }
        section(sb, "✨ New Features", c.features);
        section(sb, "🛠 Logic & Bug Fixes", c.fixes);
        section(sb, "⬆️ Upgrades", c.upgrades);
        section(sb, "🧹 Removed", c.removed);
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, List<String> lines) {
        if (lines.isEmpty()) return;
        sb.append("### ").append(title).append('\n');
        for (String line : lines) sb.append("- ").append(line).append('\n');
        sb.append('\n');
    }

    /** One line for the commit that records the version. */
    public static String commitMessage(JsonObject now, Changes c) {
        StringBuilder sb = new StringBuilder("v").append(version(now)).append(": ");
        if (c.first) return sb.append("first release of ").append(title(now)).toString();
        List<String> parts = new ArrayList<>();
        if (c.screensAdded > 0) parts.add("+" + c.screensAdded + " screen" + (c.screensAdded == 1 ? "" : "s"));
        if (c.widgetsAdded > 0) parts.add("+" + c.widgetsAdded + " widget" + (c.widgetsAdded == 1 ? "" : "s"));
        if (c.moreBlocksAdded > 0) parts.add("+" + c.moreBlocksAdded + " MoreBlock" + (c.moreBlocksAdded == 1 ? "" : "s"));
        if (c.blocksChanged > 0) parts.add(c.blocksChanged + " block" + (c.blocksChanged == 1 ? "" : "s") + " changed");
        if (!c.upgrades.isEmpty()) parts.add(c.upgrades.get(0));
        if (parts.isEmpty()) parts.add(c.isEmpty() ? "maintenance update" : "updates");
        return sb.append(join(parts, 4)).toString();
    }

    /** The CHANGELOG.md entry of {@code now}. */
    public static String changelogEntry(JsonObject now, Changes c, long time) {
        String notes = releaseNotes(now, c);
        String date = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new java.util.Date(time));
        // the entry's own heading instead of the notes' first line
        int cut = notes.indexOf('\n');
        String body = cut < 0 ? "" : notes.substring(cut + 1).trim();
        return "## v" + version(now) + " (" + date + ")\n\n" + (body.isEmpty() ? "- First release" : body) + "\n";
    }

    /** CHANGELOG.md from its entries, newest first. */
    public static String changelog(List<String> entries) {
        StringBuilder sb = new StringBuilder("# Changelog\n\nMade with Sketchware AI: each version is a release of "
                + "this repository (the project's .swb backup and, when built, its APK).\n\n");
        for (String e : entries) sb.append(e.trim()).append("\n\n");
        return sb.toString();
    }

    /** The repository's description (GitHub's About), at most 350 characters. */
    public static String description(JsonObject s) {
        StringBuilder sb = new StringBuilder(title(s)).append(": an Android app made with Sketchware AI");
        JsonObject screens = object(s, "screens");
        int count = screens.size();
        sb.append(" with ").append(count).append(count == 1 ? " screen" : " screens");
        if (count > 1) sb.append(" (").append(join(new ArrayList<>(new TreeSet<>(screens.keySet())), 4)).append(')');
        Set<String> uses = new TreeSet<>(components(s));
        uses.addAll(notableWidgets(s));
        for (String lib : strings(array(s, "libraries"))) if (!lib.startsWith("AppCompat")) uses.add(lib);
        if (!uses.isEmpty()) sb.append(". Uses ").append(join(new ArrayList<>(uses), 5));
        String min = string(s, "minSdk");
        String target = string(s, "targetSdk");
        if (target != null && !target.isEmpty()) {
            sb.append(". Android ");
            if (min != null && !min.isEmpty()) sb.append(androidName(min)).append(" to ");
            sb.append(androidName(target));
        }
        if (s.has("material3") && s.get("material3").getAsBoolean()) sb.append(", Material 3");
        sb.append('.');
        return sb.length() > 350 ? sb.substring(0, 349) + "…" : sb.toString();
    }

    /** v2.0.5: widget kinds worth naming (a web view, a map, a video ...), from every screen. */
    static Set<String> notableWidgets(JsonObject s) {
        Set<String> out = new TreeSet<>();
        JsonObject screens = object(s, "screens");
        for (String name : screens.keySet()) {
            JsonObject views = object(screens.getAsJsonObject(name), "views");
            for (String id : views.keySet()) {
                String type = views.get(id).getAsString();
                switch (type) {
                    case "WebView": out.add("WebView"); break;
                    case "MapView": out.add("Google Maps"); break;
                    case "VideoView": out.add("VideoView"); break;
                    case "RecyclerView": case "ListView": case "GridView": out.add("lists"); break;
                    case "ViewPager": case "TabLayout": out.add("tabs"); break;
                    case "BottomNavigationView": out.add("bottom navigation"); break;
                    case "AdView": out.add("AdMob banner"); break;
                    case "LottieAnimationView": out.add("Lottie"); break;
                    default: break;
                }
            }
        }
        return out;
    }

    /** GitHub topics: lower case letters, digits and hyphens, at most 20. */
    public static List<String> topics(JsonObject s) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add("sketchware-ai");
        out.add("sketchware");
        out.add("android");
        out.add("java");
        if (s.has("material3") && s.get("material3").getAsBoolean()) out.add("material3");
        for (String component : components(s)) {
            String c = component.toLowerCase(Locale.ROOT);
            if (c.contains("firebase")) out.add("firebase");
            else if (c.equals("github")) out.add("github-api");
            else if (c.contains("bluetooth")) out.add("bluetooth");
            else if (c.contains("requestnetwork")) out.add("http");
            else if (c.contains("ad")) out.add("admob");
        }
        for (String widget : notableWidgets(s)) {
            switch (widget) {
                case "WebView": out.add("webview"); break;
                case "Google Maps": out.add("google-maps"); break;
                case "AdMob banner": out.add("admob"); break;
                case "Lottie": out.add("lottie"); break;
                default: break;
            }
        }
        for (String lib : strings(array(s, "libraries"))) {
            if ("Firebase".equals(lib)) out.add("firebase");
            else if ("Google Maps".equals(lib)) out.add("google-maps");
            else if ("AdMob".equals(lib)) out.add("admob");
        }
        List<String> list = new ArrayList<>();
        for (String t : out) {
            String clean = topic(t);
            if (clean != null && list.size() < 20) list.add(clean);
        }
        return list;
    }

    /** A topic as GitHub takes it (a-z, 0-9, hyphens, ≤ 50), or null. */
    public static String topic(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT).replace('#', ' ').trim().replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (t.isEmpty()) return null;
        return t.length() > 50 ? t.substring(0, 50) : t;
    }

    /** README.md of the project. */
    public static String readme(JsonObject s, String title, String description, String repoLink) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title == null || title.isEmpty() ? title(s) : title).append("\n\n");
        sb.append("![version](https://img.shields.io/badge/version-").append(badge(version(s))).append("-1f6feb)");
        String min = string(s, "minSdk");
        String target = string(s, "targetSdk");
        if (min != null && !min.isEmpty()) sb.append(" ![min SDK](https://img.shields.io/badge/min%20SDK-").append(badge(min)).append("-2ea043)");
        if (target != null && !target.isEmpty()) sb.append(" ![target SDK](https://img.shields.io/badge/target%20SDK-").append(badge(target)).append("-2ea043)");
        sb.append(" ![Sketchware AI](https://img.shields.io/badge/made%20with-Sketchware%20AI-f4511e)\n\n");
        if (description != null && !description.isEmpty()) sb.append(description).append("\n\n");
        sb.append("## Features\n\n").append(summary(s)).append('\n');
        sb.append(screensTable(s));
        sb.append("## Download\n\n");
        sb.append("Every version is a **release** of this repository with the project's `.swb` backup")
                .append(" (and its APK when it was built). Open the Releases page and pick a version.\n\n");
        sb.append("## Open in Sketchware AI\n\n");
        sb.append("Sketchware AI > **Restore projects** > **Import from GitHub**, paste ")
                .append(repoLink == null ? "this repository's link" : repoLink).append(" and pick a version. ")
                .append("The project is restored as a new project; nothing on your phone is replaced.\n\n");
        sb.append("---\nMade with [Sketchware AI](https://github.com/smartapps000-hub/SketchwareAI).\n");
        return sb.toString();
    }

    /** v2.0.5: "## Screens" with one table row per screen, then the MoreBlocks; empty when there is no screen. */
    static String screensTable(JsonObject s) {
        JsonObject screens = object(s, "screens");
        if (screens.size() == 0) return "";
        StringBuilder sb = new StringBuilder("## Screens\n\n| Screen | Widgets | Logic blocks | Components | Parts |\n"
                + "| --- | ---: | ---: | --- | --- |\n");
        List<String> moreBlocks = new ArrayList<>();
        for (String name : new TreeSet<>(screens.keySet())) {
            JsonObject screen = screens.getAsJsonObject(name);
            List<String> components = new ArrayList<>(new TreeSet<>(valuesOf(object(screen, "components"))));
            sb.append("| ").append(cell(name)).append(" | ").append(object(screen, "views").size()).append(" | ")
                    .append(count(screen, "blocks")).append(" | ").append(cell(join(components, 4))).append(" | ")
                    .append(cell(join(strings(array(screen, "parts")), 4))).append(" |\n");
            for (String more : strings(array(screen, "moreBlocks"))) moreBlocks.add(more + " (" + name + ")");
        }
        sb.append('\n');
        if (!moreBlocks.isEmpty()) {
            sb.append("### MoreBlocks\n\n");
            for (int i = 0; i < moreBlocks.size() && i < 30; i++) sb.append("- ").append(cell(moreBlocks.get(i))).append('\n');
            if (moreBlocks.size() > 30) sb.append("- … and ").append(moreBlocks.size() - 30).append(" more\n");
            sb.append('\n');
        }
        return sb.toString();
    }

    private static List<String> valuesOf(JsonObject o) {
        List<String> out = new ArrayList<>();
        for (String key : o.keySet()) {
            JsonElement e = o.get(key);
            if (e.isJsonPrimitive()) out.add(e.getAsString());
        }
        return out;
    }

    /** A table cell's text: no pipe, no line break. */
    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "/").replace('\n', ' ').replace('\r', ' ');
    }

    /** What the project holds, as a Markdown list. */
    public static String summary(JsonObject s) {
        StringBuilder sb = new StringBuilder();
        JsonObject screens = object(s, "screens");
        int widgets = 0;
        int blocks = 0;
        for (String name : screens.keySet()) {
            widgets += object(screens.getAsJsonObject(name), "views").size();
            blocks += count(screens.getAsJsonObject(name), "blocks");
        }
        sb.append("- **").append(screens.size()).append(screens.size() == 1 ? " screen" : " screens").append("**: ")
                .append(join(new ArrayList<>(new TreeSet<>(screens.keySet())), 8)).append('\n');
        sb.append("- ").append(widgets).append(" widgets, ").append(blocks).append(" logic blocks");
        int moreBlocks = 0;
        int variables = 0;
        for (String name : screens.keySet()) {
            JsonObject screen = screens.getAsJsonObject(name);
            moreBlocks += array(screen, "moreBlocks").size();
            variables += object(screen, "variables").size() + object(screen, "lists").size();
        }
        if (moreBlocks > 0) sb.append(", ").append(moreBlocks).append(moreBlocks == 1 ? " MoreBlock" : " MoreBlocks");
        if (variables > 0) sb.append(", ").append(variables).append(variables == 1 ? " variable" : " variables");
        sb.append('\n');
        List<String> kinds = widgetKinds(s);
        if (!kinds.isEmpty()) sb.append("- Widgets: ").append(join(kinds, 10)).append('\n');
        List<String> custom = strings(array(s, "customViewNames"));
        if (!custom.isEmpty()) sb.append("- Custom views: ").append(join(custom, 8)).append('\n');
        JsonObject own = object(s, "files");
        List<String> files = new ArrayList<>();
        if (count(own, "java") > 0) files.add(count(own, "java") + " Java file" + (count(own, "java") == 1 ? "" : "s"));
        if (count(own, "assets") > 0) files.add(count(own, "assets") + " asset" + (count(own, "assets") == 1 ? "" : "s"));
        if (count(own, "native") > 0) files.add(count(own, "native") + " native librar" + (count(own, "native") == 1 ? "y" : "ies"));
        if (!files.isEmpty()) sb.append("- Own files: ").append(join(files, 4)).append('\n');
        Set<String> components = components(s);
        if (!components.isEmpty()) sb.append("- Components: ").append(join(new ArrayList<>(components), 10)).append('\n');
        List<String> libs = strings(array(s, "libraries"));
        libs.addAll(strings(array(s, "localLibraries")));
        if (!libs.isEmpty()) sb.append("- Libraries: ").append(join(libs, 10)).append('\n');
        List<String> permissions = strings(array(s, "permissions"));
        if (!permissions.isEmpty()) sb.append("- Permissions: ").append(join(permissions, 12)).append('\n');
        String min = string(s, "minSdk");
        String target = string(s, "targetSdk");
        if (target != null && !target.isEmpty()) {
            sb.append("- Android ").append(min == null || min.isEmpty() ? "" : androidName(min) + " (API " + min + ") to ")
                    .append(androidName(target)).append(" (API ").append(target).append(")\n");
        }
        if (s.has("material3") && s.get("material3").getAsBoolean()) sb.append("- Material 3 design\n");
        return sb.toString();
    }

    /** "3 Button, 2 TextView, 1 WebView": widget kinds by count, most first. */
    static List<String> widgetKinds(JsonObject s) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        JsonObject screens = object(s, "screens");
        for (String name : screens.keySet()) {
            JsonObject views = object(screens.getAsJsonObject(name), "views");
            for (String id : views.keySet()) counts.merge(views.get(id).getAsString(), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : entries) out.add(e.getValue() + " " + e.getKey());
        return out;
    }

    public static String title(JsonObject s) {
        String app = string(s, "appName");
        String name = string(s, "name");
        return app != null && !app.isEmpty() ? app : name == null || name.isEmpty() ? "Project" : name.replace('_', ' ');
    }

    private static Set<String> components(JsonObject s) {
        Set<String> out = new TreeSet<>();
        JsonObject screens = object(s, "screens");
        for (String name : screens.keySet()) {
            JsonObject c = object(screens.getAsJsonObject(name), "components");
            for (String id : c.keySet()) out.add(c.get(id).getAsString());
        }
        return out;
    }

    // ------------------------------------------------------------------ names

    static String componentName(int type) {
        switch (type) {
            case 1: return "Intent";
            case 2: return "SharedPreferences";
            case 3: return "Calendar";
            case 4: return "Vibrator";
            case 5: return "Timer";
            case 6: return "Firebase Database";
            case 7: return "Dialog";
            case 8: return "MediaPlayer";
            case 9: return "SoundPool";
            case 10: return "ObjectAnimator";
            case 11: return "Gyroscope";
            case 12: return "Firebase Auth";
            case 13: return "Interstitial Ad";
            case 14: return "Firebase Storage";
            case 15: return "Camera";
            case 16: return "File Picker";
            case 17: return "RequestNetwork";
            case 18: return "TextToSpeech";
            case 19: return "SpeechToText";
            case 20: return "Bluetooth";
            case 21: return "Location";
            case 22: return "Rewarded Ad";
            case 23: return "ProgressDialog";
            case 24: return "DatePicker";
            case 25: return "TimePicker";
            case 26: return "Notification";
            case 27: return "Fragment Adapter";
            case 28: return "Firebase Phone Auth";
            case 29: return "Firebase Dynamic Links";
            case 30: return "Firebase Cloud Messaging";
            case 31: return "Google Sign-In";
            case 32: return "OneSignal";
            case 33: return "Facebook Banner Ad";
            case 34: return "Facebook Interstitial Ad";
            case 36: return "AsyncTask";
            case GitHubCode.TYPE: return "GitHub";
            default: return "Component " + type;
        }
    }

    static String libraryName(int type) {
        switch (type) {
            case ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE: return "Firebase";
            case ProjectLibraryBean.PROJECT_LIB_TYPE_COMPAT: return "AppCompat & Material";
            case ProjectLibraryBean.PROJECT_LIB_TYPE_ADMOB: return "AdMob";
            case ProjectLibraryBean.PROJECT_LIB_TYPE_GOOGLE_MAP: return "Google Maps";
            default: return "Library " + type;
        }
    }

    static String androidName(String api) {
        try {
            switch (Integer.parseInt(api.trim())) {
                case 19: return "4.4";
                case 21: return "5.0";
                case 22: return "5.1";
                case 23: return "6";
                case 24: return "7";
                case 25: return "7.1";
                case 26: return "8";
                case 27: return "8.1";
                case 28: return "9";
                case 29: return "10";
                case 30: return "11";
                case 31: return "12";
                case 32: return "12L";
                case 33: return "13";
                case 34: return "14";
                case 35: return "15";
                case 36: return "16";
                case 37: return "17";
                default: return "API " + api;
            }
        } catch (NumberFormatException e) {
            return api;
        }
    }

    /** "button1_onClick" → "button1 onClick", "_initializeLogic" style keys kept readable. */
    static String readable(String eventKey) {
        int cut = eventKey.lastIndexOf('_');
        if (cut <= 0 || cut == eventKey.length() - 1) return eventKey;
        String target = eventKey.substring(0, cut);
        String event = eventKey.substring(cut + 1);
        if ("moreBlock".equals(event)) return "MoreBlock " + target;
        if ("onCreate".equals(target) && "initializeLogic".equals(event)) return "onCreate";
        return target + " " + event;
    }

    private static String shortPermission(String p) {
        return p.startsWith("android.permission.") ? p.substring("android.permission.".length()) : p;
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> added(JsonObject before, JsonObject now) {
        List<String> out = new ArrayList<>();
        for (String key : new TreeSet<>(now.keySet())) {
            if (!before.has(key)) out.add(key + " (" + now.get(key).getAsString() + ")");
        }
        return out;
    }

    private static List<String> addedItems(JsonArray before, JsonArray now) {
        Set<String> old = new java.util.HashSet<>(strings(before));
        List<String> out = new ArrayList<>();
        for (String s : strings(now)) if (!old.contains(s)) out.add(s);
        return out;
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (JsonElement e : a) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    private static String join(List<String> items, int most) {
        if (items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size() && i < most; i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
        if (items.size() > most) sb.append(" and ").append(items.size() - most).append(" more");
        return sb.toString();
    }

    private static JsonArray namesOf(JsonArray list, String field) {
        JsonArray out = new JsonArray();
        if (list == null) return out;
        for (JsonElement e : list) {
            if (e.isJsonObject()) {
                String name = string(e.getAsJsonObject(), field);
                if (name != null) out.add(name.replace("_V_", " "));
            }
        }
        return out;
    }

    static JsonObject object(JsonObject parent, String name) {
        JsonElement e = parent == null ? null : parent.get(name);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    static JsonArray array(JsonObject parent, String name) {
        JsonElement e = parent == null ? null : parent.get(name);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    static int count(JsonObject parent, String name) {
        try {
            JsonElement e = parent == null ? null : parent.get(name);
            return e == null || !e.isJsonPrimitive() ? 0 : e.getAsInt();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    public static String string(JsonObject o, String name) {
        JsonElement e = o == null ? null : o.get(name);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }

    private static String orDefault(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }

    private static String badge(String text) {
        return text.replace("-", "--").replace("_", "__").replace(" ", "%20").replace("(", "%28").replace(")", "%29");
    }

    private static String safe(String s) {
        String t = s == null ? "" : s.replaceAll("[^A-Za-z0-9._-]", "_");
        return t.isEmpty() ? "0" : t.length() > 40 ? t.substring(0, 40) : t;
    }

    private static File dataDir(String scId) {
        return new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId);
    }

    private static File dataFile(String scId, String name) {
        return new File(dataDir(scId), name);
    }

    static JsonObject readJson(File f) {
        String text = readText(f);
        if (text == null) return null;
        try {
            JsonElement e = JsonParser.parseString(text);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonArray readArray(File f) {
        String text = readText(f);
        if (text == null || text.trim().isEmpty()) return null;
        try {
            JsonElement e = JsonParser.parseString(text);
            return e.isJsonArray() ? e.getAsJsonArray() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String readText(File f) {
        if (f == null || !f.isFile() || f.length() > 8L * 1024 * 1024) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Tests: two snapshots as text. */
    static String toJson(JsonObject s) {
        return new Gson().toJson(s);
    }
}
