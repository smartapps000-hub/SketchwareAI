package h;

import java.io.File;
import java.util.*;

import static h.R.*;

/**
 * v1.8.8 Java editor on the Android runtime: opens fixture 919 (spec javaedit) in JavaEditSession, the way the
 * editor screen does, saves a series of edits and checks each result: what was stored (blocks, variables, MoreBlocks,
 * imports), that refused edits leave the project as it was, and that the code generated again is the edited code.
 * Then the project is built (Main.build) with the stored blocks.
 * Usage: run.sh <apk> <root> javaedit <sc_id> [nobuild]   (prints CHECK ok/FAIL lines and JAVAEDIT n/m ok)
 */
public class JavaEdit {
    static int checks = 0, fails = 0;
    static Object proj, files, libs, session;
    static String sc;
    static final String JAVA = "MainActivity.java";

    static void check(String name, boolean ok, String detail) {
        checks++;
        if (!ok) fails++;
        Main.OUT.println("CHECK " + (ok ? "ok   " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : "  [" + detail + "]"));
    }

    static String code() throws Exception {
        return (String) get(session, "code");
    }

    static Object save(String edited) throws Throwable {
        return call(session, "save", edited);
    }

    static boolean ok(Object result) throws Exception {
        return (Boolean) get(result, "ok");
    }

    static String report(Object result) throws Exception {
        return "changes=" + get(result, "changes") + " notes=" + get(result, "notes") + " problems=" + get(result, "problems");
    }

    @SuppressWarnings("unchecked")
    static ArrayList<Object> blocks(String key) throws Throwable {
        ArrayList<Object> list = (ArrayList<Object>) call(proj, "a", JAVA, key);
        return list == null ? new ArrayList<>() : list;
    }

    /** "id:opCode->next[s1,s2]" for each block, in list order. */
    static String shape(String key) throws Throwable {
        StringBuilder s = new StringBuilder();
        for (Object b : blocks(key)) {
            if (s.length() > 0) s.append(' ');
            s.append(get(b, "id")).append(':').append(get(b, "opCode")).append("->").append(get(b, "nextBlock"));
            int s1 = (Integer) get(b, "subStack1"), s2 = (Integer) get(b, "subStack2");
            if (s1 >= 0 || s2 >= 0) s.append('[').append(s1).append(',').append(s2).append(']');
        }
        return s.toString();
    }

    @SuppressWarnings("unchecked")
    static String vars() throws Exception {
        List<Object> list = ((HashMap<String, ArrayList<Object>>) get(proj, "e")).get(JAVA);
        StringBuilder s = new StringBuilder();
        if (list != null) for (Object p : list) s.append(get(p, "first")).append('=').append(get(p, "second")).append("; ");
        return s.toString();
    }

    @SuppressWarnings("unchecked")
    static String moreBlocks() throws Exception {
        List<Object> list = ((HashMap<String, ArrayList<Object>>) get(proj, "g")).get(JAVA);
        StringBuilder s = new StringBuilder();
        if (list != null) for (Object p : list) s.append(get(p, "first")).append('=').append(get(p, "second")).append("; ");
        return s.toString();
    }

    /** "opCode(child,...)" with "_" for a typed value and the slot blocks' own slots. */
    @SuppressWarnings("unchecked")
    static String tree21(Object block, Map<String, Object> all) throws Exception {
        StringBuilder out = new StringBuilder(String.valueOf(get(block, "opCode")));
        List<String> params = (List<String>) get(block, "parameters");
        if ("addSourceDirectly".equals(get(block, "opCode")) || "getVar".equals(get(block, "opCode"))) return out.toString();
        if (params.isEmpty()) return out.toString();
        out.append('(');
        boolean first = true;
        for (String p : params) {
            if (!first) out.append(',');
            first = false;
            Object child = p != null && p.startsWith("@") ? all.get(p.substring(1)) : null;
            if (child != null) out.append(tree21(child, all));
            else if (p != null && (p.equals("textview1") || p.equals("checkbox1") || p.equals("items") || p.equals("score")
                    || p.equals("title") || p.equals("done") || p.equals("store"))) continue;
            else out.append('_');
        }
        // dropdown names are left out: "setText(x)" reads as the value only
        String text = out.append(')').toString();
        return text.replace("(,", "(").replace(",)", ")");
    }

    static String sourceOf(Object block) throws Exception {
        @SuppressWarnings("unchecked") List<String> p = (List<String>) get(block, "parameters");
        return p.isEmpty() ? "" : p.get(0);
    }

    static List<String> lines(String text) {
        return new ArrayList<>(Arrays.asList(text.split("\n", -1)));
    }

    static String join(List<String> lines) {
        return String.join("\n", lines);
    }

    static int find(List<String> lines, String needle, int from) {
        for (int i = from; i < lines.size(); i++) if (lines.get(i).contains(needle)) return i;
        throw new IllegalStateException("not found: " + needle);
    }

    /** All project state the editor may change, for "refused edits change nothing". */
    static String state() throws Throwable {
        StringBuilder s = new StringBuilder(vars()).append('|').append(moreBlocks()).append('|');
        for (String key : new String[]{"button1_onClick", "button2_onClick", "onCreate_initializeLogic", "greet_moreBlock"}) {
            s.append(key).append(':').append(shape(key)).append('|');
        }
        return s.toString();
    }

    static void open() throws Throwable {
        session = call(c("com.sketchware.ai.editor.JavaEditSession"), "open", Main.CTX, sc, JAVA, files, proj, libs);
    }

    /**
     * v1.8.13: the owner's MainActivity edit in a Material 3 project (fixture 908, an outlined edittext1): lines typed
     * without indentation into initializeLogic, a setHint among them (the generator writes it SketchTextField.hint(...),
     * which made v1.8.12 refuse the save) and setText(edittext1.getText().toString()).
     * Usage: run.sh <apk> <root> javaedit-m3 <sc_id>   (CHECK lines and JAVAEDIT-M3 n/m ok)
     */
    public static void m3(String sc_id) throws Throwable {
        sc = sc_id;
        Class<?> jC = c("a.a.a.jC");
        proj = call(jC, "a", sc);
        files = call(jC, "b", sc);
        libs = call(jC, "c", sc);
        open();
        check("m3: opens convertible", (Boolean) call(session, "convertible"), String.valueOf(get(session, "problem")));
        List<String> l = lines(code());
        int at = find(l, "private void initializeLogic() {", 0);
        String[] typed = {
                "textview1.setText(\"AiTechApp PDF & Notebook Engine\");",
                "edittext1.setHint(\"Enter your notes here...\");",
                "String userNotes = edittext1.getText().toString();",
                "textview1.setText(userNotes);",
                "textview1.setVisibility(View.VISIBLE);",
                "textview1.setText(edittext1.getText().toString());"};
        for (int k = 0; k < typed.length; k++) l.add(at + 1 + k, typed[k]);   // no indentation, as typed on the phone
        Object r = save(join(l));
        check("m3: saved (setHint on an outlined field no longer refused)", ok(r), report(r));
        String again = code();
        write("javaedit-m3-saved.java", again);
        check("m3: setHint written as the field's hint", again.contains("SketchTextField.hint(edittext1, \"Enter your notes here...\");"), "");
        StringBuilder ops = new StringBuilder();
        boolean nestedGetText = false;
        for (Object b : blocks("onCreate_initializeLogic")) {
            ops.append(get(b, "opCode")).append(' ');
            nestedGetText |= "getText".equals(get(b, "opCode")) && String.valueOf(get(b, "parameters")).equals("[edittext1]");
        }
        check("m3: setHint block and nested getText block", ops.toString().contains("setHint") && nestedGetText, ops.toString());
        // setHint inside braces stays add source: stored as typed (builds with Material 3 off too), generated as the hint
        l = lines(again);
        at = find(l, "textview1.setText(edittext1.getText().toString());", find(l, "private void initializeLogic() {", 0));
        l.add(at + 1, "if (userNotes.isEmpty()) {");
        l.add(at + 2, "edittext1.setHint(\"Type here\");");
        l.add(at + 3, "}");
        r = save(join(l));
        check("m3: setHint inside an if saved", ok(r), report(r));
        String stored = null;
        for (Object b : blocks("onCreate_initializeLogic")) {
            if ("addSourceDirectly".equals(get(b, "opCode")) && sourceOf(b).contains("Type here")) stored = sourceOf(b);
        }
        check("m3: add source stores edittext1.setHint as typed", stored != null && stored.contains("edittext1.setHint(\"Type here\");")
                && !stored.contains("SketchTextField"), String.valueOf(stored));
        again = code();
        write("javaedit-m3-saved.java", again);
        check("m3: generated as the field's hint", again.contains("SketchTextField.hint(edittext1, \"Type here\");"), "");
        // saving the shown code again changes nothing
        Object r2 = save(again);
        check("m3: saved code saves unchanged", ok(r2) && (Boolean) get(r2, "unchanged"), report(r2));
        Main.OUT.println("JAVAEDIT-M3 " + (checks - fails) + "/" + checks + " ok");
        call(proj, "k");
    }

