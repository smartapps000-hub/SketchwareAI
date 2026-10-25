package mod.hey.studios.project;

import static mod.SketchwareUtil.getDip;

import android.app.Activity;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;
import com.sketchware.remod.R;

import a.a.a.aB;
import a.a.a.xB;
import mod.hey.studios.util.Helper;

public class ProjectSettingsDialog {

    private final Activity activity;
    private final ProjectSettings settings;

    public ProjectSettingsDialog(Activity activity, String sc_id) {
        this.activity = activity;
        this.settings = new ProjectSettings(sc_id);
    }

    public void show() {
        aB dialog = new aB(activity);
        dialog.a(R.drawable.ob_ic_sliders);
        dialog.b("Project Configuration");

        View content = activity.getLayoutInflater().inflate(R.layout.ob_project_config, null);

        EditText minimumSdkVersion = content.findViewById(R.id.ob_cfg_min);
        bindInput(minimumSdkVersion, ProjectSettings.SETTING_MINIMUM_SDK_VERSION, "21");
        EditText targetSdkVersion = content.findViewById(R.id.ob_cfg_target);
        bindInput(targetSdkVersion, ProjectSettings.SETTING_TARGET_SDK_VERSION, "28");
        EditText applicationClassName = content.findViewById(R.id.ob_cfg_app_class);
        bindInput(applicationClassName, ProjectSettings.SETTING_APPLICATION_CLASS, ".SketchApplication");

        CheckBox edgeToEdge = bindToggle(content.findViewById(R.id.ob_cfg_row_edge),
                com.sketchware.ai.project.ModernProjectDefaults.SETTING_EDGE_TO_EDGE,
                "Edge-to-edge layout", "Android 15+ · off keeps content below the status bar");
        CheckBox removeOldMethods = bindToggle(content.findViewById(R.id.ob_cfg_row_old),
                ProjectSettings.SETTING_DISABLE_OLD_METHODS,
                "Remove old helper methods", "showMessage, getDip and similar");
        CheckBox useNewMaterialComponentsAppTheme = bindToggle(content.findViewById(R.id.ob_cfg_row_material),
                ProjectSettings.SETTING_ENABLE_BRIDGELESS_THEMES,
                "MaterialComponents AppTheme", "Buttons become MaterialButton");
        CheckBox material3AppTheme = bindToggle(content.findViewById(R.id.ob_cfg_row_m3),
                com.sketchware.ai.project.ModernProjectDefaults.SETTING_MATERIAL3,
                "Material 3 theme", "AppCompat projects · Material components and dialogs in Material 3");

        CheckBox autoHeal = bindToggle(content.findViewById(R.id.ob_cfg_row_heal),
                com.sketchware.ai.project.ModernProjectDefaults.SETTING_AUTO_HEAL,
                "Auto-heal builds", "Fixes unreachable code and missing imports, then rebuilds");
        autoHeal.setChecked(!settings.getValue(com.sketchware.ai.project.ModernProjectDefaults.SETTING_AUTO_HEAL, "true").equals("false"));

        TextView targetHint = content.findViewById(R.id.ob_cfg_target_hint);
        Runnable updateHint = () -> {
            int target;
            try {
                target = Integer.parseInt(targetSdkVersion.getText().toString().trim());
            } catch (NumberFormatException e) {
                targetHint.setText("Enter an API level, e.g. 37 for Android 17");
                return;
            }
            String name = target >= 33 ? "Android " + (target - 20)
                    : target == 32 ? "Android 12L" : target == 31 ? "Android 12" : target == 30 ? "Android 11"
                    : target == 29 ? "Android 10" : target == 28 ? "Android 9" : "API " + target;
            targetHint.setText(name + " (API " + target + ")"
                    + (target == com.sketchware.ai.project.ModernProjectDefaults.NEW_PROJECT_TARGET_SDK
                    ? " · new-project default" : ""));
        };
        updateHint.run();
        targetSdkVersion.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateHint.run();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        // v1.8.9: older / imported projects (below Android 17, or without Material 3) get the conversion card
        int storedTarget = com.sketchware.ai.project.ModernProjectDefaults.targetSdk(settings);
        boolean m3 = com.sketchware.ai.project.ModernProjectDefaults.material3(settings);
        boolean compat = com.sketchware.ai.project.ModernProjectDefaults.appCompatOn(settings.sc_id);
        final boolean[] convert = {false};
        if (storedTarget < com.sketchware.ai.project.ModernProjectDefaults.NEW_PROJECT_TARGET_SDK || !m3 || !compat) {
            View upgrade = content.findViewById(R.id.ob_cfg_upgrade);
            upgrade.setVisibility(View.VISIBLE);
            ((TextView) content.findViewById(R.id.ob_cfg_upgrade_title)).setText("Older project (target " + storedTarget
                    + (m3 && compat ? "" : ", no Material 3") + ")");
            TextView button = content.findViewById(R.id.ob_cfg_upgrade_button);
            button.setText("Convert to Material 3 & Modern Stack");
            button.setOnClickListener(v -> new com.sketchware.ai.ui.ObAlertBuilder(activity)
                    .setTitle("Convert to Material 3?")
                    .setMessage("When you tap Save:\n"
                            + "\u2022 Target SDK " + com.sketchware.ai.project.ModernProjectDefaults.NEW_PROJECT_TARGET_SDK
                            + " (Android 17), min SDK " + com.sketchware.ai.project.ModernProjectDefaults.NEW_PROJECT_MIN_SDK + "\n"
                            + "\u2022 AppCompat and Design switched on" + (compat ? " (already on)" : "") + "\n"
                            + "\u2022 Material 3 theme: buttons, fields, switches, cards and the toolbar as Material 3 widgets\n"
                            + "\u2022 D8 dexer and Java 11\n\n"
                            + "Your blocks, layouts and colours stay as they are; the theme stays light (Dark theme can be "
                            + "switched on in the Library Manager). Target 30+ changes Android's storage and permission rules "
                            + "for the app: test file access after converting. A backup (.swb) before converting is a good idea.")
                    .setPositiveButton("Convert", (d, w) -> {
                        minimumSdkVersion.setText(String.valueOf(com.sketchware.ai.project.ModernProjectDefaults.NEW_PROJECT_MIN_SDK));
                        targetSdkVersion.setText(String.valueOf(com.sketchware.ai.project.ModernProjectDefaults.NEW_PROJECT_TARGET_SDK));
                        material3AppTheme.setChecked(true);
                        convert[0] = true;
                        mod.SketchwareUtil.toast("Tap Save to convert the project.", android.widget.Toast.LENGTH_LONG);
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        }

        dialog.a(content);

        final View[] preferences = {
                minimumSdkVersion,
                targetSdkVersion,
                applicationClassName,
                removeOldMethods,
                edgeToEdge,
                autoHeal,
                useNewMaterialComponentsAppTheme,
                material3AppTheme
        };

        dialog.a(Helper.getResString(R.string.common_word_cancel), Helper.getDialogDismissListener(dialog));
        dialog.b(Helper.getResString(R.string.common_word_save), v -> {
            settings.setValues(preferences);
            if (convert[0] && !com.sketchware.ai.project.ModernProjectDefaults.convertToModern(settings.sc_id)) {
                mod.SketchwareUtil.toast("Theme and SDK saved; AppCompat couldn't be switched on: turn it on in the Library "
                        + "Manager.", android.widget.Toast.LENGTH_LONG);
            }
            // the design editor draws Material 3 widgets for Material 3 projects
            com.sketchware.ai.editor.DesignTheme.load(settings.sc_id);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void bindInput(EditText input, String key, String defaultValue) {
        input.setText(settings.getValue(key, defaultValue));
        input.setTag(key);
    }

    private CheckBox bindToggle(View row, String key, String title, String subtitle) {
        ((TextView) row.findViewById(R.id.ob_row_title)).setText(title);
        ((TextView) row.findViewById(R.id.ob_row_subtitle)).setText(subtitle);
        CheckBox check = row.findViewById(R.id.ob_row_check);
        check.setChecked(settings.getValue(key, "false").equals("true"));
        check.setTag(key);
        row.setOnClickListener(v -> check.toggle());
        return check;
    }

    private CheckBox addTogglePref(String key, boolean defaultState, String hint, LinearLayout layout) {
        CheckBox i = new CheckBox(activity);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        l.setMargins(
                0,
                (int) getDip(10),
                0,
                0
        );
        i.setLayoutParams(l);
        layout.addView(i);

        String v = settings.getValue(key, defaultState ? "true" : "false");

        i.setText(hint);
        i.setChecked(v.equals("true"));
        i.setTextColor(activity.getResources().getColor(R.color.sw_text_primary));
        i.setPadding(
                (int) getDip(4),
                (int) getDip(8),
                (int) getDip(8),
                (int) getDip(8)
        );

        i.setTag(key);

        return i;
    }

    private EditText addInputPref(String key, String defaultValue, String hint, int inputType, LinearLayout layout) {
        TextInputLayout i = new TextInputLayout(activity);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        l.setMargins(
                0,
                (int) getDip(10),
                0,
                0
        );
        i.setLayoutParams(l);
        layout.addView(i);

        EditText e = new EditText(activity);
        e.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        e.setPadding(
                (int) getDip(4),
                (int) getDip(8),
                (int) getDip(8),
                (int) getDip(8)
        );
        e.setTextSize(16);
        e.setTextColor(activity.getResources().getColor(R.color.sw_text_primary));
        e.setHint(hint);
        e.setHintTextColor(activity.getResources().getColor(R.color.sw_text_secondary));
        e.setText(settings.getValue(key, defaultValue));
        e.setTag(key);
        e.setInputType(inputType);
        i.addView(e);

        return e;
    }
}
