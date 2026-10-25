package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.LinearLayout;

import com.besome.sketch.beans.ViewBean;
import com.sketchware.ai.editor.M3Canvas;

import a.a.a.sy;
import a.a.a.ty;
import a.a.a.wB;

/**
 * Design-canvas stand-in for a {@code <LinearLayout>}, also the screen's root (ViewPane). Same fields and behaviour as
 * upstream's precompiled class, which it replaces (v1.8.6; ViewPane and the other editor classes create it by name).
 * Added: the screen root (fixed, given the editor's grey #EEEEEE by ViewPane) of the design editor shows the generated
 * app's window background in a Material 3 project (M3Canvas.background, the project's light colour roles). Other panes
 * (the widget collection's preview) keep the grey. The roots are refreshed when DesignTheme loads a project again
 * (Library Manager, Project Configuration).
 */
public class ItemLinearLayout extends LinearLayout implements sy, ty {

    /** ViewPane's background for the screen root. */
    private static final int EDITOR_SCREEN_GREY = 0xFFEEEEEE;

    /** Screen roots of design editors, refreshed by {@link #refreshScreenRoots()}. */
    private static final java.util.Set<ItemLinearLayout> SCREEN_ROOTS =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<ItemLinearLayout, Boolean>()));

    /** ViewPane gave this layout the editor grey: it is a screen root. */
    private boolean screenRoot;

    public ViewBean a = null;
    public boolean b = false;
    public boolean c = false;
    public Paint d;
    public int e = 0;

    public ItemLinearLayout(Context context) {
        super(context);
        a(context);
    }

    /**
     * v1.9.0 fix: ViewPane gives a child FrameLayout params whenever its bean's parentType isn't 0, but containers drawn
     * as LinearLayouts (CardView, TextInputLayout, SwipeRefreshLayout, RadioGroup, CollapsingToolbarLayout) only get 0
     * from the editor's own drop. Children with other params (projects made by the v1.8.7-v1.8.9 templates: a card's
     * column stored with parentType 36) crashed LinearLayout's measure with a ClassCastException. They are converted to
     * LinearLayout params here, keeping size, margins and gravity.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            android.view.ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params != null && !(params instanceof LinearLayout.LayoutParams)) {
                LinearLayout.LayoutParams fixed = params instanceof android.view.ViewGroup.MarginLayoutParams
                        ? new LinearLayout.LayoutParams((android.view.ViewGroup.MarginLayoutParams) params)
                        : new LinearLayout.LayoutParams(params);
                if (params instanceof android.widget.FrameLayout.LayoutParams) {
                    fixed.gravity = ((android.widget.FrameLayout.LayoutParams) params).gravity;
                }
                child.setLayoutParams(fixed);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Renumbers the beans of the children by their order. */
    public void a() {
        int index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof sy) {
                ((sy) child).getBean().index = index;
                index++;
            }
        }
    }

    public final void a(Context context) {
        setOrientation(LinearLayout.HORIZONTAL);
        setDrawingCacheEnabled(true);
        setMinimumWidth((int) wB.a(context, 32.0f));
        setMinimumHeight((int) wB.a(context, 32.0f));
        d = new Paint(Paint.ANTI_ALIAS_FLAG);
        d.setStrokeWidth(wB.a(getContext(), 2.0f));
    }

    @Override
    public void addView(View child, int index) {
        int count = getChildCount();
        if (index > count) {
            super.addView(child);
            return;
        }
        int gone = -1;
        for (int i = 0; i < count; i++) {
            if (getChildAt(i).getVisibility() == View.GONE) {
                gone = i;
                break;
            }
        }
        if (gone >= 0 && index >= gone) {
            super.addView(child, index + 1);
        } else {
            super.addView(child, index);
        }
    }

    @Override
    public ViewBean getBean() {
        return a;
    }

    @Override
    public boolean getFixed() {
        return c;
    }

    public int getLayoutGravity() {
        return e;
    }

    public boolean getSelection() {
        return b;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (!c) {
            if (b) {
                d.setColor(0x9599d5d0);
                canvas.drawRect(new Rect(0, 0, getMeasuredWidth(), getMeasuredHeight()), d);
            }
            d.setColor(0x60000000);
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            float w = width;
            float h = height;
            canvas.drawLine(0, 0, w, 0, d);
            canvas.drawLine(0, 0, 0, h, d);
            canvas.drawLine(w, 0, w, h, d);
            canvas.drawLine(0, h, w, h, d);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setBackgroundColor(int color) {
        if (c && color == EDITOR_SCREEN_GREY) {
            // v1.8.6: the screen root shows the window background the generated Material 3 app has
            screenRoot = true;
            SCREEN_ROOTS.add(this);
            color = screenColor();
        } else {
            screenRoot = false;
            SCREEN_ROOTS.remove(this);
        }
        super.setBackgroundColor(color);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (screenRoot) super.setBackgroundColor(screenColor());
    }

    /** The editor grey, or the Material 3 window background in the design editor of a Material 3 project. */
    private int screenColor() {
        if (!M3Canvas.active() || !inDesignEditor()) return EDITOR_SCREEN_GREY;
        return M3Canvas.background(EDITOR_SCREEN_GREY);
    }

    private boolean inDesignEditor() {
        Context context = getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof com.besome.sketch.design.DesignActivity) return true;
            Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return false;
    }

    /** Applies the current DesignTheme to every screen root (called after DesignTheme.load, from any thread). */
    public static void refreshScreenRoots() {
        ItemLinearLayout[] roots;
        synchronized (SCREEN_ROOTS) {
            roots = SCREEN_ROOTS.toArray(new ItemLinearLayout[0]);
        }
        for (final ItemLinearLayout root : roots) {
            root.post(new Runnable() {
                @Override
                public void run() {
                    if (root.screenRoot) root.applyScreenColor();
                }
            });
        }
    }

    private void applyScreenColor() {
        super.setBackgroundColor(screenColor());
    }

    @Override
    public void setBean(ViewBean viewBean) {
        a = viewBean;
    }

    @Override
    public void setChildScrollEnabled(boolean enabled) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ty) {
                ((ty) child).setChildScrollEnabled(enabled);
            }
            if (child instanceof ItemHorizontalScrollView) {
                ((ItemHorizontalScrollView) child).setScrollEnabled(enabled);
            }
            if (child instanceof ItemVerticalScrollView) {
                ((ItemVerticalScrollView) child).setScrollEnabled(enabled);
            }
        }
    }

    public void setFixed(boolean fixed) {
        c = fixed;
    }

    public void setLayoutGravity(int gravity) {
        e = gravity;
        super.setGravity(gravity);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) wB.a(getContext(), (float) left), (int) wB.a(getContext(), (float) top),
                (int) wB.a(getContext(), (float) right), (int) wB.a(getContext(), (float) bottom));
    }

    @Override
    public void setSelection(boolean selection) {
        b = selection;
        invalidate();
    }
}
