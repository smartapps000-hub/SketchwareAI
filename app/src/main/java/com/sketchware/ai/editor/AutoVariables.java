package com.sketchware.ai.editor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v1.9.0: variables a Java editor save registers in the project by itself.
 * <ul>
 * <li>A local declaration typed as a statement of its own in an event body ({@code String name = "Ravi";}) becomes a
 * variable of the activity: String, double and boolean ones a String / Number / Boolean variable whose set block
 * writes the statement ({@code name = "Ravi";}); any other type (int, Intent, a class of a library ...) a custom
 * variable with exactly that type, its assignment kept as code. An int is not made a Number variable: Sketchware's
 * Number is a double, so {@code String.valueOf(count)} would show "1.0" and {@code list.get(count)} would no longer
 * compile.</li>
 * <li>An assignment to a name declared nowhere ({@code total = 0;}) registers the variable with the type its value
 * shows (string literal or text: String; number: Number; true/false or a comparison: Boolean).</li>
 * </ul>
 * A local becomes a field only where that can't change what the code does: declared once in the whole class, not
 * used before its declaration, not used by a listener or lambda written after it (those would read the field's later
 * value instead of the value they captured), not in a MoreBlock that calls itself, and with a name no widget,
 * component, variable, list or method of the activity has. Everything else stays code, as before.
 */
final class AutoVariables {

    static final int BOOLEAN = 0;
    static final int NUMBER = 1;
    static final int STRING = 2;
    static final int CUSTOM = 6;

    /** A variable registered by a save. */
    static final class Variable {
        final String name;
        final int kind;
        /** The Java type as declared (custom variables) or the Sketchware type's Java type. */
        final String javaType;

        Variable(String name, int kind, String javaType) {
            this.name = name;
            this.kind = kind;
            this.javaType = javaType;
        }

        /** What the project stores (eC.e): the name, or a custom variable's declaration without ';'. */
        String stored() {
            return kind == CUSTOM ? "private " + javaType + " " + name : name;
        }

        /** The field line the generator writes for it (Jx: Lx.a(mq.c(type), name, PRIVATE); custom: stored + ";"). */
        String fieldLine() {
            switch (kind) {
                case BOOLEAN:
                    return "private boolean " + name + " = false;";
                case NUMBER:
                    return "private double " + name + " = 0;";
                case STRING:
                    return "private String " + name + " = \"\";";
                default:
                    return stored() + ";";
            }
        }

        String label() {
            switch (kind) {
                case BOOLEAN:
                    return "Boolean";
                case NUMBER:
                    return "Number";
                case STRING:
                    return "String";
                default:
                    return "custom " + javaType;
            }
        }
    }

    /** A statement line that changes: the line to store instead, or null when the line goes away. */
    static final class Rewrite {
        final Variable variable;
        final String statement;

        Rewrite(Variable variable, String statement) {
            this.variable = variable;
            this.statement = statement;
        }
    }

    /** A local declaration statement: {@code [final] Type name [= value];}. */
    static final class Declaration {
        String type;
        String name;
        /** null when declared without a value. */
        String value;
    }

    // Java identifiers, not only ASCII (Telugu/Hindi names have combining marks)
    private static final String ID = "[\\p{L}\\p{Nl}_$][\\p{L}\\p{M}\\p{N}\\p{Pc}_$]*";
    static final Pattern NAME = Pattern.compile(ID);
    private static final Pattern ASSIGNMENT = Pattern.compile("^(" + ID + ")\\s*=(?!=)\\s*(.+);$");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");
    private static final Pattern GET_TEXT = Pattern.compile("^" + ID + "\\.getText\\(\\)\\.toString\\(\\)$");
    /** An anonymous class opened on a line: new View.OnClickListener() { */
    private static final Pattern ANONYMOUS = Pattern.compile("\\bnew\\s+" + ID + "(?:\\s*\\.\\s*" + ID + ")*\\s*(?:<[^;]*>)?\\s*\\([^;]*\\)\\s*\\{");
    /** The same with its '{' on the next line: new Runnable() */
    private static final Pattern ANONYMOUS_HEAD = Pattern.compile("\\bnew\\s+" + ID + "(?:\\s*\\.\\s*" + ID + ")*\\s*(?:<[^;]*>)?\\s*\\([^;]*\\)\\s*$");
    /** A local class, interface, enum or record declared in a method. */
    private static final Pattern LOCAL_TYPE = Pattern.compile("^(?:(?:final|abstract|static|strictfp)\\s+)*(?:class|interface|enum|record)\\s+" + ID);
    /** A method call: a name followed by '('. */
    private static final Pattern CALL = Pattern.compile("(" + ID + ")\\s*\\(");

