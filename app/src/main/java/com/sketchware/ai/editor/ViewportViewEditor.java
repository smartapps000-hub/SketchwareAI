package com.sketchware.ai.editor;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import com.besome.sketch.editor.view.ViewEditor;
import a.a.a.GB;
import com.sketchware.ai.project.DeviceTargets;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * v1.9.0: the design canvas with device viewports (design editor header → Device). Phone is upstream's canvas
 * unchanged ({@link ViewEditor#a()}: the phone's own screen size, scaled into the editor). Foldable, tablet, Wear OS
 * and Android TV lay the screen out at that device's size in dp ({@link DeviceTargets#viewport}) and scale it into
 * the same space, so widgets wrap and stretch as they would there; Wear OS is clipped round.
 * <p>
 * {@link #layoutFrame} is upstream's {@code ViewEditor.a()} (read from its bytecode) with the frame size as a
 * parameter: the same margins, status bar and toolbar strips, scale and offsets.
 */
public class ViewportViewEditor extends ViewEditor {

    private static String viewport = DeviceTargets.PHONE;
    private static final List<WeakReference<ViewportViewEditor>> LIVE = new ArrayList<>();

    private boolean clipped;

    public ViewportViewEditor(Context context) {
        super(context);
        register();
    }

    public ViewportViewEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        register();
    }

    private void register() {
        synchronized (LIVE) {
            LIVE.add(new WeakReference<>(this));
        }
    }

    /** The viewport every design canvas shows from now on (DeviceTargets target name). */
    public static void select(String target) {
        viewport = target == null ? DeviceTargets.PHONE : target;
        synchronized (LIVE) {
            for (int i = LIVE.size() - 1; i >= 0; i--) {
                ViewportViewEditor editor = LIVE.get(i).get();
                if (editor == null) {
                    LIVE.remove(i);
                    continue;
                }
                editor.post(editor::refresh);
            }
        }
    }

    public static String selected() {
        return viewport;
    }

    private void refresh() {
        P = true;   // ViewEditor.onLayout lays the frame out again when P is set
        requestLayout();
        try {
            a();
        } catch (RuntimeException ignored) {
            // laid out at the next layout pass
        }
    }

    @Override
    public void a() {
        DeviceTargets.Viewport port = DeviceTargets.viewport(viewport);
        if (port.widthDp <= 0) {
            unclip();
            super.a();
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        layoutFrame((int) (port.widthDp * density), (int) (port.heightDp * density), port.round);
    }

    /** Upstream's frame layout (ViewEditor.a()) for a screen of width × height pixels. */
    private void layoutFrame(int width, int height, boolean round) {
        n.setVisibility(S ? View.VISIBLE : View.GONE);   // toolbar strip
        k.setVisibility(T ? View.GONE : View.VISIBLE);   // status bar strip
        p.setVisibility(View.VISIBLE);
        g = width;
        h = height;
        // the space the editor has is the phone's, as upstream computes it
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int bottomBar = (int) (f * 21.0f);
        boolean landscape = g > h;
        int marginX = (int) (f * (landscape ? 8.0f : 2.0f));
        int marginY = (int) (f * 4.0f);
        int statusBar = GB.f(getContext());
        int toolbar = GB.a(getContext());
        int availableWidth = screenWidth - (int) (98.0f * f);
        int availableHeight = screenHeight - statusBar - toolbar - (int) (f * 48.0f) - (int) (f * 48.0f);
        if (ca == 0 && da) availableHeight -= bottomBar;
        float frameScale = Math.min((float) availableWidth / g, (float) availableHeight / h);
        float scale = Math.min((float) (availableWidth - marginX * 2) / g, (float) (availableHeight - marginY * 2) / h);

        if (round) {
            GradientDrawable bezel = new GradientDrawable();
            bezel.setShape(GradientDrawable.OVAL);
            bezel.setColor(0xFF202124);
            bezel.setStroke((int) (6 * getResources().getDisplayMetrics().density / Math.max(frameScale, 0.01f)), 0xFF5F6368);
            aa.setBackground(bezel);
        } else {
            aa.setBackgroundResource(landscape ? com.sketchware.remod.R.drawable.new_view_pane_background_land
                    : com.sketchware.remod.R.drawable.new_view_pane_background_port);
        }
        aa.setLayoutParams(new FrameLayout.LayoutParams(g, h));
        aa.setScaleX(frameScale);
        aa.setScaleY(frameScale);
        aa.setX(-(int) ((g - g * frameScale) / 2f));
        aa.setY(-(int) ((h - h * frameScale) / 2f));

        int x = marginX - (int) ((g - g * scale) / 2f);
        int top = marginY;
        if (k.getVisibility() == View.VISIBLE) {
            k.setLayoutParams(new FrameLayout.LayoutParams(g, statusBar));
            k.setScaleX(scale);
            k.setScaleY(scale);
            float scaled = statusBar * scale;
            k.setX(x);
            k.setY(marginY - (int) ((statusBar - scaled) / 2f));
            top = marginY + (int) scaled;
        }
        int paneTop = top;
        if (n.getVisibility() == View.VISIBLE) {
            n.setLayoutParams(new FrameLayout.LayoutParams(g, toolbar));
            n.setScaleX(scale);
            n.setScaleY(scale);
            float scaled = toolbar * scale;
            n.setX(x);
            n.setY(top - (int) ((toolbar - scaled) / 2f));
            paneTop = top + (int) scaled;
        }
        int paneHeight = h;
        if (k.getVisibility() == View.VISIBLE) paneHeight -= statusBar;
        if (n.getVisibility() == View.VISIBLE) paneHeight -= toolbar;
        p.setLayoutParams(new FrameLayout.LayoutParams(g, paneHeight));
        p.setScaleX(scale);
        p.setScaleY(scale);
        p.setX(x);
        p.setY(paneTop - (int) ((paneHeight - scale * paneHeight) / 2f));
        if (round) {
            // a round face: the frame, the strips and the pane are cut to one circle the size of the screen
            int statusOffset = k.getVisibility() == View.VISIBLE ? statusBar : 0;
            int paneOffset = statusOffset + (n.getVisibility() == View.VISIBLE ? toolbar : 0);
            clip(aa, 0, g);
            clip(k, 0, g);
            clip(n, statusOffset, g);
            clip(p, paneOffset, g);
            clipped = true;
        } else {
            unclip();
        }
        P = false;
    }

    /** Cuts {@code view} to the screen's circle: diameter {@code size}, the view starting {@code top} below the screen's top. */
    private static void clip(View view, final int top, final int size) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setOval(0, -top, size, size - top);
            }
        });
        view.setClipToOutline(true);
        view.invalidateOutline();
    }

    private void unclip() {
        if (!clipped) return;
        for (View view : new View[]{aa, k, n, p}) {
            view.setClipToOutline(false);
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
        clipped = false;
    }
}
