package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.appcompat.widget.SwitchCompat;

import com.besome.sketch.beans.ViewBean;
import com.sketchware.ai.editor.DesignTheme;
import com.sketchware.ai.editor.M3Canvas;

import a.a.a.sy;
import a.a.a.wB;

/**
 * Design-canvas stand-in for a {@code <Switch>}. Same fields and behaviour as upstream's precompiled class (it
 * replaces it; ViewPane creates it by name). v1.8.5: in a Material 3 project the generator writes a MaterialSwitch
 * (M3Widgets), so the canvas draws its 52x32dp track (primary when on, outlined when off) and 24dp / 16dp thumb.
 */
public class ItemSwitch extends SwitchCompat implements sy {

    public ViewBean O;
    public boolean P;
    public boolean Q;
    public Paint R;
    public float S;

    private Drawable originalThumb;
    private Drawable originalTrack;

    public ItemSwitch(Context context) {
        super(context);
        a(context);
    }

    public void a(Context context) {
        S = wB.a(context, 1.0f);
        R = new Paint(Paint.ANTI_ALIAS_FLAG);
        R.setColor(0x9599d5d0);
        setDrawingCacheEnabled(true);
        originalThumb = getThumbDrawable();
        originalTrack = getTrackDrawable();
        applyLook();
    }

    @Override
    public ViewBean getBean() {
        return O;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        O = viewBean;
        applyLook();
    }

    @Override
    public boolean getFixed() {
        return Q;
    }

    public void setFixed(boolean fixed) {
        Q = fixed;
    }

    public boolean getSelection() {
        return P;
    }

    @Override
    public void setSelection(boolean selection) {
        P = selection;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (P) {
            canvas.drawRect(new Rect(0, 0, getMeasuredWidth(), getMeasuredHeight()), R);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) (left * S), (int) (top * S), (int) (right * S), (int) (bottom * S));
    }

    private void applyLook() {
        // same rule as the generator (M3Widgets.isMaterialSwitch): plain Switch, no injected style/thumb/track
        boolean material = M3Canvas.widgets() && (O == null || com.sketchware.ai.project.M3Widgets.isMaterialSwitch(O, true));
        if (material) {
            setThumbDrawable(M3Canvas.switchThumb(getContext()));
            setTrackDrawable(M3Canvas.switchTrack(getContext(), DesignTheme.colorPrimary()));
        } else if (originalThumb != null) {
            setThumbDrawable(originalThumb);
            setTrackDrawable(originalTrack);
        }
        requestLayout();
        invalidate();
    }
}
