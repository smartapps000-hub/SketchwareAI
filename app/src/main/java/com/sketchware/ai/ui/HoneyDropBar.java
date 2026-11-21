package com.sketchware.ai.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sketchware.remod.R;

/**
 * v2.1.0 (owner's 16 Nov directive "Honey-Drop & Liquid Waves bottom bar"), v2.1.1 (owner's 21 Nov correction
 * "hyper-realistic transparent glass droplet and waves, no gold"): the projects screen's dock, six tabs (Home, Create,
 * GitHub, Play Store, Community, Settings) on an Obsidian glass bar (the 22dp glass radius, a 1dp {@code ob_divider}
 * rim). The bar's top edge is liquid: the selected tab sits on a raised glass crest, its icon lifted onto it with a
 * warm {@code #FF6D24} glow beneath. A tap plays four stages in slow motion, all in clear glass (white specular
 * highlights and rims over the Obsidian, amber only as a glow, no fill colour of its own):
 * <ol>
 * <li>descent: a glass teardrop (tapered neck above, heavy belly below) falls onto the tapped tab with three micro
 * droplets trailing beside and above it; the old crest sinks;</li>
 * <li>impact: the bar's top edge curves down into a crater under the drop and the tab's icon dips with the force;</li>
 * <li>rebound: a crown of glass spikes, then a central jet, throw five glass beads up and out, while the icon rises onto
 * the new crest;</li>
 * <li>waves: damped glass waves with white / amber edge highlights run from the impact to both ends of the bar, then
 * it settles.</li>
 * </ol>
 * A short sound ({@code res/raw/honey_drop.wav}: a water-drop chirp, a honey body and a ripple tail, made for this bar)
 * plays with it unless the phone is on silent or vibrate, or the sound is switched off in Settings ({@link #soundOn}).
 * Each tab is a real view with its name for TalkBack; the bar and crest are drawn behind them, the drop and splash over
 * them. {@link #setScrolledAway} slides the bar out of the way while the list scrolls down and back on the way up.
 */
public final class HoneyDropBar extends LinearLayout {

    public interface Listener {
        /** A tab was tapped: called after the impact (about 0.45 s after the tap), for the screen to open. */
        void onTab(int index);
    }

    public static final int HOME = 0;
    public static final int CREATE = 1;
    public static final int GITHUB = 2;
    public static final int STORE = 3;
    public static final int COMMUNITY = 4;
    public static final int SETTINGS = 5;
    public static final String[] LABELS = {"Home", "Create", "GitHub", "Play Store", "Community", "Settings"};
    static final int[] ICONS = {R.drawable.ob_ic_home, R.drawable.ob_ic_plus, R.drawable.ob_ic_github,
            R.drawable.ob_ic_store, R.drawable.ob_ic_community, R.drawable.ob_ic_settings};

    public static final String PREFS = "sketchware_ai_ui";
    public static final String KEY_SOUND = "honey_drop_sound";
    /** The whole tap, slow motion (descent, impact, rebound, waves). */
    static final long DURATION = 1150;
    /** Stage boundaries as fractions of {@link #DURATION}. */
    static final float LAND = 0.26f;
    static final float CRATER_END = 0.38f;
    /** The listener hears the tap after the crater (the screen opens over the splash). */
    static final float OPEN_AT = 0.40f;

    private final float density;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warm = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path body = new Path();
    private final Path top = new Path();
    private final Path shape = new Path();
    private final ImageView[] icons = new ImageView[LABELS.length];
    private final TextView[] labels = new TextView[LABELS.length];

    private Listener listener;
    private int selected = HOME;
    private int previous = HOME;
    /** The old crest (where the tap came from) and the impact (the tapped tab). */
    private float fromX;
    private float dropX;
    /** The tap's animation, 0..1 (NaN: at rest). */
    private float t = Float.NaN;
    private ValueAnimator animator;
    private SoundPool sounds;
    private int soundId;
    private boolean soundReady;
    private boolean scrolledAway;