    public static void run(String sc_id, boolean build) throws Throwable {
        sc = sc_id;
        Class<?> jC = c("a.a.a.jC");
        proj = call(jC, "a", sc);
        files = call(jC, "b", sc);
        libs = call(jC, "c", sc);
        open();
        Main.OUT.println("OPEN problem=" + get(session, "problem") + " convertible=" + call(session, "convertible"));
        check("opens convertible", (Boolean) call(session, "convertible"), String.valueOf(get(session, "problem")));
        String g = code();
        write("javaedit-0-generated.java", g);
        Main.OUT.println("BEFORE " + state());

        // 1. unchanged
        Object r = save(g);
        check("unchanged save", ok(r) && (Boolean) get(r, "unchanged"), report(r));

        // 2. code typed into the empty button2 onClick
        List<String> l = lines(g);
        int at = find(l, "button2.setOnClickListener", 0);
        int on = find(l, "public void onClick(", at);
        l.set(on + 1, "\t\t\t\tandroid.util.Log.d(\"b2\", \"clicked\");");
        r = save(join(l));
        check("empty event: ok", ok(r), report(r));
        ArrayList<Object> b2 = blocks("button2_onClick");
        check("empty event: one add-source block", b2.size() == 1 && "addSourceDirectly".equals(get(b2.get(0), "opCode"))
                && sourceOf(b2.get(0)).equals("android.util.Log.d(\"b2\", \"clicked\");"), shape("button2_onClick"));

        // 3. a line inside the if's stack replaced: only that block changes
        l = lines(code());
        at = find(l, "\"inside\"", find(l, "button1.setOnClickListener", 0));
        l.set(at, "android.util.Log.d(\"b1\", \"inside changed\");");
        String before = shape("button1_onClick");
        r = save(join(l));
        check("inside if: ok", ok(r), report(r));
        String after = shape("button1_onClick");
        Main.OUT.println("  button1 " + before + "  =>  " + after);
        ArrayList<Object> b1 = blocks("button1_onClick");
        Map<String, Object> byId = new HashMap<>();
        for (Object b : b1) byId.put((String) get(b, "id"), b);
        Object iff = byId.get("11");
        Object sub = iff == null ? null : byId.get(String.valueOf(get(iff, "subStack1")));
        check("inside if: if, setText x2 kept; the stack's block is add source",
                byId.containsKey("10") && iff != null && byId.containsKey("13") && byId.containsKey("20") && !byId.containsKey("12")
                        && sub != null && "addSourceDirectly".equals(get(sub, "opCode"))
                        && sourceOf(sub).equals("android.util.Log.d(\"b1\", \"inside changed\");")
                        && "if".equals(get(iff, "opCode")) && ((Integer) get(iff, "nextBlock")) == 13
                        && b1.get(0) == byId.get("10"), after);

        // 4. a line inserted at the top of onCreate's code, in front of a block that stays
        l = lines(code());
        at = find(l, "edittext1.setText(\"x\")", find(l, "private void initializeLogic()", 0));
        l.add(at, "android.util.Log.d(\"create\", \"start\");");
        r = save(join(l));
        check("insert before a block: ok", ok(r), report(r));
        ArrayList<Object> oc = blocks("onCreate_initializeLogic");
        check("insert before a block: add source first, setText after it", oc.size() == 2
                && "addSourceDirectly".equals(get(oc.get(0), "opCode"))
                && String.valueOf(get(oc.get(0), "nextBlock")).equals(get(oc.get(1), "id"))
                && "setText".equals(get(oc.get(1), "opCode")), shape("onCreate_initializeLogic"));

        // 5. field + import + MoreBlock added, MoreBlock body changed
        l = lines(code());
        at = find(l, "private int clicks = 0;", 0);
        l.add(at + 1, "private String greeting = \"hello\";");
        int lastImport = 0;
        for (int i = 0; i < l.size(); i++) if (l.get(i).startsWith("import ")) lastImport = i;
        l.add(lastImport + 1, "import java.util.concurrent.TimeUnit;");
        at = find(l, "android.util.Log.d(\"greet\", _who);", 0);
        l.set(at, "android.util.Log.d(\"greet\", \"Hello \" + _who);");
        int close = l.size() - 1;
        while (!l.get(close).trim().equals("}")) close--;
        l.add(close, "public void _sayHi(final String _who, final double _times) {");
        l.add(close + 1, "\tfor (int i = 0; i < (int) _times; i++) {");
        l.add(close + 2, "\t\tandroid.util.Log.d(\"hi\", _who + TimeUnit.SECONDS.toMillis(i));");
        l.add(close + 3, "\t}");
        l.add(close + 4, "}");
        r = save(join(l));
        check("field/import/MoreBlock: ok", ok(r), report(r));
        String v = vars();
        check("field is a custom variable", v.contains("6=private String greeting = \"hello\""), v);
        check("import is a custom import", v.contains("9=java.util.concurrent.TimeUnit"), v);
        String mb = moreBlocks();
        check("MoreBlock sayHi added", mb.contains("sayHi=sayHi %s.who %d.times"), mb);
        ArrayList<Object> hi = blocks("sayHi_moreBlock");
        check("MoreBlock body is one add-source block", hi.size() == 1 && sourceOf(hi.get(0)).contains("TimeUnit.SECONDS"), shape("sayHi_moreBlock"));
        ArrayList<Object> greet = blocks("greet_moreBlock");
        check("MoreBlock body changed", greet.size() == 1 && sourceOf(greet.get(0)).contains("\"Hello \" + _who"), shape("greet_moreBlock"));
        check("generated again: the field, import and method are in the code", code().contains("private String greeting = \"hello\";")
                && code().contains("import java.util.concurrent.TimeUnit;") && code().contains("public void _sayHi(final String _who, final double _times) {"), "");

        // 6. refused: a view lookup line in initialize(); a method that isn't a MoreBlock; nothing changes
        String state = state();
        l = lines(code());
        at = find(l, "button1 = findViewById(", 0);
        l.set(at, "button1 = (Button) findViewById(R.id.button1);");
        r = save(join(l));
        check("view lookup edit refused", !ok(r) && String.valueOf(get(r, "problems")).contains("initialize()"), report(r));
        check("view lookup edit: project unchanged", state.equals(state()), "");
        l = lines(code());
        close = l.size() - 1;
        while (!l.get(close).trim().equals("}")) close--;
        l.add(close, "private int twice(int x) {");
        l.add(close + 1, "return x * 2;");
        l.add(close + 2, "}");
        r = save(join(l));
        check("plain method refused", !ok(r) && String.valueOf(get(r, "problems")).contains("twice"), report(r));
        check("plain method: project unchanged", state.equals(state()), "");

        // 7. removed: an unused variable, a block line (the setText after the if)
        l = lines(code());
        at = find(l, "private String name = \"\";", 0);
        l.remove(at);
        at = find(l, "textview1.setText(\"after\");", 0);
        l.remove(at);
        r = save(join(l));
        check("remove variable + block: ok", ok(r), report(r));
        v = vars();
        check("variable name removed", !v.contains("2=name"), v);
        b1 = blocks("button1_onClick");
        byId.clear();
        for (Object b : b1) byId.put((String) get(b, "id"), b);
        check("block 13 removed, the if ends the event", !byId.containsKey("13") && byId.containsKey("11")
                && ((Integer) get(byId.get("11"), "nextBlock")) == -1, shape("button1_onClick"));

        // 8. the whole if (with its stack and condition) replaced by two lines
        l = lines(code());
        int start = find(l, "if (true) {", find(l, "button1.setOnClickListener", 0));
        int end = start;
        int depth = 0;
        for (int i = start; i < l.size(); i++) {
            for (char ch : l.get(i).toCharArray()) {
                if (ch == '{') depth++;
                if (ch == '}') depth--;
            }
            if (depth == 0) {
                end = i;
                break;
            }
        }
        for (int i = end; i >= start; i--) l.remove(i);
        l.add(start, "clicks++;");
        l.add(start + 1, "textview1.setText(String.valueOf(clicks));");
        r = save(join(l));
        check("replace the if: ok", ok(r), report(r));
        b1 = blocks("button1_onClick");
        byId.clear();
        for (Object b : b1) byId.put((String) get(b, "id"), b);
        check("if, its condition and its stack removed; one add-source after setText", !byId.containsKey("11")
                && !byId.containsKey("20") && b1.size() == 2 && "setText".equals(get(b1.get(0), "opCode"))
                && "addSourceDirectly".equals(get(b1.get(1), "opCode")) && sourceOf(b1.get(1)).equals("clicks++;\ntextview1.setText(String.valueOf(clicks));"),
                shape("button1_onClick"));

        // 10. if/else: a line of the else stack changed, a line put at the top of the else stack
        l = lines(code());
        int tvAt = find(l, "textview1.setOnClickListener", 0);
        at = find(l, "textview1.setText(\"no\");", tvAt);
        l.set(at, "textview1.setText(\"nope\");");
        int elseAt = find(l, "else {", tvAt);
        l.add(elseAt + 1, "android.util.Log.d(\"tv\", \"else\");");
        r = save(join(l));
        check("else stack: ok", ok(r), report(r));
        ArrayList<Object> tv = blocks("textview1_onClick");
        byId.clear();
        for (Object b : tv) byId.put((String) get(b, "id"), b);
        Object ie = byId.get("10");
        Object e1 = ie == null ? null : byId.get(String.valueOf(get(ie, "subStack2")));
        // v1.8.9: the Log line is add source; the changed setText line becomes a setText block again
        Object e2 = e1 == null ? null : byId.get(String.valueOf(get(e1, "nextBlock")));
        check("else stack: ifElse, its condition and the then-stack kept; add source + setText block in the else stack",
                ie != null && byId.containsKey("11") && byId.containsKey("12") && !byId.containsKey("13")
                        && ((Integer) get(ie, "subStack1")) == 12 && e1 != null && "addSourceDirectly".equals(get(e1, "opCode"))
                        && sourceOf(e1).equals("android.util.Log.d(\"tv\", \"else\");")
                        && e2 != null && "setText".equals(get(e2, "opCode"))
                        && String.valueOf(get(e2, "parameters")).equals("[textview1, nope]")
                        && ((Integer) get(e2, "nextBlock")) == -1, shape("textview1_onClick"));

        // 11. onCreate emptied (the generator then writes nothing between its braces), then a line put back
        l = lines(code());
        int ic = find(l, "private void initializeLogic()", 0);
        int icEnd = ic + 1;
        while (!l.get(icEnd).trim().equals("}")) icEnd++;
        for (int i = icEnd - 1; i > ic; i--) l.remove(i);
        r = save(join(l));
        check("onCreate emptied: ok", ok(r), report(r));
        check("onCreate emptied: no blocks", blocks("onCreate_initializeLogic").isEmpty(), shape("onCreate_initializeLogic"));
        l = lines(code());
        ic = find(l, "private void initializeLogic()", 0);
        check("onCreate emptied: the generator writes nothing in it", l.get(ic + 1).trim().equals("}"), l.get(ic + 1));
        l.add(ic + 1, "android.util.Log.d(\"create\", \"again\");");
        r = save(join(l));
        check("line into an empty onCreate: ok", ok(r), report(r));
        oc = blocks("onCreate_initializeLogic");
        check("line into an empty onCreate: one add-source block", oc.size() == 1
                && sourceOf(oc.get(0)).equals("android.util.Log.d(\"create\", \"again\");"), shape("onCreate_initializeLogic"));

        // 12. unreadable code is refused with the reason
        r = save(code().replace("private void initializeLogic() {", "private void initializeLogic() {{"));
        check("unbalanced braces refused", !ok(r) && String.valueOf(get(r, "problems")).contains("not closed"), report(r));

        // 13. review findings: a field written over two lines is stored on one line; a comment before the class's
        // closing brace is refused (it would be lost); a variable's type changed keeps the variable (blocks use it)
        l = lines(code());
        at = find(l, "private String greeting = \"hello\";", 0);
        l.set(at, "private String farewell =");
        l.add(at + 1, "        \"bye\";");
        r = save(join(l));
        check("two-line field: ok", ok(r), report(r));
        v = vars();
        check("two-line field: one line in the project data", v.contains("6=private String farewell = \"bye\"; ") && !v.contains("\n"), v);
        String stateBefore = state();
        l = lines(code());
        close = l.size() - 1;
        while (!l.get(close).trim().equals("}")) close--;
        l.add(close, "// helper methods end here");
        r = save(join(l));
        check("comment before the closing brace refused", !ok(r) && String.valueOf(get(r, "problems")).contains("outside the class"), report(r));
        check("comment before the closing brace: project unchanged", stateBefore.equals(state()), "");
        l = lines(code());
        at = find(l, "private double count = 0;", 0);
        l.set(at, "private int count = 0;");
        r = save(join(l));
        check("variable type changed: ok", ok(r), report(r));
        v = vars();
        check("variable type changed: same place, custom declaration", v.startsWith("6=private int count = 0;"), v);

        // 14. "//" inside a string (a URL) is not a comment: the field is stored; a real comment after a field is refused
        l = lines(code());
        at = find(l, "private int count = 0;", 0);
        l.add(at + 1, "    private String api = \"https://example.com/v1/\";");
        r = save(join(l));
        check("URL field: ok", ok(r), report(r));
        v = vars();
        check("URL field: stored as written", v.contains("6=private String api = \"https://example.com/v1/\"; "), v);
        stateBefore = state();
        l = lines(code());
        at = find(l, "private String api = \"https://example.com/v1/\";", 0);
        l.add(at + 1, "    private int retries = 3; // how often");
        r = save(join(l));
        check("field with a trailing comment refused", !ok(r) && String.valueOf(get(r, "problems")).contains("retries"), report(r));
        check("field with a trailing comment: project unchanged", stateBefore.equals(state()), "");

        // 15. v1.8.9: statements a Sketchware block writes become that block (checked with the IDE's generator);
        // other lines stay "add source directly"; the code generated again is the edited code
        l = lines(code());
        at = find(l, "button2.setOnClickListener", 0);
        on = find(l, "public void onClick(", at);
        int stop = on + 1;
        while (!l.get(stop).trim().equals("}")) stop++;
        for (int k = stop - 1; k > on; k--) l.remove(k);
        String[] typed = {
                "textview1.setText(\"Mapped\");",
                "textview1.setVisibility(View.GONE);",
                "SketchwareUtil.showMessage(getApplicationContext(), \"Hi \\\"there\\\"\");",
                "android.util.Log.d(\"keep\", \"as source\");",
                "title = \"Ravi\";",
                "textview1.setText(title);",
                "score++;",
                "score = 5;",
                "button1.setEnabled(false);",
                "intent1.setClass(getApplicationContext(), MainActivity.class);",
                "intent1.putExtra(\"key\", title);",
                "startActivity(intent1);",
                "finish();"};
        for (int k = 0; k < typed.length; k++) l.add(on + 1 + k, "\t\t\t\t" + typed[k]);
        r = save(join(l));
        check("statements to blocks: ok", ok(r), report(r));
        ArrayList<Object> chain = new ArrayList<>();
        Map<String, Object> all = new HashMap<>();
        for (Object b : blocks("button2_onClick")) all.put((String) get(b, "id"), b);
        Object cur = blocks("button2_onClick").isEmpty() ? null : blocks("button2_onClick").get(0);
        while (cur != null) {
            chain.add(cur);
            int nx = (Integer) get(cur, "nextBlock");
            cur = nx < 0 ? null : all.get(String.valueOf(nx));
        }
        StringBuilder ops = new StringBuilder();
        for (Object b : chain) ops.append(get(b, "opCode")).append(' ');
        String expected = "setText setVisible doToast addSourceDirectly setVarString setText increaseInt setVarInt setEnable intentSetScreen intentPutExtra startActivity finishActivity ";
        check("statements to blocks: one block per statement, the Log line as add source", ops.toString().equals(expected), ops.toString());
        if (chain.size() == 13) {
            check("setText parameters", String.valueOf(get(chain.get(0), "parameters")).equals("[textview1, Mapped]")
                    && "%m.textview setText %s".equals(get(chain.get(0), "spec")), get(chain.get(0), "parameters") + " " + get(chain.get(0), "spec"));
            check("setVisible parameters", String.valueOf(get(chain.get(1), "parameters")).equals("[textview1, GONE]"), String.valueOf(get(chain.get(1), "parameters")));
            check("Toast text unescaped", String.valueOf(get(chain.get(2), "parameters")).equals("[Hi \"there\"]"), String.valueOf(get(chain.get(2), "parameters")));
            @SuppressWarnings("unchecked") List<String> p5 = (List<String>) get(chain.get(5), "parameters");
            Object getter = p5.size() == 2 && p5.get(1).startsWith("@") ? all.get(p5.get(1).substring(1)) : null;
            check("setText(title): a variable block", getter != null && "getVar".equals(get(getter, "opCode")) && "title".equals(get(getter, "spec")), String.valueOf(p5));
            check("setScreen / Finish Activity", String.valueOf(get(chain.get(9), "parameters")).equals("[intent1, MainActivity]")
                    && "f".equals(get(chain.get(12), "type")), get(chain.get(9), "parameters") + " " + get(chain.get(12), "type"));
        }
        String regenerated = code();
        boolean allThere = true;
        for (String t : typed) allThere &= regenerated.contains(t);
        check("statements to blocks: generated again, every line as typed", allThere, "");

        // 16. v1.8.9: the other statements the mapper knows, in button2's onClick again
        l = lines(code());
        at = find(l, "button2.setOnClickListener", 0);
        on = find(l, "public void onClick(", at);
        stop = on + 1;
        while (!l.get(stop).trim().equals("}")) stop++;
        for (int k = stop - 1; k > on; k--) l.remove(k);
        String[] typed2 = {
                "button1.setClickable(true);",
                "checkbox1.setChecked(true);",
                "edittext1.requestFocus();",
                "setTitle(\"Home\");",
                "done = true;",
                "score--;",
                "checkbox1.setChecked(done);"};
        for (int k = 0; k < typed2.length; k++) l.add(on + 1 + k, "\t\t\t\t" + typed2[k]);
        r = save(join(l));
        check("more statements to blocks: ok", ok(r), report(r));
        all.clear();
        for (Object b : blocks("button2_onClick")) all.put((String) get(b, "id"), b);
        cur = blocks("button2_onClick").isEmpty() ? null : blocks("button2_onClick").get(0);
        ops = new StringBuilder();
        while (cur != null) {
            ops.append(get(cur, "opCode")).append(' ');
            int nx = (Integer) get(cur, "nextBlock");
            cur = nx < 0 ? null : all.get(String.valueOf(nx));
        }
        check("more statements to blocks: one block each", ops.toString().equals(
                "setClickable setChecked requestFocus setTitle setVarBoolean decreaseInt setChecked "), ops.toString());
        regenerated = code();
        allThere = true;
        for (String t : typed2) allThere &= regenerated.contains(t);
        check("more statements to blocks: generated again, every line as typed", allThere, "");

        // 17. v1.8.11: a statement typed in the class body (outside methods) is refused, not stored as a custom
        // variable; an invalid custom variable stored by v1.8.8-v1.8.10 is removed by deleting its line
        l = lines(code());
        at = find(l, "public void onClick(", 0);
        int fieldsAt = find(l, "private Intent intent1", 0);
        l.add(fieldsAt + 1, "\tbutton1.setText(\"AiTechApp\");");
        stateBefore = state();
        r = save(join(l));
        check("statement in the class body refused", !ok(r) && String.valueOf(get(r, "problems")).contains("isn't a field declaration"), report(r));
        check("statement in the class body: project unchanged", stateBefore.equals(state()), "");
        @SuppressWarnings("unchecked") List<Object> vars = ((HashMap<String, ArrayList<Object>>) get(proj, "e")).get(JAVA);
        vars.add(neu("android.util.Pair", 6, "button1.setText(\"AiTechApp\")"));   // what v1.8.8-v1.8.10 could store
        open();
        String withBad = code();
        check("invalid custom variable generated in the class body", withBad.contains("button1.setText(\"AiTechApp\");"), "");
        l = lines(withBad);
        l.remove(find(l, "button1.setText(\"AiTechApp\");", 0));
        r = save(join(l));
        boolean gone = true;
        for (Object variable : ((HashMap<String, ArrayList<Object>>) get(proj, "e")).get(JAVA)) gone &= !String.valueOf(get(variable, "second")).contains("setText");
        check("invalid custom variable removed by deleting its line", ok(r) && gone, report(r));

        // 18. v1.8.13: a value block inside a block's slot: setText(edittext1.getText().toString()) is the setText block
        // with the getText block in its text slot, no add-source block
        l = lines(code());
        at = find(l, "button2.setOnClickListener", 0);
        on = find(l, "public void onClick(", at);
        stop = on + 1;
        while (!l.get(stop).trim().equals("}")) stop++;
        for (int k = stop - 1; k > on; k--) l.remove(k);
        l.add(on + 1, "\t\t\t\ttextview1.setText(edittext1.getText().toString());");
        r = save(join(l));
        check("nested getText: ok", ok(r), report(r));
        List<Object> nested = blocks("button2_onClick");
        Object head = nested.isEmpty() ? null : nested.get(0);
        @SuppressWarnings("unchecked") List<String> hp = head == null ? null : (List<String>) get(head, "parameters");
        Object child = null;
        if (hp != null && hp.size() == 2 && hp.get(1).startsWith("@")) {
            for (Object b : nested) if (hp.get(1).substring(1).equals(get(b, "id"))) child = b;
        }
        boolean asd = false;
        for (Object b : nested) asd |= "addSourceDirectly".equals(get(b, "opCode"));
        check("nested getText: setText block with a getText block in its slot, no add source",
                head != null && "setText".equals(get(head, "opCode")) && child != null && "getText".equals(get(child, "opCode"))
                        && String.valueOf(get(child, "parameters")).equals("[edittext1]") && "s".equals(get(child, "type")) && !asd,
                shape("button2_onClick") + " " + hp + (child == null ? "" : " child " + get(child, "opCode") + get(child, "parameters")));
        check("nested getText: generated again as typed", code().contains("textview1.setText(edittext1.getText().toString());"), "");

        // 19. v1.9.0: variables registered by the save. Local declarations of String / double / boolean become project
        // variables with set blocks, an int a custom variable (its type kept), assignments to undeclared names
        // variables of the type their value shows; a name declared twice or captured by a listener stays a local.
        l = lines(code());
        int ic19 = find(l, "private void initializeLogic() {", 0);
        l.add(ic19 + 1, "String twice = \"1\";");
        at = find(l, "button2.setOnClickListener", 0);
        on = find(l, "public void onClick(", at);
        stop = on + 1;
        while (!l.get(stop).trim().equals("}")) stop++;
        for (int k = stop - 1; k > on; k--) l.remove(k);
        String[] typed19 = {
                "String userName = \"Ravi\";",
                "textview1.setText(userName);",
                "double total = 2.5;",
                "boolean shown = true;",
                "int clickCount = 3;",
                "attempts = 4;",
                "rating = 4.5;",
                "label = edittext1.getText().toString();",
                "String twice = \"2\";",
                "String captured = \"x\";",
                "button1.setOnClickListener(new View.OnClickListener() {",
                "@Override public void onClick(View v) { textview1.setText(captured); }",
                "});"};
        for (int k = 0; k < typed19.length; k++) l.add(on + 1 + k, "\t\t\t\t" + typed19[k]);
        r = save(join(l));
        check("variables: saved", ok(r), report(r));
        v = vars();
        check("variables: String, Number, Boolean registered from declarations",
                v.contains("2=userName; ") && v.contains("1=total; ") && v.contains("0=shown; "), v);
        check("variables: int kept as a custom variable of type int", v.contains("6=private int clickCount; "), v);
        check("variables: undeclared assignments registered by their value", v.contains("1=attempts; ") && v.contains("1=rating; ") && v.contains("2=label; "), v);
        check("variables: declared twice / captured by a listener stay locals", !v.contains("twice") && !v.contains("captured"), v);
        all.clear();
        for (Object b : blocks("button2_onClick")) all.put((String) get(b, "id"), b);
        cur = blocks("button2_onClick").isEmpty() ? null : blocks("button2_onClick").get(0);
        ops = new StringBuilder();
        List<Object> chain19 = new ArrayList<>();
        while (cur != null) {
            chain19.add(cur);
            ops.append(get(cur, "opCode")).append(' ');
            int nx = (Integer) get(cur, "nextBlock");
            cur = nx < 0 ? null : all.get(String.valueOf(nx));
        }
        check("variables: set blocks instead of add source", ops.toString().equals(
                "setVarString setText setVarInt setVarBoolean addSourceDirectly setVarInt setVarInt setVarString addSourceDirectly "), ops.toString());
        if (chain19.size() == 9) {
            check("variables: int assignment kept as code", sourceOf(chain19.get(4)).equals("clickCount = 3;"), sourceOf(chain19.get(4)));
            @SuppressWarnings("unchecked") List<String> p1 = (List<String>) get(chain19.get(1), "parameters");
            Object g1 = p1.size() == 2 && p1.get(1).startsWith("@") ? all.get(p1.get(1).substring(1)) : null;
            check("variables: setText(userName) reads the new variable", g1 != null && "getVar".equals(get(g1, "opCode"))
                    && "userName".equals(get(g1, "spec")), String.valueOf(p1));
            // v1.9.1: true is a true block in the slot (a bare "true" showed an empty slot on the phone)
            @SuppressWarnings("unchecked") List<String> p3 = (List<String>) get(chain19.get(3), "parameters");
            Object t3 = p3.size() == 2 && p3.get(1).startsWith("@") ? all.get(p3.get(1).substring(1)) : null;
            check("variables: set blocks' parameters", String.valueOf(get(chain19.get(0), "parameters")).equals("[userName, Ravi]")
                    && String.valueOf(get(chain19.get(2), "parameters")).equals("[total, 2.5]")
                    && "shown".equals(p3.get(0)) && t3 != null && "true".equals(get(t3, "opCode")) && "b".equals(get(t3, "type")),
                    get(chain19.get(0), "parameters") + " " + get(chain19.get(2), "parameters") + " " + p3
                            + (t3 == null ? "" : " " + get(t3, "opCode")));
            check("variables: locals that stay are one add source block", sourceOf(chain19.get(8)).startsWith("String twice = \"2\";\nString captured = \"x\";"),
                    sourceOf(chain19.get(8)));
            check("variables: decimals stored as numbers, written the generator's way", String.valueOf(get(chain19.get(6), "parameters")).equals("[rating, 4.5]"),
                    String.valueOf(get(chain19.get(6), "parameters")));
        }
        String notes19 = String.valueOf(get(r, "notes"));
        check("variables: the save says why locals stay", notes19.contains("captured stays a local") && notes19.contains("twice stays a local"), notes19);
        regenerated = code();
        write("javaedit-19-variables.java", regenerated);
        check("variables: generated again with fields and assignments", regenerated.contains("private String userName = \"\";")
                && regenerated.contains("userName = \"Ravi\";") && !regenerated.contains("String userName = \"Ravi\";")
                && regenerated.contains("private int clickCount;") && regenerated.contains("clickCount = 3;")
                && regenerated.contains("private double attempts = 0;") && regenerated.contains("total = 2.5d;") && regenerated.contains("rating = 4.5d;") && regenerated.contains("label = edittext1.getText().toString();")
                && regenerated.contains("String captured = \"x\";") && regenerated.contains("String twice = \"1\";"), "");
        Object r19 = save(regenerated);
        check("variables: the shown code saves unchanged", ok(r19) && (Boolean) get(r19, "unchanged"), report(r19));

        // 20. v1.9.0 reviews: locals that only keep their meaning as locals stay code: a final one; one used after a
        // call that can run the code again (setText in a MoreBlock: an onTextChanged may call it back); one read by a
        // listener whose '{' is on the next line; one read by a local class. In initializeLogic, and in onClick after
        // setText (only performClick runs onClick again), a use after a call still becomes a variable.
        l = lines(code());
        int ic20 = find(l, "private void initializeLogic() {", 0);
        l.add(ic20 + 1, "String greet20 = \"Hi\";");
        l.add(ic20 + 2, "textview1.setText(\"x\");");
        l.add(ic20 + 3, "textview1.setText(greet20);");
        at = find(l, "button2.setOnClickListener", 0);
        on = find(l, "public void onClick(", at);
        stop = on + 1;
        while (!l.get(stop).trim().equals("}")) stop++;
        for (int k = stop - 1; k > on; k--) l.remove(k);
        String[] typed20 = {
                "final int request20 = 7;",
                "String later20 = \"x\";",
                "Runnable task20 = new Runnable()",
                "{",
                "@Override public void run() { textview1.setText(later20); }",
                "};",
                "String local20 = \"y\";",
                "class Holder20 { String get() { return local20; } }",
                "textview1.setText(String.valueOf(request20) + new Holder20().get());",
                "String name20 = edittext1.getText().toString();",
                "textview1.setText(name20);",
                "SketchwareUtil.showMessage(getApplicationContext(), name20);"};
        for (int k = 0; k < typed20.length; k++) l.add(on + 1 + k, "\t\t\t\t" + typed20[k]);
        int mb20 = find(l, "public void _greet(", 0);
        l.add(mb20 + 1, "\t\tString raw20 = \"a,b\";");
        l.add(mb20 + 2, "\t\tedittext1.setText(raw20.replace(\",\", \"\"));");
        l.add(mb20 + 3, "\t\ttextview1.setText(raw20);");
        Object r20 = save(join(l));
        check("review locals: saved", ok(r20), report(r20));
        v = vars();
        check("review locals: final, re-entry, next-line listener, local class stay locals",
                !v.contains("request20") && !v.contains("raw20") && !v.contains("later20") && !v.contains("local20"), v);
        check("review locals: initializeLogic and onClick convert a use after setText", v.contains("2=greet20; ")
                && v.contains("2=name20; "), v);
        String notes20 = String.valueOf(get(r20, "notes"));
        check("review locals: the save says why", notes20.contains("request20 stays a local variable in its code: it is final")
                && notes20.contains("raw20 stays a local variable in its code: a call above this use")
                && notes20.contains("later20 stays a local variable in its code: a listener")
                && notes20.contains("local20 stays a local variable in its code: a listener"), notes20);
        Object r20b = save(code());
        check("review locals: the shown code saves unchanged", ok(r20b) && (Boolean) get(r20b, "unchanged"), report(r20b));
        // which events can run again, and which calls re-run them (AutoVariables.reentry / refires)
        Class<?> av = c("com.sketchware.ai.editor.AutoVariables");
        java.lang.reflect.Method reentry = av.getDeclaredMethod("reentry", String.class, String.class);
        java.lang.reflect.Method refires = av.getDeclaredMethod("refires", String.class, int.class, String.class);
        reentry.setAccessible(true);
        refires.setAccessible(true);
        int listener = (Integer) reentry.invoke(null, "event", "onTextChanged");
        int click = (Integer) reentry.invoke(null, "event", "onClick");
        String kinds = listener + " " + click + " " + reentry.invoke(null, "event", "onSensorChanged") + " "
                + reentry.invoke(null, "onCreate", "initializeLogic") + " " + reentry.invoke(null, "moreBlock", null);
        check("review locals: change events and MoreBlocks can run again, onClick only by performClick",
                kinds.equals("1 2 2 0 1"), kinds);
        String fires = refires.invoke(null, "textview1.setText(q);", listener, "edittext1") + " "
                + refires.invoke(null, "edittext1.setText(q.trim());", listener, "edittext1") + " "
                + refires.invoke(null, "_search(q);", listener, "edittext1") + " "
                + refires.invoke(null, "button1.performClick();", click, "button1") + " "
                + refires.invoke(null, "textview1.setText(q);", click, "button1") + " "
                + refires.invoke(null, "textview1.setText(q);", listener, null) + " "
                + refires.invoke(null, "myedittext1.setText(q);", listener, "edittext1");
        check("review locals: only the event's own view, performClick or a MoreBlock re-run it",
                fires.equals("false true true true false true false"), fires);

        // 21. v1.9.1 (owner's 1 Nov screenshots): values become the Operator / Math / text / List / File blocks that
        // write them, list and file statements their blocks, true / false a true / false block, and
        // Toast.makeText(..., LENGTH_SHORT).show() the Toast block; what no block writes exactly stays add source.
        l = lines(code());
        at = find(l, "button2.setOnClickListener", 0);
        on = find(l, "public void onClick(", at);
        stop = on + 1;
        while (!l.get(stop).trim().equals("}")) stop++;
        for (int k = stop - 1; k > on; k--) l.remove(k);
        String[] typed21 = {
                "score = score + 1;",
                "title = userName.concat(\"!\");",
                "done = score > 3;",
                "textview1.setText(String.valueOf(score));",
                "items.add(\"apple\");",
                "textview1.setText(items.get((int)(0)));",
                "FileUtil.writeFile(\"/sdcard/a.txt\", title);",
                "title = FileUtil.readFile(\"/sdcard/a.txt\");",
                "score = Math.pow(score, 2);",
                "done = title.contains(\"a\");",
                "done = (score > 3) && !done;",
                "checkbox1.setChecked(false);",
                "Toast.makeText(getApplicationContext(), title, Toast.LENGTH_SHORT).show();",
                "Toast.makeText(getApplicationContext(), \"Long\", Toast.LENGTH_LONG).show();",
                "items.clear();",
                "done = checkbox1.isChecked();",
                "store.put(\"k\", title);",
                "title = store.get(\"k\").toString();",
                "score = items.size();",
                "score = title.length();"};
        for (int k = 0; k < typed21.length; k++) l.add(on + 1 + k, "\t\t\t\t" + typed21[k]);
        Object r21 = save(join(l));
        check("value blocks: saved", ok(r21), report(r21));
        all.clear();
        for (Object b : blocks("button2_onClick")) all.put((String) get(b, "id"), b);
        cur = blocks("button2_onClick").isEmpty() ? null : blocks("button2_onClick").get(0);
        ops = new StringBuilder();
        List<Object> chain21 = new ArrayList<>();
        while (cur != null) {
            chain21.add(cur);
            ops.append(get(cur, "opCode")).append(' ');
            int nx = (Integer) get(cur, "nextBlock");
            cur = nx < 0 ? null : all.get(String.valueOf(nx));
        }
        Main.OUT.println("VALUE BLOCKS " + ops);
        // each statement's slot blocks, as opCode(child, child ...)
        StringBuilder tree = new StringBuilder();
        for (Object b : chain21) tree.append(tree21(b, all)).append(" | ");
        Main.OUT.println("VALUE TREE " + tree);
        check("value blocks: one block per statement, the long toast as add source", ops.toString().equals(
                "setVarInt setVarString setVarBoolean setText addListStr setText addSourceDirectly setVarInt "
                        + "setVarBoolean setVarBoolean setChecked doToast addSourceDirectly clearList setVarBoolean mapPut "
                        + "setVarString setVarInt setVarInt "), ops.toString());
        String t21 = tree.toString();
        check("value blocks: operators, text, list, file and math blocks in the slots",
                t21.contains("setVarInt(+(getVar,_)") && t21.contains("setVarString(stringJoin(getVar,_)")
                        && t21.contains("setVarBoolean(>(getVar,_)") && t21.contains("setText(toStringWithDecimal(getVar)")
                        && t21.contains("setText(getAtListStr(_)")
                        && t21.contains("setVarInt(mathPow(getVar,_)") && t21.contains("setVarBoolean(stringContains(getVar,_)")
                        && t21.contains("setVarBoolean(&&(>(getVar,_),not(getVar))") && t21.contains("setChecked(false)")
                        && t21.contains("doToast(getVar)") && t21.contains("setVarBoolean(getChecked())")
                        && t21.contains("mapPut(_,getVar)") && t21.contains("setVarString(mapGet(_))")
                        && t21.contains("setVarInt(lengthList())") && t21.contains("setVarInt(stringLength(getVar))"), t21);
        String regenerated21 = code();
        write("javaedit-21-values.java", regenerated21);
        boolean same21 = true;
        StringBuilder missing21 = new StringBuilder();
        for (String t : typed21) {
            String expect = t.startsWith("Toast.makeText(getApplicationContext(), title")
                    ? "SketchwareUtil.showMessage(getApplicationContext(), title);" : t;
            if (!regenerated21.contains(expect)) {
                same21 = false;
                missing21.append(expect).append(' ');
            }
        }
        check("value blocks: generated again as typed (the short toast as the Toast block's code)", same21, missing21.toString());
        String notes21 = String.valueOf(get(r21, "notes"));
        check("value blocks: the save says the toast became the Toast block", notes21.contains("became the Toast block"), notes21);
        // FileUtil code would be File blocks only if they added nothing: here they would add storage permissions
        Object file21 = chain21.size() > 6 ? chain21.get(6) : null;
        check("value blocks: FileUtil lines stay code (File blocks would add storage permissions) and the save says so",
                file21 != null && "addSourceDirectly".equals(get(file21, "opCode"))
                        && sourceOf(file21).equals("FileUtil.writeFile(\"/sdcard/a.txt\", title);\ntitle = FileUtil.readFile(\"/sdcard/a.txt\");")
                        && notes21.contains("FileUtil lines stay code"), (file21 == null ? "" : sourceOf(file21)) + " " + notes21);
        Object r21b = save(regenerated21);
        check("value blocks: the shown code saves unchanged", ok(r21b) && (Boolean) get(r21b, "unchanged"), report(r21b));
        // blocks saved by v1.8.9-v1.9.0 with a bare "true" in a boolean slot (an empty slot on the canvas) get a true
        // block when the logic editor opens the event (StatementBlocks.literalBooleansToBlocks); the code is the same
        Class<?> sb21 = c("com.sketchware.ai.editor.StatementBlocks");
        ArrayList<Object> old21 = new ArrayList<>();
        Object setBool21 = neu("com.besome.sketch.beans.BlockBean", "4", "set %m.varBool to %b", " ", "", "setVarBoolean");
        @SuppressWarnings("unchecked") List<String> sp21 = (List<String>) get(setBool21, "parameters");
        sp21.add("done");
        sp21.add("true");
        Object text21 = neu("com.besome.sketch.beans.BlockBean", "7", "%m.textview setText %s", " ", "", "setText");
        @SuppressWarnings("unchecked") List<String> tp21 = (List<String>) get(text21, "parameters");
        tp21.add("textview1");
        tp21.add("true");
        old21.add(setBool21);
        old21.add(text21);
        int fixed21 = (Integer) call(sb21, "literalBooleansToBlocks", old21, get(sb21, "ENGLISH_SPECS"));
        Object lit21 = old21.size() == 3 ? old21.get(2) : null;
        check("value blocks: an old bare true in a boolean slot becomes a true block (a text slot keeps its text)",
                fixed21 == 1 && lit21 != null && "true".equals(get(lit21, "opCode")) && "8".equals(get(lit21, "id"))
                        && String.valueOf(sp21).equals("[done, @8]") && String.valueOf(tp21).equals("[textview1, true]"),
                fixed21 + " " + sp21 + " " + tp21);
        // a sum written without the generator's brackets stays code (the blocks would write score + (1 + 2))
        l = lines(code());
        at = find(l, "button2.setOnClickListener", 0);
        on = find(l, "public void onClick(", at);
        l.add(on + 1, "\t\t\t\tscore = score + 1 + 2;");
        Object r21c = save(join(l));
        Object first21 = blocks("button2_onClick").isEmpty() ? null : blocks("button2_onClick").get(0);
        check("value blocks: a sum the blocks can't write exactly stays add source", ok(r21c) && first21 != null
                && "addSourceDirectly".equals(get(first21, "opCode")) && sourceOf(first21).equals("score = score + 1 + 2;"),
                report(r21c) + " " + (first21 == null ? "" : get(first21, "opCode") + " " + sourceOf(first21)));

        // 22. v1.9.1: the other operator, math, text, list and map blocks, one line each
        l = lines(code());
        at = find(l, "button2.setOnClickListener", 0);
        on = find(l, "public void onClick(", at);
        stop = on + 1;
        while (!l.get(stop).trim().equals("}")) stop++;
        for (int k = stop - 1; k > on; k--) l.remove(k);
        String[] typed22 = {
                "score = score - 1;", "score = score * 2;", "score = score / 2;", "score = score % 2;",
                "done = score < 3;", "done = score == 3;", "done = (score > 1) || done;",
                "score = Math.sqrt(score);", "score = Math.max(score, 1);", "score = Math.min(score, 1);",
                "score = Math.abs(score);", "score = Math.round(score);", "score = Math.floor(score);",
                "done = title.equals(\"x\");", "title = title.substring((int)(0), (int)(1));",
                "title = title.replace(\"a\", \"b\");", "title = title.trim();", "title = title.toUpperCase();",
                "title = title.toLowerCase();", "score = title.indexOf(\"a\");", "score = Double.parseDouble(title);",
                "score = items.indexOf(\"apple\");", "done = items.contains(\"apple\");", "done = store.containsKey(\"k\");",
                "items.remove((int)(0));", "store.remove(\"k\");", "store.clear();"};
        for (int k = 0; k < typed22.length; k++) l.add(on + 1 + k, "\t\t\t\t" + typed22[k]);
        Object r22 = save(join(l));
        check("more value blocks: saved", ok(r22), report(r22));
        all.clear();
        for (Object b : blocks("button2_onClick")) all.put((String) get(b, "id"), b);
        cur = blocks("button2_onClick").isEmpty() ? null : blocks("button2_onClick").get(0);
        StringBuilder tree22 = new StringBuilder();
        int count22 = 0;
        boolean source22 = false;
        while (cur != null) {
            count22++;
            if ("addSourceDirectly".equals(get(cur, "opCode"))) source22 = true;
            tree22.append(tree21(cur, all)).append(" | ");
            int nx = (Integer) get(cur, "nextBlock");
            cur = nx < 0 ? null : all.get(String.valueOf(nx));
        }
        Main.OUT.println("VALUE TREE 22 " + tree22);
        String t22 = tree22.toString();
        check("more value blocks: every line a block, none add source", !source22 && count22 == typed22.length
                && t22.contains("setVarInt(-(getVar,_))") && t22.contains("setVarInt(*(getVar,_))") && t22.contains("setVarInt(/(getVar,_))")
                && t22.contains("setVarInt(%(getVar,_))") && t22.contains("setVarBoolean(<(getVar,_))") && t22.contains("setVarBoolean(=(getVar,_))")
                && t22.contains("setVarBoolean(||(>(getVar,_),getVar))") && t22.contains("setVarInt(mathSqrt(getVar))")
                && t22.contains("setVarInt(mathMax(getVar,_))") && t22.contains("setVarInt(mathMin(getVar,_))")
                && t22.contains("setVarInt(mathAbs(getVar))") && t22.contains("setVarInt(mathRound(getVar))") && t22.contains("setVarInt(mathFloor(getVar))")
                && t22.contains("setVarBoolean(stringEquals(getVar,_))") && t22.contains("setVarString(stringSub(getVar,_,_))")
                && t22.contains("setVarString(stringReplace(getVar,_,_))") && t22.contains("setVarString(trim(getVar))")
                && t22.contains("setVarString(toUpperCase(getVar))") && t22.contains("setVarString(toLowerCase(getVar))")
                && t22.contains("setVarInt(stringIndex(_,getVar))") && t22.contains("setVarInt(toNumber(getVar))")
                && t22.contains("setVarInt(indexListStr(_))") && t22.contains("setVarBoolean(containListStr(_))")
                && t22.contains("setVarBoolean(mapContainKey(_))") && t22.contains("deleteList(_)") && t22.contains("mapRemoveKey(_)")
                && t22.contains("mapClear()"), count22 + " " + t22);
        String regenerated22 = code();
        boolean same22 = true;
        StringBuilder missing22 = new StringBuilder();
        for (String t : typed22) {
            if (!regenerated22.contains(t)) {
                same22 = false;
                missing22.append(t).append(' ');
            }
        }
        check("more value blocks: generated again as typed", same22, missing22.toString());
        Object r22b = save(regenerated22);
        check("more value blocks: the shown code saves unchanged", ok(r22b) && (Boolean) get(r22b, "unchanged"), report(r22b));

        // 9. the generated code is now what was saved; an edit to it saves again
        write("javaedit-9-final.java", code());
        Main.OUT.println("AFTER " + state());
        open();
        check("reopens convertible", (Boolean) call(session, "convertible"), String.valueOf(get(session, "problem")));

        Main.OUT.println("JAVAEDIT " + (checks - fails) + "/" + checks + " ok");
        // keep the project as the editor leaves it (the design editor saves to the backup copy) and build it
        call(proj, "k");
        if (build) Main.build(sc);
    }

