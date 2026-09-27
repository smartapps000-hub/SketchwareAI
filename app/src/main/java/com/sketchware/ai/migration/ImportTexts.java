package com.sketchware.ai.migration;

/** User-facing wording for import progress, shared by the dialog and the notification. */
final class ImportTexts {

    private ImportTexts() {
    }

    static String headline(LegacyProjectMigrator.Progress p) {
        if (p.projectTotal > 0 && p.projectIndex > 0) {
            return "Moving project " + Math.min(p.projectIndex, p.projectTotal)
                    + " of " + p.projectTotal + "…";
        }
        return p.bytesTotal <= 0 ? "Preparing…" : "Copying shared files…";
    }

    static String detail(LegacyProjectMigrator.Progress p) {
        return p.step == null ? "" : p.step;
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(0, bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static String summary(LegacyProjectMigrator.Result r) {
        if (r.error != null) return r.error;
        String base = "Moved back " + r.importedProjects + (r.importedProjects == 1 ? " project" : " projects");
        if (r.skippedProjects > 0) base += " (" + r.skippedProjects + " already present)";
        if (r.cancelled) base += ". Stopped before the rest; you can resume it later.";
        return base;
    }
}
