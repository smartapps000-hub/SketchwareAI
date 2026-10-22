package com.sketchware.ai.project;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Nx;
import a.a.a.hC;
import a.a.a.jq;
import mod.agus.jcoderz.beans.ViewBeans;
import mod.hey.studios.project.ProjectSettings;

/**
 * Material 3 widgets in generated code (v1.8.5). Applies to projects with AppCompat and the Material 3 theme
 * ({@link ModernProjectDefaults#SETTING_MATERIAL3}); nothing changes for other projects.
 * <ul>
 * <li>{@code <Button>}, {@code <CheckBox>}, {@code <RadioButton>}, {@code <TextView>} need no rewrite: the theme's
 * MaterialComponentsViewInflater inflates them as MaterialButton, MaterialCheckBox, MaterialRadioButton,
 * MaterialTextView (subclasses, so the generated Java types stay valid).</li>
 * <li>{@code <Switch>} has no Material subclass (MaterialSwitch extends SwitchCompat, not android.widget.Switch), so
 * the layout gets {@code MaterialSwitch} and the generated Java declares it as MaterialSwitch.</li>
 * <li>{@code <EditText>} is wrapped in an outlined TextInputLayout; the field itself is {@code <package>.SketchTextField}
 * (a TextInputEditText, so {@code EditText} fields stay valid) whose show/hide also shows/hides the outlined box.</li>
 * <li>Text that has the editor's default colour (black, hint #607D8B) and sits on the theme's background follows the
 * theme ({@code ?attr/colorOnSurface}, {@code ?attr/colorOnSurfaceVariant}, {@code ?attr/colorOnPrimary} on a
 * filled button), so it stays readable in dark mode (DayNight theme). Text on a background the user chose keeps its
 * colour.</li>
 * </ul>
 * The widget rewrites are left out for the whole project when the user replaced an activity's Java file or
 * styles.xml (their code or theme was written for the framework widgets), and per layout when the user replaced that
 * layout file. "Plain" means what the palette gives: the palette sets {@code convert} to the widget's own class name
 * ("Button", "EditText", "Switch"), so that counts as not converted.
 */
public final class M3Widgets {

    public static final String MATERIAL_SWITCH = "com.google.android.material.materialswitch.MaterialSwitch";
    public static final String TEXT_INPUT_LAYOUT = "com.google.android.material.textfield.TextInputLayout";
    public static final String TEXT_FIELD_CLASS = "SketchTextField";
    public static final String OUTLINED_BOX_STYLE = "@style/Widget.Material3.TextInputLayout.OutlinedBox";

    /** Sketchware's default text colours (TextBean). */
    public static final int DEFAULT_TEXT_COLOR = 0xff000000;
    public static final int DEFAULT_HINT_COLOR = 0xff607d8b;
    /** LayoutBean.backgroundColor of a widget without a background colour. */
    public static final int NO_BACKGROUND = 0xffffff;

