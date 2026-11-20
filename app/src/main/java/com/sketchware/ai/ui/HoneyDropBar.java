package com.sketchware.ai.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
 * v2.1.0 (owner's 16 Nov directive "Honey-Drop & Liquid Waves bottom bar"): the projects screen's dock, six tabs
 * (Home, Create, GitHub, Play Store, Community, Settings) on an Obsidian glass bar (the 22dp glass radius, a 1dp
 * {@code ob_divider} rim). A tap drops a glossy honey drop (#FF6D24 → #FFB347 → #FFD166) onto the tab's icon: it falls,
 * squashes on impact and sends damped liquid waves along the bar's top edge, while the honey pill under the active icon
 * flows to the new tab (its trailing edge follows on a spring, so it stretches like a liquid and settles). A short
 * droplet sound ({@code res/raw/honey_drop.wav}, made for this bar) plays with it unless the phone is on silent or
 * vibrate, or the sound is switched off in Settings ({@link #soundOn}). Each tab is a real view with its name for
 * TalkBack; the drawing is behind (glass, pill) and over (drop, waves) them. {@link #setScrolledAway} slides the bar out
 * of the way while the list scrolls down and back on the way up.
 */
public final class HoneyDropBar extends LinearLayout {

    public interface Listener {
        /** A tab was tapped: called when the drop lands (about 0.25 s after the tap), for the screen to open. */
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
    static final long DURATION = 760;

    private final float density;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint honey = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final ImageView[] icons = new ImageView[LABELS.length];
    private final TextView[] labels = new TextView[LABELS.length];

    private Listener listener;
    private int selected = HOME;
    /** The pill's leading and trailing centres (they meet when it rests). */
    private float headX = Float.NaN;
    private float tailX = Float.NaN;
    private float fromX;
    /** The drop's animation, 0..1 (NaN: no drop). */
    private float t = Float.NaN;
    private float dropX;
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
        setPadding(dp(6), dp(4), dp(6), dp(4));
        glass.setColor(0xF2141418);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(dp(1));
        rim.setColor(color(R.color.ob_divider));
        gloss.setColor(0x66FFFFFF);
        wave.setStyle(Paint.Style.STROKE);
        wave.setStrokeCap(Paint.Cap.ROUND);
        wave.setStrokeWidth(dp(2.2f));
        for (int i = 0; i < LABELS.length; i++) addView(tabView(context, i), new LayoutParams(0, -1, 1f));
        refreshTabs();
    }

    private View tabView(Context context, int index) {
        LinearLayout tab = new LinearLayout(context);
        tab.setOrientation(VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setContentDescription(LABELS[index]);
        ImageView icon = new ImageView(context);
        icon.setImageResource(ICONS[index]);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(3), dp(3), dp(3), dp(3));
        tab.addView(icon, new LayoutParams(dp(30), dp(30)));
        TextView label = new TextView(context);
        label.setText(LABELS[index]);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        // never setSingleLine on centred text (it scrolls sideways and draws outside: v2.0.1, v2.0.4)
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
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

    /** A tap: the drop, the sound, the pill flows; the listener hears it when the drop lands. */
    void tap(int index) {
        playSound();
        select(index, true);
        Listener l = listener;
        postDelayed(() -> {
            if (l != null) l.onTab(index);
        }, (long) (DURATION * 0.34f));
    }

    /** Moves the active tab to {@code index}, with the drop and the waves when {@code animate}. */
    public void select(int index, boolean animate) {
        if (index < 0 || index >= LABELS.length) return;
        selected = index;
        refreshTabs();
        float target = centerOf(index);
        if (!animate || Float.isNaN(headX) || getWidth() == 0) {
            if (animator != null) animator.cancel();
            headX = tailX = target;
            t = Float.NaN;
            invalidate();
            return;
        }
        if (animator != null) animator.cancel();
        fromX = (headX + tailX) / 2f;
        dropX = target;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION);
        animator.setInterpolator(null);
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            // the pill: its head reaches the tab as the drop lands, its tail follows (a liquid stretch), both settle
            float head = ease(clamp((t - 0.12f) / 0.36f));
            float tail = spring(clamp((t - 0.22f) / 0.78f));
            headX = fromX + (dropX - fromX) * head;
            tailX = fromX + (dropX - fromX) * tail;
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                t = Float.NaN;
                headX = tailX = centerOf(selected);
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
        honey.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFFFD166, 0xFFFFB347, 0xFFFF6D24},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        if (animator == null || !animator.isRunning()) headX = tailX = centerOf(selected);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        float r = dp(22);
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, r, r, glass);
        rect.inset(dp(0.5f), dp(0.5f));
        canvas.drawRoundRect(rect, r, r, rim);
        drawPill(canvas);
        super.onDraw(canvas);
    }

    /** The honey pill behind the active icon: from the tail to the head while it flows, a rounded pill at rest. */
    private void drawPill(Canvas canvas) {
        if (Float.isNaN(headX)) return;
        View icon = icons[selected];
        View tab = (View) icon.getParent();
        float cy = tab.getTop() + icon.getTop() + icon.getHeight() / 2f;
        float half = dp(20);
        float halfH = dp(15);
        float left = Math.min(headX, tailX) - half;
        float right = Math.max(headX, tailX) + half;
        // stretched: a little thinner in the middle of its run, like a pulled liquid
        float stretch = Math.abs(headX - tailX) / Math.max(1f, getWidth() / 3f);
        float h = halfH * (1f - 0.22f * Math.min(1f, stretch));
        rect.set(left, cy - h, right, cy + h);
        canvas.drawRoundRect(rect, h, h, honey);
        // its gloss
        rect.set(left + dp(6), cy - h + dp(3), right - dp(6), cy - h + dp(7));
        canvas.drawRoundRect(rect, dp(2), dp(2), gloss);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (Float.isNaN(t)) return;
        drawWaves(canvas);
        drawDrop(canvas);
    }

    /** The drop: falls (0..0.34), squashes on impact (0.34..0.46), then melts into the pill. */
    private void drawDrop(Canvas canvas) {
        View icon = icons[selected];
        View tab = (View) icon.getParent();
        float landY = tab.getTop() + icon.getTop() + icon.getHeight() / 2f;
        float startY = -dp(26);
        float r = dp(7);
        float y;
        float sx = 1f;
        float sy = 1f;
        int alpha = 255;
        if (t < 0.34f) {
            float f = t / 0.34f;
            y = startY + (landY - startY) * f * f; // gravity
            sy = 1f + 0.25f * f; // stretched while falling
            sx = 1f - 0.12f * f;
        } else if (t < 0.46f) {
            float f = (t - 0.34f) / 0.12f;
            y = landY;
            sy = 1.25f - 0.6f * (float) Math.sin(f * Math.PI); // squash
            sx = 0.88f + 0.5f * (float) Math.sin(f * Math.PI);
            alpha = (int) (255 * (1f - 0.4f * f));
        } else {
            float f = Math.min(1f, (t - 0.46f) / 0.2f);
            y = landY;
            sx = sy = 1f - 0.7f * f;
            alpha = (int) (155 * (1f - f));
            if (alpha <= 0) return;
        }
        canvas.save();
        canvas.translate(dropX, y);
        canvas.scale(sx, sy);
        // a teardrop: round below, a tapered tip above (two cubic curves)
        path.reset();
        path.moveTo(0, -r * 2.3f);
        path.cubicTo(r * 0.55f, -r * 1.35f, r * 1.05f, -r * 0.55f, r, 0);
        path.cubicTo(r * 0.98f, r * 0.62f, r * 0.55f, r, 0, r);
        path.cubicTo(-r * 0.55f, r, -r * 0.98f, r * 0.62f, -r, 0);
        path.cubicTo(-r * 1.05f, -r * 0.55f, -r * 0.55f, -r * 1.35f, 0, -r * 2.3f);
        path.close();
        honey.setAlpha(alpha);
        canvas.drawPath(path, honey);
        honey.setAlpha(255);
        gloss.setAlpha(Math.min(160, alpha));
        canvas.drawCircle(-r * 0.35f, -r * 0.3f, r * 0.28f, gloss);
        gloss.setAlpha(0x66);
        canvas.restore();
    }

    /**
     * The waves: from the impact (t 0.34) a damped sine travels both ways along the bar's top edge, fading with
     * distance and time.
     */
    private void drawWaves(Canvas canvas) {
        if (t < 0.34f) return;
        float time = (t - 0.34f) / 0.66f;
        float amplitude = dp(4.5f) * (float) Math.exp(-3.2f * time);
        if (amplitude < dp(0.3f)) return;
        float top = dp(1.2f);
        float k = (float) (2 * Math.PI / dp(26));
        float travel = time * getWidth() * 0.9f;
        path.reset();
        boolean started = false;
        float step = dp(2);
        for (float x = dp(14); x <= getWidth() - dp(14); x += step) {
            float d = Math.abs(x - dropX);
            if (d > travel + dp(10)) {
                if (started) {
                    canvas.drawPath(path, wave);
                    path.reset();
                    started = false;
                }
                continue;
            }
            float fade = (float) Math.exp(-d / (getWidth() * 0.35f));
            float y = top - amplitude * fade * (float) Math.cos(k * (d - travel));
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }
        if (started) {
            wave.setShader(null);
            wave.setColor(0xFFFFB347);
            wave.setAlpha((int) (230 * Math.max(0f, 1f - time)));
            canvas.drawPath(path, wave);
        }
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
        selected = to;
        refreshTabs();
        fromX = centerOf(from);
        dropX = centerOf(to);
        t = fraction;
        float head = ease(clamp((t - 0.12f) / 0.36f));
        float tail = spring(clamp((t - 0.22f) / 0.78f));
        headX = fromX + (dropX - fromX) * head;
        tailX = fromX + (dropX - fromX) * tail;
        invalidate();
    }

    private static float clamp(float f) {
        return f < 0 ? 0 : f > 1 ? 1 : f;
    }

    private static float ease(float f) {
        return 1f - (1f - f) * (1f - f) * (1f - f);
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
