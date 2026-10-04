package com.sketchware.ai.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the SVG of a project icon into an Android vector drawable. Projects made with newer
 * Sketchware Pro versions keep each imported icon as an SVG (data/&lt;sc_id&gt;/converted-vectors)
 * with the colour the user chose already written into it, next to the vector made from it.
 * When only the SVG is left, this makes the same vector again: same paths, same colours, twice
 * the SVG's size in dp. Paths, circles, rectangles and ellipses are converted; anything else in
 * the file (gradients, text, transforms) is not, and {@link #convert} says so.
 */
public final class SvgVector {

    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][\\w:-]*)((?:\"[^\"]*\"|'[^']*'|[^>\"'])*?)(/?)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    public static final class Result {
        /** The vector drawable XML, or null when the file has nothing that could be converted. */
        public String xml;
        /** Elements that were left out (their names), empty when the conversion is complete. */
        public final List<String> ignored = new ArrayList<>();
    }

    private SvgVector() {
    }

    public static Result convert(String svg) {
        Result result = new Result();
        float width = 24, height = 24, viewportWidth = 24, viewportHeight = 24;
        boolean header = false;
        StringBuilder paths = new StringBuilder();
        List<String> groupFill = new ArrayList<>();
        List<String> groupStroke = new ArrayList<>();
        String skipUntil = null;
        Matcher tag = TAG.matcher(svg.replaceAll("(?s)<!--.*?-->", ""));
        while (tag.find()) {
            boolean closing = !tag.group(1).isEmpty();
            String name = tag.group(2).toLowerCase(Locale.ROOT);
            int colon = name.indexOf(':');
            if (colon >= 0) name = name.substring(colon + 1);
            String attributes = tag.group(3);
            boolean selfClosing = !tag.group(4).isEmpty();
            // Definitions are not drawn where they stand; what refers to them (use, clip-path) isn't converted.
            if (skipUntil != null) {
                if (closing && name.equals(skipUntil)) skipUntil = null;
                continue;
            }
            if (!closing && (name.equals("defs") || name.equals("clippath") || name.equals("mask") || name.equals("pattern")
                    || name.equals("symbol") || name.equals("lineargradient") || name.equals("radialgradient"))) {
                if (!name.equals("defs")) note(result, name);
                if (!selfClosing) skipUntil = name;
                continue;
            }
            if (!closing) {
                for (String colour : new String[]{attribute(attributes, "fill"), attribute(attributes, "stroke")}) {
                    if (colour != null && !knownColor(colour)) note(result, "colour " + colour.trim());
                }
                if (attribute(attributes, "clip-path") != null || attribute(attributes, "mask") != null) note(result, "clip-path");
            }
            if (closing) {
                if (name.equals("g") && !groupStroke.isEmpty()) groupStroke.remove(groupStroke.size() - 1);
                if (name.equals("g") && !groupFill.isEmpty()) groupFill.remove(groupFill.size() - 1);
                continue;
            }
            switch (name) {
                case "svg": {
                    header = true;
                    groupFill.add(attribute(attributes, "fill"));
                    groupStroke.add(attribute(attributes, "stroke"));
                    String viewBox = attribute(attributes, "viewBox");
                    Float w = number(attribute(attributes, "width"));
                    Float h = number(attribute(attributes, "height"));
                    if (w != null) width = viewportWidth = w;
                    if (h != null) height = viewportHeight = h;
                    if (viewBox != null) {
                        String[] parts = viewBox.trim().split("[\\s,]+");
                        if (parts.length == 4) {
                            Float vw = number(parts[2]);
                            Float vh = number(parts[3]);
                            if (vw != null && vh != null && vw > 0 && vh > 0) {
                                viewportWidth = vw;
                                viewportHeight = vh;
                                if (w == null) width = vw;
                                if (h == null) height = vh;
                            }
                        }
                    }
                    break;
                }
                case "g": {
                    if (!selfClosing) {
                        groupFill.add(attribute(attributes, "fill"));
                        groupStroke.add(attribute(attributes, "stroke"));
                    }
                    if (attribute(attributes, "transform") != null) note(result, "transform");
                    break;
                }
                case "path":
                    appendPath(paths, attribute(attributes, "d"), attributes, inherited(groupFill), inherited(groupStroke));
                    if (attribute(attributes, "transform") != null) note(result, "transform");
                    break;
                case "circle": {
                    float cx = value(attributes, "cx"), cy = value(attributes, "cy"), r = value(attributes, "r");
                    appendPath(paths, ellipse(cx, cy, r, r), attributes, inherited(groupFill), inherited(groupStroke));
                    break;
                }
                case "ellipse": {
                    float cx = value(attributes, "cx"), cy = value(attributes, "cy");
                    appendPath(paths, ellipse(cx, cy, value(attributes, "rx"), value(attributes, "ry")), attributes, inherited(groupFill), inherited(groupStroke));
                    break;
                }
                case "rect":
                    appendPath(paths, rectangle(value(attributes, "x"), value(attributes, "y"), value(attributes, "width"),
                            value(attributes, "height"), value(attributes, "rx"), value(attributes, "ry")), attributes, inherited(groupFill), inherited(groupStroke));
                    break;
                case "polygon":
                case "polyline": {
                    String points = attribute(attributes, "points");
                    if (points != null && !points.trim().isEmpty()) {
                        appendPath(paths, "M " + points.trim() + (name.equals("polygon") ? " Z" : ""), attributes, inherited(groupFill), inherited(groupStroke));
                    }
                    break;
                }
                case "title":
                case "desc":
                case "defs":
                case "metadata":
                case "style":
                    break;
                default:
                    note(result, name);
            }
        }
        if (!header || paths.length() == 0) return result;
        result.xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:width=\"" + trim(width * 2) + "dp\"\n"
                + "    android:height=\"" + trim(height * 2) + "dp\"\n"
                + "    android:viewportWidth=\"" + trim(viewportWidth) + "\"\n"
                + "    android:viewportHeight=\"" + trim(viewportHeight) + "\">\n"
                + paths + "</vector>\n";
        return result;
    }

    private static void note(Result result, String what) {
        if (!result.ignored.contains(what)) result.ignored.add(what);
    }

    private static String inherited(List<String> groupFill) {
        for (int i = groupFill.size() - 1; i >= 0; i--) {
            if (groupFill.get(i) != null) return groupFill.get(i);
        }
        return null;
    }

    private static void appendPath(StringBuilder out, String data, String attributes, String inheritedFill, String inheritedStroke) {
        if (data == null || data.trim().isEmpty()) return;
        String fill = attribute(attributes, "fill");
        if (fill == null) fill = style(attributes, "fill");
        if (fill == null) fill = inheritedFill;
        String stroke = attribute(attributes, "stroke");
        if (stroke == null) stroke = style(attributes, "stroke");
        if (stroke == null) stroke = inheritedStroke;
        out.append("    <path\n        android:pathData=\"").append(escape(data.trim().replaceAll("\\s+", " "))).append('"');
        boolean noFill = fill != null && fill.trim().equalsIgnoreCase("none");
        if (!noFill) out.append("\n        android:fillColor=\"").append(color(fill)).append('"');
        if (stroke != null && !stroke.trim().equalsIgnoreCase("none")) {
            out.append("\n        android:strokeColor=\"").append(color(stroke)).append('"');
            Float strokeWidth = number(attribute(attributes, "stroke-width"));
            out.append("\n        android:strokeWidth=\"").append(trim(strokeWidth == null ? 1 : strokeWidth)).append('"');
        } else if (noFill) {
            // Neither filled nor stroked draws nothing; keep the shape visible like the original converter did.
            out.append("\n        android:fillColor=\"#00000000\"");
        }
        Float opacity = number(attribute(attributes, "opacity"));
        if (opacity == null) opacity = number(attribute(attributes, "fill-opacity"));
        if (opacity != null && opacity < 1f && !noFill) out.append("\n        android:fillAlpha=\"").append(trim(opacity)).append('"');
        if ("evenodd".equalsIgnoreCase(attribute(attributes, "fill-rule"))) out.append("\n        android:fillType=\"evenOdd\"");
        out.append(" />\n");
    }

    private static String style(String attributes, String property) {
        String style = attribute(attributes, "style");
        if (style == null) return null;
        for (String part : style.split(";")) {
            int colon = part.indexOf(':');
            if (colon > 0 && part.substring(0, colon).trim().equalsIgnoreCase(property)) return part.substring(colon + 1).trim();
        }
        return null;
    }

    /** Whether {@link #color} really understands the value (anything else would come out black). */
    static boolean knownColor(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("none") || v.matches("#[0-9a-f]{3}|#[0-9a-f]{6}|#[0-9a-f]{8}") || v.matches("rgba?\\(.*\\)")) return true;
        return v.equals("black") || v.equals("white") || v.equals("red") || v.equals("green") || v.equals("blue")
                || v.equals("gray") || v.equals("grey") || v.equals("transparent");
    }

    /** An SVG colour as an Android colour; black when it isn't a plain colour (as SVG's default fill is). */
    static String color(String value) {
        if (value == null) return "#FF000000";
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.matches("#[0-9a-f]{3}")) {
            return ("#FF" + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2) + v.charAt(3) + v.charAt(3)).toUpperCase(Locale.ROOT);
        }
        if (v.matches("#[0-9a-f]{6}")) return ("#FF" + v.substring(1)).toUpperCase(Locale.ROOT);
        if (v.matches("#[0-9a-f]{8}")) {
            // SVG/CSS writes #RRGGBBAA, Android #AARRGGBB.
            return ("#" + v.substring(7) + v.substring(1, 7)).toUpperCase(Locale.ROOT);
        }
        Matcher rgb = Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([\\d.]+)\\s*)?\\)").matcher(v);
        if (rgb.matches()) {
            int alpha = 255;
            if (rgb.group(4) != null) {
                try {
                    alpha = Math.max(0, Math.min(255, Math.round(Float.parseFloat(rgb.group(4)) * 255)));
                } catch (NumberFormatException ignored) {
                }
            }
            return String.format(Locale.ROOT, "#%02X%02X%02X%02X", alpha, clamp(rgb.group(1)), clamp(rgb.group(2)), clamp(rgb.group(3)));
        }
        switch (v) {
            case "black":
                return "#FF000000";
            case "white":
                return "#FFFFFFFF";
            case "red":
                return "#FFFF0000";
            case "green":
                return "#FF008000";
            case "blue":
                return "#FF0000FF";
            case "gray":
            case "grey":
                return "#FF808080";
            case "transparent":
                return "#00000000";
            default:
                return "#FF000000";
        }
    }

    private static int clamp(String number) {
        return Math.max(0, Math.min(255, Integer.parseInt(number)));
    }

    private static String ellipse(float cx, float cy, float rx, float ry) {
        if (rx <= 0 || ry <= 0) return null;
        return "M " + trim(cx - rx) + "," + trim(cy) + " A " + trim(rx) + "," + trim(ry) + " 0 0 1 " + trim(cx + rx) + "," + trim(cy)
                + " A " + trim(rx) + "," + trim(ry) + " 0 0 1 " + trim(cx - rx) + "," + trim(cy) + " Z";
    }

    private static String rectangle(float x, float y, float w, float h, float rx, float ry) {
        if (w <= 0 || h <= 0) return null;
        if (rx <= 0 && ry <= 0) return "M " + trim(x) + "," + trim(y) + " h " + trim(w) + " v " + trim(h) + " h " + trim(-w) + " Z";
        if (rx <= 0) rx = ry;
        if (ry <= 0) ry = rx;
        rx = Math.min(rx, w / 2);
        ry = Math.min(ry, h / 2);
        String arc = " a " + trim(rx) + "," + trim(ry) + " 0 0 1 ";
        return "M " + trim(x + rx) + "," + trim(y) + " h " + trim(w - 2 * rx) + arc + trim(rx) + "," + trim(ry)
                + " v " + trim(h - 2 * ry) + arc + trim(-rx) + "," + trim(ry) + " h " + trim(-(w - 2 * rx)) + arc + trim(-rx) + "," + trim(-ry)
                + " v " + trim(-(h - 2 * ry)) + arc + trim(rx) + "," + trim(-ry) + " Z";
    }

    private static String attribute(String attributes, String name) {
        Matcher m = ATTRIBUTE.matcher(attributes);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(name)) return m.group(2) != null ? m.group(2) : m.group(3);
        }
        return null;
    }

    private static float value(String attributes, String name) {
        Float number = number(attribute(attributes, name));
        return number == null ? 0 : number;
    }

    private static Float number(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("^\\s*(-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)\\s*(px|dp|pt)?\\s*$").matcher(text);
        if (!m.matches()) return null;
        try {
            return Float.parseFloat(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(float value) {
        if (value == Math.round(value)) return String.valueOf(Math.round(value));
        String text = String.format(Locale.ROOT, "%.4f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