    /** Opcodes of an event's top chain, in order. */
    static String chainOps(String key) throws Throwable {
        Map<String, Object> all = new HashMap<>();
        for (Object b : blocks(key)) all.put((String) get(b, "id"), b);
        Object cur = blocks(key).isEmpty() ? null : blocks(key).get(0);
        StringBuilder ops = new StringBuilder();
        Set<Object> seen = new HashSet<>();
        while (cur != null && seen.add(cur)) {
            ops.append(get(cur, "opCode")).append(' ');
            int nx = (Integer) get(cur, "nextBlock");
            cur = nx < 0 ? null : all.get(String.valueOf(nx));
        }
        return ops.toString().trim();
    }

    static Object openEvent(String key) throws Throwable {
        return call(c("com.sketchware.ai.editor.EventCode"), "open", Main.CTX, sc, JAVA, key, files, proj, libs);
    }

    /**
     * v1.9.0: the logic editor's "&lt;/&gt;" sheet without its dialog: EventCode opens one event of a javaedit fixture
     * (a fresh one: create javaedit &lt;id&gt; first) the way the sheet does after "Edit", applies edits like "Apply" and
     * checks what was stored, then the activity must still open in the Java editor unchanged, and the project builds.
     * Usage: run.sh <apk> <root> eventcode <sc_id> [nobuild]   (CHECK lines and EVENTCODE n/m ok)
     */
    public static void eventCode(String sc_id, boolean build) throws Throwable {
        sc = sc_id;
        Class<?> jC = c("a.a.a.jC");
        proj = call(jC, "a", sc);
        files = call(jC, "b", sc);
        libs = call(jC, "c", sc);
        // 1. an event with blocks: its body, without the method's indentation; applied unchanged it changes nothing
        Object b1 = openEvent("button1_onClick");
        check("sheet: button1 onClick editable", (Boolean) call(b1, "editable"), String.valueOf(get(b1, "problem")));
        String body = (String) get(b1, "body");
        write("eventcode-button1.txt", body);
        check("sheet: the body, not indented", !body.isEmpty() && !body.startsWith(" ") && !body.startsWith("\t")
                && body.contains("textview1.setText(") && !body.contains("public void onClick"), body);
        String before = state();
        Object r = call(b1, "apply", body);
        check("sheet: unchanged apply changes nothing", ok(r) && (Boolean) get(r, "unchanged") && before.equals(state()), report(r));
        String opsBefore = chainOps("button1_onClick");
        // 2. lines typed at the end: a new String variable and a setText reading it
        r = call(b1, "apply", body + "\nString sheetText = \"From the sheet\";\ntextview1.setText(sheetText);");
        check("sheet: applied", ok(r) && !(Boolean) get(r, "unchanged"), report(r));
        check("sheet: the variable is registered", vars().contains("2=sheetText; "), vars());
        String opsAfter = chainOps("button1_onClick");
        check("sheet: the blocks before stay, set and setText blocks follow", opsAfter.equals(opsBefore + " setVarString setText"),
                opsBefore + " => " + opsAfter);
        // 3. refused: a brace not closed; the problem's line is the sheet's; nothing changes
        Object again = openEvent("button1_onClick");
        String body2 = (String) get(again, "body");
        check("sheet: reopened with the new lines", body2.contains("sheetText = \"From the sheet\";") && body2.contains("textview1.setText(sheetText);"), body2);
        before = state();
        r = call(again, "apply", body2 + "\nif (true) {\ntextview1.setText(\"open\");");
        check("sheet: unbalanced code refused, nothing changed", !ok(r) && before.equals(state()), report(r));
        // 4. an empty event (button2 onClick of a fresh fixture): a statement becomes its block
        Object b2 = openEvent("button2_onClick");
        check("sheet: empty event editable with an empty body", (Boolean) call(b2, "editable") && "".equals(get(b2, "body")),
                get(b2, "problem") + " [" + get(b2, "body") + "]");
        r = call(b2, "apply", "textview1.setText(\"Sheet\");\nedittext1.setText(edittext1.getText().toString());");
        check("sheet: empty event applied", ok(r), report(r));
        check("sheet: empty event's blocks", chainOps("button2_onClick").equals("setText setText"), chainOps("button2_onClick"));
        // 5. a MoreBlock and onCreate
        Object mb = openEvent("greet_moreBlock");
        r = call(mb, "apply", get(mb, "body") + "\nandroid.util.Log.d(\"sheet\", \"more block\");");
        boolean mbStored = false;
        for (Object b : blocks("greet_moreBlock")) {
            mbStored |= "addSourceDirectly".equals(get(b, "opCode")) && sourceOf(b).contains("\"more block\"");
        }
        check("sheet: MoreBlock applied", ok(r) && mbStored, report(r) + " " + chainOps("greet_moreBlock"));
        Object oc = openEvent("onCreate_initializeLogic");
        check("sheet: onCreate editable", (Boolean) call(oc, "editable"), String.valueOf(get(oc, "problem")));
        r = call(oc, "apply", get(oc, "body") + "\nsetTitle(\"Sheet\");");
        check("sheet: onCreate applied, setTitle block", ok(r) && chainOps("onCreate_initializeLogic").endsWith("setTitle"),
                report(r) + " " + chainOps("onCreate_initializeLogic"));
        // 6. the whole activity opens in the Java editor and saves unchanged
        open();
        check("sheet: Java editor opens convertible", (Boolean) call(session, "convertible"), String.valueOf(get(session, "problem")));
        String all = code();
        write("eventcode-final.java", all);
        check("sheet: generated with every applied line", all.contains("private String sheetText = \"\";")
                && all.contains("sheetText = \"From the sheet\";") && all.contains("textview1.setText(\"Sheet\");")
                && all.contains("android.util.Log.d(\"sheet\", \"more block\");") && all.contains("setTitle(\"Sheet\");"), "");
        Object r2 = save(all);
        check("sheet: Java editor saves it unchanged", ok(r2) && (Boolean) get(r2, "unchanged"), report(r2));
        Main.OUT.println("EVENTCODE " + (checks - fails) + "/" + checks + " ok");
        call(proj, "k");
        if (build) Main.build(sc);
    }

