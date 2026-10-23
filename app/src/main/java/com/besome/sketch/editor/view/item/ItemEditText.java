package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatEditText;

import com.besome.sketch.beans.ViewBean;
import com.sketchware.ai.editor.M3Canvas;

import a.a.a.sy;
import a.a.a.wB;

/**
 * Design-canvas stand-in for an {@code <EditText>}. Same fields and behaviour as upstream's precompiled class (it
 * replaces it; ViewPane creates it by name). v1.8.5: in a Material 3 project the generator wraps a plain EditText in
 * an outlined TextInputLayout (M3Widgets), so the canvas draws the outlined field: 1dp outline with 8dp corners (v1.8.6), the
 * hint as the floating label in the outline's top edge when there is text, 16dp inner padding unless the widget
 * has its own, the editor's default hint colour as on-surface-variant.
 */
public class ItemEditText extends AppCompatEditText implements sy {

    public ViewBean c;
    public boolean d;
    public boolean e;
    public Paint f;
    public float g;
    public Drawable h;

    private int backgroundColor = 0xffffff;
    /** The outlined look is applied (background removed, minimum height, inner padding). */
    private boolean outlinedLook;
    private int originalMinHeight;
    private int originalMinLines;
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int[] beanPadding;

    public ItemEditText(Context context) {
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
        originalMinHeight = getMinHeight(); // -1 when the minimum is set in lines
        originalMinLines = getMinLines();
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(M3Canvas.dp(context, 1));
        outline.setColor(M3Canvas.OUTLINE);
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, context.getResources().getDisplayMetrics()));
        label.setColor(M3Canvas.ON_SURFACE_VARIANT);
    }

    @Override
    public ViewBean getBean() {
        return c;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        c = viewBean;
        refreshLook();
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

    /** Same rule as the generator (M3Widgets.wrapsEditText): a plain EditText the user didn't style or place. */
    private boolean outlined() {
        if (!M3Canvas.widgets() || backgroundColor != 0xffffff) return false;
        if (c == null) return true;
        ViewBean parent = getParent() instanceof sy ? ((sy) getParent()).getBean() : null;
        return com.sketchware.ai.project.M3Widgets.wrapsEditText(c, parent, true);
    }

    private boolean showsLabel() {
        CharSequence hint = getHint();
        return outlined() && hint != null && hint.length() > 0 && getText() != null && getText().length() > 0;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (d) {
            canvas.drawRect(new Rect(0, 0, getMeasuredWidth(), getMeasuredHeight()), f);
        }
        if (outlined()) {
            // v1.8.6: the project's colour roles (DesignTheme), as in the generated app
            outline.setColor(M3Canvas.outline());
            label.setColor(M3Canvas.onSurfaceVariant());
            float half = outline.getStrokeWidth() / 2;
            float top = labelOffset();
            box.set(getScrollX() + half, getScrollY() + top + half,
                    getScrollX() + getWidth() - half, getScrollY() + getHeight() - half);
            float radius = M3Canvas.dp(getContext(), M3Canvas.TEXT_FIELD_CORNER_DP);
            if (showsLabel()) {
                String text = getHint().toString();
                float x = box.left + M3Canvas.dp(getContext(), 12);
                float width = label.measureText(text);
                float gap = M3Canvas.dp(getContext(), 4);
                canvas.save();
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    canvas.clipOutRect(x - gap, box.top - top - half, x + width + gap, box.top + top + half);
                } else {
                    // the minApi21 flavour: clipOutRect is API 26
                    canvas.clipRect(x - gap, box.top - top - half, x + width + gap, box.top + top + half,
                            android.graphics.Region.Op.DIFFERENCE);
                }
                canvas.drawRoundRect(box, radius, radius, outline);
                canvas.restore();
                Paint.FontMetrics metrics = label.getFontMetrics();
                canvas.drawText(text, x, box.top - (metrics.ascent + metrics.descent) / 2, label);
            } else {
                canvas.drawRoundRect(box, radius, radius, outline);
            }
        }
        super.onDraw(canvas);
    }

    /** Space above the outline for the floating label (TextInputLayout keeps it whenever there is a hint). */
    private float labelOffset() {
        CharSequence hint = getHint();
        if (hint == null || hint.length() == 0) return 0;
        Paint.FontMetrics metrics = label.getFontMetrics();
        return (metrics.descent - metrics.ascent) / 2;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshLook(); // the parent (converted or not) is known now
    }

    /** As upstream's class: no colour (0xffffff) = the EditText's own background. */
    @Override
    public void setBackgroundColor(int color) {
        backgroundColor = color;
        if (outlined()) {
            refreshLook();
        } else {
            if (outlinedLook) leaveOutlined();
            if (color == 0xffffff) {
                super.setBackground(h);
            } else {
                super.setBackgroundColor(color);
            }
        }
    }

    /**
     * ViewPane sets the bean's hint colour with TextView's final setHintTextColor(int) and ends every property update
     * with setVisibility(VISIBLE): the M3 hint colour is applied there.
     */
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (outlinedLook) applyHintColor();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        beanPadding = new int[]{left, top, right, bottom};
        applyPadding();
    }

    private void applyPadding() {
        if (beanPadding == null) return;
        int left = (int) (beanPadding[0] * g), top = (int) (beanPadding[1] * g);
        int right = (int) (beanPadding[2] * g), bottom = (int) (beanPadding[3] * g);
        if (outlined()) {
            int inner = Math.round(M3Canvas.dp(getContext(), 16));
            // TextInputEditText.OutlinedBox pads 16dp unless the generated field has its own padding
            if (left == 0 && top == 0 && right == 0 && bottom == 0) {
                left = top = right = bottom = inner;
            }
            top += Math.round(labelOffset());
        }
        super.setPadding(left, top, right, bottom);
    }

    /**
     * Applies the outlined look when the field is outlined, and takes it back when it no longer is. A field that is
     * not and was not outlined is left alone: its background (colour or image) is whatever ViewPane set, as with
     * upstream's class.
     */
    private void refreshLook() {
        if (outlined()) {
            if (!outlinedLook) {
                outlinedLook = true;
                super.setBackground(null);
                setMinHeight(Math.round(M3Canvas.dp(getContext(), 56)));
            }
            applyPadding();
            applyHintColor();
            invalidate();
        } else if (outlinedLook) {
            leaveOutlined();
            if (backgroundColor == 0xffffff) {
                super.setBackground(h);
            } else {
                super.setBackgroundColor(backgroundColor);
            }
            applyPadding();
            invalidate();
        }
    }

    /** Back to the plain EditText look (ViewPane sets the bean's background image again on its next update). */
    private void leaveOutlined() {
        outlinedLook = false;
        if (originalMinHeight >= 0) {
            setMinHeight(originalMinHeight);
        } else {
            setMinLines(originalMinLines);
        }
        if (c != null && c.text != null && c.text.hintColor != 0) super.setHintTextColor(c.text.hintColor);
    }

    private void applyHintColor() {
        if (c == null || c.text == null) return;
        int color = c.text.hintColor;
        if (color == M3Canvas.DEFAULT_HINT_COLOR) {
            color = M3Canvas.onSurfaceVariant(); // the generator writes ?attr/colorOnSurfaceVariant for it
        } else if (color == 0) {
            return;
        }
        super.setHintTextColor(color);
    }
}
