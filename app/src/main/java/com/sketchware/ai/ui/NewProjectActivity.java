package com.sketchware.ai.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;
import com.sketchware.ai.project.M3Palette;
import com.sketchware.ai.project.NewProjectSetup;
import com.sketchware.ai.project.ProjectPresets;
import com.sketchware.ai.project.ThemePresets;
import com.sketchware.remod.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import a.a.a.GB;
import a.a.a.HB;
import a.a.a.LB;
import a.a.a.UB;
import a.a.a.VB;
import a.a.a.Zx;
import a.a.a.iB;
import a.a.a.lC;
import a.a.a.mB;
import a.a.a.oB;
import a.a.a.wB;
import a.a.a.wq;
import mod.SketchwareUtil;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.build.BuildSettingsDialogBridge;

/**
 * New Project screen (v1.8.7): full screen instead of the project setting dialog, in cards: project names, icon and
 * version; template; build engine; theme colours with presets, a random set and a live preview, Material 3 options;
 * minimum / target SDK and Java level. Create hands everything to {@link NewProjectSetup#create}, the same code the
 * Android-runtime harness runs, and returns {@code sc_id} + {@code is_new} like MyProjectSettingActivity did, so the
 * projects list opens the new project in the design editor.
 * <p>
 * Project settings of an existing project stay in MyProjectSettingActivity.
 */
public class NewProjectActivity extends BaseAppCompatActivity {

    private static final int REQUEST_CODE_PICK_ICON = 207;

    private static final int[] MIN_SDKS = {21, 23, 24, 26, 28, 29, 30, 31, 33, 34};
    private static final int[] TARGET_SDKS = {34, 35, 36, 37};
    /** Java levels of the screen: those the IDE's compiler offers (ECJ 3.26: 7 to 11; minApi21's ECJ 3.13: 7, 8). */
    private static String[] javaLevels() {
        java.util.List<String> levels = new java.util.ArrayList<>();
        for (String level : BuildSettingsDialogBridge.getAvailableJavaVersions()) {
            if (!BuildSettings.SETTING_JAVA_VERSION_1_9.equals(level) && !BuildSettings.SETTING_JAVA_VERSION_10.equals(level)) {
                levels.add(level);
            }
        }
        return levels.toArray(new String[0]);
    }

    private static boolean contains(String[] values, String value) {
        for (String v : values) if (v.equals(value)) return true;
        return false;
    }

    private final NewProjectSetup.Choices choices = new NewProjectSetup.Choices();
    private String scId;
    private boolean creating;

    private EditText appName;
    private EditText packageName;
    private EditText projectName;
    private EditText versionCode;
    private EditText versionName;
    private ImageView icon;
    private LB appNameValidator;
    private UB packageValidator;
    private VB projectNameValidator;

