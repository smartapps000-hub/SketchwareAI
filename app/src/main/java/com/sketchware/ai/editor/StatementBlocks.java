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
    }

    public static final Specs ENGLISH_SPECS = opCode -> ENGLISH.get(opCode);

    private static final Pattern CALL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z]+)\\((.*)\\);$");
    private static final Pattern PLAIN_CALL = Pattern.compile("^([A-Za-z]+)\\((.*)\\);$");
    private static final Pattern ASSIGN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*) = (.+);$");
    private static final Pattern STEP = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)(\\+\\+|--);$");
    private static final Pattern NUMBER = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final Generator generator;
    private final Specs specs;
    private final Map<String, ViewBean> views = new HashMap<>();
    private final Map<String, ComponentBean> components = new HashMap<>();
    /** variable name → Sketchware variable type (0 boolean, 1 number, 2 String, 3 Map) */
    private final Map<String, Integer> variables = new HashMap<>();
    private final Set<String> activities;

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

    /**
     * The blocks for one statement (the statement block first, then the variable blocks its parameters use), with ids
     * from {@code nextId}; null when no block writes exactly this statement.
     */
    public ArrayList<BlockBean> map(String statement, int[] nextId) {
        String s = statement.trim();
        List<Candidate> candidates = candidates(s);
        for (Candidate c : candidates) {
            int start = nextId[0];
            ArrayList<BlockBean> blocks = c.build(this, nextId);
            if (blocks == null) {
                nextId[0] = start;
                continue;
            }
            String code;
            try {
                code = generator.code(blocks);
            } catch (Throwable t) {
                code = null;
            }
            if (code != null && code.trim().equals(s)) return blocks;
            nextId[0] = start;
        }
        return null;
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
            String spec = m.specs.spec(opCode);
            if (spec == null || spec.isEmpty()) spec = ENGLISH.get(opCode);
            if (spec == null) return null;
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
            case "%s":
                if (isStringLiteral(arg)) return unescape(arg.substring(1, arg.length() - 1));
                return variable(arg, 2, "s", blocks, nextId);
            case "%d":
                if (NUMBER.matcher(arg).matches()) return arg;
                return variable(arg, 1, "d", blocks, nextId);
            case "%b":
                if (arg.equals("true") || arg.equals("false")) return arg;
                return variable(arg, 0, "b", blocks, nextId);
            case "%m.view":
                return view(arg, "View");
            case "%m.textview":
                return view(arg, "TextView");
            case "%m.edittext":
                return view(arg, "EditText");
            case "%m.checkbox":
                return view(arg, "CompoundButton");
            case "%m.visible":
                return arg.equals("VISIBLE") || arg.equals("INVISIBLE") || arg.equals("GONE") ? arg : null;
            case "%m.intent": {
                ComponentBean component = components.get(arg);
                return component != null && component.type == ComponentBean.COMPONENT_TYPE_INTENT ? arg : null;
            }
            case "%m.activity":
                return activities != null && activities.contains(arg) ? arg : null;
            case "%m.varStr":
                return Integer.valueOf(2).equals(variables.get(arg)) ? arg : null;
            case "%m.varInt":
                return Integer.valueOf(1).equals(variables.get(arg)) ? arg : null;
            case "%m.varBool":
                return Integer.valueOf(0).equals(variables.get(arg)) ? arg : null;
            default:
                return null;
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

    /** A variable block ("getVar") for a variable of the activity of that type: "@<id>". */
    private String variable(String name, int variableType, String blockType, ArrayList<BlockBean> blocks, int[] nextId) {
        if (!NAME.matcher(name).matches() || !Integer.valueOf(variableType).equals(variables.get(name))) return null;
        BlockBean getter = new BlockBean(String.valueOf(nextId[0]++), name, blockType, "", "getVar");
        blocks.add(getter);
        return "@" + getter.id;
    }

    private static boolean isStringLiteral(String s) {
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
