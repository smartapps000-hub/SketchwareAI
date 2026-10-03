package com.sketchware.ai.sourcemap;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

/**
 * Brings one block of the logic editor into view and marks it for a few seconds: what "Open
 * block" in the compile log ends with.
 */
public final class BlockFocus {

    private BlockFocus() {
    }

    /**
     * @param scroller the editor's scroll container (scrolls with scrollTo)
     * @param pane     its only child, the pane the blocks are placed on
     * @param block    the block's view, somewhere below {@code pane}
     * @param width    the block's drawn size; its view size is used when 0
     * @return the mark that was put over the block (it removes itself)
     */
    public static GradientDrawable show(ViewGroup scroller, final ViewGroup pane, View block, int width, int height) {
        int[] paneOnScreen = new int[2];
        int[] blockOnScreen = new int[2];
        pane.getLocationOnScreen(paneOnScreen);
        block.getLocationOnScreen(blockOnScreen);
        // Position on the pane; the same whatever the scroll position is.
        int x = blockOnScreen[0] - paneOnScreen[0];
        int y = blockOnScreen[1] - paneOnScreen[1];
        if (width <= 0) width = block.getWidth();
        if (height <= 0) height = block.getHeight();
        int pad = Math.round(6 * pane.getResources().getDisplayMetrics().density);
        int maxX = Math.max(0, pane.getWidth() - scroller.getWidth());
        int maxY = Math.max(0, pane.getHeight() - scroller.getHeight());
        scroller.scrollTo(Math.max(0, Math.min(maxX, x - pad * 4)),
                Math.max(0, Math.min(maxY, y - scroller.getHeight() / 3)));

        final GradientDrawable ring = new GradientDrawable();
        ring.setColor(0x33FF7A29);
        ring.setStroke(Math.max(2, pad / 2), 0xFFFF7A29);
        ring.setCornerRadius(pad * 2);
        ring.setBounds(x - pad, y - pad, x + width + pad, y + height + pad);
        pane.getOverlay().add(ring);
        final ValueAnimator pulse = ValueAnimator.ofInt(255, 70);
        pulse.setDuration(450);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(7);
        pulse.addUpdateListener(animation -> {
            ring.setAlpha((Integer) animation.getAnimatedValue());
            pane.invalidate();
        });
        pulse.start();
        // Removed by time, not by the animation's end: with system animations switched off the
        // pulse ends at once, and the mark should still be seen.
        pane.postDelayed(() -> {
            pulse.cancel();
            pane.getOverlay().remove(ring);
        }, 3600);
        return ring;
    }
}