    /** How the code a local is declared in can run again while it runs (see {@link #hazard}). */
    static final int REENTRY_NONE = 0;
    /** An event a view fires again when its own code changes it (onTextChanged, onCheckedChange …), or a MoreBlock. */
    static final int REENTRY_LISTENER = 1;
    /** Any other event (onClick …): only performClick runs it again. */
    static final int REENTRY_CLICK = 2;

    /** Calls that fire a view's change listener at once (setText fires onTextChanged before it returns …). */
    private static final Set<String> REFIRING = new HashSet<>(Arrays.asList(
            "setText", "append", "clear", "setChecked", "toggle", "check", "clearCheck", "setProgress",
            "incrementProgressBy", "setRating", "setSelection", "setCurrentItem", "selectTab", "setDate", "updateDate",
            "setHour", "setMinute", "setCurrentHour", "setCurrentMinute", "scrollTo", "scrollBy", "smoothScrollTo",
            "smoothScrollBy", "scrollToPosition", "smoothScrollToPosition", "setQuery", "setSelectedItemId",
            "performClick", "callOnClick"));
    /** Change events whose names say "changed / selected / scrolled" but that no setter of the activity fires. */
    private static final Set<String> NOT_REFIRED = new HashSet<>(Arrays.asList(
            "onSensorChanged", "onAccuracyChanged", "onLocationChanged", "onChildChanged", "onOptionsItemSelected",
            "onContextItemSelected", "onLetterSelected"));

    /** Words a declaration pattern can't take as a type: "return name;", "else name = 1;" are no declarations. */
    private static final Set<String> NOT_TYPES = new HashSet<>(Arrays.asList(
            "return", "throw", "new", "case", "else", "yield", "assert", "goto", "package", "import", "extends",
            "implements", "throws", "class", "interface", "enum", "break", "continue", "do", "this", "super", "default"));

    private AutoVariables() {
    }

    /**
     * The local declaration a statement line is (one declarator, no modifiers but final, not a C-style array, not
     * an array initialiser, not {@code var}), or null.
     */
    static Declaration declaration(String statement) {
        String text = statement.trim();
        if (!text.endsWith(";")) return null;
        text = text.substring(0, text.length() - 1).trim();
        if (text.startsWith("final ")) text = text.substring(6).trim();
        if (!JavaToBlocks.isFieldDeclaration(text)) return null;
        int equals = topLevelAssignment(text);
        String head = (equals < 0 ? text : text.substring(0, equals)).trim();
        String value = equals < 0 ? null : text.substring(equals + 1).trim();
        Matcher m = JavaToBlocks.FIELD_HEAD.matcher(head);
        if (!m.matches()) return null;
        if (!m.group(1).trim().isEmpty()) return null;            // static, private ...: not a local
        String name = m.group(3);
        if (!head.endsWith(name)) return null;                     // int a[] = ...
        if (value != null && (value.isEmpty() || value.startsWith("{"))) return null;   // an array initialiser
        Declaration d = new Declaration();
        d.type = m.group(2).replaceAll("\\s+", " ").trim();
        d.name = name;
        d.value = value;
        return d;
    }

    /** {name, value} of {@code name = value;}, or null. */
    static String[] assignment(String statement) {
        Matcher m = ASSIGNMENT.matcher(statement.trim());
        if (!m.matches()) return null;
        String value = m.group(2).trim();
        if (topLevelAssignment(value) >= 0) return null;          // a = b = 1
        return new String[]{m.group(1), value};
    }

    private static final Pattern DECIMAL = Pattern.compile("^-?[0-9]+\\.[0-9]+$");

    /**
     * The assignment a registered variable's statement becomes. A Number set block writes a decimal number with a
     * {@code d} suffix ({@code total = 2.5d;}, the same double as 2.5), so a decimal literal gets it here too: otherwise
     * the block couldn't write the line exactly and it would stay code.
     */
    static String assignmentStatement(String name, int kind, String value) {
        String v = value.trim();
        if (kind == NUMBER && DECIMAL.matcher(v).matches()) v = v + "d";
        return name + " = " + v + ";";
    }

