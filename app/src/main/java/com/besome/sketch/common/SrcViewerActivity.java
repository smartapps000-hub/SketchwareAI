package com.besome.sketch.common;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.besome.sketch.beans.SrcCodeBean;
import com.besome.sketch.ctrls.CommonSpinnerItem;
import com.sketchware.remod.R;

import java.util.ArrayList;

import a.a.a.bB;
import a.a.a.jC;
import a.a.a.yq;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import mod.SketchwareUtil;
import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;

public class SrcViewerActivity extends AppCompatActivity {

    private String sc_id;
    private Spinner filesListSpinner;
    private ImageView changeFontSize;
    private LinearLayout progressContainer;
    private ArrayList<SrcCodeBean> srcCodeBean;
    /**
     * Corresponds to the filename of which layout or activity the user is currently in.
     */
    private String currentPageFileName;
    private int sourceCodeFontSize = 12;
    private CodeEditor codeViewer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.src_viewer);

        currentPageFileName = getIntent().hasExtra("current") ? getIntent().getStringExtra("current") : "";

        codeViewer = new CodeEditor(this);
        codeViewer.setTypefaceText(Typeface.MONOSPACE);
        codeViewer.setEditable(false);
        codeViewer.setTextSize(sourceCodeFontSize);
        codeViewer.setPinLineNumber(true);
        setCorrectCodeEditorLanguage();

        // Sketchware AI: opened from the compile log, long lines wrap and the error's line is marked.
        if (getIntent().hasExtra("path")) {
            codeViewer.setWordwrap(true);
            codeViewer.setHighlightCurrentLine(true);
        }
        applyObsidianChrome();

        LinearLayout contentLayout = (LinearLayout) (findViewById(R.id.pager_soruce_code).getParent());
        contentLayout.removeAllViews();
        contentLayout.addView(codeViewer);

        sc_id = (savedInstanceState != null) ? savedInstanceState.getString("sc_id") : getIntent().getStringExtra("sc_id");

        changeFontSize = findViewById(R.id.imgv_src_size);
        changeFontSize.setOnClickListener((v -> showChangeFontSizeDialog()));

        filesListSpinner = findViewById(R.id.spn_src_list);
        filesListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SrcCodeBean bean = srcCodeBean.get(position);
                codeViewer.setText(bean.source);
                currentPageFileName = bean.srcFileName;
                setCorrectCodeEditorLanguage();
                jumpToRequestedLine();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        LinearLayout layoutSrcList = findViewById(R.id.layout_srclist);
        for (int i = 0; i < layoutSrcList.getChildCount(); i++) {
            View child = layoutSrcList.getChildAt(i);

            if (child instanceof LinearLayout) {
                // Found the LinearLayout containing the ProgressBar and TextView!
                progressContainer = (LinearLayout) child;

                filesListSpinner.setVisibility(View.GONE);
                changeFontSize.setVisibility(View.GONE);
                progressContainer.setVisibility(View.VISIBLE);
            }
        }

        // Sketchware AI: from the compile log, the one file the compiler read is shown as it is on disk.
        final String compiledFilePath = getIntent().getStringExtra("path");
        new Thread(() -> {
            if (compiledFilePath != null) {
                srcCodeBean = readCompiledFile(compiledFilePath);
            } else {
                srcCodeBean = new yq(getBaseContext(), sc_id).a(jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
            }

            try {
                runOnUiThread(() -> {
                    if (srcCodeBean == null && compiledFilePath != null) {
                        bB.b(getApplicationContext(), "That file isn't there any more. Build again, then open it from the new log.", bB.TOAST_NORMAL).show();
                        finish();
                    } else if (srcCodeBean == null) {
                        bB.b(getApplicationContext(), Helper.getResString(R.string.common_error_unknown), bB.TOAST_NORMAL).show();
                    } else {
                        filesListSpinner.setAdapter(new FilesListSpinnerAdapter());
                        for (SrcCodeBean src : srcCodeBean) {
                            if (src.srcFileName.equals(currentPageFileName)) {
                                filesListSpinner.setSelection(srcCodeBean.indexOf(src));
                                break;
                            }
                        }
                        SrcCodeBean shown = srcCodeBean.get(filesListSpinner.getSelectedItemPosition());
                        codeViewer.setText(shown.source);
                        if (shown.srcFileName.equals(currentPageFileName)) jumpToRequestedLine();

                        progressContainer.setVisibility(View.GONE);
                        filesListSpinner.setVisibility(View.VISIBLE);
                        changeFontSize.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {
                // May occur if the activity is killed
            }
        }).start();
    }

    /** The file bar in the app's dark glass instead of the old plain bar. */
    private void applyObsidianChrome() {
        try {
            com.sketchware.ai.ui.ObsidianWindow.apply(this);
            View bar = findViewById(R.id.layout_srclist);
            bar.setBackgroundResource(R.drawable.ob_toolbar_glass);
            ((View) bar.getParent()).setBackgroundColor(0xFF121214);
            View size = findViewById(R.id.imgv_src_size);
            size.setBackgroundResource(R.drawable.ob_pill16_state);
        } catch (Throwable t) {
            android.util.Log.w("SrcViewerActivity", "Obsidian chrome unavailable", t);
        }
    }

    /** @return the file as the only entry of the list, or null if it can't be read */
    private static ArrayList<SrcCodeBean> readCompiledFile(String path) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
            }
            ArrayList<SrcCodeBean> list = new ArrayList<>();
            list.add(new SrcCodeBean(file.getName(), new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)));
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sketchware AI: "View source" from the compile log opens the file at the error's line. Called
     * each time a file's text is put in the viewer, until the requested file has been shown.
     */
    private void jumpToRequestedLine() {
        final int line = getIntent().getIntExtra("line", 0);
        String wanted = getIntent().getStringExtra("current");
        if (line <= 0 || wanted == null || !wanted.equals(currentPageFileName)) return;
        getIntent().removeExtra("line");
        codeViewer.postDelayed(() -> {
            try {
                if (line <= codeViewer.getLineCount()) {
                    // Go some lines past it first, so the line ends up in the upper part of the screen.
                    int rows = Math.max(6, codeViewer.getHeight() / Math.max(1, codeViewer.getRowHeight()));
                    codeViewer.ensurePositionVisible(Math.min(codeViewer.getLineCount() - 1, line - 1 + rows * 2 / 3), 0);
                    codeViewer.jumpToLine(line - 1);
                    // The whole line is selected, so it stands out.
                    codeViewer.setSelectionRegion(line - 1, 0, line - 1, codeViewer.getText().getColumnCount(line - 1));
                }
            } catch (RuntimeException ignored) {
                // The viewer just stays at the top.
            }
        }, 300);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    private void setCorrectCodeEditorLanguage() {
        if (currentPageFileName.endsWith(".xml")) {
            codeViewer.setColorScheme(CodeEditorColorSchemes.GITHUB);
            codeViewer.setEditorLanguage(CodeEditorLanguages.XML);
        } else {
            codeViewer.setColorScheme(new EditorColorScheme());
            codeViewer.setEditorLanguage(new JavaLanguage());
        }
    }

    private void showChangeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(8);
        picker.setMaxValue(30);
        picker.setWrapSelectorWheel(false);
        picker.setValue(sourceCodeFontSize);

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new com.sketchware.ai.ui.ObAlertBuilder(this)
                .setTitle("Select font size")
                .setIcon(R.drawable.ic_font_48dp)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    sourceCodeFontSize = picker.getValue();
                    codeViewer.setTextSize(sourceCodeFontSize);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public class FilesListSpinnerAdapter extends BaseAdapter {

        private View getCustomSpinnerView(int position, View view, boolean isCurrentlyViewingFile) {
            CommonSpinnerItem spinnerItem = (view != null) ? (CommonSpinnerItem) view :
                    new CommonSpinnerItem(SrcViewerActivity.this);
            spinnerItem.a((srcCodeBean.get(position)).srcFileName, isCurrentlyViewingFile);
            return spinnerItem;
        }

        @Override
        public int getCount() {
            return srcCodeBean.size();
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            boolean isCheckmarkVisible = (filesListSpinner.getSelectedItemPosition() == position);
            return getCustomSpinnerView(position, convertView, isCheckmarkVisible);
        }

        @Override
        public Object getItem(int position) {
            return srcCodeBean.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomSpinnerView(position, convertView, false);
        }
    }
}
