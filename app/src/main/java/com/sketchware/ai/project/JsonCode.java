package com.sketchware.ai.project;

/**
 * v2.1.5 (owner's 23 Nov directive: "JSON blocks, a power pack"): blocks that read and change JSON text by a path
 * ("user.name", "items[0].title", "items[]") and fill lists and maps from it. The blocks call the generated helper
 * {@code SketchJson} (resource {@code SketchJson.java.txt} next to this class: Java 7, min SDK 19, ASCII), which uses
 * Android's own org.json: the app gets no library and no permission. The older Gson blocks stay as they are.
 */
public final class JsonCode {

    public static final String CLASS_NAME = "SketchJson";
    public static final String FILE_NAME = CLASS_NAME + ".java";
    /** The blocks' colour (the operators' JSON green). */
    public static final String COLOR = "#5cb722";

    /**
     * The drawer's order: {opCode, kind (" " command, "s" text, "d" number, "b" boolean), code, spec}. Setters change a
     * String variable in place; readers are value blocks.
     */
    public static final String[][] BLOCKS = {
            {"sketchJsonGetText", "s", "SketchJson.getText(%1$s, %2$s)", "JSON %s text at %s"},
            {"sketchJsonGetNumber", "d", "SketchJson.getNumber(%1$s, %2$s)", "JSON %s number at %s"},
            {"sketchJsonGetBoolean", "b", "SketchJson.getBoolean(%1$s, %2$s)", "JSON %s boolean at %s"},
            {"sketchJsonGetJson", "s", "SketchJson.getJson(%1$s, %2$s)", "JSON %s part at %s"},
            {"sketchJsonHas", "b", "SketchJson.has(%1$s, %2$s)", "JSON %s has %s"},
            {"sketchJsonCount", "d", "SketchJson.count(%1$s, %2$s)", "JSON %s count at %s"},
            {"sketchJsonType", "s", "SketchJson.type(%1$s, %2$s)", "JSON %s type at %s"},
            {"sketchJsonIsValid", "b", "SketchJson.isValid(%s)", "%s is valid JSON"},
            {"sketchJsonPretty", "s", "SketchJson.pretty(%s)", "pretty JSON %s"},
            {"sketchJsonSetText", " ", "%1$s = SketchJson.setText(%1$s, %2$s, %3$s);", "JSON %m.varStr set %s to text %s"},
            {"sketchJsonSetNumber", " ", "%1$s = SketchJson.setNumber(%1$s, %2$s, %3$s);",
                    "JSON %m.varStr set %s to number %d"},
            {"sketchJsonSetBoolean", " ", "%1$s = SketchJson.setBoolean(%1$s, %2$s, %3$s);", "JSON %m.varStr set %s to %b"},
            {"sketchJsonSetJson", " ", "%1$s = SketchJson.setJson(%1$s, %2$s, %3$s);", "JSON %m.varStr set %s to JSON %s"},
            {"sketchJsonRemove", " ", "%1$s = SketchJson.remove(%1$s, %2$s);", "JSON %m.varStr remove %s"},
            {"sketchJsonKeys", " ", "SketchJson.keys(%1$s, %2$s, %3$s);", "JSON %s keys at %s to %m.listStr"},
            {"sketchJsonToTextList", " ", "SketchJson.toTextList(%1$s, %2$s, %3$s);", "JSON %s array at %s to %m.listStr"},
            {"sketchJsonToNumberList", " ", "SketchJson.toNumberList(%1$s, %2$s, %3$s);",
                    "JSON %s array at %s to %m.listInt"},
            {"sketchJsonToMapList", " ", "SketchJson.toMapList(%1$s, %2$s, %3$s);", "JSON %s array at %s to %m.listMap"},
            {"sketchJsonToMap", " ", "SketchJson.toMap(%1$s, %2$s, %3$s);", "JSON %s object at %s to %m.varMap"},
    };

    private JsonCode() {
    }

    /** The opCodes in the drawer's order (the guide's section). */
    public static String[] opCodes() {
        String[] out = new String[BLOCKS.length];
        for (int i = 0; i < BLOCKS.length; i++) out[i] = BLOCKS[i][0];
        return out;
    }

    /** The kind of one of these blocks, else null. */
    public static String kind(String opCode) {
        for (String[] b : BLOCKS) if (b[0].equals(opCode)) return b[1];
        return null;
    }

    public static boolean isJsonBlock(String opCode) {
        return kind(opCode) != null;
    }

    /** Source of {@code <package>.SketchJson}. */
    public static String helperClass(String packageName) {
        String template;
        try {
            template = template();
        } catch (IllegalStateException e) {
            // an IDE without the resource: only a project that uses SketchJson fails, at compile time, with this text
            return "package " + packageName + ";\n\n/** " + e.getMessage() + ": reinstall Sketchware AI. */\npublic class "
                    + CLASS_NAME + " {\n}\n";
        }
        return template.replace("package __PACKAGE__;", "package " + packageName + ";");
    }

    private static String template;

    private static synchronized String template() {
        if (template == null) {
            java.io.InputStream in = JsonCode.class.getResourceAsStream(CLASS_NAME + ".java.txt");
            if (in == null) throw new IllegalStateException(CLASS_NAME + ".java.txt missing from the IDE");
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                template = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(CLASS_NAME + ".java.txt not readable", e);
            } finally {
                try {
                    in.close();
                } catch (java.io.IOException ignored) {
                }
            }
        }
        return template;
    }
}
