package com.sketchware.ai.ui;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;

import com.sketchware.remod.R;

import org.xmlpull.v1.XmlPullParser;

/**
 * Sketchware AI liquid-glass surface, drawn the same way as the design mockup's CSS:
 * <ul>
 *     <li>frosted body: a linear gradient ({@code swFillTop} to {@code swFillBottom}, at
 *     {@code swFillAngle} degrees, 270 = top to bottom like CSS 180deg);</li>
 *     <li>top sheen: {@code swSheen} fading to clear by 45% of the height;</li>
 *     <li>crisp specular rim: a {@code swRimWidth} (1dp) stroke whose colour runs from
 *     {@code swRimTop} at the top edge to {@code swRimBottom} at the bottom edge;</li>
 *     <li>optional outer glow ({@code swGlowColor}, {@code swGlowRadius}, {@code swGlowDy}) drawn
 *     only outside the shape, the equivalent of CSS box-shadow. The glow lives inside the
 *     drawable's bounds, so the shape is inset by the glow radius.</li>
 * </ul>
 * Used from XML on API 24+ as {@code <drawable class="com.sketchware.ai.ui.GlassDrawable" .../>}.
 */
public class GlassDrawable extends Drawable {

    private float radius;
    private int corners;
    private int fillTop = 0xB826262C;
    private int fillBottom = 0x9418181C;
    private int fillAngle = 270;
    private int sheen = 0;
    private int rimTop = 0x4DFFFFFF;
    private int rimBottom = 0x14FFFFFF;
    private float rimWidth;
    private int glowColor = 0;
    private float glowRadius;
    private float glowDy;
    private boolean dashed;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private final Path path = new Path();
    private int alpha = 255;

