package mod.agus.jcoderz.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.besome.sketch.beans.ViewBean;

import a.a.a.sy;
import a.a.a.wB;

public class ItemVideoView extends LinearLayout implements sy {

    /* renamed from: a  reason: collision with root package name */
    public ViewBean f24a;
    public boolean b;
    public boolean c;
    public Paint d;
    public float e;
    public ImageView f;

    public ItemVideoView(Context context) {
        super(context);
        a(context);
    }

    @Deprecated
    public void a(Context context) {
        this.e = wB.a(context, 1.0f);
        this.d = new Paint(1);
        this.d.setColor(-1785080368);
        setDrawingCacheEnabled(true);
        this.f = new ImageView(getContext());
        this.f.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        this.f.setImageResource(2131166305);
        this.f.setScaleType(ImageView.ScaleType.FIT_XY);
        this.f.setPadding(0, 0, 0, 0);
        addView(this.f);
        setGravity(17);
        setWillNotDraw(false);
    }

    @Override
    public ViewBean getBean() {
        return this.f24a;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        this.f24a = viewBean;
    }

    @Override
    public boolean getFixed() {
        return this.c;
    }

    public void setFixed(boolean z) {
        this.c = z;
    }

    public boolean getSelection() {
        return this.b;
    }

    @Override
    public void setSelection(boolean z) {
        this.b = z;
        invalidate();
    }

    /**
     * v1.8.7: measured like the app's VideoView before a video is loaded (VideoView.onMeasure with no video size is
     * View.getDefaultSize(0, spec)): with wrap_content the empty VideoView takes all the width and height its parent
     * offers, and the app shows it black (a SurfaceView) until a video plays; widgets after it are pushed off the
     * screen. The canvas showed a fixed preview instead, so a layout that looked fine here was black from the
     * VideoView down on the phone. A parent that doesn't limit the height (a ScrollView) keeps the preview size here;
     * on the phone the empty VideoView is 0 high there.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            width = Math.max(width, MeasureSpec.getSize(widthMeasureSpec));
        }
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            height = Math.max(height, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onDraw(Canvas canvas) {
        // the empty video surface is black on the phone; a background colour set on the widget stays
        Drawable background = getBackground();
        if (background == null || (background instanceof ColorDrawable && ((ColorDrawable) background).getAlpha() == 0)) {
            canvas.drawColor(0xFF000000);
        }
        if (this.b) {
            canvas.drawRect(new Rect(0, 0, getMeasuredWidth(), getMeasuredHeight()), this.d);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setPadding(int i, int i2, int i3, int i4) {
        float f2 = this.e;
        super.setPadding((int) (((float) i) * f2), (int) (((float) i2) * f2), (int) (((float) i3) * f2), (int) (f2 * ((float) i4)));
    }
}