    /** Attributes of the field that move to the outlined box: they place or transform the whole field. */
    private static final Set<String> BOX_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "alpha", "rotation", "translationX", "translationY", "scaleX", "scaleY"));

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private M3Widgets() {
    }

    /**
     * Decided once per generation (yq sets up jq): AppCompat on, the Material 3 theme, and neither styles.xml nor an
     * activity's Java file replaced by the user.
     */
    public static boolean decide(jq buildConfig, hC projectFiles) {
        return buildConfig != null && decide(buildConfig.sc_id, buildConfig.g, projectFiles);
    }

    /** {@link #decide(jq, hC)} from the project's id and whether AppCompat is on (the design editor). */
    public static boolean decide(String sc_id, boolean appCompat, hC projectFiles) {
        if (!appCompat || sc_id == null || sc_id.isEmpty()) {
            return false;
        }
        try {
            if (!ModernProjectDefaults.material3(new ProjectSettings(sc_id))) return false;
            mod.agus.jcoderz.lib.FilePathUtil paths = new mod.agus.jcoderz.lib.FilePathUtil();
            File values = new File(paths.getPathResource(sc_id), "values" + File.separator + "styles.xml");
            if (values.exists()) return false;
            if (projectFiles != null) {
                File java = new File(paths.getPathJava(sc_id));
                for (ProjectFileBean activity : projectFiles.b()) {
                    if (new File(java, activity.getJavaName()).exists()) return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether this project's generated code uses Material 3 widgets (see {@link #decide}). */
    public static boolean enabled(jq buildConfig) {
        return buildConfig != null && buildConfig.m3Widgets;
    }

    /** {@link #enabled} for one layout file: false when the user replaced that layout with own XML. */
    public static boolean enabledFor(jq buildConfig, String xmlName) {
        return enabled(buildConfig) && !layoutReplaced(buildConfig, xmlName);
    }

    /**
     * A layout the user replaced with own XML (files/resource/layout) is copied as written: its views keep their
     * framework types.
     */
    public static boolean layoutReplaced(jq buildConfig, String xmlName) {
        if (buildConfig == null || xmlName == null || xmlName.isEmpty()) return false;
        String dir = new mod.agus.jcoderz.lib.FilePathUtil().getPathResource(buildConfig.sc_id);
        return new File(dir, "layout" + File.separator + xmlName).exists();
    }

    /** For the static list-item code of Lx: set around one adapter's generation (its item layout). */
    public static void enter(boolean material3) {
        ACTIVE.set(material3);
    }

    public static void exit() {
        ACTIVE.remove();
    }

    public static boolean active() {
        Boolean value = ACTIVE.get();
        return value != null && value;
    }

    /** Not converted to another class: no convert, or the palette's own class name ("Button", "Switch", ...). */
    public static boolean plain(ViewBean bean) {
        String convert = bean.convert == null ? "" : bean.convert.replaceAll(" ", "");
        if (convert.isEmpty()) return true;
        try {
            return convert.equals(bean.getClassInfo().a());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String inject(ViewBean bean) {
        return bean.inject == null ? "" : bean.inject;
    }

    /**
     * A plain Switch becomes a MaterialSwitch, unless the user styled it: an injected style, thumb or track
     * (framework attributes MaterialSwitch doesn't read).
     */
    public static boolean isMaterialSwitch(ViewBean bean, boolean material3) {
        if (!material3 || bean.type != ViewBean.VIEW_TYPE_WIDGET_SWITCH || !plain(bean)) return false;
        String inject = inject(bean);
        return !inject.contains("style") && !inject.contains("thumb") && !inject.contains("track");
    }

    /**
     * A plain EditText is wrapped unless the user styled or placed it: own background (colour, transparent, image or
     * injected), an injected style or layout attribute, it already sits in a TextInputLayout, or its parent is
     * converted to another layout class (RelativeLayout/ConstraintLayout rules of siblings point at its id).
     */
    public static boolean wrapsEditText(ViewBean bean, ViewBean parent, boolean material3) {
        if (!material3 || bean.type != ViewBean.VIEW_TYPE_WIDGET_EDITTEXT || !plain(bean)) return false;
        if (bean.parentType == ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT) return false;
        if (parent != null && !plain(parent)) return false;
        if (hasOwnBackground(bean)) return false;
        String inject = inject(bean);
        return !inject.contains("style") && !inject.contains("background") && !inject.contains("layout_");
    }

    /** The bean of a view's parent in a layout's view list, or null (root). */
    public static ViewBean parentOf(ViewBean bean, List<ViewBean> views) {
        if (bean.parent == null || views == null) return null;
        for (ViewBean candidate : views) {
            if (bean.parent.equals(candidate.id)) return candidate;
        }
        return null;
    }

    /** Any background of its own: a colour (transparent too), an image, or an injected background or tint. */
    public static boolean hasOwnBackground(ViewBean bean) {
        String resource = bean.layout.backgroundResource;
        String inject = inject(bean);
        return bean.layout.backgroundColor != NO_BACKGROUND
                || (resource != null && !resource.isEmpty() && !"NONE".equalsIgnoreCase(resource))
                || inject.contains("android:background") || inject.contains("backgroundTint");
    }

    /** A background that hides what is behind the view (a transparent colour doesn't). */
    public static boolean hasOpaqueBackground(ViewBean bean) {
        int color = bean.layout.backgroundColor;
        String resource = bean.layout.backgroundResource;
        String inject = inject(bean);
        return (color != NO_BACKGROUND && color != 0)
                || (resource != null && !resource.isEmpty() && !"NONE".equalsIgnoreCase(resource))
                || inject.contains("android:background") || inject.contains("backgroundTint");
    }

    /**
     * A plain Button the theme turns into a filled MaterialButton (its label sits on the primary colour): no
     * background of its own and no injected style.
     */
    public static boolean filledButton(ViewBean bean) {
        return bean.type == ViewBean.VIEW_TYPE_WIDGET_BUTTON && plain(bean) && !hasOwnBackground(bean)
                && !inject(bean).contains("style");
    }

    /**
     * Wraps an EditText tag in an outlined TextInputLayout: layout attributes (size, margins, weight, gravity),
     * alpha and transforms move to the box, visibility is set on both, the field fills the box.
     */
    public static Nx wrapEditText(Nx edit, String packageName) {
        Nx box = new Nx(TEXT_INPUT_LAYOUT);
        List<String[]> moved = new ArrayList<>();
        String visibility = null;
        for (String[] attribute : edit.attributes()) {
            if (!"android".equals(attribute[0])) continue;
            if (attribute[1].startsWith("layout_") || BOX_ATTRIBUTES.contains(attribute[1])) {
                moved.add(attribute);
            } else if (attribute[1].equals("visibility")) {
                visibility = attribute[2];
            }
        }
        String height = "wrap_content";
        for (String[] attribute : moved) {
            edit.removeAttribute(attribute[0], attribute[1]);
            box.a(attribute[0], attribute[1], attribute[2]);
            if (attribute[1].equals("layout_height") && !attribute[2].equals("wrap_content")) height = "match_parent";
        }
        if (visibility != null) box.a("android", "visibility", visibility);
        box.a("", "style", OUTLINED_BOX_STYLE);
        edit.a(0, "android", "layout_height", height);
        edit.a(0, "android", "layout_width", "match_parent");
        edit.a = packageName + "." + TEXT_FIELD_CLASS;
        box.a(edit);
        return box;
    }

    /**
     * {@code <id>.setHint(...)} on a wrapped field would draw a second hint inside the box (TextInputLayout shows
     * the field's hint as its label): those calls go through {@code SketchTextField.hint(<id>, ...)}, which sets the
     * box's label. Only the call's beginning changes, so every line stays where it is (source map).
     */
    public static String routeHints(String code, Set<String> wrappedIds) {
        if (wrappedIds.isEmpty() || !code.contains(".setHint(")) return code;
        String result = code;
        for (String id : wrappedIds) {
            Pattern call = Pattern.compile("(?<![\\w.$])" + Pattern.quote(id) + "\\.setHint\\(");
            Matcher matcher = call.matcher(result);
            result = matcher.replaceAll(Matcher.quoteReplacement(TEXT_FIELD_CLASS + ".hint(" + id + ", "));
        }
        return result;
    }

    /** Source of {@code <package>.SketchTextField}. */
    public static String textFieldClass(String packageName) {
        return "package " + packageName + ";\n"
                + "\n"
                + "import android.content.Context;\n"
                + "import android.util.AttributeSet;\n"
                + "import android.view.View;\n"
                + "import android.view.ViewParent;\n"
                + "import android.widget.TextView;\n"
                + "\n"
                + "import com.google.android.material.textfield.TextInputEditText;\n"
                + "import com.google.android.material.textfield.TextInputLayout;\n"
                + "\n"
                + "/**\n"
                + " * Written by Sketchware AI for this Material 3 project: an EditText of the design editor, shown as an\n"
                + " * outlined Material 3 text field. Showing or hiding it shows or hides the whole field.\n"
                + " */\n"
                + "public class SketchTextField extends TextInputEditText {\n"
                + "\n"
                + "    public SketchTextField(Context context) {\n"
                + "        super(context);\n"
                + "    }\n"
                + "\n"
                + "    public SketchTextField(Context context, AttributeSet attrs) {\n"
                + "        super(context, attrs);\n"
                + "    }\n"
                + "\n"
                + "    public SketchTextField(Context context, AttributeSet attrs, int defStyleAttr) {\n"
                + "        super(context, attrs, defStyleAttr);\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public void setVisibility(int visibility) {\n"
                + "        super.setVisibility(visibility);\n"
                + "        TextInputLayout box = box(this);\n"
                + "        if (box != null && box.getVisibility() != visibility) {\n"
                + "            box.setVisibility(visibility);\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    /** setHint for a field that may sit in an outlined box: the box shows the hint as its label. */\n"
                + "    public static void hint(TextView field, CharSequence hint) {\n"
                + "        TextInputLayout box = box(field);\n"
                + "        if (box != null && box.isHintEnabled() && box.getEditText() == field) {\n"
                + "            box.setHint(hint);\n"
                + "        } else {\n"
                + "            field.setHint(hint);\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    private static TextInputLayout box(View view) {\n"
                + "        ViewParent parent = view.getParent();\n"
                + "        while (parent instanceof View) {\n"
                + "            if (parent instanceof TextInputLayout) {\n"
                + "                return (TextInputLayout) parent;\n"
                + "            }\n"
                + "            parent = parent.getParent();\n"
                + "        }\n"
                + "        return null;\n"
                + "    }\n"
                + "}\n";
    }
}
