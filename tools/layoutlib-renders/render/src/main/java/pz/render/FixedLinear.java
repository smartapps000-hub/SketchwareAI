package pz.render;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/** The v1.9.0 ItemLinearLayout.onMeasure conversion, verbatim, for the layoutlib test. */
public class FixedLinear extends LinearLayout {
    public FixedLinear(Context context) {
        super(context);
    }

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
}
