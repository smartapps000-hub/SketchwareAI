package com.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.sketchware.remod.R;

/**
 * v2.1.2 (owner's 21 Nov directive "the default icon is the APK's only"): a project without an icon of its own shows its
 * two letters (GH, AK, SD) inside Sketchware AI, as the Home cards do: in the New Project tile and in a project's
 * settings, where v2.1.1 showed the round Android icon the APK gets. The letters in the accent colour, bold, centred,
 * sized to the bounds.
 */
public final class MonogramDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String letters;

    public MonogramDrawable(Context context, String name) {
        paint.setColor(context.getResources().getColor(R.color.ob_accent_2, context.getTheme()));
        paint.setTextAlign(Paint.Align.CENTER);
        try {
            paint.setTypeface(context.getResources().getFont(R.font.sora_bold));
        } catch (RuntimeException e) {
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        setName(name);
    }

    /** The letters of {@code name} (Home's rule); "?" stays out: an empty name shows nothing. */
    public void setName(String name) {
        letters = a.a.a.GC.monogram(name == null ? "" : name);
        invalidateSelf();
    }

    public String letters() {
        return letters;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (letters == null || letters.isEmpty() || b.isEmpty()) return;
        paint.setTextSize(b.height() * 0.46f);
        Paint.FontMetrics m = paint.getFontMetrics();
        float y = b.exactCenterY() - (m.ascent + m.descent) / 2f;
        canvas.drawText(letters, b.exactCenterX(), y, paint);
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return -1;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
