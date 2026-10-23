package mod.hey.studios.build;

import static mod.SketchwareUtil.getDip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;
import com.sketchware.remod.R;

import mod.SketchwareUtil;
import mod.hey.studios.util.Helper;

public class BuildSettingsDialog {

    private final Context activity;
    private final BuildSettings settings;
    private View[] viewArr;

    public BuildSettingsDialog(Activity activity, String sc_id) {
        this(activity, new BuildSettings(sc_id));
    }

    /** With the settings handed in, so the content can be built without a project on disk. */
    public BuildSettingsDialog(Context context, BuildSettings settings) {
        activity = context;
        this.settings = settings;
    }

    public void show() {
        AlertDialog.Builder builder = new com.sketchware.ai.ui.ObAlertBuilder(activity);

        View inflate = createContent();
        TextView cancel = inflate.findViewById(R.id.text_cancel);
        TextView save = inflate.findViewById(R.id.text_save);

        builder.setView(inflate);

        AlertDialog buildSettingsDialog = builder.create();
        buildSettingsDialog.show();
        cancel.setOnClickListener(Helper.getDialogDismissListener(buildSettingsDialog));
        save.setOnClickListener(v -> {
            settings.setValues(viewArr);
            buildSettingsDialog.dismiss();
        });
    }

    /** The whole dialog content: title row, the options with their current values, the buttons. */
    public View createContent() {
        View inflate = LayoutInflater.from(activity).inflate(R.layout.project_config_layout, null);

        ImageView icon = inflate.findViewById(R.id.project_config_icon);
        TextView title = inflate.findViewById(R.id.project_config_title);
        LinearLayout contentView = inflate.findViewById(R.id.project_config_pref_layout);

        icon.setImageResource(R.drawable.ob_ic_settings);
        title.setText("Build Settings");
        title.setSelected(true);

        viewArr = new View[]{
                addInputPref(BuildSettings.SETTING_ANDROID_JAR_PATH, "", "Custom android.jar", EditorInfo.TYPE_CLASS_TEXT, contentView),
                addInputPref(BuildSettings.SETTING_CLASSPATH, "", "Classpath (separated by :)", EditorInfo.TYPE_CLASS_TEXT, contentView),
                // D8 first: it is what a project without a saved setting uses (ModernProjectDefaults).
                addSingleChoicePref(BuildSettings.SETTING_DEXER, new String[]{BuildSettings.SETTING_DEXER_D8, BuildSettings.SETTING_DEXER_DX},
                        com.sketchware.ai.project.ModernProjectDefaults.defaultDexer(), "Dexer", dexerCaption(), contentView),
                addSingleChoicePref(BuildSettings.SETTING_JAVA_VERSION, BuildSettingsDialogBridge.getAvailableJavaVersions(),
                        com.sketchware.ai.project.ModernProjectDefaults.defaultJavaVersion(), "Java version", javaCaption(), contentView),
                addTogglePref(BuildSettings.SETTING_NO_WARNINGS, true, "Hide warnings in error log", 12, contentView),
                addTogglePref(BuildSettings.SETTING_NO_HTTP_LEGACY, false, "Don't include http-legacy-28.dex", 12, contentView),
                addTogglePref(BuildSettings.SETTING_ENABLE_LOGCAT, true, "Enable debug logcat logs viewable in Logcat Reader. Not enabled in exported AABs/APKs.", 12, contentView)
        };

        // The fields are built with the activity's light theme: without the shared input theme
        // their floating labels are dark on the dark sheet.
        com.sketchware.ai.ui.ObsidianInputs.style(inflate);
        return inflate;
    }

    /** What the default really is on this Android version (D8 cannot run below Android 8). */
    private static String dexerCaption() {
        if (BuildSettings.SETTING_DEXER_D8.equals(com.sketchware.ai.project.ModernProjectDefaults.defaultDexer())) {
            return "D8 is the default. Dx is kept for old projects only: it cannot read Java 8+ code.";
        }
        return "Dx is the default on this Android version: D8 needs Android 8 or newer. Dx cannot read Java 8+ code.";
    }

