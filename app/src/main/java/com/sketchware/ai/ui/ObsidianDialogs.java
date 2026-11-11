package com.sketchware.ai.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.sketchware.remod.R;

/** Finishing touches for framework AlertDialogs shown as Obsidian glass sheets. */
public final class ObsidianDialogs {

    private ObsidianDialogs() {
    }

    public static void style(AlertDialog dialog) {
        try {
            if (dialog.getWindow() != null) {
                ObsidianAdapter.adaptLive(dialog.getWindow().getDecorView());
                ObsidianInputs.style(dialog.getWindow().getDecorView());
            }
            float density = dialog.getContext().getResources().getDisplayMetrics().density;
            styleButton(dialog.getButton(DialogInterface.BUTTON_NEUTRAL), false, density);
            styleButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), false, density);
            styleButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), true, density);
        } catch (RuntimeException ignored) {
            // Styling is cosmetic: never let it break a dialog.
        }
    }

    /**
     * For dialog classes that inflate R.layout.dialog themselves (the block menus' "select"
     * dialogs, the source-code input dialog, SketchDialog): the same window as a.a.a.aB. Call
     * before setContentView.
     */
    public static void window(android.app.Dialog dialog) {
        try {
            android.view.Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            window.setDimAmount(0.55f);
            android.view.WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = ViewGroup.LayoutParams.MATCH_PARENT;
            window.setAttributes(attributes);
        } catch (RuntimeException ignored) {
            // The dialog still works with its default window.
        }
    }

    /**
     * The left pill of R.layout.dialog as a named action ("Code Editor"). Those dialog classes
     * used to add a bare text view in front of the row, which pushed the other buttons together
     * until their names no longer fitted.
     *
     * @return the pill
     */
    public static android.widget.TextView leftButton(android.app.Dialog dialog, String text, View.OnClickListener listener) {
        android.widget.TextView pill = dialog.findViewById(R.id.common_dialog_default_button);
        pill.setText(text);
        pill.setOnClickListener(listener);
        pill.setVisibility(View.VISIBLE);
        return pill;
    }

    /**
     * Call at the end of such a dialog's onCreate, after its content view is filled: re-colours
     * the custom content for the dark sheet (option names, radio buttons, input fields), hides
     * the pills that have no name, and keeps the gaps of the pill row right.
     */
    public static void finish(android.app.Dialog dialog, View customView) {
        try {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (customView != null) {
                ObsidianAdapter.adaptLive(customView);
                ObsidianInputs.style(customView);
            }
            android.widget.TextView left = dialog.findViewById(R.id.common_dialog_default_button);
            View no = dialog.findViewById(R.id.dialog_btn_no);
            View yes = dialog.findViewById(R.id.dialog_btn_yes);
            View yesSlot = dialog.findViewById(R.id.ob_yes_slot);
            if (left.getText() == null || left.getText().toString().trim().isEmpty()) left.setVisibility(View.GONE);
            yesSlot.setVisibility(yes.getVisibility());
            View[] pills = {left, no, yesSlot};
            View lastVisible = null;
            for (View pill : pills) {
                if (pill.getVisibility() == View.VISIBLE) lastVisible = pill;
            }
            float density = dialog.getContext().getResources().getDisplayMetrics().density;
            for (View pill : pills) {
                if (pill != yesSlot) {
                    ((ViewGroup.MarginLayoutParams) pill.getLayoutParams()).rightMargin = pill == lastVisible ? 0 : (int) (10 * density);
                }
            }
        } catch (RuntimeException ignored) {
            // Cosmetic only.
        }
    }

    private static void styleButton(Button button, boolean primary, float density) {
        if (button == null || button.getVisibility() != View.VISIBLE) return;
        button.setBackgroundResource(primary ? R.drawable.ob_safe_cta_state : R.drawable.ob_safe_pill_state);
        button.setTextColor(primary ? 0xFFFFFFFF : 0xFFF5F5F7);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight((int) (44 * density));
        button.setMinimumHeight((int) (44 * density));
        int h = (int) (16 * density);
        button.setPadding(h, 0, h, 0);
        ViewGroup.LayoutParams lp = button.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).leftMargin = (int) (8 * density);
            button.setLayoutParams(lp);
        }
    }
}