    /**
     * Opens every activity of the given projects in the Java editor: the marked generation must be exactly the normal
     * one (convertible) and saving the code unchanged must change nothing. Usage: javaedit-open <id>...
     */
    public static void openAll(String[] ids) throws Throwable {
        Class<?> jC = c("a.a.a.jC");
        int n = 0, good = 0;
        for (String id : ids) {
            Object p = call(jC, "a", id), f = call(jC, "b", id), lb = call(jC, "c", id);
            for (Object file : (List<?>) call(f, "b")) {
                String java = (String) call(file, "getJavaName");
                Object s = call(c("com.sketchware.ai.editor.JavaEditSession"), "open", Main.CTX, id, java, f, p, lb);
                boolean convertible = (Boolean) call(s, "convertible");
                String before = String.valueOf(get(p, "d")).length() + "/" + String.valueOf(get(p, "e"));
                Object r = call(s, "save", (String) get(s, "code"));
                boolean unchanged = (Boolean) get(r, "ok") && (Boolean) get(r, "unchanged");
                String after = String.valueOf(get(p, "d")).length() + "/" + String.valueOf(get(p, "e"));
                boolean same = before.equals(after);
                n++;
                if (convertible && unchanged && same) good++;
                Main.OUT.println("OPEN #" + id + " " + java + " convertible=" + convertible + " unchanged=" + unchanged
                        + " dataSame=" + same + (convertible ? "" : " problem=" + get(s, "problem")));
                if (!convertible) explain(id, file, f, p, lb);
            }
        }
        Main.OUT.println("JAVAEDIT-OPEN " + good + "/" + n + " ok");
    }

