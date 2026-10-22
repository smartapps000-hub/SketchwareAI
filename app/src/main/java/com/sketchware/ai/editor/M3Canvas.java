package com.sketchware.ai.editor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * How the design canvas draws widgets of a Material 3 project (v1.8.5), so the editor shows what the generated app
 * shows under Theme.Material3.DayNight (light): the values are Material Components 1.13's own (Widget.Material3.Button:
 * 4dp insets, 24dp side padding, full corners, labelLarge not all caps; outlined text field: 1dp outline, 4dp corners;
 * MaterialSwitch: 52x32dp track, 16dp / 24dp thumb) and the M3 baseline light colours for the roles the project
 * doesn't set. The project's own primary colour (DesignTheme) fills buttons, checked switches and checkboxes.
 * Only the canvas uses this; nothing here reaches the generated app.
 */
public final class M3Canvas {

    public static final int ON_SURFACE = 0xFF1D1B20;
    public static final int ON_SURFACE_VARIANT = 0xFF49454F;
    public static final int OUTLINE = 0xFF79747E;
    public static final int SURFACE_CONTAINER_HIGHEST = 0xFFE6E0E9;
    public static final int ON_PRIMARY = 0xFFFFFFFF;
    /** Sketchware's default text colours (TextBean) */
    public static final int DEFAULT_TEXT_COLOR = 0xFF000000;
    public static final int DEFAULT_HINT_COLOR = 0xFF607D8B;

    private M3Canvas() {
    }

    /**
     * The open project has the Material 3 theme: its inflater makes Buttons, CheckBoxes and RadioButtons Material
     * widgets (DesignTheme.material3).
     */
    public static boolean active() {
        return DesignTheme.material3();
    }

    /**
     * The generator also rewrites widgets for it (MaterialSwitch, outlined text field, theme text colours):
     * DesignTheme.widgets, false when the user replaced an activity's Java or styles.xml.
     */
    public static boolean widgets() {
        return DesignTheme.widgets();
    }

    public static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    /** Filled button: a full-pill container in the primary colour, 4dp transparent inset at top and bottom. */
    public static Drawable filledButton(Context context, int fill) {
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(dp(context, 1000));
        pill.setColor(fill);
        int inset = Math.round(dp(context, 4));
        return new InsetDrawable(pill, 0, inset, 0, inset);
    }

    public static Typeface labelTypeface() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /** MaterialCheckBox / MaterialRadioButton: primary when checked, on-surface-variant otherwise. */
    public static ColorStateList compoundTint(int primary) {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{primary, ON_SURFACE_VARIANT});
    }

    /** MaterialSwitch thumb: 24dp on-primary circle when checked, 16dp outline circle otherwise (in a 26x32dp cell). */
    public static Drawable switchThumb(Context context) {
        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_checked}, circle(context, ON_PRIMARY, 24));
        thumb.addState(new int[]{}, circle(context, OUTLINE, 16));
        return thumb;
    }

    /** MaterialSwitch track: 52x32dp pill, primary when checked, surface-container-highest with a 2dp outline otherwise. */
    public static Drawable switchTrack(Context context, int primary) {
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, pill(context, primary, 0, 0));
        track.addState(new int[]{}, pill(context, SURFACE_CONTAINER_HIGHEST, OUTLINE, 2));
        return track;
    }

    private static Drawable circle(Context context, int color, int diameterDp) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        int cellWidth = Math.round(dp(context, 26));
        int cellHeight = Math.round(dp(context, 32));
        int size = Math.round(dp(context, diameterDp));
        dot.setSize(size, size);
        LayerDrawable cell = new LayerDrawable(new Drawable[]{dot});
        int horizontal = (cellWidth - size) / 2;
        int vertical = (cellHeight - size) / 2;
        cell.setLayerInset(0, horizontal, vertical, cellWidth - size - horizontal, cellHeight - size - vertical);
        // intrinsic size of the cell = the dot plus its insets
        return cell;
    }

    private static Drawable pill(Context context, int fill, int stroke, int strokeDp) {
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(dp(context, 16));
        pill.setColor(fill);
        if (strokeDp > 0) pill.setStroke(Math.round(dp(context, strokeDp)), stroke);
        pill.setSize(Math.round(dp(context, 52)), Math.round(dp(context, 32)));
        return pill;
    }
}