    public GlassDrawable() {
        rimPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.FILL);
        float density = Resources.getSystem().getDisplayMetrics().density;
        radius = 22 * density;
        rimWidth = density;
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) {
        TypedArray a = theme != null
                ? theme.obtainStyledAttributes(attrs, R.styleable.SwGlass, 0, 0)
                : r.obtainAttributes(attrs, R.styleable.SwGlass);
        radius = a.getDimension(R.styleable.SwGlass_swRadius, radius);
        corners = a.getInt(R.styleable.SwGlass_swCorners, 0);
        fillTop = a.getColor(R.styleable.SwGlass_swFillTop, fillTop);
        fillBottom = a.getColor(R.styleable.SwGlass_swFillBottom, fillTop == 0xB826262C ? fillBottom : fillTop);
        fillAngle = a.getInt(R.styleable.SwGlass_swFillAngle, fillAngle);
        sheen = a.getColor(R.styleable.SwGlass_swSheen, sheen);
        rimTop = a.getColor(R.styleable.SwGlass_swRimTop, rimTop);
        rimBottom = a.getColor(R.styleable.SwGlass_swRimBottom, rimTop == 0x4DFFFFFF ? rimBottom : rimTop);
        rimWidth = a.getDimension(R.styleable.SwGlass_swRimWidth, rimWidth);
        glowColor = a.getColor(R.styleable.SwGlass_swGlowColor, 0);
        glowRadius = a.getDimension(R.styleable.SwGlass_swGlowRadius, 0);
        glowDy = a.getDimension(R.styleable.SwGlass_swGlowDy, 0);
        dashed = a.getBoolean(R.styleable.SwGlass_swDashed, false);
        a.recycle();
        if (dashed) {
            float d = Resources.getSystem().getDisplayMetrics().density;
            rimPaint.setPathEffect(new DashPathEffect(new float[]{4 * d, 3 * d}, 0));
        }
        onBoundsChange(getBounds());
    }

    /** Programmatic setup, for views built in code. */
    public GlassDrawable set(float radiusPx, int fillTop, int fillBottom, int rimTop, int rimBottom) {
        this.radius = radiusPx;
        this.fillTop = fillTop;
        this.fillBottom = fillBottom;
        this.rimTop = rimTop;
        this.rimBottom = rimBottom;
        onBoundsChange(getBounds());
        invalidateSelf();
        return this;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        float inset = glowRadius;
        // The shape sits centred in its glow slot, so a glowing pill lines up exactly with its
        // neighbours; only the glow itself drops by glowDy.
        shape.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        if (shape.width() <= 0 || shape.height() <= 0) return;

        float[] radii;
        if (corners == 1) {
            radii = new float[]{radius, radius, radius, radius, 0, 0, 0, 0};
        } else if (corners == 2) {
            radii = new float[]{0, 0, 0, 0, radius, radius, radius, radius};
        } else {
            radii = new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
        }
        path.reset();
        path.addRoundRect(shape, radii, Path.Direction.CW);

        double rad = Math.toRadians(fillAngle);
        float cx = shape.centerX(), cy = shape.centerY();
        float hx = (float) (Math.cos(rad) * shape.width() / 2), hy = (float) (-Math.sin(rad) * shape.height() / 2);
        fillPaint.setShader(new LinearGradient(cx - hx, cy - hy, cx + hx, cy + hy, fillTop, fillBottom, Shader.TileMode.CLAMP));
        if (sheen != 0) {
            sheenPaint.setShader(new LinearGradient(0, shape.top, 0, shape.top + shape.height() * 0.45f,
                    sheen, sheen & 0x00FFFFFF, Shader.TileMode.CLAMP));
        }
        rimPaint.setStrokeWidth(rimWidth);
        rimPaint.setShader(new LinearGradient(0, shape.top, 0, shape.bottom, rimTop, rimBottom, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        if (shape.width() <= 0) return;
        if (glowColor != 0 && glowRadius > 0) drawGlow(canvas);
        applyAlpha(fillPaint);
        canvas.drawPath(path, fillPaint);
        if (sheen != 0) {
            applyAlpha(sheenPaint);
            canvas.drawPath(path, sheenPaint);
        }
        if (rimWidth > 0 && (rimTop != 0 || rimBottom != 0)) {
            canvas.save();
            float h = rimWidth / 2f;
            canvas.translate(0, 0);
            RectF r = new RectF(shape.left + h, shape.top + h, shape.right - h, shape.bottom - h);
            Path rim = new Path();
            float rr = Math.max(0, radius - h);
            float[] radii = corners == 1 ? new float[]{rr, rr, rr, rr, 0, 0, 0, 0}
                    : corners == 2 ? new float[]{0, 0, 0, 0, rr, rr, rr, rr}
                    : new float[]{rr, rr, rr, rr, rr, rr, rr, rr};
            rim.addRoundRect(r, radii, Path.Direction.CW);
            applyAlpha(rimPaint);
            canvas.drawPath(rim, rimPaint);
            canvas.restore();
        }
    }

    /** Soft glow outside the shape only, built from stacked rounded rects (no blur filter needed). */
    private void drawGlow(Canvas canvas) {
        canvas.save();
        if (Build.VERSION.SDK_INT >= 26) canvas.clipOutPath(path);
        int steps = 10;
        int baseA = Color.alpha(glowColor);
        for (int i = steps; i >= 1; i--) {
            float grow = glowRadius * i / steps;
            float t = 1f - (float) i / (steps + 1);
            int a = (int) (baseA * t * t * 0.34f * alpha / 255f);
            glowPaint.setColor((glowColor & 0x00FFFFFF) | (a << 24));
            RectF g = new RectF(shape.left - grow, shape.top - grow + glowDy, shape.right + grow, shape.bottom + grow + glowDy);
            canvas.drawRoundRect(g, radius + grow, radius + grow, glowPaint);
        }
        canvas.restore();
    }

    private void applyAlpha(Paint p) {
        p.setAlpha(alpha);
    }

    @Override
    public void getOutline(Outline outline) {
        if (corners == 0) {
            outline.setRoundRect((int) shape.left, (int) shape.top, (int) shape.right, (int) shape.bottom, radius);
        } else {
            outline.setRect((int) shape.left, (int) shape.top, (int) shape.right, (int) shape.bottom);
        }
        outline.setAlpha(0f);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        rimPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /**
     * Lets the framework cache and share the drawable like its own drawables (selectors,
     * layer-lists and insets that hold a GlassDrawable, and code that calls
     * getConstantState().newDrawable(), expect this).
     */
    @Override
    public ConstantState getConstantState() {
        final float radius = this.radius, rimWidth = this.rimWidth, glowRadius = this.glowRadius, glowDy = this.glowDy;
        final int corners = this.corners, fillTop = this.fillTop, fillBottom = this.fillBottom, fillAngle = this.fillAngle,
                sheen = this.sheen, rimTop = this.rimTop, rimBottom = this.rimBottom, glowColor = this.glowColor;
        final boolean dashed = this.dashed;
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                GlassDrawable copy = new GlassDrawable();
                copy.radius = radius;
                copy.corners = corners;
                copy.fillTop = fillTop;
                copy.fillBottom = fillBottom;
                copy.fillAngle = fillAngle;
                copy.sheen = sheen;
                copy.rimTop = rimTop;
                copy.rimBottom = rimBottom;
                copy.rimWidth = rimWidth;
                copy.glowColor = glowColor;
                copy.glowRadius = glowRadius;
                copy.glowDy = glowDy;
                copy.dashed = dashed;
                if (dashed) {
                    float d = Resources.getSystem().getDisplayMetrics().density;
                    copy.rimPaint.setPathEffect(new DashPathEffect(new float[]{4 * d, 3 * d}, 0));
                }
                return copy;
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }
}
