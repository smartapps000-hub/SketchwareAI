package com.sketchware.ai.project;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.jbk.util.LogUtil;

/**
 * Called when AAPT2 can't link because a layout names a resource that isn't in the build
 * ("resource drawable/logo not found"). For every such drawable it looks for the project's own
 * file and puts it back:
 * <ol>
 *     <li>the project's image folder: a file with that name that the project's image list
 *     doesn't mention (or mentions with another extension);</li>
 *     <li>the SVG a newer Sketchware Pro saved for an imported icon
 *     (data/&lt;sc_id&gt;/converted-vectors): the vector is made from it again;</li>
 *     <li>this project's own backups (.swb with the same package and project name).</li>
 * </ol>
 * Nothing else counts: an image with the same name in another project, or an icon that merely
 * looks right, is not this project's image and is never used. What can't be found is listed
 * with the screen, the widget and the property that names it, for the compile log and the
 * diagnostic sheet (mysc/&lt;sc_id&gt;/resources_required.json).
 */
public final class ResourceResolver {

    private static final String TAG = "ResourceResolver";
    public static final String FILE_NAME = "resources_required.json";

    private static final Pattern NOT_FOUND = Pattern.compile(
            "(?m)^\\s*(\\S+?):(\\d+): error: resource ([\\w.]+)/([\\w.$]+)(?: \\(aka [^)]*\\))? not found");
    private static final String[] RASTER = {".9.png", ".png", ".jpg", ".jpeg", ".webp", ".gif"};

    /** One reference AAPT2 couldn't resolve. */
    public static final class Missing {
        /** "drawable" */
        public String type;
        public String name;
        /** "home.xml" */
        public String file;
        public String path;
        public int line;
        /** The widget that names the resource ("imageview3"), when the line is a widget of a screen. */
        public String viewId;
        /** "ImageView" */
        public String widget;
        /** "android:src" */
        public String attribute;
        public boolean restored;
        /** Where the file was restored from, in words. */
        public String from;
    }

    /** The project's image list, as far as this class needs it. */
    public interface ImageList {
        boolean listed(String resName);

        /** Puts the image into the project's list under this file name (adds it, or corrects the file name). */
        void set(String resName, String fileName);

        /** The file name the list has for the image, or null. */
        String fileName(String resName);
    }

    public static final class Outcome {
        public final List<Missing> items = new ArrayList<>();
        /** Distinct resources that were put back. */
        public int restored;
        /** Distinct resources that are still missing. */
        public int required;
        /** How many of this project's own backups were looked into. */
        public int backups;
        /** "auto-fix: ..." lines; they go to the build's warnings only if the link passes afterwards. */
        public final List<String> notes = new ArrayList<>();
        /** Missing drawables for which an empty stand-in was written into this build (see {@link #placeholders}). */
        public final List<String> placeholders = new ArrayList<>();

        /** The "Resources required" section for a failed build's log, or "". */
        public String report() {
            if (required == 0) return "";
            StringBuilder text = new StringBuilder("\n\nResources required (" + required + " not on this phone"
                    + (restored > 0 ? ", " + restored + " put back" : "") + "):\n");
            List<String> seen = new ArrayList<>();
            for (Missing item : items) {
                if (item.restored) continue;
                text.append("    ").append(item.file).append(':').append(item.line).append("  ").append(item.type).append('/')
                        .append(item.name);
                if (item.viewId != null) {
                    text.append("  used by ").append(item.widget == null ? "widget" : item.widget).append(' ').append(item.viewId);
                    if (item.attribute != null) text.append(" (").append(item.attribute).append(')');
                }
                text.append('\n');
                if (!seen.contains(item.type + "/" + item.name)) seen.add(item.type + "/" + item.name);
            }
            text.append("    Looked in: the project's image folder, its saved SVG icons, and this project's own backups (")
                    .append(backups).append(backups == 1 ? " backup" : " backups").append(" found). Not there.\n")
                    .append("    No other image was put in their place. Add each image in Image Manager under exactly that name, ")
                    .append("or tap the spark button in this log and choose Remove broken references.");
            return text.toString();
        }
    }

    private ResourceResolver() {
    }

