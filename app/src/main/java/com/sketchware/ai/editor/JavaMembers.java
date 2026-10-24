package com.sketchware.ai.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits one Java file into what the Java editor compares (v1.8.8): the lines before the class (package, imports),
 * the class header, the class's members (fields, methods, inner classes; with the annotations and comments in front
 * of them) and the closing brace. Strings, characters and comments are skipped when counting braces.
 * <p>
 * Lines are 1-based, split at "\n" (a "\r" in front of it belongs to the line break).
 */
public final class JavaMembers {

    public static final int FIELD = 0;
    public static final int METHOD = 1;
    public static final int TYPE = 2;
    public static final int OTHER = 3;

    public static final class Member {
        /** First and last line (1-based), annotations and comments in front included. */
        public int start;
        public int end;
        public int kind;
        /** Identity used to pair the same member in two versions: kind + declaration head, whitespace collapsed. */
        public String key;
        /** Declaration head: text in front of the first top-level '{', '=' or ';' (annotations and comments left out). */
        public String head;
        /** Name of a field or method (the identifier in front of '=' / ';' / '('), or null. */
        public String name;
        /** For a method: line of its opening brace and of its closing brace. */
        public int bodyOpenLine;
        public int bodyCloseLine;
    }

    public final String[] lines;
    /** Brace depth at the start of each line (index 1..n). */
    public final int[] depth;
    public final List<String> imports = new ArrayList<>();
    public final List<Integer> importLines = new ArrayList<>();
    public String packageLine;
    public int packageLineNumber;
    /** Lines that are none of: package, import, the class line, a member (with what is in front of it), the closing brace. */
    public final List<Integer> stray = new ArrayList<>();
    public int classLine;
    public int closeLine;
    public final List<Member> members = new ArrayList<>();
    /** Null when the file has the expected shape; otherwise what is wrong. */
    public String problem;