    /**
     * Only what the build does with the choice (a.a.a.Dp#compileJavaCode): the value goes to the
     * ECJ compiler as its "-<version>" level, and 1.9 / 10 / 11 are compiled again as 1.8 when
     * the compiler cannot load that level's system library.
     */
    private static String javaCaption() {
        StringBuilder caption = new StringBuilder("Language level for the ECJ compiler. Default: ")
                .append(com.sketchware.ai.project.ModernProjectDefaults.defaultJavaVersion()).append('.');
        java.util.List<String> retried = new java.util.ArrayList<>();
        for (String version : BuildSettingsDialogBridge.getAvailableJavaVersions()) {
            if (com.sketchware.ai.project.ModernProjectDefaults.javaFallback(version) != null) retried.add(version);
        }
        if (!retried.isEmpty()) {
            caption.append(" If the phone cannot use ");
            for (int i = 0; i < retried.size(); i++) {
                if (i > 0) caption.append(i == retried.size() - 1 ? " or " : ", ");
                caption.append(retried.get(i));
            }
            caption.append(", the build compiles as ")
                    .append(com.sketchware.ai.project.ModernProjectDefaults.javaFallback(retried.get(0)))
                    .append(" and the log says so.");
        }
        return caption.toString();
    }

    private RadioGroup addSingleChoicePref(String key, String[] choices, String defaultValue, String title, String caption, LinearLayout addTo) {
        TextView textView = new TextView(activity);

        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setText(title);
        textView.setTextSize(12f);
        textView.setTextColor(0xFFFF9A4D);
        textView.setTypeface(null, Typeface.BOLD);
        textView.setPadding(
                0,
                (int) getDip(10),
                0,
                (int) getDip(4)
        );

        addTo.addView(textView);

        RadioGroup radioGroup = new RadioGroup(activity);
        radioGroup.setOrientation(LinearLayout.HORIZONTAL);
        radioGroup.setTag(key);
        radioGroup.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Options keep their natural width on one line ("1.7", "Dx (legacy)") and scroll
        // sideways if the dialog is narrow, instead of being squeezed into "1." / "7".
        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroller.setHorizontalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength((int) getDip(16));
        scroller.addView(radioGroup);
        addTo.addView(scroller);
        // With a very large font the row may still be wider than the dialog: start with the
        // selected option in view.
        scroller.post(() -> {
            View checked = radioGroup.findViewById(radioGroup.getCheckedRadioButtonId());
            if (checked != null && checked.getRight() > scroller.getWidth()) {
                scroller.scrollTo(checked.getRight() - scroller.getWidth(), 0);
            }
        });

        // The caption sits under the options and outside the RadioGroup, whose children must all
        // be radio buttons (ProjectSettings reads the checked one).
        if (caption != null && !caption.isEmpty()) {
            TextView note = new TextView(activity);
            note.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            note.setText(caption);
            note.setTextSize(12f);
            note.setTextColor(0xFFA1A1AA);
            note.setLineSpacing(0f, 1.15f);
            note.setPadding((int) getDip(4), (int) getDip(2), (int) getDip(4), (int) getDip(2));
            addTo.addView(note);
        }

        for (String choice : choices) {
            RadioButton radioButton = new RadioButton(activity);

            RadioGroup.LayoutParams layoutParams = new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.rightMargin = (int) getDip(4);
            radioButton.setLayoutParams(layoutParams);
            // Compact mark and spacing: the five Java versions fit on one line of the dialog
            // (the default, 11, used to be cut off at the edge); the row is still 40dp to touch.
            radioButton.setButtonDrawable(R.drawable.ob_radio);
            radioButton.setPadding((int) getDip(6), 0, (int) getDip(2), 0);
            radioButton.setMinHeight((int) getDip(40));
            radioButton.setMinimumHeight((int) getDip(40));
            radioButton.setGravity(android.view.Gravity.CENTER_VERTICAL);
            radioButton.setSingleLine(true);
            radioButton.setId(View.generateViewId());
            radioButton.setText(choice.equals(BuildSettings.SETTING_DEXER_DX) ? "Dx (legacy)" : choice);
            radioButton.setTag(choice);
            radioButton.setTextColor(0xFFF5F5F7);
            radioButton.setTextSize(14f);

            if (settings.getValue(key, defaultValue).equals(choice)) {
                radioButton.setChecked(true);
            }

            radioGroup.addView(radioButton);
            radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) return;

                if (key.equals(BuildSettings.SETTING_JAVA_VERSION)) {
                    BuildSettingsDialogBridge.handleJavaVersionChange(choice);
                } else if (key.equals(BuildSettings.SETTING_DEXER)) {
                    if (BuildSettings.SETTING_DEXER_DX.equals(choice) && android.os.Build.VERSION.SDK_INT >= 26) {
                        new com.sketchware.ai.ui.ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                                .setTitle("Use the deprecated Dx dexer?")
                                .setMessage("Dx cannot read Java 8+ bytecode, so lambdas, Java 8/11 code and most "
                                        + "modern libraries (AndroidX, Firebase, Kotlin) will fail to build. "
                                        + "D8 is recommended.")
                                .setPositiveButton("Keep D8", (d, w) -> {
                                    for (int i = 0; i < radioGroup.getChildCount(); i++) {
                                        RadioButton rb = (RadioButton) radioGroup.getChildAt(i);
                                        if (BuildSettings.SETTING_DEXER_D8.equals(rb.getTag())) rb.setChecked(true);
                                    }
                                })
                                .setNegativeButton("Use Dx anyway", null)
                                .setCancelable(false)
                                .show();
                    } else {
                        BuildSettingsDialogBridge.handleDexerChange(choice);
                    }
                }
            });
        }
        return radioGroup;
    }

    private CheckBox addTogglePref(String key, boolean defaultValue, String label, int leftMargin, LinearLayout addTo) {
        CheckBox checkBox = new CheckBox(activity);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(
                0,
                (int) getDip(leftMargin),
                0,
                0
        );
        checkBox.setLayoutParams(layoutParams);

        addTo.addView(checkBox);

        String value = settings.getValue(key, defaultValue ? "true" : "false");
        checkBox.setText(label);
        checkBox.setChecked(value.equals("true"));
        checkBox.setTextColor(0xFFF5F5F7);
        checkBox.setPadding(
                (int) getDip(4),
                (int) getDip(8),
                (int) getDip(8),
                (int) getDip(8)
        );
        checkBox.setTag(key);

        if (key.equals(BuildSettings.SETTING_NO_HTTP_LEGACY)) {
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    SketchwareUtil.toast("Note that this option may cause issues if RequestNetwork component is used");
                }
            });
        }

        return checkBox;
    }

    private EditText addInputPref(String key, String defaultValue, String hint, int inputType, LinearLayout addTo) {
        TextInputLayout textInputLayout = new TextInputLayout(activity);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(
                0,
                (int) getDip(12),
                0,
                0
        );

        textInputLayout.setLayoutParams(layoutParams);

        addTo.addView(textInputLayout);

        EditText editText = new EditText(activity);
        editText.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        editText.setPadding(
                (int) getDip(4),
                (int) getDip(8),
                (int) getDip(8),
                (int) getDip(8)
        );
        editText.setTextSize(16f);
        editText.setTextColor(0xFFF5F5F7);
        editText.setHint(hint);
        editText.setHintTextColor(0xFFA1A1AA);
        editText.setText(settings.getValue(key, defaultValue));
        editText.setTag(key);
        editText.setInputType(inputType);

        textInputLayout.addView(editText);

        return editText;
    }
}
