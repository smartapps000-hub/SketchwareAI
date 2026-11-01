package com.sketchware.ai.ui;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * v1.9.0: Material 3 dark colours for sora-editor's own Java language (the logic editor's "&lt;/&gt;" sheet).
 * <p>
 * Before, the sheet paired the TextMate Dracula theme with {@code JavaLanguage}: a TextMate scheme only colours
 * TextMate tokens, so keywords, names and literals of {@code JavaLanguage} kept {@link EditorColorScheme}'s defaults,
 * which are made for a light background (#2196F3 blue, #666666 grey: 3.2:1 on the dark sheet). Here every token
 * colour the language uses is set, each at least 5:1 against the background (WCAG AA asks 4.5:1): surface #141218
 * (M3 dark surface), text #E6E0E9, keywords #FFB68A, types #8ECFF2, method names #FFD37C, literals #A8DB8F,
 * comments #A49FAB, annotations #D0BCFF.
 */
public class M3CodeScheme extends EditorColorScheme {

    public static final int SURFACE = 0xFF141218;
    public static final int SURFACE_CONTAINER = 0xFF211F26;
    public static final int ON_SURFACE = 0xFFE6E0E9;
    public static final int ON_SURFACE_VARIANT = 0xFFCAC4D0;
    public static final int OUTLINE = 0xFF938F99;
    public static final int OUTLINE_VARIANT = 0xFF49454F;

    public M3CodeScheme() {
        super(true);   // dark; the super constructor creates the colour table and calls applyDefault()
    }

    @Override
    public void applyDefault() {
        super.applyDefault();
        setColor(WHOLE_BACKGROUND, SURFACE);
        setColor(LINE_NUMBER_BACKGROUND, SURFACE);
        setColor(LINE_DIVIDER, 0xFF2B2930);
        setColor(LINE_NUMBER, OUTLINE);
        setColor(LINE_NUMBER_CURRENT, ON_SURFACE);
        setColor(LINE_NUMBER_PANEL, SURFACE_CONTAINER);
        setColor(LINE_NUMBER_PANEL_TEXT, ON_SURFACE);
        setColor(TEXT_NORMAL, ON_SURFACE);
        setColor(IDENTIFIER_VAR, ON_SURFACE);
        setColor(KEYWORD, 0xFFFFB68A);
        setColor(IDENTIFIER_NAME, 0xFF8ECFF2);
        setColor(FUNCTION_NAME, 0xFFFFD37C);
        setColor(LITERAL, 0xFFA8DB8F);
        setColor(OPERATOR, ON_SURFACE_VARIANT);
        setColor(COMMENT, 0xFFA49FAB);
        setColor(ANNOTATION, 0xFFD0BCFF);
        setColor(HTML_TAG, 0xFFFFB68A);
        setColor(ATTRIBUTE_NAME, 0xFF8ECFF2);
        setColor(ATTRIBUTE_VALUE, 0xFFA8DB8F);
        setColor(CURRENT_LINE, SURFACE_CONTAINER);
        setColor(SELECTED_TEXT_BACKGROUND, 0xFF4A4458);
        setColor(TEXT_SELECTED, 0xFFFFFFFF);
        setColor(SELECTION_INSERT, 0xFFFF7A29);
        setColor(SELECTION_HANDLE, 0xFFFF7A29);
        setColor(MATCHED_TEXT_BACKGROUND, 0xFF4F378B);
        setColor(UNDERLINE, 0xFFFFB68A);
        setColor(BLOCK_LINE, 0xFF36343B);
        setColor(BLOCK_LINE_CURRENT, 0xFF6C6772);
        setColor(SIDE_BLOCK_LINE, 0xFF6C6772);
        setColor(NON_PRINTABLE_CHAR, OUTLINE_VARIANT);
        setColor(SCROLL_BAR_THUMB, OUTLINE_VARIANT);
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xFF6C6772);
        setColor(SCROLL_BAR_TRACK, 0x00000000);
        setColor(COMPLETION_WND_BACKGROUND, SURFACE_CONTAINER);
        setColor(COMPLETION_WND_CORNER, OUTLINE_VARIANT);
        setColor(COMPLETION_WND_TEXT_PRIMARY, ON_SURFACE);
        setColor(COMPLETION_WND_TEXT_SECONDARY, ON_SURFACE_VARIANT);
        setColor(COMPLETION_WND_ITEM_CURRENT, 0xFF36343B);
        setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, 0xFFFFB68A);
        setColor(HIGHLIGHTED_DELIMITERS_UNDERLINE, 0xFFFFB68A);
        setColor(HIGHLIGHTED_DELIMITERS_BACKGROUND, 0x00000000);
        setColor(PROBLEM_ERROR, 0xFFFFB4AB);
        setColor(PROBLEM_WARNING, 0xFFFFD37C);
        setColor(PROBLEM_TYPO, 0xFF8ECFF2);
        setColor(TEXT_INLAY_HINT_FOREGROUND, ON_SURFACE_VARIANT);
        setColor(TEXT_INLAY_HINT_BACKGROUND, SURFACE_CONTAINER);
    }

    @Override
    public boolean isDark() {
        return true;
    }
}
