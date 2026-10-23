package com.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.sketchware.ai.project.M3Palette;

/**
 * Preview of a new project's colours on the New Project screen (v1.8.7): one phone per mode (light, and dark when the
 * dark theme is on) with the status bar (primary dark), the toolbar (primary), the window background, a filled
 * button, an outlined text field, body text and the FAB (accent).
 * <p>
 * The Material 3 colours are the ones the build writes: the toolbar, button and FAB keep the project colours in both
 * modes, the window and text colours come from {@link M3Palette} (values / values-night), the "on" colours by contrast
 * as in the generated theme; the toolbar title is white (the generated AppBarOverlay). Without Material 3 it shows the
 * MaterialComponents (Bridge) look: white window, the AppCompat grey button, an underlined field.
 */
public class ThemePreviewView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float dp;

    private int accent = 0xFFFF7043;
    private int primary = 0xFFF4511E;
    private int primaryDark = 0xFFBF360C;
    private boolean material3 = true;
    private boolean dark = true;
    private String appName = "My app";
    private M3Palette palette;

    public ThemePreviewView(Context context) {
        this(context, null);
    }

    public ThemePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = context.getResources().getDisplayMetrics().density;
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    /** colours in ThemePresets order: accent, primary, primary dark, ... */
    public void set(int[] colors, boolean material3, boolean dark, String appName) {
        this.accent = colors[0] | 0xFF000000;
        this.primary = colors[1] | 0xFF000000;
        this.primaryDark = colors[2] | 0xFF000000;
        this.material3 = material3;
        this.dark = material3 && dark;
        this.appName = appName == null || appName.trim().isEmpty() ? "My app" : appName.trim();
        this.palette = material3 ? M3Palette.of(primary, accent) : null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float gap = 12 * dp;
        float phoneWidth = (getWidth() - gap) / 2f;
        if (dark) {
            drawPhone(canvas, 0, phoneWidth, false, "Light");
            drawPhone(canvas, phoneWidth + gap, phoneWidth, true, "Dark");
        } else {
            drawPhone(canvas, (getWidth() - phoneWidth) / 2f, phoneWidth, false, material3 ? "Light" : "MaterialComponents");
        }
    }

    private void drawPhone(Canvas canvas, float left, float width, boolean night, String label) {
        float labelHeight = 16 * dp;
        text.setTextSize(11 * dp);
        text.setColor(0xFFA1A1AA);
        text.setTypeface(Typeface.DEFAULT);
        canvas.drawText(label, left + 2 * dp, 11 * dp, text);

        float top = labelHeight;
        float bottom = getHeight() - 1;
        float radius = 14 * dp;
        canvas.save();
        clip.reset();
        rect.set(left, top, left + width, bottom);
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);

        int surface = material3 ? role("sketch_m3_background", night) : 0xFFFFFFFF;
        int onSurface = material3 ? role("sketch_m3_on_surface", night) : 0xDE000000;
        int onSurfaceVariant = material3 ? role("sketch_m3_on_surface_variant", night) : 0x8A000000;
        int outline = material3 ? role("sketch_m3_outline", night) : 0x61000000;
        int onPrimary = material3 ? role("sketch_m3_on_primary", night) : 0xFFFFFFFF;
        int onAccent = material3 ? role("sketch_m3_on_secondary", night) : 0xFFFFFFFF;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(surface);
        canvas.drawRect(rect, paint);

        float status = 10 * dp;
        float toolbar = 30 * dp;
        paint.setColor(primaryDark);
        canvas.drawRect(left, top, left + width, top + status, paint);
        paint.setColor(primary);
        canvas.drawRect(left, top + status, left + width, top + status + toolbar, paint);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(12 * dp);
        // the generated toolbar sits in AppTheme.AppBarOverlay (ThemeOverlay ... Dark.ActionBar): its title is white
        // whatever the primary colour
        text.setColor(0xFFFFFFFF);
        canvas.drawText(ellipsize(appName, width - 20 * dp), left + 10 * dp, top + status + 19.5f * dp, text);

        float y = top + status + toolbar + 10 * dp;
        float x = left + 10 * dp;
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(11 * dp);
        text.setColor(onSurface);
        canvas.drawText("Hello world", x, y + 9 * dp, text);

        // Material 3: outlined text field (AppTheme.TextField: 8dp corners, 4dp in the 1:2 preview). MaterialComponents
        // (Bridge, no Material inflater): the framework EditText, an underline
        y += 18 * dp;
        float fieldHeight = 26 * dp;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, dp));
        paint.setColor(outline);
        rect.set(x, y, left + width - 10 * dp, y + fieldHeight);
        if (material3) {
            canvas.drawRoundRect(rect, 4 * dp, 4 * dp, paint);
        } else {
            paint.setColor(accent);
            canvas.drawLine(rect.left, rect.bottom - 3 * dp, rect.right, rect.bottom - 3 * dp, paint);
        }
        text.setColor(onSurfaceVariant);
        text.setTextSize(10 * dp);
        canvas.drawText("Text field", x + 8 * dp, y + 16.5f * dp, text);

        // filled button: pill in Material 3, 4dp corners in MaterialComponents
        y += fieldHeight + 8 * dp;
        float buttonHeight = 24 * dp;
        float buttonWidth = Math.min(72 * dp, width - 20 * dp);
        // Material 3: filled pill in the primary colour (SketchButton). MaterialComponents (Bridge): the AppCompat
        // button, grey with black text
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(material3 ? primary : 0xFFD6D7D7);
        rect.set(x, y, x + buttonWidth, y + buttonHeight);
        float buttonRadius = material3 ? buttonHeight / 2f : 2 * dp;
        canvas.drawRoundRect(rect, buttonRadius, buttonRadius, paint);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setColor(material3 ? onPrimary : 0xDE000000);
        text.setTextSize(10 * dp);
        float labelWidth = text.measureText("Button");
        canvas.drawText("Button", x + (buttonWidth - labelWidth) / 2f, y + 15.5f * dp, text);

        // FAB in the accent colour
        float fab = 30 * dp;
        float fabLeft = left + width - fab - 10 * dp;
        float fabTop = bottom - fab - 10 * dp;
        paint.setColor(accent);
        rect.set(fabLeft, fabTop, fabLeft + fab, fabTop + fab);
        float fabRadius = material3 ? 9 * dp : fab / 2f;
        canvas.drawRoundRect(rect, fabRadius, fabRadius, paint);
        paint.setColor(onAccent);
        paint.setStrokeWidth(2 * dp);
        paint.setStyle(Paint.Style.STROKE);
        float cx = fabLeft + fab / 2f, cy = fabTop + fab / 2f, arm = 6 * dp;
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paint);
        canvas.drawLine(cx, cy - arm, cx, cy + arm, paint);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, dp));
        paint.setColor(0x33FFFFFF);
        rect.set(left, top, left + width, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private int role(String name, boolean night) {
        int value = palette == null ? 0 : palette.get(name, night);
        return value == 0 ? 0xFF808080 : value;
    }

    private String ellipsize(String value, float max) {
        if (text.measureText(value) <= max) return value;
        String shortened = value;
        while (shortened.length() > 1 && text.measureText(shortened + "…") > max) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }
}