    private LinearLayout templates;
    private LinearLayout palettes;
    private LinearLayout colorViews;
    private ThemePreviewView preview;
    private TextView previewCaption;
    private TextView engineModern;
    private TextView engineLegacy;
    private TextView engineCaption;
    private View material3Row;
    private View darkRow;
    private View dynamicRow;
    private TextView minSdkField;
    private TextView targetSdkField;
    private TextView javaField;
    private TextView javaCaption;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_new_project);
        ObsidianWindow.apply(this);
        if (!j()) {
            finish();
            return;
        }

        scId = lC.b();
        String defaultName = lC.c();
        ((TextView) findViewById(R.id.np_subtitle)).setText("Project #" + scId);
        findViewById(R.id.np_back).setOnClickListener(v -> finish());
        findViewById(R.id.np_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.np_create).setOnClickListener(v -> create());

        appName = findViewById(R.id.np_app_name);
        packageName = findViewById(R.id.np_package);
        projectName = findViewById(R.id.np_project);
        versionCode = findViewById(R.id.np_version_code);
        versionName = findViewById(R.id.np_version_name);
        appNameValidator = new LB(getApplicationContext(), (TextInputLayout) findViewById(R.id.np_ti_app_name));
        packageValidator = new UB(getApplicationContext(), (TextInputLayout) findViewById(R.id.np_ti_package));
        projectNameValidator = new VB(getApplicationContext(), (TextInputLayout) findViewById(R.id.np_ti_project));
        packageName.setPrivateImeOptions("defaultInputmode=english;");
        projectName.setPrivateImeOptions("defaultInputmode=english;");
        packageName.setText("com.my." + defaultName.toLowerCase());
        projectName.setText(defaultName);
        versionCode.setText("1");
        versionName.setText("1.0");
        appName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshPreview();
            }
        });

        icon = findViewById(R.id.np_icon);
        View iconTile = findViewById(R.id.np_icon_tile);
        iconTile.setClipToOutline(true);
        iconTile.setOnClickListener(v -> pickIcon());

        templates = findViewById(R.id.np_templates);
        buildTemplates();

        engineModern = findViewById(R.id.np_engine_modern);
        engineLegacy = findViewById(R.id.np_engine_legacy);
        engineCaption = findViewById(R.id.np_engine_caption);
        // One engine per phone, not a choice: Dp.isD8Enabled() uses D8 on Android 8.0+ even for a project that says
        // Dx, and D8 can't run below 8.0 (the minApi21 build of the IDE), where Dx is the only dexer.
        if (Build.VERSION.SDK_INT >= 26) {
            choices.dexer = BuildSettings.SETTING_DEXER_D8;
        } else {
            choices.dexer = BuildSettings.SETTING_DEXER_DX;
            choices.javaVersion = BuildSettings.SETTING_JAVA_VERSION_1_7;
        }
        String[] available = BuildSettingsDialogBridge.getAvailableJavaVersions();
        if (!contains(available, choices.javaVersion)) choices.javaVersion = available[available.length - 1];
        engineLegacy.setVisibility(View.GONE);

        palettes = findViewById(R.id.np_palettes);
        colorViews = findViewById(R.id.np_colors);
        preview = findViewById(R.id.np_preview);
        previewCaption = findViewById(R.id.np_preview_caption);
        buildPalettes();
        buildColorViews();
        findViewById(R.id.np_random).setOnClickListener(v -> {
            choices.colors = ThemePresets.random(new Random());
            refreshColors();
        });

        material3Row = toggle(R.id.np_material3, "Material 3 theme",
                "Material 3 widgets and colour roles made from the project colours. Off: the MaterialComponents theme");
        darkRow = toggle(R.id.np_dark, "Dark theme",
                "The app follows the phone's dark mode with Material 3 dark colours");
        dynamicRow = toggle(R.id.np_dynamic, "Dynamic colour",
                "Android 12 and newer use the wallpaper's colours instead of the project colours");
        material3Row.setOnClickListener(v -> {
            choices.material3 = !choices.material3;
            refreshToggles();
        });
        darkRow.setOnClickListener(v -> {
            choices.darkTheme = !choices.darkTheme;
            refreshToggles();
        });
        dynamicRow.setOnClickListener(v -> {
            choices.dynamicColor = !choices.dynamicColor;
            refreshToggles();
        });

        minSdkField = findViewById(R.id.np_min_sdk);
        targetSdkField = findViewById(R.id.np_target_sdk);
        javaField = findViewById(R.id.np_java);
        javaCaption = findViewById(R.id.np_java_caption);
        minSdkField.setOnClickListener(v -> chooseSdk(true));
        targetSdkField.setOnClickListener(v -> chooseSdk(false));
        javaField.setOnClickListener(v -> chooseJava());

        ObsidianInputs.style(findViewById(android.R.id.content));
        refreshEngine();
        refreshColors();
        refreshToggles();
        refreshSdk();
    }

    @Override
    public void onStart() {
        super.onStart();
        // as MyProjectSettingActivity: the project's folders and the icon file the icon picker writes to
        oB folders = new oB();
        folders.f(wq.e() + File.separator + scId);
        folders.f(wq.g() + File.separator + scId);
        folders.f(wq.t() + File.separator + scId);
        folders.f(wq.d() + File.separator + scId);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_PICK_ICON || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            String filename = HB.a(getApplicationContext(), data.getData());
            Bitmap bitmap = iB.a(filename, 96, 96);
            int orientation = new ExifInterface(filename).getAttributeInt("Orientation", -1);
            Bitmap rotated = iB.a(bitmap, orientation != 3 ? orientation != 6 ? orientation != 8 ? 0 : 270 : 90 : 180);
            icon.setImageBitmap(rotated);
            saveIcon(rotated);
            choices.customIcon = true;
        } catch (Exception e) {
            SketchwareUtil.toastError("Couldn't read that image");
        }
    }

    private void pickIcon() {
        File iconFile = iconFile();
        try {
            iconFile.getParentFile().mkdirs();
            if (!iconFile.exists()) iconFile.createNewFile();
        } catch (IOException ignored) {
        }
        Uri uri = Build.VERSION.SDK_INT >= 24
                ? FileProvider.a(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", iconFile)
                : Uri.fromFile(iconFile);
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        intent.putExtra("output", uri);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());
        intent.putExtra("return-data", true);
        startActivityForResult(Intent.createChooser(intent, "Choose an app icon"), REQUEST_CODE_PICK_ICON);
    }

    private File iconFile() {
        return new File(wq.e() + File.separator + scId + File.separator + "icon.png");
    }

    private void saveIcon(Bitmap bitmap) {
        try (FileOutputStream out = new FileOutputStream(iconFile())) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException ignored) {
        }
    }

    // ---- templates

    private void buildTemplates() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String[] row : ProjectPresets.LIST) {
            View card = inflater.inflate(R.layout.ob_np_template, templates, false);
            ((ImageView) card.findViewById(R.id.np_tpl_icon)).setImageResource(templateIcon(row[0]));
            ((TextView) card.findViewById(R.id.np_tpl_badge)).setText(row[1]);
            ((TextView) card.findViewById(R.id.np_tpl_title)).setText(row[2]);
            ((TextView) card.findViewById(R.id.np_tpl_desc)).setText(row[3]);
            card.setTag(row[0]);
            card.setOnClickListener(v -> {
                choices.preset = (String) v.getTag();
                refreshTemplates();
            });
            templates.addView(card);
        }
        refreshTemplates();
    }

    private static int templateIcon(String preset) {
        switch (preset) {
            case ProjectPresets.SCAFFOLD:
                return R.drawable.np_tpl_scaffold;
            case ProjectPresets.BOTTOM_NAVIGATION:
                return R.drawable.np_tpl_bottom_nav;
            case ProjectPresets.DRAWER:
                return R.drawable.np_tpl_drawer;
            case ProjectPresets.TABS:
                return R.drawable.np_tpl_tabs;
            default:
                return R.drawable.np_tpl_empty;
        }
    }

    private void refreshTemplates() {
        for (int i = 0; i < templates.getChildCount(); i++) {
            View card = templates.getChildAt(i);
            card.setSelected(choices.preset.equals(card.getTag()));
        }
    }

    // ---- build engine

    private void refreshEngine() {
        boolean d8 = BuildSettings.SETTING_DEXER_D8.equals(choices.dexer);
        engineModern.setSelected(true);
        engineModern.setTextColor(0xFFFFFFFF);
        engineModern.setText(d8 ? "On the phone: D8 / R8 8.11" : "On the phone: Dx");
        engineCaption.setText((d8
                ? "Core library desugaring (java.time, streams on old Android versions), Java 7 to 11. "
                : "D8 needs Android 8.0 or newer; on this phone Dx compiles at Java 7. ")
                + "Resources: aapt2 2.20 with 16 KB page alignment. A Gradle build doesn't run on the phone; "
                + "Export to Android Studio writes the project as a Gradle project.");
    }

    // ---- colours

    private void buildPalettes() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i <= ThemePresets.PRESETS.length; i++) {
            View pill = inflater.inflate(R.layout.ob_np_palette, palettes, false);
            boolean custom = i == ThemePresets.PRESETS.length;
            ((TextView) pill.findViewById(R.id.np_pal_name)).setText(custom ? "Custom" : ThemePresets.PRESETS[i].name);
            pill.setTag(i);
            pill.setOnClickListener(v -> {
                int index = (Integer) v.getTag();
                if (index == ThemePresets.PRESETS.length) {
                    pickColor(1);
                } else {
                    choices.colors = ThemePresets.PRESETS[index].colors.clone();
                    refreshColors();
                }
            });
            palettes.addView(pill);
        }
    }

    private void fillTones(LinearLayout tones, int[] colors) {
        tones.removeAllViews();
        GradientDrawable frame = new GradientDrawable();
        float dp = getResources().getDisplayMetrics().density;
        frame.setCornerRadius(10 * dp);
        frame.setColor(0xFF000000);
        tones.setBackground(frame);
        tones.setClipToOutline(true);
        M3Palette palette = M3Palette.of(colors[1], colors[0]);
        int[] shown = {colors[1], colors[2], colors[0], palette.get("sketch_m3_primary_container", false)};
        for (int tone : shown) {
            View stripe = new View(this);
            stripe.setBackgroundColor(tone | 0xFF000000);
            tones.addView(stripe, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private void buildColorViews() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < ThemePresets.KEYS.length; i++) {
            View swatch = inflater.inflate(R.layout.myproject_color, colorViews, false);
            ((TextView) swatch.findViewById(R.id.name)).setText(ThemePresets.LABELS[i]);
            ((View) swatch.findViewById(R.id.color).getParent()).setClipToOutline(true);
            swatch.setTag(i);
            swatch.setOnClickListener(v -> {
                if (!mB.a()) pickColor((Integer) v.getTag());
            });
            colorViews.addView(swatch, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private void pickColor(int index) {
        View view = wB.a(this, R.layout.color_picker);
        view.setAnimation(AnimationUtils.loadAnimation(this, R.anim.abc_fade_in));
        Zx picker = new Zx(view, this, choices.colors[index], false, false);
        picker.a(color -> {
            choices.colors = choices.colors.clone();
            choices.colors[index] = color;
            refreshColors();
        });
        picker.setAnimationStyle(R.anim.abc_fade_in);
        picker.showAtLocation(view, Gravity.CENTER, 0, 0);
    }

    private void refreshColors() {
        int selected = ThemePresets.indexOf(choices.colors);
        for (int i = 0; i < palettes.getChildCount(); i++) {
            View pill = palettes.getChildAt(i);
            boolean custom = i == ThemePresets.PRESETS.length;
            fillTones(pill.findViewById(R.id.np_pal_tones), custom ? choices.colors : ThemePresets.PRESETS[i].colors);
            pill.setSelected(custom ? selected < 0 : i == selected);
        }
        for (int i = 0; i < colorViews.getChildCount(); i++) {
            colorViews.getChildAt(i).findViewById(R.id.color).setBackgroundColor(choices.colors[i]);
        }
        refreshPreview();
    }

    private void refreshPreview() {
        if (preview == null) return;
        preview.set(choices.colors, choices.material3, choices.darkTheme, appName.getText().toString());
        String caption;
        if (!choices.material3) {
            caption = "MaterialComponents theme: light only, the project colours as they are.";
        } else if (choices.dynamicColor) {
            caption = "On Android 12 and newer the wallpaper's colours replace the toolbar and window colours shown here; "
                    + "colours you set on a widget yourself stay.";
        } else {
            caption = "Toolbar, button and floating button use your colours in light and dark; the window, text and "
                    + "outline colours are made from the primary colour.";
        }
        previewCaption.setText(caption);
    }

    // ---- Material 3 options

    private View toggle(int id, String title, String subtitle) {
        View row = findViewById(id);
        ((TextView) row.findViewById(R.id.ob_row_title)).setText(title);
        ((TextView) row.findViewById(R.id.ob_row_subtitle)).setText(subtitle);
        return row;
    }

    private void refreshToggles() {
        ((CheckBox) material3Row.findViewById(R.id.ob_row_check)).setChecked(choices.material3);
        ((CheckBox) darkRow.findViewById(R.id.ob_row_check)).setChecked(choices.darkTheme);
        ((CheckBox) dynamicRow.findViewById(R.id.ob_row_check)).setChecked(choices.dynamicColor);
        darkRow.setVisibility(choices.material3 ? View.VISIBLE : View.GONE);
        dynamicRow.setVisibility(choices.material3 ? View.VISIBLE : View.GONE);
        refreshPreview();
    }

    // ---- SDK and Java level

    private void chooseSdk(boolean minimum) {
        int[] values = minimum ? MIN_SDKS : TARGET_SDKS;
        String[] labels = new String[values.length];
        int checked = 0;
        int current = minimum ? choices.minSdk : choices.targetSdk;
        for (int i = 0; i < values.length; i++) {
            labels[i] = sdkLabel(values[i]);
            if (values[i] == current) checked = i;
        }
        new ObAlertBuilder(this)
                .setTitle(minimum ? "Minimum SDK" : "Target SDK")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (minimum) choices.minSdk = values[which];
                    else choices.targetSdk = values[which];
                    if (choices.targetSdk < choices.minSdk) choices.targetSdk = choices.minSdk;
                    refreshSdk();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseJava() {
        boolean legacy = BuildSettings.SETTING_DEXER_DX.equals(choices.dexer);
        String[] levels = legacy ? new String[]{BuildSettings.SETTING_JAVA_VERSION_1_7} : javaLevels();
        String[] labels = new String[levels.length];
        int checked = 0;
        for (int i = 0; i < levels.length; i++) {
            labels[i] = javaLabel(levels[i]);
            if (levels[i].equals(choices.javaVersion)) checked = i;
        }
        new ObAlertBuilder(this)
                .setTitle("Java language level")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    choices.javaVersion = levels[which];
                    refreshSdk();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshSdk() {
        minSdkField.setText(sdkLabel(choices.minSdk));
        targetSdkField.setText(sdkLabel(choices.targetSdk));
        javaField.setText(javaLabel(choices.javaVersion));
        String[] levels = javaLevels();
        String highest = javaLabel(levels[levels.length - 1]).replace(" (recommended)", "");
        javaCaption.setText(BuildSettings.SETTING_DEXER_DX.equals(choices.dexer)
                ? "Dx compiles at Java 7 only."
                : "Java 17 isn't available: the on-phone compiler supports up to " + highest + ". The bundled "
                + "android.jar is API 34; a newer target SDK changes how Android treats the app, not which APIs you can call.");
    }

    static String sdkLabel(int api) {
        switch (api) {
            case 21:
                return "21 (Android 5.0 Lollipop)";
            case 23:
                return "23 (Android 6.0 Marshmallow)";
            case 24:
                return "24 (Android 7.0 Nougat)";
            case 26:
                return "26 (Android 8.0 Oreo)";
            case 28:
                return "28 (Android 9 Pie)";
            case 29:
                return "29 (Android 10)";
            case 30:
                return "30 (Android 11)";
            case 31:
                return "31 (Android 12)";
            case 33:
                return "33 (Android 13)";
            case 34:
                return "34 (Android 14)";
            case 35:
                return "35 (Android 15)";
            case 36:
                return "36 (Android 16)";
            case 37:
                return "37 (Android 17)";
            default:
                return String.valueOf(api);
        }
    }

    static String javaLabel(String level) {
        switch (level) {
            case BuildSettings.SETTING_JAVA_VERSION_1_7:
                return "Java 7";
            case BuildSettings.SETTING_JAVA_VERSION_1_8:
                return "Java 8";
            case BuildSettings.SETTING_JAVA_VERSION_11:
                return "Java 11 (recommended)";
            default:
                return "Java " + level;
        }
    }

    // ---- create

    private void create() {
        if (creating || mB.a()) return;
        if (!(packageValidator.b() & projectNameValidator.b() & appNameValidator.b())) {
            return;
        }
        String code = versionCode.getText().toString().trim();
        String name = versionName.getText().toString().trim();
        // what the project setting dialog's version pickers produce: a whole number and "x.y" (its old version dialog
        // reads the part after the dot; a quote would also break the exported build.gradle)
        int parsedCode;
        try {
            parsedCode = code.isEmpty() ? 1 : Integer.parseInt(code);
        } catch (NumberFormatException e) {
            parsedCode = -1;
        }
        if (parsedCode < 1) {
            versionCode.setError("A whole number from 1");
            versionCode.requestFocus();
            return;
        }
        if (!name.isEmpty() && !name.matches("\\d+\\.\\d+")) {
            versionName.setError("Two numbers with a dot, like 1.0");
            versionName.requestFocus();
            return;
        }
        code = String.valueOf(parsedCode);
        choices.appName = appName.getText().toString();
        choices.packageName = packageName.getText().toString();
        choices.projectName = projectName.getText().toString();
        choices.versionCode = code.isEmpty() ? "1" : code;
        choices.versionName = name.isEmpty() ? "1.0" : name;
        choices.sketchwareVersion = GB.d(getApplicationContext());
        creating = true;
        k();
        new Thread(() -> {
            Throwable failure = null;
            try {
                NewProjectSetup.create(getApplicationContext(), scId, choices);
            } catch (Throwable t) {
                failure = t;
            }
            Throwable result = failure;
            runOnUiThread(() -> {
                h();
                creating = false;
                if (result != null) {
                    // no half-made project in the list: what was written for this id goes again
                    try {
                        lC.a(getApplicationContext(), scId);
                    } catch (Throwable ignored) {
                    }
                    SketchwareUtil.toastError("Couldn't create the project: " + result);
                    return;
                }
                Intent intent = getIntent();
                intent.putExtra("sc_id", scId);
                intent.putExtra("is_new", true);
                setResult(RESULT_OK, intent);
                finish();
            });
        }, "new-project").start();
    }
}
