package com.sketchware.ai.editor;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.besome.sketch.beans.ProjectFileBean;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.remod.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import a.a.a.jC;
import a.a.a.lC;
import mod.agus.jcoderz.lib.FileUtil;

/**
 * Code tab of the design editor (v1.8.8): the project's Java code in one place. Activities open in the Java editor
 * (their code is the blocks'; saving turns changes into blocks); the project's own Java/Kotlin files (Java Manager)
 * open in the code editor; + New class makes a Java or Kotlin class or activity in the project's package.
 */
public class CodeTabFragment extends Fragment {

    private String scId;
    private LinearLayout list;
    /** Java file names of the activities (a new class must not take one: it would replace the generated activity). */
    private final List<String> activityFiles = new ArrayList<>();
    /** Classes the build generates next to the activities. */
    private static final List<String> GENERATED = Arrays.asList("SketchwareUtil", "FileUtil", "SketchApplication",
            "DebugActivity", "SketchLogger", "SketchTextField", "SketchButton", "SketchFonts", "RequestNetwork",
            "RequestNetworkController", "BluetoothConnect", "BluetoothController", "R", "BuildConfig");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        scId = savedInstanceState != null ? savedInstanceState.getString("sc_id") : requireActivity().getIntent().getStringExtra("sc_id");
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setTag("obsidian");   // drawn for the dark ground: ObsidianAdapter leaves it alone
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        list = new LinearLayout(requireContext());
        list.setTag("obsidian");
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        list.setPadding(pad, dp(8), pad, dp(96));
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", scId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    /** True once the design editor has loaded the project (its data must not be read while ProjectLoader runs). */
    private boolean projectLoaded() {
        android.app.Activity activity = getActivity();
        if (activity instanceof com.besome.sketch.design.DesignActivity) {
            return ((com.besome.sketch.design.DesignActivity) activity).isProjectReady();
        }
        return jC.b != null && scId != null && scId.equals(jC.b.e);
    }

    public void refresh() {
        if (list == null || getContext() == null || scId == null) return;
        list.removeAllViews();
        if (!projectLoaded()) {
            note("Loading the project…");
            return;
        }
        TextView create = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.ob_lib_tab, list, false);
        create.setText("+ New class");
        create.setBackgroundResource(R.drawable.ob_cta_state);
        create.setTextColor(0xFFFFFFFF);
        create.setOnClickListener(v -> showCreateDialog());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.topMargin = dp(4);
        list.addView(create, lp);

        header("Activities");
        File javaDir = new File(javaPath());
        List<ProjectFileBean> activities;
        try {
            activities = jC.b(scId).b();
        } catch (RuntimeException e) {
            activities = new ArrayList<>();
        }
        List<String> activityFiles = this.activityFiles;
        activityFiles.clear();
        for (ProjectFileBean file : activities) {
            String javaName = file.getJavaName();
            activityFiles.add(javaName);
            boolean own = new File(javaDir, javaName).isFile();
            View row = row(javaName, own ? "Your own file (Java Manager) instead of the blocks" : "From the blocks · Edit turns code into blocks");
            row.setOnClickListener(v -> startActivity(JavaEditorActivity.intent(requireContext(), scId, javaName)));
            action(row, "Edit", v -> startActivity(JavaEditorActivity.intent(requireContext(), scId, javaName)));
            list.addView(row);
        }

