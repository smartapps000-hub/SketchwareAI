package h;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * v2.1.5: the JSON power pack's generated SketchJson from fixture 936's built APK, on ART with Android's own org.json
 * (libcore): paths, readers, setters, lists and maps. JSONCALL n/n ok.
 */
public class CallJson {
    static int checks, fails;
    static Class<?> k;

    static void check(String what, boolean ok, Object got) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + ": " + got);
    }

    static Object call(String name, Object... args) throws Exception {
        for (Method m : k.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            return m.invoke(null, args);
        }
        throw new NoSuchMethodException(name + "/" + args.length);
    }

    static String s(String name, Object... args) throws Exception {
        return (String) call(name, args);
    }

    public static void main(String[] a) throws Exception {
        k = Class.forName(a.length > 0 ? a[0] : "com.fixture.json.SketchJson");
        String j = "{\"user\":{\"name\":\"Ravi\",\"age\":21,\"score\":9.5,\"vip\":true,\"site\":\"https://x.io/a\","
                + "\"note\":null,\"a.b\":\"dots\"},\"items\":[{\"title\":\"One\",\"n\":1},{\"title\":\"Two\",\"n\":2.0},"
                + "{\"title\":\"Three\",\"tags\":[\"x\",\"y\"]}],\"count\":\"12\"}";
        check("text by path, [i], .i, [-1], quoted key", "Ravi".equals(s("getText", j, "user.name"))
                && "Two".equals(s("getText", j, "items[1].title")) && "Two".equals(s("getText", j, "items.1.title"))
                && "Three".equals(s("getText", j, "items[-1].title")) && "dots".equals(s("getText", j, "user['a.b']")),
                s("getText", j, "items[-1].title"));
        check("whole numbers without .0", "21".equals(s("getText", j, "user.age")) && "2".equals(s("getText", j, "items[1].n"))
                && "9.5".equals(s("getText", j, "user.score")), s("getText", j, "items[1].n"));
        check("number, also from text; boolean", (Double) call("getNumber", j, "count") == 12
                && (Boolean) call("getBoolean", j, "user.vip") && !(Boolean) call("getBoolean", j, "zz"), null);
        check("missing: empty text, has false; null value: has true", "".equals(s("getText", j, "a.b.c"))
                && "".equals(s("getText", "not json", "x")) && !(Boolean) call("has", j, "user.email")
                && (Boolean) call("has", j, "user.note"), null);
        check("slashes readable in parts", s("getJson", j, "user").contains("https://x.io/a"), s("getJson", j, "user"));
        check("type and count", "array".equals(s("type", j, "items")) && "invalid".equals(s("type", "{x", ""))
                && (Double) call("count", j, "items") == 3, s("type", j, "items"));
        check("valid JSON (Android's lenient reader made strict)", (Boolean) call("isValid", j)
                && (Boolean) call("isValid", "12") && !(Boolean) call("isValid", "hello")
                && !(Boolean) call("isValid", "{\"a\":1} x") && !(Boolean) call("isValid", "12abc"), null);
        String pretty = s("pretty", "{\"a\":[1,2],\"u\":\"http://x/y\"}");
        check("pretty", pretty.startsWith("{\n  \"a\": [\n    1,") && pretty.contains("http://x/y"), pretty);
        String b = "";
        b = s("setText", b, "user.name", "Ravi");
        b = s("setNumber", b, "user.age", 21.0);
        b = s("setBoolean", b, "user.vip", true);
        b = s("setText", b, "tags[]", "a");
        b = s("setJson", b, "items", "[{\"n\":1}]");
        b = s("setNumber", b, "items[0].n", 5.0);
        check("building from empty text", ("{\"user\":{\"name\":\"Ravi\",\"age\":21,\"vip\":true},\"tags\":[\"a\"],"
                + "\"items\":[{\"n\":5}]}").equals(b), b);
        check("text that isn't JSON never overwritten", "oops".equals(s("setText", "oops", "a", "b")), null);
        String removed = s("remove", j, "items[1]");
        check("remove an item (gap closed), a key", (Double) call("count", removed, "items") == 2
                && "Three".equals(s("getText", removed, "items[1].title"))
                && !(Boolean) call("has", s("remove", j, "user.age"), "user.age"), removed);
        ArrayList<String> keys = new ArrayList<>();
        call("keys", j, "user", keys);
        check("keys in order", keys.toString().equals("[name, age, score, vip, site, note, a.b]"), keys);
        ArrayList<HashMap<String, Object>> rows = new ArrayList<>();
        call("toMapList", j, "items", rows);
        check("list map: numbers as Double, nested list", rows.size() == 3 && "One".equals(rows.get(0).get("title"))
                && Double.valueOf(1).equals(rows.get(0).get("n")) && rows.get(2).get("tags") instanceof ArrayList, rows);
        HashMap<String, Object> map = new HashMap<>();
        call("toMap", j, "user", map);
        check("map: null as empty text", "Ravi".equals(map.get("name")) && "".equals(map.get("note")), map);
        ArrayList<Double> nums = new ArrayList<>();
        call("toNumberList", "{\"s\":[1,2.5,\"3\"]}", "s", nums);
        check("number list", nums.toString().equals("[1.0, 2.5, 3.0]"), nums);
        System.out.println("JSONCALL " + (checks - fails) + "/" + checks + (fails == 0 ? " ok" : " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
