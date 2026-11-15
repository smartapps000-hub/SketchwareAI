package com.sketchware.ai.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sketchware.ai.project.ProjectDiffAnalyzer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mod.agus.jcoderz.lib.FileUtil;

/**
 * v2.0.4 (owner's 16 Nov directive "Publish to Public glass sheet, auto-generated description, topics, README, release
 * notes; public projects keep their docs up to date"): what the project's public page says, kept with the project
 * ({@code data/<sc_id>/github_publish.json}, outside the backup fingerprint): title, description, topics, README on or
 * off and its text (generated, or the user's own once edited), the release notes of the newest version and the
 * CHANGELOG entries (one per version, newest first). Every backup refreshes the generated parts from the on-device
 * analyzer ({@link ProjectDiffAnalyzer}); what the user typed is kept.
 */
public final class PublishDetails {

    static final String FILE = "github_publish.json";
    static final int MOST_ENTRIES = 50;

    public String title;
    public String description;
    public final List<String> topics = new ArrayList<>();
    public boolean readmeOn = true;
    /** The README text; {@link #readmeCustom}: the user edited it (it is no longer generated). */
    public String readme;
    public boolean readmeCustom;
    public boolean descriptionCustom;
    public boolean topicsCustom;
    public boolean titleCustom;
    /** The newest version's release notes ("What's New & Fixed") and its tag. */
    public String notes;
    public String notesTag;
    /** CHANGELOG entries, newest first: [tag, text]. */
    public final List<String[]> changelog = new ArrayList<>();

    static File file(String scId) {
        return new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId + "/" + FILE);
    }

    /** The stored details, or empty ones. */
    public static PublishDetails load(String scId) {
        PublishDetails d = new PublishDetails();
        File f = file(scId);
        if (!f.isFile() || f.length() > 2L * 1024 * 1024) return d;
        try {
            JsonObject o = DeviceFlow.parse(FileUtil.readFile(f.getAbsolutePath()));
            d.title = DeviceFlow.string(o, "title");
            d.description = DeviceFlow.string(o, "description");
            JsonElement topics = o.get("topics");
            if (topics != null && topics.isJsonArray()) {
                for (JsonElement t : topics.getAsJsonArray()) if (t.isJsonPrimitive()) d.topics.add(t.getAsString());
            }
            d.readmeOn = !o.has("readme_on") || o.get("readme_on").getAsBoolean();
            d.readme = DeviceFlow.string(o, "readme");
            d.readmeCustom = flag(o, "readme_custom");
            d.descriptionCustom = flag(o, "description_custom");
            d.topicsCustom = flag(o, "topics_custom");
            d.titleCustom = flag(o, "title_custom");
            d.notes = DeviceFlow.string(o, "notes");
            d.notesTag = DeviceFlow.string(o, "notes_tag");
            JsonElement log = o.get("changelog");
            if (log != null && log.isJsonArray()) {
                for (JsonElement e : log.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    String tag = DeviceFlow.string(e.getAsJsonObject(), "tag");
                    String text = DeviceFlow.string(e.getAsJsonObject(), "text");
                    if (tag != null && text != null) d.changelog.add(new String[]{tag, text});
                }
            }
        } catch (RuntimeException ignored) {
            // unreadable: generated again
        }
        return d;
    }

    private static boolean flag(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void save(String scId) {
        File f = file(scId);
        File dir = f.getParentFile();
        if (dir == null || !dir.isDirectory()) return;
        JsonObject o = new JsonObject();
        if (title != null) o.addProperty("title", title);
        if (description != null) o.addProperty("description", description);
        JsonArray t = new JsonArray();
        for (String topic : topics) t.add(topic);
        o.add("topics", t);
        o.addProperty("readme_on", readmeOn);
        if (readme != null) o.addProperty("readme", readme);
        o.addProperty("readme_custom", readmeCustom);
        o.addProperty("description_custom", descriptionCustom);
        o.addProperty("topics_custom", topicsCustom);
        o.addProperty("title_custom", titleCustom);
        if (notes != null) o.addProperty("notes", notes);
        if (notesTag != null) o.addProperty("notes_tag", notesTag);
        JsonArray log = new JsonArray();
        for (String[] e : changelog) {
            JsonObject entry = new JsonObject();
            entry.addProperty("tag", e[0]);
            entry.addProperty("text", e[1]);
            log.add(entry);
        }
        o.add("changelog", log);
        FileUtil.writeFile(f.getAbsolutePath(), o.toString());
    }

    /**
     * Refreshes the generated parts from a snapshot (what the user typed stays): title, description, topics and README
     * when not edited, the notes of {@code tag}, and its CHANGELOG entry (replacing one of the same tag).
     */
    public void refresh(JsonObject snapshot, String tag, String notes, String changelogEntry, String repoLink) {
        if (!titleCustom || title == null) title = ProjectDiffAnalyzer.title(snapshot);
        if (!descriptionCustom || description == null) description = ProjectDiffAnalyzer.description(snapshot);
        if (!topicsCustom || topics.isEmpty()) {
            topics.clear();
            topics.addAll(ProjectDiffAnalyzer.topics(snapshot));
        }
        if (!readmeCustom || readme == null) readme = ProjectDiffAnalyzer.readme(snapshot, title, description, repoLink);
        if (notes != null) {
            this.notes = notes;
            this.notesTag = tag;
        }
        if (changelogEntry != null && tag != null) {
            for (int i = changelog.size() - 1; i >= 0; i--) if (tag.equals(changelog.get(i)[0])) changelog.remove(i);
            changelog.add(0, new String[]{tag, changelogEntry});
            while (changelog.size() > MOST_ENTRIES) changelog.remove(changelog.size() - 1);
        }
    }

    /** CHANGELOG.md of every entry. */
    public String changelogText() {
        List<String> entries = new ArrayList<>();
        for (String[] e : changelog) entries.add(e[1]);
        return ProjectDiffAnalyzer.changelog(entries);
    }
}