    /** "logo.9.png" → "logo"; "icon_add_round.xml" → "icon_add_round" (lower case). */
    static String baseName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".9.png")) return lower.substring(0, lower.length() - 6);
        int dot = lower.lastIndexOf('.');
        return dot > 0 ? lower.substring(0, dot) : lower;
    }

    /** Every "resource x/y not found" line of an AAPT2 log, once per place. */
    public static List<Missing> parse(String log) {
        List<Missing> items = new ArrayList<>();
        if (log == null) return items;
        Matcher m = NOT_FOUND.matcher(log);
        while (m.find()) {
            Missing item = new Missing();
            item.path = m.group(1);
            item.file = new File(m.group(1)).getName();
            item.line = Integer.parseInt(m.group(2));
            item.type = m.group(3);
            item.name = m.group(4);
            boolean seen = false;
            for (Missing other : items) {
                if (other.path.equals(item.path) && other.line == item.line && other.name.equals(item.name)
                        && other.type.equals(item.type)) seen = true;
            }
            if (!seen) items.add(item);
        }
        return items;
    }

    /**
     * @param sketchwareRoot /storage/emulated/0/.sketchware
     * @param buildRes       the build's res folder (mysc/&lt;sc_id&gt;/app/src/main/res)
     * @param backups        this project's own backups, newest first
     * @param images         the project's image list; null when it can't be changed (the file is
     *                       still put into this build)
     */
    public static Outcome resolve(String scId, File sketchwareRoot, File buildRes, List<File> backups, ImageList images,
                                  String aaptLog) {
        Outcome outcome = new Outcome();
        outcome.items.addAll(parse(aaptLog));
        outcome.backups = backups == null ? 0 : backups.size();
        if (outcome.items.isEmpty()) return outcome;
        Map<String, String> restoredFrom = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (Missing item : outcome.items) {
            try {
                locate(item);
            } catch (Exception e) {
                // The place is still named by file and line.
            }
            if (!"drawable".equals(item.type)) {
                if (!failed.contains(item.type + "/" + item.name)) failed.add(item.type + "/" + item.name);
                continue;
            }
            if (failed.contains("drawable/" + item.name)) continue;
            String from = restoredFrom.get(item.name);
            if (from == null) {
                boolean[] listed = new boolean[1];
                try {
                    from = restore(scId, sketchwareRoot, buildRes, backups, images, item.name, listed);
                } catch (Exception e) {
                    LogUtil.e(TAG, "Couldn't restore " + item.name, e);
                }
                if (from == null) {
                    failed.add("drawable/" + item.name);
                    continue;
                }
                restoredFrom.put(item.name, from);
                outcome.notes.add("auto-fix: the image " + item.name + " that " + item.file + " uses wasn't in the build; " + from
                        + (listed[0] ? " Permanent: it is in the project's image list again (save the project to keep it)."
                        : " Temporary repair: the project's image list couldn't be changed, so the next build does this again."));
            }
            item.restored = true;
            item.from = from;
        }
        outcome.restored = restoredFrom.size();
        outcome.required = failed.size();
        return outcome;
    }

    /**
     * For the drawables that are still missing: writes an empty (fully transparent) vector under
     * that name into this build's res/drawable, so the app can be built and run while the real
     * images are added. Nothing is written into the project; the widgets show nothing there.
     *
     * @return the names a stand-in was written for
     */
    public static List<String> placeholders(Outcome outcome, File buildRes) {
        for (Missing item : outcome.items) {
            if (item.restored || !"drawable".equals(item.type) || outcome.placeholders.contains(item.name)) continue;
            if (!item.name.matches("[a-z0-9_]+")) continue;
            try {
                write(new File(buildRes, "drawable" + File.separator + item.name + ".xml"), PLACEHOLDER.getBytes(StandardCharsets.UTF_8));
                outcome.placeholders.add(item.name);
            } catch (java.io.IOException e) {
                LogUtil.e(TAG, "Couldn't write a stand-in for " + item.name, e);
            }
        }
        return outcome.placeholders;
    }

    private static final String PLACEHOLDER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- Stand-in written by Sketchware AI for an image the project doesn't have. -->\n"
            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\">\n"
            + "    <path android:pathData=\"M0,0h24v24H0z\" android:fillColor=\"#00000000\" />\n"
            + "</vector>\n";

    /** Saves what is still missing for the diagnostic sheet; deletes the file when nothing is. */
    public static void save(File projectMyscDirectory, Outcome outcome) {
        File target = new File(projectMyscDirectory, FILE_NAME);
        try {
            List<Missing> open = new ArrayList<>();
            if (outcome != null) for (Missing item : outcome.items) if (!item.restored) open.add(item);
            if (open.isEmpty()) {
                target.delete();
                return;
            }
            write(target, new Gson().toJson(open.toArray(new Missing[0])).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            target.delete();
        }
    }

    public static List<Missing> load(File projectMyscDirectory) {
        List<Missing> items = new ArrayList<>();
        try {
            File source = new File(projectMyscDirectory, FILE_NAME);
            if (!source.isFile()) return items;
            Missing[] loaded = new Gson().fromJson(new String(read(source), StandardCharsets.UTF_8), Missing[].class);
            if (loaded != null) for (Missing item : loaded) if (item != null && item.name != null) items.add(item);
        } catch (Exception e) {
            // Nothing to show.
        }
        return items;
    }

    /**
     * @return how the image was put back, in words; null when the project has no such file anywhere
     */
    private static String restore(String scId, File sketchwareRoot, File buildRes, List<File> backups, ImageList images,
                                  String name, boolean[] listed) throws java.io.IOException {
        File imageDirectory = new File(sketchwareRoot, "resources/images/" + scId);
        File vectorDirectory = new File(sketchwareRoot, "data/" + scId + "/converted-vectors");
        String from = null;

        // 1. The project's own image folder.
        File found = named(imageDirectory, name, false);
        if (found != null) {
            String known = null;
            try {
                known = images == null ? null : images.fileName(name);
            } catch (RuntimeException ignored) {
            }
            from = known == null
                    ? "its file " + found.getName() + " was in the project's image folder but not in its image list."
                    : "the image list names " + known + ", but the file in the project's image folder is " + found.getName() + ".";
        }
        // 2. The SVG saved with an imported icon.
        if (found == null) {
            File svg = named(imageDirectory, name, true);
            if (svg == null) svg = named(vectorDirectory, name, true);
            if (svg != null) {
                found = vectorFrom(svg, imageDirectory, name);
                if (found != null) from = "it was made again from the icon's SVG that the project kept (" + svg.getName() + ").";
            }
        }
        // 3. This project's own backups.
        if (found == null && backups != null && !backups.isEmpty()) {
            ProjectBackups.Found inBackup = ProjectBackups.findFile(backups, "resources/images", name);
            if (inBackup != null) {
                String fileName = new File(inBackup.entry).getName().toLowerCase(Locale.ROOT);
                File target = new File(imageDirectory, fileName);
                if (ProjectBackups.extract(inBackup.archive, inBackup.entry, target)) {
                    if (fileName.endsWith(".svg")) {
                        found = vectorFrom(target, imageDirectory, name);
                    } else {
                        found = target;
                    }
                    if (found != null) from = "it was restored from this project's backup " + inBackup.archive.getName() + ".";
                }
            }
            if (found == null) {
                inBackup = ProjectBackups.findFile(backups, "data/converted-vectors", name);
                if (inBackup != null && inBackup.entry.toLowerCase(Locale.ROOT).endsWith(".svg")) {
                    File svg = new File(vectorDirectory, name + ".svg");
                    if (ProjectBackups.extract(inBackup.archive, inBackup.entry, svg)) {
                        found = vectorFrom(svg, imageDirectory, name);
                        if (found != null) {
                            from = "it was made again from the icon's SVG in this project's backup " + inBackup.archive.getName() + ".";
                        }
                    }
                }
            }
        }
        if (found == null) return null;

        // Into this build, under the name the layout uses.
        String extension = extension(found.getName());
        File target = new File(buildRes, (extension.equals(".xml") ? "drawable" : "drawable-xhdpi") + File.separator + name + extension);
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        copy(found, target);
        // And into the project's image list, so the next build copies it by itself.
        if (images != null) {
            try {
                String fileName = found.getName().toLowerCase(Locale.ROOT);
                if (!fileName.equals(images.fileName(name))) images.set(name, fileName);
                listed[0] = fileName.equals(images.fileName(name));
            } catch (RuntimeException e) {
                LogUtil.e(TAG, "Couldn't list " + name + " in the project's images", e);
            }
        }
        return from;
    }

    /** Makes images/&lt;name&gt;.xml from an SVG; null when the SVG has nothing convertible. */
    private static File vectorFrom(File svg, File imageDirectory, String name) throws java.io.IOException {
        SvgVector.Result vector = SvgVector.convert(new String(read(svg), StandardCharsets.UTF_8));
        // Only a complete conversion is the same picture: anything left out (a transform, a gradient,
        // a colour name this converter doesn't know) would give another one.
        if (vector.xml == null || !vector.ignored.isEmpty()) return null;
        File target = new File(imageDirectory, name + ".xml");
        write(target, vector.xml.getBytes(StandardCharsets.UTF_8));
        return target;
    }

    /** A file in {@code directory} whose name without extension is {@code name}; images, or (svg) only .svg. */
    private static File named(File directory, String name, boolean svg) {
        File[] files = directory.listFiles();
        if (files == null) return null;
        File best = null;
        for (File file : files) {
            if (!file.isFile() || file.length() == 0 || !baseName(file.getName()).equals(name)) continue;
            String extension = extension(file.getName());
            if (svg) {
                if (extension.equals(".svg")) return file;
                continue;
            }
            if (extension.equals(".xml")) {
                if (best == null) best = file;
                continue;
            }
            for (String raster : RASTER) if (extension.equals(raster)) return file;
        }
        return best;
    }

    private static String extension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".9.png")) return ".9.png";
        int dot = lower.lastIndexOf('.');
        return dot > 0 ? lower.substring(dot) : "";
    }

    private static final Pattern ID = Pattern.compile("android:id\\s*=\\s*\"@\\+?id/([\\w]+)\"");
    private static final Pattern TAG_NAME = Pattern.compile("<\\s*([\\w.]+)");

    /** Reads the element at the error's line: which widget it is and which attribute names the resource. */
    private static void locate(Missing item) throws java.io.IOException {
        File file = new File(item.path);
        if (!file.isFile() || !item.file.endsWith(".xml")) return;
        String[] lines = new String(read(file), StandardCharsets.UTF_8).split("\n", -1);
        if (item.line < 1 || item.line > lines.length) return;
        // The start tag that contains the line: back to its '<', on to its '>'.
        int start = item.line - 1;
        while (start > 0 && !lines[start].contains("<")) start--;
        StringBuilder tag = new StringBuilder();
        for (int i = start; i < lines.length && i < start + 200; i++) {
            tag.append(lines[i]).append('\n');
            if (i >= item.line - 1 && lines[i].contains(">")) break;
        }
        String text = tag.toString();
        // Several tags can be on these lines: take the one that names the resource.
        String reference = "@" + item.type + "/" + item.name;
        int at = text.indexOf("\"" + reference + "\"");
        if (at < 0) return;
        int open = text.lastIndexOf('<', at);
        if (open < 0) return;
        int close = text.indexOf('>', at);
        String element = text.substring(open, close < 0 ? text.length() : close + 1);
        Matcher name = TAG_NAME.matcher(element);
        if (name.find()) {
            String widget = name.group(1);
            item.widget = widget.substring(widget.lastIndexOf('.') + 1);
        }
        Matcher id = ID.matcher(element);
        if (id.find()) item.viewId = id.group(1);
        Matcher attribute = Pattern.compile("([\\w:]+)\\s*=\\s*\"" + Pattern.quote(reference) + "\"").matcher(element);
        if (attribute.find()) item.attribute = attribute.group(1);
    }

    static byte[] read(File file) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
        }
        return bytes.toByteArray();
    }

    private static void write(File target, byte[] bytes) throws java.io.IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File temporary = new File(target.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(temporary)) {
            out.write(bytes);
        }
        if (!temporary.renameTo(target)) {
            target.delete();
            if (!temporary.renameTo(target)) throw new java.io.IOException("can't write " + target);
        }
    }

    private static void copy(File from, File to) throws java.io.IOException {
        write(to, read(from));
    }
}