    public HoneyDropBar(Context context) {
        this(context, null);
    }

    public HoneyDropBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(dp(3), dp(4), dp(3), dp(4));
        glass.setColor(0xF2141418);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(dp(1));
        rim.setColor(color(R.color.ob_divider));
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeCap(Paint.Cap.ROUND);
        edge.setStrokeJoin(Paint.Join.ROUND);
        dropRim.setStyle(Paint.Style.STROKE);
        dropRim.setStrokeWidth(Math.max(1f, density * 0.9f));
        spec.setColor(0xF2FFFFFF);
        for (int i = 0; i < LABELS.length; i++) addView(tabView(context, i), new LayoutParams(0, -1, 1f));
        refreshTabs();
    }

    private View tabView(Context context, int index) {
        LinearLayout tab = new LinearLayout(context);
        tab.setOrientation(VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setClipChildren(false);
        tab.setContentDescription(LABELS[index]);
        ImageView icon = new ImageView(context);
        icon.setImageResource(ICONS[index]);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(3), dp(3), dp(3), dp(3));
        tab.addView(icon, new LayoutParams(dp(30), dp(30)));
        TextView label = new TextView(context);
        label.setText(LABELS[index]);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        // never setSingleLine on centred text (it scrolls sideways and draws outside: v2.0.1, v2.0.4); at a large
        // font size the label shrinks to fit its tab ("Community" whole at 1.3)
        com.sketchware.ai.github.GitHubViews.fitOneLine(label, 6f, 9.5f);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        try {
            label.setTypeface(getResources().getFont(R.font.sora_bold));
        } catch (RuntimeException e) {
            label.setTypeface(Typeface.DEFAULT_BOLD);
        }
        LayoutParams lp = new LayoutParams(-1, -2);
        lp.topMargin = dp(2);
        tab.addView(label, lp);
        icons[index] = icon;
        labels[index] = label;
        tab.setOnClickListener(x -> tap(index));
        return tab;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int selected() {
        return selected;
    }

    /** A tap: the drop, the sound, the crest moves; the listener hears it after the impact. */
    void tap(int index) {
        // review (LOW): one tab at a time (a second tap while the drop falls would open a second screen)
        if (tapping) return;
        tapping = true;
        playSound();
        select(index, true);
        Listener l = listener;
        postDelayed(() -> {
            tapping = false;
            if (l != null) l.onTab(index);
        }, (long) (DURATION * OPEN_AT));
    }

    private boolean tapping;

    /** Moves the active tab to {@code index}, with the drop, the splash and the waves when {@code animate}. */
    public void select(int index, boolean animate) {
        if (index < 0 || index >= LABELS.length) return;
        previous = selected;
        selected = index;
        refreshTabs();
        if (!animate || getWidth() == 0) {
            if (animator != null) animator.cancel();
            t = Float.NaN;
            applyIcons();
            invalidate();
            return;
        }
        if (animator != null) animator.cancel();
        fromX = centerOf(previous);
        dropX = centerOf(index);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION);
        animator.setInterpolator(null);
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            applyIcons();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                t = Float.NaN;
                applyIcons();
                invalidate();
            }
        });
        animator.start();
    }

    /** v2.1.0: the bar slides down out of the way (the list scrolls down) or comes back (it scrolls up). */
    public void setScrolledAway(boolean away) {
        if (away == scrolledAway) return;
        scrolledAway = away;
        float to = away ? getHeight() + dp(16) : 0f;
        animate().translationY(to).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void refreshTabs() {
        for (int i = 0; i < icons.length; i++) {
            boolean on = i == selected;
            icons[i].setColorFilter(on ? 0xFFFFFFFF : color(R.color.ob_text_2));
            labels[i].setTextColor(on ? color(R.color.ob_accent) : color(R.color.ob_text_2));
            ((View) icons[i].getParent()).setSelected(on);
        }
    }

    private float centerOf(int index) {
        int inner = getWidth() - getPaddingLeft() - getPaddingRight();
        float slot = inner / (float) LABELS.length;
        return getPaddingLeft() + slot * index + slot / 2f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyIcons();
    }

    // ------------------------------------------------------------------ the physics (pure functions of t)

    /** How far the selected icon is lifted onto its crest at rest. */
    private float rise() {
        return dp(9);
    }

    /** The new crest's height (0..1 of its rest height): after the rebound, on a spring. */
    static float newCrest(float t) {
        if (Float.isNaN(t)) return 1f;
        return spring(clamp((t - CRATER_END) / 0.42f));
    }

    /** The old crest's height: it sinks while the drop falls. */
    static float oldCrest(float t) {
        if (Float.isNaN(t)) return 0f;
        float f = clamp(t / LAND);
        return 1f - f * f * (3 - 2 * f);
    }

    /** The crater's depth (0..1): it opens under the impact and springs back. */
    static float crater(float t) {
        if (Float.isNaN(t) || t < LAND - 0.02f) return 0f;
        float f = clamp((t - (LAND - 0.02f)) / (CRATER_END - LAND + 0.04f));
        return (float) Math.sin(Math.PI * f);
    }

    /** The waves' strength (0..1) and how far they have run (0..1 of the way to the far end). */
    static float waveAmp(float time) {
        return (float) Math.exp(-2.4 * time);
    }

    /** The selected icon's offset (down is +): a dip with the impact, then lifted onto the crest. */
    private float iconOffset(int index) {
        if (index == selected) {
            if (Float.isNaN(t)) return -rise();
            if (index == previous) return -rise();
            float dip = dp(3.5f) * crater(t);
            return dip - rise() * newCrest(t);
        }
        if (index == previous && !Float.isNaN(t)) return -rise() * oldCrest(t);
        return 0f;
    }

    private void applyIcons() {
        for (int i = 0; i < icons.length; i++) icons[i].setTranslationY(iconOffset(i));
    }

    /** The top edge's offset at {@code x} (down is +): crests, the crater and the travelling waves. */
    private float edgeAt(float x) {
        int w = getWidth();
        float slot = (w - getPaddingLeft() - getPaddingRight()) / (float) LABELS.length;
        float crestH = dp(12);
        float crestW = Math.min(dp(32), slot * 0.62f);
        float y = 0;
        if (Float.isNaN(t)) {
            y -= crestH * bump(x - centerOf(selected), crestW);
        } else {
            if (previous != selected) {
                y -= crestH * oldCrest(t) * bump(x - fromX, crestW);
                y -= crestH * newCrest(t) * bump(x - dropX, crestW);
            } else {
                y -= crestH * bump(x - dropX, crestW);
            }
            // the crater: a smooth depression under the impact
            float c = crater(t);
            if (c > 0) {
                float d = (x - dropX) / dp(16);
                y += dp(8) * c * (float) Math.exp(-d * d);
            }
            // the waves: they start at the rebound and run to both ends, several crests, damped
            float start = CRATER_END - 0.06f;
            if (t > start) {
                float time = (t - start) / (1f - start);
                float far = Math.max(dropX, w - dropX);
                float front = time * far * 1.35f + dp(10);
                float d = Math.abs(x - dropX);
                if (d < front) {
                    float k = (float) (2 * Math.PI / dp(36));
                    float lead = 1f - (float) Math.exp(-(front - d) / dp(10));
                    float spread = 0.5f + 0.5f * (float) Math.exp(-d / (w * 0.7f));
                    y += dp(4.2f) * waveAmp(time) * spread * lead * (float) Math.sin(k * (front - d));
                }
            }
        }
        // v2.1.2 (owner's 21 Nov phone screenshot: a cut bump above Home at the bar's left end): the edge is one curve with
        // the rounded corners (see onDraw), so the window only has to be 0 at the bar's very ends; before, it was still
        // half open where the corner arc ended and Home's crest rose there in a 4-5dp vertical step
        float r = dp(22);
        float win = smooth(x / (r * 1.1f)) * smooth((w - x) / (r * 1.1f));
        return y * win;
    }

    /** One point of the top edge: the body always, the highlight from a little into the corners. */
    private void edgePoint(float x, float y, boolean highlight, boolean[] first) {
        body.lineTo(x, y);
        if (!highlight) return;
        if (first[0]) {
            top.moveTo(x, y);
            first[0] = false;
        } else {
            top.lineTo(x, y);
        }
    }

    private static float bump(float dx, float half) {
        if (Math.abs(dx) >= half) return 0f;
        float c = (float) Math.cos(Math.PI * dx / half);
        return 0.5f * (1f + c);
    }

    private static float smooth(float f) {
        f = clamp(f);
        return f * f * (3 - 2 * f);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float r = dp(22);
        float step = Math.max(1f, dp(2));
        body.reset();
        top.reset();
        body.moveTo(0, h - r);
        body.lineTo(0, r);
        // v2.1.2: the top corners are sampled along their arcs with the edge's offset added, so an edge tab's crest grows
        // out of the corner (no step where the arc ends); the highlight starts a little into each corner
        boolean[] first = {true};
        int samples = 24;
        for (int i = 1; i <= samples; i++) {
            double a = Math.PI + (Math.PI / 2) * i / samples;
            float x = r + r * (float) Math.cos(a);
            edgePoint(x, r + r * (float) Math.sin(a) + edgeAt(x), x >= r * 0.3f, first);
        }
        for (float x = r + step; x < w - r; x += step) edgePoint(x, edgeAt(x), true, first);
        for (int i = 0; i <= samples; i++) {
            double a = 1.5 * Math.PI + (Math.PI / 2) * i / samples;
            float x = w - r + r * (float) Math.cos(a);
            edgePoint(x, r + r * (float) Math.sin(a) + edgeAt(x), x <= w - r * 0.3f, first);
        }
        body.lineTo(w, h - r);
        rect.set(w - 2 * r, h - 2 * r, w, h);
        body.arcTo(rect, 0, 90);
        body.lineTo(r, h);
        rect.set(0, h - 2 * r, 2 * r, h);
        body.arcTo(rect, 90, 90);
        body.close();
        canvas.drawPath(body, glass);
        drawGlowUnderIcon(canvas);
        canvas.drawPath(body, rim);
        drawEdgeHighlight(canvas);
        super.onDraw(canvas);
    }

    /** The warm glow under the lifted icon (its crest catches the light). */
    private void drawGlowUnderIcon(Canvas canvas) {
        if (getWidth() == 0) return;
        float strength = Float.isNaN(t) || previous == selected ? 1f : newCrest(t);
        float x = Float.isNaN(t) ? centerOf(selected) : dropX;
        View icon = icons[selected];
        View tab = (View) icon.getParent();
        float cy = tab.getTop() + icon.getTop() + icon.getHeight() / 2f + icon.getTranslationY();
        drawGlowAt(canvas, x, cy, strength);
        if (!Float.isNaN(t) && previous != selected) {
            View old = icons[previous];
            float oy = tab.getTop() + old.getTop() + old.getHeight() / 2f + old.getTranslationY();
            drawGlowAt(canvas, fromX, oy, oldCrest(t));
        }
    }

    /** A soft warm pool of light under the lifted icon (below it, so the icon's own shape stays clean). */
    private void drawGlowAt(Canvas canvas, float x, float y, float strength) {
        if (strength <= 0.02f) return;
        float radius = dp(24);
        float cy = y + dp(15);
        int a = (int) (0x40 * Math.min(1f, strength));
        glow.setShader(new RadialGradient(x, cy, radius, new int[]{(a << 24) | 0xFF6D24, ((a / 3) << 24) | 0xFFB347,
                0x00FF6D24}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, cy, radius, glow);
    }

    /** The glass top edge's specular line: white, brightest over the crest, amber where the waves run. */
    private void drawEdgeHighlight(Canvas canvas) {
        int w = getWidth();
        if (w == 0) return;
        float cx = Float.isNaN(t) ? centerOf(selected) : dropX;
        float energy = Float.isNaN(t) || t < CRATER_END - 0.06f ? 0f
                : waveAmp((t - (CRATER_END - 0.06f)) / (1f - CRATER_END + 0.06f));
        int amber = (int) (0x99 * energy);
        float p = clamp(cx / w);
        edge.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0x26FFFFFF, (amber << 24) | 0xFFB347, 0xB3FFFFFF, (amber << 24) | 0xFF6D24, 0x26FFFFFF},
                new float[]{0f, Math.max(0.01f, p - 0.18f), p, Math.min(0.99f, p + 0.18f), 1f}, Shader.TileMode.CLAMP));
        edge.setStrokeWidth(dp(1.2f) + dp(1.4f) * energy);
        canvas.drawPath(top, edge);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (Float.isNaN(t)) return;
        drawSplash(canvas);
        drawDrops(canvas);
    }

    /** The surface height at the impact point before the crater (where the drop lands and the splash starts). */
    private float impactY() {
        return -dp(12) * (previous == selected ? 1f : 0.15f);
    }

    /** Stage 1: the teardrop and its micro droplets fall; at the impact they sink into the crater and are gone. */
    private void drawDrops(Canvas canvas) {
        float land = impactY();
        float start = -dp(44);
        float r = dp(6.5f);
        if (t < LAND + 0.06f) {
            float f = clamp(t / LAND);
            float y = start + (land - start) * f * f;
            float sx = 1f - 0.12f * f;
            float sy = 1f + 0.22f * f;
            float alpha = 1f;
            if (t > LAND) {
                // sinking into the crater: squashed, then gone
                float g = clamp((t - LAND) / 0.06f);
                y = land + dp(5) * g;
                sx = 0.88f + 0.45f * g;
                sy = 1.22f - 0.8f * g;
                alpha = 1f - g;
            }
            drawTeardrop(canvas, dropX, y - r * 0.2f, r, sx, sy, alpha);
        }
        // three micro droplets falling beside and above it (each a little later, smaller)
        float[][] micro = {{-dp(7), 0.07f, dp(2.1f)}, {dp(6), 0.13f, dp(1.6f)}, {-dp(2.5f), 0.2f, dp(1.2f)}};
        for (float[] m : micro) {
            float lag = m[1];
            float f = clamp((t - lag) / (LAND));
            if (t < lag) continue;
            float y = start - dp(6) + (land - start + dp(6)) * f * f;
            float alpha = t > LAND + lag ? 1f - clamp((t - LAND - lag) / 0.05f) : 1f;
            if (alpha <= 0) continue;
            drawBead(canvas, dropX + m[0] * (1f - 0.4f * f), y, m[2], alpha, 1f + 0.25f * f);
        }
    }

    /** Stage 3: the crown, the central jet and the beads it throws. */
    private void drawSplash(Canvas canvas) {
        float base = impactY() + dp(2);
        // the crown: four glass spikes from the crater's rim, leaning out
        if (t > CRATER_END - 0.04f && t < CRATER_END + 0.16f) {
            float f = clamp((t - (CRATER_END - 0.04f)) / 0.2f);
            float hgt = (float) Math.sin(Math.PI * f);
            float[][] spikes = {{-dp(10), -dp(5), dp(12)}, {-dp(4), -dp(2), dp(16)}, {dp(4), dp(2), dp(16)},
                    {dp(10), dp(5), dp(12)}};
            for (float[] s : spikes) {
                float bx = dropX + s[0];
                float tipX = bx + s[1] * (0.5f + f);
                float tipY = base - s[2] * hgt;
                drawSpike(canvas, bx, base, tipX, tipY, dp(1.7f), 0.9f * (1f - 0.5f * f));
            }
        }
        // the central jet (it rises as the crown falls back)
        if (t > CRATER_END + 0.06f && t < CRATER_END + 0.32f) {
            float f = clamp((t - (CRATER_END + 0.06f)) / 0.26f);
            float hgt = dp(22) * (float) Math.sin(Math.PI * f);
            if (hgt > dp(1)) {
                drawSpike(canvas, dropX, base, dropX, base - hgt, dp(2.6f), 0.95f);
                drawBead(canvas, dropX, base - hgt - dp(2.2f), dp(2.6f), 1f - 0.6f * f, 1f);
            }
        }
        // five beads thrown up and out, falling back on a parabola
        if (t > CRATER_END - 0.02f && t < CRATER_END + 0.4f) {
            float s = clamp((t - (CRATER_END - 0.02f)) / 0.4f);
            float[][] beads = {{-dp(30), dp(34), dp(2.2f)}, {-dp(14), dp(44), dp(1.6f)}, {dp(5), dp(50), dp(1.9f)},
                    {dp(18), dp(40), dp(1.4f)}, {dp(32), dp(30), dp(2.0f)}};
            for (float[] b : beads) {
                float x = dropX + b[0] * s;
                float y = base - (b[1] * 2.2f * s - dp(70) * s * s);
                float alpha = 1f - smooth((s - 0.6f) / 0.4f);
                drawBead(canvas, x, y, b[2], alpha, 1f);
            }
        }
    }

    /**
     * A clear glass teardrop: tapered neck above, heavy belly below; brighter at its rim (Fresnel), a white specular
     * highlight, a warm amber caustic in its belly.
     */
    private void drawTeardrop(Canvas canvas, float cx, float cy, float r, float sx, float sy, float alpha) {
        if (alpha <= 0) return;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(sx, sy);
        shape.reset();
        shape.moveTo(0, -r * 2.6f);
        shape.cubicTo(r * 0.12f, -r * 2.05f, r * 1.02f, -r * 1.0f, r, 0);
        shape.cubicTo(r * 0.98f, r * 0.6f, r * 0.56f, r, 0, r);
        shape.cubicTo(-r * 0.56f, r, -r * 0.98f, r * 0.6f, -r, 0);
        shape.cubicTo(-r * 1.02f, -r * 1.0f, -r * 0.12f, -r * 2.05f, 0, -r * 2.6f);
        shape.close();
        glassFill(r, alpha, -r * 0.3f, -r * 0.15f, r * 1.5f);
        canvas.drawPath(shape, dropFill);
        warm.setShader(new RadialGradient(r * 0.15f, r * 0.62f, r * 0.55f, new int[]{a(0x3A, alpha) | 0xFF6D24,
                0x00FF6D24}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, warm);
        dropRim.setShader(new LinearGradient(-r, -r * 2.6f, r, r, new int[]{a(0xE6, alpha) | 0xFFFFFF,
                a(0x55, alpha) | 0xFFFFFF, a(0x99, alpha) | 0xFFB347}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, dropRim);
        spec.setAlpha((int) (0xF2 * alpha));
        rect.set(-r * 0.62f, -r * 0.72f, -r * 0.18f, -r * 0.2f);
        canvas.drawOval(rect, spec);
        spec.setAlpha((int) (0x99 * alpha));
        canvas.drawCircle(r * 0.42f, r * 0.45f, r * 0.12f, spec);
        canvas.restore();
    }

    /** A small glass bead (a micro droplet or a splash bead). */
    private void drawBead(Canvas canvas, float cx, float cy, float r, float alpha, float stretch) {
        if (alpha <= 0) return;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(1f / (float) Math.sqrt(stretch), stretch);
        shape.reset();
        shape.addCircle(0, 0, r, Path.Direction.CW);
        glassFill(r, alpha, -r * 0.3f, -r * 0.3f, r * 1.3f);
        canvas.drawPath(shape, dropFill);
        dropRim.setShader(null);
        dropRim.setColor(a(0xCC, alpha) | 0xFFFFFF);
        canvas.drawPath(shape, dropRim);
        spec.setAlpha((int) (0xF2 * alpha));
        canvas.drawCircle(-r * 0.35f, -r * 0.35f, Math.max(dp(0.6f), r * 0.32f), spec);
        canvas.restore();
    }

    /** A tapered glass spike from the surface (a crown spike or the central jet). */
    private void drawSpike(Canvas canvas, float bx, float by, float tx, float ty, float halfWidth, float alpha) {
        shape.reset();
        shape.moveTo(bx - halfWidth, by);
        shape.quadTo(bx - halfWidth * 0.4f, (by + ty) / 2f, tx, ty);
        shape.quadTo(bx + halfWidth * 0.4f, (by + ty) / 2f, bx + halfWidth, by);
        shape.close();
        dropFill.setShader(new LinearGradient(bx, by, tx, ty, new int[]{a(0x18, alpha) | 0xFFFFFF,
                a(0x5A, alpha) | 0xFFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, dropFill);
        dropRim.setShader(new LinearGradient(bx, by, tx, ty, new int[]{a(0x40, alpha) | 0xFFFFFF,
                a(0xE6, alpha) | 0xFFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, dropRim);
    }

    /** Glass: nearly clear in the middle, brighter towards the rim. */
    private void glassFill(float r, float alpha, float cx, float cy, float radius) {
        dropFill.setShader(new RadialGradient(cx, cy, radius, new int[]{a(0x10, alpha) | 0xFFFFFF,
                a(0x24, alpha) | 0xFFFFFF, a(0x5E, alpha) | 0xFFFFFF}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
    }

    private static int a(int max, float alpha) {
        return ((int) (max * clamp(alpha)) & 0xFF) << 24;
    }

    // ------------------------------------------------------------------ the sound

    /** Whether the droplet sound plays (Settings → Honey-Drop sound; on by default). */
    public static boolean soundOn(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SOUND, true);
    }

    public static void setSoundOn(Context context, boolean on) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SOUND, on).apply();
    }

    private void playSound() {
        Context c = getContext();
        if (!soundOn(c)) return;
        AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        // silent or vibrate: no sound
        if (audio != null && audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
        try {
            if (sounds == null) {
                sounds = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
                sounds.setOnLoadCompleteListener((pool, id, status) -> {
                    soundReady = status == 0;
                    if (soundReady) pool.play(id, 0.55f, 0.55f, 1, 0, 1f);
                });
                soundId = sounds.load(c, R.raw.honey_drop, 1);
                return;
            }
            if (soundReady) sounds.play(soundId, 0.55f, 0.55f, 1, 0, 1f);
        } catch (RuntimeException ignored) {
            // no sound on this phone: the bar works the same
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        if (sounds != null) {
            sounds.release();
            sounds = null;
            soundReady = false;
        }
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ helpers

    /** For the renders: the animation at fraction {@code fraction} of a tap from {@code from} to {@code to}. */
    public void frameForTests(int from, int to, float fraction) {
        previous = from;
        selected = to;
        refreshTabs();
        fromX = centerOf(from);
        dropX = centerOf(to);
        t = fraction;
        applyIcons();
        invalidate();
    }

    private static float clamp(float f) {
        return f < 0 ? 0 : f > 1 ? 1 : f;
    }

    /** An underdamped spring from 0 to 1 (a small overshoot, then rest). */
    static float spring(float f) {
        if (f <= 0) return 0;
        if (f >= 1) return 1;
        return (float) (1 - Math.exp(-6.5 * f) * Math.cos(9.0 * f));
    }

    private int color(int res) {
        return getResources().getColor(res, null);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
