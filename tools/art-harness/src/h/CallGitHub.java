package h;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * v2.0.2: the GitHub component's helper SketchGitHub from fixture 934's built APK on ART: path encoding, Base64 both
 * ways, the repositories summary, the error explanations (the "diagnostics"), the token switch and the Listener the
 * generated code extends. The network calls themselves can't run here (no sockets on this ART). GITHUBCALL n/n ok.
 */
public class CallGitHub {
    static int checks, fails;

    static void check(String what, boolean ok, Object got) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + ": " + got);
    }

    public static void main(String[] a) throws Exception {
        Class<?> gh = Class.forName(a.length > 0 ? a[0] : "com.fixture.github.SketchGitHub");
        Method encodePath = method(gh, "encodePath", String.class);
        Object path = encodePath.invoke(null, "docs/note \"one\".txt");
        check("path parts encoded, slashes kept", "docs/note%20%22one%22.txt".equals(path), path);
        Object seg = method(gh, "segment", String.class).invoke(null, "a b+c/d");
        check("one segment: space %20, plus and slash encoded", "a%20b%2Bc%2Fd".equals(seg), seg);
        Method to64 = method(gh, "toBase64", String.class);
        Method from64 = method(gh, "fromBase64", String.class);
        String text = "Hello \u00e9 \u0c24\u0c46\u0c32\u0c41\u0c17\u0c41\nline 2";
        Object b64 = to64.invoke(null, text);
        check("Base64 without line breaks", b64 instanceof String && !((String) b64).contains("\n"), b64);
        check("Base64 round trip (UTF-8, Telugu)", text.equals(from64.invoke(null, b64)), from64.invoke(null, b64));
        String wrapped = ((String) b64).substring(0, 8) + "\n" + ((String) b64).substring(8);
        check("GitHub's content with line breaks decodes", text.equals(from64.invoke(null, wrapped)), null);

        String repos = "[{\"name\":\"app\",\"full_name\":\"me/app\",\"description\":null,\"html_url\":\"https://github.com/me/app\","
                + "\"private\":true,\"stargazers_count\":3,\"language\":\"Java\",\"owner\":{\"login\":\"me\"}},"
                + "{\"name\":\"docs\",\"full_name\":\"me/docs\",\"description\":\"Notes\",\"html_url\":\"https://github.com/me/docs\","
                + "\"private\":false,\"stargazers_count\":0,\"language\":null}]";
        Object summary = method(gh, "repoSummary", String.class).invoke(null, repos);
        String s = String.valueOf(summary);
        check("repositories summary: name, fullName, url, isPrivate, stars, language; null description empty",
                s.contains("\"fullName\":\"me\\/app\"") || s.contains("\"fullName\":\"me/app\""), s);
        check("summary drops other fields (owner)", !s.contains("owner"), s);
        check("summary keeps private and stars", s.contains("\"isPrivate\":true") && s.contains("\"stars\":3")
                && s.contains("\"description\":\"\""), s);

        Class<?> answer = Class.forName(gh.getName() + "$Answer");
        Constructor<?> make = answer.getDeclaredConstructor();
        make.setAccessible(true);
        Method explain = method(gh, "explain", answer, String.class);
        check("no network: check the internet", explain(explain, make, 0, "timeout", -1, 0, "getUser").startsWith("No connection to GitHub"),
                explain(explain, make, 0, "timeout", -1, 0, "getUser"));
        check("401: the token is wrong, expired or revoked", explain(explain, make, 401, "{\"message\":\"Bad credentials\"}", -1, 0, "getUser")
                .contains("wrong, expired or revoked"), null);
        String limit = explain(explain, make, 403, "{\"message\":\"API rate limit exceeded\"}", 0, 1893456000L, "listRepos");
        check("403 with the limit used up: when to try again, 60 vs 5000", limit.contains("try again after")
                && limit.contains("60") && limit.contains("5000"), limit);
        String access = explain(explain, make, 403, "{\"message\":\"Resource not accessible by personal access token\"}", 4999, 0, "uploadFile");
        check("403 without the limit (upload): Contents read and write", access.contains("Contents: read and write"), access);
        String notFound = explain(explain, make, 404, "{\"message\":\"Not Found\"}", -1, 0, "getFile");
        check("404 (file): owner, repository and path; private needs a token", notFound.contains("repository and path")
                && notFound.contains("private"), notFound);
        String workflow = explain(explain, make, 422, "{\"message\":\"Workflow does not have 'workflow_dispatch' trigger\"}", -1, 0, "triggerWorkflow");
        check("422 (workflow): workflow_dispatch and branch", workflow.contains("workflow_dispatch trigger and the branch"), workflow);
        check("5xx: GitHub's problem, later", explain(explain, make, 503, "", -1, 0, "getUser").contains("Try again later"), null);

        Class<?> listener = Class.forName(gh.getName() + "$Listener");
        int methods = 0;
        for (Method m : listener.getDeclaredMethods()) if (Modifier.isPublic(m.getModifiers())) methods++;
        check("Listener: an abstract class with the six events (generated code overrides the ones it uses)",
                Modifier.isAbstract(listener.getModifiers()) && methods == 6, methods);
        Object component = gh.getConstructor(Object.class).newInstance(new Object[]{null});
        Method setToken = gh.getMethod("setToken", String.class);
        Method hasToken = gh.getMethod("hasToken");
        setToken.invoke(component, "   ");
        boolean blank = (Boolean) hasToken.invoke(component);
        setToken.invoke(component, " github_pat_x ");
        Field tokenField = gh.getDeclaredField("token");
        tokenField.setAccessible(true);
        check("a blank token is no token; a token is trimmed", !blank && (Boolean) hasToken.invoke(component)
                && "github_pat_x".equals(tokenField.get(component)), tokenField.get(component));
        System.out.println("GITHUBCALL " + (checks - fails) + "/" + checks + (fails == 0 ? " ok" : " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }

    static Method method(Class<?> c, String name, Class<?>... types) throws Exception {
        Method m = c.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    static String explain(Method explain, Constructor<?> make, int code, String body, int remaining, long reset, String action)
            throws Exception {
        Object a = make.newInstance();
        set(a, "code", code);
        set(a, "body", body);
        set(a, "remaining", remaining);
        set(a, "reset", reset);
        return (String) explain.invoke(null, a, action);
    }

    static void set(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }
}
