package com.sketchware.ai.ui;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.sketchware.remod.R;

/** v1.9.1: the Export screen's cards (ExportProjectActivity): a notice under each title, result rows that fit their text. */
public final class ExportCards {

    private ExportCards() {
    }

    /**
     * A pill and a line of text under a card's title; the card's button, loading animation and result area move below
     * them instead of sitting at fixed offsets from the card's top (they used to overlap a two-line notice).
     */
    public static void addNotice(TextView title, String badgeText, String subtitleText, View... below) {
        android.content.Context context = title.getContext();
        if (!(title.getParent() instanceof RelativeLayout)) return;
        RelativeLayout card = (RelativeLayout) title.getParent();
        if (title.getId() == View.NO_ID) title.setId(View.generateViewId());
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout notice = new LinearLayout(context);
        notice.setId(View.generateViewId());
        notice.setOrientation(LinearLayout.VERTICAL);
        notice.setPadding(0, 0, (int) (12 * d), 0);
        TextView badge = new TextView(context);
        badge.setText(badgeText);
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.ob_accent));
        badge.setBackgroundResource(R.drawable.ob_amber_pill);
        badge.setPadding((int) (10 * d), (int) (3 * d), (int) (10 * d), (int) (3 * d));
        notice.addView(badge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView subtitle = new TextView(context);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(12);
        subtitle.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.ob_text_2));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = (int) (6 * d);
        notice.addView(subtitle, subtitleParams);
        RelativeLayout.LayoutParams noticeParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        noticeParams.addRule(RelativeLayout.BELOW, title.getId());
        noticeParams.addRule(RelativeLayout.ALIGN_LEFT, title.getId());
        noticeParams.topMargin = (int) (8 * d);
        card.addView(notice, noticeParams);
        for (View view : below) {
            if (view == null || view.getParent() != card) continue;
            RelativeLayout.LayoutParams params = view.getLayoutParams() instanceof RelativeLayout.LayoutParams
                    ? new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) view.getLayoutParams())
                    : new RelativeLayout.LayoutParams(view.getLayoutParams());
            params.addRule(RelativeLayout.BELOW, notice.getId());
            params.topMargin = (int) (12 * d);
            view.setLayoutParams(params);
        }
    }

    /**
     * v1.9.1: rows of a card's result area had a fixed 24 dp height, so a path in a larger font (the phone's font size)
     * spilled out of its row under the Send button. Each row now grows with its text (24 dp at least), and the Send
     * button keeps a gap above it.
     */
    public static void fitResultRows(ViewGroup resultArea, View sendButton) {
        android.content.Context context = resultArea.getContext();
        float d = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < resultArea.getChildCount(); i++) {
            View row = resultArea.getChildAt(i);
            if (row == sendButton) continue;
            ViewGroup.LayoutParams params = row.getLayoutParams();
            if (row instanceof LinearLayout && params != null && params.height > 0) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                row.setMinimumHeight((int) (24 * d));
                row.setLayoutParams(params);
                if (((LinearLayout) row).getChildCount() == 1 && ((LinearLayout) row).getChildAt(0) instanceof HorizontalScrollView) {
                    HorizontalScrollView scroll = (HorizontalScrollView) ((LinearLayout) row).getChildAt(0);
                    ViewGroup.LayoutParams scrollParams = scroll.getLayoutParams();
                    scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    scroll.setLayoutParams(scrollParams);
                    if (scroll.getChildCount() == 1) {
                        View text = scroll.getChildAt(0);
                        ViewGroup.LayoutParams textParams = text.getLayoutParams();
                        textParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        text.setLayoutParams(textParams);
                        text.setMinimumHeight((int) (24 * d));
                        text.setPadding(text.getPaddingLeft(), (int) (3 * d), text.getPaddingRight(), (int) (3 * d));
                    }
                }
            }
        }
        if (sendButton != null && sendButton.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) sendButton.getLayoutParams();
            params.topMargin = (int) (10 * d);
            sendButton.setLayoutParams(params);
        }
    }
}
