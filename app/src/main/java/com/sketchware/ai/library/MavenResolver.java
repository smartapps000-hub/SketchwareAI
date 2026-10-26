package com.sketchware.ai.library;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Library Resolver (v1.8.8): a Maven coordinate and everything it needs, read from the libraries' POM files the way
 * Gradle reads them: compile and runtime dependencies (not test, provided, system or optional ones), versions from
 * the parent POMs, their properties and dependency management (BOMs included), exclusions, and the highest version
 * when two libraries ask for the same one. What Sketchware AI has built in ({@link BuiltInCoordinates}) is not
 * downloaded again: the plan lists it, and the build adds the built-in copy.
 * <p>
 * Plain Java (HttpURLConnection, the platform XML parser): the same code runs on the phone and in desktop tests.
 * Every POM and archive is kept in a cache folder (/.sketchware/libs/cache/maven), so a library is fetched once.
 */
public final class MavenResolver {

    /** Where files come from: the network by default. Returns null when the file isn't there (HTTP 404/410). */
    public interface Source {
        byte[] fetch(String url) throws IOException;
    }

    public static final class Artifact {
        public String group;
        public String name;
        public String version;
        /** "aar", "jar" or "pom" (nothing to download). */
        public String packaging = "jar";
        /** Repository the POM came from. */
        public String repository;
        /** Coordinate of the library that asked for it first, or null for the one asked for. */
        public String via;
        /** Built-in library name (BuiltInLibraries constant) when Sketchware AI has it, else null. */
        public String builtIn;
        public String builtInVersion;
        /** Coordinates this artifact needs (after resolution), for the library's needs file. */
        public final List<String> needs = new ArrayList<>();

        public String key() {
            return group + ":" + name;
        }

        public String coordinate() {
            return group + ":" + name + ":" + version;
        }

        @Override
        public String toString() {
            return coordinate() + (builtIn != null ? " (built in " + builtInVersion + ")" : "") + " " + packaging;
        }
    }

    public static final class Plan {
        public Artifact root;
        /** To install (or installed already): everything the root needs that isn't built in, the root first. */
        public final List<Artifact> artifacts = new ArrayList<>();
        /** Needed and built in. */
        public final List<Artifact> builtIn = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public final List<String> problems = new ArrayList<>();
        /** Every request failed without an answer from a repository. */
        public boolean offline;
        /** v1.8.12: every request and its answer ("GET url → 404"), for Copy log. */
        public final List<String> log = new ArrayList<>();
        /** v1.8.12: the user cancelled. */
        public boolean cancelled;
    }

    /**
     * v1.8.12: cancels the resolve or download running on the thread that set it ({@link #CANCEL}): no new request is
     * made and the connection in progress is closed.
     */
    public static final class Cancel {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;

