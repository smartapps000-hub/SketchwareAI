import com.sketchware.ai.library.MavenResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Library Resolver against the live repositories (Google Maven, Maven Central, JitPack), on the desktop JVM: what each
 * coordinate needs, what is built in, and the version choices. Also fills the cache folder the Android-runtime fixture
 * uses offline.
 * Run: javac -d out tools/jvm-tests/MavenResolverTest.java app/src/main/java/com/sketchware/ai/library/{MavenResolver,
 * MavenFetcher,BuiltInCoordinates}.java && java -cp out MavenResolverTest <cache dir>
 */
public class MavenResolverTest {
    static int fails = 0;

    static void check(String name, boolean ok, Object detail) {
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + name + (detail == null ? "" : "  [" + detail + "]"));
    }

    static List<String> coords(List<MavenResolver.Artifact> list) {
        List<String> out = new ArrayList<>();
        for (MavenResolver.Artifact a : list) out.add(a.coordinate() + (a.builtIn != null ? "" : " " + a.packaging));
        return out;
    }

    public static void main(String[] args) throws Exception {
        File cache = new File(args.length > 0 ? args[0] : "out/maven-cache");
        MavenResolver resolver = new MavenResolver(MavenResolver.defaultRepositories(), cache, null);

        // offline POMs: an exclusion of everything ("*:*", Gradle's transitive = false) and a dependency without groupId
        java.util.Map<String, String> poms = new java.util.HashMap<>();
        poms.put("t/root/1/root-1.pom", "<project><groupId>t</groupId><artifactId>root</artifactId><version>1</version><packaging>aar</packaging>"
                + "<dependencies><dependency><groupId>t</groupId><artifactId>a</artifactId><version>1</version>"
                + "<exclusions><exclusion><groupId>*</groupId><artifactId>*</artifactId></exclusion></exclusions></dependency>"
                + "<dependency><artifactId>nogroup</artifactId><version>1</version></dependency></dependencies></project>");
        poms.put("t/a/1/a-1.pom", "<project><groupId>t</groupId><artifactId>a</artifactId><version>1</version>"
                + "<dependencies><dependency><groupId>t</groupId><artifactId>b</artifactId><version>1</version></dependency></dependencies></project>");
        poms.put("t/b/1/b-1.pom", "<project><groupId>t</groupId><artifactId>b</artifactId><version>1</version></project>");
        java.util.List<String[]> fakeRepo = new java.util.ArrayList<>();
        fakeRepo.add(new String[]{"fake", "https://fake.invalid/m2"});
        MavenResolver offline = new MavenResolver(fakeRepo, null, url -> {
            String key = url.substring("https://fake.invalid/m2/".length());
            return poms.containsKey(key) ? poms.get(key).getBytes("UTF-8") : null;
        });
        MavenResolver.Plan x = offline.resolve("t:root:1");
        check("*:* exclusion: root and a, not b", coords(x.artifacts).toString().equals("[t:root:1 aar, t:a:1 jar]"), coords(x.artifacts) + " " + x.problems);
        check("dependency without groupId: a problem, no crash", x.problems.toString().contains("without groupId"), x.problems);

        // version order
        check("1.10 > 1.9", MavenResolver.compareVersions("1.10", "1.9") > 0, null);
        check("1.0 == 1", MavenResolver.compareVersions("1.0", "1") == 0, null);
        check("1.0 > 1.0-beta01", MavenResolver.compareVersions("1.0", "1.0-beta01") > 0, null);
        check("1.0-rc1 > 1.0-alpha2", MavenResolver.compareVersions("1.0-rc1", "1.0-alpha2") > 0, null);
        check("2.9.0 > 2.8.5", MavenResolver.compareVersions("2.9.0", "2.8.5") > 0, null);
        check("stable 1.2.0, not 1.3.0-alpha01", MavenResolver.stable("1.2.0") && !MavenResolver.stable("1.3.0-alpha01")
                && !MavenResolver.stable("2.0.0-rc02") && !MavenResolver.stable("1.0-SNAPSHOT"), null);

        // Retrofit's Gson converter: retrofit + converter, OkHttp/Okio/Gson built in
        MavenResolver.Plan p = resolver.resolve("com.squareup.retrofit2:converter-gson:2.9.0");
        System.out.println("converter-gson: " + coords(p.artifacts) + " built in " + coords(p.builtIn) + " notes " + p.notes + " problems " + p.problems);
        check("converter-gson: retrofit + converter-gson to install", coords(p.artifacts).contains("com.squareup.retrofit2:retrofit:2.9.0 jar")
                && coords(p.artifacts).contains("com.squareup.retrofit2:converter-gson:2.9.0 jar") && p.artifacts.size() == 2, coords(p.artifacts));
        check("converter-gson: okhttp + gson built in", coords(p.builtIn).toString().contains("com.squareup.okhttp3:okhttp")
                && coords(p.builtIn).toString().contains("com.google.code.gson:gson"), coords(p.builtIn));
        check("converter-gson: no problems", p.problems.isEmpty(), p.problems);

        // a built-in AndroidX library: nothing to install
        p = resolver.resolve("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0");
        check("swiperefreshlayout: built in", p.artifacts.isEmpty() && p.builtIn.size() == 1, coords(p.builtIn));
        // an AndroidX library that isn't built in: the aar, its AndroidX needs built in
        p = resolver.resolve("androidx.preference:preference:1.2.1");
        System.out.println("preference: " + coords(p.artifacts) + " built in " + coords(p.builtIn) + " notes " + p.notes + " problems " + p.problems);
        check("preference: the aar to install, AndroidX needs built in", coords(p.artifacts).contains("androidx.preference:preference:1.2.1 aar")
                && p.builtIn.size() >= 3 && p.problems.isEmpty(), coords(p.artifacts));

        // RxAndroid 3: RxJava 3 (higher version than RxAndroid asks for comes from RxJava itself), reactive-streams
        p = resolver.resolve("io.reactivex.rxjava3:rxandroid:3.0.2");
        System.out.println("rxandroid: " + coords(p.artifacts) + " notes " + p.notes + " problems " + p.problems);
        check("rxandroid: rxjava + reactive-streams", coords(p.artifacts).toString().contains("io.reactivex.rxjava3:rxjava:")
                && coords(p.artifacts).toString().contains("org.reactivestreams:reactive-streams:"), coords(p.artifacts));

        // highest version wins: room-runtime pulls sqlite-framework / sqlite; no test or optional dependencies
        p = resolver.resolve("androidx.room:room-runtime:2.6.1");
        System.out.println("room-runtime: " + coords(p.artifacts) + " built in " + coords(p.builtIn).size() + " notes " + p.notes + " problems " + p.problems);
        check("room-runtime: room-common, sqlite, sqlite-framework", coords(p.artifacts).toString().contains("androidx.room:room-common:2.6.1")
                && coords(p.artifacts).toString().contains("androidx.sqlite:sqlite-framework:") && p.problems.isEmpty(), coords(p.artifacts));

        // JitPack
        p = resolver.resolve("com.github.PhilJay:MPAndroidChart:v3.1.0");
        System.out.println("MPAndroidChart: " + coords(p.artifacts) + " built in " + coords(p.builtIn) + " problems " + p.problems);
        check("MPAndroidChart from JitPack", p.root != null && p.artifacts.size() >= 1 && p.problems.isEmpty(), coords(p.artifacts));

        // built in itself
        p = resolver.resolve("com.google.code.gson:gson:2.10.1");
        check("gson: built in, nothing to install", p.artifacts.isEmpty() && p.builtIn.size() == 1, coords(p.builtIn));

        // not there
        p = resolver.resolve("com.example.nothing:nothing:1.0.0");
        check("unknown coordinate: a problem, nothing to install", p.artifacts.isEmpty() && !p.problems.isEmpty(), p.problems);

        // newest stable version (Updates tab)
        String latest = resolver.latestStable("com.squareup.retrofit2", "retrofit");
        check("retrofit latest stable >= 2.11.0", latest != null && MavenResolver.compareVersions(latest, "2.11.0") >= 0, latest);
        String core = resolver.latestStable("androidx.core", "core");
        check("androidx.core latest stable >= 1.13.1", core != null && MavenResolver.compareVersions(core, "1.13.1") >= 0, core);

        // archives land in the cache
        p = resolver.resolve("com.squareup.retrofit2:converter-gson:2.9.0");
        for (MavenResolver.Artifact a : p.artifacts) {
            File f = resolver.archive(a, p);
            check("archive " + a.coordinate(), f != null && f.length() > 1000, f);
        }
        p = resolver.resolve("androidx.preference:preference:1.2.1");
        for (MavenResolver.Artifact a : p.artifacts) {
            File f = resolver.archive(a, p);
            check("archive " + a.coordinate(), f != null && f.getName().endsWith("." + a.packaging), f);
        }
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
    }
}
