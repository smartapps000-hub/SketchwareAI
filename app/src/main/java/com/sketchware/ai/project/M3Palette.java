package com.sketchware.ai.project;

import com.google.android.material.color.utilities.DynamicColor;
import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.MaterialDynamicColors;
import com.google.android.material.color.utilities.SchemeContent;
import com.google.android.material.color.utilities.TonalPalette;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Material 3 colour roles of a project (v1.8.6), computed from its primary colour with Material Color Utilities (the
 * code Material Components 1.13 and the Material Theme Builder use; Apache-2.0, in app/libs).
 * <p>
 * The scheme is {@code SchemeContent}: its palettes keep the hue and chroma of the project colour, so containers,
 * surfaces and outlines are tinted with it in light and dark. The colours the user picked stay exact: the theme's
 * {@code colorPrimary} is the project's primary colour and {@code colorSecondary} its accent colour in both modes
 * (the generated toolbar, filled buttons and FAB use them). Their "on" colours are white when white is readable on them
 * (contrast 3:1 or more, the M3 minimum for large text and UI parts), otherwise the scheme's darkest tone of that hue.
 * <p>
 * Every role is written as a colour resource ({@code sketch_m3_*} in values/m3_colors.xml, dark values in
 * values-night/m3_colors.xml) and AppTheme points its attribute at it.
 */
public final class M3Palette {

    /** Theme attribute, colour resource name. The order is the order in styles.xml and m3_colors.xml. */
    public static final String[][] ROLES = {
            {"colorOnPrimary", "sketch_m3_on_primary"},
            {"colorPrimaryContainer", "sketch_m3_primary_container"},
            {"colorOnPrimaryContainer", "sketch_m3_on_primary_container"},
            {"colorPrimaryInverse", "sketch_m3_primary_inverse"},
            {"colorOnSecondary", "sketch_m3_on_secondary"},
            {"colorSecondaryContainer", "sketch_m3_secondary_container"},
            {"colorOnSecondaryContainer", "sketch_m3_on_secondary_container"},
            {"colorTertiary", "sketch_m3_tertiary"},
            {"colorOnTertiary", "sketch_m3_on_tertiary"},
            {"colorTertiaryContainer", "sketch_m3_tertiary_container"},
            {"colorOnTertiaryContainer", "sketch_m3_on_tertiary_container"},
            {"colorError", "sketch_m3_error"},
            {"colorOnError", "sketch_m3_on_error"},
            {"colorErrorContainer", "sketch_m3_error_container"},
            {"colorOnErrorContainer", "sketch_m3_on_error_container"},
            {"android:colorBackground", "sketch_m3_background"},
            {"colorOnBackground", "sketch_m3_on_background"},
            {"colorSurface", "sketch_m3_surface"},
            {"colorOnSurface", "sketch_m3_on_surface"},
            {"colorSurfaceVariant", "sketch_m3_surface_variant"},
            {"colorOnSurfaceVariant", "sketch_m3_on_surface_variant"},
            {"colorSurfaceInverse", "sketch_m3_surface_inverse"},
            {"colorOnSurfaceInverse", "sketch_m3_on_surface_inverse"},
            {"colorSurfaceBright", "sketch_m3_surface_bright"},
            {"colorSurfaceDim", "sketch_m3_surface_dim"},
            {"colorSurfaceContainerLowest", "sketch_m3_surface_container_lowest"},
            {"colorSurfaceContainerLow", "sketch_m3_surface_container_low"},
            {"colorSurfaceContainer", "sketch_m3_surface_container"},
            {"colorSurfaceContainerHigh", "sketch_m3_surface_container_high"},
            {"colorSurfaceContainerHighest", "sketch_m3_surface_container_highest"},
            {"colorOutline", "sketch_m3_outline"},
            {"colorOutlineVariant", "sketch_m3_outline_variant"},
    };

    /** Contrast the "on" colour of a user colour must reach for white to be used (WCAG ratio). */
    private static final double ON_COLOR_CONTRAST = 3.0;

    private final Map<String, Integer> light;
    private final Map<String, Integer> dark;

    private M3Palette(Map<String, Integer> light, Map<String, Integer> dark) {
        this.light = light;
        this.dark = dark;
    }

    /**
     * @param primary the project's primary colour (ARGB; alpha is ignored)
     * @param accent  the project's accent colour, kept as colorSecondary
     */
    public static M3Palette of(int primary, int accent) {
        primary |= 0xff000000;
        accent |= 0xff000000;
        return new M3Palette(roles(primary, accent, false), roles(primary, accent, true));
    }

