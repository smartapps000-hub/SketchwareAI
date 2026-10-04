package com.sketchware.ai.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;
import com.sketchware.remod.R;

/**
 * The one input theme of every Obsidian dialog and sheet: text fields, their floating labels and
 * the check / radio / switch controls.
 * <p>
 * Dialog contents are inflated or built with the <em>activity's</em> light theme, so without this
 * a field keeps the theme's dark text colour, and a {@link TextInputLayout} draws its hint in the
 * theme's dark {@code textColorHint} (it ignores the hint colour set on the EditText inside it).
 * {@link #style(View)} walks a view tree and gives
 * <ul>
 *     <li>every {@link EditText}: light text, a readable hint, an underline that is light at rest
 *     and amber when focused, an amber cursor, selection and handles;</li>
 *     <li>every {@link TextInputLayout}: a readable resting label, an amber focused label, amber
 *     box stroke, readable helper and error text;</li>
 *     <li>every check box, radio button and switch: amber when on, a light outline when off.</li>
 * </ul>
 * Views tagged {@link ObsidianAdapter#TAG_NATIVE} and anything on a light card are left alone.
 * Each view is styled once. Cosmetic only: a failure never breaks the dialog.
 */
public final class ObsidianInputs {

    static final int TEXT = 0xFFF5F5F7;
    static final int TEXT_DISABLED = 0x80F5F5F7;
    static final int HINT = 0xFFA1A1AA;
    static final int ACCENT = 0xFFFF7A29;
    static final int LABEL_FOCUSED = 0xFFFFB877;
    static final int ERROR = 0xFFFF8A65;
    static final int OUTLINE = 0xB3FFFFFF;
    static final int UNDERLINE = 0x8CFFFFFF;

    private ObsidianInputs() {
    }

    /** Styles every input under {@code root} that sits on the dark sheet. */
    public static void style(View root) {
        if (root == null) return;
        try {
            walk(root, true);
        } catch (RuntimeException ignored) {
            // Cosmetic only.
        }
    }

