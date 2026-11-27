import java.util.ArrayList;
import java.util.HashMap;

/**
 * v2.1.5: the generated SketchJson helper (JSON power pack) on the JVM with AOSP's org.json sources (libcore/json, the
 * code Android runs). Compile the helper from {@code SketchJson.java.txt} with its package line replaced by
 * {@code package t;}, AOSP's org/json/*.java and this file with javac --release 8, then run SketchJsonTest.
 * Prints "SKETCHJSON n/n ok".
 */
public class SketchJsonTest {
    static int ok, total;

    static void check(String what, boolean pass, Object seen) {
        total++;
        if (pass) ok++;
        System.out.println("JSON CHECK " + (pass ? "ok  " : "FAIL") + " " + what + (pass ? "" : " - " + seen));
    }

    public static void main(String[] args) {
        String j = "{\"user\":{\"name\":\"Ravi\",\"age\":21,\"score\":9.5,\"vip\":true,\"site\":\"https://x.io/a\","
                + "\"note\":null,\"a.b\":\"dots\"},\"items\":[{\"title\":\"One\",\"n\":1},{\"title\":\"Two\",\"n\":2.0},"
                + "{\"title\":\"Three\",\"tags\":[\"x\",\"y\"]}],\"empty\":[],\"count\":\"12\"}";
        check("text by path", "Ravi".equals(t.SketchJson.getText(j, "user.name")), t.SketchJson.getText(j, "user.name"));
        check("$ prefix and spaces", "Ravi".equals(t.SketchJson.getText(j, " $.user.name ")), null);
        check("array item by [i] and by .i", "Two".equals(t.SketchJson.getText(j, "items[1].title"))
                && "Two".equals(t.SketchJson.getText(j, "items.1.title")), null);
        check("last item [-1]", "Three".equals(t.SketchJson.getText(j, "items[-1].title")), null);
        check("quoted key with dots", "dots".equals(t.SketchJson.getText(j, "user['a.b']"))
                && "dots".equals(t.SketchJson.getText(j, "user[\"a.b\"]")), null);
        check("whole numbers without .0", "21".equals(t.SketchJson.getText(j, "user.age"))
                && "2".equals(t.SketchJson.getText(j, "items[1].n")) && "9.5".equals(t.SketchJson.getText(j, "user.score")),
                t.SketchJson.getText(j, "items[1].n"));
        check("numbers, also from text", t.SketchJson.getNumber(j, "user.age") == 21
                && t.SketchJson.getNumber(j, "count") == 12 && t.SketchJson.getNumber(j, "nothing") == 0, null);
        check("booleans", t.SketchJson.getBoolean(j, "user.vip") && !t.SketchJson.getBoolean(j, "nothing")
                && t.SketchJson.getBoolean(j, "items[0].n"), null);
        check("missing parts read as empty / 0 / false, never throw", "".equals(t.SketchJson.getText(j, "a.b.c[9]"))
                && "".equals(t.SketchJson.getText("not json", "x")) && "".equals(t.SketchJson.getText(null, null))
                && t.SketchJson.getNumber(null, "x") == 0 && "".equals(t.SketchJson.getText(j, "items[99]"))
                && "".equals(t.SketchJson.getText(j, "items[x")), null);
        check("null value: empty text, has() true", "".equals(t.SketchJson.getText(j, "user.note"))
                && t.SketchJson.has(j, "user.note") && !t.SketchJson.has(j, "user.email"), null);
        check("slashes readable", "https://x.io/a".equals(t.SketchJson.getText(j, "user.site"))
                && t.SketchJson.getJson(j, "user").contains("https://x.io/a"), t.SketchJson.getJson(j, "user"));
        check("part as JSON", "[\"x\",\"y\"]".equals(t.SketchJson.getJson(j, "items[2].tags"))
                && "\"Ravi\"".equals(t.SketchJson.getJson(j, "user.name")) && "".equals(t.SketchJson.getJson(j, "zz")),
                t.SketchJson.getJson(j, "items[2].tags"));
        check("count", t.SketchJson.count(j, "items") == 3 && t.SketchJson.count(j, "user") == 7
                && t.SketchJson.count(j, "empty") == 0 && t.SketchJson.count(j, "zz") == 0, t.SketchJson.count(j, "user"));
        check("type", "object".equals(t.SketchJson.type(j, "")) && "array".equals(t.SketchJson.type(j, "items"))
                && "text".equals(t.SketchJson.type(j, "user.name")) && "number".equals(t.SketchJson.type(j, "user.age"))
                && "boolean".equals(t.SketchJson.type(j, "user.vip")) && "null".equals(t.SketchJson.type(j, "user.note"))
                && "missing".equals(t.SketchJson.type(j, "x")) && "invalid".equals(t.SketchJson.type("{x", "")), null);
        check("valid JSON", t.SketchJson.isValid(j) && t.SketchJson.isValid("[1,2]") && t.SketchJson.isValid("\"a\"")
                && t.SketchJson.isValid("12") && t.SketchJson.isValid("true") && t.SketchJson.isValid("null")
                && !t.SketchJson.isValid("hello") && !t.SketchJson.isValid("{\"a\":1} x") && !t.SketchJson.isValid("")
                && !t.SketchJson.isValid(null) && !t.SketchJson.isValid("{\"a\":") && !t.SketchJson.isValid("nope")
                && !t.SketchJson.isValid("-x") && !t.SketchJson.isValid("12abc"), null);
        String pretty = t.SketchJson.pretty("{\"a\":[1,2],\"u\":\"http://x/y\"}");
        check("pretty", pretty.startsWith("{\n  \"a\": [\n    1,") && pretty.contains("http://x/y")
                && "plain".equals(t.SketchJson.pretty("plain")), pretty);

        String s = "";
        s = t.SketchJson.setText(s, "user.name", "Ravi");
        s = t.SketchJson.setNumber(s, "user.age", 21);
        s = t.SketchJson.setNumber(s, "user.score", 9.5);
        s = t.SketchJson.setBoolean(s, "user.vip", true);
        s = t.SketchJson.setText(s, "tags[]", "a");
        s = t.SketchJson.setText(s, "tags[]", "b");
        s = t.SketchJson.setJson(s, "items", "[{\"n\":1}]");
        s = t.SketchJson.setNumber(s, "items[0].n", 5);
        s = t.SketchJson.setText(s, "items[1].title", "new");
        check("building JSON from empty text, missing parts made, [] adds",
                ("{\"user\":{\"name\":\"Ravi\",\"age\":21,\"score\":9.5,\"vip\":true},\"tags\":[\"a\",\"b\"],"
                        + "\"items\":[{\"n\":5},{\"title\":\"new\"}]}").equals(s), s);
        String arr = t.SketchJson.setText("", "[0].name", "first");
        check("empty text with a position starts an array", "[{\"name\":\"first\"}]".equals(arr), arr);
        check("text that isn't JSON is never overwritten", "oops".equals(t.SketchJson.setText("oops", "a", "b"))
                && "5".equals(t.SketchJson.setText("5", "a", "b")), null);
        check("a key on an array or [] on an object changes nothing",
                "[1]".equals(t.SketchJson.setText("[1]", "name", "x"))
                        && "{\"a\":1}".equals(t.SketchJson.setText("{\"a\":1}", "[]", "x")), null);
        check("setJson with text that isn't JSON puts text", "{\"a\":\"hello\"}".equals(t.SketchJson.setJson("{}", "a", "hello")),
                t.SketchJson.setJson("{}", "a", "hello"));
        check("whole number written without .0, NaN as null", "{\"a\":3,\"b\":null}".equals(
                t.SketchJson.setNumber(t.SketchJson.setNumber("{}", "a", 3.0), "b", Double.NaN)), null);
        check("empty path: the value itself", "\"x\"".equals(t.SketchJson.setText("{}", "", "x")), null);
        check("far past the end refused (no huge padding)", "[]".equals(t.SketchJson.setText("[]", "[5000]", "x"))
                && "[null,\"x\"]".equals(t.SketchJson.setText("[]", "[1]", "x")), t.SketchJson.setText("[]", "[1]", "x"));
        String removed = t.SketchJson.remove(j, "items[1]");
        check("remove an array item (gap closed)", t.SketchJson.count(removed, "items") == 2
                && "Three".equals(t.SketchJson.getText(removed, "items[1].title")), removed);
        check("remove a key, a nested item, the root array's item, a missing path",
                !t.SketchJson.has(t.SketchJson.remove(j, "user.age"), "user.age")
                        && t.SketchJson.count(t.SketchJson.remove(j, "items[2].tags[0]"), "items[2].tags") == 1
                        && "[2]".equals(t.SketchJson.remove("[1,2]", "[0]"))
                        && j.equals(t.SketchJson.remove(j, "nope.x")) && "x".equals(t.SketchJson.remove("x", "a")), null);

        ArrayList<String> keys = new ArrayList<String>();
        keys.add("old");
        t.SketchJson.keys(j, "user", keys);
        check("keys in order (list emptied first)", keys.toString().equals("[name, age, score, vip, site, note, a.b]"), keys);
        t.SketchJson.keys(j, "items", keys);
        check("keys of an array: positions", keys.toString().equals("[0, 1, 2]"), keys);
        ArrayList<String> texts = new ArrayList<String>();
        t.SketchJson.toTextList(j, "items[2].tags", texts);
        check("array to text list", texts.toString().equals("[x, y]"), texts);
        ArrayList<Double> nums = new ArrayList<Double>();
        t.SketchJson.toNumberList("{\"s\":[1,2.5,\"3\"]}", "s", nums);
        check("array to number list", nums.toString().equals("[1.0, 2.5, 3.0]"), nums);
        ArrayList<HashMap<String, Object>> rows = new ArrayList<HashMap<String, Object>>();
        t.SketchJson.toMapList(j, "items", rows);
        check("array of objects to list map (numbers as Double, nested lists)", rows.size() == 3
                && "One".equals(rows.get(0).get("title")) && Double.valueOf(1).equals(rows.get(0).get("n"))
                && rows.get(2).get("tags") instanceof ArrayList, rows);
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("stale", "x");
        t.SketchJson.toMap(j, "user", map);
        check("object to map (null as empty text, emptied first)", "Ravi".equals(map.get("name"))
                && "".equals(map.get("note")) && !map.containsKey("stale") && Double.valueOf(21).equals(map.get("age")), map);
        t.SketchJson.toMap("[]", "", map);
        t.SketchJson.toTextList(null, null, texts);
        t.SketchJson.keys(j, "x", null);
        check("lists and maps from missing parts: empty, no throw", map.isEmpty() && texts.isEmpty(), null);
        System.out.println("SKETCHJSON " + ok + "/" + total + (ok == total ? " ok" : " FAILED"));
        System.exit(ok == total ? 0 : 1);
    }
}
