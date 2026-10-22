package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.appcompat.widget.AppCompatButton;

import com.besome.sketch.beans.ViewBean;
import com.sketchware.ai.editor.DesignTheme;
import com.sketchware.ai.editor.M3Canvas;

import a.a.a.sy;
import a.a.a.wB;

/**
 * Design-canvas stand-in for a {@code <Button>}. Same fields and behaviour as upstream's precompiled class (it
 * replaces it; ViewPane creates it by name). v1.8.5: in a Material 3 project the generated {@code <Button>} inflates as
 * a filled MaterialButton, so the canvas draws that: a full pill in the project's primary colour with 4dp insets,
 * labelLarge (medium, not all caps), the label in on-primary when it has the editor's default colour. A button
 * with its own background colour keeps it (MaterialButton uses android:background as given).
 */
public class ItemButton extends AppCompatButton implements sy {

    public ViewBean c;
    public boolean d;
    public boolean e;
    public Paint f;
    public float g;
    public Drawable h;

    /** The background colour the canvas last set: 0xffffff = none (theme background), 0 = transparent. */
    private int backgroundColor = 0xffffff;
    private Integer textColor;

    public ItemButton(Context context) {
        super(context);
        a(context);
    }

    public void a(Context context) {
        g = wB.a(context, 1.0f);
        f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(0x9599d5d0);
        setDrawingCacheEnabled(true);
        setFocusable(false);
        h = getBackground();
    }

    @Override
    public ViewBean getBean() {
        return c;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        c = viewBean;
        applyLook();
    }

    @Override
    public boolean getFixed() {
        return e;
    }

    public void setFixed(boolean fixed) {
        e = fixed;
    }

    public boolean getSelection() {
        return d;
    }

    @Override
    public void setSelection(boolean selection) {
        d = selection;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (d) {
            canvas.drawRect(new Rect(0, 0, getMeasuredWidth(), getMeasuredHeight()), f);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setBackgroundColor(int color) {
        backgroundColor = color;
        applyLook();
    }

    @Override
    public void setTextColor(int color) {
        textColor = color;
        applyTextColor();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) (left * g), (int) (top * g), (int) (right * g), (int) (bottom * g));
    }

    /** Same rule as the generator (M3Widgets.filledButton): plain Button, no background, no injected style. */
    private boolean materialButton() {
        return M3Canvas.active() && (c == null || com.sketchware.ai.project.M3Widgets.filledButton(c));
    }

    private void applyLook() {
        if (materialButton() && backgroundColor == 0xffffff) {
            super.setBackground(M3Canvas.filledButton(getContext(), DesignTheme.colorPrimary()));
            setMinHeight(Math.round(M3Canvas.dp(getContext(), 48)));
            setMinimumHeight(Math.round(M3Canvas.dp(getContext(), 48)));
            setAllCaps(false);
            setLetterSpacing(0.00714f);
            setTypeface(M3Canvas.labelTypeface());
        } else if (backgroundColor == 0xffffff) {
            super.setBackground(h);
        } else {
            super.setBackgroundColor(backgroundColor);
        }
        applyTextColor();
    }

    private void applyTextColor() {
        if (textColor == null) return;
        int color = textColor;
        if (materialButton() && M3Canvas.widgets() && backgroundColor == 0xffffff && color == M3Canvas.DEFAULT_TEXT_COLOR) {
            color = M3Canvas.ON_PRIMARY; // the generator writes ?attr/colorOnPrimary for it
        }
        super.setTextColor(color);
    }
}
