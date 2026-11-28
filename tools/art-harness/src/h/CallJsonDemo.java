package h;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * v2.1.6: the GitHub + JSON Live Demo's event code (fixture 937, made by the app's own JsonSample) replayed with the
 * built demo APK's own SketchJson and SketchGitHub classes on ART (Android's org.json): the statements below are the
 * generated MainActivity's onUserLoaded / onReposListed lines, copied as the generator wrote them, run on a hand-made
 * answer in the shape of GitHub's GET /users/octocat and GET /users/octocat/repos (the build machine can't reach
 * api.github.com). JSONDEMO n/n ok.
 */
public class CallJsonDemo {
    static int checks, fails;
    static Class<?> json, github;

    static void check(String what, boolean ok, Object got) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + ": " + got);
    }

    static Object call(Class<?> k, String name, Object... args) throws Exception {
        for (Method m : k.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            m.setAccessible(true);
            return m.invoke(null, args);
        }
        throw new NoSuchMethodException(name + "/" + args.length);
    }

    static String s(String name, Object... args) throws Exception {
        return (String) call(json, name, args);
    }

    static double d(String name, Object... args) throws Exception {
        return (Double) call(json, name, args);
    }

    public static void main(String[] a) throws Exception {
        String pkg = a.length > 0 ? a[0] : "com.my.githubjson";
        json = Class.forName(pkg + ".SketchJson");
        github = Class.forName(pkg + ".SketchGitHub");

        // onUserLoaded(_login, _name, _avatarUrl, _json): GitHub's own answer is _json
        String _json = "{\"login\":\"octocat\",\"id\":583231,\"node_id\":\"MDQ6VXNlcjU4MzIzMQ==\","
                + "\"avatar_url\":\"https://avatars.githubusercontent.com/u/583231?v=4\",\"type\":\"User\","
                + "\"name\":\"The Octocat\",\"company\":\"@github\",\"blog\":\"https://github.blog\","
                + "\"location\":\"San Francisco\",\"email\":null,\"bio\":null,\"public_repos\":8,\"public_gists\":8,"
                + "\"followers\":21000,\"following\":9,\"created_at\":\"2011-01-25T18:44:36Z\"}";
        String _login = "octocat";
        String profile;
        profile = "";
        profile = s("setText", profile, "login", _login);
        profile = s("setText", profile, "name", s("getText", _json, "name"));
        profile = s("setNumber", profile, "stats.followers", d("getNumber", _json, "followers"));
        profile = s("setNumber", profile, "stats.public_repos", d("getNumber", _json, "public_repos"));
        profile = s("setText", profile, "location", s("getText", _json, "location"));
        check("profile built with the set blocks (nested stats, whole numbers)",
                ("{\"login\":\"octocat\",\"name\":\"The Octocat\",\"stats\":{\"followers\":21000,\"public_repos\":8},"
                        + "\"location\":\"San Francisco\"}").equals(profile), profile);
        String textview2 = s("getText", profile, "name").concat("  ·  @".concat(s("getText", profile, "login")
                .concat("\nFollowers: ".concat(String.valueOf((long) (d("getNumber", profile, "stats.followers")))
                        .concat("  ·  Public repositories: ".concat(String.valueOf((long) (d("getNumber", profile,
                                "stats.public_repos")))))))));
        check("summary read back from the built JSON",
                "The Octocat  ·  @octocat\nFollowers: 21000  ·  Public repositories: 8".equals(textview2), textview2);
        String textview4 = s("pretty", profile);
        check("pretty JSON for the panel", textview4.startsWith("{\n  \"login\": \"octocat\",\n  \"name\": \"The Octocat\","
                + "\n  \"stats\": {\n    \"followers\": 21000,"), textview4);

        // a profile without a name or location (null in GitHub's answer): empty text, never an error
        String bare = "{\"login\":\"ghost\",\"name\":null,\"location\":null,\"public_repos\":0,\"followers\":0}";
        String p2 = "";
        p2 = s("setText", p2, "login", "ghost");
        p2 = s("setText", p2, "name", s("getText", bare, "name"));
        p2 = s("setNumber", p2, "stats.followers", d("getNumber", bare, "followers"));
        p2 = s("setText", p2, "location", s("getText", bare, "location"));
        check("null name / location: empty texts", "{\"login\":\"ghost\",\"name\":\"\",\"stats\":{\"followers\":0},"
                + "\"location\":\"\"}".equals(p2), p2);

        // onReposListed(_user, _reposJson, _count): the component's list (SketchGitHub.repoSummary of GitHub's answer)
        String raw = "[{\"name\":\"Spoon-Knife\",\"full_name\":\"octocat/Spoon-Knife\",\"private\":false,"
                + "\"html_url\":\"https://github.com/octocat/Spoon-Knife\",\"description\":\"This repo is for demonstration "
                + "purposes only.\",\"stargazers_count\":13000,\"language\":\"HTML\"},{\"name\":\"Hello-World\","
                + "\"full_name\":\"octocat/Hello-World\",\"private\":false,\"html_url\":\"https://github.com/octocat/"
                + "Hello-World\",\"description\":\"My first repository on GitHub!\",\"stargazers_count\":3000,"
                + "\"language\":null},{\"name\":\"linguist\",\"full_name\":\"octocat/linguist\",\"private\":false,"
                + "\"html_url\":\"https://github.com/octocat/linguist\",\"description\":null,\"stargazers_count\":200,"
                + "\"language\":\"Ruby\"}]";
        String _reposJson = (String) call(github, "repoSummary", raw);
        String _user = "octocat";
        double _count = 3;
        String summary;
        double i = 0;
        ArrayList<String> repoNames = new ArrayList<>();
        summary = "";
        summary = s("setText", summary, "user", _user);
        summary = s("setNumber", summary, "count", _count);
        i = 0;
        for (int _repeat14 = 0; _repeat14 < (int) (d("count", _reposJson, "")); _repeat14++) {
            summary = s("setText", summary, "names[]", s("getText", _reposJson, "[".concat(String.valueOf((long) (i))
                    .concat("].name"))));
            i++;
        }
        summary = s("setNumber", summary, "first_stars", d("getNumber", _reposJson, "[0].stars"));
        call(json, "toTextList", summary, "names", repoNames);
        check("repeat over JSON count, JSON text at [i].name appended to names[]",
                ("{\"user\":\"octocat\",\"count\":3,\"names\":[\"Spoon-Knife\",\"Hello-World\",\"linguist\"],"
                        + "\"first_stars\":13000}").equals(summary), summary);
        check("JSON array to list: the ListView's rows", repoNames.toString().equals("[Spoon-Knife, Hello-World, linguist]"),
                repoNames);
        String toast = "Stars of the newest repository: ".concat(String.valueOf((long) (d("getNumber", summary, "first_stars"))));
        check("JSON number for the Toast", "Stars of the newest repository: 13000".equals(toast), toast);
        String line = String.valueOf((long) (repoNames.size())).concat(" repository names: JSON text at [i].name, then JSON "
                + "array to list");
        check("the count line", line.startsWith("3 repository names"), line);

        // a user without repositories: the loop runs 0 times, the list is emptied
        summary = "";
        repoNames.add("left over");
        summary = s("setText", summary, "user", "nobody");
        i = 0;
        for (int _repeat14 = 0; _repeat14 < (int) (d("count", "[]", "")); _repeat14++) {
            summary = s("setText", summary, "names[]", s("getText", "[]", "[".concat(String.valueOf((long) (i)).concat("].name"))));
            i++;
        }
        call(json, "toTextList", summary, "names", repoNames);
        check("no repositories: no rows, no error", repoNames.isEmpty() && "{\"user\":\"nobody\"}".equals(summary), summary);

        System.out.println("JSONDEMO " + (checks - fails) + "/" + checks + (fails == 0 ? " ok" : " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
