package com.sketchware.ai.editor;

import android.app.Activity;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.property.PropertyInputItem;
import com.besome.sketch.editor.view.ViewPane;

import a.a.a.sy;

/**
 * Double-tap a widget on the design canvas (TextView, Button, EditText and any other widget with
 * a Text property) to open its Text dialog directly.
 * <p>
 * The canvas and its drag handling live in precompiled code, so this watches touches at the
 * Activity level without consuming them: the first tap selects the widget as usual (which binds
 * the property panel to it) and the second tap opens the panel's own Text dialog, so the change
 * goes through the normal path (undo history, save, canvas refresh).
 */
public final class CanvasQuickEdit {

    private final Activity activity;
    private final GestureDetector detector;

    public CanvasQuickEdit(Activity activity) {
        this.activity = activity;
        detector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                final sy item = findItemAt(activity.getWindow().getDecorView(), (int) e.getX(), (int) e.getY());
                if (item == null) return false;
                final ViewBean bean = item.getBean();
                if (bean == null) return false;
                // Let the canvas finish handling the second tap (selection) first.
                activity.getWindow().getDecorView().postDelayed(
                        () -> PropertyInputItem.openTextEditorFor(bean.id), 120);
                return false;
            }
        });
        detector.setIsLongpressEnabled(false);
    }

    /** Call from Activity#dispatchTouchEvent before super. Never consumes the event. */
    public void onTouchEvent(MotionEvent event) {
        try {
            detector.onTouchEvent(event);
        } catch (RuntimeException ignored) {
            // Quick edit is a convenience; never break the editor's own touch handling.
        }
    }

    private static sy findItemAt(View root, int rawX, int rawY) {
        ViewPane pane = findVisiblePane(root, rawX, rawY);
        if (pane == null) return null;
        return deepestItem(pane, rawX, rawY);
    }

    private static ViewPane findVisiblePane(View view, int rawX, int rawY) {
        if (view.getVisibility() != View.VISIBLE || !contains(view, rawX, rawY)) return null;
        if (view instanceof ViewPane) return (ViewPane) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                ViewPane found = findVisiblePane(group.getChildAt(i), rawX, rawY);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static sy deepestItem(View view, int rawX, int rawY) {
        if (view.getVisibility() != View.VISIBLE || !contains(view, rawX, rawY)) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                sy found = deepestItem(group.getChildAt(i), rawX, rawY);
                if (found != null) return found;
            }
        }
        return view instanceof sy ? (sy) view : null;
    }

    private static boolean contains(View view, int rawX, int rawY) {
        Rect rect = new Rect();
        return view.getGlobalVisibleRect(rect) && rect.contains(rawX, rawY);
    }
}
