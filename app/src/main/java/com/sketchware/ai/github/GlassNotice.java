package com.sketchware.ai.github;

import android.app.Notification;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import com.sketchware.remod.R;

/**
 * v2.1.3 (owner's 22 Nov phone review: "show that notification in our glassmorphism theme too, so it's clearly ours"):
 * the app's notifications (GitHub backups and imports, the app update) as a dark glass card: an icon tile, the title in
 * white (an error's in #FF8A65, the update card's heading colour), a grey line and a honey-orange progress line, inside
 * Android's own notification frame (app name, time, the action buttons stay Android's).
 * <p>
 * The card is a RemoteViews layout that Android's notification shade inflates in its own process, so it uses framework
 * views, plain shape drawables, plain vector icons and colours only (a custom drawable class or one of the app's fonts
 * there would make Android refuse the notification and stop the app). The plain title and text stay set on the builder
 * too, for the lock screen, accessibility and watches.
 */
final class GlassNotice {

    static final int RUNNING = 0;
    static final int DONE = 1;
    static final int FAILED = 2;

    private GlassNotice() {
    }

    /**
     * Puts the glass card on {@code b}: {@code percent} &lt; 0 = no progress line; {@code state} {@link #RUNNING},
     * {@link #DONE} or {@link #FAILED}.
     */
    static Notification.Builder apply(Context context, Notification.Builder b, int icon, String title, String text,
                                      int percent, boolean indeterminate, int state) {
        try {
            RemoteViews small = card(context, R.layout.ob_notification_glass, icon, title, text, percent, indeterminate,
                    state);
            RemoteViews big = card(context, R.layout.ob_notification_glass_big, icon, title, text, percent, indeterminate,
                    state);
            b.setStyle(new Notification.DecoratedCustomViewStyle());
            b.setCustomContentView(small);
            b.setCustomBigContentView(big);
        } catch (RuntimeException e) {
            // the plain notification (its title and text are set already)
        }
        return b;
    }

    private static RemoteViews card(Context context, int layout, int icon, String title, String text, int percent,
                                    boolean indeterminate, int state) {
        RemoteViews r = new RemoteViews(context.getPackageName(), layout);
        r.setImageViewResource(R.id.ob_notif_icon, icon);
        r.setInt(R.id.ob_notif_icon, "setColorFilter", state == FAILED ? 0xFFFF8A65 : state == DONE ? 0xFFFFB877
                : 0xFFF5F5F7);
        r.setTextViewText(R.id.ob_notif_title, title == null ? "" : title);
        r.setTextColor(R.id.ob_notif_title, state == FAILED ? 0xFFFF8A65 : 0xFFF5F5F7);
        boolean hasText = text != null && !text.isEmpty();
        r.setTextViewText(R.id.ob_notif_text, hasText ? text : "");
        r.setViewVisibility(R.id.ob_notif_text, hasText ? View.VISIBLE : View.GONE);
        if (percent >= 0) {
            r.setViewVisibility(R.id.ob_notif_bar, View.VISIBLE);
            r.setProgressBar(R.id.ob_notif_bar, 100, Math.max(0, Math.min(100, percent)), indeterminate);
        } else {
            r.setViewVisibility(R.id.ob_notif_bar, View.GONE);
        }
        return r;
    }
}