        header("Classes");
        List<File> files = new ArrayList<>();
        collect(javaDir, files);
        int shown = 0;
        for (File f : files) {
            String relative = f.getAbsolutePath().substring(javaDir.getAbsolutePath().length() + 1);
            if (activityFiles.contains(relative)) continue;
            shown++;
            View row = row(f.getName(), (relative.contains("/") ? relative.substring(0, relative.lastIndexOf('/')) + " · " : "")
                    + (f.getName().endsWith(".kt") ? "Kotlin" : "Java") + " · " + f.length() + " bytes");
            row.setOnClickListener(v -> open(f));
            action(row, "Open", v -> open(f));
            list.addView(row);
        }
        if (shown == 0) note("No classes of your own yet: + New class makes one.");
    }

    private String javaPath() {
        return new mod.agus.jcoderz.lib.FilePathUtil().getPathJava(scId);
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File c : children) {
            if (c.isDirectory()) collect(c, out);
            else if (c.getName().endsWith(".java") || c.getName().endsWith(".kt")) out.add(c);
        }
    }

    private void open(File file) {
        Intent intent = new Intent(requireContext(), mod.hey.studios.code.SrcCodeEditor.class);
        if (mod.hilal.saif.activities.tools.ConfigActivity.isLegacyCeEnabled()) {
            intent.setClass(requireContext(), mod.hey.studios.code.SrcCodeEditorLegacy.class);
        }
        intent.putExtra("java", "");
        intent.putExtra("title", file.getName());
        intent.putExtra("content", file.getAbsolutePath());
        startActivity(intent);
    }

    private static final String JAVA_CLASS = "package %s;\n\npublic class %s {\n\n}\n";
    private static final String JAVA_ACTIVITY = "package %s;\n\nimport android.app.Activity;\nimport android.os.Bundle;\n\n"
            + "public class %s extends Activity {\n\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n"
            + "        super.onCreate(savedInstanceState);\n    }\n}\n";
    private static final String KOTLIN_CLASS = "package %s\n\nclass %s {\n\n}\n";
    private static final String KOTLIN_ACTIVITY = "package %s\n\nimport android.app.Activity\nimport android.os.Bundle\n\n"
            + "class %s : Activity() {\n\n    override fun onCreate(savedInstanceState: Bundle?) {\n"
            + "        super.onCreate(savedInstanceState)\n    }\n}\n";

    private void showCreateDialog() {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = new EditText(requireContext(), null, 0, R.style.Ob_Field);
        name.setHint("Class name, e.g. ApiClient");
        name.setSingleLine(true);
        box.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        RadioGroup kind = new RadioGroup(requireContext());
        String[] labels = {"Java class", "Java activity", "Kotlin class", "Kotlin activity"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(requireContext());
            r.setId(View.generateViewId());
            r.setText(labels[i]);
            r.setTextColor(0xFFF5F5F7);
            r.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFFF7A29));
            kind.addView(r);
            if (i == 0) r.setChecked(true);
        }
        box.addView(kind);
        TextView hint = new TextView(requireContext(), null, 0, R.style.Ob_Text_Caption);
        hint.setText("In package " + packageName() + ". An activity also needs its entry in AndroidManifest (Java Manager → ⋮ → Add as Activity).");
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);
        AlertDialog dialog = new ObAlertBuilder(requireContext())
                .setTitle("New class")
                .setView(box)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (!n.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                name.setError("A Java name: letters, digits and _, not starting with a digit");
                return;
            }
            int index = kind.indexOfChild(kind.findViewById(kind.getCheckedRadioButtonId()));
            if (activityFiles.contains(n + ".java") || GENERATED.contains(n)) {
                name.setError(n + " is generated by the build: choose another name");
                return;
            }
            boolean kotlin = index >= 2;
            if (kotlin && Build.VERSION.SDK_INT < 26) {
                Toast.makeText(requireContext(), "Kotlin needs Android 8 or newer on this phone", Toast.LENGTH_LONG).show();
                return;
            }
            String template = index == 1 ? JAVA_ACTIVITY : index == 2 ? KOTLIN_CLASS : index == 3 ? KOTLIN_ACTIVITY : JAVA_CLASS;
            File file = new File(javaPath(), n + (kotlin ? ".kt" : ".java"));
            if (file.exists() || new File(javaPath(), n + (kotlin ? ".java" : ".kt")).exists()) {
                name.setError(file.getName() + " exists already");
                return;
            }
            FileUtil.writeFile(file.getAbsolutePath(), String.format(template, packageName(), n));
            dialog.dismiss();
            refresh();
            open(file);
        });
    }

    private String packageName() {
        try {
            HashMap<String, Object> project = lC.b(scId);
            Object p = project == null ? null : project.get("my_sc_pkg_name");
            if (p != null) return String.valueOf(p);
        } catch (RuntimeException ignored) {
            // falls through
        }
        return "com.my.app";
    }

    private View row(String title, String subtitle) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.ob_lib_row, list, false);
        ((TextView) row.findViewById(R.id.lib_title)).setText(title);
        ((TextView) row.findViewById(R.id.lib_subtitle)).setText(subtitle);
        return row;
    }

    private void action(View row, String text, View.OnClickListener listener) {
        TextView button = row.findViewById(R.id.lib_action);
        button.setText(text);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(listener);
    }

    private void header(String text) {
        TextView t = new TextView(getContext(), null, 0, R.style.Ob_Text_Label);
        t.setText(text);
        t.setPadding(dp(4), dp(18), dp(4), dp(2));
        list.addView(t);
    }

    private void note(String text) {
        TextView t = new TextView(getContext(), null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setPadding(dp(4), dp(10), dp(4), dp(4));
        list.addView(t);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