        /** From any thread; may block briefly while the socket closes, so not on the UI thread. */
        public void cancel() {
            cancelled = true;
            HttpURLConnection c = connection;
            if (c != null) {
                try {
                    c.disconnect();
                } catch (RuntimeException ignored) {
                    // closed already
                }
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public static final ThreadLocal<Cancel> CANCEL = new ThreadLocal<>();

    private static boolean cancelled() {
        Cancel c = CANCEL.get();
        return c != null && c.cancelled;
    }

    /** v1.8.12: the work on this thread was cancelled (installers check it between libraries). */
    public static boolean cancelledHere() {
        return cancelled();
    }

    private static final String USER_AGENT = "SketchwareAI/1.8.8 (Android; Library Resolver)";

    private final List<String[]> repositories;
    private final File cache;
    private final Source source;
    private int requests, failures;

    /**
     * @param repositories {name, url} in search order
     * @param cache        folder for POMs and archives (created when needed), or null
     */
    public MavenResolver(List<String[]> repositories, File cache, Source source) {
        this.repositories = repositories;
        this.cache = cache;
        this.source = source != null ? source : MavenResolver::download;
    }

    /**
     * v1.8.12: the repositories in the order a group is looked up in: JitPack first for GitHub/GitLab/Bitbucket groups
     * (it builds them; the others never have them), Google Maven first for androidx / Google Android groups, else the
     * configured order (Google Maven, Maven Central, JitPack).
     */
    List<String> orderFor(String group) {
        List<String> bases = new ArrayList<>();
        for (String[] repo : repositories) bases.add(base(repo[1]));
        String first = null;
        if (group != null && (group.startsWith("com.github.") || group.startsWith("com.gitlab.") || group.startsWith("org.bitbucket."))) {
            first = "jitpack.io";
        } else if (group != null && (group.startsWith("androidx.") || group.startsWith("com.google.android.")
                || group.startsWith("com.android.") || group.startsWith("com.google.firebase") || group.startsWith("com.google.gms"))) {
            first = "dl.google.com";
        }
        if (first != null) {
            for (int i = 0; i < bases.size(); i++) {
                if (bases.get(i).contains("://" + first)) {
                    bases.add(0, bases.remove(i));
                    break;
                }
            }
        }
        return bases;
    }

    public static List<String[]> defaultRepositories() {
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{"Google Maven", "https://dl.google.com/dl/android/maven2"});
        list.add(new String[]{"Maven Central", "https://repo1.maven.org/maven2"});
        list.add(new String[]{"JitPack", "https://jitpack.io"});
        return list;
    }

    // ---------------------------------------------------------------------------------------------- resolution

    private static final class Request {
        final String group, name, version, via;
        final Set<String> exclusions;

        Request(String group, String name, String version, String via, Set<String> exclusions) {
            this.group = group;
            this.name = name;
            this.version = version;
            this.via = via;
            this.exclusions = exclusions;
        }
    }

    /**
     * v1.8.11: a GitHub library on JitPack is named by its git tag, which may carry a "v" ("v3.4.0") or not: when the
     * library itself isn't found with the version as typed, the other spelling is tried once.
     */
    public Plan resolve(String coordinate) {
        Plan plan = resolveExactly(coordinate);
        String[] gav = MavenFetcher.parse(coordinate);
        if (gav == null || plan.cancelled) return plan;
        String id = gav[0] + ":" + gav[1] + ":" + gav[2];
        // the root artifact is in plan.root even when no repository has it: missing = its POM wasn't found
        boolean rootMissing = plan.problems.contains("Not found in the repositories: " + id);
        if (!rootMissing || plan.offline) return plan;   // offline: nothing else can be asked either
        boolean gitHosted = gav[0].startsWith("com.github.") || gav[0].startsWith("io.github.")
                || gav[0].startsWith("com.gitlab.") || gav[0].startsWith("org.bitbucket.");
        if (gitHosted) {
            String other = gav[2].startsWith("v") && gav[2].length() > 1 && Character.isDigit(gav[2].charAt(1))
                    ? gav[2].substring(1) : "v" + gav[2];
            Plan retry = resolveExactly(gav[0] + ":" + gav[1] + ":" + other);
            retry.log.addAll(0, plan.log);
            if (retry.root != null && !retry.problems.contains("Not found in the repositories: " + gav[0] + ":" + gav[1] + ":" + other)) {
                retry.notes.add(0, "Found as " + gav[0] + ":" + gav[1] + ":" + other + " (the tag's spelling on JitPack)");
                return retry;
            }
            plan.log.clear();
            plan.log.addAll(retry.log);
            if (retry.cancelled) {
                plan.cancelled = true;
                return plan;
            }
        }
        Plan direct = directArchive(gav, plan);
        return direct != null ? direct : plan;
    }

    /**
     * v1.8.12: the v1.8.2 – v1.8.5 way, when no repository has the POM: the .aar (then the .jar) at its Maven path.
     * Found, it is installed alone: what it needs isn't known without its POM.
     */
    private Plan directArchive(String[] gav, Plan failed) {
        for (String extension : new String[]{"aar", "jar"}) {
            String relative = path(gav[0], gav[1], gav[2], extension);
            for (String repo : orderFor(gav[0])) {
                byte[] data = get(repo + "/" + relative, failed);
                if (failed.cancelled) return null;
                if (data == null) continue;
                if (cache != null) write(new File(cache, relative), data);
                Plan plan = new Plan();
                plan.log.addAll(failed.log);
                Artifact root = new Artifact();
                root.group = gav[0];
                root.name = gav[1];
                root.version = gav[2];
                root.packaging = extension;
                root.repository = repo;
                plan.root = root;
                plan.artifacts.add(root);
                plan.notes.add("No POM for " + root.coordinate() + ": its ." + extension + " was found on " + repo
                        + " and is installed alone (what it needs can't be known without the POM).");
                return plan;
            }
        }
        return null;
    }

    /** True when the plan's root library was found (built in, or its POM read); false for "Not found". */
    public static boolean found(Plan plan) {
        return plan.root != null && (plan.root.builtIn != null || plan.artifacts.contains(plan.root));
    }

    private static final Pattern GITHUB_LINK = Pattern.compile(
            "(?i)^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?"
                    + "(?:/(?:releases/tag|releases/download|tree|commit)/([^/?#\\s]+))?(?:[/?#].*)?$");
    private static final Pattern JITPACK_LINK = Pattern.compile(
            "(?i)^(?:https?://)?(?:www\\.)?jitpack\\.io/#([A-Za-z0-9-]+)/([A-Za-z0-9._-]+?)(?:/([^/?#\\s]+))?/?$");

    /**
     * v1.8.11: + Add also takes a GitHub link (github.com/User/Repo, …/releases/tag/v1.2, …/tree/1.2) or a JitPack page
     * (jitpack.io/#User/Repo/1.2): JitPack builds GitHub repositories as com.github.User:Repo:tag. Also
     * com.github.User:Repo without a version.
     *
     * @return {group, artifact, version or null}, or null when the text is none of these
     */
    public static String[] fromLink(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = GITHUB_LINK.matcher(t);
        if (!m.matches()) m = JITPACK_LINK.matcher(t);
        if (m.matches()) {
            String version = m.group(3);
            if (version != null && !version.isEmpty() && t.matches("(?i).*github\\.com/[^/]+/[^/]+/tree/.*")
                    && !version.matches(".*\\d.*") && !version.endsWith("-SNAPSHOT")) {
                version += "-SNAPSHOT";   // a branch (tree/main): JitPack builds it as main-SNAPSHOT
            }
            return new String[]{"com.github." + m.group(1), m.group(2), version == null || version.isEmpty() ? null : version};
        }
        String d = t.replaceFirst("^(implementation|api|compile|compileOnly)\\s*\\(?", "")
                .replace("'", "").replace("\"", "").replace(")", "").trim();
        String[] p = d.split(":");
        if (p.length == 2 && p[0].startsWith("com.github.") && p[0].length() > 11 && !p[1].isEmpty()
                && p[0].matches("[A-Za-z0-9._-]+") && p[1].matches("[A-Za-z0-9._-]+")) {
            return new String[]{p[0], p[1], null};
        }
        return null;
    }

    private Plan resolveExactly(String coordinate) {
        Plan plan = new Plan();
        requests = failures = 0;   // offline is about this plan's requests
        String[] gav = MavenFetcher.parse(coordinate);
        if (gav == null) {
            plan.problems.add("Not a Maven coordinate: " + coordinate + " (use group:artifact:version)");
            return plan;
        }
        Map<String, String> chosen = new LinkedHashMap<>();
        Map<String, String> firstVia = new HashMap<>();
        Map<String, Pom> poms = new HashMap<>();
        Deque<Request> queue = new ArrayDeque<>();
        queue.add(new Request(gav[0], gav[1], gav[2], null, new HashSet<String>()));
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 5000) {
            if (cancelled()) {
                plan.cancelled = true;
                break;
            }
            Request r = queue.poll();
            String key = r.group + ":" + r.name;
            String version = r.version;
            if (isRange(version)) {
                String picked = pickFromRange(r.group, r.name, version, plan);
                if (picked == null) {
                    plan.problems.add("No version of " + key + " fits " + version);
                    continue;
                }
                version = picked;
            }
            String current = chosen.get(key);
            if (current != null && compareVersions(current, version) >= 0) continue;
            if (current != null) plan.notes.add(key + ": " + version + " instead of " + current + " (the highest one asked for)");
            chosen.put(key, version);
            if (!firstVia.containsKey(key)) firstVia.put(key, r.via);
            if (BuiltInCoordinates.version(key) != null) continue;   // its own needs come with the built-in copy
            Pom pom = pom(r.group, r.name, version, plan, poms);
            if (pom == null) continue;
            for (Dependency d : pom.dependencies) {
                if (!d.counts()) continue;
                if (excluded(r.exclusions, d)) continue;
                if (d.group == null || d.name == null) {
                    plan.problems.add(key + " has a dependency without groupId or artifactId: left out");
                    continue;
                }
                if (d.version == null || d.version.isEmpty()) {
                    plan.problems.add(key + " needs " + d.group + ":" + d.name + " without saying which version");
                    continue;
                }
                Set<String> exclusions = new HashSet<>(r.exclusions);
                exclusions.addAll(d.exclusions);
                queue.add(new Request(d.group, d.name, d.version, r.group + ":" + r.name + ":" + version, exclusions));
            }
        }
        // What is still needed with the chosen versions (a lower version's own dependencies may be gone).
        Map<String, Artifact> reached = new LinkedHashMap<>();
        Deque<Request> walk = new ArrayDeque<>();
        walk.add(new Request(gav[0], gav[1], chosen.get(gav[0] + ":" + gav[1]), null, new HashSet<String>()));
        while (!walk.isEmpty()) {
            Request r = walk.poll();
            String key = r.group + ":" + r.name;
            String version = chosen.get(key);
            if (version == null || reached.containsKey(key)) continue;
            Artifact a = new Artifact();
            a.group = r.group;
            a.name = r.name;
            a.version = version;
            a.via = r.via;
            a.builtIn = BuiltInCoordinates.name(key);
            a.builtInVersion = BuiltInCoordinates.version(key);
            reached.put(key, a);
            if (a.builtIn != null) continue;
            Pom pom = poms.get(key + ":" + version);
            if (pom == null) continue;
            a.packaging = pom.packaging;
            a.repository = pom.repository;
            for (Dependency d : pom.dependencies) {
                if (!d.counts()) continue;
                if (excluded(r.exclusions, d) || d.group == null || d.name == null) continue;
                String dk = d.group + ":" + d.name;
                if (chosen.containsKey(dk)) a.needs.add(dk + ":" + chosen.get(dk));
                Set<String> exclusions = new HashSet<>(r.exclusions);
                exclusions.addAll(d.exclusions);
                walk.add(new Request(d.group, d.name, chosen.get(dk), a.coordinate(), exclusions));
            }
        }
        for (Artifact a : reached.values()) {
            if (a.builtIn != null) {
                plan.builtIn.add(a);
                if (majorDiffers(a.version, a.builtInVersion)) {
                    plan.notes.add(a.key() + " " + a.version + " is asked for; Sketchware AI has " + a.builtInVersion
                            + " built in, a different major version: the library may not work with it");
                }
            } else if (poms.get(a.key() + ":" + a.version) != null) {
                plan.artifacts.add(a);
            }
        }
        plan.root = reached.get(gav[0] + ":" + gav[1]);
        plan.offline = requests > 0 && failures == requests;
        return plan;
    }

