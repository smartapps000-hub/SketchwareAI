package com.sketchware.ai.project;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.eC;
import a.a.a.jC;
import a.a.a.kC;
import mod.jbk.util.LogUtil;

/**
 * The project side of {@link ResourceResolver}: where a project keeps its images, its image
 * list, its backups, and the widgets that name an image.
 */
public final class ProjectImages {

    private static final String TAG = "ProjectImages";
    /** What "Remove broken references" changed, for Undo: data/&lt;sc_id&gt;/reference_history.json. */
    public static final String HISTORY_FILE = "reference_history.json";

    /** One widget property that was cleared because its image doesn't exist. */
    public static final class Removed {
        /** "home.xml" */
        public String file;
        public String viewId;
        /** "image" or "background" */
        public String property;
        /** The image name the property had. */
        public String was;
    }

    private ProjectImages() {
    }

    public static File sketchwareRoot() {
        return new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware");
    }

    /** Looks for the images an AAPT2 link log says are missing and puts the project's own files back. */
    public static ResourceResolver.Outcome resolve(String scId, File buildRes, String aaptLog) {
        List<File> backups = new ArrayList<>();
        boolean drawableMissing = false;
        for (ResourceResolver.Missing item : ResourceResolver.parse(aaptLog)) if ("drawable".equals(item.type)) drawableMissing = true;
        try {
            HashMap<String, Object> meta = drawableMissing ? a.a.a.lC.b(scId) : null;
            if (meta != null) {
                backups = ProjectBackups.archives(new File(mod.hey.studios.project.backup.BackupFactory.getBackupDir()),
                        a.a.a.yB.c(meta, "my_sc_pkg_name"), a.a.a.yB.c(meta, "my_ws_name"));
            }
        } catch (Throwable t) {
            LogUtil.e(TAG, "Couldn't list the project's backups", t);
        }
        ResourceResolver.ImageList images = null;
        try {
            final kC manager = jC.d(scId);
            if (manager != null && manager.b != null) {
                images = new ResourceResolver.ImageList() {
                    @Override
                    public boolean listed(String resName) {
                        return fileName(resName) != null;
                    }

                    @Override
                    public String fileName(String resName) {
                        synchronized (manager) {
                            for (ProjectResourceBean bean : manager.b) {
                                if (bean != null && resName.equalsIgnoreCase(bean.resName)) return bean.resFullName;
                            }
                        }
                        return null;
                    }

                    @Override
                    public void set(String resName, String fileName) {
                        synchronized (manager) {
                            boolean found = false;
                            for (ProjectResourceBean bean : manager.b) {
                                if (bean != null && resName.equalsIgnoreCase(bean.resName)) {
                                    bean.resFullName = fileName;
                                    found = true;
                                }
                            }
                            if (!found) {
                                manager.b.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, resName, fileName));
                            }
                            // The editor's working copy, like every other edit; Save writes it to the project.
                            manager.y();
                        }
                    }
                };
            }
        } catch (Throwable t) {
            LogUtil.e(TAG, "Couldn't open the project's image list", t);
        }
        return ResourceResolver.resolve(scId, sketchwareRoot(), buildRes, backups, images, aaptLog);
    }

    /**
     * For the build of a project: replaces a damaged image in the project's image folder by its
     * repaired copy, after putting the damaged file aside in data/&lt;sc_id&gt;/repair_backup/images.
     */
    public static PngSanitizer.Keeper keeper(final String scId) {
        return (buildFile, repaired) -> {
            try {
                File images = new File(sketchwareRoot(), "resources/images/" + scId);
                File[] files = images.listFiles();
                if (files == null) return null;
                File original = null;
                for (File file : files) {
                    if (file.isFile() && file.getName().equalsIgnoreCase(buildFile.getName())) original = file;
                }
                // Only the image this build file was copied from, and only if that file is itself damaged
                // (an image injected from another folder with the same name is not the project's image).
                if (original == null) return null;
                if (PngSanitizer.problem(original) == null && PngSanitizer.deepProblem(original) == null) return null;
                File backupDirectory = new File(sketchwareRoot(), "data/" + scId + "/repair_backup/images");
                if (!backupDirectory.isDirectory() && !backupDirectory.mkdirs()) return null;
                File backup = new File(backupDirectory, original.getName());
                if (backup.exists()) backup = new File(backupDirectory, System.currentTimeMillis() + "_" + original.getName());
                byte[] damaged = ResourceResolver.read(original);
                try (OutputStream out = new FileOutputStream(backup)) {
                    out.write(damaged);
                }
                File temporary = new File(original.getPath() + ".tmp");
                try (OutputStream out = new FileOutputStream(temporary)) {
                    out.write(repaired);
                }
                if (!temporary.renameTo(original)) {
                    if (!original.delete() || !temporary.renameTo(original)) {
                        // Put the original back if the swap left nothing.
                        if (!original.exists()) {
                            try (OutputStream out = new FileOutputStream(original)) {
                                out.write(damaged);
                            }
                        }
                        temporary.delete();
                        return null;
                    }
                }
                return ".sketchware/data/" + scId + "/repair_backup/images/" + backup.getName();
            } catch (Exception e) {
                LogUtil.e(TAG, "Couldn't keep the repaired image", e);
                return null;
            }
        };
    }

    /**
     * Clears the widget properties that name images the project doesn't have, in the open
     * project's data. Only properties set in the widget's own Image / Background property are
     * changed; a reference written by hand (Inject, a custom XML file) is left and counted.
     *
     * @return what was cleared (kept for {@link #undoRemove}); empty when nothing could be
     */
    public static List<Removed> removeBrokenReferences(eC data, File dataDirectory, List<ResourceResolver.Missing> missing) {
        List<Removed> removed = new ArrayList<>();
        if (data == null || missing == null) return removed;
        for (ResourceResolver.Missing item : missing) {
            if (item.viewId == null || !"drawable".equals(item.type)) continue;
            ViewBean view = find(data, item.file, item.viewId);
            if (view == null) continue;
            if (view.image != null && item.name.equalsIgnoreCase(view.image.resName)) {
                removed.add(entry(item, "image", view.image.resName));
                view.image.resName = "NONE";
            } else if (view.layout != null && view.layout.backgroundResource != null
                    && item.name.equalsIgnoreCase(view.layout.backgroundResource.replaceAll("\\.9$", ""))) {
                removed.add(entry(item, "background", view.layout.backgroundResource));
                view.layout.backgroundResource = "NONE";
            }
        }
        if (!removed.isEmpty()) {
            try {
                File file = new File(dataDirectory, HISTORY_FILE);
                List<Removed> history = new ArrayList<>(loadHistory(dataDirectory));
                history.addAll(removed);
                while (history.size() > 200) history.remove(0);
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream out = new FileOutputStream(file)) {
                    out.write(new Gson().toJson(history.toArray(new Removed[0])).getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "Couldn't note the removed references", e);
            }
        }
        return removed;
    }

    /** Sets the cleared properties back to the image names they had. */
    public static int undoRemove(eC data, List<Removed> removed) {
        int restored = 0;
        if (data == null || removed == null) return 0;
        for (Removed entry : removed) {
            ViewBean view = find(data, entry.file, entry.viewId);
            if (view == null) continue;
            if ("image".equals(entry.property) && view.image != null && "NONE".equals(view.image.resName)) {
                view.image.resName = entry.was;
                restored++;
            } else if ("background".equals(entry.property) && view.layout != null
                    && (view.layout.backgroundResource == null || "NONE".equals(view.layout.backgroundResource))) {
                view.layout.backgroundResource = entry.was;
                restored++;
            }
        }
        return restored;
    }

    public static List<Removed> loadHistory(File dataDirectory) {
        List<Removed> history = new ArrayList<>();
        try {
            File file = new File(dataDirectory, HISTORY_FILE);
            if (!file.isFile()) return history;
            Removed[] loaded = new Gson().fromJson(new String(ResourceResolver.read(file), StandardCharsets.UTF_8), Removed[].class);
            if (loaded != null) for (Removed entry : loaded) if (entry != null) history.add(entry);
        } catch (Exception e) {
            // No history.
        }
        return history;
    }

    private static Removed entry(ResourceResolver.Missing item, String property, String was) {
        Removed entry = new Removed();
        entry.file = item.file;
        entry.viewId = item.viewId;
        entry.property = property;
        entry.was = was;
        return entry;
    }

    private static ViewBean find(eC data, String xmlName, String viewId) {
        try {
            ArrayList<ViewBean> views = data.d(xmlName);
            if (views != null) {
                for (ViewBean view : views) if (view != null && viewId.equals(view.id)) return view;
            }
            ViewBean fab = data.h(xmlName);
            if (fab != null && viewId.equals(fab.id)) return fab;
        } catch (RuntimeException e) {
            // Not a screen of the project.
        }
        return null;
    }
}
