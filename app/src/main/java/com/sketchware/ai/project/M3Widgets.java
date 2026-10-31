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
 * ({@link ModernProjectDefaults#SETTING_MATERIAL3}); nothing changes for other projects. The theme keeps the AppCompat
 * inflater (as v1.8.4), so only the tags written here change class:
 * <ul>
 * <li>A plain {@code <Button>} without a background of its own becomes {@code <package>.SketchButton}, a filled
 * MaterialButton that shows a background set from code as set (MaterialButton would tint it with its own colour).</li>
 * <li>{@code <CheckBox>} / {@code <RadioButton>} become MaterialCheckBox / MaterialRadioButton (subclasses, so the
 * generated Java types stay valid), unless the user gave them an own button drawable or style.</li>
 * <li>{@code <Switch>} has no Material subclass (MaterialSwitch extends SwitchCompat, not android.widget.Switch), so
 * the layout gets {@code MaterialSwitch} and the generated Java declares it as MaterialSwitch.</li>
 * <li>{@code <EditText>} is wrapped in an outlined TextInputLayout; the field itself is {@code <package>.SketchTextField}
 * (a TextInputEditText, so {@code EditText} fields stay valid) whose show/hide also shows/hides the outlined box.</li>
 * <li>Text that has the editor's default colour (black, hint #607D8B) and sits on the theme's background follows the
 * theme ({@code ?attr/colorOnSurface}, {@code ?attr/colorOnSurfaceVariant}, {@code ?attr/colorOnPrimary} on a
 * filled button). Text on a background the user chose keeps its colour.</li>
 * </ul>
 * The widget rewrites are left out for the whole project when the user replaced an activity's Java file or
 * styles.xml (their code or theme was written for the framework widgets), and per layout when the user replaced that
 * layout file. The project's own code is read first ({@link #scanCode}): Switches stay framework Switches when any code
 * uses the type {@code Switch} or sets a thumb/track; an EditText, CheckBox or RadioButton stays plain when code moves
 * it between parents, reads or replaces its layout params, or sets its button drawable. "Plain" means what the
 * palette gives: the palette sets {@code convert} to the widget's own class name ("Button", "EditText", "Switch"), so
 * that counts as not converted.
 */
public final class M3Widgets {

    public static final String MATERIAL_CARD = "com.google.android.material.card.MaterialCardView";
    public static final String MATERIAL_CARD_STYLE = "@style/Widget.Material3.CardView.Elevated";
    public static final String MATERIAL_TOOLBAR = "com.google.android.material.appbar.MaterialToolbar";
    public static final String MATERIAL_SWITCH = "com.google.android.material.materialswitch.MaterialSwitch";
    public static final String TEXT_INPUT_LAYOUT = "com.google.android.material.textfield.TextInputLayout";
    public static final String TEXT_FIELD_CLASS = "SketchTextField";
    public static final String BUTTON_CLASS = "SketchButton";
    public static final String MATERIAL_CHECKBOX = "com.google.android.material.checkbox.MaterialCheckBox";
    public static final String MATERIAL_RADIO_BUTTON = "com.google.android.material.radiobutton.MaterialRadioButton";
    public static final String OUTLINED_BOX_STYLE = "@style/Widget.Material3.TextInputLayout.OutlinedBox";
    /** v1.8.6: the generated outlined field style (yq, styles.xml): OutlinedBox with {@link #TEXT_FIELD_CORNER} corners. */
    public static final String TEXT_FIELD_STYLE_NAME = "AppTheme.TextField";
    public static final String TEXT_FIELD_STYLE = "@style/" + TEXT_FIELD_STYLE_NAME;
    public static final String TEXT_FIELD_CORNER = "8dp";
    /** v1.8.6: the Material 3 colour roles (M3Palette): values/m3_colors.xml, and the dark ones in values-night. */
    public static final String M3_COLORS_FILE = "m3_colors.xml";
    /** Source-list name of values-night/m3_colors.xml (yq writes it there). */
    public static final String M3_COLORS_NIGHT_FILE = "m3_colors-night.xml";

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

    /**
     * Reads the project's own code (blocks with their parameters, add-source-directly code, custom variables, more
     * blocks) and records in the build config what must stay a framework widget (see the class comment).
     */
    public static void scanCode(jq buildConfig, a.a.a.eC projectData) {
        buildConfig.m3Switches = false;
        buildConfig.m3KeepPlain = new HashSet<>();
        if (!buildConfig.m3Widgets) return;
        String code;
        try {
            code = projectCode(projectData);
        } catch (RuntimeException e) {
            buildConfig.m3Widgets = false; // unreadable project data: generate as before
            return;
        }
        buildConfig.m3Switches = !SWITCH_CODE.matcher(code).find();
        if (projectData == null || projectData.c == null) return;
        for (ArrayList<ViewBean> views : projectData.c.values()) {
            if (views == null) continue;
            for (ViewBean view : views) {
                if (view == null || view.id == null || view.id.isEmpty()) continue;
                if (view.type == ViewBean.VIEW_TYPE_WIDGET_BUTTON) {
                    // v1.8.6: a Button with a background colour becomes a tinted SketchButton; code that reads its
                    // background (a ColorDrawable on a plain Button) keeps it plain
                    if (readsBackground(code, view.id)) buildConfig.m3KeepPlain.add(view.id);
                    continue;
                }
                if (view.type != ViewBean.VIEW_TYPE_WIDGET_EDITTEXT && view.type != ViewBean.VIEW_TYPE_WIDGET_CHECKBOX
                        && view.type != ViewBeans.VIEW_TYPE_WIDGET_RADIOBUTTON) continue;
                if (movesOrStyles(code, view.id)) buildConfig.m3KeepPlain.add(view.id);
            }
        }
    }

    /** The type Switch used by name, or a thumb/track set or read (MaterialSwitch would tint the user's drawables). */
    private static final Pattern SWITCH_CODE = Pattern.compile("(?<![\\w$])Switch(?![\\w$])|[sg]et(Thumb|Track)");

    private static boolean readsBackground(String code, String id) {
        String name = "(?<![\\w$.])(_drawer_)?" + Pattern.quote(id) + "(?![\\w$])";
        return Pattern.compile(name + "\\s*\\.\\s*getBackground\\s*\\(").matcher(code).find();
    }

    private static boolean movesOrStyles(String code, String id) {
        String name = "(?<![\\w$.])(_drawer_)?" + Pattern.quote(id) + "(?![\\w$])"; // drawer views are _drawer_<id>
        return Pattern.compile(name + "\\s*\\.\\s*(getLayoutParams|setLayoutParams|getParent|setButtonDrawable|setButtonTint\\w*|setButtonIcon\\w*)\\s*\\("
                + "|(addView|addViewInLayout|removeView|removeViewInLayout|indexOfChild|bringChildToFront|attachViewToParent|detachViewFromParent)\\s*\\([^;\\n]*" + name)
                .matcher(code).find();
    }

    /** All code-like text of the project: each block as opCode(parameters), custom variables, more-block specs. */
    private static String projectCode(a.a.a.eC projectData) {
        StringBuilder out = new StringBuilder();
        if (projectData == null) return "";
        if (projectData.d != null) {
            for (java.util.HashMap<String, ArrayList<com.besome.sketch.beans.BlockBean>> events : projectData.d.values()) {
                if (events == null) continue;
                for (ArrayList<com.besome.sketch.beans.BlockBean> blocks : events.values()) {
                    if (blocks == null) continue;
                    for (com.besome.sketch.beans.BlockBean block : blocks) {
                        if (block == null) continue;
                        out.append(block.opCode).append('(');
                        if (block.parameters != null) {
                            for (int i = 0; i < block.parameters.size(); i++) {
                                if (i > 0) out.append(", ");
                                out.append(block.parameters.get(i));
                            }
                        }
                        out.append(")\n");
                    }
                }
            }
        }
        appendPairs(out, projectData.e);
        appendPairs(out, projectData.f);
        appendPairs(out, projectData.g);
        return out.toString();
    }

    private static void appendPairs(StringBuilder out, java.util.HashMap<String, ? extends ArrayList<? extends android.util.Pair<?, ?>>> map) {
        if (map == null) return;
        for (ArrayList<? extends android.util.Pair<?, ?>> pairs : map.values()) {
            if (pairs == null) continue;
            for (android.util.Pair<?, ?> pair : pairs) {
                if (pair == null) continue;
                out.append(pair.first).append(' ').append(pair.second).append('\n');
            }
        }
    }

    /** Whether the project's code allows MaterialSwitch (see {@link #scanCode}); false when rewrites are off. */
    public static boolean switchesFor(jq buildConfig, String xmlName) {
        return enabledFor(buildConfig, xmlName) && buildConfig.m3Switches;
    }

    /** The project's code moves, re-lays out or restyles this view (see {@link #scanCode}): it stays plain. */
    public static boolean keepsPlain(jq buildConfig, ViewBean bean) {
        return buildConfig == null || buildConfig.m3KeepPlain == null || bean.id == null
                || buildConfig.m3KeepPlain.contains(bean.id);
    }

    /** {@link #wrapsEditText(ViewBean, ViewBean, boolean)} for a generation, with what the project's code does. */
    public static boolean wrapsEditText(jq buildConfig, ViewBean bean, ViewBean parent, boolean material3) {
        return wrapsEditText(bean, parent, material3) && !keepsPlain(buildConfig, bean);
    }

    /**
     * A plain CheckBox / RadioButton without an own button drawable or style becomes MaterialCheckBox /
     * MaterialRadioButton; null when it stays as written.
     */
    public static String materialCompound(ViewBean bean, boolean material3) {
        if (!material3 || !plain(bean)) return null;
        String inject = inject(bean);
        if (inject.contains("button") || inject.contains("style")) return null;
        if (bean.type == ViewBean.VIEW_TYPE_WIDGET_CHECKBOX) return MATERIAL_CHECKBOX;
        if (bean.type == ViewBeans.VIEW_TYPE_WIDGET_RADIOBUTTON) return MATERIAL_RADIO_BUTTON;
        return null;
    }

    /** {@link #materialCompound(ViewBean, boolean)} for a generation, with what the project's code does. */
    public static String materialCompound(jq buildConfig, ViewBean bean, boolean material3) {
        return keepsPlain(buildConfig, bean) ? null : materialCompound(bean, material3);
    }

    /** The user replaced styles.xml with own XML (files/resource/values): the generated AppTheme isn't used. */
    public static boolean stylesReplaced(String sc_id) {
        try {
            mod.agus.jcoderz.lib.FilePathUtil paths = new mod.agus.jcoderz.lib.FilePathUtil();
            return new File(paths.getPathResource(sc_id), "values" + File.separator + "styles.xml").exists();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** {@link #decide(jq, hC)} from the project's id and whether AppCompat is on (the design editor). */
    public static boolean decide(String sc_id, boolean appCompat, hC projectFiles) {
        if (!appCompat || sc_id == null || sc_id.isEmpty()) {
            return false;
        }
        try {
            if (!ModernProjectDefaults.material3(new ProjectSettings(sc_id))) return false;
            mod.agus.jcoderz.lib.FilePathUtil paths = new mod.agus.jcoderz.lib.FilePathUtil();
            if (stylesReplaced(sc_id)) return false;
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

    /** v1.8.9: a palette CardView not converted to another class, and no style injected: written as MaterialCardView. */
    public static boolean materialCard(ViewBean bean) {
        if (bean.type != ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW) return false;
        String c = bean.convert == null ? "" : bean.convert.trim();
        if (!(c.isEmpty() || c.equals("CardView") || c.equals("androidx.cardview.widget.CardView"))) return false;
        return bean.inject == null || !Pattern.compile("(^|\\s)style\\s*=").matcher(bean.inject).find();
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
        // the editor stores parentType 0 for a view dropped into a TextInputLayout (drawn as a LinearLayout): the
        // parent's own type tells (v1.9.0 fix)
        if (bean.parentType == ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT
                || parent != null && parent.type == ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT) return false;
        if (parent != null && !plain(parent)) return false;
        if (hasOwnBackground(bean)) return false;
        String inject = inject(bean);
        return !inject.contains("style") && !inject.contains("background") && !inject.contains("layout_")
                && !inject.contains("visibility");
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
     * A plain Button generated as {@code <package>.SketchButton}, a filled MaterialButton (its label sits on the
     * primary colour): no background of its own and no injected style.
     */
    public static boolean filledButton(ViewBean bean) {
        return bean.type == ViewBean.VIEW_TYPE_WIDGET_BUTTON && plain(bean) && !hasOwnBackground(bean)
                && !inject(bean).contains("style");
    }

    /**
     * v1.8.6: a plain Button whose only own background is an opaque colour (the Background colour property): generated
     * as a SketchButton filled with that colour ({@code app:backgroundTint}), so it keeps the Material 3 pill shape
     * instead of becoming a flat rectangle. An image, a transparent or translucent colour, or an injected background,
     * tint or style keep the plain Button.
     */
    public static boolean tintedButton(ViewBean bean) {
        if (bean.type != ViewBean.VIEW_TYPE_WIDGET_BUTTON || !plain(bean)) return false;
        String resource = bean.layout.backgroundResource;
        String inject = inject(bean);
        int color = bean.layout.backgroundColor;
        return color != NO_BACKGROUND && (color >>> 24) == 0xff
                && (resource == null || resource.isEmpty() || "NONE".equalsIgnoreCase(resource))
                && !inject.contains("background") && !inject.contains("style");
    }

    /** {@link #tintedButton(ViewBean)} for a generation: not when the project's code reads the button's background. */
    public static boolean tintedButton(jq buildConfig, ViewBean bean) {
        return tintedButton(bean) && !keepsPlain(buildConfig, bean);
    }

    /**
     * The label colour for a tinted button whose text has the editor's default colour: white when readable on the fill
     * (contrast 3:1 or more), otherwise near-black.
     */
    public static int onColor(int fill) {
        return M3Palette.contrast(0xffffffff, fill | 0xff000000) >= 3.0 ? 0xffffffff : 0xff1d1b20;
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
        box.a("", "style", TEXT_FIELD_STYLE);
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

    /**
     * v1.8.13: the reverse of {@link #routeHints} for text the Java editor stores (add-source blocks, custom variables):
     * {@code SketchTextField.hint(edittext1, } back to {@code edittext1.setHint(} for the fields routeHints routes, so
     * the stored code doesn't depend on Material 3 staying on; the generator routes it again. Only the exact form
     * routeHints writes is turned back (anything else would not generate back to the same text).
     */
    public static String unrouteHints(String code, Set<String> wrappedIds) {
        if (wrappedIds.isEmpty() || !code.contains(TEXT_FIELD_CLASS + ".hint(")) return code;
        String result = code;
        for (String id : wrappedIds) {
            Pattern call = Pattern.compile("(?<![\\w.$])" + Pattern.quote(TEXT_FIELD_CLASS + ".hint(" + id + ", "));
            result = call.matcher(result).replaceAll(Matcher.quoteReplacement(id + ".setHint("));
        }
        return result;
    }

    /** Source of {@code <package>.SketchButton}. */
    public static String buttonClass(String packageName) {
        return "package " + packageName + ";\n"
                + "\n"
                + "import android.content.Context;\n"
                + "import android.graphics.drawable.Drawable;\n"
                + "import android.util.AttributeSet;\n"
                + "\n"
                + "import com.google.android.material.button.MaterialButton;\n"
                + "\n"
                + "/**\n"
                + " * Written by Sketchware AI for this Material 3 project: a Button of the design editor, shown as a filled\n"
                + " * Material 3 button. A background set from code (a colour drawable, an image, a GradientDrawable with rounded\n"
                + " * corners) is shown as set, as on a plain Button; MaterialButton alone would tint it with the button colour.\n"
                + " */\n"
                + "public class SketchButton extends MaterialButton {\n"
                + "\n"
                + "    public SketchButton(Context context) {\n"
                + "        super(context);\n"
                + "    }\n"
                + "\n"
                + "    public SketchButton(Context context, AttributeSet attrs) {\n"
                + "        super(context, attrs);\n"
                + "    }\n"
                + "\n"
                + "    public SketchButton(Context context, AttributeSet attrs, int defStyleAttr) {\n"
                + "        super(context, attrs, defStyleAttr);\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    @SuppressWarnings(\"deprecation\")\n"
                + "    public void setBackgroundDrawable(Drawable background) {\n"
                + "        Drawable before = getBackground();\n"
                + "        super.setBackgroundDrawable(background);\n"
                + "        if (before != null && background != before) {\n"
                + "            setSupportBackgroundTintList(null);\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    /** Source of {@code <package>.SketchTextField}. */
    public static String textFieldClass(String packageName) {
        return "package " + packageName + ";\n"
                + "\n"
                + "import android.content.Context;\n"
                + "import android.content.res.Configuration;\n"
                + "import android.graphics.Typeface;\n"
                + "import android.os.Build;\n"
                + "import android.text.TextPaint;\n"
                + "import android.util.AttributeSet;\n"
                + "import android.util.DisplayMetrics;\n"
                + "import android.util.TypedValue;\n"
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
                + "    /**\n"
                + "     * Like a plain EditText, a field whose width is wrap_content is at least as wide as its hint: the box shows the\n"
                + "     * hint, and the label once there is text, within the field's width.\n"
                + "     */\n"
                + "    @Override\n"
                + "    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {\n"
                + "        super.onMeasure(widthMeasureSpec, heightMeasureSpec);\n"
                + "        int mode = MeasureSpec.getMode(widthMeasureSpec);\n"
                + "        if (mode == MeasureSpec.EXACTLY) {\n"
                + "            return;\n"
                + "        }\n"
                + "        TextInputLayout box = box(this);\n"
                + "        if (box == null || !box.isHintEnabled() || box.getEditText() != this) {\n"
                + "            return;\n"
                + "        }\n"
                + "        CharSequence hint = box.getHint();\n"
                + "        if (hint == null || hint.length() == 0) {\n"
                + "            return;\n"
                + "        }\n"
                + "        // the label is drawn in the box's collapsed text appearance (Material 3 bodySmall: 12sp, letter spacing\n"
                + "        // 0.4sp) and typeface, made heavier by the phone's bold-text setting: measure the widest of field and label\n"
                + "        DisplayMetrics metrics = getResources().getDisplayMetrics();\n"
                + "        measurePaint.set(getPaint());\n"
                + "        float label = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, metrics);\n"
                + "        measurePaint.setTextSize(Math.max(getTextSize(), label));\n"
                + "        if (Build.VERSION.SDK_INT >= 21) {\n"
                + "            measurePaint.setLetterSpacing(Math.max(getLetterSpacing(), 0.4f / 12f));\n"
                + "        }\n"
                + "        Typeface face = box.getTypeface() != null ? box.getTypeface() : getTypeface();\n"
                + "        if (Build.VERSION.SDK_INT >= 31 && face != null) {\n"
                + "            int adjustment = getResources().getConfiguration().fontWeightAdjustment;\n"
                + "            if (adjustment > 0 && adjustment != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED) {\n"
                + "                face = Typeface.create(face, Math.min(1000, face.getWeight() + adjustment), face.isItalic());\n"
                + "            }\n"
                + "        }\n"
                + "        if (face != null) {\n"
                + "            measurePaint.setTypeface(face);\n"
                + "        }\n"
                + "        float width = Math.max(measurePaint.measureText(hint, 0, hint.length()),\n"
                + "                getPaint().measureText(hint, 0, hint.length()));\n"
                + "        int wanted = (int) Math.ceil(width) + Math.round(2 * metrics.density)\n"
                + "                + getCompoundPaddingLeft() + getCompoundPaddingRight();\n"
                + "        if (mode == MeasureSpec.AT_MOST) {\n"
                + "            wanted = Math.min(wanted, MeasureSpec.getSize(widthMeasureSpec));\n"
                + "        }\n"
                + "        if (wanted > getMeasuredWidth()) {\n"
                + "            setMeasuredDimension(wanted, getMeasuredHeight());\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    private final TextPaint measurePaint = new TextPaint();\n"
                + "\n"
                + "    /** setHint for a field that may sit in an outlined box: the box shows the hint as its label. */\n"
                + "    public static void hint(TextView field, CharSequence hint) {\n"
                + "        TextInputLayout box = box(field);\n"
                + "        if (box != null && box.isHintEnabled() && box.getEditText() == field) {\n"
                + "            box.setHint(hint);\n"
                + "            field.requestLayout();\n"
                + "        } else {\n"
                + "            field.setHint(hint);\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    /** setHint(R.string...) for a field that may sit in an outlined box. */\n"
                + "    public static void hint(TextView field, int hint) {\n"
                + "        hint(field, field.getContext().getText(hint));\n"
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