    /** Maven exclusions: group:artifact, with * for either part ("*:*" = none of its dependencies). */
    private static boolean excluded(Set<String> exclusions, Dependency d) {
        return exclusions.contains(d.group + ":" + d.name) || exclusions.contains(d.group + ":*")
                || exclusions.contains("*:" + d.name) || exclusions.contains("*:*");
    }

    // ---------------------------------------------------------------------------------------------- POM model

    private static final class Dependency {
        String group, name, version, scope, type;
        boolean optional;
        final Set<String> exclusions = new HashSet<>();

        boolean counts() {
            if (optional) return false;
            if (scope != null && !scope.isEmpty() && !scope.equals("compile") && !scope.equals("runtime")) return false;
            return type == null || type.isEmpty() || type.equals("jar") || type.equals("aar") || type.equals("bundle");
        }
    }

    private static final class Pom {
        String group, name, version, packaging = "jar", repository;
        final Map<String, String> properties = new HashMap<>();
        /** group:artifact → Dependency carrying version/scope/exclusions. */
        final Map<String, Dependency> management = new HashMap<>();
        final List<Dependency> dependencies = new ArrayList<>();
    }

    /** POM with its parents merged and every ${…} filled in, or null (a problem is noted). */
    private Pom pom(String group, String name, String version, Plan plan, Map<String, Pom> poms) {
        String id = group + ":" + name + ":" + version;
        if (poms.containsKey(id)) return poms.get(id);
        Pom pom = readPom(group, name, version, plan, 0);
        poms.put(id, pom);
        if (pom == null) plan.problems.add("Not found in the repositories: " + id);
        return pom;
    }

