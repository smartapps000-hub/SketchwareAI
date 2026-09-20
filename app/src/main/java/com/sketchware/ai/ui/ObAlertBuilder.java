package com.sketchware.ai.ui;

import android.app.AlertDialog;
import android.content.Context;

/**
 * Drop-in {@link AlertDialog.Builder} for every framework dialog in the app: the dialog comes out
 * as an Obsidian glass sheet (window theme {@code SketchwareAI.GlassAlertDialog}) and, once
 * shown, {@link ObsidianDialogs#style} turns its buttons into pills and re-colours any classic
 * custom content so no text ends up dark-on-dark.
 */
public class ObAlertBuilder extends AlertDialog.Builder {

    public ObAlertBuilder(Context context) {
        super(context, com.sketchware.remod.R.style.SketchwareAI_GlassAlertDialog);
    }

    /** The theme argument is kept for source compatibility; every dialog uses the glass theme. */
    public ObAlertBuilder(Context context, int themeResId) {
        super(context, com.sketchware.remod.R.style.SketchwareAI_GlassAlertDialog);
    }

    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        dialog.setOnShowListener(d -> ObsidianDialogs.style(dialog));
        return dialog;
    }
}
