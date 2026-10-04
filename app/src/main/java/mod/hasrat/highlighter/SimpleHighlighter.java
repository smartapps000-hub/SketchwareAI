package mod.hasrat.highlighter;

import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import com.besome.sketch.editor.LogicEditorActivity;

import java.util.List;
import java.util.regex.Matcher;

/**
 * A Helper class used in {@link LogicEditorActivity}
 * to (currently) highlight add source directly blocks.
 */
public class SimpleHighlighter {

    private final EditText mEditor;
    private final List<SyntaxScheme> syntaxList;

    public SimpleHighlighter(EditText editor) {
        this.mEditor = editor;
        this.syntaxList = SyntaxScheme.JAVA();
        init();
    }

    private void init() {
        removeSpans(mEditor.getText(), ForegroundColorSpan.class);
        createHighlightSpans(syntaxList, mEditor.getText());

        mEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                removeSpans(s, ForegroundColorSpan.class);
                createHighlightSpans(syntaxList, s);
            }
        });
    }

    private void createHighlightSpans(List<SyntaxScheme> syntaxList, Editable editable) {
        for (SyntaxScheme scheme : syntaxList) {
            for (Matcher m = scheme.pattern.matcher(editable); m.find(); ) {
                if (scheme == scheme.getPrimarySyntax()) {
                    editable.setSpan(new SurfaceColorSpan(scheme.color), m.start(), m.end() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    editable.setSpan(new SurfaceColorSpan(scheme.color), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }

    /**
     * A syntax colour that follows the surface the field is on. The scheme's colours were made
     * for white paper (#000000, #010088, ...); both fields that use this highlighter now sit on
     * dark Obsidian surfaces. When the field's own text colour is light (so the ground is dark)
     * the colour is lifted to a readable tint of the same hue; on a light field it is unchanged.
     * Decided when the text is drawn, so it stays right if the field is re-coloured later.
     */
    private static final class SurfaceColorSpan extends ForegroundColorSpan {

        SurfaceColorSpan(int color) {
            super(color);
        }

        @Override
        public void updateDrawState(android.text.TextPaint paint) {
            int base = paint.getColor();
            int color = getForegroundColor();
            paint.setColor(com.sketchware.ai.ui.ObsidianInputs.isLight(base)
                    ? com.sketchware.ai.ui.ObsidianInputs.onDark(color) : color);
        }
    }

    private void removeSpans(Editable editable, Class<? extends CharacterStyle> type) {
        CharacterStyle[] spans = editable.getSpans(0, editable.length(), type);
        for (CharacterStyle span : spans) {
            editable.removeSpan(span);
        }
    }
}
