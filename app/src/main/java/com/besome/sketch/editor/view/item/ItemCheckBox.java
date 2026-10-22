package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.appcompat.widget.AppCompatCheckBox;

import com.besome.sketch.beans.ViewBean;
import com.sketchware.ai.editor.DesignTheme;
import com.sketchware.ai.editor.M3Canvas;

import a.a.a.sy;
import a.a.a.wB;

/**
 * Design-canvas stand-in for a {@code <CheckBox>}. Same fields and behaviour as upstream's precompiled class (it
 * replaces it; ViewPane creates it by name). v1.8.5: in a Material 3 project the theme inflates a MaterialCheckBox:
 * the box in the primary colour when checked, on-surface-variant otherwise.
 */
public class ItemCheckBox extends AppCompatCheckBox implements sy {

    public ViewBean b;
    public boolean c;
    public boolean d;
    public Paint e;
    public float f;

    private ColorStateList originalTint;

    public ItemCheckBox(Context context) {
        super(context);
        a(context);
    }

    public void a(Context context) {
        f = wB.a(context, 1.0f);
        e = new Paint(Paint.ANTI_ALIAS_FLAG);
        e.setColor(0x9599d5d0);
        setDrawingCacheEnabled(true);
        originalTint = getButtonTintList();
        applyLook();
    }

    @Override
    public ViewBean getBean() {
        return b;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        b = viewBean;
        applyLook();
    }

    @Override
    public boolean getFixed() {
        return d;
    }

    public void setFixed(boolean fixed) {
        d = fixed;
    }

    public boolean getSelection() {
        return c;
    }

    @Override
    public void setSelection(boolean selection) {
        c = selection;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (c) {
            canvas.drawRect(new Rect(0, 0, getMeasuredWidth(), getMeasuredHeight()), e);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) (left * f), (int) (top * f), (int) (right * f), (int) (bottom * f));
    }

    private boolean tinted;

    private void applyLook() {
        boolean material = M3Canvas.active() && (b == null || com.sketchware.ai.project.M3Widgets.plain(b));
        if (material) {
            setButtonTintList(M3Canvas.compoundTint(DesignTheme.colorPrimary()));
            tinted = true;
        } else if (tinted) {
            // only undo our own tint: setButtonTintList (even with null) replaces the tint AppCompat gave the box
            setButtonTintList(originalTint);
            tinted = false;
        }
    }
}
