package mod.agus.jcoderz.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.RadioButton;

import com.besome.sketch.beans.ViewBean;

import a.a.a.sy;
import a.a.a.wB;

public class ItemRadioButton extends RadioButton implements sy {
    public ViewBean b;
    public boolean c;
    public boolean d;
    public Paint e;
    public float f;

    public ItemRadioButton(Context context) {
        super(context);
        a(context);
    }

    @Deprecated
    public void a(Context context) {
        this.f = wB.a(context, 1.0f);
        this.e = new Paint(1);
        this.e.setColor(-1785080368);
        setDrawingCacheEnabled(true);
        originalTint = getButtonTintList();
        applyLook();
    }

    @Override
    public ViewBean getBean() {
        return this.b;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        this.b = viewBean;
        applyLook();
    }

    /** v1.8.5: in a Material 3 project the theme inflates a MaterialRadioButton (primary when checked). */
    private void applyLook() {
        boolean material = com.sketchware.ai.editor.M3Canvas.active() && (b == null || com.sketchware.ai.project.M3Widgets.plain(b));
        if (material) {
            setButtonTintList(com.sketchware.ai.editor.M3Canvas.compoundTint(com.sketchware.ai.editor.DesignTheme.colorPrimary()));
        } else if (originalTint != null) {
            setButtonTintList(originalTint);
        }
    }

    private android.content.res.ColorStateList originalTint;

    @Override
    public boolean getFixed() {
        return this.d;
    }

    public void setFixed(boolean z) {
        this.d = z;
    }

    public boolean getSelection() {
        return this.c;
    }

    @Override
    public void setSelection(boolean z) {
        this.c = z;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (this.c) {
            canvas.drawRect(new Rect(0, 0, getMeasuredWidth(), getMeasuredHeight()), this.e);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setPadding(int i, int i2, int i3, int i4) {
        float f2 = this.f;
        super.setPadding((int) (((float) i) * f2), (int) (((float) i2) * f2), (int) (((float) i3) * f2), (int) (f2 * ((float) i4)));
    }
}
