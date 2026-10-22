package dev.aldi.sayuti.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;

import androidx.appcompat.widget.AppCompatButton;

import com.besome.sketch.beans.ViewBean;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.sy;
import a.a.a.wB;

/**
 * Design-canvas stand-in for a MaterialButton. It is drawn the way the generated button looks: filled with the
 * background tint (the project's primary colour unless the widget has its own background colour), with the corner
 * radius of its style: a full pill for {@code Widget.Material3.Button} (Material 3 projects), the injected
 * {@code app:cornerRadius} or 4dp for {@code Widget.MaterialComponents.Button}.
 */
public class ItemMaterialButton extends AppCompatButton implements sy {

    private static final Pattern CORNER_RADIUS = Pattern.compile("app:cornerRadius=\"(\\d+(?:\\.\\d+)?)dp\"");

    private final Paint paint;
    private final float paddingFactor;
    private final Rect rect;
    private ViewBean viewBean;
    private boolean hasSelection;
    private boolean hasFixed;
    /** The background colour the canvas last set: 0xffffff = none (the button's tint), 0 = transparent. */
    private int backgroundColor = 0xffffff;

    public ItemMaterialButton(Context context) {
        super(context);
        paddingFactor = wB.a(context, 1.0f);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x9599d5d0);
        rect = new Rect();

        setDrawingCacheEnabled(true);
        setFocusable(false);
        applyLook();
    }

    @Override
    public ViewBean getBean() {
        return viewBean;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        this.viewBean = viewBean;
        applyLook();
    }

    @Override
    public boolean getFixed() {
        return hasFixed;
    }

    public void setFixed(boolean z) {
        hasFixed = z;
    }

    public boolean getSelection() {
        return hasSelection;
    }

    @Override
    public void setSelection(boolean z) {
        hasSelection = z;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (hasSelection) {
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRect(rect, paint);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setBackgroundColor(int color) {
        backgroundColor = color;
        applyLook();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) (left * paddingFactor), (int) (top * paddingFactor), (int) (right * paddingFactor), (int) (bottom * paddingFactor));
    }

    private void applyLook() {
        String inject = viewBean == null || viewBean.inject == null ? "" : viewBean.inject;
        boolean material3 = inject.contains("Widget.Material3.")
                || (viewBean == null && com.sketchware.ai.editor.DesignTheme.material3());
        float density = getResources().getDisplayMetrics().density;
        float radius;
        Matcher m = CORNER_RADIUS.matcher(inject);
        if (m.find()) {
            radius = Float.parseFloat(m.group(1)) * density;
        } else {
            radius = material3 ? 1000 * density : 4 * density; // M3 shape "full" = half the height
        }
        int fill;
        if (backgroundColor == 0xffffff) {
            fill = com.sketchware.ai.editor.DesignTheme.colorPrimary(); // app:backgroundTint="@color/colorPrimary"
        } else if (backgroundColor == 0) {
            fill = 0; // the generator writes a transparent tint
        } else {
            fill = 0xff000000 | (backgroundColor & 0xffffff); // the generator writes the colour without alpha
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radius);
        shape.setColor(fill);
        super.setBackground(shape);
    }
}
