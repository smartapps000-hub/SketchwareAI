package com.sketchware.ai.github;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.remod.R;

/**
 * v2.0.1 (owner's 13 Nov directive "glass card UI for backup and restore + classic Local libraries and Custom Blocks
 * options"): the project card's Back up sheet and the Restore card's sheet, in place of the plain lists of v2.0.0.
 * The views are {@link GitHubViews#backupSheet} and {@link GitHubViews#restoreSheet} (layoutlib V201Test draws them).
 * <ul>
 * <li>Back up: "Include used Local libraries" and "Include used Custom Blocks" (the boxes of the old "Backup Options"
 * dialog, remembered), then two glass cards: "Save on phone" (the .swb in the backups folder) and "Back up to GitHub"
 * (exactly that .swb, with the same boxes, to the private {@code SketchwareAI-Projects} repository).</li>
 * <li>Restore: "Restore local .swb file" and "GitHub import project" (own backups or a link).</li>
 * </ul>
 */
public final class ProjectBackupSheets {

    /** "Save on phone": the app's own backup (BackupRestoreManager) with the boxes' choices. */
    public interface LocalBackup {
        void save(boolean localLibraries, boolean customBlocks);
    }

    private ProjectBackupSheets() {
    }

    /** The project card's Back up. */
    public static AlertDialog backup(Activity activity, String scId, String name, LocalBackup local) {
        SharedPreferences prefs = activity.getSharedPreferences(CloudSync.PREFS, Context.MODE_PRIVATE);
        GitHubViews.BackupSheet sheet = new GitHubViews(activity).backupSheet(name,
                TokenStore.get(activity).login() != null, prefs.getBoolean("option_local_libraries", true),
                prefs.getBoolean("option_custom_blocks", true));
        AlertDialog dialog = new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setView(sheet.root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Runnable remember = () -> prefs.edit()
                .putBoolean("option_local_libraries", sheet.libraries.isChecked())
                .putBoolean("option_custom_blocks", sheet.blocks.isChecked())
                .apply();
        sheet.phone.setOnClickListener(x -> {
            remember.run();
            dialog.dismiss();
            local.save(sheet.libraries.isChecked(), sheet.blocks.isChecked());
        });
        sheet.github.setOnClickListener(x -> {
            remember.run();
            dialog.dismiss();
            CloudSync.backUpFromMenu(activity, scId, name,
                    new CloudSync.Options(sheet.libraries.isChecked(), sheet.blocks.isChecked()));
        });
        dialog.show();
        return dialog;
    }

    /** The Restore card: a .swb on this phone, or the import from GitHub. */
    public static AlertDialog restore(Activity activity, Runnable local, Runnable github) {
        GitHubViews.RestoreSheet sheet = new GitHubViews(activity).restoreSheet();
        AlertDialog dialog = new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setView(sheet.root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        sheet.local.setOnClickListener(x -> {
            dialog.dismiss();
            local.run();
        });
        sheet.github.setOnClickListener(x -> {
            dialog.dismiss();
            github.run();
        });
        dialog.show();
        return dialog;
    }
}
