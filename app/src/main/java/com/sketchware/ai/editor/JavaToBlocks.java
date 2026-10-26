package com.sketchware.ai.editor;

import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Java editor's save (v1.8.8): turns an edited copy of a generated activity back into the project's blocks.
 * <ul>
 * <li>Code changed inside an event (an event, onCreate, a MoreBlock, an adapter's onBindCustomView) replaces only the
 * blocks whose lines changed, with one "add source directly" block per changed run; blocks whose lines are unchanged
 * stay as they are. A change inside an if/repeat's stack stays inside that stack.</li>
 * <li>A new field becomes a custom variable; a changed field becomes a custom variable with the new text; a removed
 * variable is removed when no block uses it.</li>
 * <li>A new method written like a MoreBlock ({@code public void _name(final String _text) { ... }}) becomes a
 * MoreBlock with its body in one "add source directly" block; a removed MoreBlock is removed when no block calls it.</li>
 * <li>A new import becomes a custom import.</li>
 * </ul>
 * Anything else the generator writes by itself (view lookups, onCreate, listeners' frames, adapters) can't be stored
 * as blocks: the save is refused with the line and what generates it.
 * <p>
 * Nothing is kept unless the project, generated again, gives exactly the edited code (lines compared without their
 * indentation and without blank lines, imports and members as sets, "final" ignored in method headers). Otherwise the
 * project's data is put back as it was.
 */
public final class JavaToBlocks {

    public static final class Problem {
        /** Line in the edited code (1-based), 0 when not tied to a line. */
        public final int line;
        public final String message;

        Problem(int line, String message) {
            this.line = line;
            this.message = message;
        }

        @Override
        public String toString() {
            return (line > 0 ? "line " + line + ": " : "") + message;
        }
    }

    public static final class Result {
        public boolean ok;
        /** What was stored, one line each ("button1 onClick: 2 blocks → 1 add source block"). */
        public final List<String> changes = new ArrayList<>();
        /** Things the user should know that didn't stop the save. */
        public final List<String> notes = new ArrayList<>();
        public final List<Problem> problems = new ArrayList<>();
        /** Nothing differed from the generated code. */
        public boolean unchanged;
    }

    /** Regenerates the activity from the (changed) project data: the text the editor showed for it. */
    public interface Generator {
        String generate();
    }

    static final int VAR_CUSTOM = 6;
    static final int VAR_IMPORT = 9;

    private final eCAccess data;
    private final String javaName;
    private final String generated;
    private final EditMap map;
    private final Generator generator;

    /**
     * @param generated the code the editor showed (the normal generation, {@code map.code})
     * @param generator generates the activity again from {@code data}
     */
    public JavaToBlocks(a.a.a.eC data, ProjectFileBean file, String generated, EditMap map, Generator generator) {
        this.data = new eCAccess(data);
        this.javaName = file.getJavaName();
        this.generated = generated;
        this.map = map;
        this.generator = generator;
    }

    /** v1.8.9: turns single statements into real blocks; null = everything stays "add source directly". */
    private StatementBlocks statements;
    private int mappedBlocks;

    /** v1.8.9: Java packages of the project's local libraries (LibraryImports may add "import pkg.*;" for them). */
    private final Set<String> libraryPackages = new HashSet<>();

    public JavaToBlocks useLibraryPackages(java.util.Collection<String> packages) {
        if (packages != null) libraryPackages.addAll(packages);
        return this;
    }

    /** An "import pkg.*;" the generator adds by itself for a local library (LibraryImports), not stored in the project. */
    private boolean automaticImport(String line) {
        Matcher m = IMPORT.matcher(line);
        if (!m.matches()) return false;
        String target = m.group(1).trim();
        return target.endsWith(".*") && libraryPackages.contains(target.substring(0, target.length() - 2));
    }

    public JavaToBlocks useStatementBlocks(StatementBlocks statements) {
        this.statements = statements;
        return this;
    }

    // ------------------------------------------------------------------------------------------------ planning

    /** One run of changed lines inside an event body. */
    private static final class Edit {
        EditMap.Event event;
        int ga, gb;   // generated lines [ga, gb)
        int ea, eb;   // edited lines [ea, eb)
        /** Every change of the member (blank-only ones too), in absolute lines: maps generated lines to edited ones. */
        List<LineDiff.Hunk> memberHunks;
        /** Edited line of the member's first line minus generated line of it. */
        int memberOffset;
    }

    /**
     * Edited line where generated line {@code p} starts (start of a range: what was inserted in front of p comes
     * before it) or ends (end of a range, exclusive: an insertion at p comes after).
     */
    private static int editedLine(List<LineDiff.Hunk> hunks, int p, boolean start) {
        int delta = 0;
        for (LineDiff.Hunk h : hunks) {
            boolean before = h.aEnd < p || h.aEnd == p && (h.aStart < h.aEnd || start);
            if (!before) break;
            delta += (h.bEnd - h.bStart) - (h.aEnd - h.aStart);
        }
        return p + delta;
    }

    private final List<Edit> edits = new ArrayList<>();
    /** Variables: index in the activity's variable list → new value (null = remove); additions at the end. */
    private final Map<Integer, Pair<Integer, String>> variableChanges = new LinkedHashMap<>();
    private final Set<Integer> variableRemovals = new LinkedHashSet<>();
    private final List<Pair<Integer, String>> variableAdditions = new ArrayList<>();
    private final Set<Integer> listRemovals = new LinkedHashSet<>();
    private final List<String[]> moreBlockAdditions = new ArrayList<>();   // {name, spec, body}
    private final List<String> moreBlockRemovals = new ArrayList<>();
    private final Set<String> importsAdded = new LinkedHashSet<>();
    private final Set<String> originalImports = new HashSet<>();
    private final Set<String> importsRemoved = new LinkedHashSet<>();
    /** Names of removed variables and lists: no typed block may use them any more. */
    private final List<String> removedNames = new ArrayList<>();

    public Result save(String edited) {
        Result result = new Result();
        if (map.problem != null) {
            result.problems.add(new Problem(0, "This activity can't be turned back into blocks: " + map.problem));
            return result;
        }
        JavaMembers g = new JavaMembers(generated);
        JavaMembers e = new JavaMembers(edited);
        if (g.problem != null) {
            result.problems.add(new Problem(0, "The generated code couldn't be read: " + g.problem));
            return result;
        }
        if (e.problem != null) {
            result.problems.add(new Problem(0, "The code couldn't be read: " + e.problem));
            return result;
        }
        if (canonical(g).equals(canonical(e))) {
            result.ok = true;
            result.unchanged = true;
            return result;
        }
        plan(g, e, result);
        if (!result.problems.isEmpty()) return result;

        Snapshot before = new Snapshot(data, javaName);
        try {
            apply(e, result);
            if (result.problems.isEmpty()) checkRemovals(result);
            if (result.problems.isEmpty()) {
                String again = generator.generate();
                JavaMembers r = new JavaMembers(again);
                String difference = r.problem != null ? r.problem : firstDifference(r, e);
                if (difference != null) {
                    result.problems.add(new Problem(0, "The blocks wouldn't give exactly this code (" + difference
                            + "). Nothing was changed."));
                }
            }
        } catch (Throwable ex) {
            result.problems.add(new Problem(0, "Saving failed: " + ex + ". Nothing was changed."));
        }
        if (!result.problems.isEmpty()) {
            before.restore(data, javaName);
            result.changes.clear();
            return result;
        }
        result.ok = true;
        return result;
    }

    private void plan(JavaMembers g, JavaMembers e, Result result) {
        // Package and class line.
        if (!String.valueOf(g.packageLine).equals(String.valueOf(e.packageLine))) {
            result.problems.add(new Problem(lineOf(e, e.packageLine, 1), "The package comes from the project settings."));
        }
        if (!g.line(g.classLine).trim().equals(e.line(e.classLine).trim())) {
            result.problems.add(new Problem(e.classLine, "The class line is generated (its name from the activity, "
                    + "the class it extends from AppCompat and the file type)."));
        }
        // Imports.
        Set<String> gImports = new LinkedHashSet<>(g.imports);
        originalImports.addAll(g.imports);
        Set<String> eImports = new LinkedHashSet<>(e.imports);
        for (int k = 0; k < e.imports.size(); k++) {
            String line = e.imports.get(k);
            if (gImports.contains(line)) continue;
            Matcher m = IMPORT.matcher(line);
            if (!m.matches()) {
                result.problems.add(new Problem(e.importLines.get(k), "Not an import Sketchware can store: " + line));
                continue;
            }
            importsAdded.add(m.group(1).trim());
        }
        for (String line : gImports) {
            if (eImports.contains(line)) continue;
            Matcher m = IMPORT.matcher(line);
            String target = m.matches() ? m.group(1).trim() : line;
            if (findVariable(VAR_IMPORT, target) >= 0) {
                importsRemoved.add(target);
            } else {
                result.notes.add(line + " is generated and stays.");
            }
        }
        // Members, paired by declaration.
        Map<String, List<JavaMembers.Member>> eByKey = new HashMap<>();
        for (JavaMembers.Member m : e.members) {
            List<JavaMembers.Member> list = eByKey.get(m.key);
            if (list == null) eByKey.put(m.key, list = new ArrayList<>());
            list.add(m);
        }
        Set<JavaMembers.Member> pairedE = new HashSet<>();
        List<JavaMembers.Member> unpairedG = new ArrayList<>();
        for (JavaMembers.Member gm : g.members) {
            List<JavaMembers.Member> candidates = eByKey.get(gm.key);
            JavaMembers.Member em = null;
            if (candidates != null) {
                for (JavaMembers.Member c : candidates) {
                    if (!pairedE.contains(c)) {
                        em = c;
                        break;
                    }
                }
            }
            if (em == null) {
                unpairedG.add(gm);
                continue;
            }
            pairedE.add(em);
            if (g.canonical(gm).equals(e.canonical(em))) continue;
            planChanged(g, gm, e, em, result);
        }
        // A field whose declaration changed (type or modifiers) keeps its name: the same variable, changed.
        List<JavaMembers.Member> removedFields = new ArrayList<>();
        for (JavaMembers.Member gm : unpairedG) if (gm.kind == JavaMembers.FIELD && gm.name != null) removedFields.add(gm);
        for (JavaMembers.Member gm : unpairedG) {
            if (removedFields.contains(gm)) {
                JavaMembers.Member em = null;
                for (JavaMembers.Member c : e.members) {
                    if (!pairedE.contains(c) && c.kind == JavaMembers.FIELD && gm.name.equals(c.name)) {
                        em = c;
                        break;
                    }
                }
                if (em != null) {
                    pairedE.add(em);
                    planChanged(g, gm, e, em, result);
                    continue;
                }
            }
            planRemoved(g, gm, e, result);
        }
        for (JavaMembers.Member em : e.members) {
            if (!pairedE.contains(em)) planAdded(e, em, result);
        }
        // Lines outside every member (a comment before the closing brace, a second class after it) would be lost.
        java.util.Map<String, Integer> generatedStray = new HashMap<>();
        for (int line : g.stray) {
            String text = g.line(line).trim();
            Integer c = generatedStray.get(text);
            generatedStray.put(text, c == null ? 1 : c + 1);
        }
        for (int line : e.stray) {
            String text = e.line(line).trim();
            Integer c = generatedStray.get(text);
            if (c != null && c > 0) {
                generatedStray.put(text, c - 1);
                continue;
            }
            result.problems.add(new Problem(line, "This line is outside the class's fields and methods, so it can't be "
                    + "stored: put comments inside a method; other classes go in a Java file of their own (Code tab → + New class)."));
        }
    }

    private static final Pattern IMPORT = Pattern.compile("import\\s+((?:static\\s+)?[\\w.]+(?:\\.\\*)?)\\s*;");
    private static final Pattern MORE_BLOCK = Pattern.compile("public\\s+void\\s+_(\\w+)\\s*\\((.*)\\)");
    private static final Pattern PARAMETER = Pattern.compile("(?:final\\s+)?(.+?)\\s+_(\\w+)");

    private void planChanged(JavaMembers g, JavaMembers.Member gm, JavaMembers e, JavaMembers.Member em, Result result) {
        List<EditMap.Event> inside = eventsIn(gm);
        if (!inside.isEmpty()) {
            List<String> a = trimmedLines(g, gm.start, gm.end);
            List<String> b = trimmedLines(e, em.start, em.end);
            final int base = gm.start;
            List<LineDiff.Hunk> hunks = LineDiff.slide(LineDiff.diff(a, b), a, b,
                    h -> eventFor(inside, base + h.aStart, base + h.aEnd) != null);
            List<LineDiff.Hunk> absolute = new ArrayList<>();
            for (LineDiff.Hunk h : hunks) {
                LineDiff.Hunk x = new LineDiff.Hunk(gm.start + h.aStart, gm.start + h.aEnd, em.start + h.bStart, em.start + h.bEnd);
                absolute.add(x);
            }
            for (LineDiff.Hunk h : hunks) {
                if (allBlank(a, h.aStart, h.aEnd) && allBlank(b, h.bStart, h.bEnd)) continue;
                int ga = gm.start + h.aStart, gb = gm.start + h.aEnd;
                int ea = em.start + h.bStart, eb = em.start + h.bEnd;
                EditMap.Event event = eventFor(inside, ga, gb);
                if (event == null) {
                    result.problems.add(new Problem(Math.max(ea, em.start), generatedPart(gm)));
                    continue;
                }
                Edit edit = new Edit();
                edit.event = event;
                edit.ga = ga;
                edit.gb = gb;
                edit.ea = ea;
                edit.eb = eb;
                edit.memberHunks = absolute;
                edit.memberOffset = em.start - gm.start;
                edits.add(edit);
            }
            return;
        }
        if (gm.kind == JavaMembers.FIELD) {
            int variable = variableFor(gm.name);
            if (variable >= 0 && em.kind == JavaMembers.FIELD) {
                String declaration = declaration(e, em);
                if (declaration == null) {
                    result.problems.add(new Problem(em.start, "The field " + gm.name + " can't be stored with comments "
                            + "or annotations in it or in front of it: remove them (comments can go inside a method)."));
                    return;
                }
                if (!isFieldDeclaration(declaration)) {
                    result.problems.add(new Problem(em.start, notAField(declaration)));
                    return;
                }
                Pair<Integer, String> old = data.variables(javaName).get(variable);
                variableChanges.put(variable, new Pair<>(VAR_CUSTOM, declaration));
                if (old.first != VAR_CUSTOM) {
                    result.notes.add(gm.name + " is now a custom variable (its declaration as you wrote it).");
                }
                result.changes.add("Variable " + gm.name + ": declaration changed");
                return;
            }
            if (listFor(gm.name) >= 0) {
                result.problems.add(new Problem(em.start, "Lists are declared by the logic editor (" + gm.name
                        + "): change its type there, or remove the list and declare your own field."));
                return;
            }
        }
        result.problems.add(new Problem(em.start, generatedPart(gm)));
    }

    private void planRemoved(JavaMembers g, JavaMembers.Member gm, JavaMembers e, Result result) {
        if (gm.kind == JavaMembers.FIELD) {
            int variable = variableFor(gm.name);
            if (variable < 0) {
                // v1.8.11: a custom variable that isn't a declaration (a statement stored by v1.8.8-v1.8.10, e.g.
                // button1.setText("AiTechApp")) has no name to find it by: it is found by its generated line
                variable = customVariableWithText(String.join(" ", g.canonical(gm)));
                if (variable >= 0) {
                    variableRemovals.add(variable);
                    // a name it does declare (int arr[] = …: arr) is still checked against the blocks that use it
                    String declared = variableName(data.variables(javaName).get(variable)).replaceAll("\\s*\\[\\s*\\]", "");
                    if (declared.matches(ID)) removedNames.add(declared);
                    result.changes.add("Custom variable removed: " + data.variables(javaName).get(variable).second);
                    return;
                }
            }
            if (variable >= 0) {
                variableRemovals.add(variable);
                removedNames.add(gm.name);
                result.changes.add("Variable " + gm.name + " removed");
                return;
            }
            int list = listFor(gm.name);
            if (list >= 0) {
                listRemovals.add(list);
                removedNames.add(gm.name);
                result.changes.add("List " + gm.name + " removed");
                return;
            }
        }
        if (gm.kind == JavaMembers.METHOD && gm.name != null && gm.name.startsWith("_")) {
            String name = moreBlockName(gm.name.substring(1));
            if (name != null) {
                moreBlockRemovals.add(name);
                result.changes.add("MoreBlock " + name + " removed");
                return;
            }
        }
        result.problems.add(new Problem(nearestLine(e, gm), "Removed: " + gm.head + ". " + generatedPart(gm)));
    }

    private void planAdded(JavaMembers e, JavaMembers.Member em, Result result) {
        String declared = em.name == null ? em.head : em.name;
        if (hasCommentInFront(e, em)) {
            result.problems.add(new Problem(em.start, "The comment or annotation in front of " + declared
                    + " can't be stored with it: put comments inside a method, or remove it."));
            return;
        }
        if (em.kind == JavaMembers.FIELD) {
            String declaration = declaration(e, em);
            if (declaration == null) {
                result.problems.add(new Problem(em.start, "The field " + declared + " can't be stored with a comment "
                        + "or annotation in it: remove it (comments can go inside a method)."));
                return;
            }
            if (!isFieldDeclaration(declaration)) {
                result.problems.add(new Problem(em.start, notAField(declaration)));
                return;
            }
            variableAdditions.add(new Pair<>(VAR_CUSTOM, declaration));
            result.changes.add("Custom variable " + em.name + " added");
            return;
        }
        if (em.kind == JavaMembers.METHOD) {
            Matcher m = MORE_BLOCK.matcher(em.head);
            String why = null;
            if (m.matches()) {
                String name = m.group(1);
                String spec = moreBlockSpec(name, m.group(2));
                String existing = moreBlockName(name);
                if (spec == null) {
                    why = "its parameters can't be MoreBlock parameters (boolean, double, String, Map, List or View, each named _name)";
                } else if (existing != null && !moreBlockRemovals.contains(existing)) {
                    why = "a MoreBlock named " + name + " exists already";
                } else if (!bodyOnOwnLines(e, em)) {
                    why = "write its { at the end of the first line and its } on a line of its own";
                } else {
                    String header = header(a.a.a.Lx.getMoreBlockCode(name, spec, ""));
                    if (!stripFinal(header).equals(stripFinal(em.head + " {"))) {
                        why = "its parameter types aren't the ones a MoreBlock writes (" + header + ")";
                    } else {
                        moreBlockAdditions.add(new String[]{name, spec, body(e, em.bodyOpenLine + 1, em.bodyCloseLine)});
                        result.changes.add("MoreBlock " + name + " added");
                        return;
                    }
                }
            } else {
                why = "only methods written like a MoreBlock can be stored: public void _name(final String _text) { … }";
            }
            result.problems.add(new Problem(em.start, "The method " + em.name + " can't be stored as blocks: " + why
                    + ". Classes and other methods go in a Java file of their own (Java Manager)."));
            return;
        }
        result.problems.add(new Problem(em.start, "Only fields and MoreBlock methods can be added to the class: "
                + em.head + ". Classes go in a Java file of their own (Java Manager)."));
    }

    /** Comment or annotation lines the member starts with (they can't be stored with a variable or MoreBlock). */
    private static boolean hasCommentInFront(JavaMembers e, JavaMembers.Member em) {
        String first = e.line(em.start).trim();
        return first.startsWith("@") || first.startsWith("//") || first.startsWith("/*") || first.startsWith("*")
                && e.hasComment(em.start);
    }

    private static boolean bodyOnOwnLines(JavaMembers e, JavaMembers.Member em) {
        if (em.bodyOpenLine <= 0 || em.bodyCloseLine <= em.bodyOpenLine) return false;
        String open = e.line(em.bodyOpenLine).trim();
        String close = e.line(em.bodyCloseLine).trim();
        return open.endsWith("{") && open.indexOf('{') == open.length() - 1 && close.equals("}");
    }

    /** MoreBlock spec for "name" and the parameter text, or null if a parameter can't be one. */
    private static String moreBlockSpec(String name, String parameters) {
        StringBuilder spec = new StringBuilder(name);
        String p = parameters.trim();
        if (p.isEmpty()) return spec.toString();
        for (String parameter : splitTopLevel(p)) {
            Matcher m = PARAMETER.matcher(parameter.trim());
            if (!m.matches()) return null;
            String type = m.group(1).replaceAll("\\s+", " ").trim();
            String code = PARAMETER_TYPES.get(type);
            if (code == null) return null;
            spec.append(' ').append(code).append('.').append(m.group(2));
        }
        return spec.toString();
    }

    /** Java type → MoreBlock parameter code, as Lx.getMoreBlockCode writes them back. */
    private static final Map<String, String> PARAMETER_TYPES = new HashMap<>();

    static {
        PARAMETER_TYPES.put("boolean", "%b");
        PARAMETER_TYPES.put("double", "%d");
        PARAMETER_TYPES.put("String", "%s");
        PARAMETER_TYPES.put("HashMap<String, Object>", "%m.varMap");
        PARAMETER_TYPES.put("ArrayList<Double>", "%m.listInt");
        PARAMETER_TYPES.put("ArrayList<String>", "%m.listStr");
        PARAMETER_TYPES.put("ArrayList<HashMap<String, Object>>", "%m.listMap");
        PARAMETER_TYPES.put("View", "%m.view");
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0, from = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(from, i));
                from = i + 1;
            }
        }
        parts.add(s.substring(from));
        return parts;
    }

    private static String header(String code) {
        int brace = code.indexOf('{');
        return (brace < 0 ? code : code.substring(0, brace + 1)).replaceAll("\\s+", " ").trim();
    }

    private static String stripFinal(String s) {
        return s.replaceAll("\\bfinal\\s+", "").replaceAll("\\s+", " ").trim();
    }

    private List<EditMap.Event> eventsIn(JavaMembers.Member member) {
        List<EditMap.Event> list = new ArrayList<>();
        for (EditMap.Event event : map.events) {
            if (!event.info.marked) continue;
            if (event.start >= member.start && event.start <= member.end) list.add(event);
        }
        return list;
    }

    /** The event whose body holds generated lines [ga, gb), or the insertion point ga when the range is empty. */
    private static EditMap.Event eventFor(List<EditMap.Event> events, int ga, int gb) {
        EditMap.Event best = null;
        for (EditMap.Event event : events) {
            boolean fits;
            if (event.empty && !event.ownLine) {
                // nothing is written for the body: only lines inserted where it is
                fits = ga == gb && ga == event.start;
            } else if (event.empty) {
                fits = ga < gb ? ga >= event.start && gb - 1 <= event.start : ga == event.start || ga == event.start + 1;
            } else if (ga < gb) {
                fits = ga >= event.start && gb - 1 <= event.end;
            } else {
                fits = ga >= event.start && ga <= event.end + 1;
            }
            if (fits && (best == null || event.end - event.start < best.end - best.start)) best = event;
        }
        return best;
    }

    private String generatedPart(JavaMembers.Member gm) {
        String name = gm.name == null ? gm.head : gm.name;
        if ("initialize".equals(name)) {
            return "initialize() is generated: the view lookups come from the View tab, the listeners from the events "
                    + "you add (write your code inside an event's method).";
        }
        if ("onCreate".equals(name) || "onCreateView".equals(name)) {
            return "onCreate is generated: put your code in the onCreate event (initializeLogic).";
        }
        if (gm.kind == JavaMembers.FIELD) {
            return "the field " + name + " is generated (a widget, a component or a list): change it in the View tab "
                    + "or the logic editor.";
        }
        if (gm.kind == JavaMembers.TYPE) {
            return "the class " + name + " is generated (a ListView/RecyclerView/ViewPager adapter): its code goes in "
                    + "the onBindCustomView event.";
        }
        return name + " is generated by Sketchware: only the code inside events, MoreBlocks, fields and imports can "
                + "be changed here.";
    }

    // ------------------------------------------------------------------------------------------------ applying

    private void apply(JavaMembers e, Result result) {
        // Events: group the edits.
        Map<EditMap.Event, List<Edit>> byEvent = new LinkedHashMap<>();
        for (Edit edit : edits) {
            List<Edit> list = byEvent.get(edit.event);
            if (list == null) byEvent.put(edit.event, list = new ArrayList<>());
            list.add(edit);
        }
        for (Map.Entry<EditMap.Event, List<Edit>> entry : byEvent.entrySet()) {
            applyEvent(entry.getKey(), entry.getValue(), e, result);
        }
        // Variables and lists.
        ArrayList<Pair<Integer, String>> variables = data.variables(javaName);
        for (Map.Entry<Integer, Pair<Integer, String>> change : variableChanges.entrySet()) {
            variables.set(change.getKey(), change.getValue());
        }
        List<Integer> removals = new ArrayList<>(variableRemovals);
        Collections.sort(removals, Collections.reverseOrder());
        for (int index : removals) variables.remove(index);
        variables.addAll(variableAdditions);
        for (String target : importsAdded) variables.add(new Pair<>(VAR_IMPORT, target));
        for (String target : importsRemoved) {
            int index = findVariable(VAR_IMPORT, target);
            if (index >= 0) variables.remove(index);
        }
        if (!importsAdded.isEmpty()) result.changes.add("Imports added: " + importsAdded);
        if (!importsRemoved.isEmpty()) result.changes.add("Imports removed: " + importsRemoved);
        ArrayList<Pair<Integer, String>> lists = data.lists(javaName);
        List<Integer> listIndexes = new ArrayList<>(listRemovals);
        Collections.sort(listIndexes, Collections.reverseOrder());
        for (int index : listIndexes) lists.remove(index);
        // MoreBlocks.
        ArrayList<Pair<String, String>> moreBlocks = data.moreBlocks(javaName);
        for (String name : moreBlockRemovals) {
            for (int k = 0; k < moreBlocks.size(); k++) {
                if (name.equals(moreBlocks.get(k).first)) {
                    moreBlocks.remove(k);
                    break;
                }
            }
            data.blocks(javaName).remove(name + "_moreBlock");
        }
        for (String[] added : moreBlockAdditions) {
            moreBlocks.add(new Pair<>(added[0], added[1]));
            ArrayList<BlockBean> blocks = new ArrayList<>();
            if (!added[2].isEmpty()) {
                Map<String, BlockBean> nested = new LinkedHashMap<>();
                int counted = mappedBlocks;
                List<BlockBean> chain = chainFor(added[2], new int[]{1}, nested);
                mappedBlocks = counted;   // the count is reported per event; a new MoreBlock is its own change
                blocks.addAll(chain);
                for (BlockBean b : nested.values()) if (!chain.contains(b)) blocks.add(b);
            }
            data.blocks(javaName).put(added[0] + "_moreBlock", blocks);
        }
    }

    /** Where a changed run of lines goes: a run of blocks of one chain (or a place between two of them). */
    private static final class Target {
        EditMap.Block parent;        // null: the event's top chain
        int stack;                   // 1 or 2 when parent != null
        List<EditMap.Block> chain;
        int from = -1, to = -1;      // blocks [from, to] are replaced; -1 = insertion
        int insertAt;                // insertion index when from < 0
        int gStart, gEnd;            // generated lines [gStart, gEnd) the target covers
        int eStart, eEnd;            // edited lines [eStart, eEnd)
    }

    private void applyEvent(EditMap.Event event, List<Edit> eventEdits, JavaMembers e, Result result) {
        // Merge edits whose targets overlap, until none do.
        List<Edit> work = new ArrayList<>(eventEdits);
        List<Target> targets;
        while (true) {
            targets = new ArrayList<>();
            for (Edit edit : work) targets.add(locate(event, edit));
            // ascending by first line; at the same line a replacement comes after an insertion (applied bottom-up:
            // the replacement first, so the insertion links to the new block)
            List<Integer> order = new ArrayList<>();
            for (int k = 0; k < targets.size(); k++) order.add(k);
            final List<Target> ts = targets;
            Collections.sort(order, (x, y) -> {
                Target a = ts.get(x), b = ts.get(y);
                if (a.gStart != b.gStart) return Integer.compare(a.gStart, b.gStart);
                return Boolean.compare(a.from >= 0, b.from >= 0);
            });
            int mergeA = -1, mergeB = -1;
            for (int k = 0; k + 1 < order.size() && mergeA < 0; k++) {
                Target a = targets.get(order.get(k)), b = targets.get(order.get(k + 1));
                boolean overlap = b.gStart == b.gEnd
                        ? a.gStart < b.gStart && b.gStart < a.gEnd
                        : b.gStart < a.gEnd;
                if (overlap) {
                    mergeA = order.get(k);
                    mergeB = order.get(k + 1);
                }
            }
            if (mergeA < 0) {
                List<Target> sorted = new ArrayList<>();
                for (int k : order) sorted.add(targets.get(k));
                targets = sorted;
                break;
            }
            Target tx = targets.get(mergeA), ty = targets.get(mergeB);
            Edit joined = new Edit();
            joined.event = event;
            joined.ga = Math.min(tx.gStart, ty.gStart);
            joined.gb = Math.max(tx.gEnd, ty.gEnd);
            joined.ea = Math.min(tx.eStart, ty.eStart);
            joined.eb = Math.max(tx.eEnd, ty.eEnd);
            joined.memberHunks = work.get(mergeA).memberHunks;
            joined.memberOffset = work.get(mergeA).memberOffset;
            List<Edit> next = new ArrayList<>();
            for (int k = 0; k < work.size(); k++) {
                if (k != mergeA && k != mergeB) next.add(work.get(k));
            }
            next.add(joined);
            work = next;
        }

        String key = event.info.key();
        ArrayList<BlockBean> original = data.blocks(javaName).get(key);
        Map<String, BlockBean> live = new LinkedHashMap<>();
        if (original != null) for (BlockBean block : original) live.put(block.id, block.clone());
        String[] head = {original != null && !original.isEmpty() ? original.get(0).id : null};
        int[] nextId = {1};
        for (String id : live.keySet()) {
            try {
                nextId[0] = Math.max(nextId[0], Integer.parseInt(id) + 1);
            } catch (NumberFormatException ignored) {
                // marked events have numeric ids only
            }
        }
        int replacedBlocks = 0, addedBlocks = 0;
        // Bottom-up, so the blocks above a change keep their places.
        for (int k = targets.size() - 1; k >= 0; k--) {
            Target t = targets.get(k);
            String content = body(e, t.eStart, t.eEnd);
            List<BlockBean> chain = content.isEmpty() ? new ArrayList<BlockBean>() : chainFor(content, nextId, live);
            BlockBean added = chain.isEmpty() ? null : chain.get(0);
            BlockBean lastAdded = chain.isEmpty() ? null : chain.get(chain.size() - 1);
            String after;      // id the new block (or the removal) continues with
            String before;     // id of the block in front, or null (head of the chain)
            if (t.from >= 0) {
                BlockBean last = live.get(t.chain.get(t.to).id);
                after = last == null || last.nextBlock < 0 ? null : String.valueOf(last.nextBlock);
                before = t.from > 0 ? t.chain.get(t.from - 1).id : null;
                for (int i = t.from; i <= t.to; i++) {
                    replacedBlocks++;
                    removeWithChildren(live, t.chain.get(i).id);
                }
            } else {
                before = t.insertAt > 0 ? t.chain.get(t.insertAt - 1).id : null;
                if (before != null) {
                    BlockBean previous = live.get(before);
                    after = previous == null || previous.nextBlock < 0 ? null : String.valueOf(previous.nextBlock);
                } else if (t.parent == null) {
                    after = head[0];
                } else {
                    BlockBean parent = live.get(t.parent.id);
                    int sub = t.stack == 1 ? parent.subStack1 : parent.subStack2;
                    after = sub < 0 ? null : String.valueOf(sub);
                }
            }
            String continueWith = added != null ? added.id : after;
            if (added != null) {
                lastAdded.nextBlock = after == null ? -1 : Integer.parseInt(after);
                for (BlockBean b : chain) {
                    live.put(b.id, b);
                    if ("addSourceDirectly".equals(b.opCode)) addedBlocks++;
                }
            }
            if (before != null) {
                live.get(before).nextBlock = continueWith == null ? -1 : Integer.parseInt(continueWith);
            } else if (t.parent == null) {
                head[0] = continueWith;
            } else {
                BlockBean parent = live.get(t.parent.id);
                int value = continueWith == null ? -1 : Integer.parseInt(continueWith);
                if (t.stack == 1) parent.subStack1 = value;
                else parent.subStack2 = value;
            }
        }
        ArrayList<BlockBean> list = new ArrayList<>();
        if (head[0] != null && live.containsKey(head[0])) list.add(live.get(head[0]));
        for (BlockBean block : live.values()) {
            if (!block.id.equals(head[0])) list.add(block);
        }
        if (head[0] == null && !list.isEmpty()) {
            // Only blocks that aren't reachable from the event stay (none of ours): keep them, but nothing runs.
            list.clear();
        }
        data.blocks(javaName).put(key, list);
        int mapped = mappedBlocks;
        mappedBlocks = 0;
        result.changes.add(SourceName.of(event) + ": " + (replacedBlocks == 0 ? "" : replacedBlocks + " block"
                + (replacedBlocks == 1 ? "" : "s") + " replaced, ") + (mapped == 0 ? "" : mapped + " block"
                + (mapped == 1 ? "" : "s") + " from your code, ") + addedBlocks + " add source block"
                + (addedBlocks == 1 ? "" : "s"));
    }

    private Target locate(EditMap.Event event, Edit edit) {
        Target t = new Target();
        t.chain = event.top;
        t.parent = null;
        t.stack = 0;
        locateIn(t, edit.ga, edit.gb);
        // The edited lines of the whole target: generated lines mapped through every change of the member
        if (edit.memberHunks != null) {
            t.eStart = Math.min(edit.ea, editedLine(edit.memberHunks, t.gStart, true) + edit.memberOffset);
            t.eEnd = Math.max(edit.eb, editedLine(edit.memberHunks, t.gEnd, false) + edit.memberOffset);
        } else {
            t.eStart = edit.ea - (edit.ga - t.gStart);
            t.eEnd = edit.eb + (t.gEnd - edit.gb);
        }
        return t;
    }

    private void locateIn(Target t, int ga, int gb) {
        List<EditMap.Block> chain = t.chain;
        if (ga < gb) {
            int first = -1, last = -1;
            for (int i = 0; i < chain.size(); i++) {
                EditMap.Block b = chain.get(i);
                if (b.end < b.start) continue;
                if (b.end >= ga && b.start <= gb - 1) {
                    if (first < 0) first = i;
                    last = i;
                }
            }
            if (first >= 0 && first == last) {
                EditMap.Block b = chain.get(first);
                for (int s = 1; s <= 2; s++) {
                    List<EditMap.Block> sub = b.stacks[s];
                    if (sub.isEmpty()) continue;
                    int lo = sub.get(0).start, hi = sub.get(sub.size() - 1).end;
                    if (ga >= lo && gb - 1 <= hi) {
                        t.parent = b;
                        t.stack = s;
                        t.chain = sub;
                        locateIn(t, ga, gb);
                        return;
                    }
                }
            }
            if (first < 0) {
                // only lines that belong to no block of this chain (cannot happen in generated code): insert
                t.insertAt = countBefore(chain, ga);
                t.gStart = ga;
                t.gEnd = gb;
                return;
            }
            t.from = first;
            t.to = last;
            t.gStart = Math.min(ga, chain.get(first).start);
            t.gEnd = Math.max(gb, chain.get(last).end + 1);
            return;
        }
        // Insertion in front of generated line ga.
        for (int i = 0; i < chain.size(); i++) {
            EditMap.Block b = chain.get(i);
            if (b.end < b.start) continue;
            if (ga > b.start && ga <= b.end) {
                for (int s = 1; s <= 2; s++) {
                    List<EditMap.Block> sub = b.stacks[s];
                    if (sub.isEmpty()) continue;
                    int lo = sub.get(0).start, hi = sub.get(sub.size() - 1).end;
                    if (ga >= lo && ga <= hi + 1) {
                        t.parent = b;
                        t.stack = s;
                        t.chain = sub;
                        locateIn(t, ga, gb);
                        return;
                    }
                }
                // inside the block's own lines (e.g. an empty if): the block is written again with the new lines
                t.from = i;
                t.to = i;
                t.gStart = b.start;
                t.gEnd = b.end + 1;
                return;
            }
        }
        t.insertAt = countBefore(chain, ga);
        t.gStart = ga;
        t.gEnd = ga;
    }

    private static int countBefore(List<EditMap.Block> chain, int line) {
        int n = 0;
        for (EditMap.Block b : chain) {
            if (b.start < line) n++;
        }
        return n;
    }

    private static void removeWithChildren(Map<String, BlockBean> live, String id) {
        BlockBean block = live.remove(id);
        if (block == null) return;
        removeChain(live, block.subStack1);
        removeChain(live, block.subStack2);
        for (String parameter : block.parameters) {
            if (parameter != null && parameter.length() > 1 && parameter.charAt(0) == '@') {
                removeWithChildren(live, parameter.substring(1));
            }
        }
    }

    private static void removeChain(Map<String, BlockBean> live, int first) {
        Set<Integer> seen = new HashSet<>();
        int current = first;
        while (current >= 0 && seen.add(current)) {
            BlockBean block = live.get(String.valueOf(current));
            if (block == null) return;
            int next = block.nextBlock;
            removeWithChildren(live, block.id);
            current = next;
        }
    }

    /**
     * v1.8.9: the blocks for edited code: each complete one-line statement outside any braces that a block writes
     * exactly ({@link StatementBlocks}) becomes that block; the other lines, in runs, become "add source directly"
     * blocks. Returns the chain (linked by nextBlock, the last one's nextBlock -1); variable blocks used as parameters
     * go into {@code live} only.
     */
    private List<BlockBean> chainFor(String content, int[] nextId, Map<String, BlockBean> live) {
        List<BlockBean> chain = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        int lastCode = lines.length - 1;
        while (lastCode >= 0 && lines[lastCode].trim().isEmpty()) lastCode--;
        StringBuilder pending = new StringBuilder();
        int depth = 0;
        boolean[] inComment = {false};
        String previous = null;   // the last non-blank code line before this one
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            int before = depth;
            boolean commentBefore = inComment[0];
            depth += braceDelta(line, inComment);
            String trimmed = line.trim();
            ArrayList<BlockBean> mapped = null;
            // only a whole statement of its own: not inside braces or a block comment, not the body of a braceless
            // if / else / loop on the line before (the previous code line ends a statement or a block)
            boolean own = previous == null || previous.endsWith(";") || previous.endsWith("{") || previous.endsWith("}");
            if (statements != null && before == 0 && depth == 0 && !commentBefore && !inComment[0] && own
                    && !trimmed.contains("/*") && !trimmed.contains("*/") && trimmed.endsWith(";")) {
                mapped = statements.map(line, nextId);
                // Finish Activity is a cap block: nothing may hang under it, so only as the last statement
                if (mapped != null && "finishActivity".equals(mapped.get(0).opCode) && n != lastCode) {
                    mapped = null;   // its ids stay unused (gaps are fine; ids are only unique)
                }
            }
            if (!trimmed.isEmpty() && !commentBefore && !trimmed.startsWith("//")) previous = codePart(trimmed);
            if (mapped == null) {
                if (pending.length() > 0) pending.append('\n');
                pending.append(line);
                continue;
            }
            if (pending.toString().trim().length() > 0) chain.add(addSource(String.valueOf(nextId[0]++), pending.toString().trim()));
            pending.setLength(0);
            chain.add(mapped.get(0));
            mappedBlocks++;
            for (int i = 1; i < mapped.size(); i++) live.put(mapped.get(i).id, mapped.get(i));
        }
        if (pending.toString().trim().length() > 0) chain.add(addSource(String.valueOf(nextId[0]++), pending.toString().trim()));
        for (int i = 0; i < chain.size(); i++) {
            chain.get(i).nextBlock = i + 1 < chain.size() ? Integer.parseInt(chain.get(i + 1).id) : -1;
        }
        return chain;
    }

    /** A code line without a trailing // comment (approximate: a // inside a string ends it too early, which only
     * makes the next line stay "add source"). */
    private static String codePart(String trimmed) {
        int c = trimmed.indexOf("//");
        return (c >= 0 ? trimmed.substring(0, c) : trimmed).trim();
    }

    /** '{' minus '}' outside strings, characters and comments of one line; {@code inComment} carries a block comment
     * over line ends. */
    private static int braceDelta(String line, boolean[] inComment) {
        int d = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inComment[0]) {
                if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    inComment[0] = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') break;
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                inComment[0] = true;
                i++;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < line.length() && line.charAt(j) != c) {
                    if (line.charAt(j) == '\\') j++;
                    j++;
                }
                i = j;
            } else if (c == '{') {
                d++;
            } else if (c == '}') {
                d--;
            }
        }
        return d;
    }

    static BlockBean addSource(String id, String code) {
        BlockBean block = new BlockBean(id, "add source directly %s.inputOnly", " ", "", "addSourceDirectly");
        block.color = 0xff5cb722;
        block.parameters.add(code);
        return block;
    }

    /** Edited lines [from, to) without their indentation (the generator indents again), blank ones at the ends left out. */
    static String body(JavaMembers e, int from, int to) {
        int a = from, b = to;
        while (a < b && e.line(a).trim().isEmpty()) a++;
        while (b > a && e.line(b - 1).trim().isEmpty()) b--;
        StringBuilder out = new StringBuilder();
        for (int k = a; k < b; k++) {
            if (out.length() > 0) out.append('\n');
            out.append(stripLeading(e.line(k)));
        }
        return out.toString();
    }

    private static String stripLeading(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        int end = s.length();
        while (end > i && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t')) end--;
        return s.substring(i, end);
    }

    // ------------------------------------------------------------------------------------------------ checks

    /** Removed variables, lists and MoreBlocks must not be used by blocks any more (after the edits). */
    private void checkRemovals(Result result) {
        HashMap<String, ArrayList<BlockBean>> blocks = data.blocks(javaName);
        for (String name : removedNames) {
            for (Map.Entry<String, ArrayList<BlockBean>> entry : blocks.entrySet()) {
                for (BlockBean block : entry.getValue()) {
                    if ("addSourceDirectly".equals(block.opCode)) continue;
                    if (name.equals(block.spec) || block.parameters.contains(name)) {
                        result.problems.add(new Problem(0, name + " is still used by a block in "
                                + eventLabel(entry.getKey()) + ": remove that block first (or keep the declaration)."));
                        return;
                    }
                }
            }
        }
        for (String name : moreBlockRemovals) {
            for (Map.Entry<String, ArrayList<BlockBean>> entry : blocks.entrySet()) {
                for (BlockBean block : entry.getValue()) {
                    if ("definedFunc".equals(block.opCode) && block.spec != null
                            && (block.spec.equals(name) || block.spec.startsWith(name + " "))) {
                        result.problems.add(new Problem(0, "MoreBlock " + name + " is still called by a block in "
                                + eventLabel(entry.getKey()) + ": remove that block first."));
                        return;
                    }
                }
            }
        }
    }

    private static String eventLabel(String key) {
        if (key.endsWith("_moreBlock")) return "MoreBlock " + key.substring(0, key.length() - "_moreBlock".length());
        if (key.equals("onCreate_initializeLogic")) return "onCreate";
        return key.replace('_', ' ');
    }

    /** First difference between the regenerated code and the edited code, or null when they are the same. */
    private String firstDifference(JavaMembers r, JavaMembers e) {
        if (!String.valueOf(r.packageLine).equals(String.valueOf(e.packageLine))) return "package";
        Set<String> expected = new HashSet<>(e.imports);
        Set<String> got = new HashSet<>(r.imports);
        // generated imports the user removed come back
        for (String line : got) {
            if (!expected.contains(line)) {
                Matcher m = IMPORT.matcher(line);
                String target = m.matches() ? m.group(1).trim() : line;
                if (importsRemoved.contains(target)) return "import " + target + " is still there";
            }
        }
        for (String line : expected) {
            // an automatic library import that is no longer needed (or would now be ambiguous) goes away by itself
            if (!got.contains(line) && !(automaticImport(line) && originalImports.contains(line))) return "missing " + line;
        }
        if (!r.line(r.classLine).trim().equals(e.line(e.classLine).trim())) return "class line";
        Map<String, Integer> counts = new HashMap<>();
        for (JavaMembers.Member m : r.members) {
            String text = memberText(r, m);
            Integer c = counts.get(text);
            counts.put(text, c == null ? 1 : c + 1);
        }
        for (JavaMembers.Member m : e.members) {
            String text = memberText(e, m);
            Integer c = counts.get(text);
            if (c == null || c == 0) return "line " + m.start + ", " + (m.name != null ? m.name : m.head);
            counts.put(text, c - 1);
        }
        for (Map.Entry<String, Integer> left : counts.entrySet()) {
            if (left.getValue() > 0) {
                String text = left.getKey();
                return "the blocks also write " + text.substring(0, Math.min(60, text.indexOf('\n') < 0 ? text.length() : text.indexOf('\n')));
            }
        }
        return null;
    }

    private static String memberText(JavaMembers f, JavaMembers.Member m) {
        if (m.kind == JavaMembers.FIELD) {
            StringBuilder joined = new StringBuilder();
            for (String line : f.canonical(m)) joined.append(line).append(' ');
            return joined.toString().replaceAll("\\s+", " ").trim() + "\n";
        }
        StringBuilder text = new StringBuilder();
        boolean inHeader = m.kind == JavaMembers.METHOD;
        for (String line : f.canonical(m)) {
            // a parameter's "final" changes nothing when the method runs (MoreBlocks always write it)
            if (inHeader) {
                if (line.contains("{")) inHeader = false;
                line = stripFinal(line);
            }
            text.append(line).append('\n');
        }
        return text.toString();
    }

    private static String canonical(JavaMembers f) {
        StringBuilder s = new StringBuilder();
        s.append(f.packageLine).append('\n');
        List<String> imports = new ArrayList<>(new java.util.TreeSet<>(f.imports));
        for (String i : imports) s.append(i).append('\n');
        for (int k = f.classLine; k <= f.count(); k++) {
            String t = f.line(k).trim();
            if (!t.isEmpty()) s.append(t).append('\n');
        }
        return s.toString();
    }

    // ------------------------------------------------------------------------------------------------ data

    private int variableFor(String name) {
        if (name == null) return -1;
        ArrayList<Pair<Integer, String>> variables = data.variables(javaName);
        for (int k = 0; k < variables.size(); k++) {
            Pair<Integer, String> v = variables.get(k);
            if (v.first == VAR_IMPORT) continue;
            if (name.equals(variableName(v))) return k;
        }
        return -1;
    }

    /** The custom variable (type 6) the class body shows as this text (the stored text + ";"), or -1. */
    private int customVariableWithText(String generated) {
        String text = generated.trim().replaceAll("\\s+", " ");
        if (text.endsWith(";")) text = text.substring(0, text.length() - 1).trim();
        ArrayList<Pair<Integer, String>> variables = data.variables(javaName);
        for (int k = 0; k < variables.size(); k++) {
            Pair<Integer, String> v = variables.get(k);
            if (v.first != VAR_CUSTOM || v.second == null) continue;
            String stored = v.second.trim().replaceAll("\\s+", " ");
            if (stored.endsWith(";")) stored = stored.substring(0, stored.length() - 1).trim();
            if (stored.equals(text)) return k;
        }
        return -1;
    }

    /** The name a variable declares: the name itself, or the identifier of a custom declaration. */
    static String variableName(Pair<Integer, String> v) {
        String value = v.second == null ? "" : v.second.trim();
        if (v.first == 5 || v.first == VAR_CUSTOM) {
            String head = value;
            int eq = head.indexOf('=');
            if (eq >= 0) head = head.substring(0, eq);
            head = head.replace(";", "").trim();
            int space = head.lastIndexOf(' ');
            return space < 0 ? head : head.substring(space + 1);
        }
        return value;
    }

    private int listFor(String name) {
        if (name == null) return -1;
        ArrayList<Pair<Integer, String>> lists = data.lists(javaName);
        for (int k = 0; k < lists.size(); k++) {
            if (name.equals(lists.get(k).second)) return k;
        }
        return -1;
    }

    private int findVariable(int type, String value) {
        ArrayList<Pair<Integer, String>> variables = data.variables(javaName);
        for (int k = 0; k < variables.size(); k++) {
            Pair<Integer, String> v = variables.get(k);
            if (v.first == type && value.equals(v.second == null ? null : v.second.trim())) return k;
        }
        return -1;
    }

    private String moreBlockName(String name) {
        for (Pair<String, String> moreBlock : data.moreBlocks(javaName)) {
            if (name.equals(mod.hey.studios.moreblock.ReturnMoreblockManager.getMbName(moreBlock.first))) return moreBlock.first;
        }
        return null;
    }

    /**
     * A field's declaration as written, on one line (the project file stores a variable per line: its lines are joined
     * with spaces) and without its ';' (the generator adds it); null when a line of it is a comment or an annotation or
     * has a // comment (those can't be joined).
     */
    private static String declaration(JavaMembers e, JavaMembers.Member m) {
        StringBuilder s = new StringBuilder();
        for (int k = m.start; k <= m.end; k++) {
            String t = e.line(k).trim();
            if (t.isEmpty()) continue;
            // a comment (not "//" inside a string such as a URL) or an annotation line can't be kept in one stored line
            if (e.hasComment(k) || t.startsWith("@")) {
                return null;
            }
            if (s.length() > 0) s.append(' ');
            s.append(t);
        }
        String text = s.toString().replaceAll("\\s+", " ");
        if (text.endsWith(";")) text = text.substring(0, text.length() - 1).trim();
        return text.isEmpty() || text.contains("\n") ? null : text;
    }

    private static final java.util.Set<String> KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var"));
    private static final String ID = "[\\p{L}_$][\\p{L}\\p{N}_$]*";   // Java identifiers, not only ASCII
    private static final Pattern FIELD_HEAD = Pattern.compile(
            "((?:(?:public|protected|private|static|final|transient|volatile)\\s+)*)"   // modifiers
            + "(" + ID + "(?:\\s*\\.\\s*" + ID + ")*"                                     // type name
            + "(?:\\s*<[\\p{L}\\p{N}_$\\s,.?<>\\[\\]&]*>)?(?:\\s*\\[\\s*\\])*)"          // generics, arrays
            + "\\s+(" + ID + ")(?:\\s*\\[\\s*\\])*"                                         // name
            + "(?:\\s*,\\s*" + ID + "(?:\\s*\\[\\s*\\])*)*");                                // int a, b (= 0)

    /**
     * v1.8.11: whether a stored declaration (no ';') is one field the logic editor can read: modifiers, a type and one
     * name, optionally "= value". A statement such as {@code button1.setText("x")} that ended up in the class body is
     * not: stored as a custom variable it broke the logic editor ("Found invalid data of Custom Variable") and the build.
     */
    static boolean isFieldDeclaration(String declaration) {
        if (declaration == null) return false;
        String text = declaration.trim();
        int equals = -1, depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == '=' && depth == 0) {
                equals = i;
                break;
            } else if ((c == '(' || c == '"' || c == '\'' || c == '{' || c == ';') && depth == 0) {
                return false;   // a call, a literal or a block before any '=': not a declaration
            }
        }
        String head = (equals < 0 ? text : text.substring(0, equals)).trim();
        if (equals >= 0 && text.substring(equals + 1).trim().isEmpty()) return false;
        Matcher m = FIELD_HEAD.matcher(head);
        if (!m.matches()) return false;
        String type = m.group(2).replaceAll("\\s+", "");
        String name = m.group(3);
        if (KEYWORDS.contains(name)) return false;
        String bare = type.contains("<") ? type.substring(0, type.indexOf('<')) : type.replace("[]", "");
        return !KEYWORDS.contains(bare) || bare.matches("boolean|byte|char|short|int|long|float|double");
    }

    private static String notAField(String text) {
        return "This isn't a field declaration (type, name, optionally = value): " + text.trim()
                + ". Statements go inside a method: an event (onCreate, a click) or a MoreBlock.";
    }

    private static List<String> trimmedLines(JavaMembers f, int from, int to) {
        List<String> out = new ArrayList<>();
        for (int k = from; k <= to; k++) out.add(f.line(k).trim());
        return out;
    }

    private static boolean allBlank(List<String> lines, int from, int to) {
        for (int k = from; k < to; k++) if (!lines.get(k).isEmpty()) return false;
        return true;
    }

    private static int lineOf(JavaMembers f, String text, int fallback) {
        if (text == null) return fallback;
        for (int k = 1; k <= f.count(); k++) if (f.line(k).trim().equals(text)) return k;
        return fallback;
    }

    private static int nearestLine(JavaMembers e, JavaMembers.Member gm) {
        return Math.max(1, Math.min(e.count(), gm.start));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** The project data's maps for one activity (created when missing). */
    static final class eCAccess {
        final a.a.a.eC data;

        eCAccess(a.a.a.eC data) {
            this.data = data;
        }

        HashMap<String, ArrayList<BlockBean>> blocks(String javaName) {
            HashMap<String, ArrayList<BlockBean>> map = data.d.get(javaName);
            if (map == null) data.d.put(javaName, map = new HashMap<>());
            return map;
        }

        ArrayList<Pair<Integer, String>> variables(String javaName) {
            ArrayList<Pair<Integer, String>> list = data.e.get(javaName);
            if (list == null) data.e.put(javaName, list = new ArrayList<>());
            return list;
        }

        ArrayList<Pair<Integer, String>> lists(String javaName) {
            ArrayList<Pair<Integer, String>> list = data.f.get(javaName);
            if (list == null) data.f.put(javaName, list = new ArrayList<>());
            return list;
        }

        ArrayList<Pair<String, String>> moreBlocks(String javaName) {
            ArrayList<Pair<String, String>> list = data.g.get(javaName);
            if (list == null) data.g.put(javaName, list = new ArrayList<>());
            return list;
        }
    }

    /** Copy of an activity's blocks, variables, lists and MoreBlocks, put back when a save is refused. */
    private static final class Snapshot {
        final HashMap<String, ArrayList<BlockBean>> blocks = new HashMap<>();
        final ArrayList<Pair<Integer, String>> variables;
        final ArrayList<Pair<Integer, String>> lists;
        final ArrayList<Pair<String, String>> moreBlocks;
        final boolean hadBlocks, hadVariables, hadLists, hadMoreBlocks;

        Snapshot(eCAccess data, String javaName) {
            hadBlocks = data.data.d.containsKey(javaName);
            hadVariables = data.data.e.containsKey(javaName);
            hadLists = data.data.f.containsKey(javaName);
            hadMoreBlocks = data.data.g.containsKey(javaName);
            for (Map.Entry<String, ArrayList<BlockBean>> entry : data.blocks(javaName).entrySet()) {
                ArrayList<BlockBean> copy = new ArrayList<>();
                for (BlockBean block : entry.getValue()) copy.add(block.clone());
                blocks.put(entry.getKey(), copy);
            }
            variables = new ArrayList<>(data.variables(javaName));
            lists = new ArrayList<>(data.lists(javaName));
            moreBlocks = new ArrayList<>(data.moreBlocks(javaName));
        }

        void restore(eCAccess data, String javaName) {
            if (hadBlocks) data.data.d.put(javaName, blocks);
            else data.data.d.remove(javaName);
            if (hadVariables) data.data.e.put(javaName, variables);
            else data.data.e.remove(javaName);
            if (hadLists) data.data.f.put(javaName, lists);
            else data.data.f.remove(javaName);
            if (hadMoreBlocks) data.data.g.put(javaName, moreBlocks);
            else data.data.g.remove(javaName);
        }
    }

    /** Human names of events for the save report. */
    static final class SourceName {
        static String of(EditMap.Event event) {
            switch (event.info.kind) {
                case "onCreate":
                    return "onCreate";
                case "moreBlock":
                    return "MoreBlock " + event.info.targetId;
                case "adapter":
                    return event.info.targetId + " onBindCustomView";
                default:
                    return event.info.targetId + " " + event.info.eventName;
            }
        }
    }
}