    /** Sketchware kind for a declared Java type: String, double, boolean; anything else is a custom variable. */
    static int kindOf(String type) {
        switch (type) {
            case "String":
            case "java.lang.String":
                return STRING;
            case "double":
                return NUMBER;
            case "boolean":
                return BOOLEAN;
            default:
                return CUSTOM;
        }
    }

    /** A custom variable's type as stored: generics without spaces (the variable palette splits the text at spaces). */
    static String storedType(String type) {
        StringBuilder out = new StringBuilder();
        int angle = 0;
        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<') angle++;
            else if (c == '>') angle--;
            if (Character.isWhitespace(c) && (angle > 0 || i + 1 < type.length() && (type.charAt(i + 1) == '<'
                    || type.charAt(i + 1) == '[') || i > 0 && type.charAt(i - 1) == '>')) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * The Sketchware kind a value shows, or -1 when it doesn't say: string literal, text of a field, String.valueOf,
     * a concatenation with a string literal, a call that returns text (toString, trim ...): String; a number or
     * arithmetic of numbers and Number variables, Double.parseDouble: Number; true/false, a comparison, !x, equals /
     * isEmpty / contains / isChecked ...: Boolean; a variable: its kind.
     */
    static int infer(String value, Map<String, Integer> known) {
        String v = value.trim();
        if (v.isEmpty()) return -1;
        if (StatementBlocks.isStringLiteral(v) || GET_TEXT.matcher(v).matches()) return STRING;
        if (NUMBER_LITERAL.matcher(v).matches()) return NUMBER;
        if (v.equals("true") || v.equals("false")) return BOOLEAN;
        if (NAME.matcher(v).matches()) {
            Integer k = known.get(v);
            return k != null && k >= 0 && k <= 2 ? k : -1;
        }
        if (v.startsWith("new ")) return -1;
        if (wholeCall(v, "String.valueOf(")) return STRING;
        if (wholeCall(v, "Double.parseDouble(") || wholeCall(v, "Double.valueOf(")) return NUMBER;
        List<String> sums = split(v, "+");
        if (sums != null && sums.size() > 1) {
            for (String operand : sums) if (StatementBlocks.isStringLiteral(operand.trim())) return STRING;
        }
        if (comparison(v)) return BOOLEAN;
        if (v.startsWith("!") && !v.startsWith("!=")) return BOOLEAN;
        String last = lastCall(v);
        if (last != null) {
            switch (last) {
                case "toString":
                case "trim":
                case "toUpperCase":
                case "toLowerCase":
                case "substring":
                    return STRING;
                case "equals":
                case "equalsIgnoreCase":
                case "isEmpty":
                case "contains":
                case "startsWith":
                case "endsWith":
                case "isChecked":
                    return BOOLEAN;
                default:
                    break;
            }
        }
        // arithmetic of numbers and Number variables
        List<String> terms = split(v, "+-*/%");
        if (terms != null && terms.size() > 1) {
            for (String term : terms) {
                String t = term.trim();
                if (t.startsWith("(") && t.endsWith(")")) t = t.substring(1, t.length() - 1).trim();
                boolean number = NUMBER_LITERAL.matcher(t).matches() || NAME.matcher(t).matches()
                        && Integer.valueOf(NUMBER).equals(known.get(t));
                if (!number) return -1;
            }
            return NUMBER;
        }
        return -1;
    }

    private static boolean wholeCall(String v, String prefix) {
        return v.startsWith(prefix) && v.endsWith(")") && matching(v, prefix.length() - 1) == v.length() - 1;
    }

    /** Index of the bracket closing the one at {@code open}, strings skipped; -1 if none. */
    private static int matching(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(s, i);
                if (i < 0) return -1;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int skipLiteral(String s, int at) {
        char quote = s.charAt(at);
        int j = at + 1;
        while (j < s.length() && s.charAt(j) != quote) {
            if (s.charAt(j) == '\\') j++;
            j++;
        }
        return j < s.length() ? j : -1;
    }

    /**
     * The value split at top-level operators of {@code ops} (outside brackets and literals); null when brackets don't
     * balance. "++"/"--" and a sign at the start of an operand don't split.
     */
    private static List<String> split(String v, String ops) {
        List<String> out = new java.util.ArrayList<>();
        int depth = 0, from = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(v, i);
                if (i < 0) return null;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth < 0) return null;
            } else if (depth == 0 && ops.indexOf(c) >= 0) {
                boolean doubled = i + 1 < v.length() && v.charAt(i + 1) == c && (c == '+' || c == '-');
                boolean sign = (c == '+' || c == '-') && v.substring(from, i).trim().isEmpty();
                if (doubled) {
                    i++;
                    continue;
                }
                if (sign) continue;
                out.add(v.substring(from, i));
                from = i + 1;
            }
        }
        if (depth != 0) return null;
        out.add(v.substring(from));
        return out;
    }

    /** A comparison or logical operator at the top level (outside brackets and literals). */
    private static boolean comparison(String v) {
        int depth = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(v, i);
                if (i < 0) return false;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (depth == 0) {
                String two = i + 1 < v.length() ? v.substring(i, i + 2) : "";
                if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=") || two.equals("&&")
                        || two.equals("||")) {
                    return true;
                }
                if (c == '<' || c == '>') return true;
            }
        }
        return false;
    }

    /** The method name of the call the value ends with ("a.b().trim()": trim), or null. */
    private static String lastCall(String v) {
        if (!v.endsWith(")")) return null;
        int depth = 0;
        int open = -1;
        for (int i = v.length() - 1; i >= 0; i--) {
            char c = v.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') {
                depth--;
                if (depth == 0) {
                    open = i;
                    break;
                }
            }
        }
        if (open <= 0) return null;
        int end = open;
        int start = end;
        while (start > 0 && Character.isJavaIdentifierPart(v.charAt(start - 1))) start--;
        if (start == end || start == 0 || v.charAt(start - 1) != '.') return null;
        return v.substring(start, end);
    }

    /** Index of the first '=' at the top level that is an assignment (not ==, !=, <=, >=), or -1. */
    static int topLevelAssignment(String text) {
        int depth = 0, angle = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i);
                if (i < 0) return -1;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == '<' && depth == 0 && i > 0 && Character.isJavaIdentifierPart(text.charAt(i - 1))) {
                angle++;
            } else if (c == '>' && angle > 0) {
                angle--;
            } else if (c == '=' && depth == 0 && angle == 0) {
                char before = i > 0 ? text.charAt(i - 1) : ' ';
                char after = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
                if (after == '=' || before == '=' || before == '!' || before == '<' || before == '>') {
                    if (after == '=') i++;
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    // --------------------------------------------------------------------------------------------- scope checks

    /** Lines of the class (code only: strings and comments blanked) where {@code name} is declared: a type word in
     * front of it, followed by '=', ';', ',', ')', ':' or '['; or ", name =" (another declarator). */
    static int declarations(JavaMembers e, String name) {
        Pattern typed = Pattern.compile("(?<![\\p{L}\\p{N}_$.])(" + ID + ")(?:\\s*<[^;=(){}]*>)?(?:\\s*\\[\\s*\\])*\\s+"
                + Pattern.quote(name) + "\\s*(?:=(?!=)|;|,|\\)|:|\\[)");
        Pattern another = Pattern.compile(",\\s*" + Pattern.quote(name) + "\\s*(?:=(?!=)|;|,)");
        int count = 0;
        for (int k = 1; k <= e.count(); k++) {
            String code = e.code(k);
            if (code.indexOf(name) < 0) continue;
            Matcher m = typed.matcher(code);
            while (m.find()) {
                if (!NOT_TYPES.contains(m.group(1))) count++;
            }
            Matcher a = another.matcher(code);
            while (a.find()) count++;
        }
        return count;
    }

    /** Whether code line {@code k} uses {@code name} as an identifier (not as a member "x.name"). */
    static boolean uses(JavaMembers e, int k, String name) {
        String code = e.code(k);
        int at = code.indexOf(name);
        while (at >= 0) {
            boolean startOk = at == 0 || !Character.isJavaIdentifierPart(code.charAt(at - 1)) && code.charAt(at - 1) != '.';
            int end = at + name.length();
            boolean endOk = end >= code.length() || !Character.isJavaIdentifierPart(code.charAt(end));
            if (startOk && endOk) return true;
            at = code.indexOf(name, at + 1);
        }
        return false;
    }

    /** How code of this kind ("event", "onCreate", "moreBlock", "adapter") and event can run again while it runs. */
    static int reentry(String kind, String eventName) {
        if ("moreBlock".equals(kind)) return REENTRY_LISTENER;
        if (!"event".equals(kind)) return REENTRY_NONE;
        if (eventName != null && !NOT_REFIRED.contains(eventName)
                && eventName.matches(".*(Change|Changed|Selected|Scrolled)$")) {
            return REENTRY_LISTENER;
        }
        return REENTRY_CLICK;
    }

    /**
     * Whether a code line makes a call that can run the code again: performClick anywhere; in a change event a
     * {@link #REFIRING} call on the event's own view ({@code target}: edittext1.setText in edittext1's onTextChanged) or
     * a MoreBlock call (_name(…), it may make that call); in a MoreBlock ({@code target} null) a REFIRING call on any
     * view or another MoreBlock call.
     */
    static boolean refires(String code, int reentry, String target) {
        if (reentry == REENTRY_NONE) return false;
        Matcher m = CALL.matcher(code);
        while (m.find()) {
            String called = m.group(1);
            if (called.equals("performClick") || called.equals("callOnClick")) return true;
            if (reentry == REENTRY_CLICK) continue;
            if (called.startsWith("_") && called.length() > 1) return true;
            if (!REFIRING.contains(called)) continue;
            if (target == null) return true;
            // the receiver: the name in front of ".called(" (whole name, not a member of something else)
            String before = code.substring(0, m.start()).replaceAll("\\s+$", "");
            if (!before.endsWith(".")) continue;
            before = before.substring(0, before.length() - 1).replaceAll("\\s+$", "");
            if (before.endsWith(target)) {
                int at = before.length() - target.length();
                char c = at > 0 ? before.charAt(at - 1) : ' ';
                if (!Character.isJavaIdentifierPart(c) && c != '.') return true;
            }
        }
        return false;
    }

    /**
     * Why the local declared on line {@code line} can't become a field (see the class comment), or null when it can.
     * The local's scope is the block it is declared in. {@code reentry} ({@link #reentry}): whether the code can run
     * again while it runs (setText in onTextChanged runs onTextChanged again before it returns, setChecked
     * onCheckedChange …; performClick in onClick): a use after such a call would see the value that inner run left in
     * the field.
     */
    static String hazard(JavaMembers e, int line, String name, String selfCall, int reentry, String target) {
        int d = e.depth[line];
        // the line that opened the block (depth at its start below d), and the line that closes it
        int open = line - 1;
        while (open >= 1 && e.depth[open] >= d) open--;
        int close = line + 1;
        while (close <= e.count() && e.depth[close] >= d) close++;
        close = Math.min(close - 1, e.count());   // the line holding the closing brace
        for (int k = Math.max(open + 1, 1); k < line; k++) {
            if (uses(e, k, name)) return "it is used before its declaration";
        }
        boolean nested = false;
        boolean called = false;
        String declared = e.code(line);
        int afterDeclaration = declared.indexOf(';');
        if (afterDeclaration >= 0) {
            String rest = declared.substring(afterDeclaration + 1);
            if (rest.contains("->") || ANONYMOUS.matcher(rest).find()) return "a listener or lambda uses it";
        }
        for (int k = line + 1; k <= close; k++) {
            String code = e.code(k);
            String trimmed = code.trim();
            if (code.contains("->") || ANONYMOUS.matcher(code).find() || LOCAL_TYPE.matcher(trimmed).find()
                    || ANONYMOUS_HEAD.matcher(trimmed).find() && k < e.count() && e.code(k + 1).trim().startsWith("{")) {
                nested = true;
            }
            boolean used = uses(e, k, name);
            if (nested && used) return "a listener, lambda or class below it uses it (it would see a later value)";
            if (selfCall != null && code.contains(selfCall)) return "the MoreBlock calls itself (each call has its own value)";
            if (called && used) {
                return reentry == REENTRY_CLICK ? "performClick above this use runs this event again, which would change it"
                        : "a call above this use (setText, setChecked, setProgress … or a MoreBlock) can run this code "
                        + "again, which would change it";
            }
            if (refires(code, reentry, target)) called = true;
        }
        return null;
    }
}
