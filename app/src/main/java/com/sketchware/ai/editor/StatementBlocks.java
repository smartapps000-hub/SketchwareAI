package com.sketchware.ai.editor;

import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v1.8.9: one Java statement of the Java editor turned into the Sketchware block that generates it (setText,
 * setVisible, Toast, intent setScreen / putExtra, StartActivity, Finish Activity, variable set / increase / decrease,
 * setEnable, setClickable, setChecked, set hint, request focus, Activity set title). A candidate is only used when the
 * IDE's own generator ({@code a.a.a.Fx}) writes exactly the statement for it; anything else stays "add source directly".
 * Arguments: string, number and boolean literals, and the activity's variables of the matching type (a variable block).
 * <p>
 * v1.9.1: values written by blocks of their own go into the slot as those blocks (operators, math, text, list, file,
 * map and widget getters: {@link #REPORTERS}), and list / file / map / widget statements become their blocks
 * ({@link #STATEMENTS}). The code a block writes is read from the generator itself (a template made by generating the
 * block with marker values), so nothing here hard-codes the generator's output; the whole statement is still checked
 * against the generator, and a literal {@code true} / {@code false} is a true / false block (an empty slot before).
 */
public final class StatementBlocks {

    /** Code the generator writes for a block list (the first block and its chain). */
    public interface Generator {
        String code(ArrayList<BlockBean> blocks);
    }

    /** Spec (the block's text with its parameter slots) of an opcode as the logic editor's palette writes it. */
    public interface Specs {
        String spec(String opCode);
    }

    /** English specs, assembled like the palette does from the block_* strings (head, parameters + body words, tail). */
    static final Map<String, String> ENGLISH = new HashMap<>();

    static {
        ENGLISH.put("setText", "%m.textview setText %s");
        ENGLISH.put("setHint", "%m.edittext set hint text to %s");
        ENGLISH.put("setVisible", "%m.view setVisible %m.visible");
        ENGLISH.put("setEnable", "%m.view setEnable %b");
        ENGLISH.put("setClickable", "%m.view setClickable %b");
        ENGLISH.put("setChecked", "%m.checkbox setChecked %b");
        ENGLISH.put("requestFocus", "%m.view request focus");
        ENGLISH.put("doToast", "Toast %s");
        ENGLISH.put("intentSetScreen", "%m.intent setScreen %m.activity");
        ENGLISH.put("intentPutExtra", "%m.intent putExtra key %s value %s");
        ENGLISH.put("startActivity", "StartActivity %m.intent");
        ENGLISH.put("finishActivity", "Finish Activity");
        ENGLISH.put("increaseInt", "%m.varInt increase 1");
        ENGLISH.put("decreaseInt", "%m.varInt decrease 1");
        ENGLISH.put("setVarString", "set %m.varStr to %s");
        ENGLISH.put("setVarInt", "set %m.varInt to %d");
        ENGLISH.put("setVarBoolean", "set %m.varBool to %b");
        ENGLISH.put("setTitle", "Activity set title %s");
        ENGLISH.put("getText", "%m.textview getText");   // v1.8.13: a value block inside another block's slot
        ENGLISH.put("true", "true");                      // v1.9.1: the Operator palette's true / false blocks
        ENGLISH.put("false", "false");
        // v1.9.3: the Operator palette's extra blocks (mod.hasrat ExtraBlocks / BlocksHandler specs)
        ENGLISH.put("strParseInteger", "parse int %s");
        ENGLISH.put("toHashCode", "toHashCode %s");
        ENGLISH.put("stringMatches", "%s matches RegExp %s");
        ENGLISH.put("reverse", "reverse %s");
        ENGLISH.put("stringSubSingle", "%s subString %d");
        ENGLISH.put("ternaryString", "%b ? %s : %s");
    }

    /**
     * v1.9.1: value blocks that may fill a slot, with their block type (b boolean, d number, s text). Each writes one
     * expression; which one is read from the generator ({@link #template}).
     */
    static final Map<String, String> REPORTERS = new java.util.LinkedHashMap<>();
    /** v1.9.1: one-line statement blocks matched by the code the generator writes for them (list, file, map, view). */
    static final List<String> STATEMENTS = new ArrayList<>();

    static {
        for (String op : new String[]{"true", "false", "<", ">", "=", "&&", "||", "not", "stringEquals", "stringContains",
                "fileutilisexist", "fileutilisdir", "fileutilisfile", "fileutilStartsWith", "fileutilEndsWith",
                "containListStr", "containListInt", "getChecked", "getEnable", "mapContainKey", "mapIsEmpty", "stringMatches"}) {
            REPORTERS.put(op, "b");
        }
        for (String op : new String[]{"+", "-", "*", "/", "%", "stringLength", "toNumber", "stringIndex", "stringLastIndex",
                "random", "mathPow", "mathMin", "mathMax", "mathSqrt", "mathAbs", "mathRound", "mathCeil", "mathFloor",
                "mathSin", "mathCos", "mathTan", "mathAsin", "mathAcos", "mathAtan", "mathExp", "mathLog", "mathLog10",
                "mathToRadian", "mathToDegree", "mathPi", "mathE", "mathGetDip", "mathGetDisplayWidth",
                "mathGetDisplayHeight", "fileutillength", "lengthList", "getAtListInt", "indexListStr", "indexListInt",
                "mapSize", "seekBarGetProgress", "calendarGetTime", "strParseInteger", "toHashCode"}) {
            REPORTERS.put(op, "d");
        }
        for (String op : new String[]{"toString", "toStringWithDecimal", "toStringFormat", "stringJoin", "stringSub",
                "stringReplace", "stringReplaceFirst", "stringReplaceAll", "trim", "toUpperCase", "toLowerCase",
                "fileutilread", "fileutilGetLastSegmentPath", "getExternalStorageDir", "getPackageDataDir", "getAtListStr",
                "getText", "mapGet", "intentGetString", "reverse", "stringSubSingle", "ternaryString"}) {
            REPORTERS.put(op, "s");
        }
        java.util.Collections.addAll(STATEMENTS, "setVarString", "setVarInt", "setVarBoolean", "setText", "setHint",
                "setEnable", "setClickable", "setChecked", "doToast", "setTitle", "intentPutExtra", "addListStr",
                "addListInt", "insertListStr", "insertListInt", "deleteList", "clearList", "fileutilwrite", "fileutilcopy",
                "fileutilmove", "fileutildelete", "fileutilmakedir", "fileutillistdir", "mapPut", "mapRemoveKey", "mapClear",
                "copyToClipboard", "setAlpha", "setRotate", "setTranslationX", "setTranslationY", "setScaleX", "setScaleY",
                "seekBarSetProgress", "spnSetSelection", "fileSetData", "fileRemoveData");
    }

    /**
     * v1.9.1: blocks whose use makes the build add storage permissions (manifest and a request in onCreate, a.a.a.yq):
     * read ones READ_EXTERNAL_STORAGE, write ones READ and WRITE. Typed code never asked for them, so such a block is
     * only used when the activity already has one of the same kind (the permission is there already); otherwise the
     * line stays code. Permissions are never added silently.
     */
    static final Set<String> READ_FILE_OPS = new java.util.HashSet<>(java.util.Arrays.asList("fileutilread",
            "fileutilisexist", "fileutillistdir", "fileutilisdir", "fileutilisfile", "fileutillength", "fileutilStartsWith",
            "fileutilEndsWith", "getJpegRotate", "setImageFilePath", "fileutilGetLastSegmentPath"));
    static final Set<String> WRITE_FILE_OPS = new java.util.HashSet<>(java.util.Arrays.asList("fileutilwrite",
            "fileutilcopy", "fileutilcopydir", "fileutilmove", "fileutildelete", "fileutilmakedir",
            "resizeBitmapFileRetainRatio", "resizeBitmapFileToSquare", "resizeBitmapFileToCircle",
            "resizeBitmapFileWithRoundedBorder", "cropBitmapFileFromCenter", "rotateBitmapFile", "scaleBitmapFile",
            "skewBitmapFile", "setBitmapFileColorFilter", "setBitmapFileBrightness", "setBitmapFileContrast"));

    /** v1.9.1: the slot kinds of an opcode, in order (the IDE's own table, a.a.a.lq), or null when unknown. */
    public interface Kinds {
        List<String> kinds(String opCode);
    }

    public static final Kinds IDE_KINDS = opCode -> {
        try {
            return a.a.a.lq.a(opCode);
        } catch (Throwable t) {
            return null;
        }
    };

    /** Classes a widget slot accepts (the view's class info must be one of these). */
    private static final Map<String, String> VIEW_KINDS = new HashMap<>();

    static {
        VIEW_KINDS.put("%m.view", "View");
        VIEW_KINDS.put("%m.textview", "TextView");
        VIEW_KINDS.put("%m.edittext", "EditText");
        VIEW_KINDS.put("%m.checkbox", "CompoundButton");
        VIEW_KINDS.put("%m.imageview", "ImageView");
        VIEW_KINDS.put("%m.seekbar", "SeekBar");
        VIEW_KINDS.put("%m.spinner", "Spinner");
        VIEW_KINDS.put("%m.listview", "ListView");
        VIEW_KINDS.put("%m.webview", "WebView");
    }

    /** Component slots: the component type they take (ComponentBean.COMPONENT_TYPE_*). */
    private static final Map<String, Integer> COMPONENT_KINDS = new HashMap<>();

    static {
        COMPONENT_KINDS.put("%m.intent", ComponentBean.COMPONENT_TYPE_INTENT);
        COMPONENT_KINDS.put("%m.file", ComponentBean.COMPONENT_TYPE_SHAREDPREF);
        COMPONENT_KINDS.put("%m.calendar", ComponentBean.COMPONENT_TYPE_CALENDAR);
    }

    /** v1.8.13: "edittext1.getText().toString()" — the getText block's code, used inside setText(…), Toast(…) … */
    private static final Pattern GET_TEXT = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\.getText\\(\\)\\.toString\\(\\)$");

    /** v1.8.13: Material 3 projects write setHint on an outlined field as {@code SketchTextField.hint(field, text)}. */
    static final String M3_HINT_CLASS = "SketchTextField";

    public static final Specs ENGLISH_SPECS = opCode -> ENGLISH.get(opCode);

    private static final Pattern CALL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z]+)\\((.*)\\);$");
    private static final Pattern PLAIN_CALL = Pattern.compile("^([A-Za-z]+)\\((.*)\\);$");
    private static final Pattern ASSIGN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*) = (.+);$");
    private static final Pattern STEP = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)(\\+\\+|--);$");
    private static final Pattern NUMBER = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");
    private static final Pattern DECIMAL_D = Pattern.compile("^-?[0-9]+\\.[0-9]+[dD]$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final Generator generator;
    private final Specs specs;
    private final Map<String, ViewBean> views = new HashMap<>();
    private final Map<String, ComponentBean> components = new HashMap<>();
    /** variable name → Sketchware variable type (0 boolean, 1 number, 2 String, 3 Map) */
    private final Map<String, Integer> variables = new HashMap<>();
    private final Set<String> activities;
    /** v1.9.1: list name → list type (1 number, 2 String, 3 Map), see {@link #useLists}. */
    private final Map<String, Integer> lists = new HashMap<>();
    private Kinds kinds = IDE_KINDS;
    /** v1.9.1: what the activity's blocks already make the build add (storage permissions), see {@link #useExistingBlocks}. */
    private boolean readsFiles, writesFiles;
    /** v1.9.1: a statement matched a file block that wasn't used because of its permission (the save says so). */
    private boolean heldForPermission;
    /** v1.9.1: templates read from the generator, by opcode (a missing entry: not made yet; NONE: no template). */
    private final Map<String, Template> templates = new HashMap<>();
    private static final Template NONE = new Template(null, null, null, null, null);
    /** v1.9.1: bounds the search for one statement (nested values can be split in several ways). */
    private int budget;
    private int depth;

    public StatementBlocks(Generator generator, Specs specs, Collection<ViewBean> views, Collection<ComponentBean> components,
                           List<Pair<Integer, String>> variables, Set<String> activities) {
        this.generator = generator;
        this.specs = specs;
        if (views != null) for (ViewBean view : views) this.views.put(view.id, view);
        if (components != null) for (ComponentBean component : components) this.components.put(component.componentId, component);
        if (variables != null) {
            for (Pair<Integer, String> variable : variables) {
                if (variable.first != null && variable.first >= 0 && variable.first <= 3 && variable.second != null
                        && NAME.matcher(variable.second).matches()) {
                    this.variables.put(variable.second, variable.first);
                }
            }
        }
        this.activities = activities;
    }

    /** v1.9.1: the activity's lists (eC.f: type 1 number, 2 String, 3 Map), for list blocks. */
    public StatementBlocks useLists(List<Pair<Integer, String>> lists) {
        if (lists != null) {
            for (Pair<Integer, String> list : lists) {
                if (list.first != null && list.first >= 1 && list.first <= 3 && list.second != null
                        && NAME.matcher(list.second.trim()).matches()) {
                    this.lists.put(list.second.trim(), list.first);
                }
            }
        }
        return this;
    }

    /** v1.9.1: the opcodes of the activity's blocks (file blocks are only offered when one of their kind is there). */
    public StatementBlocks useExistingBlocks(Collection<String> opCodes) {
        if (opCodes != null) {
            for (String op : opCodes) {
                if (WRITE_FILE_OPS.contains(op)) writesFiles = readsFiles = true;
                else if (READ_FILE_OPS.contains(op)) readsFiles = true;
            }
        }
        return this;
    }

    /** Whether a FileUtil statement stayed code because its block would have added a storage permission. */
    public boolean heldForPermission() {
        return heldForPermission;
    }

    /** Whether using a block of {@code opCode} leaves the build's permissions as they are. */
    boolean addsNothing(String opCode) {
        if (WRITE_FILE_OPS.contains(opCode)) return writesFiles;
        if (READ_FILE_OPS.contains(opCode)) return readsFiles;
        return true;
    }

    /** v1.9.1: where slot kinds come from (tests without the IDE's tables). */
    public StatementBlocks useKinds(Kinds kinds) {
        if (kinds != null) this.kinds = kinds;
        return this;
    }

    /** v1.9.0: a variable the save registers (AutoVariables): its set and variable blocks can be used from now on. */
    void declare(String name, int type) {
        if (type >= 0 && type <= 3 && name != null && NAME.matcher(name).matches()) variables.put(name, type);
    }

    /** v1.9.0: Sketchware type of a variable of the activity (0 boolean, 1 number, 2 String, 3 Map), or null. */
    Integer typeOf(String name) {
        return variables.get(name);
    }

    /**
     * The blocks for one statement (the statement block first, then the variable blocks its parameters use), with ids
     * from {@code nextId}; null when no block writes exactly this statement.
     */
    public ArrayList<BlockBean> map(String statement, int[] nextId) {
        String s = statement.trim();
        budget = 4000;
        depth = 0;
        List<Candidate> candidates = candidates(s);
        for (Candidate c : candidates) {
            int start = nextId[0];
            ArrayList<BlockBean> blocks = c.build(this, nextId);
            if (blocks != null && generates(blocks, s)) return blocks;
            nextId[0] = start;
        }
        // v1.9.1: any statement block of the list whose generated code has the statement's shape
        if (s.endsWith(";") && s.indexOf('\n') < 0) {
            for (String opCode : STATEMENTS) {
                if (!addsNothing(opCode)) {
                    Template held = template(opCode, " ");
                    if (held != null && !held.split(s, this).isEmpty()) heldForPermission = true;
                    continue;
                }
                Template t = template(opCode, " ");
                if (t == null) continue;
                for (String[] holes : t.split(s, this)) {
                    int start = nextId[0];
                    ArrayList<BlockBean> blocks = new ArrayList<>();
                    BlockBean block = t.block(this, nextId);
                    blocks.add(block);
                    if (fill(t, block, holes, blocks, nextId) && generates(blocks, s)) return blocks;
                    nextId[0] = start;
                    if (budget <= 0) return null;
                }
            }
        }
        return null;
    }

    /** Whether the generator writes exactly {@code statement} for {@code blocks}. */
    private boolean generates(ArrayList<BlockBean> blocks, String statement) {
        String code;
        try {
            code = generator.code(blocks);
        } catch (Throwable t) {
            code = null;
        }
        return code != null && code.trim().equals(statement);
    }

    // ------------------------------------------------------------------------------------------- templates (v1.9.1)

    /**
     * The code a block writes, with holes where its slots go: {@code pieces[0] hole pieces[1] hole … pieces[n]}, hole i
     * being slot {@code holes[i]} (the generator may write slots in another order than the block shows them).
     */
    static final class Template {
        final String opCode, type, spec;
        final String[] kinds;
        final String[] pieces;
        final int[] holes;

        Template(String opCode, String type, String spec, String[] kinds, Object[] pieceAndHoles) {
            this.opCode = opCode;
            this.type = type;
            this.spec = spec;
            this.kinds = kinds;
            if (pieceAndHoles == null) {
                pieces = null;
                holes = null;
            } else {
                pieces = (String[]) pieceAndHoles[0];
                holes = (int[]) pieceAndHoles[1];
            }
        }

        BlockBean block(StatementBlocks m, int[] nextId) {
            BlockBean block = new BlockBean(String.valueOf(nextId[0]++), spec, type, "", opCode);
            for (int i = 0; i < kinds.length; i++) block.parameters.add("");
            return block;
        }

        /** The ways {@code text} splits into this template's pieces and slot texts (slot texts by slot index). */
        List<String[]> split(String text, StatementBlocks m) {
            List<String[]> out = new ArrayList<>();
            if (!text.startsWith(pieces[0]) || !text.endsWith(pieces[pieces.length - 1])) return out;
            if (holes.length == 0) {
                if (text.equals(pieces[0])) out.add(new String[0]);
                return out;
            }
            if (text.length() < pieces[0].length() + pieces[pieces.length - 1].length()) return out;
            String[] found = new String[kinds.length];
            splitFrom(text, pieces[0].length(), 0, found, out, m);
            return out;
        }

        private void splitFrom(String text, int at, int hole, String[] found, List<String[]> out, StatementBlocks m) {
            if (out.size() >= 6 || m.budget-- <= 0) return;
            String next = pieces[hole + 1];
            boolean last = hole == holes.length - 1;
            int end = last ? text.length() - next.length() : -1;
            int depth = 0;
            for (int i = at; i <= text.length(); i++) {
                if (depth == 0 && i > at && (last ? i == end : text.startsWith(next, i))) {
                    String part = text.substring(at, i);
                    if (!part.trim().isEmpty() && part.trim().equals(part)) {
                        found[holes[hole]] = part;
                        if (last) {
                            out.add(found.clone());
                        } else {
                            splitFrom(text, i + next.length(), hole + 1, found, out, m);
                        }
                    }
                    if (last) return;
                }
                if (i >= text.length() || (last && i > end)) return;
                char c = text.charAt(i);
                if (c == '"' || c == '\'') {
                    int j = i + 1;
                    while (j < text.length() && text.charAt(j) != c) {
                        if (text.charAt(j) == '\\') j++;
                        j++;
                    }
                    if (j >= text.length()) return;
                    i = j;
                } else if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    if (--depth < 0) return;
                }
            }
        }
    }

    /** The slot kinds in a spec, in order; null when one isn't a plain kind (%s.inputOnly …). */
    static String[] kindsOf(String spec) {
        List<String> out = new ArrayList<>();
        for (String token : spec.split(" ")) {
            // a bare "%" is the modulo block's text, not a slot (mq.a needs two characters)
            if (!token.startsWith("%") || token.length() < 2) continue;
            if (token.equals("%s") || token.equals("%d") || token.equals("%b") || token.matches("%m\\.[A-Za-z]+")) {
                out.add(token);
            } else {
                return null;
            }
        }
        return out.toArray(new String[0]);
    }

    /** The spec of an opcode: the palette's, the English one, or one made of its slot kinds. */
    private String specFor(String opCode) {
        String spec = specs.spec(opCode);
        if (spec == null || spec.isEmpty()) spec = ENGLISH.get(opCode);
        if (spec == null || spec.isEmpty()) {
            List<String> k = kinds.kinds(opCode);
            StringBuilder b = new StringBuilder(opCode);
            if (k != null) for (String x : k) b.append(' ').append(x);
            spec = b.toString();
        }
        return spec;
    }

    /**
     * The template of {@code opCode} (block type {@code type}): the block is generated with marker values in its slots
     * (a value block inside a set block), and the markers are found again in the code. Null when the code isn't one
     * line, a marker isn't there exactly once, or the slots aren't what the IDE's table says.
     */
    Template template(String opCode, String type) {
        Template cached = templates.get(opCode);
        if (cached != null) return cached == NONE ? null : cached;
        Template made = null;
        try {
            made = makeTemplate(opCode, type);
        } catch (Throwable ignored) {
            // no template: the block isn't offered
        }
        templates.put(opCode, made == null ? NONE : made);
        return made;
    }

    private Template makeTemplate(String opCode, String type) {
        String spec = specFor(opCode);
        String[] slotKinds = kindsOf(spec);
        if (slotKinds == null) return null;
        List<String> ide = kinds.kinds(opCode);
        if (ide != null && !ide.equals(java.util.Arrays.asList(slotKinds))) return null;
        ArrayList<BlockBean> blocks = new ArrayList<>();
        String carrierName = "zqcarrierqz";
        BlockBean block = new BlockBean("900001", spec, type, "", opCode);
        String carrierOp = type.equals("b") ? "setVarBoolean" : type.equals("d") ? "setVarInt" : type.equals("s") ? "setVarString" : null;
        if (carrierOp != null) {
            String carrierSpec = specFor(carrierOp);
            BlockBean carrier = new BlockBean("900000", carrierSpec, " ", "", carrierOp);
            carrier.parameters.add(carrierName);
            carrier.parameters.add("@900001");
            blocks.add(carrier);
        }
        blocks.add(block);
        for (int i = 0; i < slotKinds.length; i++) {
            String marker = "zqv" + i + "qz";
            String kind = slotKinds[i];
            if (kind.equals("%s") || kind.equals("%d") || kind.equals("%b")) {
                String id = String.valueOf(900010 + i);
                blocks.add(new BlockBean(id, marker, kind.substring(1), "", "getVar"));
                block.parameters.add("@" + id);
            } else {
                block.parameters.add(marker);
            }
        }
        String code = generator.code(blocks);
        if (code == null) return null;
        code = code.trim();
        if (carrierOp != null) {
            String head = carrierName + " = ";
            if (!code.startsWith(head) || !code.endsWith(";")) return null;
            code = code.substring(head.length(), code.length() - 1);
        }
        if (code.isEmpty() || code.indexOf('\n') >= 0 || code.indexOf('{') >= 0 || code.contains("zqcarrierqz")) return null;
        // the markers, in the order they appear
        List<Integer> order = new ArrayList<>();
        List<Integer> at = new ArrayList<>();
        for (int i = 0; i < slotKinds.length; i++) {
            String marker = "zqv" + i + "qz";
            int first = code.indexOf(marker);
            if (first < 0 || code.indexOf(marker, first + 1) >= 0) return null;
            int k = 0;
            while (k < at.size() && at.get(k) < first) k++;
            at.add(k, first);
            order.add(k, i);
        }
        String[] pieces = new String[order.size() + 1];
        int[] holes = new int[order.size()];
        int from = 0;
        for (int k = 0; k < order.size(); k++) {
            pieces[k] = code.substring(from, at.get(k));
            holes[k] = order.get(k);
            from = at.get(k) + ("zqv" + order.get(k) + "qz").length();
            if (k > 0 && pieces[k].isEmpty()) return null;   // two slots side by side can't be told apart
        }
        pieces[order.size()] = code.substring(from);
        return new Template(opCode, type, spec, slotKinds, new Object[]{pieces, holes});
    }

    /** Fills the block's slots from the slot texts; false (blocks and ids rolled back by the caller) if one can't be. */
    private boolean fill(Template t, BlockBean block, String[] holes, ArrayList<BlockBean> blocks, int[] nextId) {
        for (int i = 0; i < t.kinds.length; i++) {
            if (holes[i] == null) return false;
            String value = parameter(t.kinds[i], holes[i].trim(), blocks, nextId);
            if (value == null) return false;
            block.parameters.set(i, value);
        }
        return true;
    }

    /**
     * A value block (and the blocks in its own slots) whose code is exactly {@code text}, of block type {@code type}:
     * "@<id>", or null. A text wholly in brackets is also tried without them: the generator brackets an operator
     * inside another one ({@code (a + 7) + b}); the whole statement is checked against the generator in the end.
     */
    private String nested(String text, String type, ArrayList<BlockBean> blocks, int[] nextId) {
        if (depth >= 8 || budget <= 0) return null;
        depth++;
        try {
            String value = nestedAs(text, type, blocks, nextId);
            if (value == null && wrapped(text)) value = nestedAs(text.substring(1, text.length() - 1).trim(), type, blocks, nextId);
            return value;
        } finally {
            depth--;
        }
    }

    private String nestedAs(String text, String type, ArrayList<BlockBean> blocks, int[] nextId) {
        for (Map.Entry<String, String> reporter : REPORTERS.entrySet()) {
            if (!reporter.getValue().equals(type)) continue;
            if (!addsNothing(reporter.getKey())) {
                Template held = template(reporter.getKey(), type);
                if (held != null && !held.split(text, this).isEmpty()) heldForPermission = true;
                continue;
            }
            Template t = template(reporter.getKey(), type);
            if (t == null) continue;
            for (String[] holes : t.split(text, this)) {
                int size = blocks.size(), start = nextId[0];
                BlockBean block = t.block(this, nextId);
                blocks.add(block);
                if (fill(t, block, holes, blocks, nextId)) return "@" + block.id;
                while (blocks.size() > size) blocks.remove(blocks.size() - 1);
                nextId[0] = start;
                if (budget <= 0) return null;
            }
        }
        return null;
    }

    /** Whether the text is one bracketed expression: "(…)" with the first bracket closed by the last. */
    static boolean wrapped(String text) {
        if (text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')') return false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != c) {
                    if (text.charAt(j) == '\\') j++;
                    j++;
                }
                i = j;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i < text.length() - 1) return false;
            }
        }
        return depth == 0;
    }

    // ------------------------------------------------------------------------------------------- candidates

    /** opCode, block type, parameter kinds (%m.view, %s, ...) and the Java text of each parameter. */
    private static final class Candidate {
        final String opCode, type;
        final String[] kinds;
        final String[] args;

        Candidate(String opCode, String type, String[] kinds, String... args) {
            this.opCode = opCode;
            this.type = type;
            this.kinds = kinds;
            this.args = args;
        }

        ArrayList<BlockBean> build(StatementBlocks m, int[] nextId) {
            if (kinds.length != args.length) return null;
            String spec = m.specFor(opCode);
            ArrayList<BlockBean> blocks = new ArrayList<>();
            BlockBean block = new BlockBean(String.valueOf(nextId[0]++), spec, type, "", opCode);
            blocks.add(block);
            for (int i = 0; i < kinds.length; i++) {
                String value = m.parameter(kinds[i], args[i].trim(), blocks, nextId);
                if (value == null) return null;
                block.parameters.add(value);
            }
            return blocks;
        }
    }

    private static List<Candidate> candidates(String s) {
        List<Candidate> out = new ArrayList<>();
        Matcher m;
        if (s.equals("finish();")) {
            out.add(new Candidate("finishActivity", "f", new String[0]));
            return out;
        }
        if ((m = STEP.matcher(s)).matches()) {
            out.add(new Candidate(m.group(2).equals("++") ? "increaseInt" : "decreaseInt", " ",
                    new String[]{"%m.varInt"}, m.group(1)));
            return out;
        }
        if ((m = CALL.matcher(s)).matches()) {
            String target = m.group(1), method = m.group(2);
            List<String> args = splitArguments(m.group(3));
            if (args == null) return out;
            if (target.equals("SketchwareUtil") && method.equals("showMessage") && args.size() == 2
                    && args.get(0).trim().equals("getApplicationContext()")) {
                out.add(new Candidate("doToast", " ", new String[]{"%s"}, args.get(1)));
                return out;
            }
            if (target.equals(M3_HINT_CLASS) && method.equals("hint") && args.size() == 2) {
                out.add(new Candidate("setHint", " ", new String[]{"%m.edittext", "%s"}, args.get(0), args.get(1)));
                return out;
            }
            switch (method) {
                case "setText":
                    if (args.size() == 1) out.add(new Candidate("setText", " ", new String[]{"%m.textview", "%s"}, target, args.get(0)));
                    break;
                case "setHint":
                    if (args.size() == 1) out.add(new Candidate("setHint", " ", new String[]{"%m.edittext", "%s"}, target, args.get(0)));
                    break;
                case "setVisibility":
                    if (args.size() == 1 && args.get(0).trim().startsWith("View.")) {
                        out.add(new Candidate("setVisible", " ", new String[]{"%m.view", "%m.visible"}, target,
                                args.get(0).trim().substring(5)));
                    }
                    break;
                case "setEnabled":
                    if (args.size() == 1) out.add(new Candidate("setEnable", " ", new String[]{"%m.view", "%b"}, target, args.get(0)));
                    break;
                case "setClickable":
                    if (args.size() == 1) out.add(new Candidate("setClickable", " ", new String[]{"%m.view", "%b"}, target, args.get(0)));
                    break;
                case "setChecked":
                    if (args.size() == 1) out.add(new Candidate("setChecked", " ", new String[]{"%m.checkbox", "%b"}, target, args.get(0)));
                    break;
                case "requestFocus":
                    if (args.isEmpty()) out.add(new Candidate("requestFocus", " ", new String[]{"%m.view"}, target));
                    break;
                case "setClass":
                    if (args.size() == 2 && args.get(0).trim().equals("getApplicationContext()") && args.get(1).trim().endsWith(".class")) {
                        String activity = args.get(1).trim();
                        out.add(new Candidate("intentSetScreen", " ", new String[]{"%m.intent", "%m.activity"}, target,
                                activity.substring(0, activity.length() - 6)));
                    }
                    break;
                case "putExtra":
                    if (args.size() == 2) out.add(new Candidate("intentPutExtra", " ", new String[]{"%m.intent", "%s", "%s"}, target, args.get(0), args.get(1)));
                    break;
                default:
                    break;
            }
            return out;
        }
        if ((m = PLAIN_CALL.matcher(s)).matches()) {
            List<String> args = splitArguments(m.group(2));
            if (args == null) return out;
            if (m.group(1).equals("startActivity") && args.size() == 1) {
                out.add(new Candidate("startActivity", " ", new String[]{"%m.intent"}, args.get(0)));
            } else if (m.group(1).equals("setTitle") && args.size() == 1) {
                out.add(new Candidate("setTitle", " ", new String[]{"%s"}, args.get(0)));
            }
            return out;
        }
        if ((m = ASSIGN.matcher(s)).matches()) {
            String name = m.group(1), value = m.group(2);
            out.add(new Candidate("setVarString", " ", new String[]{"%m.varStr", "%s"}, name, value));
            out.add(new Candidate("setVarInt", " ", new String[]{"%m.varInt", "%d"}, name, value));
            out.add(new Candidate("setVarBoolean", " ", new String[]{"%m.varBool", "%b"}, name, value));
        }
        return out;
    }

    private static final Pattern TOAST = Pattern.compile("^(?:android\\.widget\\.)?Toast\\.makeText\\((.*)\\)\\.show\\(\\);$");
    private static final Pattern THIS_CONTEXT = Pattern.compile("^(?:[A-Z][A-Za-z0-9_]*\\.)?this$");

    /**
     * v1.9.1: a statement that does exactly what a block's code does, written as that code, or null. Only
     * {@code Toast.makeText(context, text, Toast.LENGTH_SHORT).show();}: the Toast block's
     * {@code SketchwareUtil.showMessage(getApplicationContext(), text)} makes the same short toast. The text must be a
     * String (the caller maps the result to the Toast block, whose slot only takes text), so an int resource id or a
     * long toast stays code.
     */
    public static String equivalent(String statement) {
        Matcher m = TOAST.matcher(statement.trim());
        if (!m.matches()) return null;
        List<String> args = splitArguments(m.group(1));
        if (args == null || args.size() != 3) return null;
        String context = args.get(0).trim(), duration = args.get(2).trim();
        if (!context.equals("getApplicationContext()") && !context.equals("getBaseContext()")
                && !THIS_CONTEXT.matcher(context).matches()) {
            return null;
        }
        if (!duration.equals("Toast.LENGTH_SHORT") && !duration.equals("android.widget.Toast.LENGTH_SHORT")) return null;
        return "SketchwareUtil.showMessage(getApplicationContext(), " + args.get(1).trim() + ");";
    }

    /**
     * v1.9.1: blocks the Java editor saved in v1.8.9-v1.9.0 hold a bare true / false in a boolean slot, which the logic
     * editor shows as an empty slot (the owner's "set Boolean isNative37 to [ ]"). They get a true / false block there
     * instead; the generated code is the same. Returns how many slots changed.
     */
    public static int literalBooleansToBlocks(ArrayList<BlockBean> blocks, Specs specs) {
        if (blocks == null || blocks.isEmpty()) return 0;
        int next = 0;
        for (BlockBean block : blocks) {
            try {
                next = Math.max(next, Integer.parseInt(block.id) + 1);
            } catch (NumberFormatException ignored) {
                // ids are numbers in the editor; another one doesn't take part
            }
        }
        List<BlockBean> added = new ArrayList<>();
        for (BlockBean block : blocks) {
            List<a.a.a.Gx> info;
            try {
                info = block.getParamClassInfo();
            } catch (Throwable t) {
                continue;
            }
            if (info == null || block.parameters == null) continue;
            for (int i = 0; i < block.parameters.size() && i < info.size(); i++) {
                String value = block.parameters.get(i);
                if (!"true".equals(value) && !"false".equals(value)) continue;
                a.a.a.Gx slot = info.get(i);
                if (slot == null || !slot.b("boolean")) continue;
                String spec = null;
                try {
                    if (specs != null) spec = specs.spec(value);
                } catch (Throwable ignored) {
                    // the English one below
                }
                if (spec == null || spec.isEmpty()) spec = value;
                BlockBean literal = new BlockBean(String.valueOf(next++), spec, "b", "", value);
                added.add(literal);
                block.parameters.set(i, "@" + literal.id);
            }
        }
        blocks.addAll(added);
        return added.size();
    }

    /** Top-level arguments of a call (commas inside strings, characters and brackets kept); null if unbalanced. */
    static List<String> splitArguments(String text) {
        List<String> out = new ArrayList<>();
        if (text.trim().isEmpty()) return out;
        int depth = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != c) {
                    if (text.charAt(j) == '\\') j++;
                    j++;
                }
                if (j >= text.length()) return null;
                i = j;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth < 0) return null;
            } else if (c == ',' && depth == 0) {
                out.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (depth != 0) return null;
        out.add(text.substring(start));
        return out;
    }

    // ------------------------------------------------------------------------------------------- parameters

    /** The stored parameter for Java text {@code arg} in a slot of kind {@code kind}, or null when it can't be one. */
    private String parameter(String kind, String arg, ArrayList<BlockBean> blocks, int[] nextId) {
        switch (kind) {
            case "%s": {
                if (isStringLiteral(arg)) return unescape(arg.substring(1, arg.length() - 1));
                String variable = variable(arg, 2, "s", blocks, nextId);
                if (variable == null) variable = expression(arg, blocks, nextId);
                return variable != null ? variable : nested(arg, "s", blocks, nextId);
            }
            case "%d": {
                if (NUMBER.matcher(arg).matches()) return arg;
                // v1.9.0: the generator writes a decimal number as 2.5d: typed so, the block stores 2.5
                if (DECIMAL_D.matcher(arg).matches()) return arg.substring(0, arg.length() - 1);
                String variable = variable(arg, 1, "d", blocks, nextId);
                return variable != null ? variable : nested(arg, "d", blocks, nextId);
            }
            case "%b": {
                // v1.9.1: true / false are blocks of their own; a word in the slot showed as an empty slot
                String variable = variable(arg, 0, "b", blocks, nextId);
                return variable != null ? variable : nested(arg, "b", blocks, nextId);
            }
            case "%m.visible":
                return arg.equals("VISIBLE") || arg.equals("INVISIBLE") || arg.equals("GONE") ? arg : null;
            case "%m.list":
                return lists.containsKey(arg) ? arg : null;
            case "%m.listInt":
                return Integer.valueOf(1).equals(lists.get(arg)) ? arg : null;
            case "%m.listStr":
                return Integer.valueOf(2).equals(lists.get(arg)) ? arg : null;
            case "%m.listMap":
                return Integer.valueOf(3).equals(lists.get(arg)) ? arg : null;
            case "%m.varMap":
                return Integer.valueOf(3).equals(variables.get(arg)) ? arg : null;
            case "%m.activity":
                return activities != null && activities.contains(arg) ? arg : null;
            case "%m.varStr":
                return Integer.valueOf(2).equals(variables.get(arg)) ? arg : null;
            case "%m.varInt":
                return Integer.valueOf(1).equals(variables.get(arg)) ? arg : null;
            case "%m.varBool":
                return Integer.valueOf(0).equals(variables.get(arg)) ? arg : null;
            default: {
                String viewClass = VIEW_KINDS.get(kind);
                if (viewClass != null) return view(arg, viewClass);
                Integer componentType = COMPONENT_KINDS.get(kind);
                if (componentType != null) {
                    ComponentBean component = components.get(arg);
                    return component != null && component.type == componentType ? arg : null;
                }
                return null;
            }
        }
    }

    private String view(String id, String classInfo) {
        ViewBean view = views.get(id);
        if (view == null) return null;
        try {
            return view.getClassInfo().a(classInfo) ? id : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * v1.8.13: a string value written by a block of its own, put in the slot as "@<id>": {@code X.getText().toString()}
     * is the getText block of TextView / EditText X. The whole statement is still checked against the generator.
     */
    private String expression(String arg, ArrayList<BlockBean> blocks, int[] nextId) {
        Matcher m = GET_TEXT.matcher(arg);
        if (!m.matches()) return null;
        String view = view(m.group(1), "TextView");
        if (view == null) return null;
        String spec = specs.spec("getText");
        if (spec == null || spec.isEmpty()) spec = ENGLISH.get("getText");
        BlockBean getter = new BlockBean(String.valueOf(nextId[0]++), spec, "s", "", "getText");
        getter.parameters.add(view);
        blocks.add(getter);
        return "@" + getter.id;
    }

    /** A variable block ("getVar") for a variable of the activity of that type: "@<id>". */
    private String variable(String name, int variableType, String blockType, ArrayList<BlockBean> blocks, int[] nextId) {
        if (!NAME.matcher(name).matches() || !Integer.valueOf(variableType).equals(variables.get(name))) return null;
        BlockBean getter = new BlockBean(String.valueOf(nextId[0]++), name, blockType, "", "getVar");
        blocks.add(getter);
        return "@" + getter.id;
    }

    static boolean isStringLiteral(String s) {
        if (s.length() < 2 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"') return false;
        for (int i = 1; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return false;
            }
        }
        return true;
    }

    /** Java string escapes back to text (the generator escapes them again; the comparison catches any difference). */
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n':
                        out.append("\\n");
                        break;
                    case 't':
                        out.append("\\t");
                        break;
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    default:
                        out.append('\\').append(n);
                        break;
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
