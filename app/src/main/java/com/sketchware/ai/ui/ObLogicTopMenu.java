package com.sketchware.ai.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.editor.logic.LogicTopMenu;

/**
 * v1.9.2: the logic editor's drag bar (Delete | Copy | Collection | Detail) in the dark surface colours. The precompiled
 * LogicTopMenu paints each item white (#FEFEFE) with dark text whenever a dragged block leaves it; after every state
 * change the inactive items get the dark surface and light text again (the active colours stay upstream's).
 */
public class ObLogicTopMenu extends LogicTopMenu {

    private static final int SURFACE = 0xFF1E1F22;
    private static final int ITEM = 0xFF2B2D31;
    private static final int ON_SURFACE = 0xFFF5F5F7;

    public ObLogicTopMenu(Context context) {
        super(context);
        restyle();
    }

    public ObLogicTopMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        restyle();
    }

    private void restyle() {
        setBackgroundColor(SURFACE);
        if (getChildCount() == 1 && getChildAt(0) instanceof LinearLayout) getChildAt(0).setBackgroundColor(SURFACE);
        item(a, b, m, false);
        item(c, d, n, false);
        item(e, f, o, false);
        item(g, h, p, false);
    }

    private static void item(LinearLayout box, TextView label, ImageView icon, boolean active) {
        if (active || box == null) return;
        box.setBackgroundColor(ITEM);
        if (label != null) label.setTextColor(ON_SURFACE);
        if (icon != null) icon.setColorFilter(null);
    }

    @Override
    public void a(boolean active) {
        super.a(active);
        item(a, b, m, active);
    }

    @Override
    public void b(boolean active) {
        super.b(active);
        item(c, d, n, active);
    }

    @Override
    public void c(boolean active) {
        super.c(active);
        item(e, f, o, active);
    }

    @Override
    public void d(boolean active) {
        super.d(active);
        item(g, h, p, active);
    }
}
