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