    private static Map<String, Integer> roles(int primary, int accent, boolean isDark) {
        DynamicScheme scheme = new SchemeContent(Hct.fromInt(primary), isDark, 0.0);
        MaterialDynamicColors colors = new MaterialDynamicColors();
        // tone 10 of the user colour's own palette: dark text of the same hue on a light user colour
        int darkOnPrimary = TonalPalette.fromInt(primary).tone(10);
        int darkOnSecondary = TonalPalette.fromInt(accent).tone(10);
        Map<String, Integer> roles = new LinkedHashMap<>();
        roles.put("sketch_m3_on_primary", onColor(primary, darkOnPrimary));
        put(roles, "sketch_m3_primary_container", colors.primaryContainer(), scheme);
        put(roles, "sketch_m3_on_primary_container", colors.onPrimaryContainer(), scheme);
        put(roles, "sketch_m3_primary_inverse", colors.inversePrimary(), scheme);
        roles.put("sketch_m3_on_secondary", onColor(accent, darkOnSecondary));
        put(roles, "sketch_m3_secondary_container", colors.secondaryContainer(), scheme);
        put(roles, "sketch_m3_on_secondary_container", colors.onSecondaryContainer(), scheme);
        put(roles, "sketch_m3_tertiary", colors.tertiary(), scheme);
        put(roles, "sketch_m3_on_tertiary", colors.onTertiary(), scheme);
        put(roles, "sketch_m3_tertiary_container", colors.tertiaryContainer(), scheme);
        put(roles, "sketch_m3_on_tertiary_container", colors.onTertiaryContainer(), scheme);
        put(roles, "sketch_m3_error", colors.error(), scheme);
        put(roles, "sketch_m3_on_error", colors.onError(), scheme);
        put(roles, "sketch_m3_error_container", colors.errorContainer(), scheme);
        put(roles, "sketch_m3_on_error_container", colors.onErrorContainer(), scheme);
        put(roles, "sketch_m3_background", colors.background(), scheme);
        put(roles, "sketch_m3_on_background", colors.onBackground(), scheme);
        put(roles, "sketch_m3_surface", colors.surface(), scheme);
        put(roles, "sketch_m3_on_surface", colors.onSurface(), scheme);
        put(roles, "sketch_m3_surface_variant", colors.surfaceVariant(), scheme);
        put(roles, "sketch_m3_on_surface_variant", colors.onSurfaceVariant(), scheme);
        put(roles, "sketch_m3_surface_inverse", colors.inverseSurface(), scheme);
        put(roles, "sketch_m3_on_surface_inverse", colors.inverseOnSurface(), scheme);
        put(roles, "sketch_m3_surface_bright", colors.surfaceBright(), scheme);
        put(roles, "sketch_m3_surface_dim", colors.surfaceDim(), scheme);
        put(roles, "sketch_m3_surface_container_lowest", colors.surfaceContainerLowest(), scheme);
        put(roles, "sketch_m3_surface_container_low", colors.surfaceContainerLow(), scheme);
        put(roles, "sketch_m3_surface_container", colors.surfaceContainer(), scheme);
        put(roles, "sketch_m3_surface_container_high", colors.surfaceContainerHigh(), scheme);
        put(roles, "sketch_m3_surface_container_highest", colors.surfaceContainerHighest(), scheme);
        put(roles, "sketch_m3_outline", colors.outline(), scheme);
        put(roles, "sketch_m3_outline_variant", colors.outlineVariant(), scheme);
        return roles;
    }

    private static void put(Map<String, Integer> roles, String name, DynamicColor color, DynamicScheme scheme) {
        roles.put(name, color.getArgb(scheme) | 0xff000000);
    }

    /** White on the colour when readable (contrast >= 3:1), otherwise the given dark tone. */
    static int onColor(int background, int darkTone) {
        return contrast(0xffffffff, background) >= ON_COLOR_CONTRAST ? 0xffffffff : darkTone | 0xff000000;
    }

    /** WCAG 2 contrast ratio of two opaque colours. */
    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel((color >> 16) & 0xff) + 0.7152 * channel((color >> 8) & 0xff) + 0.0722 * channel(color & 0xff);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** The value of a role ({@link #ROLES} resource name) in light or dark. */
    public int get(String resourceName, boolean isDark) {
        Integer value = (isDark ? dark : light).get(resourceName);
        return value == null ? 0 : value;
    }

    /** values/m3_colors.xml (light) or values-night/m3_colors.xml (dark). */
    public String colorsXml(boolean isDark) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        xml.append(isDark
                ? "\t<!-- Material 3 dark colour roles from the project's primary colour (Sketchware AI, M3Palette) -->\n"
                : "\t<!-- Material 3 colour roles from the project's primary colour (Sketchware AI, M3Palette) -->\n");
        for (Map.Entry<String, Integer> role : (isDark ? dark : light).entrySet()) {
            xml.append("\t<color name=\"").append(role.getKey()).append("\">")
                    .append(String.format("#%06X", role.getValue() & 0xffffff)).append("</color>\n");
        }
        return xml.append("</resources>\n").toString();
    }
}
