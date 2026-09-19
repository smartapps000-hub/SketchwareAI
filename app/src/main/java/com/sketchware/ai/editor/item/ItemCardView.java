package com.sketchware.ai.editor.item;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.item.ItemLinearLayout;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Design-canvas preview of androidx CardView.
 * <p>
 * Stock Sketchware draws a CardView as a plain LinearLayout (a flat grey-outlined slab). This
 * preview reads the card's own attributes from its Inject property — app:cardCornerRadius,
 * app:cardElevation, app:cardBackgroundColor — and draws the rounded, shadowed card the app
 * will actually show. Missing attributes fall back to androidx CardView's defaults (2dp radius,
 * 2dp elevation, white), so the preview never promises something the built app won't render.
 */
public class ItemCardView extends ItemLinearLayout {

    private static final Pattern RADIUS = Pattern.compile("app:cardCornerRadius\\s*=\\s*\"([0-9.]+)dp\"");
    private static final Pattern ELEVATION = Pattern.compile("app:cardElevation\\s*=\\s*\"([0-9.]+)dp\"");
    private static final Pattern BACKGROUND = Pattern.compile("app:cardBackgroundColor\\s*=\\s*\"(#[0-9A-Fa-f]{6,8})\"");

    private float radiusPx;
    private final GradientDrawable surface = new GradientDrawable();

    public ItemCardView(Context context) {
        super(context);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        applyCardStyle(0x00FFFFFF);
    }

    @Override
    public void setBean(ViewBean bean) {
        super.setBean(bean);
        applyCardStyle(bean == null ? 0x00FFFFFF : bean.layout.backgroundColor);
    }

    @Override
    public void setBackgroundColor(int color) {
        applyCardStyle(color);
    }

    private void applyCardStyle(int layoutBackground) {
        float density = getResources().getDisplayMetrics().density;
        String inject = getBean() == null || getBean().inject == null ? "" : getBean().inject;

        float radiusDp = parse(RADIUS, inject, 2f);
        float elevationDp = parse(ELEVATION, inject, 2f);
        int color = Color.WHITE;
        Matcher bg = BACKGROUND.matcher(inject);
        if (bg.find()) {
            try {
                color = Color.parseColor(bg.group(1));
            } catch (IllegalArgumentException ignored) {
            }
        } else if (Color.alpha(layoutBackground) != 0) {
            color = layoutBackground;
        }

        radiusPx = radiusDp * density;
        surface.setColor(color);
        surface.setCornerRadius(radiusPx);
        super.setBackground(surface);
        setElevation(elevationDp * density);
        invalidateOutline();
    }

    private static float parse(Pattern pattern, String text, float fallback) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) return fallback;
        try {
            return Math.min(64f, Float.parseFloat(m.group(1)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