    /** Prints where the marked generation (markers taken out) and the normal one part. */
    static void explain(String id, Object file, Object f, Object p, Object lb) throws Throwable {
        Object q = neu("a.a.a.yq", Main.CTX, id);
        call(q, "getFileSrc", (String) call(file, "getJavaName"), f, p, lb);
        Object n = get(q, "N");
        String classic = (String) call(neu("a.a.a.Jx", n, file, p), "formattedCode");
        Class<?> markers = c("com.sketchware.ai.editor.EditMarkers");
        call(markers, "begin");
        String marked = (String) call(neu("a.a.a.Jx", n, file, p), "formattedCode");
        Object session = call(markers, "end");
        Object map = call(c("com.sketchware.ai.editor.EditMap"), "extract", marked, session, classic);
        String code = (String) get(map, "code");
        Main.OUT.println("  extract problem: " + get(map, "problem"));
        String[] a = code.split("\n", -1), b = classic.split("\n", -1);
        int i = 0;
        while (i < a.length && i < b.length && a[i].equals(b[i])) i++;
        for (int k = Math.max(0, i - 4); k < Math.min(Math.max(a.length, b.length), i + 4); k++) {
            Main.OUT.println("  " + (k + 1) + " marked:  " + (k < a.length ? a[k].replace("\r", "\\r").replace("\t", "  ") : "<end>"));
            Main.OUT.println("  " + (k + 1) + " classic: " + (k < b.length ? b[k].replace("\r", "\\r").replace("\t", "  ") : "<end>"));
        }
        String[] m = marked.split("\n", -1);
        int at = 0;
        for (int k = 0; k < m.length; k++) if (m[k].contains("/*@SWED") && k > i - 3) { at = k; break; }
        for (int k = Math.max(0, at - 6); k < Math.min(m.length, at + 10); k++) Main.OUT.println("  raw " + (k + 1) + ": " + m[k].replace("\r", "\\r").replace("\t", "  "));
    }

    static void write(String name, String text) throws Exception {
        File out = new File(System.getProperty("h.root"), name);
        java.nio.file.Files.write(out.toPath(), text.getBytes("UTF-8"));
        Main.OUT.println("wrote " + out);
    }
}
