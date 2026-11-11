package com.sketchware.ai.ui;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.sketchware.remod.R;

/**
 * The parts of the view property panel (design screen, bottom) that are coloured from code.
 * The layouts (view_property, property_group_item, property_grid_item, event_grid_item) carry
 * the Obsidian look; {@link com.besome.sketch.editor.view.ViewProperty} calls these for what it
 * sets at run time: the selected tab, the "See All" tile and the rows of the widget picker.
 * Kept free of project classes so the same code can be rendered in the layout tests.
 */
public final class ObsidianPropertyPanel {

    private static final int TEXT = 0xFFF5F5F7;
    private static final int TEXT_DIM = 0xFFD4D4D8;
    private static final int ACCENT_TEXT = 0xFFFFB877;
    private static final int ACCENT = 0xFFFF9A4D;

    private ObsidianPropertyPanel() {
    }

    /**
     * A Basic / Recent / Event pill: amber with a white label when selected, dark glass with a
     * light label otherwise (the pill itself is the ob_seg_pill selector). Both keep full size
     * and opacity; the classic panel shrank and faded the unselected tabs, which made their
     * labels hard to read.
     */
    public static void tab(View item, TextView title, boolean selected) {
        item.animate().cancel();
        item.setScaleX(1f);
        item.setScaleY(1f);
        item.setAlpha(1f);
        item.setSelected(selected);
        title.setTextColor(selected ? 0xFFFFFFFF : TEXT_DIM);
    }

    /** The floating "See All" tile: amber dots and label. */
    public static void seeAll(ImageView icon, TextView title) {
        icon.setImageTintList(ColorStateList.valueOf(ACCENT));
        title.setTextColor(ACCENT_TEXT);
    }

    /**
     * A row of the widget picker (var_id_spinner_item), after ViewIdSpinnerItem filled it: light
     * text on the dark header pill and on the dark drop-down, amber for the special entries
     * (ids starting with an underscore, such as the FAB), an amber tick on the current one.
     *
     * @param dropDown whether this is a row of the opened list (false: the header itself)
     * @param special  whether the id is one of the underscore entries
     */
    public static void idItem(View item, boolean dropDown, boolean special) {
        // A row of the opened list is a touch target: 40dp instead of the classic 24dp.
        item.setMinimumHeight(dropDown ? (int) (40 * item.getResources().getDisplayMetrics().density) : 0);
        if (item instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) item).setGravity(android.view.Gravity.CENTER_VERTICAL);
        }
        TextView name = item.findViewById(R.id.name);
        if (name != null) {
            name.setTextColor(special ? ACCENT_TEXT : TEXT);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, dropDown ? 13f : 14f);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (!special) {
                name.setTypeface(dropDown ? Typeface.DEFAULT : ObFonts.bold(item.getContext()));
            }
        }
        View tick = item.findViewById(R.id.imgv_selected);
        if (tick instanceof ImageView) {
            ((ImageView) tick).setImageTintList(ColorStateList.valueOf(ACCENT));
        }
    }
}