    private Pom readPom(String group, String name, String version, Plan plan, int depth) {
        if (depth > 12) return null;
        String[] found = fetchFirst(group, name, version, "pom", plan);
        if (found == null) return null;
        Element project;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(found[1].getBytes("UTF-8")));
            project = doc.getDocumentElement();
        } catch (Exception e) {
            plan.problems.add("Unreadable POM of " + group + ":" + name + ":" + version + ": " + e.getMessage());
            return null;
        }
        Pom pom = new Pom();
        pom.repository = found[0];
        Pom parent = null;
        Element parentElement = child(project, "parent");
        if (parentElement != null) {
            String pg = text(parentElement, "groupId"), pa = text(parentElement, "artifactId"), pv = text(parentElement, "version");
            if (pg != null && pa != null && pv != null) parent = readPom(pg, pa, pv, plan, depth + 1);
        }
        if (parent != null) {
            pom.properties.putAll(parent.properties);
            pom.management.putAll(parent.management);
        }
        pom.group = or(text(project, "groupId"), parentElement != null ? text(parentElement, "groupId") : group);
        pom.name = or(text(project, "artifactId"), name);
        pom.version = or(text(project, "version"), parentElement != null ? text(parentElement, "version") : version);
        String packaging = text(project, "packaging");
        if (packaging != null) pom.packaging = packaging.trim();
        Element properties = child(project, "properties");
        if (properties != null) {
            for (Element p : children(properties)) pom.properties.put(p.getTagName(), p.getTextContent().trim());
        }
        pom.properties.put("project.groupId", pom.group);
        pom.properties.put("project.artifactId", pom.name);
        pom.properties.put("project.version", pom.version);
        pom.properties.put("pom.version", pom.version);
        pom.properties.put("version", pom.version);
        if (parent != null) {
            pom.properties.put("project.parent.version", parent.version);
            pom.properties.put("parent.version", parent.version);
            pom.properties.put("project.parent.groupId", parent.group);
        }
        pom.packaging = fill(pom.packaging, pom.properties);
        if ("bundle".equals(pom.packaging)) pom.packaging = "jar";
        Element management = child(project, "dependencyManagement");
        Element managed = management == null ? null : child(management, "dependencies");
        if (managed != null) {
            for (Element e : children(managed)) {
                Dependency d = dependency(e, pom.properties);
                if (d.group == null || d.name == null) continue;
                if ("import".equals(d.scope) && "pom".equals(d.type) && d.version != null) {
                    Pom bom = readPom(d.group, d.name, d.version, plan, depth + 1);
                    if (bom != null) {
                        for (Map.Entry<String, Dependency> m : bom.management.entrySet()) {
                            if (!pom.management.containsKey(m.getKey())) pom.management.put(m.getKey(), m.getValue());
                        }
                    }
                    continue;
                }
                pom.management.put(d.group + ":" + d.name, d);
            }
        }
        if (parent != null) pom.dependencies.addAll(parent.dependencies);
        Element dependencies = child(project, "dependencies");
        if (dependencies != null) {
            for (Element e : children(dependencies)) {
                Dependency d = dependency(e, pom.properties);
                Dependency m = pom.management.get(d.group + ":" + d.name);
                if (m != null) {
                    if (d.version == null || d.version.isEmpty()) d.version = m.version;
                    if (d.scope == null || d.scope.isEmpty()) d.scope = m.scope;
                    if (d.exclusions.isEmpty()) d.exclusions.addAll(m.exclusions);
                }
                pom.dependencies.add(d);
            }
        }
        return pom;
    }

    private static Dependency dependency(Element e, Map<String, String> properties) {
        Dependency d = new Dependency();
        d.group = fill(text(e, "groupId"), properties);
        d.name = fill(text(e, "artifactId"), properties);
        d.version = fill(text(e, "version"), properties);
        d.scope = fill(text(e, "scope"), properties);
        d.type = fill(text(e, "type"), properties);
        d.optional = "true".equals(fill(text(e, "optional"), properties));
        Element exclusions = child(e, "exclusions");
        if (exclusions != null) {
            for (Element x : children(exclusions)) {
                d.exclusions.add(fill(text(x, "groupId"), properties) + ":" + fill(text(x, "artifactId"), properties));
            }
        }
        if (d.version != null) d.version = d.version.trim();
        return d;
    }

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)\\}");

    private static String fill(String value, Map<String, String> properties) {
        if (value == null) return null;
        String text = value.trim();
        for (int pass = 0; pass < 10 && text.contains("${"); pass++) {
            Matcher m = PROPERTY.matcher(text);
            StringBuffer out = new StringBuffer();
            boolean changed = false;
            while (m.find()) {
                String v = properties.get(m.group(1));
                if (v != null) changed = true;
                m.appendReplacement(out, Matcher.quoteReplacement(v != null ? v : m.group()));
            }
            m.appendTail(out);
            text = out.toString();
            if (!changed) break;
        }
        return text;
    }

    private static Element child(Element parent, String tag) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element && tag.equals(((Element) n).getTagName())) return (Element) n;
        }
        return null;
    }

    private static List<Element> children(Element parent) {
        List<Element> list = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element) list.add((Element) nodes.item(i));
        }
        return list;
    }

    private static String text(Element parent, String tag) {
        Element e = child(parent, tag);
        return e == null ? null : e.getTextContent().trim();
    }

    private static String or(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    // ---------------------------------------------------------------------------------------------- versions

    static boolean isRange(String v) {
        return v != null && (v.startsWith("[") || v.startsWith("(")) && (v.endsWith("]") || v.endsWith(")"));
    }

    /** Highest version in the repository's list that fits a Maven range ("[1.0,2.0)", "[1.2]"), or null. */
    private String pickFromRange(String group, String name, String range, Plan plan) {
        String inside = range.substring(1, range.length() - 1);
        if (!inside.contains(",")) return inside.trim();
        List<String> versions = versions(group, name, plan);
        String best = null;
        for (String v : versions) {
            if (inRange(v, range) && (best == null || compareVersions(v, best) > 0)) best = v;
        }
        return best;
    }

    static boolean inRange(String v, String range) {
        boolean lowInclusive = range.startsWith("["), highInclusive = range.endsWith("]");
        String[] parts = range.substring(1, range.length() - 1).split(",", -1);
        String low = parts[0].trim(), high = parts.length > 1 ? parts[1].trim() : "";
        if (!low.isEmpty()) {
            int c = compareVersions(v, low);
            if (c < 0 || c == 0 && !lowInclusive) return false;
        }
        if (!high.isEmpty()) {
            int c = compareVersions(v, high);
            if (c > 0 || c == 0 && !highInclusive) return false;
        }
        return true;
    }

    /** Versions listed in the repository's maven-metadata.xml (first repository that has one). */
    public List<String> versions(String group, String name, Plan plan) {
        List<String> list = new ArrayList<>();
        for (String repo : orderFor(group)) {
            String url = repo + "/" + group.replace('.', '/') + "/" + name + "/maven-metadata.xml";
            byte[] data = get(url, plan);
            if (data == null) continue;
            Matcher m = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>").matcher(new String(data));
            while (m.find()) list.add(m.group(1));
            if (!list.isEmpty()) break;
        }
        return list;
    }

    /** Newest stable version (no alpha/beta/rc/SNAPSHOT/milestone), or null. */
    public String latestStable(String group, String name) {
        return latestStable(group, name, null);
    }

    /** @param plan collects "No answer from …" notes, or null */
    public String latestStable(String group, String name, Plan plan) {
        String best = null;
        for (String v : versions(group, name, plan)) {
            if (!stable(v)) continue;
            if (best == null || compareVersions(v, best) > 0) best = v;
        }
        return best;
    }

    public static boolean stable(String v) {
        String s = v.toLowerCase(java.util.Locale.ROOT);
        return !(s.contains("alpha") || s.contains("beta") || s.contains("-rc") || s.contains(".rc") || s.matches(".*\\brc\\d*\\b.*")
                || s.contains("snapshot") || s.contains("-m") && s.matches(".*-m\\d+.*") || s.contains("-dev") || s.contains("preview")
                || s.contains("-eap"));
    }

    /** Maven-like ordering: numbers numerically, alpha < beta < milestone < rc < snapshot < release < sp. */
    public static int compareVersions(String a, String b) {
        List<Object> x = tokens(a), y = tokens(b);
        int n = Math.max(x.size(), y.size());
        for (int i = 0; i < n; i++) {
            Object p = i < x.size() ? x.get(i) : null, q = i < y.size() ? y.get(i) : null;
            int c = compareToken(p, q);
            if (c != 0) return c;
        }
        return 0;
    }

    private static List<Object> tokens(String v) {
        List<Object> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+|[A-Za-z]+").matcher(v == null ? "" : v);
        while (m.find()) {
            String t = m.group();
            if (Character.isDigit(t.charAt(0))) {
                try {
                    list.add(Long.parseLong(t));
                } catch (NumberFormatException e) {
                    list.add(Long.MAX_VALUE);
                }
            } else {
                list.add(t.toLowerCase(java.util.Locale.ROOT));
            }
        }
        // trailing zeros don't count: 1.0 == 1
        while (!list.isEmpty() && Long.valueOf(0).equals(list.get(list.size() - 1))) list.remove(list.size() - 1);
        return list;
    }

    private static int qualifierRank(String q) {
        switch (q) {
            case "alpha":
            case "a":
                return 1;
            case "beta":
            case "b":
                return 2;
            case "milestone":
            case "m":
                return 3;
            case "rc":
            case "cr":
                return 4;
            case "snapshot":
                return 5;
            case "":
            case "ga":
            case "final":
            case "release":
                return 6;
            case "sp":
                return 7;
            default:
                return 8;
        }
    }

    private static int compareToken(Object p, Object q) {
        if (p == null && q == null) return 0;
        if (p == null) {
            // a version that ends here: like a 0, or like a release next to a qualifier (1.0 > 1.0-beta, < 1.0-sp)
            if (q instanceof Long) return Long.compare(0, (Long) q);
            int rank = qualifierRank((String) q);
            return rank < 6 ? 1 : rank == 6 ? 0 : -1;
        }
        if (q == null) return -compareToken(null, p);
        if (p instanceof Long && q instanceof Long) return Long.compare((Long) p, (Long) q);
        if (p instanceof Long) return 1;      // 1.0.1 > 1.0-beta
        if (q instanceof Long) return -1;
        int c = Integer.compare(qualifierRank((String) p), qualifierRank((String) q));
        return c != 0 ? c : ((String) p).compareTo((String) q);
    }

    static boolean majorDiffers(String a, String b) {
        List<Object> x = tokens(a), y = tokens(b);
        Object p = x.isEmpty() ? 0L : x.get(0), q = y.isEmpty() ? 0L : y.get(0);
        return !p.equals(q);
    }

    // ---------------------------------------------------------------------------------------------- files

    /** The cache folder (POMs and archives), or null. */
    public File cacheDir() {
        return cache;
    }

    private static String base(String repo) {
        return repo.endsWith("/") ? repo.substring(0, repo.length() - 1) : repo;
    }

    static String path(String group, String name, String version, String extension) {
        return group.replace('.', '/') + "/" + name + "/" + version + "/" + name + "-" + version + "." + extension;
    }

    /** @return {repository url, text} of the first repository that has the file, or null */
    private String[] fetchFirst(String group, String name, String version, String extension, Plan plan) {
        String relative = path(group, name, version, extension);
        File cached = cache == null ? null : new File(cache, relative);
        File origin = cache == null ? null : new File(cache, group.replace('.', '/') + "/" + name + "/" + version + "/repository.txt");
        if (cached != null && cached.isFile() && cached.length() > 0) {
            try {
                String repo = origin.isFile() ? new String(read(origin), "UTF-8").trim() : "";
                return new String[]{repo, new String(read(cached), "UTF-8")};
            } catch (IOException ignored) {
                // read it again from the network
            }
        }
        for (String repo : orderFor(group)) {
            String url = repo + "/" + relative;
            byte[] data = get(url, plan);
            if (data == null) continue;
            if (cached != null) {
                write(cached, data);
                write(origin, repo.getBytes());
            }
            try {
                return new String[]{repo, new String(data, "UTF-8")};
            } catch (java.io.UnsupportedEncodingException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * The artifact's archive (.aar or .jar) in the cache, downloaded when it isn't there yet.
     *
     * @return the file, or null when no repository has it
     */
    public File archive(Artifact a, Plan plan) {
        String[] extensions = "aar".equals(a.packaging) ? new String[]{"aar", "jar"} : new String[]{"jar", "aar"};
        for (String extension : extensions) {
            String relative = path(a.group, a.name, a.version, extension);
            File cached = cache == null ? null : new File(cache, relative);
            if (cached != null && cached.isFile() && cached.length() > 0) return cached;
            List<String> order = new ArrayList<>();
            if (a.repository != null && !a.repository.isEmpty()) order.add(a.repository);
            for (String repo : orderFor(a.group)) if (!order.contains(repo)) order.add(repo);
            for (String repo : order) {
                byte[] data = get(repo + "/" + relative, plan);
                if (data == null) continue;
                File out = cached != null ? cached : new File(System.getProperty("java.io.tmpdir"), a.name + "-" + a.version + "." + extension);
                write(out, data);
                return out;
            }
        }
        return null;
    }

    /**
     * v1.8.11: a repository that can't be reached at all (unknown host, connection refused, connect time-out, TLS
     * handshake) is not asked again by this resolver (one action of the Library Resolver): one dead repository in the
     * list no longer costs 20 seconds per POM. Answers about one artifact (HTTP 401/403, which JitPack gives for a
     * missing repository, a read time-out while JitPack builds, 202) don't count: they say nothing about the others.
     * Each plan that skips it says so once.
     */
    private final Map<String, String> deadRepositories = new HashMap<>();

    private byte[] get(String url, Plan plan) {
        if (cancelled()) {
            if (plan != null) plan.cancelled = true;
            return null;
        }
        String repository = repositoryOf(url);
        requests++;
        String dead = repository == null ? null : deadRepositories.get(repository);
        if (dead != null) {
            failures++;
            String note = "No answer from " + repository + " (not asked again in this search): " + dead;
            if (plan != null && !plan.notes.contains(note) && plan.problems.size() < 40) plan.notes.add(note);
            log(plan, url, "skipped (no answer earlier)");
            return null;
        }
        RequestListener listener = REQUEST.get();
        if (listener != null) listener.request(url);
        try {
            byte[] data = source.fetch(url);
            log(plan, url, data == null ? "404 not there" : "200, " + data.length + " bytes");
            return data;
        } catch (IOException e) {
            if (cancelled()) {
                if (plan != null) plan.cancelled = true;
                log(plan, url, "cancelled");
                return null;
            }
            failures++;
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            if (e instanceof java.net.UnknownHostException) message = "unknown host (no connection, or the address is wrong): " + message;
            else if (e instanceof java.net.SocketTimeoutException) message = "timed out: " + message;
            else if (e instanceof java.net.ConnectException) message = "connection refused: " + message;
            boolean unreachable = e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                    || e instanceof javax.net.ssl.SSLHandshakeException
                    || e instanceof java.net.SocketTimeoutException && String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT).contains("connect");
            if (unreachable && repository != null) deadRepositories.put(repository, message);
            if (plan != null && plan.problems.size() < 40) plan.notes.add("No answer from " + url + ": " + message);
            log(plan, url, message);
            return null;
        }
    }

    private static void log(Plan plan, String url, String answer) {
        if (plan != null && plan.log.size() < 400) plan.log.add("GET " + url + " → " + answer);
    }

    /** The configured repository a URL belongs to: the longest base it starts with. */
    private String repositoryOf(String url) {
        String best = null;
        for (String[] repo : repositories) {
            String b = base(repo[1]);
            if (url.startsWith(b + "/") && (best == null || b.length() > best.length())) best = b;
        }
        return best;
    }

    /** v1.8.11: bytes of the current download (set per worker thread by the Library Resolver's progress dialog). */
    public interface ByteProgress {
        /** @param total the size the server announced, or -1 */
        void bytes(String url, long done, long total);
    }

    public static final ThreadLocal<ByteProgress> PROGRESS = new ThreadLocal<>();

    /** v1.8.12: told before each request (the Add dialog's "Connecting to jitpack.io…"). */
    public interface RequestListener {
        void request(String url);
    }

    public static final ThreadLocal<RequestListener> REQUEST = new ThreadLocal<>();

    /**
     * Default source: HTTPS GET; null for 404/410. v1.8.11: redirects are followed across http/https too (Java's
     * client follows only same-protocol ones: Sonatype, JCenter and Atlassian answer 301/302 to another host), at most
     * five; JitPack's 202 ("building") is reported as such.
     */
    public static byte[] download(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop < 6; hop++) {
            URL target = new URL(current);
            if (!"https".equals(target.getProtocol()) && !"http".equals(target.getProtocol())) {
                throw new IOException("redirected to a " + target.getProtocol() + ": address");
            }
            Cancel cancel = CANCEL.get();
            if (cancel != null && cancel.cancelled) throw new IOException("cancelled");
            HttpURLConnection connection = (HttpURLConnection) target.openConnection();
            if (cancel != null) cancel.connection = connection;
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            try {
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) throw new IOException("HTTP " + code + " without a Location");
                    String next = new URL(new URL(current), location).toString();
                    // never from https down to plain http: the archive could be changed on the way
                    if (current.startsWith("https:") && next.startsWith("http:")) {
                        throw new IOException("HTTP " + code + " to an http:// address (not followed)");
                    }
                    current = next;
                    continue;
                }
                if (code == 404 || code == 410) return null;
                if (code == 202) throw new IOException("JitPack is still building this version (HTTP 202): try again in a minute");
                if (code == 401 && target.getHost().endsWith("jitpack.io")) {
                    throw new IOException("HTTP 401: JitPack has no such GitHub repository (deleted, private or renamed)");
                }
                if (code >= 300) throw new IOException("HTTP " + code);
                // Content-Length read by hand: getContentLengthLong() is API 24 (the minApi21 flavour runs on 5.0+)
                long total = -1;
                try {
                    String length = connection.getHeaderField("Content-Length");
                    if (length != null) total = Long.parseLong(length.trim());
                } catch (NumberFormatException ignored) {
                    // size unknown: the bar moves without a percentage
                }
                ByteProgress progress = PROGRESS.get();
                try (InputStream in = connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(total > 0 && total < Integer.MAX_VALUE ? (int) total : 16384);
                    byte[] buffer = new byte[16384];
                    long done = 0, reported = 0;
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        if (cancel != null && cancel.cancelled) throw new IOException("cancelled");
                        out.write(buffer, 0, n);
                        done += n;
                        if (progress != null && done - reported >= 32768) {
                            reported = done;
                            progress.bytes(url, done, total);
                        }
                    }
                    if (progress != null) progress.bytes(url, done, total);
                    return out.toByteArray();
                }
            } finally {
                if (cancel != null) cancel.connection = null;
                connection.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    static byte[] read(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Atomic: a temporary file next to it, then renamed. */
    static void write(File file, byte[] data) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            File temporary = new File(dir, file.getName() + ".tmp" + Thread.currentThread().getId());
            try (OutputStream out = new FileOutputStream(temporary)) {
                out.write(data);
            }
            if (!temporary.renameTo(file)) {
                file.delete();
                if (!temporary.renameTo(file)) temporary.delete();
            }
        } catch (IOException ignored) {
            // the cache is a convenience
        }
    }

    /** Sorts plan artifacts so a library comes after the ones it needs (for installing). */
    public static List<Artifact> installOrder(List<Artifact> artifacts) {
        Map<String, Artifact> byCoordinate = new LinkedHashMap<>();
        for (Artifact a : artifacts) byCoordinate.put(a.coordinate(), a);
        List<Artifact> out = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (Artifact a : artifacts) visit(a, byCoordinate, done, out, new HashSet<String>());
        return out;
    }

    private static void visit(Artifact a, Map<String, Artifact> all, Set<String> done, List<Artifact> out, Set<String> path) {
        if (done.contains(a.coordinate()) || !path.add(a.coordinate())) return;
        for (String need : a.needs) {
            Artifact n = all.get(need);
            if (n != null) visit(n, all, done, out, path);
        }
        done.add(a.coordinate());
        out.add(a);
    }

    static List<String> sorted(java.util.Collection<String> values) {
        List<String> list = new ArrayList<>(values);
        Collections.sort(list);
        return list;
    }
}