    private static void walk(View view, boolean onDark) {
        if (ObsidianAdapter.TAG_NATIVE.equals(view.getTag())) return;
        onDark = ObsidianAdapter.groundIsDark(view, onDark);
        if (onDark) apply(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), onDark);
        }
    }

    /** Styles {@code view} itself if it is an input; the caller knows it sits on a dark ground. */
    static void apply(View view) {
        try {
            if (view instanceof TextInputLayout) {
                layout((TextInputLayout) view);
            } else if (view instanceof EditText) {
                field((EditText) view);
            } else if (view instanceof CompoundButton) {
                toggle((CompoundButton) view);
            }
        } catch (RuntimeException ignored) {
            // Cosmetic only: this also runs from layout listeners, where nothing may be thrown.
        }
    }

    public static void field(EditText edit) {
        if (edit.getTag(R.id.ob_input_styled) != null) return;
        edit.setTag(R.id.ob_input_styled, Boolean.TRUE);

        ColorStateList colors = edit.getTextColors();
        if (colors == null || luminance(colors.getDefaultColor()) < 0.6 || Color.alpha(colors.getDefaultColor()) < 200) {
            edit.setTextColor(new ColorStateList(
                    new int[][]{{-android.R.attr.state_enabled}, {}},
                    new int[]{TEXT_DISABLED, TEXT}));
        }
        edit.setHintTextColor(HINT);
        edit.setHighlightColor(0x66FF7A29);
        if (isUnderline(edit.getBackground())) {
            edit.setBackgroundTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{ACCENT, UNDERLINE}));
        }
        cursor(edit);
    }

    public static void layout(TextInputLayout layout) {
        if (layout.getTag(R.id.ob_input_styled) != null) return;
        layout.setTag(R.id.ob_input_styled, Boolean.TRUE);
        // Resting label (and the label of a filled, unfocused field); this setter also resets
        // the focused colour, so the focused appearance is applied after it.
        layout.setDefaultHintTextColor(ColorStateList.valueOf(HINT));
        try {
            layout.setHintTextAppearance(R.style.Ob_FieldLabel_Focused);
        } catch (RuntimeException ignored) {
            // The label keeps the resting colour when focused.
        }
        layout.setBoxStrokeColor(ACCENT);
        try {
            layout.setErrorTextColor(ColorStateList.valueOf(ERROR));
            layout.setHelperTextColor(ColorStateList.valueOf(HINT));
        } catch (RuntimeException ignored) {
            // Error and helper text keep the library colours.
        }
        // Newer Material versions than the bundled one: resting outline and counter.
        call(layout, "setBoxStrokeColorStateList", new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{ACCENT, UNDERLINE}));
        call(layout, "setCounterTextColor", ColorStateList.valueOf(HINT));
        EditText edit = layout.getEditText();
        if (edit != null) field(edit);
    }

    public static void toggle(CompoundButton button) {
        if (button.getTag(R.id.ob_input_styled) != null) return;
        button.setTag(R.id.ob_input_styled, Boolean.TRUE);
        ColorStateList mark = new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}},
                new int[]{0x66FFFFFF, ACCENT, OUTLINE});
        if (button.getClass().getName().contains("Switch")) {
            call(button, "setThumbTintList", new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{ACCENT, 0xFFD4D4D8}));
            call(button, "setTrackTintList", new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{0x80FF7A29, 0x4DFFFFFF}));
        } else {
            button.setButtonTintList(mark);
        }
    }

    /**
     * A colour that stays readable on the dark sheet: light colours are kept, dark greys become
     * the text colour, dark hues keep their hue but are lifted (syntax colours made for white
     * paper, such as #010088 or #880000).
     */
    public static int onDark(int color) {
        if (luminance(color) >= 0.45) return color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] < 0.2f) return TEXT;
        hsv[1] = Math.min(hsv[1], 0.5f);
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    /** Whether a text colour is a light one, i.e. the text was made for a dark ground. */
    public static boolean isLight(int color) {
        return luminance(color) >= 0.5;
    }

    /**
     * The classic text-field line (AppCompat's and the framework's edit-text background: an inset
     * around a selector of nine-patches). It is a decoration, not a surface: text on it sits on
     * whatever is behind the field.
     */
    static boolean isUnderline(Drawable background) {
        if (background instanceof InsetDrawable) {
            Drawable inner = ((InsetDrawable) background).getDrawable();
            if (inner == null) return false;
            background = inner;
        }
        if (background instanceof NinePatchDrawable) return true;
        if (background instanceof DrawableContainer) {
            Drawable current = background.getCurrent();
            return !(current instanceof GlassDrawable) && !(current instanceof GradientDrawable)
                    && !(current instanceof ColorDrawable);
        }
        return false;
    }

    private static void cursor(TextView edit) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                edit.setTextCursorDrawable(R.drawable.ob_cursor);
                tintHandle(edit, "TextSelectHandle");
                tintHandle(edit, "TextSelectHandleLeft");
                tintHandle(edit, "TextSelectHandleRight");
            } else {
                // Android 8-9 have no setter: the cursor resource is read when it is first drawn.
                java.lang.reflect.Field res = TextView.class.getDeclaredField("mCursorDrawableRes");
                res.setAccessible(true);
                res.setInt(edit, R.drawable.ob_cursor);
            }
        } catch (Throwable ignored) {
            // The cursor keeps the theme's colour.
        }
    }

    private static void tintHandle(TextView edit, String name) throws ReflectiveOperationException {
        Drawable handle = (Drawable) TextView.class.getMethod("get" + name).invoke(edit);
        if (handle == null) return;
        handle = handle.mutate();
        handle.setTint(ACCENT);
        TextView.class.getMethod("set" + name, Drawable.class).invoke(edit, handle);
    }

    /** Calls a one-argument setter that only some library versions have. */
    private static void call(Object target, String method, ColorStateList value) {
        try {
            target.getClass().getMethod(method, ColorStateList.class).invoke(target, value);
        } catch (Throwable ignored) {
            // Not in this version.
        }
    }

    private static double luminance(int color) {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0;
    }
}