    public JavaMembers(String text) {
        lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].endsWith("\r")) lines[i] = lines[i].substring(0, lines[i].length() - 1);
        }
        depth = new int[lines.length + 2];
        parse();
        if (problem == null) findStray();
    }

    private void findStray() {
        boolean[] covered = new boolean[lines.length + 2];
        if (packageLineNumber > 0) covered[packageLineNumber] = true;
        for (int line : importLines) covered[line] = true;
        covered[classLine] = true;
        covered[closeLine] = true;
        for (Member m : members) for (int k = m.start; k <= m.end; k++) covered[k] = true;
        for (int k = 1; k <= lines.length; k++) {
            if (!covered[k] && !lines[k - 1].trim().isEmpty()) stray.add(k);
        }
    }

    public String line(int number) {
        return lines[number - 1];
    }

    public int count() {
        return lines.length;
    }

    /** Lines of the file with the code (strings and comments blanked) for brace counting. */
    private String[] code;
    /** Lines that hold (part of) a comment outside string and character literals. */
    private boolean[] commented;

    /** Whether line {@code k} (1-based) holds (part of) a comment; "//" inside a string literal is not one. */
    public boolean hasComment(int k) {
        return k >= 1 && k <= commented.length && commented[k - 1];
    }

    private void parse() {
        code = new String[lines.length];
        commented = new boolean[lines.length];
        boolean inBlockComment = false;
        int d = 0;
        for (int i = 0; i < lines.length; i++) {
            depth[i + 1] = d;
            String s = lines[i];
            StringBuilder c = new StringBuilder(s.length());
            int j = 0;
            if (inBlockComment) commented[i] = true;
            while (j < s.length()) {
                char ch = s.charAt(j);
                if (inBlockComment) {
                    if (ch == '*' && j + 1 < s.length() && s.charAt(j + 1) == '/') {
                        inBlockComment = false;
                        c.append("  ");
                        j += 2;
                    } else {
                        c.append(' ');
                        j++;
                    }
                    continue;
                }
                if (ch == '/' && j + 1 < s.length() && s.charAt(j + 1) == '/') {
                    commented[i] = true;
                    break;
                }
                if (ch == '/' && j + 1 < s.length() && s.charAt(j + 1) == '*') {
                    commented[i] = true;
                    inBlockComment = true;
                    c.append("  ");
                    j += 2;
                    continue;
                }
                if (ch == '"' || ch == '\'') {
                    char quote = ch;
                    c.append(quote);
                    j++;
                    while (j < s.length() && s.charAt(j) != quote) {
                        if (s.charAt(j) == '\\') {
                            c.append(' ');
                            j++;
                        }
                        if (j < s.length()) {
                            c.append(' ');
                            j++;
                        }
                    }
                    if (j < s.length()) {
                        c.append(quote);
                        j++;
                    }
                    continue;
                }
                if (ch == '{') d++;
                if (ch == '}') d--;
                c.append(ch);
                j++;
            }
            code[i] = c.toString();
        }
        depth[lines.length + 1] = d;
        if (d != 0) {
            problem = d > 0 ? "a '{' is not closed" : "there is a '}' too many";
        }

        // Before the class: package and imports.
        int n = lines.length;
        int i = 1;
        for (; i <= n; i++) {
            String t = code[i - 1].trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("package ")) {
                packageLine = lines[i - 1].trim();
                packageLineNumber = i;
                continue;
            }
            if (t.startsWith("import ")) {
                imports.add(lines[i - 1].trim());
                importLines.add(i);
                continue;
            }
            break;
        }
        // Class header: up to the line where depth becomes 1.
        int header = i;
        while (i <= n && depth[i + 1] < 1) i++;
        if (i > n || header > n) {
            if (problem == null) problem = "no class found";
            return;
        }
        classLine = i;
        i++;
        // Members.
        int pendingStart = -1;
        while (i <= n) {
            if (depth[i] == 0) break;
            String t = lines[i - 1].trim();
            String tc = code[i - 1].trim();
            if (depth[i] == 1 && tc.equals("}") && depth[i + 1] == 0) {
                closeLine = i;
                break;
            }
            if (t.isEmpty()) {
                i++;
                continue;
            }
            if (depth[i] == 1 && (tc.isEmpty() || tc.startsWith("@") && !tc.contains("{") && !tc.contains(";") && !tc.contains("("))) {
                // a comment line or an annotation without arguments: belongs to what follows
                if (pendingStart < 0) pendingStart = i;
                i++;
                continue;
            }
            if (depth[i] != 1) {
                if (problem == null) problem = "unexpected nesting at line " + i;
                return;
            }
            Member member = new Member();
            member.start = pendingStart >= 0 ? pendingStart : i;
            pendingStart = -1;
            int declStart = i;
            // Find the end: a ';' at depth 1, or the line where depth comes back to 1 after a '{'.
            StringBuilder head = new StringBuilder();
            boolean headDone = false;
            char stop = 0;
            int j = i;
            int end = -1;
            while (j <= n) {
                String c = code[j - 1];
                int dd = depth[j];
                for (int k = 0; k < c.length(); k++) {
                    char ch = c.charAt(k);
                    if (dd == 1 && !headDone) {
                        if (ch == '{' || ch == '=' || ch == ';') {
                            headDone = true;
                            stop = ch;
                        } else {
                            head.append(ch);
                        }
                    }
                    if (ch == '{') {
                        if (dd == 1 && member.bodyOpenLine == 0 && stop != '=') member.bodyOpenLine = j;
                        dd++;
                    } else if (ch == '}') {
                        dd--;
                        if (dd == 1) {
                            if (member.bodyOpenLine > 0 && member.bodyCloseLine == 0) member.bodyCloseLine = j;
                            // a method or type ends with its body's '}' unless a ';' follows (field initialiser)
                            if (stop == '{' && restIsBlank(c, k + 1)) {
                                end = j;
                                break;
                            }
                        }
                    } else if (ch == ';' && dd == 1) {
                        end = j;
                        break;
                    }
                }
                if (end > 0) break;
                if (!headDone) head.append(' ');
                j++;
            }
            if (end < 0) {
                if (problem == null) problem = "a declaration at line " + declStart + " doesn't end";
                return;
            }
            member.end = end;
            member.head = head.toString().trim().replaceAll("\\s+", " ");
            String h = member.head;
            if (stop == '{' && (h.matches(".*\\b(class|interface|enum)\\s+\\w+.*") && !h.contains("("))) {
                member.kind = TYPE;
            } else if (stop == '{' && h.endsWith(")") || stop == '{' && h.matches(".*\\)\\s*throws\\s+[\\w.,\\s]+")) {
                member.kind = METHOD;
            } else if (stop == '=' || stop == ';') {
                member.kind = FIELD;
            } else {
                member.kind = OTHER;
            }
            member.name = nameOf(member);
            member.key = member.kind + ":" + h;
            members.add(member);
            i = end + 1;
        }
        if (closeLine == 0 && problem == null) problem = "the class is not closed";
    }

    private static boolean restIsBlank(String s, int from) {
        for (int k = from; k < s.length(); k++) if (!Character.isWhitespace(s.charAt(k))) return false;
        return true;
    }

    private static String nameOf(Member member) {
        String h = member.head;
        if (member.kind == METHOD) {
            int paren = h.indexOf('(');
            String before = h.substring(0, paren).trim();
            int space = before.lastIndexOf(' ');
            return space < 0 ? before : before.substring(space + 1);
        }
        if (member.kind == FIELD) {
            String t = h.trim();
            int space = t.lastIndexOf(' ');
            return space < 0 ? t : t.substring(space + 1);
        }
        if (member.kind == TYPE) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(class|interface|enum)\\s+(\\w+)").matcher(h);
            if (m.find()) return m.group(2);
        }
        return null;
    }

    /** The member's lines, trimmed, blank ones left out: what two versions are compared by. */
    public List<String> canonical(Member member) {
        return canonical(member.start, member.end);
    }

    public List<String> canonical(int from, int to) {
        List<String> out = new ArrayList<>();
        for (int k = from; k <= to; k++) {
            String t = lines[k - 1].trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public Member memberAt(int line) {
        for (Member member : members) {
            if (line >= member.start && line <= member.end) return member;
        }
        return null;
    }
}
