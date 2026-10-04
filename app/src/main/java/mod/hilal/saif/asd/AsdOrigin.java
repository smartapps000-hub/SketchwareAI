package mod.hilal.saif.asd;

import static mod.SketchwareUtil.getDip;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.besome.sketch.editor.LogicEditorActivity;
import com.sketchware.remod.R;

import a.a.a.Ss;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.hilal.saif.asd.old.AsdOldDialog;

public class AsdOrigin extends Dialog {

    public Activity activity;
    public LogicEditorActivity logicEditorActivity;
    public android.view.ViewGroup b; // dialog.xml root (FrameLayout since v1.6)
    public View c;
    public ImageView d;
    public TextView dialog_title;
    public TextView dialog_msg;
    public FrameLayout custom_view;
    public TextView dialog_btn_yes;
    public TextView dialog_btn_no;
    public String dialog_title_str = "";
    public String dialog_msg_str = "";
    public String dialog_btn_yes_str = "Yes";
    public String dialog_btn_no_str = "No";
    public int dialog_img_image_id = -1;
    public int code_editor_theme = 0;
    public View.OnClickListener dialog_btn_yes_listener = null;
    public View.OnClickListener dialog_btn_no_listener = null;
    public View layout_button;

    public Ss ss;
    public boolean boo;
    public EditText editText;

    public TextView title;
    public Space space;

    public AsdOrigin(Activity activity) {
        super(activity);
        this.activity = activity;
    }

    public void a(String str) {
        dialog_msg_str = str;
    }

    public void b(String str) {
        dialog_title_str = str;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Sketchware AI: the dark glass sheet of R.layout.dialog needs aB's clear window.
        com.sketchware.ai.ui.ObsidianDialogs.window(this);
        // The sheet is laid out above the keyboard instead of being pushed half off the screen;
        // the value field is capped at 6 lines and scrolls, so the three buttons stay visible.
        if (getWindow() != null) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        setContentView(R.layout.dialog);

        space = new Space(getContext());
        space.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                0,
                1.0f
        ));

        LinearLayout base = findViewById(R.id.layout_button);

        title = com.sketchware.ai.ui.ObsidianDialogs.leftButton(this, "Code Editor", null);
        title.setOnClickListener(v -> {
            if (ConfigActivity.isLegacyCeEnabled()) {
                AsdOldDialog asdOldDialog = new AsdOldDialog(activity);
                asdOldDialog.setCon(editText.getText().toString());
                asdOldDialog.show();
                asdOldDialog.saveLis(logicEditorActivity, boo, ss, asdOldDialog);
                asdOldDialog.cancelLis(logicEditorActivity, asdOldDialog);
            } else {
                AsdDialog asdDialog = new AsdDialog(activity);
                asdDialog.setCon(editText.getText().toString());
                asdDialog.show();
                asdDialog.saveLis(logicEditorActivity, boo, ss, asdDialog);
                asdDialog.cancelLis(asdDialog);
            }
            dismiss();
        });

        b = findViewById(R.id.sdialog_root);
        d = findViewById(R.id.dialog_img);
        dialog_title = findViewById(R.id.dialog_title);
        dialog_msg = findViewById(R.id.dialog_msg);
        custom_view = findViewById(R.id.custom_view);
        layout_button = base;
        dialog_btn_yes = findViewById(R.id.dialog_btn_yes);
        dialog_btn_yes.setText(dialog_btn_yes_str);
        dialog_btn_yes.setOnClickListener(dialog_btn_yes_listener);
        dialog_btn_no = findViewById(R.id.dialog_btn_no);
        dialog_btn_no.setText(dialog_btn_no_str);
        dialog_btn_no.setOnClickListener(dialog_btn_no_listener);
        if (dialog_title_str.isEmpty()) {
            dialog_title.setVisibility(View.GONE);
        } else {
            dialog_title.setVisibility(View.VISIBLE);
            dialog_title.setText(dialog_title_str);
        }
        if (dialog_msg_str.isEmpty()) {
            dialog_msg.setVisibility(View.GONE);
        } else {
            dialog_msg.setVisibility(View.VISIBLE);
            dialog_msg.setText(dialog_msg_str);
        }
        if (dialog_btn_no_listener == null) {
            dialog_btn_no.setVisibility(View.GONE);
        }
        if (dialog_btn_yes_listener == null) {
            dialog_btn_yes.setVisibility(View.GONE);
        }
        if (dialog_img_image_id == -1) {
            d.setVisibility(View.GONE);
        } else {
            d.setImageResource(dialog_img_image_id);
        }
        if (c != null) {
            custom_view.setVisibility(View.VISIBLE);
            custom_view.addView(c);
        } else {
            custom_view.setVisibility(View.GONE);
        }
        com.sketchware.ai.ui.ObsidianDialogs.finish(this, c);
    }

    @Override
    public void show() {
        super.show();
        if (dialog_btn_yes_listener == null && dialog_btn_no_listener == null && layout_button != null) {
            layout_button.setVisibility(View.GONE);
        }
    }

    public void a(int i) {
        dialog_img_image_id = i;
    }

    public void b(String str, View.OnClickListener onClickListener) {
        dialog_btn_yes_str = str;
        dialog_btn_yes_listener = onClickListener;
    }

    public void a(View view) {
        c = view;
    }

    public void a(String str, View.OnClickListener onClickListener) {
        dialog_btn_no_str = str;
        dialog_btn_no_listener = onClickListener;
    }

    public void carry(LogicEditorActivity logicEditorActivity, Ss ss, boolean b, EditText editText) {
        this.ss = ss;
        boo = b;
        this.editText = editText;
        this.logicEditorActivity = logicEditorActivity;
    }
}
