package a.a.a;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.sketchware.remod.R;

/**
 * A Sketchware-styled dialog.
 */
public class aB extends Dialog {

    private int dialogImageResId = -1;
    private String dialogTitleText = "";
    private String dialogMessageText = "";
    private View dialogCustomView;
    private View dialogButtonsContainer;
    private String dialogDefaultText = "Default";
    private View.OnClickListener dialogDefaultListener = null;
    private String dialogNoText = "No";
    private View.OnClickListener dialogNoListener = null;
    private String dialogYesText = "Yes";
    private View.OnClickListener dialogYesListener = null;

    public aB(Activity activity) {
        super(activity);
    }

    /**
     * Set the dialog's image's resource ID
     */
    public void a(int resId) {
        dialogImageResId = resId;
    }

    /**
     * Set the dialog's custom view
     */
    public void a(View customView) {
        dialogCustomView = customView;
    }

    /**
     * Set the dialog's message
     */
    public void a(String message) {
        dialogMessageText = message;
    }

    /**
     * Set the dialog's "No" button text and listener
     */
    public void a(String noText, View.OnClickListener noListener) {
        dialogNoText = noText;
        dialogNoListener = noListener;
    }

    /**
     * Set the dialog's title
     */
    public void b(String title) {
        dialogTitleText = title;
    }

    /**
     * Set the dialog's "Yes" button text and listener
     */
    public void b(String yesText, View.OnClickListener yesListener) {
        dialogYesText = yesText;
        dialogYesListener = yesListener;
    }

    public void configureDefaultButton(String defaultText, View.OnClickListener defaultListener) {
        dialogDefaultText = defaultText;
        dialogDefaultListener = defaultListener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Obsidian glass sheet: the layout draws the sheet, the window stays clear, and on
        // Android 12+ phones that support it the app behind is frosted (real 22dp blur).
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        getWindow().setDimAmount(0.55f);

        {
            LayoutParams attributes = getWindow().getAttributes();
            attributes.width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                getWindow().addFlags(LayoutParams.FLAG_BLUR_BEHIND);
                try {
                    // WindowManager.LayoutParams#setBlurBehindRadius (API 31; compileSdk is 30).
                    LayoutParams.class.getMethod("setBlurBehindRadius", int.class).invoke(attributes,
                            (int) (22 * getContext().getResources().getDisplayMetrics().density));
                } catch (ReflectiveOperationException ignored) {
                }
            }
            getWindow().setAttributes(attributes);
        }
        setContentView(R.layout.dialog);

        ImageView dialogImage = findViewById(R.id.dialog_img);
        TextView dialogTitle = findViewById(R.id.dialog_title);
        TextView dialogMessage = findViewById(R.id.dialog_msg);
        FrameLayout dialogCustomViewContainer = findViewById(R.id.custom_view);

        dialogButtonsContainer = findViewById(R.id.layout_button);
        TextView dialogDefault = findViewById(R.id.common_dialog_default_button);
        dialogDefault.setText(dialogDefaultText);
        dialogDefault.setOnClickListener(dialogDefaultListener);
        TextView dialogNo = findViewById(R.id.dialog_btn_no);
        dialogNo.setText(dialogNoText);
        dialogNo.setOnClickListener(dialogNoListener);
        TextView dialogYes = findViewById(R.id.dialog_btn_yes);
        dialogYes.setText(dialogYesText);
        dialogYes.setOnClickListener(dialogYesListener);

        if (dialogTitleText.isEmpty()) {
            dialogTitle.setVisibility(View.GONE);
        } else {
            dialogTitle.setVisibility(View.VISIBLE);
            dialogTitle.setText(dialogTitleText);
        }

        if (dialogMessageText.isEmpty()) {
            dialogMessage.setVisibility(View.GONE);
        } else {
            dialogMessage.setVisibility(View.VISIBLE);
            dialogMessage.setText(dialogMessageText);
        }

        if (dialogDefaultListener == null) {
            dialogDefault.setVisibility(View.GONE);
        }

        if (dialogNoListener == null) {
            dialogNo.setVisibility(View.GONE);
        }

        if (dialogYesListener == null) {
            dialogYes.setVisibility(View.GONE);
        }

        if (dialogImageResId == -1) {
            dialogImage.setVisibility(View.GONE);
        } else {
            dialogImage.setImageResource(dialogImageResId);
        }

        if (dialogCustomView != null) {
            dialogCustomViewContainer.setVisibility(View.VISIBLE);
            if (dialogCustomView.getParent() instanceof ViewGroup) {
                ((ViewGroup) dialogCustomView.getParent()).removeView(dialogCustomView);
            }
            // Classic content (dark text on white) is re-coloured for the dark glass sheet.
            com.sketchware.ai.ui.ObsidianAdapter.adaptLive(dialogCustomView);
            dialogCustomViewContainer.addView(dialogCustomView);
        } else {
            dialogCustomViewContainer.setVisibility(View.GONE);
        }
        if (dialogMessageText.isEmpty() && dialogTitleText.isEmpty() && dialogImageResId == -1) {
            ((ViewGroup.MarginLayoutParams) dialogCustomViewContainer.getLayoutParams()).topMargin = 0;
        }

        // Pill row: the CTA lives in a glow slot, and only visible pills keep the 10dp gaps.
        View yesSlot = findViewById(R.id.ob_yes_slot);
        yesSlot.setVisibility(dialogYes.getVisibility());
        View[] pills = {dialogDefault, dialogNo, yesSlot};
        View lastVisible = null;
        for (View pill : pills) {
            if (pill.getVisibility() == View.VISIBLE) lastVisible = pill;
        }
        for (View pill : pills) {
            if (pill != yesSlot) {
                ((ViewGroup.MarginLayoutParams) pill.getLayoutParams()).rightMargin = pill == lastVisible ? 0
                        : (int) (10 * getContext().getResources().getDisplayMetrics().density);
            }
        }
    }

    @Override
    public void show() {
        super.show();
        if (dialogDefaultListener == null && dialogYesListener == null && dialogNoListener == null) {
            if (dialogButtonsContainer != null) {
                dialogButtonsContainer.setVisibility(View.GONE);
            }
        }
    }
}
