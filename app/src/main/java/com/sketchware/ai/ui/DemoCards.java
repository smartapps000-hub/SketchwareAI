package com.sketchware.ai.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sketchware.ai.github.GitHubViews;
import com.sketchware.remod.R;

import java.util.HashMap;

import a.a.a.yB;

/**
 * v2.0.5 (owner's 16 Nov directive "a dedicated Demo category: the latest demo first, older ones in one horizontal
 * row"): one compact glass card per demo project in the projects list's Demo row ({@code ob_demo_row} in
 * {@code myprojects.xml}, filled by {@code GC}): the app name, "Latest demo" (accent) on the first card, "Older demo" on
 * the others, "#id · version (code)". A tap opens the project, a long press asks to delete it. Plain view code, so
 * layoutlib draws it (V205Test).
 */
public final class DemoCards {

    private DemoCards() {
    }

    public static View card(Context context, HashMap<String, Object> project, int index, Runnable open, Runnable longPress) {
        GitHubViews v = new GitHubViews(context);
        String scId = yB.c(project, "sc_id");
        String name = yB.c(project, "my_app_name");
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_pill16_state);
        card.setPadding(v.dp(14), v.dp(10), v.dp(14), v.dp(10));
        card.setMinimumWidth(v.dp(180));
        TextView title = new TextView(context, null, 0, R.style.Ob_Text_Label);
        title.setText(name.isEmpty() ? yB.c(project, "my_ws_name") : name);
        title.setTypeface(v.bold());
        GitHubViews.oneLine(title);
        card.addView(title);
        TextView badge = new TextView(context, null, 0, R.style.Ob_Text_Caption);
        badge.setText(index == 0 ? "Latest demo" : "Older demo");
        badge.setTypeface(v.bold());
        badge.setTextColor(v.color(index == 0 ? R.color.ob_accent : R.color.ob_text_2));
        card.addView(badge);
        TextView detail = new TextView(context, null, 0, R.style.Ob_Text_Caption);
        detail.setText("#" + scId + " · " + yB.c(project, "sc_ver_name") + " (" + yB.c(project, "sc_ver_code") + ")");
        GitHubViews.oneLine(detail);
        card.addView(detail);
        card.setClickable(true);
        card.setOnClickListener(x -> open.run());
        card.setOnLongClickListener(x -> {
            longPress.run();
            return true;
        });
        card.setContentDescription(title.getText() + ", " + badge.getText() + ", project " + scId);
        return card;
    }

    /** The card's layout parameters in the row: wrap content, 8dp between cards. */
    public static LinearLayout.LayoutParams params(Context context, int index) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        if (index > 0) p.leftMargin = new GitHubViews(context).dp(8);
        return p;
    }
}
